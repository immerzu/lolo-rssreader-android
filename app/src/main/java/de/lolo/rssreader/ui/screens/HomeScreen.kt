package de.lolo.rssreader.ui.screens

import android.content.Context
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import de.lolo.rssreader.R
import de.lolo.rssreader.debug.DebugLogger
import de.lolo.rssreader.data.db.FeedSummary
import de.lolo.rssreader.data.errors.RssReaderException
import de.lolo.rssreader.data.errors.toUserMessage
import de.lolo.rssreader.data.repository.FeedRepository
import de.lolo.rssreader.data.repository.RefreshRunStats
import de.lolo.rssreader.data.repository.RepositoryDiagnosticsSnapshot
import de.lolo.rssreader.data.settings.AppPreferences
import de.lolo.rssreader.notifications.ArticleUpdateNotifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class HomeAction {
    DELETE_READ,
    DELETE_ALL,
    DELETE_FEEDS
}

private enum class FeedConfirmAction {
    DELETE_READ,
    DELETE_ALL,
    DELETE_FEED
}

private const val HOME_REFRESH_INDICATOR_DURATION_MS = 700L
private object HomeScreenFeedCache {
    var lastFeeds: List<FeedSummary> = emptyList()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun HomeScreen(
    repository: FeedRepository,
    settings: AppPreferences,
    settingsLoaded: Boolean,
    onOpenFeed: (Long) -> Unit,
    onAddFeed: () -> Unit,
    onEditFeed: (Long) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val logTag = "HomeScreen"
    val context = LocalContext.current
    val activity = findHostActivity(context)
    val feeds by repository.observeFeedSummaries().collectAsState(initial = null)
    var lastVisibleFeeds by remember { mutableStateOf(HomeScreenFeedCache.lastFeeds) }
    LaunchedEffect(feeds) {
        feeds?.let { currentFeeds ->
            lastVisibleFeeds = currentFeeds
            HomeScreenFeedCache.lastFeeds = currentFeeds
        }
    }
    val feedsObserved = feeds != null
    val loadedFeeds = feeds ?: lastVisibleFeeds
    val feedsLoadedForUi = feeds != null || lastVisibleFeeds.isNotEmpty()
    val scope = rememberCoroutineScope()
    var isRefreshing by rememberSaveable { mutableStateOf(false) }
    var showRefreshIndicator by rememberSaveable { mutableStateOf(false) }
    var refreshIndicatorToken by rememberSaveable { mutableIntStateOf(0) }
    var initialRefreshDone by rememberSaveable { mutableStateOf(false) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedFeedMenuId by rememberSaveable { mutableStateOf<Long?>(null) }
    var topMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var pendingHomeAction by rememberSaveable { mutableStateOf<HomeAction?>(null) }
    var pendingFeedAction by rememberSaveable { mutableStateOf<FeedConfirmAction?>(null) }
    var pendingFeedActionId by rememberSaveable { mutableStateOf<Long?>(null) }
    var isMoveMode by rememberSaveable { mutableStateOf(false) }
    var busy by rememberSaveable { mutableStateOf(false) }
    var importInProgress by rememberSaveable { mutableStateOf(false) }
    var importStatusText by rememberSaveable { mutableStateOf<String?>(null) }
    var importDialogToken by rememberSaveable { mutableIntStateOf(0) }
    var importResultMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var refreshStatusText by rememberSaveable { mutableStateOf<String?>(null) }
    var diagnosticsText by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val showInfoMessage: (String) -> Unit = { message ->
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
    }
    fun showRefreshIndicatorBriefly() {
        val token = refreshIndicatorToken + 1
        refreshIndicatorToken = token
        showRefreshIndicator = true
        refreshStatusText = null
        scope.launch {
            delay(HOME_REFRESH_INDICATOR_DURATION_MS)
            if (refreshIndicatorToken == token) {
                showRefreshIndicator = false
                if (isRefreshing) {
                    refreshStatusText = context.getString(R.string.home_refresh_running)
                }
            }
        }
    }
    suspend fun runRefreshAll(showSuccessMessage: Boolean, manualTrigger: Boolean) {
        if (isRefreshing) {
            return
        }
        if (isDefinitelyOffline(context)) {
            if (manualTrigger) {
                errorMessage = noNetworkConnectionMessage(context)
            }
            return
        }
        val hasWifiConnection = hasWifiRefreshConnection(context)
        if (shouldBlockRefreshForWifiOnlySetting(settings.refreshOnlyOnWifi, hasWifiConnection)) {
            if (manualTrigger) {
                errorMessage = wifiOnlyRefreshMessage(context)
            }
            return
        }
        isRefreshing = true
        showRefreshIndicatorBriefly()
        try {
            runCatching { repository.refreshAll(hasWifiConnection = hasWifiConnection) }
                .onSuccess { stats ->
                    if (showSuccessMessage) {
                        showInfoMessage(formatRefreshSummary(context, stats))
                    }
                    if (
                        shouldShowCompletionNotificationForRefresh(
                            manualTrigger = manualTrigger,
                            notificationsEnabled = settings.notificationsEnabled,
                            newArticles = stats.newArticles,
                            isAppInForeground = activity
                                ?.lifecycle
                                ?.currentState
                                ?.isAtLeast(Lifecycle.State.STARTED)
                                ?: false
                        )
                    ) {
                        DebugLogger.i(
                            logTag,
                            "Manueller Refresh im Hintergrund abgeschlossen, Benachrichtigung wird angefragt: newArticles=${stats.newArticles}, refreshedFeeds=${stats.refreshedFeeds}"
                        )
                        ArticleUpdateNotifier(context).showNewArticlesNotification(
                            newArticles = stats.newArticles,
                            refreshedFeeds = stats.refreshedFeeds
                        )
                    }
                }
                .onFailure {
                    if (it !is CancellationException) {
                        errorMessage = it.toUserMessage(
                            context.getString(R.string.home_update_failed)
                        )
                    }
                }
        } finally {
            isRefreshing = false
            refreshStatusText = null
        }
    }
    fun launchRefreshAll(showSuccessMessage: Boolean, manualTrigger: Boolean) {
        launchFromUiScope(activity, scope) {
            runRefreshAll(
                showSuccessMessage = showSuccessMessage,
                manualTrigger = manualTrigger
            )
        }
    }
    val refreshAllFeeds: () -> Unit = {
        launchRefreshAll(showSuccessMessage = true, manualTrigger = true)
    }
    val pullToRefreshAllFeeds: () -> Unit = {
        launchRefreshAll(showSuccessMessage = true, manualTrigger = true)
    }
    val pullRefreshState = rememberPullRefreshState(
        refreshing = showRefreshIndicator,
        onRefresh = pullToRefreshAllFeeds
    )

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            launchFromUiScope(activity, scope) {
                busy = true
                importInProgress = true
                importStatusText = null
                val token = importDialogToken + 1
                importDialogToken = token
                scope.launch {
                    delay(HOME_REFRESH_INDICATOR_DURATION_MS)
                    if (importDialogToken == token) {
                        importStatusText = context.getString(R.string.shared_import_in_progress)
                    }
                }
                try {
                    runCatching { importOpmlFromUri(context, repository, uri) }
                        .onSuccess { result ->
                            importResultMessage = formatImportResultDialogMessage(result, context)
                        }
                        .onFailure {
                            if (it !is CancellationException) {
                                errorMessage = it.toUserMessage(
                                    context.getString(R.string.home_opml_import_failed)
                                )
                            }
                        }
                } finally {
                    importStatusText = null
                    importInProgress = false
                    busy = false
                }
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/xml")
    ) { uri ->
        if (uri != null) {
            launchFromUiScope(activity, scope) {
                busy = true
                runCatching { exportOpmlToUri(context, repository, uri) }
                    .onSuccess { exportedFeeds ->
                        showInfoMessage(formatExportSummary(context, exportedFeeds))
                    }
                .onFailure {
                    if (it !is CancellationException) {
                        errorMessage = it.toUserMessage(
                            context.getString(R.string.home_opml_export_failed)
                        )
                    }
                }
                busy = false
            }
        }
    }

    LaunchedEffect(settingsLoaded, settings.refreshOnStart) {
        if (settingsLoaded && settings.refreshOnStart && !initialRefreshDone) {
            initialRefreshDone = true
            launchRefreshAll(showSuccessMessage = false, manualTrigger = false)
        }
    }

    LaunchedEffect(isMoveMode) {
        if (isMoveMode) {
            selectedFeedMenuId = null
        }
    }

    DisposableEffect(Unit) {
        DebugLogger.i(logTag, "sichtbar")
        onDispose {
            DebugLogger.i(logTag, "verlassen")
        }
    }

    LaunchedEffect(
        settingsLoaded,
        feedsObserved,
        feedsLoadedForUi,
        loadedFeeds.size,
        isRefreshing,
        showRefreshIndicator,
        busy,
        importInProgress,
        importStatusText,
        refreshStatusText,
        isMoveMode,
        topMenuExpanded
    ) {
        DebugLogger.d(
            logTag,
            "state settingsLoaded=$settingsLoaded, feedsObserved=$feedsObserved, feedsLoadedForUi=$feedsLoadedForUi, " +
                "feedCount=${loadedFeeds.size}, isRefreshing=$isRefreshing, showRefreshIndicator=$showRefreshIndicator, " +
                "busy=$busy, importInProgress=$importInProgress, importStatusText=${importStatusText != null}, " +
                "refreshStatusText=${refreshStatusText != null}, isMoveMode=$isMoveMode, topMenuExpanded=$topMenuExpanded"
        )
    }

    BackHandler(enabled = topMenuExpanded) {
        topMenuExpanded = false
    }

    val homeMenuTextStyle = MaterialTheme.typography.titleMedium

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(R.drawable.toolbar_logo_lolosoft),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                if (isMoveMode) {
                                    stringResource(R.string.home_title_move_mode)
                                } else {
                                    "RSS Reader"
                                }
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = onAddFeed,
                            enabled = !isMoveMode
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = stringResource(R.string.home_add_feed_cd)
                            )
                        }
                        IconButton(
                            onClick = refreshAllFeeds,
                            enabled = !isRefreshing && !busy && !isMoveMode
                        ) {
                            RefreshActionIcon(
                                isRefreshing = showRefreshIndicator,
                                contentDescription = stringResource(R.string.home_refresh_all_feeds_cd)
                            )
                        }
                        IconButton(
                            onClick = { topMenuExpanded = true },
                            enabled = !busy
                        ) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.home_menu_cd))
                        }
                    }
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .pullRefresh(pullRefreshState)
            ) {
                FeedListScreen(
                    feeds = loadedFeeds,
                    feedsLoaded = feedsLoadedForUi,
                    isRefreshing = isRefreshing || showRefreshIndicator,
                    isMoveMode = isMoveMode,
                    onOpenFeed = onOpenFeed,
                    onOpenFeedMenu = { feedId ->
                        selectedFeedMenuId = feedId
                    },
                    onMoveFeedUp = { feedId ->
                        scope.launch {
                            runCatching { repository.moveFeedUp(feedId) }
                                .onFailure {
                                    if (it !is CancellationException) {
                                        errorMessage = it.toUserMessage(
                                            context.getString(R.string.home_move_failed)
                                        )
                                    }
                                }
                        }
                    },
                    onMoveFeedDown = { feedId ->
                        scope.launch {
                            runCatching { repository.moveFeedDown(feedId) }
                                .onFailure {
                                    if (it !is CancellationException) {
                                        errorMessage = it.toUserMessage(
                                            context.getString(R.string.home_move_failed)
                                        )
                                    }
                                }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
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
                if (importStatusText != null && !showRefreshIndicator) {
                    Text(
                        text = importStatusText!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 8.dp)
                    )
                }
            }
        }

        if (topMenuExpanded) {
            val dismissMenuInteractionSource = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = dismissMenuInteractionSource,
                        indication = null
                    ) {
                        topMenuExpanded = false
                    }
            )
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 56.dp, end = 8.dp)
                    .widthIn(min = 220.dp, max = 320.dp),
                tonalElevation = 8.dp,
                shadowElevation = 8.dp
            ) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.home_menu_search), style = homeMenuTextStyle) },
                        modifier = Modifier.compactMenuItem(),
                        contentPadding = compactMenuItemPadding(),
                        onClick = {
                            topMenuExpanded = false
                            onOpenSearch()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.home_menu_settings), style = homeMenuTextStyle) },
                        modifier = Modifier.compactMenuItem(),
                        contentPadding = compactMenuItemPadding(),
                        onClick = {
                            topMenuExpanded = false
                            onOpenSettings()
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (isMoveMode) {
                                    stringResource(R.string.home_menu_move_mode_disable)
                                } else {
                                    stringResource(R.string.home_menu_move_mode_enable)
                                },
                                style = homeMenuTextStyle
                            )
                        },
                        modifier = Modifier.compactMenuItem(),
                        contentPadding = compactMenuItemPadding(),
                        onClick = {
                            topMenuExpanded = false
                            isMoveMode = !isMoveMode
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.home_menu_refresh_all), style = homeMenuTextStyle) },
                        enabled = !isRefreshing && !busy && !isMoveMode,
                        modifier = Modifier.compactMenuItem(),
                        contentPadding = compactMenuItemPadding(),
                        onClick = {
                            topMenuExpanded = false
                            refreshAllFeeds()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.home_menu_mark_all_read), style = homeMenuTextStyle) },
                        enabled = !isMoveMode,
                        modifier = Modifier.compactMenuItem(),
                        contentPadding = compactMenuItemPadding(),
                        onClick = {
                            topMenuExpanded = false
                            scope.launch {
                                runCatching { repository.markAllReadGlobally() }
                                    .onSuccess { affectedCount ->
                                        showInfoMessage(
                                            formatStateChangeSummary(
                                                affectedCount = affectedCount,
                                                singularLabel = context.getString(R.string.home_article_label_singular),
                                                pluralLabel = context.getString(R.string.home_article_label_plural),
                                                suffix = context.getString(R.string.home_suffix_marked_read)
                                            )
                                        )
                                    }
                                    .onFailure {
                                        if (it !is CancellationException) {
                                            errorMessage = it.toUserMessage(
                                                context.getString(R.string.home_entries_mark_failed)
                                            )
                                        }
                                    }
                            }
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.home_menu_mark_all_unread), style = homeMenuTextStyle) },
                        enabled = !isMoveMode,
                        modifier = Modifier.compactMenuItem(),
                        contentPadding = compactMenuItemPadding(),
                        onClick = {
                            topMenuExpanded = false
                            scope.launch {
                                runCatching { repository.markAllUnreadGlobally() }
                                    .onSuccess { affectedCount ->
                                        showInfoMessage(
                                            formatStateChangeSummary(
                                                affectedCount = affectedCount,
                                                singularLabel = context.getString(R.string.home_article_label_singular),
                                                pluralLabel = context.getString(R.string.home_article_label_plural),
                                                suffix = context.getString(R.string.home_suffix_marked_unread)
                                            )
                                        )
                                    }
                                    .onFailure {
                                        if (it !is CancellationException) {
                                            errorMessage = it.toUserMessage(
                                                context.getString(R.string.home_entries_reset_failed)
                                            )
                                        }
                                    }
                            }
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.home_menu_delete_read), style = homeMenuTextStyle) },
                        enabled = !isMoveMode && !isRefreshing && !busy,
                        modifier = Modifier.compactMenuItem(),
                        contentPadding = compactMenuItemPadding(),
                        onClick = {
                            topMenuExpanded = false
                            pendingHomeAction = HomeAction.DELETE_READ
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.home_menu_delete_non_favorite), style = homeMenuTextStyle) },
                        enabled = !isMoveMode && !isRefreshing && !busy,
                        modifier = Modifier.compactMenuItem(),
                        contentPadding = compactMenuItemPadding(),
                        onClick = {
                            topMenuExpanded = false
                            pendingHomeAction = HomeAction.DELETE_ALL
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.home_menu_delete_all_feeds), style = homeMenuTextStyle) },
                        enabled = !isMoveMode && loadedFeeds.isNotEmpty() && !isRefreshing && !busy,
                        modifier = Modifier.compactMenuItem(),
                        contentPadding = compactMenuItemPadding(),
                        onClick = {
                            topMenuExpanded = false
                            pendingHomeAction = HomeAction.DELETE_FEEDS
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.home_menu_import_opml), style = homeMenuTextStyle) },
                        enabled = !isMoveMode,
                        modifier = Modifier.compactMenuItem(),
                        contentPadding = compactMenuItemPadding(),
                        onClick = {
                            topMenuExpanded = false
                            importLauncher.launch(arrayOf("text/xml", "application/xml", "*/*"))
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.home_menu_export_opml), style = homeMenuTextStyle) },
                        enabled = !isMoveMode,
                        modifier = Modifier.compactMenuItem(),
                        contentPadding = compactMenuItemPadding(),
                        onClick = {
                            topMenuExpanded = false
                            exportLauncher.launch("rss-reader-feeds.xml")
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.home_menu_show_diagnostics), style = homeMenuTextStyle) },
                        enabled = !busy,
                        modifier = Modifier.compactMenuItem(),
                        contentPadding = compactMenuItemPadding(),
                        onClick = {
                            topMenuExpanded = false
                            scope.launch {
                                busy = true
                                runCatching { repository.diagnosticsSnapshot() }
                                    .onSuccess { snapshot ->
                                        diagnosticsText = formatDiagnosticsSummary(
                                            context = context,
                                            snapshot = snapshot,
                                            versionLabel = currentAppVersionLabel(context)
                                        )
                                    }
                                    .onFailure {
                                        if (it !is CancellationException) {
                                            errorMessage = it.toUserMessage(
                                                context.getString(R.string.home_diagnostics_failed)
                                            )
                                        }
                                    }
                                busy = false
                            }
                        }
                    )
                }
            }
        }
    }

    selectedFeedMenuId?.let { feedId ->
        val selectedFeed = loadedFeeds.firstOrNull { it.id == feedId }
        AlertDialog(
            onDismissRequest = { selectedFeedMenuId = null },
            title = { Text(selectedFeed?.displayTitle ?: stringResource(R.string.home_feed_menu_title)) },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            selectedFeedMenuId = null
                            scope.launch {
                                if (isRefreshing || busy) {
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
                                        feedWifiOnly = selectedFeed?.wifiOnly == true
                                    )
                                ) {
                                    errorMessage = wifiOnlyRefreshMessage(context)
                                    return@launch
                                }
                                runCatching { repository.refreshFeed(feedId) }
                                    .onSuccess { newArticles ->
                                        showInfoMessage(formatFeedRefreshSummary(context, newArticles))
                                    }
                                    .onFailure {
                                        if (it !is CancellationException) {
                                            errorMessage = it.toUserMessage(
                                                context.getString(R.string.home_feed_refresh_failed)
                                            )
                                        }
                                    }
                            }
                        }
                    ) {
                        Text(stringResource(R.string.home_feed_refresh), fontSize = 18.sp)
                    }
                    TextButton(
                        onClick = {
                            selectedFeedMenuId = null
                            scope.launch {
                                runCatching { repository.markAllRead(feedId) }
                                    .onSuccess { affectedCount ->
                                        showInfoMessage(
                                            formatStateChangeSummary(
                                                affectedCount = affectedCount,
                                                singularLabel = context.getString(R.string.home_feed_article_label_singular),
                                                pluralLabel = context.getString(R.string.home_feed_article_label_plural),
                                                suffix = context.getString(R.string.home_suffix_marked_read)
                                            )
                                        )
                                    }
                                    .onFailure {
                                        if (it !is CancellationException) {
                                            errorMessage = it.toUserMessage(
                                                context.getString(R.string.home_feed_mark_read_failed)
                                            )
                                        }
                                    }
                            }
                        }
                    ) {
                        Text(stringResource(R.string.home_menu_mark_all_read), fontSize = 18.sp)
                    }
                    TextButton(
                        onClick = {
                            selectedFeedMenuId = null
                            scope.launch {
                                runCatching { repository.markAllUnread(feedId) }
                                    .onSuccess { affectedCount ->
                                        showInfoMessage(
                                            formatStateChangeSummary(
                                                affectedCount = affectedCount,
                                                singularLabel = context.getString(R.string.home_feed_article_label_singular),
                                                pluralLabel = context.getString(R.string.home_feed_article_label_plural),
                                                suffix = context.getString(R.string.home_suffix_marked_unread)
                                            )
                                        )
                                    }
                                    .onFailure {
                                        if (it !is CancellationException) {
                                            errorMessage = it.toUserMessage(
                                                context.getString(R.string.home_feed_mark_unread_failed)
                                            )
                                        }
                                    }
                            }
                        }
                    ) {
                        Text(stringResource(R.string.home_menu_mark_all_unread), fontSize = 18.sp)
                    }
                    TextButton(
                        onClick = {
                            selectedFeedMenuId = null
                            pendingFeedActionId = feedId
                            pendingFeedAction = FeedConfirmAction.DELETE_READ
                        }
                    ) {
                        Text(stringResource(R.string.home_feed_delete_read), fontSize = 18.sp)
                    }
                    TextButton(
                        onClick = {
                            selectedFeedMenuId = null
                            pendingFeedActionId = feedId
                            pendingFeedAction = FeedConfirmAction.DELETE_ALL
                        }
                    ) {
                        Text(stringResource(R.string.home_feed_delete_non_favorite), fontSize = 18.sp)
                    }
                    TextButton(
                        onClick = {
                            selectedFeedMenuId = null
                            onEditFeed(feedId)
                        }
                    ) {
                        Text(stringResource(R.string.home_feed_edit), fontSize = 18.sp)
                    }
                    TextButton(
                        onClick = {
                            selectedFeedMenuId = null
                            scope.launch {
                                runCatching { repository.resetFeedUpdatedAt(feedId) }
                                    .onSuccess {
                                        showInfoMessage(
                                            context.getString(R.string.home_feed_reset_updated_at_success)
                                        )
                                    }
                                    .onFailure {
                                        if (it !is CancellationException) {
                                            errorMessage = it.toUserMessage(
                                                context.getString(R.string.home_feed_reset_updated_at_failed)
                                            )
                                        }
                                    }
                            }
                        }
                    ) {
                        Text(stringResource(R.string.home_feed_reset_updated_at), fontSize = 18.sp)
                    }
                    TextButton(
                        onClick = {
                            selectedFeedMenuId = null
                            pendingFeedActionId = feedId
                            pendingFeedAction = FeedConfirmAction.DELETE_FEED
                        }
                    ) {
                        Text(stringResource(R.string.common_delete), fontSize = 18.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedFeedMenuId = null }) {
                    Text(stringResource(R.string.common_close), fontSize = 18.sp)
                }
            },
            dismissButton = null
        )
    }

    if (pendingFeedAction != null && pendingFeedActionId != null) {
        val action = pendingFeedAction!!
        val feedId = pendingFeedActionId!!
        val title = when (action) {
            FeedConfirmAction.DELETE_READ -> context.getString(R.string.home_confirm_delete_read_title)
            FeedConfirmAction.DELETE_ALL -> context.getString(R.string.home_confirm_delete_all_entries_title)
            FeedConfirmAction.DELETE_FEED -> context.getString(R.string.home_confirm_feed_delete_title)
        }
        val text = when (action) {
            FeedConfirmAction.DELETE_READ -> context.getString(R.string.home_confirm_feed_delete_read_text)
            FeedConfirmAction.DELETE_ALL -> context.getString(R.string.home_confirm_feed_delete_all_text)
            FeedConfirmAction.DELETE_FEED -> context.getString(R.string.home_confirm_feed_delete_feed_text)
        }
        AlertDialog(
            onDismissRequest = {
                pendingFeedAction = null
                pendingFeedActionId = null
            },
            title = { Text(title) },
            text = { Text(text) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val currentAction = action
                        val currentFeedId = feedId
                        pendingFeedAction = null
                        pendingFeedActionId = null
                        scope.launch {
                            runCatching {
                                when (currentAction) {
                                    FeedConfirmAction.DELETE_READ -> repository.deleteFeedReadEntries(currentFeedId)
                                    FeedConfirmAction.DELETE_ALL -> repository.deleteFeedEntries(currentFeedId)
                                    FeedConfirmAction.DELETE_FEED -> repository.deleteFeed(currentFeedId)
                                }
                            }.onSuccess { result ->
                                showInfoMessage(
                                    when (currentAction) {
                                        FeedConfirmAction.DELETE_READ -> formatDeleteSummary(
                                            deletedCount = result as Int,
                                            singularLabel = context.getString(R.string.home_read_entry_label_singular),
                                            pluralLabel = context.getString(R.string.home_read_entry_label_plural),
                                            suffix = context.getString(R.string.home_suffix_deleted_in_feed)
                                        )
                                        FeedConfirmAction.DELETE_ALL -> formatDeleteSummary(
                                            deletedCount = result as Int,
                                            singularLabel = context.getString(R.string.home_entry_label_singular),
                                            pluralLabel = context.getString(R.string.home_entry_label_plural),
                                            suffix = context.getString(R.string.home_suffix_deleted_in_feed)
                                        )
                                        FeedConfirmAction.DELETE_FEED -> formatDeleteSummary(
                                            deletedCount = 1,
                                            singularLabel = context.getString(R.string.home_feed_label_singular),
                                            pluralLabel = context.getString(R.string.home_feed_label_plural),
                                            suffix = context.getString(R.string.home_suffix_deleted)
                                        )
                                    }
                                )
                            }.onFailure {
                                if (it !is CancellationException) {
                                    errorMessage = it.toUserMessage(
                                        context.getString(R.string.home_action_failed)
                                    )
                                }
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.common_delete))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pendingFeedAction = null
                        pendingFeedActionId = null
                    }
                ) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    pendingHomeAction?.let { action ->
        val title = when (action) {
            HomeAction.DELETE_READ -> context.getString(R.string.home_confirm_delete_read_title)
            HomeAction.DELETE_ALL -> context.getString(R.string.home_confirm_delete_all_entries_title)
            HomeAction.DELETE_FEEDS -> context.getString(R.string.home_confirm_delete_all_feeds_title)
        }
        val text = when (action) {
            HomeAction.DELETE_READ -> context.getString(R.string.home_confirm_home_delete_read_text)
            HomeAction.DELETE_ALL -> context.getString(R.string.home_confirm_home_delete_all_text)
            HomeAction.DELETE_FEEDS -> context.getString(R.string.home_confirm_home_delete_feeds_text)
        }
        AlertDialog(
            onDismissRequest = { pendingHomeAction = null },
            title = { Text(title) },
            text = { Text(text) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val currentAction = action
                        pendingHomeAction = null
                        if (isRefreshing || busy) {
                            return@TextButton
                        }
                        scope.launch {
                            runCatching {
                                when (currentAction) {
                                    HomeAction.DELETE_READ -> repository.deleteAllReadEntries()
                                    HomeAction.DELETE_ALL -> repository.deleteAllEntries()
                                    HomeAction.DELETE_FEEDS -> repository.deleteAllFeeds()
                                }
                            }.onSuccess { deletedCount ->
                                showInfoMessage(
                                    when (currentAction) {
                                        HomeAction.DELETE_READ -> formatDeleteSummary(
                                            deletedCount = deletedCount,
                                            singularLabel = context.getString(R.string.home_read_entry_label_singular),
                                            pluralLabel = context.getString(R.string.home_read_entry_label_plural),
                                            suffix = context.getString(R.string.home_suffix_deleted)
                                        )
                                        HomeAction.DELETE_ALL -> formatDeleteSummary(
                                            deletedCount = deletedCount,
                                            singularLabel = context.getString(R.string.home_entry_label_singular),
                                            pluralLabel = context.getString(R.string.home_entry_label_plural),
                                            suffix = context.getString(R.string.home_suffix_deleted)
                                        )
                                        HomeAction.DELETE_FEEDS -> formatDeleteSummary(
                                            deletedCount = deletedCount,
                                            singularLabel = context.getString(R.string.home_feed_label_singular),
                                            pluralLabel = context.getString(R.string.home_feed_label_plural),
                                            suffix = context.getString(R.string.home_suffix_deleted)
                                        )
                                    }
                                )
                            }.onFailure {
                                if (it !is CancellationException) {
                                    errorMessage = it.toUserMessage(
                                        context.getString(R.string.home_delete_failed)
                                    )
                                }
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.common_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingHomeAction = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    diagnosticsText?.let { message ->
        AlertDialog(
            onDismissRequest = { diagnosticsText = null },
            title = { Text(stringResource(R.string.home_diagnostics_title)) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { diagnosticsText = null }) {
                    Text(stringResource(R.string.common_ok))
                }
            }
        )
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

    importResultMessage?.let { message ->
        ImportResultDialog(
            message = message,
            onDismiss = { importResultMessage = null }
        )
    }

}

private fun compactMenuItemPadding(): PaddingValues {
    return PaddingValues(horizontal = 12.dp, vertical = 0.dp)
}

private fun Modifier.compactMenuItem(): Modifier {
    return this.heightIn(min = 30.dp)
}

internal fun shouldShowCompletionNotificationForRefresh(
    manualTrigger: Boolean,
    notificationsEnabled: Boolean,
    newArticles: Int,
    isAppInForeground: Boolean
): Boolean {
    return manualTrigger &&
        notificationsEnabled &&
        newArticles > 0 &&
        !isAppInForeground
}

internal fun formatRefreshSummary(context: Context, stats: RefreshRunStats): String {
    return buildString {
        append(context.getString(R.string.home_refresh_summary_base, stats.refreshedFeeds, stats.newArticles))
        if (stats.failedFeeds > 0) {
            append(", ")
            append(context.getString(R.string.home_refresh_summary_failed_part, stats.failedFeeds))
        }
        if (stats.skippedFeeds > 0) {
            append(", ")
            append(context.getString(R.string.home_refresh_summary_skipped_part, stats.skippedFeeds))
        }
    }
}

internal fun formatRefreshSummary(stats: RefreshRunStats): String {
    return buildString {
        append("Aktualisierung beendet: ")
        append(stats.refreshedFeeds)
        append(" aktualisiert, ")
        append(stats.newArticles)
        append(" neu")
        if (stats.failedFeeds > 0) {
            append(", ")
            append(stats.failedFeeds)
            append(" Fehler")
        }
        if (stats.skippedFeeds > 0) {
            append(", ")
            append(stats.skippedFeeds)
            append(" uebersprungen")
        }
    }
}

internal fun formatFeedRefreshSummary(context: Context, newArticles: Int): String {
    return context.getString(R.string.home_feed_refresh_summary, newArticles)
}

internal fun formatFeedRefreshSummary(newArticles: Int): String {
    return "Feed aktualisiert: $newArticles neue Artikel"
}

internal fun formatImportSummary(result: de.lolo.rssreader.data.repository.OpmlImportResult): String {
    return buildString {
        append("OPML importiert: ${result.importedFeeds} importiert, ${result.skippedFeeds} uebersprungen, ${result.failedFeeds} Fehler")
        if (result.failedFeeds > 0 && !result.firstFailedFeedUrl.isNullOrBlank()) {
            append(" (zuerst: ${result.firstFailedFeedUrl})")
        }
    }
}

internal fun formatExportSummary(exportedFeeds: Int): String {
    return "OPML exportiert: $exportedFeeds Feeds"
}

internal fun formatExportSummary(context: Context, exportedFeeds: Int): String {
    return context.getString(R.string.home_export_summary, exportedFeeds)
}

internal fun formatDeleteSummary(
    deletedCount: Int,
    singularLabel: String,
    pluralLabel: String,
    suffix: String
): String {
    return buildString {
        append(deletedCount)
        append(' ')
        append(if (deletedCount == 1) singularLabel else pluralLabel)
        append(' ')
        append(suffix)
    }
}

internal fun formatStateChangeSummary(
    affectedCount: Int,
    singularLabel: String,
    pluralLabel: String,
    suffix: String
): String {
    return buildString {
        append(affectedCount)
        append(' ')
        append(if (affectedCount == 1) singularLabel else pluralLabel)
        append(' ')
        append(suffix)
    }
}

internal fun formatDiagnosticsSummary(
    context: Context,
    snapshot: RepositoryDiagnosticsSnapshot,
    versionLabel: String
): String {
    return buildString {
        appendLine(context.getString(R.string.home_diagnostics_version, versionLabel))
        appendLine(context.getString(R.string.home_diagnostics_feeds, snapshot.feedCount))
        appendLine(context.getString(R.string.home_diagnostics_articles, snapshot.articleCount))
        appendLine(context.getString(R.string.home_diagnostics_fts_rows, snapshot.searchIndexRowCount))
        appendLine(
            context.getString(
                R.string.home_diagnostics_fts_mode,
                if (snapshot.manualFtsMode) {
                    context.getString(R.string.home_diagnostics_fts_manual)
                } else {
                    context.getString(R.string.home_diagnostics_fts_unknown)
                }
            )
        )
        appendLine(
            snapshot.lastRefreshRunStats?.let { stats ->
                context.getString(
                    R.string.home_diagnostics_last_refresh,
                    stats.refreshedFeeds,
                    stats.failedFeeds,
                    stats.skippedFeeds,
                    stats.newArticles
                )
            } ?: context.getString(R.string.home_diagnostics_last_refresh_none)
        )
        appendLine(
            snapshot.lastImportResult?.let { result ->
                context.getString(
                    R.string.home_diagnostics_last_import,
                    result.importedFeeds,
                    result.skippedFeeds,
                    result.failedFeeds
                ).let { summary ->
                    if (result.failedFeeds > 0 && !result.firstFailedFeedUrl.isNullOrBlank()) {
                        context.getString(
                            R.string.home_diagnostics_first_failed_suffix,
                            summary,
                            result.firstFailedFeedUrl
                        )
                    } else {
                        summary
                    }
                }
            } ?: context.getString(R.string.home_diagnostics_last_import_none)
        )
        snapshot.debugLogFilePath?.takeIf { it.isNotBlank() }?.let { path ->
            append(context.getString(R.string.home_diagnostics_debug_log, path))
        }
    }.trim()
}

internal fun formatDiagnosticsSummary(
    snapshot: RepositoryDiagnosticsSnapshot,
    versionLabel: String
): String {
    return buildString {
        appendLine("Version: $versionLabel")
        appendLine("Feeds: ${snapshot.feedCount}")
        appendLine("Artikel: ${snapshot.articleCount}")
        appendLine("FTS-Zeilen: ${snapshot.searchIndexRowCount}")
        appendLine("FTS-Modus: ${if (snapshot.manualFtsMode) "manuell" else "unbekannt"}")
        appendLine(
            snapshot.lastRefreshRunStats?.let { stats ->
                "Letzte Aktualisierung: refreshed=${stats.refreshedFeeds}, failed=${stats.failedFeeds}, skipped=${stats.skippedFeeds}, new=${stats.newArticles}"
            } ?: "Letzte Aktualisierung: keine"
        )
        appendLine(
            snapshot.lastImportResult?.let { result ->
                "Letzter Import: imported=${result.importedFeeds}, skipped=${result.skippedFeeds}, failed=${result.failedFeeds}"
                    .let { summary ->
                        if (result.failedFeeds > 0 && !result.firstFailedFeedUrl.isNullOrBlank()) {
                            "$summary, firstFailed=${result.firstFailedFeedUrl}"
                        } else {
                            summary
                        }
                    }
            } ?: "Letzter Import: keiner"
        )
        snapshot.debugLogFilePath?.takeIf { it.isNotBlank() }?.let { path ->
            append("Debug-Log: ")
            append(path)
        }
    }.trim()
}

private fun currentAppVersionLabel(context: Context): String {
    val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
    val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        packageInfo.longVersionCode
    } else {
        @Suppress("DEPRECATION")
        packageInfo.versionCode.toLong()
    }
    return "${packageInfo.versionName.orEmpty()} ($versionCode)"
}
