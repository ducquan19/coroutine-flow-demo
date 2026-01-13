package com.example.dictionary.utils

sealed class UiState {
    object Idle : UiState()
    object Loading : UiState()
    data class Success(
        val data: List<String>,
        val threadInfo: String = "",
        val executionTimeMs: Long = 0
    ) : UiState()
    data class Error(val message: String) : UiState()
}
