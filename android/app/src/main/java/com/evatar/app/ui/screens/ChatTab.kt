package com.evatar.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.evatar.app.R
import com.evatar.app.ui.theme.EvatarColors
import com.evatar.app.ui.theme.EvatarTypography
import com.evatar.app.ui.components.MarkdownText
import com.evatar.app.viewmodel.ChatViewModel
import com.evatar.app.viewmodel.UiConversation
import com.evatar.app.viewmodel.UiMessage
import kotlinx.coroutines.delay

@Composable
fun ChatTab(
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = viewModel(),
    onChatActiveChange: (Boolean) -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Notify parent when chat view is active (for hiding bottom bar)
    LaunchedEffect(state.activeConvId) {
        onChatActiveChange(state.activeConvId != null)
    }

    // Periodic auto-refresh conversation list every 30 seconds (when viewing list)
    LaunchedEffect(state.activeConvId) {
        if (state.activeConvId == null) {
            while (true) {
                delay(30_000L)
                viewModel.loadConversations()
            }
        }
    }

    LaunchedEffect(state.messages.size) { if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.size - 1) }

    if (state.activeConvId == null) {
        // Conversation list
        ConversationList(
            conversations = state.conversations,
            loading = state.loading,
            onSelect = { viewModel.selectConversation(it.id) },
            onNew = { viewModel.startNewConversation() },
            onDelete = { conv -> viewModel.deleteConversation(conv.id) },
            onRefresh = { viewModel.loadConversations() },
            modifier = modifier,
        )
    } else {
        // Chat view (standalone full-screen with swipe-left to go back)
        ChatView(
            messages = state.messages,
            input = input,
            onInputChange = { input = it },
            onSend = {
                val text = input.trim()
                if (text.isNotEmpty() && !state.sending) { input = ""; viewModel.sendMessage(text) }
            },
            sending = state.sending,
            lastFailedMessage = state.lastFailedMessage,
            onRetry = { state.lastFailedMessage?.let { viewModel.sendMessage(it, isRetry = true) } },
            onCancel = { viewModel.cancelRetry() },
            onBack = { viewModel.goBackToList() },
            onNewChat = { viewModel.startNewConversation() },
            modifier = modifier,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
private fun ConversationList(
    conversations: List<UiConversation>,
    loading: Boolean,
    onSelect: (UiConversation) -> Unit,
    onNew: () -> Unit,
    onDelete: (UiConversation) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var refreshing by remember { mutableStateOf(false) }

    // Reset refreshing when loading completes
    LaunchedEffect(loading) {
        if (!loading) {
            refreshing = false
        }
    }

    val pullRefreshState = rememberPullRefreshState(
        refreshing = refreshing,
        onRefresh = {
            refreshing = true
            onRefresh()
        },
    )

    Box(modifier = modifier.fillMaxSize().pullRefresh(pullRefreshState)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Text(
                stringResource(R.string.chat_title),
                style = EvatarTypography.largeTitle,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp)
            )

            if (loading && !refreshing) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 2.dp)
                }
            } else if (conversations.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.Forum, contentDescription = null,
                            modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(stringResource(R.string.chat_empty_title), style = EvatarTypography.headline, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.chat_empty_desc), style = EvatarTypography.subheadline, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(conversations, key = { it.id }) { conv ->
                        ConversationRow(
                            conversation = conv,
                            onClick = { onSelect(conv) },
                            onDelete = { onDelete(conv) },
                        )
                    }
                }
            }
        }

        // Pull-to-refresh indicator
        PullRefreshIndicator(
            refreshing = refreshing,
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter),
            contentColor = MaterialTheme.colorScheme.primary,
        )

        // FAB
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
            FloatingActionButton(
                onClick = onNew,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.padding(20.dp).size(56.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.chat_new_conversation), modifier = Modifier.size(24.dp))
            }
        }
    }
}

@Composable
private fun ConversationRow(conversation: UiConversation, onClick: () -> Unit, onDelete: () -> Unit) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.chat_delete_title)) },
            text = { Text(stringResource(R.string.chat_delete_confirm, conversation.title)) },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false; onDelete() }) {
                    Text(stringResource(R.string.chat_delete), color = EvatarColors.DarkError)
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.chat_cancel)) } },
        )
    }

    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Text("E", style = EvatarTypography.headline, color = MaterialTheme.colorScheme.primary)
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    conversation.title,
                    style = EvatarTypography.headline,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (conversation.lastMessage.isNotEmpty()) {
                    Text(
                        conversation.lastMessage,
                        style = EvatarTypography.subheadline,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            IconButton(onClick = { showDeleteDialog = true }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.chat_delete),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        }
    }

    HorizontalDivider(
        modifier = Modifier.padding(start = 78.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
    )
}

@Composable
private fun ChatView(
    messages: List<UiMessage>,
    input: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    sending: Boolean,
    lastFailedMessage: String?,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onBack: () -> Unit,
    onNewChat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) {
                detectHorizontalDragGestures { _, dragAmount ->
                    // Swipe left to go back
                    if (dragAmount < -120f) {
                        onBack()
                    }
                }
            },
    ) {
        // Top bar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 0.5.dp,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().statusBarsPadding().height(52.dp).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = stringResource(R.string.chat_back), tint = MaterialTheme.colorScheme.primary)
                }
                Text(stringResource(R.string.chat_title), style = EvatarTypography.headline, modifier = Modifier.weight(1f))
                TextButton(onClick = onNewChat) {
                    Text(stringResource(R.string.chat_new_conversation), style = EvatarTypography.subheadline, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        // Messages
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(messages) { msg -> ChatBubble(msg) }

            if (sending) {
                item {
                    Row {
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp))
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            TypingDots()
                        }
                    }
                }
            }
        }

        // Retry bar
        if (lastFailedMessage != null && !sending) {
            Surface(
                color = EvatarColors.DarkError.copy(alpha = 0.1f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.chat_send_failed), style = EvatarTypography.subheadline, color = EvatarColors.DarkError, modifier = Modifier.weight(1f))
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.chat_retry), color = MaterialTheme.colorScheme.primary) }
                    TextButton(onClick = onCancel) { Text(stringResource(R.string.chat_cancel), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }

        // Input
        Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 0.5.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = onInputChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.chat_input_hint), style = EvatarTypography.subheadline) },
                    maxLines = 4,
                    shape = RoundedCornerShape(20.dp),
                    textStyle = EvatarTypography.body,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    ),
                )
                FilledIconButton(
                    onClick = onSend,
                    enabled = input.isNotBlank() && !sending,
                    modifier = Modifier.size(44.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Icon(Icons.Filled.Send, contentDescription = stringResource(R.string.chat_send), modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(msg: UiMessage) {
    val isUser = msg.role == "user"
    val bubbleColor = if (isUser) MaterialTheme.colorScheme.primary
                      else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isUser) Color.White else MaterialTheme.colorScheme.onSurface
    val shape = RoundedCornerShape(
        topStart = 18.dp, topEnd = 18.dp,
        bottomStart = if (isUser) 18.dp else 4.dp,
        bottomEnd = if (isUser) 4.dp else 18.dp,
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        // No avatar for assistant messages - clean bubble-only layout

        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .background(bubbleColor, shape)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            if (isUser) {
                Text(
                    text = msg.content,
                    style = EvatarTypography.body,
                    color = textColor,
                    lineHeight = 22.sp,
                )
            } else {
                MarkdownText(
                    text = msg.content,
                    color = textColor,
                    baseStyle = EvatarTypography.body,
                )
            }
        }
    }
}

@Composable
private fun TypingDots() {
    var dotCount by remember { mutableIntStateOf(1) }
    LaunchedEffect(Unit) {
        while (true) { kotlinx.coroutines.delay(400); dotCount = (dotCount % 3) + 1 }
    }
    Text(
        "●".repeat(dotCount) + "○".repeat(3 - dotCount),
        style = EvatarTypography.body,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
