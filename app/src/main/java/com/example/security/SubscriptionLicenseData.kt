package com.example.security

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Data model for subscriber license payload.
 * Encrypted and stored securely in local device storage.
 */
@JsonClass(generateAdapter = true)
data class SubscriptionLicenseData(
    @Json(name = "license_id") val licenseId: String = "",
    @Json(name = "customer_name") val customerName: String = "",
    @Json(name = "start_date") val startDate: String = "",
    @Json(name = "expire_date") val expireDate: String = "",
    @Json(name = "plan_type") val planType: String = "PREMIUM",
    @Json(name = "features") val features: List<String> = listOf("FULL_ACCESS"),
    @Json(name = "signature") val signature: String = ""
)
