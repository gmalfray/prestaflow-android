package com.rebuildit.prestaflow.domain.auth

import java.net.URI

class ShopUrlValidator {

    fun validate(input: String): Result {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return Result.Invalid.Empty

        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return Result.Invalid.Malformed

        val scheme = uri.scheme?.lowercase() ?: return Result.Invalid.Malformed
        if (scheme != "https") return Result.Invalid.NonHttps

        val host = uri.host?.takeIf { it.isNotBlank() } ?: return Result.Invalid.Malformed

        val port = if (uri.port in -1..-1) "" else ":${uri.port}"
        val path = uri.rawPath
            ?.takeIf { it.isNotBlank() && it != "/" }
            ?.trimEnd('/')
            ?: ""

        val normalized = buildString {
            append("https://")
            append(host)
            append(port)
            append(path)
        }

        return Result.Valid(normalized)
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
