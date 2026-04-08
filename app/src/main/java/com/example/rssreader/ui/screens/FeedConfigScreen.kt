package com.example.rssreader.ui.screens

import android.util.Patterns
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.rssreader.R
import com.example.rssreader.data.errors.toUserMessage
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
    val context = LocalContext.current
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
                title = {
                    Text(
                        if (isEditMode) {
                            stringResource(R.string.feed_config_title_edit)
                        } else {
                            stringResource(R.string.feed_config_title_add)
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
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
                                        errorMessage = it.toUserMessage(
                                            context.getString(R.string.feed_config_save_failed)
                                        )
                                    }
                                }.also {
                                    loading = false
                                }
                            }
                        },
                        enabled = valid && !loading
                    ) {
                        Text(stringResource(R.string.common_save))
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
                label = { Text(stringResource(R.string.feed_config_url_label)) },
                placeholder = { Text("https://example.com/feed.xml") },
                singleLine = true,
                enabled = !loading
            )
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.feed_config_display_title_label)) },
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
                Text(stringResource(R.string.settings_refresh_wifi_only_title))
            }
            if (isEditMode) {
                TextButton(
                    onClick = { showDeleteDialog = true },
                    enabled = !loading
                ) {
                    Text(stringResource(R.string.feed_config_delete_title))
                }
            }
        }
    }

    if (showDeleteDialog && feedId != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.feed_config_delete_title)) },
            text = { Text(stringResource(R.string.feed_config_delete_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            loading = true
                            runCatching {
                                repository.deleteFeed(feedId)
                            }.onSuccess {
                                showDeleteDialog = false
                                onDone()
                            }.onFailure {
                                if (it !is CancellationException) {
                                    errorMessage = it.toUserMessage(
                                        context.getString(R.string.feed_config_delete_failed)
                                    )
                                }
                            }.also {
                                loading = false
                            }
                        }
                    },
                    enabled = !loading
                ) {
                    Text(stringResource(R.string.common_delete))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteDialog = false },
                    enabled = !loading
                ) {
                    Text(stringResource(R.string.common_cancel))
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
}


