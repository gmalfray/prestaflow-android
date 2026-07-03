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
import kotlinx.coroutines.test.advanceTimeBy
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
 * Tests unitaires JVM du [StockReplenishViewModel] : écran « Ajout / réappro stock » (Lot 1) —
 * accumulation du delta (boutons rapides + saisie libre) et validation différée avec fenêtre
 * d'annulation (pattern swipe commandes, cf. [com.rebuildit.prestaflow.ui.orders.OrdersViewModel]).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StockReplenishViewModelTest {
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

    private fun buildViewModel(): StockReplenishViewModel =
        StockReplenishViewModel(
            productsRepository = fakeRepo,
            networkErrorMapper = NetworkErrorMapper(),
        )

    // ─── Résolution du scan ──────────────────────────────────────────────────

    @Test
    fun `un scan resolu expose le produit et un delta a zero`() =
        runTest {
            val product = buildProduct(1L, quantity = 12)
            fakeRepo.barcodeSearchResult = listOf(product)

            val vm = buildViewModel()
            vm.onBarcodeScanned("3401234567890")
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals(product, state.product)
            assertEquals(0, state.delta)
            assertFalse(state.isScannerActive)
        }

    @Test
    fun `zero resultat expose notFound et desactive le scanner`() =
        runTest {
            fakeRepo.barcodeSearchResult = emptyList()

            val vm = buildViewModel()
            vm.onBarcodeScanned("0000000000000")
            advanceUntilIdle()

            val state = vm.uiState.value
            assertTrue(state.notFound)
            assertNull(state.product)
            assertFalse(state.isScannerActive)
        }

    @Test
    fun `un scan est ignore si le scanner n est pas actif`() =
        runTest {
            fakeRepo.barcodeSearchResult = listOf(buildProduct(1L, quantity = 5))

            val vm = buildViewModel()
            vm.onBarcodeScanned("3401234567890")
            advanceUntilIdle()
            fakeRepo.barcodeSearchCalls.clear()

            // Un produit est déjà affiché (scanner en pause) : un second scan doit être ignoré.
            vm.onBarcodeScanned("9999999999999")
            advanceUntilIdle()

            assertTrue(fakeRepo.barcodeSearchCalls.isEmpty())
        }

    @Test
    fun `plusieurs resultats distincts exposent une liste de choix`() =
        runTest {
            val productA = buildProduct(1L, quantity = 5)
            val productB = buildProduct(2L, quantity = 8)
            fakeRepo.barcodeSearchResult = listOf(productA, productB)

            val vm = buildViewModel()
            vm.onBarcodeScanned("1234567890123")
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals(listOf(productA, productB), state.multipleResults)
            assertNull(state.product)

            vm.onSelectFromMultipleResults(productB)
            assertEquals(productB, vm.uiState.value.product)
            assertTrue(vm.uiState.value.multipleResults.isEmpty())
        }

    // ─── Combinaison-aware ───────────────────────────────────────────────────

    @Test
    fun `un scan matchant une combinaison affiche directement sa quantite`() =
        runTest {
            val combination = MatchedCombination(id = 99L, name = "Coloris - Bleu", quantity = 7)
            val product = buildProduct(1L, quantity = 120).copy(matchedCombination = combination)
            fakeRepo.barcodeSearchResult = listOf(product)

            val vm = buildViewModel()
            vm.onBarcodeScanned("3401234567899")
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals(combination, state.product?.matchedCombination)
            assertEquals(7, state.newQuantity)
        }

    @Test
    fun `un produit a plusieurs declinaisons expose un selecteur puis resout au choix`() =
        runTest {
            val combinationA = Combination(id = 5L, name = "Coloris - Bleu", quantity = 3)
            val combinationB = Combination(id = 6L, name = "Coloris - Rouge", quantity = 9)
            val product = buildProduct(1L, quantity = 20).copy(combinations = listOf(combinationA, combinationB))
            fakeRepo.barcodeSearchResult = listOf(product)

            val vm = buildViewModel()
            vm.onBarcodeScanned("3401234567890")
            advanceUntilIdle()

            val afterScan = vm.uiState.value
            assertEquals(listOf(combinationA, combinationB), afterScan.combinationChoices)
            assertNull(afterScan.product)

            vm.onSelectCombination(combinationB)

            val afterChoice = vm.uiState.value
            assertTrue(afterChoice.combinationChoices.isEmpty())
            assertEquals(combinationB.id, afterChoice.product?.matchedCombination?.id)
            assertEquals(9, afterChoice.newQuantity)
        }

    // ─── Accumulation du delta ───────────────────────────────────────────────

    @Test
    fun `les boutons rapides s accumulent en un delta sans rien ecrire`() =
        runTest {
            fakeRepo.barcodeSearchResult = listOf(buildProduct(1L, quantity = 10))

            val vm = buildViewModel()
            vm.onBarcodeScanned("3401234567890")
            advanceUntilIdle()

            vm.onQuickAdd(5)
            vm.onQuickAdd(10)
            vm.onQuickAdd(20)
            advanceUntilIdle()

            assertEquals(35, vm.uiState.value.delta)
            assertEquals(45, vm.uiState.value.newQuantity)
            assertTrue("Rien ne doit être écrit tant que Valider n'est pas tapé", fakeRepo.updateStockCalls.isEmpty())
        }

    @Test
    fun `la saisie libre s ajoute au delta accumule et vide le champ`() =
        runTest {
            fakeRepo.barcodeSearchResult = listOf(buildProduct(1L, quantity = 10))

            val vm = buildViewModel()
            vm.onBarcodeScanned("3401234567890")
            advanceUntilIdle()

            vm.onQuickAdd(5)
            vm.onQuantityInputChange("7")
            vm.onAddTypedQuantity()

            val state = vm.uiState.value
            assertEquals(12, state.delta)
            assertEquals("", state.quantityInput)
        }

    @Test
    fun `onResetDelta remet le delta a zero sans perdre le produit affiche`() =
        runTest {
            fakeRepo.barcodeSearchResult = listOf(buildProduct(1L, quantity = 10))

            val vm = buildViewModel()
            vm.onBarcodeScanned("3401234567890")
            advanceUntilIdle()
            vm.onQuickAdd(20)

            vm.onResetDelta()

            val state = vm.uiState.value
            assertEquals(0, state.delta)
            assertEquals(1L, state.product?.id)
        }

    // ─── Validation différée (fenêtre d'annulation) ──────────────────────────

    @Test
    fun `valider rearme immediatement le scanner sans ecrire tout de suite`() =
        runTest {
            fakeRepo.barcodeSearchResult = listOf(buildProduct(1L, quantity = 10))

            val vm = buildViewModel()
            vm.onBarcodeScanned("3401234567890")
            advanceUntilIdle()
            vm.onQuickAdd(15)

            vm.onValidate()

            val state = vm.uiState.value
            assertTrue("Le scanner doit être réarmé immédiatement après Valider", state.isScannerActive)
            assertNull(state.product)
            assertEquals(1, state.pendingWrites.size)
            assertEquals(15, state.pendingWrites.first().delta)
            assertTrue("Rien n'est écrit avant la fin de la fenêtre d'annulation", fakeRepo.updateStockCalls.isEmpty())
        }

    @Test
    fun `l ecriture part apres la fenetre d annulation avec la quantite absolue`() =
        runTest {
            fakeRepo.barcodeSearchResult = listOf(buildProduct(42L, quantity = 10, warehouseId = 3L))

            val vm = buildViewModel()
            vm.onBarcodeScanned("3401234567890")
            advanceUntilIdle()
            vm.onQuickAdd(15)
            vm.onValidate()

            advanceTimeBy(REPLENISH_UNDO_DELAY_MS + 1)
            advanceUntilIdle()

            val call = fakeRepo.updateStockCalls.single()
            assertEquals(42L, call.productId)
            assertEquals(25, call.quantity)
            assertEquals(3L, call.warehouseId)
            assertNull(call.combinationId)
            assertTrue("L'écriture terminée doit être retirée de la file d'attente", vm.uiState.value.pendingWrites.isEmpty())
        }

    @Test
    fun `annuler avant la fin de la fenetre empeche l ecriture`() =
        runTest {
            fakeRepo.barcodeSearchResult = listOf(buildProduct(1L, quantity = 10))

            val vm = buildViewModel()
            vm.onBarcodeScanned("3401234567890")
            advanceUntilIdle()
            vm.onQuickAdd(15)
            vm.onValidate()
            val id = vm.uiState.value.pendingWrites.single().id

            vm.onCancelPendingWrite(id)
            advanceTimeBy(REPLENISH_UNDO_DELAY_MS + 1)
            advanceUntilIdle()

            assertTrue(fakeRepo.updateStockCalls.isEmpty())
            assertTrue(vm.uiState.value.pendingWrites.isEmpty())
        }

    @Test
    fun `une combinaison matchee envoie son combinationId a l ecriture`() =
        runTest {
            val combination = MatchedCombination(id = 99L, name = "Coloris - Bleu", quantity = 7)
            val product = buildProduct(1L, quantity = 120).copy(matchedCombination = combination)
            fakeRepo.barcodeSearchResult = listOf(product)

            val vm = buildViewModel()
            vm.onBarcodeScanned("3401234567899")
            advanceUntilIdle()
            vm.onQuickAdd(5)
            vm.onValidate()

            advanceTimeBy(REPLENISH_UNDO_DELAY_MS + 1)
            advanceUntilIdle()

            val call = fakeRepo.updateStockCalls.single()
            assertEquals(1L, call.productId)
            assertEquals(99L, call.combinationId)
            assertEquals(12, call.quantity)
        }

    @Test
    fun `deux validations successives coexistent independamment`() =
        runTest {
            fakeRepo.barcodeSearchResult = listOf(buildProduct(1L, quantity = 10))
            val vm = buildViewModel()
            vm.onBarcodeScanned("3401234567890")
            advanceUntilIdle()
            vm.onQuickAdd(5)
            vm.onValidate()
            val firstId = vm.uiState.value.pendingWrites.single().id

            // Réarme immédiatement : on peut enchaîner sur un second scan/validate pendant que
            // le premier compte encore (réappro en série). NB : runCurrent() plutôt que
            // advanceUntilIdle() — ce dernier ferait avancer le temps virtuel jusqu'à déclencher
            // le job différé du 1er onValidate (delay 10 s), ce qui n'est pas le but ici.
            fakeRepo.barcodeSearchResult = listOf(buildProduct(2L, quantity = 4))
            vm.onBarcodeScanned("1112223334445")
            testDispatcher.scheduler.runCurrent()
            vm.onQuickAdd(10)
            vm.onValidate()

            assertEquals(2, vm.uiState.value.pendingWrites.size)

            // On annule uniquement le premier : le second doit tout de même s'écrire à terme.
            vm.onCancelPendingWrite(firstId)
            advanceTimeBy(REPLENISH_UNDO_DELAY_MS + 1)
            advanceUntilIdle()

            val call = fakeRepo.updateStockCalls.single()
            assertEquals(2L, call.productId)
            assertEquals(14, call.quantity)
        }

    @Test
    fun `un echec d ecriture en tache de fond expose un message`() =
        runTest {
            fakeRepo.barcodeSearchResult = listOf(buildProduct(1L, quantity = 10))
            fakeRepo.shouldThrowOnUpdateStock = true

            val vm = buildViewModel()
            vm.onBarcodeScanned("3401234567890")
            advanceUntilIdle()
            vm.onQuickAdd(5)
            vm.onValidate()

            advanceTimeBy(REPLENISH_UNDO_DELAY_MS + 1)
            advanceUntilIdle()

            assertTrue(vm.uiState.value.writeErrorMessage != null)
        }

    // ─── Association d'un EAN inconnu (délégué, cf. StockReplenishScreen) ────

    @Test
    fun `onProductResolvedExternally reprend la main apres une association reussie`() =
        runTest {
            fakeRepo.barcodeSearchResult = emptyList()
            val vm = buildViewModel()
            vm.onBarcodeScanned("0000000000000")
            advanceUntilIdle()
            assertTrue(vm.uiState.value.notFound)

            val linked = buildProduct(9L, quantity = 4)
            vm.onProductResolvedExternally(linked)

            val state = vm.uiState.value
            assertFalse(state.notFound)
            assertEquals(linked, state.product)
            assertEquals(0, state.delta)
        }

    // ─── Passer sans valider ──────────────────────────────────────────────────

    @Test
    fun `onSkip repart a l etat de scan sans ecrire et conserve les ecritures en attente`() =
        runTest {
            fakeRepo.barcodeSearchResult = listOf(buildProduct(1L, quantity = 10))
            val vm = buildViewModel()
            vm.onBarcodeScanned("3401234567890")
            advanceUntilIdle()
            vm.onQuickAdd(5)
            vm.onValidate()
            assertEquals(1, vm.uiState.value.pendingWrites.size)

            // runCurrent() plutôt que advanceUntilIdle() : ce dernier ferait avancer le temps
            // virtuel jusqu'à déclencher le job différé du onValidate ci-dessus (delay 10 s).
            fakeRepo.barcodeSearchResult = listOf(buildProduct(2L, quantity = 1))
            vm.onBarcodeScanned("1112223334445")
            testDispatcher.scheduler.runCurrent()

            vm.onSkip()

            val state = vm.uiState.value
            assertNull(state.product)
            assertEquals(0, state.delta)
            assertTrue(state.isScannerActive)
            assertEquals(
                "Les écritures déjà validées ne doivent pas être perdues par onSkip",
                1,
                state.pendingWrites.size,
            )
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
