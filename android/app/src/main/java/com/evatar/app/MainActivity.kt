package com.evatar.app

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
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
import com.evatar.app.network.ApiClient
import com.evatar.app.sync.SyncManager
import com.evatar.app.sync.SyncService
import com.evatar.app.ui.AppNavigation
import com.evatar.app.ui.screens.OnboardingScreen
import com.evatar.app.ui.theme.EvatarTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : ComponentActivity() {

    companion object {
        const val PREF_NAME = "evatar_prefs"
        const val KEY_THEME = "theme_mode"
        const val KEY_ONBOARDING_DONE = "onboarding_done"
        const val KEY_LANGUAGE = "language_mode"
    }

    private var permissionsGranted = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        permissionsGranted = permissions.values.all { it }
        // Start services after permissions
        if (permissionsGranted) {
            startBackgroundServices()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissions()
        applyLanguagePreference()

        setContent {
            val prefs = remember { getSharedPreferences(PREF_NAME, MODE_PRIVATE) }
            var themeMode by rememberSaveable { mutableStateOf(prefs.getString(KEY_THEME, "dark") ?: "dark") }
            var onboardingDone by rememberSaveable {
                mutableStateOf(prefs.getBoolean(KEY_ONBOARDING_DONE, false))
            }

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
                    if (!onboardingDone || !ApiClient.getInstance(this).isServerConfigured()) {
                        OnboardingScreen(
                            onComplete = {
                                prefs.edit().putBoolean(KEY_ONBOARDING_DONE, true).apply()
                                onboardingDone = true
                                startBackgroundServices()
                            }
                        )
                    } else {
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
    }

    private fun startBackgroundServices() {
        SyncService.start(this)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val apiClient = ApiClient.getInstance(this@MainActivity)
                val syncManager = SyncManager(this@MainActivity)
                apiClient.registerDevice(syncManager.deviceId)
            } catch (e: Exception) {
                android.util.Log.w("MainActivity", "Device registration failed: ${e.message}")
            }
        }
    }

    private fun applyLanguagePreference() {
        val langMode = getSharedPreferences(PREF_NAME, MODE_PRIVATE).getString(KEY_LANGUAGE, "system") ?: "system"
        if (langMode == "zh" || langMode == "en") {
            val locale = Locale(langMode)
            Locale.setDefault(locale)
            val config = Configuration(resources.configuration)
            config.setLocale(locale)
            @Suppress("DEPRECATION")
            resources.updateConfiguration(config, resources.displayMetrics)
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
        if (permissions.isNotEmpty()) {
            permissionLauncher.launch(permissions.toTypedArray())
        } else {
            permissionsGranted = true
        }
    }
}
