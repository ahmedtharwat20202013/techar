package com.example.data

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Data class representing the license validation request body.
 */
data class LicenseRequest(
    val license_key: String,
    val device_id: String,
    val product_id: String
)

/**
 * Data class representing the license validation response.
 */
data class LicenseResponse(
    val success: Boolean,
    val valid: Boolean,
    val error: String?,
    val message: String?,
    val type: String? = null,
    val expires_at: String? = null,
    val user_name: String? = null
)

/**
 * Retrofit interface for the licensing and validation backend.
 */
interface LicenseApi {
    @POST("api/validate")
    suspend fun validateLicense(
        @Body request: LicenseRequest
    ): Response<LicenseResponse>
}
