package com.rebuildit.prestaflow.domain.auth.model

data class AuthToken(
    val value: String,
    val expiresAtEpochMillis: Long?,
    val scopes: List<String> = emptyList()
) {
    val isExpired: Boolean
        get() = expiresAtEpochMillis?.let { System.currentTimeMillis() >= it } ?: false
}
