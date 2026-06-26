package com.rebuildit.prestaflow.ui.orders.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rebuildit.prestaflow.R
import com.rebuildit.prestaflow.domain.orders.model.OrderStatusFilter
import com.rebuildit.prestaflow.ui.theme.Dimensions

/**
 * Dialogue de sélection de statut unique.
 *
 * Affiche la liste des statuts disponibles avec leur pastille de couleur.
 * Pré-sélectionne le statut courant si son id est fourni via [currentStatusId].
 * Appelle [onConfirm] avec l'id (en string) du statut choisi.
 * Si [statuses] est vide, affiche un message de repli sans planter.
 */
@Composable
fun StatusPickerDialog(
    statuses: List<OrderStatusFilter>,
    currentStatusId: Int?,
    onConfirm: (statusId: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedId by remember(statuses, currentStatusId) {
        mutableStateOf(currentStatusId)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.status_picker_title)) },
        text = {
            if (statuses.isEmpty()) {
                Text(
                    text = stringResource(R.string.status_picker_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(Dimensions.spacingXs),
                ) {
                    items(statuses, key = { it.id }) { status ->
                        StatusPickerRow(
                            status = status,
                            isSelected = status.id == selectedId,
                            onClick = { selectedId = status.id },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val id = selectedId
                    if (id != null) onConfirm(id.toString())
                },
                enabled = selectedId != null,
            ) {
                Text(stringResource(R.string.status_picker_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.status_picker_cancel))
            }
        },
    )
}

@Composable
private fun StatusPickerRow(
    status: OrderStatusFilter,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Dimensions.chipCornerRadius))
                .clickable(onClick = onClick)
                .padding(horizontal = Dimensions.spacingS, vertical = Dimensions.spacingS),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingM),
    ) {
        // Pastille de couleur du statut
        val dotColor = parseHexColor(status.color)
        Box(
            modifier =
                Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(dotColor),
        )
        Text(
            text = status.name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.size(Dimensions.spacingXs))
        Icon(
            imageVector =
                if (isSelected) {
                    Icons.Filled.RadioButtonChecked
                } else {
                    Icons.Outlined.RadioButtonUnchecked
                },
            contentDescription = null,
            tint =
                if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            modifier = Modifier.size(Dimensions.iconSizeMedium),
        )
    }
}

/**
 * Tente de parser une couleur hexadécimale du connecteur (#RRGGBB ou #RGB).
 * Retourne une couleur neutre en cas d'échec de parsing.
 */
private fun parseHexColor(hex: String): Color =
    runCatching {
        val stripped = hex.trimStart('#')
        val normalized =
            when (stripped.length) {
                3 -> stripped.map { "$it$it" }.joinToString("")
                6 -> stripped
                else -> null
            }
        if (normalized != null) Color(android.graphics.Color.parseColor("#$normalized")) else null
    }.getOrNull() ?: Color(0xFFBDBDBD)
