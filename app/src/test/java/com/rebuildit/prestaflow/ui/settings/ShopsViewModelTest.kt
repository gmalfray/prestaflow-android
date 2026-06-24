package com.rebuildit.prestaflow.ui.settings

import com.rebuildit.prestaflow.R
import com.rebuildit.prestaflow.core.ui.UiText
import com.rebuildit.prestaflow.domain.auth.AuthFailure
import com.rebuildit.prestaflow.domain.auth.AuthRepository
import com.rebuildit.prestaflow.domain.auth.AuthResult
import com.rebuildit.prestaflow.domain.auth.AuthState
import com.rebuildit.prestaflow.domain.auth.ShopUrlValidator
import com.rebuildit.prestaflow.domain.auth.model.AuthToken
import com.rebuildit.prestaflow.domain.auth.model.ShopConnection
import com.rebuildit.prestaflow.fakes.FakeAuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests unitaires de [ShopsViewModel].
 *
 * Couvre : submitAdd (validation, succès, erreurs réseau), switchShop, removeShop,
 * show/dismiss dialog, et les mutations de champ (onUrlChange, onKeyChange, onLabelChange).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ShopsViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeAuth: ControlledFakeAuthRepository
    private lateinit var viewModel: ShopsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeAuth = ControlledFakeAuthRepository()
        viewModel = ShopsViewModel(fakeAuth)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ─── showAddDialog / dismissAddDialog ─────────────────────────────────────

    @Test
    fun `showAddDialog rend le dialog visible`() =
        runTest(testDispatcher) {
            viewModel.showAddDialog()

            assertTrue(viewModel.addState.value.visible)
        }

    @Test
    fun `dismissAddDialog cache le dialog et reinitialise l etat`() =
        runTest(testDispatcher) {
            viewModel.showAddDialog()
            viewModel.onUrlChange("https://shop.test")

            viewModel.dismissAddDialog()

            assertFalse(viewModel.addState.value.visible)
        }

    @Test
    fun `dismissAddDialog reinitialise les champs de formulaire`() =
        runTest(testDispatcher) {
            viewModel.showAddDialog()
            viewModel.onUrlChange("https://shop.test")
            viewModel.onKeyChange("ma-cle")
            viewModel.onLabelChange("Mon label")

            viewModel.dismissAddDialog()

            val state = viewModel.addState.value
            assertEquals("", state.shopUrl)
            assertEquals("", state.apiKey)
            assertEquals("", state.label)
        }

    // ─── onUrlChange / onKeyChange / onLabelChange ────────────────────────────

    @Test
    fun `onUrlChange met a jour shopUrl dans l etat`() =
        runTest(testDispatcher) {
            viewModel.onUrlChange("https://boutique.test")

            assertEquals("https://boutique.test", viewModel.addState.value.shopUrl)
        }

    @Test
    fun `onKeyChange met a jour apiKey dans l etat`() =
        runTest(testDispatcher) {
            viewModel.onKeyChange("ma-cle-api")

            assertEquals("ma-cle-api", viewModel.addState.value.apiKey)
        }

    @Test
    fun `onLabelChange met a jour label dans l etat`() =
        runTest(testDispatcher) {
            viewModel.onLabelChange("Mon label")

            assertEquals("Mon label", viewModel.addState.value.label)
        }

    @Test
    fun `onUrlChange remet l erreur a null`() =
        runTest(testDispatcher) {
            // Forcer une erreur en soumettant sans remplir les champs
            viewModel.submitAdd()
            advanceUntilIdle()
            assertNotNull("L'erreur doit être présente avant le changement", viewModel.addState.value.error)

            viewModel.onUrlChange("https://shop.test")

            assertNull("onUrlChange doit effacer l'erreur", viewModel.addState.value.error)
        }

    @Test
    fun `onKeyChange remet l erreur a null`() =
        runTest(testDispatcher) {
            viewModel.submitAdd()
            advanceUntilIdle()
            assertNotNull(viewModel.addState.value.error)

            viewModel.onKeyChange("une-cle")

            assertNull("onKeyChange doit effacer l'erreur", viewModel.addState.value.error)
        }

    @Test
    fun `onLabelChange remet l erreur a null`() =
        runTest(testDispatcher) {
            viewModel.submitAdd()
            advanceUntilIdle()
            assertNotNull(viewModel.addState.value.error)

            viewModel.onLabelChange("un-label")

            assertNull("onLabelChange doit effacer l'erreur", viewModel.addState.value.error)
        }

    // ─── submitAdd : validation ───────────────────────────────────────────────

    @Test
    fun `submitAdd avec url et cle vides pose une erreur de champ obligatoire`() =
        runTest(testDispatcher) {
            viewModel.submitAdd()
            advanceUntilIdle()

            val error = viewModel.addState.value.error
            assertNotNull("L'erreur doit être non nulle", error)
            assertTrue("L'erreur doit être un UiText.FromResources", error is UiText.FromResources)
            assertEquals(R.string.shops_add_error_required, (error as UiText.FromResources).resId)
        }

    @Test
    fun `submitAdd avec url remplie mais cle vide pose une erreur`() =
        runTest(testDispatcher) {
            viewModel.onUrlChange("https://shop.test")

            viewModel.submitAdd()
            advanceUntilIdle()

            assertNotNull(viewModel.addState.value.error)
        }

    @Test
    fun `submitAdd avec cle remplie mais url vide pose une erreur`() =
        runTest(testDispatcher) {
            viewModel.onKeyChange("ma-cle")

            viewModel.submitAdd()
            advanceUntilIdle()

            assertNotNull(viewModel.addState.value.error)
        }

    @Test
    fun `submitAdd avec erreur de validation ne passe pas en loading`() =
        runTest(testDispatcher) {
            viewModel.submitAdd()
            advanceUntilIdle()

            assertFalse("loading doit rester false pour une erreur de validation locale", viewModel.addState.value.loading)
        }

    // ─── submitAdd : succès ───────────────────────────────────────────────────

    @Test
    fun `submitAdd avec succes ferme le dialog`() =
        runTest(testDispatcher) {
            fakeAuth.addConnectionResult = AuthResult.Success
            viewModel.showAddDialog()
            viewModel.onUrlChange("https://shop.test")
            viewModel.onKeyChange("ma-cle")

            viewModel.submitAdd()
            advanceUntilIdle()

            assertFalse("Le dialog doit être fermé après un succès", viewModel.addState.value.visible)
        }

    @Test
    fun `submitAdd avec succes reinitialise l etat du formulaire`() =
        runTest(testDispatcher) {
            fakeAuth.addConnectionResult = AuthResult.Success
            viewModel.showAddDialog()
            viewModel.onUrlChange("https://shop.test")
            viewModel.onKeyChange("ma-cle")
            viewModel.onLabelChange("Mon label")

            viewModel.submitAdd()
            advanceUntilIdle()

            val state = viewModel.addState.value
            assertEquals("", state.shopUrl)
            assertEquals("", state.apiKey)
            assertEquals("", state.label)
            assertNull(state.error)
            assertFalse(state.loading)
        }

    @Test
    fun `submitAdd passe en loading avant l appel au repository`() =
        runTest(testDispatcher) {
            var loadingDurantAppel = false
            fakeAuth.onAddConnectionCalled = {
                loadingDurantAppel = viewModel.addState.value.loading
            }
            fakeAuth.addConnectionResult = AuthResult.Success
            viewModel.onUrlChange("https://shop.test")
            viewModel.onKeyChange("ma-cle")

            viewModel.submitAdd()
            advanceUntilIdle()

            assertTrue("loading doit être true pendant l'appel au repository", loadingDurantAppel)
        }

    // ─── submitAdd : échecs par type d'AuthFailure ────────────────────────────

    @Test
    fun `submitAdd avec InvalidShopUrl produit le message d erreur d URL invalide`() =
        runTest(testDispatcher) {
            fakeAuth.addConnectionResult =
                AuthResult.Failure(
                    AuthFailure.InvalidShopUrl(ShopUrlValidator.Result.Invalid.Malformed),
                )
            viewModel.onUrlChange("https://shop.test")
            viewModel.onKeyChange("ma-cle")

            viewModel.submitAdd()
            advanceUntilIdle()

            val error = viewModel.addState.value.error
            assertTrue(error is UiText.FromResources)
            assertEquals(R.string.auth_error_shop_url_malformed, (error as UiText.FromResources).resId)
        }

    @Test
    fun `submitAdd avec ModuleNotInstalled produit le message module absent`() =
        runTest(testDispatcher) {
            fakeAuth.addConnectionResult = AuthResult.Failure(AuthFailure.ModuleNotInstalled)
            viewModel.onUrlChange("https://shop.test")
            viewModel.onKeyChange("ma-cle")

            viewModel.submitAdd()
            advanceUntilIdle()

            val error = viewModel.addState.value.error
            assertTrue(error is UiText.FromResources)
            assertEquals(R.string.auth_error_module_not_installed, (error as UiText.FromResources).resId)
        }

    @Test
    fun `submitAdd avec HostUnreachable transmet le message du failure`() =
        runTest(testDispatcher) {
            val expectedMessage = UiText.Dynamic("Hôte injoignable")
            fakeAuth.addConnectionResult = AuthResult.Failure(AuthFailure.HostUnreachable(expectedMessage))
            viewModel.onUrlChange("https://shop.test")
            viewModel.onKeyChange("ma-cle")

            viewModel.submitAdd()
            advanceUntilIdle()

            assertEquals(expectedMessage, viewModel.addState.value.error)
        }

    @Test
    fun `submitAdd avec Network failure transmet le message du failure`() =
        runTest(testDispatcher) {
            val expectedMessage = UiText.Dynamic("Erreur réseau 500")
            fakeAuth.addConnectionResult = AuthResult.Failure(AuthFailure.Network(expectedMessage))
            viewModel.onUrlChange("https://shop.test")
            viewModel.onKeyChange("ma-cle")

            viewModel.submitAdd()
            advanceUntilIdle()

            assertEquals(expectedMessage, viewModel.addState.value.error)
        }

    @Test
    fun `submitAdd avec Unknown failure transmet le message du failure`() =
        runTest(testDispatcher) {
            val expectedMessage = UiText.Dynamic("Erreur inconnue")
            fakeAuth.addConnectionResult = AuthResult.Failure(AuthFailure.Unknown(expectedMessage))
            viewModel.onUrlChange("https://shop.test")
            viewModel.onKeyChange("ma-cle")

            viewModel.submitAdd()
            advanceUntilIdle()

            assertEquals(expectedMessage, viewModel.addState.value.error)
        }

    @Test
    fun `submitAdd echec remet loading a false`() =
        runTest(testDispatcher) {
            fakeAuth.addConnectionResult = AuthResult.Failure(AuthFailure.ModuleNotInstalled)
            viewModel.onUrlChange("https://shop.test")
            viewModel.onKeyChange("ma-cle")

            viewModel.submitAdd()
            advanceUntilIdle()

            assertFalse("loading doit redevenir false après un échec", viewModel.addState.value.loading)
        }

    // ─── switchShop / removeShop ──────────────────────────────────────────────

    @Test
    fun `switchShop delegue au repository avec l id fourni`() =
        runTest(testDispatcher) {
            viewModel.switchShop("https://shop.test")
            advanceUntilIdle()

            assertEquals("https://shop.test", fakeAuth.lastSwitchedId)
        }

    @Test
    fun `removeShop delegue au repository avec l id fourni`() =
        runTest(testDispatcher) {
            viewModel.removeShop("https://shop.test")
            advanceUntilIdle()

            assertEquals("https://shop.test", fakeAuth.lastRemovedId)
        }

    // ─── connections ─────────────────────────────────────────────────────────

    @Test
    fun `connections expose la valeur initiale du repository`() =
        runTest(testDispatcher) {
            // stateIn est initialisé avec authRepository.connections.value directement.
            // Le ViewModel lit connections.value dès la construction, avant tout collecteur.
            val shop = FakeAuthRepository.singleActiveConnection("https://shop-init.test")
            fakeAuth.emitConnections(listOf(shop))

            // Recrée le ViewModel pour que stateIn lise la nouvelle valeur initiale
            viewModel = ShopsViewModel(fakeAuth)

            assertEquals(1, viewModel.connections.value.size)
            assertEquals("https://shop-init.test", viewModel.connections.value.first().id)
        }

    @Test
    fun `connections expose plusieurs boutiques quand le repository les emet`() =
        runTest(testDispatcher) {
            // Pré-configure le repo avant la création du ViewModel pour que stateIn lise la valeur initiale.
            val shop1 = FakeAuthRepository.singleActiveConnection("https://shop1.test")
            val shop2 = FakeAuthRepository.singleActiveConnection("https://shop2.test")
            fakeAuth.emitConnections(listOf(shop1, shop2))

            viewModel = ShopsViewModel(fakeAuth)
            advanceUntilIdle()

            assertEquals(2, viewModel.connections.value.size)
            val ids = viewModel.connections.value.map { it.id }.toSet()
            assertTrue(ids.contains("https://shop1.test"))
            assertTrue(ids.contains("https://shop2.test"))
        }

    // ─── Fake contrôlable ────────────────────────────────────────────────────

    /**
     * Extension de [FakeAuthRepository] qui expose des points de contrôle
     * supplémentaires pour les tests de [ShopsViewModel].
     */
    private class ControlledFakeAuthRepository : AuthRepository {
        private val _authState = MutableStateFlow<AuthState>(AuthState.Authenticated(fakeToken()))
        override val authState: StateFlow<AuthState> = _authState

        private val _connections =
            MutableStateFlow<List<ShopConnection>>(
                listOf(FakeAuthRepository.singleActiveConnection()),
            )
        override val connections: StateFlow<List<ShopConnection>> = _connections

        var addConnectionResult: AuthResult = AuthResult.Success
        var onAddConnectionCalled: (() -> Unit)? = null
        var lastSwitchedId: String? = null
        var lastRemovedId: String? = null

        fun emitConnections(connections: List<ShopConnection>) {
            _connections.value = connections
        }

        override suspend fun login(
            shopUrl: String,
            apiKey: String,
        ): AuthResult = AuthResult.Success

        override suspend fun addConnection(
            shopUrl: String,
            apiKey: String,
            label: String,
        ): AuthResult {
            onAddConnectionCalled?.invoke()
            return addConnectionResult
        }

        override suspend fun switchActiveConnection(id: String) {
            lastSwitchedId = id
        }

        override suspend fun removeConnection(id: String) {
            lastRemovedId = id
        }

        override suspend fun logout() {
            _authState.value = AuthState.Unauthenticated
        }

        override suspend fun getActiveToken(): AuthToken = fakeToken()

        override suspend fun refreshActiveToken(): Boolean = true

        companion object {
            fun fakeToken() =
                AuthToken(
                    value = "fake-token",
                    expiresAtEpochMillis = Long.MAX_VALUE,
                    scopes = listOf("orders"),
                )
        }
    }
}
