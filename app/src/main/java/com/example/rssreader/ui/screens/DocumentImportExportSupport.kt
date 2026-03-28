package com.example.rssreader.ui.screens

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.example.rssreader.data.errors.RssReaderException
import com.example.rssreader.data.repository.FeedRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal suspend fun importOpmlFromUri(
    context: Context,
    repository: FeedRepository,
    uri: Uri
) = runCatching {
    context.contentResolver.openInputStream(uri)?.use { inputStream ->
        repository.importOpml(inputStream)
    } ?: throw RssReaderException.FileReadFailed()
}.getOrElse { throwable ->
    if (throwable is CancellationException) {
        throw throwable
    }
    if (throwable is RssReaderException) {
        throw throwable
    }
    throw RssReaderException.FileReadFailed(throwable)
}

internal suspend fun exportOpmlToUri(
    context: Context,
    repository: FeedRepository,
    uri: Uri
) = runCatching {
    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
        repository.exportOpml(outputStream)
    } ?: throw RssReaderException.FileWriteFailed()
}.getOrElse { throwable ->
    if (throwable is CancellationException) {
        throw throwable
    }
    if (throwable is RssReaderException) {
        throw throwable
    }
    throw RssReaderException.FileWriteFailed(throwable)
}

internal fun findHostActivity(context: Context): ComponentActivity? {
    var current: Context? = context
    while (current is ContextWrapper) {
        if (current is ComponentActivity) {
            return current
        }
        current = current.baseContext
    }
    return current as? ComponentActivity
}

internal fun launchFromUiScope(
    activity: ComponentActivity?,
    fallbackScope: CoroutineScope,
    block: suspend () -> Unit
) {
    val stableScope = activity?.lifecycleScope
    if (stableScope != null) {
        stableScope.launch { block() }
    } else {
        fallbackScope.launch { block() }
    }
}
