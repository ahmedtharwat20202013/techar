package com.example.security

import android.content.Context
import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

/**
 * LicenseManager serves as the primary high-level API orchestrator for handling
 * secure subscription lifecycle (Encryption, Secure Storage, and Validation).
 */
object LicenseManager {
    private const val TAG = "LicenseManager"

    private val moshi: Moshi by lazy {
        Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }

    private val adapter by lazy {
        moshi.adapter(SubscriptionLicenseData::class.java)
    }

    /**
     * Encrypts and securely stores subscription license data locally.
     * 
     * Workflow:
     * 1. Constructs SubscriptionLicenseData payload.
     * 2. Serializes object to JSON string using Moshi.
     * 3. Passes JSON string to EncryptionService (AES-256-GCM + Android Keystore key).
     * 4. SecureStorageService stores ciphertext, IV, and HMAC signature in preferences.
     */
    fun saveLicense(
        context: Context,
        customerName: String = "مشترك",
        licenseKey: String,
        expireDate: String,
        licenseId: String = "",
        startDate: String = "",
        planType: String = "PREMIUM",
        features: List<String> = listOf("FULL_ACCESS")
    ): Boolean {
        return try {
            val licenseData = SubscriptionLicenseData(
                licenseId = if (licenseId.isBlank()) licenseKey else licenseId,
                customerName = customerName,
                startDate = startDate,
                expireDate = expireDate,
                planType = planType,
                features = features,
                signature = licenseKey
            )

            val jsonString = adapter.toJson(licenseData)
            val encryptedPayload = EncryptionService.encrypt(jsonString)

            if (encryptedPayload != null) {
                val storageService = SecureStorageService(context)
                val isSaved = storageService.saveEncryptedLicense(encryptedPayload)
                Log.d(TAG, "Subscription license encrypted and stored successfully for: $customerName")
                isSaved
            } else {
                Log.e(TAG, "Encryption returned null payload")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save secure license", e)
            false
        }
    }

    /**
     * Decrypts and retrieves stored SubscriptionLicenseData object.
     * 
     * Workflow:
     * 1. Retrieves encrypted payload from SecureStorageService (validates HMAC signature).
     * 2. Decrypts ciphertext using EncryptionService (verifies AES-GCM 128-bit Auth Tag).
     * 3. Deserializes plaintext JSON string to SubscriptionLicenseData using Moshi.
     * 4. Returns null if data is tampered, corrupted, or missing.
     */
    fun getLicenseData(context: Context): SubscriptionLicenseData? {
        return try {
            val storageService = SecureStorageService(context)
            val encryptedPayload = storageService.getEncryptedLicense() ?: return null

            val decryptedJson = EncryptionService.decrypt(
                cipherTextBase64 = encryptedPayload.cipherTextBase64,
                ivBase64 = encryptedPayload.ivBase64
            )

            if (decryptedJson.isNullOrBlank()) {
                Log.e(TAG, "Decryption returned null or blank json")
                return null
            }

            adapter.fromJson(decryptedJson)
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving or parsing license data", e)
            null
        }
    }

    /**
     * Validates current subscription status.
     * Returns SubscriptionStatus (Active, Expired, or TamperedOrInvalid).
     */
    fun validateSubscription(context: Context): SubscriptionStatus {
        val licenseData = getLicenseData(context)
        return SubscriptionValidator.validate(licenseData)
    }

    /**
     * Quick boolean check to determine if the device has an active, untampered, non-expired license.
     */
    fun isSubscriptionActive(context: Context): Boolean {
        return when (validateSubscription(context)) {
            is SubscriptionStatus.Active -> true
            else -> false
        }
    }

    /**
     * Returns remaining subscription days, or null if expired or invalid.
     */
    fun getDaysRemaining(context: Context): Int? {
        return when (val status = validateSubscription(context)) {
            is SubscriptionStatus.Active -> status.daysRemaining
            else -> null
        }
    }

    /**
     * Clears all encrypted license data from the device.
     */
    fun clearLicense(context: Context) {
        val storageService = SecureStorageService(context)
        storageService.clearStorage()
        Log.d(TAG, "Subscription license cleared from secure storage.")
    }
}
