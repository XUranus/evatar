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
        when {
            trimmed.isEmpty() -> { _state.value = _state.value.copy(urlError = "请输入服务端地址") }
            !trimmed.startsWith("http://") && !trimmed.startsWith("https://") -> {
                _state.value = _state.value.copy(urlError = "地址必须以 http:// 或 https:// 开头")
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
            _state.value = _state.value.copy(lastResult = result, isSyncing = false)
        }
    }

    fun setKeepAlive(running: Boolean) {
        _state.value = _state.value.copy(isKeepAliveRunning = running)
    }
}
