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
import com.rebuildit.prestaflow.fakes.FakeReplenishSessionRepository
import com.rebuildit.prestaflow.fakes.FakeStockReplenishPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
 * Tests unitaires JVM du [StockReplenishViewModel] : écran « Ajout / réappro stock » —
 * accumulation du delta (boutons rapides + saisie libre), journal de session persistant (fusion par
 * cible, annulation sans fenêtre de temps) et validation définitive (envoi incrémental en lot).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StockReplenishViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var fakeRepo: FakeProductsRepository
    private lateinit var fakePrefsRepo: FakeStockReplenishPreferencesRepository
    private lateinit var fakeSessionRepo: FakeReplenishSessionRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepo = FakeProductsRepository()
        fakePrefsRepo = FakeStockReplenishPreferencesRepository()
        fakeSessionRepo = FakeReplenishSessionRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel(): StockReplenishViewModel =
        StockReplenishViewModel(
            productsRepository = fakeRepo,
            replenishSessionRepository = fakeSessionRepo,
            networkErrorMapper = NetworkErrorMapper(),
            // Aucun test de ce fichier ne fournit de frame caméra (`onBarcodeScanned(code)` sans
            // 2ᵉ paramètre) : le secours OCR est donc toujours sauté (cf.
            // `attemptLabelFallback`), ce fake n'est jamais sollicité. Cf.
            // StockReplenishLabelFallbackTest pour l'orchestration OCR elle-même.
            labelTextRecognizer = FakeLabelTextRecognizer(),
            stockReplenishPreferencesRepository = fakePrefsRepo,
        )

    /**
     * [StockReplenishViewModel.logEntries]/[StockReplenishViewModel.sessionRecap] sont des
     * StateFlows `WhileSubscribed` (même pattern que [StockReplenishViewModel.quickAddAmounts]) :
     * sans souscripteur actif, `.value` resterait figé à leur valeur initiale.
     */
    private fun TestScope.collectLogState(vm: StockReplenishViewModel) {
        backgroundScope.launch { vm.logEntries.collect {} }
        backgroundScope.launch { vm.sessionRecap.collect {} }
    }

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
            assertTrue(fakeRepo.adjustStockCalls.isEmpty())
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

    // ─── Journalisation (aucune écriture avant validation définitive) ───────

    @Test
    fun `valider rearme immediatement le scanner et n ecrit rien`() =
        runTest {
            fakeRepo.barcodeSearchResult = listOf(buildProduct(1L, quantity = 10))
            val vm = buildViewModel()
            collectLogState(vm)

            vm.onBarcodeScanned("3401234567890")
            advanceUntilIdle()
            vm.onQuickAdd(15)

            vm.onValidate()
            advanceUntilIdle()

            val state = vm.uiState.value
            assertTrue("Le scanner doit être réarmé immédiatement après Valider", state.isScannerActive)
            assertNull(state.product)
            assertEquals(1, vm.logEntries.value.size)
            assertEquals(15, vm.logEntries.value.first().delta)
            assertTrue("Aucun appel réseau tant que la session n'est pas validée", fakeRepo.updateStockCalls.isEmpty())
            assertTrue(fakeRepo.adjustStockCalls.isEmpty())
        }

    @Test
    fun `deux scans du meme produit fusionnent en une seule ligne du journal`() =
        runTest {
            // Codes différents (mais même produit id=1) : évite le filtre anti-doublon immédiat
            // (fenêtre de 1,2 s en temps RÉEL, cf. onBarcodeScanned) qui n'a rien à voir avec ce test
            // — dans la vraie vie, Greg rescanne le même code après un délai bien supérieur.
            fakeRepo.barcodeSearchResult = listOf(buildProduct(1L, quantity = 10))
            val vm = buildViewModel()
            collectLogState(vm)

            vm.onBarcodeScanned("3401234567890")
            advanceUntilIdle()
            vm.onQuickAdd(1)
            vm.onValidate()
            advanceUntilIdle()

            vm.onBarcodeScanned("1112223334445")
            advanceUntilIdle()
            vm.onQuickAdd(1)
            vm.onValidate()
            advanceUntilIdle()

            val entries = vm.logEntries.value
            assertEquals("Une seule ligne pour la même cible", 1, entries.size)
            assertEquals(2, entries.single().delta)
            assertEquals(1, vm.sessionRecap.value.articleCount)
            assertEquals(2, vm.sessionRecap.value.unitsCount)
        }

    @Test
    fun `deux produits differents restent sur deux lignes distinctes`() =
        runTest {
            fakeRepo.barcodeSearchResult = listOf(buildProduct(1L, quantity = 10))
            val vm = buildViewModel()
            collectLogState(vm)

            vm.onBarcodeScanned("3401234567890")
            advanceUntilIdle()
            vm.onQuickAdd(5)
            vm.onValidate()
            advanceUntilIdle()

            fakeRepo.barcodeSearchResult = listOf(buildProduct(2L, quantity = 4))
            vm.onBarcodeScanned("1112223334445")
            advanceUntilIdle()
            vm.onQuickAdd(10)
            vm.onValidate()
            advanceUntilIdle()

            val entries = vm.logEntries.value
            assertEquals(2, entries.size)
            assertEquals(setOf(1L, 2L), entries.map { it.productId }.toSet())
            assertEquals(2, vm.sessionRecap.value.articleCount)
            assertEquals(15, vm.sessionRecap.value.unitsCount)
        }

    @Test
    fun `une combinaison matchee est fusionnee separement du produit parent`() =
        runTest {
            val combination = MatchedCombination(id = 99L, name = "Coloris - Bleu", quantity = 7)
            val product = buildProduct(1L, quantity = 120).copy(matchedCombination = combination)
            fakeRepo.barcodeSearchResult = listOf(product)
            val vm = buildViewModel()
            collectLogState(vm)

            vm.onBarcodeScanned("3401234567899")
            advanceUntilIdle()
            vm.onQuickAdd(5)
            vm.onValidate()
            advanceUntilIdle()

            val entry = vm.logEntries.value.single()
            assertEquals(1L, entry.productId)
            assertEquals(99L, entry.combinationId)
            assertEquals(5, entry.delta)
        }

    @Test
    fun `annuler une ligne du journal ne declenche aucun appel reseau`() =
        runTest {
            fakeRepo.barcodeSearchResult = listOf(buildProduct(1L, quantity = 10))
            val vm = buildViewModel()
            collectLogState(vm)

            vm.onBarcodeScanned("3401234567890")
            advanceUntilIdle()
            vm.onQuickAdd(15)
            vm.onValidate()
            advanceUntilIdle()

            val entryId = vm.logEntries.value.single().id
            vm.onRemoveLogEntry(entryId)
            advanceUntilIdle()

            assertTrue("La ligne annulée disparaît du journal", vm.logEntries.value.isEmpty())
            assertEquals(0, vm.sessionRecap.value.articleCount)
            assertTrue(fakeRepo.updateStockCalls.isEmpty())
            assertTrue(fakeRepo.adjustStockCalls.isEmpty())
        }

    @Test
    fun `onSkip ne journalise rien et conserve le journal deja rempli`() =
        runTest {
            fakeRepo.barcodeSearchResult = listOf(buildProduct(1L, quantity = 10))
            val vm = buildViewModel()
            collectLogState(vm)

            vm.onBarcodeScanned("3401234567890")
            advanceUntilIdle()
            vm.onQuickAdd(5)
            vm.onValidate()
            advanceUntilIdle()
            assertEquals(1, vm.logEntries.value.size)

            fakeRepo.barcodeSearchResult = listOf(buildProduct(2L, quantity = 1))
            vm.onBarcodeScanned("1112223334445")
            advanceUntilIdle()

            vm.onSkip()

            val state = vm.uiState.value
            assertNull(state.product)
            assertEquals(0, state.delta)
            assertTrue(state.isScannerActive)
            assertEquals(
                "Le journal déjà rempli ne doit pas être perdu par onSkip",
                1,
                vm.logEntries.value.size,
            )
        }

    @Test
    fun `queueAddedTick s incremente a chaque ligne journalisee`() =
        runTest {
            fakeRepo.barcodeSearchResult = listOf(buildProduct(1L, quantity = 10))
            val vm = buildViewModel()
            assertEquals(0, vm.uiState.value.queueAddedTick)

            vm.onBarcodeScanned("3401234567890")
            advanceUntilIdle()
            vm.onQuickAdd(5)
            vm.onValidate()
            advanceUntilIdle()

            assertEquals(1, vm.uiState.value.queueAddedTick)

            fakeRepo.barcodeSearchResult = listOf(buildProduct(2L, quantity = 1))
            vm.onBarcodeScanned("1112223334445")
            advanceUntilIdle()
            vm.onQuickAdd(3)
            vm.onValidate()
            advanceUntilIdle()

            assertEquals(2, vm.uiState.value.queueAddedTick)
        }

    // ─── Validation définitive (envoi du journal, incrémental) ──────────────

    @Test
    fun `la validation definitive envoie un increment par ligne du journal`() =
        runTest {
            fakeRepo.barcodeSearchResult = listOf(buildProduct(1L, quantity = 10, warehouseId = 3L))
            val vm = buildViewModel()
            collectLogState(vm)

            vm.onBarcodeScanned("3401234567890")
            advanceUntilIdle()
            vm.onQuickAdd(5)
            vm.onValidate()
            advanceUntilIdle()

            fakeRepo.barcodeSearchResult = listOf(buildProduct(2L, quantity = 4))
            vm.onBarcodeScanned("1112223334445")
            advanceUntilIdle()
            vm.onQuickAdd(10)
            vm.onValidate()
            advanceUntilIdle()

            vm.onSubmitSession()
            advanceUntilIdle()

            assertEquals(2, fakeRepo.adjustStockCalls.size)
            val callProduct1 = fakeRepo.adjustStockCalls.single { it.productId == 1L }
            assertEquals(5, callProduct1.delta)
            assertEquals(3L, callProduct1.warehouseId)
            val callProduct2 = fakeRepo.adjustStockCalls.single { it.productId == 2L }
            assertEquals(10, callProduct2.delta)
            assertTrue("Aucune écriture ABSOLUE ne doit jamais partir", fakeRepo.updateStockCalls.isEmpty())
            assertTrue("Le journal est vidé après un envoi intégral réussi", vm.logEntries.value.isEmpty())
            assertEquals(0, vm.sessionRecap.value.articleCount)
        }

    @Test
    fun `un echec partiel laisse le journal exploitable pour un nouvel essai`() =
        runTest {
            fakeRepo.barcodeSearchResult = listOf(buildProduct(1L, quantity = 10))
            val vm = buildViewModel()
            collectLogState(vm)

            vm.onBarcodeScanned("3401234567890")
            advanceUntilIdle()
            vm.onQuickAdd(5)
            vm.onValidate()
            advanceUntilIdle()

            fakeRepo.barcodeSearchResult = listOf(buildProduct(2L, quantity = 4))
            vm.onBarcodeScanned("1112223334445")
            advanceUntilIdle()
            vm.onQuickAdd(10)
            vm.onValidate()
            advanceUntilIdle()

            fakeRepo.productIdsFailingOnAdjustStock += 2L

            vm.onSubmitSession()
            advanceUntilIdle()

            // La ligne réussie (produit 1) a disparu du journal ; celle en échec (produit 2) y reste,
            // avec un message d'erreur associé.
            val remaining = vm.logEntries.value
            assertEquals(1, remaining.size)
            assertEquals(2L, remaining.single().productId)
            assertTrue(vm.uiState.value.submitErrors.containsKey(remaining.single().id))
            assertFalse(vm.uiState.value.isSubmittingSession)

            // Un nouvel essai ne renvoie QUE la ligne encore en échec — pas de double écriture sur
            // la ligne déjà réussie.
            fakeRepo.adjustStockCalls.clear()
            fakeRepo.productIdsFailingOnAdjustStock.clear()
            vm.onSubmitSession()
            advanceUntilIdle()

            assertEquals(1, fakeRepo.adjustStockCalls.size)
            assertEquals(2L, fakeRepo.adjustStockCalls.single().productId)
            assertTrue("La ligne réessayée avec succès disparaît à son tour", vm.logEntries.value.isEmpty())
            assertTrue(vm.uiState.value.submitErrors.isEmpty())
        }

    @Test
    fun `un journal vide ne declenche aucun appel a la validation definitive`() =
        runTest {
            val vm = buildViewModel()
            collectLogState(vm)

            vm.onSubmitSession()
            advanceUntilIdle()

            assertTrue(fakeRepo.adjustStockCalls.isEmpty())
            assertFalse(vm.uiState.value.isSubmittingSession)
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
