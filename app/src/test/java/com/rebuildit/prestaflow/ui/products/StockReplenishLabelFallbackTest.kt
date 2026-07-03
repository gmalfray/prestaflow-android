package com.rebuildit.prestaflow.ui.products

import android.app.Application
import android.graphics.Bitmap
import com.rebuildit.prestaflow.core.network.NetworkErrorMapper
import com.rebuildit.prestaflow.domain.products.model.Product
import com.rebuildit.prestaflow.domain.products.model.ProductImage
import com.rebuildit.prestaflow.domain.products.model.ProductStock
import com.rebuildit.prestaflow.fakes.FakeLabelTextRecognizer
import com.rebuildit.prestaflow.fakes.FakeProductsRepository
import com.rebuildit.prestaflow.fakes.FakeStockReplenishPreferencesRepository
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests unitaires du secours OCR de [StockReplenishViewModel] (lecture d'étiquette quand l'EAN
 * scanné est introuvable, v0.38.0) : orchestration "introuvable → OCR → candidats → fallback".
 *
 * Fichier SÉPARÉ de [StockReplenishViewModelTest] (Robolectric requis ici pour construire de vrais
 * [Bitmap] via [Bitmap.createBitmap] — indisponible en JVM pur, cf.
 * `testOptions.unitTests.isReturnDefaultValues` dans `app/build.gradle.kts`, qui renvoie `null` pour
 * les méthodes Android non-shadowées) : garde le fichier historique rapide et inchangé.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class StockReplenishLabelFallbackTest {
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var fakeRepo: FakeProductsRepository
    private lateinit var fakePrefsRepo: FakeStockReplenishPreferencesRepository
    private lateinit var fakeRecognizer: FakeLabelTextRecognizer

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepo = FakeProductsRepository()
        fakePrefsRepo = FakeStockReplenishPreferencesRepository()
        fakeRecognizer = FakeLabelTextRecognizer()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel(): StockReplenishViewModel =
        StockReplenishViewModel(
            productsRepository = fakeRepo,
            networkErrorMapper = NetworkErrorMapper(),
            labelTextRecognizer = fakeRecognizer,
            stockReplenishPreferencesRepository = fakePrefsRepo,
        )

    private fun testFrame(): Bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)

    private fun buildProduct(
        id: Long,
        reference: String = "REF$id",
    ) = Product(
        id = id,
        name = "Produit $id",
        reference = reference,
        price = 9.99,
        active = true,
        stock = ProductStock(quantity = 10),
        images = emptyList<ProductImage>(),
        updatedAt = "2026-07-01T00:00:00Z",
        ean13 = "340123456789$id",
    )

    @Test
    fun `un candidat trouve via l etiquette est expose en suggestion et desactive le scanner`() =
        runTest {
            fakeRepo.barcodeSearchResult = emptyList()
            fakeRecognizer.recognizedText = "RICORUMI049\n049"
            val candidate = buildProduct(9L, reference = "RICORUMI049")
            fakeRepo.searchProductsResult = listOf(candidate)

            val vm = buildViewModel()
            vm.onBarcodeScanned("0000000000000") { testFrame() }
            advanceUntilIdle()

            val state = vm.uiState.value
            assertTrue(state.notFound)
            assertEquals(listOf(candidate), state.labelSuggestions)
            assertFalse("Scanner en pause tant que l'association n'est pas résolue", state.isScannerActive)
            assertTrue(
                "La recherche produit doit avoir tenté au moins le jeton alphanumérique",
                fakeRepo.searchProductsCalls.contains("RICORUMI049"),
            )
        }

    @Test
    fun `aucun texte lisible sur l etiquette retombe silencieusement sur notFound sans suggestion`() =
        runTest {
            fakeRepo.barcodeSearchResult = emptyList()
            fakeRecognizer.recognizedText = ""

            val vm = buildViewModel()
            vm.onBarcodeScanned("0000000000000") { testFrame() }
            advanceUntilIdle()

            val state = vm.uiState.value
            assertTrue(state.notFound)
            assertTrue(state.labelSuggestions.isEmpty())
        }

    @Test
    fun `des jetons sans aucun produit correspondant retombent sur notFound sans suggestion`() =
        runTest {
            fakeRepo.barcodeSearchResult = emptyList()
            fakeRecognizer.recognizedText = "RICORUMI049"
            fakeRepo.searchProductsResult = emptyList()

            val vm = buildViewModel()
            vm.onBarcodeScanned("0000000000000") { testFrame() }
            advanceUntilIdle()

            val state = vm.uiState.value
            assertTrue(state.notFound)
            assertTrue(state.labelSuggestions.isEmpty())
        }

    @Test
    fun `un OCR trop lent depasse le timeout dur et retombe sans suggestion`() =
        runTest {
            fakeRepo.barcodeSearchResult = emptyList()
            fakeRecognizer.recognizedText = "RICORUMI049"
            fakeRecognizer.delayMs = LABEL_FALLBACK_TIMEOUT_MS_FOR_TEST + 500
            fakeRepo.searchProductsResult = listOf(buildProduct(9L))

            val vm = buildViewModel()
            vm.onBarcodeScanned("0000000000000") { testFrame() }
            advanceUntilIdle()

            val state = vm.uiState.value
            assertTrue("Le secours doit s'effacer après le timeout, pas rester bloqué", state.notFound)
            assertFalse(state.isLabelSearchLoading)
            assertTrue(
                "Le secours ne doit pas exposer un résultat arrivé après le timeout",
                state.labelSuggestions.isEmpty(),
            )
        }

    @Test
    fun `aucune frame disponible saute l OCR et retombe immediatement`() =
        runTest {
            fakeRepo.barcodeSearchResult = emptyList()
            fakeRecognizer.recognizedText = "RICORUMI049"
            fakeRepo.searchProductsResult = listOf(buildProduct(9L))

            val vm = buildViewModel()
            vm.onBarcodeScanned("0000000000000") { null }
            advanceUntilIdle()

            val state = vm.uiState.value
            assertTrue(state.notFound)
            assertTrue(state.labelSuggestions.isEmpty())
            assertTrue(
                "Sans frame, l'OCR ne doit même pas être tenté",
                fakeRecognizer.recognizeCalls.isEmpty(),
            )
        }

    @Test
    fun `les recherches par jeton sont lancees en parallele pas en serie`() =
        runTest {
            fakeRepo.barcodeSearchResult = emptyList()
            // Deux jetons alphanumériques distincts (mêlent lettres+chiffres) sur des lignes
            // séparées pour dépasser 1 seul jeton candidat.
            fakeRecognizer.recognizedText = "ABC123\nDEF456"
            fakeRepo.searchProductsResult = listOf(buildProduct(1L))

            val vm = buildViewModel()
            vm.onBarcodeScanned("0000000000000") { testFrame() }
            advanceUntilIdle()

            assertEquals(
                "Les deux jetons doivent avoir été recherchés",
                setOf("ABC123", "DEF456"),
                fakeRepo.searchProductsCalls.toSet(),
            )
        }

    private companion object {
        // Doit rester alignée sur LABEL_FALLBACK_TIMEOUT_MS (privée dans StockReplenishViewModel.kt).
        const val LABEL_FALLBACK_TIMEOUT_MS_FOR_TEST = 1_300L
    }
}
