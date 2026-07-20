package com.example.data.service

import android.util.Log
import com.example.data.DatabaseConfig
import com.example.data.model.SubscriptionLicense
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI
import java.sql.Connection
import java.sql.DriverManager
import java.util.Properties

object PostgresDatabaseService {
    private const val TAG = "PostgresDatabaseService"

    init {
        try {
            Class.forName("org.postgresql.Driver")
            Log.d(TAG, "PostgreSQL Driver registered successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register PostgreSQL Driver", e)
        }
    }

    private fun getConnection(): Connection {
        val rawUrl = DatabaseConfig.DATABASE_URL
        val cleanUrl = if (rawUrl.startsWith("postgresql://")) {
            rawUrl.substring("postgresql://".length)
        } else if (rawUrl.startsWith("postgres://")) {
            rawUrl.substring("postgres://".length)
        } else {
            rawUrl
        }

        // Format: user:password@host:port/database?query
        val atIndex = cleanUrl.indexOf('@')
        if (atIndex == -1) {
            throw IllegalArgumentException("Invalid Connection String URL formatting.")
        }
        val userInfo = cleanUrl.substring(0, atIndex)
        val rest = cleanUrl.substring(atIndex + 1)

        val userPass = userInfo.split(":")
        val user = userPass[0]
        val password = userPass[1]

        val slashIndex = rest.indexOf('/')
        if (slashIndex == -1) {
            throw IllegalArgumentException("Invalid Connection String URL database path formatting.")
        }
        val hostPort = rest.substring(0, slashIndex)
        val dbAndQuery = rest.substring(slashIndex + 1)

        val queryIndex = dbAndQuery.indexOf('?')
        val dbName = if (queryIndex != -1) dbAndQuery.substring(0, queryIndex) else dbAndQuery

        // Build standard JDBC URL
        val jdbcUrl = "jdbc:postgresql://$hostPort/$dbName"

        val props = Properties()
        props.setProperty("user", user)
        props.setProperty("password", password)
        props.setProperty("ssl", "true")
        props.setProperty("sslmode", "require")
        
        Log.d(TAG, "Connecting to: jdbc:postgresql://$hostPort/$dbName")
        return DriverManager.getConnection(jdbcUrl, props)
    }

    /**
     * Finds a license by customer name and license key.
     */
    suspend fun getLicense(customerName: String, licenseKey: String): SubscriptionLicense? = withContext(Dispatchers.IO) {
        var conn: Connection? = null
        var stmt: java.sql.PreparedStatement? = null
        var rs: java.sql.ResultSet? = null
        try {
            conn = getConnection()
            val sql = "SELECT customer_name, license_key, status, expire_date, device_fingerprint " +
                      "FROM licenses " +
                      "WHERE TRIM(customer_name) = ? AND TRIM(license_key) = ?"
            stmt = conn.prepareStatement(sql)
            stmt.setString(1, customerName.trim())
            stmt.setString(2, licenseKey.trim())
            
            rs = stmt.executeQuery()
            if (rs.next()) {
                val foundCustomer = rs.getString("customer_name")
                val foundKey = rs.getString("license_key")
                val status = rs.getString("status")
                val expireDate = rs.getString("expire_date")
                val deviceFingerprint = rs.getString("device_fingerprint")
                
                return@withContext SubscriptionLicense(
                    customerName = foundCustomer,
                    licenseKey = foundKey,
                    status = status ?: "",
                    expireDate = expireDate ?: "",
                    deviceFingerprint = deviceFingerprint
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching license from PostgreSQL", e)
            throw e
        } finally {
            try { rs?.close() } catch (ignored: Exception) {}
            try { stmt?.close() } catch (ignored: Exception) {}
            try { conn?.close() } catch (ignored: Exception) {}
        }
        return@withContext null
    }

    /**
     * Updates the device fingerprint in the database if it is NULL.
     */
    suspend fun updateDeviceFingerprint(
        customerName: String,
        licenseKey: String,
        fingerprint: String
    ): Boolean = withContext(Dispatchers.IO) {
        var conn: Connection? = null
        var stmt: java.sql.PreparedStatement? = null
        try {
            conn = getConnection()
            
            // First we try to update just the device_fingerprint
            val sql = "UPDATE licenses SET device_fingerprint = ? WHERE TRIM(customer_name) = ? AND TRIM(license_key) = ? AND device_fingerprint IS NULL"
            stmt = conn.prepareStatement(sql)
            stmt.setString(1, fingerprint)
            stmt.setString(2, customerName.trim())
            stmt.setString(3, licenseKey.trim())
            
            val rows = stmt.executeUpdate()
            return@withContext rows > 0
        } catch (e: Exception) {
            Log.e(TAG, "Error updating device fingerprint in PostgreSQL", e)
            throw e
        } finally {
            try { stmt?.close() } catch (ignored: Exception) {}
            try { conn?.close() } catch (ignored: Exception) {}
        }
    }
}
