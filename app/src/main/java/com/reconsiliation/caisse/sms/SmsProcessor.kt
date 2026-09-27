package com.reconsiliation.caisse.sms

import android.content.Context
import android.util.Log
import com.reconsiliation.caisse.data.local.AppDatabase
import com.reconsiliation.caisse.data.local.entity.VenteEntity
import com.reconsiliation.caisse.data.local.entity.SmsErrorEntity
import com.reconsiliation.caisse.data.repository.MainRepository
import com.reconsiliation.caisse.data.repository.SalesRepository
import com.reconsiliation.caisse.di.ApplicationScope
import com.reconsiliation.caisse.notification.NotificationHelper
import com.reconsiliation.caisse.utils.AnomalyEngine
import com.reconsiliation.caisse.utils.PhoneUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmsProcessor @Inject constructor(
    private val db: AppDatabase,
    private val repository: MainRepository,
    private val salesRepository: SalesRepository,
    private val notificationHelper: NotificationHelper,
    @ApplicationScope private val scope: CoroutineScope
) {
    fun processSms(
        context: Context,
        messages: List<IncomingSms>,
        onFinished: (() -> Unit)? = null
    ) {
        scope.launch(Dispatchers.IO) {
            try {
                for (message in messages) {
                    val body = message.body
                    val sender = message.sender
                    val parsed = SmsParser.parse(body, sender)
                    if (parsed != null && parsed.isSecure && parsed.transactionId.isNotBlank()) {
                        if (AnomalyEngine.isSmsClockAnomaly(message.timestampMillis, System.currentTimeMillis())) {
                            repository.logAction("DATE_ANOMALY", "Écart de temps détecté (>30m). Heure système potentiellement falsifiée.", severity = "CRITICAL")
                        }

                        // Logic 3: SMS De-duplication (Permanent Memory)
                        val alreadyProcessed = repository.isSmsProcessed(parsed.transactionId)
                        val existingVente = salesRepository.getVenteByTransactionId(parsed.transactionId)
                        if (alreadyProcessed || existingVente != null) {
                            Log.d("SmsProcessor", "Ignored duplicate SMS: ${parsed.transactionId}")
                            continue
                        }
                        
                        repository.markSmsAsProcessed(parsed.transactionId)

                        if (parsed.type == "SUB_CONFIRMATION") {
                            repository.processSubscriptionSms(context, parsed.amount, parsed.transactionId)
                            Log.d("SmsProcessor", "Abonnement auto-activé : ${parsed.amount} FCFA")
                            continue
                        }

                        // Logic 0: Try to match as Debt Repayment (Point 2)
                        if (parsed.sender.isNotEmpty()) {
                            try {
                                val normalized = PhoneUtil.normalize(parsed.sender)
                                salesRepository.processMoMoRepayment(normalized, parsed.amount, parsed.transactionId)
                                Log.d("SmsProcessor", "Dette remboursée par MoMo : ${parsed.transactionId}")
                                continue
                            } catch (e: Exception) {
                                Log.d("SmsProcessor", "Pas un remboursement de dette : ${e.message}")
                            }
                        }

                        // Logic 1: Matching with Phone + Amount (Strong Match)
                        var pendingVente = if (parsed.sender.isNotEmpty()) {
                            val normalized = PhoneUtil.normalize(parsed.sender)
                            salesRepository.findPendingMomoSaleWithPhone(parsed.amount, normalized)
                        } else null

                        // Logic 2: Fallback to Amount only matching (Weak Match)
                        if (pendingVente == null) {
                            pendingVente = salesRepository.findPendingMomoSale(parsed.amount)
                        }

                        if (pendingVente != null && isRecent(pendingVente.date)) {
                            // Update existing sale
                            val updatedVente = pendingVente.copy(
                                status = "CONFIRMED",
                                transactionId = parsed.transactionId,
                                smsBody = body,
                                reconciliationStatus = "OK",
                                customerPhone = PhoneUtil.normalize(parsed.sender)
                            )
                            salesRepository.updateVente(updatedVente)
                            repository.markSmsAsProcessed(parsed.transactionId)
                            
                            notificationHelper.showPaymentNotification(parsed.amount, parsed.sender)
                            
                            Log.d("SmsProcessor", "Réconciliation MoMo réussie : ${parsed.transactionId}")
                        } else {
                            // Create new orphan sale
                            val vente = VenteEntity(
                                amount = parsed.amount,
                                amountMomo = parsed.amount,
                                description = "MoMo Reçu (Orphelin): ${parsed.sender}",
                                date = Date(),
                                paymentMethod = "MOMO",
                                status = "CONFIRMED",
                                customerPhone = PhoneUtil.normalize(parsed.sender),
                                transactionId = parsed.transactionId,
                                smsBody = body,
                                reconciliationStatus = "ORPHAN"
                            )
                            salesRepository.insertVenteWithItems(vente, emptyList())
                            repository.markSmsAsProcessed(parsed.transactionId)
                            
                            notificationHelper.showPaymentNotification(parsed.amount, parsed.sender)
                        }
                    } else {
                        // Only log if it looks like a MoMo message
                        if (body.contains("MoMo", true) || body.contains("Orange", true) || body.contains("Money", true)) {
                            db.smsErrorDao().insert(SmsErrorEntity(body = body, date = Date(), sender = sender))
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("SmsProcessor", "Error processing SMS: ${e.message}", e)
            } finally {
                onFinished?.invoke()
            }
        }
    }

    private fun isRecent(date: Date): Boolean {
        val diff = System.currentTimeMillis() - date.time
        return diff < 24 * 60 * 60 * 1000 // Extended to 24 hours
    }
}
