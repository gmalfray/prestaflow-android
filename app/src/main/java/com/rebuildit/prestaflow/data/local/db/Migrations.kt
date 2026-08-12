package com.rebuildit.prestaflow.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v14 → v15 : ajoute `pending_sync.shop_url`.
 *
 * Avant ce correctif, `PendingSyncEntity` ne stockait aucune boutique : une écriture
 * enfilée hors-ligne sur la boutique A était rejouée par [com.rebuildit.prestaflow.core.sync.SyncWorker]
 * contre la boutique **active au moment de l'exécution**, potentiellement B après un
 * changement de boutique entre-temps. La colonne est désormais renseignée **au moment de
 * l'enfilement** de la tâche (cf. `SyncQueueRepositoryImpl.enqueue`) et utilisée par
 * `SyncWorker` pour router chaque tâche vers SA boutique d'origine.
 *
 * [fallbackShopUrl] est appliqué aux lignes déjà en file au moment de la migration (créées
 * avant ce correctif) : ces tâches n'ont pu être enfilées que pendant que cette boutique
 * était active (mono-cible avant ce fix), c'est donc la meilleure estimation disponible.
 * Chaîne vide si aucune boutique n'était active (rare) : `SyncWorker` traite alors la tâche
 * comme "boutique inconnue" et l'abandonne proprement (trace Timber), plutôt que de la
 * rejouer sur la mauvaise boutique.
 */
fun migration14To15(fallbackShopUrl: String): Migration =
    object : Migration(DB_VERSION_14, DB_VERSION_15) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE pending_sync ADD COLUMN shop_url TEXT NOT NULL DEFAULT ''")
            if (fallbackShopUrl.isNotBlank()) {
                db.execSQL(
                    "UPDATE pending_sync SET shop_url = ? WHERE shop_url = ''",
                    arrayOf(fallbackShopUrl),
                )
            }
        }
    }

/**
 * v15 → v16 : crée `replenish_log` (journal de session de réappro, cf.
 * `com.rebuildit.prestaflow.domain.products.ReplenishSessionRepository`) — table neuve, aucune
 * donnée existante à préserver, mais une vraie migration reste nécessaire pour ne PAS déclencher
 * `fallbackToDestructiveMigration()` (qui détruirait `pending_sync` et le cache produits/commandes
 * en cours de route, cf. migration14To15 pour le même garde-fou).
 */
val MIGRATION_15_16: Migration =
    object : Migration(DB_VERSION_15, DB_VERSION_16) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `replenish_log` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `product_id` INTEGER NOT NULL,
                    `combination_id` INTEGER,
                    `warehouse_id` INTEGER,
                    `product_name` TEXT NOT NULL,
                    `delta` INTEGER NOT NULL,
                    `created_at_iso` TEXT NOT NULL
                )
                """.trimIndent(),
            )
        }
    }

private const val DB_VERSION_14 = 14
private const val DB_VERSION_15 = 15
private const val DB_VERSION_16 = 16
