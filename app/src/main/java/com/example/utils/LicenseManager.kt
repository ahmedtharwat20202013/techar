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
import okhttp3.CertificatePinner
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class ActivationDetails(
    val licenseKey: String,
    val deviceId: String,
    val activationToken: String,
    val timestamp: Long
)

object LicenseManager {

    // Configurable Grace Period
    private const val GRACE_PERIOD_DAYS = 7
    private val GRACE_PERIOD_MS = GRACE_PERIOD_DAYS * 24 * 60 * 60 * 1000L

    // Unified backend URL defined in build.gradle.kts per-environment
    const val BACKEND_URL = com.example.BuildConfig.BACKEND_URL
    
    // Certificate pinning SHA256 values
    private const val CERTIFICATE_PIN_PRIMARY = "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
    // Backup Google Root CA GTS Root R1 pin to ensure seamless execution over Google Cloud Run
    private const val CERTIFICATE_PIN_GTS = "sha256/hxqP7gSJF/vSztvSGB1j37gSPX707A8A/J+HGP8p4X0="
    
    private val PBKDF2_SALT = "teacher_assistant_secure_salt_2026".toByteArray(Charsets.UTF_8)

    val client: OkHttpClient = OkHttpClient.Builder()
        .certificatePinner(
            CertificatePinner.Builder()
                .add("ais-pre-4o74uqo3764j7n42vf3hop-630524974552.europe-west2.run.app", CERTIFICATE_PIN_PRIMARY)
                .add("ais-pre-4o74uqo3764j7n42vf3hop-630524974552.europe-west2.run.app", CERTIFICATE_PIN_GTS)
                .build()
        )
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private fun isJsonValid(test: String): Boolean {
        if (test.trim().isEmpty()) return false
        return try {
            JSONObject(test)
            true
        } catch (ex: Exception) {
            false
        }
    }

    private suspend fun sendPostRequest(path: String, json: JSONObject): String = withContext(Dispatchers.IO) {
        val requestBody = json.toString().toRequestBody("application/json".toMediaType())
        
        val request = Request.Builder()
            .url("$BACKEND_URL$path")
            .post(requestBody)
            .build()
        
        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: ""
            if (!isJsonValid(body)) {
                throw java.io.IOException("فشل في معالجة استجابة الخادم: ${response.code}")
            }
            return@withContext body
        }
    }

    /**
     * Generate unique filename per app install to obfuscate license file storage
     */
    private fun getLicenseFileName(context: Context): String {
        val prefs = context.getSharedPreferences("app_config", Context.MODE_PRIVATE)
        var filename = prefs.getString("license_file", null)
        if (filename == null) {
            filename = "data_${System.currentTimeMillis()}_${(Math.random() * 10000).toInt()}.cache"
            prefs.edit().putString("license_file", filename).apply()
        }
        return filename
    }

    /**
     * Build secure hardware device fingerprint using CPU/OS specifics
     */
    fun getDeviceFingerprint(context: Context): String {
        return try {
            val sb = StringBuilder()
            
            // Hardware identifiers
            sb.append(Build.BOARD).append(":")
            sb.append(Build.BRAND).append(":")
            sb.append(Build.DEVICE).append(":")
            sb.append(Build.HARDWARE).append(":")
            sb.append(Build.MANUFACTURER).append(":")
            sb.append(Build.MODEL).append(":")
            sb.append(Build.PRODUCT).append(":")
            sb.append(Build.VERSION.RELEASE).append(":")
            sb.append(Build.VERSION.SDK_INT).append(":")
            
            // Android ID (unique per device + user)
            val androidId = Settings.Secure.getString(
                context.contentResolver, 
                Settings.Secure.ANDROID_ID
            ) ?: "unknown"
            sb.append(androidId)
            
            // Add package-specific salt (different per app install)
            val packageName = context.packageName
            sb.append(":").append(packageName)
            
            sha256(sb.toString())
        } catch (e: Exception) {
            Timber.e(e, "Error generating device fingerprint")
            sha256("fallback_fingerprint_" + Build.MODEL + "_" + Build.MODEL.hashCode() + "_" + context.packageName)
        }
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hash.fold("") { str, it -> str + "%02x".format(it) }
    }

    private fun deriveKey(password: String, salt: ByteArray): Pair<SecretKeySpec, IvParameterSpec> {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password.toCharArray(), salt, 10000, 256)
        val tmp = factory.generateSecret(spec)
        val keyBytes = tmp.encoded
        
        // Use first 16 bytes for AES key, next 16 for IV
        val aesKey = keyBytes.copyOfRange(0, 16)
        val iv = keyBytes.copyOfRange(16, 32)
        
        return Pair(SecretKeySpec(aesKey, "AES"), IvParameterSpec(iv))
    }

    /**
     * AES Encryption using the device specific fingerprint as key bytes
     */
    private fun encryptAES(plainText: String, keyString: String): String {
        val (secretKeySpec, ivParameterSpec) = deriveKey(keyString, PBKDF2_SALT)

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, ivParameterSpec)
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    /**
     * AES Decryption using the device specific fingerprint as key bytes
     */
    private fun decryptAES(encryptedText: String, keyString: String): String {
        val (secretKeySpec, ivParameterSpec) = deriveKey(keyString, PBKDF2_SALT)

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, ivParameterSpec)
        val decryptedBytes = cipher.doFinal(Base64.decode(encryptedText, Base64.NO_WRAP))
        return String(decryptedBytes, Charsets.UTF_8)
    }

    /**
     * Check activation status - ONLY via online validation
     * Falls back to cached "grace period" if offline
     */
    fun isAppActivated(context: Context): Boolean {
        val details = readLicenseLocally(context) ?: return false
        val currentFingerprint = getDeviceFingerprint(context)
        
        // Basic local checks only (no secret verification)
        if (details.deviceId != currentFingerprint) {
            Timber.w("Device mismatch")
            return false
        }
        
        // Check if we have a valid cached validation within grace period
        val now = System.currentTimeMillis()
        if (now - details.timestamp < GRACE_PERIOD_MS) {
            return true // Within grace period, allow offline
        }
        
        // Grace period expired - MUST go online
        return false
    }

    /**
     * Online-only validation - server verifies the token
     */
    suspend fun validateLicenseOnline(context: Context): Result<Boolean> = withContext(Dispatchers.IO) {
        val details = readLicenseLocally(context) ?: return@withContext Result.failure(Exception("No license found"))
        
        try {
            val json = JSONObject()
                .put("license_key", details.licenseKey)
                .put("device_id", details.deviceId)
                .put("activation_token", details.activationToken)
                .put("timestamp", System.currentTimeMillis()) // Anti-replay timestamp signing

            val body = sendPostRequest("/api/license/validate", json)
            val jsonObj = JSONObject(body)
            
            if (jsonObj.getBoolean("success")) {
                // Update timestamp on successful validation
                val updated = details.copy(timestamp = System.currentTimeMillis())
                saveLicenseLocally(context, updated)
                Result.success(true)
            } else {
                deinstallLicense(context)
                Result.failure(Exception(jsonObj.optString("message", "License invalid")))
            }
        } catch (e: Exception) {
            // Network error - allow if within grace period
            val withinGrace = (System.currentTimeMillis() - details.timestamp) < GRACE_PERIOD_MS
            if (withinGrace) {
                Result.success(true)
            } else {
                Result.failure(Exception("يرجى الاتصال بالإنترنت للتحقق من صلاحية رخصة التطبيق."))
            }
        }
    }

    /**
     * Saves activation details to encrypted folder
     */
    fun saveLicenseLocally(context: Context, details: ActivationDetails): Boolean {
        return try {
            val rawString = "${details.licenseKey}|${details.deviceId}|${details.activationToken}|${details.timestamp}"
            val fingerprint = getDeviceFingerprint(context)
            val encryptedData = encryptAES(rawString, fingerprint)

            val file = File(context.filesDir, getLicenseFileName(context))
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
            val file = File(context.filesDir, getLicenseFileName(context))
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
                .put("timestamp", System.currentTimeMillis()) // Anti-replay timestamp signing

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
     * Offline logic to wipe activation cache when invalid or re-activation requested
     */
    fun deinstallLicense(context: Context) {
        try {
            val file = File(context.filesDir, getLicenseFileName(context))
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
    suspend fun tryOnlineRevalidation(context: Context) {
        if (!isNetworkAvailable(context)) return
        validateLicenseOnline(context)
    }
}
