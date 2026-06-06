package com.evatar.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.evatar.app.ui.AppNavigation
import com.evatar.app.ui.theme.EvatarTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    companion object {
        const val PREF_NAME = "evatar_prefs"
        const val KEY_THEME = "theme_mode" // "system", "dark", "light"
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val denied = permissions.filter { !it.value }.keys
        if (denied.isNotEmpty()) {
            android.util.Log.w("MainActivity", "Denied permissions: $denied")
        }
        // Start sync service and register device only after permissions are handled
        com.evatar.app.sync.SyncService.start(this)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val apiClient = com.evatar.app.network.ApiClient.getInstance(this@MainActivity)
                val syncManager = com.evatar.app.sync.SyncManager(this@MainActivity)
                apiClient.registerDevice(syncManager.deviceId)
            } catch (e: Exception) {
                android.util.Log.w("MainActivity", "Device registration failed: ${e.message}")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissions()

        setContent {
            val prefs = remember { getSharedPreferences(PREF_NAME, MODE_PRIVATE) }
            var themeMode by rememberSaveable { mutableStateOf(prefs.getString(KEY_THEME, "dark") ?: "dark") }

            val darkTheme = when (themeMode) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }

            EvatarTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(
                        themeMode = themeMode,
                        onThemeChange = { mode ->
                            themeMode = mode
                            prefs.edit().putString(KEY_THEME, mode).apply()
                        }
                    )
                }
            }
        }
    }

    private fun requestPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                != PackageManager.PERMISSION_GRANTED
            ) permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (permissions.isNotEmpty()) permissionLauncher.launch(permissions.toTypedArray())
    }
}
