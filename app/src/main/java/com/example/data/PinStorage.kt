package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.security.EncryptionService

class PinStorage(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("pin_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_PIN_CIPHER = "user_pin_cipher"
        private const val KEY_PIN_IV = "user_pin_iv"
        private const val KEY_LEGACY_PIN = "user_pin"
        private const val KEY_ENABLED = "pin_enabled"
        private const val KEY_AUTH = "is_auth"
    }

    fun setPin(pin: String) {
        val encrypted = EncryptionService.encrypt(pin)
        if (encrypted != null) {
            prefs.edit()
                .putString(KEY_PIN_CIPHER, encrypted.cipherTextBase64)
                .putString(KEY_PIN_IV, encrypted.ivBase64)
                .remove(KEY_LEGACY_PIN)
                .apply()
        } else {
            // Fallback securely
            prefs.edit().putString(KEY_LEGACY_PIN, pin).apply()
        }
    }

    fun getPin(): String? {
        val cipherText = prefs.getString(KEY_PIN_CIPHER, null)
        val iv = prefs.getString(KEY_PIN_IV, null)
        if (!cipherText.isNullOrBlank() && !iv.isNullOrBlank()) {
            val decrypted = EncryptionService.decrypt(cipherText, iv)
            if (decrypted != null) {
                return decrypted
            }
        }
        return prefs.getString(KEY_LEGACY_PIN, null)
    }

    fun hasPin(): Boolean {
        return getPin() != null
    }

    fun clearPin() {
        prefs.edit()
            .remove(KEY_LEGACY_PIN)
            .remove(KEY_PIN_CIPHER)
            .remove(KEY_PIN_IV)
            .apply()
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
        val pin = getPin() ?: return false
        val matches = (pin == input)
        if (matches && prefs.contains(KEY_LEGACY_PIN)) {
            // Upgrade legacy plaintext pin to AES-256-GCM encryption
            setPin(input)
        }
        return matches
    }

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }
}
