package com.rebuildit.prestaflow.data.remote.interceptor

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Tests de [AcceptLanguageInterceptor] via un vrai [MockWebServer] : on vérifie le header
 * `Accept-Language` réellement reçu côté "serveur", pas seulement construit côté client.
 *
 * Utilise Robolectric pour disposer d'un vrai `Context`/`Resources` dont on peut piloter la
 * locale courante via [RuntimeEnvironment.setQualifiers] — reproduit un changement de langue
 * in-app (per-app language ou mode "Système (auto)") sans dépendre d'`AppCompatDelegate`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AcceptLanguageInterceptorTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val interceptor = AcceptLanguageInterceptor(ApplicationProvider.getApplicationContext())
        client = OkHttpClient.Builder().addInterceptor(interceptor).build()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `pose le tag de langue primaire en minuscule`() {
        RuntimeEnvironment.setQualifiers("+de")

        server.enqueue(MockResponse().setResponseCode(200))
        client.newCall(Request.Builder().url(server.url("/ping")).build()).execute().use { }

        val recorded = server.takeRequest()
        assertEquals("de", recorded.getHeader("Accept-Language"))
    }

    @Test
    fun `ne garde que le sous-tag primaire pour une locale composee comme pt-BR`() {
        RuntimeEnvironment.setQualifiers("+pt-rBR")

        server.enqueue(MockResponse().setResponseCode(200))
        client.newCall(Request.Builder().url(server.url("/ping")).build()).execute().use { }

        val recorded = server.takeRequest()
        assertEquals("pt", recorded.getHeader("Accept-Language"))
    }

    @Test
    fun `relit la locale a chaque requete sans mise en cache`() {
        RuntimeEnvironment.setQualifiers("+fr")
        server.enqueue(MockResponse().setResponseCode(200))
        client.newCall(Request.Builder().url(server.url("/ping")).build()).execute().use { }
        assertEquals("fr", server.takeRequest().getHeader("Accept-Language"))

        // Changement de langue in-app SANS recréer l'interceptor (déjà instancié dans setUp) :
        // la valeur doit malgré tout suivre, preuve qu'il n'y a pas de cache en champ de classe.
        RuntimeEnvironment.setQualifiers("+es")
        server.enqueue(MockResponse().setResponseCode(200))
        client.newCall(Request.Builder().url(server.url("/ping")).build()).execute().use { }
        assertEquals("es", server.takeRequest().getHeader("Accept-Language"))
    }

    @Test
    fun `ne remplace pas un header Accept-Language deja pose explicitement`() {
        RuntimeEnvironment.setQualifiers("+de")

        server.enqueue(MockResponse().setResponseCode(200))
        client.newCall(
            Request.Builder()
                .url(server.url("/ping"))
                .header("Accept-Language", "custom")
                .build(),
        ).execute().use { }

        val recorded = server.takeRequest()
        assertEquals("custom", recorded.getHeader("Accept-Language"))
    }
}
