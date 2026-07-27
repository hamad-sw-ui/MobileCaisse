package com.reconsiliation.caisse.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "subscription")
data class SubscriptionEntity(
    @PrimaryKey val id: Int = 1,
    val startDate: Date,
    val endDate: Date,
    val isActive: Boolean = false,
    val type: String = "PREMIUM",
    val deviceId: String, // ID unique du téléphone
    val activationKey: String? = null // La clé cryptographique générée par vous
)
