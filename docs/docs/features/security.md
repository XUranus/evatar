---
sidebar_position: 8
title: 安全机制
description: 认证、加密与防护策略
---

# 安全机制

## 概述

Evatar 采用多层安全防护策略，包括 API Key 认证、SSRF 防护、速率限制、文件上传验证、路径遍历防护和数据加密。所有安全机制在 `main.py` 中间件和各 API 路由中统一实现。

## 认证机制

### API Key 认证

系统使用 Bearer Token 方式进行 API 认证：

```python
# main.py - auth_middleware()
EXEMPT_PATHS = {"/", "/api/health"}

@app.middleware("http")
async def auth_middleware(request: Request, call_next):
    if settings.api_key and request.url.path not in EXEMPT_PATHS:
        auth = request.headers.get("Authorization", "")
        key_from_header = auth.removeprefix("Bearer ").strip() if auth.startswith("Bearer ") else ""
        if not hmac.compare_digest(key_from_header, settings.api_key):
            raise HTTPException(status_code=401, detail="Invalid or missing API key")
    return await call_next(request)
```

关键设计：
- 使用 `hmac.compare_digest` 进行时间恒定比较，防止时序攻击
- 健康检查端点 (`/`, `/api/health`) 免认证
- 未设置 `EVATAR_API_KEY` 时所有端点公开访问（开发模式）
- 未设置 API Key 且非 dev_mode 时会打印醒目的安全警告

### 配置方式

```bash
# 生产环境 - 必须设置
export EVATAR_API_KEY="your-secret-key-here"

# 开发环境 - 可选
export EVATAR_DEV_MODE=true
```

## SSRF 防护

LLM Base URL 配置包含严格的 SSRF (Server-Side Request Forgery) 防护，防止攻击者通过配置恶意 URL 访问内网资源：

```python
# api/config.py - _validate_base_url()
def _validate_base_url(url: str):
    # 1. 强制 HTTPS (dev 模式允许 localhost HTTP)
    if not url.startswith("https://"):
        if not (app_settings.dev_mode and url.startswith("http://")):
            raise HTTPException(status_code=400, detail="base_url must use https://")

    # 2. 正则检查私有 IP 地址
    parsed = urllib.parse.urlparse(url)
    hostname = parsed.hostname
    if _PRIVATE_HOST.search(hostname):
        raise HTTPException(status_code=400, detail="must not point to private address")

    # 3. IPv6 私有地址检查
    for prefix in ("::1", "fe80:", "fc00:", "fd", "::ffff:"):
        if hostname.lower().startswith(prefix):
            raise HTTPException(status_code=400, detail="must not point to private address")

    # 4. ipaddress 模块验证
    addr = ipaddress.ip_address(clean_host)
    if addr.is_private or addr.is_loopback or addr.is_link_local:
        raise HTTPException(status_code=400, detail="must not point to private address")

    # 5. DNS 解析验证 (防止 DNS rebinding)
    resolved_addrs = socket.getaddrinfo(hostname, 0, proto=socket.IPPROTO_TCP)
    for family, _, _, _, sockaddr in resolved_addrs:
        addr = ipaddress.ip_address(sockaddr[0])
        if addr.is_private or addr.is_loopback or addr.is_link_local:
            raise HTTPException(status_code=400, detail="DNS rebinding detected")
```

### 防护层级

| 层级 | 检查内容 | 防护目标 |
|------|---------|---------|
| 协议检查 | 强制 HTTPS | 防止明文传输 |
| 正则匹配 | 私有 IP 模式 | 快速拦截常见内网地址 |
| IPv6 检查 | `::1`, `fe80:`, `fc00:`, `fd` | IPv6 私有地址 |
| IPv4-mapped | `::ffff:x.x.x.x` | IPv4 映射的 IPv6 地址 |
| ipaddress 模块 | `is_private`, `is_loopback`, `is_link_local` | 全面 IP 验证 |
| DNS 解析 | `socket.getaddrinfo` | 防止 DNS rebinding 攻击 |

### 拦截的私有地址模式

```python
_PRIVATE_HOST = re.compile(
    r'(localhost|127\.0\.0\.1|0\.0\.0\.0|169\.254\.\d+\.\d+|'
    r'10\.\d+\.\d+\.\d+|192\.168\.\d+\.\d+|172\.(1[6-9]|2\d|3[01])\.\d+\.\d+)'
)
```

覆盖范围：
- `localhost`, `127.0.0.1`, `0.0.0.0`
- `10.0.0.0/8` (A 类私有)
- `172.16.0.0/12` (B 类私有)
- `192.168.0.0/16` (C 类私有)
- `169.254.0.0/16` (链路本地)

## 速率限制

对高开销的 API 端点实施基于 IP 的速率限制：

```python
# main.py
_RATE_LIMITED_PATHS = {"/api/chat/send", "/api/chat/send-with-file", "/api/dynamics/trigger"}
_rate_limits: dict[str, list[float]] = defaultdict(list)

@app.middleware("http")
async def rate_limit_middleware(request: Request, call_next):
    if request.url.path in _RATE_LIMITED_PATHS:
        client_ip = request.client.host if request.client else "unknown"
        now = time.time()
        _rate_limits[client_ip] = [t for t in _rate_limits[client_ip] if now - t < 60]
        if len(_rate_limits[client_ip]) >= 10:
            raise HTTPException(status_code=429, detail="Rate limit exceeded (10 requests per minute)")
        _rate_limits[client_ip].append(now)
    return await call_next(request)
```

### 限流规则

| 端点 | 限制 | 窗口 | 说明 |
|------|------|------|------|
| `/api/chat/send` | 10 次 | 1 分钟 | 聊天消息 (涉及 LLM 调用) |
| `/api/chat/send-with-file` | 10 次 | 1 分钟 | 带附件聊天 |
| `/api/dynamics/trigger` | 10 次 | 1 分钟 | 手动触发推理 |

超出限制返回 HTTP 429：

```json
{"detail": "Rate limit exceeded (10 requests per minute)"}
```

## 文件上传验证

### MIME 类型验证

只允许特定图片格式上传：

```python
# api/photos.py
ALLOWED_MIMES = {"image/jpeg", "image/png", "image/webp", "image/gif"}
```

### 文件大小限制

```python
# config.py
max_upload_bytes: int = 50 * 1024 * 1024  # 50MB

# api/photos.py
if len(file_bytes) > settings.max_upload_bytes:
    raise HTTPException(status_code=413, detail="File too large (max 50MB)")
```

| 场景 | 限制 |
|------|------|
| 截图上传 | 50MB |
| 聊天附件 | 20MB |

### 文件扩展名验证

```python
# services/storage.py
ALLOWED_EXTS = {".jpg", ".jpeg", ".png", ".webp", ".gif"}

ext = Path(original_filename).suffix or ".jpg"
if ext.lower() not in ALLOWED_EXTS:
    ext = ".jpg"  # 不允许的扩展名强制改为 .jpg
```

### device_id 格式验证

```python
# api/photos.py
_DEVICE_ID_RE = re.compile(r'^[a-zA-Z0-9_.\-]{1,256}$')

def _validate_device_id(device_id: str):
    if device_id and not _DEVICE_ID_RE.match(device_id):
        raise HTTPException(status_code=400, detail="Invalid device_id format")
```

### conversation_id 格式验证

```python
# api/chat.py
_CONV_ID_RE = re.compile(r'^[a-f0-9]{1,64}$')

def _validate_conversation_id(conv_id: str):
    if conv_id and not _CONV_ID_RE.match(conv_id):
        raise HTTPException(status_code=400, detail="Invalid conversation_id format")
```

## 路径遍历防护

文件访问路径经过严格验证，防止路径遍历攻击：

```python
# api/photos.py
def _resolve_path(path_str: str, photos_dir: Path) -> str:
    resolved = Path(path_str).resolve()
    if not resolved.is_relative_to(photos_dir.resolve()):
        raise HTTPException(status_code=403, detail="Path outside photos directory")
    return str(resolved)
```

文件存储时也进行路径验证：

```python
# services/storage.py
resolved = Path(original_path).resolve()
if not str(resolved).startswith(str(settings.photos_dir.resolve())):
    raise ValueError("Path traversal detected")
```

删除文件时同样检查路径：

```python
# api/photos.py - delete_photo()
for p in [photo.original_path, photo.thumbnail_path]:
    if p and os.path.exists(p):
        resolved = Path(p).resolve()
        if resolved.is_relative_to(settings.photos_dir.resolve()):
            os.remove(resolved)
```

## 数据加密

### Fernet 加密

系统使用 `cryptography.Fernet` (AES-128-CBC + HMAC-SHA256) 对敏感数据进行加密：

```python
# services/encryption.py
from cryptography.fernet import Fernet

def encrypt_field(plaintext: str | None) -> str | None:
    f = _get_fernet()
    return f.encrypt(plaintext.encode("utf-8")).decode("utf-8")

def decrypt_field(ciphertext: str | None) -> str | None:
    f = _get_fernet()
    return f.decrypt(ciphertext.encode("utf-8")).decode("utf-8")
```

### 密钥管理

```python
def _get_or_create_key() -> str:
    # 1. 优先使用环境变量
    key = settings.encryption_key
    if key:
        return key

    # 2. 尝试从文件加载
    if _KEY_FILE.exists():
        return _KEY_FILE.read_text().strip()

    # 3. 自动生成并持久化
    key = Fernet.generate_key().decode("utf-8")
    _KEY_FILE.write_text(key)
    os.chmod(_KEY_FILE, 0o600)  # 仅所有者可读写
    return key
```

密钥文件存储在 `data/.encryption_key`，权限为 `0o600` (仅所有者可读写)。

### 加密的数据

| 数据 | 字段 | 说明 |
|------|------|------|
| 聊天消息 | `ChatMessage.encrypted_content` | 敏感对话内容 |
| 记忆条目 | `Memory.encrypted_content` | 用户记忆内容 |

### 透明解密

通过 `display_content` 属性实现透明解密：

```python
# models.py
class ChatMessage(Base):
    @property
    def display_content(self) -> str | None:
        if self.encrypted_content:
            from services.encryption import decrypt_field
            return decrypt_field(self.encrypted_content)
        return self.content
```

### 密钥轮换

支持密钥轮换，将所有加密数据用新密钥重新加密：

```python
# services/encryption.py - rotate_key()
def rotate_key(old_key: str, new_key: str):
    old_f = Fernet(old_key.encode())
    new_f = Fernet(new_key.encode())

    # 重新加密所有 ChatMessage
    messages = db.query(ChatMessage).filter(ChatMessage.encrypted_content.isnot(None)).all()
    for msg in messages:
        plain = old_f.decrypt(msg.encrypted_content.encode())
        msg.encrypted_content = new_f.encrypt(plain.encode()).decode()

    # 重新加密所有 Memory
    memories = db.query(Memory).filter(Memory.encrypted_content.isnot(None)).all()
    for mem in memories:
        plain = old_f.decrypt(mem.encrypted_content.encode())
        mem.encrypted_content = new_f.encrypt(plain.encode()).decode()

    db.commit()
    # 持久化新密钥
    _KEY_FILE.write_text(new_key)
    os.chmod(_KEY_FILE, 0o600)
```

## CORS 配置

```python
# main.py
_DEFAULT_CORS = ["http://localhost:3000", "http://localhost:5173", "http://localhost:8421"]

_cors_origins = (
    [o.strip() for o in settings.cors_origins.split(",") if o.strip()]
    if settings.cors_origins
    else _DEFAULT_CORS
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=_cors_origins,
    allow_credentials=False,
    allow_methods=["GET", "POST", "PUT", "DELETE", "OPTIONS"],
    allow_headers=["*"],
)
```

默认允许的来源：
- `http://localhost:3000` (生产 Web 前端)
- `http://localhost:5173` (Vite 开发服务器)
- `http://localhost:8421` (Backend 自身)

可通过 `EVATAR_CORS_ORIGINS` 环境变量自定义，逗号分隔。

## 数据保留

系统定期清理过期数据，默认保留 30 天：

```python
# services/retention.py
def cleanup_old_data(db, days=None):
    if days is None:
        days = settings.retention_days  # 默认 30

    cutoff = now - timedelta(days=days)

    # 清理照片及文件
    old_photos = db.query(Photo).filter(Photo.created_at < cutoff).all()
    for photo in old_photos:
        os.remove(photo.original_path)   # 删除原图
        os.remove(photo.thumbnail_path)  # 删除缩略图
        db.delete(photo)

    # 清理聊天消息
    db.query(ChatMessage).filter(ChatMessage.created_at < cutoff).delete()

    # 清理空会话
    # 清理动态文章
    db.query(Dynamic).filter(Dynamic.created_at < cutoff).delete()
```

保留任务每天执行一次，由调度器管理：

```python
# services/scheduler.py
RETENTION_INTERVAL = 86400  # 24 小时
```

## 安全检查清单

| 检查项 | 状态 | 说明 |
|--------|------|------|
| API Key 认证 | 已实现 | Bearer Token + 时间恒定比较 |
| SSRF 防护 | 已实现 | 多层 URL 验证 + DNS rebinding 检测 |
| 速率限制 | 已实现 | 10 req/min (聊天和推理端点) |
| 文件类型验证 | 已实现 | MIME 类型 + 扩展名白名单 |
| 文件大小限制 | 已实现 | 截图 50MB, 附件 20MB |
| 路径遍历防护 | 已实现 | `is_relative_to` 验证 |
| 输入格式验证 | 已实现 | device_id, conversation_id 正则校验 |
| 数据加密 | 已实现 | Fernet 加密敏感字段 |
| CORS 限制 | 已实现 | 白名单来源 |
| 数据保留 | 已实现 | 默认 30 天自动清理 |
| HTTPS 强制 | 已实现 | LLM Base URL 强制 HTTPS |
