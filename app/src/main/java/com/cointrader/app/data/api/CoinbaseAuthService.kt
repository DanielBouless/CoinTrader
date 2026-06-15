package com.cointrader.app.data.api

import com.cointrader.app.data.api.models.TokenRequest
import com.cointrader.app.data.api.models.TokenResponse
import retrofit2.http.Body
import retrofit2.http.POST

interface CoinbaseAuthService {

    /** Exchange an authorization code for access + refresh tokens. */
    @POST("oauth/token")
    suspend fun exchangeCode(@Body request: TokenRequest): TokenResponse

    /** Use a refresh token to get a new access token. */
    @POST("oauth/token")
    suspend fun refreshToken(@Body request: TokenRequest): TokenResponse
}
