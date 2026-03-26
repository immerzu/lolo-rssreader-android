package com.example.rssreader.ui.screens

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
import androidx.compose.material.icons.filled.GTranslate
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.rssreader.data.db.ArticleEntity
import com.example.rssreader.data.errors.toUserMessage
import com.example.rssreader.data.repository.FeedRepository
import com.example.rssreader.data.settings.EntrySortOrder
import com.example.rssreader.data.translation.ArticleTranslationManager
import com.example.rssreader.data.translation.shouldOfferTranslation
import com.example.rssreader.data.translation.translationSourceText
import com.example.rssreader.debug.DebugLogger
import com.example.rssreader.ui.model.TranslationUiState
import com.example.rssreader.ui.formatRelativeTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterialApi::class,
    ExperimentalFoundationApi::class
)
@Composable
fun ArticleListScreen(
    feedId: Long,
    repository: FeedRepository,
    translationManager: ArticleTranslationManager,
    entrySortOrder: EntrySortOrder,
    onOpenArticle: (Long) -> Unit,
    onBack: () -> Unit
) {
    val logTag = "ArticleListScreen"
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
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedArticleId by rememberSaveable { mutableStateOf<Long?>(null) }
    val previewTranslationStates by translationManager.states.collectAsState(initial = emptyMap())
    val translationTargetLanguage = remember { defaultPreviewTranslationTargetLanguage() }
    val refreshFeed: () -> Unit = {
        scope.launch {
            isRefreshing = true
            DebugLogger.i(logTag, "Feed-Refresh manuell gestartet: feedId=$feedId")
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
                        errorMessage = it.toUserMessage("Feed konnte nicht aktualisiert werden.")
                    }
                }
            isRefreshing = false
        }
    }
    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = refreshFeed
    )

    LaunchedEffect(feedId) {
        DebugLogger.i(logTag, "Artikelliste geoeffnet: feedId=$feedId")
        repository.markFeedOpened(feedId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = feed?.customTitle?.ifBlank { null } ?: feed?.title ?: "Artikel",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurueck")
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
                                            errorMessage = it.toUserMessage("Artikel konnten nicht markiert werden.")
                                        }
                                    }
                            }
                        }
                    ) {
                        Icon(Icons.Default.DoneAll, contentDescription = "Alle als gelesen markieren")
                    }
                    IconButton(
                        onClick = refreshFeed,
                        enabled = !isRefreshing
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Feed aktualisieren")
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
                        text = "$unreadCount ungelesen",
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
                    val previewTranslationState =
                        previewTranslationStates[item.id] ?: TranslationUiState.Idle
                    val translatedPreview = previewTranslationState as? TranslationUiState.Success
                    val previewTitle = translatedPreview?.result?.translatedTitle
                        ?.ifBlank { item.title }
                        ?: item.title
                    val previewBody = translatedPreview?.result?.translatedBody
                        ?.ifBlank { item.plainText }
                        ?: item.plainText
                    val showTranslateButton =
                        previewTranslationState is TranslationUiState.Success ||
                            shouldOfferTranslation(
                                title = item.title,
                                body = item.translationSourceText()
                            )
                    val titleColor = if (item.isRead) {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f)
                    } else {
                        Color.White
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
                                    text = previewTitle,
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
                                    if (showTranslateButton) {
                                        IconButton(
                                            onClick = {
                                                if (previewTranslationState is TranslationUiState.Loading) {
                                                    return@IconButton
                                                }
                                                if (previewTranslationState is TranslationUiState.Success) {
                                                    DebugLogger.d(
                                                        logTag,
                                                        "Vorschau-Uebersetzung verworfen: articleId=${item.id}"
                                                    )
                                                    translationManager.clearArticle(item.id)
                                                    return@IconButton
                                                }
                                                DebugLogger.i(
                                                    logTag,
                                                    "Vorschau-Uebersetzung angefordert: articleId=${item.id}, feedId=$feedId"
                                                )
                                                scope.launch {
                                                    val translationState = translationManager.translateArticle(
                                                        articleId = item.id,
                                                        title = item.title,
                                                        body = item.translationSourceText(),
                                                        targetLanguage = translationTargetLanguage
                                                    )
                                                    if (translationState is TranslationUiState.Error) {
                                                        DebugLogger.w(
                                                            logTag,
                                                            "Vorschau-Uebersetzung fehlgeschlagen: articleId=${item.id}"
                                                        )
                                                        errorMessage = translationState.message
                                                    }
                                                }
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            if (previewTranslationState is TranslationUiState.Loading) {
                                                CircularProgressIndicator(
                                                    strokeWidth = 2.dp,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            } else {
                                                Icon(
                                                    Icons.Default.GTranslate,
                                                    contentDescription = if (previewTranslationState is TranslationUiState.Success) {
                                                        "Originalvorschau anzeigen"
                                                    } else {
                                                        "Vorschau mit Google uebersetzen"
                                                    },
                                                    tint = if (previewTranslationState is TranslationUiState.Success) {
                                                        MaterialTheme.colorScheme.primary
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurfaceVariant
                                                    },
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
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
                                                        errorMessage = it.toUserMessage("Favorit konnte nicht geaendert werden.")
                                                    }
                                                }
                                            }
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        if (item.isFavorite) {
                                            Icon(
                                                Icons.Filled.Star,
                                                contentDescription = "Favorit entfernen",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        } else {
                                            Icon(
                                                Icons.Outlined.StarOutline,
                                                contentDescription = "Als Favorit markieren",
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
                        if (previewBody.isNotBlank()) {
                            Text(
                                text = previewBody.take(220),
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
                refreshing = isRefreshing,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }

    errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            title = { Text("Fehler") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { errorMessage = null }) {
                    Text("OK")
                }
            }
        )
    }

    selectedArticleId?.let { articleId ->
        val selectedArticle = sortedArticles.firstOrNull { it.id == articleId }
        if (selectedArticle != null) {
            AlertDialog(
                onDismissRequest = { selectedArticleId = null },
                title = { Text("Beitrag markieren") },
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
                                            errorMessage = it.toUserMessage("Beitrag konnte nicht als gelesen markiert werden.")
                                        }
                                    }
                            }
                        },
                        enabled = !selectedArticle.isRead
                    ) {
                        Text("Gelesen")
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
                                            errorMessage = it.toUserMessage("Beitrag konnte nicht als ungelesen markiert werden.")
                                        }
                                    }
                                }
                            },
                            enabled = selectedArticle.isRead
                        ) {
                            Text("Ungelesen")
                        }
                        TextButton(onClick = { selectedArticleId = null }) {
                            Text("Abbrechen")
                        }
                    }
                }
            )
        } else {
            selectedArticleId = null
        }
    }
}

private fun defaultPreviewTranslationTargetLanguage(): String {
    return Locale.getDefault().language
        .trim()
        .ifBlank { "de" }
}


