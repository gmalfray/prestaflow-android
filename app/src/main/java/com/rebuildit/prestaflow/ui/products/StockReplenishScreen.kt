package com.rebuildit.prestaflow.ui.products

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.ToneGenerator
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import com.rebuildit.prestaflow.R
import com.rebuildit.prestaflow.core.ui.asString
import com.rebuildit.prestaflow.domain.products.model.Combination
import com.rebuildit.prestaflow.domain.products.model.DEFAULT_QUICK_ADD_AMOUNTS
import com.rebuildit.prestaflow.domain.products.model.Product
import com.rebuildit.prestaflow.domain.products.model.ProductStock
import com.rebuildit.prestaflow.ui.theme.Dimensions
import com.rebuildit.prestaflow.ui.theme.PrestaFlowTheme
import kotlinx.coroutines.delay

/** Formats reconnus par le scanner permanent — identiques à ceux du scan ponctuel historique. */
private val REPLENISH_BARCODE_FORMATS =
    listOf(BarcodeFormat.EAN_13, BarcodeFormat.EAN_8, BarcodeFormat.CODE_128)

/**
 * Écran « Ajout / réappro stock » (Lot 1) : scanner permanent en haut, infos du dernier produit
 * scanné au milieu, delta accumulé (boutons rapides + saisie libre), validation différée avec
 * fenêtre d'annulation.
 *
 * Point d'intégration (décision prise, cf. résumé de livraison) : remplace le FAB "Scanner un
 * produit" de [ProductsRoute] pour l'ajustement d'un produit CONNU. Le flux d'association d'un EAN
 * INCONNU reste porté tel quel par [ProductScanViewModel]/[ProductScanSheet] : cet écran délègue le
 * sous-cas [StockReplenishUiState.notFound] à un second ViewModel Hilt dédié à ce seul sous-flux,
 * et récupère la main dès que l'association aboutit.
 *
 * Lot 3 : consomme [StockReplenishViewModel.scanFeedbackEvents] pour déclencher le retour
 * haptique ([LocalHapticFeedback], toujours actif) et le bip sonore ([rememberScanConfirmationTone],
 * seulement si [StockReplenishViewModel.soundOnScan] est activé).
 *
 * Secours OCR (v0.38.0, cf. KDoc [StockReplenishViewModel]) : [PermanentBarcodeScanner] transmet à
 * chaque décodage un `() -> Bitmap?` paresseux ([BarcodeResult.getBitmap]) plutôt qu'un bitmap déjà
 * calculé — la conversion coûteuse n'est déclenchée que si le code s'avère introuvable. Si le
 * secours trouve des suggestions ([StockReplenishUiState.labelSuggestions]), elles sont transmises
 * à [ProductScanViewModel.onKnownNotFoundWithSuggestions] (au lieu de [onKnownNotFound]) pour
 * pré-remplir l'écran d'association existant plutôt qu'une recherche manuelle vide.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongMethod") // Orchestration scan + sous-flux d'association (2 ViewModels observés) + feedback Lot 3
@Composable
fun StockReplenishRoute(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StockReplenishViewModel = hiltViewModel(),
    associationViewModel: ProductScanViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val quickAddAmounts by viewModel.quickAddAmounts.collectAsStateWithLifecycle()
    val soundOnScan by viewModel.soundOnScan.collectAsStateWithLifecycle()
    val associationState by associationViewModel.uiState.collectAsStateWithLifecycle()

    // Code introuvable → délègue au flux d'association existant, sur le MÊME code. `onKnownNotFound`
    // (pas `onBarcodeScanned`) : le code vient déjà d'être cherché ci-dessus par
    // [StockReplenishViewModel] et déclaré introuvable — relancer `onBarcodeScanned` referait un 2ᵉ
    // `GET /products?barcode=` du même code déjà su vide, retardant inutilement l'ouverture du sheet
    // d'association. Si le secours OCR (cf. KDoc classe) a trouvé des suggestions entre-temps,
    // `onKnownNotFoundWithSuggestions` ouvre directement la recherche pré-remplie ; sinon,
    // comportement historique inchangé (recherche manuelle vide).
    LaunchedEffect(state.notFound, state.scannedCode, state.labelSuggestions) {
        val code = state.scannedCode
        if (state.notFound && code != null) {
            if (state.labelSuggestions.isNotEmpty()) {
                associationViewModel.onKnownNotFoundWithSuggestions(code, state.labelSuggestions)
            } else {
                associationViewModel.onKnownNotFound(code)
            }
        }
    }
    // Association terminée (succès → produit résolu) ou abandonnée (sheet refermée sans résultat) :
    // dans les deux cas on reprend la main côté écran de réappro et on réarme le scanner permanent.
    LaunchedEffect(associationState.selectedProduct, associationState.isSheetVisible) {
        val linked = associationState.selectedProduct
        if (linked != null) {
            viewModel.onProductResolvedExternally(linked)
            associationViewModel.onDismiss()
        } else if (!associationState.isSheetVisible && state.notFound) {
            viewModel.onSkip()
        }
    }

    // Lot 3 — retour haptique + bip sonore sur scan résolu (jamais sur échec/doublon, filtré en
    // amont côté ViewModel). Le haptique respecte déjà le réglage système ; le son suit [soundOnScan].
    val hapticFeedback = LocalHapticFeedback.current
    val scanTone = rememberScanConfirmationTone()
    LaunchedEffect(viewModel, scanTone) {
        viewModel.scanFeedbackEvents.collect {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            if (soundOnScan) scanTone.play()
        }
    }

    StockReplenishScreen(
        modifier = modifier,
        state = state,
        quickAddAmounts = quickAddAmounts,
        onBackClick = onBackClick,
        onBarcodeScanned = viewModel::onBarcodeScanned,
        onSelectFromMultipleResults = viewModel::onSelectFromMultipleResults,
        onSelectCombination = viewModel::onSelectCombination,
        onQuantityInputChange = viewModel::onQuantityInputChange,
        onAddTypedQuantity = viewModel::onAddTypedQuantity,
        onQuickAdd = viewModel::onQuickAdd,
        onResetDelta = viewModel::onResetDelta,
        onSkip = viewModel::onSkip,
        onValidate = viewModel::onValidate,
        onCancelPendingWrite = viewModel::onCancelPendingWrite,
        onClearError = viewModel::clearError,
        onConsumeWriteError = viewModel::consumeWriteError,
    )

    // Sous-flux d'association — affiché seulement tant qu'aucun produit n'est résolu (au-delà, le
    // LaunchedEffect ci-dessus a déjà rendu la main à l'écran de réappro).
    if (associationState.isSheetVisible && associationState.selectedProduct == null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ProductScanSheet(
            state = associationState,
            sheetState = sheetState,
            onDismiss = associationViewModel::onDismiss,
            onScanAgain = associationViewModel::onDismiss,
            onSelectProduct = associationViewModel::onSelectProduct,
            onBackToResults = associationViewModel::onBackToResults,
            onSelectCombination = associationViewModel::onSelectCombination,
            onQuantityChange = associationViewModel::onQuantityChange,
            onIncrement = associationViewModel::onIncrement,
            onDecrement = associationViewModel::onDecrement,
            onConfirm = associationViewModel::onConfirmAdjustment,
            onStartAssociation = associationViewModel::onStartAssociation,
            onCancelAssociation = associationViewModel::onCancelAssociation,
            onAssociationQueryChange = associationViewModel::onAssociationQueryChange,
            onSelectAssociationProduct = associationViewModel::onAssociationProductSelected,
            onSelectAssociationCombination = associationViewModel::onSelectAssociationCombination,
            onCancelAssociationCombinationChoice = associationViewModel::onCancelAssociationCombinationChoice,
        )
    }
}

/**
 * Lot 3 : bandeau récap de session ([SessionRecapBanner]) visible dès qu'un article a été validé,
 * confirmation visuelle discrète à chaque ajout à la file ([queueAddedTick]), et récap de sortie
 * ([showExitRecap]) intercepté sur le retour (bouton ET geste système via [BackHandler]) tant que la
 * session contient au moins un article validé — cf. KDoc [StockReplenishViewModel].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongParameterList", "LongMethod") // Écran orchestrant scan + accumulation + validation : callbacks distincts
@Composable
fun StockReplenishScreen(
    state: StockReplenishUiState,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Montants des boutons rapides — configurables en préférences (Lot 2), défaut +5/+10/+20. */
    quickAddAmounts: List<Int> = DEFAULT_QUICK_ADD_AMOUNTS,
    onBarcodeScanned: (String, () -> Bitmap?) -> Unit = { _, _ -> },
    onSelectFromMultipleResults: (Product) -> Unit = {},
    onSelectCombination: (Combination) -> Unit = {},
    onQuantityInputChange: (String) -> Unit = {},
    onAddTypedQuantity: () -> Unit = {},
    onQuickAdd: (Int) -> Unit = {},
    onResetDelta: () -> Unit = {},
    onSkip: () -> Unit = {},
    onValidate: () -> Unit = {},
    onCancelPendingWrite: (String) -> Unit = {},
    onClearError: () -> Unit = {},
    onConsumeWriteError: () -> Unit = {},
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val errorMessage = state.error?.asString()
    val backDesc = stringResource(R.string.content_description_back)
    val context = LocalContext.current
    val reduceMotion = remember { isReduceMotionEnabled(context) }

    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            snackbarHostState.showSnackbar(errorMessage)
            onClearError()
        }
    }
    LaunchedEffect(state.writeErrorMessage) {
        state.writeErrorMessage?.let {
            snackbarHostState.showSnackbar(it)
            onConsumeWriteError()
        }
    }

    // Confirmation visuelle discrète (Lot 3) : brève apparition à chaque écriture ajoutée à la
    // file — un compteur (plutôt qu'un simple Boolean) car deux validations rapprochées doivent
    // chacune redéclencher l'effet, même si le booléen resterait "true" entre les deux.
    var showAddedFlash by remember { mutableStateOf(false) }
    LaunchedEffect(state.queueAddedTick) {
        if (state.queueAddedTick > 0) {
            showAddedFlash = true
            delay(QUEUE_ADDED_FLASH_DURATION_MS)
            showAddedFlash = false
        }
    }

    // Récap de sortie (Lot 3) : intercepte le retour (bouton + geste système) tant qu'au moins un
    // article a été réellement validé dans la session, pour l'afficher avant de quitter l'écran.
    var showExitRecap by remember { mutableStateOf(false) }
    val hasSessionRecap = state.sessionRecap.articleCount > 0
    val attemptExit = {
        if (hasSessionRecap) showExitRecap = true else onBackClick()
    }
    BackHandler(enabled = hasSessionRecap, onBack = attemptExit)

    if (showExitRecap) {
        SessionRecapExitDialog(
            recap = state.sessionRecap,
            onDismiss = {
                showExitRecap = false
                onBackClick()
            },
        )
    }

    // Destination plein écran (cf. MainActivity.isFullScreenRoute) : ce header EST l'unique barre du
    // haut affichée — aucun chrome parent (TopAppBar "Produits", bottom nav/rail) ne se superpose.
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.stock_replenish_title)) },
                navigationIcon = {
                    IconButton(onClick = attemptExit) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = backDesc)
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        // Mise en page compacte à 3 zones, dans l'ordre :
        //  1. Bandeau récap de session (fixe, tout en haut — jamais scrollé hors champ).
        //  2. Zone scrollable (caméra compacte + fiche produit) : seule partie qui rétrécit/scrolle
        //     sur petit écran, jamais la zone d'action. `weight(1f, fill = false)` (au lieu de
        //     `weight(1f)`) : la zone ne réclame QUE l'espace dont son contenu a besoin, plafonné à
        //     l'espace restant — sur un écran de hauteur normale, le contenu (caméra + fiche) est
        //     plus petit que l'espace dispo, donc plus de grand vide poussant la zone d'action tout
        //     en bas ; sur petit écran, le plafond force le scroll interne (cf.
        //     [ReplenishScrollableSection]) et la zone d'action reste entièrement visible en dessous.
        //  3. Zone d'action épinglée (écritures en attente + boutons rapides/quantité/Valider) :
        //     toujours visible en bas, jamais coupée par la hauteur d'écran.
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            SessionRecapBanner(recap = state.sessionRecap)

            ReplenishScrollableSection(
                modifier = Modifier.weight(1f, fill = false).fillMaxWidth(),
                state = state,
                showAddedFlash = showAddedFlash,
                reduceMotion = reduceMotion,
                onBarcodeScanned = onBarcodeScanned,
                onSelectFromMultipleResults = onSelectFromMultipleResults,
                onSelectCombination = onSelectCombination,
                onResetDelta = onResetDelta,
            )

            if (state.pendingWrites.isNotEmpty()) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Dimensions.screenEdgeMargin, vertical = Dimensions.spacingXs),
                    verticalArrangement = Arrangement.spacedBy(Dimensions.spacingXs),
                ) {
                    state.pendingWrites.forEach { pending ->
                        PendingWriteBar(
                            pending = pending,
                            onCancel = { onCancelPendingWrite(pending.id) },
                        )
                    }
                }
            }

            if (state.product != null) {
                ReplenishActionBar(
                    state = state,
                    quickAddAmounts = quickAddAmounts,
                    onQuantityInputChange = onQuantityInputChange,
                    onAddTypedQuantity = onAddTypedQuantity,
                    onQuickAdd = onQuickAdd,
                    onSkip = onSkip,
                    onValidate = onValidate,
                )
            }
        }
    }
}

/**
 * Zone scrollable : caméra compacte + corps de l'écran ([ReplenishBody]), avec la confirmation
 * visuelle discrète ([QueueAddedFlash]) superposée en haut.
 *
 * Isolée dans sa propre fonction (plutôt qu'inlinée dans le `Column` appelant) : un `AnimatedVisibility`
 * scopé `BoxScope` (avec `Modifier.align`) imbriqué directement dans un `Column { Box { … } }` crée une
 * ambiguïté de résolution entre les overloads `ColumnScope.AnimatedVisibility` et `BoxScope`/générique
 * (récepteurs implicites empilés) → échec de compilation. Une fonction dédiée démarre une pile de
 * récepteurs implicites propre : seul `BoxScope` est en jeu ici.
 *
 * Ni la [Box] racine ni le [Column] interne (caméra + fiche) ne portent `fillMaxSize`/`weight` : leur
 * hauteur est bornée par le `weight(1f, fill = false)` posé par l'appelant ([StockReplenishScreen])
 * mais pas forcée à l'atteindre — le contenu ne prend que la place dont il a besoin (plus de grand
 * vide sur écran normal), et déborde vers le scroll interne de [ReplenishBody] si la hauteur d'écran
 * est trop petite pour tout afficher (la caméra, elle, garde toujours sa hauteur fixe).
 */
@Suppress("LongParameterList")
@Composable
private fun ReplenishScrollableSection(
    state: StockReplenishUiState,
    showAddedFlash: Boolean,
    reduceMotion: Boolean,
    onBarcodeScanned: (String, () -> Bitmap?) -> Unit,
    onSelectFromMultipleResults: (Product) -> Unit,
    onSelectCombination: (Combination) -> Unit,
    onResetDelta: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth()) {
            PermanentBarcodeScanner(
                isActive = state.isScannerActive,
                onBarcodeScanned = onBarcodeScanned,
                modifier = Modifier.fillMaxWidth().height(replenishCameraHeight()),
            )
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(Dimensions.screenEdgeMargin),
                verticalArrangement = Arrangement.spacedBy(Dimensions.spacingM),
            ) {
                ReplenishBody(
                    state = state,
                    onSelectFromMultipleResults = onSelectFromMultipleResults,
                    onSelectCombination = onSelectCombination,
                    onResetDelta = onResetDelta,
                )
            }
        }

        AnimatedVisibility(
            visible = showAddedFlash,
            enter = if (reduceMotion) EnterTransition.None else fadeIn() + scaleIn(initialScale = 0.85f),
            exit = if (reduceMotion) ExitTransition.None else fadeOut(),
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = Dimensions.spacingM),
        ) {
            QueueAddedFlash()
        }
    }
}

/** Hauteur de l'aperçu caméra sur écran de hauteur normale — 140dp (trop étriqué pour viser) → 188dp. */
private val REPLENISH_CAMERA_HEIGHT = 188.dp

/**
 * Hauteur repliée de l'aperçu caméra sur très petit écran (cf. [PreviewStockReplenishScreenSmallHeight],
 * 480dp) : sous [REPLENISH_COMPACT_SCREEN_HEIGHT_THRESHOLD_DP], la fiche produit scrollable a trop peu
 * de place restante pour respirer si la caméra garde sa pleine hauteur — reprend la taille précédente.
 */
private val REPLENISH_CAMERA_HEIGHT_COMPACT = 140.dp

/** Seuil de hauteur d'écran (dp) sous lequel [REPLENISH_CAMERA_HEIGHT_COMPACT] remplace [REPLENISH_CAMERA_HEIGHT]. */
private const val REPLENISH_COMPACT_SCREEN_HEIGHT_THRESHOLD_DP = 600

/** Hauteur de l'aperçu caméra, adaptée à la hauteur d'écran courante (cf. constantes ci-dessus). */
@Composable
private fun replenishCameraHeight(): Dp {
    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    return if (screenHeightDp < REPLENISH_COMPACT_SCREEN_HEIGHT_THRESHOLD_DP) {
        REPLENISH_CAMERA_HEIGHT_COMPACT
    } else {
        REPLENISH_CAMERA_HEIGHT
    }
}

/** Durée d'affichage de [QueueAddedFlash] après chaque validation. */
private const val QUEUE_ADDED_FLASH_DURATION_MS = 1_200L

/** Vrai si l'utilisateur a désactivé les animations système ("Échelle de durée des animations" = 0). */
private fun isReduceMotionEnabled(context: Context): Boolean =
    runCatching {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }.getOrDefault(false)

// ─── Récap de session (Lot 3) ──────────────────────────────────────────────────

/** Bandeau persistant « N articles · +Q en stock » — masqué tant qu'aucun article n'a été validé. */
@Composable
private fun SessionRecapBanner(recap: ReplenishSessionRecap) {
    if (recap.articleCount <= 0) return
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimensions.screenEdgeMargin, vertical = Dimensions.spacingXs),
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.stock_replenish_session_recap, recap.articleCount, recap.unitsCount),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

/** Coche brièvement affichée quand un ajustement est ajouté à la file (confirmation visuelle discrète). */
@Composable
private fun QueueAddedFlash() {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        tonalElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Dimensions.spacingM, vertical = Dimensions.spacingXs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingXs),
        ) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                modifier = Modifier.size(Dimensions.iconSizeSmall),
            )
            Text(
                text = stringResource(R.string.stock_replenish_queue_added_content_description),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

/** Dialogue de récap affiché à la sortie de l'écran si au moins un article a été validé. */
@Composable
private fun SessionRecapExitDialog(
    recap: ReplenishSessionRecap,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(Dimensions.cardCornerRadius),
        title = { Text(stringResource(R.string.stock_replenish_session_recap_exit_title)) },
        text = {
            Text(
                stringResource(
                    R.string.stock_replenish_session_recap_exit_message,
                    recap.articleCount,
                    recap.unitsCount,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.stock_replenish_session_recap_exit_action))
            }
        },
    )
}

// ─── Bip de confirmation au scan (Lot 3) ────────────────────────────────────────

/**
 * Bip court de confirmation au scan, joué via [ToneGenerator] (pas d'asset embarqué). Respecte le
 * mode silencieux système ([AudioManager.RINGER_MODE_SILENT]) — l'activation/désactivation
 * explicite reste pilotée par la préférence [StockReplenishViewModel.soundOnScan] (cf. appelant).
 */
@Composable
private fun rememberScanConfirmationTone(): ScanConfirmationTone {
    val context = LocalContext.current
    val tone = remember { ScanConfirmationTone(context) }
    DisposableEffect(tone) {
        onDispose { tone.release() }
    }
    return tone
}

private class ScanConfirmationTone(private val context: Context) {
    private val toneGenerator: ToneGenerator? =
        runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, TONE_VOLUME_PERCENT) }.getOrNull()

    fun play() {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (audioManager?.ringerMode == AudioManager.RINGER_MODE_SILENT) return
        runCatching { toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, TONE_DURATION_MS) }
    }

    fun release() {
        runCatching { toneGenerator?.release() }
    }

    companion object {
        /** Volume modéré (0-100 % du flux notification) — repère discret, pas une alarme. */
        private const val TONE_VOLUME_PERCENT = 60
        private const val TONE_DURATION_MS = 90
    }
}

// ─── Scanner permanent (zxing-android-embedded, décodage continu) ──────────────

/**
 * Aperçu caméra permanent (ligne de scan incluse via [DecoratedBarcodeView]) — même librairie que
 * l'ancien flux ponctuel ([ProductsRoute]), mais EMBARQUÉE dans l'écran (décodage continu) au lieu
 * d'ouvrir une Activity plein écran séparée par scan.
 *
 * [isActive] pilote la pause/reprise de la caméra : le flux se met en pause pendant l'affichage
 * d'un produit scanné (évite les re-scans en boucle, cf. KDoc [StockReplenishViewModel]) et reprend
 * une fois l'écran revenu à l'état "prêt à scanner".
 *
 * [onBarcodeScanned] reçoit un second paramètre `() -> Bitmap?` (secours OCR, cf. KDoc
 * [StockReplenishRoute]) : capture PARESSEUSEMENT `result.getBitmap()` (zxing la documente comme
 * potentiellement coûteuse — conversion YUV→Bitmap de la frame source) — n'est invoquée que si
 * [StockReplenishViewModel] en a réellement besoin (code introuvable), jamais sur le chemin chaud
 * d'un scan qui matche du premier coup.
 */
@Composable
private fun PermanentBarcodeScanner(
    isActive: Boolean,
    onBarcodeScanned: (String, () -> Bitmap?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasCameraPermission by
        remember {
            mutableStateOf(
                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED,
            )
        }
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            hasCameraPermission = granted
        }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    if (!hasCameraPermission) {
        CameraPermissionPlaceholder(
            modifier = modifier,
            onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
        )
        return
    }

    var decoratedView by remember { mutableStateOf<DecoratedBarcodeView?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            DecoratedBarcodeView(ctx).apply {
                this.barcodeView.decoderFactory = DefaultDecoderFactory(REPLENISH_BARCODE_FORMATS)
                decodeContinuous(
                    object : BarcodeCallback {
                        override fun barcodeResult(result: BarcodeResult) {
                            result.text?.let { text -> onBarcodeScanned(text) { result.bitmap } }
                        }
                    },
                )
                decoratedView = this
            }
        },
        update = { view ->
            if (isActive) view.resume() else view.pause()
        },
    )

    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> if (isActive) decoratedView?.resume()
                    Lifecycle.Event.ON_PAUSE -> decoratedView?.pause()
                    else -> Unit
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            decoratedView?.pause()
        }
    }
}

@Composable
private fun CameraPermissionPlaceholder(
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(Dimensions.spacingM),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.stock_replenish_camera_permission_rationale),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(Dimensions.spacingS))
            Button(onClick = onRequestPermission) {
                Text(stringResource(R.string.stock_replenish_camera_permission_action))
            }
        }
    }
}

// ─── Corps de l'écran (zone scrollable) : dispatch selon l'état ────────────────

/**
 * Contenu de la zone scrollable de l'écran (sous la caméra) : uniquement les infos (chargement,
 * introuvable, choix multiples, fiche produit + delta) — les actions (boutons rapides, saisie,
 * Ignorer/Valider) sont épinglées séparément dans [ReplenishActionBar], toujours visibles.
 */
@Composable
private fun ReplenishBody(
    state: StockReplenishUiState,
    onSelectFromMultipleResults: (Product) -> Unit,
    onSelectCombination: (Combination) -> Unit,
    onResetDelta: () -> Unit,
) {
    when {
        state.isLookupLoading ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = Dimensions.spacingL),
                horizontalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
            }
        // Secours OCR (cf. KDoc StockReplenishViewModel) : tentative brève avant `notFound` — les
        // deux ne sont jamais vrais en même temps (cf. invariant du ViewModel).
        state.isLabelSearchLoading ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = Dimensions.spacingL),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(Dimensions.iconSizeMedium))
                Spacer(modifier = Modifier.size(Dimensions.spacingS))
                Text(
                    text = stringResource(R.string.stock_replenish_label_search_loading),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        state.notFound ->
            Text(
                text = stringResource(R.string.stock_replenish_not_found),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        state.multipleResults.size > 1 -> MultipleResultsList(state.multipleResults, onSelectFromMultipleResults)
        state.combinationChoices.isNotEmpty() ->
            CombinationChoiceList(state.combinationChoices, onSelectCombination)
        state.product != null ->
            ScannedProductInfo(
                product = state.product,
                delta = state.delta,
                newQuantity = state.newQuantity,
                onResetDelta = onResetDelta,
            )
        else ->
            Text(
                text = stringResource(R.string.stock_replenish_idle_hint),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
    }
}

@Composable
private fun MultipleResultsList(
    results: List<Product>,
    onSelect: (Product) -> Unit,
) {
    Column {
        Text(
            text = stringResource(R.string.products_scan_multiple_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = Dimensions.spacingS),
        )
        results.forEachIndexed { index, product ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(product) }
                        .padding(vertical = Dimensions.spacingS),
            ) {
                Column {
                    Text(text = product.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = product.reference,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (index < results.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)
        }
    }
}

@Composable
private fun CombinationChoiceList(
    choices: List<Combination>,
    onSelect: (Combination) -> Unit,
) {
    Column {
        Text(
            text = stringResource(R.string.products_scan_combination_choice_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = Dimensions.spacingS),
        )
        choices.forEachIndexed { index, combination ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(combination) }
                        .padding(vertical = Dimensions.spacingS),
            ) {
                Column {
                    Text(text = combination.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = stringResource(R.string.products_scan_combination_stock_row, combination.quantity),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (index < choices.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)
        }
    }
}

// ─── Produit scanné : fiche + delta (zone scrollable) ──────────────────────────

/** Fiche produit + delta accumulé — partie scrollable de l'écran (cf. [ReplenishBody]). */
@Composable
private fun ScannedProductInfo(
    product: Product,
    delta: Int,
    newQuantity: Int,
    onResetDelta: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimensions.spacingM)) {
        ScannedProductHeader(product = product)
        DeltaSummary(delta = delta, newQuantity = newQuantity, onResetDelta = onResetDelta)
    }
}

// ─── Zone d'action épinglée : boutons rapides + saisie + validation ───────────

/**
 * Zone d'action épinglée en bas de l'écran (boutons rapides, saisie libre, Ignorer/Valider) —
 * toujours visible quelle que soit la hauteur d'écran, jamais poussée sous l'écran par la fiche
 * produit scrollable au-dessus (cf. mise en page 3 zones de [StockReplenishScreen]). Portée par une
 * [Surface] avec élévation tonale pour se détacher visuellement du contenu défilant.
 */
@Suppress("LongParameterList")
@Composable
private fun ReplenishActionBar(
    state: StockReplenishUiState,
    quickAddAmounts: List<Int>,
    onQuantityInputChange: (String) -> Unit,
    onAddTypedQuantity: () -> Unit,
    onQuickAdd: (Int) -> Unit,
    onSkip: () -> Unit,
    onValidate: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 3.dp,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimensions.screenEdgeMargin, vertical = Dimensions.spacingS),
            verticalArrangement = Arrangement.spacedBy(Dimensions.spacingS),
        ) {
            QuickAddRow(amounts = quickAddAmounts, onQuickAdd = onQuickAdd)

            TypedQuantityRow(
                quantityInput = state.quantityInput,
                onQuantityInputChange = onQuantityInputChange,
                onAdd = onAddTypedQuantity,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = onSkip) {
                    Text(stringResource(R.string.stock_replenish_skip_action))
                }
                Button(onClick = onValidate, enabled = state.canValidate) {
                    Text(stringResource(R.string.stock_replenish_validate_action))
                }
            }
        }
    }
}

@Composable
private fun ScannedProductHeader(product: Product) {
    val matchedCombination = product.matchedCombination
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimensions.cardCornerRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier.padding(Dimensions.cardPadding),
            horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingM),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val imageUrl = product.images.firstOrNull()?.url
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = product.name,
                    modifier =
                        Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(Dimensions.chipCornerRadius))
                            .background(MaterialTheme.colorScheme.surfaceContainer),
                )
            } else {
                Surface(
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(Dimensions.chipCornerRadius)),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) { Text("?", style = MaterialTheme.typography.labelLarge) }
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = product.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (product.reference.isNotBlank()) {
                    Text(
                        text = product.reference,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (matchedCombination != null) {
                    Text(
                        text = stringResource(R.string.products_scan_combination_label, matchedCombination.name),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    text = stringResource(R.string.products_scan_current_stock, product.scannedQuantity),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DeltaSummary(
    delta: Int,
    newQuantity: Int,
    onResetDelta: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = stringResource(R.string.stock_replenish_delta_label, formatSignedDelta(delta)),
                style = MaterialTheme.typography.headlineSmall,
                color = if (delta != 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.stock_replenish_new_quantity, newQuantity),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (delta != 0) {
            TextButton(onClick = onResetDelta) {
                Text(stringResource(R.string.stock_replenish_reset_action))
            }
        }
    }
}

private fun formatSignedDelta(delta: Int): String = if (delta > 0) "+$delta" else delta.toString()

/**
 * Rangée de boutons rapides — montants configurables en préférences (Lot 2, Réglages ›
 * « Réappro / boutons rapides »), défaut +5/+10/+20 (Lot 1) tant que rien n'est configuré.
 */
@Composable
private fun QuickAddRow(
    amounts: List<Int>,
    onQuickAdd: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingS),
    ) {
        amounts.forEach { amount ->
            OutlinedButton(
                onClick = { onQuickAdd(amount) },
                modifier = Modifier.weight(1f),
            ) {
                Text("+$amount")
            }
        }
    }
}

@Composable
private fun TypedQuantityRow(
    quantityInput: String,
    onQuantityInputChange: (String) -> Unit,
    onAdd: () -> Unit,
) {
    val addDesc = stringResource(R.string.stock_replenish_add_content_description)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingS),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = quantityInput,
            onValueChange = onQuantityInputChange,
            label = { Text(stringResource(R.string.stock_replenish_quantity_placeholder)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onAdd() }),
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onAdd, enabled = quantityInput.toIntOrNull()?.let { it > 0 } == true) {
            Icon(imageVector = Icons.Filled.Add, contentDescription = addDesc)
        }
    }
}

// ─── Barre d'annulation d'une écriture en attente (pattern swipe commandes) ────

/**
 * Barre d'annulation d'une écriture de stock en attente, avec décompte vivant des secondes
 * restantes avant l'envoi effectif — même pattern visuel que `SwipeUndoBar` (commandes), mais
 * plusieurs peuvent coexister ici (réappro en série : valider réarme le scanner immédiatement, cf.
 * KDoc [StockReplenishViewModel.onValidate]).
 */
@Composable
private fun PendingWriteBar(
    pending: PendingStockWrite,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val totalSeconds = (REPLENISH_UNDO_DELAY_MS / 1_000L).toInt()
    var remainingSeconds by remember(pending.id) { mutableIntStateOf(totalSeconds) }

    LaunchedEffect(pending.id) {
        for (secondsLeft in totalSeconds - 1 downTo 0) {
            delay(1_000L)
            remainingSeconds = secondsLeft
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimensions.cardCornerRadius),
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        tonalElevation = 6.dp,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimensions.spacingM, vertical = Dimensions.spacingS),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text =
                    stringResource(
                        R.string.stock_replenish_pending_label,
                        pending.productName,
                        formatSignedDelta(pending.delta),
                    ),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onCancel) {
                Text(
                    text = stringResource(R.string.orders_swipe_undo_countdown, remainingSeconds),
                    color = MaterialTheme.colorScheme.inversePrimary,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

// ─── Previews ─────────────────────────────────────────────────────────────────

private fun previewReplenishProduct() =
    Product(
        id = 1L,
        name = "Pelote de laine — Coloris Bleu",
        reference = "LAINE-BLU",
        price = 6.90,
        active = true,
        stock = ProductStock(quantity = 12),
        images = emptyList(),
        updatedAt = "2026-07-01T00:00:00Z",
        ean13 = "3401234567890",
    )

@Preview(showBackground = true, name = "Réappro — fiche produit (zone scrollable)")
@Composable
private fun PreviewScannedProductInfo() {
    PrestaFlowTheme {
        Surface {
            Column(modifier = Modifier.padding(Dimensions.screenEdgeMargin)) {
                ScannedProductInfo(
                    product = previewReplenishProduct(),
                    delta = 15,
                    newQuantity = 27,
                    onResetDelta = {},
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "Réappro — zone d'action épinglée")
@Composable
private fun PreviewReplenishActionBar() {
    PrestaFlowTheme {
        ReplenishActionBar(
            state = StockReplenishUiState(product = previewReplenishProduct(), delta = 15),
            quickAddAmounts = DEFAULT_QUICK_ADD_AMOUNTS,
            onQuantityInputChange = {},
            onAddTypedQuantity = {},
            onQuickAdd = {},
            onSkip = {},
            onValidate = {},
        )
    }
}

/**
 * Vérification anti-régression « petit écran » : écran complet à hauteur réduite (480dp, plus bas
 * qu'un Pixel compact) avec récap de session + une écriture en attente + fiche produit — la zone
 * d'action (dont le bouton Valider) doit rester entièrement visible, la fiche produit peut scroller.
 */
@Preview(showBackground = true, heightDp = 480, name = "Réappro — écran complet, petite hauteur")
@Composable
private fun PreviewStockReplenishScreenSmallHeight() {
    PrestaFlowTheme {
        StockReplenishScreen(
            state =
                StockReplenishUiState(
                    product = previewReplenishProduct(),
                    delta = 15,
                    sessionRecap = ReplenishSessionRecap(articleCount = 3, unitsCount = 42),
                    pendingWrites =
                        listOf(
                            PendingStockWrite(
                                id = "1",
                                productId = 1L,
                                combinationId = null,
                                warehouseId = null,
                                productName = "Pelote de laine — Coloris Bleu",
                                delta = 15,
                                newQuantity = 27,
                            ),
                        ),
                ),
            onBackClick = {},
        )
    }
}

@Preview(showBackground = true, name = "Réappro — écriture en attente")
@Composable
private fun PreviewPendingWriteBar() {
    PrestaFlowTheme {
        Surface {
            PendingWriteBar(
                pending =
                    PendingStockWrite(
                        id = "1",
                        productId = 1L,
                        combinationId = null,
                        warehouseId = null,
                        productName = "Pelote de laine — Coloris Bleu",
                        delta = 15,
                        newQuantity = 27,
                    ),
                onCancel = {},
                modifier = Modifier.padding(Dimensions.screenEdgeMargin),
            )
        }
    }
}
