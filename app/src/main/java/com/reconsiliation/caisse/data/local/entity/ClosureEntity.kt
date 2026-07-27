package com.reconsiliation.caisse.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "closures")
data class ClosureEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Date,
    val theoreticalCash: Double,
    val actualCash: Double,
    val theoreticalMomo: Double,
    val actualMomo: Double,
    val discrepancy: Double,
    val note: String? = null
)
