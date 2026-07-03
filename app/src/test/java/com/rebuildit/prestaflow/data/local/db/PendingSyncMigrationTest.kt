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
 * Test de la migration Room v14 -> v15 ([migration14To15]), qui ajoute `pending_sync.shop_url`
 * (cf. FIX "file offline rejouée contre la mauvaise boutique").
 *
 * Vérifie une VRAIE migration (ALTER TABLE), pas un `fallbackToDestructiveMigration()` — sinon
 * la table `pending_sync` serait détruite en prod à la mise à jour de l'app, ce qui est
 * précisément le risque (M3) que ce test garde-fou empêche de réintroduire.
 *
 * Nécessite Robolectric : Room (2.6.1, pinné dans ce projet) s'appuie sur un vrai moteur SQLite
 * Android, indisponible en JVM pur.
 *
 * `application = Application::class` (pas `PrestaFlowApp`) : évite de démarrer le graphe Hilt
 * complet au boot de Robolectric (`provideEncryptedSharedPreferences` a besoin d'AndroidKeyStore,
 * absent de l'environnement Robolectric) — ce test ne teste QUE la migration Room, en dehors de
 * toute injection de dépendances.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PendingSyncMigrationTest {
    @get:Rule
    val helper: MigrationTestHelper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            PrestaFlowDatabase::class.java,
        )

    @Test
    fun `la migration 14 vers 15 ajoute shop_url sans perte de lignes`() {
        helper.createDatabase(TEST_DB_NAME, VERSION_14).apply {
            execSQL(
                """
                INSERT INTO pending_sync
                    (id, endpoint, method, payload_json, conflict_strategy, attempt_count, created_at_iso)
                VALUES
                    (1, 'products/5/stock', 'PATCH', '{}', 'MERGE', 0, '2026-01-01T00:00:00Z')
                """.trimIndent(),
            )
            close()
        }

        val migrated =
            helper.runMigrationsAndValidate(
                TEST_DB_NAME,
                VERSION_15,
                true,
                migration14To15(FALLBACK_SHOP_URL),
            )

        migrated.query("SELECT id, shop_url FROM pending_sync WHERE id = 1").use { cursor ->
            assertTrue("La ligne créée avant la migration doit survivre", cursor.moveToFirst())
            assertEquals(
                "Le fallback shopUrl fourni à la migration doit être appliqué aux lignes existantes",
                FALLBACK_SHOP_URL,
                cursor.getString(cursor.getColumnIndexOrThrow("shop_url")),
            )
        }
    }

    @Test
    fun `la migration 14 vers 15 laisse shop_url vide sans boutique active connue`() {
        helper.createDatabase(TEST_DB_NAME, VERSION_14).apply {
            execSQL(
                """
                INSERT INTO pending_sync
                    (id, endpoint, method, payload_json, conflict_strategy, attempt_count, created_at_iso)
                VALUES
                    (1, 'orders/1/status', 'PATCH', '{}', 'LAST_WRITE_WINS', 0, '2026-01-01T00:00:00Z')
                """.trimIndent(),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB_NAME, VERSION_15, true, migration14To15(""))

        migrated.query("SELECT shop_url FROM pending_sync WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("", cursor.getString(cursor.getColumnIndexOrThrow("shop_url")))
        }
    }

    private companion object {
        const val TEST_DB_NAME = "pending-sync-migration-test"
        const val VERSION_14 = 14
        const val VERSION_15 = 15
        const val FALLBACK_SHOP_URL = "https://boutique-active.test"
    }
}
