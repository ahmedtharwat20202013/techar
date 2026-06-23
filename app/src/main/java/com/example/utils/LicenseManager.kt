package com.example.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.provider.Settings
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

data class ActivationDetails(
    val licenseKey: String,
    val deviceId: String,
    val activationToken: String,
    val timestamp: Long
)

object LicenseManager {

    private const val LICENSE_FILE_NAME = "activation.dat"
    const val BACKEND_PUBLIC_URL = "https://ais-pre-4o74uqo3764j7n42vf3hop-630524974552.europe-west2.run.app"
    const val BACKEND_LOCAL_URL = "http://10.0.2.2:8080"
    private const val SIGNING_SECRET = "secure_secret_licensekey_signing_backend_2026"

    val client: OkHttpClient = try {
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })

        val sslContext = SSLContext.getInstance("SSL")
        sslContext.init(null, trustAllCerts, SecureRandom())
        
        OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    } catch (e: Exception) {
        OkHttpClient.Builder()
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    private suspend fun sendPostRequest(path: String, json: JSONObject): String = withContext(Dispatchers.IO) {
        val requestBody = json.toString().toRequestBody("application/json".toMediaType())
        val errors = StringBuilder()
        
        // 1. Try local emulator loopback URL first
        try {
            val request = Request.Builder()
                .url("$BACKEND_LOCAL_URL$path")
                .post(requestBody)
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (response.isSuccessful || response.code == 400 || response.code == 403 || response.code == 404) {
                    if (body.contains("success") || body.contains("message")) {
                        return@withContext body
                    }
                }
                errors.append("Local Server Error [Code ${response.code}]: ${if (body.length > 100) body.take(100) + "..." else body}\n")
            }
        } catch (e: Exception) {
            errors.append("Local Network Fail: ${e.message ?: e.toString()}\n")
            Timber.d("Local backend fail, trying public url...")
        }

        // 2. Try public cloud URL as secondary fallback path
        try {
            val request = Request.Builder()
                .url("$BACKEND_PUBLIC_URL$path")
                .post(requestBody)
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (response.isSuccessful || response.code == 400 || response.code == 403 || response.code == 404) {
                    if (body.contains("success") || body.contains("message")) {
                        return@withContext body
                    }
                }
                errors.append("Public Server Error [Code ${response.code}]: ${if (body.length > 100) body.take(100) + "..." else body}\n")
            }
        } catch (e: Exception) {
            errors.append("Public Network Fail: ${e.message ?: e.toString()}\n")
        }

        throw java.io.IOException(errors.toString())
    }

    /**
     * Build secure hardware device fingerprint using CPU/OS specifics
     */
    fun getDeviceFingerprint(context: Context): String {
        return try {
            val sb = StringBuilder()
            sb.append(Build.BOARD).append("|")
            sb.append(Build.BRAND).append("|")
            sb.append(Build.DEVICE).append("|")
            sb.append(Build.HARDWARE).append("|")
            sb.append(Build.MODEL).append("|")
            sb.append(Build.PRODUCT).append("|")
            sb.append(Build.VERSION.RELEASE).append("|")
            sb.append(Build.VERSION.SDK_INT).append("|")
            val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown_android_id"
            sb.append(androidId)
            
            sha256(sb.toString())
        } catch (e: Exception) {
            Timber.e(e, "Error generating device fingerprint")
            "fallback_fingerprint_" + Build.MODEL.hashCode()
        }
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hash.fold("") { str, it -> str + "%02x".format(it) }
    }

    private fun computeHmacSha256(payload: String, secret: String): String {
        val key = SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256")
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(key)
        val hash = mac.doFinal(payload.toByteArray(Charsets.UTF_8))
        return hash.fold("") { str, it -> str + "%02x".format(it) }
    }

    /**
     * AES Encryption using the device specific fingerprint as key bytes
     */
    private fun encryptAES(plainText: String, keyString: String): String {
        val keyBytes = keyString.substring(0, 16).toByteArray(Charsets.UTF_8)
        val ivBytes = keyString.substring(16, 32).toByteArray(Charsets.UTF_8)
        val secretKeySpec = SecretKeySpec(keyBytes, "AES")
        val ivParameterSpec = IvParameterSpec(ivBytes)

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, ivParameterSpec)
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    /**
     * AES Decryption using the device specific fingerprint as key bytes
     */
    private fun decryptAES(encryptedText: String, keyString: String): String {
        val keyBytes = keyString.substring(0, 16).toByteArray(Charsets.UTF_8)
        val ivBytes = keyString.substring(16, 32).toByteArray(Charsets.UTF_8)
        val secretKeySpec = SecretKeySpec(keyBytes, "AES")
        val ivParameterSpec = IvParameterSpec(ivBytes)

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, ivParameterSpec)
        val decryptedBytes = cipher.doFinal(Base64.decode(encryptedText, Base64.NO_WRAP))
        return String(decryptedBytes, Charsets.UTF_8)
    }

    /**
     * Check if offline/online license resides correctly on disk
     */
    fun isAppActivated(context: Context): Boolean {
        val details = readLicenseLocally(context) ?: return false
        val currentFingerprint = getDeviceFingerprint(context)

        // 1. Device ID matching
        if (details.deviceId != currentFingerprint) {
            Timber.w("Activation failed: Device fingerprint mismatch.")
            return false
        }

        // 2. Token match signature integrity check
        val expectedToken = computeHmacSha256("${details.licenseKey}:${details.deviceId}", SIGNING_SECRET)
        if (details.activationToken != expectedToken) {
            Timber.w("Activation failed: Activation token signature mismatch.")
            return false
        }

        return true
    }

    /**
     * Saves activation details to encrypted folder
     */
    fun saveLicenseLocally(context: Context, details: ActivationDetails): Boolean {
        return try {
            val rawString = "${details.licenseKey}|${details.deviceId}|${details.activationToken}|${details.timestamp}"
            val fingerprint = getDeviceFingerprint(context)
            val encryptedData = encryptAES(rawString, fingerprint)

            val file = File(context.filesDir, LICENSE_FILE_NAME)
            file.writeText(encryptedData)
            Timber.d("License successfully saved locally.")
            true
        } catch (e: Exception) {
            Timber.e(e, "Error writing activation file locally.")
            false
        }
    }

    /**
     * Reads activation details from encrypted folder
     */
    fun readLicenseLocally(context: Context): ActivationDetails? {
        return try {
            val file = File(context.filesDir, LICENSE_FILE_NAME)
            if (!file.exists()) return null

            val encryptedData = file.readText().trim()
            val fingerprint = getDeviceFingerprint(context)
            val decryptedData = decryptAES(encryptedData, fingerprint)

            val parts = decryptedData.split("|")
            if (parts.size >= 4) {
                ActivationDetails(
                    licenseKey = parts[0],
                    deviceId = parts[1],
                    activationToken = parts[2],
                    timestamp = parts[3].toLong()
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to decrypt/load local license record (probable hacking/tempering).")
            null
        }
    }

    /**
     * Decoupled server call to trigger license activation
     */
    suspend fun activateLicenseOnline(
        context: Context,
        licenseKey: String,
        userName: String
    ): Result<ActivationDetails> = withContext(Dispatchers.IO) {
        try {
            val deviceId = getDeviceFingerprint(context)
            val json = JSONObject()
                .put("license_key", licenseKey)
                .put("device_id", deviceId)
                .put("user_name", userName)

            val body = sendPostRequest("/api/license/activate", json)
            val jsonObj = JSONObject(body)

            if (jsonObj.getBoolean("success")) {
                val dataObj = jsonObj.getJSONObject("data")
                val details = ActivationDetails(
                    licenseKey = dataObj.getString("license_key"),
                    deviceId = dataObj.getString("device_id"),
                    activationToken = dataObj.getString("activation_token"),
                    timestamp = System.currentTimeMillis()
                )
                
                // Persistence
                saveLicenseLocally(context, details)
                Result.success(details)
            } else {
                val msg = if (jsonObj.has("message")) jsonObj.getString("message") else "رمز التفعيل غير صحيح"
                Result.failure(Exception(msg))
            }
        } catch (e: Exception) {
            Timber.e(e, "Network error during license activation")
            Result.failure(Exception("خطأ في الاتصال بالخادم: ${e.message ?: e.toString()}\nيرجى التحقق من اتصال الإنترنت والمحاولة مجدداً."))
        }
    }

    /**
     * Offline logic to wipe activation.dat when invalid or re-activation requested
     */
    fun deinstallLicense(context: Context) {
        try {
            val file = File(context.filesDir, LICENSE_FILE_NAME)
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            Timber.e(e, "Error erasing license registration")
        }
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
            true // fallback to assume network is active if permissions aren't declared fully or sandbox restrictions occur
        }
    }

    /**
     * Anti-crack Layer: Runs periodic revalidation online when network is present
     */
    suspend fun tryOnlineRevalidation(context: Context) = withContext(Dispatchers.IO) {
        if (!isNetworkAvailable(context)) return@withContext
        
        val details = readLicenseLocally(context) ?: return@withContext
        
        try {
            val json = JSONObject()
                .put("license_key", details.licenseKey)
                .put("device_id", details.deviceId)
                .put("activation_token", details.activationToken)

            val body = sendPostRequest("/api/license/validate", json)
            val jsonObj = JSONObject(body)
            if (jsonObj.getBoolean("success")) {
                // Success: Keep app active and update timestamp
                val updatedDetails = details.copy(timestamp = System.currentTimeMillis())
                saveLicenseLocally(context, updatedDetails)
                Timber.d("Periodic license validation succeeded and recorded.")
            } else {
                deinstallLicense(context)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to connect to license validation endpoint, skipping this turn.")
        }
    }
}
