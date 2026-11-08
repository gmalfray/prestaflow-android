package com.rebuildit.prestaflow.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DragHandle
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.rebuildit.prestaflow.R
import com.rebuildit.prestaflow.domain.dashboard.model.DashboardLayout
import com.rebuildit.prestaflow.domain.dashboard.model.DashboardLayoutItem
import com.rebuildit.prestaflow.ui.theme.Dimensions
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * Éditeur de disposition : liste réordonnable (poignée de glissement) avec un interrupteur de
 * visibilité par widget. Travaille sur une copie locale ; [onSave] n'est appelé qu'à
 * l'enregistrement, l'annulation ou la fermeture ne persistent rien.
 */
@Composable
internal fun DashboardLayoutEditorDialog(
    layout: DashboardLayout,
    onSave: (DashboardLayout) -> Unit,
    onDismiss: () -> Unit,
) {
    var items by remember { mutableStateOf(layout.items) }
    val lazyListState = rememberLazyListState()
    val reorderableState =
        rememberReorderableLazyListState(lazyListState) { from, to ->
            items = items.toMutableList().apply { add(to.index, removeAt(from.index)) }
        }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(Dimensions.spacingM),
                verticalArrangement = Arrangement.spacedBy(Dimensions.spacingM),
            ) {
                Text(
                    text = stringResource(id = R.string.dashboard_edit_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    state = lazyListState,
                    verticalArrangement = Arrangement.spacedBy(Dimensions.spacingS),
                ) {
                    items(items, key = { it.widget.id }) { item ->
                        ReorderableItem(reorderableState, key = item.widget.id) {
                            WidgetEditorRow(
                                item = item,
                                dragHandle = Modifier.draggableHandle(),
                                onVisibleChange = { visible ->
                                    items = items.map { if (it.widget == item.widget) it.copy(visible = visible) else it }
                                },
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingS, Alignment.End),
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(text = stringResource(id = R.string.action_cancel))
                    }
                    Button(onClick = { onSave(DashboardLayout(items)) }) {
                        Text(text = stringResource(id = R.string.dashboard_edit_save))
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetEditorRow(
    item: DashboardLayoutItem,
    dragHandle: Modifier,
    onVisibleChange: (Boolean) -> Unit,
) {
    val title = stringResource(id = item.widget.titleRes())
    val moveDescription = stringResource(id = R.string.dashboard_edit_drag_handle, title)
    val showDescription = stringResource(id = R.string.dashboard_edit_show_widget, title)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimensions.cardCornerRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Dimensions.spacingS),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingS),
        ) {
            IconButton(onClick = {}, modifier = dragHandle.semantics { contentDescription = moveDescription }) {
                Icon(imageVector = Icons.Outlined.DragHandle, contentDescription = null)
            }
            Icon(
                imageVector = item.widget.icon(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Switch(
                checked = item.visible,
                onCheckedChange = onVisibleChange,
                modifier = Modifier.semantics { contentDescription = showDescription },
            )
        }
    }
}
