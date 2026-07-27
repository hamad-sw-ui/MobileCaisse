package com.reconsiliation.caisse.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.reconsiliation.caisse.data.local.entity.PriceHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PriceHistoryDao {
    @Insert
    suspend fun insert(history: PriceHistoryEntity)

    @Query("SELECT * FROM price_history WHERE productId = :productId ORDER BY date DESC")
    fun getHistoryForProduct(productId: Long): Flow<List<PriceHistoryEntity>>
    
    @Query("SELECT * FROM price_history WHERE type = :type ORDER BY date DESC")
    fun getHistoryByType(type: String): Flow<List<PriceHistoryEntity>>
}
