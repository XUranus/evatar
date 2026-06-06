package com.evatar.app.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.evatar.app.network.ApiClient
import com.evatar.app.sync.SyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class UiDynamic(
    val id: Int, val title: String, val summary: String, val content: String,
    val category: String, val confidence: Double, val isRead: Boolean, val isPinned: Boolean, val createdAt: String,
)

data class DynamicUiState(
    val items: List<UiDynamic> = emptyList(),
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val serverConnected: Boolean = false,
    val isSyncing: Boolean = false,
    val filter: String = "",
    val unreadCounts: Map<String, Int> = emptyMap(),
)

class DynamicViewModel(app: Application) : AndroidViewModel(app) {
    private val apiClient = ApiClient.getInstance(app)
    private val syncManager = SyncManager(app)
    private val prefs = app.getSharedPreferences("dynamics_cache", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(DynamicUiState())
    val state: StateFlow<DynamicUiState> = _state

    private var nextCursor: Int = 0

    init {
        // Load from cache first for instant display
        loadFromCache()
        // Then refresh from server
        checkConnection()
        refresh()
    }

    fun checkConnection() {
        viewModelScope.launch {
            val connected = withContext(Dispatchers.IO) { apiClient.checkHealth() }
            _state.value = _state.value.copy(serverConnected = connected)
        }
    }

    /**
     * Refresh: reload first page from server, replace local cache.
     */
    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            val filter = _state.value.filter
            val json = withContext(Dispatchers.IO) {
                apiClient.getDynamicsPaginated(cursor = 0, limit = 30, category = filter.ifEmpty { null })
            }
            val items = parseItems(json)
            val hasMore = json.optBoolean("has_more", false)
            nextCursor = json.optInt("next_cursor", 0)

            val newHash = items.hashCode()
            if (newHash != _state.value.items.hashCode() || _state.value.items.isEmpty()) {
                _state.value = _state.value.copy(
                    items = items, loading = false, hasMore = hasMore,
                    unreadCounts = computeUnreadCounts(items),
                )
                saveToCache(items)
            } else {
                _state.value = _state.value.copy(loading = false, hasMore = hasMore)
            }
        }
    }

    /**
     * Load more: append next page using cursor.
     */
    fun loadMore() {
        if (_state.value.loadingMore || !_state.value.hasMore || nextCursor == 0) return

        viewModelScope.launch {
            _state.value = _state.value.copy(loadingMore = true)
            val filter = _state.value.filter
            val json = withContext(Dispatchers.IO) {
                apiClient.getDynamicsPaginated(cursor = nextCursor, limit = 30, category = filter.ifEmpty { null })
            }
            val newItems = parseItems(json)
            val hasMore = json.optBoolean("has_more", false)
            nextCursor = json.optInt("next_cursor", 0)

            // Deduplicate by ID
            val existingIds = _state.value.items.map { it.id }.toSet()
            val uniqueNew = newItems.filter { it.id !in existingIds }

            if (uniqueNew.isNotEmpty()) {
                val merged = _state.value.items + uniqueNew
                _state.value = _state.value.copy(
                    items = merged, loadingMore = false, hasMore = hasMore,
                    unreadCounts = computeUnreadCounts(merged),
                )
                saveToCache(merged)
            } else {
                _state.value = _state.value.copy(loadingMore = false, hasMore = hasMore)
            }
        }
    }

    fun setFilter(filter: String) {
        _state.value = _state.value.copy(filter = filter, items = emptyList(), hasMore = true)
        nextCursor = 0
        refresh()
    }

    fun markAsRead(itemId: Int) {
        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) { apiClient.markDynamicAsRead(itemId) }
            if (success) {
                val updatedItems = _state.value.items.map { item ->
                    if (item.id == itemId) item.copy(isRead = true) else item
                }
                _state.value = _state.value.copy(
                    items = updatedItems,
                    unreadCounts = computeUnreadCounts(updatedItems),
                )
            }
        }
    }

    fun triggerSync() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isSyncing = true)
            withContext(Dispatchers.IO) { syncManager.runSync() }
            refresh()
            _state.value = _state.value.copy(isSyncing = false)
        }
    }

    // ── Private helpers ──

    private fun parseItems(json: JSONObject): List<UiDynamic> {
        val arr = json.optJSONArray("items") ?: JSONArray()
        val list = mutableListOf<UiDynamic>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            list.add(
                UiDynamic(
                    id = obj.optInt("id"),
                    title = obj.optString("title", ""),
                    summary = obj.optString("summary", ""),
                    content = obj.optString("content", ""),
                    category = obj.optString("category", "note"),
                    confidence = obj.optDouble("confidence", 0.5),
                    isRead = obj.optBoolean("is_read", false),
                    isPinned = obj.optBoolean("is_pinned", false),
                    createdAt = obj.optString("created_at", ""),
                )
            )
        }
        return list
    }

    private fun computeUnreadCounts(items: List<UiDynamic>): Map<String, Int> {
        return items.filter { !it.isRead }
            .groupBy { it.category }
            .mapValues { it.value.size }
    }

    // ── Local cache (SharedPreferences) ──

    private fun saveToCache(items: List<UiDynamic>) {
        val jsonArray = JSONArray()
        for (item in items.take(100)) { // Cache at most 100 items
            jsonArray.put(JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("summary", item.summary)
                put("content", item.content)
                put("category", item.category)
                put("confidence", item.confidence)
                put("is_read", item.isRead)
                put("is_pinned", item.isPinned)
                put("created_at", item.createdAt)
            })
        }
        prefs.edit().putString("cached_items", jsonArray.toString()).apply()
    }

    private fun loadFromCache() {
        val cached = prefs.getString("cached_items", null) ?: return
        try {
            val arr = JSONArray(cached)
            val items = mutableListOf<UiDynamic>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                items.add(
                    UiDynamic(
                        id = obj.optInt("id"),
                        title = obj.optString("title", ""),
                        summary = obj.optString("summary", ""),
                        content = obj.optString("content", ""),
                        category = obj.optString("category", "note"),
                        confidence = obj.optDouble("confidence", 0.5),
                        isRead = obj.optBoolean("is_read", false),
                        isPinned = obj.optBoolean("is_pinned", false),
                        createdAt = obj.optString("created_at", ""),
                    )
                )
            }
            if (items.isNotEmpty()) {
                _state.value = _state.value.copy(
                    items = items,
                    loading = false,
                    unreadCounts = computeUnreadCounts(items),
                )
            }
        } catch (_: Exception) {
            // Cache corrupted, ignore
        }
    }
}
