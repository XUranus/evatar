package com.evatar.app.sync

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import com.evatar.app.network.ApiClient
import com.evatar.app.settings.AppExclusionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class SyncManager(context: Context) {

    companion object {
        private const val TAG = "SyncManager"
        private const val MAX_CONCURRENT = 3
    }

    val apiClient = ApiClient.getInstance(context)
    private val appContext = context.applicationContext
    private val exclusionManager = AppExclusionManager(context)

    val deviceId: String by lazy {
        "${Build.MANUFACTURER}_${Build.MODEL}_${
            Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID)
        }"
    }

    suspend fun runSync(
        onProgress: (synced: Int, failed: Int, total: Int) -> Unit = { _, _, _ -> }
    ): SyncResult = withContext(Dispatchers.IO) {
        val syncState = apiClient.getSyncState(deviceId)
        val sinceMs = syncState.lastSyncedTsMs
        Log.i(TAG, "Server last synced: $sinceMs (total: ${syncState.totalSynced})")

        val newPhotos = scanMediaStoreSince(sinceMs)
        if (newPhotos.isEmpty()) return@withContext SyncResult(0, 0, 0)

        Log.i(TAG, "Found ${newPhotos.size} new screenshots")
        val semaphore = Semaphore(MAX_CONCURRENT)
        val successCount = AtomicInteger(0)
        val failCount = AtomicInteger(0)
        val total = newPhotos.size

        coroutineScope {
            newPhotos.map { photo ->
                async {
                    ensureActive()
                    semaphore.withPermit {
                        val ok = uploadOne(photo)
                        if (ok) successCount.incrementAndGet() else failCount.incrementAndGet()
                        onProgress(successCount.get(), failCount.get(), total)
                    }
                }
            }.awaitAll()
        }

        Log.i(TAG, "Sync complete: ${successCount.get()} synced, ${failCount.get()} failed, $total total")
        SyncResult(successCount.get(), failCount.get(), total)
    }

    private suspend fun uploadOne(photo: MediaStorePhoto): Boolean {
        // On API 29+, filePath is a content:// URI which java.io.File cannot open.
        // Copy to a temp file first so that ApiClient can read it as a regular file.
        val uploadPath = if (photo.filePath.startsWith("content://")) {
            try {
                val tmpFile = File(appContext.cacheDir, "upload_${photo.id}_${photo.displayName}")
                appContext.contentResolver.openInputStream(android.net.Uri.parse(photo.filePath))?.use { input ->
                    tmpFile.outputStream().use { output -> input.copyTo(output) }
                } ?: throw Exception("openInputStream returned null for ${photo.filePath}")
                tmpFile.absolutePath
            } catch (e: Exception) {
                Log.e(TAG, "Failed to copy content URI to temp file: ${photo.displayName}: ${e.message}")
                return false
            }
        } else {
            photo.filePath
        }

        return try {
            val result = apiClient.uploadPhoto(
                filePath = uploadPath, deviceId = deviceId,
                localMediaStoreId = photo.id, displayName = photo.displayName,
                timestamp = photo.timestamp, mimeType = photo.mimeType,
            )
            // Clean up temp file if we created one
            if (uploadPath != photo.filePath) {
                try { File(uploadPath).delete() } catch (_: Exception) {}
            }
            result.isSuccess
        } catch (e: Exception) {
            Log.e(TAG, "Upload failed: ${photo.displayName}: ${e.javaClass.simpleName}: ${e.message}")
            // Clean up temp file on error too
            if (uploadPath != photo.filePath) {
                try { File(uploadPath).delete() } catch (_: Exception) {}
            }
            false
        }
    }

    private fun scanMediaStoreSince(sinceMs: Long): List<MediaStorePhoto> {
        val results = mutableListOf<MediaStorePhoto>()

        // On API 29+, avoid deprecated DATA column; use content URI instead
        val useDataColumn = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

        val projection = if (useDataColumn) {
            arrayOf(
                MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATA, MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.DATE_ADDED, MediaStore.Images.Media.MIME_TYPE,
                MediaStore.Images.Media.RELATIVE_PATH,
            )
        } else {
            arrayOf(
                MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.DATE_ADDED, MediaStore.Images.Media.MIME_TYPE,
                MediaStore.Images.Media.RELATIVE_PATH,
            )
        }

        val parts = mutableListOf(
            "(${MediaStore.Images.Media.RELATIVE_PATH} LIKE ? OR ${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?)"
        )
        val args = mutableListOf("%Screenshots%", "%screenshot%")
        if (sinceMs > 0) {
            parts.add("${MediaStore.Images.Media.DATE_ADDED} > ?")
            args.add((sinceMs / 1000).toString())
        }

        try {
            appContext.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection,
                parts.joinToString(" AND "), args.toTypedArray(),
                "${MediaStore.Images.Media.DATE_ADDED} ASC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val dataCol = if (useDataColumn) cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA) else -1
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
                val relPathIdx = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val relativePath = cursor.getString(relPathIdx) ?: ""

                    // Skip screenshots from excluded apps
                    if (isExcludedByPath(relativePath)) continue

                    // On API 29+, build content URI; on older APIs, use DATA column
                    val path = if (useDataColumn) {
                        cursor.getString(dataCol) ?: continue
                    } else {
                        ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id).toString()
                    }

                    results.add(
                        MediaStorePhoto(
                            id = id, filePath = path,
                            displayName = cursor.getString(nameCol) ?: "unknown.jpg",
                            fileSize = cursor.getLong(sizeCol),
                            timestamp = cursor.getLong(dateCol) * 1000,
                            mimeType = cursor.getString(mimeCol) ?: "image/jpeg",
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "scanMediaStoreSince error", e)
        }

        return results
    }

    private fun isExcludedByPath(relativePath: String): Boolean {
        val exclusions = exclusionManager.getExclusions()
        // Check if any excluded package name appears in the relative path
        // e.g., "Pictures/com.android.settings/" would match "com.android.settings"
        return exclusions.any { excluded ->
            relativePath.contains(excluded, ignoreCase = true)
        }
    }
}

data class MediaStorePhoto(
    val id: Long, val filePath: String, val displayName: String,
    val fileSize: Long, val timestamp: Long, val mimeType: String,
)

data class SyncResult(val success: Int, val failed: Int, val total: Int)
