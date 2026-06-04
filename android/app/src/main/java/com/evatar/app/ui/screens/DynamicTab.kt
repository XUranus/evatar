package com.evatar.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evatar.app.network.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class UiDynamic(
    val id: Int,
    val title: String,
    val summary: String,
    val content: String,
    val category: String,
    val confidence: Double,
    val isRead: Boolean,
    val isPinned: Boolean,
    val createdAt: String,
)

private val categoryIcons = mapOf(
    "insight" to "💡", "reminder" to "⏰", "report" to "📊", "note" to "📝"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DynamicTab(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val apiClient = remember { ApiClient.getInstance(context) }

    var items by remember { mutableStateOf(listOf<UiDynamic>()) }
    var expandedId by remember { mutableIntStateOf(-1) }
    var filter by remember { mutableStateOf("") }
    var triggering by remember { mutableStateOf(false) }

    fun load() {
        scope.launch {
            val arr = withContext(Dispatchers.IO) {
                try {
                    val url = "${apiClient.getServerUrl()}/api/dynamics?page=1&page_size=50" +
                            if (filter.isNotEmpty()) "&category=$filter" else ""
                    val request = okhttp3.Request.Builder().url(url).get().build()
                    val response = okhttp3.OkHttpClient().newCall(request).execute()
                    if (response.isSuccessful) {
                        val json = org.json.JSONObject(response.body?.string() ?: "{}")
                        json.optJSONArray("items") ?: org.json.JSONArray()
                    } else org.json.JSONArray()
                } catch (_: Exception) { org.json.JSONArray() }
            }
            val list = mutableListOf<UiDynamic>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                list.add(UiDynamic(
                    id = obj.optInt("id"),
                    title = obj.optString("title", ""),
                    summary = obj.optString("summary", ""),
                    content = obj.optString("content", ""),
                    category = obj.optString("category", "note"),
                    confidence = obj.optDouble("confidence", 0.5),
                    isRead = obj.optBoolean("is_read", false),
                    isPinned = obj.optBoolean("is_pinned", false),
                    createdAt = obj.optString("created_at", ""),
                ))
            }
            items = list
        }
    }

    LaunchedEffect(filter) { load() }

    Column(modifier = modifier.fillMaxSize()) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("动态", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("" to "全部", "insight" to "💡", "reminder" to "⏰", "report" to "📊", "note" to "📝").forEach { (key, label) ->
                    FilterChip(
                        selected = filter == key,
                        onClick = { filter = key },
                        label = { Text(label, fontSize = 12.sp) },
                        modifier = Modifier.height(32.dp)
                    )
                }
            }
        }

        if (items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📝", fontSize = 40.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("暂无动态", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("后台会定期分析截图和聊天生成笔记", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(items, key = { it.id }) { item ->
                    DynamicCard(
                        item = item,
                        expanded = expandedId == item.id,
                        onToggle = {
                            expandedId = if (expandedId == item.id) -1 else item.id
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun DynamicCard(item: UiDynamic, expanded: Boolean, onToggle: () -> Unit) {
    val icon = categoryIcons[item.category] ?: "📝"

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onToggle() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (!item.isRead)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(icon, fontSize = 20.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(item.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (item.isPinned) { Spacer(modifier = Modifier.width(4.dp)); Text("📌", fontSize = 12.sp) }
                        if (!item.isRead) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Surface(modifier = Modifier.size(6.dp), shape = RoundedCornerShape(3.dp), color = MaterialTheme.colorScheme.primary) {}
                        }
                    }
                    Text(item.summary, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }

            if (expanded && item.content.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))
                // Simple markdown-like rendering
                Text(item.content, fontSize = 14.sp, lineHeight = 20.sp)
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row {
                Text(item.category, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    if (item.createdAt.length > 16) item.createdAt.substring(5, 16) else item.createdAt,
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
