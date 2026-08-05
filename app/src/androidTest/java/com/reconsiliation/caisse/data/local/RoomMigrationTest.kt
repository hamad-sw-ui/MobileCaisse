package com.reconsiliation.caisse.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class RoomMigrationTest {
    private val TEST_DB = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    @Throws(IOException::class)
    fun migrateAll() {
        // Create earliest version of the database if available. 
        // Here we start from 28 because we only have schemas from 28+.
        // To test previous ones, we would need 18.json, 19.json, etc.
        helper.createDatabase(TEST_DB, 28).apply {
            close()
        }

        // Open latest version of the database. Room will validate the schema
        // and execute all necessary migrations.
        AppDatabase.getDatabase(InstrumentationRegistry.getInstrumentation().targetContext).apply {
            openHelper.writableDatabase.close()
        }
    }
}
