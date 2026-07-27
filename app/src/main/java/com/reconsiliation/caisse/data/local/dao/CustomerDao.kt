package com.reconsiliation.caisse.data.local.dao

import androidx.room.*
import com.reconsiliation.caisse.data.local.entity.CustomerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomerDao {
    @Query("SELECT * FROM customers ORDER BY name ASC")
    fun getAllCustomers(): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE totalDebt > 0 ORDER BY totalDebt DESC")
    fun getDebtors(): Flow<List<CustomerEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(customer: CustomerEntity): Long

    @Query("UPDATE customers SET totalDebt = totalDebt + :amount WHERE id = :id")
    suspend fun updateDebt(id: Long, amount: Double): Int

    @Query("UPDATE customers SET loyaltyPoints = loyaltyPoints + :points, totalSpent = totalSpent + :spent WHERE id = :id")
    suspend fun updateLoyalty(id: Long, points: Int, spent: Double)

    @Query("SELECT * FROM customers WHERE phone = :phone LIMIT 1")
    suspend fun getCustomerByPhone(phone: String): CustomerEntity?

    @Query("SELECT * FROM customers WHERE phone = :phone AND totalDebt > 0 LIMIT 1")
    suspend fun getDebtorByPhone(phone: String): CustomerEntity?

    @Query("UPDATE customers SET creditBalance = creditBalance + :delta WHERE id = :id")
    suspend fun updateCreditBalance(id: Long, delta: Double): Int

    @Query("UPDATE customers SET isVip = :isVip WHERE id = :id")
    suspend fun updateVipStatus(id: Long, isVip: Boolean): Int

    @Query("SELECT * FROM customers WHERE id = :id")
    suspend fun getCustomerById(id: Long): CustomerEntity?

    @Delete
    suspend fun delete(customer: CustomerEntity)
}
