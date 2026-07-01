package com.rebuildit.prestaflow.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "orders")
data class OrderEntity(
    @PrimaryKey val id: Long,
    val reference: String,
    val status: String,
    @ColumnInfo(name = "total_paid") val totalPaid: Double,
    val currency: String,
    @ColumnInfo(name = "customer_name") val customerName: String,
    @ColumnInfo(name = "created_at_iso", defaultValue = "") val createdAtIso: String = "",
    @ColumnInfo(name = "updated_at_iso") val updatedAtIso: String,
    @ColumnInfo(name = "has_invoice", defaultValue = "0") val hasInvoice: Boolean = false,
    @ColumnInfo(name = "has_shipping_label", defaultValue = "0") val hasShippingLabel: Boolean = false,
    @ColumnInfo(name = "items_json") val itemsJson: String? = null,
    @ColumnInfo(name = "shipping_json") val shippingJson: String? = null,
    /** Couleur hex du statut (`#RRGGBB`), null si absent (connecteur < v1.9). */
    @ColumnInfo(name = "status_color") val statusColor: String? = null,
    /** ID numérique du statut PrestaShop, 0 si absent. */
    @ColumnInfo(name = "current_state_id", defaultValue = "0") val currentStateId: Int = 0,
)
