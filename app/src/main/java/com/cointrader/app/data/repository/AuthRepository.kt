package com.cointrader.app.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import com.cointrader.app.BuildConfig
import com.cointrader.app.data.api.CoinbaseAuthService
import com.cointrader.app.data.api.models.TokenRequest
import com.cointrader.app.data.security.SecureStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authService: CoinbaseAuthService,
    private val secureStorage: SecureStorage
) {
    companion object {
        private const val AUTH_URL = "https://www.coinbase.com/oauth/authorize"

        // Scopes needed to read all tradeable products and their candle data
        private const val SCOPES = "wallet:user:read wallet:accounts:read"
    }

    // ── Session state ─────────────────────────────────────────────────────

    fun isLoggedIn(): Boolean = secureStorage.isLoggedIn()

    /** Returns a valid access token, refreshing silently if it has expired. */
    suspend fun getValidAccessToken(): String? {
        if (!secureStorage.isLoggedIn()) return null

        if (secureStorage.isTokenExpired()) {
            val refreshed = tryRefresh()
            if (!refreshed) {
                secureStorage.clearTokens()
                return null
            }
        }
        return secureStorage.getAccessToken()
    }

    fun logout() = secureStorage.clearTokens()

    // ── OAuth2 Authorization Code flow ────────────────────────────────────

    /**
     * Opens the Coinbase login page in a Chrome Custom Tab.
     * After the user approves, Coinbase redirects to cointrader://oauth/callback?code=…
     * which MainActivity catches and forwards to [handleCallback].
     */
    fun launchCoinbaseLogin(context: Context) {
        val uri = Uri.parse(AUTH_URL).buildUpon()
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id",     BuildConfig.COINBASE_CLIENT_ID)
            .appendQueryParameter("redirect_uri",  BuildConfig.COINBASE_REDIRECT_URI)
            .appendQueryParameter("scope",         SCOPES)
            .build()

        CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
            .launchUrl(context, uri)
    }

    /**
     * Called by MainActivity when the OAuth redirect arrives.
     * Exchanges the authorization code for access + refresh tokens.
     */
    suspend fun handleCallback(uri: Uri): Result<Unit> {
        val code = uri.getQueryParameter("code")
            ?: return Result.failure(Exception("No authorization code in redirect"))

        return runCatching {
            val response = authService.exchangeCode(
                TokenRequest(
                    grantType    = "authorization_code",
                    code         = code,
                    redirectUri  = BuildConfig.COINBASE_REDIRECT_URI,
                    clientId     = BuildConfig.COINBASE_CLIENT_ID,
                    clientSecret = BuildConfig.COINBASE_CLIENT_SECRET
                )
            )
            secureStorage.saveTokens(
                accessToken  = response.accessToken,
                refreshToken = response.refreshToken,
                expiresIn    = response.expiresIn
            )
        }
    }

    // ── Token refresh ─────────────────────────────────────────────────────

    private suspend fun tryRefresh(): Boolean {
        val refreshToken = secureStorage.getRefreshToken() ?: return false
        return runCatching {
            val response = authService.refreshToken(
                TokenRequest(
                    grantType    = "refresh_token",
                    refreshToken = refreshToken,
                    clientId     = BuildConfig.COINBASE_CLIENT_ID,
                    clientSecret = BuildConfig.COINBASE_CLIENT_SECRET
                )
            )
            secureStorage.saveTokens(
                accessToken  = response.accessToken,
                refreshToken = response.refreshToken,
                expiresIn    = response.expiresIn
            )
        }.isSuccess
    }
}
