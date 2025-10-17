package com.rebuildit.prestaflow.domain.auth

import com.rebuildit.prestaflow.core.ui.UiText

sealed class AuthResult {
    data object Success : AuthResult()
    data class Failure(val reason: AuthFailure) : AuthResult()
}

sealed class AuthFailure {
    data class InvalidShopUrl(val reason: ShopUrlValidator.Result.Invalid) : AuthFailure()
    data class Network(val message: UiText) : AuthFailure()
    data class Unknown(val message: UiText) : AuthFailure()
}
