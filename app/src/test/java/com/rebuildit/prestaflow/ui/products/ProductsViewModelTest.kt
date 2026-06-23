package com.rebuildit.prestaflow.ui.products

import com.rebuildit.prestaflow.core.network.NetworkErrorMapper
import com.rebuildit.prestaflow.domain.products.model.Product
import com.rebuildit.prestaflow.domain.products.model.ProductImage
import com.rebuildit.prestaflow.domain.products.model.ProductStock
import com.rebuildit.prestaflow.domain.products.model.StockFilter
import com.rebuildit.prestaflow.fakes.FakeAuthRepository
import com.rebuildit.prestaflow.fakes.FakeProductsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests unitaires JVM du [ProductsViewModel].
 *
 * Points vérifiés :
 * - Le total vient du retour de [refresh] (API), pas de la taille de la liste.
 * - La recherche passe bien en paramètre `search` au repository (pas de filtrage local).
 * - Le filtre de stock est transmis au repository.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProductsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var fakeProductsRepo: FakeProductsRepository
    private lateinit var fakeAuthRepo: FakeAuthRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeProductsRepo = FakeProductsRepository()
        fakeAuthRepo = FakeAuthRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel(): ProductsViewModel =
        ProductsViewModel(
            productsRepository = fakeProductsRepo,
            networkErrorMapper = NetworkErrorMapper(),
            authRepository = fakeAuthRepo,
        )

    // ─── Total depuis l'API ──────────────────────────────────────────────────

    @Test
    fun `totalCount vient du retour de refresh et non de la taille de la liste`() = runTest {
        // L'API renvoie un total de 150, mais on n'a que 2 produits dans la liste locale
        fakeProductsRepo.refreshTotal = 150
        fakeProductsRepo.setProducts(listOf(buildProduct(1L), buildProduct(2L)))

        val vm = buildViewModel()
        advanceUntilIdle()

        assertEquals(
            "totalCount doit correspondre au total renvoyé par l'API (150), pas à la taille de la liste (2)",
            150,
            vm.uiState.value.totalCount,
        )
    }

    @Test
    fun `totalCount reste inchange si refresh retourne null`() = runTest {
        fakeProductsRepo.refreshTotal = null

        val vm = buildViewModel()
        advanceUntilIdle()

        // Valeur initiale attendue (0 car pas encore de refresh réussi)
        assertEquals(0, vm.uiState.value.totalCount)
    }

    // ─── Recherche déléguée à l'API ──────────────────────────────────────────

    @Test
    fun `onQueryChange avec debounce transmet la recherche au repository`() = runTest {
        val vm = buildViewModel()
        advanceUntilIdle()
        fakeProductsRepo.refreshCalls.clear()

        vm.onQueryChange("chaussures")
        // Avancer le temps pour dépasser le debounce de 300ms
        testDispatcher.scheduler.advanceTimeBy(400L)
        advanceUntilIdle()

        val lastCall = fakeProductsRepo.refreshCalls.lastOrNull()
        assertEquals(
            "La recherche 'chaussures' doit être transmise au repository",
            "chaussures",
            lastCall?.search,
        )
    }

    @Test
    fun `onQueryChange vide transmet null comme search au repository`() = runTest {
        val vm = buildViewModel()
        advanceUntilIdle()

        vm.onQueryChange("chaussures")
        testDispatcher.scheduler.advanceTimeBy(400L)
        advanceUntilIdle()
        fakeProductsRepo.refreshCalls.clear()

        // Vider la query
        vm.onQueryChange("")
        testDispatcher.scheduler.advanceTimeBy(400L)
        advanceUntilIdle()

        val lastCall = fakeProductsRepo.refreshCalls.lastOrNull()
        assertNull(
            "Une query vide doit transmettre null comme search",
            lastCall?.search,
        )
    }

    // ─── Filtre de stock ─────────────────────────────────────────────────────

    @Test
    fun `onStockFilterSelected transmet l apiValue du filtre au repository`() = runTest {
        val vm = buildViewModel()
        advanceUntilIdle()
        fakeProductsRepo.refreshCalls.clear()

        vm.onStockFilterSelected(StockFilter.LOW_STOCK)
        advanceUntilIdle()

        val lastCall = fakeProductsRepo.refreshCalls.lastOrNull()
        assertEquals(
            "Le filtre LOW_STOCK doit transmettre son apiValue au repository",
            StockFilter.LOW_STOCK.apiValue,
            lastCall?.stockFilter,
        )
    }

    @Test
    fun `onStockFilterSelected ALL transmet null comme stockFilter au repository`() = runTest {
        val vm = buildViewModel()
        advanceUntilIdle()
        fakeProductsRepo.refreshCalls.clear()

        vm.onStockFilterSelected(StockFilter.ALL)
        advanceUntilIdle()

        val lastCall = fakeProductsRepo.refreshCalls.lastOrNull()
        assertNull(
            "StockFilter.ALL doit transmettre null comme stockFilter",
            lastCall?.stockFilter,
        )
    }

    // ─── visibleProducts (pas de filtrage local) ─────────────────────────────

    @Test
    fun `visibleProducts retourne tous les produits du repository sans filtrage local`() = runTest {
        fakeProductsRepo.setProducts(listOf(buildProduct(1L), buildProduct(2L), buildProduct(3L)))

        val vm = buildViewModel()
        advanceUntilIdle()

        assertEquals(
            "visibleProducts doit retourner tous les produits (pas de filtrage local)",
            3,
            vm.uiState.value.visibleProducts.size,
        )
    }

    // ─── État d'erreur ───────────────────────────────────────────────────────

    @Test
    fun `un echec de refresh avec onRefresh expose une erreur dans l etat`() = runTest {
        fakeProductsRepo.shouldThrowOnRefresh = true

        val vm = buildViewModel()
        advanceUntilIdle()

        vm.onRefresh()
        advanceUntilIdle()

        assertTrue(
            "L'état doit contenir une erreur après onRefresh() échoué",
            vm.uiState.value.error != null,
        )
    }

    // ─── Builders ────────────────────────────────────────────────────────────

    private fun buildProduct(id: Long) = Product(
        id = id,
        name = "Produit $id",
        reference = "REF$id",
        price = 19.99,
        active = true,
        stock = ProductStock(quantity = 10),
        images = emptyList<ProductImage>(),
        updatedAt = "2024-01-01T00:00:00Z",
    )
}
