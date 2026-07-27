package com.reconsiliation.caisse.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.reconsiliation.caisse.data.local.entity.SupplyEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SupplyDao {
    @Insert
    suspend fun insert(supply: SupplyEntity): Long

    @Query("SELECT * FROM supplies ORDER BY date DESC")
    fun getAllSupplies(): Flow<List<SupplyEntity>>

    @Query("SELECT * FROM supplies WHERE productId = :productId ORDER BY date DESC")
    fun getSuppliesForProduct(productId: Long): Flow<List<SupplyEntity>>
}
