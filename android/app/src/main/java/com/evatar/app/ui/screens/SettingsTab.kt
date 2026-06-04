package com.evatar.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.evatar.app.R
import com.evatar.app.network.ApiClient
import com.evatar.app.settings.AppExclusionManager

@Composable
fun SettingsTab(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val apiClient = remember { ApiClient.getInstance(context) }
    val exclusionManager = remember { AppExclusionManager(context) }
    var serverUrl by remember { mutableStateOf(apiClient.getServerUrl()) }
    var urlField by remember { mutableStateOf(serverUrl) }
    var excludedApps by remember { mutableStateOf(exclusionManager.getExclusions()) }
    var newPackageName by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Server URL
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(stringResource(R.string.setting_server_url), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = urlField,
                    onValueChange = { urlField = it },
                    label = { Text("URL") },
                    placeholder = { Text("http://192.168.0.107:8000") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        apiClient.setServerUrl(urlField)
                        serverUrl = urlField
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.setting_save)) }
            }
        }

        // Battery
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row {
                    Icon(Icons.Default.BatteryStd, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.setting_battery), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.setting_battery_desc), style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        try {
                            context.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            })
                        } catch (_: Exception) {
                            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.setting_battery_btn)) }
            }
        }

        // Overlay
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(stringResource(R.string.setting_overlay), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.setting_overlay_desc), style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            context.startActivity(
                                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.setting_overlay_btn)) }
            }
        }

        // App Exclusion List
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(stringResource(R.string.setting_exclusion_title), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.setting_exclusion_desc), style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(12.dp))

                // List of excluded apps
                excludedApps.sorted().forEach { pkg ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(pkg, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        IconButton(onClick = {
                            exclusionManager.removeExclusion(pkg)
                            excludedApps = exclusionManager.getExclusions()
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.setting_exclusion_remove))
                        }
                    }
                }

                if (excludedApps.isEmpty()) {
                    Text(stringResource(R.string.setting_exclusion_empty), style = MaterialTheme.typography.bodySmall)
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Add custom package name
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = newPackageName,
                        onValueChange = { newPackageName = it },
                        label = { Text(stringResource(R.string.setting_exclusion_input_label)) },
                        placeholder = { Text("com.example.app") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Button(
                        onClick = {
                            if (newPackageName.isNotBlank()) {
                                exclusionManager.addExclusion(newPackageName.trim())
                                excludedApps = exclusionManager.getExclusions()
                                newPackageName = ""
                            }
                        },
                        enabled = newPackageName.isNotBlank()
                    ) {
                        Text(stringResource(R.string.setting_exclusion_add))
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(stringResource(R.string.setting_about), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Evatar v0.1.0", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
