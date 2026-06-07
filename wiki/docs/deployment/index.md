---
sidebar_position: 1
title: 部署指南
description: Evatar 开发与生产环境完整部署说明
---

# 部署指南

本文档涵盖 Evatar 的开发环境搭建、生产环境部署、Docker 容器化部署，以及环境变量参考和常见问题排查。

---

## 开发环境部署

### 前置条件

| 工具 | 版本要求 | 用途 |
|------|---------|------|
| Python | 3.11+ | 后端运行时 |
| Node.js | 18+ | 前端构建 |
| pnpm | 8+ | 前端包管理器 |
| JDK | 17 | Android 编译 |
| Android Studio | 最新版 | Android 开发（可选） |
| Git | 2.30+ | 版本管理 |

### 1. 后端

后端使用 Python + FastAPI + SQLite，所有数据存储在本地 `data/` 目录下。

```bash
cd backend

# 创建虚拟环境
python3.11 -m venv .venv
source .venv/bin/activate

# 安装依赖
pip install -r requirements.txt

# 启动开发服务器
python main.py
```

后端默认运行在 `http://0.0.0.0:8421`。启动时会自动：

- 创建 `data/` 目录和 `data/photos/` 目录
- 初始化 SQLite 数据库 `data/evatar.db`（启用 WAL 模式）
- 启动后台调度器（每小时推理、每日记忆衰减和数据清理）

如果需要使用 LLM 分析功能，必须先设置 API Key：

```bash
export EVATAR_LLM_API_KEY="your-api-key-here"
export EVATAR_LLM_BASE_URL="https://api.example.com/v1"
export EVATAR_LLM_MODEL="your-model-name"
python main.py
```

如需关闭认证（仅限开发环境）：

```bash
export EVATAR_DEV_MODE=true
python main.py
```

### 2. 前端

前端使用 React + TypeScript + Vite + Tailwind CSS。

```bash
cd frontend

# 安装依赖
pnpm install

# 启动开发服务器
pnpm dev
```

前端默认运行在 `http://localhost:3000`，通过 Vite 开发服务器自动将 `/api` 请求代理到后端 `http://localhost:8421`（参见 `frontend/vite.config.ts` 中的 proxy 配置）。

其他可用命令：

```bash
pnpm build    # 生产构建，输出到 dist/
pnpm lint     # ESLint 代码检查
pnpm preview  # 预览生产构建
```

### 3. Android

Android 应用使用 Kotlin + Jetpack Compose，最低支持 API 26（Android 8.0）。

```bash
cd android

# 构建 Debug APK
./gradlew :app:assembleDebug

# 安装到已连接的设备或模拟器
adb install app/build/outputs/apk/debug/app-debug.apk
```

构建要求：

- JDK 17（已在 `android/app/build.gradle.kts` 中配置 `jvmTarget = "17"`）
- Android SDK，compileSdk 34
- Gradle 会自动下载所需依赖

### 4. 使用流程

1. 启动后端：`cd backend && python main.py`
2. 启动前端：`cd frontend && pnpm dev`
3. 构建并安装 Android 应用
4. 在 Android 应用中配置服务端地址（默认 `http://localhost:8421`）
5. 确认连接状态为绿色后，点击「开始同步」
6. 手机截图会自动上传，由 LLM 进行分析
7. 在 Web 管理后台查看分析结果、统计数据和对话

---

## 生产环境部署

### 后端：Gunicorn + Uvicorn Workers

生产环境建议使用 Gunicorn 作为进程管理器，搭配 Uvicorn worker 处理异步请求：

```bash
cd backend
source .venv/bin/activate

# 需要先安装 gunicorn
pip install gunicorn

# 启动生产服务器（4 个 worker 进程）
gunicorn main:app \
  --workers 4 \
  --worker-class uvicorn.workers.UvicornWorker \
  --bind 0.0.0.0:8421 \
  --timeout 120 \
  --access-logfile - \
  --error-logfile -
```

#### Systemd 服务文件

创建 `/etc/systemd/system/evatar.service`：

```ini
[Unit]
Description=Evatar Backend
After=network.target

[Service]
Type=exec
User=evatar
Group=evatar
WorkingDirectory=/opt/evatar/backend
Environment="PATH=/opt/evatar/backend/.venv/bin"
Environment="EVATAR_LLM_API_KEY=your-api-key-here"
Environment="EVATAR_LLM_BASE_URL=https://api.example.com/v1"
Environment="EVATAR_API_KEY=your-auth-key-here"
Environment="EVATAR_ENCRYPTION_KEY=your-encryption-key-here"
ExecStart=/opt/evatar/backend/.venv/bin/gunicorn main:app \
  --workers 4 \
  --worker-class uvicorn.workers.UvicornWorker \
  --bind 127.0.0.1:8421 \
  --timeout 120
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
```

启动并设置开机自启：

```bash
sudo systemctl daemon-reload
sudo systemctl enable evatar
sudo systemctl start evatar
sudo systemctl status evatar
```

### 前端：静态文件 + Nginx

构建前端静态文件：

```bash
cd frontend
pnpm build
```

生成的文件位于 `frontend/dist/` 目录，将该目录部署到 Nginx。

#### Nginx 配置示例

创建 `/etc/nginx/sites-available/evatar`：

```nginx
server {
    listen 80;
    server_name evatar.example.com;

    # 前端静态文件
    root /opt/evatar/frontend/dist;
    index index.html;

    # 前端路由 — 所有非文件请求回退到 index.html
    location / {
        try_files $uri $uri/ /index.html;
    }

    # API 反向代理到后端
    location /api/ {
        proxy_pass http://127.0.0.1:8421;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        # 文件上传可能较大
        client_max_body_size 60m;

        # 长连接支持（SSE 流式响应）
        proxy_buffering off;
        proxy_read_timeout 300s;
    }

    # 静态资源缓存
    location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg|woff2?)$ {
        expires 30d;
        add_header Cache-Control "public, immutable";
    }
}
```

启用站点并重启 Nginx：

```bash
sudo ln -s /etc/nginx/sites-available/evatar /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl reload nginx
```

### Android：Release 构建

Release 构建需要签名配置。在 `android/` 目录下创建 `keystore.properties`（不要提交到版本控制）：

```properties
storeFile=../keystore.jks
storePassword=your-store-password
keyAlias=your-key-alias
keyPassword=your-key-password
```

然后在 `android/app/build.gradle.kts` 中添加签名配置：

```kotlin
signingConfigs {
    create("release") {
        val props = java.util.Properties()
        props.load(file("../keystore.properties").inputStream())
        storeFile = file(props["storeFile"] as String)
        storePassword = props["storePassword"] as String
        keyAlias = props["keyAlias"] as String
        keyPassword = props["keyPassword"] as String
    }
}

buildTypes {
    release {
        isMinifyEnabled = true
        signingConfig = signingConfigs.getByName("release")
        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
}
```

构建 Release APK：

```bash
cd android
./gradlew :app:assembleRelease
# APK 输出位置: app/build/outputs/apk/release/app-release.apk
```

### SQLite 注意事项

Evatar 使用 SQLite 作为数据库，在启动时自动启用 WAL（Write-Ahead Logging）模式以提高并发读写性能。相关代码位于 `backend/models.py` 的 `_enable_wal()` 函数中。

#### WAL 模式

SQLite 的 WAL 模式允许读写并发执行，非常适合 Evatar 的使用场景（前端读取 + Android 上传写入 + 后台分析写入）。

#### 备份策略

SQLite 数据库文件位于 `data/evatar.db`，备份时需要注意：

```bash
# 方法一：使用 SQLite 的 .backup 命令（推荐，支持在线备份）
sqlite3 /opt/evatar/backend/data/evatar.db ".backup '/backup/evatar-$(date +%Y%m%d).db'"

# 方法二：直接复制（需要先停止服务或使用 WAL checkpoint）
sqlite3 /opt/evatar/backend/data/evatar.db "PRAGMA wal_checkpoint(FULL);"
cp /opt/evatar/backend/data/evatar.db /backup/evatar-$(date +%Y%m%d).db
cp /opt/evatar/backend/data/evatar.db-wal /backup/
cp /opt/evatar/backend/data/evatar.db-shm /backup/
```

建议配合 cron 定时任务每日备份：

```bash
# /etc/cron.d/evatar-backup
0 3 * * * evatar sqlite3 /opt/evatar/backend/data/evatar.db ".backup '/backup/evatar-$(date +\%Y\%m\%d).db'"
```

照片文件位于 `data/photos/` 目录，建议使用 rsync 或其他工具同步备份。

---

## Docker 部署

### 后端 Dockerfile

在项目根目录创建 `Dockerfile.backend`：

```dockerfile
FROM python:3.11-slim

WORKDIR /app

# 安装依赖
COPY backend/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt gunicorn

# 复制后端代码
COPY backend/ .

# 创建数据目录
RUN mkdir -p /app/data/photos

# 暴露端口
EXPOSE 8421

# 启动命令
CMD ["gunicorn", "main:app", \
     "--workers", "4", \
     "--worker-class", "uvicorn.workers.UvicornWorker", \
     "--bind", "0.0.0.0:8421", \
     "--timeout", "120"]
```

### 前端 Dockerfile

在项目根目录创建 `Dockerfile.frontend`：

```dockerfile
FROM node:18-alpine AS builder

WORKDIR /app
COPY frontend/package.json frontend/pnpm-lock.yaml ./
RUN corepack enable && pnpm install --frozen-lockfile
COPY frontend/ .
RUN pnpm build

FROM nginx:alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
```

### Nginx 配置（Docker 用）

创建 `nginx.conf`：

```nginx
server {
    listen 80;
    server_name _;

    root /usr/share/nginx/html;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location /api/ {
        proxy_pass http://backend:8421;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        client_max_body_size 60m;
        proxy_buffering off;
        proxy_read_timeout 300s;
    }

    location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg|woff2?)$ {
        expires 30d;
        add_header Cache-Control "public, immutable";
    }
}
```

### Docker Compose

创建 `docker-compose.yml`：

```yaml
version: "3.8"

services:
  backend:
    build:
      context: .
      dockerfile: Dockerfile.backend
    container_name: evatar-backend
    restart: unless-stopped
    ports:
      - "8421:8421"
    environment:
      - EVATAR_LLM_API_KEY=${EVATAR_LLM_API_KEY}
      - EVATAR_LLM_BASE_URL=${EVATAR_LLM_BASE_URL}
      - EVATAR_LLM_MODEL=${EVATAR_LLM_MODEL:-mimo-v2.5}
      - EVATAR_API_KEY=${EVATAR_API_KEY}
      - EVATAR_ENCRYPTION_KEY=${EVATAR_ENCRYPTION_KEY}
      - EVATAR_CORS_ORIGINS=${EVATAR_CORS_ORIGINS:-}
    volumes:
      - evatar-data:/app/data

  frontend:
    build:
      context: .
      dockerfile: Dockerfile.frontend
    container_name: evatar-frontend
    restart: unless-stopped
    ports:
      - "80:80"
    depends_on:
      - backend

volumes:
  evatar-data:
    driver: local
```

启动：

```bash
# 创建 .env 文件
cat > .env << 'EOF'
EVATAR_LLM_API_KEY=your-api-key
EVATAR_LLM_BASE_URL=https://api.example.com/v1
EVATAR_LLM_MODEL=mimo-v2.5
EVATAR_API_KEY=your-auth-key
EVATAR_ENCRYPTION_KEY=
EOF

# 构建并启动
docker compose up -d

# 查看日志
docker compose logs -f

# 停止
docker compose down
```

数据持久化通过 Docker volume `evatar-data` 实现，挂载到容器的 `/app/data` 目录，包含数据库文件和照片。

---

## 环境变量参考

Evatar 使用 `pydantic-settings` 管理配置，所有环境变量以 `EVATAR_` 为前缀（参见 `backend/config.py` 中的 `class Config: env_prefix = "EVATAR_"`）。

### 服务器配置

| 环境变量 | 类型 | 默认值 | 说明 |
|---------|------|--------|------|
| `EVATAR_HOST` | string | `0.0.0.0` | 监听地址 |
| `EVATAR_PORT` | int | `8421` | 监听端口 |
| `EVATAR_CORS_ORIGINS` | string | `""` | CORS 允许的源，逗号分隔。为空时默认允许 `localhost:3000`、`localhost:5173`、`localhost:8421` |

### 认证与安全

| 环境变量 | 类型 | 默认值 | 说明 |
|---------|------|--------|------|
| `EVATAR_API_KEY` | string | `""` | API 认证密钥。设置后所有请求需通过 `Authorization: Bearer <key>` 头认证（`/` 和 `/api/health` 除外） |
| `EVATAR_DEV_MODE` | bool | `false` | 开发模式。设为 `true` 时 API Key 和 LLM Key 未设置不会报错 |
| `EVATAR_ENCRYPTION_KEY` | string | `""` | Fernet 加密密钥，用于加密聊天消息和记忆中的敏感内容。为空时自动生成并存储在 `data/.encryption_key` 文件中 |

**安全建议**：生产环境务必设置 `EVATAR_API_KEY` 和 `EVATAR_ENCRYPTION_KEY`，并确保 `EVATAR_DEV_MODE` 为 `false`。自动生成的加密密钥仅适用于单机部署，多实例部署请显式设置统一的加密密钥。

### 存储配置

| 环境变量 | 类型 | 默认值 | 说明 |
|---------|------|--------|------|
| `EVATAR_DATA_DIR` | Path | `backend/data/` | 数据根目录 |
| `EVATAR_PHOTOS_DIR` | Path | `backend/data/photos/` | 照片存储目录 |
| `EVATAR_DB_PATH` | string | `backend/data/evatar.db` | SQLite 数据库文件路径 |
| `EVATAR_MAX_UPLOAD_BYTES` | int | `52428800` (50MB) | 单个文件上传大小限制 |
| `EVATAR_RETENTION_DAYS` | int | `30` | 数据保留天数，超期数据由后台调度器自动清理 |

### LLM 配置

| 环境变量 | 类型 | 默认值 | 说明 |
|---------|------|--------|------|
| `EVATAR_LLM_API_KEY` | string | `""` | LLM 服务 API Key（必填） |
| `EVATAR_LLM_BASE_URL` | string | `""` | LLM 服务 API 地址，如 `https://api.openai.com/v1` |
| `EVATAR_LLM_MODEL` | string | `mimo-v2.5` | 默认模型名称 |
| `EVATAR_LLM_MAX_TOKENS` | int | `4096` | 最大输出 token 数 |
| `EVATAR_LLM_TEMPERATURE` | float | `0.1` | 生成温度 |

LLM 配置也可以通过 Web 后台的「设置」页面在线修改，运行时配置存储在数据库的 `llm_config` 表中，支持快速切换预设服务商（MiMo、通义千问、OpenAI、Claude、GLM、Kimi、DeepSeek）。

### Agent 配置

| 环境变量 | 类型 | 默认值 | 说明 |
|---------|------|--------|------|
| `EVATAR_AGENT_MAX_ROUNDS` | int | `3` | Agent 最大推理轮数 |
| `EVATAR_AGENT_HISTORY_LIMIT` | int | `20` | 上下文历史消息数量限制 |

### Web 搜索（可选）

| 环境变量 | 类型 | 默认值 | 说明 |
|---------|------|--------|------|
| `EVATAR_TAVILY_API_KEY` | string | `""` | Tavily 搜索 API Key |
| `EVATAR_BRAVE_API_KEY` | string | `""` | Brave Search API Key |

### 推送通知（可选）

| 环境变量 | 类型 | 默认值 | 说明 |
|---------|------|--------|------|
| `EVATAR_FCM_PROJECT_ID` | string | `""` | Firebase Cloud Messaging 项目 ID |
| `EVATAR_FCM_CREDENTIALS_JSON` | string | `""` | FCM 服务账号 JSON 文件路径或内联 JSON |
| `EVATAR_PUSH_WEBHOOK_URL` | string | `""` | FCM 未配置时的备用 Webhook URL |

---

## 常见问题

### 端口冲突

**问题**：启动时报 `Address already in use` 或 `OSError: [Errno 98] Address already in use`。

**排查**：

```bash
# 查看占用 8421 端口的进程
lsof -i :8421
# 或
ss -tlnp | grep 8421
```

**解决**：停止占用端口的进程，或通过环境变量更改端口：

```bash
export EVATAR_PORT=9000
python main.py
```

前端开发服务器端口在 `frontend/vite.config.ts` 中配置，默认为 3000。如需修改，同时更新 `backend/main.py` 中的 CORS 默认源。

### 权限问题

**问题**：照片上传失败，或数据库写入报错。

**排查**：

```bash
# 检查数据目录权限
ls -la backend/data/
ls -la backend/data/photos/
```

**解决**：确保运行后端进程的用户对 `data/` 目录有读写权限：

```bash
# 如以 evatar 用户运行
sudo chown -R evatar:evatar /opt/evatar/backend/data/
chmod 750 /opt/evatar/backend/data/
chmod 750 /opt/evatar/backend/data/photos/
```

### LLM API 错误

**问题**：截图上传成功但分析状态一直为 `pending` 或显示 `error`。

**排查**：

1. 检查 LLM 配置是否正确：

```bash
curl http://localhost:8421/api/config/llm
```

2. 查看后端日志中的 LLM 相关错误信息：

```bash
# 日志中搜索 LLM 相关信息
journalctl -u evatar | grep -i llm
```

**常见原因**：

- `EVATAR_LLM_API_KEY` 未设置或已过期
- `EVATAR_LLM_BASE_URL` 地址不正确，或网络不通
- 模型名称不匹配（检查服务商是否支持该模型）
- API 额度不足或请求频率超限

**解决**：在 Web 后台的「设置」页面重新配置 LLM，或使用预设一键切换服务商。

### 数据库锁定错误

**问题**：日志中出现 `database is locked` 错误。

**原因**：SQLite 在高并发写入时可能出现锁冲突。Evatar 已在启动时自动启用 WAL 模式和 5 秒 busy timeout（参见 `backend/models.py` 中的 `_enable_wal()`），正常情况下不会出现此问题。

**排查**：

```bash
# 检查是否有残留的 WAL 文件
ls -la backend/data/evatar.db*
```

**解决**：

1. 确保没有外部工具（如 SQLite 客户端）同时打开数据库文件
2. 如果使用 Docker，确保数据目录正确挂载到 volume，而非 bind mount 到网络文件系统（NFS/CIFS）
3. 如果问题持续，尝试执行 WAL checkpoint：

```bash
sqlite3 backend/data/evatar.db "PRAGMA wal_checkpoint(FULL);"
```

### Android 应用无法连接服务端

**排查步骤**：

1. 确认服务端正在运行：`curl http://localhost:8421/api/health`
2. 如果使用真机调试，确保手机和电脑在同一局域网，并使用电脑的局域网 IP（非 `localhost`）
3. 检查 `EVATAR_CORS_ORIGINS` 是否包含了 Android 应用的请求源
4. 检查防火墙是否放行了 8421 端口

### 照片同步后无分析结果

确认以下条件：

1. LLM 配置正确（在 Web 后台「设置」页面检查）
2. 后端日志中没有 LLM 调用错误
3. 后台调度器已启动（日志中应有 `Background scheduler started`）
4. 可手动触发分析重试：在照片详情页面点击「重新分析」

### 前端构建失败

```bash
cd frontend

# 清除缓存重新安装
rm -rf node_modules dist
pnpm install
pnpm build
```

如 TypeScript 编译报错，检查 Node.js 版本是否满足要求（18+）。
