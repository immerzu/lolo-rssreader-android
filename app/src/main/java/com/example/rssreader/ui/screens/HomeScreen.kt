package com.example.rssreader.ui.screens

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rssreader.data.errors.RssReaderException
import com.example.rssreader.data.errors.toUserMessage
import com.example.rssreader.data.repository.FeedRepository
import com.example.rssreader.data.repository.RefreshRunStats
import com.example.rssreader.data.repository.RepositoryDiagnosticsSnapshot
import com.example.rssreader.data.settings.AppPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class HomeAction {
    DELETE_READ,
    DELETE_ALL
}

private enum class FeedConfirmAction {
    DELETE_READ,
    DELETE_ALL,
    DELETE_FEED
}

private const val HOME_REFRESH_INDICATOR_DURATION_MS = 700L

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
    val context = LocalContext.current
    val activity = findHostActivity(context)
    val feeds by repository.observeFeedSummaries().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var isRefreshing by rememberSaveable { mutableStateOf(false) }
    var showRefreshIndicator by rememberSaveable { mutableStateOf(false) }
    var refreshIndicatorToken by rememberSaveable { mutableStateOf(0) }
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
    var importResultMessage by rememberSaveable { mutableStateOf<String?>(null) }
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
        scope.launch {
            delay(HOME_REFRESH_INDICATOR_DURATION_MS)
            if (refreshIndicatorToken == token) {
                showRefreshIndicator = false
            }
        }
    }
    suspend fun runRefreshAll(showSuccessMessage: Boolean, manualTrigger: Boolean) {
        if (isRefreshing) {
            return
        }
        if (manualTrigger && isDefinitelyOffline(context)) {
            errorMessage = NO_NETWORK_CONNECTION_MESSAGE
            return
        }
        isRefreshing = true
        showRefreshIndicatorBriefly()
        try {
            runCatching { repository.refreshAll() }
                .onSuccess { stats ->
                    if (showSuccessMessage) {
                        showInfoMessage(formatRefreshSummary(stats))
                    }
                }
                .onFailure {
                    if (it !is CancellationException) {
                        errorMessage = it.toUserMessage("Aktualisierung fehlgeschlagen.")
                    }
                }
        } finally {
            isRefreshing = false
        }
    }
    val refreshAllFeeds: () -> Unit = {
        scope.launch {
            runRefreshAll(showSuccessMessage = true, manualTrigger = true)
        }
    }
    val pullToRefreshAllFeeds: () -> Unit = {
        scope.launch {
            runRefreshAll(showSuccessMessage = true, manualTrigger = true)
        }
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
                runCatching { importOpmlFromUri(context, repository, uri) }
                    .onSuccess { result ->
                        importResultMessage = formatImportResultDialogMessage(result)
                    }
                .onFailure {
                    if (it !is CancellationException) {
                        errorMessage = it.toUserMessage("OPML konnte nicht importiert werden.")
                    }
                }
                importInProgress = false
                busy = false
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
                        showInfoMessage(formatExportSummary(exportedFeeds))
                    }
                .onFailure {
                    if (it !is CancellationException) {
                        errorMessage = it.toUserMessage("OPML konnte nicht exportiert werden.")
                    }
                }
                busy = false
            }
        }
    }

    LaunchedEffect(settingsLoaded, settings.refreshOnStart) {
        if (settingsLoaded && settings.refreshOnStart && !initialRefreshDone) {
            initialRefreshDone = true
            runRefreshAll(showSuccessMessage = false, manualTrigger = false)
        }
    }

    LaunchedEffect(isMoveMode) {
        if (isMoveMode) {
            selectedFeedMenuId = null
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isMoveMode) {
                            "RSS Reader - Verschiebemodus"
                        } else {
                            "RSS Reader"
                        }
                    )
                },
                actions = {
                    IconButton(
                        onClick = onAddFeed,
                        enabled = !isMoveMode
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Feed anlegen")
                    }
                    IconButton(
                        onClick = refreshAllFeeds,
                        enabled = !isRefreshing && !busy && !isMoveMode
                    ) {
                        RefreshActionIcon(
                            isRefreshing = isRefreshing,
                            contentDescription = "Alle Feeds aktualisieren"
                        )
                    }
                    IconButton(
                        onClick = { topMenuExpanded = true },
                        enabled = !busy
                    ) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Menue")
                    }
                    DropdownMenu(
                        expanded = topMenuExpanded,
                        onDismissRequest = { topMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Suchen", fontSize = 14.sp) },
                            modifier = compactMenuItemModifier(),
                            contentPadding = compactMenuItemPadding(),
                            onClick = {
                                topMenuExpanded = false
                                onOpenSearch()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Einstellungen", fontSize = 14.sp) },
                            modifier = compactMenuItemModifier(),
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
                                        "Verschiebemodus beenden"
                                    } else {
                                        "Verschiebemodus aktivieren"
                                    },
                                    fontSize = 14.sp
                                )
                            },
                            modifier = compactMenuItemModifier(),
                            contentPadding = compactMenuItemPadding(),
                            onClick = {
                                topMenuExpanded = false
                                isMoveMode = !isMoveMode
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Alle Feeds aktualisieren", fontSize = 14.sp) },
                            enabled = !isRefreshing && !busy && !isMoveMode,
                            modifier = compactMenuItemModifier(),
                            contentPadding = compactMenuItemPadding(),
                            onClick = {
                                topMenuExpanded = false
                                refreshAllFeeds()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Alle Artikel als gelesen", fontSize = 14.sp) },
                            enabled = !isMoveMode,
                            modifier = compactMenuItemModifier(),
                            contentPadding = compactMenuItemPadding(),
                            onClick = {
                                topMenuExpanded = false
                                scope.launch {
                                    runCatching { repository.markAllReadGlobally() }
                                        .onSuccess { affectedCount ->
                                            showInfoMessage(
                                                formatStateChangeSummary(
                                                    affectedCount = affectedCount,
                                                    singularLabel = "Artikel",
                                                    pluralLabel = "Artikel",
                                                    suffix = "als gelesen markiert"
                                                )
                                            )
                                        }
                                        .onFailure {
                                            if (it !is CancellationException) {
                                                errorMessage = it.toUserMessage("Eintraege konnten nicht markiert werden.")
                                            }
                                    }
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Alle Artikel als ungelesen", fontSize = 14.sp) },
                            enabled = !isMoveMode,
                            modifier = compactMenuItemModifier(),
                            contentPadding = compactMenuItemPadding(),
                            onClick = {
                                topMenuExpanded = false
                                scope.launch {
                                    runCatching { repository.markAllUnreadGlobally() }
                                        .onSuccess { affectedCount ->
                                            showInfoMessage(
                                                formatStateChangeSummary(
                                                    affectedCount = affectedCount,
                                                    singularLabel = "Artikel",
                                                    pluralLabel = "Artikel",
                                                    suffix = "als ungelesen markiert"
                                                )
                                            )
                                        }
                                        .onFailure {
                                            if (it !is CancellationException) {
                                                errorMessage = it.toUserMessage("Eintraege konnten nicht zurueckgesetzt werden.")
                                            }
                                        }
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Gelesene Artikel loeschen", fontSize = 14.sp) },
                            enabled = !isMoveMode,
                            modifier = compactMenuItemModifier(),
                            contentPadding = compactMenuItemPadding(),
                            onClick = {
                                topMenuExpanded = false
                                pendingHomeAction = HomeAction.DELETE_READ
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Nicht favorisierte Artikel loeschen", fontSize = 14.sp) },
                            enabled = !isMoveMode,
                            modifier = compactMenuItemModifier(),
                            contentPadding = compactMenuItemPadding(),
                            onClick = {
                                topMenuExpanded = false
                                pendingHomeAction = HomeAction.DELETE_ALL
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("OPML importieren", fontSize = 14.sp) },
                            enabled = !isMoveMode,
                            modifier = compactMenuItemModifier(),
                            contentPadding = compactMenuItemPadding(),
                            onClick = {
                                topMenuExpanded = false
                                importLauncher.launch(arrayOf("text/xml", "application/xml", "*/*"))
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("OPML exportieren", fontSize = 14.sp) },
                            enabled = !isMoveMode,
                            modifier = compactMenuItemModifier(),
                            contentPadding = compactMenuItemPadding(),
                            onClick = {
                                topMenuExpanded = false
                                exportLauncher.launch("rss-reader-feeds.xml")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Technischen Status anzeigen", fontSize = 14.sp) },
                            enabled = !busy,
                            modifier = compactMenuItemModifier(),
                            contentPadding = compactMenuItemPadding(),
                            onClick = {
                                topMenuExpanded = false
                                scope.launch {
                                    busy = true
                                    runCatching { repository.diagnosticsSnapshot() }
                                        .onSuccess { snapshot ->
                                            diagnosticsText = formatDiagnosticsSummary(
                                                snapshot = snapshot,
                                                versionLabel = currentAppVersionLabel(context)
                                            )
                                        }
                                        .onFailure {
                                            if (it !is CancellationException) {
                                                errorMessage = it.toUserMessage("Diagnose konnte nicht geladen werden.")
                                            }
                                        }
                                    busy = false
                                }
                            }
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
                .pullRefresh(pullRefreshState)
        ) {
            FeedListScreen(
                feeds = feeds,
                isRefreshing = isRefreshing,
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
                                    errorMessage = it.toUserMessage("Feed konnte nicht verschoben werden.")
                                }
                            }
                    }
                },
                onMoveFeedDown = { feedId ->
                    scope.launch {
                        runCatching { repository.moveFeedDown(feedId) }
                            .onFailure {
                                if (it !is CancellationException) {
                                    errorMessage = it.toUserMessage("Feed konnte nicht verschoben werden.")
                                }
                            }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            PullRefreshIndicator(
                refreshing = showRefreshIndicator,
                state = pullRefreshState,
                modifier = Modifier.align(androidx.compose.ui.Alignment.TopCenter)
            )
        }
    }

    selectedFeedMenuId?.let { feedId ->
        val selectedFeed = feeds.firstOrNull { it.id == feedId }
        AlertDialog(
            onDismissRequest = { selectedFeedMenuId = null },
            title = { Text(selectedFeed?.displayTitle ?: "Feed-Menue") },
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
                                    errorMessage = NO_NETWORK_CONNECTION_MESSAGE
                                    return@launch
                                }
                                runCatching { repository.refreshFeed(feedId) }
                                    .onSuccess { newArticles ->
                                        showInfoMessage(formatFeedRefreshSummary(newArticles))
                                    }
                                    .onFailure {
                                        if (it !is CancellationException) {
                                            errorMessage = it.toUserMessage("Feed konnte nicht aktualisiert werden.")
                                        }
                                    }
                            }
                        }
                    ) {
                        Text("Feed aktualisieren", fontSize = 18.sp)
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
                                                singularLabel = "Artikel des Feeds",
                                                pluralLabel = "Artikel des Feeds",
                                                suffix = "als gelesen markiert"
                                            )
                                        )
                                    }
                                    .onFailure {
                                        if (it !is CancellationException) {
                                            errorMessage = it.toUserMessage("Feed konnte nicht als gelesen markiert werden.")
                                        }
                                    }
                            }
                        }
                    ) {
                        Text("Alle Artikel als gelesen", fontSize = 18.sp)
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
                                                singularLabel = "Artikel des Feeds",
                                                pluralLabel = "Artikel des Feeds",
                                                suffix = "als ungelesen markiert"
                                            )
                                        )
                                    }
                                    .onFailure {
                                        if (it !is CancellationException) {
                                            errorMessage = it.toUserMessage("Feed konnte nicht als ungelesen markiert werden.")
                                        }
                                    }
                            }
                        }
                    ) {
                        Text("Alle Artikel als ungelesen", fontSize = 18.sp)
                    }
                    TextButton(
                        onClick = {
                            selectedFeedMenuId = null
                            pendingFeedActionId = feedId
                            pendingFeedAction = FeedConfirmAction.DELETE_READ
                        }
                    ) {
                        Text("Gelesene Artikel loeschen", fontSize = 18.sp)
                    }
                    TextButton(
                        onClick = {
                            selectedFeedMenuId = null
                            pendingFeedActionId = feedId
                            pendingFeedAction = FeedConfirmAction.DELETE_ALL
                        }
                    ) {
                        Text("Nicht favorisierte Artikel loeschen", fontSize = 18.sp)
                    }
                    TextButton(
                        onClick = {
                            selectedFeedMenuId = null
                            onEditFeed(feedId)
                        }
                    ) {
                        Text("Bearbeiten", fontSize = 18.sp)
                    }
                    TextButton(
                        onClick = {
                            selectedFeedMenuId = null
                            scope.launch {
                                runCatching { repository.resetFeedUpdatedAt(feedId) }
                                    .onSuccess {
                                        showInfoMessage("Aktualisierungsdatum zurueckgesetzt")
                                    }
                                    .onFailure {
                                        if (it !is CancellationException) {
                                            errorMessage = it.toUserMessage("Aktualisierungsdatum konnte nicht zurueckgesetzt werden.")
                                        }
                                    }
                            }
                        }
                    ) {
                        Text("Aktualisierungsdatum zuruecksetzen", fontSize = 18.sp)
                    }
                    TextButton(
                        onClick = {
                            selectedFeedMenuId = null
                            pendingFeedActionId = feedId
                            pendingFeedAction = FeedConfirmAction.DELETE_FEED
                        }
                    ) {
                        Text("Loeschen", fontSize = 18.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedFeedMenuId = null }) {
                    Text("Schliessen", fontSize = 18.sp)
                }
            },
            dismissButton = null
        )
    }

    if (pendingFeedAction != null && pendingFeedActionId != null) {
        val action = pendingFeedAction!!
        val feedId = pendingFeedActionId!!
        val title = when (action) {
            FeedConfirmAction.DELETE_READ -> "Gelesene Eintraege loeschen"
            FeedConfirmAction.DELETE_ALL -> "Alle Eintraege loeschen"
            FeedConfirmAction.DELETE_FEED -> "Feed loeschen"
        }
        val text = when (action) {
            FeedConfirmAction.DELETE_READ -> "Alle gelesenen, nicht favorisierten Eintraege dieses Feeds wirklich loeschen?"
            FeedConfirmAction.DELETE_ALL -> "Alle nicht favorisierten Eintraege dieses Feeds wirklich loeschen?"
            FeedConfirmAction.DELETE_FEED -> "Diesen Feed wirklich loeschen? Dabei werden auch favorisierte Eintraege dieses Feeds entfernt."
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
                                            singularLabel = "gelesener Eintrag",
                                            pluralLabel = "gelesene Eintraege",
                                            suffix = "des Feeds geloescht"
                                        )
                                        FeedConfirmAction.DELETE_ALL -> formatDeleteSummary(
                                            deletedCount = result as Int,
                                            singularLabel = "Eintrag",
                                            pluralLabel = "Eintraege",
                                            suffix = "des Feeds geloescht"
                                        )
                                        FeedConfirmAction.DELETE_FEED -> "Feed geloescht"
                                    }
                                )
                            }.onFailure {
                                if (it !is CancellationException) {
                                    errorMessage = it.toUserMessage("Aktion fehlgeschlagen.")
                                }
                            }
                        }
                    }
                ) {
                    Text("Loeschen")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pendingFeedAction = null
                        pendingFeedActionId = null
                    }
                ) {
                    Text("Abbrechen")
                }
            }
        )
    }

    pendingHomeAction?.let { action ->
        val title = when (action) {
            HomeAction.DELETE_READ -> "Gelesene Eintraege loeschen"
            HomeAction.DELETE_ALL -> "Alle Eintraege loeschen"
        }
        val text = when (action) {
            HomeAction.DELETE_READ -> "Alle gelesenen, nicht favorisierten Eintraege wirklich loeschen?"
            HomeAction.DELETE_ALL -> "Alle nicht favorisierten Eintraege wirklich loeschen?"
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
                        scope.launch {
                            runCatching {
                                when (currentAction) {
                                    HomeAction.DELETE_READ -> repository.deleteAllReadEntries()
                                    HomeAction.DELETE_ALL -> repository.deleteAllEntries()
                                }
                            }.onSuccess { deletedCount ->
                                showInfoMessage(
                                    when (currentAction) {
                                        HomeAction.DELETE_READ -> formatDeleteSummary(
                                            deletedCount = deletedCount,
                                            singularLabel = "gelesener Eintrag",
                                            pluralLabel = "gelesene Eintraege",
                                            suffix = "geloescht"
                                        )
                                        HomeAction.DELETE_ALL -> formatDeleteSummary(
                                            deletedCount = deletedCount,
                                            singularLabel = "Eintrag",
                                            pluralLabel = "Eintraege",
                                            suffix = "geloescht"
                                        )
                                    }
                                )
                            }.onFailure {
                                if (it !is CancellationException) {
                                    errorMessage = it.toUserMessage("Loeschen fehlgeschlagen.")
                                }
                            }
                        }
                    }
                ) {
                    Text("Loeschen")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingHomeAction = null }) {
                    Text("Abbrechen")
                }
            }
        )
    }

    diagnosticsText?.let { message ->
        AlertDialog(
            onDismissRequest = { diagnosticsText = null },
            title = { Text("Diagnose") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { diagnosticsText = null }) {
                    Text("OK")
                }
            }
        )
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

    if (importInProgress) {
        ImportProgressDialog()
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

private fun compactMenuItemModifier(): Modifier {
    return Modifier.heightIn(min = 30.dp)
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

internal fun formatFeedRefreshSummary(newArticles: Int): String {
    return "Feed aktualisiert: $newArticles neue Artikel"
}

internal fun formatImportSummary(result: com.example.rssreader.data.repository.OpmlImportResult): String {
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
    return "${packageInfo.versionName.orEmpty()} (${packageInfo.longVersionCode})"
}
