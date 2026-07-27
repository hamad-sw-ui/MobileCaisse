package com.reconsiliation.caisse.data.seed

import com.reconsiliation.caisse.data.local.AppDatabase
import com.reconsiliation.caisse.data.local.entity.*
import java.util.*

class DataSeeder(private val db: AppDatabase) {
    suspend fun seedIfNeeded() {
        // Check if data already exists
        // This is a simplified seeder
    }

    suspend fun seedSampleData() {
        val boutique = BoutiqueEntity(
            name = "Boulangerie Chez Marie",
            ownerName = "Marie",
            phoneNumber = "677123456",
            operator = "MTN",
            momoNumber = "677123456",
            isSetupComplete = true
        )
        db.boutiqueDao().insertOrUpdate(boutique)

        val now = Date()
        val ventes = listOf(
            VenteEntity(amount = 5000.0, description = "Pain et lait", date = now, paymentMethod = "CASH", status = "CONFIRMED"),
            VenteEntity(amount = 2500.0, description = "Gâteau", date = now, paymentMethod = "MOMO", status = "CONFIRMED", transactionId = "123456789")
        )
        ventes.forEach { db.venteDao().insertVente(it) }

        val stocks = listOf(
            StockEntity(productName = "Farine", quantity = 100.0, unitPrice = 12000.0, alertThreshold = 10.0),
            StockEntity(productName = "Sucre", quantity = 50.0, unitPrice = 800.0, alertThreshold = 5.0)
        )
        stocks.forEach { db.stockDao().insertOrUpdate(it) }

        val sub = SubscriptionEntity(
            startDate = now,
            endDate = Date(now.time + (30L * 24 * 60 * 60 * 1000)),
            isActive = true,
            deviceId = "SAMPLE_DEVICE"
        )
        db.subscriptionDao().insertOrUpdate(sub)

        // Seed Default Categories
        val productCategories = listOf("Général", "Boissons", "Alimentation", "Divers")
        productCategories.forEach { db.categoryDao().insert(CategoryEntity(name = it, type = "PRODUCT")) }

        val expenseCategories = listOf("Divers", "Loyer", "Électricité", "Salaires", "Transport")
        expenseCategories.forEach { db.categoryDao().insert(CategoryEntity(name = it, type = "EXPENSE")) }
    }
}
