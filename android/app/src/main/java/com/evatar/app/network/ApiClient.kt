package com.evatar.app.network

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
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
        private const val DEFAULT_SERVER_URL = ""
        private const val MAX_RETRIES = 3
        private val RETRY_DELAYS = longArrayOf(1000, 2000, 4000)

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

    val appVersion: String by lazy {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }

    // Cache server URL in memory
    @Volatile
    private var cachedUrl: String? = null

    fun getServerUrl(): String {
        return cachedUrl ?: synchronized(this) {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            val url = prefs.getString(KEY_SERVER_URL, DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL
            cachedUrl = url
            url
        }
    }

    fun isServerConfigured(): Boolean = getServerUrl().isNotBlank()

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

    /** Execute a request with retry logic. */
    private inline fun <T> executeWithRetry(request: Request, default: T, transform: (Response) -> T): T {
        var lastException: Exception? = null
        for (attempt in 0 until MAX_RETRIES) {
            try {
                return execute(request, transform)
            } catch (e: java.net.SocketTimeoutException) {
                lastException = e
                Log.w(TAG, "Request timeout, attempt ${attempt + 1}/$MAX_RETRIES")
            } catch (e: java.net.ConnectException) {
                lastException = e
                Log.w(TAG, "Connection failed, attempt ${attempt + 1}/$MAX_RETRIES")
            } catch (e: Exception) {
                throw e // Non-retryable error
            }
            if (attempt < MAX_RETRIES - 1) {
                try { Thread.sleep(RETRY_DELAYS[attempt]) } catch (_: InterruptedException) { break }
            }
        }
        Log.w(TAG, "Request failed after $MAX_RETRIES attempts: ${lastException?.message}")
        return default
    }

    // ── Health ──

    suspend fun checkHealth(): Boolean = withContext(Dispatchers.IO) {
        if (!isServerConfigured()) return@withContext false
        try {
            val request = Request.Builder().url("${getServerUrl()}/api/health").get().build()
            executeWithRetry(request, false) { it.isSuccessful }
        } catch (e: Exception) {
            Log.w(TAG, "checkHealth: ${e.javaClass.simpleName}: ${e.message}")
            false
        }
    }

    // ── Sync state ──

    data class SyncState(val lastSyncedTsMs: Long = 0, val totalSynced: Int = 0)

    suspend fun getSyncState(deviceId: String): SyncState = withContext(Dispatchers.IO) {
        if (!isServerConfigured()) return@withContext SyncState()
        try {
            val request = Request.Builder().url("${getServerUrl()}/api/photos/sync-state?device_id=$deviceId").get().build()
            executeWithRetry(request, SyncState()) { resp ->
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

    suspend fun uploadPhoto(
        filePath: String, deviceId: String, localMediaStoreId: Long,
        displayName: String, timestamp: Long, mimeType: String,
    ): Result<Int> = withContext(Dispatchers.IO) {
        if (!isServerConfigured()) return@withContext Result.failure(Exception("Server not configured"))
        try {
            val file = File(filePath)
            if (!file.exists()) return@withContext Result.failure(Exception("File not found"))

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
            var lastException: Exception? = null
            for (attempt in 0 until MAX_RETRIES) {
                try {
                    return@withContext execute(request) { resp ->
                        if (resp.isSuccessful) {
                            val json = JSONObject(resp.body?.string() ?: "{}")
                            Result.success(json.optInt("id", -1))
                        } else {
                            Result.failure(Exception("HTTP ${resp.code}"))
                        }
                    }
                } catch (e: java.net.SocketTimeoutException) {
                    lastException = e
                    Log.w(TAG, "uploadPhoto timeout, attempt ${attempt + 1}/$MAX_RETRIES")
                } catch (e: java.net.ConnectException) {
                    lastException = e
                    Log.w(TAG, "uploadPhoto connection failed, attempt ${attempt + 1}/$MAX_RETRIES")
                }
                if (attempt < MAX_RETRIES - 1) {
                    try { kotlinx.coroutines.delay(RETRY_DELAYS[attempt]) } catch (_: Exception) { break }
                }
            }
            Result.failure(lastException ?: Exception("Upload failed after retries"))
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

    suspend fun sendMessage(message: String, conversationId: String?, filePath: String? = null): ChatResult = withContext(Dispatchers.IO) {
        if (!isServerConfigured()) return@withContext ChatResult(success = false, errorMessage = "Server not configured")
        try {
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
            var lastException: Exception? = null
            for (attempt in 0 until MAX_RETRIES) {
                try {
                    return@withContext execute(request) { resp ->
                        if (resp.isSuccessful) {
                            ChatResult(success = true, data = JSONObject(resp.body?.string() ?: "{}"))
                        } else {
                            val errBody = resp.body?.string() ?: ""
                            val errMsg = try { JSONObject(errBody).optString("detail", errBody) } catch (_: Exception) { errBody }
                            ChatResult(success = false, errorMessage = "HTTP ${resp.code}: $errMsg", statusCode = resp.code)
                        }
                    }
                } catch (e: java.net.SocketTimeoutException) {
                    lastException = e
                } catch (e: java.net.ConnectException) {
                    lastException = e
                } catch (e: Exception) {
                    return@withContext ChatResult(success = false, errorMessage = "${e.javaClass.simpleName}: ${e.message}")
                }
                if (attempt < MAX_RETRIES - 1) {
                    try { kotlinx.coroutines.delay(RETRY_DELAYS[attempt]) } catch (_: Exception) { break }
                }
            }
            when (lastException) {
                is java.net.SocketTimeoutException -> ChatResult(success = false, errorMessage = "请求超时，AI 可能正在处理大量内容")
                is java.net.ConnectException -> ChatResult(success = false, errorMessage = "无法连接服务端: ${lastException?.message}")
                else -> ChatResult(success = false, errorMessage = "请求失败: ${lastException?.message}")
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

    suspend fun getConversations(): JSONArray = withContext(Dispatchers.IO) {
        if (!isServerConfigured()) return@withContext JSONArray()
        try {
            val request = Request.Builder().url("${getServerUrl()}/api/chat/conversations").get().build()
            executeWithRetry(request, JSONArray()) { resp ->
                if (resp.isSuccessful) {
                    JSONObject(resp.body?.string() ?: "{}").optJSONArray("conversations") ?: JSONArray()
                } else JSONArray()
            }
        } catch (e: Exception) {
            Log.e(TAG, "getConversations error", e)
            JSONArray()
        }
    }

    suspend fun deleteConversation(conversationId: String): Boolean = withContext(Dispatchers.IO) {
        if (!isServerConfigured()) return@withContext false
        try {
            val request = Request.Builder().url("${getServerUrl()}/api/chat/conversations/$conversationId").delete().build()
            executeWithRetry(request, false) { it.isSuccessful }
        } catch (e: Exception) {
            Log.e(TAG, "deleteConversation error", e)
            false
        }
    }

    suspend fun getConversationMessages(conversationId: String): JSONArray = withContext(Dispatchers.IO) {
        if (!isServerConfigured()) return@withContext JSONArray()
        try {
            val request = Request.Builder().url("${getServerUrl()}/api/chat/conversations/$conversationId").get().build()
            executeWithRetry(request, JSONArray()) { resp ->
                if (resp.isSuccessful) {
                    JSONObject(resp.body?.string() ?: "{}").optJSONArray("messages") ?: JSONArray()
                } else JSONArray()
            }
        } catch (e: Exception) {
            Log.e(TAG, "getConversationMessages error", e)
            JSONArray()
        }
    }

    // ── Dynamics ──

    /**
     * Register this device with the server for push notifications.
     */
    suspend fun registerDevice(deviceId: String): Boolean = withContext(Dispatchers.IO) {
        if (!isServerConfigured()) return@withContext false
        try {
            val body = JSONObject().apply {
                put("device_id", deviceId)
                put("token", deviceId)  // placeholder until FCM is integrated
                put("platform", "android")
                put("device_name", deviceName)
                put("device_model", "${Build.MODEL}")
                put("app_version", appVersion)
            }.toString().toRequestBody("application/json".toMediaTypeOrNull())

            val request = Request.Builder()
                .url("${getServerUrl()}/api/push/register")
                .post(body)
                .build()
            executeWithRetry(request, false) { it.isSuccessful }
        } catch (e: Exception) {
            Log.e(TAG, "registerDevice error", e)
            false
        }
    }

    suspend fun getDynamics(category: String? = null, page: Int = 1, pageSize: Int = 50): JSONObject = withContext(Dispatchers.IO) {
        if (!isServerConfigured()) return@withContext JSONObject()
        try {
            val urlBuilder = StringBuilder("${getServerUrl()}/api/dynamics?page=$page&page_size=$pageSize")
            if (!category.isNullOrEmpty()) urlBuilder.append("&category=$category")
            val request = Request.Builder().url(urlBuilder.toString()).get().build()
            executeWithRetry(request, JSONObject()) { resp ->
                if (resp.isSuccessful) {
                    JSONObject(resp.body?.string() ?: "{}")
                } else JSONObject()
            }
        } catch (e: Exception) {
            Log.e(TAG, "getDynamics error", e)
            JSONObject()
        }
    }
}
