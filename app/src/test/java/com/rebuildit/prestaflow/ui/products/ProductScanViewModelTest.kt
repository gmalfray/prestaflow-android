package com.rebuildit.prestaflow.ui.products

import com.rebuildit.prestaflow.core.network.NetworkErrorMapper
import com.rebuildit.prestaflow.domain.products.model.Product
import com.rebuildit.prestaflow.domain.products.model.ProductImage
import com.rebuildit.prestaflow.domain.products.model.ProductStock
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests unitaires JVM du [ProductScanViewModel] : flux scan code-barres → recherche produit →
 * ajustement stock (mise en stock par réception de marchandise).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProductScanViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var fakeRepo: FakeProductsRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepo = FakeProductsRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel(): ProductScanViewModel =
        ProductScanViewModel(
            productsRepository = fakeRepo,
            networkErrorMapper = NetworkErrorMapper(),
        )

    // ─── Résultat unique ─────────────────────────────────────────────────────

    @Test
    fun `un seul resultat selectionne automatiquement le produit`() =
        runTest {
            val product = buildProduct(1L, quantity = 12)
            fakeRepo.barcodeSearchResult = listOf(product)

            val vm = buildViewModel()
            vm.onBarcodeScanned("3401234567890")
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals(product, state.selectedProduct)
            assertEquals("12", state.quantityInput)
            assertFalse(state.isLoading)
            assertFalse(state.notFound)
            assertEquals(listOf("3401234567890"), fakeRepo.barcodeSearchCalls)
        }

    // ─── Aucun résultat ──────────────────────────────────────────────────────

    @Test
    fun `zero resultat expose notFound`() =
        runTest {
            fakeRepo.barcodeSearchResult = emptyList()

            val vm = buildViewModel()
            vm.onBarcodeScanned("0000000000000")
            advanceUntilIdle()

            val state = vm.uiState.value
            assertTrue(state.notFound)
            assertNull(state.selectedProduct)
            assertFalse(state.isLoading)
        }

    // ─── Plusieurs résultats ─────────────────────────────────────────────────

    @Test
    fun `plusieurs resultats exposent hasMultipleResults sans selection auto`() =
        runTest {
            val productA = buildProduct(1L, quantity = 5)
            val productB = buildProduct(2L, quantity = 8)
            fakeRepo.barcodeSearchResult = listOf(productA, productB)

            val vm = buildViewModel()
            vm.onBarcodeScanned("1234567890123")
            advanceUntilIdle()

            val state = vm.uiState.value
            assertTrue(state.hasMultipleResults)
            assertNull(state.selectedProduct)

            vm.onSelectProduct(productB)
            val afterSelect = vm.uiState.value
            assertEquals(productB, afterSelect.selectedProduct)
            assertEquals("8", afterSelect.quantityInput)
            assertFalse(afterSelect.hasMultipleResults)
        }

    // ─── Erreur réseau au lookup ─────────────────────────────────────────────

    @Test
    fun `une erreur reseau au lookup expose une erreur`() =
        runTest {
            fakeRepo.shouldThrowOnBarcodeSearch = true

            val vm = buildViewModel()
            vm.onBarcodeScanned("3401234567890")
            advanceUntilIdle()

            val state = vm.uiState.value
            assertTrue(state.error != null)
            assertNull(state.selectedProduct)
            assertFalse(state.isLoading)
        }

    // ─── Stepper quantité ────────────────────────────────────────────────────

    @Test
    fun `onIncrement et onDecrement ajustent la quantite sans descendre sous zero`() =
        runTest {
            fakeRepo.barcodeSearchResult = listOf(buildProduct(1L, quantity = 0))

            val vm = buildViewModel()
            vm.onBarcodeScanned("3401234567890")
            advanceUntilIdle()

            vm.onDecrement()
            assertEquals("0", vm.uiState.value.quantityInput)

            vm.onIncrement()
            vm.onIncrement()
            assertEquals("2", vm.uiState.value.quantityInput)
        }

    // ─── Confirmation de l'ajustement ────────────────────────────────────────

    @Test
    fun `onConfirmAdjustment envoie la quantite saisie au repository et expose submitSuccess`() =
        runTest {
            val product = buildProduct(42L, quantity = 3, warehouseId = 7L)
            fakeRepo.barcodeSearchResult = listOf(product)

            val vm = buildViewModel()
            vm.onBarcodeScanned("3401234567890")
            advanceUntilIdle()

            vm.onQuantityChange("25")
            vm.onConfirmAdjustment()
            advanceUntilIdle()

            val call = fakeRepo.updateStockCalls.single()
            assertEquals(42L, call.productId)
            assertEquals(25, call.quantity)
            assertEquals(7L, call.warehouseId)
            assertTrue(vm.uiState.value.submitSuccess)
        }

    @Test
    fun `un echec de confirmation expose une erreur et n a pas submitSuccess`() =
        runTest {
            fakeRepo.barcodeSearchResult = listOf(buildProduct(1L, quantity = 3))
            fakeRepo.shouldThrowOnUpdateStock = true

            val vm = buildViewModel()
            vm.onBarcodeScanned("3401234567890")
            advanceUntilIdle()

            vm.onConfirmAdjustment()
            advanceUntilIdle()

            val state = vm.uiState.value
            assertFalse(state.submitSuccess)
            assertTrue(state.error != null)
            assertFalse(state.isSubmitting)
        }

    // ─── Réinitialisation ────────────────────────────────────────────────────

    @Test
    fun `onDismiss reinitialise completement l etat`() =
        runTest {
            fakeRepo.barcodeSearchResult = listOf(buildProduct(1L, quantity = 3))

            val vm = buildViewModel()
            vm.onBarcodeScanned("3401234567890")
            advanceUntilIdle()
            assertTrue(vm.uiState.value.isSheetVisible)

            vm.onDismiss()

            val state = vm.uiState.value
            assertFalse(state.isSheetVisible)
            assertNull(state.selectedProduct)
            assertNull(state.scannedCode)
        }

    // ─── Builders ────────────────────────────────────────────────────────────

    private fun buildProduct(
        id: Long,
        quantity: Int,
        warehouseId: Long? = null,
    ) = Product(
        id = id,
        name = "Produit $id",
        reference = "REF$id",
        price = 9.99,
        active = true,
        stock = ProductStock(quantity = quantity, warehouseId = warehouseId),
        images = emptyList<ProductImage>(),
        updatedAt = "2026-07-01T00:00:00Z",
        ean13 = "340123456789$id",
    )
}
