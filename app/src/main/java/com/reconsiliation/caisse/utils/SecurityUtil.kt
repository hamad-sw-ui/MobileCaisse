package com.reconsiliation.caisse.utils

import android.content.Context
import android.provider.Settings
import kotlinx.coroutines.flow.first
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import java.nio.ByteBuffer
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object SecurityUtil {
    private const val KEY_ALIAS = "db_key_enc_v1"
    private const val PREFS_NAME = "security_prefs"
    private const val ENCRYPTED_KEY_SET = "encrypted_db_key"
    private const val PBKDF2_ITERATIONS = 5000
    private const val KEY_LENGTH = 256

    private fun getSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        keyStore.getKey(KEY_ALIAS, null)?.let { return it as SecretKey }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        keyGenerator.init(KeyGenParameterSpec.Builder(KEY_ALIAS, 
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build())
        return keyGenerator.generateKey()
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun saveMigratedKey(context: Context, key: ByteArray) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(key)
        val combined = ByteBuffer.allocate(1 + iv.size + encrypted.size)
            .put(iv.size.toByte()).put(iv).put(encrypted).array()

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(ENCRYPTED_KEY_SET, Base64.encode(combined))
            .apply()
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun getMigratedKey(context: Context): ByteArray? {
        val encryptedBase64 = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(ENCRYPTED_KEY_SET, null) ?: return null
        return try {
            val combined = Base64.decode(encryptedBase64)
            val buffer = ByteBuffer.wrap(combined)
            val ivLength = buffer.get().toInt()
            val iv = ByteArray(ivLength).also { buffer.get(it) }
            val encrypted = ByteArray(buffer.remaining()).also { buffer.get(it) }

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), GCMParameterSpec(128, iv))
            cipher.doFinal(encrypted)
        } catch (e: Exception) { null }
    }

    fun hashPin(pin: String): String {
        val bytes = pin.toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.fold("") { str, it -> str + "%02x".format(it) }
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun generateSalt(): String {
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)
        return Base64.encode(salt)
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun hashPinPbkdf2(pin: String, salt: String): String {
        val saltBytes = Base64.decode(salt)
        val spec = PBEKeySpec(pin.toCharArray(), saltBytes, PBKDF2_ITERATIONS, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hash = factory.generateSecret(spec).encoded
        return Base64.encode(hash)
    }

    /**
     * Vérifie un PIN par rapport à un hash stocké avec comparaison à temps constant.
     * Tente PBKDF2 si un sel existe, sinon se rabat sur SHA-256 legacy.
     */
    @OptIn(ExperimentalEncodingApi::class)
    suspend fun verifyPin(pin: String, savedHash: String?, savedSalt: String?): Boolean = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
        if (savedHash == null) return@withContext false
        
        if (savedSalt != null) {
            val computedHash = hashPinPbkdf2(pin, savedSalt)
            // Comparaison à temps constant pour PBKDF2 (Base64)
            java.security.MessageDigest.isEqual(
                Base64.decode(computedHash),
                Base64.decode(savedHash)
            )
        } else {
            val computedHash = hashPin(pin)
            // Comparaison à temps constant pour SHA-256 legacy (Hex)
            java.security.MessageDigest.isEqual(
                computedHash.toByteArray(),
                savedHash.toByteArray()
            )
        }
    }

    suspend fun getDatabaseKey(
        context: Context,
        boutiqueDao: com.reconsiliation.caisse.data.local.dao.BoutiqueDao
    ): ByteArray {
        // 1. Try to get key from Keystore cache first
        getMigratedKey(context)?.let { return it }

        return try {
            // 2. Try to get boutique info from DB
            val boutique = boutiqueDao.getBoutique().first()
                ?: throw Exception("Boutique not configured")
            
            // Validate manager code
            if (boutique.managerCode.length < 12 || 
                boutique.managerCode == "SETUP_REQUIRED_12CHARS" ||
                !boutique.managerCode.matches(Regex("^[a-zA-Z0-9]+$"))) {
                throw Exception("Manager code not yet configured")
            }
            
            deriveNewKey(boutique.phoneNumber, boutique.managerCode)
        } catch (e: Exception) {
            // Fallback for migration: use old ANDROID_ID-based key
            getDatabaseKeyCompat(context)
        }
    }

    fun deriveNewKey(phone: String, managerCode: String): ByteArray {
        val derivationMaterial = buildString {
            append(phone)
            append("|")
            append(managerCode)
            append("|")
            append("CIPHER_SECRET_V1")
        }
        return MessageDigest.getInstance("SHA-256").digest(
            derivationMaterial.toByteArray()
        )
    }

    /**
     * Synchronous version for use during database initialization.
     * Uses Keystore cache.
     */
    fun getDatabaseKeySync(context: Context): ByteArray {
        return getMigratedKey(context) ?: getDatabaseKeyCompat(context)
    }

    // Backward compatibility function (single context parameter)
    fun getDatabaseKeyCompat(context: Context): ByteArray {
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: "DEFAULT_SALT_CAISSE_2024"
        val secret = androidId + "CAISSE_CIPHER_SECRET"
        return MessageDigest.getInstance("SHA-256").digest(secret.toByteArray())
    }
}
