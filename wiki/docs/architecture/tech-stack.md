---
sidebar_position: 3
title: 技术栈
description: 项目使用的所有技术选型及版本信息
---

# 技术栈

本文列出 Evatar 项目使用的所有技术及其版本和用途，基于实际 `requirements.txt`、`package.json` 和 `build.gradle.kts`。

---

## 后端 (Python)

后端位于 `backend/` 目录，基于 Python FastAPI 框架。

| 技术 | 版本 | 用途 |
|------|------|------|
| **Python** | 3.11+ | 运行时环境，使用 `asyncio` 异步编程 |
| **FastAPI** | 0.115.12 | Web 框架，自动生成 OpenAPI 文档，依赖注入 |
| **Uvicorn** | 0.34.3 | ASGI 服务器，支持 WebSocket 和 HTTP/2 |
| **SQLAlchemy** | 2.0.41 | ORM 框架，声明式模型定义，查询构建 |
| **SQLite** | 内置 | 嵌入式数据库，启用 WAL 模式和 FTS5 全文检索 |
| **httpx** | 0.28.1 | 异步 HTTP 客户端，用于调用 LLM API 和搜索 API |
| **Pillow** | 11.2.1 | 图片处理，生成缩略图和缩放大图（>2048px） |
| **Pydantic** | 2.11.3 | 数据校验和序列化 |
| **pydantic-settings** | 2.9.1 | 环境变量配置管理，前缀 `EVATAR_` |
| **python-multipart** | 0.0.20 | 文件上传解析（Multipart form data） |
| **aiofiles** | 24.1.0 | 异步文件 I/O |
| **cryptography** | 44.0.3 | Fernet 对称加密，保护敏感数据字段 |

### 后端依赖安装

```bash
cd backend
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
```

完整的 `requirements.txt`：

```
fastapi==0.115.12
uvicorn[standard]==0.34.3
sqlalchemy==2.0.41
aiofiles==24.1.0
httpx==0.28.1
pillow==11.2.1
python-multipart==0.0.20
pydantic==2.11.3
pydantic-settings==2.9.1
cryptography==44.0.3
```

### LLM 集成

后端通过 OpenAI 兼容 API 调用 LLM，默认配置：

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `llm_base_url` | `https://token-plan-cn.xiaomimimo.com/v1` | API 端点 |
| `llm_model` | `mimo-v2.5` | 模型名称（支持 Vision + Tool Calling） |
| `llm_max_tokens` | `4096` | 最大输出 token 数 |
| `llm_temperature` | `0.1` | 生成温度（截图分析使用低温度保证准确性） |
| `agent_max_rounds` | `3` | Agent 工具调用最大轮次 |
| `agent_history_limit` | `20` | 聊天历史消息加载数量 |

### Web 搜索集成

| 服务 | 优先级 | 配置变量 |
|------|--------|----------|
| **Tavily** | 优先 | `EVATAR_TAVILY_API_KEY` |
| **Brave Search** | 备选 | `EVATAR_BRAVE_API_KEY` |

---

## Android 客户端 (Kotlin)

Android 客户端位于 `android/` 目录，最低支持 API 26 (Android 8.0)。

| 技术 | 版本 | 用途 |
|------|------|------|
| **Kotlin** | 2.0+ (plugin.compose) | 主要开发语言，协程异步编程 |
| **Jetpack Compose** | BOM 2024.06.00 | 声明式 UI 框架 |
| **Material3** | compose-bom | Material Design 3 组件库 |
| **OkHttp** | 4.12.0 | HTTP 客户端，支持连接池、拦截器、自动重试 |
| **Gson** | 2.11.0 | JSON 序列化/反序列化 |
| **WorkManager** | 2.9.1 | 后台定时任务调度（截图同步） |
| **Coil** | 2.6.0 | Compose 原生图片加载库 |
| **Accompanist** | 0.34.0 | 运行时权限请求封装 |
| **Navigation Compose** | 2.7.7 | 页面导航 |
| **Lifecycle** | 2.8.4 | ViewModel + Compose 生命周期管理 |
| **Coroutines** | 1.8.1 | Kotlin 协程，异步网络请求和并发上传 |

### 构建配置

```kotlin
// android/app/build.gradle.kts
android {
    namespace = "com.evatar.app"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.evatar.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
```

### Gradle 版本

| 工具 | 版本 |
|------|------|
| Gradle | 8.14 |
| Android Gradle Plugin | 通过 `plugins` 块声明 |
| Kotlin Plugin | `org.jetbrains.kotlin.android` + `org.jetbrains.kotlin.plugin.compose` |

### 关键设计模式

| 模式 | 实现 |
|------|------|
| **单例** | `ApiClient.getInstance(context)` — 共享 OkHttpClient 实例 |
| **MVVM** | `ChatViewModel` / `DynamicViewModel` / `SettingsViewModel` — 状态管理 |
| **后台同步** | `SyncWorker` (CoroutineWorker) + `SyncService` (ForegroundService) |
| **并发控制** | `Semaphore(3)` 限制截图上传并发数 |
| **重试机制** | `executeWithRetry()` — 最多 3 次，指数退避 (1s, 2s, 4s) |
| **缓存** | 服务器 URL 内存缓存 (`@Volatile cachedUrl`) |

### 设备 ID 生成

```kotlin
val deviceId: String by lazy {
    "${Build.MANUFACTURER}_${Build.MODEL}_${
        Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID)
    }"
}
// 示例: "Xiaomi_2312DRAABC_abc123def456"
```

---

## Web 前端 (React)

前端位于 `frontend/` 目录，使用 Vite 8 构建。

| 技术 | 版本 | 用途 |
|------|------|------|
| **React** | 19.2.6 | UI 框架，函数组件 + Hooks |
| **TypeScript** | 6.0.2 | 类型安全的 JavaScript 超集 |
| **Vite** | 8.0.12 | 构建工具，HMR 热更新，API 代理 |
| **Tailwind CSS** | 4.3.0 | 原子化 CSS 框架 |
| **@tailwindcss/vite** | 4.3.0 | Vite 插件集成 |
| **@tailwindcss/typography** | 0.5.19 | Markdown 排版样式 |
| **Axios** | 1.16.1 | HTTP 客户端，请求/响应拦截 |
| **react-markdown** | 10.1.0 | Markdown 渲染组件（聊天消息、动态笔记） |
| **lucide-react** | 1.17.0 | 图标库 |
| **i18next** | 26.3.0 | 国际化框架 |
| **react-i18next** | 17.0.8 | React i18n 绑定 |
| **i18next-browser-languagedetector** | 8.2.1 | 自动检测浏览器语言 |

### 开发配置

```typescript
// vite.config.ts
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:8421',  // 后端地址
        changeOrigin: true,
      },
    },
  },
})
```

### API 客户端

前端使用 Axios 封装了完整的 API 客户端（`src/api/client.ts`），覆盖所有后端端点：

| 模块 | 主要函数 |
|------|----------|
| **截图** | `getPhotos()`, `getPhoto()`, `getPhotoUrl()`, `deletePhoto()`, `getStats()` |
| **聊天** | `getConversations()`, `getConversation()`, `deleteConversation()` |
| **动态** | `getDynamics()`, `getDynamic()`, `markDynamicRead()`, `triggerReasoning()` |
| **记忆** | `getMemories()`, `getMemoryStats()` |
| **配置** | `getLLMConfig()`, `updateLLMConfig()`, `getLLMPresets()`, `applyLLMPreset()` |
| **数据** | `getDataStats()`, `getRetentionDays()`, `clearAllData()`, `exportData()` |
| **推送** | `registerPushToken()`, `sendTestNotification()`, `getDevices()` |

### 前端页面

| 页面 | 组件 | 功能 |
|------|------|------|
| **Dashboard** | `Dashboard.tsx` | 数据总览：截图统计、分析状态、意图/分类分布 |
| **Photos** | `Photos.tsx` | 截图列表：分页浏览、状态筛选、缩略图、详情查看 |
| **Chat** | `Chat.tsx` | 智能助手：多轮对话、Markdown 渲染、工具调用展示 |
| **Dynamics** | `Dynamics.tsx` | 动态笔记：游标分页、分类筛选、已读/置顶操作 |
| **Settings** | `Settings.tsx` | 系统配置：LLM 设置、数据管理、推送通知 |

---

## 完整版本对照表

| 类别 | 技术 | 版本 |
|------|------|------|
| **后端** | Python | 3.11+ |
| | FastAPI | 0.115.12 |
| | SQLAlchemy | 2.0.41 |
| | httpx | 0.28.1 |
| | Pillow | 11.2.1 |
| | Pydantic | 2.11.3 |
| | cryptography | 44.0.3 |
| **Android** | Kotlin | 2.0+ |
| | Jetpack Compose BOM | 2024.06.00 |
| | OkHttp | 4.12.0 |
| | WorkManager | 2.9.1 |
| | Coil | 2.6.0 |
| | Gradle | 8.14 |
| | compileSdk / targetSdk | 34 |
| | minSdk | 26 |
| | JDK | 17 |
| **前端** | React | 19.2.6 |
| | TypeScript | 6.0.2 |
| | Vite | 8.0.12 |
| | Tailwind CSS | 4.3.0 |
| | Axios | 1.16.1 |
| | react-markdown | 10.1.0 |
| | i18next | 26.3.0 |
| **文档** | Docusaurus | 3.x |
