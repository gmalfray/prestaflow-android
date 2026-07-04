package com.rebuildit.prestaflow.ui.components

import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale

/**
 * Formate un montant en devise avec le symbole correspondant à [currencyCode].
 * Retourne la valeur brute formatée par défaut si le code devise est invalide.
 */
fun formatCurrency(
    amount: Double,
    currencyCode: String,
): String {
    val formatter = NumberFormat.getCurrencyInstance()
    runCatching { formatter.currency = Currency.getInstance(currencyCode) }
    return formatter.format(amount)
}

/**
 * Formateur de devise pour les montants boutique (toujours en euros — les boutiques gérées
 * sont mono-devise EUR). Utilise la locale de l'appareil pour la position du symbole et les
 * séparateurs (milliers/décimales), mais fige la devise en EUR pour éviter qu'un appareil
 * réglé sur une locale non-euro (ex : anglais US) n'affiche $ au lieu de €.
 */
fun euroCurrencyFormatter(locale: Locale = Locale.getDefault()): NumberFormat =
    NumberFormat.getCurrencyInstance(locale).apply {
        runCatching { currency = Currency.getInstance("EUR") }
    }

/**
 * Tente de parser et de formater un timestamp ISO ou "yyyy-MM-dd HH:mm:ss".
 * Retourne null si [value] est null/vide, ou la valeur brute si le parsing échoue.
 */
@Suppress("ReturnCount") // Tentatives successives de parsing de formats datetime différents
fun formatTimestamp(
    value: String?,
    formatter: DateTimeFormatter,
): String? {
    if (value.isNullOrBlank()) return null
    val zone = ZoneId.systemDefault()

    val fromInstant =
        runCatching { Instant.parse(value) }
            .map { instant -> instant.atZone(zone).format(formatter) }
    if (fromInstant.isSuccess) {
        return fromInstant.getOrThrow()
    }

    val patterns = listOf("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss")
    patterns.forEach { pattern ->
        runCatching {
            LocalDateTime.parse(value, DateTimeFormatter.ofPattern(pattern))
        }.map { localDateTime ->
            return localDateTime.atZone(zone).format(formatter)
        }
    }

    return value
}
