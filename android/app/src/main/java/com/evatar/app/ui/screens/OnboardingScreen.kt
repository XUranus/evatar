package com.evatar.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evatar.app.network.ApiClient
import com.evatar.app.sync.SyncManager
import com.evatar.app.ui.theme.EvatarColors
import com.evatar.app.ui.theme.EvatarTypography
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class OnboardingStep {
    WELCOME,
    SERVER_SETUP,
    SYNC_TIME,
    SYNCING,
    DONE,
}

data class SyncTimeOption(val label: String, val days: Int, val description: String)

val SYNC_TIME_OPTIONS = listOf(
    SyncTimeOption("1 天", 1, "仅同步最近一天的截图"),
    SyncTimeOption("3 天", 3, "同步最近三天的截图"),
    SyncTimeOption("7 天", 7, "同步最近一周的截图（推荐）"),
    SyncTimeOption("30 天", 30, "同步最近一个月的截图"),
    SyncTimeOption("全部", 0, "同步所有截图（可能需要较长时间）"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val apiClient = remember { ApiClient.getInstance(context) }
    val syncManager = remember { SyncManager(context) }

    var step by remember { mutableStateOf(OnboardingStep.WELCOME) }
    var serverUrl by remember { mutableStateOf("") }
    var urlError by remember { mutableStateOf<String?>(null) }
    var serverConnected by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var selectedDays by remember { mutableIntStateOf(7) }
    var syncProgress by remember { mutableStateOf("") }
    var syncDone by remember { mutableIntStateOf(0) }
    var syncTotal by remember { mutableIntStateOf(0) }

    fun checkServer(url: String) {
        scope.launch {
            checking = true
            urlError = null
            val trimmed = url.trim()
            when {
                trimmed.isEmpty() -> { urlError = "请输入服务端地址" }
                !trimmed.startsWith("http://") && !trimmed.startsWith("https://") -> {
                    urlError = "地址必须以 http:// 或 https:// 开头"
                }
                else -> {
                    apiClient.setServerUrl(trimmed)
                    val ok = withContext(Dispatchers.IO) { apiClient.checkHealth() }
                    serverConnected = ok
                    if (!ok) urlError = "无法连接到服务端，请检查地址和网络"
                }
            }
            checking = false
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Progress indicator
            if (step != OnboardingStep.WELCOME && step != OnboardingStep.DONE) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StepDot(active = step == OnboardingStep.SERVER_SETUP, label = "1")
                    StepLine()
                    StepDot(active = step == OnboardingStep.SYNC_TIME, label = "2")
                    StepLine()
                    StepDot(active = step == OnboardingStep.SYNCING, label = "3")
                }
            }

            when (step) {
                OnboardingStep.WELCOME -> WelcomeStep(
                    onNext = { step = OnboardingStep.SERVER_SETUP }
                )
                OnboardingStep.SERVER_SETUP -> ServerSetupStep(
                    serverUrl = serverUrl,
                    onUrlChange = { serverUrl = it; urlError = null; serverConnected = false },
                    urlError = urlError,
                    serverConnected = serverConnected,
                    checking = checking,
                    onCheck = { checkServer(serverUrl) },
                    onNext = { step = OnboardingStep.SYNC_TIME },
                    onBack = { step = OnboardingStep.WELCOME },
                )
                OnboardingStep.SYNC_TIME -> SyncTimeStep(
                    selectedDays = selectedDays,
                    onSelect = { selectedDays = it },
                    onStart = {
                        step = OnboardingStep.SYNCING
                        scope.launch {
                            // Register device
                            withContext(Dispatchers.IO) {
                                apiClient.registerDevice(syncManager.deviceId)
                            }
                            // Calculate since timestamp
                            val sinceMs = if (selectedDays == 0) 0L
                            else System.currentTimeMillis() - selectedDays * 24 * 60 * 60 * 1000L

                            // Set sync state on server
                            syncProgress = "正在准备同步..."
                            val syncState = withContext(Dispatchers.IO) {
                                apiClient.setSyncSince(syncManager.deviceId, sinceMs)
                            }

                            // Run initial sync
                            syncProgress = "正在同步截图..."
                            val result = withContext(Dispatchers.IO) {
                                syncManager.runSync { synced, failed, total ->
                                    syncDone = synced + failed
                                    syncTotal = total
                                    syncProgress = "已同步 $synced/$total"
                                }
                            }

                            syncDone = result.success
                            syncTotal = result.total
                            syncProgress = "同步完成：${result.success}/${result.total}"
                            delay(1000)
                            step = OnboardingStep.DONE
                        }
                    },
                    onBack = { step = OnboardingStep.SERVER_SETUP },
                )
                OnboardingStep.SYNCING -> SyncingStep(
                    progress = syncProgress,
                    done = syncDone,
                    total = syncTotal,
                )
                OnboardingStep.DONE -> DoneStep(onComplete = onComplete)
            }
        }
    }
}

@Composable
private fun WelcomeStep(onNext: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Logo
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(EvatarColors.DarkPrimary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Text("◈", fontSize = 36.sp, color = EvatarColors.DarkPrimary)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text("欢迎使用 Evatar", style = EvatarTypography.title1, color = MaterialTheme.colorScheme.onBackground)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "你的智能截图助手\n自动同步截图，AI 分析内容，生成有价值的笔记",
            style = EvatarTypography.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
        ) {
            Text("开始设置", style = EvatarTypography.headline)
        }
    }
}

@Composable
private fun ServerSetupStep(
    serverUrl: String,
    onUrlChange: (String) -> Unit,
    urlError: String?,
    serverConnected: Boolean,
    checking: Boolean,
    onCheck: () -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("连接服务端", style = EvatarTypography.title2, color = MaterialTheme.colorScheme.onBackground)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "输入 Evatar 服务端地址。服务端运行在你的电脑或服务器上。",
            style = EvatarTypography.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = serverUrl,
            onValueChange = onUrlChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("服务端地址") },
            placeholder = { Text("http://192.168.0.107:8421") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            isError = urlError != null,
            supportingText = if (urlError != null) {
                { Text(urlError, color = EvatarColors.DarkError) }
            } else null,
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Test connection button
        OutlinedButton(
            onClick = onCheck,
            enabled = serverUrl.isNotBlank() && !checking,
            modifier = Modifier.fillMaxWidth().height(44.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            if (checking) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(if (checking) "测试中..." else "测试连接")
        }

        // Connection status
        if (serverConnected) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp).background(
                    EvatarColors.DarkSuccess.copy(alpha = 0.1f),
                    RoundedCornerShape(8.dp)
                ).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = EvatarColors.DarkSuccess, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("连接成功", color = EvatarColors.DarkSuccess, style = EvatarTypography.subheadline)
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(12.dp)) {
                Text("返回")
            }
            Button(
                onClick = onNext,
                enabled = serverConnected,
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("下一步")
            }
        }
    }
}

@Composable
private fun SyncTimeStep(
    selectedDays: Int,
    onSelect: (Int) -> Unit,
    onStart: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("同步范围", style = EvatarTypography.title2, color = MaterialTheme.colorScheme.onBackground)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "选择从多久之前开始同步截图。首次同步会上传所选范围内的所有截图。",
            style = EvatarTypography.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(24.dp))

        SYNC_TIME_OPTIONS.forEach { option ->
            val selected = selectedDays == option.days
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { onSelect(option.days) },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (selected)
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    else MaterialTheme.colorScheme.surface,
                ),
                border = if (selected)
                    androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                else null,
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(option.label, style = EvatarTypography.headline, color = MaterialTheme.colorScheme.onSurface)
                        Text(option.description, style = EvatarTypography.caption1, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (selected) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(12.dp)) {
                Text("返回")
            }
            Button(onClick = onStart, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(12.dp)) {
                Text("开始同步")
            }
        }
    }
}

@Composable
private fun SyncingStep(progress: String, done: Int, total: Int) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(48.dp), strokeWidth = 4.dp, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(24.dp))
        Text("正在同步截图...", style = EvatarTypography.title2, color = MaterialTheme.colorScheme.onBackground)
        Spacer(modifier = Modifier.height(8.dp))
        Text(progress, style = EvatarTypography.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (total > 0) {
            Spacer(modifier = Modifier.height(16.dp))
            LinearProgressIndicator(
                progress = { done.toFloat() / total.coerceAtLeast(1).toFloat() },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            )
        }
    }
}

@Composable
private fun DoneStep(onComplete: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(EvatarColors.DarkSuccess.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = EvatarColors.DarkSuccess, modifier = Modifier.size(40.dp))
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text("设置完成！", style = EvatarTypography.title1, color = MaterialTheme.colorScheme.onBackground)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "截图同步已启动，AI 会在后台自动分析你的截图并生成笔记。\n\n你可以在「动态」页面查看分析结果，在「设置」中调整同步选项。",
            style = EvatarTypography.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onComplete,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
        ) {
            Text("开始使用", style = EvatarTypography.headline)
        }
    }
}

@Composable
private fun StepDot(active: Boolean, label: String) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            color = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StepLine() {
    Box(
        modifier = Modifier
            .width(32.dp)
            .height(2.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(1.dp))
    )
}
