package com.example.security

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Result state of license subscription validation.
 */
sealed class SubscriptionStatus {
    data class Active(
        val daysRemaining: Int,
        val license: SubscriptionLicenseData
    ) : SubscriptionStatus()

    data class Expired(
        val expireDate: String,
        val license: SubscriptionLicenseData?
    ) : SubscriptionStatus()

    data class TamperedOrInvalid(
        val reason: String
    ) : SubscriptionStatus()
}

/**
 * SubscriptionValidator validates decrypted SubscriptionLicenseData objects.
 * Checks date validity, expiration rules, and structure integrity.
 */
object SubscriptionValidator {
    private const val TAG = "SubscriptionValidator"

    /**
     * Checks if the expiration date string has already passed (expired).
     * Supports ISO-8601, full timestamps, and standard yyyy-MM-dd date formats.
     */
    fun isExpired(expireDateStr: String?): Boolean {
        if (expireDateStr.isNullOrBlank()) return true
        return try {
            val now = Date()
            val sdfDateOnly = SimpleDateFormat("yyyy-MM-dd", Locale.US)

            // Try parsing full ISO or DateTime first if timestamp is present
            val fullDate = tryParseDateTime(expireDateStr)
            if (fullDate != null) {
                return now.after(fullDate)
            }

            // Fallback to date-only comparison
            val cleanDateStr = expireDateStr.split(" ")[0].split("T")[0]
            val expiryDate = sdfDateOnly.parse(cleanDateStr) ?: return true
            val todayStr = sdfDateOnly.format(now)
            val todayDate = sdfDateOnly.parse(todayStr) ?: return true

            // Expired if today is strictly after expiryDate
            expiryDate.before(todayDate)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking expiration date: $expireDateStr", e)
            true
        }
    }

    private fun tryParseDateTime(dateStr: String): Date? {
        val formats = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd HH:mm"
        )
        for (fmt in formats) {
            try {
                val sdf = SimpleDateFormat(fmt, Locale.US)
                val date = sdf.parse(dateStr)
                if (date != null) return date
            } catch (_: Exception) {}
        }
        return null
    }

    /**
     * Calculates days remaining between today and expiration date string (e.g. "2026-12-31").
     */
    fun calculateDaysRemaining(expireDateStr: String?): Int? {
        if (expireDateStr.isNullOrBlank()) return null
        return try {
            val cleanDate = expireDateStr.split(" ")[0].split("T")[0]
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val expiryDate = sdf.parse(cleanDate)
            val todayStr = sdf.format(Date())
            val today = sdf.parse(todayStr)

            if (expiryDate != null && today != null) {
                val diffMs = expiryDate.time - today.time
                val days = TimeUnit.MILLISECONDS.toDays(diffMs)
                days.toInt()
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing expiration date: $expireDateStr", e)
            null
        }
    }

    /**
     * Validates subscription license object.
     */
    fun validate(license: SubscriptionLicenseData?): SubscriptionStatus {
        if (license == null) {
            return SubscriptionStatus.TamperedOrInvalid("بيانات الاشتراك غير موجودة أو معطوبة")
        }

        if (license.expireDate.isBlank()) {
            return SubscriptionStatus.TamperedOrInvalid("بيانات الاشتراك ناقصة")
        }

        if (isExpired(license.expireDate)) {
            return SubscriptionStatus.Expired(expireDate = license.expireDate, license = license)
        }

        val daysRemaining = calculateDaysRemaining(license.expireDate) ?: 0
        return SubscriptionStatus.Active(daysRemaining = daysRemaining, license = license)
    }
}
