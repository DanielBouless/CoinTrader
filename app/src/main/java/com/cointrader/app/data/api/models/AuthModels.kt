package com.cointrader.app.data.api.models

import com.google.gson.annotations.SerializedName

data class TokenResponse(
    @SerializedName("access_token")  val accessToken: String  = "",
    @SerializedName("token_type")    val tokenType: String    = "Bearer",
    @SerializedName("expires_in")    val expiresIn: Int       = 7200,
    @SerializedName("refresh_token") val refreshToken: String = "",
    @SerializedName("scope")         val scope: String        = ""
)

data class TokenRequest(
    @SerializedName("grant_type")    val grantType: String,
    @SerializedName("code")          val code: String?         = null,
    @SerializedName("redirect_uri")  val redirectUri: String?  = null,
    @SerializedName("client_id")     val clientId: String,
    @SerializedName("client_secret") val clientSecret: String,
    @SerializedName("refresh_token") val refreshToken: String? = null
)
