package de.lolo.rssreader.ui.screens

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.lolo.rssreader.data.db.AppDatabase
import de.lolo.rssreader.data.db.ArticleEntity
import de.lolo.rssreader.data.db.FeedEntity
import de.lolo.rssreader.data.network.FeedFetcher
import de.lolo.rssreader.data.network.FeedParser
import de.lolo.rssreader.data.repository.FeedRepository
import de.lolo.rssreader.data.settings.AppPreferences
import de.lolo.rssreader.data.settings.EntrySortOrder
import de.lolo.rssreader.data.settings.ThemeMode
import de.lolo.rssreader.ui.theme.RssReaderTheme
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * RSS-AUDIT-004: instrumentierte Regressionstests fuer Paging und UI-Zustand der
 * Artikelliste. Isolation wie bei [ArticleListUnreadFilterUiTest]: ausschliesslich
 * In-Memory-Room, kein Netzwerk, keine produktiven Nutzerdaten.
 *
 * Ausfuehrung nur auf einem freigegebenen Geraet (bernd), da instrumentierte Tests
 * eine Test-APK-Installation benoetigen; auf alice blockiert MIUI diese Installation.
 */
@RunWith(AndroidJUnit4::class)
class ArticleListPagingUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: FeedRepository

    private var feedId = 0L
    private var otherFeedId = 0L

    private val articleCount = 150
    private val pageSize = 100

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

        runBlocking {
            feedId = database.feedDao().insert(
                FeedEntity(title = "Paging-Feed", url = "https://test.invalid/paging")
            )
            otherFeedId = database.feedDao().insert(
                FeedEntity(title = "Fremdfeed", url = "https://test.invalid/other")
            )
            database.articleDao().insertAll(
                (1..articleCount).map { id ->
                    article(
                        feedId = feedId,
                        id = id.toLong(),
                        title = articleTitle(id),
                        isRead = false
                    )
                } + (1..3).map { id ->
                    article(
                        feedId = otherFeedId,
                        id = (1_000 + id).toLong(),
                        title = "Fremdfeed-Artikel-$id",
                        isRead = false
                    )
                }
            )
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun articleTitle(id: Int) = "Paging-Artikel-%03d".format(id)

    private fun article(feedId: Long, id: Long, title: String, isRead: Boolean) = ArticleEntity(
        id = id,
        feedId = feedId,
        uniqueKey = "key-$id",
        title = title,
        link = "https://test.invalid/article/$id",
        publishedAt = id,
        plainText = "Vorschautext zu $title",
        contentHtml = "<p>Vorschautext zu $title</p>",
        imageUrls = "",
        isRead = isRead
    )

    private fun setArticleListScreen(feed: Long) {
        composeRule.setContent {
            RssReaderTheme(themeMode = ThemeMode.SYSTEM) {
                ArticleListScreen(
                    feedId = feed,
                    repository = repository,
                    settings = AppPreferences(),
                    entrySortOrder = EntrySortOrder.NEWEST_FIRST,
                    showUnreadOnlyPreference = false,
                    showUnreadOnlySaveFailed = false,
                    onShowUnreadOnlyChange = {},
                    onOpenArticle = { _, _ -> },
                    onBack = {}
                )
            }
        }
    }

    /** Scrollt ans Ende der geladenen Seite und wartet, bis der naechste Seitenbereich existiert. */
    private fun scrollUntilIndexIsReachable(index: Int, timeoutMs: Long = 10_000) {
        composeRule.waitUntil(timeoutMs) {
            runCatching {
                composeRule.onNode(hasScrollAction()).performScrollToIndex(index)
                true
            }.getOrDefault(false)
        }
    }

    @Test
    fun paging_loadsFurtherArticlesWhenScrollingToTheEnd() {
        setArticleListScreen(feedId)

        // Erste Seite (100 Artikel, neueste zuerst): der aelteste ist noch nicht geladen.
        composeRule.onNodeWithText(articleTitle(articleCount)).assertIsDisplayed()
        assertFalse(
            "Artikel ausserhalb der ersten Seite darf noch nicht im Baum sein",
            textExists(articleTitle(1))
        )

        // Ans Ende der ersten Seite scrollen; der Trigger laedt die zweite Seite.
        composeRule.onNode(hasScrollAction()).performScrollToIndex(pageSize)
        scrollUntilIndexIsReachable(articleCount)

        composeRule.onNodeWithText(articleTitle(1)).assertIsDisplayed()
    }

    @Test
    fun stateRestoration_keepsScrollPositionAfterLoadingIsDone() {
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent {
            RssReaderTheme(themeMode = ThemeMode.SYSTEM) {
                ArticleListScreen(
                    feedId = feedId,
                    repository = repository,
                    settings = AppPreferences(),
                    entrySortOrder = EntrySortOrder.NEWEST_FIRST,
                    showUnreadOnlyPreference = false,
                    showUnreadOnlySaveFailed = false,
                    onShowUnreadOnlyChange = {},
                    onOpenArticle = { _, _ -> },
                    onBack = {}
                )
            }
        }

        // Position 30 im geladenen Bereich ansteuern (Artikel 121 von 150).
        composeRule.onNode(hasScrollAction()).performScrollToIndex(30)
        composeRule.waitForIdle()
        composeRule.onNodeWithText(articleTitle(121)).assertIsDisplayed()

        // Prozess-/Konfigurationswechsel simulieren (Rotation).
        restorationTester.emulateSavedInstanceStateRestore()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(articleTitle(121)).assertIsDisplayed()
        assertFalse(
            "Nach der Wiederherstellung darf die Liste nicht auf die Startposition springen",
            textExists(articleTitle(articleCount))
        )
    }

    @Test
    fun feedSwitch_showsOnlyTheSelectedFeed() {
        var currentFeed by mutableStateOf(feedId)
        composeRule.setContent {
            RssReaderTheme(themeMode = ThemeMode.SYSTEM) {
                ArticleListScreen(
                    feedId = currentFeed,
                    repository = repository,
                    settings = AppPreferences(),
                    entrySortOrder = EntrySortOrder.NEWEST_FIRST,
                    showUnreadOnlyPreference = false,
                    showUnreadOnlySaveFailed = false,
                    onShowUnreadOnlyChange = {},
                    onOpenArticle = { _, _ -> },
                    onBack = {}
                )
            }
        }

        composeRule.onNodeWithText(articleTitle(articleCount)).assertIsDisplayed()

        composeRule.runOnUiThread { currentFeed = otherFeedId }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Fremdfeed-Artikel-1").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("Fremdfeed-Artikel-1").assertIsDisplayed()
        assertFalse(
            "Nach dem Feedwechsel duerfen keine Artikel des vorherigen Feeds erscheinen",
            textExists(articleTitle(articleCount))
        )
        assertTrue(textExists("Fremdfeed-Artikel-3"))
    }

    private fun textExists(text: String): Boolean =
        composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
}
