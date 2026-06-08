package com.cointrader.app.di

import com.cointrader.app.data.api.CoinbaseService
import com.cointrader.app.data.auth.JwtBuilder
import com.cointrader.app.data.security.SecureStorage
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val BASE_URL = "https://api.coinbase.com/api/v3/"

    @Provides
    @Singleton
    fun provideOkHttpClient(secureStorage: SecureStorage): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val jwtInterceptor = Interceptor { chain ->
            val request = chain.request()
            val apiKeyName = secureStorage.getApiKeyName()
            val privateKey = secureStorage.getPrivateKey()

            if (apiKeyName != null && privateKey != null) {
                val method = request.method
                val path = request.url.encodedPath + if (request.url.encodedQuery != null) "?${request.url.encodedQuery}" else ""
                val jwt = runCatching {
                    JwtBuilder.buildJwt(apiKeyName, privateKey, method, path)
                }.getOrNull()

                if (jwt != null) {
                    val newRequest = request.newBuilder()
                        .addHeader("Authorization", "Bearer $jwt")
                        .addHeader("Content-Type", "application/json")
                        .build()
                    return@Interceptor chain.proceed(newRequest)
                }
            }
            chain.proceed(request)
        }

        return OkHttpClient.Builder()
            .addInterceptor(jwtInterceptor)
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    @Provides
    @Singleton
    fun provideCoinbaseService(retrofit: Retrofit): CoinbaseService =
        retrofit.create(CoinbaseService::class.java)
}
