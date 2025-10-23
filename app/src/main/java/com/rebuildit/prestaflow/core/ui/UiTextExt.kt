package com.rebuildit.prestaflow.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalContext

@Composable
@ReadOnlyComposable
fun UiText.asString(): String {
    val context = LocalContext.current
    return when (this) {
        is UiText.Dynamic -> value
        is UiText.FromResources -> context.getString(resId, *args.toTypedArray())
    }
}
