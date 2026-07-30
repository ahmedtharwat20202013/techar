package com.example.data.storage

import android.content.Context
import android.content.SharedPreferences
import com.example.security.LicenseManager

/**
 * ActivationStorage acts as the storage interface for subscription state,
 * backed by LicenseManager with hardware-backed AES-256-GCM encryption and HMAC integrity verification.
 */
class ActivationStorage(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("premium_activation_prefs", Context.MODE_PRIVATE)

    init {
        // Migration check: If an unencrypted legacy license exists, upgrade it to AES-256-GCM encrypted format
        val legacyActivated = prefs.getBoolean(KEY_IS_ACTIVATED, false)
        val legacyName = prefs.getString(KEY_CUSTOMER_NAME, null)
        val legacyKey = prefs.getString(KEY_LICENSE_KEY, null)
        val legacyExpire = prefs.getString(KEY_EXPIRE_DATE, null)

        if (legacyActivated && !legacyName.isNullOrBlank() && !legacyKey.isNullOrBlank() && !legacyExpire.isNullOrBlank()) {
            if (!LicenseManager.isSubscriptionActive(context)) {
                LicenseManager.saveLicense(
                    context = context,
                    customerName = legacyName,
                    licenseKey = legacyKey,
                    expireDate = legacyExpire
                )
            }
            // Wipe legacy plain text keys
            prefs.edit().remove(KEY_CUSTOMER_NAME).remove(KEY_LICENSE_KEY).remove(KEY_EXPIRE_DATE).apply()
        }
    }

    /**
     * Checks if the subscription is currently active, untampered, and non-expired.
     */
    fun isActivated(): Boolean {
        val isActiveInManager = LicenseManager.isSubscriptionActive(context)
        if (!isActiveInManager && prefs.getBoolean(KEY_IS_ACTIVATED, false)) {
            // Tampered or expired - clear pref state
            prefs.edit().putBoolean(KEY_IS_ACTIVATED, false).apply()
        }
        return isActiveInManager
    }

    /**
     * Stores subscription data with AES-256-GCM encryption.
     */
    fun setActivated(activated: Boolean, customerName: String? = null, licenseKey: String? = null, expireDate: String? = null) {
        if (activated && !licenseKey.isNullOrBlank() && !expireDate.isNullOrBlank()) {
            LicenseManager.saveLicense(
                context = context,
                customerName = if (!customerName.isNullOrBlank()) customerName else "مشترك",
                licenseKey = licenseKey,
                expireDate = expireDate
            )
            prefs.edit().putBoolean(KEY_IS_ACTIVATED, true).apply()
        } else if (!activated) {
            clearActivation()
        }
    }

    /**
     * Retrieves decrypted customer name.
     */
    fun getActivatedCustomerName(): String? {
        val license = LicenseManager.getLicenseData(context)
        return license?.customerName
    }

    /**
     * Retrieves decrypted license key / ID.
     */
    fun getActivatedLicenseKey(): String? {
        val license = LicenseManager.getLicenseData(context) ?: return null
        return if (license.signature.isNotBlank()) license.signature else license.licenseId
    }

    /**
     * Retrieves decrypted expiration date.
     */
    fun getExpireDate(): String? {
        val license = LicenseManager.getLicenseData(context)
        return license?.expireDate
    }

    /**
     * Updates encrypted expiration date.
     */
    fun setExpireDate(expireDate: String?) {
        val license = LicenseManager.getLicenseData(context)
        if (license != null && !expireDate.isNullOrBlank()) {
            LicenseManager.saveLicense(
                context = context,
                customerName = license.customerName,
                licenseKey = if (license.signature.isNotBlank()) license.signature else license.licenseId,
                expireDate = expireDate,
                licenseId = license.licenseId,
                startDate = license.startDate,
                planType = license.planType,
                features = license.features
            )
        }
    }

    fun getLastVerificationTime(): Long {
        return prefs.getLong(KEY_LAST_VERIFICATION_TIME, System.currentTimeMillis())
    }

    fun setLastVerificationTime(time: Long) {
        prefs.edit().putLong(KEY_LAST_VERIFICATION_TIME, time).apply()
    }

    /**
     * Wipes all encrypted subscription data.
     */
    fun clearActivation() {
        LicenseManager.clearLicense(context)
        prefs.edit().clear().apply()
    }

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    companion object {
        const val KEY_IS_ACTIVATED = "is_activated"
        private const val KEY_CUSTOMER_NAME = "customer_name"
        private const val KEY_LICENSE_KEY = "license_key"
        private const val KEY_EXPIRE_DATE = "expire_date"
        private const val KEY_LAST_VERIFICATION_TIME = "last_verification_time"
    }
}
