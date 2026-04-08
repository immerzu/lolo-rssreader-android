package com.example.rssreader.data.repository

import com.example.rssreader.data.db.AppDatabase
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val legacyFtsTriggerNames = listOf("articles_fts_ai", "articles_fts_au", "articles_fts_ad")

internal class FtsMaintenance(
    private val database: AppDatabase,
    private val onManualModeEnabled: (Int) -> Unit
) {
    @Volatile
    private var manualModeEnsured = false
    private val mutex = Mutex()

    suspend fun ensureManualMode() {
        if (manualModeEnsured) {
            return
        }
        mutex.withLock {
            if (manualModeEnsured) {
                return
            }
            val droppedTriggers = disableLegacyFtsMaintenanceTriggers(database)
            manualModeEnsured = true
            onManualModeEnabled(droppedTriggers)
        }
    }
}

internal fun disableLegacyFtsMaintenanceTriggers(database: AppDatabase): Int {
    val writableDatabase = database.openHelper.writableDatabase
    var existingTriggers = 0
    // Supported databases should already be clean after MIGRATION_7_8. This is only a
    // defensive fallback for unexpected legacy trigger state, without changing search semantics.
    writableDatabase.query(
        """
        SELECT name
        FROM sqlite_master
        WHERE type = 'trigger'
          AND name IN (${legacyFtsTriggerNames.joinToString(",") { "'$it'" }})
        """.trimIndent()
    ).use { cursor ->
        while (cursor.moveToNext()) {
            existingTriggers += 1
        }
    }
    legacyFtsTriggerNames.forEach { triggerName ->
        writableDatabase.execSQL("DROP TRIGGER IF EXISTS $triggerName")
    }
    return existingTriggers
}

