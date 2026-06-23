package com.example.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import timber.log.Timber

data class ActivationDetails(
    val licenseKey: String,
    val deviceId: String,
    val activationToken: String,
    val timestamp: Long
)

object LicenseManager {

    // Unified backend URL defined in BuildConfig
    const val BACKEND_URL = com.example.BuildConfig.BACKEND_URL

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    /**
     * Check if app is activated (offline validation)
     */
    fun isAppActivated(context: Context): Boolean {
        val licenseData = SecureStorage.decryptAndValidate(context) ?: return false
        
        // Expiration check
        licenseData.expiresAt?.let { expiresAt ->
            if (System.currentTimeMillis() > expiresAt) {
                Timber.w("License expired locally")
                SecureStorage.deleteLicense(context)
                return false
            }
        }
        
        return true
    }

    /**
     * Get activation details (if activated)
     */
    fun getActivationDetails(context: Context): SecureStorage.LicenseData? {
        return SecureStorage.decryptAndValidate(context)
    }

    /**
     * Online-only validation - server verifies the token
     */
    suspend fun validateLicenseOnline(context: Context): Result<Boolean> = withContext(Dispatchers.IO) {
        val licenseData = SecureStorage.decryptAndValidate(context)
            ?: return@withContext Result.failure(Exception("No local license found"))
        
        try {
            val json = JSONObject().apply {
                put("license_key", licenseData.licenseKey)
                put("device_id", licenseData.deviceFingerprint)
                put("activation_token", licenseData.activationToken)
                put("timestamp", System.currentTimeMillis())
            }
            
            val requestBody = json.toString().toRequestBody("application/json".toMediaType())
            
            val request = Request.Builder()
                .url("$BACKEND_URL/api/license/validate")
                .post(requestBody)
                .build()
            
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                
                if (!response.isSuccessful) {
                    val errorMsg = try {
                        JSONObject(body).optString("message", "Validation failed")
                    } catch (e: Exception) {
                        "Validation failed with code: ${response.code}"
                    }
                    if (response.code in 401..403) {
                        SecureStorage.deleteLicense(context)
                    }
                    return@withContext Result.failure(Exception(errorMsg))
                }
                
                val responseJson = JSONObject(body)
                if (responseJson.getBoolean("success")) {
                    // Update last check timestamp locally
                    val updatedData = licenseData.copy(
                        activatedAt = System.currentTimeMillis()
                    )
                    SecureStorage.encryptLicense(context, updatedData)
                    Result.success(true)
                } else {
                    SecureStorage.deleteLicense(context)
                    Result.failure(Exception(responseJson.optString("message", "Validation failed")))
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Validation error")
            // If offline, check if license is still valid locally
            if (isAppActivated(context)) {
                Result.success(true) // Allow offline use
            } else {
                Result.failure(e)
            }
        }
    }

    /**
     * First-time activation with Neon PostgreSQL
     */
    suspend fun activateLicenseOnline(
        context: Context,
        licenseKey: String,
        userName: String
    ): Result<ActivationDetails> = withContext(Dispatchers.IO) {
        try {
            val deviceFingerprint = DeviceUtils.getDeviceFingerprint(context)
            
            val json = JSONObject().apply {
                put("license_key", licenseKey)
                put("device_id", deviceFingerprint)
                put("user_name", userName)
                put("timestamp", System.currentTimeMillis())
            }
            
            val requestBody = json.toString().toRequestBody("application/json".toMediaType())
            
            val request = Request.Builder()
                .url("$BACKEND_URL/api/license/activate")
                .post(requestBody)
                .build()
            
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                
                if (!response.isSuccessful) {
                    val errorMsg = try {
                        JSONObject(body).optString("message", "فشلت عملية التفعيل")
                    } catch (e: Exception) {
                        "خطأ من الخادم برقم: ${response.code}"
                    }
                    return@withContext Result.failure(Exception(errorMsg))
                }
                
                val responseJson = JSONObject(body)
                if (!responseJson.getBoolean("success")) {
                    val msg = responseJson.optString("message", "فشلت عملية التفعيل")
                    return@withContext Result.failure(Exception(msg))
                }
                
                val data = responseJson.getJSONObject("data")
                val activationToken = data.getString("activation_token")
                val expiresAt = if (data.has("expires_at") && !data.isNull("expires_at")) {
                    data.getLong("expires_at")
                } else null
                
                // Save to secure local storage
                val licenseData = SecureStorage.LicenseData(
                    licenseKey = licenseKey,
                    deviceFingerprint = deviceFingerprint,
                    activationToken = activationToken,
                    activatedAt = System.currentTimeMillis(),
                    expiresAt = expiresAt,
                    userName = userName
                )
                
                if (!SecureStorage.encryptLicense(context, licenseData)) {
                    return@withContext Result.failure(
                        Exception("فشل حفظ الرخصة محلياً بجهازك.")
                    )
                }
                
                val details = ActivationDetails(
                    licenseKey = licenseKey,
                    deviceId = deviceFingerprint,
                    activationToken = activationToken,
                    timestamp = System.currentTimeMillis()
                )
                Result.success(details)
            }
        } catch (e: Exception) {
            Timber.e(e, "Activation error")
            Result.failure(Exception("خطأ في الاتصال بالخادم: ${e.message ?: e.toString()}\nيرجى التحقق من اتصال الإنترنت والمحاولة مجدداً."))
        }
    }

    /**
     * Offline logic to wipe activation cache when invalid or re-activation requested
     */
    fun deinstallLicense(context: Context) {
        SecureStorage.deleteLicense(context)
    }

    /**
     * Checks if internet connectivity is available
     */
    fun isNetworkAvailable(context: Context): Boolean {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val nw = connectivityManager.activeNetwork ?: return false
            val actNw = connectivityManager.getNetworkCapabilities(nw) ?: return false
            actNw.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    actNw.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                    actNw.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        } catch (e: Exception) {
            true // fallback to assume network is active if sandbox restrictions occur
        }
    }

    /**
     * Anti-crack Layer: Runs periodic revalidation online when network is present
     */
    suspend fun tryOnlineRevalidation(context: Context) {
        if (!isNetworkAvailable(context)) return
        validateLicenseOnline(context)
    }
}
