package com.example.rssreader.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.text.format.DateUtils
import android.text.TextUtils
import android.util.Log
import android.view.MotionEvent
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
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
import androidx.compose.material.icons.filled.GTranslate
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
import com.example.rssreader.data.db.ArticleNavigationEntry
import com.example.rssreader.data.db.ArticleEntity
import com.example.rssreader.data.repository.FeedRepository
import com.example.rssreader.data.translation.ArticleTranslationManager
import com.example.rssreader.data.translation.shouldOfferTranslation
import com.example.rssreader.data.translation.translationSourceText
import com.example.rssreader.debug.DebugLogger
import com.example.rssreader.ui.model.TranslationUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URI
import java.util.Locale
import kotlin.math.abs

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleReaderScreen(
    articleId: Long,
    repository: FeedRepository,
    translationManager: ArticleTranslationManager,
    showImages: Boolean,
    articleBodyTextSizeOffset: Int,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val effectiveArticleBodyTextSizeOffset = articleBodyTextSizeOffset.coerceIn(-2, 2)
    var currentArticleId by rememberSaveable { mutableStateOf(articleId) }
    var article by remember { mutableStateOf<ArticleEntity?>(null) }
    var isLoadingArticle by rememberSaveable { mutableStateOf(true) }
    var swipeDirection by remember { mutableStateOf(SwipeDirection.None) }
    var hasShownInitialArticle by rememberSaveable { mutableStateOf(false) }
    var showSwipeHint by rememberSaveable { mutableStateOf(false) }
    var activeWebView by remember { mutableStateOf<WebView?>(null) }
    var webViewFailed by remember(currentArticleId) { mutableStateOf(false) }
    val webSwipeTracker = remember { WebSwipeTracker() }
    val translationTargetLanguage = remember { defaultTranslationTargetLanguage() }
    val translationStates by translationManager.states.collectAsState(initial = emptyMap())
    val translationUiState = translationStates[currentArticleId] ?: TranslationUiState.Idle
    val showTranslateButton by remember(article?.id, translationUiState) {
        derivedStateOf {
            translationUiState is TranslationUiState.Success || shouldOfferTranslation(
                title = article?.title.orEmpty(),
                body = article?.translationSourceText().orEmpty()
            )
        }
    }
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
    val triggerSwipe: (Float) -> Unit = { totalHorizontalDrag ->
        when {
            totalHorizontalDrag >= ARTICLE_SWIPE_THRESHOLD_PX && newerArticleId != null -> {
                scope.launch {
                    swipeDirection = SwipeDirection.ToNewer
                    currentArticleId = newerArticleId!!
                }
            }
            totalHorizontalDrag <= -ARTICLE_SWIPE_THRESHOLD_PX && olderArticleId != null -> {
                scope.launch {
                    swipeDirection = SwipeDirection.ToOlder
                    currentArticleId = olderArticleId!!
                }
            }
        }
    }

    LaunchedEffect(articleId) {
        currentArticleId = articleId
    }

    LaunchedEffect(currentArticleId) {
        isLoadingArticle = true
        webViewFailed = false
        clearWebViewForReuse(activeWebView)
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
                        text = (translationUiState as? TranslationUiState.Success)
                            ?.result
                            ?.translatedTitle
                            ?.ifBlank { article?.title.orEmpty() }
                            ?: article?.title
                            ?: "Artikel",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    Row {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurueck")
                        }
                        if (showTranslateButton) {
                            IconButton(
                                onClick = {
                                    val currentArticle = article ?: return@IconButton
                                    if (translationUiState is TranslationUiState.Loading) {
                                        return@IconButton
                                    }
                                    if (translationUiState is TranslationUiState.Success) {
                                        DebugLogger.d(
                                            TAG,
                                            "Reader-Uebersetzung verworfen: articleId=${currentArticle.id}"
                                        )
                                        translationManager.clearArticle(currentArticle.id)
                                        return@IconButton
                                    }
                                    DebugLogger.i(
                                        TAG,
                                        "Reader-Uebersetzung angefordert: articleId=${currentArticle.id}, tl=$translationTargetLanguage"
                                    )
                                    scope.launch {
                                        val translationState = translationManager.translateArticle(
                                            articleId = currentArticle.id,
                                            title = currentArticle.title,
                                            body = currentArticle.translationSourceText(),
                                            targetLanguage = translationTargetLanguage
                                        )
                                        if (translationState is TranslationUiState.Error) {
                                            Log.w(
                                                TAG,
                                                "Article translation failed: articleId=${currentArticle.id}, message=${translationState.message}"
                                            )
                                        }
                                    }
                                },
                                enabled = article != null
                            ) {
                                if (translationUiState is TranslationUiState.Loading) {
                                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(10.dp))
                                } else {
                                    Icon(
                                        Icons.Default.GTranslate,
                                        contentDescription = if (translationUiState is TranslationUiState.Success) {
                                            "Original anzeigen"
                                        } else {
                                            "Mit Google uebersetzen"
                                        }
                                    )
                                }
                            }
                        }
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
        if (isLoadingArticle) {
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
                    text = "Artikel nicht gefunden.",
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
                    val translatedResult = translationUiState as? TranslationUiState.Success
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
                    val shouldShowTranslatedContent = translatedResult != null
                    val shouldUseWebView = remember(articleHtmlContent, shouldShowTranslatedContent) {
                        !shouldShowTranslatedContent && articleHtmlContent?.shouldUseWebView() == true
                    }
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
                                    Modifier.pointerInput(newerArticleId, olderArticleId) {
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
                                                triggerSwipe(totalHorizontalDrag)
                                            },
                                            onDragCancel = {}
                                        )
                                    }
                                }
                            ),
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        when (val translationState = translationUiState) {
                            is TranslationUiState.Loading -> {
                                Text(
                                    text = "Uebersetzung wird geladen...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                                )
                            }

                            is TranslationUiState.Error -> {
                                Text(
                                    text = translationState.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                                )
                            }

                            else -> Unit
                        }
                        if (shouldUseWebView && !webViewFailed) {
                            AndroidView(
                                factory = { viewContext ->
                                    WebView(viewContext).apply {
                                        activeWebView = this
                                        DebugLogger.i(
                                            TAG,
                                            "WebView erstellt: articleId=${currentArticle?.id?.toString().orEmpty()}"
                                        )
                                        configureReaderWebViewSettings(
                                            webView = this,
                                            requiresJavaScript = false
                                        )
                                        overScrollMode = WebView.OVER_SCROLL_NEVER
                                        // Touch-Beobachtung direkt am nativen WebView, damit
                                        // vertikales Lesen nicht von einer Compose-Wischgeste
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
                                                    val totalHorizontalDrag = webSwipeTracker.deltaX
                                                    val totalVerticalDrag = webSwipeTracker.deltaY
                                                    val shouldHandleSwipe =
                                                        abs(totalHorizontalDrag) >= ARTICLE_SWIPE_THRESHOLD_PX &&
                                                            abs(totalHorizontalDrag) >
                                                            abs(totalVerticalDrag) * ARTICLE_SWIPE_DIRECTION_BIAS

                                                    if (shouldHandleSwipe) {
                                                        triggerSwipe(totalHorizontalDrag)
                                                    }
                                                    webSwipeTracker.reset()
                                                    shouldHandleSwipe
                                                }

                                                MotionEvent.ACTION_CANCEL -> {
                                                    webSwipeTracker.reset()
                                                    false
                                                }

                                                else -> false
                                            }
                                        }
                                        webViewClient = object : WebViewClient() {
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
                                        clearWebViewForReuse(webView)
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
                                titleOverride = translatedResult?.result?.translatedTitle,
                                bodyTextOverride = translatedResult?.result?.translatedBody,
                                translationHint = translatedResult?.result?.let {
                                    "Uebersetzt mit ${it.providerLabel}"
                                },
                                translationActionLabel = if (translatedResult != null) {
                                    "Original anzeigen"
                                } else {
                                    null
                                },
                                onTranslationAction = if (translatedResult != null) {
                                    { currentArticle?.id?.let(translationManager::clearArticle) }
                                } else {
                                    null
                                },
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
    val twitterScript = if (
        !loadProfile.isHeavy &&
        "twitter-tweet" in rawBody ||
        !loadProfile.isHeavy && "twitter.com" in rawBody ||
        !loadProfile.isHeavy && "x.com" in rawBody
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
              font-size: 17px;
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

private fun String.normalizeReaderBodyHtml(loadProfile: ReaderLoadProfile): String {
    // Reader-HTML bleibt lesbar, aber fremde Inline-Skripte und Feed-eigene Styles
    // sollen die WebView nicht unnötig belasten oder das Layout destabilisieren.
    val normalizedHtml = readerResponsiveImageAttributeRegex.replace(
        readerNoscriptTagRegex.replace(
            readerStyleTagRegex.replace(
                readerScriptTagRegex.replace(this, ""),
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
    titleOverride: String? = null,
    bodyTextOverride: String? = null,
    translationHint: String? = null,
    translationActionLabel: String? = null,
    onTranslationAction: (() -> Unit)? = null,
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
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = titleOverride?.ifBlank { article?.title.orEmpty() } ?: article?.title.orEmpty(),
            style = compactHeaderTextStyle,
            modifier = Modifier.clickable(onClick = onOpenArticle)
        )
        if (article?.publishedAt != null) {
            Text(
                text = formatReaderRelativeTime(article.publishedAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (!translationHint.isNullOrBlank()) {
            Text(
                text = translationHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        if (!translationActionLabel.isNullOrBlank() && onTranslationAction != null) {
            Text(
                text = translationActionLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(onClick = onTranslationAction)
            )
        }
        Text(
            text = bodyTextOverride?.ifBlank {
                article?.plainText.orEmpty()
            } ?: article?.plainText?.ifBlank { "Kein Textinhalt vorhanden." }.orEmpty(),
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
                text = "Bilder sind in den Einstellungen deaktiviert.",
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
    if (targetUri?.scheme == IMAGE_TAP_SCHEME) {
        openArticleInBrowser(context, targetUri.getQueryParameter("url"))
        return true
    }
    if (isMainFrame) {
        openArticleInBrowser(context, targetUri?.toString())
        return true
    }
    return false
}

private fun openArticleInBrowser(context: android.content.Context, articleLink: String?) {
    val targetUri = articleLink
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?.let(Uri::parse)
        ?.takeIf { uri ->
            when (uri.scheme?.lowercase()) {
                "http", "https" -> true
                else -> false
            }
        }
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

private fun clearWebViewForReuse(webView: WebView?) {
    webView ?: return
    runCatching {
        webView.stopLoading()
        webView.onPause()
        webView.pauseTimers()
        webView.loadUrl("about:blank")
        webView.clearHistory()
        webView.clearFocus()
    }
}

private fun configureReaderWebViewSettings(
    webView: WebView,
    requiresJavaScript: Boolean
) {
    runCatching {
        val settings = webView.settings.apply {
            javaScriptEnabled = requiresJavaScript
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            loadsImagesAutomatically = true
            mediaPlaybackRequiresUserGesture = true
            allowFileAccess = false
            allowContentAccess = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            offscreenPreRaster = false
            builtInZoomControls = false
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                safeBrowsingEnabled = true
            }
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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    append(", mixedContentMode=").append(settings.mixedContentMode)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    append(", safeBrowsing=").append(settings.safeBrowsingEnabled)
                }
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
    runCatching {
        clearWebViewForReuse(webView)
        webView.removeAllViews()
        webView.destroy()
    }
}

private fun formatReaderRelativeTime(timestamp: Long?): String {
    if (timestamp == null) {
        return "nie"
    }

    return DateUtils.getRelativeTimeSpanString(
        timestamp,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE
    ).toString()
}

private fun defaultTranslationTargetLanguage(): String {
    return Locale.getDefault().language
        .trim()
        .ifBlank { "de" }
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
private const val ARTICLE_SWITCH_ANIMATION_MS = 220
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
private val readerNoscriptTagRegex = Regex(
    "<noscript\\b[^>]*>.*?</noscript>",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
)
private val readerResponsiveImageAttributeRegex = Regex(
    "\\s(?:srcset|sizes|fetchpriority)\\s*=\\s*(?:\"[^\"]*\"|'[^']*'|[^\\s>]+)",
    RegexOption.IGNORE_CASE
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

private enum class SwipeDirection {
    None,
    ToNewer,
    ToOlder
}


