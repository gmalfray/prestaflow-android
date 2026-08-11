package com.rebuildit.prestaflow.ui.sav.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.res.stringResource
import com.rebuildit.prestaflow.R
import com.rebuildit.prestaflow.domain.sav.model.SavThreadStatus
import com.rebuildit.prestaflow.ui.theme.Dimensions

/**
 * Dialogue de changement de statut d'un fil SAV — les 4 valeurs natives sont fixes (pas de
 * chargement réseau, contrairement aux statuts de commande). Aucun e-mail envoyé par ce geste
 * (contrairement à [com.rebuildit.prestaflow.ui.sav.SavThreadDetailScreen]'s reply).
 */
@Composable
fun SavStatusPickerDialog(
    currentStatus: SavThreadStatus,
    onConfirm: (SavThreadStatus) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember(currentStatus) { mutableStateOf(currentStatus) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sav_status_picker_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Dimensions.spacingXs)) {
                SavThreadStatus.entries.forEach { status ->
                    SavStatusPickerRow(
                        status = status,
                        isSelected = status == selected,
                        onClick = { selected = status },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected) }) {
                Text(stringResource(R.string.sav_status_picker_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.sav_status_picker_cancel))
            }
        },
    )
}

@Composable
private fun SavStatusPickerRow(
    status: SavThreadStatus,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val label =
        when (status) {
            SavThreadStatus.OPEN -> stringResource(R.string.sav_status_open)
            SavThreadStatus.AWAITING_CUSTOMER_REPLY -> stringResource(R.string.sav_status_awaiting_customer)
            SavThreadStatus.AWAITING_MERCHANT_REPLY -> stringResource(R.string.sav_status_awaiting_merchant)
            SavThreadStatus.CLOSED -> stringResource(R.string.sav_status_closed)
        }
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
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = if (isSelected) Icons.Filled.RadioButtonChecked else Icons.Outlined.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
