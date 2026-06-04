package com.evatar.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evatar.app.network.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class UiMessage(
    val role: String,
    val content: String,
    val toolCalls: String? = null,
)

data class UiConversation(
    val id: String,
    val title: String,
    val lastMessage: String,
)

@Composable
fun ChatTab(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val apiClient = remember { ApiClient.getInstance(context) }

    var conversations by remember { mutableStateOf(listOf<UiConversation>()) }
    var activeConvId by remember { mutableStateOf<String?>(null) }
    var messages by remember { mutableStateOf(listOf<UiMessage>()) }
    var input by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    var showConvList by remember { mutableStateOf(true) }

    fun loadConversations() {
        scope.launch {
            val arr = withContext(Dispatchers.IO) { apiClient.getConversations() }
            val list = mutableListOf<UiConversation>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                list.add(UiConversation(
                    id = obj.optString("id"),
                    title = obj.optString("title", "新对话"),
                    lastMessage = obj.optString("last_message", ""),
                ))
            }
            conversations = list
        }
    }

    fun loadMessages(convId: String) {
        scope.launch {
            val arr = withContext(Dispatchers.IO) { apiClient.getConversationMessages(convId) }
            val list = mutableListOf<UiMessage>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                list.add(UiMessage(
                    role = obj.optString("role", "user"),
                    content = obj.optString("content", ""),
                    toolCalls = if (obj.isNull("tool_calls")) null else obj.optString("tool_calls", ""),
                ))
            }
            messages = list
        }
    }

    LaunchedEffect(Unit) { loadConversations() }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    var lastFailedMessage by remember { mutableStateOf<String?>(null) }

    fun doSend(text: String) {
        sending = true
        lastFailedMessage = null
        messages = messages + UiMessage("user", text)

        scope.launch {
            val result = withContext(Dispatchers.IO) { apiClient.sendMessage(text, activeConvId) }
            if (result.success && result.data != null) {
                activeConvId = result.data.optString("conversation_id")
                val msg = result.data.optJSONObject("message")
                if (msg != null) {
                    messages = messages + UiMessage(
                        role = "assistant",
                        content = msg.optString("content", ""),
                        toolCalls = msg.optJSONArray("tool_calls")?.toString(),
                    )
                }
                loadConversations()
            } else {
                lastFailedMessage = text
                messages = messages + UiMessage("assistant", "❌ 发送失败\n${result.errorMessage}")
            }
            sending = false
        }
    }

    fun sendMessage() {
        val text = input.trim()
        if (text.isEmpty() || sending) return
        input = ""
        doSend(text)
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Conversation list panel
        AnimatedVisibility(
            visible = showConvList && activeConvId == null,
            enter = slideInHorizontally(),
            exit = slideOutHorizontally(),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("对话列表", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    IconButton(onClick = {
                        activeConvId = null
                        messages = listOf(UiMessage("assistant", "你好！我是 Evatar AI 助手。\n\n🔍 搜索截图知识库\n🌐 搜索互联网\n\n试试问我什么吧！"))
                        showConvList = false
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "新对话")
                    }
                }

                if (conversations.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("暂无对话，点击右上角 + 创建", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(conversations, key = { it.id }) { conv ->
                            ConversationItem(
                                conv = conv,
                                onClick = {
                                    activeConvId = conv.id
                                    loadMessages(conv.id)
                                    showConvList = false
                                },
                                onDelete = {
                                    scope.launch {
                                        withContext(Dispatchers.IO) { apiClient.deleteConversation(conv.id) }
                                        loadConversations()
                                        if (activeConvId == conv.id) {
                                            activeConvId = null
                                            messages = emptyList()
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        // Chat view
        AnimatedVisibility(
            visible = !showConvList || activeConvId != null,
            enter = slideInHorizontally(initialOffsetX = { it }),
            exit = slideOutHorizontally(targetOffsetX = { it }),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Chat header
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = {
                        activeConvId = null
                        messages = emptyList()
                        showConvList = true
                        loadConversations()
                    }) { Text("← 返回") }

                    Spacer(modifier = Modifier.weight(1f))

                    TextButton(onClick = {
                        activeConvId = null
                        messages = listOf(UiMessage("assistant", "新对话已开始，请输入你的问题。"))
                    }) { Text("新对话") }
                }

                // Messages
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(messages) { msg -> ChatBubble(msg) }
                    if (sending) {
                        item {
                            Row {
                                Box(
                                    modifier = Modifier
                                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp))
                                        .padding(horizontal = 16.dp, vertical = 12.dp)
                                ) { TypingDots() }
                            }
                        }
                    }
                }

                // Retry bar
                if (lastFailedMessage != null && !sending) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("发送失败", fontSize = 13.sp, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                            TextButton(onClick = {
                                lastFailedMessage?.let { retry ->
                                    lastFailedMessage = null
                                    doSend(retry)
                                }
                            }) { Text("重试", fontSize = 13.sp) }
                            TextButton(onClick = { lastFailedMessage = null }) { Text("取消", fontSize = 13.sp) }
                        }
                    }
                }

                // Input
                Surface(tonalElevation = 2.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = input,
                            onValueChange = { input = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("输入消息...", fontSize = 14.sp) },
                            maxLines = 4,
                            shape = RoundedCornerShape(24.dp),
                        )
                        FilledIconButton(
                            onClick = { sendMessage() },
                            enabled = input.isNotBlank() && !sending,
                            modifier = Modifier.size(48.dp)
                        ) { Icon(Icons.Default.Send, contentDescription = "发送") }
                    }
                }
            }
        }
    }
}

@Composable
fun ConversationItem(conv: UiConversation, onClick: () -> Unit, onDelete: () -> Unit) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除对话") },
            text = { Text("确定删除「${conv.title}」？") },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; onDelete() }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") } }
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) { Text("E", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(conv.title, fontWeight = FontWeight.Medium, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (conv.lastMessage.isNotEmpty()) {
                Text(conv.lastMessage, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        IconButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun ChatBubble(msg: UiMessage) {
    val isUser = msg.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            Box(
                modifier = Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) { Text("E", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
            Spacer(modifier = Modifier.width(8.dp))
        }
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .background(
                    if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(16.dp, 16.dp, if (isUser) 16.dp else 4.dp, if (isUser) 4.dp else 16.dp)
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            MarkdownText(
                text = msg.content,
                color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Simple markdown renderer for Compose. Handles **bold**, `code`, and basic formatting.
 */
@Composable
fun MarkdownText(text: String, color: androidx.compose.ui.graphics.Color) {
    val annotated = remember(text) { parseMarkdown(text, color) }
    Text(text = annotated, fontSize = 14.sp, lineHeight = 20.sp)
}

private fun parseMarkdown(text: String, color: androidx.compose.ui.graphics.Color) = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        when {
            // **bold**
            i + 1 < text.length && text[i] == '*' && text[i + 1] == '*' -> {
                val end = text.indexOf("**", i + 2)
                if (end != -1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = color)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                } else { append(text[i]); i++ }
            }
            // `code`
            text[i] == '`' -> {
                val end = text.indexOf('`', i + 1)
                if (end != -1) {
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = color)) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else { append(text[i]); i++ }
            }
            else -> { append(text[i]); i++ }
        }
    }
}

@Composable
fun TypingDots() {
    var dotCount by remember { mutableIntStateOf(1) }
    LaunchedEffect(Unit) {
        while (true) { kotlinx.coroutines.delay(400); dotCount = (dotCount % 3) + 1 }
    }
    Text("●".repeat(dotCount) + "○".repeat(3 - dotCount), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
