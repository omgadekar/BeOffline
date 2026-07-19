package com.beoffline.app.di

import android.content.Context
import androidx.room.Room
import androidx.work.Configuration
import androidx.hilt.work.HiltWorkerFactory
import com.beoffline.app.data.local.AllowanceDao
import com.beoffline.app.data.local.BeOfflineDatabase
import com.beoffline.app.data.local.BlockRuleDao
import com.beoffline.app.data.local.OpenBlockRuleDao
import com.beoffline.app.data.local.OutboxDao
import com.beoffline.app.data.local.PartnerDao
import com.beoffline.app.data.local.SoloTeaserStateDao
import com.beoffline.app.data.local.UnlockRequestCacheDao
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
        // Published app: real migrations only. Destructive fallback would wipe
        // users' rules on any schema bump — never re-add it.
        .addMigrations(
            BeOfflineDatabase.MIGRATION_1_2,
            BeOfflineDatabase.MIGRATION_2_3,
            BeOfflineDatabase.MIGRATION_3_4
        )
        .build()
    }

    @Provides
    @Singleton
    fun provideBlockRuleDao(db: BeOfflineDatabase): BlockRuleDao = db.blockRuleDao()

    @Provides
    @Singleton
    fun provideOpenBlockRuleDao(db: BeOfflineDatabase): OpenBlockRuleDao = db.openBlockRuleDao()

    @Provides
    @Singleton
    fun provideAllowanceDao(db: BeOfflineDatabase): AllowanceDao = db.allowanceDao()

    @Provides
    @Singleton
    fun provideSoloTeaserStateDao(db: BeOfflineDatabase): SoloTeaserStateDao = db.soloTeaserStateDao()

    @Provides
    @Singleton
    fun providePartnerDao(db: BeOfflineDatabase): PartnerDao = db.partnerDao()

    @Provides
    @Singleton
    fun provideUnlockRequestCacheDao(db: BeOfflineDatabase): UnlockRequestCacheDao = db.unlockRequestCacheDao()

    @Provides
    @Singleton
    fun provideOutboxDao(db: BeOfflineDatabase): OutboxDao = db.outboxDao()

    @Provides
    fun provideWorkManagerConfiguration(workerFactory: HiltWorkerFactory): Configuration {
        return Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
    }
}
