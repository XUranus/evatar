package com.evatar.app.network

import android.content.Context
import android.os.Build
import android.util.Log
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

class ApiClient(private val context: Context) {

    companion object {
        private const val TAG = "ApiClient"
        private const val PREF_NAME = "evatar_prefs"
        private const val KEY_SERVER_URL = "server_url"
        private const val DEFAULT_SERVER_URL = "http://192.168.0.107:8000"
    }

    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(logging)
        .build()

    // Chat client with longer read timeout (LLM calls can take 60-120s)
    private val chatClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .addInterceptor(logging)
        .build()

    val deviceName: String by lazy {
        "${Build.MANUFACTURER} ${Build.MODEL}"
    }

    fun getServerUrl(): String {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_SERVER_URL, DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL
    }

    fun setServerUrl(url: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_SERVER_URL, url).apply()
    }

    /**
     * Check which local IDs already exist on the server.
     * Returns the set of local IDs that are already synced.
     */
    fun checkDuplicates(localIds: List<Long>, deviceId: String): Set<Long> {
        if (localIds.isEmpty()) return emptySet()
        return try {
            val body = JSONObject().apply {
                put("local_ids", JSONArray(localIds.map { it.toString() }))
                put("device_id", deviceId)
            }.toString().toRequestBody("application/json".toMediaTypeOrNull())

            val request = Request.Builder()
                .url("${getServerUrl()}/api/photos/check-duplicates")
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val json = JSONObject(response.body?.string() ?: "{}")
                val arr = json.optJSONArray("existing") ?: JSONArray()
                (0 until arr.length()).mapNotNull { arr.optString(it).toLongOrNull() }.toSet()
            } else {
                Log.w(TAG, "checkDuplicates failed: ${response.code}")
                emptySet()
            }
        } catch (e: Exception) {
            Log.w(TAG, "checkDuplicates error: ${e.message}")
            emptySet()
        }
    }

    data class SyncState(
        val lastSyncedTsMs: Long = 0,
        val totalSynced: Int = 0,
    )

    /**
     * Ask server for the latest synced timestamp for this device.
     */
    fun getSyncState(deviceId: String): SyncState {
        return try {
            val request = Request.Builder()
                .url("${getServerUrl()}/api/photos/sync-state?device_id=$deviceId")
                .get()
                .build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val json = JSONObject(response.body?.string() ?: "{}")
                SyncState(
                    lastSyncedTsMs = json.optLong("last_synced_ts_ms", 0),
                    totalSynced = json.optInt("total_synced", 0),
                )
            } else {
                SyncState()
            }
        } catch (e: Exception) {
            Log.w(TAG, "getSyncState error: ${e.message}")
            SyncState()
        }
    }

    /**
     * Upload a photo with full metadata.
     */
    fun uploadPhoto(
        filePath: String,
        deviceId: String,
        localMediaStoreId: Long,
        displayName: String,
        timestamp: Long,
        mimeType: String,
        sourceType: String = "screenshot",
    ): Result<Int> {
        return try {
            val file = File(filePath)
            if (!file.exists()) {
                return Result.failure(Exception("File not found: $filePath"))
            }

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", displayName, file.asRequestBody(mimeType.toMediaTypeOrNull()))
                .addFormDataPart("device_id", deviceId)
                .addFormDataPart("device_name", deviceName)
                .addFormDataPart("source_type", sourceType)
                .addFormDataPart("local_media_store_id", localMediaStoreId.toString())
                .addFormDataPart("original_timestamp", timestamp.toString())
                .addFormDataPart("mime_type", mimeType)
                .build()

            val request = Request.Builder()
                .url("${getServerUrl()}/api/photos/upload")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: "{}"
                val json = JSONObject(body)
                val id = json.optInt("id", -1)
                val dedup = json.optBoolean("dedup", false)
                Log.i(TAG, "Upload success: id=$id dedup=$dedup")
                Result.success(id)
            } else {
                val error = response.body?.string() ?: "Unknown error"
                Log.e(TAG, "Upload failed: ${response.code} $error")
                Result.failure(Exception("Upload failed: ${response.code}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Upload error", e)
            Result.failure(e)
        }
    }

    fun checkHealth(): Boolean {
        return try {
            val request = Request.Builder()
                .url("${getServerUrl()}/api/health")
                .get()
                .build()
            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    // --- Chat API ---

    data class ChatResult(
        val success: Boolean,
        val data: JSONObject? = null,
        val errorMessage: String = "",
        val statusCode: Int = 0,
    )

    fun sendMessage(message: String, conversationId: String?, filePath: String? = null): ChatResult {
        return try {
            val bodyBuilder = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("message", message)
            if (conversationId != null) bodyBuilder.addFormDataPart("conversation_id", conversationId)
            if (filePath != null) {
                val file = java.io.File(filePath)
                if (file.exists()) {
                    bodyBuilder.addFormDataPart("file", file.name, file.asRequestBody("application/octet-stream".toMediaTypeOrNull()))
                }
            }

            val request = Request.Builder()
                .url("${getServerUrl()}/api/chat/send")
                .post(bodyBuilder.build())
                .build()

            val response = chatClient.newCall(request).execute()
            if (response.isSuccessful) {
                ChatResult(success = true, data = JSONObject(response.body?.string() ?: "{}"))
            } else {
                val errBody = response.body?.string() ?: ""
                val errMsg = try { JSONObject(errBody).optString("detail", errBody) } catch (_: Exception) { errBody }
                Log.e(TAG, "sendMessage HTTP ${response.code}: $errMsg")
                ChatResult(success = false, errorMessage = "HTTP ${response.code}: $errMsg", statusCode = response.code)
            }
        } catch (e: java.net.SocketTimeoutException) {
            Log.e(TAG, "sendMessage timeout", e)
            ChatResult(success = false, errorMessage = "请求超时（180s），AI 可能正在处理大量内容")
        } catch (e: java.net.ConnectException) {
            Log.e(TAG, "sendMessage connect error", e)
            ChatResult(success = false, errorMessage = "无法连接服务端: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "sendMessage error", e)
            ChatResult(success = false, errorMessage = "${e.javaClass.simpleName}: ${e.message}")
        }
    }

    fun getConversations(): JSONArray {
        return try {
            val request = Request.Builder()
                .url("${getServerUrl()}/api/chat/conversations")
                .get()
                .build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val json = JSONObject(response.body?.string() ?: "{}")
                json.optJSONArray("conversations") ?: JSONArray()
            } else JSONArray()
        } catch (e: Exception) {
            Log.e(TAG, "getConversations error", e)
            JSONArray()
        }
    }

    fun deleteConversation(conversationId: String): Boolean {
        return try {
            val request = Request.Builder()
                .url("${getServerUrl()}/api/chat/conversations/$conversationId")
                .delete()
                .build()
            client.newCall(request).execute().isSuccessful
        } catch (e: Exception) {
            Log.e(TAG, "deleteConversation error", e)
            false
        }
    }

    fun getConversationMessages(conversationId: String): JSONArray {
        return try {
            val request = Request.Builder()
                .url("${getServerUrl()}/api/chat/conversations/$conversationId")
                .get()
                .build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val json = JSONObject(response.body?.string() ?: "{}")
                json.optJSONArray("messages") ?: JSONArray()
            } else JSONArray()
        } catch (e: Exception) {
            JSONArray()
        }
    }
}
