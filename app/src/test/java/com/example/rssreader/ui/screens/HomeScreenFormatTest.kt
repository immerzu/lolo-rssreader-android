package com.example.rssreader.ui.screens

import com.example.rssreader.data.repository.OpmlImportResult
import com.example.rssreader.data.repository.RefreshRunStats
import com.example.rssreader.data.repository.RepositoryDiagnosticsSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeScreenFormatTest {

    @Test
    fun shouldShowCompletionNotificationOnlyForManualRefreshFinishedInBackground() {
        assertTrue(
            shouldShowCompletionNotificationForRefresh(
                manualTrigger = true,
                notificationsEnabled = true,
                newArticles = 1,
                isAppInForeground = false
            )
        )
        assertTrue(
            shouldShowCompletionNotificationForRefresh(
                manualTrigger = true,
                notificationsEnabled = true,
                newArticles = 2,
                isAppInForeground = false
            )
        )
        assertEquals(
            false,
            shouldShowCompletionNotificationForRefresh(
                manualTrigger = false,
                notificationsEnabled = true,
                newArticles = 1,
                isAppInForeground = false
            )
        )
        assertEquals(
            false,
            shouldShowCompletionNotificationForRefresh(
                manualTrigger = true,
                notificationsEnabled = true,
                newArticles = 0,
                isAppInForeground = false
            )
        )
        assertEquals(
            false,
            shouldShowCompletionNotificationForRefresh(
                manualTrigger = true,
                notificationsEnabled = false,
                newArticles = 1,
                isAppInForeground = false
            )
        )
        assertEquals(
            false,
            shouldShowCompletionNotificationForRefresh(
                manualTrigger = true,
                notificationsEnabled = true,
                newArticles = 1,
                isAppInForeground = true
            )
        )
    }

    @Test
    fun formatRefreshSummaryIncludesFailuresAndSkipsWhenPresent() {
        assertEquals(
            "Aktualisierung beendet: 9 aktualisiert, 3 neu, 1 Fehler, 2 uebersprungen",
            formatRefreshSummary(
                RefreshRunStats(
                    refreshedFeeds = 9,
                    skippedFeeds = 2,
                    failedFeeds = 1,
                    newArticles = 3
                )
            )
        )
    }

    @Test
    fun formatFeedRefreshSummaryUsesConciseFeedSpecificLabel() {
        assertEquals(
            "Feed aktualisiert: 4 neue Artikel",
            formatFeedRefreshSummary(4)
        )
    }

    @Test
    fun formatImportAndExportSummaryStayExplicit() {
        assertEquals(
            "OPML importiert: 9 importiert, 1 uebersprungen, 2 Fehler (zuerst: https://example.com/failing.xml)",
            formatImportSummary(
                OpmlImportResult(
                    importedFeeds = 9,
                    skippedFeeds = 1,
                    failedFeeds = 2,
                    firstFailedFeedUrl = "https://example.com/failing.xml"
                )
            )
        )
        assertEquals(
            "OPML exportiert: 7 Feeds",
            formatExportSummary(7)
        )
    }

    @Test
    fun formatDeleteSummaryHandlesSingularAndPlural() {
        assertEquals(
            "1 gelesener Eintrag geloescht",
            formatDeleteSummary(
                deletedCount = 1,
                singularLabel = "gelesener Eintrag",
                pluralLabel = "gelesene Eintraege",
                suffix = "geloescht"
            )
        )
        assertEquals(
            "4 Eintraege des Feeds geloescht",
            formatDeleteSummary(
                deletedCount = 4,
                singularLabel = "Eintrag",
                pluralLabel = "Eintraege",
                suffix = "des Feeds geloescht"
            )
        )
    }

    @Test
    fun formatStateChangeSummaryHandlesCounts() {
        assertEquals(
            "1 Artikel als gelesen markiert",
            formatStateChangeSummary(
                affectedCount = 1,
                singularLabel = "Artikel",
                pluralLabel = "Artikel",
                suffix = "als gelesen markiert"
            )
        )
        assertEquals(
            "6 Artikel des Feeds als ungelesen markiert",
            formatStateChangeSummary(
                affectedCount = 6,
                singularLabel = "Artikel des Feeds",
                pluralLabel = "Artikel des Feeds",
                suffix = "als ungelesen markiert"
            )
        )
    }

    @Test
    fun formatDiagnosticsSummaryIncludesKeyMaintenanceFields() {
        val summary = formatDiagnosticsSummary(
            snapshot = RepositoryDiagnosticsSnapshot(
                feedCount = 9,
                articleCount = 254,
                searchIndexRowCount = 254,
                manualFtsMode = true,
                lastRefreshRunStats = RefreshRunStats(
                    refreshedFeeds = 9,
                    skippedFeeds = 0,
                    failedFeeds = 0,
                    newArticles = 0
                ),
                lastImportResult = OpmlImportResult(
                    importedFeeds = 9,
                    skippedFeeds = 0,
                    failedFeeds = 0
                ),
                debugLogFilePath = "/data/user/0/de.lolo.rssreader/files/debug/rss-reader-debug.log"
            ),
            versionLabel = "1.70.02 (124)"
        )

        assert(summary.contains("Version: 1.70.02 (124)"))
        assert(summary.contains("Feeds: 9"))
        assert(summary.contains("Artikel: 254"))
        assert(summary.contains("FTS-Modus: manuell"))
        assert(summary.contains("Letzte Aktualisierung: refreshed=9, failed=0, skipped=0, new=0"))
        assert(summary.contains("Letzter Import: imported=9, skipped=0, failed=0"))
        assert(summary.contains("Debug-Log: /data/user/0/de.lolo.rssreader/files/debug/rss-reader-debug.log"))
    }

    @Test
    fun formatDiagnosticsSummaryIncludesFirstFailedImportFeedWhenPresent() {
        val summary = formatDiagnosticsSummary(
            snapshot = RepositoryDiagnosticsSnapshot(
                feedCount = 9,
                articleCount = 254,
                searchIndexRowCount = 254,
                manualFtsMode = true,
                lastRefreshRunStats = null,
                lastImportResult = OpmlImportResult(
                    importedFeeds = 8,
                    skippedFeeds = 0,
                    failedFeeds = 1,
                    firstFailedFeedUrl = "https://topwar.ru/news/rss.xml"
                ),
                debugLogFilePath = null
            ),
            versionLabel = "1.85.01 (129)"
        )

        assertTrue(summary.contains("Letzter Import: imported=8, skipped=0, failed=1, firstFailed=https://topwar.ru/news/rss.xml"))
    }

    @Test
    fun formatDiagnosticsSummaryShowsMissingRefreshAndImportState() {
        val summary = formatDiagnosticsSummary(
            snapshot = RepositoryDiagnosticsSnapshot(
                feedCount = 2,
                articleCount = 10,
                searchIndexRowCount = 10,
                manualFtsMode = true,
                lastRefreshRunStats = null,
                lastImportResult = null,
                debugLogFilePath = null
            ),
            versionLabel = "1.70.02 (124)"
        )

        assertTrue(summary.contains("Letzte Aktualisierung: keine"))
        assertTrue(summary.contains("Letzter Import: keiner"))
    }
}
