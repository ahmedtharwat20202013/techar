package com.example.data

import android.content.Context
import android.content.SharedPreferences

class PinStorage(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("pin_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_PIN = "user_pin"
        private const val KEY_ENABLED = "pin_enabled"
        private const val KEY_AUTH = "is_auth"
    }

    fun setPin(pin: String) {
        prefs.edit().putString(KEY_PIN, pin).apply()
    }

    fun getPin(): String? {
        return prefs.getString(KEY_PIN, null)
    }

    fun hasPin(): Boolean {
        return getPin() != null
    }

    fun clearPin() {
        prefs.edit().remove(KEY_PIN).apply()
    }

    fun setPinEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun isPinEnabled(): Boolean {
        return prefs.getBoolean(KEY_ENABLED, false)
    }

    fun setAuthenticated(auth: Boolean) {
        prefs.edit().putBoolean(KEY_AUTH, auth).apply()
    }

    fun isAuthenticated(): Boolean {
        return prefs.getBoolean(KEY_AUTH, false)
    }

    fun verifyPin(input: String): Boolean {
        return input == getPin()
    }

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }
}
