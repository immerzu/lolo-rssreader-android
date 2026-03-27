package com.example.rssreader

import android.app.Application
import androidx.room.Room
import com.example.rssreader.data.db.AppDatabase
import com.example.rssreader.debug.DebugLogger
import com.example.rssreader.data.network.FeedFetcher
import com.example.rssreader.data.network.FeedParser
import com.example.rssreader.data.repository.FeedRepository
import com.example.rssreader.data.settings.SettingsRepository
import com.example.rssreader.data.translation.ArticleTranslationManager
import com.example.rssreader.data.translation.GoogleFreeTranslationService
import com.example.rssreader.data.translation.TranslationService
import com.example.rssreader.sync.RefreshScheduler

class RssReaderApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        DebugLogger.init(this)
        DebugLogger.i(
            "App",
            "Application gestartet, debugLog=${DebugLogger.currentLogFilePath().orEmpty()}"
        )
    }

    val database: AppDatabase by lazy {
        Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "rss_reader.db"
        )
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .addMigrations(AppDatabase.MIGRATION_2_3)
            .addMigrations(AppDatabase.MIGRATION_3_4)
            .addMigrations(AppDatabase.MIGRATION_4_5)
            .addMigrations(AppDatabase.MIGRATION_5_6)
            .addMigrations(AppDatabase.MIGRATION_6_7)
            .build()
    }

    val repository: FeedRepository by lazy {
        FeedRepository(
            database = database,
            feedDao = database.feedDao(),
            articleDao = database.articleDao(),
            fetcher = FeedFetcher(),
            parser = FeedParser()
        )
    }

    val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(applicationContext)
    }

    val translationService: TranslationService by lazy {
        GoogleFreeTranslationService()
    }

    val articleTranslationManager: ArticleTranslationManager by lazy {
        ArticleTranslationManager(translationService)
    }

    val refreshScheduler: RefreshScheduler by lazy {
        RefreshScheduler(applicationContext)
    }
}


