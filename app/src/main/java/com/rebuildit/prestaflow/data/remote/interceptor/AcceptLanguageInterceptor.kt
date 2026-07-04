package com.rebuildit.prestaflow.data.remote.interceptor

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ajoute le header `Accept-Language` sur chaque requête vers l'API du connecteur, avec le tag de
 * langue primaire (ex. `fr`, `de`, `en` — pas de sous-tag région, pas de `q=`) de la langue
 * d'affichage courante de l'app, pour que le contenu localisé côté serveur (statuts de commande
 * notamment) revienne dans la bonne langue.
 *
 * ⚠️ La locale est lue depuis `context.resources.configuration.locales` à CHAQUE `intercept()`
 * (pas de cache en champ de classe) : elle change dynamiquement quand l'utilisateur bascule la
 * langue in-app, potentiellement sans que ce composant Hilt singleton soit recréé.
 *
 * ⚠️ On NE PAS utiliser `AppCompatDelegate.getApplicationLocales()` seul : il retourne une liste
 * vide en mode « Système (auto) ». `resources.configuration.locales` reflète toujours la locale
 * réellement appliquée à l'UI, per-app override compris.
 */
@Singleton
class AcceptLanguageInterceptor
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            if (request.header(ACCEPT_LANGUAGE_HEADER) != null) {
                // Un composant en amont a déjà posé le header explicitement : on ne l'écrase pas.
                return chain.proceed(request)
            }

            val newRequest =
                request.newBuilder()
                    .header(ACCEPT_LANGUAGE_HEADER, primaryLanguageTag())
                    .build()
            return chain.proceed(newRequest)
        }

        private fun primaryLanguageTag(): String {
            val locale = context.resources.configuration.locales.get(0)
            return locale.toLanguageTag().substringBefore('-').lowercase()
        }

        private companion object {
            const val ACCEPT_LANGUAGE_HEADER = "Accept-Language"
        }
    }
