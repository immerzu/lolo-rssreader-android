package com.example.rssreader.data.translation

import com.example.rssreader.debug.DebugLogger
import com.example.rssreader.ui.model.TranslationUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class ArticleTranslationManager(
    private val translationService: TranslationService
) {
    private val mutableStates = MutableStateFlow<Map<Long, TranslationUiState>>(emptyMap())
    val states: StateFlow<Map<Long, TranslationUiState>> = mutableStates.asStateFlow()

    suspend fun translateArticle(
        articleId: Long,
        title: String,
        body: String,
        targetLanguage: String,
        sourceLanguage: String = "auto"
    ): TranslationUiState {
        DebugLogger.i(
            "Translation",
            "Uebersetzung gestartet: articleId=$articleId, tl=$targetLanguage, chars=${body.length}"
        )
        mutableStates.update { current ->
            current + (articleId to TranslationUiState.Loading)
        }

        val nextState = runCatching {
            translationService.translate(
                TranslationRequest(
                    title = title,
                    body = body,
                    sourceLanguage = sourceLanguage,
                    targetLanguage = targetLanguage
                )
            )
        }.fold(
            onSuccess = {
                DebugLogger.i(
                    "Translation",
                    "Uebersetzung erfolgreich: articleId=$articleId, provider=${it.providerLabel}, sl=${it.detectedSourceLanguage}, tl=${it.targetLanguage}"
                )
                TranslationUiState.Success(it)
            },
            onFailure = { throwable ->
                DebugLogger.w("Translation", "Uebersetzung fehlgeschlagen: articleId=$articleId", throwable)
                TranslationUiState.Error(throwable.toTranslationStateMessage())
            }
        )

        mutableStates.update { current ->
            current + (articleId to nextState)
        }

        return nextState
    }

    fun clearArticle(articleId: Long) {
        DebugLogger.d("Translation", "Uebersetzung verworfen: articleId=$articleId")
        mutableStates.update { current ->
            current - articleId
        }
    }
}

private fun Throwable.toTranslationStateMessage(): String {
    return when (this) {
        is TranslationException -> userMessage
        else -> "Die Uebersetzung konnte nicht geladen werden."
    }
}
