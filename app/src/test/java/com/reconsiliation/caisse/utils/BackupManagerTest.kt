package com.reconsiliation.caisse.utils

import java.io.File
import java.io.RandomAccessFile
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests de [BackupManager] — exigés avant tout branchement dans l'application.
 *
 * Couvrent les cinq garanties demandées :
 *  1. une sauvegarde exportée peut toujours être restaurée ;
 *  2. un mauvais mot de passe provoque un échec ;
 *  3. aucune donnée n'est corrompue ;
 *  4. l'intégrité cryptographique est vérifiée ;
 *  5. le code fonctionne sur minSdk 24 (aucune API > 24).
 *
 * `BackupManager` est du Kotlin/JVM pur : ces tests s'exécutent dans Docker,
 * sans appareil Android ni Robolectric.
 */
class BackupManagerTest {

    private lateinit var tmp: File
    private lateinit var dbFile: File

    private val password = "MotDePasse2026"
    private val phone = "677112233"
    private val managerCode = "SECURECODE2026"
    private val dbVersion = 28

    /** Contenu réaliste : en-tête SQLite + charge binaire non compressible. */
    private fun sampleDatabaseBytes(sizeKb: Int = 128): ByteArray {
        val header = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
        val body = ByteArray(sizeKb * 1024)
        java.util.Random(42).nextBytes(body)
        return header + body
    }

    @Before
    fun setUp() {
        tmp = File(System.getProperty("java.io.tmpdir"), "mc-backup-${System.nanoTime()}")
        tmp.mkdirs()
        dbFile = File(tmp, "caisse_database").apply { writeBytes(sampleDatabaseBytes()) }
    }

    @After
    fun tearDown() {
        tmp.deleteRecursively()
    }

    private fun export(
        out: File = File(tmp, "backup.zip"),
        pwd: String = password
    ): Result<File> = BackupManager.exportBackupWithPassword(
        dbFile = dbFile,
        outputFile = out,
        password = pwd,
        boutiquePhone = phone,
        boutiqueManagerCode = managerCode,
        databaseVersion = dbVersion
    )

    private fun import(
        archive: File,
        pwd: String = password,
        dest: File = File(tmp, "restored.db")
    ) = BackupManager.importBackupWithPassword(
        backupFile = archive,
        destFile = dest,
        password = pwd,
        expectedPhone = phone,
        expectedManagerCode = managerCode
    )

    // ================= 1. Aller-retour =================

    @Test
    fun `une sauvegarde exportee peut etre restauree a l identique`() {
        val archive = export().getOrThrow()
        assertTrue("L'archive doit exister", archive.exists())

        val result = import(archive).getOrThrow()

        assertArrayEqualsMsg(
            "Les octets restaurés doivent être identiques à l'original",
            dbFile.readBytes(),
            result.restoredFile.readBytes()
        )
        assertEquals(phone, result.metadata.boutiquePhone)
        assertEquals(managerCode, result.metadata.boutiqueManagerCode)
        assertTrue("L'identité doit correspondre", result.identityMatches)
    }

    @Test
    fun `l aller-retour fonctionne sur une base volumineuse`() {
        dbFile.writeBytes(sampleDatabaseBytes(sizeKb = 4096)) // 4 Mo, traité en flux
        val archive = export().getOrThrow()
        val restored = import(archive).getOrThrow().restoredFile
        assertEquals(dbFile.length(), restored.length())
        assertArrayEqualsMsg("Intégrité sur gros fichier", dbFile.readBytes(), restored.readBytes())
    }

    @Test
    fun `deux exports du meme contenu produisent des archives differentes`() {
        // Sel et IV aléatoires : deux archives identiques révéleraient une
        // réutilisation de nonce, faute critique en GCM.
        val a = export(File(tmp, "a.zip")).getOrThrow().readBytes()
        val b = export(File(tmp, "b.zip")).getOrThrow().readBytes()
        assertFalse("Sel/IV doivent être aléatoires", a.contentEquals(b))
    }

    // ================= 2. Mauvais mot de passe =================

    @Test
    fun `un mauvais mot de passe fait echouer la restauration`() {
        val archive = export().getOrThrow()
        val result = import(archive, pwd = "MauvaisPass99")

        assertTrue("L'import doit échouer", result.isFailure)
        val message = result.exceptionOrNull()?.message.orEmpty()
        assertTrue(
            "Le message doit mentionner le mot de passe (obtenu : $message)",
            message.contains("Mot de passe", ignoreCase = true)
        )
    }

    @Test
    fun `un mauvais mot de passe ne laisse aucun fichier restaure`() {
        val archive = export().getOrThrow()
        val dest = File(tmp, "restored.db")
        import(archive, pwd = "MauvaisPass99", dest = dest)
        assertFalse("Aucune donnée partielle ne doit subsister", dest.exists())
    }

    @Test
    fun `un mot de passe correct a un caractere pres echoue`() {
        val archive = export().getOrThrow()
        assertTrue(import(archive, pwd = password + "x").isFailure)
        assertTrue(import(archive, pwd = password.dropLast(1)).isFailure)
        assertTrue(import(archive, pwd = password.lowercase()).isFailure)
    }

    // ================= 3. Aucune donnée corrompue =================

    @Test
    fun `la base n est pas stockee en clair dans l archive`() {
        // Cœur de BUG-019 : la base doit être chiffrée par la clé du mot de passe,
        // pas seulement par SQLCipher.
        val archive = export().getOrThrow()
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(archive.inputStream()).use { zip ->
            var e = zip.nextEntry
            while (e != null) {
                entries[e.name] = zip.readBytes()
                zip.closeEntry()
                e = zip.nextEntry
            }
        }

        val dbEntry = entries["database.enc"]
        requireNotNull(dbEntry) { "L'entrée database.enc doit exister" }

        val plain = dbFile.readBytes()
        assertFalse(
            "La base ne doit pas apparaître en clair dans l'archive",
            dbEntry.contentEquals(plain)
        )
        val header = "SQLite format 3".toByteArray(Charsets.US_ASCII)
        assertFalse("L'en-tête SQLite ne doit pas être lisible", indexOf(dbEntry, header) >= 0)
        assertFalse(
            "Le code manager ne doit pas fuiter dans les métadonnées",
            indexOf(entries["metadata.enc"]!!, managerCode.toByteArray()) >= 0
        )
    }

    @Test
    fun `l export echoue proprement sans laisser d archive partielle`() {
        val out = File(tmp, "faible.zip")
        val result = export(out = out, pwd = "faible") // ne respecte pas la politique
        assertTrue(result.isFailure)
        assertFalse("Aucune archive ne doit subsister après échec", out.exists())
    }

    @Test
    fun `une base vide est refusee`() {
        dbFile.writeBytes(ByteArray(0))
        assertTrue("Une base vide ne doit pas être sauvegardée", export().isFailure)
    }

    // ================= 4. Intégrité cryptographique =================

    @Test
    fun `une base alteree dans l archive est detectee`() {
        val archive = export().getOrThrow()
        val tampered = tamper(archive, "database.enc") { bytes ->
            bytes.copyOf().also { it[it.size / 2] = (it[it.size / 2].toInt() xor 0xFF).toByte() }
        }
        val result = import(tampered)
        assertTrue("L'altération doit être détectée", result.isFailure)
    }

    @Test
    fun `des metadonnees alterees sont detectees`() {
        val archive = export().getOrThrow()
        val tampered = tamper(archive, "metadata.enc") { bytes ->
            bytes.copyOf().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 0x01).toByte() }
        }
        assertTrue("Le tag GCM doit rejeter l'altération", import(tampered).isFailure)
    }

    @Test
    fun `un manifeste altere est detecte via l AAD`() {
        // Le manifeste est en clair mais authentifié : le modifier doit invalider
        // le déchiffrement, même si le mot de passe est correct.
        val archive = export().getOrThrow()
        val tampered = tamper(archive, "manifest.json") { bytes ->
            String(bytes).replace("\"kdfIterations\":100000", "\"kdfIterations\":1000")
                .toByteArray()
        }
        assertTrue("Un manifeste modifié doit invalider l'archive", import(tampered).isFailure)
    }

    @Test
    fun `une archive tronquee est rejetee`() {
        val archive = export().getOrThrow()
        RandomAccessFile(archive, "rw").use { it.setLength(archive.length() / 2) }
        assertTrue(import(archive).isFailure)
    }

    @Test
    fun `un fichier qui n est pas une archive est rejete`() {
        val bogus = File(tmp, "pas_une_archive.zip").apply { writeText("ceci n'est pas un zip") }
        assertTrue(import(bogus).isFailure)
    }

    @Test
    fun `une archive sans manifeste est rejetee`() {
        val archive = File(tmp, "incomplete.zip")
        ZipOutputStream(archive.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("database.enc"))
            zip.write(ByteArray(64))
            zip.closeEntry()
        }
        val result = import(archive)
        assertTrue(result.isFailure)
        assertTrue(
            result.exceptionOrNull()?.message.orEmpty().contains("manifeste", ignoreCase = true)
        )
    }

    // ================= 5. Compatibilité minSdk 24 =================

    @Test
    fun `aucune API superieure a l API 24 n est utilisee`() {
        // BUG-020 : java.time.Instant (API 26), java.util.Base64 (API 26),
        // InputStream.readAllBytes (API 33) sont interdits.
        val source = File("src/main/java/com/reconsiliation/caisse/utils/BackupManager.kt")
            .takeIf { it.exists() }
            ?: File("app/src/main/java/com/reconsiliation/caisse/utils/BackupManager.kt")
        assertTrue("Source introuvable : ${source.absolutePath}", source.exists())

        val text = source.readText()
        listOf(
            "java.time.",
            "java.util.Base64",
            "readAllBytes()",
            "java.nio.file.Files"
        ).forEach { forbidden ->
            assertFalse(
                "API incompatible minSdk 24 détectée : $forbidden",
                text.contains(forbidden)
            )
        }
    }

    @Test
    fun `l horodatage est au format ISO-8601 UTC`() {
        val stamp = BackupManager.isoTimestamp(java.util.Date(0L))
        assertEquals("1970-01-01T00:00:00Z", stamp)
        assertTrue(stamp.matches(Regex("""\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z""")))
    }

    @Test
    fun `l encodage Base64 maison est conforme au standard`() {
        // Vecteurs de la RFC 4648 §10 : référence indépendante de toute
        // implémentation (java.util.Base64 est interdit ici — API 26).
        val vectors = mapOf(
            "" to "",
            "f" to "Zg==",
            "fo" to "Zm8=",
            "foo" to "Zm9v",
            "foob" to "Zm9vYg==",
            "fooba" to "Zm9vYmE=",
            "foobar" to "Zm9vYmFy"
        )
        vectors.forEach { (input, expected) ->
            val encoded = BackupManager.encodeBase64(input.toByteArray())
            assertEquals("Encodage RFC 4648 de « $input »", expected, encoded)
            assertArrayEqualsMsg(
                "Aller-retour Base64 de « $input »",
                input.toByteArray(),
                BackupManager.decodeBase64(encoded)
            )
        }
    }

    @Test
    fun `l aller-retour Base64 fonctionne sur des donnees binaires aleatoires`() {
        val rnd = java.util.Random(7)
        repeat(50) {
            val data = ByteArray(rnd.nextInt(200)).also { rnd.nextBytes(it) }
            assertArrayEqualsMsg(
                "Aller-retour binaire",
                data,
                BackupManager.decodeBase64(BackupManager.encodeBase64(data))
            )
        }
    }

    // ================= Métadonnées & politique =================

    @Test
    fun `la version du schema est celle transmise et non une valeur figee`() {
        // BUG-021 : databaseVersion était figé à 27 alors que le schéma est en 28.
        val archive = export().getOrThrow()
        val metadata = BackupManager.peekMetadata(archive, password).getOrThrow()
        assertEquals(28, metadata.databaseVersion)
        assertNotEquals("La valeur ne doit plus être figée à 27", 27, metadata.databaseVersion)
    }

    @Test
    fun `peekMetadata lit les metadonnees sans restaurer la base`() {
        val archive = export().getOrThrow()
        val metadata = BackupManager.peekMetadata(archive, password).getOrThrow()
        assertEquals(phone, metadata.boutiquePhone)
        assertEquals(BackupManager.FORMAT_VERSION, metadata.formatVersion)
        assertFalse(File(tmp, "restored.db").exists())
    }

    @Test
    fun `peekMetadata echoue avec un mauvais mot de passe`() {
        val archive = export().getOrThrow()
        assertTrue(BackupManager.peekMetadata(archive, "MauvaisPass99").isFailure)
    }

    @Test
    fun `un ecart d identite est signale sans bloquer la restauration`() {
        // Choix de conception : la couche UI décide, le module informe.
        val archive = export().getOrThrow()
        val result = BackupManager.importBackupWithPassword(
            backupFile = archive,
            destFile = File(tmp, "restored.db"),
            password = password,
            expectedPhone = "699999999",
            expectedManagerCode = managerCode
        ).getOrThrow()

        assertFalse("L'écart de numéro doit être signalé", result.phoneMatches)
        assertTrue(result.managerCodeMatches)
        assertFalse(result.identityMatches)
        assertTrue("La restauration doit tout de même aboutir", result.restoredFile.exists())
    }

    @Test
    fun `la politique de mot de passe est appliquee`() {
        listOf(
            "court1A" to "trop court",
            "minuscules1" to "sans majuscule",
            "MAJUSCULES1" to "sans minuscule",
            "SansChiffres" to "sans chiffre"
        ).forEach { (pwd, raison) ->
            val out = File(tmp, "pol_${pwd.hashCode()}.zip")
            assertTrue("Doit être refusé ($raison) : $pwd", export(out = out, pwd = pwd).isFailure)
        }
        assertTrue(export(File(tmp, "ok.zip"), "ValidePass1").isSuccess)
    }

    @Test
    fun `un code manager invalide est refuse`() {
        val result = BackupManager.exportBackupWithPassword(
            dbFile = dbFile,
            outputFile = File(tmp, "b.zip"),
            password = password,
            boutiquePhone = phone,
            boutiqueManagerCode = "court", // < 12 caractères
            databaseVersion = dbVersion
        )
        assertTrue(result.isFailure)
    }

    @Test
    fun `la derivation de cle est deterministe et sensible au sel`() {
        val salt = ByteArray(32) { it.toByte() }
        val other = ByteArray(32) { (it + 1).toByte() }
        val k1 = BackupManager.deriveKeyFromPassword(password, salt, iterations = 1000)
        val k2 = BackupManager.deriveKeyFromPassword(password, salt, iterations = 1000)
        val k3 = BackupManager.deriveKeyFromPassword(password, other, iterations = 1000)

        assertArrayEqualsMsg("Déterminisme", k1, k2)
        assertFalse("Un sel différent doit produire une clé différente", k1.contentEquals(k3))
        assertEquals("Clé de 256 bits", 32, k1.size)
    }

    // ================= Utilitaires de test =================

    private fun tamper(archive: File, entryName: String, transform: (ByteArray) -> ByteArray): File {
        val entries = LinkedHashMap<String, ByteArray>()
        ZipInputStream(archive.inputStream()).use { zip ->
            var e = zip.nextEntry
            while (e != null) {
                entries[e.name] = zip.readBytes()
                zip.closeEntry()
                e = zip.nextEntry
            }
        }
        entries[entryName] = transform(entries.getValue(entryName))

        val out = File(tmp, "tampered-${System.nanoTime()}.zip")
        ZipOutputStream(out.outputStream()).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out
    }

    private fun indexOf(haystack: ByteArray, needle: ByteArray): Int {
        if (needle.isEmpty() || haystack.size < needle.size) return -1
        outer@ for (i in 0..haystack.size - needle.size) {
            for (j in needle.indices) if (haystack[i + j] != needle[j]) continue@outer
            return i
        }
        return -1
    }

    private fun assertArrayEqualsMsg(message: String, expected: ByteArray, actual: ByteArray) {
        assertEquals("$message — taille", expected.size, actual.size)
        assertTrue(message, expected.contentEquals(actual))
    }
}
