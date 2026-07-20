package com.example.utils

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.example.data.model.DeviceHiddenData
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DeviceUtils {

    fun getAndroidId(context: Context): String {
        return try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown_id"
        } catch (e: Exception) {
            "unknown_id"
        }
    }

    fun getAppVersion(context: Context): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.0"
        } catch (e: Exception) {
            "1.0"
        }
    }

    fun getDeviceFingerprint(context: Context): String {
        val androidId = getAndroidId(context)
        val model = Build.MODEL ?: ""
        val manufacturer = Build.MANUFACTURER ?: ""
        val brand = Build.BRAND ?: ""
        
        // Combine hardware details with Android ID to build a unique hardware key
        val rawString = "AndroidApp::${androidId}::${manufacturer}::${model}::${brand}"
        return sha256(rawString)
    }

    private fun sha256(input: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
            val hexString = StringBuilder()
            for (b in hash) {
                val hex = Integer.toHexString(0xff and b.toInt())
                if (hex.length == 1) hexString.append('0')
                hexString.append(hex)
            }
            hexString.toString()
        } catch (e: Exception) {
            // Robust fallback representation of string hash
            val hashCode = input.hashCode().toString()
            "fallback_$hashCode"
        }
    }

    fun collectHiddenData(context: Context): DeviceHiddenData {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val currentDate = sdf.format(Date())
        
        return DeviceHiddenData(
            fingerprint = getDeviceFingerprint(context),
            androidId = getAndroidId(context),
            model = Build.MODEL ?: "Unknown",
            manufacturer = Build.MANUFACTURER ?: "Unknown",
            brand = Build.BRAND ?: "Unknown",
            androidVersion = Build.VERSION.RELEASE ?: "Unknown",
            appVersion = getAppVersion(context),
            currentDate = currentDate
        )
    }
}
