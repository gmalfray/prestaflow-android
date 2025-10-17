package com.rebuildit.prestaflow.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Common pagination metadata returned by list endpoints.
 */
@Serializable
data class PaginationDto(
    @SerialName("limit") val limit: Int? = null,
    @SerialName("offset") val offset: Int? = null,
    @SerialName("count") val count: Int? = null,
    @SerialName("total") val total: Int? = null,
    @SerialName("has_next") val hasNext: Boolean? = null
)
