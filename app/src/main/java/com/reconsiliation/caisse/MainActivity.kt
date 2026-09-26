package com.reconsiliation.caisse

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.ComponentActivity
import dagger.hilt.android.AndroidEntryPoint
import androidx.work.*
import com.reconsiliation.caisse.data.local.BackupWorker
import com.reconsiliation.caisse.data.local.SubscriptionWorker
import java.util.concurrent.TimeUnit
import com.reconsiliation.caisse.ui.navigation.AppNavigation
import com.reconsiliation.caisse.ui.theme.CAISSETheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        scheduleBackup()
        scheduleSubscriptionCheck()

        setContent {
            CAISSETheme {
                AppNavigation()
            }
        }
    }

    private fun scheduleBackup() {
        val backupRequest = PeriodicWorkRequestBuilder<BackupWorker>(24, TimeUnit.HOURS)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "DailyBackup",
            ExistingPeriodicWorkPolicy.KEEP,
            backupRequest
        )
    }

    private fun scheduleSubscriptionCheck() {
        val subRequest = PeriodicWorkRequestBuilder<SubscriptionWorker>(24, TimeUnit.HOURS)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "SubscriptionCheck",
            ExistingPeriodicWorkPolicy.KEEP,
            subRequest
        )
    }
}
