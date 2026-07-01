package com.example.fittostrava

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseRoute
import androidx.health.connect.client.records.ExerciseRouteResult
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.io.File
import java.text.Normalizer
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class HealthConnectManager(private val context: Context) {
    private companion object {
        const val TAG = "FitToStrava"

        val SAMSUNG_HEALTH_TO_STRAVA_SPORT = mapOf(
            "abdominais curtos" to "Workout",
            "abdominais sit-up" to "Workout",
            "afundo" to "Workout",
            "agachamentos" to "Workout",
            "aerobico" to "Workout",
            "alongamentos" to "Yoga",
            "aparelho eliptico" to "Elliptical",
            "aparelho remada" to "Rowing",
            "aparelho step" to "StairStepper",
            "aparelhos de musculacao" to "WeightTraining",
            "artes marciais" to "Workout",
            "asa-delta" to "Workout",
            "badminton" to "Badminton",
            "bale" to "Dance",
            "bambole" to "Workout",
            "barra fixa" to "WeightTraining",
            "basquete" to "Basketball",
            "bicicleta" to "Ride",
            "bicicleta indoor" to "VirtualRide",
            "bike estacionaria" to "VirtualRide",
            "boxe" to "Workout",
            "burpees" to "HighIntensityIntervalTraining",
            "cadeira extensora" to "WeightTraining",
            "cadeira flexora" to "WeightTraining",
            "caiaque" to "Kayaking",
            "caminhada" to "Walk",
            "caminhar na neve" to "Snowshoe",
            "canoagem" to "Canoeing",
            "corrida" to "Run",
            "corrida de pista" to "Run",
            "corrida estacionaria" to "VirtualRun",
            "criquete" to "Cricket",
            "danca" to "Dance",
            "danca de salao" to "Dance",
            "desenvolvimento de ombros" to "WeightTraining",
            "elevacao frontal" to "WeightTraining",
            "elevacao lateral" to "WeightTraining",
            "escalada em rocha" to "RockClimbing",
            "escalador" to "Workout",
            "esqui" to "AlpineSki",
            "esqui alpino" to "AlpineSki",
            "esqui cross-country" to "NordicSki",
            "esteira" to "VirtualRun",
            "extensao de bracos" to "WeightTraining",
            "extensao lombar" to "WeightTraining",
            "flexao biceps" to "WeightTraining",
            "flexao de bracos" to "Workout",
            "futebol" to "Soccer",
            "futebol americano" to "Football",
            "galaxy fit (termo inicial)" to "Workout",
            "golfe" to "Golf",
            "handebol" to "Workout",
            "hidroginastica" to "Swim",
            "hoquei" to "Hockey",
            "hoquei no gelo" to "IceSkate",
            "iatismo" to "Sail",
            "ioga" to "Yoga",
            "lancamento de disco" to "Workout",
            "leg press" to "WeightTraining",
            "levantamento de pernas" to "WeightTraining",
            "levantamento terra" to "WeightTraining",
            "mergulho" to "Workout",
            "mountain bike" to "MountainBikeRide",
            "nado mar aberto" to "OpenWaterSwim",
            "natacao em aguas abertas" to "OpenWaterSwim",
            "nado piscina" to "Swim",
            "natacao em piscina" to "Swim",
            "orientacao" to "Workout",
            "outro exercicio" to "Workout",
            "patinacao" to "InlineSkate",
            "patinacao inline" to "InlineSkate",
            "patinacao no gelo" to "IceSkate",
            "pilates" to "Pilates",
            "polichinelo" to "HighIntensityIntervalTraining",
            "prancha" to "Workout",
            "pular corda" to "Workout",
            "puxada alta" to "WeightTraining",
            "rafting" to "Rowing",
            "raquetebol" to "Racquetball",
            "remo" to "Rowing",
            "rugby" to "Rugby",
            "simulador de escada" to "StairStepper",
            "skate" to "Skateboard",
            "snorkel" to "Workout",
            "snowboard" to "Snowboard",
            "softball" to "Workout",
            "squash" to "Squash",
            "subir andares" to "StairStepper",
            "supino" to "WeightTraining",
            "tenis" to "Tennis",
            "tenis de mesa" to "TableTennis",
            "tiro com arco" to "Workout",
            "treino em circuito" to "HighIntensityIntervalTraining",
            "trekking" to "Hike",
            "trilha" to "TrailRun",
            "trilha montada" to "Ride",
            "vela" to "Sail",
            "voleibol" to "Volleyball",
            "volei de praia" to "Volleyball",
            "hiit" to "HighIntensityIntervalTraining",
            "musculacao" to "WeightTraining",
            "yoga" to "Yoga",
        )
    }

    val exerciseSessionPermission: String =
        HealthPermission.getReadPermission(ExerciseSessionRecord::class)

    val permissions: Set<String> = setOf(
        exerciseSessionPermission,
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
    )

    private val client: HealthConnectClient by lazy {
        Log.d(TAG, "Inicializando HealthConnectClient")
        HealthConnectClient.getOrCreate(context)
    }

    fun isAvailable(): Boolean {
        val sdkStatus = HealthConnectClient.getSdkStatus(context)
        val available = sdkStatus == HealthConnectClient.SDK_AVAILABLE
        Log.d(TAG, "Checagem de disponibilidade do Health Connect: status=$sdkStatus available=$available")
        return available
    }

    fun stravaSportTypeFor(exerciseName: String): String = exerciseName.toTcxSport()

    suspend fun hasAllPermissions(): Boolean {
        if (!isAvailable()) {
            Log.d(TAG, "Permissoes nao checadas porque Health Connect indisponivel")
            return false
        }
        val grantedPermissions = client.permissionController.getGrantedPermissions()
        val hasAll = grantedPermissions.containsAll(permissions)
        Log.d(
            TAG,
            "Resultado da checagem de permissoes: granted=$grantedPermissions required=$permissions hasAll=$hasAll",
        )
        return hasAll
    }

    suspend fun getGrantedPermissions(): Set<String> {
        if (!isAvailable()) {
            Log.d(TAG, "Permissoes concedidas nao consultadas porque Health Connect indisponivel")
            return emptySet()
        }
        val grantedPermissions = client.permissionController.getGrantedPermissions()
        Log.d(TAG, "Permissoes concedidas pelo Health Connect: $grantedPermissions")
        return grantedPermissions
    }

    suspend fun hasExerciseSessionPermission(): Boolean {
        if (!isAvailable()) {
            Log.d(TAG, "Permissao de exercicio nao checada porque Health Connect indisponivel")
            return false
        }
        val grantedPermissions = getGrantedPermissions()
        val hasExercisePermission = exerciseSessionPermission in grantedPermissions
        Log.d(
            TAG,
            "Confirmacao da permissao de ExerciseSessionRecord: required=$exerciseSessionPermission granted=$hasExercisePermission",
        )
        return hasExercisePermission
    }

    suspend fun readActivities(days: Long = 30): List<FitnessActivity> {
        val now = Instant.now()
        val start = now.minus(Duration.ofDays(days))
        Log.d(TAG, "Intervalo de datas da busca: days=$days start=$start end=$now")
        Log.d(TAG, "Consultando ExerciseSessionRecord")
        val sessions = try {
            client.readRecords(
                ReadRecordsRequest(
                    recordType = ExerciseSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, now),
                )
            ).records
        } catch (error: Exception) {
            Log.e(TAG, "Erro ao consultar ExerciseSessionRecord", error)
            throw error
        }
        Log.d(TAG, "Quantidade de ExerciseSessionRecord retornados: ${sessions.size}")

        val activities = sessions
            .sortedByDescending { it.startTime }
            .map { session ->
                val distanceMeters = readDistanceMeters(session.startTime, session.endTime)
                FitnessActivity(
                    id = session.metadata.id,
                    startTime = session.startTime,
                    endTime = session.endTime,
                    exerciseType = session.displayExerciseName(),
                    duration = Duration.between(session.startTime, session.endTime),
                    distanceMeters = distanceMeters,
                )
            }
        Log.d(TAG, "Retorno da quantidade de atividades para a UI: ${activities.size}")
        return activities
    }

    suspend fun readActivityDetail(activity: FitnessActivity): FitnessActivityDetail {
        Log.d(TAG, "Lendo detalhe da atividade id=${activity.id}")
        val session = try {
            client.readRecords(
                ReadRecordsRequest(
                    recordType = ExerciseSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(activity.startTime, activity.endTime),
                )
            ).records.firstOrNull { it.metadata.id == activity.id }
        } catch (error: Exception) {
            Log.e(TAG, "Erro ao reler ExerciseSessionRecord no detalhe id=${activity.id}", error)
            throw error
        }

        val heartRateSamples = try {
            val records = client.readRecords(
                ReadRecordsRequest(
                    recordType = HeartRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(activity.startTime, activity.endTime),
                )
            ).records
            val samples = records
                .flatMap { it.samples }
                .map { HeartRateSampleData(time = it.time, beatsPerMinute = it.beatsPerMinute) }
                .sortedBy { it.time }
            Log.d(TAG, "Leitura de batimentos: records=${records.size} samples=${samples.size}")
            samples
        } catch (error: Exception) {
            Log.e(TAG, "Erro ao ler batimentos da atividade id=${activity.id}", error)
            emptyList()
        }

        val distanceRecords = try {
            val records = client.readRecords(
                ReadRecordsRequest(
                    recordType = DistanceRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(activity.startTime, activity.endTime),
                )
            ).records
            Log.d(TAG, "Leitura de distancia no detalhe: records=${records.size}")
            records
        } catch (error: Exception) {
            Log.e(TAG, "Erro ao ler distancia da atividade id=${activity.id}", error)
            emptyList()
        }

        val caloriesKcal = readCaloriesKcal(activity.startTime, activity.endTime)
        val routeResult = session?.exerciseRouteResult
        val route = (routeResult as? ExerciseRouteResult.Data)?.exerciseRoute
        val routePoints = route?.route.orEmpty()
        val routeStatus = when (routeResult) {
            is ExerciseRouteResult.Data -> RouteStatus.Available
            is ExerciseRouteResult.ConsentRequired -> RouteStatus.ConsentRequired
            else -> RouteStatus.NoData
        }
        val routeResultName = when (routeResult) {
            is ExerciseRouteResult.Data -> "Data"
            is ExerciseRouteResult.ConsentRequired -> "ConsentRequired"
            is ExerciseRouteResult.NoData -> "NoData"
            null -> "NoData"
            else -> routeResult.javaClass.simpleName
        }
        Log.d(
            TAG,
            "ExerciseRouteResult=$routeResultName points=${routePoints.size} sessionFound=${session != null}",
        )
        logRoutePointTimes("Health Connect retornou rota", routePoints)

        return FitnessActivityDetail(
            activity = activity.copy(
                distanceMeters = distanceRecords.sumOf { it.distance.inMeters }.takeIf { it > 0.0 }
                    ?: activity.distanceMeters,
            ),
            heartRateSampleCount = heartRateSamples.size,
            heartRateSamples = heartRateSamples,
            averageHeartRateBpm = heartRateSamples.takeIf { it.isNotEmpty() }
                ?.map { it.beatsPerMinute }
                ?.average()
                ?.toLong(),
            minHeartRateBpm = heartRateSamples.minOfOrNull { it.beatsPerMinute },
            maxHeartRateBpm = heartRateSamples.maxOfOrNull { it.beatsPerMinute },
            distanceRecordCount = distanceRecords.size,
            caloriesKcal = caloriesKcal,
            routeStatus = routeStatus,
            routePointCount = routePoints.size,
            route = route,
        )
    }

    fun detailWithRoute(detail: FitnessActivityDetail, route: ExerciseRoute): FitnessActivityDetail {
        logRoutePointTimes("Rota retornada pelo consentimento", route.route)
        return detail.copy(
            routeStatus = RouteStatus.Available,
            routePointCount = route.route.size,
            route = route,
        )
    }

    fun generateTcx(detail: FitnessActivityDetail): FitnessActivityDetail {
        val routePoints = detail.route?.route.orEmpty()
        logRoutePointTimes("Rota disponivel antes de gerar TCX", routePoints)
        val trackpoints = detail.toTcxTrackpoints()
        Log.d(
            TAG,
            "Trackpoints calculados para TCX: count=${trackpoints.size} routePoints=${routePoints.size} heartRateSamples=${detail.heartRateSamples.size}",
        )
        if (trackpoints.isEmpty()) {
            throw IllegalStateException("Não foi possível gerar TCX: a atividade não possui pontos de rota/tempo suficientes.")
        }

        val fileName = "fittostrava_${detail.activity.id.sanitizeFileName()}.tcx"
        val outputDirectory = context.getExternalFilesDir(null)
            ?: error("Diretorio externo privado indisponivel")
        val file = File(outputDirectory, fileName)
        Log.d(
            TAG,
            "Gerando TCX: file=${file.absolutePath} routePoints=${routePoints.size} heartRateSamples=${detail.heartRateSamples.size} calories=${detail.caloriesKcal}",
        )

        file.writeText(buildTcx(detail, trackpoints), Charsets.UTF_8)

        val result = TcxExportResult(
            filePath = file.absolutePath,
            routePointCount = routePoints.size,
            heartRateSampleCount = detail.heartRateSamples.size,
        )
        Log.d(TAG, "TCX gerado com sucesso: $result")
        return detail.copy(tcxExportResult = result)
    }

    private suspend fun readDistanceMeters(start: Instant, end: Instant): Double? {
        val records = try {
            client.readRecords(
                ReadRecordsRequest(
                    recordType = DistanceRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                )
            ).records
        } catch (error: Exception) {
            Log.e(TAG, "Erro ao ler distancia da sessao start=$start end=$end", error)
            return null
        }
        val total = records.sumOf { it.distance.inMeters }
        Log.d(TAG, "Leitura de distancia da sessao: records=${records.size} totalMeters=$total")

        return total.takeIf { it > 0.0 }
    }

    private suspend fun readCaloriesKcal(start: Instant, end: Instant): Double? {
        val records = try {
            client.readRecords(
                ReadRecordsRequest(
                    recordType = TotalCaloriesBurnedRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                )
            ).records
        } catch (error: Exception) {
            Log.e(TAG, "Erro ao ler calorias da atividade start=$start end=$end", error)
            return null
        }

        val total = records.sumOf { it.energy.inKilocalories }
        Log.d(TAG, "Leitura de calorias: records=${records.size} totalKcal=$total")
        return total.takeIf { it > 0.0 }
    }

    private fun buildTcx(detail: FitnessActivityDetail, trackpoints: List<TcxTrackpoint>): String {
        val activity = detail.activity
        val sport = activity.exerciseType.toTcxSport()
        val calories = detail.caloriesKcal?.toInt()

        return buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
            appendLine("""<TrainingCenterDatabase xmlns="http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2">""")
            appendLine("  <Activities>")
            appendLine("""    <Activity Sport="${sport.escapeXml()}">""")
            appendLine("      <Id>${activity.startTime}</Id>")
            appendLine("      <Notes>${activity.exerciseType.escapeXml()}</Notes>")
            appendLine("      <Lap StartTime=\"${activity.startTime}\">")
            appendLine("        <TotalTimeSeconds>${activity.duration.seconds}</TotalTimeSeconds>")
            activity.distanceMeters?.let { appendLine("        <DistanceMeters>${it.formatTcxNumber()}</DistanceMeters>") }
            calories?.let { appendLine("        <Calories>$it</Calories>") }
            appendLine("        <Intensity>Active</Intensity>")
            appendLine("        <TriggerMethod>Manual</TriggerMethod>")
            appendLine("        <Track>")

            trackpoints.forEach { trackpoint ->
                appendLine("          <Trackpoint>")
                appendLine("            <Time>${trackpoint.time.formatTcxTime()}</Time>")
                if (trackpoint.latitude != null && trackpoint.longitude != null) {
                    appendLine("            <Position>")
                    appendLine("              <LatitudeDegrees>${trackpoint.latitude}</LatitudeDegrees>")
                    appendLine("              <LongitudeDegrees>${trackpoint.longitude}</LongitudeDegrees>")
                    appendLine("            </Position>")
                }
                trackpoint.distanceMeters?.let {
                    appendLine("            <DistanceMeters>${it.formatTcxNumber()}</DistanceMeters>")
                }
                trackpoint.heartRateBpm?.let { heartRate ->
                    appendLine("            <HeartRateBpm>")
                    appendLine("              <Value>$heartRate</Value>")
                    appendLine("            </HeartRateBpm>")
                }
                appendLine("          </Trackpoint>")
            }

            appendLine("        </Track>")
            appendLine("      </Lap>")
            appendLine("    </Activity>")
            appendLine("  </Activities>")
            appendLine("</TrainingCenterDatabase>")
        }
    }

    private fun FitnessActivityDetail.toTcxTrackpoints(): List<TcxTrackpoint> {
        val routePoints = route?.route.orEmpty()
        if (routePoints.isNotEmpty()) {
            val cumulativeDistances = routePoints.cumulativeDistances(activity.distanceMeters)
            return routePoints.mapIndexed { index, location ->
                TcxTrackpoint(
                    time = location.time,
                    latitude = location.latitude,
                    longitude = location.longitude,
                    distanceMeters = cumulativeDistances.getOrNull(index),
                    heartRateBpm = closestHeartRate(location.time)?.beatsPerMinute,
                )
            }
        }

        val heartRateSamples = heartRateSamples
            .distinctBy { it.time }
            .sortedBy { it.time }
        if (heartRateSamples.isNotEmpty()) {
            return heartRateSamples.map { sample ->
                TcxTrackpoint(
                    time = sample.time,
                    distanceMeters = distanceAt(sample.time),
                    heartRateBpm = sample.beatsPerMinute,
                )
            }
        }

        return emptyList()
    }

    private fun FitnessActivityDetail.distanceAt(time: Instant): Double? {
        val totalDistance = activity.distanceMeters ?: return null
        val elapsedSeconds = Duration.between(activity.startTime, time).seconds
            .coerceIn(0, activity.duration.seconds)
        val durationSeconds = activity.duration.seconds.takeIf { it > 0 } ?: return null
        return totalDistance * elapsedSeconds.toDouble() / durationSeconds.toDouble()
    }

    private fun List<ExerciseRoute.Location>.cumulativeDistances(totalDistanceMeters: Double?): List<Double> {
        if (isEmpty()) return emptyList()

        val rawDistances = mutableListOf(0.0)
        for (index in 1 until size) {
            rawDistances += rawDistances.last() + this[index - 1].distanceTo(this[index])
        }

        val rawTotal = rawDistances.lastOrNull() ?: 0.0
        if (totalDistanceMeters == null || rawTotal <= 0.0) return rawDistances

        val scale = totalDistanceMeters / rawTotal
        return rawDistances.map { it * scale }
    }

    private fun ExerciseRoute.Location.distanceTo(other: ExerciseRoute.Location): Double {
        val earthRadiusMeters = 6371000.0
        val lat1 = Math.toRadians(latitude)
        val lat2 = Math.toRadians(other.latitude)
        val deltaLat = Math.toRadians(other.latitude - latitude)
        val deltaLon = Math.toRadians(other.longitude - longitude)
        val a = sin(deltaLat / 2).pow(2.0) + cos(lat1) * cos(lat2) * sin(deltaLon / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(max(0.0, 1 - a)))
        return earthRadiusMeters * c
    }

    private fun FitnessActivityDetail.closestHeartRate(time: Instant): HeartRateSampleData? =
        heartRateSamples.minByOrNull { sample ->
            kotlin.math.abs(Duration.between(sample.time, time).seconds)
        }?.takeIf { sample ->
            kotlin.math.abs(Duration.between(sample.time, time).seconds) <= 30
        }

    private fun logRoutePointTimes(prefix: String, routePoints: List<ExerciseRoute.Location>) {
        Log.d(TAG, "$prefix: points=${routePoints.size}")
        if (routePoints.isNotEmpty()) {
            Log.d(
                TAG,
                "$prefix: first=${routePoints.first().time.formatTcxTime()} last=${routePoints.last().time.formatTcxTime()}",
            )
        }
    }

    private fun String.toTcxSport(): String =
        SAMSUNG_HEALTH_TO_STRAVA_SPORT[normalizeActivityName()]
            ?: when {
                contains("running", ignoreCase = true) -> "Run"
                contains("biking", ignoreCase = true) -> "Ride"
                else -> "Workout"
            }

    private fun String.normalizeActivityName(): String {
        val withoutAccents = Normalizer.normalize(this, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        return withoutAccents
            .lowercase(Locale.ROOT)
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun String.sanitizeFileName(): String =
        replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "activity" }

    private fun String.escapeXml(): String =
        replace("&", "&amp;")
            .replace("\"", "&quot;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("'", "&apos;")

    private fun Double.formatTcxNumber(): String = "%.2f".format(Locale.US, this)

    private fun Instant.formatTcxTime(): String = DateTimeFormatter.ISO_INSTANT.format(this)

    private fun ExerciseSessionRecord.displayExerciseName(): String =
        title?.takeIf { it.isNotBlank() }?.trim() ?: exerciseName()

    private fun ExerciseSessionRecord.exerciseName(): String = when (exerciseType) {
        ExerciseSessionRecord.EXERCISE_TYPE_BADMINTON -> "Badminton"
        ExerciseSessionRecord.EXERCISE_TYPE_BASKETBALL -> "Basquete"
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING -> "Ciclismo"
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY -> "Bike estacionaria"
        ExerciseSessionRecord.EXERCISE_TYPE_BOXING -> "Boxe"
        ExerciseSessionRecord.EXERCISE_TYPE_CRICKET -> "Criquete"
        ExerciseSessionRecord.EXERCISE_TYPE_DANCING -> "Danca"
        ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL -> "Aparelho eliptico"
        ExerciseSessionRecord.EXERCISE_TYPE_FOOTBALL_AMERICAN -> "Futebol americano"
        ExerciseSessionRecord.EXERCISE_TYPE_GOLF -> "Golfe"
        ExerciseSessionRecord.EXERCISE_TYPE_HANDBALL -> "Handebol"
        ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING -> "HIIT"
        ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> "Trekking"
        ExerciseSessionRecord.EXERCISE_TYPE_ICE_HOCKEY -> "Hoquei no gelo"
        ExerciseSessionRecord.EXERCISE_TYPE_ICE_SKATING -> "Patinacao no gelo"
        ExerciseSessionRecord.EXERCISE_TYPE_MARTIAL_ARTS -> "Artes marciais"
        ExerciseSessionRecord.EXERCISE_TYPE_PADDLING -> "Canoagem"
        ExerciseSessionRecord.EXERCISE_TYPE_PILATES -> "Pilates"
        ExerciseSessionRecord.EXERCISE_TYPE_RACQUETBALL -> "Raquetebol"
        ExerciseSessionRecord.EXERCISE_TYPE_ROCK_CLIMBING -> "Escalada em rocha"
        ExerciseSessionRecord.EXERCISE_TYPE_ROWING -> "Remo"
        ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE -> "Aparelho remada"
        ExerciseSessionRecord.EXERCISE_TYPE_RUGBY -> "Rugby"
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING -> "Corrida"
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL -> "Esteira"
        ExerciseSessionRecord.EXERCISE_TYPE_SAILING -> "Vela"
        ExerciseSessionRecord.EXERCISE_TYPE_SKATING -> "Patinacao"
        ExerciseSessionRecord.EXERCISE_TYPE_SKIING -> "Esqui"
        ExerciseSessionRecord.EXERCISE_TYPE_SNOWBOARDING -> "Snowboard"
        ExerciseSessionRecord.EXERCISE_TYPE_SNOWSHOEING -> "Caminhar na neve"
        ExerciseSessionRecord.EXERCISE_TYPE_SOCCER -> "Futebol"
        ExerciseSessionRecord.EXERCISE_TYPE_SOFTBALL -> "Softball"
        ExerciseSessionRecord.EXERCISE_TYPE_SQUASH -> "Squash"
        ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING -> "Subir andares"
        ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING_MACHINE -> "Simulador de escada"
        ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING -> "Musculacao"
        ExerciseSessionRecord.EXERCISE_TYPE_STRETCHING -> "Alongamentos"
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> "Natacao em aguas abertas"
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL -> "Natacao em piscina"
        ExerciseSessionRecord.EXERCISE_TYPE_TABLE_TENNIS -> "Tenis de mesa"
        ExerciseSessionRecord.EXERCISE_TYPE_TENNIS -> "Tenis"
        ExerciseSessionRecord.EXERCISE_TYPE_VOLLEYBALL -> "Voleibol"
        ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> "Caminhada"
        ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING -> "Musculacao"
        ExerciseSessionRecord.EXERCISE_TYPE_YOGA -> "Yoga"
        else -> "Outro exercicio"
    }

    private data class TcxTrackpoint(
        val time: Instant,
        val latitude: Double? = null,
        val longitude: Double? = null,
        val distanceMeters: Double? = null,
        val heartRateBpm: Long? = null,
    )
}
