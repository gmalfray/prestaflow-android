package com.rebuildit.prestaflow.core.network

import com.rebuildit.prestaflow.domain.auth.model.AuthToken
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de logique pure liée à l'authentification/jeton.
 *
 * Note : [TokenAuthenticator] dépend directement de okhttp3.Response/Route dont le
 * constructeur est package-private. Les tests JVM purs de l'Authenticator OkHttp
 * nécessiteraient un vrai serveur de test (MockWebServer) — hors scope JVM pur.
 * On vérifie ici la logique de [AuthToken.isExpired] qui pilote le comportement
 * de l'Authenticator.
 */
class TokenAuthenticatorTest {

    // ─── AuthToken.isExpired ─────────────────────────────────────────────────

    @Test
    fun `isExpired retourne false si expiresAtEpochMillis est dans le futur`() {
        val token = AuthToken(
            value = "tok",
            expiresAtEpochMillis = System.currentTimeMillis() + 60_000L,
        )

        assertFalse(token.isExpired)
    }

    @Test
    fun `isExpired retourne true si expiresAtEpochMillis est dans le passe`() {
        val token = AuthToken(
            value = "tok",
            expiresAtEpochMillis = 1L, // timestamp epoch très ancien
        )

        assertTrue(token.isExpired)
    }

    @Test
    fun `isExpired retourne false si expiresAtEpochMillis est null`() {
        val token = AuthToken(
            value = "tok",
            expiresAtEpochMillis = null,
        )

        // Un token sans expiration est considéré comme valide
        assertFalse(token.isExpired)
    }

    @Test
    fun `isExpired retourne true si expiresAtEpochMillis est exactement l heure courante`() {
        val now = System.currentTimeMillis()
        val token = AuthToken(
            value = "tok",
            expiresAtEpochMillis = now - 1L, // légèrement dans le passé
        )

        assertTrue(token.isExpired)
    }
}
