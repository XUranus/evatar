package com.evatar.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.evatar.app.ui.screens.ChatTab
import com.evatar.app.ui.screens.DynamicTab
import com.evatar.app.ui.screens.HomeScreen
import com.evatar.app.ui.screens.SettingsTab

enum class Tab(val label: String, val icon: ImageVector) {
    HOME("首页", Icons.Default.Home),
    CHAT("AI 助手", Icons.Outlined.Chat),
    DYNAMIC("动态", Icons.Outlined.Newspaper),
    SETTINGS("设置", Icons.Default.Settings),
}

@Composable
fun AppNavigation() {
    var selectedTab by remember { mutableStateOf(Tab.HOME) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        }
    ) { padding ->
        when (selectedTab) {
            Tab.HOME -> HomeScreen(modifier = Modifier.padding(padding))
            Tab.CHAT -> ChatTab(modifier = Modifier.padding(padding))
            Tab.DYNAMIC -> DynamicTab(modifier = Modifier.padding(padding))
            Tab.SETTINGS -> SettingsTab(modifier = Modifier.padding(padding))
        }
    }
}
