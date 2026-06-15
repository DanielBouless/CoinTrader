package com.cointrader.app.data.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecureStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG           = "SecureStorage"
        private const val PREFS_FILE    = "cointrader_secure_prefs"
        private const val KEY_ACCESS    = "oauth_access_token"
        private const val KEY_REFRESH   = "oauth_refresh_token"
        private const val KEY_EXPIRES   = "oauth_token_expires_at"
    }

    private val prefs: SharedPreferences by lazy { createOrRecover() }

    // ── Token persistence ─────────────────────────────────────────────────

    fun saveTokens(accessToken: String, refreshToken: String, expiresIn: Int) {
        val expiresAt = System.currentTimeMillis() + (expiresIn * 1000L)
        prefs.edit()
            .putString(KEY_ACCESS,  accessToken)
            .putString(KEY_REFRESH, refreshToken)
            .putLong(KEY_EXPIRES,   expiresAt)
            .apply()
    }

    fun getAccessToken(): String?  = prefs.getString(KEY_ACCESS,  null)
    fun getRefreshToken(): String? = prefs.getString(KEY_REFRESH, null)

    fun isTokenExpired(): Boolean {
        val expiresAt = prefs.getLong(KEY_EXPIRES, 0L)
        if (expiresAt == 0L) return true
        // Treat as expired 60 s early to avoid races
        return System.currentTimeMillis() >= expiresAt - 60_000L
    }

    fun isLoggedIn(): Boolean =
        getAccessToken() != null && getRefreshToken() != null

    fun clearTokens() {
        prefs.edit()
            .remove(KEY_ACCESS)
            .remove(KEY_REFRESH)
            .remove(KEY_EXPIRES)
            .apply()
    }

    // ── EncryptedSharedPreferences setup ─────────────────────────────────

    private fun buildMasterKey() =
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

    private fun buildPrefs(key: MasterKey): SharedPreferences =
        EncryptedSharedPreferences.create(
            context, PREFS_FILE, key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

    private fun createOrRecover(): SharedPreferences {
        return try {
            buildPrefs(buildMasterKey())
        } catch (e: Exception) {
            Log.w(TAG, "Keyset corrupted — wiping and recreating", e)
            wipeCorruptedState()
            buildPrefs(buildMasterKey())
        }
    }

    private fun wipeCorruptedState() {
        try { context.deleteSharedPreferences(PREFS_FILE) } catch (_: Exception) {}
        try {
            val ks = KeyStore.getInstance("AndroidKeyStore").also { it.load(null) }
            if (ks.containsAlias(MasterKey.DEFAULT_MASTER_KEY_ALIAS))
                ks.deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
        } catch (_: Exception) {}
    }
}
