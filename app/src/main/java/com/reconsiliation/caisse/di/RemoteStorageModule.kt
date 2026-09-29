package com.reconsiliation.caisse.di

import android.content.Context
import com.reconsiliation.caisse.data.local.dao.BoutiqueDao
import com.reconsiliation.caisse.data.prefs.PreferencesManager
import com.reconsiliation.caisse.data.repository.MainRepository
import com.reconsiliation.caisse.data.repository.RemoteBackupRepository
import com.reconsiliation.caisse.platform.remote.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RemoteStorageModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideGoogleDriveAuthManager(
        @ApplicationContext context: Context
    ): GoogleDriveAuthManager {
        return PlayServicesGoogleDriveAuthManager(context)
    }

    @Provides
    @Singleton
    fun provideRemoteStorageClient(
        authManager: GoogleDriveAuthManager,
        okHttpClient: OkHttpClient
    ): RemoteStorageClient {
        return GoogleDriveStorageClient(authManager, okHttpClient)
    }

    @Provides
    @Singleton
    fun providePreferencesManager(
        @ApplicationContext context: Context
    ): PreferencesManager {
        return PreferencesManager(context)
    }

    @Provides
    @Singleton
    fun provideRemoteBackupRepository(
        @ApplicationContext context: Context,
        remoteStorageClient: RemoteStorageClient,
        authManager: GoogleDriveAuthManager,
        boutiqueDao: BoutiqueDao,
        preferencesManager: PreferencesManager,
        mainRepository: MainRepository
    ): RemoteBackupRepository {
        return RemoteBackupRepository(
            context = context,
            remoteStorageClient = remoteStorageClient,
            authManager = authManager,
            boutiqueDao = boutiqueDao,
            preferencesManager = preferencesManager,
            mainRepository = mainRepository
        )
    }
}
