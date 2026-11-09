package com.rebuildit.prestaflow.ui.dashboard

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rebuildit.prestaflow.R
import com.rebuildit.prestaflow.ui.theme.Dimensions
import com.rebuildit.prestaflow.ui.theme.PrestaFlowTheme

private const val SKELETON_PULSE_MS = 900
private const val SKELETON_ALPHA_LOW = 0.35f
private const val SKELETON_ALPHA_HIGH = 0.8f
private val SkeletonShape = RoundedCornerShape(6.dp)

/** État d'une valeur de tuile : chargement, indisponible (erreur réseau) ou valeur prête. */
internal enum class TileStatus { Ready, Loading, Unavailable }

/** Barre de placeholder qui respire doucement : remplace les « … » bruts pendant un chargement. */
@Composable
internal fun SkeletonBar(
    modifier: Modifier = Modifier,
    height: Dp = 14.dp,
) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = SKELETON_ALPHA_LOW,
        targetValue = SKELETON_ALPHA_HIGH,
        animationSpec = infiniteRepeatable(tween(SKELETON_PULSE_MS), RepeatMode.Reverse),
        label = "skeletonAlpha",
    )
    Row(
        modifier =
            modifier
                .height(height)
                .alpha(alpha)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest, SkeletonShape),
    ) {}
}

/** Squelette de carte liste : quelques lignes de largeurs décroissantes, annoncé comme « Chargement ». */
@Composable
internal fun WidgetSkeleton(
    modifier: Modifier = Modifier,
    lines: Int = 3,
) {
    val description = stringResource(id = R.string.dashboard_widget_loading)
    Column(
        modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(Dimensions.spacingS),
    ) {
        repeat(lines) { index ->
            val fraction = if (index == lines - 1) 0.55f else 1f - index * 0.15f
            SkeletonBar(modifier = Modifier.fillMaxWidth(fraction))
        }
    }
}

/** Erreur locale d'une carte : icône + message discret, puis action « Réessayer » alignée à droite. */
@Composable
internal fun WidgetErrorRow(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingS),
        ) {
            Icon(
                imageVector = Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(Dimensions.iconSizeSmall),
            )
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TextButton(onClick = onRetry) { Text(text = stringResource(id = R.string.action_retry)) }
    }
}

@Composable
internal fun WidgetEmptyText(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(id = R.string.dashboard_widget_empty),
        modifier = modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Preview(name = "Etats 1.0", showBackground = true)
@Preview(name = "Etats 1.5", showBackground = true, fontScale = 1.5f)
@Preview(name = "Etats 2.0", showBackground = true, fontScale = 2f)
@Composable
private fun PreviewWidgetStates() {
    PrestaFlowTheme {
        Column(
            modifier = Modifier.padding(Dimensions.spacingM),
            verticalArrangement = Arrangement.spacedBy(Dimensions.spacingM),
        ) {
            WidgetSkeleton()
            WidgetErrorRow(message = stringResource(id = R.string.dashboard_widget_error), onRetry = {})
            WidgetEmptyText()
        }
    }
}
