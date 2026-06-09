package com.cointrader.app.data.api

import com.cointrader.app.data.api.models.CandlesResponse
import com.cointrader.app.data.api.models.ProductsResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface CoinbaseService {

    /**
     * Lists all products. Coinbase paginates with cursor; pass [cursor] on subsequent calls.
     * [limit] max is 250 per Coinbase docs.
     */
    @GET("brokerage/products")
    suspend fun getProducts(
        @Query("product_type") productType: String = "SPOT",
        @Query("limit") limit: Int = 250,
        @Query("cursor") cursor: String? = null
    ): ProductsResponse

    /**
     * Returns up to [limit] daily (or other granularity) OHLCV candles for [productId].
     * [start] and [end] are Unix epoch seconds as strings.
     * Coinbase returns results newest-first; the repository sorts them oldest-first.
     */
    @GET("brokerage/products/{product_id}/candles")
    suspend fun getCandles(
        @Path("product_id") productId: String,
        @Query("start") start: String,
        @Query("end") end: String,
        @Query("granularity") granularity: String = "ONE_DAY",
        @Query("limit") limit: Int = 300
    ): CandlesResponse
}
