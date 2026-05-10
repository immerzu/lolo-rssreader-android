package de.lolo.rssreader.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.lolo.rssreader.R
import de.lolo.rssreader.data.db.ArticleEntity
import de.lolo.rssreader.data.errors.toUserMessage
import de.lolo.rssreader.data.repository.FeedRepository
import de.lolo.rssreader.data.settings.AppPreferences
import de.lolo.rssreader.data.settings.EntrySortOrder
import de.lolo.rssreader.debug.DebugLogger
import de.lolo.rssreader.ui.formatRelativeTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterialApi::class,
    ExperimentalFoundationApi::class
)
@Composable
fun ArticleListScreen(
    feedId: Long,
    repository: FeedRepository,
    settings: AppPreferences,
    entrySortOrder: EntrySortOrder,
    onOpenArticle: (Long) -> Unit,
    onBack: () -> Unit
) {
    val logTag = "ArticleListScreen"
    val context = LocalContext.current
    val feed by repository.observeFeed(feedId).collectAsState(initial = null)
    val articles by repository.observeArticles(feedId).collectAsState(initial = emptyList())
    val sortedArticles = remember(articles, entrySortOrder) {
        when (entrySortOrder) {
            EntrySortOrder.NEWEST_FIRST -> {
                articles.sortedWith(
                    compareByDescending<ArticleEntity> { it.publishedAt ?: 0L }
                        .thenByDescending { it.id }
                )
            }
            EntrySortOrder.OLDEST_FIRST -> {
                articles.sortedWith(
                    compareBy<ArticleEntity> { it.publishedAt ?: 0L }
                        .thenBy { it.id }
                )
            }
        }
    }
    val unreadCount = remember(articles) { articles.count { !it.isRead } }
    val scope = rememberCoroutineScope()
    var isRefreshing by rememberSaveable { mutableStateOf(false) }
    var showRefreshIndicator by rememberSaveable { mutableStateOf(false) }
    var refreshIndicatorToken by rememberSaveable { mutableIntStateOf(0) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedArticleId by rememberSaveable { mutableStateOf<Long?>(null) }
    var refreshStatusText by rememberSaveable { mutableStateOf<String?>(null) }
    fun showRefreshIndicatorBriefly() {
        val token = refreshIndicatorToken + 1
        refreshIndicatorToken = token
        showRefreshIndicator = true
        refreshStatusText = null
        scope.launch {
            delay(ARTICLE_LIST_REFRESH_INDICATOR_DURATION_MS)
            if (refreshIndicatorToken == token) {
                showRefreshIndicator = false
                if (isRefreshing) {
                    refreshStatusText = context.getString(R.string.home_refresh_running)
                }
            }
        }
    }
    val refreshFeed: () -> Unit = {
        scope.launch {
            if (isRefreshing) {
                return@launch
            }
            if (isDefinitelyOffline(context)) {
                errorMessage = noNetworkConnectionMessage(context)
                return@launch
            }
            if (
                isRefreshBlockedForWifiRequirements(
                    context = context,
                    globalRefreshOnlyOnWifi = settings.refreshOnlyOnWifi,
                    feedWifiOnly = feed?.wifiOnly == true
                )
            ) {
                errorMessage = wifiOnlyRefreshMessage(context)
                return@launch
            }
            isRefreshing = true
            showRefreshIndicatorBriefly()
            DebugLogger.i(logTag, "Feed-Refresh manuell gestartet: feedId=$feedId")
            try {
                runCatching { repository.refreshFeed(feedId) }
                    .onSuccess { inserted ->
                        DebugLogger.i(
                            logTag,
                            "Feed-Refresh erfolgreich: feedId=$feedId, inserted=$inserted"
                        )
                    }
                    .onFailure {
                        if (it !is CancellationException) {
                            DebugLogger.w(logTag, "Feed-Refresh fehlgeschlagen: feedId=$feedId", it)
                            errorMessage = it.toUserMessage(
                                context.getString(R.string.article_list_refresh_failed)
                            )
                        }
                    }
            } finally {
                isRefreshing = false
                refreshStatusText = null
            }
        }
    }
    val pullRefreshState = rememberPullRefreshState(
        refreshing = showRefreshIndicator,
        onRefresh = refreshFeed
    )

    LaunchedEffect(feedId, isRefreshing, showRefreshIndicator, refreshStatusText) {
        DebugLogger.d(
            logTag,
            "state feedId=$feedId, isRefreshing=$isRefreshing, showRefreshIndicator=$showRefreshIndicator, " +
                "refreshStatusText=${refreshStatusText != null}"
        )
    }

    LaunchedEffect(feedId) {
        DebugLogger.i(logTag, "Artikelliste geoeffnet: feedId=$feedId")
        repository.markFeedOpened(feedId)
    }

    DisposableEffect(feedId) {
        DebugLogger.i(logTag, "sichtbar: feedId=$feedId")
        onDispose {
            DebugLogger.i(logTag, "verlassen: feedId=$feedId")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = feed?.customTitle?.ifBlank { null }
                            ?: feed?.title
                            ?: stringResource(R.string.article_default_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            DebugLogger.i(logTag, "Alle Artikel als gelesen: feedId=$feedId")
                            scope.launch {
                                runCatching { repository.markAllRead(feedId) }
                                    .onFailure {
                                        if (it !is CancellationException) {
                                            DebugLogger.w(logTag, "Mark all read fehlgeschlagen: feedId=$feedId", it)
                                            errorMessage = it.toUserMessage(
                                                context.getString(R.string.article_list_mark_failed)
                                            )
                                        }
                                    }
                            }
                        }
                    ) {
                        Icon(
                            Icons.Default.DoneAll,
                            contentDescription = stringResource(R.string.article_list_mark_all_read_cd)
                        )
                    }
                    IconButton(
                        onClick = refreshFeed,
                        enabled = !isRefreshing
                    ) {
                        RefreshActionIcon(
                            isRefreshing = showRefreshIndicator,
                            contentDescription = stringResource(R.string.article_list_refresh_feed_cd)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .pullRefresh(pullRefreshState)
            ) {
                item(key = "article-list-unread-header") {
                    Text(
                        text = pluralStringResource(
                            R.plurals.article_list_unread_count,
                            unreadCount,
                            unreadCount
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
                itemsIndexed(
                    items = sortedArticles,
                    // Defensive keying: einzelne Feed-Quellen koennen unruhige Listenzustaende
                    // ausloesen. Mit Index+ID bleibt der Key innerhalb der aktuellen Liste sicher eindeutig.
                    key = { index, item -> "article-${item.id}-$index" }
                ) { _, item ->
                    val titleColor = if (item.isRead) {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f)
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                    val bodyColor = if (item.isRead) {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
                    }
                    val timeColor = if (item.isRead) {
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
                    } else {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.95f)
                    }
                    val titleStyle = if (item.isRead) {
                        MaterialTheme.typography.titleMedium
                    } else {
                        MaterialTheme.typography.titleMedium.merge(
                            TextStyle(
                                shadow = Shadow(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                                    offset = Offset(0f, 0f),
                                    blurRadius = 12f
                                )
                            )
                        )
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {
                                    DebugLogger.i(
                                        logTag,
                                        "Artikel aus Liste geoeffnet: articleId=${item.id}, feedId=$feedId"
                                    )
                                    onOpenArticle(item.id)
                                },
                                onLongClick = { selectedArticleId = item.id }
                            )
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = item.title,
                                    style = titleStyle,
                                    fontWeight = if (item.isRead) FontWeight.Normal else FontWeight.ExtraBold,
                                    color = titleColor,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Column(
                                modifier = Modifier.padding(start = 6.dp),
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                                    IconButton(
                                        onClick = {
                                            DebugLogger.d(
                                                logTag,
                                                "Favoritenstatus wechseln: articleId=${item.id}, target=${!item.isFavorite}"
                                            )
                                            scope.launch {
                                                runCatching {
                                                    repository.setFavorite(item.id, !item.isFavorite)
                                                }.onFailure {
                                                    if (it !is CancellationException) {
                                                        DebugLogger.w(
                                                            logTag,
                                                            "Favoritenstatus konnte nicht geaendert werden: articleId=${item.id}",
                                                            it
                                                        )
                                                        errorMessage = it.toUserMessage(
                                                            context.getString(R.string.article_list_favorite_failed)
                                                        )
                                                    }
                                                }
                                            }
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        if (item.isFavorite) {
                                            Icon(
                                                Icons.Filled.Star,
                                                contentDescription = stringResource(R.string.article_list_remove_favorite_cd),
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        } else {
                                            Icon(
                                                Icons.Outlined.StarOutline,
                                                contentDescription = stringResource(R.string.article_list_add_favorite_cd),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = formatRelativeTime(item.publishedAt),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = timeColor,
                                    maxLines = 1
                                )
                            }
                        }
                        if (item.plainText.isNotBlank()) {
                            Text(
                                text = item.plainText.take(220),
                                style = MaterialTheme.typography.bodyMedium,
                                color = bodyColor,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    HorizontalDivider()
                }
            }
            PullRefreshIndicator(
                refreshing = showRefreshIndicator,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter)
            )
            if (refreshStatusText != null && !showRefreshIndicator) {
                Text(
                    text = refreshStatusText!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp)
                )
            }
        }
    }

    errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            title = { Text(stringResource(R.string.common_error)) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { errorMessage = null }) {
                    Text(stringResource(R.string.common_ok))
                }
            }
        )
    }

    selectedArticleId?.let { articleId ->
        val selectedArticle = sortedArticles.firstOrNull { it.id == articleId }
        if (selectedArticle != null) {
            AlertDialog(
                onDismissRequest = { selectedArticleId = null },
                title = { Text(stringResource(R.string.article_list_mark_dialog_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(selectedArticle.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            selectedArticleId = null
                            scope.launch {
                                runCatching { repository.markRead(articleId) }
                                    .onFailure {
                                        if (it !is CancellationException) {
                                            errorMessage = it.toUserMessage(
                                                context.getString(R.string.article_list_mark_read_failed)
                                            )
                                        }
                                    }
                            }
                        },
                        enabled = !selectedArticle.isRead
                    ) {
                        Text(stringResource(R.string.article_list_mark_read_action))
                    }
                },
                dismissButton = {
                    Row {
                        TextButton(
                            onClick = {
                                selectedArticleId = null
                                scope.launch {
                                runCatching { repository.markUnread(articleId) }
                                    .onFailure {
                                        if (it !is CancellationException) {
                                            errorMessage = it.toUserMessage(
                                                context.getString(R.string.article_list_mark_unread_failed)
                                            )
                                        }
                                    }
                                }
                            },
                            enabled = selectedArticle.isRead
                        ) {
                            Text(stringResource(R.string.article_list_mark_unread_action))
                        }
                        TextButton(onClick = { selectedArticleId = null }) {
                            Text(stringResource(R.string.common_cancel))
                        }
                    }
                }
            )
        } else {
            selectedArticleId = null
        }
    }
}

private const val ARTICLE_LIST_REFRESH_INDICATOR_DURATION_MS = 700L

