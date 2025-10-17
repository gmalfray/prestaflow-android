package com.rebuildit.prestaflow.core.ui

import android.content.Context
import androidx.annotation.StringRes

sealed class UiText {
    data class Dynamic(val value: String) : UiText()
    data class FromResources(@StringRes val resId: Int, val args: List<Any> = emptyList()) : UiText()

    fun resolve(context: Context): String = when (this) {
        is Dynamic -> value
        is FromResources -> context.getString(resId, *args.toTypedArray())
    }
}

fun String.asUiText(): UiText = UiText.Dynamic(this)
