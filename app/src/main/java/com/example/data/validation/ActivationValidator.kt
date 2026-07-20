package com.example.data.validation

import com.example.data.model.SubscriptionLicense
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class ValidationResult {
    object Success : ValidationResult()
    object Expired : ValidationResult()
    object Blocked : ValidationResult()
    object InvalidFingerprint : ValidationResult()
    object InvalidCredentials : ValidationResult()
}

object ActivationValidator {
    fun validateLicense(license: SubscriptionLicense, currentFingerprint: String): ValidationResult {
        // 1. Verify subscription status
        val statusLower = license.status.lowercase(Locale.US).trim()
        if (statusLower == "blocked" || statusLower == "inactive" || statusLower != "active") {
            return ValidationResult.Blocked
        }

        // 2. Verify subscription expiration
        val isValidDate = try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val expireDate = sdf.parse(license.expireDate)
            val todayStr = sdf.format(Date())
            val today = sdf.parse(todayStr)
            if (expireDate != null && today != null) {
                // If expireDate is today or after, it's valid
                !expireDate.before(today)
            } else {
                false
            }
        } catch (e: Exception) {
            // Fallback for different date formats
            try {
                val sdfWithTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                val expireDate = sdfWithTime.parse(license.expireDate)
                val today = Date()
                expireDate != null && !expireDate.before(today)
            } catch (ex: Exception) {
                false
            }
        }

        if (!isValidDate) {
            return ValidationResult.Expired
        }

        // 3. Verify hardware fingerprint matching
        if (!license.deviceFingerprint.isNullOrBlank() && license.deviceFingerprint != currentFingerprint) {
            return ValidationResult.InvalidFingerprint
        }

        return ValidationResult.Success
    }
}
