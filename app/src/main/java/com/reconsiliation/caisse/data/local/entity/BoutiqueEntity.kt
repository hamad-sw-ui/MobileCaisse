package com.reconsiliation.caisse.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "boutique")
data class BoutiqueEntity(
    @PrimaryKey val id: Int = 1,
    val name: String,
    val ownerName: String,
    val phoneNumber: String,
    val operator: String, // MTN or ORANGE
    val momoNumber: String,
    val currency: String = "FCFA",
    val pinHash: String? = null,
    val pinSalt: String? = null,
    val isSetupComplete: Boolean = false,
    val allowNegativeStock: Boolean = true,
    val printerAddress: String? = null,
    val hideProfitsFromStaff: Boolean = false,
    val managerPinHash: String? = null,
    val managerPinSalt: String? = null,
    val taxName: String = "TVA",
    val taxRate: Double = 0.0,
    val isTaxEnabled: Boolean = false,
    val address: String? = null,
    val receiptFooter: String? = null,
    val showCustomerPhoneOnReceipt: Boolean = true,
    val showTaxesOnReceipt: Boolean = true,
    val showTotalQuantityOnReceipt: Boolean = false,
    val printMerchantCopy: Boolean = false,
    val managerCode: String = "SETUP_REQUIRED_12CHARS"  // Min 12 alphanumeric chars
)
