package com.cointrader.app.di

import com.cointrader.app.data.api.CoinbaseAuthService
import com.cointrader.app.data.api.CoinbaseService
import com.cointrader.app.data.security.SecureStorage
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val COINBASE_BASE_URL = "https://api.coinbase.com/"

    // ── Auth Retrofit (no auth header — used only to exchange/refresh tokens) ──

    @Provides
    @Singleton
    @Named("auth")
    fun provideAuthRetrofit(): Retrofit =
        Retrofit.Builder()
            .baseUrl(COINBASE_BASE_URL)
            .client(
                OkHttpClient.Builder()
                    .addInterceptor(loggingInterceptor())
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build()
            )
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    @Provides
    @Singleton
    fun provideCoinbaseAuthService(@Named("auth") retrofit: Retrofit): CoinbaseAuthService =
        retrofit.create(CoinbaseAuthService::class.java)

    // ── API Retrofit (injects Bearer token from SecureStorage on every call) ──

    @Provides
    @Singleton
    @Named("api")
    fun provideApiRetrofit(secureStorage: SecureStorage): Retrofit {
        val bearerInterceptor = Interceptor { chain ->
            // runBlocking is safe here because OkHttp runs on an IO thread
            val token = runBlocking {
                if (secureStorage.isTokenExpired()) null  // Caller handles re-auth
                else secureStorage.getAccessToken()
            }
            val request = chain.request().newBuilder().apply {
                if (token != null) {
                    header("Authorization", "Bearer $token")
                }
                header("Content-Type", "application/json")
            }.build()
            chain.proceed(request)
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(bearerInterceptor)
            .addInterceptor(loggingInterceptor())
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(COINBASE_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideCoinbaseService(@Named("api") retrofit: Retrofit): CoinbaseService =
        retrofit.create(CoinbaseService::class.java)

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun loggingInterceptor() = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }
}
