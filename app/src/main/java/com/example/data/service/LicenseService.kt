package com.example.data.service

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class LicenseVerificationRequest(
    @Json(name = "customer_name") val customerName: String,
    @Json(name = "license_key") val licenseKey: String,
    @Json(name = "device_fingerprint") val deviceFingerprint: String,
    @Json(name = "android_id") val androidId: String,
    @Json(name = "device_model") val deviceModel: String,
    @Json(name = "manufacturer") val manufacturer: String
)

@JsonClass(generateAdapter = true)
data class LicenseVerificationResponse(
    @Json(name = "success") val success: Boolean = false,
    @Json(name = "message") val message: String? = null,
    @Json(name = "expire_date") val expireDate: String? = null,
    @Json(name = "status") val status: String? = null
)

interface LicenseApi {
    @POST("https://genertion-kay.ahmedtharwat20202013.workers.dev")
    suspend fun verifyLicense(
        @Body request: LicenseVerificationRequest
    ): LicenseVerificationResponse
}

object LicenseService {
    private const val BASE_URL = "https://genertion-kay.ahmedtharwat20202013.workers.dev/"

    private val api: LicenseApi by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()

        val moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()

        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(LicenseApi::class.java)
    }

    suspend fun verifyLicense(
        customerName: String,
        licenseKey: String,
        deviceFingerprint: String,
        androidId: String,
        deviceModel: String,
        manufacturer: String
    ): LicenseVerificationResponse = withContext(Dispatchers.IO) {
        api.verifyLicense(
            LicenseVerificationRequest(
                customerName = customerName,
                licenseKey = licenseKey,
                deviceFingerprint = deviceFingerprint,
                androidId = androidId,
                deviceModel = deviceModel,
                manufacturer = manufacturer
            )
        )
    }
}
