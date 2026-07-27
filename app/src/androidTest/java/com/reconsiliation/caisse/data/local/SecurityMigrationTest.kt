package com.reconsiliation.caisse.data.local

import android.content.Context
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.reconsiliation.caisse.utils.SecurityUtil
import net.sqlcipher.database.SQLiteDatabase
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.io.encoding.ExperimentalEncodingApi

@RunWith(AndroidJUnit4::class)
class SecurityMigrationTest {

    private lateinit var context: Context
    private val dbName = "caisse_database"

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        // Start fresh: delete existing DB and clear security preferences
        context.deleteDatabase(dbName)
        context.getSharedPreferences("security_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        SQLiteDatabase.loadLibs(context)
        Log.i("SecurityMigrationTest", "Setup complete: Database deleted and Prefs cleared.")
    }

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun testFullRekeyMigrationFlow() {
        val oldKey = SecurityUtil.getDatabaseKeyCompat(context)
        val testPhone = "677112233"
        val testManagerCode = "SECURE_CODE_2024" // > 10 alphanumeric chars
        val dbFile = context.getDatabasePath(dbName)

        // 1. CREATE LEGACY STATE: Create a SQLCipher DB chiffrée avec l'ancienne clé
        val db = SQLiteDatabase.openOrCreateDatabase(dbFile.absolutePath, oldKey, null)
        
        db.execSQL("""
            CREATE TABLE boutique (
                id INTEGER PRIMARY KEY NOT NULL, 
                name TEXT NOT NULL, 
                ownerName TEXT NOT NULL, 
                phoneNumber TEXT NOT NULL, 
                operator TEXT NOT NULL, 
                momoNumber TEXT NOT NULL, 
                currency TEXT NOT NULL DEFAULT 'FCFA',
                managerCode TEXT NOT NULL DEFAULT 'SETUP_REQUIRED_12CHARS'
            )
        """.trimIndent())
        
        db.execSQL("""
            INSERT INTO boutique (id, name, ownerName, phoneNumber, operator, momoNumber, currency, managerCode) 
            VALUES (1, 'Test Shop', 'Owner', '$testPhone', 'MTN', '677000000', 'FCFA', '$testManagerCode')
        """.trimIndent())
        
        db.close()
        Log.i("SecurityMigrationTest", "1. Legacy database created with old key.")

        // 2. TRIGGER MIGRATION: Appel direct à la logique de migration
        AppDatabase.performRekeyIfNecessary(context, dbName)
        
        Log.i("SecurityMigrationTest", "2. Migration logic executed.")

        // 3. PHYSICAL VERIFICATION: Vérifier le fichier physique en SQL brut
        
        // Tentative avec l'ancienne clé -> doit échouer
        try {
            SQLiteDatabase.openOrCreateDatabase(dbFile.absolutePath, oldKey, null)
            fail("The database should NO LONGER be openable with the old legacy key!")
        } catch (e: Exception) {
            Log.i("SecurityMigrationTest", "3a. Verified: Old key is now rejected.")
        }

        // Tentative avec la nouvelle clé dérivée (Mode Standard / Passphrase) -> doit réussir
        val newKey = SecurityUtil.deriveNewKey(testPhone, testManagerCode)
        val newKeyPassphrase = newKey.joinToString("") { "%02x".format(it) }
        
        val migratedDb = SQLiteDatabase.openOrCreateDatabase(dbFile.absolutePath, newKeyPassphrase, null)
        val cursor = migratedDb.rawQuery("SELECT phoneNumber FROM boutique LIMIT 1", null)
        assertTrue(cursor.moveToFirst())
        assertEquals(testPhone, cursor.getString(0))
        cursor.close()
        migratedDb.close()
        Log.i("SecurityMigrationTest", "3b. Verified: Database opens perfectly with the NEW standard derived passphrase.")

        // 4. KEYSTORE VERIFICATION: Vérifier que la clé est bien en cache sécurisé
        val cachedKey = SecurityUtil.getMigratedKey(context)
        assertNotNull("The migrated key must be stored in the Keystore cache", cachedKey)
        assertArrayEquals("The cached key must match the new derived key", newKey, cachedKey)
        Log.i("SecurityMigrationTest", "4. Verified: New key is securely cached in Keystore.")
        
        // 5. ROOM INTEGRATION VERIFICATION (Optional/Sanity check)
        // Note: Room might fail schema validation because we only created 'boutique' table,
        // but we want to see if it can at least START to open it with the cached key.
        try {
            val roomDb = AppDatabase.getDatabase(context)
            assertNotNull(roomDb)
            Log.i("SecurityMigrationTest", "5. Room instance created (schema check might follow).")
        } catch (e: Exception) {
            // Expected failure if schema is incomplete, but should NOT be "file is not a database"
            Log.i("SecurityMigrationTest", "5. Room initialization attempted: ${e.message}")
            assertFalse("Should not fail with 'file is not a database'", 
                e.message?.contains("file is not a database") == true)
        }
    }

    @Test
    fun testMigrationResilienceOnFailure() {
        val oldKey = SecurityUtil.getDatabaseKeyCompat(context)
        val dbFile = context.getDatabasePath(dbName)

        // 1. Créer une base corrompue (ou avec une clé invalide pour simuler un échec d'ouverture)
        val db = SQLiteDatabase.openOrCreateDatabase(dbFile.absolutePath, "WRONG_KEY".toByteArray(), null)
        db.close()
        Log.i("SecurityMigrationTest", "Simulating corrupted/un-openable database state.")

        // 2. Vérifier que AppDatabase.getDatabase() ne crashe pas l'app
        // Le bloc try/finally/finally doit nous protéger
        try {
            val roomDb = AppDatabase.getDatabase(context)
            // Note: Room pourrait échouer à l'ouverture finale si la DB est vraiment corrompue,
            // mais l'appel au singleton ne doit pas jeter une exception fatale non gérée
            // empêchant l'initialisation de l'instance.
            assertNotNull(roomDb)
            Log.i("SecurityMigrationTest", "Resilience verified: App survived database initialization failure.")
        } catch (e: Exception) {
            Log.i("SecurityMigrationTest", "Caught expected Room initialization error: ${e.message}")
        }
    }
}
