package de.lolo.rssreader.ui.screens

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
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
 * RSS-AUDIT-005: Rueckkehr aus dem Reader nach erfolgtem Paging.
 *
 * Der Test bildet den echten Navigationspfad nach (Navigation-Compose mit Liste und einem
 * Reader-Stub) und prueft danach Listenposition, Ungelesen-Filter und die bereits
 * nachgeladenen Artikel. Isolation wie bei den uebrigen UI-Tests: In-Memory-Room, kein Netz.
 */
@RunWith(AndroidJUnit4::class)
class ArticleListNavigationUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: FeedRepository

    private var feedId = 0L

    private val articleCount = 150
    private val pageSize = 100
    private val readArticleId = 200

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
                FeedEntity(title = "Navigations-Feed", url = "https://test.invalid/nav")
            )
            database.articleDao().insertAll(
                (1..articleCount).map { id ->
                    article(id.toLong(), articleTitle(id), isRead = false)
                } + listOf(
                    // Neuester Artikel, bereits gelesen: darf bei aktivem Filter nie erscheinen.
                    article(readArticleId.toLong(), "Gelesener-Artikel", isRead = true)
                )
            )
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun articleTitle(id: Int) = "Navigations-Artikel-%03d".format(id)

    private fun article(id: Long, title: String, isRead: Boolean) = ArticleEntity(
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

    private fun scrollUntilIndexIsReachable(index: Int, timeoutMs: Long = 10_000) {
        composeRule.waitUntil(timeoutMs) {
            runCatching {
                composeRule.onNode(hasScrollAction()).performScrollToIndex(index)
                true
            }.getOrDefault(false)
        }
    }

    private fun textExists(text: String): Boolean =
        composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    private fun labelExists(label: String): Boolean =
        composeRule.onAllNodesWithContentDescription(label).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun returnFromReaderKeepsPositionFilterAndLoadedPages() {
        val filterActive = mutableStateOf(true)
        val filterActiveLabel = context.getString(R.string.article_list_filter_show_all_cd)

        composeRule.setContent {
            val navController = rememberNavController()
            RssReaderTheme(themeMode = de.lolo.rssreader.data.settings.ThemeMode.SYSTEM) {
                NavHost(navController = navController, startDestination = "list") {
                    composable("list") {
                        ArticleListScreen(
                            feedId = feedId,
                            repository = repository,
                            settings = AppPreferences(),
                            entrySortOrder = EntrySortOrder.NEWEST_FIRST,
                            showUnreadOnlyPreference = filterActive.value,
                            showUnreadOnlySaveFailed = false,
                            onShowUnreadOnlyChange = { filterActive.value = it },
                            onOpenArticle = { _, _ -> navController.navigate("reader") },
                            onBack = {}
                        )
                    }
                    composable("reader") {
                        Button(onClick = { navController.popBackStack() }) {
                            Text("Reader-Stub")
                        }
                    }
                }
            }
        }

        // Filter aktiv: der gelesene Artikel ist nie sichtbar.
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText(articleTitle(articleCount)).fetchSemanticsNodes().isNotEmpty()
        }
        assertFalse(textExists("Gelesener-Artikel"))
        assertTrue(labelExists(filterActiveLabel))

        // Ans Ende der ersten Seite scrollen und die zweite Seite laden.
        composeRule.onNode(hasScrollAction()).performScrollToIndex(pageSize)
        scrollUntilIndexIsReachable(articleCount)
        composeRule.onNodeWithText(articleTitle(1)).assertIsDisplayed()

        // Artikel oeffnen (echter Navigationsschritt) und wieder zurueckkehren.
        composeRule.onNodeWithText(articleTitle(1)).performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Reader-Stub").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Reader-Stub").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText(articleTitle(1)).fetchSemanticsNodes().isNotEmpty()
        }

        // Position und nachgeladene Seite sind erhalten: der aelteste Artikel steht weiterhin
        // in der Liste, ohne erneut zu scrollen.
        composeRule.onNodeWithText(articleTitle(1)).assertIsDisplayed()
        // Der Filterzustand ist ebenfalls erhalten (Schaltflaeche bietet weiterhin "alle anzeigen").
        assertTrue("Filterzustand muss die Rueckkehr ueberstehen", labelExists(filterActiveLabel))
        assertFalse("Gelesener Artikel darf auch nach der Rueckkehr nicht erscheinen", textExists("Gelesener-Artikel"))
    }
}
