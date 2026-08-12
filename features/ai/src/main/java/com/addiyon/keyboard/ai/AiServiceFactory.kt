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

    fun create(baseUrl: String = BASE_URL, debug: Boolean = false): AiApi {
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        val clientBuilder = OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
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
