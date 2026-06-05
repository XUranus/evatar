package com.evatar.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.evatar.app.R
import com.evatar.app.keepalive.KeepAliveService
import com.evatar.app.sync.SyncManager
import com.evatar.app.sync.SyncResult
import com.evatar.app.sync.SyncService
import com.evatar.app.ui.theme.EvatarColors
import com.evatar.app.ui.theme.EvatarTypography
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val syncManager = remember { SyncManager(context) }

    var serverConnected by remember { mutableStateOf(false) }
    var isSyncRunning by remember { mutableStateOf(false) }
    var isSyncing by remember { mutableStateOf(false) }
    var lastResult by remember { mutableStateOf<SyncResult?>(null) }
    var autoSyncTriggered by remember { mutableStateOf(false) }

    val overlayLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(context)) {
            KeepAliveService.start(context)
        }
    }

    fun checkConnection() {
        scope.launch {
            serverConnected = withContext(Dispatchers.IO) { syncManager.apiClient.checkHealth() }
            if (serverConnected && !isSyncRunning && !autoSyncTriggered) {
                autoSyncTriggered = true
                SyncService.start(context)
                isSyncRunning = true
            }
        }
    }

    LaunchedEffect(Unit) {
        checkConnection()
        while (true) {
            kotlinx.coroutines.delay(30_000)
            serverConnected = withContext(Dispatchers.IO) { syncManager.apiClient.checkHealth() }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // iOS-style large title
        Text(
            text = stringResource(R.string.app_name),
            style = EvatarTypography.largeTitle,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp)
        )

        // Connection status card
        StatusCard(
            connected = serverConnected,
            serverUrl = syncManager.apiClient.getServerUrl(),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )

        // Sync stats card
        SyncStatsCard(
            isSyncing = isSyncing,
            lastResult = lastResult,
            serverConnected = serverConnected,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )

        // Action buttons
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy( 10.dp)
        ) {
            // Primary sync button
            Button(
                onClick = {
                    if (isSyncRunning) {
                        SyncService.stop(context)
                        com.evatar.app.sync.WorkScheduler.setScheduled(context, false)
                        isSyncRunning = false
                        autoSyncTriggered = false
                    } else {
                        SyncService.start(context)
                        com.evatar.app.sync.WorkScheduler.setScheduled(context, true)
                        isSyncRunning = true
                    }
                },
                enabled = serverConnected || isSyncRunning,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = if (isSyncRunning)
                    ButtonDefaults.buttonColors(containerColor = EvatarColors.DarkError)
                else
                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Icon(
                    if (isSyncRunning) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (isSyncRunning) stringResource(R.string.btn_stop_sync)
                    else stringResource(R.string.btn_start_sync),
                    style = EvatarTypography.headline,
                )
            }

            // Manual sync
            OutlinedButton(
                onClick = {
                    scope.launch {
                        isSyncing = true
                        lastResult = withContext(Dispatchers.IO) { syncManager.runSync() }
                        isSyncing = false
                    }
                },
                enabled = serverConnected && !isSyncing,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.Outlined.Sync, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("手动同步", style = EvatarTypography.headline)
            }

            // Keep-alive
            OutlinedButton(
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                        overlayLauncher.launch(
                            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}"))
                        )
                    } else {
                        KeepAliveService.start(context)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.btn_start_keepalive), style = EvatarTypography.headline)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Footer
        Text(
            "Evatar v0.3.0",
            style = EvatarTypography.caption1,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun StatusCard(connected: Boolean, serverUrl: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (connected)
                EvatarColors.DarkSuccess.copy(alpha = 0.1f)
            else
                EvatarColors.DarkError.copy(alpha = 0.1f)
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Status dot
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (connected) EvatarColors.DarkSuccess else EvatarColors.DarkError)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (connected) stringResource(R.string.server_connected)
                    else stringResource(R.string.server_disconnected),
                    style = EvatarTypography.headline,
                    color = if (connected) EvatarColors.DarkSuccess else EvatarColors.DarkError,
                )
                Text(
                    serverUrl,
                    style = EvatarTypography.caption1,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SyncStatsCard(isSyncing: Boolean, lastResult: SyncResult?, serverConnected: Boolean, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.sync_stats),
                style = EvatarTypography.headline,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(12.dp))

            when {
                isSyncing -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            lastResult?.let { "同步中... ${it.success + it.failed}/${it.total}" }
                                ?: "正在准备...",
                            style = EvatarTypography.subheadline,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                lastResult != null -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        StatColumn(stringResource(R.string.stat_synced), lastResult.success, EvatarColors.DarkSuccess)
                        StatColumn(stringResource(R.string.stat_errors), lastResult.failed, EvatarColors.DarkError)
                        StatColumn(stringResource(R.string.stat_total), lastResult.total, MaterialTheme.colorScheme.onSurface)
                    }
                }
                else -> {
                    Text(
                        if (serverConnected) "连接成功，等待同步..."
                        else stringResource(R.string.hint_connect_server),
                        style = EvatarTypography.subheadline,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatColumn(label: String, value: Int, valueColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value.toString(),
            style = EvatarTypography.title1,
            fontWeight = FontWeight.Bold,
            color = valueColor,
        )
        Text(
            label,
            style = EvatarTypography.caption1,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
