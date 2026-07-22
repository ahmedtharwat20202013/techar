package com.example.data.storage

import android.content.Context
import android.content.SharedPreferences

class ActivationStorage(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("premium_activation_prefs", Context.MODE_PRIVATE)

    fun isActivated(): Boolean {
        return prefs.getBoolean(KEY_IS_ACTIVATED, false)
    }

    fun setActivated(activated: Boolean, customerName: String? = null, licenseKey: String? = null, expireDate: String? = null) {
        prefs.edit().apply {
            putBoolean(KEY_IS_ACTIVATED, activated)
            putString(KEY_CUSTOMER_NAME, customerName)
            putString(KEY_LICENSE_KEY, licenseKey)
            putString(KEY_EXPIRE_DATE, expireDate)
            putLong(KEY_ACTIVATION_TIME, System.currentTimeMillis())
            putLong(KEY_LAST_VERIFICATION_TIME, System.currentTimeMillis())
            apply()
        }
    }

    fun getActivatedCustomerName(): String? {
        return prefs.getString(KEY_CUSTOMER_NAME, null)
    }

    fun getActivatedLicenseKey(): String? {
        return prefs.getString(KEY_LICENSE_KEY, null)
    }

    fun getExpireDate(): String? {
        return prefs.getString(KEY_EXPIRE_DATE, null)
    }

    fun setExpireDate(expireDate: String?) {
        prefs.edit().putString(KEY_EXPIRE_DATE, expireDate).apply()
    }

    fun getLastVerificationTime(): Long {
        return prefs.getLong(KEY_LAST_VERIFICATION_TIME, 0L)
    }

    fun setLastVerificationTime(time: Long) {
        prefs.edit().putLong(KEY_LAST_VERIFICATION_TIME, time).apply()
    }

    fun clearActivation() {
        prefs.edit().apply {
            putBoolean(KEY_IS_ACTIVATED, false)
            putString(KEY_CUSTOMER_NAME, null)
            putString(KEY_LICENSE_KEY, null)
            putString(KEY_EXPIRE_DATE, null)
            putLong(KEY_ACTIVATION_TIME, 0L)
            putLong(KEY_LAST_VERIFICATION_TIME, 0L)
            apply()
        }
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
        private const val KEY_ACTIVATION_TIME = "activation_time"
        private const val KEY_EXPIRE_DATE = "expire_date"
        private const val KEY_LAST_VERIFICATION_TIME = "last_verification_time"
    }
}
