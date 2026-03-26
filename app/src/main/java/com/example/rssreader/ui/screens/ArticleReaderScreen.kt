package com.example.rssreader.ui.screens

import android.content.Intent
import android.net.Uri
import android.text.TextUtils
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.example.rssreader.data.db.ArticleEntity
import com.example.rssreader.data.repository.FeedRepository
import com.example.rssreader.ui.formatRelativeTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleReaderScreen(
    articleId: Long,
    repository: FeedRepository,
    showImages: Boolean,
    articleBodyTextSizeOffset: Int,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val effectiveArticleBodyTextSizeOffset = articleBodyTextSizeOffset.coerceIn(-2, 2)
    var currentArticleId by rememberSaveable { mutableStateOf(articleId) }
    var article by remember { mutableStateOf<ArticleEntity?>(null) }
    var swipeDirection by remember { mutableStateOf(SwipeDirection.None) }
    var hasShownInitialArticle by rememberSaveable { mutableStateOf(false) }
    var showSwipeHint by rememberSaveable { mutableStateOf(false) }
    val defaultBodyTextStyle = MaterialTheme.typography.bodyLarge
    val feedArticlesFlow = remember(repository, article?.feedId) {
        article?.feedId?.let(repository::observeArticles) ?: flowOf(emptyList())
    }
    val feedArticles by feedArticlesFlow.collectAsState(initial = emptyList())
    val fallbackBodyTextStyle = remember(defaultBodyTextStyle, effectiveArticleBodyTextSizeOffset) {
        defaultBodyTextStyle.copy(
            fontSize = (defaultBodyTextStyle.fontSize.value + effectiveArticleBodyTextSizeOffset).sp
        )
    }
    val orderedArticles by remember(feedArticles) {
        derivedStateOf {
            feedArticles.sortedWith(
                compareByDescending<ArticleEntity> { it.publishedAt ?: 0L }
                    .thenByDescending { it.id }
            )
        }
    }
    val currentIndex by remember(article?.id, orderedArticles) {
        derivedStateOf { orderedArticles.indexOfFirst { it.id == article?.id } }
    }
    val newerArticleId by remember(currentIndex, orderedArticles) {
        derivedStateOf {
            if (currentIndex > 0) orderedArticles[currentIndex - 1].id else null
        }
    }
    val olderArticleId by remember(currentIndex, orderedArticles) {
        derivedStateOf {
            if (currentIndex != -1 && currentIndex < orderedArticles.lastIndex) {
                orderedArticles[currentIndex + 1].id
            } else {
                null
            }
        }
    }
    val openInBrowser = { openArticleInBrowser(context, article?.link) }

    LaunchedEffect(articleId) {
        currentArticleId = articleId
    }

    LaunchedEffect(currentArticleId) {
        article = withContext(Dispatchers.IO) { repository.getArticle(currentArticleId) }
        withContext(Dispatchers.IO) { repository.markRead(currentArticleId) }
    }

    LaunchedEffect(article?.id) {
        if (article != null) {
            if (!hasShownInitialArticle) {
                hasShownInitialArticle = true
            }
            showSwipeHint = true
            delay(SWIPE_HINT_DURATION_MS.toLong())
            showSwipeHint = false
        }
    }

    LaunchedEffect(article?.id, swipeDirection) {
        if (article != null && swipeDirection != SwipeDirection.None) {
            delay(ARTICLE_SWITCH_ANIMATION_MS.toLong())
            swipeDirection = SwipeDirection.None
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = article?.title ?: "Artikel",
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
                    if (!article?.link.isNullOrBlank()) {
                        IconButton(onClick = openInBrowser) {
                            Icon(Icons.Default.OpenInBrowser, contentDescription = "Im Browser oeffnen")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (article == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            val colorScheme = MaterialTheme.colorScheme
            Box(modifier = Modifier.fillMaxSize()) {
                AnimatedContent(
                    targetState = article,
                    transitionSpec = {
                        if (!hasShownInitialArticle || initialState == null || targetState == null) {
                            ContentTransform(
                                targetContentEnter = EnterTransition.None,
                                initialContentExit = ExitTransition.None
                            )
                        } else when (swipeDirection) {
                            SwipeDirection.ToOlder -> {
                                slideInHorizontally(
                                    animationSpec = tween(durationMillis = ARTICLE_SWITCH_ANIMATION_MS),
                                    initialOffsetX = { it }
                                ) togetherWith slideOutHorizontally(
                                    animationSpec = tween(durationMillis = ARTICLE_SWITCH_ANIMATION_MS),
                                    targetOffsetX = { -it }
                                )
                            }
                            SwipeDirection.ToNewer -> {
                                slideInHorizontally(
                                    animationSpec = tween(durationMillis = ARTICLE_SWITCH_ANIMATION_MS),
                                    initialOffsetX = { -it }
                                ) togetherWith slideOutHorizontally(
                                    animationSpec = tween(durationMillis = ARTICLE_SWITCH_ANIMATION_MS),
                                    targetOffsetX = { it }
                                )
                            }
                            SwipeDirection.None -> {
                                ContentTransform(
                                    targetContentEnter = EnterTransition.None,
                                    initialContentExit = ExitTransition.None
                                )
                            }
                        }
                    },
                    contentKey = { it?.id ?: -1L },
                    label = "article-swipe"
                ) { currentArticle ->
                    val articleHtml = remember(currentArticle, showImages, colorScheme, effectiveArticleBodyTextSizeOffset) {
                        buildReaderHtml(
                            article = currentArticle,
                            showImages = showImages,
                            colorScheme = colorScheme,
                            articleBodyTextSizeOffset = effectiveArticleBodyTextSizeOffset
                        )
                    }
                    val articleHtmlContent = remember(currentArticle?.link, articleHtml) {
                        articleHtml?.let {
                            ReaderHtmlContent(
                                baseUrl = currentArticle?.link,
                                html = it
                            )
                        }
                    }
                    val fallbackImageUrls = remember(currentArticle?.imageUrls) {
                        currentArticle?.imageUrls
                            ?.split("\n")
                            ?.filter { imageUrl -> imageUrl.isNotBlank() }
                            .orEmpty()
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .pointerInput(newerArticleId, olderArticleId) {
                                var totalHorizontalDrag = 0f
                                detectHorizontalDragGestures(
                                    onDragStart = {
                                        totalHorizontalDrag = 0f
                                        showSwipeHint = false
                                    },
                                    onHorizontalDrag = { _, dragAmount ->
                                        totalHorizontalDrag += dragAmount
                                    },
                                    onDragEnd = {
                                        when {
                                            totalHorizontalDrag >= 80f && newerArticleId != null -> {
                                                scope.launch {
                                                    swipeDirection = SwipeDirection.ToNewer
                                                    currentArticleId = newerArticleId!!
                                                }
                                            }
                                            totalHorizontalDrag <= -80f && olderArticleId != null -> {
                                                scope.launch {
                                                    swipeDirection = SwipeDirection.ToOlder
                                                    currentArticleId = olderArticleId!!
                                                }
                                            }
                                        }
                                    },
                                    onDragCancel = {}
                                )
                            },
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        if (articleHtml != null) {
                            AndroidView(
                                factory = { viewContext ->
                                    WebView(viewContext).apply {
                                        settings.javaScriptEnabled = true
                                        settings.domStorageEnabled = true
                                        settings.cacheMode = WebSettings.LOAD_DEFAULT
                                        settings.loadsImagesAutomatically = true
                                        settings.mediaPlaybackRequiresUserGesture = true
                                        settings.allowFileAccess = false
                                        settings.allowContentAccess = false
                                        settings.javaScriptCanOpenWindowsAutomatically = false
                                        settings.builtInZoomControls = false
                                        settings.displayZoomControls = false
                                        settings.useWideViewPort = true
                                        settings.loadWithOverviewMode = true
                                        overScrollMode = WebView.OVER_SCROLL_NEVER
                                        webViewClient = object : WebViewClient() {
                                            override fun shouldOverrideUrlLoading(
                                                view: WebView?,
                                                request: WebResourceRequest?
                                            ): Boolean {
                                                val targetUri = request?.url
                                                if (targetUri?.scheme == IMAGE_TAP_SCHEME) {
                                                    val articleTarget = targetUri.getQueryParameter("url").orEmpty()
                                                    openArticleInBrowser(viewContext, articleTarget)
                                                    return true
                                                }
                                                val target = targetUri?.toString().orEmpty()
                                                if (request?.isForMainFrame == true && target.isNotBlank()) {
                                                    viewContext.startActivity(
                                                        Intent(Intent.ACTION_VIEW, Uri.parse(target))
                                                    )
                                                    return true
                                                }
                                                return false
                                            }
                                        }
                                    }
                                },
                                update = { webView ->
                                    if (articleHtmlContent != null && webView.tag != articleHtmlContent) {
                                        webView.tag = articleHtmlContent
                                        webView.loadDataWithBaseURL(
                                            articleHtmlContent.baseUrl,
                                            articleHtmlContent.html,
                                            "text/html",
                                            "utf-8",
                                            null
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = currentArticle?.title ?: "",
                                    style = MaterialTheme.typography.headlineSmall
                                )
                                if (currentArticle?.publishedAt != null) {
                                    Text(
                                        text = formatRelativeTime(currentArticle.publishedAt),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = currentArticle?.plainText?.ifBlank { "Kein Textinhalt vorhanden." } ?: "",
                                    style = fallbackBodyTextStyle
                                )
                                if (showImages) {
                                    fallbackImageUrls.forEach { imageUrl ->
                                        AsyncImage(
                                            model = imageUrl,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { openInBrowser() }
                                        )
                                    }
                                } else if (!currentArticle?.imageUrls.isNullOrBlank()) {
                                    Text(
                                        text = "Bilder sind in den Einstellungen deaktiviert.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
                AnimatedVisibility(
                    visible = showSwipeHint,
                    enter = fadeIn(animationSpec = tween(durationMillis = 220)),
                    exit = fadeOut(animationSpec = tween(durationMillis = 280)),
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding()
                        .padding(start = 24.dp, end = 24.dp, bottom = 24.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = "←",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.align(Alignment.BottomStart)
                        )
                        Text(
                            text = "→",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.align(Alignment.BottomEnd)
                        )
                    }
                }
            }
        }
    }
}

private fun buildReaderHtml(
    article: ArticleEntity?,
    showImages: Boolean,
    colorScheme: ColorScheme,
    articleBodyTextSizeOffset: Int
): String? {
    article ?: return null
    val prefersDark = colorScheme.background.luminance() < 0.5f
    val articleBodyFontSizePx = (17 + articleBodyTextSizeOffset.coerceIn(-2, 2)).coerceIn(15, 19)
    val encodedTitle = TextUtils.htmlEncode(article.title)
    val encodedDate = article.publishedAt?.let(::formatRelativeTime)?.let(TextUtils::htmlEncode)
    val rawBody = article.contentHtml.ifBlank {
        article.plainText
            .lineSequence()
            .map(TextUtils::htmlEncode)
            .joinToString(separator = "<br><br>")
    }
    if (rawBody.isBlank()) {
        return null
    }
    val twitterScript = if (
        "twitter-tweet" in rawBody ||
        "twitter.com" in rawBody ||
        "x.com" in rawBody
    ) {
        """<script async src="https://platform.twitter.com/widgets.js" charset="utf-8"></script>"""
    } else {
        ""
    }
    val imageRule = if (showImages) {
        ""
    } else {
        "img, figure { display: none !important; }"
    }
    val imageTapUrl = article.link
        .takeIf { it.isNotBlank() }
        ?.let { "$IMAGE_TAP_SCHEME://open?url=${Uri.encode(it)}" }
        .orEmpty()
    val imageTapScript = if (imageTapUrl.isNotBlank()) {
        """
        <script>
          document.addEventListener('DOMContentLoaded', function() {
            var articleUrl = '$imageTapUrl';
            document.querySelectorAll('img').forEach(function(img) {
              img.addEventListener('click', function(event) {
                event.preventDefault();
                event.stopPropagation();
                window.location.href = articleUrl;
              });
            });
          });
        </script>
        """
    } else {
        ""
    }
    return """
        <!DOCTYPE html>
        <html lang="de">
        <head>
          <meta charset="utf-8" />
          <meta name="viewport" content="width=device-width, initial-scale=1.0" />
          <meta name="color-scheme" content="${if (prefersDark) "dark" else "light"}" />
          <style>
            :root {
              color-scheme: ${if (prefersDark) "dark" else "light"};
              --bg: ${colorScheme.background.toCssColor()};
              --text: ${colorScheme.onBackground.toCssColor()};
              --muted: ${colorScheme.onSurfaceVariant.toCssColor()};
              --accent: ${colorScheme.primary.toCssColor()};
              --surface: ${colorScheme.surfaceVariant.toCssColor()};
              --border: ${colorScheme.outline.toCssColor()};
            }
            * {
              box-sizing: border-box;
            }
            html {
              background: var(--bg);
            }
            body {
              margin: 0;
              padding: 18px 16px 28px;
              background: var(--bg);
              color: var(--text);
              font-family: sans-serif;
              font-size: ${articleBodyFontSizePx}px;
              line-height: 1.7;
              overflow-wrap: anywhere;
              -webkit-text-size-adjust: 100%;
              word-break: break-word;
            }
            a {
              color: var(--accent);
              text-decoration-thickness: 0.08em;
              text-underline-offset: 0.14em;
            }
            h1 {
              margin: 0 0 6px;
              font-size: 22px;
              line-height: 1.16;
            }
            h2, h3 {
              margin: 1.4em 0 0.55em;
              line-height: 1.25;
            }
            h2 {
              font-size: 1.3rem;
            }
            h3 {
              font-size: 1.15rem;
            }
            p {
              margin: 0 0 1em;
              line-height: 1.72;
            }
            ul, ol {
              margin: 0.4em 0 1.1em;
              padding-left: 1.45em;
            }
            li {
              margin: 0.24em 0;
            }
            blockquote {
              margin: 1.1em 0;
              padding: 0.15em 0 0.15em 1em;
              border-left: 4px solid var(--accent);
              color: var(--muted);
              background: color-mix(in srgb, var(--surface) 75%, transparent);
            }
            .meta {
              margin: 0 0 16px;
              color: var(--muted);
              font-size: 0.95rem;
            }
            img, iframe, video, blockquote, twitterwidget {
              max-width: 100% !important;
            }
            img {
              display: block;
              max-width: min(100%, 560px) !important;
              width: auto !important;
              height: auto !important;
              max-height: 300px !important;
              margin: 0.85em auto 1.1em;
              margin-left: auto;
              margin-right: auto;
              border-radius: 14px;
            }
            a[href*="youtube.com"] img,
            a[href*="youtu.be"] img,
            img[src*="ytimg.com"],
            img[src*="youtube.com"],
            img[src*="i.ytimg.com"] {
              max-width: min(100%, 480px) !important;
              max-height: 200px !important;
            }
            figure {
              margin: 1em auto 1.15em;
              max-width: min(100%, 560px);
            }
            figure > img {
              margin-bottom: 0;
            }
            figcaption {
              margin-top: 0.55em;
              color: var(--muted);
              font-size: 0.92rem;
              line-height: 1.45;
            }
            iframe {
              display: block;
              width: min(100%, 560px) !important;
              max-width: 100% !important;
              margin: 1em auto 1.15em;
              margin-left: auto;
              margin-right: auto;
              border: 0;
            }
            iframe[src*="youtube.com"],
            iframe[src*="youtu.be"],
            iframe[src*="youtube-nocookie.com"],
            iframe[src*="player.vimeo.com"],
            video {
              display: block;
              width: min(100%, 560px) !important;
              max-width: 100% !important;
              height: auto !important;
              aspect-ratio: 16 / 9;
              max-height: 240px !important;
              margin: 1em auto 1.15em;
              border: 0;
              border-radius: 14px;
              background: #000;
            }
            table {
              display: block;
              width: 100%;
              max-width: 100%;
              margin: 1.2em 0;
              overflow-x: auto;
              border-collapse: collapse;
              border-spacing: 0;
              white-space: nowrap;
              border: 1px solid var(--border);
              background: color-mix(in srgb, var(--surface) 55%, transparent);
            }
            th, td {
              padding: 0.65em 0.8em;
              border: 1px solid var(--border);
              text-align: left;
              vertical-align: top;
            }
            th {
              color: var(--text);
              background: color-mix(in srgb, var(--surface) 80%, transparent);
            }
            pre, code {
              font-family: "Cascadia Mono", "Consolas", monospace;
            }
            code {
              padding: 0.14em 0.35em;
              border-radius: 6px;
              background: color-mix(in srgb, var(--surface) 88%, transparent);
            }
            pre {
              margin: 1.2em 0;
              padding: 0.95em 1em;
              border-radius: 12px;
              background: color-mix(in srgb, var(--surface) 88%, transparent);
              border: 1px solid var(--border);
              overflow-x: auto;
              white-space: pre-wrap;
              overflow-wrap: anywhere;
            }
            pre code {
              padding: 0;
              background: transparent;
            }
            hr {
              border: 0;
              border-top: 1px solid var(--border);
              margin: 1.5em 0;
            }
            $imageRule
          </style>
          $twitterScript
          $imageTapScript
        </head>
        <body>
          <h1><a href="${TextUtils.htmlEncode(article.link)}">$encodedTitle</a></h1>
          ${encodedDate?.let { """<div class="meta">$it</div>""" } ?: ""}
          $rawBody
        </body>
        </html>
    """.trimIndent()
}

private fun Color.toCssColor(): String {
    val red = (red * 255).toInt().coerceIn(0, 255)
    val green = (green * 255).toInt().coerceIn(0, 255)
    val blue = (blue * 255).toInt().coerceIn(0, 255)
    return "rgb($red, $green, $blue)"
}

private data class ReaderHtmlContent(
    val baseUrl: String?,
    val html: String
)

private fun openArticleInBrowser(context: android.content.Context, articleLink: String?) {
    val targetUri = articleLink
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?.let(Uri::parse)
        ?.takeIf { uri -> !uri.scheme.isNullOrBlank() }
        ?: return

    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, targetUri))
    }
}

private const val IMAGE_TAP_SCHEME = "rssreader-article"
private const val ARTICLE_SWITCH_ANIMATION_MS = 220
private const val SWIPE_HINT_DURATION_MS = 2400

private enum class SwipeDirection {
    None,
    ToNewer,
    ToOlder
}


========================================================================================================================