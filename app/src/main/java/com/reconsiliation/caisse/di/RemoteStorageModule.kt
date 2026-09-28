package com.reconsiliation.caisse.di

import android.content.Context
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
}
