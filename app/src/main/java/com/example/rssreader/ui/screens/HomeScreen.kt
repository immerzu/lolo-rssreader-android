package com.example.rssreader.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.unit.sp
import com.example.rssreader.data.repository.FeedRepository
import com.example.rssreader.data.settings.AppPreferences
import kotlinx.coroutines.CancellationException
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
    val feeds by repository.observeFeedSummaries().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var isRefreshing by rememberSaveable { mutableStateOf(false) }
    var initialRefreshDone by rememberSaveable { mutableStateOf(false) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedFeedMenuId by rememberSaveable { mutableStateOf<Long?>(null) }
    var topMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var pendingHomeAction by rememberSaveable { mutableStateOf<HomeAction?>(null) }
    var pendingFeedAction by rememberSaveable { mutableStateOf<FeedConfirmAction?>(null) }
    var pendingFeedActionId by rememberSaveable { mutableStateOf<Long?>(null) }
    var busy by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val showInfoMessage: (String) -> Unit = { message ->
        scope.launch { snackbarHostState.showSnackbar(message) }
    }
    val refreshAllFeeds: () -> Unit = {
        scope.launch {
            if (isRefreshing) {
                return@launch
            }
            isRefreshing = true
            runCatching { repository.refreshAll() }
                .onSuccess { stats ->
                    showInfoMessage(
                        buildString {
                            append(stats.refreshedFeeds)
                            append(" Feeds aktualisiert")
                            append(", ")
                            append(stats.newArticles)
                            append(" neue Artikel")
                        }
                    )
                }
                .onFailure {
                    if (it !is CancellationException) {
                        errorMessage = it.message ?: "Aktualisierung fehlgeschlagen."
                    }
                }
            isRefreshing = false
        }
    }
    val pullToRefreshAllFeeds: () -> Unit = {
        scope.launch {
            if (isRefreshing) {
                return@launch
            }
            isRefreshing = true
            runCatching { repository.refreshAll() }
                .onFailure {
                    if (it !is CancellationException) {
                        errorMessage = it.message ?: "Aktualisierung fehlgeschlagen."
                    }
                }
            isRefreshing = false
        }
    }
    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = pullToRefreshAllFeeds
    )

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                busy = true
                runCatching { importOpml(context, repository, uri) }
                    .onSuccess { result ->
                        showInfoMessage(
                            buildString {
                                append("Importiert: ${result.importedFeeds}")
                                append(", uebersprungen: ${result.skippedFeeds}")
                                append(", Fehler: ${result.failedFeeds}")
                            }
                        )
                    }
                    .onFailure {
                        if (it !is CancellationException) {
                            errorMessage = it.message ?: "OPML konnte nicht importiert werden."
                        }
                    }
                busy = false
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/xml")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                busy = true
                runCatching { exportOpml(context, repository, uri) }
                    .onSuccess { exportedFeeds ->
                        showInfoMessage("OPML exportiert, enthaltene Feeds: $exportedFeeds")
                    }
                    .onFailure {
                        if (it !is CancellationException) {
                            errorMessage = it.message ?: "OPML konnte nicht exportiert werden."
                        }
                    }
                busy = false
            }
        }
    }

    LaunchedEffect(settingsLoaded, settings.refreshOnStart) {
        if (settingsLoaded && settings.refreshOnStart && !initialRefreshDone) {
            initialRefreshDone = true
            isRefreshing = true
            runCatching { repository.refreshAll() }
                .onFailure {
                    if (it !is CancellationException) {
                        errorMessage = it.message ?: "Aktualisierung fehlgeschlagen."
                    }
                }
            isRefreshing = false
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("RSS Reader") },
                actions = {
                    IconButton(onClick = onAddFeed) {
                        Icon(Icons.Default.Add, contentDescription = "Feed anlegen")
                    }
                    IconButton(
                        onClick = refreshAllFeeds,
                        enabled = !isRefreshing && !busy
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Alle Feeds aktualisieren")
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
                            text = { Text("Suchen", fontSize = 18.sp) },
                            onClick = {
                                topMenuExpanded = false
                                onOpenSearch()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Einstellungen", fontSize = 18.sp) },
                            onClick = {
                                topMenuExpanded = false
                                onOpenSettings()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Alle gelesen", fontSize = 18.sp) },
                            onClick = {
                                topMenuExpanded = false
                                scope.launch {
                                    runCatching { repository.markAllReadGlobally() }
                                        .onSuccess {
                                            showInfoMessage("Alle Eintraege als gelesen markiert")
                                        }
                                        .onFailure {
                                            if (it !is CancellationException) {
                                                errorMessage = it.message ?: "Eintraege konnten nicht markiert werden."
                                            }
                                    }
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Alle ungelesen", fontSize = 18.sp) },
                            onClick = {
                                topMenuExpanded = false
                                scope.launch {
                                    runCatching { repository.markAllUnreadGlobally() }
                                        .onSuccess {
                                            showInfoMessage("Alle Eintraege als ungelesen markiert")
                                        }
                                        .onFailure {
                                            if (it !is CancellationException) {
                                                errorMessage = it.message ?: "Eintraege konnten nicht zurueckgesetzt werden."
                                            }
                                        }
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Importiere OPML", fontSize = 18.sp) },
                            onClick = {
                                topMenuExpanded = false
                                importLauncher.launch(arrayOf("text/xml", "application/xml", "*/*"))
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Exportiere OPML", fontSize = 18.sp) },
                            onClick = {
                                topMenuExpanded = false
                                exportLauncher.launch("rss-reader-feeds.xml")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Alle gelesenen Eintraege loeschen", fontSize = 18.sp) },
                            onClick = {
                                topMenuExpanded = false
                                pendingHomeAction = HomeAction.DELETE_READ
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Alle Eintraege loeschen", fontSize = 18.sp) },
                            onClick = {
                                topMenuExpanded = false
                                pendingHomeAction = HomeAction.DELETE_ALL
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
                onOpenFeed = onOpenFeed,
                onOpenFeedMenu = { feedId ->
                    selectedFeedMenuId = feedId
                },
                modifier = Modifier.fillMaxSize()
            )
            PullRefreshIndicator(
                refreshing = isRefreshing,
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
                                runCatching { repository.refreshFeed(feedId) }
                                    .onSuccess { newArticles ->
                                        showInfoMessage("Feed aktualisiert, $newArticles neue Artikel")
                                    }
                                    .onFailure {
                                        if (it !is CancellationException) {
                                            errorMessage = it.message ?: "Feed konnte nicht aktualisiert werden."
                                        }
                                    }
                            }
                        }
                    ) {
                        Text("Aktualisieren", fontSize = 18.sp)
                    }
                    TextButton(
                        onClick = {
                            selectedFeedMenuId = null
                            scope.launch {
                                runCatching { repository.markAllRead(feedId) }
                                    .onSuccess {
                                        showInfoMessage("Feed als gelesen markiert")
                                    }
                                    .onFailure {
                                        if (it !is CancellationException) {
                                            errorMessage = it.message ?: "Feed konnte nicht als gelesen markiert werden."
                                        }
                                    }
                            }
                        }
                    ) {
                        Text("Als gelesen markieren", fontSize = 18.sp)
                    }
                    TextButton(
                        onClick = {
                            selectedFeedMenuId = null
                            scope.launch {
                                runCatching { repository.markAllUnread(feedId) }
                                    .onSuccess {
                                        showInfoMessage("Feed als ungelesen markiert")
                                    }
                                    .onFailure {
                                        if (it !is CancellationException) {
                                            errorMessage = it.message ?: "Feed konnte nicht als ungelesen markiert werden."
                                        }
                                    }
                            }
                        }
                    ) {
                        Text("Als ungelesen markieren", fontSize = 18.sp)
                    }
                    TextButton(
                        onClick = {
                            selectedFeedMenuId = null
                            pendingFeedActionId = feedId
                            pendingFeedAction = FeedConfirmAction.DELETE_READ
                        }
                    ) {
                        Text("Alle gelesenen Eintraege loeschen", fontSize = 18.sp)
                    }
                    TextButton(
                        onClick = {
                            selectedFeedMenuId = null
                            pendingFeedActionId = feedId
                            pendingFeedAction = FeedConfirmAction.DELETE_ALL
                        }
                    ) {
                        Text("Alle Eintraege loeschen", fontSize = 18.sp)
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
                                            errorMessage = it.message ?: "Aktualisierungsdatum konnte nicht zurueckgesetzt werden."
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
                            }.onSuccess {
                                showInfoMessage(
                                    when (currentAction) {
                                        FeedConfirmAction.DELETE_READ -> "Gelesene Eintraege des Feeds geloescht"
                                        FeedConfirmAction.DELETE_ALL -> "Eintraege des Feeds geloescht"
                                        FeedConfirmAction.DELETE_FEED -> "Feed geloescht"
                                    }
                                )
                            }.onFailure {
                                if (it !is CancellationException) {
                                    errorMessage = it.message ?: "Aktion fehlgeschlagen."
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
                            }.onSuccess {
                                showInfoMessage(
                                    when (currentAction) {
                                        HomeAction.DELETE_READ -> "Gelesene Eintraege geloescht"
                                        HomeAction.DELETE_ALL -> "Eintraege geloescht"
                                    }
                                )
                            }.onFailure {
                                if (it !is CancellationException) {
                                    errorMessage = it.message ?: "Loeschen fehlgeschlagen."
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

}

private suspend fun importOpml(
    context: Context,
    repository: FeedRepository,
    uri: Uri
) = context.contentResolver.openInputStream(uri)?.use { inputStream ->
    repository.importOpml(inputStream)
} ?: error("Die gewaehlte Datei konnte nicht gelesen werden.")

private suspend fun exportOpml(
    context: Context,
    repository: FeedRepository,
    uri: Uri
) = context.contentResolver.openOutputStream(uri)?.use { outputStream ->
    repository.exportOpml(outputStream)
} ?: error("Die Zieldatei konnte nicht geschrieben werden.")
