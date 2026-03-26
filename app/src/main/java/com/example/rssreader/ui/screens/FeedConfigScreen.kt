package com.example.rssreader.ui.screens

import android.util.Patterns
import java.net.UnknownHostException
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.rssreader.data.repository.FeedRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedConfigScreen(
    repository: FeedRepository,
    feedId: Long?,
    onBack: () -> Unit,
    onDone: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val isEditMode = feedId != null
    var url by rememberSaveable { mutableStateOf("") }
    var title by rememberSaveable { mutableStateOf("") }
    var wifiOnly by rememberSaveable { mutableStateOf(false) }
    var loading by rememberSaveable { mutableStateOf(isEditMode) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(feedId) {
        if (feedId != null) {
            val feed = repository.getFeed(feedId)
            if (feed != null) {
                url = feed.url
                title = feed.customTitle.orEmpty()
                wifiOnly = feed.wifiOnly
            }
            loading = false
        }
    }

    val valid = Patterns.WEB_URL.matcher(url.trim()).matches()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditMode) "Feed bearbeiten" else "Feed anlegen") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurueck")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            scope.launch {
                                loading = true
                                runCatching {
                                    if (feedId == null) {
                                        repository.addFeed(
                                            url = url.trim(),
                                            customTitle = title.trim().ifBlank { null },
                                            wifiOnly = wifiOnly
                                        )
                                    } else {
                                        repository.updateFeed(
                                            feedId = feedId,
                                            url = url.trim(),
                                            customTitle = title.trim().ifBlank { null },
                                            wifiOnly = wifiOnly
                                        )
                                    }
                                }.onSuccess {
                                    onDone()
                                }.onFailure {
                                    if (it !is CancellationException) {
                                        errorMessage = it.toUserMessage()
                                    }
                                }.also {
                                    loading = false
                                }
                            }
                        },
                        enabled = valid && !loading
                    ) {
                        Text("Speichern")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Feed-URL") },
                placeholder = { Text("https://example.com/feed.xml") },
                singleLine = true,
                enabled = !loading
            )
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Anzeigetitel") },
                singleLine = true,
                enabled = !loading
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = wifiOnly,
                    onCheckedChange = { wifiOnly = it },
                    enabled = !loading
                )
                Text("Nur ueber WLAN aktualisieren")
            }
            if (isEditMode) {
                TextButton(
                    onClick = { showDeleteDialog = true },
                    enabled = !loading
                ) {
                    Text("Feed loeschen")
                }
            }
        }
    }

    if (showDeleteDialog && feedId != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Feed loeschen") },
            text = { Text("Der Feed und seine Artikel werden entfernt.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            repository.deleteFeed(feedId)
                            showDeleteDialog = false
                            onDone()
                        }
                    }
                ) {
                    Text("Loeschen")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
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

private fun Throwable.toUserMessage(): String {
    return when (this) {
        is UnknownHostException -> "Die Feed-Adresse konnte nicht aufgeloest werden."
        else -> message ?: "Feed konnte nicht gespeichert werden."
    }
}
