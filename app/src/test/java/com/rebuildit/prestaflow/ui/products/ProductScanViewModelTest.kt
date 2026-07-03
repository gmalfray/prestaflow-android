package com.rebuildit.prestaflow.ui.products

import com.rebuildit.prestaflow.core.network.NetworkErrorMapper
import com.rebuildit.prestaflow.domain.products.model.Combination
import com.rebuildit.prestaflow.domain.products.model.MatchedCombination
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

    // ─── Code déjà connu introuvable (hand-off depuis StockReplenishViewModel) ──

    @Test
    fun `onKnownNotFound ouvre directement l etat introuvable sans appel reseau`() =
        runTest {
            val vm = buildViewModel()
            vm.onKnownNotFound("0000000000000")

            val state = vm.uiState.value
            assertTrue(state.isSheetVisible)
            assertTrue(state.notFound)
            assertEquals("0000000000000", state.scannedCode)
            assertFalse(state.isLoading)
            assertNull(state.selectedProduct)
            assertTrue(fakeRepo.barcodeSearchCalls.isEmpty())
        }

    @Test
    fun `onKnownNotFound permet ensuite de lancer l association normalement`() =
        runTest {
            val match = buildProduct(9L, quantity = 4)
            fakeRepo.searchProductsResult = listOf(match)

            val vm = buildViewModel()
            vm.onKnownNotFound("0000000000000")
            vm.onStartAssociation()
            vm.onAssociationQueryChange("laine rico")
            testDispatcher.scheduler.advanceTimeBy(400L)
            advanceUntilIdle()

            assertEquals(listOf("laine rico"), fakeRepo.searchProductsCalls)
            assertEquals(listOf(match), vm.uiState.value.associationResults)
        }

    // ─── Secours OCR (suggestions pré-remplies, cf. StockReplenishViewModel) ─

    @Test
    fun `onKnownNotFoundWithSuggestions ouvre directement l ecran d association pre-rempli`() =
        runTest {
            val suggestion = buildProduct(9L, quantity = 4)
            val vm = buildViewModel()

            vm.onKnownNotFoundWithSuggestions("0000000000000", listOf(suggestion))

            val state = vm.uiState.value
            assertTrue(state.isSheetVisible)
            assertTrue(state.notFound)
            assertTrue("Doit ouvrir directement la recherche, pas l'écran vide", state.isAssociating)
            assertEquals(listOf(suggestion), state.associationResults)
            assertTrue(
                "Aucun appel réseau : les suggestions viennent déjà de l'appelant",
                fakeRepo.searchProductsCalls.isEmpty(),
            )
        }

    @Test
    fun `selectionner une suggestion OCR associe l ean comme une association normale`() =
        runTest {
            val suggestion = buildProduct(9L, quantity = 4)
            fakeRepo.setProductEan13Result = suggestion
            fakeRepo.barcodeSearchResult = listOf(suggestion)
            val vm = buildViewModel()
            vm.onKnownNotFoundWithSuggestions("0000000000000", listOf(suggestion))

            vm.onAssociationProductSelected(suggestion)
            advanceUntilIdle()

            val call = fakeRepo.setProductEan13Calls.single()
            assertEquals(9L, call.productId)
            assertEquals("0000000000000", call.ean13)
            assertEquals(suggestion, vm.uiState.value.selectedProduct)
        }

    @Test
    fun `un code vide est ignore par onKnownNotFoundWithSuggestions`() =
        runTest {
            val vm = buildViewModel()

            vm.onKnownNotFoundWithSuggestions("", listOf(buildProduct(1L, quantity = 1)))

            assertFalse(vm.uiState.value.isSheetVisible)
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

    // ─── Combinaison (déclinaison) matchée par le scan ──────────────────────

    @Test
    fun `un scan matchant une combinaison affiche la quantite de la combinaison`() =
        runTest {
            val combination = MatchedCombination(id = 99L, name = "Coloris - Bleu", quantity = 7)
            val product = buildProduct(1L, quantity = 120).copy(matchedCombination = combination)
            fakeRepo.barcodeSearchResult = listOf(product)

            val vm = buildViewModel()
            vm.onBarcodeScanned("3401234567899")
            advanceUntilIdle()

            val state = vm.uiState.value
            // La quantité éditée est celle de la COMBINAISON (7), pas celle du produit parent (120).
            assertEquals("7", state.quantityInput)
            assertEquals(combination, state.selectedProduct?.matchedCombination)
        }

    @Test
    fun `confirmer un ajustement sur une combinaison envoie son combinationId`() =
        runTest {
            val combination = MatchedCombination(id = 99L, name = "Coloris - Bleu", quantity = 7)
            val product = buildProduct(1L, quantity = 120).copy(matchedCombination = combination)
            fakeRepo.barcodeSearchResult = listOf(product)

            val vm = buildViewModel()
            vm.onBarcodeScanned("3401234567899")
            advanceUntilIdle()

            vm.onQuantityChange("10")
            vm.onConfirmAdjustment()
            advanceUntilIdle()

            val call = fakeRepo.updateStockCalls.single()
            assertEquals(1L, call.productId)
            assertEquals(10, call.quantity)
            assertEquals(99L, call.combinationId)
            assertTrue(vm.uiState.value.submitSuccess)
        }

    @Test
    fun `confirmer un ajustement sur un produit simple n envoie pas de combinationId`() =
        runTest {
            val product = buildProduct(1L, quantity = 3)
            fakeRepo.barcodeSearchResult = listOf(product)

            val vm = buildViewModel()
            vm.onBarcodeScanned("3401234567891")
            advanceUntilIdle()

            vm.onConfirmAdjustment()
            advanceUntilIdle()

            val call = fakeRepo.updateStockCalls.single()
            assertNull(call.combinationId)
        }

    // ─── Déclinaisons multiples (sélecteur "Quelle déclinaison ?") ──────────

    @Test
    fun `un scan sur un produit a une seule declinaison ne declenche pas de selecteur`() =
        runTest {
            val combination = Combination(id = 5L, name = "Taille - M", quantity = 3)
            val product = buildProduct(1L, quantity = 20).copy(combinations = listOf(combination))
            fakeRepo.barcodeSearchResult = listOf(product)

            val vm = buildViewModel()
            vm.onBarcodeScanned("3401234567890")
            advanceUntilIdle()

            val state = vm.uiState.value
            // 1 seule déclinaison = pas d'ambiguïté : sélection auto au niveau produit, comme avant.
            assertFalse(state.needsCombinationChoice)
            assertEquals(product, state.selectedProduct)
            assertNull(state.selectedProduct?.matchedCombination)
        }

    @Test
    fun `un scan sur un produit a plusieurs declinaisons affiche un selecteur`() =
        runTest {
            val combinationA = Combination(id = 5L, name = "Coloris - Bleu", quantity = 3)
            val combinationB = Combination(id = 6L, name = "Coloris - Rouge", quantity = 9)
            val product = buildProduct(1L, quantity = 20).copy(combinations = listOf(combinationA, combinationB))
            fakeRepo.barcodeSearchResult = listOf(product)

            val vm = buildViewModel()
            vm.onBarcodeScanned("3401234567890")
            advanceUntilIdle()

            val state = vm.uiState.value
            assertTrue(state.needsCombinationChoice)
            assertNull(state.selectedProduct)
            assertEquals(listOf(combinationA, combinationB), state.combinationChoices)
        }

    @Test
    fun `choisir une declinaison ouvre sa fiche stock et confirmer envoie son combinationId`() =
        runTest {
            val combinationA = Combination(id = 5L, name = "Coloris - Bleu", quantity = 3)
            val combinationB = Combination(id = 6L, name = "Coloris - Rouge", quantity = 9)
            val product = buildProduct(1L, quantity = 20).copy(combinations = listOf(combinationA, combinationB))
            fakeRepo.barcodeSearchResult = listOf(product)

            val vm = buildViewModel()
            vm.onBarcodeScanned("3401234567890")
            advanceUntilIdle()

            vm.onSelectCombination(combinationB)

            val afterChoice = vm.uiState.value
            assertFalse(afterChoice.needsCombinationChoice)
            assertEquals("9", afterChoice.quantityInput)
            assertEquals(combinationB.id, afterChoice.selectedProduct?.matchedCombination?.id)

            vm.onConfirmAdjustment()
            advanceUntilIdle()

            val call = fakeRepo.updateStockCalls.single()
            assertEquals(1L, call.productId)
            assertEquals(6L, call.combinationId)
            assertTrue(vm.uiState.value.submitSuccess)
        }

    // ─── Association code-barres → produit existant ─────────────────────────

    @Test
    fun `onStartAssociation ouvre la recherche depuis l etat notFound`() =
        runTest {
            fakeRepo.barcodeSearchResult = emptyList()

            val vm = buildViewModel()
            vm.onBarcodeScanned("0000000000000")
            advanceUntilIdle()

            vm.onStartAssociation()

            val state = vm.uiState.value
            assertTrue(state.isAssociating)
            assertEquals("", state.associationQuery)
            assertTrue(state.associationResults.isEmpty())
        }

    @Test
    fun `la recherche d association appelle searchProducts apres debounce`() =
        runTest {
            fakeRepo.barcodeSearchResult = emptyList()
            val match = buildProduct(9L, quantity = 4)
            fakeRepo.searchProductsResult = listOf(match)

            val vm = buildViewModel()
            vm.onBarcodeScanned("0000000000000")
            advanceUntilIdle()
            vm.onStartAssociation()

            vm.onAssociationQueryChange("laine rico")
            // Avancer le temps pour dépasser le debounce de 300ms
            testDispatcher.scheduler.advanceTimeBy(400L)
            advanceUntilIdle()

            assertEquals(listOf("laine rico"), fakeRepo.searchProductsCalls)
            assertEquals(listOf(match), vm.uiState.value.associationResults)
            assertFalse(vm.uiState.value.isAssociationSearching)
        }

    @Test
    fun `selectionner un produit associe l ean13 et bascule sur la fiche stock`() =
        runTest {
            fakeRepo.barcodeSearchResult = emptyList()
            val candidate = buildProduct(9L, quantity = 4)
            val updated = candidate.copy(ean13 = "0000000000000", stock = candidate.stock.copy(quantity = 4))
            fakeRepo.searchProductsResult = listOf(candidate)
            fakeRepo.setProductEan13Result = updated

            val vm = buildViewModel()
            vm.onBarcodeScanned("0000000000000")
            advanceUntilIdle()
            vm.onStartAssociation()
            vm.onAssociationQueryChange("laine rico")
            // Avancer le temps pour dépasser le debounce de 300ms
            testDispatcher.scheduler.advanceTimeBy(400L)
            advanceUntilIdle()

            vm.onAssociationProductSelected(candidate)
            advanceUntilIdle()

            val call = fakeRepo.setProductEan13Calls.single()
            assertEquals(9L, call.productId)
            assertEquals("0000000000000", call.ean13)

            val state = vm.uiState.value
            assertFalse(state.isAssociating)
            assertFalse(state.notFound)
            assertEquals(updated, state.selectedProduct)
            assertEquals("4", state.quantityInput)
        }

    @Test
    fun `un echec d association expose une erreur et reste en recherche`() =
        runTest {
            fakeRepo.barcodeSearchResult = emptyList()
            val candidate = buildProduct(9L, quantity = 4)
            fakeRepo.searchProductsResult = listOf(candidate)
            fakeRepo.shouldThrowOnSetProductEan13 = true

            val vm = buildViewModel()
            vm.onBarcodeScanned("0000000000000")
            advanceUntilIdle()
            vm.onStartAssociation()
            vm.onAssociationQueryChange("laine rico")
            // Avancer le temps pour dépasser le debounce de 300ms
            testDispatcher.scheduler.advanceTimeBy(400L)
            advanceUntilIdle()

            vm.onAssociationProductSelected(candidate)
            advanceUntilIdle()

            val state = vm.uiState.value
            assertTrue(state.isAssociating)
            assertNull(state.selectedProduct)
            assertTrue(state.associationError != null)
            assertFalse(state.isAssociationSubmitting)
        }

    @Test
    fun `associer un produit a une seule declinaison pose l ean sur celle-ci automatiquement`() =
        runTest {
            fakeRepo.barcodeSearchResult = emptyList()
            val combination = Combination(id = 15L, name = "Taille - M", quantity = 2)
            val candidate = buildProduct(9L, quantity = 4).copy(combinations = listOf(combination))
            val updated = candidate.copy(ean13 = "0000000000000")
            fakeRepo.searchProductsResult = listOf(candidate)
            fakeRepo.setProductEan13Result = updated

            val vm = buildViewModel()
            vm.onBarcodeScanned("0000000000000")
            advanceUntilIdle()
            vm.onStartAssociation()
            vm.onAssociationQueryChange("laine rico")
            testDispatcher.scheduler.advanceTimeBy(400L)
            advanceUntilIdle()

            vm.onAssociationProductSelected(candidate)
            advanceUntilIdle()

            // Pas de sélecteur affiché : 1 seule déclinaison, pas d'ambiguïté.
            assertTrue(vm.uiState.value.associationCombinationChoices.isEmpty())
            val call = fakeRepo.setProductEan13Calls.single()
            assertEquals(9L, call.productId)
            assertEquals("0000000000000", call.ean13)
            assertEquals(15L, call.combinationId)
        }

    @Test
    fun `associer un produit a plusieurs declinaisons affiche un selecteur puis pose l ean sur celle choisie`() =
        runTest {
            fakeRepo.barcodeSearchResult = emptyList()
            val combinationA = Combination(id = 15L, name = "Taille - M", quantity = 2)
            val combinationB = Combination(id = 16L, name = "Taille - L", quantity = 5)
            val candidate = buildProduct(9L, quantity = 4).copy(combinations = listOf(combinationA, combinationB))
            val updated = candidate.copy(ean13 = "0000000000000")
            fakeRepo.searchProductsResult = listOf(candidate)
            fakeRepo.setProductEan13Result = updated

            val vm = buildViewModel()
            vm.onBarcodeScanned("0000000000000")
            advanceUntilIdle()
            vm.onStartAssociation()
            vm.onAssociationQueryChange("laine rico")
            testDispatcher.scheduler.advanceTimeBy(400L)
            advanceUntilIdle()

            vm.onAssociationProductSelected(candidate)

            val afterSelectProduct = vm.uiState.value
            assertEquals(listOf(combinationA, combinationB), afterSelectProduct.associationCombinationChoices)
            assertTrue(fakeRepo.setProductEan13Calls.isEmpty())

            vm.onSelectAssociationCombination(combinationB)
            advanceUntilIdle()

            val call = fakeRepo.setProductEan13Calls.single()
            assertEquals(9L, call.productId)
            assertEquals("0000000000000", call.ean13)
            assertEquals(16L, call.combinationId)
            assertTrue(vm.uiState.value.associationCombinationChoices.isEmpty())
            assertEquals(updated, vm.uiState.value.selectedProduct)
        }

    @Test
    fun `onCancelAssociation revient a l etat notFound`() =
        runTest {
            fakeRepo.barcodeSearchResult = emptyList()

            val vm = buildViewModel()
            vm.onBarcodeScanned("0000000000000")
            advanceUntilIdle()
            vm.onStartAssociation()
            vm.onAssociationQueryChange("laine")
            advanceUntilIdle()

            vm.onCancelAssociation()

            val state = vm.uiState.value
            assertFalse(state.isAssociating)
            assertTrue(state.notFound)
            assertEquals("", state.associationQuery)
            assertTrue(state.associationResults.isEmpty())
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
