---
sidebar_position: 5
title: Configuration
description: Backend service configuration reference
---

# Configuration

## Configuration Mechanism

Evatar uses `pydantic-settings` for configuration management. All environment variables use the `EVATAR_` prefix. Configuration priority:

1. Environment variables `EVATAR_*`
2. `.env` file
3. Code defaults

## Environment Variables Reference

### Server

| Variable | Default | Description |
|----------|---------|-------------|
| `EVATAR_HOST` | `0.0.0.0` | Listen address |
| `EVATAR_PORT` | `8421` | Listen port |
| `EVATAR_DEV_MODE` | `false` | Development mode (skip auth, allow HTTP) |
| `EVATAR_CORS_ORIGINS` | `""` | CORS allowed origins (comma-separated) |

### Authentication

| Variable | Default | Description |
|----------|---------|-------------|
| `EVATAR_API_KEY` | `""` | API authentication key |

### Storage

| Variable | Default | Description |
|----------|---------|-------------|
| `EVATAR_DATA_DIR` | `./data` | Data directory |
| `EVATAR_PHOTOS_DIR` | `./data/photos` | Photo storage directory |
| `EVATAR_DB_PATH` | `./data/evatar.db` | SQLite database path |
| `EVATAR_MAX_UPLOAD_BYTES` | `52428800` (50MB) | Single file upload size limit |

### LLM

| Variable | Default | Description |
|----------|---------|-------------|
| `EVATAR_LLM_BASE_URL` | `""` | LLM API address |
| `EVATAR_LLM_API_KEY` | `""` | LLM API Key (required for AI features) |
| `EVATAR_LLM_MODEL` | `mimo-v2.5` | Model name |
| `EVATAR_LLM_MAX_TOKENS` | `4096` | Max generation tokens |
| `EVATAR_LLM_TEMPERATURE` | `0.1` | Generation temperature |

### Agent

| Variable | Default | Description |
|----------|---------|-------------|
| `EVATAR_AGENT_MAX_ROUNDS` | `3` | Agent max tool call rounds |
| `EVATAR_AGENT_HISTORY_LIMIT` | `20` | Loaded history message count |

### Web Search

| Variable | Default | Description |
|----------|---------|-------------|
| `EVATAR_TAVILY_API_KEY` | `""` | Tavily search API Key (preferred) |
| `EVATAR_BRAVE_API_KEY` | `""` | Brave Search API Key (fallback) |

### Encryption

| Variable | Default | Description |
|----------|---------|-------------|
| `EVATAR_ENCRYPTION_KEY` | `""` | Fernet encryption key. Auto-generated and stored in `data/.encryption_key` when empty |

### Data Retention

| Variable | Default | Description |
|----------|---------|-------------|
| `EVATAR_RETENTION_DAYS` | `30` | Data retention days, expired data auto-cleaned |

### Push Notifications

| Variable | Default | Description |
|----------|---------|-------------|
| `EVATAR_FCM_PROJECT_ID` | `""` | Firebase project ID |
| `EVATAR_FCM_CREDENTIALS_JSON` | `""` | FCM service account JSON |
| `EVATAR_PUSH_WEBHOOK_URL` | `""` | Push Webhook URL (fallback when FCM not configured) |

## LLM Presets

System includes 7 built-in LLM provider presets, one-click switching:

| Preset | Provider | Model | Context Window |
|--------|----------|-------|---------------|
| `mimo` | Xiaomi MiMo | mimo-v2.5 | 1M tokens |
| `qwen` | Tongyi Qianwen | qwen-vl-max | 128K tokens |
| `openai` | OpenAI | gpt-4o | 128K tokens |
| `claude` | Anthropic | claude-sonnet-4-20250514 | 200K tokens |
| `glm` | Zhipu GLM | glm-4v | 128K tokens |
| `kimi` | Moonshot | moonshot-v1-128k-vision-preview | 128K tokens |
| `deepseek` | DeepSeek | deepseek-chat | 64K tokens |

## Minimum Startup Configuration

```bash
# Simplest startup (development mode)
EVATAR_DEV_MODE=true python main.py

# Production mode minimum
EVATAR_API_KEY=your-secret-key \
EVATAR_LLM_API_KEY=your-llm-key \
python main.py

# Full configuration
EVATAR_API_KEY=your-secret-key \
EVATAR_LLM_API_KEY=your-llm-key \
EVATAR_LLM_BASE_URL=https://api.openai.com/v1 \
EVATAR_LLM_MODEL=gpt-4o \
EVATAR_TAVILY_API_KEY=tvly-xxx \
EVATAR_ENCRYPTION_KEY=$(python -c "from cryptography.fernet import Fernet; print(Fernet.generate_key().decode())") \
EVATAR_RETENTION_DAYS=60 \
python main.py
```
