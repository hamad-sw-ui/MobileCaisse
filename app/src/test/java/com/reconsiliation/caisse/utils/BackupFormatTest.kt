package com.reconsiliation.caisse.utils

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests de [BackupFormat] — risque R1 de l'analyse d'impact : confondre une
 * archive chiffrée avec une base brute corromprait les données.
 */
class BackupFormatTest {

    private lateinit var tmp: File

    @Before
    fun setUp() {
        tmp = File(System.getProperty("java.io.tmpdir"), "mc-format-${System.nanoTime()}")
        tmp.mkdirs()
    }

    @After
    fun tearDown() {
        tmp.deleteRecursively()
    }

    private fun zipFile(name: String = "a.zip"): File =
        File(tmp, name).also { f ->
            ZipOutputStream(f.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("manifest.json"))
                zip.write("{}".toByteArray())
                zip.closeEntry()
            }
        }

    /** Une base SQLCipher est intégralement chiffrée : contenu pseudo-aléatoire. */
    private fun rawDbFile(name: String = "caisse_database", sizeKb: Int = 8): File =
        File(tmp, name).also { f ->
            val data = ByteArray(sizeKb * 1024)
            java.util.Random(1).nextBytes(data)
            f.writeBytes(data)
        }

    @Test
    fun `une archive ZIP est reconnue comme chiffree`() {
        assertEquals(BackupFormat.Encrypted, BackupFormat.detect(zipFile()))
    }

    @Test
    fun `une base brute est reconnue comme legacy`() {
        assertEquals(BackupFormat.LegacyRaw, BackupFormat.detect(rawDbFile()))
    }

    @Test
    fun `l extension ne dicte pas la detection`() {
        // Cœur du risque R1 : l'utilisateur peut renommer ses fichiers.
        val zipNommeDb = zipFile("sauvegarde.db")
        assertEquals(BackupFormat.Encrypted, BackupFormat.detect(zipNommeDb))

        val dbNommeZip = rawDbFile("sauvegarde.zip")
        assertEquals(BackupFormat.LegacyRaw, BackupFormat.detect(dbNommeZip))
    }

    @Test
    fun `un fichier inexistant est inconnu`() {
        val r = BackupFormat.detect(File(tmp, "absent.zip"))
        assertTrue(r is BackupFormat.Unknown)
    }

    @Test
    fun `un fichier vide est inconnu`() {
        val f = File(tmp, "vide.db").apply { writeBytes(ByteArray(0)) }
        assertTrue(BackupFormat.detect(f) is BackupFormat.Unknown)
    }

    @Test
    fun `un fichier trop court est inconnu`() {
        val f = File(tmp, "court.db").apply { writeBytes(byteArrayOf(1, 2)) }
        assertTrue(BackupFormat.detect(f) is BackupFormat.Unknown)
    }

    @Test
    fun `un fichier plus petit qu une page SQLite est inconnu`() {
        val f = File(tmp, "mini.db").apply { writeBytes(ByteArray(100) { it.toByte() }) }
        val r = BackupFormat.detect(f)
        assertTrue("Attendu Unknown, obtenu $r", r is BackupFormat.Unknown)
    }

    @Test
    fun `du texte de taille suffisante est traite comme une base brute`() {
        // Une base SQLCipher est indiscernable d'un flux aléatoire : la détection
        // ne peut trancher que par élimination. L'ouverture échouera ensuite,
        // proprement — comportement documenté et assumé.
        val f = File(tmp, "texte.db").apply { writeText("x".repeat(1024)) }
        assertEquals(BackupFormat.LegacyRaw, BackupFormat.detect(f))
    }

    @Test
    fun `les extensions recommandees sont coherentes`() {
        assertEquals("zip", BackupFormat.extensionFor(BackupFormat.Encrypted))
        assertEquals("db", BackupFormat.extensionFor(BackupFormat.LegacyRaw))
        assertEquals("bin", BackupFormat.extensionFor(BackupFormat.Unknown("test")))
    }
}
