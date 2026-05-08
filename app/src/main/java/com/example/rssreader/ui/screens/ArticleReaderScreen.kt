package com.example.rssreader.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri

import android.text.format.DateUtils
import android.text.TextUtils
import android.util.Log
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.RenderProcessGoneDetail
import android.webkit.CookieManager
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.example.rssreader.R
import com.example.rssreader.data.db.ArticleNavigationEntry
import com.example.rssreader.data.db.ArticleEntity
import com.example.rssreader.data.repository.FeedRepository
import com.example.rssreader.debug.DebugLogger
import java.io.ByteArrayInputStream
import java.util.WeakHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URI
import kotlin.math.abs

@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
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
    var currentArticleId by rememberSaveable { mutableLongStateOf(articleId) }
    var article by remember { mutableStateOf<ArticleEntity?>(null) }
    var isLoadingArticle by rememberSaveable { mutableStateOf(true) }
    var swipeDirection by remember { mutableStateOf(SwipeDirection.None) }
    var hasShownInitialArticle by rememberSaveable { mutableStateOf(false) }
    var showSwipeHint by rememberSaveable { mutableStateOf(false) }
    var activeWebView by remember { mutableStateOf<WebView?>(null) }
    var webViewFailed by remember(currentArticleId) { mutableStateOf(false) }
    val webSwipeTracker = remember { WebSwipeTracker() }
    val defaultBodyTextStyle = MaterialTheme.typography.bodyLarge
    val articleNavigationFlow = remember(repository, article?.feedId) {
        article?.feedId?.let(repository::observeArticleNavigation) ?: flowOf(emptyList())
    }
    val articleNavigation by articleNavigationFlow.collectAsState(initial = emptyList())
    val fallbackBodyTextStyle = remember(defaultBodyTextStyle, effectiveArticleBodyTextSizeOffset) {
        defaultBodyTextStyle.copy(
            fontSize = (defaultBodyTextStyle.fontSize.value + effectiveArticleBodyTextSizeOffset).sp
        )
    }
    val currentIndex by remember(article?.id, articleNavigation) {
        derivedStateOf { articleNavigation.indexOfFirst { it.id == article?.id } }
    }
    val newerArticleId by remember(currentIndex, articleNavigation) {
        derivedStateOf {
            if (currentIndex > 0) articleNavigation[currentIndex - 1].id else null
        }
    }
    val olderArticleId by remember(currentIndex, articleNavigation) {
        derivedStateOf {
            if (currentIndex != -1 && currentIndex < articleNavigation.lastIndex) {
                articleNavigation[currentIndex + 1].id
            } else {
                null
            }
        }
    }
    val openInBrowser = { openArticleInBrowser(context, article?.link) }
    val triggerSwipe: (SwipeDirection) -> Unit = { direction ->
        when (direction) {
            SwipeDirection.ToNewer -> {
                if (newerArticleId != null) {
                    scope.launch {
                        swipeDirection = SwipeDirection.ToNewer
                        currentArticleId = newerArticleId!!
                    }
                }
            }
            SwipeDirection.ToOlder -> {
                if (olderArticleId != null) {
                    scope.launch {
                        swipeDirection = SwipeDirection.ToOlder
                        currentArticleId = olderArticleId!!
                    }
                }
            }
            SwipeDirection.None -> Unit
        }
    }

    LaunchedEffect(articleId) {
        currentArticleId = articleId
    }

    LaunchedEffect(currentArticleId) {
        isLoadingArticle = true
        webViewFailed = false
        DebugLogger.i(TAG, "Artikel wird geladen: articleId=$currentArticleId")
        val loadedArticle = withContext(Dispatchers.IO) { repository.getArticle(currentArticleId) }
        article = loadedArticle
        if (loadedArticle != null) {
            DebugLogger.i(TAG, "Artikel geladen: articleId=${loadedArticle.id}, title=${loadedArticle.title.take(80)}")
            withContext(Dispatchers.IO) { repository.markRead(currentArticleId) }
        } else {
            DebugLogger.w(TAG, "Artikel nicht gefunden: articleId=$currentArticleId")
        }
        isLoadingArticle = false
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

    DisposableEffect(Unit) {
        onDispose {
            destroyWebView(activeWebView)
            activeWebView = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = article?.title ?: stringResource(R.string.article_default_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    Row {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.common_back)
                            )
                        }
                    }
                },
                actions = {
                    if (!article?.link.isNullOrBlank()) {
                        IconButton(onClick = openInBrowser) {
                            Icon(
                                Icons.Default.OpenInBrowser,
                                contentDescription = stringResource(R.string.article_reader_open_browser_cd)
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (isLoadingArticle && article == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (article == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.article_reader_not_found),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
                                (
                                    slideInHorizontally(
                                        animationSpec = tween(durationMillis = ARTICLE_SWITCH_ANIMATION_MS),
                                        initialOffsetX = { it }
                                    ) + fadeIn(
                                        animationSpec = tween(durationMillis = ARTICLE_SWITCH_FADE_IN_MS)
                                    )
                                ) togetherWith (
                                    slideOutHorizontally(
                                        animationSpec = tween(durationMillis = ARTICLE_SWITCH_ANIMATION_MS),
                                        targetOffsetX = { -it }
                                    ) + fadeOut(
                                        animationSpec = tween(durationMillis = ARTICLE_SWITCH_FADE_OUT_MS)
                                    )
                                )
                            }
                            SwipeDirection.ToNewer -> {
                                (
                                    slideInHorizontally(
                                        animationSpec = tween(durationMillis = ARTICLE_SWITCH_ANIMATION_MS),
                                        initialOffsetX = { -it }
                                    ) + fadeIn(
                                        animationSpec = tween(durationMillis = ARTICLE_SWITCH_FADE_IN_MS)
                                    )
                                ) togetherWith (
                                    slideOutHorizontally(
                                        animationSpec = tween(durationMillis = ARTICLE_SWITCH_ANIMATION_MS),
                                        targetOffsetX = { it }
                                    ) + fadeOut(
                                        animationSpec = tween(durationMillis = ARTICLE_SWITCH_FADE_OUT_MS)
                                    )
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
                    val readerLoadProfile = remember(currentArticle?.id, currentArticle?.contentHtml) {
                        analyzeReaderLoad(currentArticle)
                    }
                    LaunchedEffect(currentArticle?.id, readerLoadProfile) {
                        if (currentArticle != null && readerLoadProfile.isHeavy) {
                            Log.d(
                                TAG,
                                "Heavy article detected: articleId=${currentArticle.id}, " +
                                    "reasons=${readerLoadProfile.reasonSummary}, " +
                                    "fallback=${readerLoadProfile.useFallback}"
                            )
                        }
                    }
                    val articleHtml = remember(currentArticle, showImages, colorScheme, effectiveArticleBodyTextSizeOffset) {
                        buildReaderHtml(
                            article = currentArticle,
                            showImages = showImages,
                            colorScheme = colorScheme,
                            articleBodyTextSizeOffset = effectiveArticleBodyTextSizeOffset,
                            loadProfile = readerLoadProfile
                        )
                    }
                    val articleHtmlContent = remember(currentArticle?.link, articleHtml, readerLoadProfile) {
                        articleHtml?.let {
                            ReaderHtmlContent(
                                baseUrl = currentArticle?.link,
                                html = it,
                                requiresJavaScript = it.requiresReaderJavaScript(),
                                loadProfile = readerLoadProfile
                            )
                        }
                    }
                    val shouldUseWebView = remember(articleHtmlContent) {
                        articleHtmlContent?.shouldUseWebView() == true
                    }
                    val fallbackScrollState = rememberScrollState()
                    val fallbackImageUrls = remember(currentArticle?.imageUrls, readerLoadProfile) {
                        currentArticle?.imageUrls
                            ?.split("\n")
                            ?.filter { imageUrl ->
                                imageUrl.isNotBlank() && imageUrl.looksLikeDirectImageUrl()
                            }
                            ?.let { imageUrls ->
                                if (readerLoadProfile.isHeavy) {
                                    imageUrls.take(READER_HEAVY_ARTICLE_MAX_IMAGES)
                                } else {
                                    imageUrls
                                }
                            }
                            .orEmpty()
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .then(
                                if (shouldUseWebView && !webViewFailed) {
                                    Modifier
                                } else {
                                    Modifier.fallbackSwipe(
                                        newerArticleId = newerArticleId,
                                        olderArticleId = olderArticleId,
                                        onSwipeStart = { showSwipeHint = false },
                                        onSwipeTrigger = triggerSwipe
                                    )
                                }
                            ),
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        if (shouldUseWebView && !webViewFailed) {
                            AndroidView(
                                factory = { viewContext ->
                                    WebView(viewContext).apply {
                                        activeWebView = this
                                        DebugLogger.i(
                                            TAG,
                                            "WebView erstellt: articleId=${currentArticle?.id?.toString().orEmpty()}"
                                        )
                                        setRendererPriorityPolicy(
                                            WebView.RENDERER_PRIORITY_BOUND,
                                            true
                                        )
                                        configureReaderWebViewSettings(
                                            webView = this,
                                            requiresJavaScript = false
                                        )
                                        overScrollMode = WebView.OVER_SCROLL_NEVER
                                        // Touch-Beobachtung direkt am nativen WebView, damit
                                        // normales Lesen nicht von einer Compose-Wischgeste
                                        // versehentlich als Artikelwechsel gewertet wird.
                                        setOnTouchListener { _, motionEvent ->
                                            when (motionEvent.actionMasked) {
                                                MotionEvent.ACTION_DOWN -> {
                                                    webSwipeTracker.startX = motionEvent.x
                                                    webSwipeTracker.startY = motionEvent.y
                                                    webSwipeTracker.deltaX = 0f
                                                    webSwipeTracker.deltaY = 0f
                                                    showSwipeHint = false
                                                    false
                                                }

                                                MotionEvent.ACTION_MOVE -> {
                                                    webSwipeTracker.deltaX = motionEvent.x - webSwipeTracker.startX
                                                    webSwipeTracker.deltaY = motionEvent.y - webSwipeTracker.startY
                                                    false
                                                }

                                                MotionEvent.ACTION_UP -> {
                                                    val swipeResult = determineHorizontalSwipeDirection(
                                                        totalHorizontalDrag = webSwipeTracker.deltaX,
                                                        totalVerticalDrag = webSwipeTracker.deltaY,
                                                        canGoNewer = newerArticleId != null,
                                                        canGoOlder = olderArticleId != null
                                                    )

                                                    if (swipeResult != SwipeDirection.None) {
                                                        triggerSwipe(swipeResult)
                                                    }
                                                    webSwipeTracker.reset()
                                                    swipeResult != SwipeDirection.None
                                                }

                                                MotionEvent.ACTION_CANCEL -> {
                                                    webSwipeTracker.reset()
                                                    false
                                                }

                                                else -> false
                                            }
                                        }
                                        webViewClient = object : WebViewClient() {
                                            override fun shouldInterceptRequest(
                                                view: WebView?,
                                                request: WebResourceRequest?
                                            ): WebResourceResponse? {
                                                if (articleHtmlContent?.requiresJavaScript == true) {
                                                    return super.shouldInterceptRequest(view, request)
                                                }
                                                if (
                                                    shouldBlockReaderSubresource(
                                                        targetUri = request?.url,
                                                        isMainFrame = request?.isForMainFrame == true
                                                    )
                                                ) {
                                                    DebugLogger.w(
                                                        TAG,
                                                        "WebView Subresource blockiert: articleId=${currentArticle?.id?.toString().orEmpty()}, uri=${request?.url?.toString().orEmpty()}"
                                                    )
                                                    return createBlockedSubresourceResponse()
                                                }
                                                return super.shouldInterceptRequest(view, request)
                                            }

                                            override fun onPageFinished(view: WebView?, url: String?) {
                                                super.onPageFinished(view, url)
                                                DebugLogger.d(
                                                    TAG,
                                                    "WebView Seite geladen: articleId=${currentArticle?.id?.toString().orEmpty()}, url=${url.orEmpty()}"
                                                )
                                            }

                                            override fun shouldOverrideUrlLoading(
                                                view: WebView?,
                                                request: WebResourceRequest?
                                            ): Boolean {
                                                return handleExternalNavigation(
                                                    context = viewContext,
                                                    targetUri = request?.url,
                                                    isMainFrame = request?.isForMainFrame == true
                                                )
                                            }

                                            override fun onRenderProcessGone(
                                                view: WebView?,
                                                detail: RenderProcessGoneDetail?
                                            ): Boolean {
                                                DebugLogger.w(
                                                    TAG,
                                                    "WebView Renderprozess beendet: articleId=${currentArticle?.id?.toString().orEmpty()}, crashed=${detail?.didCrash() == true}"
                                                )
                                                if (activeWebView === view) {
                                                    activeWebView = null
                                                }
                                                destroyWebView(view)
                                                webViewFailed = true
                                                return true
                                            }

                                            override fun onReceivedError(
                                                view: WebView?,
                                                request: WebResourceRequest?,
                                                error: WebResourceError?
                                            ) {
                                                super.onReceivedError(view, request, error)
                                        if (request?.isForMainFrame == true) {
                                            DebugLogger.w(
                                                TAG,
                                                "WebView Fehler im Hauptframe: articleId=${currentArticle?.id?.toString().orEmpty()}, code=${error?.errorCode}"
                                            )
                                            webViewFailed = true
                                        }
                                    }

                                            override fun onReceivedHttpError(
                                                view: WebView?,
                                                request: WebResourceRequest?,
                                                errorResponse: WebResourceResponse?
                                            ) {
                                                super.onReceivedHttpError(view, request, errorResponse)
                                        if (request?.isForMainFrame == true) {
                                            DebugLogger.w(
                                                TAG,
                                                "WebView HTTP-Fehler im Hauptframe: articleId=${currentArticle?.id?.toString().orEmpty()}, code=${errorResponse?.statusCode}"
                                            )
                                            webViewFailed = true
                                        }
                                    }

                                            @Deprecated("Deprecated in WebView, but still used on some devices.")
                                            override fun shouldOverrideUrlLoading(
                                                view: WebView?,
                                                url: String?
                                            ): Boolean {
                                                return handleExternalNavigation(
                                                    context = viewContext,
                                                    targetUri = url?.let(Uri::parse),
                                                    isMainFrame = true
                                                )
                                            }

                                            @Deprecated("Deprecated on older WebView versions.")
                                            override fun onReceivedError(
                                                view: WebView?,
                                                errorCode: Int,
                                                description: String?,
                                                failingUrl: String?
                                            ) {
                                                super.onReceivedError(view, errorCode, description, failingUrl)
                                                if (!failingUrl.isNullOrBlank() && failingUrl == view?.url) {
                                                    DebugLogger.w(
                                                        TAG,
                                                        "Legacy WebView Fehler: articleId=${currentArticle?.id?.toString().orEmpty()}, code=$errorCode, url=$failingUrl"
                                                    )
                                                    webViewFailed = true
                                                }
                                            }
                                        }
                                    }
                                },
                                update = { webView ->
                                    if (articleHtmlContent != null && webView.tag != articleHtmlContent) {
                                        webViewFailed = false
                                        webView.tag = articleHtmlContent
                                        configureReaderWebViewSettings(
                                            webView = webView,
                                            requiresJavaScript = articleHtmlContent.requiresJavaScript
                                        )
                                        webView.resumeTimers()
                                        webView.onResume()
                                        DebugLogger.i(
                                            TAG,
                                            "WebView Inhalt geladen: articleId=${currentArticle?.id?.toString().orEmpty()}, heavy=${articleHtmlContent.loadProfile.isHeavy}, js=${articleHtmlContent.requiresJavaScript}"
                                        )
                                        webView.loadDataWithBaseURL(
                                            resolveReaderWebViewBaseUrl(articleHtmlContent.baseUrl),
                                            articleHtmlContent.html,
                                            "text/html",
                                            "utf-8",
                                            null
                                        )
                                    }
                                },
                                onRelease = { releasedWebView ->
                                    if (activeWebView === releasedWebView) {
                                        activeWebView = null
                                    }
                                    destroyWebView(releasedWebView)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                            )
                        } else {
                            ReaderFallbackContent(
                                article = currentArticle,
                                showImages = showImages,
                                fallbackImageUrls = fallbackImageUrls,
                                bodyTextStyle = fallbackBodyTextStyle,
                                scrollState = fallbackScrollState,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                onOpenArticle = openInBrowser
                            )
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
    articleBodyTextSizeOffset: Int,
    loadProfile: ReaderLoadProfile
): String? {
    article ?: return null
    val prefersDark = colorScheme.background.luminance() < 0.5f
    val articleBodyFontSizePx = (17 + articleBodyTextSizeOffset.coerceIn(-2, 2)).coerceIn(15, 19)
    val encodedTitle = TextUtils.htmlEncode(article.title)
    val encodedDate = article.publishedAt
        ?.let { formatReaderRelativeTime(it) }
        ?.let(TextUtils::htmlEncode)
    val imageMaxWidthPx = if (loadProfile.isHeavy) 460 else 560
    val imageMaxHeightPx = if (loadProfile.isHeavy) 220 else 300
    val videoMaxHeightPx = if (loadProfile.isHeavy) 180 else 240
    val heavyMediaContainRule = if (loadProfile.isHeavy) {
        """
        img, figure, iframe, video {
          content-visibility: auto;
          contain-intrinsic-size: 240px 180px;
        }
        """
    } else {
        ""
    }
    val rawBody = article.contentHtml.ifBlank {
        article.plainText
            .lineSequence()
            .map(TextUtils::htmlEncode)
            .joinToString(separator = "<br><br>")
    }.normalizeReaderBodyHtml(loadProfile)
    if (rawBody.isBlank()) {
        return null
    }
    val twitterScript = ""
    val imageRule = if (showImages) {
        ""
    } else {
        "img, figure { display: none !important; }"
    }
    val articleTapUrl = article.link
        .takeIf { it.isNotBlank() }
        ?.let { "$IMAGE_TAP_SCHEME://open?url=${Uri.encode(it)}" }
        .orEmpty()
    val imageTapScript = if (articleTapUrl.isNotBlank()) {
        """
        <script>
          document.addEventListener('DOMContentLoaded', function() {
            var articleUrl = '$articleTapUrl';
            document.querySelectorAll('img').forEach(function(img) {
              img.loading = 'lazy';
              img.decoding = 'async';
              img.addEventListener('click', function(event) {
                event.preventDefault();
                event.stopPropagation();
                window.location.href = articleUrl;
              });
            });
            document.querySelectorAll('iframe').forEach(function(frame) {
              frame.loading = 'lazy';
            });
            // Force the title link through the same external-open path.
            var titleLink = document.querySelector('h1 a');
            if (titleLink) {
              titleLink.addEventListener('click', function(event) {
                event.preventDefault();
                event.stopPropagation();
                window.location.href = articleUrl;
              });
            }
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
              margin: 0 0 3px;
              font-size: 19px;
              line-height: 1.12;
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
              max-width: min(100%, ${imageMaxWidthPx}px) !important;
              width: auto !important;
              height: auto !important;
              max-height: ${imageMaxHeightPx}px !important;
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
              width: min(100%, ${imageMaxWidthPx}px) !important;
              max-width: 100% !important;
              height: auto !important;
              aspect-ratio: 16 / 9;
              max-height: ${videoMaxHeightPx}px !important;
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
            $heavyMediaContainRule
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
    val html: String,
    val requiresJavaScript: Boolean,
    val loadProfile: ReaderLoadProfile
)

internal fun resolveReaderWebViewBaseUrl(articleBaseUrl: String?): String {
    val resolvedArticleBaseUrl = articleBaseUrl
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?.let { runCatching { URI(it) }.getOrNull() }
        ?.takeIf { uri ->
            when (uri.scheme?.lowercase()) {
                "http", "https" -> true
                else -> false
            }
        }
        ?.toString()

    return resolvedArticleBaseUrl ?: READER_WEBVIEW_BASE_URL
}

private fun ReaderHtmlContent.shouldUseWebView(): Boolean {
    return !loadProfile.useFallback &&
        (requiresJavaScript || readerComplexHtmlRegex.containsMatchIn(html))
}

internal fun sanitizeReaderBodyHtmlForTest(
    html: String,
    isHeavy: Boolean = false
): String = html.normalizeReaderBodyHtml(ReaderLoadProfile(isHeavy = isHeavy))

private fun String.normalizeReaderBodyHtml(loadProfile: ReaderLoadProfile): String {
    // Reader-HTML bleibt lesbar, aber fremde Inline-Skripte und Feed-eigene Styles
    // sollen die WebView nicht unnötig belasten oder das Layout destabilisieren.
    val normalizedHtml = readerAccessibilityAttributeRegex.replace(
        readerMetadataAttributeRegex.replace(
        readerInteractionAttributeRegex.replace(
        readerLegacyImageMapAttributeRegex.replace(
        readerResponsiveImageAttributeRegex.replace(
        readerDangerousStyleAttributeRegex.replace(
        readerInlineEventHandlerRegex.replace(
            readerFormControlTagRegex.replace(
                readerFormTagRegex.replace(
                    readerLegacyPresentationTagRegex.replace(
                    readerLegacyMiscTagRegex.replace(
                        readerAppletTagRegex.replace(
                            readerLegacyFrameTagRegex.replace(
                                readerPluginEmbedTagRegex.replace(
                                    readerTemplateTagRegex.replace(
                                        readerNoscriptTagRegex.replace(
                                            readerStyleTagRegex.replace(
                                                readerMetaTagRegex.replace(
                                                    readerLinkTagRegex.replace(
                                                        readerBaseTagRegex.replace(
                                                            readerScriptTagRegex.replace(this, ""),
                                                            ""
                                                        ),
                                                        ""
                                                    ),
                                                    ""
                                                ),
                                                ""
                                            ),
                                            ""
                                        ),
                                        ""
                                    ),
                                    ""
                                ),
                                ""
                            ),
                            ""
                        ),
                        ""
                    ),
                    ""
                    ),
                    ""
                ),
                ""
            ),
            ""
        ),
        ""
        ),
        ""
    ),
    ""
    ),
    ""
    ),
    ""
    ),
    ""
    )
    if (!loadProfile.isHeavy) {
        return normalizedHtml
    }

    return readerHeavyMediaTagRegex
        .replace(normalizedHtml, "")
        .limitReaderImages(READER_HEAVY_ARTICLE_MAX_IMAGES)
}

private fun String.requiresReaderJavaScript(): Boolean {
    return contains(IMAGE_TAP_SCHEME) || contains("platform.twitter.com/widgets.js")
}

private fun analyzeReaderLoad(article: ArticleEntity?): ReaderLoadProfile {
    val html = article?.contentHtml.orEmpty()
    if (html.isBlank()) {
        return ReaderLoadProfile()
    }

    val htmlLength = html.length
    val imageCount = readerImgTagRegex.findAll(html).count()
    val mediaEmbedCount = readerHeavyMediaTagRegex.findAll(html).count()
    val reasons = buildList {
        if (htmlLength >= READER_HEAVY_HTML_LENGTH_THRESHOLD) {
            add("html=$htmlLength")
        }
        if (imageCount >= READER_HEAVY_IMAGE_COUNT_THRESHOLD) {
            add("img=$imageCount")
        }
        if (mediaEmbedCount >= READER_HEAVY_MEDIA_COUNT_THRESHOLD) {
            add("media=$mediaEmbedCount")
        }
    }

    val useFallback =
        htmlLength >= READER_FALLBACK_HTML_LENGTH_THRESHOLD ||
            mediaEmbedCount >= READER_FALLBACK_MEDIA_COUNT_THRESHOLD ||
            (
                htmlLength >= READER_HEAVY_HTML_LENGTH_THRESHOLD &&
                    imageCount >= READER_FALLBACK_IMAGE_COUNT_THRESHOLD
                )

    return ReaderLoadProfile(
        isHeavy = reasons.isNotEmpty(),
        useFallback = useFallback,
        htmlLength = htmlLength,
        imageCount = imageCount,
        mediaEmbedCount = mediaEmbedCount,
        reasonSummary = reasons.joinToString(", ")
    )
}

private fun String.limitReaderImages(maxImages: Int): String {
    if (maxImages <= 0) {
        return readerImgTagRegex.replace(this, "")
    }
    var keptImages = 0
    return readerImgTagRegex.replace(this) {
        if (keptImages < maxImages) {
            keptImages += 1
            it.value
        } else {
            ""
        }
    }
}

private fun String.looksLikeDirectImageUrl(): Boolean {
    val normalizedUrl = trim()
    if (
        !normalizedUrl.startsWith("https://", ignoreCase = true) &&
        !normalizedUrl.startsWith("http://", ignoreCase = true)
    ) {
        return false
    }

    val lowerPath = substringBefore('#')
        .substringBefore('?')
        .lowercase()

    return lowerPath.endsWith(".jpg") ||
        lowerPath.endsWith(".jpeg") ||
        lowerPath.endsWith(".png") ||
        lowerPath.endsWith(".webp") ||
        lowerPath.endsWith(".gif") ||
        lowerPath.endsWith(".bmp") ||
        lowerPath.endsWith(".svg") ||
        lowerPath.endsWith(".avif")
}

@Composable
private fun ReaderFallbackContent(
    article: ArticleEntity?,
    showImages: Boolean,
    fallbackImageUrls: List<String>,
    bodyTextStyle: androidx.compose.ui.text.TextStyle,
    scrollState: androidx.compose.foundation.ScrollState,
    modifier: Modifier = Modifier,
    onOpenArticle: () -> Unit
) {
    val compactHeaderTextStyle = MaterialTheme.typography.titleMedium.copy(
        fontSize = 17.sp,
        lineHeight = 19.sp
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = article?.title.orEmpty(),
            style = compactHeaderTextStyle,
            modifier = Modifier.clickable(onClick = onOpenArticle)
        )
        if (article?.publishedAt != null) {
            Text(
                text = formatReaderRelativeTime(
                    article.publishedAt,
                    stringResource(R.string.common_never)
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = resolveReaderFallbackBodyText(
                article = article,
                emptyTextLabel = stringResource(R.string.article_reader_no_text)
            ),
            style = bodyTextStyle
        )
        if (showImages) {
            fallbackImageUrls.forEach { imageUrl ->
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenArticle() }
                )
            }
        } else if (!article?.imageUrls.isNullOrBlank()) {
            Text(
                text = stringResource(R.string.article_reader_images_disabled),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun handleExternalNavigation(
    context: android.content.Context,
    targetUri: Uri?,
    isMainFrame: Boolean
): Boolean {
    if (normalizeReaderNavigationScheme(targetUri?.scheme) == IMAGE_TAP_SCHEME) {
        openArticleInBrowser(context, targetUri?.getQueryParameter("url"))
        return true
    }
    if (!isMainFrame || targetUri == null) {
        return false
    }
    if (!isReaderExternallyOpenableScheme(targetUri.scheme)) {
        DebugLogger.w(TAG, "Reader-Navigation blockiert: uri=${targetUri}")
        return true
    }
    openArticleInBrowser(context, targetUri.toString())
    return true
}

private fun shouldBlockReaderSubresource(targetUri: Uri?, isMainFrame: Boolean): Boolean {
    return shouldBlockReaderSubresourceScheme(
        scheme = targetUri?.scheme,
        isMainFrame = isMainFrame
    )
}

internal fun shouldBlockReaderSubresourceScheme(
    scheme: String?,
    isMainFrame: Boolean
): Boolean {
    if (isMainFrame || scheme == null) {
        return false
    }

    return normalizeReaderNavigationScheme(scheme) in READER_BLOCKED_SUBRESOURCE_SCHEMES
}

private fun createBlockedSubresourceResponse(): WebResourceResponse =
    WebResourceResponse(
        "text/plain",
        "utf-8",
        ByteArrayInputStream(ByteArray(0))
    )

private fun openArticleInBrowser(context: android.content.Context, articleLink: String?) {
    val targetUri = articleLink
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?.let(Uri::parse)
        ?.takeIf { uri -> isReaderExternallyOpenableScheme(uri.scheme) }
        ?: return

    DebugLogger.i(TAG, "Externer Browser wird geoeffnet: $targetUri")
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, targetUri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.onFailure {
        DebugLogger.w(TAG, "Browser konnte nicht geoeffnet werden: $targetUri", it)
    }
}

internal fun isReaderExternallyOpenableScheme(scheme: String?): Boolean {
    return normalizeReaderNavigationScheme(scheme) in READER_EXTERNAL_OPENABLE_SCHEMES
}

private fun normalizeReaderNavigationScheme(scheme: String?): String? = scheme?.lowercase()

@SuppressLint("ClickableViewAccessibility")
private fun clearWebViewForReuse(
    webView: WebView?,
    forDestroy: Boolean = false
) {
    webView ?: return
    if (destroyedReaderWebViews[webView] == true) {
        return
    }
    runCatching {
        webView.setOnTouchListener(null)
        webView.webViewClient = WebViewClient()
        webView.webChromeClient = null
        webView.tag = null
        webView.stopLoading()
        webView.onPause()
        webView.pauseTimers()
        if (!forDestroy) {
            webView.loadUrl("about:blank")
            webView.clearHistory()
        }
        webView.clearFocus()
    }
}

private fun configureReaderWebViewSettings(
    webView: WebView,
    requiresJavaScript: Boolean
) {
    runCatching {
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, false)
        }
        val settings = webView.settings.apply {
            javaScriptEnabled = requiresJavaScript
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            loadsImagesAutomatically = true
            mediaPlaybackRequiresUserGesture = true
            allowFileAccess = false
            allowContentAccess = false
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            offscreenPreRaster = false
            builtInZoomControls = false
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            safeBrowsingEnabled = true
        }
        DebugLogger.d(
            TAG,
            buildString {
                append("WebView Settings: ")
                append("js=").append(settings.javaScriptEnabled)
                append(", domStorage=").append(settings.domStorageEnabled)
                append(", fileAccess=").append(settings.allowFileAccess)
                append(", contentAccess=").append(settings.allowContentAccess)
                append(", multiWindow=").append(settings.supportMultipleWindows())
                append(", jsOpenWindows=").append(settings.javaScriptCanOpenWindowsAutomatically)
                append(", mixedContentMode=").append(settings.mixedContentMode)
                append(", safeBrowsing=").append(settings.safeBrowsingEnabled)
            }
        )
    }.onFailure {
        DebugLogger.w(
            TAG,
            "WebView Einstellungen konnten nicht vollstaendig gesetzt werden",
            it
        )
    }
}

private fun destroyWebView(webView: WebView?) {
    webView ?: return
    if (destroyedReaderWebViews[webView] == true) {
        return
    }
    destroyedReaderWebViews[webView] = true
    runCatching {
        (webView.parent as? ViewGroup)?.removeView(webView)
        clearWebViewForReuse(webView, forDestroy = true)
        webView.removeAllViews()
        webView.destroy()
    }
}

private fun formatReaderRelativeTime(timestamp: Long?, neverLabel: String = "nie"): String {
    if (timestamp == null) {
        return neverLabel
    }

    return DateUtils.getRelativeTimeSpanString(
        timestamp,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE
    ).toString()
}

internal fun resolveReaderFallbackBodyText(article: ArticleEntity?): String {
    return article?.plainText?.ifBlank { "Kein Textinhalt vorhanden." }.orEmpty()
}

internal fun resolveReaderFallbackBodyText(
    article: ArticleEntity?,
    emptyTextLabel: String
): String {
    return article?.plainText?.ifBlank { emptyTextLabel }.orEmpty()
}

private data class ReaderLoadProfile(
    val isHeavy: Boolean = false,
    val useFallback: Boolean = false,
    val htmlLength: Int = 0,
    val imageCount: Int = 0,
    val mediaEmbedCount: Int = 0,
    val reasonSummary: String = ""
)

private const val TAG = "ArticleReaderScreen"
private const val IMAGE_TAP_SCHEME = "rssreader-article"
private const val READER_WEBVIEW_BASE_URL = "https://localhost/"
private const val ARTICLE_SWITCH_ANIMATION_MS = 280
private const val ARTICLE_SWITCH_FADE_IN_MS = 180
private const val ARTICLE_SWITCH_FADE_OUT_MS = 140
private const val ARTICLE_SWIPE_THRESHOLD_PX = 80f
private const val ARTICLE_SWIPE_DIRECTION_BIAS = 1.35f
private const val SWIPE_HINT_DURATION_MS = 2400
private const val READER_HEAVY_HTML_LENGTH_THRESHOLD = 60000
private const val READER_HEAVY_IMAGE_COUNT_THRESHOLD = 12
private const val READER_HEAVY_MEDIA_COUNT_THRESHOLD = 3
private const val READER_FALLBACK_HTML_LENGTH_THRESHOLD = 120000
private const val READER_FALLBACK_IMAGE_COUNT_THRESHOLD = 28
private const val READER_FALLBACK_MEDIA_COUNT_THRESHOLD = 5
private const val READER_HEAVY_ARTICLE_MAX_IMAGES = 8
private val readerStyleTagRegex = Regex(
    "<style\\b[^>]*>.*?</style>",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
)
private val readerScriptTagRegex = Regex(
    "<script\\b[^>]*>.*?</script>",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
)
private val readerBaseTagRegex = Regex(
    "<base\\b[^>]*>",
    RegexOption.IGNORE_CASE
)
private val readerMetaTagRegex = Regex(
    "<meta\\b[^>]*>",
    RegexOption.IGNORE_CASE
)
private val readerLinkTagRegex = Regex(
    "<link\\b[^>]*>",
    RegexOption.IGNORE_CASE
)
private val readerFormTagRegex = Regex(
    "</?form\\b[^>]*>",
    RegexOption.IGNORE_CASE
)
private val readerFormControlTagRegex = Regex(
    "</?(?:input|button|select|textarea|option|optgroup|fieldset|legend|keygen|menuitem|command)\\b[^>]*>",
    RegexOption.IGNORE_CASE
)
private val readerPluginEmbedTagRegex = Regex(
    "<\\s*(?:embed|object)\\b[^>]*>.*?</\\s*(?:embed|object)\\s*>|<\\s*(?:embed|object)\\b[^>]*/?>",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
)
private val readerLegacyFrameTagRegex = Regex(
    "<\\s*(?:frame|frameset)\\b[^>]*>.*?</\\s*(?:frame|frameset)\\s*>|<\\s*(?:frame|frameset)\\b[^>]*/?>",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
)
private val readerAppletTagRegex = Regex(
    "<\\s*applet\\b[^>]*>.*?</\\s*applet\\s*>|<\\s*applet\\b[^>]*/?>",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
)
private val readerLegacyMiscTagRegex = Regex(
    "<\\s*(?:xml|isindex|nextid|scriptlet)\\b[^>]*>.*?</\\s*(?:xml|isindex|nextid|scriptlet)\\s*>|<\\s*(?:xml|isindex|nextid|scriptlet)\\b[^>]*/?>",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
)
private val readerLegacyPresentationTagRegex = Regex(
    "<\\s*(?:bgsound|basefont|param|spacer|plaintext)\\b[^>]*/?>|<\\s*/?\\s*(?:marquee|blink|noembed|noframes|nobr|multicol|center|font|acronym|tt|strike|big|listing|xmp|layer|ilayer|nolayer|shadow|banner)\\b[^>]*>",
    RegexOption.IGNORE_CASE
)
private val readerTemplateTagRegex = Regex(
    "<template\\b[^>]*>.*?</template>",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
)
private val readerNoscriptTagRegex = Regex(
    "<noscript\\b[^>]*>.*?</noscript>",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
)
private val readerResponsiveImageAttributeRegex = Regex(
    "\\s(?:srcset|sizes|fetchpriority|dynsrc|lowsrc|datasrc|datafld|dataformatas|background|ping|manifest|profile|longdesc|cite|codebase|archive|classid|pluginspage|pluginurl|crossorigin|referrerpolicy|integrity|attributionsrc|importance|target|download)\\s*=\\s*(?:\"[^\"]*\"|'[^']*'|[^\\s>]+)",
    RegexOption.IGNORE_CASE
)
private val readerLegacyImageMapAttributeRegex = Regex(
    "\\s(?:usemap\\s*=\\s*(?:\"[^\"]*\"|'[^']*'|[^\\s>]+)|ismap\\b)",
    RegexOption.IGNORE_CASE
)
private val readerInteractionAttributeRegex = Regex(
    "\\s(?:contenteditable|draggable|spellcheck|autofocus|tabindex|accesskey|hidefocus|unselectable|contextmenu|inputmode|enterkeyhint|autocapitalize|autocorrect|autocomplete|translate|popover|popovertarget|popovertargetaction|virtualkeyboardpolicy|writingsuggestions)(?:\\s*=\\s*(?:\"[^\"]*\"|'[^']*'|[^\\s>]+))?",
    RegexOption.IGNORE_CASE
)
private val readerMetadataAttributeRegex = Regex(
    "\\s(?:itemscope\\b|itemprop\\s*=\\s*(?:\"[^\"]*\"|'[^']*'|[^\\s>]+)|itemid\\s*=\\s*(?:\"[^\"]*\"|'[^']*'|[^\\s>]+)|itemref\\s*=\\s*(?:\"[^\"]*\"|'[^']*'|[^\\s>]+)|itemtype\\s*=\\s*(?:\"[^\"]*\"|'[^']*'|[^\\s>]+)|about\\s*=\\s*(?:\"[^\"]*\"|'[^']*'|[^\\s>]+)|typeof\\s*=\\s*(?:\"[^\"]*\"|'[^']*'|[^\\s>]+)|property\\s*=\\s*(?:\"[^\"]*\"|'[^']*'|[^\\s>]+)|resource\\s*=\\s*(?:\"[^\"]*\"|'[^']*'|[^\\s>]+)|vocab\\s*=\\s*(?:\"[^\"]*\"|'[^']*'|[^\\s>]+)|prefix\\s*=\\s*(?:\"[^\"]*\"|'[^']*'|[^\\s>]+)|slot\\s*=\\s*(?:\"[^\"]*\"|'[^']*'|[^\\s>]+)|part\\s*=\\s*(?:\"[^\"]*\"|'[^']*'|[^\\s>]+)|exportparts\\s*=\\s*(?:\"[^\"]*\"|'[^']*'|[^\\s>]+)|nonce\\s*=\\s*(?:\"[^\"]*\"|'[^']*'|[^\\s>]+)|blocking\\s*=\\s*(?:\"[^\"]*\"|'[^']*'|[^\\s>]+))",
    RegexOption.IGNORE_CASE
)
private val readerAccessibilityAttributeRegex = Regex(
    "\\s(?:role\\s*=\\s*(?:\"[^\"]*\"|'[^']*'|[^\\s>]+)|aria-label\\s*=\\s*(?:\"[^\"]*\"|'[^']*'|[^\\s>]+)|aria-labelledby\\s*=\\s*(?:\"[^\"]*\"|'[^']*'|[^\\s>]+)|aria-describedby\\s*=\\s*(?:\"[^\"]*\"|'[^']*'|[^\\s>]+)|aria-hidden\\s*=\\s*(?:\"[^\"]*\"|'[^']*'|[^\\s>]+))",
    RegexOption.IGNORE_CASE
)
private val READER_EXTERNAL_OPENABLE_SCHEMES = setOf("http", "https")
private val READER_BLOCKED_SUBRESOURCE_SCHEMES =
    setOf("file", "content", "intent", "vbscript", "javascript")
private val readerInlineEventHandlerRegex = Regex(
    "\\s+on[a-zA-Z0-9_-]+\\s*=\\s*(?:\"[^\"]*\"|'[^']*'|[^\\s>]+)",
    RegexOption.IGNORE_CASE
)
private val readerDangerousStyleAttributeRegex = Regex(
    "\\sstyle\\s*=\\s*(\"[^\"]*(?:expression\\s*\\(|behavior\\s*:|-moz-binding\\s*:|url\\s*\\(\\s*['\"]?\\s*(?:javascript|vbscript)\\s*:)[^\"]*\"|'[^']*(?:expression\\s*\\(|behavior\\s*:|-moz-binding\\s*:|url\\s*\\(\\s*['\"]?\\s*(?:javascript|vbscript)\\s*:)[^']*'|[^\\s>]*(?:expression\\s*\\(|behavior\\s*:|-moz-binding\\s*:|url\\s*\\(\\s*['\"]?\\s*(?:javascript|vbscript)\\s*:)[^\\s>]*)",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
)
private val readerImgTagRegex = Regex(
    "<img\\b[^>]*>",
    RegexOption.IGNORE_CASE
)
private val readerHeavyMediaTagRegex = Regex(
    "<\\s*(?:iframe|video|embed|object)\\b[^>]*>.*?</\\s*(?:iframe|video|embed|object)\\s*>|<\\s*(?:iframe|video|embed|object)\\b[^>]*/?>",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
)
private val readerComplexHtmlRegex = Regex(
    "<\\s*(?:a|iframe|video|embed|object|table|blockquote|pre|code|ul|ol|li|h[2-6])\\b|twitter-tweet|instagram-media|tiktok",
    RegexOption.IGNORE_CASE
)
private val destroyedReaderWebViews = WeakHashMap<WebView, Boolean>()

private class WebSwipeTracker {
    var startX: Float = 0f
    var startY: Float = 0f
    var deltaX: Float = 0f
    var deltaY: Float = 0f

    fun reset() {
        startX = 0f
        startY = 0f
        deltaX = 0f
        deltaY = 0f
    }
}

private fun Modifier.fallbackSwipe(
    newerArticleId: Long?,
    olderArticleId: Long?,
    onSwipeStart: () -> Unit,
    onSwipeTrigger: (SwipeDirection) -> Unit
): Modifier {
    return this.pointerInput(newerArticleId, olderArticleId) {
        var totalHorizontalDrag = 0f
        detectHorizontalDragGestures(
            onDragStart = {
                totalHorizontalDrag = 0f
                onSwipeStart()
            },
            onHorizontalDrag = { _, dragAmount ->
                totalHorizontalDrag += dragAmount
            },
            onDragEnd = {
                val swipeResult = determineHorizontalSwipeDirection(
                    totalHorizontalDrag = totalHorizontalDrag,
                    totalVerticalDrag = 0f,
                    canGoNewer = newerArticleId != null,
                    canGoOlder = olderArticleId != null
                )
                if (swipeResult != SwipeDirection.None) {
                    onSwipeTrigger(swipeResult)
                }
            },
            onDragCancel = {}
        )
    }
}

internal enum class SwipeDirection {
    None,
    ToNewer,
    ToOlder
}

internal fun determineHorizontalSwipeDirection(
    totalHorizontalDrag: Float,
    totalVerticalDrag: Float,
    canGoNewer: Boolean,
    canGoOlder: Boolean
): SwipeDirection {
    val isHorizontalSwipe =
        abs(totalHorizontalDrag) >= ARTICLE_SWIPE_THRESHOLD_PX &&
            abs(totalHorizontalDrag) > abs(totalVerticalDrag) * ARTICLE_SWIPE_DIRECTION_BIAS

    if (!isHorizontalSwipe) {
        return SwipeDirection.None
    }

    return when {
        totalHorizontalDrag >= ARTICLE_SWIPE_THRESHOLD_PX && canGoNewer -> SwipeDirection.ToNewer
        totalHorizontalDrag <= -ARTICLE_SWIPE_THRESHOLD_PX && canGoOlder -> SwipeDirection.ToOlder
        else -> SwipeDirection.None
    }
}


