---
sidebar_position: 3
title: 数据模型
description: 后端 SQLAlchemy 数据模型定义
---

# 数据模型

所有模型定义在 `models.py` 中，使用 SQLAlchemy 2.x 声明式语法，继承自 `DeclarativeBase`。数据库为 SQLite，启用 WAL 模式。

## 实体关系图

```mermaid
erDiagram
    Photo ||--o| Analysis : "has one"
    Photo {
        int id PK
        string local_media_store_id
        string filename
        string original_path
        string thumbnail_path
        bigint file_size
        int width
        int height
        string mime_type
        string source_type
        string device_id
        string device_name
        datetime original_timestamp
        datetime sync_time
        datetime created_at
    }

    Analysis {
        int id PK
        int photo_id FK
        enum status
        text llm_response
        string app_name
        string content_category
        string intent
        text summary
        text entities
        float confidence
        text error_message
        datetime created_at
        datetime completed_at
    }

    Conversation ||--o{ ChatMessage : "has many"
    Conversation {
        string id PK
        string title
        string device_id
        datetime created_at
        datetime updated_at
    }

    ChatMessage {
        int id PK
        string conversation_id FK
        string role
        text content
        text encrypted_content
        string tool_name
        string tool_call_id
        text tool_calls
        datetime created_at
    }

    Dynamic {
        int id PK
        string title
        text content
        text summary
        string category
        text source_photo_ids
        text source_conversation_ids
        text source_memory_ids
        float confidence
        boolean is_read
        boolean is_pinned
        string device_id
        datetime created_at
    }

    Memory {
        int id PK
        text content
        text encrypted_content
        string content_hash
        string memory_type
        string source_type
        string source_id
        string category
        float importance
        int access_count
        string device_id
        datetime created_at
        datetime last_accessed
        datetime expires_at
    }

    DeviceToken {
        int id PK
        string device_id UQ
        string token
        string platform
        string device_name
        string device_model
        string app_version
        datetime last_seen
        datetime created_at
    }

    DeviceSyncState {
        string device_id PK
        string device_name
        datetime last_synced_timestamp
        datetime last_sync_time
        int total_synced
    }

    Skill {
        string id PK
        string name
        text description
        text system_prompt
        string icon
        boolean enabled
        datetime created_at
    }

    MCPServer {
        string id PK
        string name
        string url
        text description
        boolean enabled
        text tools_json
        datetime created_at
    }

    LLMConfig {
        int id PK
        string provider
        string base_url
        string api_key
        string model
        int max_context_tokens
        float temperature
        datetime updated_at
    }
```

---

## Photo (截图)

表名：`photos`

| 列名 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | Integer | PK, autoincrement | 主键 |
| local_media_store_id | String(128) | nullable | Android MediaStore ID（用于去重） |
| filename | String(512) | NOT NULL | 原始文件名 |
| original_path | String(1024) | NOT NULL | 原始文件存储路径 |
| thumbnail_path | String(1024) | nullable | 缩略图路径 |
| file_size | BigInteger | default 0 | 文件大小（字节） |
| width | Integer | nullable | 图片宽度 |
| height | Integer | nullable | 图片高度 |
| mime_type | String(64) | default `"image/jpeg"` | MIME 类型 |
| source_type | String(64) | default `"screenshot"` | 来源类型 |
| device_id | String(256) | nullable | 设备标识 |
| device_name | String(256) | nullable | 设备名称 |
| original_timestamp | DateTime | nullable | 截图原始时间 |
| sync_time | DateTime | default utcnow | 同步时间 |
| created_at | DateTime | default utcnow | 记录创建时间 |

**唯一约束：** `(device_id, local_media_store_id)` — 同一设备的同一张截图不会重复入库。

**索引：**
- `ix_photos_device_id` — 按设备查询
- `ix_photos_original_timestamp` — 按时间排序

**关系：** `analysis` — 一对一关联 `Analysis`，cascade delete-orphan，使用 `selectin` 加载策略。

---

## Analysis (分析)

表名：`analyses`

| 列名 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | Integer | PK, autoincrement | 主键 |
| photo_id | Integer | FK -> photos.id, NOT NULL | 关联截图 |
| status | Enum | default `PENDING` | 状态: `pending`/`processing`/`done`/`error` |
| llm_response | Text | nullable | LLM 原始返回的完整 JSON |
| app_name | String(256) | nullable | 识别的应用名称 |
| content_category | String(256) | nullable | 内容分类 |
| intent | String(256) | nullable | 意图类型 |
| summary | Text | nullable | 内容摘要 |
| entities | Text | nullable | 提取的实体 (JSON 数组) |
| confidence | Float | nullable | 置信度 0-1 |
| error_message | Text | nullable | 错误信息 |
| created_at | DateTime | default utcnow | 创建时间 |
| completed_at | DateTime | nullable | 完成时间 |

**索引：**
- `ix_analyses_status` — 按状态筛选
- `ix_analyses_photo_id` — 按截图查询

**AnalysisStatus 枚举值：**

```python
class AnalysisStatus(str, enum.Enum):
    PENDING = "pending"       # 等待处理
    PROCESSING = "processing" # 正在分析
    DONE = "done"            # 分析完成
    ERROR = "error"          # 分析失败
```

---

## Conversation (对话)

表名：`conversations`

| 列名 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | String(64) | PK | 对话 ID (hex 格式) |
| title | String(256) | default `"新对话"` | 对话标题 |
| device_id | String(256) | nullable | 设备标识 |
| created_at | DateTime | default utcnow | 创建时间 |
| updated_at | DateTime | default utcnow | 最后更新时间 |

**索引：**
- `ix_conversations_device_id` — 按设备筛选
- `ix_conversations_updated_at` — 按更新时间排序

**关系：** `messages` — 一对多关联 `ChatMessage`，按 `created_at` 排序，cascade delete-orphan。

---

## ChatMessage (聊天消息)

表名：`chat_messages`

| 列名 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | Integer | PK, autoincrement | 主键 |
| conversation_id | String(64) | FK -> conversations.id, NOT NULL | 所属对话 |
| role | String(32) | NOT NULL | 角色: `user`/`assistant`/`tool` |
| content | Text | nullable | 消息内容 |
| encrypted_content | Text | nullable | Fernet 加密的敏感内容 |
| tool_name | String(64) | nullable | 工具名称 (role=tool 时) |
| tool_call_id | String(128) | nullable | 工具调用 ID |
| tool_calls | Text | nullable | 工具调用列表 (JSON, role=assistant 时) |
| created_at | DateTime | default utcnow | 创建时间 |

**属性：**
- `display_content` — 如果 `encrypted_content` 存在则解密返回，否则返回 `content`

**索引：** `ix_chat_messages_conversation_id`

---

## Dynamic (动态)

表名：`dynamics`

由后台意图推理生成的文章/笔记。

| 列名 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | Integer | PK, autoincrement | 主键 |
| title | String(512) | NOT NULL | 文章标题 |
| content | Text | NOT NULL | Markdown 正文 |
| summary | Text | nullable | 一句话摘要 |
| category | String(64) | default `"note"` | 分类: `insight`/`reminder`/`report`/`note` |
| source_photo_ids | Text | nullable | 来源截图 ID (JSON 数组) |
| source_conversation_ids | Text | nullable | 来源对话 ID (JSON 数组) |
| source_memory_ids | Text | nullable | 来源记忆 ID (JSON 数组) |
| confidence | Float | default 0.5 | 置信度 |
| is_read | Boolean | default False | 是否已读 |
| is_pinned | Boolean | default False | 是否置顶 |
| device_id | String(256) | nullable | 设备标识 |
| created_at | DateTime | default utcnow | 创建时间 |

**索引：** `ix_dynamics_device_created` — `(device_id, created_at)` 复合索引

---

## Memory (记忆)

表名：`memories`

Agent 记忆系统，分为短期（48h 过期）和长期（持久）两种类型。

| 列名 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | Integer | PK, autoincrement | 主键 |
| content | Text | NOT NULL | 记忆内容 |
| encrypted_content | Text | nullable | Fernet 加密的内容 |
| content_hash | String(32) | nullable, indexed | MD5 哈希（用于去重） |
| memory_type | String(32) | NOT NULL | `short_term`/`long_term` |
| source_type | String(32) | NOT NULL | `chat`/`photo`/`inferred` |
| source_id | String(128) | nullable | 来源 ID (conversation_id 或 photo_id) |
| category | String(64) | default `"fact"` | 分类: `fact`/`people`/`project`/`finance`/`schedule`/`preference`/`interest`/`habit` |
| importance | Float | default 0.5 | 重要度 0-1 |
| access_count | Integer | default 0 | 访问次数 |
| device_id | String(256) | nullable | 设备标识 |
| created_at | DateTime | default utcnow | 创建时间 |
| last_accessed | DateTime | nullable | 最后访问时间 |
| expires_at | DateTime | nullable | 过期时间（null = 永不过期） |

**唯一约束：** `(device_id, content_hash)` — 同一设备不存储重复记忆。

**索引：**
- `ix_memories_device_type` — `(device_id, memory_type)` 复合索引
- `ix_memories_expires` — 按过期时间查询（清理用）

**属性：**
- `display_content` — 解密后的内容

---

## DeviceToken (推送设备)

表名：`device_tokens`

| 列名 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | Integer | PK, autoincrement | 主键 |
| device_id | String(256) | NOT NULL, UNIQUE | 设备标识 |
| token | String(1024) | NOT NULL | FCM Token 或推送端点 |
| platform | String(32) | default `"android"` | 平台: `android`/`ios` |
| device_name | String(256) | nullable | 设备名称 |
| device_model | String(256) | nullable | 设备型号 |
| app_version | String(32) | nullable | App 版本 |
| last_seen | DateTime | nullable | 最后在线时间 |
| created_at | DateTime | default utcnow | 注册时间 |

**索引：** `ix_device_tokens_device_id`

---

## DeviceSyncState (设备同步状态)

表名：`device_sync_state`

| 列名 | 类型 | 约束 | 说明 |
|------|------|------|------|
| device_id | String(256) | PK | 设备标识 |
| device_name | String(256) | nullable | 设备名称 |
| last_synced_timestamp | DateTime | nullable | 最后同步的截图时间戳 |
| last_sync_time | DateTime | nullable | 最后同步操作时间 |
| total_synced | Integer | default 0 | 已同步总数 |

---

## Skill (技能)

表名：`skills`

| 列名 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | String(64) | PK | 技能 ID (小写字母数字下划线) |
| name | String(128) | NOT NULL | 技能名称 |
| description | Text | nullable | 技能描述 |
| system_prompt | Text | NOT NULL | 注入 Agent 的系统提示词 |
| icon | String(16) | default `"⚡"` | 图标 (emoji) |
| enabled | Boolean | default True | 是否启用 |
| created_at | DateTime | default utcnow | 创建时间 |

**默认技能：**

| ID | 名称 | 图标 | 说明 |
|----|------|------|------|
| summarize | 总结截图 | 📝 | 分析最近截图，生成摘要笔记 |
| reminders | 提取提醒 | ⏰ | 从截图中提取提醒事项 |
| research | 深度研究 | 🔬 | 对截图主题进行深入研究 |
| stock | 股票分析 | 📈 | 整理截图中的金融信息 |

---

## MCPServer (MCP 服务器)

表名：`mcp_servers`

| 列名 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | String(64) | PK | 服务器 ID |
| name | String(128) | NOT NULL | 服务器名称 |
| url | String(512) | NOT NULL | 服务器 URL (含 SSRF 防护) |
| description | Text | nullable | 描述 |
| enabled | Boolean | default True | 是否启用 |
| tools_json | Text | nullable | 工具列表 (JSON) |
| created_at | DateTime | default utcnow | 创建时间 |

---

## LLMConfig (LLM 配置)

表名：`llm_config`

单行配置表（固定 `id=1`），存储 LLM 服务商配置。

| 列名 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | Integer | PK, default 1 | 固定为 1 |
| provider | String(64) | default `"mimo"` | 服务商名称 |
| base_url | String(512) | default mimo URL | API 地址 |
| api_key | String(512) | default `""` | API Key（明文存储，已知限制） |
| model | String(128) | default `"mimo-v2.5"` | 模型名称 |
| max_context_tokens | Integer | default 1048576 | 最大上下文 token 数 |
| temperature | Float | default 0.1 | 生成温度 |
| updated_at | DateTime | default utcnow | 最后更新时间 |

!!! warning "安全注意"
    `api_key` 以明文存储在数据库中。API 响应中不会返回实际值，仅返回 `api_key_set: true/false`。生产环境建议使用 `EVATAR_ENCRYPTION_KEY` 环境变量配合 Fernet 加密。
