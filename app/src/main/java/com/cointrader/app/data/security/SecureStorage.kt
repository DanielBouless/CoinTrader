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
        private const val TAG            = "SecureStorage"
        private const val PREFS_FILE     = "cointrader_secure_prefs"
        private const val KEY_API_NAME   = "coinbase_api_key_name"
        private const val KEY_PRIV_KEY   = "coinbase_private_key"
    }

    private val prefs: SharedPreferences by lazy { createOrRecover() }

    fun saveApiCredentials(apiKeyName: String, privateKey: String) {
        prefs.edit()
            .putString(KEY_API_NAME, apiKeyName)
            .putString(KEY_PRIV_KEY, privateKey)
            .apply()
    }

    fun getApiKeyName(): String?  = prefs.getString(KEY_API_NAME, null)
    fun getPrivateKey(): String?  = prefs.getString(KEY_PRIV_KEY, null)
    fun hasCredentials(): Boolean = getApiKeyName() != null && getPrivateKey() != null

    fun clearCredentials() {
        prefs.edit()
            .remove(KEY_API_NAME)
            .remove(KEY_PRIV_KEY)
            .apply()
    }

    private fun buildMasterKey() =
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()

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
