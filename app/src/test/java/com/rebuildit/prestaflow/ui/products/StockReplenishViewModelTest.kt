package com.rebuildit.prestaflow.ui.products

import app.cash.turbine.test
import com.rebuildit.prestaflow.core.network.NetworkErrorMapper
import com.rebuildit.prestaflow.domain.products.model.Combination
import com.rebuildit.prestaflow.domain.products.model.DEFAULT_QUICK_ADD_AMOUNTS
import com.rebuildit.prestaflow.domain.products.model.MatchedCombination
import com.rebuildit.prestaflow.domain.products.model.Product
import com.rebuildit.prestaflow.domain.products.model.ProductImage
import com.rebuildit.prestaflow.domain.products.model.ProductStock
import com.rebuildit.prestaflow.fakes.FakeLabelTextRecognizer
import com.rebuildit.prestaflow.fakes.FakeProductsRepository
import com.rebuildit.prestaflow.fakes.FakeStockReplenishPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
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
 * d'annulation.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StockReplenishViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var fakeRepo: FakeProductsRepository
    private lateinit var fakePrefsRepo: FakeStockReplenishPreferencesRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepo = FakeProductsRepository()
        fakePrefsRepo = FakeStockReplenishPreferencesRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel(): StockReplenishViewModel =
        StockReplenishViewModel(
            productsRepository = fakeRepo,
            networkErrorMapper = NetworkErrorMapper(),
            // Aucun test de ce fichier ne fournit de frame caméra (`onBarcodeScanned(code)` sans
            // 2ᵉ paramètre) : le secours OCR est donc toujours sauté (cf.
            // `attemptLabelFallback`), ce fake n'est jamais sollicité. Cf.
            // StockReplenishLabelFallbackTest pour l'orchestration OCR elle-même.
            labelTextRecognizer = FakeLabelTextRecognizer(),
            stockReplenishPreferencesRepository = fakePrefsRepo,
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

    // ─── Boutons rapides configurables (Lot 2) ───────────────────────────────

    @Test
    fun `sans preference enregistree expose le defaut Lot 1`() =
        runTest {
            val vm = buildViewModel()
            backgroundScope.launch { vm.quickAddAmounts.collect {} }
            advanceUntilIdle()

            assertEquals(DEFAULT_QUICK_ADD_AMOUNTS, vm.quickAddAmounts.value)
        }

    @Test
    fun `un changement des preferences met a jour quickAddAmounts en direct`() =
        runTest {
            val vm = buildViewModel()
            backgroundScope.launch { vm.quickAddAmounts.collect {} }
            advanceUntilIdle()

            fakePrefsRepo.emit(listOf(1, 25))
            advanceUntilIdle()

            assertEquals(listOf(1, 25), vm.quickAddAmounts.value)
        }

    @Test
    fun `onQuickAdd fonctionne avec un montant issu des preferences configurees`() =
        runTest {
            fakeRepo.barcodeSearchResult = listOf(buildProduct(1L, quantity = 10))
            fakePrefsRepo.emit(listOf(1, 25, 50))

            val vm = buildViewModel()
            backgroundScope.launch { vm.quickAddAmounts.collect {} }
            vm.onBarcodeScanned("3401234567890")
            advanceUntilIdle()

            vm.onQuickAdd(25)

            assertEquals(25, vm.uiState.value.delta)
        }

    // ─── Compteur de session (Lot 3) ─────────────────────────────────────────

    @Test
    fun `valider incremente le recap de session de facon optimiste`() =
        runTest {
            fakeRepo.barcodeSearchResult = listOf(buildProduct(1L, quantity = 10))
            val vm = buildViewModel()
            vm.onBarcodeScanned("3401234567890")
            advanceUntilIdle()
            vm.onQuickAdd(15)

            vm.onValidate()

            val recap = vm.uiState.value.sessionRecap
            assertEquals("Un article validé", 1, recap.articleCount)
            assertEquals("15 unités validées", 15, recap.unitsCount)
        }

    @Test
    fun `deux validations successives cumulent le recap de session`() =
        runTest {
            fakeRepo.barcodeSearchResult = listOf(buildProduct(1L, quantity = 10))
            val vm = buildViewModel()
            vm.onBarcodeScanned("3401234567890")
            advanceUntilIdle()
            vm.onQuickAdd(5)
            vm.onValidate()

            fakeRepo.barcodeSearchResult = listOf(buildProduct(2L, quantity = 4))
            vm.onBarcodeScanned("1112223334445")
            testDispatcher.scheduler.runCurrent()
            vm.onQuickAdd(10)
            vm.onValidate()

            val recap = vm.uiState.value.sessionRecap
            assertEquals(2, recap.articleCount)
            assertEquals(15, recap.unitsCount)
        }

    @Test
    fun `annuler une ecriture en attente decremente le recap de session`() =
        runTest {
            fakeRepo.barcodeSearchResult = listOf(buildProduct(1L, quantity = 10))
            val vm = buildViewModel()
            vm.onBarcodeScanned("3401234567890")
            advanceUntilIdle()
            vm.onQuickAdd(15)
            vm.onValidate()
            val id = vm.uiState.value.pendingWrites.single().id

            vm.onCancelPendingWrite(id)

            val recap = vm.uiState.value.sessionRecap
            assertEquals("L'annulation retire l'article du récap", 0, recap.articleCount)
            assertEquals(0, recap.unitsCount)
        }

    @Test
    fun `annuler un seul des deux en attente ne decremente que celui-la`() =
        runTest {
            fakeRepo.barcodeSearchResult = listOf(buildProduct(1L, quantity = 10))
            val vm = buildViewModel()
            vm.onBarcodeScanned("3401234567890")
            advanceUntilIdle()
            vm.onQuickAdd(5)
            vm.onValidate()
            val firstId = vm.uiState.value.pendingWrites.single().id

            fakeRepo.barcodeSearchResult = listOf(buildProduct(2L, quantity = 4))
            vm.onBarcodeScanned("1112223334445")
            testDispatcher.scheduler.runCurrent()
            vm.onQuickAdd(10)
            vm.onValidate()

            vm.onCancelPendingWrite(firstId)

            val recap = vm.uiState.value.sessionRecap
            assertEquals(1, recap.articleCount)
            assertEquals(10, recap.unitsCount)
        }

    @Test
    fun `un echec d ecriture en tache de fond decremente le recap de session`() =
        runTest {
            fakeRepo.barcodeSearchResult = listOf(buildProduct(1L, quantity = 10))
            fakeRepo.shouldThrowOnUpdateStock = true
            val vm = buildViewModel()
            vm.onBarcodeScanned("3401234567890")
            advanceUntilIdle()
            vm.onQuickAdd(5)
            vm.onValidate()
            assertEquals("Compté de façon optimiste avant l'échec", 1, vm.uiState.value.sessionRecap.articleCount)

            advanceTimeBy(REPLENISH_UNDO_DELAY_MS + 1)
            advanceUntilIdle()

            val recap = vm.uiState.value.sessionRecap
            assertEquals("Le stock n'a pas réellement changé : retiré du récap", 0, recap.articleCount)
            assertEquals(0, recap.unitsCount)
        }

    @Test
    fun `une ecriture reussie apres la fenetre ne modifie plus le recap deja compte`() =
        runTest {
            fakeRepo.barcodeSearchResult = listOf(buildProduct(1L, quantity = 10))
            val vm = buildViewModel()
            vm.onBarcodeScanned("3401234567890")
            advanceUntilIdle()
            vm.onQuickAdd(5)
            vm.onValidate()

            advanceTimeBy(REPLENISH_UNDO_DELAY_MS + 1)
            advanceUntilIdle()

            val recap = vm.uiState.value.sessionRecap
            assertEquals(1, recap.articleCount)
            assertEquals(5, recap.unitsCount)
        }

    @Test
    fun `onSkip conserve le recap de session accumule`() =
        runTest {
            fakeRepo.barcodeSearchResult = listOf(buildProduct(1L, quantity = 10))
            val vm = buildViewModel()
            vm.onBarcodeScanned("3401234567890")
            advanceUntilIdle()
            vm.onQuickAdd(5)
            vm.onValidate()

            fakeRepo.barcodeSearchResult = listOf(buildProduct(2L, quantity = 1))
            vm.onBarcodeScanned("1112223334445")
            testDispatcher.scheduler.runCurrent()
            vm.onSkip()

            assertEquals(1, vm.uiState.value.sessionRecap.articleCount)
            assertEquals(5, vm.uiState.value.sessionRecap.unitsCount)
        }

    @Test
    fun `une nouvelle session demarre le recap a zero`() =
        runTest {
            val vm = buildViewModel()
            assertEquals(0, vm.uiState.value.sessionRecap.articleCount)
            assertEquals(0, vm.uiState.value.sessionRecap.unitsCount)
        }

    @Test
    fun `queueAddedTick s incremente a chaque validation reussie`() =
        runTest {
            fakeRepo.barcodeSearchResult = listOf(buildProduct(1L, quantity = 10))
            val vm = buildViewModel()
            assertEquals(0, vm.uiState.value.queueAddedTick)

            vm.onBarcodeScanned("3401234567890")
            advanceUntilIdle()
            vm.onQuickAdd(5)
            vm.onValidate()

            assertEquals(1, vm.uiState.value.queueAddedTick)

            fakeRepo.barcodeSearchResult = listOf(buildProduct(2L, quantity = 1))
            vm.onBarcodeScanned("1112223334445")
            testDispatcher.scheduler.runCurrent()
            vm.onQuickAdd(3)
            vm.onValidate()

            assertEquals(2, vm.uiState.value.queueAddedTick)
        }

    // ─── Retour scan : feedback + doublon immédiat (Lot 3) ───────────────────

    @Test
    fun `un scan resolu emet un evenement de feedback`() =
        runTest {
            fakeRepo.barcodeSearchResult = listOf(buildProduct(1L, quantity = 10))
            val vm = buildViewModel()

            vm.scanFeedbackEvents.test {
                vm.onBarcodeScanned("3401234567890")
                awaitItem()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `un code introuvable n emet aucun evenement de feedback`() =
        runTest {
            fakeRepo.barcodeSearchResult = emptyList()
            val vm = buildViewModel()

            vm.scanFeedbackEvents.test {
                vm.onBarcodeScanned("0000000000000")
                advanceUntilIdle()
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `un doublon immediat du meme code est ignore sans nouveau lookup`() =
        runTest {
            fakeRepo.barcodeSearchResult = listOf(buildProduct(1L, quantity = 10))
            val vm = buildViewModel()

            vm.onBarcodeScanned("3401234567890")
            // Deuxième décodage du MÊME code avant que l'état ne bascule (scanner continu) :
            // volontairement PAS d'avanceUntilIdle() entre les deux, pour simuler la course.
            vm.onBarcodeScanned("3401234567890")
            advanceUntilIdle()

            assertEquals(
                "Le doublon immédiat ne doit déclencher qu'un seul lookup",
                1,
                fakeRepo.barcodeSearchCalls.size,
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
