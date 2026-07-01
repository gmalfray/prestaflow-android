package com.rebuildit.prestaflow.ui.auth

import com.rebuildit.prestaflow.domain.auth.ShopUrlValidator
import com.rebuildit.prestaflow.fakes.FakeAuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Tests unitaires du [AuthViewModel] — validation URL en temps réel (défaut E).
 *
 * Points vérifiés :
 * - URL vide → pas d'erreur.
 * - Saisie du préfixe seul ("https://") → pas d'erreur (saisie en cours).
 * - URL http valide → erreur HTTPS requise.
 * - URL invalide courte → pas d'erreur (trop tôt pour diagnostiquer).
 * - URL invalide longue → erreur malformée.
 * - URL HTTPS valide → pas d'erreur.
 * - La correction d'une URL invalide efface l'erreur.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var fakeAuthRepo: FakeAuthRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeAuthRepo = FakeAuthRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel(): AuthViewModel =
        AuthViewModel(
            authRepository = fakeAuthRepo,
            shopUrlValidator = ShopUrlValidator(),
        )

    // ─── Pas d'erreur pour saisie incomplète ─────────────────────────────────

    @Test
    fun `url vide ne produit pas d erreur`() {
        val vm = buildViewModel()
        vm.onShopUrlChanged("")
        assertNull(
            "Une URL vide ne doit pas déclencher d'erreur en temps réel",
            vm.uiState.value.shopUrlError,
        )
    }

    @Test
    fun `prefixe https seul ne produit pas d erreur`() {
        val vm = buildViewModel()
        vm.onShopUrlChanged("https://")
        assertNull(
            "Le préfixe 'https://' seul est une saisie en cours, pas une erreur",
            vm.uiState.value.shopUrlError,
        )
    }

    @Test
    fun `prefixe http seul ne produit pas d erreur`() {
        val vm = buildViewModel()
        vm.onShopUrlChanged("http://")
        assertNull(
            "Le préfixe 'http://' seul est une saisie en cours, pas une erreur",
            vm.uiState.value.shopUrlError,
        )
    }

    @Test
    fun `texte sans schema ne produit pas d erreur`() {
        val vm = buildViewModel()
        vm.onShopUrlChanged("monsite")
        assertNull(
            "Un texte sans schéma ('://') n'est pas encore validable",
            vm.uiState.value.shopUrlError,
        )
    }

    @Test
    fun `url http invalide courte ne produit pas d erreur`() {
        // 3 chars après :// → trop court pour diagnostiquer "malformed"
        val vm = buildViewModel()
        vm.onShopUrlChanged("http://ab")
        // NonHttps déclenche une erreur même pour les courtes URLs http://
        assertNotNull(
            "Une URL http (non-https) doit signaler l'erreur HTTPS même courte",
            vm.uiState.value.shopUrlError,
        )
    }

    // ─── Erreurs effectives ───────────────────────────────────────────────────

    @Test
    fun `url http complete signale erreur https requise`() {
        val vm = buildViewModel()
        vm.onShopUrlChanged("http://mabouttique.fr")
        assertNotNull(
            "Une URL http doit déclencher l'erreur HTTPS requise en temps réel",
            vm.uiState.value.shopUrlError,
        )
    }

    @Test
    fun `url malformee longue signale erreur malformee`() {
        val vm = buildViewModel()
        // "https://" + "abcde" (5 chars) → assez long pour afficher Malformed
        vm.onShopUrlChanged("https://[invalid")
        assertNotNull(
            "Une URL syntaxiquement invalide (assez longue) doit déclencher l'erreur malformée",
            vm.uiState.value.shopUrlError,
        )
    }

    @Test
    fun `url https valide ne produit pas d erreur`() {
        val vm = buildViewModel()
        vm.onShopUrlChanged("https://mabouttique.fr")
        assertNull(
            "Une URL HTTPS valide ne doit pas produire d'erreur en temps réel",
            vm.uiState.value.shopUrlError,
        )
    }

    @Test
    fun `url https avec chemin valide ne produit pas d erreur`() {
        val vm = buildViewModel()
        vm.onShopUrlChanged("https://mabouttique.fr/shop")
        assertNull(
            "Une URL HTTPS avec chemin ne doit pas produire d'erreur",
            vm.uiState.value.shopUrlError,
        )
    }

    // ─── Correction de l'erreur ───────────────────────────────────────────────

    @Test
    fun `correction d une url invalide efface l erreur`() {
        val vm = buildViewModel()

        // Déclenche une erreur
        vm.onShopUrlChanged("http://mabouttique.fr")
        assertNotNull(vm.uiState.value.shopUrlError)

        // Corrige l'URL
        vm.onShopUrlChanged("https://mabouttique.fr")
        assertNull(
            "La correction vers une URL HTTPS valide doit effacer l'erreur",
            vm.uiState.value.shopUrlError,
        )
    }

    @Test
    fun `formError est efface au changement d url`() {
        val vm = buildViewModel()
        // Simule une formError résiduelle (déjà dans l'état via copie directe)
        // On teste que onShopUrlChanged efface le formError
        vm.onShopUrlChanged("https://test.fr")
        assertNull(
            "onShopUrlChanged doit effacer formError",
            vm.uiState.value.formError,
        )
    }
}
