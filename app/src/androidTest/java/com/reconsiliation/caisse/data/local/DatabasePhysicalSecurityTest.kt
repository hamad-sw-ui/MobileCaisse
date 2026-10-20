package com.reconsiliation.caisse.data.local

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.reconsiliation.caisse.data.repository.MainRepository
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class DatabasePhysicalSecurityTest {

    private lateinit var context: Context
    private lateinit var repository: MainRepository
    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        // On s'assure d'avoir une base propre pour le test
        context.deleteDatabase("caisse_database")
        db = AppDatabase.getDatabase(context)
        repository = MainRepository(db, context, com.reconsiliation.caisse.data.repository.InventoryRepository(db))
    }

    @Test
    fun testRestoreDatabaseWithStaging() {
        val dbFile = context.getDatabasePath("caisse_database")
        assertTrue("La base de données doit exister", dbFile.exists())

        // 1. Créer un fichier de "sauvegarde" bidon
        val backupFile = File(context.cacheDir, "test_backup.db")
        val content = "Contenu de test pour la restauration".toByteArray()
        FileOutputStream(backupFile).use { it.write(content) }

        // 2. Exécuter la restauration
        val result = repository.restoreDatabase(context, backupFile)
        assertTrue("La restauration doit réussir", result)

        // 3. Vérifier que le fichier final a le bon contenu
        assertArrayEquals("Le contenu de la base doit correspondre à la sauvegarde", 
            content, dbFile.readBytes())
        
        // 4. Vérifier que le staging a été purgé
        val staging = File(context.cacheDir, "restore_staging.db")
        assertFalse("Le fichier de staging doit être supprimé", staging.exists())
    }

    @Test
    fun testRestoreDatabasePorgesWalAndShm() {
        // Simuler la présence de fichiers WAL et SHM
        val walFile = File(context.getDatabasePath("caisse_database").parent, "caisse_database-wal")
        val shmFile = File(context.getDatabasePath("caisse_database").parent, "caisse_database-shm")
        walFile.writeText("old wal")
        shmFile.writeText("old shm")
        
        val backupFile = File(context.cacheDir, "test_backup_2.db")
        backupFile.writeText("new db content")

        repository.restoreDatabase(context, backupFile)

        assertFalse("Le fichier WAL doit être supprimé", walFile.exists())
        assertFalse("Le fichier SHM doit être supprimé", shmFile.exists())
    }

    @Test
    fun testRestoreResilienceOnStagingFailure() {
        val dbFile = context.getDatabasePath("caisse_database")
        val originalContent = dbFile.readBytes()
        
        // Simuler un échec en passant un fichier source inexistant
        val nonExistentFile = File(context.cacheDir, "missing.db")
        
        val result = repository.restoreDatabase(context, nonExistentFile)
        
        assertFalse("La restauration doit échouer", result)
        assertArrayEquals("La base originale doit être intacte", originalContent, dbFile.readBytes())
    }
}
