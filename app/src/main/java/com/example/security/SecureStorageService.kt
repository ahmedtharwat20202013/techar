package com.example.security

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import android.util.Base64
import android.util.Log
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * SecureStorageService handles storing and retrieving encrypted subscription credentials.
 * 
 * Security Features:
 * - Stores encrypted ciphertext and IV in SharedPreferences.
 * - Computes and verifies an HMAC-SHA256 signature calculated over (AndroidId + CipherText + IV).
 * - Prevents copying preference files from one device to another (anti-cloning).
 */
class SecureStorageService(private val context: Context) {
    private val TAG = "SecureStorageService"
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "secure_encrypted_license_prefs"
        private const val KEY_ENCRYPTED_DATA = "encrypted_license_data"
        private const val KEY_IV = "license_iv"
        private const val KEY_HMAC_SIGNATURE = "data_hmac_signature"
        private const val KEY_IS_ACTIVATED = "is_license_activated"
        private const val HMAC_ALGORITHM = "HmacSHA256"
    }

    /**
     * Gets unique device hardware binding identifier (Android ID).
     */
    private fun getDeviceBindingId(): String {
        return try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "UNKNOWN_DEVICE_ID"
        } catch (e: Exception) {
            "FALLBACK_DEVICE_ID"
        }
    }

    /**
     * Computes HMAC-SHA256 signature across the device ID and encrypted payload.
     * Prevents data tampering and unauthorized preference file duplication across devices.
     */
    private fun computeHmacSignature(cipherText: String, iv: String): String {
        return try {
            val deviceId = getDeviceBindingId()
            val rawDataToSign = "$deviceId|$cipherText|$iv"
            
            // Use static salt combined with device id to form HMAC key
            val secretKeyBytes = "TeacherSecureSalt_2026_$deviceId".toByteArray(Charsets.UTF_8)
            val secretKey = SecretKeySpec(secretKeyBytes, HMAC_ALGORITHM)

            val mac = Mac.getInstance(HMAC_ALGORITHM)
            mac.init(secretKey)
            val hmacBytes = mac.doFinal(rawDataToSign.toByteArray(Charsets.UTF_8))
            Base64.encodeToString(hmacBytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "Error computing HMAC signature", e)
            ""
        }
    }

    /**
     * Saves encrypted license payload and its HMAC signature to SharedPreferences.
     */
    fun saveEncryptedLicense(payload: EncryptedPayload): Boolean {
        return try {
            val hmacSignature = computeHmacSignature(payload.cipherTextBase64, payload.ivBase64)
            prefs.edit().apply {
                putString(KEY_ENCRYPTED_DATA, payload.cipherTextBase64)
                putString(KEY_IV, payload.ivBase64)
                putString(KEY_HMAC_SIGNATURE, hmacSignature)
                putBoolean(KEY_IS_ACTIVATED, true)
                apply()
            }
            Log.d(TAG, "Encrypted license payload and HMAC stored successfully.")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save encrypted license", e)
            false
        }
    }

    /**
     * Retrieves encrypted payload if present and signature is valid.
     * Returns null if missing or if HMAC signature check fails (tampering/cloning detected).
     */
    fun getEncryptedLicense(): EncryptedPayload? {
        val cipherText = prefs.getString(KEY_ENCRYPTED_DATA, null)
        val iv = prefs.getString(KEY_IV, null)
        val storedHmac = prefs.getString(KEY_HMAC_SIGNATURE, null)

        if (cipherText.isNullOrBlank() || iv.isNullOrBlank() || storedHmac.isNullOrBlank()) {
            return null
        }

        // Verify HMAC integrity & device binding
        val calculatedHmac = computeHmacSignature(cipherText, iv)
        if (calculatedHmac != storedHmac) {
            Log.e(TAG, "HMAC Integrity check failed! Preference data was tampered or moved to another device.")
            clearStorage()
            return null
        }

        return EncryptedPayload(cipherTextBase64 = cipherText, ivBase64 = iv)
    }

    /**
     * Checks if local preferences report active status.
     */
    fun isMarkedActivated(): Boolean {
        return prefs.getBoolean(KEY_IS_ACTIVATED, false)
    }

    /**
     * Clears all encrypted license data from SharedPreferences.
     */
    fun clearStorage() {
        prefs.edit().clear().apply()
        Log.d(TAG, "Secure license storage cleared.")
    }
}
