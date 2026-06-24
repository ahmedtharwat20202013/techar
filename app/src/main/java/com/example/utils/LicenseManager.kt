package com.example.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.data.LicenseApi
import com.example.data.LicenseRequest
import com.example.data.LicenseResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import timber.log.Timber

data class ActivationDetails(
    val licenseKey: String,
    val deviceId: String,
    val timestamp: Long,
    val expiresAt: Long? = null,
    val userName: String? = null
)

object LicenseManager {

    // Unified backend URL defined in BuildConfig
    const val BACKEND_URL = com.example.BuildConfig.BACKEND_URL
    const val PRODUCT_ID = "techar_app"

    val client: OkHttpClient by lazy {
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)

        val hostName = try {
            java.net.URL(BACKEND_URL).host
        } catch (e: Exception) {
            null
        }

        // Standard SSL system trust store handles validation for run.app (HTTPS)

        builder.build()
    }

    val retrofit: Retrofit by lazy {
        val baseUrlWithSlash = if (BACKEND_URL.endsWith("/")) BACKEND_URL else "$BACKEND_URL/"
        Retrofit.Builder()
            .baseUrl(baseUrlWithSlash)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
    }

    val api: LicenseApi by lazy {
        retrofit.create(LicenseApi::class.java)
    }

    /**
     * Check if app is activated (offline validation)
     */
    fun isAppActivated(context: Context): Boolean {
        val licenseData = SecureStorage.decryptAndValidate(context) ?: return false
        
        // Expiration check
        licenseData.expiresAt?.let { expiresAt ->
            if (expiresAt > 0 && System.currentTimeMillis() > expiresAt) {
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
     * Online-only validation - server verifies the token or checks if the key remains valid
     */
    suspend fun validateLicenseOnline(context: Context): Result<Boolean> = withContext(Dispatchers.IO) {
        val licenseData = SecureStorage.decryptAndValidate(context)
            ?: return@withContext Result.failure(Exception("No local license found"))
        
        try {
            val request = LicenseRequest(
                license_key = licenseData.licenseKey,
                device_id = licenseData.deviceFingerprint,
                product_id = PRODUCT_ID
            )
            
            val response = api.validateLicense(request)
            if (response.isSuccessful) {
                val responseBody = response.body()
                if (responseBody != null && responseBody.success) {
                    // Update last check timestamp locally and save refreshed info if returned
                    val updatedData = licenseData.copy(
                        activatedAt = System.currentTimeMillis(),
                        expiresAt = responseBody.expires_at ?: licenseData.expiresAt,
                        userName = responseBody.user_name ?: licenseData.userName
                    )
                    SecureStorage.encryptLicense(context, updatedData)
                    Result.success(true)
                } else {
                    val errMsg = responseBody?.message ?: "فشلت عملية التحقق"
                    SecureStorage.deleteLicense(context)
                    Result.failure(Exception(errMsg))
                }
            } else {
                val errorMsg = "فشل في التحقق: كود ${response.code()}"
                if (response.code() in 400..403) {
                    SecureStorage.deleteLicense(context)
                }
                Result.failure(Exception(errorMsg))
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
     * First-time activation with Neon PostgreSQL backend via Retrofit
     */
    suspend fun activateLicenseOnline(
        context: Context,
        licenseKey: String
    ): Result<ActivationDetails> = withContext(Dispatchers.IO) {
        try {
            val deviceFingerprint = DeviceUtils.getDeviceFingerprint(context)
            val cleanKey = licenseKey.trim().uppercase()
            
            val request = LicenseRequest(
                license_key = cleanKey,
                device_id = deviceFingerprint,
                product_id = PRODUCT_ID
            )
            
            val response = api.validateLicense(request)
            if (response.isSuccessful) {
                val responseBody = response.body()
                if (responseBody != null && responseBody.success) {
                    val userName = responseBody.user_name ?: "مستخدم مفعل"
                    val expiresAt = responseBody.expires_at
                    
                    // Save to secure local storage (EncryptedSharedPreferences)
                    val licenseData = SecureStorage.LicenseData(
                        licenseKey = cleanKey,
                        deviceFingerprint = deviceFingerprint,
                        activationToken = "valid", // Token string used inside local security logic
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
                        licenseKey = cleanKey,
                        deviceId = deviceFingerprint,
                        timestamp = System.currentTimeMillis(),
                        expiresAt = expiresAt,
                        userName = userName
                    )
                    Result.success(details)
                } else {
                    val msg = responseBody?.message ?: "رمز التفعيل غير صالح أو مستخدم من قبل."
                    Result.failure(Exception(msg))
                }
            } else {
                val errorBody = response.errorBody()?.string()
                val errorMsg = if (!errorBody.isNullOrBlank()) {
                    try {
                        org.json.JSONObject(errorBody).optString("message", "فشلت عملية التفعيل")
                    } catch (e: Exception) {
                        "خطأ من الخادم برقم: ${response.code()}"
                    }
                } else {
                    "خطأ من الخادم برقم: ${response.code()}"
                }
                Result.failure(Exception(errorMsg))
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
