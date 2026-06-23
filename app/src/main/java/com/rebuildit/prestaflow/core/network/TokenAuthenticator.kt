package com.rebuildit.prestaflow.core.network

import com.rebuildit.prestaflow.core.security.TokenManager
import com.rebuildit.prestaflow.domain.auth.AuthRepository
import dagger.Lazy
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sur un `401`, tente un re-login silencieux de la boutique active (via sa clé API conservée)
 * puis rejoue la requête avec le nouveau jeton. Rend l'expiration du jeton (TTL court côté
 * module, 1 h par défaut) totalement transparente — l'utilisateur n'est jamais redéconnecté.
 */
@Singleton
class TokenAuthenticator
    @Inject
    constructor(
        // Lazy : casse le cycle Hilt OkHttpClient → Authenticator → AuthRepository → api → OkHttpClient.
        private val authRepository: Lazy<AuthRepository>,
        private val tokenManager: TokenManager,
    ) : Authenticator {
        override fun authenticate(
            route: Route?,
            response: Response,
        ): Request? {
            // Ne jamais ré-authentifier la requête de login elle-même (éviterait une boucle).
            if (response.request.url.encodedPath.contains(LOGIN_PATH)) return null
            // Déjà retenté → abandon (la session est réellement invalide → écran de connexion).
            if (responseCount(response) >= MAX_ATTEMPTS) return null

            val refreshed =
                runCatching { runBlocking { authRepository.get().refreshActiveToken() } }
                    .getOrElse { error ->
                        Timber.w(error, "Rafraîchissement du jeton interrompu")
                        false
                    }
            if (!refreshed) return null

            val newToken = tokenManager.currentToken()?.value ?: return null
            return response.request.newBuilder()
                .header("Authorization", "Bearer $newToken")
                .build()
        }

        private fun responseCount(response: Response): Int {
            var current: Response? = response
            var count = 1
            while (current?.priorResponse != null) {
                count++
                current = current.priorResponse
            }
            return count
        }

        private companion object {
            const val LOGIN_PATH = "connector/login"
            const val MAX_ATTEMPTS = 2
        }
    }
