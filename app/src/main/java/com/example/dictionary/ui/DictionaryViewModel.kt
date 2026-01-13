package com.example.dictionary.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dictionary.data.api.ApiClient
import com.example.dictionary.data.repository.DictionaryRepository
import com.example.dictionary.data.repository.SearchResult
import com.example.dictionary.utils.UiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Data class để lưu log events cho demo
data class FlowEvent(
    val timestamp: String,
    val operator: String,
    val message: String,
    val type: EventType = EventType.INFO
)

enum class EventType {
    INFO, SUCCESS, ERROR, CANCEL, COROUTINE
}

class DictionaryViewModel : ViewModel() {

    private val repository = DictionaryRepository(ApiClient.api)

    // Flow events log để hiển thị trên UI
    private val _flowEvents = MutableStateFlow<List<FlowEvent>>(emptyList())
    val flowEvents: StateFlow<List<FlowEvent>> = _flowEvents.asStateFlow()

    // Đếm số lần API được gọi thực sự
    private val _apiCallCount = MutableStateFlow(0)
    val apiCallCount: StateFlow<Int> = _apiCallCount.asStateFlow()

    // Đếm số lần user gõ phím
    private val _keystrokeCount = MutableStateFlow(0)
    val keystrokeCount: StateFlow<Int> = _keystrokeCount.asStateFlow()

    // Đếm số lần request bị cancel
    private val _cancelCount = MutableStateFlow(0)
    val cancelCount: StateFlow<Int> = _cancelCount.asStateFlow()

    private fun addEvent(operator: String, message: String, type: EventType = EventType.INFO) {
        val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
        val event = FlowEvent(
            timestamp = timeFormat.format(Date()),
            operator = operator,
            message = message,
            type = type
        )
        _flowEvents.value = (_flowEvents.value + event).takeLast(10) // Giữ 10 events gần nhất
    }

    private val _query = MutableStateFlow("")

    fun onQueryChange(text: String) {
        _keystrokeCount.value++
        addEvent("MutableStateFlow", "emit(\"$text\") - Keystroke #${_keystrokeCount.value}")
        _query.value = text
    }

    fun clearLogs() {
        _flowEvents.value = emptyList()
        _apiCallCount.value = 0
        _keystrokeCount.value = 0
        _cancelCount.value = 0
    }

    private var currentRequestId = 0

    val uiState: StateFlow<UiState> = _query
        // DEBOUNCE: Chờ 500ms sau khi user ngừng gõ
        .debounce(500)
        .onEach { word ->
            if (word.isNotBlank()) {
                addEvent("debounce(500ms)", "Passed: \"$word\" - Waited 500ms ✓")
            }
        }
        // DISTINCT: Chỉ tiếp tục nếu query khác với query trước
        .distinctUntilChanged()
        .onEach { word ->
            if (word.isNotBlank()) {
                addEvent("distinctUntilChanged", "New unique query: \"$word\"")
            }
        }
        // FLATMAPLATEST: Cancel request cũ, chỉ lấy kết quả mới nhất
        .flatMapLatest { word ->
            if (word.isBlank()) {
                addEvent("flatMapLatest", "Empty query → Idle state")
                kotlinx.coroutines.flow.flowOf(UiState.Idle)
            } else {
                _apiCallCount.value++
                val requestId = ++currentRequestId
                addEvent(
                    "flatMapLatest",
                    "🚀 Coroutine #$requestId launched for \"$word\"",
                    EventType.COROUTINE
                )

                repository.search(word)
                    .map<SearchResult, UiState> { result ->
                        addEvent(
                            "Coroutine",
                            "⚡ ${result.threadInfo} | ${result.executionTimeMs}ms",
                            EventType.COROUTINE
                        )
                        addEvent("map", "✅ Success: ${result.definitions.size} definitions", EventType.SUCCESS)
                        UiState.Success(
                            data = result.definitions,
                            threadInfo = result.threadInfo,
                            executionTimeMs = result.executionTimeMs
                        )
                    }
                    .onStart {
                        addEvent("onStart", "⏳ Loading state emitted")
                        emit(UiState.Loading)
                    }
                    .onCompletion { cause ->
                        if (cause is CancellationException) {
                            _cancelCount.value++
                            addEvent(
                                "Cancellation",
                                "🛑 Coroutine #$requestId CANCELLED (new query arrived)",
                                EventType.CANCEL
                            )
                        }
                    }
                    .catch { error ->
                        if (error !is CancellationException) {
                            addEvent("catch", "❌ Error: ${error.message}", EventType.ERROR)
                            emit(UiState.Error(error.message ?: "An unexpected error occurred"))
                        }
                    }
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            UiState.Idle
        )
}
