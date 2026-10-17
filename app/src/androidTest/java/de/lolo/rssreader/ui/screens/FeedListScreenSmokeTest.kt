package de.lolo.rssreader.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import de.lolo.rssreader.R
import de.lolo.rssreader.data.settings.ThemeMode
import de.lolo.rssreader.ui.theme.RssReaderTheme
import org.junit.Rule
import org.junit.Test

/**
 * RSS-002 Phase 2A: minimaler, deterministischer Compose-Smoke-Test.
 *
 * Prueft echtes RSS-Reader-UI-Verhalten: Ist die Feed-Liste geladen und leer, zeigt
 * [FeedListScreen] den definierten Leerzustand (Titel + Untertitel).
 *
 * Isolation: Es werden ausschliesslich kontrollierte Parameter uebergeben
 * (`feeds = emptyList()`, `feedsLoaded = true`). Kein FeedRepository, kein Room,
 * kein DataStore, kein Netzwerk, keine Benutzerdaten.
 */
class FeedListScreenSmokeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun feedList_whenLoadedAndEmpty_showsEmptyState() {
        // Erwartete Texte ueber die vorhandenen Ressourcen ermitteln (sprachunabhaengig).
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val expectedTitle = context.getString(R.string.feed_list_empty_title)
        val expectedSubtitle = context.getString(R.string.feed_list_empty_subtitle)

        composeRule.setContent {
            RssReaderTheme(themeMode = ThemeMode.SYSTEM) {
                FeedListScreen(
                    feeds = emptyList(),
                    feedsLoaded = true,
                    isRefreshing = false,
                    isMoveMode = false,
                    onOpenFeed = {},
                    onOpenFeedMenu = {},
                    onMoveFeedUp = {},
                    onMoveFeedDown = {}
                )
            }
        }

        composeRule.onNodeWithText(expectedTitle).assertIsDisplayed()
        composeRule.onNodeWithText(expectedSubtitle).assertIsDisplayed()
    }
}
