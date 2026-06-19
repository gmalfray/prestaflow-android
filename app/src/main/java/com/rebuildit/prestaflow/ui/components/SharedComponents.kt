package com.rebuildit.prestaflow.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.rebuildit.prestaflow.R

/**
 * Barre d'erreur en haut d'une liste, avec message et bouton retry.
 */
@Composable
fun ErrorRow(message: String, onRefresh: () -> Unit) {
    val retryDesc = stringResource(R.string.content_description_retry)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f)
        )
        IconButton(
            onClick = onRefresh,
            modifier = Modifier.semantics { contentDescription = retryDesc }
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
    onRefresh: (() -> Unit)? = null
) {
    val retryDesc = stringResource(R.string.content_description_retry)
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                if (onRefresh != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier.semantics { contentDescription = retryDesc }
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
    onBackClick: () -> Unit
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
