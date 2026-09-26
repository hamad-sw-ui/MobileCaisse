package com.reconsiliation.caisse.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.reconsiliation.caisse.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(@ApplicationContext private val context: Context) {
    private val STOCK_CHANNEL_ID = "stock_alerts"
    private val SUBSCRIPTION_CHANNEL_ID = "subscription_alerts"
    private val MOMO_CHANNEL_ID = "mobile_caisse_alerts"
    
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val stockChannel = NotificationChannel(STOCK_CHANNEL_ID, "Alertes Stock", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Notifications pour les produits en stock bas"
            }
            val subChannel = NotificationChannel(SUBSCRIPTION_CHANNEL_ID, "Abonnement", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Rappels de renouvellement d'abonnement"
            }
            val momoChannel = NotificationChannel(MOMO_CHANNEL_ID, "Paiements MoMo", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Alertes de réception de paiements mobiles"
            }
            notificationManager.createNotificationChannel(stockChannel)
            notificationManager.createNotificationChannel(subChannel)
            notificationManager.createNotificationChannel(momoChannel)
        }
    }

    fun showPaymentNotification(amount: Double, sender: String) {
        val notification = NotificationCompat.Builder(context, MOMO_CHANNEL_ID)
            .setContentTitle("Paiement MoMo Reçu")
            .setContentText("Vous avez reçu ${amount} FCFA de ${sender}")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        deliver(System.currentTimeMillis().toInt(), notification)
    }

    fun showLowStockNotification(productName: String, remainingQty: Double) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(context, STOCK_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Alerte Stock Bas !")
            .setContentText("Le produit '$productName' est presque épuisé ($remainingQty restant).")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        deliver(productName.hashCode(), notification)
    }

    fun showSubscriptionReminder(daysRemaining: Int) {
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("navigate_to", "subscription")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(context, 1, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val title = when {
            daysRemaining <= 0 -> "Abonnement Expiré !"
            daysRemaining <= 1 -> "Urgent : Abonnement finit demain !"
            else -> "Abonnement : Fin proche"
        }

        val text = when {
            daysRemaining <= 0 -> "Votre abonnement a expiré. Veuillez renouveler pour continuer à utiliser toutes les fonctionnalités."
            daysRemaining <= 1 -> "Votre abonnement expire demain. Renouvelez-le maintenant pour éviter toute interruption."
            else -> "Il vous reste $daysRemaining jours d'abonnement. Pensez à renouveler bientôt !"
        }

        val notification = NotificationCompat.Builder(context, SUBSCRIPTION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        deliver(1001, notification)
    }

    private fun deliver(id: Int, notification: android.app.Notification) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        try {
            notificationManager.notify(id, notification)
        } catch (_: SecurityException) {
            // API 33+ without POST_NOTIFICATIONS: skip, never crash.
        }
    }
}
