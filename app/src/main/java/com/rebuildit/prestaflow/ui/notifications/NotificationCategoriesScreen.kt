package com.rebuildit.prestaflow.ui.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rebuildit.prestaflow.R
import com.rebuildit.prestaflow.domain.notifications.NotificationCategory
import com.rebuildit.prestaflow.ui.theme.Dimensions
import com.rebuildit.prestaflow.ui.theme.PrestaFlowTheme

@Composable
fun NotificationCategoriesRoute(
    onBackClick: () -> Unit,
    viewModel: NotificationCategoriesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    NotificationCategoriesScreen(
        uiState = uiState,
        onBackClick = onBackClick,
        onCategoryToggle = viewModel::setCategory,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationCategoriesScreen(
    uiState: NotificationCategoriesUiState,
    onBackClick: () -> Unit,
    onCategoryToggle: (NotificationCategory, Boolean) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.notif_categories_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.content_description_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .verticalScroll(rememberScrollState())
                    .padding(innerPadding)
                    .padding(horizontal = Dimensions.screenEdgeMargin, vertical = Dimensions.spacingL),
            verticalArrangement = Arrangement.spacedBy(Dimensions.spacingM),
        ) {
            // Section interrupteurs catégories
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Dimensions.cardCornerRadius),
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Column(
                    modifier = Modifier.padding(Dimensions.cardPadding),
                    verticalArrangement = Arrangement.spacedBy(Dimensions.spacingS),
                ) {
                    Text(
                        text = stringResource(R.string.notif_categories_section_label).uppercase(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    NotificationCategory.entries.forEach { category ->
                        CategoryRow(
                            category = category,
                            enabled = uiState.categories[category] != false,
                            onToggle = { checked -> onCategoryToggle(category, checked) },
                        )
                    }
                }
            }

            // Avertissement toutes catégories désactivées
            if (uiState.allDisabled) {
                AllDisabledWarning()
            }
        }
    }
}

@Composable
private fun CategoryRow(
    category: NotificationCategory,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = Dimensions.spacingXs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(category.labelRes()),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = enabled,
            onCheckedChange = onToggle,
        )
    }
}

@Composable
private fun AllDisabledWarning() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimensions.cardCornerRadius),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
            ),
    ) {
        Row(
            modifier = Modifier.padding(Dimensions.cardPadding),
            horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingS),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                text = stringResource(R.string.notif_categories_all_disabled_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

/** Renvoie la ressource string du libellé de la catégorie. */
private fun NotificationCategory.labelRes(): Int =
    when (this) {
        NotificationCategory.ORDER_CREATED -> R.string.notif_category_order_created
        NotificationCategory.ORDER_STATUS_CHANGED -> R.string.notif_category_order_status_changed
        NotificationCategory.ORDER_SHIPPING_UPDATED -> R.string.notif_category_order_shipping_updated
    }

// ─── Previews ────────────────────────────────────────────────────────────────

@Preview(showBackground = true, name = "Toutes activées")
@Composable
private fun PreviewAllEnabled() {
    PrestaFlowTheme {
        NotificationCategoriesScreen(
            uiState = NotificationCategoriesUiState(),
            onBackClick = {},
            onCategoryToggle = { _, _ -> },
        )
    }
}

@Preview(showBackground = true, name = "Toutes désactivées")
@Composable
private fun PreviewAllDisabled() {
    PrestaFlowTheme {
        val allDisabledState =
            NotificationCategoriesUiState(
                categories = NotificationCategory.entries.associateWith { false },
            )
        NotificationCategoriesScreen(
            uiState = allDisabledState,
            onBackClick = {},
            onCategoryToggle = { _, _ -> },
        )
    }
}
