package com.example.data.model

data class SubscriptionLicense(
    val id: Int?,
    val customerName: String,
    val licenseKey: String,
    val status: String,          // 'active', 'blocked', etc.
    val expireDate: String,      // format: yyyy-MM-dd or similar
    val deviceFingerprint: String?,
    val androidId: String? = null,
    val deviceModel: String? = null,
    val manufacturer: String? = null,
    val brand: String? = null,
    val androidVersion: String? = null,
    val appVersion: String? = null,
    val activatedAt: String? = null,
    val lastCheck: String? = null
)

data class DeviceHiddenData(
    val fingerprint: String,
    val androidId: String,
    val model: String,
    val manufacturer: String,
    val brand: String,
    val androidVersion: String,
    val appVersion: String,
    val currentDate: String
)
