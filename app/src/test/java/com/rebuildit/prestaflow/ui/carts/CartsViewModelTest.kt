package com.rebuildit.prestaflow.ui.carts

import com.rebuildit.prestaflow.domain.carts.model.CartSummary
import com.rebuildit.prestaflow.fakes.FakeAuthRepository
import com.rebuildit.prestaflow.fakes.FakeCartsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests unitaires du [CartsViewModel].
 *
 * Points vérifiés :
 * - Chargement initial et état loading.
 * - Filtrage des paniers à 0 € (défaut G).
 * - Recherche par nom client (défaut A).
 * - Pagination « Charger plus » (défaut A).
 * - Réinitialisation de la pagination au changement de requête.
 * - Gestion des erreurs.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CartsViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var fakeCartsRepo: FakeCartsRepository
    private lateinit var fakeAuthRepo: FakeAuthRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeCartsRepo = FakeCartsRepository()
        fakeAuthRepo = FakeAuthRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel(): CartsViewModel =
        CartsViewModel(
            cartsRepository = fakeCartsRepo,
            authRepository = fakeAuthRepo,
        )

    private fun makeCart(
        id: Int,
        customerName: String = "Client $id",
        total: Double = 50.0,
    ): CartSummary =
        CartSummary(
            id = id,
            customerName = customerName,
            customerEmail = "client$id@test.fr",
            currencyIso = "EUR",
            totalTaxIncl = total,
            itemsCount = 1,
            hasOrder = false,
            createdAtIso = null,
            updatedAtIso = null,
        )

    // ─── Chargement initial ───────────────────────────────────────────────────

    @Test
    fun `etat initial isLoading est true et allCarts est vide`() {
        val vm = buildViewModel()
        assertTrue(vm.uiState.value.isLoading)
        assertTrue(vm.uiState.value.allCarts.isEmpty())
    }

    @Test
    fun `chargement remplit allCarts et desactive isLoading`() =
        runTest {
            fakeCartsRepo.cartsResult = listOf(makeCart(1), makeCart(2))

            val vm = buildViewModel()
            advanceUntilIdle()

            assertFalse(vm.uiState.value.isLoading)
            assertEquals(2, vm.uiState.value.allCarts.size)
        }

    // ─── Défaut G — filtrage des paniers à 0 € ────────────────────────────────

    @Test
    fun `paniers a 0 euro sont exclus de allCarts`() =
        runTest {
            fakeCartsRepo.cartsResult = listOf(
                makeCart(1, total = 0.0),
                makeCart(2, total = 25.0),
                makeCart(3, total = -1.0),
                makeCart(4, total = 99.99),
            )

            val vm = buildViewModel()
            advanceUntilIdle()

            assertEquals(
                "Seuls les paniers avec total > 0 doivent être conservés",
                2,
                vm.uiState.value.allCarts.size,
            )
            assertTrue(vm.uiState.value.allCarts.all { it.totalTaxIncl > 0 })
        }

    @Test
    fun `panier a total negatif est egalement exclu`() =
        runTest {
            fakeCartsRepo.cartsResult = listOf(makeCart(1, total = -0.01))

            val vm = buildViewModel()
            advanceUntilIdle()

            assertTrue(
                "Un panier à total négatif doit être exclu",
                vm.uiState.value.allCarts.isEmpty(),
            )
        }

    // ─── Défaut A — recherche par nom client ──────────────────────────────────

    @Test
    fun `recherche filtre par nom client insensible a la casse`() =
        runTest {
            fakeCartsRepo.cartsResult = listOf(
                makeCart(1, customerName = "Alice Dupont"),
                makeCart(2, customerName = "Bob Martin"),
                makeCart(3, customerName = "ALICE Smith"),
            )

            val vm = buildViewModel()
            advanceUntilIdle()

            vm.onQueryChanged("alice")
            advanceUntilIdle()

            assertEquals(
                "La recherche doit trouver 2 paniers contenant 'alice' (insensible à la casse)",
                2,
                vm.uiState.value.carts.size,
            )
        }

    @Test
    fun `recherche vide retourne tous les paniers pagines`() =
        runTest {
            fakeCartsRepo.cartsResult = List(5) { makeCart(it + 1) }

            val vm = buildViewModel()
            advanceUntilIdle()

            vm.onQueryChanged("")

            assertEquals(5, vm.uiState.value.carts.size)
        }

    @Test
    fun `recherche sans resultat retourne liste vide`() =
        runTest {
            fakeCartsRepo.cartsResult = listOf(makeCart(1, customerName = "Alice"))

            val vm = buildViewModel()
            advanceUntilIdle()

            vm.onQueryChanged("xxxxnotfound")

            assertTrue(vm.uiState.value.carts.isEmpty())
        }

    // ─── Défaut A — pagination ────────────────────────────────────────────────

    @Test
    fun `pagination limite la liste initiale a PAGE_SIZE elements`() =
        runTest {
            fakeCartsRepo.cartsResult = List(30) { makeCart(it + 1) }

            val vm = buildViewModel()
            advanceUntilIdle()

            assertEquals(
                "La liste affichée doit être limitée à PAGE_SIZE=${CartsUiState.PAGE_SIZE}",
                CartsUiState.PAGE_SIZE,
                vm.uiState.value.carts.size,
            )
            assertTrue(
                "hasMore doit être vrai quand il y a plus de paniers",
                vm.uiState.value.hasMore,
            )
        }

    @Test
    fun `loadMore augmente le nombre de paniers affiches`() =
        runTest {
            fakeCartsRepo.cartsResult = List(30) { makeCart(it + 1) }

            val vm = buildViewModel()
            advanceUntilIdle()

            vm.loadMore()

            assertEquals(30, vm.uiState.value.carts.size)
            assertFalse(
                "hasMore doit être faux quand tous les paniers sont affichés",
                vm.uiState.value.hasMore,
            )
        }

    @Test
    fun `changement de requete reinitialise la pagination`() =
        runTest {
            fakeCartsRepo.cartsResult = List(30) { makeCart(it + 1) }

            val vm = buildViewModel()
            advanceUntilIdle()

            // Charge plus pour aller au-delà de PAGE_SIZE
            vm.loadMore()
            assertEquals(30, vm.uiState.value.carts.size)

            // Nouvelle recherche → pagination remise à PAGE_SIZE
            vm.onQueryChanged("Client")
            assertEquals(
                "La pagination doit être réinitialisée à PAGE_SIZE lors d'une nouvelle recherche",
                CartsUiState.PAGE_SIZE,
                vm.uiState.value.displayedCount,
            )
        }

    @Test
    fun `hasMore est faux quand tous les paniers tiennent dans la premiere page`() =
        runTest {
            fakeCartsRepo.cartsResult = listOf(makeCart(1), makeCart(2))

            val vm = buildViewModel()
            advanceUntilIdle()

            assertFalse(
                "hasMore doit être faux si la liste est plus courte que PAGE_SIZE",
                vm.uiState.value.hasMore,
            )
        }

    // ─── Gestion erreur ───────────────────────────────────────────────────────

    @Test
    fun `erreur reseau est exposee dans uiState`() =
        runTest {
            fakeCartsRepo.shouldThrow = true

            val vm = buildViewModel()
            advanceUntilIdle()

            assertFalse(vm.uiState.value.isLoading)
            assertFalse(vm.uiState.value.isRefreshing)
            assertEquals(0, vm.uiState.value.allCarts.size)
            // L'erreur doit être présente
            assertTrue(vm.uiState.value.error != null)
        }

    @Test
    fun `onRefresh met a jour la liste et efface l erreur`() =
        runTest {
            fakeCartsRepo.shouldThrow = true
            val vm = buildViewModel()
            advanceUntilIdle()

            // Corrige le repo et rafraîchit
            fakeCartsRepo.shouldThrow = false
            fakeCartsRepo.cartsResult = listOf(makeCart(1, total = 10.0))
            vm.onRefresh()
            advanceUntilIdle()

            assertNull(vm.uiState.value.error)
            assertEquals(1, vm.uiState.value.allCarts.size)
        }
}
