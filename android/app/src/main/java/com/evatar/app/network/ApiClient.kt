package com.evatar.app.network

import android.content.Context
import android.os.Build
import android.util.Log
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Singleton API client. One OkHttpClient instance shared across the app.
 */
class ApiClient private constructor(private val context: Context) {

    companion object {
        private const val TAG = "ApiClient"
        private const val PREF_NAME = "evatar_prefs"
        private const val KEY_SERVER_URL = "server_url"
        private const val DEFAULT_SERVER_URL = "http://192.168.0.107:8000"

        @Volatile
        private var INSTANCE: ApiClient? = null

        fun getInstance(context: Context): ApiClient {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ApiClient(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .addInterceptor(logging)
        .build()

    val deviceName: String by lazy { "${Build.MANUFACTURER} ${Build.MODEL}" }

    // Cache server URL in memory
    @Volatile
    private var cachedUrl: String? = null

    fun getServerUrl(): String {
        return cachedUrl ?: synchronized(this) {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            (prefs.getString(KEY_SERVER_URL, DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL).also { cachedUrl = it }
        }
    }

    fun setServerUrl(url: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_SERVER_URL, url).apply()
        cachedUrl = url
    }

    /** Execute a request and auto-close the response body. */
    private inline fun <T> execute(request: Request, transform: (Response) -> T): T {
        val response = client.newCall(request).execute()
        return response.use { transform(it) }
    }

    // ── Health ──

    fun checkHealth(): Boolean {
        return try {
            val request = Request.Builder().url("${getServerUrl()}/api/health").get().build()
            execute(request) { it.isSuccessful }
        } catch (e: Exception) {
            Log.w(TAG, "checkHealth: ${e.javaClass.simpleName}: ${e.message}")
            false
        }
    }

    // ── Sync state ──

    data class SyncState(val lastSyncedTsMs: Long = 0, val totalSynced: Int = 0)

    fun getSyncState(deviceId: String): SyncState {
        return try {
            val request = Request.Builder().url("${getServerUrl()}/api/photos/sync-state?device_id=$deviceId").get().build()
            execute(request) { resp ->
                if (resp.isSuccessful) {
                    val json = JSONObject(resp.body?.string() ?: "{}")
                    SyncState(json.optLong("last_synced_ts_ms", 0), json.optInt("total_synced", 0))
                } else SyncState()
            }
        } catch (e: Exception) {
            Log.w(TAG, "getSyncState: ${e.message}")
            SyncState()
        }
    }

    // ── Upload ──

    fun uploadPhoto(
        filePath: String, deviceId: String, localMediaStoreId: Long,
        displayName: String, timestamp: Long, mimeType: String,
    ): Result<Int> {
        return try {
            val file = File(filePath)
            if (!file.exists()) return Result.failure(Exception("File not found"))

            val body = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("file", displayName, file.asRequestBody(mimeType.toMediaTypeOrNull()))
                .addFormDataPart("device_id", deviceId)
                .addFormDataPart("device_name", deviceName)
                .addFormDataPart("source_type", "screenshot")
                .addFormDataPart("local_media_store_id", localMediaStoreId.toString())
                .addFormDataPart("original_timestamp", timestamp.toString())
                .addFormDataPart("mime_type", mimeType)
                .build()

            val request = Request.Builder().url("${getServerUrl()}/api/photos/upload").post(body).build()
            execute(request) { resp ->
                if (resp.isSuccessful) {
                    val json = JSONObject(resp.body?.string() ?: "{}")
                    Result.success(json.optInt("id", -1))
                } else {
                    Result.failure(Exception("HTTP ${resp.code}"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "uploadPhoto error", e)
            Result.failure(e)
        }
    }

    // ── Chat ──

    data class ChatResult(
        val success: Boolean, val data: JSONObject? = null,
        val errorMessage: String = "", val statusCode: Int = 0,
    )

    fun sendMessage(message: String, conversationId: String?, filePath: String? = null): ChatResult {
        return try {
            val bodyBuilder = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("message", message)
            if (conversationId != null) bodyBuilder.addFormDataPart("conversation_id", conversationId)
            if (filePath != null) {
                val file = File(filePath)
                if (file.exists()) {
                    bodyBuilder.addFormDataPart("file", file.name, file.asRequestBody("application/octet-stream".toMediaTypeOrNull()))
                }
            }

            val request = Request.Builder().url("${getServerUrl()}/api/chat/send-with-file").post(bodyBuilder.build()).build()
            execute(request) { resp ->
                if (resp.isSuccessful) {
                    ChatResult(success = true, data = JSONObject(resp.body?.string() ?: "{}"))
                } else {
                    val errBody = resp.body?.string() ?: ""
                    val errMsg = try { JSONObject(errBody).optString("detail", errBody) } catch (_: Exception) { errBody }
                    ChatResult(success = false, errorMessage = "HTTP ${resp.code}: $errMsg", statusCode = resp.code)
                }
            }
        } catch (e: java.net.SocketTimeoutException) {
            ChatResult(success = false, errorMessage = "请求超时，AI 可能正在处理大量内容")
        } catch (e: java.net.ConnectException) {
            ChatResult(success = false, errorMessage = "无法连接服务端: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "sendMessage error", e)
            ChatResult(success = false, errorMessage = "${e.javaClass.simpleName}: ${e.message}")
        }
    }

    fun getConversations(): JSONArray {
        return try {
            val request = Request.Builder().url("${getServerUrl()}/api/chat/conversations").get().build()
            execute(request) { resp ->
                if (resp.isSuccessful) {
                    JSONObject(resp.body?.string() ?: "{}").optJSONArray("conversations") ?: JSONArray()
                } else JSONArray()
            }
        } catch (e: Exception) {
            Log.e(TAG, "getConversations error", e)
            JSONArray()
        }
    }

    fun deleteConversation(conversationId: String): Boolean {
        return try {
            val request = Request.Builder().url("${getServerUrl()}/api/chat/conversations/$conversationId").delete().build()
            execute(request) { it.isSuccessful }
        } catch (e: Exception) {
            Log.e(TAG, "deleteConversation error", e)
            false
        }
    }

    fun getConversationMessages(conversationId: String): JSONArray {
        return try {
            val request = Request.Builder().url("${getServerUrl()}/api/chat/conversations/$conversationId").get().build()
            execute(request) { resp ->
                if (resp.isSuccessful) {
                    JSONObject(resp.body?.string() ?: "{}").optJSONArray("messages") ?: JSONArray()
                } else JSONArray()
            }
        } catch (e: Exception) {
            Log.e(TAG, "getConversationMessages error", e)
            JSONArray()
        }
    }
}
