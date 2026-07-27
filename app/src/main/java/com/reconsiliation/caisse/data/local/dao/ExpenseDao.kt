package com.reconsiliation.caisse.data.local.dao

import androidx.room.*
import com.reconsiliation.caisse.data.local.entity.ExpenseEntity
import kotlinx.coroutines.flow.Flow
import java.util.Date

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses ORDER BY date DESC")
    fun getAllExpenses(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE date BETWEEN :start AND :end ORDER BY date DESC")
    fun getExpensesByDateRange(start: Date, end: Date): Flow<List<ExpenseEntity>>

    @Insert
    suspend fun insert(expense: ExpenseEntity)

    @Delete
    suspend fun delete(expense: ExpenseEntity)

    @Query("SELECT SUM(amount) FROM expenses WHERE date BETWEEN :start AND :end")
    fun getTotalExpenses(start: Date, end: Date): Flow<Double?>

    @Query("SELECT category, SUM(amount) as total FROM expenses WHERE date BETWEEN :start AND :end GROUP BY category")
    fun getExpensesByCategory(start: Date, end: Date): Flow<List<com.reconsiliation.caisse.ui.viewmodel.CategoryTotal>>
}
