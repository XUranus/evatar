package com.evatar.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.evatar.app.network.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class UiMessage(val role: String, val content: String, val toolCalls: String? = null)
data class UiConversation(val id: String, val title: String, val lastMessage: String, val messageCount: Int)

data class ChatUiState(
    val conversations: List<UiConversation> = emptyList(),
    val messages: List<UiMessage> = emptyList(),
    val activeConvId: String? = null,
    val sending: Boolean = false,
    val loading: Boolean = true,
    val lastFailedMessage: String? = null,
)

class ChatViewModel(app: Application) : AndroidViewModel(app) {
    private val apiClient = ApiClient.getInstance(app)
    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state

    init { loadConversations() }

    fun loadConversations() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            val arr = apiClient.getConversations()
            val list = mutableListOf<UiConversation>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                list.add(UiConversation(
                    id = obj.optString("id"), title = obj.optString("title", "新对话"),
                    lastMessage = obj.optString("last_message", ""), messageCount = obj.optInt("message_count", 0)
                ))
            }
            _state.value = _state.value.copy(conversations = list, loading = false)
        }
    }

    fun loadMessages(convId: String) {
        viewModelScope.launch {
            val arr = apiClient.getConversationMessages(convId)
            val list = mutableListOf<UiMessage>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                list.add(UiMessage(
                    role = obj.optString("role", "user"), content = obj.optString("content", ""),
                    toolCalls = if (obj.has("tool_calls") && !obj.isNull("tool_calls")) obj.optString("tool_calls") else null
                ))
            }
            _state.value = _state.value.copy(activeConvId = convId, messages = list)
        }
    }

    fun selectConversation(convId: String) { loadMessages(convId) }

    fun startNewConversation() {
        _state.value = _state.value.copy(
            activeConvId = "", // sentinel for new conversation
            messages = listOf(UiMessage("assistant", "你好！我是 Evatar AI 助手。\n\n搜索截图知识库、搜索互联网、上传图片分析"))
        )
    }

    fun goBackToList() {
        _state.value = _state.value.copy(activeConvId = null, messages = emptyList())
        loadConversations()
    }

    fun sendMessage(text: String, isRetry: Boolean = false) {
        val s = _state.value
        _state.value = s.copy(sending = true, lastFailedMessage = null,
            messages = if (isRetry) s.messages else s.messages + UiMessage("user", text))

        viewModelScope.launch {
            val result = apiClient.sendMessage(text, s.activeConvId)
            if (result.success && result.data != null) {
                val newConvId = result.data.optString("conversation_id")
                val msg = result.data.optJSONObject("message")
                val newMsg = if (msg != null) UiMessage(
                    role = "assistant", content = msg.optString("content", ""),
                    toolCalls = if (msg.has("tool_calls") && !msg.isNull("tool_calls")) msg.optString("tool_calls") else null
                ) else null
                _state.value = _state.value.copy(
                    activeConvId = newConvId,
                    messages = _state.value.messages + listOfNotNull(newMsg),
                    sending = false
                )
                loadConversations()
            } else {
                _state.value = _state.value.copy(
                    lastFailedMessage = text, sending = false,
                    messages = _state.value.messages + UiMessage("assistant", "发送失败: ${result.errorMessage}")
                )
            }
        }
    }

    fun cancelRetry() { _state.value = _state.value.copy(lastFailedMessage = null) }

    fun deleteConversation(convId: String) {
        viewModelScope.launch {
            apiClient.deleteConversation(convId)
            loadConversations()
        }
    }
}
