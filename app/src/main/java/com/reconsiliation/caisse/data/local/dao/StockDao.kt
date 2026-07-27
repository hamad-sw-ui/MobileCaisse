package com.reconsiliation.caisse.data.local.dao

import androidx.room.*
import com.reconsiliation.caisse.data.local.entity.StockEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StockDao {
    @Query("SELECT * FROM stock ORDER BY productName ASC")
    fun getAllStock(): Flow<List<StockEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(stock: StockEntity)

    @Delete
    suspend fun delete(stock: StockEntity)

    @Query("UPDATE stock SET quantity = quantity + :delta WHERE id = :id")
    suspend fun updateQuantity(id: Long, delta: Double)

    @Query("SELECT * FROM stock WHERE quantity <= alertThreshold")
    fun getLowStockAlerts(): Flow<List<StockEntity>>

    @Query("SELECT * FROM stock WHERE id = :id")
    suspend fun getStockById(id: Long): StockEntity?

    @Query("SELECT * FROM stock WHERE barcode = :barcode LIMIT 1")
    suspend fun getProductByBarcode(barcode: String): StockEntity?

    @Query("SELECT * FROM stock WHERE productName LIKE '%' || :query || '%' OR category LIKE '%' || :query || '%' ORDER BY productName ASC")
    fun searchStock(query: String): Flow<List<StockEntity>>

    @Query("""
        SELECT s.*, SUM(vi.quantity * (vi.unitPrice - vi.purchasePrice)) as totalProfit
        FROM stock s
        JOIN vente_items vi ON s.id = vi.productId
        JOIN ventes v ON v.id = vi.venteId
        WHERE v.status = 'CONFIRMED'
        GROUP BY s.id
        ORDER BY totalProfit DESC
        LIMIT :limit
    """)
    fun getTopProfitableProducts(limit: Int): Flow<List<StockEntity>>
}
