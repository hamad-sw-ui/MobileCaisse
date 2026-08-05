package com.reconsiliation.caisse.di

import android.content.Context
import com.reconsiliation.caisse.data.local.AppDatabase
import com.reconsiliation.caisse.data.local.dao.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getDatabase(context)
    }

    @Provides
    fun provideBoutiqueDao(db: AppDatabase): BoutiqueDao = db.boutiqueDao()

    @Provides
    fun provideVenteDao(db: AppDatabase): VenteDao = db.venteDao()

    @Provides
    fun provideStockDao(db: AppDatabase): StockDao = db.stockDao()

    @Provides
    fun provideSubscriptionDao(db: AppDatabase): SubscriptionDao = db.subscriptionDao()

    @Provides
    fun provideSmsErrorDao(db: AppDatabase): SmsErrorDao = db.smsErrorDao()

    @Provides
    fun provideCustomerDao(db: AppDatabase): CustomerDao = db.customerDao()

    @Provides
    fun provideRepaymentDao(db: AppDatabase): RepaymentDao = db.repaymentDao()

    @Provides
    fun provideExpenseDao(db: AppDatabase): ExpenseDao = db.expenseDao()

    @Provides
    fun provideProcessedSmsDao(db: AppDatabase): ProcessedSmsDao = db.processedSmsDao()

    @Provides
    fun provideAuditDao(db: AppDatabase): AuditDao = db.auditDao()

    @Provides
    fun providePriceHistoryDao(db: AppDatabase): PriceHistoryDao = db.priceHistoryDao()

    @Provides
    fun provideSupplyDao(db: AppDatabase): SupplyDao = db.supplyDao()

    @Provides
    fun provideClosureDao(db: AppDatabase): ClosureDao = db.closureDao()

    @Provides
    fun provideActionLogDao(db: AppDatabase): ActionLogDao = db.actionLogDao()

    @Provides
    fun provideSupplierDao(db: AppDatabase): SupplierDao = db.supplierDao()

    @Provides
    fun provideSessionDao(db: AppDatabase): SessionDao = db.sessionDao()

    @Provides
    fun provideStockMovementDao(db: AppDatabase): StockMovementDao = db.stockMovementDao()

    @Provides
    fun provideCategoryDao(db: AppDatabase): CategoryDao = db.categoryDao()

    @Provides
    fun provideRecipeDao(db: AppDatabase): RecipeDao = db.recipeDao()

    @Provides
    fun provideStaffDao(db: AppDatabase): StaffDao = db.staffDao()
}
