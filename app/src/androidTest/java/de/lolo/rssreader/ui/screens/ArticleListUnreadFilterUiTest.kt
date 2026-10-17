package de.lolo.rssreader.ui.screens

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.lolo.rssreader.R
import de.lolo.rssreader.data.db.AppDatabase
import de.lolo.rssreader.data.db.ArticleEntity
import de.lolo.rssreader.data.db.FeedEntity
import de.lolo.rssreader.data.network.FeedFetcher
import de.lolo.rssreader.data.network.FeedParser
import de.lolo.rssreader.data.repository.FeedRepository
import de.lolo.rssreader.data.settings.AppPreferences
import de.lolo.rssreader.data.settings.EntrySortOrder
import de.lolo.rssreader.data.settings.SettingsRepository
import de.lolo.rssreader.data.settings.ThemeMode
import de.lolo.rssreader.ui.theme.RssReaderTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * RSS-002 Phase 2B: instrumentierte UI-Tests des **feedbezogenen** Ungelesen-Filters
 * (GitHub Issue #12).
 *
 * Isolation (vollstaendig, keine produktiven Nutzerdaten):
 * - **Room:** ausschliesslich `Room.inMemoryDatabaseBuilder` — es wird nie eine
 *   Datei-Datenbank geoeffnet (kein `rss_reader.db`).
 * - **DataStore:** eigener, testbezogener Store ueber `PreferenceDataStoreFactory.create(...)`
 *   in einer temporaeren Datei unter `context.cacheDir` (eigener Scope; [tearDown] beendet ihn
 *   und loescht die Datei). Der produktive Store `rss_reader_settings` wird nicht beruehrt.
 * - **Netzwerk:** es wird nie `refreshFeed`/`refreshAll` aufgerufen; `FeedFetcher` erhaelt einen
 *   `OkHttpClient`, der in diesen Tests nicht benutzt wird.
 *
 * Synchronisation ohne Sleeps: UI-Zustand ueber `waitUntil` (Compose-Idling), DataStore-
 * Schreibvorgaenge ueber den beobachtbaren Flow (`first { … }` mit `withTimeout`).
 *
 * **Test 3** verwendet eine neue [SettingsRepository]-Instanz auf demselben Store. Das belegt
 * die Persistenz im Store — **nicht** einen echten App-Prozessneustart.
 */
@RunWith(AndroidJUnit4::class)
class ArticleListUnreadFilterUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: FeedRepository
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var dataStoreScope: CoroutineScope
    private lateinit var dataStoreFile: File
    private lateinit var settings: SettingsRepository

    private var feedAId = 0L
    private var feedBId = 0L

    private val titleAUngelesen = "A-ungelesen"
    private val titleAGelesen = "A-gelesen"
    private val titleBUngelesen = "B-ungelesen"
    private val titleBGelesen = "B-gelesen"

    private val filterInactiveLabel: String
        get() = context.getString(R.string.article_list_filter_unread_only_cd)
    private val filterActiveLabel: String
        get() = context.getString(R.string.article_list_filter_show_all_cd)

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = FeedRepository(
            database = database,
            feedDao = database.feedDao(),
            articleDao = database.articleDao(),
            fetcher = FeedFetcher(OkHttpClient()),
            parser = FeedParser()
        )

        dataStoreFile = File(context.cacheDir, "ui_filter_test_${System.nanoTime()}.preferences_pb")
        dataStoreScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        dataStore = PreferenceDataStoreFactory.create(scope = dataStoreScope) { dataStoreFile }
        settings = SettingsRepository(context, dataStore)

        runBlocking {
            feedAId = database.feedDao().insert(
                FeedEntity(title = "Feed A", url = "https://test.invalid/feed-a")
            )
            feedBId = database.feedDao().insert(
                FeedEntity(title = "Feed B", url = "https://test.invalid/feed-b")
            )
            database.articleDao().insertAll(
                listOf(
                    article(feedAId, "a-unread", titleAUngelesen, isRead = false),
                    article(feedAId, "a-read", titleAGelesen, isRead = true),
                    article(feedBId, "b-unread", titleBUngelesen, isRead = false),
                    article(feedBId, "b-read", titleBGelesen, isRead = true)
                )
            )
        }
    }

    @After
    fun tearDown() {
        database.close()
        dataStoreScope.cancel()
        dataStoreFile.delete()
    }

    // ── Test 1: Aktivieren und Deaktivieren ─────────────────────────────────

    @Test
    fun unreadFilter_toggleOnAndOff_showsCorrectArticlesAndLabels() {
        setArticleListScreen(feedAId)

        // Ausgangszustand: beide Artikel sichtbar, Schaltflaeche bietet "nur ungelesene" an.
        waitForText(titleAGelesen)
        waitForText(titleAUngelesen)
        assertTrue("Filter-Schaltflaeche muss im Ausgangszustand 'nur ungelesene' anbieten",
            labelExists(filterInactiveLabel))

        // Aktivieren ueber die echte UI.
        composeRule.onNodeWithContentDescription(filterInactiveLabel).performClick()

        // Nur der ungelesene Artikel bleibt sichtbar; Beschriftung wechselt.
        waitUntilTextGone(titleAGelesen)
        waitForText(titleAUngelesen)
        waitForLabel(filterActiveLabel)
        assertFalse("Gelesener Artikel darf bei aktivem Filter nicht sichtbar sein",
            textExists(titleAGelesen))
        // Persistenz: der Wert muss tatsaechlich im (Test-)Store ankommen.
        awaitStoredFilter(feedAId, true)

        // Deaktivieren.
        composeRule.onNodeWithContentDescription(filterActiveLabel).performClick()

        waitForText(titleAGelesen)
        waitForText(titleAUngelesen)
        waitForLabel(filterInactiveLabel)
        awaitStoredFilter(feedAId, false)
    }

    // ── Test 2: Unabhaengigkeit der Feeds ───────────────────────────────────

    @Test
    fun unreadFilter_isIndependentPerFeed_whenSwitchingFeeds() {
        var currentFeedId by mutableStateOf(feedAId)
        composeRule.setContent { ArticleListUnderTest(currentFeedId) }

        // Feed A: beide Artikel sichtbar -> filtern.
        waitForText(titleAGelesen)
        waitForText(titleAUngelesen)
        composeRule.onNodeWithContentDescription(filterInactiveLabel).performClick()
        waitUntilTextGone(titleAGelesen)
        awaitStoredFilter(feedAId, true)

        // Wechsel zu Feed B (neue feedId = neuer Screen-Zustand).
        composeRule.runOnUiThread { currentFeedId = feedBId }

        // Feed B ist ungefiltert: beide B-Artikel sichtbar, Beschriftung "nur ungelesene".
        waitForText(titleBGelesen)
        waitForText(titleBUngelesen)
        waitForLabel(filterInactiveLabel)
        assertFalse("A-Artikel duerfen in Feed B nicht erscheinen", textExists(titleAUngelesen))
        // Kein Filterzustand uebertragen.
        assertEquals(false, storedFilter(feedBId))

        // Zurueck zu Feed A: Filter dort unveraendert aktiv.
        composeRule.runOnUiThread { currentFeedId = feedAId }
        waitUntilTextGone(titleAGelesen)
        waitForText(titleAUngelesen)
        waitForLabel(filterActiveLabel)
        assertEquals(true, storedFilter(feedAId))
    }

    // ── Test 3: Dauerhafte Speicherung (neue Repository-Instanz) ────────────

    @Test
    fun unreadFilter_isRestoredByNewRepositoryInstanceOnSameStore() {
        // Zustaende ueber die oeffentliche API setzen (A aktiv, B inaktiv).
        writeFilterAndAwait(feedAId, true)
        writeFilterAndAwait(feedBId, false)
        assertEquals(true, storedFilter(feedAId))
        assertEquals(false, storedFilter(feedBId))

        // Neue Repository-Instanz auf demselben Test-Store (KEIN Prozessneustart).
        val restored = SettingsRepository(context, dataStore)
        assertEquals(true, readFilter(restored, feedAId))
        assertEquals(false, readFilter(restored, feedBId))

        // UI mit der neuen Instanz: Feed A zeigt nur den ungelesenen Artikel.
        var currentFeedId by mutableStateOf(feedAId)
        composeRule.setContent {
            RssReaderTheme(themeMode = ThemeMode.SYSTEM) {
                val feedId = currentFeedId
                val preference by restored.unreadOnlyFilterForFeed(feedId)
                    .collectAsState(initial = null)
                ArticleListScreen(
                    feedId = feedId,
                    repository = repository,
                    settings = AppPreferences(),
                    entrySortOrder = EntrySortOrder.NEWEST_FIRST,
                    showUnreadOnlyPreference = preference,
                    showUnreadOnlySaveFailed = false,
                    onShowUnreadOnlyChange = { value -> writeFilter(feedId, value) },
                    onOpenArticle = {},
                    onBack = {}
                )
            }
        }

        waitUntilTextGone(titleAGelesen)
        waitForText(titleAUngelesen)
        waitForLabel(filterActiveLabel)

        // Feed B bleibt ungefiltert (beide Artikel sichtbar).
        composeRule.runOnUiThread { currentFeedId = feedBId }
        waitForText(titleBGelesen)
        waitForText(titleBUngelesen)
        waitForLabel(filterInactiveLabel)
    }

    // ── Test-Komposition ────────────────────────────────────────────────────

    private fun setArticleListScreen(feedId: Long) {
        composeRule.setContent { ArticleListUnderTest(feedId) }
    }

    /** Bildet denselben Pfad wie `RssReaderApp`: Store-Flow -> Screen -> Store. */
    @Composable
    private fun ArticleListUnderTest(feedId: Long) {
        val preference by settings.unreadOnlyFilterForFeed(feedId)
            .collectAsState(initial = null)
        RssReaderTheme(themeMode = ThemeMode.SYSTEM) {
            ArticleListScreen(
                feedId = feedId,
                repository = repository,
                settings = AppPreferences(),
                entrySortOrder = EntrySortOrder.NEWEST_FIRST,
                showUnreadOnlyPreference = preference,
                showUnreadOnlySaveFailed = false,
                onShowUnreadOnlyChange = { value -> writeFilter(feedId, value) },
                onOpenArticle = {},
                onBack = {}
            )
        }
    }

    // ── Synchronisation / Persistenz ────────────────────────────────────────

    private fun writeFilter(feedId: Long, value: Boolean) {
        dataStoreScope.launch { settings.setUnreadOnlyFilterForFeed(feedId, value) }
    }

    private fun writeFilterAndAwait(feedId: Long, value: Boolean) {
        runBlocking {
            withTimeout(10_000) {
                settings.setUnreadOnlyFilterForFeed(feedId, value)
                settings.unreadOnlyFilterForFeed(feedId).first { it == value }
            }
        }
    }

    private fun awaitStoredFilter(feedId: Long, expected: Boolean) {
        runBlocking {
            withTimeout(10_000) {
                settings.unreadOnlyFilterForFeed(feedId).first { it == expected }
            }
        }
    }

    private fun storedFilter(feedId: Long): Boolean = readFilter(settings, feedId)

    private fun readFilter(repository: SettingsRepository, feedId: Long): Boolean {
        return runBlocking {
            withTimeout(10_000) { repository.unreadOnlyFilterForFeed(feedId).first() }
        }
    }

    // ── Compose-Abfragen ────────────────────────────────────────────────────

    private fun textExists(text: String): Boolean =
        composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    private fun labelExists(label: String): Boolean =
        composeRule.onAllNodesWithContentDescription(label).fetchSemanticsNodes().isNotEmpty()

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = 10_000) { textExists(text) }
        composeRule.onNodeWithText(text).assertIsDisplayed()
    }

    private fun waitUntilTextGone(text: String) {
        composeRule.waitUntil(timeoutMillis = 10_000) { !textExists(text) }
    }

    private fun waitForLabel(label: String) {
        composeRule.waitUntil(timeoutMillis = 10_000) { labelExists(label) }
    }

    // ── Testdaten ───────────────────────────────────────────────────────────

    private fun article(feedId: Long, uniqueKey: String, title: String, isRead: Boolean): ArticleEntity {
        return ArticleEntity(
            id = 0L,
            feedId = feedId,
            uniqueKey = uniqueKey,
            title = title,
            link = "https://test.invalid/$uniqueKey",
            publishedAt = 1_700_000_000_000L,
            plainText = "$title Text",
            contentHtml = "<p>$title Text</p>",
            imageUrls = "",
            isRead = isRead,
            isFavorite = false
        )
    }
}
