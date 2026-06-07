---
sidebar_position: 1
title: 贡献指南
description: 如何参与 Evatar 项目开发
---

# 贡献指南

欢迎参与 Evatar 项目开发！本文档将帮助你快速搭建开发环境、了解代码规范和提交流程。

---

## 开发环境设置

### Fork 与克隆

```bash
# 1. 在 GitHub 上 Fork 项目仓库

# 2. 克隆你的 Fork
git clone https://github.com/<your-username>/evatar.git
cd evatar

# 3. 添加上游仓库
git remote add upstream https://github.com/<original-owner>/evatar.git

# 4. 同步上游最新代码
git fetch upstream
git rebase upstream/master
```

### 后端开发环境

后端使用 Python 3.11+，框架为 FastAPI，数据库为 SQLAlchemy + SQLite。

```bash
cd backend

# 创建并激活虚拟环境
python3.11 -m venv .venv
source .venv/bin/activate

# 安装项目依赖
pip install -r requirements.txt

# 启动开发服务器（默认监听 0.0.0.0:8421）
python main.py
```

**核心依赖**（参见 `backend/requirements.txt`）：

| 包 | 用途 |
|---|------|
| `fastapi` | Web 框架 |
| `uvicorn[standard]` | ASGI 服务器 |
| `sqlalchemy` | ORM 与数据库操作 |
| `pydantic` / `pydantic-settings` | 数据校验与配置管理 |
| `httpx` | 异步 HTTP 客户端（调用 LLM API） |
| `pillow` | 图片处理（缩略图生成） |
| `cryptography` | Fernet 对称加密（敏感字段加密） |
| `aiofiles` | 异步文件操作 |
| `python-multipart` | 文件上传解析 |

### 前端开发环境

前端使用 React 19 + TypeScript + Vite + Tailwind CSS 4。

```bash
cd frontend

# 安装依赖（使用 pnpm）
pnpm install

# 启动开发服务器（默认 http://localhost:3000）
pnpm dev
```

开发服务器会自动将 `/api` 请求代理到后端 `http://localhost:8421`（配置位于 `frontend/vite.config.ts`）。

**主要依赖**：

| 包 | 用途 |
|---|------|
| `react` / `react-dom` | UI 框架 |
| `axios` | HTTP 客户端 |
| `react-markdown` | Markdown 渲染（聊天消息） |
| `lucide-react` | 图标库 |
| `i18next` / `react-i18next` | 国际化（支持中文/英文） |
| `tailwindcss` | 原子化 CSS 框架 |
| `typescript` | 类型系统 |
| `vite` | 构建工具 |

### Android 开发环境

Android 应用使用 Kotlin + Jetpack Compose，最低支持 API 26（Android 8.0）。

```bash
cd android

# 使用 Android Studio 打开项目
# 或命令行构建
./gradlew :app:assembleDebug

# 安装到设备
adb install app/build/outputs/apk/debug/app-debug.apk
```

**技术栈**：

| 技术 | 用途 |
|------|------|
| Jetpack Compose | 声明式 UI 框架 |
| Room | 本地数据库（缓存同步状态） |
| OkHttp | HTTP 客户端（与后端通信） |
| Coil | Compose 图片加载库 |
| WorkManager | 后台任务调度 |
| Navigation Compose | 页面导航 |
| Accompanist Permissions | 运行时权限管理 |

---

## 代码规范

### Python（后端）

- 遵循 [PEP 8](https://peps.python.org/pep-0008/) 代码风格
- 使用类型注解（type hints）标注函数参数和返回值
- 编写 docstring 说明函数用途，尤其是 `services/` 下的核心服务模块
- 使用 `logging` 模块记录日志，不要使用 `print()`

示例风格（参考项目中实际代码）：

```python
"""模块简要说明。"""

import logging
from typing import Optional

logger = logging.getLogger("evatar.module_name")


def process_item(item_id: int, config: Optional[dict] = None) -> dict:
    """处理指定项目并返回结果。

    Args:
        item_id: 项目 ID
        config: 可选配置参数

    Returns:
        包含处理结果的字典
    """
    logger.info(f"Processing item {item_id}")
    # ...
    return {"id": item_id, "status": "done"}
```

项目结构约定：

- `backend/api/` — API 路由层（FastAPI Router），负责请求解析和响应格式化
- `backend/services/` — 业务逻辑层，包含 LLM 调用、Agent 推理、加密、调度等核心服务
- `backend/models.py` — SQLAlchemy 数据模型定义
- `backend/config.py` — Pydantic Settings 配置类，所有环境变量在此声明
- `backend/main.py` — 应用入口，FastAPI app 创建、中间件注册、路由挂载

### Kotlin（Android）

- 遵循 [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- UI 层使用 Jetpack Compose 声明式范式，ViewModel 管理状态
- 网络请求使用 OkHttp + Gson
- 使用协程（Coroutines）处理异步操作

项目结构约定（位于 `android/app/src/main/java/com/evatar/app/`）：

- `ui/screens/` — 各页面 Composable（`ChatTab.kt`、`DynamicTab.kt`、`SettingsTab.kt`、`OnboardingScreen.kt`）
- `ui/components/` — 可复用 Compose 组件（如 `MarkdownText.kt`）
- `ui/theme/` — Material 3 主题定义（`Theme.kt`）
- `viewmodel/` — ViewModel 层（`ChatViewModel.kt`、`DynamicViewModel.kt`、`SettingsViewModel.kt`）
- `sync/` — 截图同步核心逻辑（`SyncManager.kt`、`SyncService.kt`、`SyncWorker.kt`、`WorkScheduler.kt`）
- `network/` — 网络客户端（`ApiClient.kt`）
- `keepalive/` — 后台保活机制（`KeepAliveService.kt`、`OverlayWindow.kt`）
- `settings/` — 应用排除管理（`AppExclusionManager.kt`）

### TypeScript（前端）

- 使用 ESLint 进行代码检查（配置文件：`frontend/eslint.config.js`）
- 启用了 `typescript-eslint`、`react-hooks`、`react-refresh` 插件
- 组件使用函数式风格 + Hooks
- Props 通过 TypeScript `interface` 定义类型

项目结构约定（位于 `frontend/src/`）：

- `pages/` — 页面组件（`Dashboard.tsx`、`Photos.tsx`、`Chat.tsx`、`Dynamics.tsx`、`Settings.tsx`）
- `components/` — 共享组件（如 `ErrorBoundary.tsx`）
- `api/client.ts` — Axios 客户端实例、所有 API 函数和 TypeScript 类型定义
- `i18n/` — 国际化配置和翻译文件（`locales/zh-CN.json`、`locales/en.json`）
- `App.tsx` — 根组件
- `main.tsx` — 应用入口

命名规范：

- 组件文件使用 PascalCase：`Dashboard.tsx`、`ErrorBoundary.tsx`
- 工具函数文件使用 camelCase
- CSS 使用 Tailwind CSS 原子类，不编写独立样式文件

---

## Git 工作流

### 分支命名

| 前缀 | 用途 | 示例 |
|------|------|------|
| `feat/` | 新功能 | `feat/push-notification` |
| `fix/` | Bug 修复 | `fix/database-locked` |
| `docs/` | 文档更新 | `docs/deployment-guide` |
| `refactor/` | 代码重构 | `refactor/sync-service` |
| `test/` | 测试相关 | `test/api-endpoints` |
| `chore/` | 构建/工具链 | `chore/upgrade-deps` |

### Commit 消息格式

使用 [Conventional Commits](https://www.conventionalcommits.org/) 规范：

```
<type>(<scope>): <description>

[optional body]

[optional footer]
```

**类型**：

| 类型 | 说明 |
|------|------|
| `feat` | 新功能 |
| `fix` | Bug 修复 |
| `docs` | 文档变更 |
| `style` | 代码格式调整（不影响逻辑） |
| `refactor` | 代码重构（不新增功能或修复 Bug） |
| `test` | 添加或修改测试 |
| `chore` | 构建流程、依赖管理等杂项 |

**作用域**（scope）建议使用模块名：`backend`、`frontend`、`android`、`wiki`。

**示例**：

```
feat(android): add screenshot auto-sync with foreground service
fix(backend): resolve database locked error under high concurrency
docs(wiki): add deployment guide for Docker Compose
refactor(frontend): extract API client types to shared module
test(backend): add integration tests for photo upload endpoint
chore(android): upgrade Compose BOM to 2024.06.00
```

### Pull Request 流程

1. 确保你的分支基于最新的 `master`：

```bash
git fetch upstream
git rebase upstream/master
```

2. 确保代码通过检查：

```bash
# 后端测试
cd backend && source .venv/bin/activate && pytest tests/ -v

# 前端构建检查
cd frontend && pnpm build

# Android 构建检查
cd android && ./gradlew :app:assembleDebug
```

3. 推送分支到你的 Fork：

```bash
git push origin feat/your-feature
```

4. 在 GitHub 上创建 Pull Request，填写：
   - 变更说明：做了什么、为什么做
   - 测试情况：如何验证变更正确
   - 关联 Issue（如有）

5. 等待 Code Review，根据反馈修改后重新推送

---

## 测试

### 后端测试

后端使用 `pytest` + `httpx` 进行测试，配置位于 `backend/pytest.ini`（`asyncio_mode = auto`）。

```bash
cd backend
source .venv/bin/activate

# 运行全部测试
pytest tests/ -v

# 运行指定测试文件
pytest tests/test_api.py -v

# 运行指定测试用例
pytest tests/test_api.py::test_health_endpoint -v

# 显示详细输出
pytest tests/ -v -s
```

测试架构（参见 `backend/tests/conftest.py`）：

- `conftest.py` — 共享 fixture：数据库 session（自动创建/销毁表）、HTTP 测试客户端（基于 httpx ASGITransport）、Mock LLM 响应
- `test_api.py` — API 端点集成测试
- `test_services.py` — 服务层单元测试

测试环境会自动使用临时目录作为数据目录，不影响开发数据。Mock LLM fixture 会替换所有模块中的 `call_llm` 函数，避免测试时发起真实 LLM 请求。

### 前端构建验证

```bash
cd frontend

# TypeScript 编译 + Vite 生产构建
pnpm build

# ESLint 代码检查
pnpm lint
```

`pnpm build` 会先执行 `tsc -b`（TypeScript 类型检查）再执行 `vite build`（打包），任何类型错误都会导致构建失败。

### Android 构建验证

```bash
cd android

# Debug 构建
./gradlew :app:assembleDebug

# 如需运行单元测试
./gradlew :app:testDebugUnitTest
```

---

## 项目结构总览

```
evatar/
├── backend/                    # Python 后端
│   ├── main.py                 # 应用入口（FastAPI app、中间件、路由挂载）
│   ├── config.py               # 配置类（Pydantic Settings，EVATAR_* 环境变量）
│   ├── models.py               # 数据模型（SQLAlchemy ORM，SQLite）
│   ├── requirements.txt        # Python 依赖
│   ├── pytest.ini              # pytest 配置
│   ├── api/                    # API 路由层
│   │   ├── photos.py           # 照片上传、列表、详情
│   │   ├── analysis.py         # 分析结果查询、重新处理
│   │   ├── chat.py             # 对话管理、消息发送（含 Agent 工具调用）
│   │   ├── config.py           # LLM 配置读写、预设管理
│   │   ├── dynamics.py         # 动态/文章（后台推理生成）
│   │   ├── memories.py         # 记忆管理（短期/长期）
│   │   ├── push.py             # 推送通知注册与发送
│   │   └── skills.py           # 技能/角色管理
│   ├── services/               # 业务逻辑层
│   │   ├── llm.py              # LLM API 调用封装
│   │   ├── agent.py            # Agent 推理循环
│   │   ├── pipeline.py         # 截图分析管线
│   │   ├── reasoner.py         # 后台意图推理（生成动态文章）
│   │   ├── memory.py           # 记忆存储与衰减
│   │   ├── search.py           # Web 搜索集成
│   │   ├── rag.py              # RAG 检索增强
│   │   ├── encryption.py       # Fernet 加密服务
│   │   ├── push.py             # 推送通知服务（FCM）
│   │   ├── scheduler.py        # 后台调度器
│   │   ├── retention.py        # 数据保留策略
│   │   ├── storage.py          # 文件存储
│   │   └── utils.py            # 工具函数
│   ├── tests/                  # 测试
│   │   ├── conftest.py         # 共享 fixture
│   │   ├── test_api.py         # API 测试
│   │   └── test_services.py    # 服务测试
│   └── data/                   # 运行时数据（数据库、照片，不提交到 Git）
│
├── frontend/                   # React 前端
│   ├── src/
│   │   ├── main.tsx            # 应用入口
│   │   ├── App.tsx             # 根组件
│   │   ├── pages/              # 页面组件
│   │   │   ├── Dashboard.tsx   # 仪表盘（统计概览）
│   │   │   ├── Photos.tsx      # 照片列表与详情
│   │   │   ├── Chat.tsx        # AI 对话界面
│   │   │   ├── Dynamics.tsx    # 动态/文章列表
│   │   │   └── Settings.tsx    # 设置页面（LLM、推送、数据管理）
│   │   ├── components/         # 共享组件
│   │   │   └── ErrorBoundary.tsx
│   │   ├── api/
│   │   │   └── client.ts       # Axios 实例 + API 函数 + 类型定义
│   │   ├── i18n/               # 国际化
│   │   │   ├── index.ts        # i18next 初始化
│   │   │   └── locales/        # 翻译文件（zh-CN.json, en.json）
│   │   └── assets/             # 静态资源
│   ├── package.json            # 项目配置与依赖
│   ├── vite.config.ts          # Vite 配置（端口、API 代理）
│   ├── eslint.config.js        # ESLint 配置
│   └── tsconfig.json           # TypeScript 配置
│
├── android/                    # Android 原生应用
│   ├── app/
│   │   ├── build.gradle.kts    # 应用级构建配置（compileSdk 34, minSdk 26）
│   │   └── src/main/java/com/evatar/app/
│   │       ├── MainActivity.kt         # 主 Activity
│   │       ├── EvatarApp.kt            # Application 类
│   │       ├── ui/                     # UI 层
│   │       │   ├── AppNavigation.kt    # 导航图
│   │       │   ├── ShareReceiverActivity.kt  # 分享接收
│   │       │   ├── screens/            # 页面
│   │       │   ├── components/         # 组件
│   │       │   └── theme/              # 主题
│   │       ├── viewmodel/              # ViewModel 层（MVVM）
│   │       ├── sync/                   # 同步服务
│   │       ├── network/                # 网络客户端
│   │       ├── keepalive/              # 保活机制
│   │       └── settings/               # 应用设置
│   ├── build.gradle.kts        # 项目级构建配置
│   └── settings.gradle.kts     # Gradle 设置
│
├── wiki/                       # 项目文档站（Docusaurus）
│   ├── docs/                   # 文档内容
│   ├── docusaurus.config.ts    # Docusaurus 配置
│   ├── sidebars.ts             # 侧边栏结构
│   └── package.json
│
├── README.md                   # 英文说明
└── README.zh-CN.md             # 中文说明
```

### 架构概览

**请求流程**：

```
Android App ──POST /api/photos/upload──> Backend (FastAPI)
                                            │
Frontend (React) ──GET /api/photos──────>   │
                                            ├──> SQLite (data/evatar.db)
                                            ├──> LLM API (截图分析)
                                            └──> data/photos/ (文件存储)
```

**后端分层**：

- **API 层**（`api/`）：路由定义、请求校验、响应格式化
- **服务层**（`services/`）：业务逻辑、LLM 调用、数据处理
- **模型层**（`models.py`）：ORM 模型、数据库连接
- **配置层**（`config.py`）：环境变量管理

**Android MVVM 模式**：

- **Screen**（`ui/screens/`）：Compose UI，观察 ViewModel 状态
- **ViewModel**（`viewmodel/`）：管理 UI 状态和业务逻辑
- **Repository/Service**（`sync/`、`network/`）：数据访问和后台任务

---

## 提交第一个 PR 的建议

如果你是首次贡献，建议从小处入手：

1. **文档改进**：修正错别字、补充说明、翻译缺失内容
2. **简单 Bug 修复**：查看 GitHub Issues 中标记为 `good first issue` 的任务
3. **测试补充**：为现有 API 端点或服务函数添加测试用例
4. **国际化**：补充 `frontend/src/i18n/locales/` 中缺失的翻译条目

如有任何疑问，欢迎在 GitHub Issues 中提问。
