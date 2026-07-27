package com.reconsiliation.caisse.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "audits")
data class AuditEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Date,
    val note: String? = null,
    val totalDiscrepancy: Double = 0.0 // Value of lost/extra stock
)
