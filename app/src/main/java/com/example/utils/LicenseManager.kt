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
     * Parses ISO-8601 date string to epoch milliseconds.
     */
    private fun parseDate(dateStr: String?): Long? {
        if (dateStr.isNullOrBlank()) return null
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                java.time.Instant.parse(dateStr).toEpochMilli()
            } else {
                val format = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
                format.timeZone = java.util.TimeZone.getTimeZone("UTC")
                format.parse(dateStr)?.time
            }
        } catch (e: Exception) {
            try {
                val format = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
                format.timeZone = java.util.TimeZone.getTimeZone("UTC")
                format.parse(dateStr)?.time
            } catch (e2: Exception) {
                Timber.e(e2, "Failed to parse date: $dateStr")
                null
            }
        }
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
                if (responseBody != null && responseBody.valid) {
                    val parsedExpiresAt = parseDate(responseBody.expires_at)
                    // Update last check timestamp locally and save refreshed info if returned
                    val updatedData = licenseData.copy(
                        activatedAt = System.currentTimeMillis(),
                        expiresAt = parsedExpiresAt ?: licenseData.expiresAt,
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
                if (responseBody != null && responseBody.valid) {
                    val userName = responseBody.user_name ?: "مستخدم مفعل"
                    val parsedExpiresAt = parseDate(responseBody.expires_at)
                    
                    // Save to secure local storage (EncryptedSharedPreferences)
                    val licenseData = SecureStorage.LicenseData(
                        licenseKey = cleanKey,
                        deviceFingerprint = deviceFingerprint,
                        activationToken = "valid", // Token string used inside local security logic
                        activatedAt = System.currentTimeMillis(),
                        expiresAt = parsedExpiresAt,
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
                        expiresAt = parsedExpiresAt,
                        userName = userName
                    )
                    Result.success(details)
                } else {
                    val serverErr = responseBody?.error
                    val msg = when (serverErr) {
                        "KEY_NOT_FOUND" -> "رمز التفعيل غير صحيح. يرجى التأكد وإعادة المحاولة."
                        "KEY_ALREADY_USED" -> "عفواً، رمز التفعيل هذا مستخدم بالفعل على جهاز آخر ولا يمكن تفعيله على أكثر من جهاز."
                        "KEY_EXPIRED" -> "رمز التفعيل هذا منتهي الصلاحية."
                        else -> responseBody?.message ?: "رمز التفعيل غير صالح أو مستخدم من قبل."
                    }
                    Result.failure(Exception(msg))
                }
            } else {
                val errorBody = response.errorBody()?.string()
                var serverMsg: String? = null
                var serverErrCode: String? = null
                if (!errorBody.isNullOrBlank()) {
                    try {
                        val jsonObj = org.json.JSONObject(errorBody)
                        serverMsg = jsonObj.optString("message", null)
                        serverErrCode = jsonObj.optString("error", null)
                    } catch (e: Exception) {
                        // ignore
                    }
                }

                val errorMsg = when {
                    serverErrCode == "KEY_NOT_FOUND" || response.code() == 404 -> 
                        "رمز التفعيل غير صحيح. يرجى التأكد وإعادة المحاولة."
                    serverErrCode == "KEY_ALREADY_USED" || response.code() == 403 -> 
                        "عفواً، رمز التفعيل هذا مستخدم بالفعل على جهاز آخر ولا يمكن تفعيله على أكثر من جهاز."
                    serverErrCode == "KEY_EXPIRED" -> 
                        "رمز التفعيل هذا منتهي الصلاحية."
                    response.code() == 429 -> 
                        "لقد تجاوزت الحد المسموح به من المحاولات (طلب زائد). يرجى الانتظار والمحاولة لاحقاً."
                    response.code() >= 500 -> 
                        "خطأ داخلي بالخادم (كود ${response.code()}) أثناء معالجة طلبك. يرجى إعادة المحاولة لاحقاً."
                    else -> 
                        serverMsg ?: "فشلت عملية التفعيل: كود ${response.code()}"
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
