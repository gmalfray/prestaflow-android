package com.rebuildit.prestaflow.ui.products

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rebuildit.prestaflow.core.network.NetworkErrorMapper
import com.rebuildit.prestaflow.core.ui.UiText
import com.rebuildit.prestaflow.domain.products.ProductsRepository
import com.rebuildit.prestaflow.domain.products.model.Product
import com.rebuildit.prestaflow.domain.products.model.ProductImage
import com.rebuildit.prestaflow.domain.products.model.ProductUpdateFields
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import javax.inject.Inject

/** Longueur maximale de la référence produit, alignée sur la validation côté connecteur. */
private const val MAX_REFERENCE_LENGTH = 64

@HiltViewModel
class ProductEditViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val productsRepository: ProductsRepository,
        private val networkErrorMapper: NetworkErrorMapper,
    ) : ViewModel() {
        private val productId: Long = checkNotNull(savedStateHandle["productId"])

        private val _uiState = MutableStateFlow(ProductEditUiState())
        val uiState: StateFlow<ProductEditUiState> = _uiState.asStateFlow()

        /** Instantané du produit tel que chargé, servant de référence pour le calcul du diff/dirty. */
        private var original: Product? = null

        init {
            loadProduct()
        }

        private fun loadProduct() {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true) }
                runCatching { productsRepository.refreshProduct(productId, forceRemote = false) }
                    .onFailure { error -> Timber.w(error, "Failed to refresh product $productId before edit") }

                val product = productsRepository.observeProduct(productId).firstOrNull()
                if (product == null) {
                    _uiState.update { it.copy(isLoading = false, productNotFound = true) }
                    return@launch
                }
                applyProduct(product)
            }
        }

        private fun applyProduct(product: Product) {
            original = product
            _uiState.update {
                it.copy(
                    isLoading = false,
                    productNotFound = false,
                    name = product.name,
                    description = product.description.orEmpty(),
                    descriptionShort = product.descriptionShort.orEmpty(),
                    reference = product.reference,
                    priceText = referencePrice(product).toString(),
                    active = product.active,
                    images = product.images,
                    isDirty = false,
                )
            }
        }

        /**
         * Met à jour uniquement la liste d'images depuis le produit renvoyé par le serveur, sans
         * toucher aux autres champs — évite d'écraser une édition texte en cours (dirty/non
         * sauvegardée) suite à un ajout/suppression d'image, qui est appliqué immédiatement côté
         * serveur (pas soumis au bouton Enregistrer).
         */
        private fun applyImages(product: Product) {
            original = original?.copy(images = product.images) ?: product
            _uiState.update { it.copy(images = product.images) }
        }

        fun onNameChange(value: String) = updateField { it.copy(name = value) }

        fun onDescriptionChange(value: String) = updateField { it.copy(description = value) }

        fun onDescriptionShortChange(value: String) = updateField { it.copy(descriptionShort = value) }

        fun onReferenceChange(value: String) = updateField { it.copy(reference = value) }

        fun onPriceChange(value: String) = updateField { it.copy(priceText = value) }

        fun onActiveChange(value: Boolean) = updateField { it.copy(active = value) }

        private fun updateField(transform: (ProductEditUiState) -> ProductEditUiState) {
            _uiState.update { state ->
                val updated = transform(state)
                updated.copy(isDirty = computeDirty(updated))
            }
        }

        private fun computeDirty(state: ProductEditUiState): Boolean {
            val ref = original ?: return false
            return state.name != ref.name ||
                state.description != ref.description.orEmpty() ||
                state.descriptionShort != ref.descriptionShort.orEmpty() ||
                state.reference != ref.reference ||
                state.priceValue != referencePrice(ref) ||
                state.active != ref.active
        }

        fun onSave() {
            val state = _uiState.value
            val ref = original
            if (!state.canSave || ref == null) return

            val fields =
                ProductUpdateFields(
                    name = state.name.takeIf { it != ref.name },
                    description = state.description.takeIf { it != ref.description.orEmpty() },
                    descriptionShort = state.descriptionShort.takeIf { it != ref.descriptionShort.orEmpty() },
                    reference = state.reference.takeIf { it != ref.reference },
                    priceTaxExcl = state.priceValue?.takeIf { it != referencePrice(ref) },
                    active = state.active.takeIf { it != ref.active },
                )
            if (fields.isEmpty) return

            viewModelScope.launch {
                _uiState.update { it.copy(isSaving = true, error = null) }
                runCatching { productsRepository.updateProductFields(productId, fields) }
                    .onSuccess { updated ->
                        applyProduct(updated)
                        _uiState.update { it.copy(isSaving = false, saveSuccess = true) }
                    }
                    .onFailure { error ->
                        Timber.w(error, "Failed to update product $productId fields")
                        _uiState.update { state2 -> state2.copy(isSaving = false, error = networkErrorMapper.map(error)) }
                    }
            }
        }

        /**
         * Envoie l'image [file] (déjà préparée/compressée par [com.rebuildit.prestaflow.core.media.ProductImagePreparer])
         * au serveur. Le fichier temporaire est toujours nettoyé après l'appel, succès ou échec.
         */
        fun onImageSelected(file: File) {
            viewModelScope.launch {
                _uiState.update { it.copy(isUploadingImage = true, error = null) }
                runCatching { productsRepository.uploadProductImage(productId, file) }
                    .onSuccess { updated ->
                        applyImages(updated)
                        _uiState.update { it.copy(isUploadingImage = false) }
                    }
                    .onFailure { error ->
                        Timber.w(error, "Failed to upload image for product $productId")
                        _uiState.update { state -> state.copy(isUploadingImage = false, error = networkErrorMapper.map(error)) }
                    }
                file.delete()
            }
        }

        /** Demande confirmation avant de supprimer l'image [imageId] (affichage d'un dialogue). */
        fun onDeleteImageRequested(imageId: Long) {
            _uiState.update { it.copy(pendingDeleteImageId = imageId) }
        }

        fun onDeleteImageCancelled() {
            _uiState.update { it.copy(pendingDeleteImageId = null) }
        }

        fun onDeleteImageConfirmed() {
            val imageId = _uiState.value.pendingDeleteImageId ?: return
            viewModelScope.launch {
                _uiState.update { it.copy(pendingDeleteImageId = null, deletingImageId = imageId, error = null) }
                runCatching { productsRepository.deleteProductImage(productId, imageId) }
                    .onSuccess { updated ->
                        applyImages(updated)
                        _uiState.update { it.copy(deletingImageId = null) }
                    }
                    .onFailure { error ->
                        Timber.w(error, "Failed to delete image $imageId for product $productId")
                        _uiState.update { state -> state.copy(deletingImageId = null, error = networkErrorMapper.map(error)) }
                    }
            }
        }

        fun clearError() {
            _uiState.update { it.copy(error = null) }
        }

        fun consumeSaveSuccess() {
            _uiState.update { it.copy(saveSuccess = false) }
        }

        /** Prix de référence pour le champ HT : `priceTaxExcl` si le connecteur l'expose, sinon [Product.price] (TTC) en approximation. */
        private fun referencePrice(product: Product): Double = product.priceTaxExcl ?: product.price
    }

data class ProductEditUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val productNotFound: Boolean = false,
    val name: String = "",
    val description: String = "",
    val descriptionShort: String = "",
    val reference: String = "",
    val priceText: String = "",
    val active: Boolean = true,
    val isDirty: Boolean = false,
    val images: List<ProductImage> = emptyList(),
    val isUploadingImage: Boolean = false,
    val deletingImageId: Long? = null,
    val pendingDeleteImageId: Long? = null,
    val error: UiText? = null,
    val saveSuccess: Boolean = false,
) {
    val priceValue: Double?
        get() = priceText.trim().replace(',', '.').toDoubleOrNull()

    val nameError: Boolean get() = name.isBlank()
    val priceError: Boolean get() = priceValue == null || (priceValue ?: -1.0) < 0.0
    val referenceError: Boolean get() = reference.length > MAX_REFERENCE_LENGTH

    val isValid: Boolean get() = !nameError && !priceError && !referenceError
    val canSave: Boolean get() = isDirty && isValid && !isSaving && !isLoading
}
