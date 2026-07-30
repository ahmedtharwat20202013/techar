package com.example.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Data holder for AES-256-GCM encrypted payload and its Initialization Vector (IV).
 */
data class EncryptedPayload(
    val cipherTextBase64: String,
    val ivBase64: String
)

/**
 * EncryptionService provides secure cryptographic operations using Android Keystore.
 * 
 * Cryptographic Specs:
 * - Algorithm: AES-256 in GCM (Galois/Counter Mode) with No Padding.
 * - Key Storage: Non-exportable SecretKey stored inside Android Keystore.
 * - Integrity Protection: AES-GCM provides authenticated encryption (AEAD) with a 128-bit Authentication Tag.
 */
object EncryptionService {
    private const val TAG = "EncryptionService"
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val KEY_ALIAS = "TeacherAppSecureLicenseKey_AES256"
    private const val AES_GCM_CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val GCM_IV_LENGTH_BYTES = 12

    /**
     * Retrieves existing SecretKey from Android Keystore or generates a new AES-256 key.
     * 
     * Key Generation Process:
     * 1. Accesses "AndroidKeyStore" hardware-backed key store.
     * 2. Checks if alias "TeacherAppSecureLicenseKey_AES256" exists.
     * 3. If missing, uses KeyGenerator with KeyGenParameterSpec specifying:
     *    - Purpose: ENCRYPT | DECRYPT
     *    - Block Mode: GCM
     *    - Padding: NONE
     *    - Key Size: 256 bits
     */
    @Synchronized
    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }

        if (keyStore.containsAlias(KEY_ALIAS)) {
            val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
            if (entry?.secretKey != null) {
                return entry.secretKey
            }
        }

        Log.d(TAG, "Generating new AES-256 master key in Android Keystore...")
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            KEYSTORE_PROVIDER
        )

        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()

        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    /**
     * Encrypts plaintext string using AES-256-GCM.
     * 
     * Encryption Process:
     * 1. Retrieves SecretKey from Keystore.
     * 2. Cipher initializes in ENCRYPT_MODE (generates random 12-byte IV automatically).
     * 3. Cipher produces ciphertext + 128-bit authentication tag appended at the end.
     * 4. Converts ciphertext and IV to Base64 strings for secure local storage.
     */
    fun encrypt(plainText: String): EncryptedPayload? {
        return try {
            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance(AES_GCM_CIPHER_TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)

            val iv = cipher.iv // 12-byte random IV
            val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

            EncryptedPayload(
                cipherTextBase64 = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP),
                ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to encrypt payload", e)
            null
        }
    }

    /**
     * Decrypts ciphertext Base64 using AES-256-GCM and IV.
     * 
     * Decryption Process & Integrity Verification:
     * 1. Decodes Base64 ciphertext and IV.
     * 2. Cipher initializes in DECRYPT_MODE with SecretKey and GCMParameterSpec(128, iv).
     * 3. Cipher evaluates ciphertext and validates embedded 128-bit GCM Authentication Tag.
     * 4. If any bit of ciphertext, IV, or stored data was altered/tampered, tag check fails
     *    and an exception (AEADBadTagException) is raised, returning null.
     */
    fun decrypt(cipherTextBase64: String, ivBase64: String): String? {
        return try {
            val secretKey = getOrCreateSecretKey()
            val cipherTextBytes = Base64.decode(cipherTextBase64, Base64.NO_WRAP)
            val ivBytes = Base64.decode(ivBase64, Base64.NO_WRAP)

            val cipher = Cipher.getInstance(AES_GCM_CIPHER_TRANSFORMATION)
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, ivBytes)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)

            val decryptedBytes = cipher.doFinal(cipherTextBytes)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: javax.crypto.AEADBadTagException) {
            Log.e(TAG, "Data integrity validation failed! Ciphertext was tampered with.", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decrypt payload", e)
            null
        }
    }
}
