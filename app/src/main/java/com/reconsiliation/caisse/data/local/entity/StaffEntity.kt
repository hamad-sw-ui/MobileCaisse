package com.reconsiliation.caisse.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "staff")
data class StaffEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val pinHash: String,
    val pinSalt: String? = null,
    val phone: String? = null,
    val isActive: Boolean = true,
    val role: String = "STAFF" // Can be STAFF or MANAGER
)
