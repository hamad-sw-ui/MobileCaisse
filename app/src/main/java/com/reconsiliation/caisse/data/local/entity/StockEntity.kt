package com.reconsiliation.caisse.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

import java.io.Serializable

@Entity(
    tableName = "stock",
    indices = [androidx.room.Index(value = ["barcode"], unique = true)]
)
data class StockEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productName: String,
    val quantity: Double,
    val unitPrice: Double,
    val purchasePrice: Double = 0.0,
    val vipPrice: Double = 0.0, // Point 4: VIP Pricing
    val alertThreshold: Double,
    val barcode: String? = null,
    val category: String = "Général",
    val unit: String = "pcs", // kg, litre, sac, etc.
    val isBulk: Boolean = false,
    val retailUnit: String? = null,
    val conversionFactor: Double = 1.0, // 1 Bulk = X Retail units
    val retailPrice: Double = 0.0
) : Serializable
