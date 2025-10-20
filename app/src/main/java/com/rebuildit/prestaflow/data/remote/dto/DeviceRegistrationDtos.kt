package com.rebuildit.prestaflow.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DeviceRegistrationRequestDto(
    @SerialName("token") val token: String,
    @SerialName("device_id") val deviceId: String? = null,
    @SerialName("platform") val platform: String,
    @SerialName("topics") val topics: List<String> = emptyList(),
    @SerialName("do_not_disturb") val doNotDisturb: DoNotDisturbDto? = null
)

@Serializable
data class DoNotDisturbDto(
    @SerialName("start") val start: String,
    @SerialName("end") val end: String
)
