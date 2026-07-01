package com.example.fittostrava

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class StravaApiClient(
    private val accessToken: String,
) {
    suspend fun uploadTcxAndUpdateActivity(
        file: File,
        sportType: String,
        name: String,
        description: String,
        externalId: String,
    ): StravaUploadResult = withContext(Dispatchers.IO) {
        val upload = createUpload(
            file = file,
            sportType = sportType,
            name = name,
            description = description,
            externalId = externalId,
        )
        val readyUpload = waitForUpload(upload.id)
        val activityId = readyUpload.activityId
            ?: error(readyUpload.error ?: "Upload do Strava terminou sem activity_id.")

        updateActivity(
            activityId = activityId,
            sportType = sportType,
            name = name,
            description = description,
        )

        StravaUploadResult(
            uploadId = upload.id,
            activityId = activityId,
            sportType = sportType,
        )
    }

    private fun createUpload(
        file: File,
        sportType: String,
        name: String,
        description: String,
        externalId: String,
    ): UploadStatus {
        val boundary = "FitToStrava${System.currentTimeMillis()}"
        val connection = openConnection("https://www.strava.com/api/v3/uploads", "POST")
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        connection.doOutput = true

        connection.outputStream.use { output ->
            fun writeField(name: String, value: String) {
                output.write("--$boundary\r\n".toByteArray())
                output.write("Content-Disposition: form-data; name=\"$name\"\r\n\r\n".toByteArray())
                output.write(value.toByteArray())
                output.write("\r\n".toByteArray())
            }

            writeField("data_type", "tcx")
            writeField("sport_type", sportType)
            writeField("name", name)
            writeField("description", description)
            writeField("external_id", externalId)

            output.write("--$boundary\r\n".toByteArray())
            output.write(
                "Content-Disposition: form-data; name=\"file\"; filename=\"${file.name}\"\r\n".toByteArray()
            )
            output.write("Content-Type: application/vnd.garmin.tcx+xml\r\n\r\n".toByteArray())
            file.inputStream().use { input -> input.copyTo(output) }
            output.write("\r\n--$boundary--\r\n".toByteArray())
        }

        return connection.readJsonResponse().toUploadStatus()
    }

    private suspend fun waitForUpload(uploadId: String): UploadStatus {
        repeat(30) {
            val upload = getUpload(uploadId)
            if (upload.error != null || upload.activityId != null) {
                return upload
            }
            delay(1_000)
        }
        error("Tempo limite ao aguardar processamento do upload no Strava.")
    }

    private fun getUpload(uploadId: String): UploadStatus {
        val connection = openConnection("https://www.strava.com/api/v3/uploads/$uploadId", "GET")
        return connection.readJsonResponse().toUploadStatus()
    }

    private fun updateActivity(
        activityId: String,
        sportType: String,
        name: String,
        description: String,
    ) {
        val body = JSONObject()
            .put("name", name)
            .put("sport_type", sportType)
            .put("description", description)
            .toString()

        val connection = openConnection("https://www.strava.com/api/v3/activities/$activityId", "PUT")
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        connection.doOutput = true
        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        connection.readJsonResponse()
    }

    private fun openConnection(url: String, method: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Accept", "application/json")
            connectTimeout = 20_000
            readTimeout = 30_000
        }

    private fun HttpURLConnection.readJsonResponse(): JSONObject {
        val response = if (responseCode in 200..299) {
            inputStream.bufferedReader().use { it.readText() }
        } else {
            val message = errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            error("Strava HTTP $responseCode: $message")
        }
        return JSONObject(response)
    }

    private fun JSONObject.toUploadStatus(): UploadStatus =
        UploadStatus(
            id = optString("id_str").ifBlank { optLong("id").toString() },
            status = optString("status"),
            error = optString("error").takeIf { it.isNotBlank() && it != "null" },
            activityId = optString("activity_id").takeIf { it.isNotBlank() && it != "null" },
        )

    private data class UploadStatus(
        val id: String,
        val status: String,
        val error: String?,
        val activityId: String?,
    )
}
