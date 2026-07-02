package com.rebuildit.prestaflow.ui.products

import androidx.lifecycle.SavedStateHandle
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests unitaires JVM du [ProductEditViewModel] : préremplissage depuis le produit courant,
 * édition/validation des champs simples, calcul du dirty/canSave, sauvegarde succès/échec.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProductEditViewModelTest {
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

    private fun buildViewModel(productId: Long = 1L): ProductEditViewModel =
        ProductEditViewModel(
            savedStateHandle = SavedStateHandle(mapOf("productId" to productId)),
            productsRepository = fakeRepo,
            networkErrorMapper = NetworkErrorMapper(),
        )

    private fun buildProduct(
        id: Long = 1L,
        name: String = "Pull en laine",
        reference: String = "REF1",
        price: Double = 39.9,
        priceTaxExcl: Double? = 33.25,
    ) = Product(
        id = id,
        name = name,
        reference = reference,
        price = price,
        active = true,
        stock = ProductStock(quantity = 5),
        images = emptyList<ProductImage>(),
        updatedAt = "2026-07-01T00:00:00Z",
        description = "Une belle description longue.",
        descriptionShort = "Description courte.",
        priceTaxExcl = priceTaxExcl,
    )

    // ─── Préremplissage ──────────────────────────────────────────────────────

    @Test
    fun `l ecran preremplit les champs depuis le produit courant`() =
        runTest {
            val product = buildProduct()
            fakeRepo.setProducts(listOf(product))

            val vm = buildViewModel(product.id)
            advanceUntilIdle()

            val state = vm.uiState.value
            assertFalse(state.isLoading)
            assertFalse(state.productNotFound)
            assertEquals("Pull en laine", state.name)
            assertEquals("Une belle description longue.", state.description)
            assertEquals("Description courte.", state.descriptionShort)
            assertEquals("REF1", state.reference)
            assertEquals("33.25", state.priceText)
            assertTrue(state.active)
            assertFalse("Aucune édition encore : pas dirty", state.isDirty)
            assertFalse("Pas de modif → save désactivé", state.canSave)
        }

    @Test
    fun `preremplissage du prix retombe sur le prix TTC si price_tax_excl absent`() =
        runTest {
            val product = buildProduct(priceTaxExcl = null, price = 19.9)
            fakeRepo.setProducts(listOf(product))

            val vm = buildViewModel(product.id)
            advanceUntilIdle()

            assertEquals("19.9", vm.uiState.value.priceText)
        }

    @Test
    fun `produit introuvable expose productNotFound`() =
        runTest {
            fakeRepo.setProducts(emptyList())

            val vm = buildViewModel(999L)
            advanceUntilIdle()

            val state = vm.uiState.value
            assertFalse(state.isLoading)
            assertTrue(state.productNotFound)
        }

    // ─── Édition / dirty / validation ───────────────────────────────────────

    @Test
    fun `modifier un champ passe l etat en dirty et active canSave si valide`() =
        runTest {
            val product = buildProduct()
            fakeRepo.setProducts(listOf(product))
            val vm = buildViewModel(product.id)
            advanceUntilIdle()

            vm.onNameChange("Nouveau nom")

            val state = vm.uiState.value
            assertTrue(state.isDirty)
            assertTrue(state.canSave)
        }

    @Test
    fun `revenir a la valeur d origine annule le dirty`() =
        runTest {
            val product = buildProduct()
            fakeRepo.setProducts(listOf(product))
            val vm = buildViewModel(product.id)
            advanceUntilIdle()

            vm.onNameChange("Nouveau nom")
            vm.onNameChange(product.name)

            assertFalse(vm.uiState.value.isDirty)
        }

    @Test
    fun `nom vide est invalide et bloque canSave`() =
        runTest {
            val product = buildProduct()
            fakeRepo.setProducts(listOf(product))
            val vm = buildViewModel(product.id)
            advanceUntilIdle()

            vm.onNameChange("")

            val state = vm.uiState.value
            assertTrue(state.nameError)
            assertFalse(state.canSave)
        }

    @Test
    fun `prix negatif ou non numerique est invalide`() =
        runTest {
            val product = buildProduct()
            fakeRepo.setProducts(listOf(product))
            val vm = buildViewModel(product.id)
            advanceUntilIdle()

            vm.onPriceChange("-5")
            assertTrue(vm.uiState.value.priceError)

            vm.onPriceChange("abc")
            assertTrue(vm.uiState.value.priceError)

            vm.onPriceChange("12.5")
            assertFalse(vm.uiState.value.priceError)
        }

    @Test
    fun `reference de plus de 64 caracteres est invalide`() =
        runTest {
            val product = buildProduct()
            fakeRepo.setProducts(listOf(product))
            val vm = buildViewModel(product.id)
            advanceUntilIdle()

            vm.onReferenceChange("R".repeat(65))

            val state = vm.uiState.value
            assertTrue(state.referenceError)
            assertFalse(state.canSave)
        }

    // ─── Sauvegarde ──────────────────────────────────────────────────────────

    @Test
    fun `onSave envoie uniquement les champs modifies et applique le produit renvoye`() =
        runTest {
            val product = buildProduct()
            fakeRepo.setProducts(listOf(product))
            val vm = buildViewModel(product.id)
            advanceUntilIdle()

            vm.onNameChange("Nom modifié")
            val updated = product.copy(name = "Nom modifié")
            fakeRepo.updateProductFieldsResult = updated

            vm.onSave()
            advanceUntilIdle()

            val call = fakeRepo.updateProductFieldsCalls.single()
            assertEquals(product.id, call.productId)
            assertEquals("Nom modifié", call.fields.name)
            assertEquals(null, call.fields.description)
            assertEquals(null, call.fields.descriptionShort)
            assertEquals(null, call.fields.reference)
            assertEquals(null, call.fields.priceTaxExcl)
            assertEquals(null, call.fields.active)

            val state = vm.uiState.value
            assertFalse(state.isSaving)
            assertFalse(state.isDirty)
            assertTrue(state.saveSuccess)
        }

    @Test
    fun `un echec de sauvegarde expose une erreur et n a pas saveSuccess`() =
        runTest {
            val product = buildProduct()
            fakeRepo.setProducts(listOf(product))
            val vm = buildViewModel(product.id)
            advanceUntilIdle()

            vm.onNameChange("Nom modifié")
            fakeRepo.shouldThrowOnUpdateProductFields = true

            vm.onSave()
            advanceUntilIdle()

            val state = vm.uiState.value
            assertFalse(state.isSaving)
            assertFalse(state.saveSuccess)
            assertTrue(state.error != null)
        }

    @Test
    fun `onSave sans modification n appelle pas le repository`() =
        runTest {
            val product = buildProduct()
            fakeRepo.setProducts(listOf(product))
            val vm = buildViewModel(product.id)
            advanceUntilIdle()

            vm.onSave()
            advanceUntilIdle()

            assertTrue(fakeRepo.updateProductFieldsCalls.isEmpty())
        }

    @Test
    fun `clearError remet error a null`() =
        runTest {
            val product = buildProduct()
            fakeRepo.setProducts(listOf(product))
            val vm = buildViewModel(product.id)
            advanceUntilIdle()

            vm.clearError()
            assertEquals(null, vm.uiState.value.error)
        }
}
