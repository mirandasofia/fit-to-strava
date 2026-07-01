package com.example.fittostrava

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class StravaOAuthClient(
    private val tokenStore: StravaTokenStore,
) {
    fun authorizationUri(): Uri {
        require(StravaAuthConfig.isConfigured) {
            "Configure STRAVA_CLIENT_ID e STRAVA_CLIENT_SECRET no local.properties."
        }
        return Uri.parse("https://www.strava.com/oauth/mobile/authorize")
            .buildUpon()
            .appendQueryParameter("client_id", StravaAuthConfig.clientId)
            .appendQueryParameter("redirect_uri", StravaAuthConfig.REDIRECT_URI)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("approval_prompt", "auto")
            .appendQueryParameter("scope", StravaAuthConfig.SCOPE)
            .build()
    }

    suspend fun exchangeCode(code: String, scope: String): StravaTokens = withContext(Dispatchers.IO) {
        if (!scope.hasActivityWriteScope()) {
            error("O Strava nao concedeu o escopo activity:write.")
        }
        val response = postTokenRequest(
            "client_id" to StravaAuthConfig.clientId,
            "client_secret" to StravaAuthConfig.clientSecret,
            "code" to code,
            "grant_type" to "authorization_code",
        )
        response.toTokens(scope).also { tokenStore.save(it) }
    }

    suspend fun getValidAccessToken(): String {
        val tokens = tokenStore.read()
            ?: error("Conecte ao Strava antes de enviar a atividade.")
        if (!tokens.hasActivityWriteScope()) {
            error("Reconecte ao Strava concedendo o escopo activity:write.")
        }
        if (!tokens.expiresSoon()) {
            return tokens.accessToken
        }
        return refresh(tokens.refreshToken).accessToken
    }

    private suspend fun refresh(refreshToken: String): StravaTokens = withContext(Dispatchers.IO) {
        val response = postTokenRequest(
            "client_id" to StravaAuthConfig.clientId,
            "client_secret" to StravaAuthConfig.clientSecret,
            "grant_type" to "refresh_token",
            "refresh_token" to refreshToken,
        )
        response.toTokens(StravaAuthConfig.SCOPE).also { tokenStore.save(it) }
    }

    private fun postTokenRequest(vararg params: Pair<String, String>): JSONObject {
        if (!StravaAuthConfig.isConfigured) {
            error("Configure STRAVA_CLIENT_ID e STRAVA_CLIENT_SECRET no local.properties.")
        }
        val body = params.joinToString("&") { (key, value) ->
            "${key.urlEncode()}=${value.urlEncode()}"
        }
        val connection = (URL("https://www.strava.com/oauth/token").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connectTimeout = 20_000
            readTimeout = 30_000
            doOutput = true
        }
        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        return connection.readJsonResponse()
    }

    private fun HttpURLConnection.readJsonResponse(): JSONObject {
        val response = if (responseCode in 200..299) {
            inputStream.bufferedReader().use { it.readText() }
        } else {
            val message = errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            error("Strava OAuth HTTP $responseCode: $message")
        }
        return JSONObject(response)
    }

    private fun JSONObject.toTokens(scope: String): StravaTokens =
        StravaTokens(
            accessToken = getString("access_token"),
            refreshToken = getString("refresh_token"),
            expiresAt = getLong("expires_at"),
            scope = scope,
        )

    private fun String.urlEncode(): String =
        URLEncoder.encode(this, Charsets.UTF_8.name())

    private fun String.hasActivityWriteScope(): Boolean =
        split(',', ' ')
            .map { it.trim() }
            .any { it == StravaAuthConfig.SCOPE }
}
