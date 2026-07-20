package com.example.data.service

import android.util.Log
import com.example.BuildConfig
import com.example.data.model.DeviceHiddenData
import com.example.data.model.SubscriptionLicense
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
import retrofit2.http.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// --- Supabase Data Models for Retrofit/Moshi Serialization ---

@JsonClass(generateAdapter = true)
data class SupabaseLicense(
    @Json(name = "id") val id: Int?,
    @Json(name = "customer_name") val customerName: String,
    @Json(name = "license_key") val licenseKey: String,
    @Json(name = "status") val status: String,
    @Json(name = "expire_date") val expireDate: String,
    @Json(name = "device_fingerprint") val deviceFingerprint: String?,
    @Json(name = "android_id") val androidId: String? = null,
    @Json(name = "device_model") val deviceModel: String? = null,
    @Json(name = "manufacturer") val manufacturer: String? = null,
    @Json(name = "brand") val brand: String? = null,
    @Json(name = "android_version") val androidVersion: String? = null,
    @Json(name = "app_version") val appVersion: String? = null,
    @Json(name = "activated_at") val activatedAt: String? = null,
    @Json(name = "last_check") val lastCheck: String? = null
)

@JsonClass(generateAdapter = true)
data class SupabaseActivationInsert(
    @Json(name = "license_id") val licenseId: Int?,
    @Json(name = "device_fingerprint") val deviceFingerprint: String,
    @Json(name = "android_id") val androidId: String,
    @Json(name = "device_model") val deviceModel: String,
    @Json(name = "manufacturer") val manufacturer: String,
    @Json(name = "brand") val brand: String,
    @Json(name = "android_version") val androidVersion: String,
    @Json(name = "app_version") val appVersion: String,
    @Json(name = "result") val result: String,
    @Json(name = "activated_at") val activatedAt: String
)

// --- Retrofit Service Interface ---

interface SupabaseApi {
    @GET("rest/v1/licenses")
    suspend fun getLicense(
        @Header("apikey") apiKey: String,
        @Header("Authorization") auth: String,
        @Query("customer_name") customerNameQuery: String,
        @Query("license_key") licenseKeyQuery: String,
        @Query("select") select: String = "*"
    ): List<SupabaseLicense>

    @PATCH("rest/v1/licenses")
    suspend fun updateDeviceFingerprint(
        @Header("apikey") apiKey: String,
        @Header("Authorization") auth: String,
        @Header("Prefer") prefer: String = "return=representation",
        @Query("id") idQuery: String,
        @Body updates: Map<String, String?>
    ): List<SupabaseLicense>

    @POST("rest/v1/activations")
    suspend fun insertActivationAttempt(
        @Header("apikey") apiKey: String,
        @Header("Authorization") auth: String,
        @Body activation: SupabaseActivationInsert
    ): List<Map<String, Any>>
}

// --- Singleton Service implementation ---

object PostgresDatabaseService {
    private const val TAG = "PostgresDatabaseService"

    private var lastUrl: String? = null
    private var cachedApi: SupabaseApi? = null

    /**
     * Loads the active Supabase URL and Key from compile-time generated BuildConfig.
     */
    fun loadSupabaseConfig(): Pair<String, String> {
        val url = BuildConfig.SUPABASE_URL
        val key = BuildConfig.SUPABASE_ANON_KEY
        Log.d(TAG, "Loaded Supabase Configuration from BuildConfig: URL=$url")
        return Pair(url, key)
    }

    @Synchronized
    private fun getApi(): SupabaseApi {
        val (url, _) = loadSupabaseConfig()
        if (cachedApi == null || lastUrl != url) {
            val baseUrl = if (url.endsWith("/")) url else "$url/"
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
            val client = OkHttpClient.Builder()
                .addInterceptor(logging)
                .build()

            val moshi = Moshi.Builder()
                .addLast(KotlinJsonAdapterFactory())
                .build()

            cachedApi = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(SupabaseApi::class.java)
            lastUrl = url
        }
        return cachedApi!!
    }

    /**
     * Finds a license by customer name and license key from Supabase licenses table.
     */
    suspend fun getLicense(customerName: String, licenseKey: String): SubscriptionLicense? = withContext(Dispatchers.IO) {
        try {
            val (_, anonKey) = loadSupabaseConfig()
            val apiService = getApi()

            val response = apiService.getLicense(
                apiKey = anonKey,
                auth = "Bearer $anonKey",
                customerNameQuery = "eq.${customerName.trim()}",
                licenseKeyQuery = "eq.${licenseKey.trim()}"
            )

            if (response.isNotEmpty()) {
                val item = response[0]
                return@withContext SubscriptionLicense(
                    id = item.id,
                    customerName = item.customerName,
                    licenseKey = item.licenseKey,
                    status = item.status,
                    expireDate = item.expireDate,
                    deviceFingerprint = item.deviceFingerprint,
                    androidId = item.androidId,
                    deviceModel = item.deviceModel,
                    manufacturer = item.manufacturer,
                    brand = item.brand,
                    androidVersion = item.androidVersion,
                    appVersion = item.appVersion,
                    activatedAt = item.activatedAt,
                    lastCheck = item.lastCheck
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching license from Supabase", e)
            throw e
        }
        return@withContext null
    }

    /**
     * Binds the current device fingerprint metadata to the subscription record if it was previously NULL.
     */
    suspend fun updateDeviceFingerprint(
        customerName: String,
        licenseKey: String,
        hiddenData: DeviceHiddenData
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val license = getLicense(customerName, licenseKey) ?: return@withContext false
            val licenseId = license.id ?: return@withContext false

            val (_, anonKey) = loadSupabaseConfig()
            val apiService = getApi()

            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            val nowStr = sdf.format(Date())

            val updates = mapOf(
                "device_fingerprint" to hiddenData.fingerprint,
                "android_id" to hiddenData.androidId,
                "device_model" to hiddenData.model,
                "manufacturer" to hiddenData.manufacturer,
                "brand" to hiddenData.brand,
                "android_version" to hiddenData.androidVersion,
                "app_version" to hiddenData.appVersion,
                "activated_at" to nowStr,
                "last_check" to nowStr
            )

            val result = apiService.updateDeviceFingerprint(
                apiKey = anonKey,
                auth = "Bearer $anonKey",
                idQuery = "eq.$licenseId",
                updates = updates
            )

            return@withContext result.isNotEmpty()
        } catch (e: Exception) {
            Log.e(TAG, "Error updating device fingerprint on Supabase", e)
            throw e
        }
    }

    /**
     * Saves every activation attempt into the activations table on Supabase.
     */
    suspend fun saveActivationAttempt(
        licenseId: Int?,
        hiddenData: DeviceHiddenData,
        result: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val (_, anonKey) = loadSupabaseConfig()
            val apiService = getApi()

            val attempt = SupabaseActivationInsert(
                licenseId = licenseId,
                deviceFingerprint = hiddenData.fingerprint,
                androidId = hiddenData.androidId,
                deviceModel = hiddenData.model,
                manufacturer = hiddenData.manufacturer,
                brand = hiddenData.brand,
                androidVersion = hiddenData.androidVersion,
                appVersion = hiddenData.appVersion,
                result = result,
                activatedAt = hiddenData.currentDate
            )

            apiService.insertActivationAttempt(
                apiKey = anonKey,
                auth = "Bearer $anonKey",
                activation = attempt
            )
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Error logging activation attempt to Supabase", e)
            return@withContext false
        }
    }
}
