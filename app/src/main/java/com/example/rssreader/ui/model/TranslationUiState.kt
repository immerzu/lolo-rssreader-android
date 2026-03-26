package com.example.rssreader.ui.model

import com.example.rssreader.data.translation.TranslationResult

sealed interface TranslationUiState {
    data object Idle : TranslationUiState
    data object Loading : TranslationUiState
    data class Success(val result: TranslationResult) : TranslationUiState
    data class Error(val message: String) : TranslationUiState
}
