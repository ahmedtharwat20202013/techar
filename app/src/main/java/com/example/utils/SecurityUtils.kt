package com.example.utils

import android.content.Context
import android.os.Build
import java.io.File

object SecurityUtils {
    
    /**
     * Check if device is rooted
     */
    fun isDeviceRooted(): Boolean {
        val testKeys = android.os.Build.TAGS?.contains("test-keys") ?: false
        val superuserApk = File("/system/app/Superuser.apk").exists()
        val suBinary = File("/system/bin/su").exists() || File("/system/xbin/su").exists()
        
        return testKeys || superuserApk || suBinary
    }
    
    /**
     * Check if app is being debugged
     */
    fun isDebugged(): Boolean {
        return android.os.Debug.isDebuggerConnected()
    }
    
    /**
     * Check if running in emulator
     */
    fun isEmulator(): Boolean {
        return (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.HARDWARE.contains("goldfish")
                || Build.HARDWARE.contains("ranchu")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || Build.PRODUCT.contains("sdk_google")
                || Build.PRODUCT.contains("google_sdk")
                || Build.PRODUCT.contains("sdk")
                || Build.PRODUCT.contains("sdk_x86")
                || Build.PRODUCT.contains("vbox86p")
                || Build.PRODUCT.contains("emulator")
                || Build.PRODUCT.contains("simulator")
    }
    
    /**
     * Comprehensive security check
     */
    fun performSecurityCheck(context: Context): Boolean {
        if (isDeviceRooted()) {
            return false
        }
        if (isDebugged() && !com.example.BuildConfig.DEBUG) {
            return false
        }
        // Allow emulator in debug builds only
        if (isEmulator() && !com.example.BuildConfig.DEBUG) {
            return false
        }
        return true
    }
}
