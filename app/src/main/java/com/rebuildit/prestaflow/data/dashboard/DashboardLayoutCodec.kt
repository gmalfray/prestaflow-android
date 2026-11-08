package com.rebuildit.prestaflow.data.dashboard

import com.rebuildit.prestaflow.domain.dashboard.model.DashboardLayout
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Entrée persistée : `{"id":"kpi_orders","visible":true}`. */
@Serializable
internal data class StoredWidget(
    @SerialName("id") val id: String,
    @SerialName("visible") val visible: Boolean = true,
)

/** (Dé)sérialisation JSON de la [DashboardLayout] : tableau ordonné de [StoredWidget]. */
internal object DashboardLayoutCodec {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    private val serializer = ListSerializer(StoredWidget.serializer())

    fun encode(layout: DashboardLayout): String =
        json.encodeToString(serializer, layout.items.map { StoredWidget(it.widget.id, it.visible) })

    /** Lecture défensive : absent ou corrompu → disposition par défaut. */
    fun decode(raw: String?): DashboardLayout {
        val stored =
            raw?.takeIf { it.isNotBlank() }?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }
        return stored?.let { list -> DashboardLayout.merge(list.map { it.id to it.visible }) } ?: DashboardLayout.DEFAULT
    }
}
