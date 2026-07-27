package com.reconsiliation.caisse.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * Represents a component of a composite product.
 * Example: A "Pain Chargé" (parentProductId) contains 0.05 units of "Chocolat Seau" (ingredientProductId).
 */
@Entity(
    tableName = "recipes",
    foreignKeys = [
        ForeignKey(
            entity = StockEntity::class,
            parentColumns = ["id"],
            childColumns = ["parentProductId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = StockEntity::class,
            parentColumns = ["id"],
            childColumns = ["ingredientProductId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        androidx.room.Index("parentProductId"),
        androidx.room.Index("ingredientProductId")
    ]
)
data class RecipeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val parentProductId: Long,      // The composite product (e.g., Pain Chargé)
    val ingredientProductId: Long,  // The raw material (e.g., Chocolat)
    val quantityRequired: Double    // Amount to deduct from ingredient stock
)
