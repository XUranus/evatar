package com.evatar.app.ui

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.ChatBubble
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.evatar.app.R
import com.evatar.app.ui.screens.ChatTab
import com.evatar.app.ui.screens.DynamicTab
import com.evatar.app.ui.screens.SettingsTab
import com.evatar.app.ui.theme.EvatarTypography

enum class Tab(
    val labelResId: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    DYNAMIC(R.string.dynamic_title, Icons.Filled.Newspaper, Icons.Outlined.Newspaper),
    CHAT(R.string.chat_title, Icons.Filled.ChatBubble, Icons.Outlined.ChatBubble),
    SETTINGS(R.string.settings_title, Icons.Filled.Settings, Icons.Outlined.Settings),
}

@Composable
fun AppNavigation(
    themeMode: String = "dark",
    onThemeChange: (String) -> Unit = {},
) {
    var selectedTab by remember { mutableStateOf(Tab.DYNAMIC) }
    val haptic = LocalHapticFeedback.current
    // Track whether the chat is in full-screen mode (active conversation)
    var chatIsFullScreen by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            // Hide bottom bar when chat is in full-screen mode
            AnimatedVisibility(
                visible = !chatIsFullScreen,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it }),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.5.dp,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .height(56.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Tab.entries.forEach { tab ->
                            val selected = selectedTab == tab
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            selectedTab = tab
                                        },
                                    )
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                    .widthIn(min = 56.dp),
                            ) {
                                Icon(
                                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = stringResource(tab.labelResId),
                                    tint = if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp),
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = stringResource(tab.labelResId),
                                    style = EvatarTypography.caption2,
                                    color = if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (selectedTab) {
                Tab.DYNAMIC -> DynamicTab()
                Tab.CHAT -> ChatTab(onChatActiveChange = { chatIsFullScreen = it })
                Tab.SETTINGS -> SettingsTab(themeMode = themeMode, onThemeChange = onThemeChange)
            }
        }
    }
}
