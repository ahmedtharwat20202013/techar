package com.example.data.service

import android.util.Log
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
    @Json(name = "license_key") val licenseKey: String,
    @Json(name = "device_fingerprint") val deviceFingerprint: String,
    @Json(name = "android_id") val androidId: String,
    @Json(name = "device_model") val deviceModel: String,
    @Json(name = "manufacturer") val manufacturer: String,
    @Json(name = "brand") val brand: String = "",
    @Json(name = "android_version") val androidVersion: String = "",
    @Json(name = "app_version") val appVersion: String = ""
)

@JsonClass(generateAdapter = true)
data class LicenseVerificationResponse(
    @Json(name = "success") val success: Boolean = false,
    @Json(name = "message") val message: String? = null,
    @Json(name = "expire_date") val expireDate: String? = null,
    @Json(name = "start_date") val startDate: String? = null,
    @Json(name = "status") val status: String? = null,
    @Json(name = "max_devices") val maxDevices: Int? = null,
    @Json(name = "customer_name") val customerName: String? = null,
    @Json(name = "license_id") val licenseId: String? = null,
    @Json(name = "plan_type") val planType: String? = null
)

interface LicenseApi {
    @POST("https://genertion-kay.ahmedtharwat20202013.workers.dev")
    suspend fun verifyLicense(
        @Body request: LicenseVerificationRequest
    ): LicenseVerificationResponse
}

object LicenseService {
    private const val TAG = "LicenseService"
    private const val BASE_URL = "https://genertion-kay.ahmedtharwat20202013.workers.dev/"

    private val moshi: Moshi by lazy {
        Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }

    private val api: LicenseApi by lazy {
        val logging = HttpLoggingInterceptor { message ->
            Log.d("LicenseService_Http", message)
        }.apply {
            level = HttpLoggingInterceptor.Level.NONE
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()

        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(LicenseApi::class.java)
    }

    suspend fun verifyLicense(
        licenseKey: String,
        deviceFingerprint: String,
        androidId: String,
        deviceModel: String,
        manufacturer: String,
        brand: String = "",
        androidVersion: String = "",
        appVersion: String = ""
    ): LicenseVerificationResponse = withContext(Dispatchers.IO) {
        val request = LicenseVerificationRequest(
            licenseKey = licenseKey,
            deviceFingerprint = deviceFingerprint,
            androidId = androidId,
            deviceModel = deviceModel,
            manufacturer = manufacturer,
            brand = brand,
            androidVersion = androidVersion,
            appVersion = appVersion
        )

        Log.d(TAG, "Outgoing License Request initiated for device: ${request.deviceModel}")

        try {
            val response = api.verifyLicense(request)
            Log.d(TAG, "Received License Response (HTTP 200): success=${response.success}, message=${response.message}, expireDate=${response.expireDate}, status=${response.status}")
            response
        } catch (e: retrofit2.HttpException) {
            val rawError = e.response()?.errorBody()?.string()
            Log.e(TAG, "HTTP Exception Status Code ${e.code()}: Raw Body = '$rawError'", e)

            if (!rawError.isNullOrBlank()) {
                try {
                    val adapter = moshi.adapter(LicenseVerificationResponse::class.java)
                    val parsedError = adapter.fromJson(rawError)
                    if (parsedError != null) {
                        Log.d(TAG, "Successfully parsed error response JSON: success=${parsedError.success}, message=${parsedError.message}")
                        return@withContext parsedError
                    }
                } catch (parseEx: Exception) {
                    Log.e(TAG, "Failed to parse error response body as LicenseVerificationResponse", parseEx)
                }
            }
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "License verification network/server exception: ${e.message}", e)
            throw e
        }
    }
}
