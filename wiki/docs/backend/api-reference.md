---
sidebar_position: 2
title: API 参考
description: 后端 REST API 完整接口文档
---

# API 参考

## 认证

除 `/` 和 `/api/health` 外，所有 API 需要在请求头中携带认证信息：

```
Authorization: Bearer <EVATAR_API_KEY>
```

如果 `EVATAR_API_KEY` 未设置且 `EVATAR_DEV_MODE=true`，则跳过认证（仅限开发环境）。

## 速率限制

以下接口限制为 **每分钟 10 次**（按客户端 IP）：

- `POST /api/chat/send`
- `POST /api/chat/send-with-file`
- `POST /api/dynamics/trigger`

超出限制返回 `429 Too Many Requests`。

## 通用响应状态码

| 状态码 | 说明 |
|--------|------|
| 200 | 成功 |
| 400 | 请求参数错误 |
| 401 | API Key 无效或缺失 |
| 404 | 资源不存在 |
| 413 | 文件过大 |
| 429 | 速率限制超出 |
| 500 | 服务器内部错误 |

---

## 健康检查

### GET /api/health

检查服务是否正常运行。

**响应示例：**

```json
{ "status": "ok" }
```

---

## 照片 (Photos)

### POST /api/photos/upload

上传单张照片。最大文件大小 50MB。

**Content-Type:** `multipart/form-data`

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| file | File | 是 | - | 图片文件 (JPEG/PNG/WebP/GIF) |
| device_id | String | 否 | `"unknown"` | 设备标识 (1-256字符, 字母数字下划线) |
| device_name | String | 否 | `""` | 设备名称 |
| source_type | String | 否 | `"screenshot"` | 来源类型 |
| local_media_store_id | String | 否 | `""` | 本地 MediaStore ID (用于去重) |
| original_timestamp | String | 否 | `""` | 原始时间戳 (毫秒) |
| mime_type | String | 否 | `"image/jpeg"` | MIME 类型 |

**响应示例：**

```json
{
  "id": 42,
  "filename": "screenshot_2024.png",
  "file_size": 1048576,
  "dedup": false,
  "message": "Uploaded"
}
```

如果 `local_media_store_id` 已存在（去重），返回：

```json
{
  "id": 42,
  "filename": "screenshot_2024.png",
  "dedup": true,
  "message": "Already exists (dedup)"
}
```

### POST /api/photos/upload-batch

批量上传照片，最多 50 张。

**Content-Type:** `multipart/form-data`

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| device_id | String | 是 | 设备标识 |
| device_name | String | 否 | 设备名称 |
| files | File[] | 是 | 图片文件列表 |
| timestamps | String | 否 | 逗号分隔的时间戳列表 |
| local_ids | String | 否 | 逗号分隔的本地 ID 列表 |
| mime_types | String | 否 | 逗号分隔的 MIME 类型列表 |

**响应示例：**

```json
{
  "uploaded": 3,
  "results": [
    {"id": 42, "filename": "a.png", "dedup": false},
    {"id": 43, "filename": "b.png", "dedup": false},
    {"filename": "c.png", "error": "empty"}
  ]
}
```

### GET /api/photos

获取照片列表（分页）。

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| page | int | 1 | 页码 (>=1) |
| page_size | int | 20 | 每页数量 (1-100) |
| status | String | - | 按分析状态筛选: `pending`/`processing`/`done`/`error` |
| device_id | String | - | 按设备筛选 |

**响应示例：**

```json
{
  "total": 150,
  "page": 1,
  "page_size": 20,
  "items": [
    {
      "id": 42,
      "filename": "screenshot.png",
      "file_size": 1048576,
      "width": 1080,
      "height": 2400,
      "mime_type": "image/png",
      "source_type": "screenshot",
      "device_id": "xiaomi-abc123",
      "device_name": "Xiaomi 14",
      "original_timestamp": "2024-01-15T10:30:00",
      "created_at": "2024-01-15T10:30:05",
      "analysis_status": "done",
      "intent": "reference",
      "summary": "微信聊天记录，关于明天下午的会议安排"
    }
  ]
}
```

### GET /api/photos/``photo_id``

获取单张照片详情（含完整分析结果）。

**响应示例：**

```json
{
  "id": 42,
  "local_media_store_id": "12345",
  "filename": "screenshot.png",
  "original_path": "/data/photos/2024-01-15/abc123.png",
  "thumbnail_path": "/data/photos/2024-01-15/abc123_thumb.jpg",
  "file_size": 1048576,
  "width": 1080,
  "height": 2400,
  "mime_type": "image/png",
  "source_type": "screenshot",
  "device_id": "xiaomi-abc123",
  "device_name": "Xiaomi 14",
  "original_timestamp": "2024-01-15T10:30:00",
  "created_at": "2024-01-15T10:30:05",
  "analysis": {
    "id": 42,
    "status": "done",
    "app_name": "微信",
    "content_category": "chat",
    "intent": "reference",
    "summary": "微信聊天记录，关于明天下午的会议安排",
    "entities": "[{\"type\":\"人名\",\"value\":\"张三\"},{\"type\":\"日期\",\"value\":\"明天下午\"}]",
    "confidence": 0.9,
    "llm_response": "{...}",
    "error_message": null,
    "created_at": "2024-01-15T10:30:05",
    "completed_at": "2024-01-15T10:30:08"
  }
}
```

### GET /api/photos/``photo_id``/image

返回照片原始文件（二进制流）。

### GET /api/photos/``photo_id``/thumbnail

返回照片缩略图（JPEG，最大 512px）。

### DELETE /api/photos/``photo_id``

删除照片及其分析结果，同时删除磁盘文件。

```json
{ "message": "Deleted" }
```

### GET /api/photos/devices

列出所有已同步设备。

```json
{
  "devices": [
    {
      "device_id": "xiaomi-abc123",
      "device_name": "Xiaomi 14",
      "last_synced_timestamp": "2024-01-15T10:30:00",
      "last_sync_time": "2024-01-15T10:30:05",
      "total_synced": 150
    }
  ]
}
```

---

## 同步状态 (Sync State)

### GET /api/photos/sync-state

查询设备同步状态。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| device_id | String | 是 | 设备标识 |

**响应示例：**

```json
{
  "device_id": "xiaomi-abc123",
  "last_synced_timestamp": "2024-01-15T10:30:00",
  "last_synced_ts_ms": 1705312200000,
  "total_synced": 150
}
```

### POST /api/photos/sync-state

设置设备同步起始时间戳（用于初始化）。

**Content-Type:** `multipart/form-data`

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| device_id | String | 是 | 设备标识 |
| since_ms | int | 否 | 起始时间戳（毫秒），0 表示同步所有 |
| device_name | String | 否 | 设备名称 |

---

## 分析 (Analysis)

### GET /api/analysis

获取分析记录列表。

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| page | int | 1 | 页码 |
| page_size | int | 20 | 每页数量 (1-100) |
| status | String | - | 状态筛选 |
| intent | String | - | 意图筛选 |

**响应示例：**

```json
{
  "total": 100,
  "page": 1,
  "page_size": 20,
  "items": [
    {
      "id": 42,
      "photo_id": 42,
      "status": "done",
      "app_name": "微信",
      "content_category": "chat",
      "intent": "reference",
      "summary": "聊天记录...",
      "entities": "[...]",
      "confidence": 0.9,
      "error_message": null,
      "created_at": "2024-01-15T10:30:05",
      "completed_at": "2024-01-15T10:30:08",
      "photo_filename": "screenshot.png"
    }
  ]
}
```

### POST /api/analysis/``photo_id``/reprocess

重新分析指定照片。

```json
{ "message": "Reprocessing queued" }
```

### GET /api/stats

获取全局统计数据。

**响应示例：**

```json
{
  "total_photos": 150,
  "total_analyses": 150,
  "done": 140,
  "pending": 5,
  "processing": 2,
  "errors": 3,
  "intent_distribution": {
    "reference": 60,
    "reminder": 20,
    "research": 30,
    "note": 20,
    "ignore": 10
  },
  "category_distribution": {
    "chat": 50,
    "webpage": 30,
    "finance": 20,
    "notification": 15,
    "social_media": 10,
    "other": 15
  }
}
```

---

## 聊天 (Chat)

### POST /api/chat/send

发送聊天消息。

**Content-Type:** `application/json`

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| message | String | 是 | 消息内容 (最长 32768 字符) |
| conversation_id | String | 否 | 对话 ID (hex 格式，不传则新建) |
| device_id | String | 否 | 设备标识 |
| skill_id | String | 否 | 使用的技能 ID |

**请求示例：**

```json
{
  "message": "帮我搜索最近的火车票截图",
  "conversation_id": "a1b2c3d4e5f6",
  "skill_id": "research"
}
```

**响应示例：**

```json
{
  "conversation_id": "a1b2c3d4e5f6",
  "message": {
    "role": "assistant",
    "content": "根据你的截图记录，找到了以下火车票信息：\n\n| 日期 | 车次 | 出发-到达 |\n|------|------|----------|\n| 01-15 | G1234 | 北京-上海 |",
    "tool_calls": []
  }
}
```

### POST /api/chat/send-with-file

发送带附件的消息（支持图片分析）。

**Content-Type:** `multipart/form-data`

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| message | String | 否 | 消息内容 (默认 `"发送了附件"`) |
| conversation_id | String | 否 | 对话 ID |
| skill_id | String | 否 | 技能 ID |
| device_id | String | 否 | 设备标识 |
| file | File | 否 | 附件文件 (最大 20MB) |

### GET /api/chat/conversations

获取对话列表。

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| device_id | String | - | 按设备筛选 |
| page | int | 1 | 页码 |
| page_size | int | 50 | 每页数量 (1-100) |

**响应示例：**

```json
{
  "conversations": [
    {
      "id": "a1b2c3d4e5f6",
      "title": "帮我搜索最近的火车票截图",
      "device_id": "xiaomi-abc123",
      "created_at": "2024-01-15T10:30:00",
      "updated_at": "2024-01-15T10:35:00",
      "message_count": 6,
      "last_message": "帮我搜索最近的火车票截图"
    }
  ]
}
```

### GET /api/chat/conversations/``conversation_id``

获取对话详情（含所有消息）。

**响应示例：**

```json
{
  "id": "a1b2c3d4e5f6",
  "title": "帮我搜索最近的火车票截图",
  "device_id": "xiaomi-abc123",
  "created_at": "2024-01-15T10:30:00",
  "messages": [
    {
      "id": 1,
      "role": "user",
      "content": "帮我搜索最近的火车票截图",
      "created_at": "2024-01-15T10:30:00"
    },
    {
      "id": 2,
      "role": "assistant",
      "content": null,
      "tool_calls": "[{\"id\":\"call_1\",\"type\":\"function\",\"function\":{\"name\":\"search_knowledge\",\"arguments\":\"{\\\"query\\\":\\\"火车票\\\"}\"}}]",
      "created_at": "2024-01-15T10:30:02"
    },
    {
      "id": 3,
      "role": "tool",
      "content": "{\"tool\":\"search_knowledge\",\"count\":3,\"results\":[...]}",
      "tool_name": "search_knowledge",
      "created_at": "2024-01-15T10:30:03"
    },
    {
      "id": 4,
      "role": "assistant",
      "content": "根据你的截图记录，找到了以下火车票信息...",
      "created_at": "2024-01-15T10:30:05"
    }
  ]
}
```

### GET /api/chat/conversations/``conversation_id``/messages

获取对话消息（可增量拉取）。

| 参数 | 类型 | 说明 |
|------|------|------|
| after_id | int | 只返回 ID 大于此值的消息（增量同步） |

### DELETE /api/chat/conversations/``conversation_id``

删除对话及其所有消息。

---

## 动态 (Dynamics)

### GET /api/dynamics

获取动态列表（游标分页）。

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| cursor | int | 0 | 上一页最后一条的 ID (0 = 首次加载) |
| limit | int | 30 | 每页数量 (1-100) |
| category | String | - | 按分类筛选: `insight`/`reminder`/`report`/`note` |
| device_id | String | - | 按设备筛选 |

置顶项始终排在最前面。

**响应示例：**

```json
{
  "items": [
    {
      "id": 10,
      "title": "近期出行计划整理",
      "summary": "根据截图分析，你近期有多个出行相关的安排",
      "category": "insight",
      "confidence": 0.8,
      "is_read": false,
      "is_pinned": true,
      "device_id": "xiaomi-abc123",
      "created_at": "2024-01-15T12:00:00"
    }
  ],
  "next_cursor": 10,
  "has_more": true
}
```

### GET /api/dynamics/stats

获取动态未读统计。

```json
{
  "total_unread": 5,
  "by_category": {
    "insight": 2,
    "reminder": 2,
    "note": 1
  }
}
```

### GET /api/dynamics/``dynamic_id``

获取动态详情（含完整内容）。

```json
{
  "id": 10,
  "title": "近期出行计划整理",
  "summary": "根据截图分析...",
  "content": "# 近期出行计划\n\n根据你的截图记录...",
  "category": "insight",
  "confidence": 0.8,
  "is_read": false,
  "is_pinned": false,
  "device_id": "xiaomi-abc123",
  "created_at": "2024-01-15T12:00:00",
  "source_photo_ids": "[42, 43, 44]",
  "source_conversation_ids": "[\"a1b2c3d4\"]"
}
```

### PUT /api/dynamics/``dynamic_id``/read

标记动态为已读。

### PUT /api/dynamics/read-all

标记所有动态为已读。

| 参数 | 类型 | 说明 |
|------|------|------|
| device_id | String | 可选，按设备筛选 |

### PUT /api/dynamics/``dynamic_id``/pin

切换动态置顶状态。

```json
{ "is_pinned": true }
```

### DELETE /api/dynamics/``dynamic_id``

删除动态。

### POST /api/dynamics/trigger

手动触发意图推理（生成动态文章）。

| 参数 | 类型 | 说明 |
|------|------|------|
| device_id | String | 可选，按设备筛选 |

```json
{
  "generated": 2,
  "articles": [
    {"title": "出行计划整理", "category": "insight"},
    {"title": "提醒事项汇总", "category": "reminder"}
  ]
}
```

---

## 记忆 (Memories)

### GET /api/memories

获取记忆列表。

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| page | int | 1 | 页码 |
| page_size | int | 50 | 每页数量 (1-200) |
| memory_type | String | - | `short_term`/`long_term` |
| category | String | - | `fact`/`people`/`project`/`finance`/`schedule`/`preference`/`interest`/`habit` |
| device_id | String | - | 按设备筛选 |

**响应示例：**

```json
{
  "total": 25,
  "page": 1,
  "page_size": 50,
  "items": [
    {
      "id": 1,
      "content": "用户经常查看股票 NVDA 和 TSLA",
      "memory_type": "long_term",
      "source_type": "chat",
      "category": "interest",
      "importance": 0.8,
      "access_count": 5,
      "created_at": "2024-01-15T10:00:00",
      "expires_at": null
    }
  ]
}
```

### GET /api/memories/stats

获取记忆统计信息。

```json
{
  "total": 25,
  "short_term": 10,
  "long_term": 15,
  "categories": {
    "fact": 8,
    "people": 5,
    "finance": 4,
    "interest": 3,
    "schedule": 3,
    "preference": 2
  }
}
```

### DELETE /api/memories/``memory_id``

删除单条记忆。

---

## LLM 配置 (Config)

### GET /api/config/llm

获取当前 LLM 配置（API Key 不会返回，仅返回是否已设置）。

```json
{
  "provider": "mimo",
  "base_url": "https://token-plan-cn.xiaomimimo.com/v1",
  "api_key_set": true,
  "model": "mimo-v2.5",
  "max_context_tokens": 1048576,
  "temperature": 0.1
}
```

### PUT /api/config/llm

更新 LLM 配置。

```json
{
  "provider": "openai",
  "base_url": "https://api.openai.com/v1",
  "api_key": "sk-xxx",
  "model": "gpt-4o",
  "temperature": 0.2
}
```

`base_url` 包含 SSRF 防护：必须使用 `https://`（开发模式允许 `http://localhost`），禁止指向私有地址，包含 DNS rebinding 检测。

### GET /api/config/llm/presets

获取预设 LLM 方案列表。

```json
{
  "presets": {
    "mimo": {"provider": "mimo", "base_url": "https://token-plan-cn.xiaomimimo.com/v1", "model": "mimo-v2.5", "max_context_tokens": 1048576},
    "qwen": {"provider": "qwen", "base_url": "https://dashscope.aliyuncs.com/compatible-mode/v1", "model": "qwen-vl-max", "max_context_tokens": 131072},
    "openai": {"provider": "openai", "base_url": "https://api.openai.com/v1", "model": "gpt-4o", "max_context_tokens": 128000},
    "claude": {"provider": "claude", "base_url": "https://api.anthropic.com/v1", "model": "claude-sonnet-4-20250514", "max_context_tokens": 200000},
    "glm": {"provider": "glm", "base_url": "https://open.bigmodel.cn/api/paas/v4", "model": "glm-4v", "max_context_tokens": 128000},
    "kimi": {"provider": "kimi", "base_url": "https://api.moonshot.cn/v1", "model": "moonshot-v1-128k-vision-preview", "max_context_tokens": 128000},
    "deepseek": {"provider": "deepseek", "base_url": "https://api.deepseek.com/v1", "model": "deepseek-chat", "max_context_tokens": 65536}
  }
}
```

### POST /api/config/llm/presets/`name`/apply

应用预设方案。

```json
{ "api_key": "sk-xxx" }
```

---

## 推送 (Push)

### POST /api/push/register

注册或更新推送设备（每次 App 启动调用）。

```json
{
  "device_id": "xiaomi-abc123",
  "token": "fcm_token_xxx",
  "platform": "android",
  "device_name": "Xiaomi 14",
  "device_model": "2312DRAABC",
  "app_version": "1.0.0"
}
```

### GET /api/push/devices

列出所有已注册设备。

```json
{
  "devices": [
    {
      "id": 1,
      "device_id": "xiaomi-abc123",
      "device_name": "Xiaomi 14",
      "device_model": "2312DRAABC",
      "platform": "android",
      "app_version": "1.0.0",
      "last_seen": "2024-01-15T10:30:00",
      "created_at": "2024-01-10T08:00:00"
    }
  ]
}
```

### DELETE /api/push/devices/``device_id``

移除已注册设备。

### POST /api/push/test

发送测试推送通知。

```json
{ "device_id": "xiaomi-abc123" }
```

### POST /api/push/broadcast

广播推送通知到所有设备。

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| title | String | `"Evatar"` | 通知标题 |
| body | String | `"新消息"` | 通知内容 |

---

## 技能 (Skills)

### GET /api/skills

获取已启用的技能列表。首次调用会自动创建默认技能。

```json
{
  "skills": [
    {"id": "summarize", "name": "总结截图", "description": "分析最近的截图，生成摘要笔记", "icon": "📝"},
    {"id": "reminders", "name": "提取提醒", "description": "从截图中提取所有需要提醒的事项", "icon": "⏰"},
    {"id": "research", "name": "深度研究", "description": "对截图中的主题进行深入研究", "icon": "🔬"},
    {"id": "stock", "name": "股票分析", "description": "整理截图中的股票和金融信息", "icon": "📈"}
  ]
}
```

### GET /api/skills/``skill_id``

获取技能详情（含 system_prompt）。

### POST /api/skills

创建自定义技能。

```json
{
  "id": "my-skill",
  "name": "我的技能",
  "description": "自定义技能描述",
  "system_prompt": "请帮我...",
  "icon": "🎯"
}
```

### PUT /api/skills/``skill_id``

更新技能。

### DELETE /api/skills/``skill_id``

删除技能。

---

## MCP 服务器

### GET /api/mcp-servers

获取已配置的 MCP 服务器列表。

```json
{
  "servers": [
    {
      "id": "weather",
      "name": "天气服务",
      "url": "https://weather-mcp.example.com",
      "description": "获取天气信息",
      "enabled": true
    }
  ]
}
```

### POST /api/mcp-servers

添加 MCP 服务器。URL 包含 SSRF 防护验证。

```json
{
  "id": "weather",
  "name": "天气服务",
  "url": "https://weather-mcp.example.com",
  "description": "获取天气信息"
}
```

### DELETE /api/mcp-servers/``server_id``

删除 MCP 服务器。
