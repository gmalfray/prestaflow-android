package com.rebuildit.prestaflow.domain.clients.model

/**
 * Statistiques agrégées des clients, issues de [GET customers/stats].
 *
 * @param total Nombre total de clients de la boutique.
 * @param newThisMonth Nombre de nouveaux clients inscrits ce mois civil.
 */
data class ClientStats(
    val total: Int,
    val newThisMonth: Int,
)
