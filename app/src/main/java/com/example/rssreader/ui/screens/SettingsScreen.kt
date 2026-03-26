package com.example.rssreader.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import com.example.rssreader.data.repository.FeedRepository
import com.example.rssreader.data.settings.AppPreferences
import com.example.rssreader.data.settings.EntrySortOrder
import com.example.rssreader.data.settings.SettingsRepository
import com.example.rssreader.data.settings.ThemeMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SettingsRouteScreen(
    repository: FeedRepository,
    settings: AppPreferences,
    settingsRepository: SettingsRepository,
    onOpenOverview: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Einstellungen") },
                navigationIcon = {
                    IconButton(onClick = onOpenOverview) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurueck")
                    }
                }
            )
        }
    ) { padding ->
        SettingsScreen(
            repository = repository,
            settings = settings,
            settingsRepository = settingsRepository,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 20.dp)
        )
    }
}

@Composable
fun SettingsScreen(
    repository: FeedRepository,
    settings: AppPreferences,
    settingsRepository: SettingsRepository,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    var busy by rememberSaveable { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    val refreshOptions = listOf(
        0 to "Nur manuell",
        1 to "Stuendlich",
        3 to "Alle 3 Stunden",
        6 to "Alle 6 Stunden",
        12 to "Alle 12 Stunden",
        24 to "Taeglich"
    )
    val themeOptions = listOf(
        ThemeMode.SYSTEM to "Systemstandard",
        ThemeMode.LIGHT to "Tagmodus",
        ThemeMode.DARK to "Nachtmodus"
    )
    val entrySortOptions = listOf(
        EntrySortOrder.NEWEST_FIRST to "Neuste zuerst",
        EntrySortOrder.OLDEST_FIRST to "Aelteste zuerst"
    )
    val articleTextSizeOptions = listOf(
        -2 to "-2",
        -1 to "-1",
        0 to "0",
        1 to "+1",
        2 to "+2"
    )

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                busy = true
                runCatching { importOpml(context, repository, uri) }
                    .onSuccess { result ->
                        message = buildString {
                            append("Import abgeschlossen.")
                            append(" Importiert: ${result.importedFeeds}.")
                            append(" Uebersprungen: ${result.skippedFeeds}.")
                            append(" Fehler: ${result.failedFeeds}.")
                        }
                    }
                    .onFailure {
                        if (it !is CancellationException) {
                            message = it.message ?: "OPML konnte nicht importiert werden."
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
                        message = "OPML exportiert. Enthaltene Feeds: $exportedFeeds."
                    }
                    .onFailure {
                        if (it !is CancellationException) {
                            message = it.message ?: "OPML konnte nicht exportiert werden."
                        }
                    }
                busy = false
            }
        }
    }

    Column(
        modifier = modifier
            .verticalScroll(scrollState)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SettingsCard(title = "Aktualisierung") {
            SettingsToggleRow(
                title = "Beim Start aktualisieren",
                subtitle = "Passt zum klassischen RSS-Reader-Verhalten.",
                checked = settings.refreshOnStart,
                onCheckedChange = {
                    scope.launch { settingsRepository.setRefreshOnStart(it) }
                }
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Intervall fuer Hintergrundaktualisierung", style = MaterialTheme.typography.titleSmall)
                refreshOptions.forEach { (hours, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                scope.launch { settingsRepository.setRefreshIntervalHours(hours) }
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = settings.refreshIntervalHours == hours,
                            onClick = {
                                scope.launch { settingsRepository.setRefreshIntervalHours(hours) }
                            }
                        )
                        Text(label)
                    }
                }
            }
        }

        SettingsCard(title = "OPML") {
            SettingsActionRow(
                title = "Importiere OPML",
                subtitle = "Waehlt eine XML-Datei mit Feeds aus und liest sie ein.",
                actionLabel = "Datei waehlen",
                enabled = !busy,
                onClick = {
                    importLauncher.launch(arrayOf("text/xml", "application/xml", "*/*"))
                }
            )
            SettingsActionRow(
                title = "Exportiere OPML",
                subtitle = "Waehlt Speicherort und Dateinamen fuer den Export aller Feeds.",
                actionLabel = "Speicherort waehlen",
                enabled = !busy,
                onClick = {
                    exportLauncher.launch("rss-reader-feeds.xml")
                }
            )
            if (busy) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }

        SettingsCard(title = "Darstellung") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Farbschema", style = MaterialTheme.typography.titleSmall)
                themeOptions.forEach { (mode, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                scope.launch { settingsRepository.setThemeMode(mode) }
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = settings.themeMode == mode,
                            onClick = {
                                scope.launch { settingsRepository.setThemeMode(mode) }
                            }
                        )
                        Text(label)
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Eintraege anzeigen", style = MaterialTheme.typography.titleSmall)
                entrySortOptions.forEach { (order, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                scope.launch { settingsRepository.setEntrySortOrder(order) }
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = settings.entrySortOrder == order,
                            onClick = {
                                scope.launch { settingsRepository.setEntrySortOrder(order) }
                            }
                        )
                        Text(label)
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Textgroesse Artikel", style = MaterialTheme.typography.titleSmall)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    articleTextSizeOptions.forEach { (offset, label) ->
                        SettingsChoiceChip(
                            label = label,
                            selected = settings.articleBodyTextSizeOffset == offset,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                scope.launch { settingsRepository.setArticleBodyTextSizeOffset(offset) }
                            }
                        )
                    }
                }
            }
            SettingsToggleRow(
                title = "Bilder im Reader anzeigen",
                subtitle = "Entspricht dem klassischen Text-plus-Bild-Lesemodus.",
                checked = settings.showImages,
                onCheckedChange = {
                    scope.launch { settingsRepository.setShowImages(it) }
                }
            )
        }

        SettingsCard(title = "Benachrichtigungen") {
            SettingsToggleRow(
                title = "Benachrichtigungen vormerken",
                subtitle = "Neue Artikel werden nach Hintergrund-Aktualisierungen gemeldet.",
                checked = settings.notificationsEnabled,
                onCheckedChange = {
                    scope.launch { settingsRepository.setNotificationsEnabled(it) }
                }
            )
        }
    }

    message?.let { currentMessage ->
        AlertDialog(
            onDismissRequest = { message = null },
            title = { Text("Hinweis") },
            text = { Text(currentMessage) },
            confirmButton = {
                TextButton(onClick = { message = null }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
private fun SettingsChoiceChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = MaterialTheme.shapes.small,
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        tonalElevation = if (selected) 2.dp else 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        tonalElevation = 2.dp,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                content()
            }
        )
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsActionRow(
    title: String,
    subtitle: String,
    actionLabel: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TextButton(
            onClick = onClick,
            enabled = enabled
        ) {
            Text(actionLabel)
        }
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


========================================================================================================================