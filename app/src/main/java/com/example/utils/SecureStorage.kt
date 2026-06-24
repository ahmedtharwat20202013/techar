package com.example.utils

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

/**
 * Secure storage for license data using Android's EncryptedSharedPreferences.
 * Provides high-level hardware-backed encryption to store license details securely.
 */
object SecureStorage {
    
    private const val PREFS_FILE_NAME = "secure_license_prefs"
    private const val KEY_LICENSE_KEY = "license_key"
    private const val KEY_DEVICE_FINGERPRINT = "device_fingerprint"
    private const val KEY_ACTIVATION_TOKEN = "activation_token"
    private const val KEY_ACTIVATED_AT = "activated_at"
    private const val KEY_EXPIRES_AT = "expires_at"
    private const val KEY_USER_NAME = "user_name"
    
    /**
     * Data class for license information
     */
    data class LicenseData(
        val licenseKey: String,
        val deviceFingerprint: String,
        val activationToken: String,
        val activatedAt: Long,
        val expiresAt: Long? = null,
        val userName: String? = null
    )
    
    /**
     * Get or create EncryptedSharedPreferences instance
     */
    private fun getEncryptedPrefs(context: Context): android.content.SharedPreferences {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        return EncryptedSharedPreferences.create(
            PREFS_FILE_NAME,
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    
    /**
     * Encrypt and store license data
     */
    fun encryptLicense(context: Context, data: LicenseData): Boolean {
        return try {
            val prefs = getEncryptedPrefs(context)
            prefs.edit().apply {
                putString(KEY_LICENSE_KEY, data.licenseKey)
                putString(KEY_DEVICE_FINGERPRINT, data.deviceFingerprint)
                putString(KEY_ACTIVATION_TOKEN, data.activationToken)
                putLong(KEY_ACTIVATED_AT, data.activatedAt)
                putLong(KEY_EXPIRES_AT, data.expiresAt ?: -1L)
                putString(KEY_USER_NAME, data.userName ?: "")
                apply()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * Decrypt and validate license data.
     * Checks if the device fingerprint matches the currently calculated one.
     * Returns null if no license is stored, or if a device mismatch is detected.
     */
    fun decryptAndValidate(context: Context): LicenseData? {
        return try {
            val prefs = getEncryptedPrefs(context)
            val licenseKey = prefs.getString(KEY_LICENSE_KEY, null) ?: return null
            val storedFingerprint = prefs.getString(KEY_DEVICE_FINGERPRINT, null) ?: return null
            val activationToken = prefs.getString(KEY_ACTIVATION_TOKEN, "") ?: ""
            val activatedAt = prefs.getLong(KEY_ACTIVATED_AT, 0L)
            val expiresAtVal = prefs.getLong(KEY_EXPIRES_AT, -1L)
            val userName = prefs.getString(KEY_USER_NAME, null)
            
            val currentFingerprint = DeviceUtils.getDeviceFingerprint(context)
            
            // CRITICAL: Verify device fingerprint
            if (storedFingerprint != currentFingerprint) {
                // Device mismatch detected! Erase the license data immediately
                deleteLicense(context)
                return null
            }
            
            val expiresAt = if (expiresAtVal > 0) expiresAtVal else null
            
            LicenseData(
                licenseKey = licenseKey,
                deviceFingerprint = storedFingerprint,
                activationToken = activationToken,
                activatedAt = activatedAt,
                expiresAt = expiresAt,
                userName = userName
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Check if license exists in EncryptedSharedPreferences
     */
    fun hasLicenseFile(context: Context): Boolean {
        val prefs = getEncryptedPrefs(context)
        return prefs.contains(KEY_LICENSE_KEY)
    }
    
    /**
     * Clear all license data from EncryptedSharedPreferences
     */
    fun deleteLicense(context: Context): Boolean {
        return try {
            val prefs = getEncryptedPrefs(context)
            prefs.edit().clear().apply()
            true
        } catch (e: Exception) {
            false
        }
    }
}
