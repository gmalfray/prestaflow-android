package com.rebuildit.prestaflow.domain.dashboard.model

/**
 * Conversion « offert → payant » : parmi les clients dont la 1re commande était offerte (0 €),
 * ceux qui ont ensuite passé une commande payante.
 */
data class FreeConversion(
    val freeFirstCustomers: Int,
    val paidFirstCustomers: Int,
    val converted: Int,
    /** Pourcentage (0 à 100), 1 décimale côté serveur. */
    val rate: Double,
)
