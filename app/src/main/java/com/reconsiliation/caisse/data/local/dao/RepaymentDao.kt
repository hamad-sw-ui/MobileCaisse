package com.reconsiliation.caisse.data.local.dao

import androidx.room.*
import com.reconsiliation.caisse.data.local.entity.RepaymentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RepaymentDao {
    @Query("SELECT * FROM repayments WHERE customerId = :customerId ORDER BY date DESC")
    fun getRepaymentsForCustomer(customerId: Long): Flow<List<RepaymentEntity>>

    @Insert
    suspend fun insert(repayment: RepaymentEntity)
}
