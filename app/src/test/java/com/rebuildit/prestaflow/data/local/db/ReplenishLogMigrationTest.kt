package com.rebuildit.prestaflow.data.local.db

import android.app.Application
import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Test de la migration Room v15 -> v16 ([MIGRATION_15_16]), qui crée `replenish_log` (journal
 * persistant de session de réappro, cf. `ReplenishSessionRepository`).
 *
 * Vérifie une VRAIE migration (`CREATE TABLE`), pas un `fallbackToDestructiveMigration()` — sinon
 * `pending_sync` et le cache produits/commandes seraient détruits en prod à la mise à jour de l'app
 * pour ce même saut de version (cf. [PendingSyncMigrationTest] pour le même garde-fou sur v14->v15).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ReplenishLogMigrationTest {
    @get:Rule
    val helper: MigrationTestHelper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            PrestaFlowDatabase::class.java,
        )

    @Test
    fun `la migration 15 vers 16 cree replenish_log et accepte une ligne`() {
        helper.createDatabase(TEST_DB_NAME, VERSION_15).apply { close() }

        val migrated = helper.runMigrationsAndValidate(TEST_DB_NAME, VERSION_16, true, MIGRATION_15_16)

        migrated.execSQL(
            """
            INSERT INTO replenish_log
                (id, product_id, combination_id, warehouse_id, product_name, delta, created_at_iso)
            VALUES
                (1, 42, NULL, NULL, 'Pelote de laine', 3, '2026-08-12T00:00:00Z')
            """.trimIndent(),
        )

        migrated.query("SELECT product_id, delta FROM replenish_log WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(42L, cursor.getLong(cursor.getColumnIndexOrThrow("product_id")))
            assertEquals(3, cursor.getInt(cursor.getColumnIndexOrThrow("delta")))
        }
    }

    private companion object {
        const val TEST_DB_NAME = "replenish-log-migration-test"
        const val VERSION_15 = 15
        const val VERSION_16 = 16
    }
}
