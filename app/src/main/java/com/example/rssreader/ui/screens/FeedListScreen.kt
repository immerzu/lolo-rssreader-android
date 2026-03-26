package com.example.rssreader.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.rssreader.data.db.FeedSummary
import com.example.rssreader.ui.formatFeedUpdatedAt
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import java.net.URI

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FeedListScreen(
    feeds: List<FeedSummary>,
    isRefreshing: Boolean,
    onOpenFeed: (Long) -> Unit,
    onOpenFeedMenu: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
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
                Text("Noch keine Feeds angelegt.", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Lege oben rechts einen Feed an und aktualisiere ihn von dort.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    LazyColumn(modifier = modifier.fillMaxSize()) {
        items(feeds, key = { it.id }) { feed ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { onOpenFeed(feed.id) },
                        onLongClick = { onOpenFeedMenu(feed.id) }
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp),
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
                        Color.White
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f)
                    }
                    val feedMetaColor = if (feed.isUnreadFeed) {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.92f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.48f)
                    }
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
                            append("Aktualisiert: ")
                            append(formatFeedUpdatedAt(feed.lastFetchedAt))
                            append(", ")
                            append(feed.unreadArticles)
                            append("/")
                            append(feed.totalArticles)
                            append(" ungelesen")
                            if (feed.wifiOnly) {
                                append("  WLAN")
                            }
                            if (isRefreshing) {
                                append("  Sync...")
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = feedMetaColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
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
