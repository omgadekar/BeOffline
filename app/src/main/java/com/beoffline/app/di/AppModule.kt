package com.beoffline.app.di

import android.content.Context
import androidx.room.Room
import androidx.work.Configuration
import androidx.hilt.work.HiltWorkerFactory
import com.beoffline.app.data.local.BeOfflineDatabase
import com.beoffline.app.data.local.BlockRuleDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): BeOfflineDatabase {
        return Room.databaseBuilder(
            context,
            BeOfflineDatabase::class.java,
            "beoffline.db"
        )
        .fallbackToDestructiveMigration() // For development only; use migrations in prod
        .build()
    }

    @Provides
    @Singleton
    fun provideBlockRuleDao(db: BeOfflineDatabase): BlockRuleDao = db.blockRuleDao()

    @Provides
    fun provideWorkManagerConfiguration(workerFactory: HiltWorkerFactory): Configuration {
        return Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
    }
}
