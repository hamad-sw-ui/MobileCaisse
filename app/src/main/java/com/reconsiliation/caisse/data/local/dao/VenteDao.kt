package com.reconsiliation.caisse.data.local.dao

import androidx.room.*
import com.reconsiliation.caisse.data.local.entity.VenteEntity
import com.reconsiliation.caisse.data.local.entity.VenteItemEntity
import kotlinx.coroutines.flow.Flow
import java.util.Date

data class VenteWithItems(
    @Embedded val vente: VenteEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "venteId"
    )
    val items: List<VenteItemEntity>
)

data class CategoryReport(
    val category: String,
    val totalSales: Double
)

data class DailySales(
    val day: String,
    val total: Double
)

@Dao
interface VenteDao {
    @Transaction
    @Query("SELECT * FROM ventes ORDER BY date DESC")
    fun getAllVentesWithItems(): Flow<List<VenteWithItems>>

    @Transaction
    @Query("SELECT * FROM ventes WHERE id = :venteId")
    suspend fun getVenteWithItemsById(venteId: Long): VenteWithItems?

    @Transaction
    @Query("SELECT * FROM ventes WHERE customerId = :customerId ORDER BY date DESC")
    fun getVentesByCustomerId(customerId: Long): Flow<List<VenteWithItems>>

    @Query("SELECT * FROM ventes ORDER BY date DESC")
    fun getAllVentes(): Flow<List<VenteEntity>>

    @Query("SELECT * FROM ventes ORDER BY date DESC LIMIT :limit OFFSET :offset")
    fun getVentesPaged(limit: Int, offset: Int): Flow<List<VenteEntity>>

    @Query("SELECT * FROM ventes WHERE date BETWEEN :start AND :end ORDER BY date DESC")
    fun getVentesByDateRange(start: Date, end: Date): Flow<List<VenteEntity>>

    @Transaction
    @Query("SELECT * FROM ventes WHERE date BETWEEN :start AND :end ORDER BY date DESC")
    fun getVentesWithItemsByDateRange(start: Date, end: Date): Flow<List<VenteWithItems>>

    @Query("SELECT * FROM ventes WHERE id = :id LIMIT 1")
    suspend fun getVenteById(id: Long): VenteEntity?

    @Insert
    suspend fun insertVente(vente: VenteEntity): Long

    @Insert
    suspend fun insertVenteItems(items: List<VenteItemEntity>)

    @Update
    suspend fun updateVente(vente: VenteEntity)

    @Delete
    suspend fun deleteVente(vente: VenteEntity)

    @Query("SELECT * FROM ventes WHERE transactionId = :transId LIMIT 1")
    suspend fun getVenteByTransactionId(transId: String): VenteEntity?

    @Query("SELECT * FROM ventes WHERE status = 'PENDING' AND (amount = :amount OR amountMomo = :amount) AND (paymentMethod = 'MOMO' OR paymentMethod = 'MIXED') ORDER BY date DESC LIMIT 1")
    suspend fun findPendingMomoSale(amount: Double): VenteEntity?

    @Query("SELECT * FROM ventes WHERE status = 'PENDING' AND (amount = :amount OR amountMomo = :amount) AND (customerPhone = :phone OR :phone = '') AND (paymentMethod = 'MOMO' OR paymentMethod = 'MIXED') ORDER BY date DESC LIMIT 1")
    suspend fun findPendingMomoSaleWithPhone(amount: Double, phone: String): VenteEntity?

    @Query("SELECT SUM(amount) FROM ventes WHERE date BETWEEN :start AND :end AND status = 'CONFIRMED' AND paymentMethod = :method")
    fun getTotalByMethod(start: Date, end: Date, method: String): Flow<Double?>

    @Query("SELECT SUM(amountCash) FROM ventes WHERE date BETWEEN :start AND :end AND status = 'CONFIRMED'")
    fun getTotalCash(start: Date, end: Date): Flow<Double?>

    @Query("SELECT SUM(amountMomo) FROM ventes WHERE date BETWEEN :start AND :end AND status = 'CONFIRMED'")
    fun getTotalMomo(start: Date, end: Date): Flow<Double?>

    @Query("""
        SELECT
            SUM(v.amount - v.fees - v.discount - (SELECT SUM(vi.quantity * vi.purchasePrice) FROM vente_items vi WHERE vi.venteId = v.id))
        FROM ventes v
        WHERE v.date BETWEEN :start AND :end AND v.status = 'CONFIRMED'
    """)
    fun getProfitForRange(start: Date, end: Date): Flow<Double?>

    @Query("""
        SELECT s.category, SUM(vi.quantity * vi.unitPrice) as totalSales
        FROM vente_items vi
        JOIN stock s ON vi.productId = s.id
        JOIN ventes v ON vi.venteId = v.id
        WHERE v.date BETWEEN :start AND :end AND v.status = 'CONFIRMED'
        GROUP BY s.category
        ORDER BY totalSales DESC
    """)
    fun getSalesByCategory(start: Date, end: Date): Flow<List<CategoryReport>>

    @Query("""
        SELECT strftime('%H', datetime(date / 1000, 'unixepoch', 'localtime')) as day, SUM(amount) as total
        FROM ventes
        WHERE date BETWEEN :start AND :end AND status = 'CONFIRMED'
        GROUP BY day
        ORDER BY day ASC
    """)
    fun getHourlySales(start: Date, end: Date): Flow<List<DailySales>>

    @Query("""
        SELECT strftime('%d', datetime(date / 1000, 'unixepoch', 'localtime')) as day, SUM(amount) as total
        FROM ventes
        WHERE date BETWEEN :start AND :end AND status = 'CONFIRMED'
        GROUP BY day
        ORDER BY day ASC
    """)
    fun getDailySales(start: Date, end: Date): Flow<List<DailySales>>

    @Query("""
        SELECT strftime('%m', datetime(date / 1000, 'unixepoch', 'localtime')) as day, SUM(amount) as total
        FROM ventes
        WHERE date BETWEEN :start AND :end AND status = 'CONFIRMED'
        GROUP BY day
        ORDER BY day ASC
    """)
    fun getMonthlySales(start: Date, end: Date): Flow<List<DailySales>>

    @Query("SELECT * FROM supplies WHERE expiryDate IS NOT NULL AND expiryDate <= :limit ORDER BY expiryDate ASC")
    fun getExpiringSupplies(limit: Date): Flow<List<com.reconsiliation.caisse.data.local.entity.SupplyEntity>>

    @Query("UPDATE ventes SET isLocked = 1 WHERE date BETWEEN :start AND :end")
    suspend fun lockSalesForDateRange(start: Date, end: Date): Int

    @Query("DELETE FROM ventes WHERE date < :beforeDate AND isLocked = 1")
    suspend fun deleteOldLockedVentes(beforeDate: Date): Int

    @Query("DELETE FROM vente_items WHERE venteId NOT IN (SELECT id FROM ventes)")
    suspend fun deleteOrphanVenteItems(): Int

    @Query("DELETE FROM vente_items WHERE venteId = :venteId")
    suspend fun deleteItemsForVente(venteId: Long)

    @Query("SELECT * FROM vente_items WHERE venteId = :venteId")
    suspend fun getItemsForVente(venteId: Long): List<VenteItemEntity>

    @Query("SELECT MAX(invoiceNumber) FROM ventes WHERE invoiceNumber LIKE :yearPrefix || '%'")
    suspend fun getMaxInvoiceNumberForYear(yearPrefix: String): String?

    @Query("SELECT * FROM ventes ORDER BY date DESC LIMIT :limit")
    suspend fun getRecentVentes(limit: Int): List<VenteEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM vente_items WHERE productId = :productId)")
    suspend fun isProductUsed(productId: Long): Boolean
}
