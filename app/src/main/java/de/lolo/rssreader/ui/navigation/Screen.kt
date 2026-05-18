package de.lolo.rssreader.ui.navigation

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Search : Screen("search")
    data object Settings : Screen("settings")
    data object AddFeed : Screen("feeds/new")
    data object EditFeed : Screen("feeds/{feedId}/edit") {
        fun create(feedId: Long) = "feeds/$feedId/edit"
    }

    data object ArticleList : Screen("articles/{feedId}") {
        fun create(feedId: Long) = "articles/$feedId"
    }

    data object Reader : Screen("reader/{articleId}") {
        fun create(articleId: Long) = "reader/$articleId"
    }
}



