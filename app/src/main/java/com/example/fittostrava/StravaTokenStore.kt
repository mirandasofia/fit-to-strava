package com.example.fittostrava

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class StravaTokenStore(context: Context) {
    private val appContext = context.applicationContext

    private val preferences by lazy {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            "strava_tokens",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    suspend fun read(): StravaTokens? = withContext(Dispatchers.IO) {
        val accessToken = preferences.getString(KEY_ACCESS_TOKEN, null)?.takeIf { it.isNotBlank() }
        val refreshToken = preferences.getString(KEY_REFRESH_TOKEN, null)?.takeIf { it.isNotBlank() }
        val expiresAt = preferences.getLong(KEY_EXPIRES_AT, 0L)
        val scope = preferences.getString(KEY_SCOPE, "").orEmpty()
        if (accessToken == null || refreshToken == null || expiresAt <= 0L) {
            null
        } else {
            StravaTokens(
                accessToken = accessToken,
                refreshToken = refreshToken,
                expiresAt = expiresAt,
                scope = scope,
            )
        }
    }

    suspend fun save(tokens: StravaTokens) = withContext(Dispatchers.IO) {
        preferences.edit()
            .putString(KEY_ACCESS_TOKEN, tokens.accessToken)
            .putString(KEY_REFRESH_TOKEN, tokens.refreshToken)
            .putLong(KEY_EXPIRES_AT, tokens.expiresAt)
            .putString(KEY_SCOPE, tokens.scope)
            .apply()
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        preferences.edit().clear().apply()
    }

    private companion object {
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_REFRESH_TOKEN = "refresh_token"
        const val KEY_EXPIRES_AT = "expires_at"
        const val KEY_SCOPE = "scope"
    }
}

data class StravaTokens(
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Long,
    val scope: String,
) {
    fun expiresSoon(nowEpochSeconds: Long = System.currentTimeMillis() / 1000L): Boolean =
        expiresAt <= nowEpochSeconds + REFRESH_WINDOW_SECONDS

    fun hasActivityWriteScope(): Boolean =
        scope.split(',', ' ')
            .map { it.trim() }
            .any { it == StravaAuthConfig.SCOPE }

    private companion object {
        const val REFRESH_WINDOW_SECONDS = 3600L
    }
}
