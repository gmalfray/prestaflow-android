package com.rebuildit.prestaflow.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * SAV natif PrestaShop (`ps_customer_thread` / `ps_customer_message`) — cf. `rebuild-connector`
 * docs/api.md § SAV. Une seule forme de fil (`SavThreadDto`), réutilisée en liste et en détail
 * (contrairement aux commandes, pas de schéma plat/imbriqué distinct ici).
 */
@Serializable
data class SavThreadListResponseDto(
    @SerialName("threads") val threads: List<SavThreadDto> = emptyList(),
    @SerialName("pagination") val pagination: PaginationDto? = null,
)

@Serializable
data class SavThreadDto(
    @SerialName("id") val id: Long,
    /** Un des 4 statuts natifs (`open`/`pending1`/`pending2`/`closed`). */
    @SerialName("status") val status: String = "",
    @SerialName("unread") val unread: Boolean = false,
    /**
     * ⚠️ Le connecteur émet TOUJOURS cet objet, même pour un contact anonyme : c'est
     * `customer.id`/`customer.name` qui valent alors `null` (jamais l'objet entier). Nullable ici
     * uniquement par tolérance/rétrocompat — ne pas s'appuyer sur `customer == null` pour détecter
     * un fil anonyme.
     */
    @SerialName("customer") val customer: SavCustomerDto? = null,
    /** Null si le fil n'est rattaché à aucune commande. */
    @SerialName("order") val order: SavOrderDto? = null,
    @SerialName("last_message_at") val lastMessageAt: String? = null,
    @SerialName("date_add") val dateAdd: String? = null,
    @SerialName("date_upd") val dateUpd: String? = null,
)

@Serializable
data class SavCustomerDto(
    @SerialName("id") val id: Long? = null,
    @SerialName("name") val name: String? = null,
    /**
     * ⚠️ Chaîne VIDE (jamais `null`) quand le fil n'a pas d'adresse exploitable — le connecteur
     * caste en `(string)` (`SavService::formatThreadRow()`). Toujours tester `isNotBlank()`, jamais
     * `!= null` : c'est cette adresse qui décide de `email_sent` sur `POST /sav/{id}/reply`.
     */
    @SerialName("email") val email: String? = null,
)

@Serializable
data class SavOrderDto(
    @SerialName("id") val id: Long? = null,
    @SerialName("reference") val reference: String? = null,
)

@Serializable
data class SavThreadDetailResponseDto(
    @SerialName("thread") val thread: SavThreadDto,
    @SerialName("messages") val messages: List<SavMessageDto> = emptyList(),
)

@Serializable
data class SavMessageDto(
    @SerialName("id") val id: Long,
    /** `"employee"` si `id_employee > 0`, `"customer"` sinon. */
    @SerialName("author") val author: String = "customer",
    @SerialName("employee_name") val employeeName: String? = null,
    @SerialName("message") val message: String = "",
    @SerialName("private") val private: Boolean = false,
    @SerialName("read") val read: Boolean = false,
    @SerialName("date_add") val dateAdd: String? = null,
)

@Serializable
data class SavStatusUpdateRequestDto(
    @SerialName("status") val status: String,
)

@Serializable
data class SavReplyRequestDto(
    @SerialName("message") val message: String,
)

/**
 * Réponse de `POST /sav/{id}/reply` (201). ⚠️ Cet appel envoie un VRAI e-mail à la cliente — cf.
 * [com.rebuildit.prestaflow.domain.sav.SavRepository.replyToThread].
 */
@Serializable
data class SavReplyResponseDto(
    @SerialName("thread") val thread: SavThreadDto,
    @SerialName("message") val message: SavMessageDto,
    /** `false` si le fil n'a pas d'adresse e-mail exploitable — n'indique pas un échec de l'appel. */
    @SerialName("email_sent") val emailSent: Boolean = false,
)
