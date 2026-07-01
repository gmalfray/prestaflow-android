package com.rebuildit.prestaflow.domain.auth

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Base64
import kotlin.text.Charsets

/**
 * Parse le contenu d'un QR code PrestaFlow et en extrait l'URL boutique et la clé API.
 *
 * Deux formats supportés :
 * - URI profonde `prestaflow://connect?data=<base64(json)>` — format généré par le module.
 * - JSON brut `{"shopUrl":"…","apiKey":"…"}` — format alternatif.
 *
 * Retourne `null` si le contenu ne correspond à aucun format reconnu ou si des champs
 * obligatoires sont absents/vides.
 *
 * N'a aucune dépendance au SDK Android (utilise [java.util.Base64] et kotlinx-serialization) :
 * testable directement en JUnit JVM sans Robolectric.
 */
object QrCodeParser {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * @param raw Contenu brut retourné par le scanner ZXing (déjà trimmé de préférence).
     * @return Paire (shopUrl, apiKey) si le QR est valide, `null` sinon.
     */
    @Suppress("ReturnCount") // Parsing défensif QR code : early-returns sur chaque étape de décodage/validation
    fun parse(raw: String): Pair<String, String>? {
        if (raw.isBlank()) return null

        val jsonString =
            if (raw.startsWith("prestaflow://", ignoreCase = true)) {
                val encoded = extractQueryParam(raw, "data") ?: return null
                val decoded =
                    runCatching {
                        Base64.getDecoder().decode(encoded)
                    }.getOrElse { return null }
                String(decoded, Charsets.UTF_8)
            } else {
                raw
            }

        val element = runCatching { json.parseToJsonElement(jsonString) }.getOrNull() ?: return null
        val obj = runCatching { element.jsonObject }.getOrNull() ?: return null
        val shopUrl = runCatching { obj["shopUrl"]?.jsonPrimitive?.content }.getOrNull()
        val apiKey = runCatching { obj["apiKey"]?.jsonPrimitive?.content }.getOrNull()
        if (shopUrl.isNullOrBlank() || apiKey.isNullOrBlank()) {
            return null
        }

        return shopUrl to apiKey
    }

    /**
     * Extrait la valeur d'un paramètre de query string depuis une URI brute sans
     * dépendre de [android.net.Uri] (compatible tests JVM).
     *
     * Exemple : `prestaflow://connect?data=abc&foo=bar` → `extractQueryParam(..., "data")` = `"abc"`.
     */
    private fun extractQueryParam(
        uri: String,
        name: String,
    ): String? {
        val query = uri.substringAfter("?", "")
        if (query.isBlank()) return null
        return query.split("&")
            .firstOrNull { it.startsWith("$name=") }
            ?.substringAfter("=")
            ?.takeIf { it.isNotBlank() }
    }
}
