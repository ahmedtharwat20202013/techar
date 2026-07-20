package com.example.data.storage

import android.content.Context
import android.content.SharedPreferences

class ActivationStorage(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("premium_activation_prefs", Context.MODE_PRIVATE)

    fun isActivated(): Boolean {
        return prefs.getBoolean(KEY_IS_ACTIVATED, false)
    }

    fun setActivated(activated: Boolean, customerName: String? = null, licenseKey: String? = null) {
        prefs.edit().apply {
            putBoolean(KEY_IS_ACTIVATED, activated)
            putString(KEY_CUSTOMER_NAME, customerName)
            putString(KEY_LICENSE_KEY, licenseKey)
            putLong(KEY_ACTIVATION_TIME, System.currentTimeMillis())
            apply()
        }
    }

    fun getActivatedCustomerName(): String? {
        return prefs.getString(KEY_CUSTOMER_NAME, null)
    }

    fun getActivatedLicenseKey(): String? {
        return prefs.getString(KEY_LICENSE_KEY, null)
    }

    fun clearActivation() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_IS_ACTIVATED = "is_activated"
        private const val KEY_CUSTOMER_NAME = "customer_name"
        private const val KEY_LICENSE_KEY = "license_key"
        private const val KEY_ACTIVATION_TIME = "activation_time"
    }
}
