package com.reconsiliation.caisse.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.reconsiliation.caisse.data.local.entity.StockMovementEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StockMovementDao {
    @Insert
    suspend fun insert(movement: StockMovementEntity)

    @Query("SELECT * FROM stock_movements WHERE productId = :productId ORDER BY date DESC")
    fun getMovementsForProduct(productId: Long): Flow<List<StockMovementEntity>>
}
