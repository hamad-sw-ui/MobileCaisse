package com.reconsiliation.caisse.utils

import android.content.Context
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

@Serializable
data class BackupMetadata(
    val version: String = "1.0",
    val timestamp: String,
    val boutiquePhone: String,
    val boutiqueManagerCode: String,
    val checksum: String,
    val databaseVersion: Int = 27
)

object BackupManager {
    private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val CIPHER_ALGORITHM = "AES/GCM/NoPadding"
    private const val KEY_SIZE = 256
    private const val PBKDF2_ITERATIONS = 100000
    private const val SALT_LENGTH = 32
    private const val GCM_TAG_LENGTH_BIT = 128
    private const val GCM_IV_LENGTH_BIT = 96
    
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Export backup with password protection
     * @param context Android context
     * @param dbFile Path to database file to backup
     * @param outputFile Where to save backup ZIP
     * @param password User-provided password (NOT stored)
     * @param boutiquePhone Boutique phone number
     * @param boutiqueManagerCode Manager code (min 12 alphanumeric)
     * @throws Exception if password is weak or backup fails
     */
    suspend fun exportBackupWithPassword(
        context: Context,
        dbFile: File,
        outputFile: File,
        password: String,
        boutiquePhone: String,
        boutiqueManagerCode: String
    ): Result<Unit> = runCatching {
        // Validate password strength
        validatePasswordStrength(password)
        
        // Validate manager code
        validateManagerCode(boutiqueManagerCode)
        
        // Generate random salt
        val salt = ByteArray(SALT_LENGTH)
        SecureRandom().nextBytes(salt)
        
        // Derive backup encryption key from password
        val backupKey = deriveKeyFromPassword(password, salt)
        
        // Prepare metadata
        val metadata = BackupMetadata(
            version = "1.0",
            timestamp = Instant.now().toString(),
            boutiquePhone = boutiquePhone,
            boutiqueManagerCode = boutiqueManagerCode,
            checksum = calculateFileChecksum(dbFile),
            databaseVersion = 27
        )
        
        // Encrypt metadata
        val metadataJson = json.encodeToString(metadata)
        val metadataBytes = metadataJson.toByteArray(Charsets.UTF_8)
        val encryptedMetadata = encryptAesGcm(metadataBytes, backupKey)
        
        // Read database file
        val dbBytes = dbFile.readBytes()
        
        // Create ZIP with all components
        ZipOutputStream(outputFile.outputStream().buffered()).use { zip ->
            // Add salt (plaintext, needed for key derivation on restore)
            zip.putNextEntry(ZipEntry("salt.bin"))
            zip.write(salt)
            zip.closeEntry()
            
            // Add IV (plaintext, needed for decryption)
            zip.putNextEntry(ZipEntry("metadata_iv.bin"))
            zip.write(encryptedMetadata.iv)
            zip.closeEntry()
            
            // Add encrypted metadata
            zip.putNextEntry(ZipEntry("metadata.bin"))
            zip.write(encryptedMetadata.ciphertext)
            zip.closeEntry()
            
            // Add GCM tag (authentication tag)
            zip.putNextEntry(ZipEntry("metadata_tag.bin"))
            zip.write(encryptedMetadata.tag)
            zip.closeEntry()
            
            // Add encrypted database
            zip.putNextEntry(ZipEntry("database.db"))
            zip.write(dbBytes)
            zip.closeEntry()
        }
        
        Result.success(Unit)
    }

    /**
     * Import backup with password verification
     * @param backupFile ZIP file to restore from
     * @param password User-provided password for decryption
     * @throws Exception if password is wrong or backup is corrupted
     */
    suspend fun importBackupWithPassword(
        backupFile: File,
        password: String,
        currentBoutiquePhone: String,
        currentBoutiqueManagerCode: String
    ): Result<BackupImportResult> = runCatching {
        var salt: ByteArray? = null
        var metadataIv: ByteArray? = null
        var metadataTag: ByteArray? = null
        var encryptedMetadata: ByteArray? = null
        var dbBytes: ByteArray? = null
        
        // Read ZIP entries
        ZipInputStream(backupFile.inputStream().buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val data = zip.readBytes()
                when (entry.name) {
                    "salt.bin" -> salt = data
                    "metadata_iv.bin" -> metadataIv = data
                    "metadata_tag.bin" -> metadataTag = data
                    "metadata.bin" -> encryptedMetadata = data
                    "database.db" -> dbBytes = data
                }
                entry = zip.nextEntry
            }
        }
        
        // Validate all required components
        if (salt == null || metadataIv == null || metadataTag == null || 
            encryptedMetadata == null || dbBytes == null) {
            throw Exception("Fichier de sauvegarde invalide ou corrompu")
        }
        
        // Derive backup key using password
        val backupKey = deriveKeyFromPassword(password, salt!!)
        
        // Decrypt metadata
        val decryptedMetadataBytes = try {
            decryptAesGcm(
                ciphertext = encryptedMetadata!!,
                iv = metadataIv!!,
                tag = metadataTag!!,
                key = backupKey
            )
        } catch (e: Exception) {
            throw Exception("Mot de passe incorrect ou sauvegarde corrompue")
        }
        
        // Parse metadata
        val metadataJson = String(decryptedMetadataBytes, Charsets.UTF_8)
        val metadata = json.decodeFromString<BackupMetadata>(metadataJson)
        
        // Validate boutique info
        if (metadata.boutiquePhone != currentBoutiquePhone) {
            throw Exception("""
                La sauvegarde provient d'une boutique différente:
                Actuelle: $currentBoutiquePhone
                Sauvegarde: ${metadata.boutiquePhone}
                
                Options:
                1. Annuler la restauration
                2. Mettre à jour les données de la boutique
            """)
        }
        
        if (metadata.boutiqueManagerCode != currentBoutiqueManagerCode) {
            throw Exception("""
                Le code manager a changé depuis la sauvegarde.
                
                La restauration réinitialisera votre code manager.
                Continuer?
            """)
        }
        
        BackupImportResult(
            database = dbBytes!!,
            metadata = metadata,
            isValid = true
        )
    }

    // ==================== Helper Functions ====================
    
    private fun deriveKeyFromPassword(
        password: String,
        salt: ByteArray
    ): ByteArray {
        val keyFactory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        val spec = PBEKeySpec(
            password.toCharArray(),
            salt,
            PBKDF2_ITERATIONS,
            KEY_SIZE
        )
        return keyFactory.generateSecret(spec).encoded
    }

    private data class EncryptedData(
        val ciphertext: ByteArray,
        val iv: ByteArray,
        val tag: ByteArray
    )

    private fun encryptAesGcm(plaintext: ByteArray, key: ByteArray): EncryptedData {
        val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
        val secretKey = SecretKeySpec(key, 0, key.size, "AES")
        
        // Generate random IV
        val iv = ByteArray(GCM_IV_LENGTH_BIT / 8)
        SecureRandom().nextBytes(iv)
        
        // Initialize cipher in encrypt mode
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BIT, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)
        
        // Encrypt plaintext
        val ciphertext = cipher.doFinal(plaintext)
        
        // Extract tag (last 16 bytes)
        val tag = ciphertext.takeLast(GCM_TAG_LENGTH_BIT / 8).toByteArray()
        
        return EncryptedData(
            ciphertext = ciphertext,
            iv = iv,
            tag = tag
        )
    }

    private fun decryptAesGcm(
        ciphertext: ByteArray,
        iv: ByteArray,
        tag: ByteArray,
        key: ByteArray
    ): ByteArray {
        val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
        val secretKey = SecretKeySpec(key, 0, key.size, "AES")
        
        // Initialize cipher in decrypt mode
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BIT, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)
        
        // Combine ciphertext + tag for GCM decryption
        val input = ciphertext + tag
        
        // Decrypt and authenticate
        return cipher.doFinal(input)
    }

    private fun calculateFileChecksum(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } > 0) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun validatePasswordStrength(password: String) {
        if (password.length < 8) {
            throw Exception("Le mot de passe doit contenir au moins 8 caractères")
        }
        if (!password.any { it.isUpperCase() } || !password.any { it.isLowerCase() } ||
            !password.any { it.isDigit() }) {
            throw Exception("Le mot de passe doit contenir majuscules, minuscules et chiffres")
        }
    }

    private fun validateManagerCode(code: String) {
        if (code.length < 12) {
            throw Exception("Le code manager doit contenir au moins 12 caractères")
        }
        if (!code.matches(Regex("^[a-zA-Z0-9]+$"))) {
            throw Exception("Le code manager doit contenir uniquement des lettres et chiffres")
        }
    }
}

data class BackupImportResult(
    val database: ByteArray,
    val metadata: BackupMetadata,
    val isValid: Boolean
)
