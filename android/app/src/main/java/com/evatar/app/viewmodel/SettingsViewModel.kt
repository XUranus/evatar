package com.evatar.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.evatar.app.network.ApiClient
import com.evatar.app.sync.SyncManager
import com.evatar.app.sync.SyncResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val serverUrl: String = "",
    val urlField: String = "",
    val serverConnected: Boolean = false,
    val saved: Boolean = false,
    val urlError: String? = null,
    val lastResult: SyncResult? = null,
    val lastSyncMessage: String? = null,
    val isSyncing: Boolean = false,
    val isKeepAliveRunning: Boolean = false,
)

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val apiClient = ApiClient.getInstance(app)
    private val syncManager = SyncManager(app)
    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state

    init {
        val url = apiClient.getServerUrl()
        _state.value = _state.value.copy(serverUrl = url, urlField = url)
        checkConnection()
    }

    fun checkConnection() {
        viewModelScope.launch {
            val connected = apiClient.checkHealth()
            _state.value = _state.value.copy(serverConnected = connected)
        }
    }

    fun updateUrlField(value: String) {
        _state.value = _state.value.copy(urlField = value, saved = false, urlError = null)
    }

    fun saveUrl() {
        val trimmed = _state.value.urlField.trim()
        val context = getApplication<Application>()
        when {
            trimmed.isEmpty() -> { _state.value = _state.value.copy(urlError = context.getString(com.evatar.app.R.string.onboard_error_empty_url)) }
            !trimmed.startsWith("http://") && !trimmed.startsWith("https://") -> {
                _state.value = _state.value.copy(urlError = context.getString(com.evatar.app.R.string.onboard_error_bad_scheme))
            }
            else -> {
                apiClient.setServerUrl(trimmed)
                _state.value = _state.value.copy(serverUrl = trimmed, saved = true, urlError = null)
                checkConnection()
            }
        }
    }

    fun manualSync() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isSyncing = true)
            val result = syncManager.runSync()
            val message = when {
                result.total == 0 -> "✅ 已是最新，无需同步"
                result.success > 0 && result.failed == 0 -> "✅ 同步完成: ${result.success} 张新截图"
                result.success > 0 && result.failed > 0 -> "⚠️ 同步完成: ${result.success} 成功, ${result.failed} 失败"
                else -> "❌ 同步失败: ${result.failed} 张上传失败"
            }
            _state.value = _state.value.copy(lastResult = result, lastSyncMessage = message, isSyncing = false)
        }
    }

    fun setKeepAlive(running: Boolean) {
        _state.value = _state.value.copy(isKeepAliveRunning = running)
    }
}
