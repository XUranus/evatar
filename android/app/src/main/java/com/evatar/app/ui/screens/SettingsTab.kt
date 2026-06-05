package com.evatar.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.evatar.app.R
import com.evatar.app.keepalive.KeepAliveService
import com.evatar.app.network.ApiClient
import com.evatar.app.sync.SyncManager
import com.evatar.app.sync.SyncResult
import com.evatar.app.sync.SyncService
import com.evatar.app.ui.theme.EvatarColors
import com.evatar.app.ui.theme.EvatarTypography
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsTab(
    modifier: Modifier = Modifier,
    themeMode: String = "dark",
    onThemeChange: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val apiClient = remember { ApiClient.getInstance(context) }
    val syncManager = remember { SyncManager(context) }

    var serverUrl by remember { mutableStateOf(apiClient.getServerUrl()) }
    var urlField by remember { mutableStateOf(serverUrl) }
    var saved by remember { mutableStateOf(false) }
    var urlError by remember { mutableStateOf<String?>(null) }
    var serverConnected by remember { mutableStateOf(false) }
    var lastResult by remember { mutableStateOf<SyncResult?>(null) }
    var isSyncing by remember { mutableStateOf(false) }
    var isKeepAliveRunning by remember { mutableStateOf(false) }

    val overlayLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(context)) {
            KeepAliveService.start(context)
            isKeepAliveRunning = true
        }
    }

    // Check connection on first load
    LaunchedEffect(Unit) {
        serverConnected = withContext(Dispatchers.IO) { apiClient.checkHealth() }
    }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())
    ) {
        Text(
            stringResource(R.string.settings_title),
            style = EvatarTypography.largeTitle,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp)
        )

        // ── Server section ──
        SectionHeader(stringResource(R.string.setting_server_url))
        SettingsGroup {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                // Connection status
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (serverConnected) EvatarColors.DarkSuccess else EvatarColors.DarkError)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (serverConnected) stringResource(R.string.server_connected)
                        else stringResource(R.string.server_disconnected),
                        style = EvatarTypography.subheadline,
                        color = if (serverConnected) EvatarColors.DarkSuccess else EvatarColors.DarkError,
                    )
                }

                OutlinedTextField(
                    value = urlField,
                    onValueChange = { urlField = it; saved = false; urlError = null },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("http://192.168.0.107:8000", style = EvatarTypography.subheadline) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    textStyle = EvatarTypography.body,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        val trimmed = urlField.trim()
                        when {
                            trimmed.isEmpty() -> { urlError = "请输入服务端地址" }
                            !trimmed.startsWith("http://") && !trimmed.startsWith("https://") -> {
                                urlError = "地址必须以 http:// 或 https:// 开头"
                            }
                            else -> {
                                urlError = null
                                apiClient.setServerUrl(trimmed)
                                serverUrl = trimmed
                                saved = true
                                // Re-check connection
                                scope.launch {
                                    serverConnected = withContext(Dispatchers.IO) { apiClient.checkHealth() }
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(stringResource(R.string.setting_save))
                }
                if (saved) {
                    Text("已保存", style = EvatarTypography.caption1, color = EvatarColors.DarkSuccess,
                        modifier = Modifier.padding(top = 4.dp))
                }
                if (urlError != null) {
                    Text(urlError!!, style = EvatarTypography.caption1, color = EvatarColors.DarkError,
                        modifier = Modifier.padding(top = 4.dp))
                }
            }
        }

        // ── Sync section ──
        SectionHeader(stringResource(R.string.sync_stats))
        SettingsGroup {
            Column(modifier = Modifier.padding(16.dp)) {
                // Last sync result
                if (lastResult != null) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        MiniStat(stringResource(R.string.stat_synced), lastResult!!.success, EvatarColors.DarkSuccess)
                        MiniStat(stringResource(R.string.stat_errors), lastResult!!.failed, EvatarColors.DarkError)
                        MiniStat(stringResource(R.string.stat_total), lastResult!!.total, MaterialTheme.colorScheme.onSurface)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Sync controls
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            scope.launch {
                                isSyncing = true
                                lastResult = withContext(Dispatchers.IO) { syncManager.runSync() }
                                isSyncing = false
                            }
                        },
                        enabled = serverConnected && !isSyncing,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(if (isSyncing) "同步中..." else "手动同步")
                    }

                    OutlinedButton(
                        onClick = {
                            if (isKeepAliveRunning) {
                                KeepAliveService.stop(context)
                                isKeepAliveRunning = false
                            } else {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                                    overlayLauncher.launch(
                                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            Uri.parse("package:${context.packageName}"))
                                    )
                                } else {
                                    KeepAliveService.start(context)
                                    isKeepAliveRunning = true
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(if (isKeepAliveRunning) "关闭悬浮窗" else "悬浮窗保活")
                    }
                }

                if (!serverConnected) {
                    Text(
                        stringResource(R.string.hint_connect_server),
                        style = EvatarTypography.caption1,
                        color = EvatarColors.DarkError,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }

        // ── Theme section ──
        SectionHeader("外观")
        SettingsGroup {
            SettingsRow(
                icon = Icons.Outlined.Brightness6,
                label = stringResource(R.string.setting_theme),
                subtitle = when (themeMode) {
                    "dark" -> stringResource(R.string.setting_theme_dark)
                    "light" -> stringResource(R.string.setting_theme_light)
                    else -> stringResource(R.string.setting_theme_system)
                },
                onClick = {
                    val next = when (themeMode) { "dark" -> "light"; "light" -> "system"; else -> "dark" }
                    onThemeChange(next)
                },
            )
        }

        // ── System section ──
        SectionHeader("系统")
        SettingsGroup {
            SettingsRow(
                icon = Icons.Outlined.BatteryChargingFull,
                label = stringResource(R.string.setting_battery),
                subtitle = stringResource(R.string.setting_battery_desc),
                onClick = {
                    try {
                        context.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        })
                    } catch (_: Exception) {
                        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    }
                },
            )
            SettingsRow(
                icon = Icons.Outlined.Layers,
                label = stringResource(R.string.setting_overlay),
                subtitle = stringResource(R.string.setting_overlay_desc),
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")))
                    }
                },
            )
        }

        // ── About section ──
        SectionHeader(stringResource(R.string.setting_about))
        SettingsGroup {
            SettingsInfo("版本", "0.3.0")
            SettingsInfo("设备ID", syncManager.deviceId.take(24) + "...")
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun MiniStat(label: String, value: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value.toString(), style = EvatarTypography.title2, fontWeight = FontWeight.Bold, color = color)
        Text(label, style = EvatarTypography.caption1, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title.uppercase(),
        style = EvatarTypography.caption1,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, top = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) { content() }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    label: String,
    subtitle: String? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = EvatarTypography.body, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) {
                Text(subtitle, style = EvatarTypography.caption1, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Icon(Icons.Outlined.ChevronRight, contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), modifier = Modifier.size(18.dp))
    }
    HorizontalDivider(modifier = Modifier.padding(start = 52.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
}

@Composable
private fun SettingsInfo(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(modifier = Modifier.width(36.dp))
        Text(label, style = EvatarTypography.body, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Text(value, style = EvatarTypography.subheadline, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider(modifier = Modifier.padding(start = 52.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
}
