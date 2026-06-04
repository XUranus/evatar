package com.evatar.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.evatar.app.R
import com.evatar.app.network.ApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class ShareReceiverActivity : ComponentActivity() {

    companion object {
        private const val TAG = "ShareReceiverActivity"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        when (intent?.action) {
            Intent.ACTION_SEND -> {
                val type = intent.type ?: ""
                when {
                    type.startsWith("image/") -> handleShareImage(intent)
                    type == "text/plain" -> handleShareText(intent)
                    else -> {
                        Toast.makeText(this, getString(R.string.share_unsupported_type), Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            }
            else -> finish()
        }
    }

    private fun handleShareImage(intent: Intent) {
        val imageUri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) ?: run {
            Toast.makeText(this, getString(R.string.share_no_image), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        scope.launch {
            try {
                val tempFile = copyUriToTempFile(imageUri)
                if (tempFile == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ShareReceiverActivity, getString(R.string.share_image_failed), Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    return@launch
                }

                val apiClient = ApiClient.getInstance(this@ShareReceiverActivity)
                val result = apiClient.uploadPhoto(
                    filePath = tempFile.absolutePath,
                    deviceId = "shared_image",
                    localMediaStoreId = 0L,
                    displayName = tempFile.name,
                    timestamp = System.currentTimeMillis(),
                    mimeType = contentResolver.getType(imageUri) ?: "image/jpeg"
                )

                tempFile.delete()

                withContext(Dispatchers.Main) {
                    if (result.isSuccess) {
                        Toast.makeText(this@ShareReceiverActivity, getString(R.string.share_success), Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@ShareReceiverActivity, getString(R.string.share_image_failed), Toast.LENGTH_SHORT).show()
                    }
                    finish()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Share image failed", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ShareReceiverActivity, getString(R.string.share_image_failed), Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    private fun handleShareText(intent: Intent) {
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: run {
            Toast.makeText(this, getString(R.string.share_no_text), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        scope.launch {
            try {
                val apiClient = ApiClient.getInstance(this@ShareReceiverActivity)
                val result = apiClient.sendMessage(message = text, conversationId = null)

                withContext(Dispatchers.Main) {
                    if (result.success) {
                        Toast.makeText(this@ShareReceiverActivity, getString(R.string.share_success), Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@ShareReceiverActivity, getString(R.string.share_text_failed), Toast.LENGTH_SHORT).show()
                    }
                    finish()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Share text failed", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ShareReceiverActivity, getString(R.string.share_text_failed), Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    private fun copyUriToTempFile(uri: Uri): File? {
        return try {
            val inputStream = contentResolver.openInputStream(uri) ?: return null
            val tempFile = File(cacheDir, "share_${System.currentTimeMillis()}.jpg")
            FileOutputStream(tempFile).use { output ->
                inputStream.use { input ->
                    input.copyTo(output)
                }
            }
            tempFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy URI to temp file", e)
            null
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
