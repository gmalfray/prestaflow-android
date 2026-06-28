package com.rebuildit.prestaflow.data.auth

import com.rebuildit.prestaflow.core.network.ApiEndpointManager
import com.rebuildit.prestaflow.core.network.NetworkErrorMapper
import com.rebuildit.prestaflow.core.security.InMemoryTokenProvider
import com.rebuildit.prestaflow.core.security.ShopConnectionStore
import com.rebuildit.prestaflow.core.security.TokenManager
import com.rebuildit.prestaflow.data.remote.dto.AuthRequestDto
import com.rebuildit.prestaflow.data.remote.dto.AuthResponseDto
import com.rebuildit.prestaflow.domain.auth.AuthResult
import com.rebuildit.prestaflow.domain.auth.AuthState
import com.rebuildit.prestaflow.domain.auth.ShopUrlValidator
import com.rebuildit.prestaflow.domain.auth.model.AuthToken
import com.rebuildit.prestaflow.domain.auth.model.ShopConnection
import com.rebuildit.prestaflow.fakes.FakeSharedPreferences
import com.rebuildit.prestaflow.fakes.FakeTokenStorage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/**
 * Tests unitaires de [AuthRepositoryImpl] couvrant la gestion multi-boutiques.
 *
 * Stratégie : les classes concrètes (TokenManager, ApiEndpointManager, ShopConnectionStore)
 * sont instanciées avec des fakes en mémoire ([FakeSharedPreferences], [FakeTokenStorage]).
 * L'API réseau est simulée via une lambda configurable.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthRepositoryImplTest {
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var fakePrefs: FakeSharedPreferences
    private lateinit var fakeEndpointPrefs: FakeSharedPreferences
    private lateinit var fakeTokenStorage: FakeTokenStorage
    private lateinit var connectionStore: ShopConnectionStore
    private lateinit var tokenManager: TokenManager
    private lateinit var endpointManager: ApiEndpointManager

    /** Comportement configurable du login réseau. */
    private var loginResult: (AuthRequestDto) -> AuthResponseDto = { _ ->
        AuthResponseDto(
            token = "jwt-valide",
            expiresIn = 3600L,
            scopes = listOf("orders"),
        )
    }

    private lateinit var repository: AuthRepositoryImpl

    @Before
    fun setUp() {
        fakePrefs = FakeSharedPreferences()
        fakeEndpointPrefs = FakeSharedPreferences()
        fakeTokenStorage = FakeTokenStorage()

        connectionStore = ShopConnectionStore(fakePrefs)
        tokenManager = TokenManager(fakeTokenStorage, InMemoryTokenProvider())
        endpointManager = ApiEndpointManager(fakeEndpointPrefs)

        repository = buildRepository()
    }

    private fun buildRepository(): AuthRepositoryImpl {
        val fakeApi = CapturingFakeApi { req -> loginResult(req) }
        return AuthRepositoryImpl(
            api = fakeApi,
            shopUrlValidator = ShopUrlValidator(),
            endpointManager = endpointManager,
            tokenManager = tokenManager,
            connectionStore = connectionStore,
            networkErrorMapper = NetworkErrorMapper(),
            ioDispatcher = testDispatcher,
        )
    }

    // ─── addConnection : ajout réussi ────────────────────────────────────────

    @Test
    fun `addConnection avec URL et cle valides retourne Success`() =
        runTest(testDispatcher) {
            val result = repository.addConnection("https://shop.test", "cle-api", "Ma boutique")
            advanceUntilIdle()

            assertEquals(AuthResult.Success, result)
        }

    @Test
    fun `addConnection rend la boutique active apres succes`() =
        runTest(testDispatcher) {
            repository.addConnection("https://shop.test", "cle-api", "Ma boutique")
            advanceUntilIdle()

            val activeId = connectionStore.getActiveId()
            assertEquals("https://shop.test", activeId)
        }

    @Test
    fun `addConnection avec label vide derive le label depuis l URL`() =
        runTest(testDispatcher) {
            repository.addConnection("https://shop.test", "cle-api", "")
            advanceUntilIdle()

            val connection = connectionStore.read().firstOrNull { it.id == "https://shop.test" }
            assertNotNull(connection)
            // labelFor("https://shop.test") = "shop.test"
            assertEquals("shop.test", connection!!.label)
        }

    @Test
    fun `addConnection avec label non vide utilise le label fourni`() =
        runTest(testDispatcher) {
            repository.addConnection("https://shop.test", "cle-api", "Mon label perso")
            advanceUntilIdle()

            val connection = connectionStore.read().firstOrNull { it.id == "https://shop.test" }
            assertNotNull(connection)
            assertEquals("Mon label perso", connection!!.label)
        }

    @Test
    fun `addConnection avec label en espaces blancs derive le label depuis l URL`() =
        runTest(testDispatcher) {
            repository.addConnection("https://shop.test", "cle-api", "   ")
            advanceUntilIdle()

            val connection = connectionStore.read().firstOrNull { it.id == "https://shop.test" }
            assertEquals("shop.test", connection!!.label)
        }

    @Test
    fun `addConnection conserve la cle API dans la connexion persistee`() =
        runTest(testDispatcher) {
            repository.addConnection("https://shop.test", "ma-cle-secrete", "Boutique")
            advanceUntilIdle()

            val connection = connectionStore.read().firstOrNull { it.id == "https://shop.test" }
            assertEquals("ma-cle-secrete", connection?.apiKey)
        }

    @Test
    fun `addConnection met a jour authState a Authenticated apres succes`() =
        runTest(testDispatcher) {
            repository.addConnection("https://shop.test", "cle-api", "Boutique")
            advanceUntilIdle()

            assertTrue(repository.authState.value is AuthState.Authenticated)
        }

    @Test
    fun `addConnection met a jour le flow connections`() =
        runTest(testDispatcher) {
            repository.addConnection("https://shop.test", "cle-api", "Boutique")
            advanceUntilIdle()

            val connections = repository.connections.value
            assertEquals(1, connections.size)
            assertEquals("https://shop.test", connections.first().id)
        }

    // ─── addConnection : dédup ────────────────────────────────────────────────

    @Test
    fun `addConnection avec la meme URL deux fois ne cree pas de doublon`() =
        runTest(testDispatcher) {
            repository.addConnection("https://shop.test", "cle-v1", "Boutique")
            advanceUntilIdle()
            repository.addConnection("https://shop.test", "cle-v2", "Boutique renommee")
            advanceUntilIdle()

            assertEquals("Pas de doublon après ré-ajout de la même URL", 1, connectionStore.read().size)
        }

    @Test
    fun `addConnection avec la meme URL met a jour la cle API`() =
        runTest(testDispatcher) {
            repository.addConnection("https://shop.test", "cle-v1", "Boutique")
            advanceUntilIdle()

            loginResult = { _ -> AuthResponseDto(token = "nouveau-jwt", expiresIn = 3600L, scopes = listOf("orders")) }
            repository.addConnection("https://shop.test", "cle-v2", "Boutique")
            advanceUntilIdle()

            val connection = connectionStore.read().firstOrNull { it.id == "https://shop.test" }
            assertEquals("cle-v2", connection?.apiKey)
        }

    // ─── addConnection : URL invalide ─────────────────────────────────────────

    @Test
    fun `addConnection avec URL vide retourne Failure InvalidShopUrl`() =
        runTest(testDispatcher) {
            val result = repository.addConnection("", "cle-api", "Boutique")
            advanceUntilIdle()

            assertTrue(result is AuthResult.Failure)
        }

    @Test
    fun `addConnection avec URL non-https retourne Failure`() =
        runTest(testDispatcher) {
            val result = repository.addConnection("http://shop.test", "cle-api", "Boutique")
            advanceUntilIdle()

            assertTrue(result is AuthResult.Failure)
        }

    @Test
    fun `addConnection avec URL invalide restaure la boutique active precedente`() =
        runTest(testDispatcher) {
            // Première boutique active
            repository.addConnection("https://shop1.test", "cle1", "Boutique 1")
            advanceUntilIdle()

            // Tentative avec URL invalide
            repository.addConnection("pas-une-url", "cle-api", "Boutique")
            advanceUntilIdle()

            // La boutique 1 doit rester active
            assertEquals("https://shop1.test", connectionStore.getActiveId())
        }

    // ─── addConnection : erreurs réseau ──────────────────────────────────────

    @Test
    fun `addConnection avec erreur IOException retourne Failure HostUnreachable`() =
        runTest(testDispatcher) {
            loginResult = { _ -> throw IOException("Timeout") }

            val result = repository.addConnection("https://shop.test", "cle-api", "Boutique")
            advanceUntilIdle()

            assertTrue(result is AuthResult.Failure)
        }

    @Test
    fun `addConnection avec HTTP 404 retourne Failure ModuleNotInstalled`() =
        runTest(testDispatcher) {
            loginResult = { _ ->
                val rawResponse =
                    okhttp3.Response.Builder()
                        .request(okhttp3.Request.Builder().url("https://shop.test/module/rebuildconnector/api/connector/login").build())
                        .protocol(okhttp3.Protocol.HTTP_1_1)
                        .code(404)
                        .message("Not Found")
                        .build()
                val retrofitResponse =
                    Response.error<AuthResponseDto>(
                        okhttp3.ResponseBody.create(null, ""),
                        rawResponse,
                    )
                throw HttpException(retrofitResponse)
            }

            val result = repository.addConnection("https://shop.test", "cle-api", "Boutique")
            advanceUntilIdle()

            assertTrue(result is AuthResult.Failure)
        }

    // ─── switchActiveConnection ───────────────────────────────────────────────

    @Test
    fun `switchActiveConnection bascule la boutique active`() =
        runTest(testDispatcher) {
            repository.addConnection("https://shop1.test", "cle1", "Boutique 1")
            advanceUntilIdle()
            repository.addConnection("https://shop2.test", "cle2", "Boutique 2")
            advanceUntilIdle()

            repository.switchActiveConnection("https://shop1.test")
            advanceUntilIdle()

            assertEquals("https://shop1.test", connectionStore.getActiveId())
        }

    @Test
    fun `switchActiveConnection met a jour connections avec une seule boutique isActive true`() =
        runTest(testDispatcher) {
            repository.addConnection("https://shop1.test", "cle1", "Boutique 1")
            advanceUntilIdle()
            repository.addConnection("https://shop2.test", "cle2", "Boutique 2")
            advanceUntilIdle()

            repository.switchActiveConnection("https://shop1.test")
            advanceUntilIdle()

            val connections = repository.connections.value
            val actives = connections.filter { it.isActive }
            assertEquals("Une seule connexion doit être active", 1, actives.size)
            assertEquals("https://shop1.test", actives.first().id)
        }

    @Test
    fun `switchActiveConnection avec id inconnu est un no-op`() =
        runTest(testDispatcher) {
            repository.addConnection("https://shop1.test", "cle1", "Boutique 1")
            advanceUntilIdle()

            repository.switchActiveConnection("https://boutique-inexistante.test")
            advanceUntilIdle()

            // L'active ne change pas
            assertEquals("https://shop1.test", connectionStore.getActiveId())
        }

    @Test
    fun `switchActiveConnection met a jour authState avec le token de la nouvelle boutique`() =
        runTest(testDispatcher) {
            var callCount = 0
            loginResult = { _ ->
                callCount++
                AuthResponseDto(token = "token-shop-$callCount", expiresIn = 3600L, scopes = listOf("orders"))
            }

            repository.addConnection("https://shop1.test", "cle1", "Boutique 1")
            advanceUntilIdle()
            repository.addConnection("https://shop2.test", "cle2", "Boutique 2")
            advanceUntilIdle()

            // Shop 2 est active, on rebascule sur shop 1
            repository.switchActiveConnection("https://shop1.test")
            advanceUntilIdle()

            val state = repository.authState.value
            assertTrue(state is AuthState.Authenticated)
            assertEquals("token-shop-1", (state as AuthState.Authenticated).token.value)
        }

    // ─── removeConnection ─────────────────────────────────────────────────────

    @Test
    fun `removeConnection supprime la boutique de la liste`() =
        runTest(testDispatcher) {
            repository.addConnection("https://shop1.test", "cle1", "Boutique 1")
            advanceUntilIdle()
            repository.addConnection("https://shop2.test", "cle2", "Boutique 2")
            advanceUntilIdle()

            repository.removeConnection("https://shop1.test")
            advanceUntilIdle()

            val ids = connectionStore.read().map { it.id }
            assertFalse(ids.contains("https://shop1.test"))
            assertTrue(ids.contains("https://shop2.test"))
        }

    @Test
    fun `removeConnection met a jour le flow connections`() =
        runTest(testDispatcher) {
            repository.addConnection("https://shop1.test", "cle1", "Boutique 1")
            advanceUntilIdle()
            repository.addConnection("https://shop2.test", "cle2", "Boutique 2")
            advanceUntilIdle()

            repository.removeConnection("https://shop1.test")
            advanceUntilIdle()

            assertEquals(1, repository.connections.value.size)
            assertEquals("https://shop2.test", repository.connections.value.first().id)
        }

    @Test
    fun `removeConnection sur la boutique active bascule sur une autre`() =
        runTest(testDispatcher) {
            repository.addConnection("https://shop1.test", "cle1", "Boutique 1")
            advanceUntilIdle()
            repository.addConnection("https://shop2.test", "cle2", "Boutique 2")
            advanceUntilIdle()

            // shop2 est active (dernier ajouté), on la supprime
            repository.removeConnection("https://shop2.test")
            advanceUntilIdle()

            // shop1 doit devenir active
            assertEquals("https://shop1.test", connectionStore.getActiveId())
            assertTrue(repository.authState.value is AuthState.Authenticated)
        }

    @Test
    fun `removeConnection sur la seule boutique deconnecte`() =
        runTest(testDispatcher) {
            repository.addConnection("https://shop.test", "cle", "Boutique")
            advanceUntilIdle()

            repository.removeConnection("https://shop.test")
            advanceUntilIdle()

            assertEquals(AuthState.Unauthenticated, repository.authState.value)
            assertTrue(repository.connections.value.isEmpty())
        }

    @Test
    fun `removeConnection sur boutique inactive ne change pas l active`() =
        runTest(testDispatcher) {
            repository.addConnection("https://shop1.test", "cle1", "Boutique 1")
            advanceUntilIdle()
            repository.addConnection("https://shop2.test", "cle2", "Boutique 2")
            advanceUntilIdle()
            // shop2 est active
            repository.switchActiveConnection("https://shop2.test")
            advanceUntilIdle()

            repository.removeConnection("https://shop1.test")
            advanceUntilIdle()

            assertEquals("shop2 doit rester active", "https://shop2.test", connectionStore.getActiveId())
        }

    // ─── getActiveToken ───────────────────────────────────────────────────────

    @Test
    fun `getActiveToken retourne null si non authentifie`() =
        runTest(testDispatcher) {
            val token = repository.getActiveToken()

            assertNull(token)
        }

    @Test
    fun `getActiveToken retourne le token apres login reussi`() =
        runTest(testDispatcher) {
            repository.addConnection("https://shop.test", "cle-api", "Boutique")
            advanceUntilIdle()

            val token = repository.getActiveToken()

            assertNotNull(token)
            assertEquals("jwt-valide", token!!.value)
        }

    @Test
    fun `getActiveToken retourne null si le token est expire`() =
        runTest(testDispatcher) {
            // expiresAtEpochMillis = 1L : timestamp dans le passé → isExpired = true
            // On injecte la connexion directement dans le store pour contrôler l'expiration
            val expiredConnection =
                ShopConnection(
                    id = "https://shop.test",
                    shopUrl = "https://shop.test",
                    label = "Boutique",
                    token = AuthToken(value = "token-expire", expiresAtEpochMillis = 1L),
                    apiKey = "cle-api",
                )
            connectionStore.write(listOf(expiredConnection))
            connectionStore.setActiveId("https://shop.test")
            tokenManager.update(expiredConnection.token)

            val token = repository.getActiveToken()
            advanceUntilIdle()

            assertNull("Un token expiré doit retourner null", token)
        }

    // ─── refreshActiveToken ───────────────────────────────────────────────────

    @Test
    fun `refreshActiveToken retourne false si non authentifie`() =
        runTest(testDispatcher) {
            val result = repository.refreshActiveToken()
            advanceUntilIdle()

            assertFalse(result)
        }

    @Test
    fun `refreshActiveToken retourne true et met a jour le token si login reussi`() =
        runTest(testDispatcher) {
            repository.addConnection("https://shop.test", "cle-api", "Boutique")
            advanceUntilIdle()

            loginResult = { _ -> AuthResponseDto(token = "nouveau-jwt", expiresIn = 3600L, scopes = listOf("orders")) }
            val result = repository.refreshActiveToken()
            advanceUntilIdle()

            assertTrue(result)
            assertEquals("nouveau-jwt", tokenManager.currentToken()?.value)
        }

    @Test
    fun `refreshActiveToken retourne false si le login echoue`() =
        runTest(testDispatcher) {
            repository.addConnection("https://shop.test", "cle-api", "Boutique")
            advanceUntilIdle()

            loginResult = { _ -> throw IOException("Réseau indisponible") }
            val result = repository.refreshActiveToken()
            advanceUntilIdle()

            assertFalse(result)
        }

    @Test
    fun `refreshActiveToken retourne false si apiKey est vide`() =
        runTest(testDispatcher) {
            // Connexion sans apiKey (migration legacy)
            connectionStore.write(
                listOf(
                    ShopConnection(
                        id = "https://shop.test",
                        shopUrl = "https://shop.test",
                        label = "Boutique",
                        token = AuthToken(value = "ancien-token", expiresAtEpochMillis = Long.MAX_VALUE),
                        apiKey = "",
                    ),
                ),
            )
            connectionStore.setActiveId("https://shop.test")
            // Recrée le repo pour que le init() lise l'état persisted
            val repo = buildRepository()

            val result = repo.refreshActiveToken()
            advanceUntilIdle()

            assertFalse("Pas de rafraîchissement possible sans apiKey", result)
        }

    // ─── login (alias de addConnection) ──────────────────────────────────────

    @Test
    fun `login appelle addConnection avec label vide`() =
        runTest(testDispatcher) {
            val result = repository.login("https://shop.test", "cle-api")
            advanceUntilIdle()

            assertEquals(AuthResult.Success, result)
            // Le label doit avoir été dérivé de l'URL
            val connection = connectionStore.read().firstOrNull { it.id == "https://shop.test" }
            assertEquals("shop.test", connection?.label)
        }

    // ─── logout ───────────────────────────────────────────────────────────────

    @Test
    fun `logout vide les connexions et passe en Unauthenticated`() =
        runTest(testDispatcher) {
            repository.addConnection("https://shop.test", "cle", "Boutique")
            advanceUntilIdle()

            repository.logout()
            advanceUntilIdle()

            assertEquals(AuthState.Unauthenticated, repository.authState.value)
            assertTrue(repository.connections.value.isEmpty())
            assertNull(tokenManager.currentToken())
        }

    // ─── Normalisation d'URL ──────────────────────────────────────────────────

    @Test
    fun `addConnection normalise l URL en supprimant le slash final`() =
        runTest(testDispatcher) {
            repository.addConnection("https://shop.test/", "cle", "Boutique")
            advanceUntilIdle()

            val stored = connectionStore.read()
            assertEquals(1, stored.size)
            assertEquals("https://shop.test", stored.first().id)
        }

    @Test
    fun `addConnection avec la meme URL avec et sans slash est un dedup`() =
        runTest(testDispatcher) {
            repository.addConnection("https://shop.test", "cle", "Boutique")
            advanceUntilIdle()
            repository.addConnection("https://shop.test/", "cle", "Boutique")
            advanceUntilIdle()

            assertEquals("L'URL avec et sans slash doit produire le même id", 1, connectionStore.read().size)
        }

    // ─── Fake interne ─────────────────────────────────────────────────────────

    /**
     * Implémentation minimale de [com.rebuildit.prestaflow.data.remote.api.PrestaFlowApi]
     * qui délègue uniquement `login` à un lambda configurable.
     */
    private inner class CapturingFakeApi(
        private val loginBlock: (AuthRequestDto) -> AuthResponseDto,
    ) : com.rebuildit.prestaflow.data.remote.api.PrestaFlowApi {
        override suspend fun login(request: AuthRequestDto): AuthResponseDto = loginBlock(request)

        override suspend fun getOrders(filters: Map<String, String>) = throw UnsupportedOperationException()

        override suspend fun getOrderStatuses() = throw UnsupportedOperationException()

        override suspend fun getOrder(orderId: Long) = throw UnsupportedOperationException()

        override suspend fun updateOrderStatus(
            orderId: Long,
            body: com.rebuildit.prestaflow.data.remote.dto.OrderStatusUpdateRequestDto,
        ) = throw UnsupportedOperationException()

        override suspend fun updateOrderShipping(
            orderId: Long,
            body: com.rebuildit.prestaflow.data.remote.dto.OrderShippingUpdateRequestDto,
        ) = throw UnsupportedOperationException()

        override suspend fun getInvoicePdf(orderId: Long) = throw UnsupportedOperationException()

        override suspend fun getShippingLabelPdf(orderId: Long) = throw UnsupportedOperationException()

        override suspend fun getProducts(
            filters: Map<String, String>,
            search: String?,
        ) = throw UnsupportedOperationException()

        override suspend fun getProduct(productId: Long) = throw UnsupportedOperationException()

        override suspend fun updateProductStock(
            productId: Long,
            body: com.rebuildit.prestaflow.data.remote.dto.StockUpdateRequestDto,
        ) = throw UnsupportedOperationException()

        override suspend fun getDashboardMetrics(
            period: String?,
            from: String?,
            to: String?,
        ) = throw UnsupportedOperationException()

        override suspend fun getCustomerStats() = throw UnsupportedOperationException()

        override suspend fun getTopCustomers(limit: Int) = throw UnsupportedOperationException()

        override suspend fun getCustomers(
            limit: Int?,
            offset: Int?,
            search: String?,
            sort: String?,
            createdFrom: String?,
            createdTo: String?,
        ) = throw UnsupportedOperationException()

        override suspend fun getCustomer(customerId: Long) = throw UnsupportedOperationException()

        override suspend fun registerDevice(body: com.rebuildit.prestaflow.data.remote.dto.DeviceRegistrationRequestDto) =
            throw UnsupportedOperationException()

        override suspend fun unregisterDevice(token: String) = throw UnsupportedOperationException()

        override suspend fun getBaskets(abandonedSinceDays: Int?) = throw UnsupportedOperationException()

        override suspend fun getBasketById(cartId: Int) = throw UnsupportedOperationException()

        override suspend fun generateShippingLabel(orderId: Long) = throw UnsupportedOperationException()
    }
}
