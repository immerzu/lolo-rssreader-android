package de.lolo.rssreader.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.lolo.rssreader.R
import de.lolo.rssreader.data.db.FeedSummary
import de.lolo.rssreader.debug.DebugLogger
import de.lolo.rssreader.ui.formatFeedUpdatedAt
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import java.net.URI

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FeedListScreen(
    feeds: List<FeedSummary>,
    feedsLoaded: Boolean,
    isRefreshing: Boolean,
    isMoveMode: Boolean,
    onOpenFeed: (Long) -> Unit,
    onOpenFeedMenu: (Long) -> Unit,
    onMoveFeedUp: (Long) -> Unit,
    onMoveFeedDown: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(feedsLoaded, feeds.size, isRefreshing, isMoveMode) {
        DebugLogger.d(
            "FeedListScreen",
            "state feedsLoaded=$feedsLoaded, feedCount=${feeds.size}, isRefreshing=$isRefreshing, isMoveMode=$isMoveMode"
        )
    }

    if (!feedsLoaded) {
        LaunchedEffect(Unit) {
            DebugLogger.i("FeedListScreen", "Ladezustand gerendert, weil feedsLoaded=false")
        }
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircularProgressIndicator()
                Text(
                    stringResource(R.string.feed_list_loading),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
        return
    }

    if (feeds.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(stringResource(R.string.feed_list_empty_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.feed_list_empty_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    LazyColumn(modifier = modifier.fillMaxSize()) {
        if (isMoveMode) {
            item(key = "move-mode-hint") {
                Text(
                    text = stringResource(R.string.feed_list_move_mode_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
        }
        itemsIndexed(feeds, key = { _, feed -> feed.id }) { index, feed ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = {
                            if (!isMoveMode) {
                                DebugLogger.i(
                                    "FeedListScreen",
                                    "Feed aus Uebersicht angeklickt: feedId=${feed.id}, title=${feed.displayTitle}"
                                )
                                onOpenFeed(feed.id)
                            }
                        },
                        onLongClick = {
                            if (!isMoveMode) {
                                DebugLogger.i(
                                    "FeedListScreen",
                                    "Feed-Menue per Long-Click: feedId=${feed.id}, title=${feed.displayTitle}"
                                )
                                onOpenFeedMenu(feed.id)
                            }
                        }
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isMoveMode) {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)
                        } else {
                            Color.Transparent
                        }
                    )
                    .padding(horizontal = if (isMoveMode) 8.dp else 0.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top
            ) {
                FeedIcon(
                    title = feed.displayTitle,
                    iconUrl = resolveFeedIconUrl(feed),
                    alpha = if (feed.isUnreadFeed) 1f else 0.58f,
                    modifier = Modifier.padding(top = 1.dp)
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    val feedTitleColor = if (feed.isUnreadFeed) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f)
                    }
                    val feedMetaColor = if (feed.isUnreadFeed) {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.92f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.48f)
                    }
                    val updatedLabel = stringResource(R.string.feed_list_updated_label)
                    val unreadSuffix = stringResource(R.string.feed_list_unread_suffix)
                    val wifiLabel = stringResource(R.string.feed_list_wifi_label)
                    val syncLabel = stringResource(R.string.feed_list_sync_label)
                    Text(
                        text = feed.displayTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (feed.isUnreadFeed) FontWeight.SemiBold else FontWeight.Normal,
                        color = feedTitleColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = buildString {
                            append(updatedLabel)
                            append(formatFeedUpdatedAt(feed.lastFetchedAt))
                            append(", ")
                            append(feed.unreadArticles)
                            append("/")
                            append(feed.totalArticles)
                            append(' ')
                            append(unreadSuffix)
                            if (feed.wifiOnly) {
                                append(' ')
                                append(wifiLabel)
                            }
                            if (isRefreshing) {
                                append(' ')
                                append(syncLabel)
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = feedMetaColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (isMoveMode) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.UnfoldMore,
                            contentDescription = "Verschiebbar",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp)
                        )
                        IconButton(
                            onClick = { onMoveFeedUp(feed.id) },
                            enabled = index > 0,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowUpward,
                                contentDescription = "Feed nach oben"
                            )
                        }
                        IconButton(
                            onClick = { onMoveFeedDown(feed.id) },
                            enabled = index < feeds.lastIndex,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowDownward,
                                contentDescription = "Feed nach unten"
                            )
                        }
                    }
                }
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun FeedIcon(
    title: String,
    iconUrl: String?,
    alpha: Float,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(6.dp)

    Surface(
        modifier = modifier
            .size(22.dp)
            .alpha(alpha),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        if (iconUrl.isNullOrBlank()) {
            FeedIconFallback(title = title)
        } else {
            SubcomposeAsyncImage(
                model = iconUrl,
                contentDescription = "$title Icon",
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape),
                loading = {
                    FeedIconFallback(title = title)
                },
                error = {
                    FeedIconFallback(title = title)
                },
                success = {
                    SubcomposeAsyncImageContent(modifier = Modifier.fillMaxSize())
                }
            )
        }
    }
}

@Composable
private fun FeedIconFallback(title: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title.trim().firstOrNull()?.uppercase() ?: "?",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun resolveFeedIconUrl(feed: FeedSummary): String? {
    return when {
        !feed.iconUrl.isNullOrBlank() -> feed.iconUrl
        else -> buildFaviconUrl(feed.siteUrl ?: feed.url)
    }
}

private fun buildFaviconUrl(sourceUrl: String?): String? {
    val value = sourceUrl?.trim().orEmpty()
    if (value.isBlank()) {
        return null
    }

    return runCatching {
        val uri = URI(value)
        val scheme = uri.scheme ?: "https"
        val host = uri.host ?: return null
        "$scheme://$host/favicon.ico"
    }.getOrNull()
}


