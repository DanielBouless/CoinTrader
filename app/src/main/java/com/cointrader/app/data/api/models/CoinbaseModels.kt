package com.cointrader.app.data.api.models

import com.google.gson.annotations.SerializedName

data class ProductsResponse(
    val products: List<Product> = emptyList()
)

data class Product(
    @SerializedName("product_id") val productId: String = "",
    @SerializedName("base_currency_id") val baseCurrency: String = "",
    @SerializedName("quote_currency_id") val quoteCurrency: String = "",
    val price: String = "",
    val status: String = ""
)

data class CandlesResponse(
    val candles: List<Candle> = emptyList()
)

data class Candle(
    val start: String = "",
    val low: String = "",
    val high: String = "",
    val open: String = "",
    val close: String = "",
    val volume: String = ""
) {
    val lowDouble: Double get() = low.toDoubleOrNull() ?: 0.0
    val highDouble: Double get() = high.toDoubleOrNull() ?: 0.0
    val openDouble: Double get() = open.toDoubleOrNull() ?: 0.0
    val closeDouble: Double get() = close.toDoubleOrNull() ?: 0.0
}
