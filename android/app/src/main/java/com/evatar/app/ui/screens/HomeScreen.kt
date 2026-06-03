package com.evatar.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evatar.app.R
import com.evatar.app.keepalive.KeepAliveService
import com.evatar.app.sync.SyncManager
import com.evatar.app.sync.SyncResult
import com.evatar.app.sync.SyncService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val syncManager = remember { SyncManager(context.applicationContext) }

    var serverConnected by remember { mutableStateOf(false) }
    var isSyncRunning by remember { mutableStateOf(false) }
    var isKeepAliveRunning by remember { mutableStateOf(false) }
    var isSyncing by remember { mutableStateOf(false) }
    var lastResult by remember { mutableStateOf<SyncResult?>(null) }
    var autoSyncTriggered by remember { mutableStateOf(false) }

    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(context)) {
            KeepAliveService.start(context)
            isKeepAliveRunning = true
        }
    }

    fun checkAndAutoSync() {
        scope.launch {
            val connected = withContext(Dispatchers.IO) { syncManager.apiClient.checkHealth() }
            serverConnected = connected
            if (connected && !isSyncRunning && !autoSyncTriggered) {
                autoSyncTriggered = true
                SyncService.start(context)
                isSyncRunning = true
            }
        }
    }

    LaunchedEffect(Unit) {
        checkAndAutoSync()
        while (true) {
            kotlinx.coroutines.delay(5000)
            serverConnected = withContext(Dispatchers.IO) { syncManager.apiClient.checkHealth() }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Server status
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (serverConnected)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (serverConnected) Icons.Default.CheckCircle else Icons.Default.Error,
                    contentDescription = null,
                    tint = if (serverConnected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        if (serverConnected) stringResource(R.string.server_connected)
                        else stringResource(R.string.server_disconnected),
                        fontWeight = FontWeight.Bold
                    )
                    Text(syncManager.apiClient.getServerUrl(), style = MaterialTheme.typography.bodySmall)
                }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = { checkAndAutoSync() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "刷新")
                }
            }
        }

        // Sync stats
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(stringResource(R.string.sync_stats), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(12.dp))
                if (isSyncing) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(lastResult?.let { "同步中... ${it.success + it.failed}/${it.total}" } ?: "正在准备...")
                    }
                } else if (lastResult != null) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        StatItem(stringResource(R.string.stat_synced), lastResult!!.success)
                        StatItem(stringResource(R.string.stat_errors), lastResult!!.failed)
                        StatItem(stringResource(R.string.stat_total), lastResult!!.total)
                    }
                } else {
                    Text(
                        if (serverConnected) "连接成功，等待同步..." else stringResource(R.string.hint_connect_server),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Sync controls
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    if (isSyncRunning) {
                        SyncService.stop(context); isSyncRunning = false; autoSyncTriggered = false
                    } else {
                        SyncService.start(context); isSyncRunning = true
                    }
                },
                enabled = serverConnected || isSyncRunning,
                modifier = Modifier.weight(1f),
                colors = if (isSyncRunning) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                else ButtonDefaults.buttonColors()
            ) {
                Icon(if (isSyncRunning) Icons.Default.Stop else Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (isSyncRunning) stringResource(R.string.btn_stop_sync) else stringResource(R.string.btn_start_sync))
            }
            OutlinedButton(
                onClick = {
                    scope.launch {
                        isSyncing = true
                        lastResult = withContext(Dispatchers.IO) { syncManager.runSync() }
                        isSyncing = false
                    }
                },
                enabled = serverConnected && !isSyncing,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Sync, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("手动同步")
            }
        }

        // Keep-alive
        OutlinedButton(
            onClick = {
                if (isKeepAliveRunning) {
                    KeepAliveService.stop(context); isKeepAliveRunning = false
                } else {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                        overlayPermissionLauncher.launch(
                            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                        )
                    } else {
                        KeepAliveService.start(context); isKeepAliveRunning = true
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(if (isKeepAliveRunning) Icons.Default.Stop else Icons.Default.Lock, contentDescription = null)
            Spacer(modifier = Modifier.width(4.dp))
            Text(if (isKeepAliveRunning) stringResource(R.string.btn_stop_keepalive) else stringResource(R.string.btn_start_keepalive))
        }

        Spacer(modifier = Modifier.weight(1f))

        Text(
            "Evatar v0.1.0",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

@Composable
fun StatItem(label: String, value: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value.toString(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}
