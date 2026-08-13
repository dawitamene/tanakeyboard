package com.addiyon.keyboard.ai

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object AiServiceFactory {
    const val BASE_URL = "https://api.textrevamp.com/"
    private const val TIMEOUT_SECONDS = 30L
    private const val COMPLETION_TIMEOUT_MILLISECONDS = 12_000L

    fun create(baseUrl: String = BASE_URL, debug: Boolean = false): AiApi {
        return create(
            baseUrl = baseUrl,
            debug = debug,
            timeout = TIMEOUT_SECONDS,
            timeoutUnit = TimeUnit.SECONDS,
            retryOnConnectionFailure = true
        )
    }

    fun createCompletion(baseUrl: String = BASE_URL, debug: Boolean = false): AiApi {
        return create(
            baseUrl = baseUrl,
            debug = debug,
            timeout = COMPLETION_TIMEOUT_MILLISECONDS,
            timeoutUnit = TimeUnit.MILLISECONDS,
            retryOnConnectionFailure = false
        )
    }

    private fun create(
        baseUrl: String,
        debug: Boolean,
        timeout: Long,
        timeoutUnit: TimeUnit,
        retryOnConnectionFailure: Boolean
    ): AiApi {
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        val clientBuilder = OkHttpClient.Builder()
            .connectTimeout(timeout, timeoutUnit)
            .readTimeout(timeout, timeoutUnit)
            .writeTimeout(timeout, timeoutUnit)
            .callTimeout(timeout, timeoutUnit)
            .retryOnConnectionFailure(retryOnConnectionFailure)
        if (debug) {
            val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
            clientBuilder.addInterceptor(logging)
        }
        val client = clientBuilder.build()
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
        return retrofit.create(AiApi::class.java)
    }
}
