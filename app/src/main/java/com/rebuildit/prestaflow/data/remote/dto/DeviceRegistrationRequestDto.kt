package com.rebuildit.prestaflow.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class DeviceRegistrationRequestDto(
    val token: String,
    val platform: String = "android",
    val deviceId: String
)
