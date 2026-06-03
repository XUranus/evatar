package com.evatar.app.sync

import android.content.Context
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import com.evatar.app.network.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * Stateless sync manager. Server is the source of truth.
 *
 * Flow:
 *   1. Ask server: "what's the latest timestamp you have for my device?"
 *   2. Scan MediaStore for screenshots newer than that timestamp
 *   3. Upload them (concurrent, with dedup on server side)
 *   4. Server updates its per-device sync state
 */
class SyncManager(private val context: Context) {

    companion object {
        private const val TAG = "SyncManager"
        private const val MAX_CONCURRENT_UPLOADS = 3
    }

    val apiClient = ApiClient(context)

    val deviceId: String by lazy {
        "${Build.MANUFACTURER}_${Build.MODEL}_${
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        }"
    }

    /**
     * Run a full sync cycle. Returns (successCount, failCount).
     */
    suspend fun runSync(
        onProgress: (synced: Int, failed: Int, total: Int) -> Unit = { _, _, _ -> }
): SyncResult = withContext(Dispatchers.IO) {
        // 1. Ask server for latest synced timestamp
        val syncState = apiClient.getSyncState(deviceId)
        val sinceMs = syncState.lastSyncedTsMs
        Log.i(TAG, "Server last synced timestamp: $sinceMs (total synced: ${syncState.totalSynced})")

        // 2. Scan MediaStore for screenshots newer than that
        val newPhotos = scanMediaStoreSince(sinceMs)
        if (newPhotos.isEmpty()) {
            Log.i(TAG, "No new screenshots to sync")
            return@withContext SyncResult(0, 0, 0)
        }
        Log.i(TAG, "Found ${newPhotos.size} new screenshots to sync")

        // 3. Upload concurrently
        val semaphore = Semaphore(MAX_CONCURRENT_UPLOADS)
        var successCount = 0
        var failCount = 0
        val total = newPhotos.size

        coroutineScope {
            newPhotos.map { photo ->
                async {
                    semaphore.withPermit {
                        val ok = uploadOne(photo)
                        if (ok) successCount++ else failCount++
                        onProgress(successCount, failCount, total)
                        ok
                    }
                }
            }.awaitAll()
        }

        Log.i(TAG, "Sync done: $successCount ok, $failCount failed out of $total")
        SyncResult(successCount, failCount, total)
    }

    private suspend fun uploadOne(photo: MediaStorePhoto): Boolean {
        return try {
            val result = apiClient.uploadPhoto(
                filePath = photo.filePath,
                deviceId = deviceId,
                localMediaStoreId = photo.id,
                displayName = photo.displayName,
                timestamp = photo.timestamp,
                mimeType = photo.mimeType,
            )
            result.isSuccess
        } catch (e: Exception) {
            Log.e(TAG, "Upload failed: ${photo.displayName}: ${e.message}")
            false
        }
    }

    /**
     * Scan MediaStore for screenshots with dateAdded > sinceMs.
     * If sinceMs == 0, returns all screenshots (initial sync).
     */
    private fun scanMediaStoreSince(sinceMs: Long): List<MediaStorePhoto> {
        val results = mutableListOf<MediaStorePhoto>()

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.RELATIVE_PATH,
        )

        val selectionParts = mutableListOf(
            "(${MediaStore.Images.Media.RELATIVE_PATH} LIKE ? OR ${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?)"
        )
        val selectionArgs = mutableListOf("%Screenshots%", "%screenshot%")

        if (sinceMs > 0) {
            selectionParts.add("${MediaStore.Images.Media.DATE_ADDED} > ?")
            selectionArgs.add((sinceMs / 1000).toString()) // DATE_ADDED is in seconds
        }

        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selectionParts.joinToString(" AND "),
            selectionArgs.toTypedArray(),
            "${MediaStore.Images.Media.DATE_ADDED} ASC"  // oldest first for stable ordering
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)

            while (cursor.moveToNext()) {
                results.add(
                    MediaStorePhoto(
                        id = cursor.getLong(idCol),
                        filePath = cursor.getString(dataCol) ?: continue,
                        displayName = cursor.getString(nameCol) ?: "unknown.jpg",
                        fileSize = cursor.getLong(sizeCol),
                        timestamp = cursor.getLong(dateCol) * 1000, // to millis
                        mimeType = cursor.getString(mimeCol) ?: "image/jpeg",
                    )
                )
            }
        }

        return results
    }
}

data class MediaStorePhoto(
    val id: Long,
    val filePath: String,
    val displayName: String,
    val fileSize: Long,
    val timestamp: Long,
    val mimeType: String,
)

data class SyncResult(
    val success: Int,
    val failed: Int,
    val total: Int,
)
