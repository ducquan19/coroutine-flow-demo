package com.example.dictionary.data.repository

import com.example.dictionary.data.api.DictionaryApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext

// Data class để trả về kết quả kèm thông tin coroutine
data class SearchResult(
    val definitions: List<String>,
    val threadInfo: String,
    val executionTimeMs: Long,
    val wasCancelled: Boolean = false
)

class DictionaryRepository(
    private val api: DictionaryApi
) {

    fun search(word: String): Flow<SearchResult> = flow {
        if (word.isBlank()) {
            emit(SearchResult(emptyList(), "", 0))
            return@flow
        }

        val startTime = System.currentTimeMillis()

        try {
            // Demo: Thêm delay để thấy rõ async behavior và cancellation
            // Bạn có thể gõ nhanh để thấy request cũ bị cancel
            delay(300) // Giả lập network latency

            // Kiểm tra coroutine còn active không (demo cancellation)
            currentCoroutineContext().ensureActive()

            // Lấy thread name ngay trước khi gọi API
            val threadName = Thread.currentThread().name

            val response = api.searchWord(word) // Suspend function - chạy trên IO thread!

            // Kiểm tra lại sau API call
            currentCoroutineContext().ensureActive()

            val definitions = response
                .flatMap { it.meanings }
                .flatMap { it.definitions }
                .map { it.definition }

            val executionTime = System.currentTimeMillis() - startTime

            if (definitions.isEmpty()) {
                throw Exception("No definitions found for '$word'")
            }

            emit(SearchResult(
                definitions = definitions,
                threadInfo = "Thread: $threadName",
                executionTimeMs = executionTime
            ))

        } catch (e: CancellationException) {
            // Coroutine bị cancel - ném lại để Flow xử lý đúng
            throw e
        } catch (e: retrofit2.HttpException) {
            if (e.code() == 404) {
                throw Exception("Word not found. Please check spelling.")
            } else {
                throw Exception("Network error: ${e.message}")
            }
        } catch (e: java.io.IOException) {
            throw Exception("Connection error. Check your internet.")
        } catch (e: Exception) {
            throw Exception(e.message ?: "Unknown error occurred")
        }
    }.flowOn(Dispatchers.IO) // ← Chạy trên IO Dispatcher (background thread)
}
