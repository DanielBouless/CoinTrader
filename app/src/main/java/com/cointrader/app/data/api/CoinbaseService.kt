package com.cointrader.app.data.api

import com.cointrader.app.data.api.models.CandlesResponse
import com.cointrader.app.data.api.models.ProductsResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface CoinbaseService {

    @GET("brokerage/products")
    suspend fun getProducts(
        @Query("product_type") productType: String = "SPOT",
        @Query("limit") limit: Int = 250
    ): ProductsResponse

    @GET("brokerage/products/{product_id}/candles")
    suspend fun getCandles(
        @Path("product_id") productId: String,
        @Query("start") start: String,
        @Query("end") end: String,
        @Query("granularity") granularity: String = "ONE_DAY",
        @Query("limit") limit: Int = 300
    ): CandlesResponse
}
