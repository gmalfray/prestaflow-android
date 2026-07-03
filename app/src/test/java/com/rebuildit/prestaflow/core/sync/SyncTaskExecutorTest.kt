package com.rebuildit.prestaflow.core.sync

import androidx.work.ListenableWorker
import com.rebuildit.prestaflow.core.network.ApiEndpointManager
import com.rebuildit.prestaflow.core.security.ShopConnectionStore
import com.rebuildit.prestaflow.data.remote.dto.AuthResponseDto
import com.rebuildit.prestaflow.domain.auth.model.AuthToken
import com.rebuildit.prestaflow.domain.auth.model.ShopConnection
import com.rebuildit.prestaflow.domain.sync.model.ConflictStrategy
import com.rebuildit.prestaflow.domain.sync.model.PendingSyncTask
import com.rebuildit.prestaflow.fakes.FakeLoginApiClient
import com.rebuildit.prestaflow.fakes.FakeSharedPreferences
import com.rebuildit.prestaflow.fakes.FakeSyncFailureNotifier
import com.rebuildit.prestaflow.fakes.FakeSyncQueueRepository
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests unitaires de [SyncTaskExecutor] : vérifie que chaque tâche est routée vers la boutique
 * STOCKÉE SUR LA TÂCHE ([PendingSyncTask.shopUrl]), jamais vers une autre boutique — cf. FIX
 * "file offline rejouée contre la mauvaise boutique".
 *
 * Pas de Robolectric : [ApiEndpointManager] et [ShopConnectionStore] sont de simples classes JVM
 * (cf. [com.rebuildit.prestaflow.data.auth.AuthRepositoryImplTest]) ; le réseau est simulé par
 * [MockWebServer].
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SyncTaskExecutorTest {
    private lateinit var serverA: MockWebServer
    private lateinit var serverB: MockWebServer
    private lateinit var endpointManager: ApiEndpointManager
    private lateinit var connectionStore: ShopConnectionStore
    private lateinit var fakeLoginApiClient: FakeLoginApiClient
    private lateinit var fakeSyncQueueRepository: FakeSyncQueueRepository
    private lateinit var fakeSyncFailureNotifier: FakeSyncFailureNotifier
    private lateinit var executor: SyncTaskExecutor

    private lateinit var shopAUrl: String
    private lateinit var shopBUrl: String

    @Before
    fun setUp() {
        serverA = MockWebServer().apply { start() }
        serverB = MockWebServer().apply { start() }
        shopAUrl = serverA.url("/").toString().trimEnd('/')
        shopBUrl = serverB.url("/").toString().trimEnd('/')

        endpointManager = ApiEndpointManager(FakeSharedPreferences())
        connectionStore = ShopConnectionStore(FakeSharedPreferences())
        fakeLoginApiClient = FakeLoginApiClient()
        fakeSyncQueueRepository = FakeSyncQueueRepository()
        fakeSyncFailureNotifier = FakeSyncFailureNotifier()

        executor =
            SyncTaskExecutor(
                endpointManager = endpointManager,
                connectionStore = connectionStore,
                loginApiClient = fakeLoginApiClient,
                httpClient = OkHttpClient(),
                syncQueueRepository = fakeSyncQueueRepository,
                conflictResolver = SyncConflictResolver(),
                syncFailureNotifier = fakeSyncFailureNotifier,
            )
    }

    @After
    fun tearDown() {
        serverA.shutdown()
        serverB.shutdown()
    }

    private fun connection(
        shopUrl: String,
        token: String = "token-$shopUrl",
        apiKey: String = "",
        expiresAtEpochMillis: Long? = Long.MAX_VALUE,
    ) = ShopConnection(
        id = shopUrl,
        shopUrl = shopUrl,
        label = shopUrl,
        token = AuthToken(value = token, expiresAtEpochMillis = expiresAtEpochMillis),
        apiKey = apiKey,
    )

    private fun task(
        shopUrl: String,
        id: Long = 1L,
        endpoint: String = "products/5/stock",
    ) = PendingSyncTask(
        id = id,
        endpoint = endpoint,
        method = "PATCH",
        payloadJson = "{}",
        resourceType = "product",
        resourceId = 5L,
        attemptCount = 0,
        lastAttemptIso = null,
        createdAtIso = "2026-01-01T00:00:00Z",
        conflictStrategy = ConflictStrategy.MERGE,
        shopUrl = shopUrl,
    )

    @Test
    fun `execute route la requete vers la boutique stockee sur la tache, jamais vers une autre`() =
        runTest {
            connectionStore.write(listOf(connection(shopAUrl, token = "token-A"), connection(shopBUrl, token = "token-B")))
            serverA.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

            val result = executor.execute(task(shopUrl = shopAUrl))

            assertTrue("La tâche doit être traitée avec succès", result is ListenableWorker.Result.Success)
            assertEquals("La tâche doit être retirée de la file après succès", listOf(1L), fakeSyncQueueRepository.removedIds)

            val recorded = serverA.takeRequest()
            assertEquals("PATCH", recorded.method)
            assertTrue(
                "L'URL doit cibler la boutique de la tâche (A)",
                recorded.path?.contains("products/5/stock") == true,
            )
            assertEquals("Bearer token-A", recorded.getHeader("Authorization"))
            assertEquals(
                "La boutique B (non ciblée par la tâche) ne doit recevoir aucune requête",
                0,
                serverB.requestCount,
            )
        }

    @Test
    fun `execute abandonne une tache dont la boutique est inconnue, sans appel reseau`() =
        runTest {
            // Aucune connexion enregistrée pour cette boutique (retirée / jamais connue).
            val result = executor.execute(task(shopUrl = "https://boutique-inconnue.test"))

            assertTrue(result is ListenableWorker.Result.Success)
            assertEquals(listOf(1L), fakeSyncQueueRepository.removedIds)
            assertEquals(0, serverA.requestCount)
            assertEquals(0, serverB.requestCount)
        }

    @Test
    fun `execute rafraichit un jeton expire via LoginApiClient avant de rejouer la tache`() =
        runTest {
            connectionStore.write(
                listOf(
                    connection(shopAUrl, token = "token-expire", apiKey = "cle-api-A", expiresAtEpochMillis = 1L),
                ),
            )
            fakeLoginApiClient =
                FakeLoginApiClient { _, _ ->
                    AuthResponseDto(
                        token = "token-rafraichi",
                        expiresIn = 3600L,
                        scopes = emptyList(),
                    )
                }
            executor =
                SyncTaskExecutor(
                    endpointManager = endpointManager,
                    connectionStore = connectionStore,
                    loginApiClient = fakeLoginApiClient,
                    httpClient = OkHttpClient(),
                    syncQueueRepository = fakeSyncQueueRepository,
                    conflictResolver = SyncConflictResolver(),
                    syncFailureNotifier = fakeSyncFailureNotifier,
                )
            serverA.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

            executor.execute(task(shopUrl = shopAUrl))

            assertEquals(1, fakeLoginApiClient.calls.size)
            assertEquals("cle-api-A", fakeLoginApiClient.calls.first().request.apiKey)
            val recorded = serverA.takeRequest()
            assertEquals("Bearer token-rafraichi", recorded.getHeader("Authorization"))
        }

    @Test
    fun `execute rend visible une tache abandonnee apres un 422 (notification) au lieu de la droper en silence`() =
        runTest {
            connectionStore.write(listOf(connection(shopAUrl)))
            serverA.enqueue(MockResponse().setResponseCode(422).setBody("""{"error":"invalid"}"""))

            val result = executor.execute(task(shopUrl = shopAUrl))

            assertTrue("La tâche non réessayable doit être considérée traitée", result is ListenableWorker.Result.Success)
            assertEquals(
                "La tâche doit malgré tout être retirée de la file (elle ne redeviendra jamais valide)",
                listOf(1L),
                fakeSyncQueueRepository.removedIds,
            )
            assertEquals(
                "L'abandon doit être signalé (notification), pas seulement loggé en silence",
                1,
                fakeSyncFailureNotifier.droppedCalls.size,
            )
            assertEquals(422, fakeSyncFailureNotifier.droppedCalls.first().httpCode)
        }

    @Test
    fun `execute retente une tache Hold (409 + strategie MERGE) au lieu de l'abandonner definitivement`() =
        runTest {
            connectionStore.write(listOf(connection(shopAUrl)))
            serverA.enqueue(MockResponse().setResponseCode(409).setBody("""{"error":"conflict"}"""))

            // task() ci-dessus utilise ConflictStrategy.MERGE => SyncConflictResolver.resolve() renvoie Hold.
            val result = executor.execute(task(shopUrl = shopAUrl))

            assertTrue(
                "Une tâche Hold doit être rejouée (backoff WorkManager), pas silencieusement acceptée",
                result is ListenableWorker.Result.Retry,
            )
            assertEquals(
                "La tâche Hold ne doit pas être retirée de la file : elle doit rester en attente",
                emptyList<Long>(),
                fakeSyncQueueRepository.removedIds,
            )
        }
}
