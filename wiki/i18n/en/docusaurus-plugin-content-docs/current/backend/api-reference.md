---
sidebar_position: 2
title: API Reference
description: Backend REST API complete endpoint documentation
---

# API Reference

## Authentication

All APIs except `/` and `/api/health` require authentication in the request header:

```
Authorization: Bearer <EVATAR_API_KEY>
```

If `EVATAR_API_KEY` is not set and `EVATAR_DEV_MODE=true`, authentication is skipped (development only).

## Rate Limiting

The following endpoints are limited to **10 requests per minute** (per client IP):

- `POST /api/chat/send`
- `POST /api/chat/send-with-file`
- `POST /api/dynamics/trigger`

Exceeding the limit returns `429 Too Many Requests`.

## Common Response Status Codes

| Status Code | Description |
|-------------|-------------|
| 200 | Success |
| 400 | Bad request parameters |
| 401 | Invalid or missing API Key |
| 404 | Resource not found |
| 413 | File too large |
| 429 | Rate limit exceeded |
| 500 | Internal server error |

---

## Health Check

### GET /api/health

Check if the service is running normally.

**Response example:**

```json
{ "status": "ok" }
```

---

## Photos

### POST /api/photos/upload

Upload a single photo. Max file size 50MB.

**Content-Type:** `multipart/form-data`

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| file | File | Yes | - | Image file (JPEG/PNG/WebP/GIF) |
| device_id | String | No | `"unknown"` | Device identifier (1-256 chars, alphanumeric underscore) |
| device_name | String | No | `""` | Device name |
| source_type | String | No | `"screenshot"` | Source type |
| local_media_store_id | String | No | `""` | Local MediaStore ID (for dedup) |
| original_timestamp | String | No | `""` | Original timestamp (milliseconds) |
| mime_type | String | No | `"image/jpeg"` | MIME type |

**Response example:**

```json
{
  "id": 42,
  "filename": "screenshot_2024.png",
  "file_size": 1048576,
  "dedup": false,
  "message": "Uploaded"
}
```

### POST /api/photos/upload-batch

Batch upload photos, max 50.

### GET /api/photos

Get photo list (paginated).

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| page | int | 1 | Page number (>=1) |
| page_size | int | 20 | Items per page (1-100) |
| status | String | - | Filter by analysis status: `pending`/`processing`/`done`/`error` |
| device_id | String | - | Filter by device |

### GET /api/photos/`photo_id`

Get single photo details (with complete analysis results).

### GET /api/photos/`photo_id`/image

Returns the original photo file (binary stream).

### GET /api/photos/`photo_id`/thumbnail

Returns the photo thumbnail (JPEG, max 512px).

### DELETE /api/photos/`photo_id`

Delete photo and its analysis results, also deletes disk files.

### GET /api/photos/devices

List all synced devices.

---

## Sync State

### GET /api/photos/sync-state

Query device sync state.

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| device_id | String | Yes | Device identifier |

### POST /api/photos/sync-state

Set device sync start timestamp (for initialization).

---

## Analysis

### GET /api/analysis

Get analysis record list.

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| page | int | 1 | Page number |
| page_size | int | 20 | Items per page (1-100) |
| status | String | - | Status filter |
| intent | String | - | Intent filter |

### POST /api/analysis/`photo_id`/reprocess

Re-analyze the specified photo.

### GET /api/stats

Get global statistics.

---

## Chat

### POST /api/chat/send

Send a chat message.

**Content-Type:** `application/json`

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| message | String | Yes | Message content (max 32768 chars) |
| conversation_id | String | No | Conversation ID (hex format, creates new if not provided) |
| device_id | String | No | Device identifier |
| skill_id | String | No | Skill ID to use |

### POST /api/chat/send-with-file

Send a message with attachment (supports image analysis).

**Content-Type:** `multipart/form-data`

### GET /api/chat/conversations

Get conversation list.

### GET /api/chat/conversations/`conversation_id`

Get conversation details (with all messages).

### GET /api/chat/conversations/`conversation_id`/messages

Get conversation messages (incremental pull).

| Parameter | Type | Description |
|-----------|------|-------------|
| after_id | int | Only return messages with ID greater than this (incremental sync) |

### DELETE /api/chat/conversations/`conversation_id`

Delete conversation and all its messages.

---

## Dynamics

### GET /api/dynamics

Get dynamics list (cursor pagination).

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| cursor | int | 0 | Last item's ID from previous page (0 = first load) |
| limit | int | 30 | Items per page (1-100) |
| category | String | - | Filter by category: `insight`/`reminder`/`report`/`note` |
| device_id | String | - | Filter by device |

Pinned items always appear first.

### GET /api/dynamics/stats

Get unread dynamics statistics.

### GET /api/dynamics/`dynamic_id`

Get dynamic details (with full content).

### PUT /api/dynamics/`dynamic_id`/read

Mark dynamic as read.

### PUT /api/dynamics/read-all

Mark all dynamics as read.

### PUT /api/dynamics/`dynamic_id`/pin

Toggle pin status.

### DELETE /api/dynamics/`dynamic_id`

Delete dynamic.

### POST /api/dynamics/trigger

Manually trigger intent reasoning (generate dynamic articles).

---

## Memories

### GET /api/memories

Get memory list.

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| page | int | 1 | Page number |
| page_size | int | 50 | Items per page (1-200) |
| memory_type | String | - | `short_term`/`long_term` |
| category | String | - | `fact`/`people`/`project`/`finance`/`schedule`/`preference`/`interest`/`habit` |
| device_id | String | - | Filter by device |

### GET /api/memories/stats

Get memory statistics.

### DELETE /api/memories/`memory_id`

Delete a single memory.

---

## LLM Config

### GET /api/config/llm

Get current LLM configuration (API Key not returned, only whether it's set).

### PUT /api/config/llm

Update LLM configuration.

`base_url` includes SSRF protection: must use `https://` (dev mode allows `http://localhost`), private addresses blocked, includes DNS rebinding detection.

### GET /api/config/llm/presets

Get preset LLM provider list.

### POST /api/config/llm/presets/`name`/apply

Apply a preset.

---

## Push

### POST /api/push/register

Register or update push device (called on each app startup).

### GET /api/push/devices

List all registered devices.

### DELETE /api/push/devices/`device_id`

Remove registered device.

### POST /api/push/test

Send test push notification.

### POST /api/push/broadcast

Broadcast push notification to all devices.

---

## Skills

### GET /api/skills

Get enabled skill list. First call auto-creates default skills.

### GET /api/skills/`skill_id`

Get skill details (with system_prompt).

### POST /api/skills

Create custom skill.

### PUT /api/skills/`skill_id`

Update skill.

### DELETE /api/skills/`skill_id`

Delete skill.

---

## MCP Servers

### GET /api/mcp-servers

Get configured MCP server list.

### POST /api/mcp-servers

Add MCP server. URL includes SSRF protection validation.

### DELETE /api/mcp-servers/`server_id`

Delete MCP server.
