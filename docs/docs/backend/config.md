---
sidebar_position: 5
title: 配置项
description: 后端服务配置完整参考
---

# 配置项

## 配置机制

Evatar 使用 `pydantic-settings` 管理配置，所有环境变量以 `EVATAR_` 为前缀。配置优先级：

1. 环境变量 `EVATAR_*`
2. `.env` 文件
3. 代码中的默认值

```python
# config.py
class Settings(BaseSettings):
    host: str = "0.0.0.0"
    port: int = 8421
    # ...

    class Config:
        env_prefix = "EVATAR_"  # 所有字段自动加 EVATAR_ 前缀

settings = Settings()  # 全局单例
```

## 环境变量完整参考

### 服务器

| 环境变量 | 默认值 | 说明 |
|----------|--------|------|
| `EVATAR_HOST` | `0.0.0.0` | 监听地址 |
| `EVATAR_PORT` | `8421` | 监听端口 |
| `EVATAR_DEV_MODE` | `false` | 开发模式（跳过认证，允许 HTTP） |
| `EVATAR_CORS_ORIGINS` | `""` | CORS 允许的源（逗号分隔，空则使用默认 localhost） |

### 认证

| 环境变量 | 默认值 | 说明 |
|----------|--------|------|
| `EVATAR_API_KEY` | `""` | API 认证密钥。为空且非 dev_mode 时所有端点公开访问 |

### 存储

| 环境变量 | 默认值 | 说明 |
|----------|--------|------|
| `EVATAR_DATA_DIR` | `./data` | 数据目录 |
| `EVATAR_PHOTOS_DIR` | `./data/photos` | 照片存储目录 |
| `EVATAR_DB_PATH` | `./data/evatar.db` | SQLite 数据库路径 |
| `EVATAR_MAX_UPLOAD_BYTES` | `52428800` (50MB) | 单文件上传大小限制 |

### LLM 配置

| 环境变量 | 默认值 | 说明 |
|----------|--------|------|
| `EVATAR_LLM_BASE_URL` | `""` | LLM API 地址 |
| `EVATAR_LLM_API_KEY` | `""` | LLM API Key（必填，否则 AI 功能不可用） |
| `EVATAR_LLM_MODEL` | `mimo-v2.5` | 模型名称 |
| `EVATAR_LLM_MAX_TOKENS` | `4096` | 最大生成 token 数 |
| `EVATAR_LLM_TEMPERATURE` | `0.1` | 生成温度 |

### Agent

| 环境变量 | 默认值 | 说明 |
|----------|--------|------|
| `EVATAR_AGENT_MAX_ROUNDS` | `3` | Agent 工具调用最大轮数 |
| `EVATAR_AGENT_HISTORY_LIMIT` | `20` | 加载的历史消息条数 |

### 网络搜索

| 环境变量 | 默认值 | 说明 |
|----------|--------|------|
| `EVATAR_TAVILY_API_KEY` | `""` | Tavily 搜索 API Key（优先使用） |
| `EVATAR_BRAVE_API_KEY` | `""` | Brave Search API Key（备选） |

### 加密

| 环境变量 | 默认值 | 说明 |
|----------|--------|------|
| `EVATAR_ENCRYPTION_KEY` | `""` | Fernet 加密密钥。为空时自动生成并存储到 `data/.encryption_key` |

### 数据保留

| 环境变量 | 默认值 | 说明 |
|----------|--------|------|
| `EVATAR_RETENTION_DAYS` | `30` | 数据保留天数，超期数据自动清理 |

### 推送通知

| 环境变量 | 默认值 | 说明 |
|----------|--------|------|
| `EVATAR_FCM_PROJECT_ID` | `""` | Firebase 项目 ID |
| `EVATAR_FCM_CREDENTIALS_JSON` | `""` | FCM 服务账号 JSON（文件路径或内联 JSON） |
| `EVATAR_PUSH_WEBHOOK_URL` | `""` | 推送 Webhook URL（FCM 未配置时的回退方案） |

## 通过 Web UI 配置

### LLM 配置

LLM 配置存储在数据库的 `llm_config` 表中（单行，id=1），可通过 Web 设置页面实时修改，无需重启服务。

**配置优先级：** 数据库配置 > 环境变量

数据库中的 LLM 配置有 60 秒缓存，修改后通过 `invalidate_llm_config_cache()` 立即生效。

### 预设方案

系统内置 7 种 LLM 预设方案，一键切换：

| 预设 ID | 服务商 | 模型 | 上下文窗口 |
|---------|--------|------|-----------|
| `mimo` | 小米 MiMo | mimo-v2.5 | 1M tokens |
| `qwen` | 通义千问 | qwen-vl-max | 128K tokens |
| `openai` | OpenAI | gpt-4o | 128K tokens |
| `claude` | Anthropic | claude-sonnet-4-20250514 | 200K tokens |
| `glm` | 智谱 GLM | glm-4v | 128K tokens |
| `kimi` | 月之暗面 | moonshot-v1-128k-vision-preview | 128K tokens |
| `deepseek` | DeepSeek | deepseek-chat | 64K tokens |

### MCP 服务器

通过 `POST /api/mcp-servers` 添加外部 MCP 服务器，URL 需通过 SSRF 防护验证（仅允许 HTTPS，禁止私有地址）。

## SSRF 防护

`base_url` 和 MCP 服务器 URL 配置包含多层 SSRF 防护：

```python
def _validate_base_url(url: str):
    # 1. 必须使用 https://（开发模式允许 http://localhost）
    # 2. 正则检查私有 IPv4 地址（10.x, 192.168.x, 172.16-31.x）
    # 3. 检查 IPv6 私有前缀（::1, fe80:, fc00:, fd）
    # 4. ipaddress 模块检查 is_private/is_loopback/is_link_local
    # 5. DNS rebinding 防护：解析域名后验证所有 IP
```

## 安全注意事项

| 项目 | 现状 | 建议 |
|------|------|------|
| API Key 存储 | 明文 | 生产环境使用 `EVATAR_API_KEY` 环境变量 |
| LLM API Key | DB 明文 | API 响应中不返回，仅返回 `api_key_set` 标志 |
| 加密密钥 | 自动生成或环境变量 | 生产环境务必设置 `EVATAR_ENCRYPTION_KEY` |
| CORS | 默认允许 localhost:3000/5173/8421 | 生产环境通过 `EVATAR_CORS_ORIGINS` 限制 |
| 速率限制 | 聊天/动态 10次/分钟/IP | 可根据需要调整 |
| 认证比较 | `hmac.compare_digest` 防时序攻击 | - |

## 最小启动配置

```bash
# 最简启动（开发模式）
EVATAR_DEV_MODE=true python main.py

# 生产模式最小配置
EVATAR_API_KEY=your-secret-key \
EVATAR_LLM_API_KEY=your-llm-key \
python main.py

# 完整配置
EVATAR_API_KEY=your-secret-key \
EVATAR_LLM_API_KEY=your-llm-key \
EVATAR_LLM_BASE_URL=https://api.openai.com/v1 \
EVATAR_LLM_MODEL=gpt-4o \
EVATAR_TAVILY_API_KEY=tvly-xxx \
EVATAR_ENCRYPTION_KEY=$(python -c "from cryptography.fernet import Fernet; print(Fernet.generate_key().decode())") \
EVATAR_RETENTION_DAYS=60 \
python main.py
```
