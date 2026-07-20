package com.example.data.model

data class SubscriptionLicense(
    val customerName: String,
    val licenseKey: String,
    val status: String,          // 'active', 'blocked', etc.
    val expireDate: String,      // format: yyyy-MM-dd or similar
    val deviceFingerprint: String?
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
