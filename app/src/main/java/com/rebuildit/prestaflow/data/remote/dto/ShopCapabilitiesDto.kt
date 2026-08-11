package com.rebuildit.prestaflow.data.remote.dto

import com.rebuildit.prestaflow.domain.capabilities.model.ShopCapabilities
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `GET connector/capabilities` — cf. étude `rebuild-it/docs/app-avis-sav.md` § « Capacité ≠ droit ».
 *
 * Toutes les propriétés ont une valeur par défaut : le connecteur peut n'exposer que les
 * capacités pertinentes, et une régression du contrat ne doit jamais faire échouer la
 * désérialisation (cf. convention `explicitNulls=false` / champs requis absents du projet).
 */
@Serializable
data class ShopCapabilitiesDto(
    @SerialName("sav") val sav: Boolean = true,
    @SerialName("reviews") val reviews: Boolean = false,
    @SerialName("shipping_labels") val shippingLabels: Boolean = false,
)

fun ShopCapabilitiesDto.toDomain(): ShopCapabilities =
    ShopCapabilities(
        sav = sav,
        reviews = reviews,
        shippingLabels = shippingLabels,
    )
