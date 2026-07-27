package com.reconsiliation.caisse.data.local

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.reconsiliation.caisse.data.repository.MainRepository
import com.reconsiliation.caisse.utils.NotificationHelper
import kotlinx.coroutines.flow.first
import java.util.*
import java.util.concurrent.TimeUnit

class SubscriptionWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val db = AppDatabase.getDatabase(applicationContext)
            val repository = MainRepository(db, applicationContext)
            val notificationHelper = NotificationHelper(applicationContext)

            val subscription = repository.getSubscriptionStatus(applicationContext).first()
            
            if (subscription != null && subscription.isActive) {
                val now = Date()
                val diffInMillis = subscription.endDate.time - now.time
                val daysRemaining = TimeUnit.MILLISECONDS.toDays(diffInMillis).toInt()

                Log.d("SubscriptionWorker", "Subscription ends in $daysRemaining days")

                // Logic: Notify at 7, 3, 1, and 0 days
                when (daysRemaining) {
                    7, 3, 1 -> {
                        notificationHelper.showSubscriptionReminder(daysRemaining)
                    }
                    0 -> {
                        // On the day it expires
                        if (subscription.endDate.before(now)) {
                            notificationHelper.showSubscriptionReminder(0)
                        } else {
                            notificationHelper.showSubscriptionReminder(1)
                        }
                    }
                }
                
                // If already expired
                if (daysRemaining < 0) {
                     // Maybe notify once or periodically when expired
                     // For now, let's notify only if just expired (e.g., -1 day)
                     if (daysRemaining == -1) {
                         notificationHelper.showSubscriptionReminder(0)
                     }
                }
            }

            Result.success()
        } catch (e: Exception) {
            Log.e("SubscriptionWorker", "Error checking subscription", e)
            Result.failure()
        }
    }
}
