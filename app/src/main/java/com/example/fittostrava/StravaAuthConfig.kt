package com.example.fittostrava

object StravaAuthConfig {
    const val REDIRECT_URI = "fittostrava://oauth"
    const val SCOPE = "activity:write"

    val clientId: String = BuildConfig.STRAVA_CLIENT_ID
    val clientSecret: String = BuildConfig.STRAVA_CLIENT_SECRET

    val isConfigured: Boolean
        get() = clientId.isNotBlank() && clientSecret.isNotBlank()
}
