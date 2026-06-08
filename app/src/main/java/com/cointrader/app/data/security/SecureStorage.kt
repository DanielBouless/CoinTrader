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
        private const val TAG = "SecureStorage"
        private const val PREFS_FILE_NAME = "cointrader_secure_prefs"
        private const val KEY_API_KEY_NAME = "coinbase_api_key_name"
        private const val KEY_PRIVATE_KEY = "coinbase_private_key"
    }

    private val sharedPreferences: SharedPreferences by lazy { createOrRecover() }

    private fun buildMasterKey(): MasterKey =
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

    private fun buildPrefs(key: MasterKey): SharedPreferences =
        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE_NAME,
            key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

    private fun createOrRecover(): SharedPreferences {
        return try {
            buildPrefs(buildMasterKey())
        } catch (e: Exception) {
            Log.w(TAG, "EncryptedSharedPreferences keyset corrupted — wiping and recreating", e)
            wipeCorruptedState()
            buildPrefs(buildMasterKey())
        }
    }

    private fun wipeCorruptedState() {
        try { context.deleteSharedPreferences(PREFS_FILE_NAME) } catch (_: Exception) {}
        try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").also { it.load(null) }
            if (keyStore.containsAlias(MasterKey.DEFAULT_MASTER_KEY_ALIAS)) {
                keyStore.deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
            }
        } catch (_: Exception) {}
    }

    fun saveApiCredentials(apiKeyName: String, privateKey: String) {
        sharedPreferences.edit()
            .putString(KEY_API_KEY_NAME, apiKeyName)
            .putString(KEY_PRIVATE_KEY, privateKey)
            .apply()
    }

    fun getApiKeyName(): String? = sharedPreferences.getString(KEY_API_KEY_NAME, null)
    fun getPrivateKey(): String? = sharedPreferences.getString(KEY_PRIVATE_KEY, null)

    fun hasCredentials(): Boolean = getApiKeyName() != null && getPrivateKey() != null

    fun clearCredentials() {
        sharedPreferences.edit()
            .remove(KEY_API_KEY_NAME)
            .remove(KEY_PRIVATE_KEY)
            .apply()
    }
}
