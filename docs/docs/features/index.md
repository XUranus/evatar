---
sidebar_position: 1
title: 功能概览
description: Evatar 核心功能介绍
---

# 功能概览

Evatar 是一个智能个人助手系统，通过同步手机截图、AI 分析、记忆系统和意图推理，为用户提供个性化的信息整理与知识管理服务。

## 功能矩阵

| 功能模块 | 描述 | 状态 | 平台 |
|---------|------|------|------|
| [截图同步](./screenshot-sync) | Android 端自动监控 MediaStore，增量上传截图到服务端 | 已实现 | Android + Backend |
| [AI 分析](./ai-analysis) | LLM 多模态分析截图内容，提取结构化信息 | 已实现 | Backend |
| [AI 聊天助手](./chat-agent) | 基于 RAG 的智能对话，支持工具调用和联网搜索 | 已实现 | Android + Backend |
| [动态系统](./dynamics) | 后台意图推理引擎，自动生成洞察文章和提醒 | 已实现 | Android + Backend |
| [记忆系统](./memory) | 短期/长期记忆管理，自动提取、衰减和去重 | 已实现 | Backend |
| [推送通知](./push-notifications) | 多设备广播推送，支持 Webhook 和 FCM | 已实现 | Android + Backend |
| [安全机制](./security) | API 认证、SSRF 防护、加密存储、速率限制 | 已实现 | Backend |

## 系统架构

```mermaid
graph TB
    subgraph Android["Android 客户端"]
        SyncService["SyncService<br/>(前台服务)"]
        SyncWorker["SyncWorker<br/>(WorkManager)"]
        MediaStore["MediaStore<br/>(系统截图库)"]
        ChatUI["聊天界面"]
        DynamicUI["动态界面"]
        LocalCache["本地缓存<br/>(SharedPreferences)"]
    end

    subgraph Backend["Backend 服务"]
        API["FastAPI 路由层"]
        Pipeline["分析管线<br/>(pipeline.py)"]
        Agent["Agent 服务<br/>(agent.py)"]
        Reasoner["意图推理器<br/>(reasoner.py)"]
        Memory["记忆服务<br/>(memory.py)"]
        RAG["RAG 检索<br/>(rag.py)"]
        Push["推送服务<br/>(push.py)"]
        Scheduler["调度器<br/>(scheduler.py)"]
        LLM["LLM 服务<br/>(llm.py)"]
        Encrypt["加密服务<br/>(encryption.py)"]
        Storage["文件存储<br/>(storage.py)"]
    end

    subgraph External["外部服务"]
        LLMProvider["LLM 服务商<br/>(mimo/qwen/openai/...)"]
        SearchAPI["搜索 API<br/>(Tavily/Brave)"]
        FCM["推送通道<br/>(Webhook/FCM)"]
    end

    subgraph Data["数据层"]
        SQLite["SQLite 数据库"]
        PhotoFS["照片文件系统"]
    end

    MediaStore --> SyncService
    SyncService --> SyncWorker
    SyncWorker -->|上传截图| API
    SyncService -->|上传截图| API

    API --> Pipeline
    Pipeline --> LLM
    LLM --> LLMProvider
    Pipeline --> Memory

    ChatUI -->|发送消息| API
    API --> Agent
    Agent --> RAG
    Agent --> LLM
    Agent -->|web_search| SearchAPI
    Agent --> Memory

    DynamicUI -->|获取动态| API
    API -->|cursor 分页| SQLite

    Scheduler -->|每小时| Reasoner
    Scheduler -->|每天| Memory
    Reasoner --> LLM
    Reasoner --> Memory
    Reasoner --> Push
    Push --> FCM

    API --> SQLite
    Storage --> PhotoFS
    Encrypt --> SQLite

    API --> LocalCache
```

## 用户旅程

```mermaid
journey
    title Evatar 用户旅程
    section 初始化
      安装 Android 应用: 5: 用户
      配置服务端地址: 4: 用户
      选择 LLM 服务商: 4: 用户
      授权截图访问权限: 3: 用户
    section 日常使用
      自动同步截图: 5: 系统
      AI 分析截图内容: 5: 系统
      后台生成洞察文章: 5: 系统
      推送新笔记通知: 4: 系统
    section 交互
      查看动态文章列表: 5: 用户
      与 AI 助手对话: 5: 用户
      搜索截图知识库: 4: 用户
      查看记忆管理: 3: 用户
```

## 核心数据流

```mermaid
flowchart LR
    A["手机截图"] -->|MediaStore 监控| B["增量上传"]
    B -->|去重检查| C["存储照片"]
    C -->|触发分析| D["LLM 多模态分析"]
    D -->|结构化数据| E["存入数据库"]
    E -->|每 3 张| F["意图推理"]
    E -->|每次对话| G["记忆提取"]
    F -->|生成文章| H["推送通知"]
    F -->|提取记忆| G
    G -->|注入上下文| I["Agent 对话"]
    I -->|RAG 检索| E
```

## 技术栈

| 层级 | 技术 |
|------|------|
| Android 客户端 | Kotlin, Jetpack Compose, WorkManager, OkHttp |
| Backend 框架 | FastAPI (Python 3.11) |
| 数据库 | SQLite + WAL 模式, SQLAlchemy ORM |
| 全文搜索 | SQLite FTS5 |
| LLM 集成 | OpenAI-compatible API (httpx) |
| 加密 | cryptography.Fernet (AES-128-CBC) |
| 文件处理 | Pillow (缩略图, 图片压缩) |
| 推送通道 | Webhook (FCM HTTP v1 预留) |

## LLM 服务商预设

系统内置 7 个 LLM 服务商预设，可在设置页面一键切换：

| 预设名称 | Provider | Base URL | 模型 | 上下文窗口 |
|---------|----------|----------|------|-----------|
| mimo | mimo | `https://token-plan-cn.xiaomimimo.com/v1` | mimo-v2.5 | 1,048,576 |
| qwen | qwen | `https://dashscope.aliyuncs.com/compatible-mode/v1` | qwen-vl-max | 131,072 |
| openai | openai | `https://api.openai.com/v1` | gpt-4o | 128,000 |
| claude | claude | `https://api.anthropic.com/v1` | claude-sonnet-4-20250514 | 200,000 |
| glm | glm | `https://open.bigmodel.cn/api/paas/v4` | glm-4v | 128,000 |
| kimi | kimi | `https://api.moonshot.cn/v1` | moonshot-v1-128k-vision-preview | 128,000 |
| deepseek | deepseek | `https://api.deepseek.com/v1` | deepseek-chat | 65,536 |

## API 路由总览

| 路由模块 | 前缀 | 说明 |
|---------|------|------|
| photos | `/api/photos` | 截图上传、同步状态、设备管理 |
| analysis | `/api/analysis` | 分析任务管理 |
| chat | `/api/chat` | 对话、消息、会话管理 |
| dynamics | `/api/dynamics` | 动态文章 CRUD、统计 |
| memories | `/api/memories` | 记忆管理、统计 |
| push | `/api/push` | 设备注册、推送广播 |
| config | `/api/config` | LLM 配置、预设管理 |
| skills | `/api/skills` | 技能管理、MCP Server |
