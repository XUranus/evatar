package com.evatar.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.evatar.app.ui.theme.EvatarColors
import com.evatar.app.ui.theme.EvatarTypography
import com.evatar.app.viewmodel.DynamicViewModel
import com.evatar.app.viewmodel.UiDynamic

@Composable
fun DynamicTab(modifier: Modifier = Modifier, viewModel: DynamicViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    var expandedId by remember { mutableIntStateOf(-1) }

    Column(modifier = modifier.fillMaxSize()) {
        // Header with connection indicator and sync button
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("动态", style = EvatarTypography.largeTitle, color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f))

            // Connection dot
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (state.serverConnected) EvatarColors.DarkSuccess else EvatarColors.DarkError)
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Sync button
            IconButton(
                onClick = { viewModel.triggerSync() },
                enabled = state.serverConnected && !state.isSyncing,
                modifier = Modifier.size(36.dp),
            ) {
                if (state.isSyncing) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Outlined.Refresh, contentDescription = "同步", modifier = Modifier.size(20.dp))
                }
            }
        }

        // Filter chips
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(vertical = 8.dp),
        ) {
            val filters = listOf("" to "全部", "insight" to "💡 洞察", "reminder" to "⏰ 提醒", "report" to "📊 报告", "note" to "📝 笔记")
            items(filters) { (key, label) ->
                FilterChip(
                    selected = state.filter == key,
                    onClick = { viewModel.setFilter(key) },
                    label = { Text(label, style = EvatarTypography.subheadline) },
                    shape = RoundedCornerShape(20.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        selectedLabelColor = MaterialTheme.colorScheme.primary,
                    ),
                )
            }
        }

        if (state.loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 2.dp)
            }
        } else if (state.items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.Article, contentDescription = null,
                        modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("暂无动态", style = EvatarTypography.headline, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("后台会定期分析截图和聊天生成笔记", style = EvatarTypography.subheadline, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.items, key = { it.id }) { item ->
                    DynamicCard(
                        item = item,
                        expanded = expandedId == item.id,
                        onToggle = { expandedId = if (expandedId == item.id) -1 else item.id },
                    )
                }
            }
        }
    }
}

@Composable
private fun DynamicCard(item: UiDynamic, expanded: Boolean, onToggle: () -> Unit) {
    val categoryEmoji = when (item.category) {
        "insight" -> "💡"
        "reminder" -> "⏰"
        "report" -> "📊"
        else -> "📝"
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onToggle() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (!item.isRead)
                MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
            else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(categoryEmoji, fontSize = 20.sp)
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(item.title, style = EvatarTypography.headline, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (item.isPinned) { Spacer(modifier = Modifier.width(4.dp)); Text("📌", fontSize = 12.sp) }
                        if (!item.isRead) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                        }
                    }
                    Text(item.summary, style = EvatarTypography.subheadline, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }

            if (expanded && item.content.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(12.dp))
                Text(item.content, style = EvatarTypography.body, lineHeight = 24.sp)
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row {
                Text(item.category, style = EvatarTypography.caption1, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    if (item.createdAt.length > 16) item.createdAt.substring(5, 16) else item.createdAt,
                    style = EvatarTypography.caption1, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
