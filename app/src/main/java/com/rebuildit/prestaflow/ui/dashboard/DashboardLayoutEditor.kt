package com.rebuildit.prestaflow.ui.dashboard

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.outlined.ViewColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rebuildit.prestaflow.R
import com.rebuildit.prestaflow.domain.dashboard.model.DashboardLayout
import com.rebuildit.prestaflow.domain.dashboard.model.DashboardLayoutItem
import com.rebuildit.prestaflow.domain.dashboard.model.DashboardWidget
import com.rebuildit.prestaflow.domain.dashboard.model.DashboardWidgetSpan
import com.rebuildit.prestaflow.ui.theme.Dimensions
import com.rebuildit.prestaflow.ui.theme.PrestaFlowTheme
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * Éditeur de disposition : liste réordonnable (poignée de glissement) avec un interrupteur de
 * visibilité par widget. Travaille sur une copie locale ; [onSave] n'est appelé qu'à
 * l'enregistrement, l'annulation ou la fermeture ne persistent rien.
 */
@Composable
internal fun DashboardLayoutEditorOverlay(
    layout: DashboardLayout,
    onSave: (DashboardLayout) -> Unit,
    onDismiss: () -> Unit,
) {
    // Overlay plein écran (pas un Dialog) : posé dans un Box borné par l'écran, la hauteur est
    // bornée, donc le weight(1f) de la liste laisse toujours la place au pied d'actions. Un Dialog
    // gardait une fenêtre en wrap-content qui débordait et rognait le pied sous l'écran.
    BackHandler(onBack = onDismiss)
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        DashboardLayoutEditorContent(
            layout = layout,
            onSave = onSave,
            onDismiss = onDismiss,
            modifier = Modifier.systemBarsPadding(),
        )
    }
}

/**
 * Contenu plein écran : en-tête fixe, liste scrollable ([weight] 1) et pied d'actions fixe, toujours
 * visible quelle que soit la longueur de la liste ou la taille de police.
 */
@Composable
internal fun DashboardLayoutEditorContent(
    layout: DashboardLayout,
    onSave: (DashboardLayout) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var items by remember { mutableStateOf(layout.items) }
    val lazyListState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current
    val reorderableState =
        rememberReorderableLazyListState(lazyListState) { from, to ->
            items = items.toMutableList().apply { add(to.index, removeAt(from.index)) }
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    Column(modifier = modifier.fillMaxSize().padding(top = Dimensions.spacingM)) {
        EditorHeader()
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            state = lazyListState,
            contentPadding = PaddingValues(horizontal = Dimensions.screenEdgeMargin, vertical = Dimensions.spacingM),
            verticalArrangement = Arrangement.spacedBy(Dimensions.spacingS),
        ) {
            items(items, key = { it.widget.id }) { item ->
                // ReorderableItem anime déjà le déplacement des voisins (animateItem).
                ReorderableItem(reorderableState, key = item.widget.id) { isDragging ->
                    WidgetEditorRow(
                        item = item,
                        isDragging = isDragging,
                        dragHandle =
                            Modifier.draggableHandle(
                                onDragStarted = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                                onDragStopped = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
                            ),
                        onVisibleChange = { visible ->
                            items = items.map { if (it.widget == item.widget) it.copy(visible = visible) else it }
                        },
                    )
                }
            }
        }
        EditorActionBar(onDismiss = onDismiss, onSave = { onSave(DashboardLayout(items)) })
    }
}

@Composable
private fun EditorHeader() {
    Column(
        modifier = Modifier.padding(horizontal = Dimensions.screenEdgeMargin),
        verticalArrangement = Arrangement.spacedBy(Dimensions.spacingXs),
    ) {
        Text(
            text = stringResource(id = R.string.dashboard_edit_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(id = R.string.dashboard_edit_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EditorActionBar(
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 2.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Dimensions.screenEdgeMargin, vertical = Dimensions.spacingS),
            horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingS, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onDismiss) { Text(text = stringResource(id = R.string.action_cancel)) }
            Button(onClick = onSave) { Text(text = stringResource(id = R.string.dashboard_edit_save)) }
        }
    }
}

private const val DRAG_SCALE = 1.03f
private const val HIDDEN_ALPHA = 0.55f
private val DragElevation = 12.dp
private val RestElevation = 1.dp

/**
 * Ligne d'un widget dans l'éditeur. Soulevée (`isDragging`), elle grossit légèrement, gagne de
 * l'ombre et se teinte : le retour se voit sous le pouce. Un widget masqué est atténué, mais
 * son état reste lisible par l'interrupteur et par le libellé annoncé.
 */
@Composable
internal fun WidgetEditorRow(
    item: DashboardLayoutItem,
    isDragging: Boolean,
    dragHandle: Modifier,
    onVisibleChange: (Boolean) -> Unit,
) {
    val title = stringResource(id = item.widget.titleRes())
    val moveDescription = stringResource(id = R.string.dashboard_edit_drag_handle, title)
    val showDescription = stringResource(id = R.string.dashboard_edit_show_widget, title)
    val elevation by animateDpAsState(if (isDragging) DragElevation else RestElevation, label = "editorElevation")
    val scale by animateFloatAsState(if (isDragging) DRAG_SCALE else 1f, label = "editorScale")
    val container by animateColorAsState(
        if (isDragging) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLowest,
        label = "editorContainer",
    )
    Card(
        modifier =
            Modifier.fillMaxWidth().graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = RoundedCornerShape(Dimensions.cardCornerRadius),
        colors = CardDefaults.cardColors(containerColor = container),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = Dimensions.spacingXs, end = Dimensions.spacingM),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingS),
        ) {
            IconButton(onClick = {}, modifier = dragHandle.semantics { contentDescription = moveDescription }) {
                Icon(
                    imageVector = Icons.Outlined.DragHandle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                modifier = Modifier.weight(1f).alpha(if (item.visible) 1f else HIDDEN_ALPHA),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingS),
            ) {
                WidgetIconTile(widget = item.widget)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    SpanBadge(span = item.widget.span)
                }
            }
            Switch(
                checked = item.visible,
                onCheckedChange = onVisibleChange,
                modifier = Modifier.semantics { contentDescription = showDescription },
            )
        }
    }
}

@Composable
private fun WidgetIconTile(widget: DashboardWidget) {
    Box(
        modifier =
            Modifier
                .size(Dimensions.iconContainerSize)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = widget.icon(),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(Dimensions.iconSizeMedium),
        )
    }
}

/** Indique la largeur du widget par une icône ET un texte (pas la forme ou la couleur seules). */
@Composable
private fun SpanBadge(span: DashboardWidgetSpan) {
    val half = span == DashboardWidgetSpan.HALF
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingXs)) {
        Icon(
            imageVector = if (half) Icons.Outlined.ViewColumn else Icons.Outlined.ViewAgenda,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = stringResource(id = if (half) R.string.dashboard_edit_span_half else R.string.dashboard_edit_span_full),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun EditorPreviewColumn() {
    PrestaFlowTheme {
        Column(
            modifier = Modifier.padding(Dimensions.spacingM),
            verticalArrangement = Arrangement.spacedBy(Dimensions.spacingS),
        ) {
            WidgetEditorRow(
                DashboardLayoutItem(DashboardWidget.TURNOVER, true),
                isDragging = false,
                dragHandle = Modifier,
                onVisibleChange = {},
            )
            WidgetEditorRow(
                DashboardLayoutItem(DashboardWidget.CHART, true),
                isDragging = true,
                dragHandle = Modifier,
                onVisibleChange = {},
            )
            WidgetEditorRow(
                DashboardLayoutItem(DashboardWidget.PENDING_ORDERS, false),
                isDragging = false,
                dragHandle = Modifier,
                onVisibleChange = {},
            )
            WidgetEditorRow(
                DashboardLayoutItem(DashboardWidget.FREE_CONVERSION, true),
                isDragging = false,
                dragHandle = Modifier,
                onVisibleChange = {},
            )
        }
    }
}

@Preview(name = "Editeur 1.0", showBackground = true)
@Preview(name = "Editeur 1.5", showBackground = true, fontScale = 1.5f, heightDp = 700)
@Preview(name = "Editeur 2.0", showBackground = true, fontScale = 2f, heightDp = 900)
@Composable
private fun PreviewLayoutEditorRows() = EditorPreviewColumn()

@Preview(name = "Editeur complet 1.0", showBackground = true, widthDp = 411, heightDp = 800)
@Preview(name = "Editeur complet 1.5", showBackground = true, widthDp = 411, heightDp = 800, fontScale = 1.5f)
@Preview(name = "Editeur complet 2.0", showBackground = true, widthDp = 411, heightDp = 800, fontScale = 2f)
@Composable
private fun PreviewLayoutEditorFull() {
    PrestaFlowTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            DashboardLayoutEditorContent(layout = DashboardLayout.DEFAULT, onSave = {}, onDismiss = {})
        }
    }
}
