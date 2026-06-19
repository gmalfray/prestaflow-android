package com.rebuildit.prestaflow.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rebuildit.prestaflow.R
import com.rebuildit.prestaflow.ui.theme.Dimensions

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

    // Palette de fond tournante basée sur la valeur ASCII du premier char
    val bgColors =
        listOf(
            Color(0xFFE9DED4), // secondary-container Stitch
            Color(0xFFC99587).copy(alpha = 0.22f), // primary-container léger
            Color(0xFFF0EDED), // surface-container Stitch
        )
    val textColors =
        listOf(
            Color(0xFF655D56), // secondary Stitch
            Color(0xFF7F5448), // primary Stitch
            Color(0xFF514440), // on-surface-variant Stitch
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
            androidx.compose.material3.TextButton(onClick = onSeeAll) {
                Text(
                    text = seeAllLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
