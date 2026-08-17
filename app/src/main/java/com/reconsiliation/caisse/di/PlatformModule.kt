package com.reconsiliation.caisse.di

import android.content.Context
import com.reconsiliation.caisse.platform.PrinterManager
import com.reconsiliation.caisse.platform.ReportManager
import com.reconsiliation.caisse.platform.ShareManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PlatformModule {

    @Provides
    @Singleton
    fun provideReportManager(@ApplicationContext context: Context): ReportManager {
        return ReportManager(context)
    }

    @Provides
    @Singleton
    fun providePrinterManager(@ApplicationContext context: Context): PrinterManager {
        return PrinterManager(context)
    }

    @Provides
    @Singleton
    fun provideShareManager(@ApplicationContext context: Context): ShareManager {
        return ShareManager(context)
    }
}
