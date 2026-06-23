package com.rebuildit.prestaflow.ui.components

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rebuildit.prestaflow.R
import com.rebuildit.prestaflow.domain.auth.model.ShopConnection
import com.rebuildit.prestaflow.ui.theme.Dimensions
import kotlinx.coroutines.launch

/**
 * Barre d'erreur en haut d'une liste, avec message et bouton retry.
 */
@Composable
fun ErrorRow(
    message: String,
    onRefresh: () -> Unit,
) {
    val retryDesc = stringResource(R.string.content_description_retry)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = onRefresh,
            modifier = Modifier.semantics { contentDescription = retryDesc },
        ) {
            Icon(imageVector = Icons.Outlined.Refresh, contentDescription = null)
        }
    }
}

/**
 * Indicateur de chargement centré, occupe tout l'espace disponible.
 */
@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}

/**
 * État vide centré, avec message optionnel d'erreur et bouton retry.
 */
@Composable
fun EmptyState(
    message: String,
    modifier: Modifier = Modifier,
    errorMessage: String? = null,
    onRefresh: (() -> Unit)? = null,
) {
    val retryDesc = stringResource(R.string.content_description_retry)
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                if (onRefresh != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier.semantics { contentDescription = retryDesc },
                    ) {
                        Icon(imageVector = Icons.Outlined.Refresh, contentDescription = null)
                    }
                }
            }
        }
    }
}

/**
 * État "introuvable" affiché quand un item de détail est absent.
 */
@Composable
fun NotFoundState(
    message: String,
    modifier: Modifier = Modifier,
    // Conservé pour compatibilité future de l'API
    @Suppress("UnusedParameter") onBackClick: () -> Unit,
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ─── Composants Terracotta partagés ──────────────────────────────────────────

/**
 * Champ de recherche réutilisable (filtre local des listes) — design Terracotta.
 * Affiche une icône loupe, un placeholder, et une croix pour effacer quand non vide.
 *
 * @param query Texte de recherche courant.
 * @param onQueryChange Callback à chaque frappe (et pour effacer via la croix).
 * @param placeholder Texte d'invite (ex. « Rechercher une commande »).
 */
@Composable
fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val clearDesc = stringResource(R.string.content_description_clear_search)
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = {
            Text(text = placeholder, style = MaterialTheme.typography.bodyMedium)
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.semantics { contentDescription = clearDesc },
                ) {
                    Icon(imageVector = Icons.Outlined.Close, contentDescription = null)
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        colors =
            OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
            ),
    )
}

/**
 * Avatar circulaire avec initiales du nom — design Terracotta.
 * Génère automatiquement 1 ou 2 initiales depuis [name].
 * La couleur de fond est dérivée de l'index numérique du premier caractère
 * pour assurer la cohérence entre les sessions.
 *
 * @param name Nom complet ou prénom nom du client/commande.
 * @param size Diamètre de l'avatar (défaut : 40dp).
 */
@Composable
fun AvatarInitials(
    name: String,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = Dimensions.avatarSize,
) {
    val initials =
        remember(name) {
            val parts = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            when {
                parts.size >= 2 ->
                    "${parts.first().first().uppercaseChar()}${parts.last().first().uppercaseChar()}"
                parts.size == 1 ->
                    parts.first().take(2).uppercase()
                else -> "?"
            }
        }

    // Palette de fond tournante basée sur la valeur ASCII du premier char.
    // Tokens Stitch : secondary-container, primary-container léger, surface-container.
    val bgColors =
        listOf(
            Color(0xFFE9DED4),
            Color(0xFFC99587).copy(alpha = 0.22f),
            Color(0xFFF0EDED),
        )
    // Tokens Stitch : secondary, primary, on-surface-variant.
    val textColors =
        listOf(
            Color(0xFF655D56),
            Color(0xFF7F5448),
            Color(0xFF514440),
        )
    val colorIndex = (name.firstOrNull()?.code ?: 0) % bgColors.size

    Box(
        modifier =
            modifier
                .size(size)
                .clip(CircleShape)
                .background(bgColors[colorIndex]),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            color = textColors[colorIndex],
            fontSize = (size.value * 0.36f).sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Badge de statut coloré (chip pill) — adapté au contexte commande.
 * Sélectionne automatiquement la couleur selon le statut normalisé.
 *
 * Catégories reconnues (insensible à la casse, sous-chaîne) :
 *  - "payé" / "paid"          → vert succès
 *  - "expédi" / "shipped" / "livr" → primary terracotta
 *  - "attente" / "pending"    → ambre/warning
 *  - "annul" / "cancel"       → erreur rouge
 *  - Tout autre statut        → neutral (surface-container)
 */
@Composable
fun OrderStatusBadge(
    status: String,
    modifier: Modifier = Modifier,
) {
    val normalized = status.lowercase()
    val (bgColor, textColor) =
        when {
            normalized.contains("pay") || normalized.contains("paid") ->
                Color(0xFFDCFCE7) to Color(0xFF166534)
            normalized.contains("expédi") || normalized.contains("shipped") ||
                normalized.contains("livr") || normalized.contains("deliver") ->
                MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
            normalized.contains("attente") || normalized.contains("pending") ||
                normalized.contains("en cours") ->
                Color(0xFFFEF9C3) to Color(0xFF854D0E)
            normalized.contains("annul") || normalized.contains("cancel") ||
                normalized.contains("refus") ->
                MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
            else ->
                MaterialTheme.colorScheme.surfaceContainer to MaterialTheme.colorScheme.onSurfaceVariant
        }

    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(Dimensions.chipCornerRadius))
                .background(bgColor)
                .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = status.uppercase(),
            color = textColor,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
        )
    }
}

/**
 * En-tête de section « Titre / Voir tout » réutilisable.
 * @param title Titre de la section.
 * @param onSeeAll Callback du lien « Voir tout ». Si null, le lien est masqué.
 * @param seeAllLabel Texte du lien (par défaut : utiliser [R.string] en appelant)
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    seeAllLabel: String? = null,
    onSeeAll: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (onSeeAll != null && seeAllLabel != null) {
            TextButton(onClick = onSeeAll) {
                Text(
                    text = seeAllLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

// ─── Sélecteur de boutique active ────────────────────────────────────────────

/**
 * Chip cliquable affichant la boutique active. Au clic, ouvre un bottom-sheet
 * listant toutes les boutiques connectées (avec coche sur l'active) et une entrée
 * « + Ajouter une boutique ».
 *
 * @param connections Liste complète des boutiques (celle avec [ShopConnection.isActive] est active).
 * @param onSwitch Callback appelé avec l'id de la boutique choisie.
 * @param onAddShop Callback pour naviguer vers l'ajout d'une boutique.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopSwitcherChip(
    connections: List<ShopConnection>,
    onSwitch: (String) -> Unit,
    onAddShop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeShop = connections.firstOrNull { it.isActive }
    val chipLabel = activeShop?.label ?: activeShop?.shopUrl?.substringAfter("://")?.trimEnd('/') ?: ""

    var showSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    val chipDesc = stringResource(R.string.shop_switcher_content_description)

    // Chip pill — affiché seulement si au moins une boutique est connectée
    if (chipLabel.isBlank()) return

    Surface(
        modifier =
            modifier
                .clip(CircleShape)
                .clickable(
                    onClickLabel = chipDesc,
                    role = Role.Button,
                    onClick = { showSheet = true },
                )
                .semantics { role = Role.Button },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Dimensions.spacingM, vertical = Dimensions.spacingXs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingXs),
        ) {
            Text(
                text = chipLabel,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (connections.size > 1) {
                Icon(
                    imageVector = Icons.Outlined.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(Dimensions.iconSizeSmall),
                )
            }
        }
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState,
        ) {
            ShopSwitcherSheetContent(
                connections = connections,
                onSwitch = { id ->
                    scope.launch {
                        sheetState.hide()
                        showSheet = false
                        onSwitch(id)
                    }
                },
                onAddShop = {
                    scope.launch {
                        sheetState.hide()
                        showSheet = false
                        onAddShop()
                    }
                },
            )
        }
    }
}

@Composable
private fun ShopSwitcherSheetContent(
    connections: List<ShopConnection>,
    onSwitch: (String) -> Unit,
    onAddShop: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(bottom = Dimensions.spacingXl),
    ) {
        Text(
            text = stringResource(R.string.shop_switcher_sheet_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimensions.screenEdgeMargin, vertical = Dimensions.spacingM),
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        connections.forEach { connection ->
            ShopSheetRow(
                connection = connection,
                onSwitch = { onSwitch(connection.id) },
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onAddShop)
                    .padding(horizontal = Dimensions.screenEdgeMargin, vertical = Dimensions.spacingM),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingM),
        ) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(Dimensions.iconSizeMedium),
            )
            Text(
                text = stringResource(R.string.shop_switcher_add),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun ShopSheetRow(
    connection: ShopConnection,
    onSwitch: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onSwitch, enabled = !connection.isActive)
                .padding(horizontal = Dimensions.screenEdgeMargin, vertical = Dimensions.spacingM),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingM),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = connection.label,
                style = MaterialTheme.typography.bodyLarge,
                color =
                    if (connection.isActive) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                fontWeight = if (connection.isActive) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = connection.shopUrl.substringAfter("://").trimEnd('/'),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (connection.isActive) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(Dimensions.iconSizeMedium),
            )
        }
    }
}
