package com.reconsiliation.caisse.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.reconsiliation.caisse.data.local.AppDatabase
import com.reconsiliation.caisse.data.local.entity.VenteEntity
import com.reconsiliation.caisse.data.local.entity.SmsErrorEntity
import com.reconsiliation.caisse.utils.PhoneUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Date

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val pendingResult = goAsync()
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            processSms(context, messages.mapNotNull { it.messageBody to it.originatingAddress }, pendingResult)
        } else if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("SmsReceiver", "Appareil redémarré : vérification des SMS manqués...")
            // L'initialisation du ViewModel au démarrage de l'app fera le catchUp
        }
    }

    companion object {
        fun processSms(
            context: Context, 
            messages: List<Pair<String, String?>>, 
            pendingResult: PendingResult? = null
        ) {
            val db = AppDatabase.getDatabase(context)
            val repository = com.reconsiliation.caisse.data.repository.MainRepository(db, context)
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    for ((body, sender) in messages) {
                        val parsed = SmsParser.parse(body, sender)
                        if (parsed != null && parsed.isSecure && parsed.transactionId.isNotBlank()) {
                            // Point 2: Date Anomaly Check
                            val networkTime = System.currentTimeMillis() // In a real scenario, extract from PDU/Service Center Timestamp if possible
                            val systemTime = System.currentTimeMillis()
                            if (kotlin.math.abs(networkTime - systemTime) > 30 * 60 * 1000) { // 30 mins threshold
                                repository.logAction("DATE_ANOMALY", "Écart de temps détecté (>30m). Heure système potentiellement falsifiée.", severity = "CRITICAL")
                            }

                            // Logic 3: SMS De-duplication (Permanent Memory)
                            val alreadyProcessed = repository.isSmsProcessed(parsed.transactionId)
                            val existingVente = db.venteDao().getVenteByTransactionId(parsed.transactionId)
                            if (alreadyProcessed || existingVente != null) {
                                Log.d("SmsReceiver", "Ignored duplicate SMS: ${parsed.transactionId}")
                                continue
                            }
                            
                            repository.markSmsAsProcessed(parsed.transactionId)

                            if (parsed.type == "SUB_CONFIRMATION") {
                                repository.processSubscriptionSms(context, parsed.amount, parsed.transactionId)
                                Log.d("SmsReceiver", "Abonnement auto-activé : ${parsed.amount} FCFA")
                                continue
                            }

                            // Logic 0: Try to match as Debt Repayment (Point 2)
                            if (parsed.sender.isNotEmpty()) {
                                try {
                                    val normalized = PhoneUtil.normalize(parsed.sender)
                                    repository.processMoMoRepayment(normalized, parsed.amount, parsed.transactionId)
                                    Log.d("SmsReceiver", "Dette remboursée par MoMo : ${parsed.transactionId}")
                                    continue
                                } catch (e: Exception) {
                                    // Not a debtor or error, continue to normal sale logic
                                }
                            }

                            // Logic 1: Matching with Phone + Amount (Strong Match)
                            var pendingVente = if (parsed.sender.isNotEmpty()) {
                                val normalized = PhoneUtil.normalize(parsed.sender)
                                db.venteDao().findPendingMomoSaleWithPhone(parsed.amount, normalized)
                            } else null

                            // Logic 2: Fallback to Amount only matching (Weak Match)
                            if (pendingVente == null) {
                                pendingVente = db.venteDao().findPendingMomoSale(parsed.amount)
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
                                repository.updateVente(updatedVente)
                                repository.markSmsAsProcessed(parsed.transactionId)
                                
                                // Feedback: Notify user of success
                                com.reconsiliation.caisse.notification.NotificationHelper(context)
                                    .showPaymentNotification(parsed.amount, parsed.sender)
                                
                                Log.d("SmsReceiver", "Réconciliation MoMo réussie : ${parsed.transactionId}")
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
                                repository.insertVenteWithItems(vente, emptyList())
                                repository.markSmsAsProcessed(parsed.transactionId)
                                
                                // Feedback: Notify user of orphan payment
                                com.reconsiliation.caisse.notification.NotificationHelper(context)
                                    .showPaymentNotification(parsed.amount, parsed.sender)
                            }
                        } else {
                            // Only log if it looks like a MoMo message
                            if (body.contains("MoMo", true) || body.contains("Orange", true) || body.contains("Money", true)) {
                                db.smsErrorDao().insert(SmsErrorEntity(body = body, date = Date(), sender = sender))
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("SmsReceiver", "Error processing SMS: ${e.message}", e)
                } finally {
                    pendingResult?.finish()
                }
            }
        }

        private fun isRecent(date: Date): Boolean {
            val diff = System.currentTimeMillis() - date.time
            return diff < 24 * 60 * 60 * 1000 // Extended to 24 hours
        }
    }
}
