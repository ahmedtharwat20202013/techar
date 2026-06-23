package com.example.utils

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject

/**
 * Secure storage for license data with device binding
 * Data is encrypted and can only be decrypted on the same device
 */
object SecureStorage {
    
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "TecharLicenseKey"
    private const val LICENSE_FILE_NAME = ".license_secure.dat"
    private const val IV_LENGTH = 12 // 96 bits for GCM
    private const val TAG_LENGTH = 128 // 128 bits auth tag
    
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
     * Get or create secret key from Android Keystore
     */
    private fun getSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        
        // Check if key exists
        keyStore.getKey(KEY_ALIAS, null)?.let {
            return it as SecretKey
        }
        
        // Generate new key
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(false)
            .build()
        )
        
        return keyGenerator.generateKey()
    }
    
    /**
     * Encrypt license data
     */
    fun encryptLicense(context: Context, data: LicenseData): Boolean {
        return try {
            val secretKey = getSecretKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            
            // Create JSON with all license info
            val json = JSONObject().apply {
                put("license_key", data.licenseKey)
                put("device_fingerprint", data.deviceFingerprint)
                put("activation_token", data.activationToken)
                put("activated_at", data.activatedAt)
                put("expires_at", data.expiresAt ?: -1L)
                put("user_name", data.userName ?: "")
            }
            
            val plaintext = json.toString().toByteArray(Charsets.UTF_8)
            val encrypted = cipher.doFinal(plaintext)
            
            // Combine IV + encrypted data
            val iv = cipher.iv
            val combined = iv + encrypted
            
            // Save to file
            val file = File(context.filesDir, LICENSE_FILE_NAME)
            file.writeBytes(combined)
            
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * Decrypt and validate license data
     * Returns null if:
     * - File doesn't exist
     * - Decryption fails
     * - Device fingerprint mismatch (file copied to different device)
     */
    fun decryptAndValidate(context: Context): LicenseData? {
        return try {
            val file = File(context.filesDir, LICENSE_FILE_NAME)
            if (!file.exists()) return null
            
            val combined = file.readBytes()
            if (combined.size < IV_LENGTH) return null
            
            // Extract IV and encrypted data
            val iv = combined.copyOfRange(0, IV_LENGTH)
            val encrypted = combined.copyOfRange(IV_LENGTH, combined.size)
            
            // Decrypt
            val secretKey = getSecretKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(TAG_LENGTH, iv))
            
            val decrypted = cipher.doFinal(encrypted)
            val json = JSONObject(String(decrypted, Charsets.UTF_8))
            
            val storedFingerprint = json.getString("device_fingerprint")
            val currentFingerprint = DeviceUtils.getDeviceFingerprint(context)
            
            // CRITICAL: Verify device fingerprint
            if (storedFingerprint != currentFingerprint) {
                // File copied to different device!
                // Delete the file to prevent reuse
                file.delete()
                return null
            }
            
            val expiresAtVal = json.getLong("expires_at")
            val expiresAt = if (expiresAtVal > 0) expiresAtVal else null
            
            LicenseData(
                licenseKey = json.getString("license_key"),
                deviceFingerprint = storedFingerprint,
                activationToken = json.getString("activation_token"),
                activatedAt = json.getLong("activated_at"),
                expiresAt = expiresAt,
                userName = json.optString("user_name", null)
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Check if license file exists
     */
    fun hasLicenseFile(context: Context): Boolean {
        return File(context.filesDir, LICENSE_FILE_NAME).exists()
    }
    
    /**
     * Delete license file (for logout/reset)
     */
    fun deleteLicense(context: Context): Boolean {
        return try {
            File(context.filesDir, LICENSE_FILE_NAME).delete()
        } catch (e: Exception) {
            false
        }
    }
}
