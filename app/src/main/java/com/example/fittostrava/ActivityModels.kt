package com.example.fittostrava

import androidx.health.connect.client.records.ExerciseRoute
import java.time.Duration
import java.time.Instant

data class FitnessActivity(
    val id: String,
    val startTime: Instant,
    val endTime: Instant,
    val exerciseType: String,
    val duration: Duration,
    val distanceMeters: Double?,
)

data class FitnessActivityDetail(
    val activity: FitnessActivity,
    val heartRateSampleCount: Int,
    val heartRateSamples: List<HeartRateSampleData> = emptyList(),
    val averageHeartRateBpm: Long?,
    val minHeartRateBpm: Long?,
    val maxHeartRateBpm: Long?,
    val distanceRecordCount: Int,
    val caloriesKcal: Double? = null,
    val routeStatus: RouteStatus,
    val routePointCount: Int? = null,
    val route: ExerciseRoute? = null,
    val tcxExportResult: TcxExportResult? = null,
    val stravaUploadResult: StravaUploadResult? = null,
)

data class HeartRateSampleData(
    val time: Instant,
    val beatsPerMinute: Long,
)

data class TcxExportResult(
    val filePath: String,
    val routePointCount: Int,
    val heartRateSampleCount: Int,
)

data class StravaUploadResult(
    val uploadId: String,
    val activityId: String,
    val sportType: String,
)

enum class RouteStatus {
    Available,
    ConsentRequired,
    NoData,
}

data class FitToStravaUiState(
    val isHealthConnectAvailable: Boolean = false,
    val hasExerciseSessionPermission: Boolean = false,
    val isLoading: Boolean = true,
    val isDetailLoading: Boolean = false,
    val isGeneratingTcx: Boolean = false,
    val activities: List<FitnessActivity> = emptyList(),
    val selectedDetail: FitnessActivityDetail? = null,
    val errorMessage: String? = null,
    val routeMessage: String? = null,
    val permissionMessage: String? = null,
    val isStravaConnected: Boolean = false,
    val isConnectingToStrava: Boolean = false,
    val isUploadingToStrava: Boolean = false,
)
