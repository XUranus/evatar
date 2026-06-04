package com.evatar.app.settings

import android.content.Context
import android.content.SharedPreferences

class AppExclusionManager(context: Context) {

    companion object {
        private const val PREFS_NAME = "app_exclusion_prefs"
        private const val KEY_EXCLUDED_APPS = "excluded_apps"

        private val DEFAULT_EXCLUSIONS = setOf(
            "com.android.settings",
            "com.android.camera",
            "com.android.systemui"
        )
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isExcluded(packageName: String): Boolean {
        return getExclusions().contains(packageName)
    }

    fun addExclusion(packageName: String) {
        val current = getExclusions().toMutableSet()
        current.add(packageName)
        saveExclusions(current)
    }

    fun removeExclusion(packageName: String) {
        val current = getExclusions().toMutableSet()
        current.remove(packageName)
        saveExclusions(current)
    }

    fun getExclusions(): Set<String> {
        val saved = prefs.getStringSet(KEY_EXCLUDED_APPS, null)
        return if (saved == null) {
            // First access: initialize with defaults
            saveExclusions(DEFAULT_EXCLUSIONS)
            DEFAULT_EXCLUSIONS
        } else {
            saved
        }
    }

    private fun saveExclusions(apps: Set<String>) {
        prefs.edit().putStringSet(KEY_EXCLUDED_APPS, apps).apply()
    }
}
