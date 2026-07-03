package com.rebuildit.prestaflow.data.local.db

/**
 * Purge du cache local Room, extrait pour permettre l'injection de fakes en test
 * (cf. [com.rebuildit.prestaflow.core.notifications.ShopDeviceRegistrarContract]).
 *
 * Aucune entité Room n'est cloisonnée par boutique : sans purge explicite au changement de
 * boutique active, l'UI peut continuer à afficher (et agir sur) les données de l'ancienne
 * boutique sous l'identité de la nouvelle. La purge évite aussi que des PII (noms, emails,
 * historiques clients) restent en clair dans `prestaflow.db` après un logout.
 */
interface LocalCacheStore {
    /** Vide toutes les tables du cache local (Room `clearAllTables()`). */
    suspend fun clearAll()
}
