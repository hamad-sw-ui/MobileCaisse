package com.reconsiliation.caisse.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "customers")
data class CustomerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String,
    val totalDebt: Double = 0.0,
    val loyaltyPoints: Int = 0,
    val isVip: Boolean = false,
    val totalSpent: Double = 0.0,
    val creditBalance: Double = 0.0 // Point 3: Avoirs/Customer Credit
)
