package com.evatar.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.evatar.app.R
import com.evatar.app.ui.components.MarkdownText
import com.evatar.app.ui.theme.EvatarColors
import com.evatar.app.ui.theme.EvatarTypography
import com.evatar.app.viewmodel.DynamicViewModel
import com.evatar.app.viewmodel.UiDynamic
import kotlinx.coroutines.delay

/** Represents a filter category with icon and label. */
private data class FilterOption(val key: String, val icon: ImageVector, val labelResId: Int)

private val FILTER_OPTIONS = listOf(
    FilterOption("", Icons.Outlined.List, R.string.dynamic_filter_all),
    FilterOption("insight", Icons.Outlined.Lightbulb, R.string.dynamic_filter_insight),
    FilterOption("reminder", Icons.Outlined.Notifications, R.string.dynamic_filter_reminder),
    FilterOption("report", Icons.Outlined.Assessment, R.string.dynamic_filter_report),
    FilterOption("note", Icons.Outlined.Article, R.string.dynamic_filter_note),
)

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun DynamicTab(modifier: Modifier = Modifier, viewModel: DynamicViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var expandedId by remember { mutableIntStateOf(-1) }
    var refreshing by remember { mutableStateOf(false) }

    // Current filter index for swipe navigation
    val currentFilterIndex = remember(state.filter) {
        FILTER_OPTIONS.indexOfFirst { it.key == state.filter }.coerceAtLeast(0)
    }

    // Periodic auto-refresh every 60 seconds
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            viewModel.refresh()
            viewModel.checkConnection()
        }
    }

    val pullRefreshState = rememberPullRefreshState(
        refreshing = refreshing,
        onRefresh = {
            refreshing = true
            viewModel.refresh()
            viewModel.checkConnection()
        },
    )

    // Reset refreshing when loading completes
    LaunchedEffect(state.loading) {
        if (!state.loading) refreshing = false
    }

    Box(modifier = modifier.fillMaxSize().pullRefresh(pullRefreshState)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { _, dragAmount ->
                        val threshold = 80f
                        if (dragAmount < -threshold) {
                            // Swipe left -> next category
                            val nextIndex = (currentFilterIndex + 1) % FILTER_OPTIONS.size
                            viewModel.setFilter(FILTER_OPTIONS[nextIndex].key)
                        } else if (dragAmount > threshold) {
                            // Swipe right -> previous category
                            val prevIndex = (currentFilterIndex - 1 + FILTER_OPTIONS.size) % FILTER_OPTIONS.size
                            viewModel.setFilter(FILTER_OPTIONS[prevIndex].key)
                        }
                    }
                },
        ) {
            // Header with connection indicator
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.dynamic_title), style = EvatarTypography.largeTitle, color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f))

                // Connection dot
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (state.serverConnected) EvatarColors.DarkSuccess else EvatarColors.DarkError)
                )
            }

            // Filter chips with icons and unread badges
            val unreadCounts = state.unreadCounts
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 8.dp),
            ) {
                items(FILTER_OPTIONS) { option ->
                    val count = if (option.key.isNotEmpty()) unreadCounts[option.key] ?: 0
                                else unreadCounts.values.sum()
                    BadgedBox(
                        badge = {
                            if (count > 0) {
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = Color.White,
                                ) {
                                    Text(if (count > 99) "99+" else count.toString(),
                                        fontSize = 10.sp)
                                }
                            }
                        }
                    ) {
                        FilterChip(
                            selected = state.filter == option.key,
                            onClick = { viewModel.setFilter(option.key) },
                            label = { Text(stringResource(option.labelResId), style = EvatarTypography.subheadline) },
                            leadingIcon = {
                                Icon(
                                    imageVector = option.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                            shape = RoundedCornerShape(20.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                selectedLabelColor = MaterialTheme.colorScheme.primary,
                            ),
                        )
                    }
                }
            }

            if (state.loading && !refreshing) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 2.dp)
                }
            } else if (state.items.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.Article, contentDescription = null,
                            modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(stringResource(R.string.dynamic_empty_title), style = EvatarTypography.headline, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.dynamic_empty_desc), style = EvatarTypography.subheadline, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            onToggle = {
                                val wasExpanded = expandedId == item.id
                                expandedId = if (wasExpanded) -1 else item.id
                                if (!wasExpanded && !item.isRead) {
                                    viewModel.markAsRead(item.id)
                                }
                            },
                        )
                    }

                    // Load more trigger
                    if (state.hasMore && !state.loading) {
                        item {
                            LaunchedEffect(Unit) { viewModel.loadMore() }
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            }
                        }
                    }

                    // Loading more indicator
                    if (state.loadingMore) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            }
                        }
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
    }
}

/** Get the category icon for a dynamic card. */
private fun categoryIcon(category: String): ImageVector = when (category) {
    "insight" -> Icons.Outlined.Lightbulb
    "reminder" -> Icons.Outlined.Notifications
    "report" -> Icons.Outlined.Assessment
    else -> Icons.Outlined.Article
}

@Composable
private fun DynamicCard(item: UiDynamic, expanded: Boolean, onToggle: () -> Unit) {
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
                Icon(
                    imageVector = categoryIcon(item.category),
                    contentDescription = item.category,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(item.title, style = EvatarTypography.headline, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (item.isPinned) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Outlined.PushPin, contentDescription = null, modifier = Modifier.size(12.dp))
                        }
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
                MarkdownText(text = item.content)
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
