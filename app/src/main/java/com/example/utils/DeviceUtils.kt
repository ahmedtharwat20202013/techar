package com.example.utils

import android.content.Context
import android.os.Build
import android.provider.Settings
import java.security.MessageDigest

object DeviceUtils {
    
    /**
     * Generate unique device fingerprint
     * This fingerprint is unique per device and cannot be easily spoofed
     */
    fun getDeviceFingerprint(context: Context): String {
        val sb = StringBuilder()
        
        // Hardware identifiers (stable across factory resets/devices)
        sb.append(Build.BOARD).append("|")
        sb.append(Build.BRAND).append("|")
        sb.append(Build.DEVICE).append("|")
        sb.append(Build.HARDWARE).append("|")
        sb.append(Build.MANUFACTURER).append("|")
        sb.append(Build.MODEL).append("|")
        sb.append(Build.PRODUCT).append("|")
        
        // Software identifiers
        sb.append(Build.VERSION.RELEASE).append("|")
        sb.append(Build.VERSION.SDK_INT).append("|")
        sb.append(Build.FINGERPRINT).append("|")
        
        // Android ID (unique per device + user + app signing)
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: "unknown"
        sb.append(androidId).append("|")
        
        // Package name (prevents copying file to different app)
        sb.append(context.packageName)
        
        // Generate SHA-256 hash
        return sha256(sb.toString())
    }
    
    /**
     * Generate SHA-256 hash
     */
    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
    
    /**
     * Get device info for display
     */
    fun getDeviceInfo(): Map<String, String> {
        return mapOf(
            "model" to Build.MODEL,
            "manufacturer" to Build.MANUFACTURER,
            "android_version" to Build.VERSION.RELEASE,
            "sdk" to Build.VERSION.SDK_INT.toString()
        )
    }
}
