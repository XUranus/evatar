package com.evatar.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.evatar.app.R
import com.evatar.app.network.ApiClient
import com.evatar.app.ui.theme.EvatarTypography

@Composable
fun SettingsTab(
    modifier: Modifier = Modifier,
    themeMode: String = "dark",
    onThemeChange: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val apiClient = remember { ApiClient.getInstance(context) }
    var serverUrl by remember { mutableStateOf(apiClient.getServerUrl()) }
    var urlField by remember { mutableStateOf(serverUrl) }
    var saved by remember { mutableStateOf(false) }
    var urlError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())
    ) {
        // Large title
        Text(
            stringResource(R.string.settings_title),
            style = EvatarTypography.largeTitle,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp)
        )

        // Server URL section
        SectionHeader("服务端")
        SettingsGroup {
            SettingsTextField(
                label = stringResource(R.string.setting_server_url),
                value = urlField,
                onValueChange = { urlField = it; saved = false },
                placeholder = "http://192.168.0.107:8000",
            )
            SettingsAction(
                label = stringResource(R.string.setting_save),
                onClick = {
                    val trimmed = urlField.trim()
                    when {
                        trimmed.isEmpty() -> { urlError = "请输入服务端地址"; saved = false }
                        !trimmed.startsWith("http://") && !trimmed.startsWith("https://") -> {
                            urlError = "地址必须以 http:// 或 https:// 开头"; saved = false
                        }
                        else -> {
                            urlError = null
                            apiClient.setServerUrl(trimmed)
                            serverUrl = trimmed
                            saved = true
                        }
                    }
                },
            )
            if (saved) {
                Text("已保存", style = EvatarTypography.caption1, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 56.dp, bottom = 8.dp))
            }
            if (urlError != null) {
                Text(urlError!!, style = EvatarTypography.caption1, color = com.evatar.app.ui.theme.EvatarColors.DarkError,
                    modifier = Modifier.padding(start = 56.dp, bottom = 8.dp))
            }
        }

        // System section
        SectionHeader("系统")
        SettingsGroup {
            // Theme toggle
            SettingsRow(
                icon = Icons.Outlined.Brightness6,
                label = stringResource(R.string.setting_theme),
                subtitle = when (themeMode) {
                    "dark" -> stringResource(R.string.setting_theme_dark)
                    "light" -> stringResource(R.string.setting_theme_light)
                    else -> stringResource(R.string.setting_theme_system)
                },
                onClick = {
                    val next = when (themeMode) {
                        "dark" -> "light"
                        "light" -> "system"
                        else -> "dark"
                    }
                    onThemeChange(next)
                },
            )

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

        // About section
        SectionHeader(stringResource(R.string.setting_about))
        SettingsGroup {
            SettingsInfo("版本", "0.3.0")
            SettingsInfo("后端", "FastAPI + SQLite")
            SettingsInfo("前端", "React + Vite + Tailwind")
            SettingsInfo("Android", "Kotlin + Jetpack Compose")
        }

        Spacer(modifier = Modifier.height(24.dp))
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
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            content()
        }
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
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
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
private fun SettingsTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(label, style = EvatarTypography.caption1, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder, style = EvatarTypography.subheadline) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            textStyle = EvatarTypography.body,
        )
    }
}

@Composable
private fun SettingsAction(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(modifier = Modifier.width(36.dp))
        Text(label, style = EvatarTypography.body, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
    }
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
