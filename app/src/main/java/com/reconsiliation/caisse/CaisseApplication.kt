package com.reconsiliation.caisse

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import androidx.hilt.work.HiltWorkerFactory
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlin.system.exitProcess

@HiltAndroidApp
class CaisseApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        setupCrashHandler()
    }

    private fun setupCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // Log local de l'erreur fatale
            Log.e("FATAL_ERROR", "Crash détecté sur ${thread.name}: ${throwable.message}", throwable)
            
            // Ici, on pourrait envoyer le rapport d'erreur à un serveur ou le stocker localement
            // pour le prochain redémarrage.
            
            // On laisse le système gérer la fermeture propre après le log
            defaultHandler?.uncaughtException(thread, throwable) ?: exitProcess(1)
        }
    }
}
