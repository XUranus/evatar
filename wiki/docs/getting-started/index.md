---
sidebar_position: 1
title: 快速开始
description: 从零搭建 Evatar 开发环境并运行项目
---

# 快速开始

本章介绍如何从零搭建 Evatar 开发环境，启动后端、前端和 Android 客户端，并完成第一次截图同步。

## 项目结构

```
evatar/
├── backend/          # Python 后端 (FastAPI)
│   ├── api/          #   API 路由 (photos, chat, dynamics, memories...)
│   ├── services/     #   业务逻辑 (agent, pipeline, reasoner, memory...)
│   ├── models.py     #   SQLAlchemy 数据模型
│   ├── config.py     #   Pydantic Settings 配置
│   ├── main.py       #   FastAPI 应用入口
│   └── requirements.txt
├── frontend/         # React Web 前端
│   ├── src/
│   │   ├── pages/    #   Dashboard, Photos, Chat, Dynamics, Settings
│   │   ├── api/      #   Axios API 客户端
│   │   └── components/
│   └── package.json
├── android/          # Android 客户端
│   ├── app/src/main/java/com/evatar/app/
│   │   ├── sync/     #   SyncManager, SyncWorker, SyncService
│   │   ├── network/  #   ApiClient (OkHttp)
│   │   ├── ui/       #   Jetpack Compose 界面
│   │   └── viewmodel/
│   └── build.gradle.kts
└── wiki/             # 本文档站点 (Docusaurus)
```

---

## 步骤一：克隆仓库

```bash
git clone <repo-url> evatar
cd evatar
```

---

## 步骤二：启动后端

后端基于 Python FastAPI，使用 SQLite 作为数据库，无需额外安装数据库服务。

```bash
cd backend

# 创建并激活虚拟环境
python -m venv .venv
source .venv/bin/activate    # Linux/macOS
# .venv\Scripts\activate     # Windows

# 安装依赖
pip install -r requirements.txt

# 以开发模式启动（跳过认证）
EVATAR_DEV_MODE=true python -m uvicorn main:app --host 0.0.0.0 --port 8421 --reload
```

启动成功后，终端会显示：

```
INFO:     Started server process [xxxxx]
INFO:     Waiting for application startup.
INFO:     Starting Evatar backend...
INFO:     Database initialized at /path/to/backend/data/evatar.db
INFO:     Background scheduler started
INFO:     Application startup complete.
INFO:     Uvicorn running on http://0.0.0.0:8421
```

验证后端运行：

```bash
curl http://localhost:8421/api/health
# 期望输出: {"status": "ok"}
```

---

## 步骤三：启动前端

前端使用 React + Vite，开发模式下通过 Vite proxy 将 `/api` 请求转发到后端。

```bash
cd frontend

# 安装依赖
pnpm install

# 启动开发服务器
pnpm dev
```

启动成功后，终端会显示：

```
  VITE v8.x.x  ready in xxx ms

  ➜  Local:   http://localhost:3000/
  ➜  press h + enter to show help
```

在浏览器打开 `http://localhost:3000` 即可看到 Evatar Web 界面。

---

## 步骤四：构建 Android APK

Android 客户端最低支持 API 26 (Android 8.0)，使用 JDK 17 编译。

```bash
cd android

# 构建 Debug APK
./gradlew assembleDebug

# APK 输出路径
ls app/build/outputs/apk/debug/app-debug.apk
```

安装到设备：

```bash
# 通过 ADB 安装
adb install app/build/outputs/apk/debug/app-debug.apk

# 或直接在 Android Studio 中点击 Run
```

---

## 步骤五：配置服务器 URL

首次打开 Android 应用，会进入引导流程（`OnboardingScreen`）：

1. **欢迎页** — 点击"开始配置"
2. **服务器配置** — 输入后端地址，例如 `http://192.168.0.107:8421`，点击"测试连接"验证
3. **同步范围** — 选择同步时间范围（1天/3天/7天/30天/全部）
4. **开始同步** — 应用自动扫描并上传截图

:::tip
确保手机和电脑在同一局域网。使用 `ipconfig`（Windows）或 `ifconfig`（Linux/macOS）查看电脑 IP 地址。
:::

---

## 步骤六：首次截图同步

同步开始后，Android 端的 `SyncManager` 会：

1. 查询服务器获取上次同步时间戳（`GET /api/photos/sync-state`）
2. 扫描 `MediaStore` 中指定时间范围内的截图（`RELATIVE_PATH LIKE '%Screenshots%'`）
3. 使用 3 并发信号量（`Semaphore(MAX_CONCURRENT=3)`）批量上传截图
4. 服务器端保存图片文件，创建分析记录，自动触发 LLM 分析

在 Web 前端的 **Dashboard** 页面可以看到：
- 已同步的截图总数
- 分析状态分布（pending / processing / done / error）
- 内容分类和意图分布统计

---

## 端口与 URL 一览

| 服务 | 默认端口 | URL | 说明 |
|------|---------|-----|------|
| **后端 API** | `8421` | `http://localhost:8421` | FastAPI 服务 |
| **后端健康检查** | `8421` | `http://localhost:8421/api/health` | 返回 `{"status":"ok"}` |
| **前端开发服务器** | `3000` | `http://localhost:3000` | Vite dev server |
| **Vite API 代理** | `3000` | `http://localhost:3000/api/*` | 转发到 `localhost:8421` |
| **Android 客户端** | - | 配置的服务器地址 | 运行在 Android 设备上 |

:::info
CORS 默认允许的源：`http://localhost:3000`、`http://localhost:5173`、`http://localhost:8421`。可通过环境变量 `EVATAR_CORS_ORIGINS` 自定义（逗号分隔）。
:::

---

## 环境变量速查

| 变量名 | 默认值 | 说明 |
|--------|--------|------|
| `EVATAR_DEV_MODE` | `false` | 设为 `true` 跳过 API Key 认证 |
| `EVATAR_API_KEY` | 空 | API 认证密钥（生产环境必设） |
| `EVATAR_LLM_API_KEY` | 空 | LLM 服务 API Key |
| `EVATAR_LLM_BASE_URL` | 空 | LLM 服务地址 |
| `EVATAR_HOST` | `0.0.0.0` | 监听地址 |
| `EVATAR_PORT` | `8421` | 监听端口 |
| `EVATAR_RETENTION_DAYS` | `30` | 数据保留天数 |

---

## 下一步

- **[环境准备](./prerequisites.md)** — 详细安装各依赖工具
- **[第一次运行](./first-run.md)** — 完整的首次运行流程与验证
- **[架构概览](../architecture/index.md)** — 了解系统设计
