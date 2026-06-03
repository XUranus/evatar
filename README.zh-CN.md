# Evatar

截图同步 & AI 分析助手 —— 通过手机截图理解你的生活，主动帮你整理信息。

## 功能

- 📱 **Android 原生应用**：后台常驻监听相册截图，自动同步到服务端
- 🖥️ **Web 管理后台**：查看同步照片、LLM 分析结果、统计数据
- 🤖 **AI 分析**：多模态大模型解析截图内容，提取意图、实体、摘要
- 💬 **多 LLM 支持**：快速切换 MiMo、通义千问、OpenAI、Claude、GLM、Kimi、DeepSeek
- 🌐 **国际化**：Web 和 Android 均支持中/英文

## 项目结构

```
evatar/
├── android/          # Android 原生应用 (Kotlin + Jetpack Compose)
├── backend/          # Python 后端 (FastAPI + SQLite)
└── frontend/         # Web 管理后台 (React + Vite + Tailwind CSS)
```

## 快速开始

### 1. 启动后端

```bash
cd backend
python3.11 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
python main.py
```

后端运行在 `http://localhost:8000`

### 2. 启动前端

```bash
cd frontend
pnpm install
pnpm dev
```

前端运行在 `http://localhost:3000`，自动代理 API 到后端。

### 3. 构建 Android 应用

```bash
cd android
./gradlew :app:assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

### 4. 使用

1. 打开 Android 应用，配置服务端地址（默认 `http://192.168.0.107:8000`）
2. 确保服务端已连接（状态卡片显示绿色）
3. 点击「开始同步」
4. 手机截图会自动上传并由 LLM 分析
5. 在 Web 管理后台查看分析结果

## LLM 配置

在 Web 后端的「LLM 配置」页面可以：

- 快速选择预设服务商（小米 MiMo、通义千问 DashScope、OpenAI、Claude、智谱 GLM、Kimi、DeepSeek）
- 手动配置 API 地址、Key、模型名称
- 设置最大上下文 tokens 和温度

## Android 保活

- **前台服务**：常驻通知栏保活
- **悬浮窗**：圆形半透明气泡，显示同步状态（绿=空闲，黄=同步中，红=错误）
- **电池优化**：引导用户将应用从电池优化中移除

## 技术栈

| 组件 | 技术 |
|------|------|
| Android | Kotlin, Jetpack Compose, Room, OkHttp |
| 后端 | Python, FastAPI, SQLAlchemy, SQLite, httpx |
| 前端 | React, TypeScript, Vite, Tailwind CSS, i18next |
| LLM | OpenAI 兼容 API (多模态) |

## API 端点

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/photos/upload` | 上传照片 |
| GET | `/api/photos` | 照片列表 |
| GET | `/api/photos/{id}` | 照片详情+分析 |
| GET | `/api/photos/{id}/image` | 原图 |
| GET | `/api/stats` | 统计数据 |
| GET | `/api/config/llm` | LLM 配置 |
| PUT | `/api/config/llm` | 更新 LLM 配置 |
| GET | `/api/config/llm/presets` | 预设列表 |
| POST | `/api/config/llm/presets/{name}/apply` | 应用预设 |
