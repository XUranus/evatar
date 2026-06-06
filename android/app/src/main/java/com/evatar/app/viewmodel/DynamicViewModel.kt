package com.evatar.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.evatar.app.network.ApiClient
import com.evatar.app.sync.SyncManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class UiDynamic(
    val id: Int, val title: String, val summary: String, val content: String,
    val category: String, val confidence: Double, val isRead: Boolean, val isPinned: Boolean, val createdAt: String,
)

data class DynamicUiState(
    val items: List<UiDynamic> = emptyList(),
    val loading: Boolean = true,
    val serverConnected: Boolean = false,
    val isSyncing: Boolean = false,
    val filter: String = "",
)

class DynamicViewModel(app: Application) : AndroidViewModel(app) {
    private val apiClient = ApiClient.getInstance(app)
    private val syncManager = SyncManager(app)
    private val _state = MutableStateFlow(DynamicUiState())
    val state: StateFlow<DynamicUiState> = _state

    init {
        checkConnection()
        loadDynamics()
    }

    fun checkConnection() {
        viewModelScope.launch {
            val connected = apiClient.checkHealth()
            _state.value = _state.value.copy(serverConnected = connected)
        }
    }

    fun loadDynamics() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            val filter = _state.value.filter
            val json = apiClient.getDynamics(filter.ifEmpty { null })
            val arr = json.optJSONArray("items") ?: org.json.JSONArray()
            val list = mutableListOf<UiDynamic>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                list.add(UiDynamic(
                    id = obj.optInt("id"), title = obj.optString("title", ""),
                    summary = obj.optString("summary", ""), content = obj.optString("content", ""),
                    category = obj.optString("category", "note"), confidence = obj.optDouble("confidence", 0.5),
                    isRead = obj.optBoolean("is_read", false), isPinned = obj.optBoolean("is_pinned", false),
                    createdAt = obj.optString("created_at", ""),
                ))
            }
            _state.value = _state.value.copy(items = list, loading = false)
        }
    }

    fun setFilter(filter: String) {
        _state.value = _state.value.copy(filter = filter)
        loadDynamics()
    }

    fun triggerSync() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isSyncing = true)
            syncManager.runSync()
            loadDynamics()
            _state.value = _state.value.copy(isSyncing = false)
        }
    }
}
