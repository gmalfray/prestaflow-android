package com.rebuildit.prestaflow.di

import javax.inject.Qualifier

/**
 * Client OkHttp dédié à la relecture de la file offline ([com.rebuildit.prestaflow.core.sync.SyncTaskExecutor]).
 *
 * Volontairement dépourvu de `DynamicBaseUrlInterceptor`/`AuthInterceptor` (le client OkHttp
 * partagé qualifié par défaut) : chaque tâche de la file porte SA propre boutique
 * ([com.rebuildit.prestaflow.domain.sync.model.PendingSyncTask.shopUrl]), qui peut différer de
 * la boutique active au moment de l'exécution. Le client partagé réécrirait systématiquement
 * l'hôte et le Bearer vers la boutique active — exactement le bug corrigé par cette
 * séparation.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SyncHttpClient
