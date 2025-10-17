package com.rebuildit.prestaflow.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AuthRequestDto(
    @SerialName("api_key") val apiKey: String,
    @SerialName("shop_url") val shopUrl: String
)

@Serializable
data class AuthResponseDto(
    @SerialName("token") val token: String,
    @SerialName("expires_in") val expiresIn: Long,
    @SerialName("scopes") val scopes: List<String>
)
