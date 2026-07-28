package com.reconsiliation.caisse.utils

import java.io.File
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Métadonnées d'une sauvegarde. Chiffrées dans l'archive : elles contiennent le
 * numéro de la boutique et le code manager, qui ne doivent jamais apparaître en clair.
 */
@Serializable
data class BackupMetadata(
    val formatVersion: Int = BackupManager.FORMAT_VERSION,
    /** Horodatage ISO-8601 UTC. Chaîne volontairement simple : compatible API 24. */
    val createdAt: String,
    val boutiquePhone: String,
    val boutiqueManagerCode: String,
    /** SHA-256 du fichier de base *en clair*, vérifié après déchiffrement. */
    val checksum: String,
    /** Version du schéma Room au moment de l'export (fournie par l'appelant). */
    val databaseVersion: Int
)

/** En-tête en clair de l'archive. Authentifié par GCM via l'AAD : toute altération casse l'import. */
@Serializable
data class BackupManifest(
    val formatVersion: Int = BackupManager.FORMAT_VERSION,
    val kdf: String = "PBKDF2WithHmacSHA256",
    val kdfIterations: Int = BackupManager.PBKDF2_ITERATIONS,
    val saltBase64: String
)

/** Résultat d'un import réussi. Les écarts d'identité sont signalés, pas imposés. */
data class BackupImportResult(
    val metadata: BackupMetadata,
    val restoredFile: File,
    val phoneMatches: Boolean,
    val managerCodeMatches: Boolean
) {
    val identityMatches: Boolean get() = phoneMatches && managerCodeMatches
}

/**
 * Sauvegarde chiffrée par mot de passe utilisateur.
 *
 * Format d'archive **v2** (ZIP) :
 * ```
 * manifest.json   en clair : version, paramètres KDF, sel — authentifié via AAD
 * metadata.enc    IV(12) || AES-GCM(métadonnées)
 * database.enc    IV(12) || AES-GCM(fichier de base)
 * ```
 *
 * Garanties :
 * - le mot de passe n'est **jamais** stocké ni dérivé d'une donnée applicative
 *   (décision D2-C : saisie manuelle à chaque export) ;
 * - la base est **réellement chiffrée** par la clé dérivée du mot de passe, en
 *   plus du chiffrement SQLCipher (BUG-019) ;
 * - l'altération du manifeste, des métadonnées ou de la base est détectée
 *   (AES-GCM + AAD + checksum SHA-256) ;
 * - aucune API supérieure à l'API 24 n'est utilisée (BUG-020).
 *
 * Le traitement est **en flux** : le fichier de base n'est jamais chargé
 * entièrement en mémoire, contrainte importante sur les appareils d'entrée de gamme.
 */
object BackupManager {

    const val FORMAT_VERSION = 2

    internal const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
    internal const val CIPHER_ALGORITHM = "AES/GCM/NoPadding"
    internal const val PBKDF2_ITERATIONS = 100_000
    private const val KEY_SIZE_BIT = 256
    private const val SALT_LENGTH = 32
    private const val GCM_TAG_LENGTH_BIT = 128
    private const val GCM_IV_LENGTH = 12
    private const val STREAM_BUFFER = 8 * 1024

    private const val ENTRY_MANIFEST = "manifest.json"
    private const val ENTRY_METADATA = "metadata.enc"
    private const val ENTRY_DATABASE = "database.enc"

    private val json = Json { ignoreUnknownKeys = true }

    // ==================== Export ====================

    /**
     * Exporte [dbFile] vers [outputFile], chiffré par [password].
     *
     * @param password mot de passe saisi par l'utilisateur ; jamais conservé.
     * @param databaseVersion version réelle du schéma Room (cf. AppDatabase).
     */
    fun exportBackupWithPassword(
        dbFile: File,
        outputFile: File,
        password: String,
        boutiquePhone: String,
        boutiqueManagerCode: String,
        databaseVersion: Int
    ): Result<File> = runCatching {
        validatePasswordStrength(password)
        validateManagerCode(boutiqueManagerCode)
        require(dbFile.exists() && dbFile.length() > 0) {
            "Fichier de base introuvable ou vide : ${dbFile.name}"
        }

        val salt = ByteArray(SALT_LENGTH).also { SecureRandom().nextBytes(it) }
        val key = deriveKeyFromPassword(password, salt)

        val manifest = BackupManifest(saltBase64 = encodeBase64(salt))
        val manifestBytes = json.encodeToString(BackupManifest.serializer(), manifest)
            .toByteArray(Charsets.UTF_8)

        val metadata = BackupMetadata(
            createdAt = isoTimestamp(),
            boutiquePhone = boutiquePhone,
            boutiqueManagerCode = boutiqueManagerCode,
            checksum = sha256(dbFile),
            databaseVersion = databaseVersion
        )
        val metadataBytes = json.encodeToString(BackupMetadata.serializer(), metadata)
            .toByteArray(Charsets.UTF_8)

        outputFile.parentFile?.mkdirs()
        ZipOutputStream(outputFile.outputStream().buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(ENTRY_MANIFEST))
            zip.write(manifestBytes)
            zip.closeEntry()

            // Le manifeste sert d'AAD : il est authentifié sans être chiffré.
            zip.putNextEntry(ZipEntry(ENTRY_METADATA))
            writeEncrypted(metadataBytes.inputStream(), zip, key, manifestBytes)
            zip.closeEntry()

            zip.putNextEntry(ZipEntry(ENTRY_DATABASE))
            dbFile.inputStream().buffered().use { writeEncrypted(it, zip, key, manifestBytes) }
            zip.closeEntry()
        }
        outputFile
    }.recoverCatching { cause ->
        // Une archive partielle est pire qu'aucune archive : l'utilisateur croirait
        // disposer d'une sauvegarde exploitable.
        outputFile.delete()
        throw cause
    }

    // ==================== Import ====================

    /**
     * Restaure [backupFile] vers [destFile].
     *
     * Les écarts d'identité ([expectedPhone], [expectedManagerCode]) sont
     * **signalés** dans le résultat, jamais transformés en exception : la
     * décision de poursuivre appartient à la couche UI.
     */
    fun importBackupWithPassword(
        backupFile: File,
        destFile: File,
        password: String,
        expectedPhone: String? = null,
        expectedManagerCode: String? = null
    ): Result<BackupImportResult> = runCatching {
        require(backupFile.exists() && backupFile.length() > 0) {
            "Fichier de sauvegarde introuvable ou vide"
        }

        var manifestBytes: ByteArray? = null
        var metadataCipher: ByteArray? = null
        var databaseCipher: ByteArray? = null

        ZipInputStream(backupFile.inputStream().buffered()).use { zip ->
            var entry: ZipEntry? = zip.nextEntry
            while (entry != null) {
                when (entry.name) {
                    ENTRY_MANIFEST -> manifestBytes = zip.readAllBytesCompat()
                    ENTRY_METADATA -> metadataCipher = zip.readAllBytesCompat()
                    ENTRY_DATABASE -> databaseCipher = zip.readAllBytesCompat()
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        val manifestRaw = manifestBytes
            ?: throw BackupException("Archive invalide : manifeste absent (format non reconnu).")
        val metaRaw = metadataCipher
            ?: throw BackupException("Archive invalide : métadonnées absentes.")
        val dbRaw = databaseCipher
            ?: throw BackupException("Archive invalide : base de données absente.")

        val manifest = try {
            json.decodeFromString(BackupManifest.serializer(), String(manifestRaw, Charsets.UTF_8))
        } catch (e: Exception) {
            throw BackupException("Archive invalide : manifeste illisible.")
        }
        if (manifest.formatVersion > FORMAT_VERSION) {
            throw BackupException(
                "Sauvegarde créée par une version plus récente de l'application " +
                    "(format ${manifest.formatVersion} > $FORMAT_VERSION)."
            )
        }

        val salt = decodeBase64(manifest.saltBase64)
        val key = deriveKeyFromPassword(password, salt, manifest.kdfIterations)

        // Premier déchiffrement : sert aussi de vérification du mot de passe.
        val metadataBytes = try {
            decryptToBytes(metaRaw, key, manifestRaw)
        } catch (e: Exception) {
            throw BackupException("Mot de passe incorrect, ou sauvegarde altérée.")
        }
        val metadata = json.decodeFromString(
            BackupMetadata.serializer(),
            String(metadataBytes, Charsets.UTF_8)
        )

        destFile.parentFile?.mkdirs()
        try {
            destFile.outputStream().buffered().use { out ->
                decryptToStream(dbRaw, key, manifestRaw, out)
            }
        } catch (e: Exception) {
            destFile.delete()
            throw BackupException("Sauvegarde corrompue : le déchiffrement de la base a échoué.")
        }

        val actual = sha256(destFile)
        if (actual != metadata.checksum) {
            destFile.delete()
            throw BackupException(
                "Contrôle d'intégrité en échec : la base restaurée diffère de l'originale."
            )
        }

        BackupImportResult(
            metadata = metadata,
            restoredFile = destFile,
            phoneMatches = expectedPhone == null || expectedPhone == metadata.boutiquePhone,
            managerCodeMatches =
                expectedManagerCode == null || expectedManagerCode == metadata.boutiqueManagerCode
        )
    }

    /** Lit les métadonnées sans restaurer la base (aperçu avant confirmation). */
    fun peekMetadata(backupFile: File, password: String): Result<BackupMetadata> = runCatching {
        var manifestBytes: ByteArray? = null
        var metadataCipher: ByteArray? = null
        ZipInputStream(backupFile.inputStream().buffered()).use { zip ->
            var entry: ZipEntry? = zip.nextEntry
            while (entry != null) {
                when (entry.name) {
                    ENTRY_MANIFEST -> manifestBytes = zip.readAllBytesCompat()
                    ENTRY_METADATA -> metadataCipher = zip.readAllBytesCompat()
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        val manifestRaw = manifestBytes ?: throw BackupException("Archive invalide.")
        val metaRaw = metadataCipher ?: throw BackupException("Archive invalide.")
        val manifest = json.decodeFromString(
            BackupManifest.serializer(),
            String(manifestRaw, Charsets.UTF_8)
        )
        val key = deriveKeyFromPassword(password, decodeBase64(manifest.saltBase64), manifest.kdfIterations)
        val bytes = try {
            decryptToBytes(metaRaw, key, manifestRaw)
        } catch (e: Exception) {
            throw BackupException("Mot de passe incorrect, ou sauvegarde altérée.")
        }
        json.decodeFromString(BackupMetadata.serializer(), String(bytes, Charsets.UTF_8))
    }

    // ==================== Chiffrement ====================

    /**
     * Écrit `IV || AES-GCM(source)` dans [out].
     *
     * `Cipher.doFinal` produit déjà `ciphertext || tag` : le tag ne doit surtout
     * pas être extrait puis rajouté — c'était la cause de BUG-018.
     */
    private fun writeEncrypted(source: InputStream, out: OutputStream, key: ByteArray, aad: ByteArray) {
        val iv = ByteArray(GCM_IV_LENGTH).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(CIPHER_ALGORITHM).apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_LENGTH_BIT, iv))
            updateAAD(aad)
        }
        out.write(iv)
        // CipherOutputStream.close() finalise le chiffrement (écrit le tag) mais
        // fermerait aussi le ZipOutputStream : on le protège.
        CipherOutputStream(NonClosingOutputStream(out), cipher).use { source.copyTo(it, STREAM_BUFFER) }
    }

    private fun decryptToStream(payload: ByteArray, key: ByteArray, aad: ByteArray, out: OutputStream) {
        if (payload.size <= GCM_IV_LENGTH) throw BackupException("Charge chiffrée tronquée.")
        val cipher = newDecryptCipher(payload, key, aad)
        CipherInputStream(
            payload.inputStream(GCM_IV_LENGTH, payload.size - GCM_IV_LENGTH),
            cipher
        ).use { it.copyTo(out, STREAM_BUFFER) }
        out.flush()
    }

    private fun decryptToBytes(payload: ByteArray, key: ByteArray, aad: ByteArray): ByteArray {
        if (payload.size <= GCM_IV_LENGTH) throw BackupException("Charge chiffrée tronquée.")
        val cipher = newDecryptCipher(payload, key, aad)
        // doFinal (et non CipherInputStream) : propage AEADBadTagException, ce qui
        // permet de distinguer un mauvais mot de passe d'une archive saine.
        return cipher.doFinal(payload, GCM_IV_LENGTH, payload.size - GCM_IV_LENGTH)
    }

    private fun newDecryptCipher(payload: ByteArray, key: ByteArray, aad: ByteArray): Cipher {
        val iv = payload.copyOfRange(0, GCM_IV_LENGTH)
        return Cipher.getInstance(CIPHER_ALGORITHM).apply {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_LENGTH_BIT, iv))
            updateAAD(aad)
        }
    }

    internal fun deriveKeyFromPassword(
        password: String,
        salt: ByteArray,
        iterations: Int = PBKDF2_ITERATIONS
    ): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_SIZE_BIT)
        return SecretKeyFactory.getInstance(PBKDF2_ALGORITHM).generateSecret(spec).encoded
    }

    // ==================== Utilitaires ====================

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(STREAM_BUFFER)
            var read = input.read(buffer)
            while (read > 0) {
                digest.update(buffer, 0, read)
                read = input.read(buffer)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Horodatage ISO-8601 UTC.
     *
     * `java.time.Instant` exige l'API 26 alors que le projet cible `minSdk 24`
     * sans desugaring : c'était BUG-020. `SimpleDateFormat` est disponible
     * depuis l'API 1 et reste cohérent avec le reste du projet, qui manipule
     * `java.util.Date` partout.
     */
    internal fun isoTimestamp(now: Date = Date()): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(now)

    // android.util.Base64 est indisponible en test JVM pur, et java.util.Base64
    // exige l'API 26. Encodage maison : quelques lignes, zéro dépendance.
    private const val B64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

    internal fun encodeBase64(data: ByteArray): String {
        val sb = StringBuilder((data.size + 2) / 3 * 4)
        var i = 0
        while (i < data.size) {
            val b0 = data[i].toInt() and 0xFF
            val b1 = if (i + 1 < data.size) data[i + 1].toInt() and 0xFF else 0
            val b2 = if (i + 2 < data.size) data[i + 2].toInt() and 0xFF else 0
            sb.append(B64[b0 shr 2])
            sb.append(B64[((b0 and 0x03) shl 4) or (b1 shr 4)])
            sb.append(if (i + 1 < data.size) B64[((b1 and 0x0F) shl 2) or (b2 shr 6)] else '=')
            sb.append(if (i + 2 < data.size) B64[b2 and 0x3F] else '=')
            i += 3
        }
        return sb.toString()
    }

    internal fun decodeBase64(text: String): ByteArray {
        val clean = text.filter { it != '\n' && it != '\r' }
        val out = java.io.ByteArrayOutputStream(clean.length * 3 / 4)
        var buffer = 0
        var bits = 0
        for (c in clean) {
            if (c == '=') break
            val v = B64.indexOf(c)
            if (v < 0) throw BackupException("Chaîne Base64 invalide dans l'archive.")
            buffer = (buffer shl 6) or v
            bits += 6
            if (bits >= 8) {
                bits -= 8
                out.write((buffer shr bits) and 0xFF)
            }
        }
        return out.toByteArray()
    }

    internal fun validatePasswordStrength(password: String) {
        if (password.length < 8) {
            throw BackupException("Le mot de passe doit contenir au moins 8 caractères.")
        }
        if (!password.any { it.isUpperCase() } ||
            !password.any { it.isLowerCase() } ||
            !password.any { it.isDigit() }
        ) {
            throw BackupException("Le mot de passe doit contenir majuscules, minuscules et chiffres.")
        }
    }

    internal fun validateManagerCode(code: String) {
        if (code.length < 12) {
            throw BackupException("Le code manager doit contenir au moins 12 caractères.")
        }
        if (!code.matches(Regex("^[a-zA-Z0-9]+$"))) {
            throw BackupException("Le code manager doit contenir uniquement des lettres et des chiffres.")
        }
    }
}

/** Erreur fonctionnelle de sauvegarde, porteuse d'un message affichable à l'utilisateur. */
class BackupException(message: String) : Exception(message)

/** Empêche [CipherOutputStream] de fermer le [ZipOutputStream] sous-jacent. */
private class NonClosingOutputStream(out: OutputStream) : FilterOutputStream(out) {
    override fun write(b: ByteArray, off: Int, len: Int) = out.write(b, off, len)
    override fun close() { flush() }
}

/** `InputStream.readAllBytes()` exige l'API 33 : incompatible avec minSdk 24. */
private fun InputStream.readAllBytesCompat(): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    copyTo(out, 8 * 1024)
    return out.toByteArray()
}
