---
sidebar_position: 2
title: 数据流
description: 核心功能的数据流转过程详解
---

# 数据流

本文详细描述 Evatar 各核心功能的数据流转过程，帮助开发者理解请求从客户端到存储层的完整路径。

---

## 截图同步流程

截图同步是 Evatar 最基础的功能。Android 端扫描设备上的截图文件，上传到后端，后端自动触发 LLM 分析。

```mermaid
sequenceDiagram
    participant App as Android App
    participant SM as SyncManager
    participant API as 后端 API
    participant DB as SQLite
    participant FS as 文件系统
    participant LLM as LLM 服务

    Note over App: 应用启动 / 定时任务触发
    App->>SM: SyncService.start()
    SM->>API: GET /api/photos/sync-state?device_id=xxx
    API->>DB: 查询 DeviceSyncState
    DB-->>API: last_synced_ts_ms
    API-->>SM: {"last_synced_ts_ms": 1700000000000}

    SM->>SM: scanMediaStoreSince(ts)<br/>查询 RELATIVE_PATH LIKE '%Screenshots%'<br/>排除已排除应用的截图

    loop 每张新截图 (并发 Semaphore=3)
        SM->>API: POST /api/photos/upload<br/>MultipartBody: file, device_id,<br/>local_media_store_id, timestamp...

        API->>API: _validate_device_id()
        API->>DB: 查询去重 (device_id + local_media_store_id)
        alt 已存在
            API-->>SM: {"id": 123, "dedup": true}
        else 新截图
            API->>FS: save_photo()<br/>保存原图 + 生成缩略图 (256px)
            FS-->>API: (original_path, thumb_path, size, w, h)
            API->>DB: INSERT Photo + Analysis(status=pending)
            API->>DB: UPDATE DeviceSyncState
            API->>API: enqueue_analysis(photo_id)<br/>创建 asyncio.Task
            API-->>SM: {"id": 456, "dedup": false}

            Note over API,LLM: 异步分析（不阻塞上传响应）
            API->>LLM: call_llm(messages)<br/>system: 截图分析 prompt<br/>user: [base64 图片]
            LLM-->>API: JSON {app_name, content_category,<br/>intent, summary, entities, confidence}
            API->>DB: UPDATE Analysis (status=done)
            API->>API: extract_memories_from_text()
            API->>DB: INSERT Memory (去重, 加密)
        end
    end

    SM->>API: POST /api/photos/sync-state<br/>(更新同步时间戳)
```

### 关键实现细节

**MediaStore 查询条件**（`SyncManager.scanMediaStoreSince()`）：

```kotlin
// 筛选 Screenshots 目录中的图片
"(${RELATIVE_PATH} LIKE ? OR ${DISPLAY_NAME} LIKE ?)"
// 参数: "%Screenshots%", "%screenshot%"

// 按时间范围筛选
"${DATE_ADDED} > ?"  // sinceMs / 1000
```

**去重机制**（`api/photos.py`）：

```python
# 基于 device_id + local_media_store_id 唯一约束
existing = db.query(Photo).filter(
    Photo.device_id == device_id,
    Photo.local_media_store_id == local_media_store_id,
).first()
```

**分析流水线**（`services/pipeline.py`）：

```python
# LLM Vision 请求
messages = [
    {"role": "system", "content": SYSTEM_PROMPT},  # 截图分析 JSON 格式指令
    {"role": "user", "content": [
        {"type": "text", "text": "请分析这张手机截图："},
        {"type": "image_url", "image_url": {"url": f"data:{mime};base64,{b64}"}},
    ]},
]
result = await call_llm(messages)  # 解析为结构化 JSON
```

---

## 聊天消息流程

聊天系统基于 Agent 架构，支持多轮对话和工具调用。

```mermaid
sequenceDiagram
    participant User as 用户
    participant Android as Android / Web
    participant API as /api/chat/send
    participant Agent as agent.py
    participant LLM as LLM 服务
    participant RAG as rag.py (FTS5)
    participant Search as search.py
    participant DB as SQLite

    User->>Android: 输入消息 "最近有什么火车票信息？"
    Android->>API: POST /api/chat/send<br/>{message, conversation_id}

    API->>Agent: chat(conversation_id, user_message, db)

    Agent->>DB: 查询或创建 Conversation
    Agent->>DB: INSERT ChatMessage(role=user)
    Agent->>DB: 读取历史消息 (最近 20 条)
    Agent->>DB: 加载用户记忆 (get_memories_as_context)

    Note over Agent: Agent 循环 (最多 3 轮)

    Agent->>LLM: call_llm(messages, tools=TOOLS)<br/>system: Agent prompt + 记忆上下文<br/>+ 历史对话

    alt LLM 返回 tool_calls
        LLM-->>Agent: {tool_calls: [{name: "search_knowledge", args: {"query": "火车票"}}]}

        Agent->>Agent: _execute_tool("search_knowledge", {"query": "火车票"})
        Agent->>RAG: search_screenshots(db, "火车票", limit=8)
        RAG->>DB: FTS5 MATCH 查询<br/>或 fallback 到 LIKE 查询
        DB-->>RAG: [分析结果列表]
        RAG-->>Agent: [{summary, app_name, entities...}]

        Agent->>DB: INSERT ChatMessage(role=tool, tool_name=search_knowledge)

        Note over Agent: 第 2 轮：将工具结果作为上下文再次调用
        Agent->>LLM: call_llm(messages + tool_result)

        alt LLM 返回纯文本
            LLM-->>Agent: "根据您的截图记录，找到以下火车票信息..."
        end
    else LLM 直接返回文本
        LLM-->>Agent: "你好！我可以帮你查询截图中的信息..."
    end

    Agent->>DB: INSERT ChatMessage(role=assistant)
    Agent->>Agent: 异步提取记忆 (background task)
    Agent-->>API: {"role": "assistant", "content": "..."}
    API-->>Android: JSON 响应
    Android-->>User: 显示 Markdown 渲染结果
```

### Agent 工具列表

| 工具名 | 功能 | 实现 |
|--------|------|------|
| `search_knowledge` | 单关键词搜索截图知识库 | FTS5 全文检索，fallback 到 LIKE 模糊匹配 |
| `search_multi` | 多关键词同时搜索，合并去重 | 多次调用 `search_screenshots`，按 analysis_id 去重 |
| `get_recent` | 获取最近 N 条截图分析结果 | 按 original_timestamp DESC 排序 |
| `web_search` | 搜索互联网 | Tavily API 优先，Brave Search 备选 |

### Agent 循环机制

```python
# services/agent.py
for round_num in range(settings.agent_max_rounds):  # 默认 3 轮
    response = await call_llm(full_messages, tools=TOOLS)
    if not response["tool_calls"]:
        # LLM 返回纯文本，结束循环
        return {"role": "assistant", "content": response["content"]}

    # 执行工具调用，将结果追加到 history
    for tc in response["tool_calls"]:
        result = await _execute_tool(tc["function"]["name"], args, db)
        history.append({"role": "tool", "content": json.dumps(result)})
```

---

## 记忆提取流程

记忆系统从三个来源提取用户信息：聊天对话、截图分析、意图推理文章。

```mermaid
sequenceDiagram
    participant Source as 来源 (Chat / Photo / Reasoner)
    participant Memory as memory.py
    participant LLM as LLM 服务
    participant DB as SQLite

    Source->>Memory: extract_memories_from_text(text, source_type, source_id, device_id, db)

    Memory->>LLM: call_llm([<br/>  {system: MEMORY_EXTRACT_PROMPT},<br/>  {user: text[:6000]}<br/>], temperature=0.2)

    LLM-->>Memory: JSON 数组<br/>[{"content":"...", "category":"fact",<br/>"memory_type":"long_term", "importance":0.8}]

    loop 每条记忆
        Memory->>Memory: normalize → MD5 哈希
        Memory->>DB: 查询去重 (device_id + content_hash)

        alt 已存在
            Memory->>DB: UPDATE access_count +1
        else 新记忆
            alt 加密启用
                Memory->>Memory: encrypt_field(content) → Fernet 加密
                Memory->>DB: INSERT (content="[encrypted:hash]",<br/>encrypted_content=密文)
            else 不加密
                Memory->>DB: INSERT (content=明文)
            end
        end
    end

    Memory-->>Source: 返回提取的记忆列表
```

### 记忆类型

| memory_type | 有效期 | 说明 |
|-------------|--------|------|
| `short_term` | 48 小时后自动过期 | 临时信息，如即时聊天中的待办事项 |
| `long_term` | 永不过期（但会衰减） | 人名、公司、偏好等持久信息 |

### 记忆分类

| category | 说明 | 举例 |
|----------|------|------|
| `fact` | 事实信息 | "用户在北京工作" |
| `people` | 人物信息 | "张三是项目经理" |
| `project` | 项目信息 | "参与 XX 招标项目" |
| `finance` | 财务信息 | "工资 15000 元" |
| `schedule` | 日程安排 | "12月20日项目截止" |
| `preference` | 偏好 | "喜欢高铁出行" |
| `interest` | 兴趣 | "关注 AI 技术" |
| `habit` | 习惯 | "每天早上查看股票" |

### 记忆衰减机制

```python
# services/memory.py - decay_memories()
# 每 24 小时运行一次

# 1. 删除过期的短期记忆
deleted = db.query(Memory).filter(Memory.expires_at < now).delete()

# 2. 降低 7 天未访问的长期记忆的重要性
stale = db.query(Memory).filter(
    Memory.memory_type == "long_term",
    Memory.last_accessed < week_ago,
).all()
for m in stale:
    m.importance = max(0.1, m.importance * 0.9)  # 每次衰减 10%，最低 0.1
```

---

## 意图推理流程

意图推理器（`services/reasoner.py`）是 Evatar 的"后台思考"模块，每小时运行一次，分析用户近期活动并生成结构化笔记。

```mermaid
sequenceDiagram
    participant Sched as 调度器 / 自动触发
    participant Reasoner as reasoner.py
    participant DB as SQLite
    participant LLM as LLM 服务
    participant Push as push.py
    participant Device as Android 设备

    Note over Sched: 每小时定时触发<br/>或每 3 张新截图分析后自动触发

    Sched->>Reasoner: run_reasoning_cycle()

    Reasoner->>DB: 查询近 24h 截图分析 (最多 20 条)
    Reasoner->>DB: 查询近 24h 用户聊天消息 (最多 20 条)
    Reasoner->>DB: 获取用户记忆上下文 (最多 10 条)

    Reasoner->>Reasoner: 构建上下文文本<br/>"## 用户近期截图分析\n- [12-15 10:30] 应用:微信..."

    Reasoner->>LLM: call_llm([<br/>  {system: REASONING_PROMPT},<br/>  {user: context[:8000]}<br/>], temperature=0.3)

    LLM-->>Reasoner: JSON 数组<br/>[{"title":"...", "summary":"...",<br/>"content":"# Markdown 文章",<br/>"category":"insight", "confidence":0.8}]

    loop 每篇文章 (最多 3 篇)
        Reasoner->>DB: INSERT Dynamic (title, content, category...)
        Reasoner->>DB: 记录来源 photo_ids, conversation_ids
    end

    Reasoner->>Reasoner: extract_memories_from_text(articles)

    Reasoner->>Push: broadcast_push(title, body, data)
    Push->>DB: 查询所有注册的 DeviceToken
    loop 每个设备
        Push->>Device: POST webhook_url<br/>{device_id, title, body, data}
    end

    Reasoner-->>Sched: [{"title":"...", "category":"..."}]
```

### 推理输入来源

| 来源 | 查询条件 | 数量限制 |
|------|----------|----------|
| 截图分析 | `Analysis.status=done AND Photo.created_at >= 24h ago` | 最多 20 条 |
| 聊天消息 | `ChatMessage.role=user AND created_at >= 24h ago` | 最多 20 条 |
| 用户记忆 | `Memory.importance DESC, Memory.last_accessed DESC` | 最多 10 条 |

### 笔记分类

| category | 说明 | 使用场景 |
|----------|------|----------|
| `insight` | 洞察 | 趋势分析、模式识别 |
| `reminder` | 提醒 | 有时间约束的事项 |
| `report` | 报告 | 综合性的活动汇总 |
| `note` | 笔记 | 知识整理、信息归档 |

---

## 动态生成与消费流程

动态笔记的生成（后端推理器）和消费（客户端浏览）形成完整的数据闭环。

```mermaid
graph LR
    subgraph Generation["生成端"]
        A["截图分析结果"] --> D["意图推理器"]
        B["聊天记录"] --> D
        C["用户记忆"] --> D
        D -->|"LLM 生成"| E["Dynamic 文章"]
        E --> F["推送通知"]
    end

    subgraph Consumption["消费端"]
        G["Android DynamicTab"] -->|"GET /api/dynamics?cursor=0&limit=30"| H["后端 API"]
        I["Web DynamicsPage"] -->|"GET /api/dynamics?cursor=0&limit=30"| H
        H --> J["SQLite 查询"]
        J --> K["返回分页结果"]
        K --> L["Markdown 渲染"]
        L --> M["标记已读 PUT /api/dynamics/{id}/read"]
    end

    E --> H
    F --> G

    style Generation fill:#e3f2fd,stroke:#2196f3
    style Consumption fill:#e8f5e9,stroke:#4caf50
```

### 游标分页

动态列表使用游标分页（cursor-based pagination），而非传统的 offset 分页：

```python
# api/dynamics.py
# cursor 是最后一条记录的 ID
# 返回 next_cursor 用于请求下一页
items = db.query(Dynamic).filter(Dynamic.id < cursor).order_by(desc(Dynamic.id)).limit(limit)
```

Android 端通过 `DynamicViewModel` 实现无限滚动加载，每次滚动到底部时自动请求下一页。

---

## RAG 检索流程

当用户在聊天中提问时，Agent 通过 RAG（Retrieval-Augmented Generation）从截图知识库中检索相关信息。

```mermaid
sequenceDiagram
    participant Agent as Agent
    participant RAG as rag.py
    participant DB as SQLite

    Agent->>RAG: search_screenshots(db, "火车票", limit=8)

    RAG->>DB: 检查 FTS5 索引是否存在
    alt FTS5 索引不存在或过期
        RAG->>DB: CREATE VIRTUAL TABLE analysis_fts<br/>USING fts5(summary, app_name, content_category,<br/>intent, entities)
        RAG->>DB: INSERT INTO analysis_fts<br/>SELECT FROM analyses WHERE status='done'
    end

    RAG->>DB: SELECT ... FROM analysis_fts<br/>WHERE analysis_fts MATCH :query<br/>ORDER BY rank LIMIT :limit

    alt FTS5 有结果
        DB-->>RAG: [匹配结果]
    else FTS5 无结果
        RAG->>DB: fallback: LIKE 查询<br/>WHERE summary LIKE '%火车%'<br/>OR entities LIKE '%火车%'
        DB-->>RAG: [模糊匹配结果]
    end

    RAG-->>Agent: [{analysis_id, summary, app_name,<br/>content_category, intent, entities,<br/>filename, timestamp}]
```

### FTS5 索引维护

```python
# 索引构建策略
1. 首次查询时自动构建索引
2. 后续查询时检查索引是否过期（FTS 行数 < analyses 行数）
3. 过期时重建：创建临时表 → 填充数据 → 原子替换
```

### 查询安全处理

```python
def _sanitize_fts_query(query: str) -> str:
    """移除 FTS5 特殊字符，防止语法注入"""
    tokens = query.split()
    clean = [re.sub(r'[^\w\s一-鿿]', '', t) for t in tokens[:10]]
    return " OR ".join(t for t in clean if t)
```
