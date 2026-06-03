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
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

class SyncManager(context: Context) {

    companion object {
        private const val TAG = "SyncManager"
        private const val MAX_CONCURRENT = 3
    }

    val apiClient = ApiClient.getInstance(context)
    private val appContext = context.applicationContext

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

        SyncResult(successCount.get(), failCount.get(), total)
    }

    private suspend fun uploadOne(photo: MediaStorePhoto): Boolean {
        return try {
            val result = apiClient.uploadPhoto(
                filePath = photo.filePath, deviceId = deviceId,
                localMediaStoreId = photo.id, displayName = photo.displayName,
                timestamp = photo.timestamp, mimeType = photo.mimeType,
            )
            result.isSuccess
        } catch (e: Exception) {
            Log.e(TAG, "Upload failed: ${photo.displayName}: ${e.message}")
            false
        }
    }

    private fun scanMediaStoreSince(sinceMs: Long): List<MediaStorePhoto> {
        val results = mutableListOf<MediaStorePhoto>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATA, MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_ADDED, MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.RELATIVE_PATH,
        )

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
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)

                while (cursor.moveToNext()) {
                    val path = cursor.getString(dataCol) ?: continue
                    results.add(
                        MediaStorePhoto(
                            id = cursor.getLong(idCol), filePath = path,
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
}

data class MediaStorePhoto(
    val id: Long, val filePath: String, val displayName: String,
    val fileSize: Long, val timestamp: Long, val mimeType: String,
)

data class SyncResult(val success: Int, val failed: Int, val total: Int)
