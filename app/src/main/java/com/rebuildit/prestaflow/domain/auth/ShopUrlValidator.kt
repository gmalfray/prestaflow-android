package com.rebuildit.prestaflow.domain.auth

import android.net.Uri

class ShopUrlValidator {

    fun validate(input: String): Result {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return Result.Invalid.Empty

        val uri = runCatching { Uri.parse(trimmed) }.getOrNull() ?: return Result.Invalid.Malformed

        val scheme = uri.scheme?.lowercase()
        if (scheme != "https") return Result.Invalid.NonHttps

        if (uri.host.isNullOrBlank()) return Result.Invalid.Malformed

        return Result.Valid(trimmed.removeSuffix("/"))
    }

    sealed class Result {
        data class Valid(val normalizedUrl: String) : Result()

        sealed class Invalid : Result() {
            data object Empty : Invalid()
            data object Malformed : Invalid()
            data object NonHttps : Invalid()
        }
    }
}
