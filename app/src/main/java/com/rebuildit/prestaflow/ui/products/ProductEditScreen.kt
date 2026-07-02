package com.rebuildit.prestaflow.ui.products

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.rebuildit.prestaflow.R
import com.rebuildit.prestaflow.core.media.ProductImagePreparer
import com.rebuildit.prestaflow.core.ui.asString
import com.rebuildit.prestaflow.domain.products.model.ProductImage
import com.rebuildit.prestaflow.ui.components.LoadingState
import com.rebuildit.prestaflow.ui.components.NotFoundState
import com.rebuildit.prestaflow.ui.theme.Dimensions
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun ProductEditRoute(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProductEditViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.saveSuccess) {
        if (state.saveSuccess) {
            viewModel.consumeSaveSuccess()
            onBackClick()
        }
    }

    ProductEditScreen(
        modifier = modifier,
        state = state,
        onBackClick = onBackClick,
        onNameChange = viewModel::onNameChange,
        onDescriptionChange = viewModel::onDescriptionChange,
        onDescriptionShortChange = viewModel::onDescriptionShortChange,
        onReferenceChange = viewModel::onReferenceChange,
        onPriceChange = viewModel::onPriceChange,
        onActiveChange = viewModel::onActiveChange,
        onSaveClick = viewModel::onSave,
        onClearError = viewModel::clearError,
        onImageSelected = viewModel::onImageSelected,
        onDeleteImageRequested = viewModel::onDeleteImageRequested,
        onDeleteImageConfirmed = viewModel::onDeleteImageConfirmed,
        onDeleteImageCancelled = viewModel::onDeleteImageCancelled,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongParameterList") // Composable d'écran : chaque paramètre est un callback de champ d'édition distinct
@Composable
fun ProductEditScreen(
    state: ProductEditUiState,
    onBackClick: () -> Unit,
    onNameChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onDescriptionShortChange: (String) -> Unit,
    onReferenceChange: (String) -> Unit,
    onPriceChange: (String) -> Unit,
    onActiveChange: (Boolean) -> Unit,
    onSaveClick: () -> Unit,
    onClearError: () -> Unit,
    onImageSelected: (File) -> Unit,
    onDeleteImageRequested: (Long) -> Unit,
    onDeleteImageConfirmed: () -> Unit,
    onDeleteImageCancelled: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val errorMessage = state.error?.asString()
    val backDesc = stringResource(R.string.content_description_back)

    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            snackbarHostState.showSnackbar(errorMessage)
            onClearError()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.product_edit_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = backDesc,
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            state.isLoading -> LoadingState(Modifier.padding(padding))
            state.productNotFound ->
                NotFoundState(
                    message = stringResource(R.string.product_not_found),
                    modifier = Modifier.padding(padding),
                    onBackClick = onBackClick,
                )
            else ->
                ProductEditForm(
                    modifier = Modifier.padding(padding),
                    state = state,
                    snackbarHostState = snackbarHostState,
                    onNameChange = onNameChange,
                    onDescriptionChange = onDescriptionChange,
                    onDescriptionShortChange = onDescriptionShortChange,
                    onReferenceChange = onReferenceChange,
                    onPriceChange = onPriceChange,
                    onActiveChange = onActiveChange,
                    onSaveClick = onSaveClick,
                    onImageSelected = onImageSelected,
                    onDeleteImageRequested = onDeleteImageRequested,
                    onDeleteImageConfirmed = onDeleteImageConfirmed,
                    onDeleteImageCancelled = onDeleteImageCancelled,
                )
        }
    }
}

@Suppress("LongParameterList", "LongMethod") // Formulaire d'édition : un callback par champ + états dérivés
@Composable
private fun ProductEditForm(
    state: ProductEditUiState,
    snackbarHostState: SnackbarHostState,
    onNameChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onDescriptionShortChange: (String) -> Unit,
    onReferenceChange: (String) -> Unit,
    onPriceChange: (String) -> Unit,
    onActiveChange: (Boolean) -> Unit,
    onSaveClick: () -> Unit,
    onImageSelected: (File) -> Unit,
    onDeleteImageRequested: (Long) -> Unit,
    onDeleteImageConfirmed: () -> Unit,
    onDeleteImageCancelled: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimensions.screenEdgeMargin, vertical = Dimensions.spacingM),
        verticalArrangement = Arrangement.spacedBy(Dimensions.spacingM),
    ) {
        ProductImagesSection(
            images = state.images,
            isUploading = state.isUploadingImage,
            deletingImageId = state.deletingImageId,
            enabled = !state.isSaving,
            snackbarHostState = snackbarHostState,
            onImageSelected = onImageSelected,
            onDeleteRequested = onDeleteImageRequested,
        )

        if (state.pendingDeleteImageId != null) {
            AlertDialog(
                onDismissRequest = onDeleteImageCancelled,
                title = { Text(stringResource(R.string.product_edit_images_delete_dialog_title)) },
                text = { Text(stringResource(R.string.product_edit_images_delete_dialog_message)) },
                confirmButton = {
                    TextButton(onClick = onDeleteImageConfirmed) {
                        Text(stringResource(R.string.product_edit_images_delete_dialog_confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDeleteImageCancelled) {
                        Text(stringResource(R.string.product_edit_images_delete_dialog_cancel))
                    }
                },
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Dimensions.cardCornerRadius),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Column(
                modifier = Modifier.padding(Dimensions.cardPadding),
                verticalArrangement = Arrangement.spacedBy(Dimensions.spacingM),
            ) {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = onNameChange,
                    label = { Text(stringResource(R.string.product_edit_name_label)) },
                    isError = state.nameError,
                    supportingText = {
                        if (state.nameError) Text(stringResource(R.string.product_edit_name_error))
                    },
                    singleLine = true,
                    enabled = !state.isSaving,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = state.reference,
                    onValueChange = onReferenceChange,
                    label = { Text(stringResource(R.string.product_edit_reference_label)) },
                    isError = state.referenceError,
                    supportingText = {
                        if (state.referenceError) Text(stringResource(R.string.product_edit_reference_error))
                    },
                    singleLine = true,
                    enabled = !state.isSaving,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = state.priceText,
                    onValueChange = onPriceChange,
                    label = { Text(stringResource(R.string.product_edit_price_label)) },
                    isError = state.priceError,
                    supportingText = {
                        if (state.priceError) Text(stringResource(R.string.product_edit_price_error))
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    enabled = !state.isSaving,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = state.descriptionShort,
                    onValueChange = onDescriptionShortChange,
                    label = { Text(stringResource(R.string.product_edit_description_short_label)) },
                    minLines = 2,
                    maxLines = 4,
                    enabled = !state.isSaving,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = state.description,
                    onValueChange = onDescriptionChange,
                    label = { Text(stringResource(R.string.product_edit_description_label)) },
                    minLines = 5,
                    maxLines = 10,
                    enabled = !state.isSaving,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.product_edit_active_label),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Switch(
                        checked = state.active,
                        onCheckedChange = onActiveChange,
                        enabled = !state.isSaving,
                    )
                }
            }
        }

        Button(
            onClick = onSaveClick,
            enabled = state.canSave,
            modifier = Modifier.align(Alignment.End),
            shape = RoundedCornerShape(50),
        ) {
            Text(stringResource(R.string.product_edit_save_button))
        }

        if (state.isSaving) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Taille des vignettes d'image et de la tuile d'ajout. */
private val imageTileSize = 88.dp

/** Actions du sélecteur d'image de la fiche produit (galerie / appareil photo). */
private class ProductImagePicker(
    val launchGallery: () -> Unit,
    val launchCamera: () -> Unit,
)

/**
 * Prépare les launchers de sélection d'image (galerie via [ActivityResultContracts.PickVisualMedia],
 * appareil photo via [ActivityResultContracts.TakePicture] + [ProductImagePreparer.createCameraCaptureTarget])
 * et la demande de permission caméra runtime. Le fichier préparé (compressé/pivoté) est remonté via
 * [onImageSelected] ; un échec de préparation ou une permission refusée affiche un message dans
 * [snackbarHostState].
 */
@Composable
private fun rememberProductImagePicker(
    onImageSelected: (File) -> Unit,
    snackbarHostState: SnackbarHostState,
): ProductImagePicker {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val imagePreparer = remember(context) { ProductImagePreparer(context) }
    val prepareErrorMessage = stringResource(R.string.product_edit_images_prepare_error)
    val cameraPermissionDeniedMessage = stringResource(R.string.product_edit_images_camera_permission_denied)

    var pendingCameraFile by remember { mutableStateOf<File?>(null) }

    suspend fun handlePrepared(prepared: File?) {
        if (prepared != null) onImageSelected(prepared) else snackbarHostState.showSnackbar(prepareErrorMessage)
    }

    val galleryLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch { handlePrepared(imagePreparer.prepareFromContentUri(uri)) }
        }

    val cameraLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            val capturedFile = pendingCameraFile
            pendingCameraFile = null
            if (!success || capturedFile == null) {
                capturedFile?.let { imagePreparer.cleanup(it) }
                return@rememberLauncherForActivityResult
            }
            scope.launch {
                val prepared = imagePreparer.prepareFromFile(capturedFile)
                imagePreparer.cleanup(capturedFile)
                handlePrepared(prepared)
            }
        }

    fun startCameraCapture() {
        val target = imagePreparer.createCameraCaptureTarget()
        pendingCameraFile = target.file
        cameraLauncher.launch(target.uri)
    }

    val cameraPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startCameraCapture()
            } else {
                scope.launch { snackbarHostState.showSnackbar(cameraPermissionDeniedMessage) }
            }
        }

    return ProductImagePicker(
        launchGallery = {
            galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
        launchCamera = {
            val hasPermission =
                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            if (hasPermission) startCameraCapture() else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        },
    )
}

/**
 * Section "Images" de la fiche produit : liste horizontale des vignettes existantes (avec bouton
 * supprimer) + tuile d'ajout (appareil photo ou galerie).
 */
@Suppress("LongParameterList") // Un callback par action (upload/suppression) + états d'affichage associés
@Composable
private fun ProductImagesSection(
    images: List<ProductImage>,
    isUploading: Boolean,
    deletingImageId: Long?,
    enabled: Boolean,
    snackbarHostState: SnackbarHostState,
    onImageSelected: (File) -> Unit,
    onDeleteRequested: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val picker = rememberProductImagePicker(onImageSelected, snackbarHostState)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Dimensions.spacingS)) {
        Text(
            text = stringResource(R.string.product_edit_images_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingS)) {
            items(images, key = { it.id }) { image ->
                ProductImageThumbnail(
                    image = image,
                    isDeleting = deletingImageId == image.id,
                    enabled = enabled && deletingImageId == null,
                    onDeleteClick = { onDeleteRequested(image.id) },
                )
            }
            item {
                AddImageTile(
                    isUploading = isUploading,
                    enabled = enabled && !isUploading,
                    onPickCamera = picker.launchCamera,
                    onPickGallery = picker.launchGallery,
                )
            }
        }
    }
}

@Composable
private fun ProductImageThumbnail(
    image: ProductImage,
    isDeleting: Boolean,
    enabled: Boolean,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val removeDesc = stringResource(R.string.product_edit_images_remove_content_description)
    val thumbnailDesc = stringResource(R.string.product_edit_images_thumbnail_content_description)

    Box(modifier = modifier.size(imageTileSize)) {
        Card(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(Dimensions.chipCornerRadius),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        ) {
            AsyncImage(
                model = image.url,
                contentDescription = thumbnailDesc,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (isDeleting) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(Dimensions.chipCornerRadius)),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(Dimensions.iconSizeMedium),
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        } else {
            IconButton(
                onClick = onDeleteClick,
                enabled = enabled,
                modifier =
                    Modifier
                        .size(Dimensions.iconSizeMedium)
                        .align(Alignment.TopEnd)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), RoundedCornerShape(50)),
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = removeDesc,
                    modifier = Modifier.size(Dimensions.spacingM),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun AddImageTile(
    isUploading: Boolean,
    enabled: Boolean,
    onPickCamera: () -> Unit,
    onPickGallery: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showMenu by remember { mutableStateOf(false) }
    val addDesc = stringResource(R.string.product_edit_images_add_content_description)

    Box(modifier = modifier.size(imageTileSize)) {
        Card(
            modifier =
                Modifier
                    .fillMaxSize()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(Dimensions.chipCornerRadius)),
            shape = RoundedCornerShape(Dimensions.chipCornerRadius),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            IconButton(
                onClick = { if (enabled) showMenu = true },
                enabled = enabled,
                modifier = Modifier.fillMaxSize(),
            ) {
                if (isUploading) {
                    CircularProgressIndicator(modifier = Modifier.size(Dimensions.iconSizeMedium))
                } else {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = addDesc,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.product_edit_images_source_camera)) },
                leadingIcon = { Icon(Icons.Default.PhotoCamera, contentDescription = null) },
                onClick = {
                    showMenu = false
                    onPickCamera()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.product_edit_images_source_gallery)) },
                leadingIcon = { Icon(Icons.Default.PhotoLibrary, contentDescription = null) },
                onClick = {
                    showMenu = false
                    onPickGallery()
                },
            )
        }
    }
}
