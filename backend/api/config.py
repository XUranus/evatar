import re
import ipaddress
import socket
import urllib.parse
from datetime import datetime, timezone
from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel
from sqlalchemy.orm import Session

from config import settings as app_settings
from models import get_db, LLMConfig

_PRIVATE_HOST = re.compile(
    r'(localhost|127\.0\.0\.1|0\.0\.0\.0|169\.254\.\d+\.\d+|'
    r'10\.\d+\.\d+\.\d+|192\.168\.\d+\.\d+|172\.(1[6-9]|2\d|3[01])\.\d+\.\d+)'
)

_PRIVATE_IPV6_PREFIXES = ("::1", "fe80:", "fc00:", "fd", "::ffff:")
_PRIVATE_IPV4_MAPPED = re.compile(r'::ffff:(\d+\.\d+\.\d+\.\d+)')

router = APIRouter(prefix="/api/config", tags=["config"])

PRESETS = {
    "mimo": {"provider": "mimo", "base_url": "https://token-plan-cn.xiaomimimo.com/v1", "model": "mimo-v2.5", "max_context_tokens": 1048576},
    "qwen": {"provider": "qwen", "base_url": "https://dashscope.aliyuncs.com/compatible-mode/v1", "model": "qwen-vl-max", "max_context_tokens": 131072},
    "openai": {"provider": "openai", "base_url": "https://api.openai.com/v1", "model": "gpt-4o", "max_context_tokens": 128000},
    "claude": {"provider": "claude", "base_url": "https://api.anthropic.com/v1", "model": "claude-sonnet-4-20250514", "max_context_tokens": 200000},
    "glm": {"provider": "glm", "base_url": "https://open.bigmodel.cn/api/paas/v4", "model": "glm-4v", "max_context_tokens": 128000},
    "kimi": {"provider": "kimi", "base_url": "https://api.moonshot.cn/v1", "model": "moonshot-v1-128k-vision-preview", "max_context_tokens": 128000},
    "deepseek": {"provider": "deepseek", "base_url": "https://api.deepseek.com/v1", "model": "deepseek-chat", "max_context_tokens": 65536},
}


class LLMConfigUpdate(BaseModel):
    provider: str | None = None
    base_url: str | None = None
    api_key: str | None = None
    model: str | None = None
    max_context_tokens: int | None = None
    temperature: float | None = None


def _get_or_create(db: Session) -> LLMConfig:
    cfg = db.query(LLMConfig).filter(LLMConfig.id == 1).first()
    if not cfg:
        cfg = LLMConfig(id=1)
        db.add(cfg)
        db.commit()
        db.refresh(cfg)
    return cfg


@router.get("/llm")
def get_llm_config(db: Session = Depends(get_db)):
    cfg = _get_or_create(db)
    return {
        "provider": cfg.provider,
        "base_url": cfg.base_url,
        "api_key_set": bool(cfg.api_key),
        "model": cfg.model,
        "max_context_tokens": cfg.max_context_tokens,
        "temperature": cfg.temperature,
    }


def _validate_base_url(url: str):
    """Reject SSRF-prone URLs. Raises HTTPException if the URL targets a private/loopback address."""
    if not url.startswith("https://"):
        # In dev mode, allow http:// for localhost addresses
        if app_settings.dev_mode and url.startswith("http://"):
            parsed = urllib.parse.urlparse(url)
            hostname = parsed.hostname or ""
            if not (_PRIVATE_HOST.search(hostname) or hostname in ("localhost", "127.0.0.1")):
                raise HTTPException(status_code=400, detail="base_url must use https:// (except localhost in dev mode)")
        else:
            raise HTTPException(status_code=400, detail="base_url must use https://")

    parsed = urllib.parse.urlparse(url)
    hostname = parsed.hostname or ""

    # Check IPv4-style private hosts via regex (covers hostname in full URL)
    if _PRIVATE_HOST.search(hostname):
        raise HTTPException(status_code=400, detail="base_url must not point to a private/localhost address")

    # Check IPv6 private ranges
    hostname_lower = hostname.lower()
    for prefix in _PRIVATE_IPV6_PREFIXES:
        if hostname_lower.startswith(prefix):
            # For ::ffff: mapped IPv4, extract and validate the IPv4 part
            m = _PRIVATE_IPV4_MAPPED.match(hostname_lower)
            if m and _PRIVATE_HOST.search(m.group(1)):
                raise HTTPException(status_code=400, detail="base_url must not point to a private/localhost address (IPv4-mapped IPv6)")
            if not m:
                raise HTTPException(status_code=400, detail="base_url must not point to a private/localhost address")

    # Validate with ipaddress module for bracketed IPv6 URLs like https://[::1]/...
    clean_host = hostname.strip("[]")
    try:
        addr = ipaddress.ip_address(clean_host)
        if addr.is_private or addr.is_loopback or addr.is_link_local:
            raise HTTPException(status_code=400, detail="base_url must not point to a private/localhost address")
    except ValueError:
        pass  # Not an IP address literal, continue to DNS resolution check

    # DNS rebinding defense: resolve hostname and verify ALL resolved IPs
    try:
        resolved_addrs = socket.getaddrinfo(hostname, 0, proto=socket.IPPROTO_TCP)
        for family, _, _, _, sockaddr in resolved_addrs:
            ip_str = sockaddr[0]
            try:
                addr = ipaddress.ip_address(ip_str)
                if addr.is_private or addr.is_loopback or addr.is_link_local:
                    raise HTTPException(
                        status_code=400,
                        detail="base_url resolves to a private/localhost address (DNS rebinding detected)",
                    )
            except ValueError:
                continue  # skip unparseable addresses
    except HTTPException:
        raise
    except socket.gaierror:
        raise HTTPException(status_code=400, detail="base_url hostname could not be resolved")
    except Exception:
        pass  # non-critical; other checks already passed


@router.put("/llm")
def update_llm_config(body: LLMConfigUpdate, db: Session = Depends(get_db)):
    if body.base_url is not None:
        _validate_base_url(body.base_url)
    cfg = _get_or_create(db)
    for field, value in body.model_dump(exclude_none=True).items():
        setattr(cfg, field, value)
    cfg.updated_at = datetime.now(timezone.utc)
    db.commit()
    from services.llm import invalidate_llm_config_cache
    invalidate_llm_config_cache()
    return {"message": "Updated", "provider": cfg.provider}


@router.get("/llm/presets")
def list_presets():
    return {"presets": PRESETS}


class PresetApplyRequest(BaseModel):
    api_key: str = ""


@router.post("/llm/presets/{name}/apply")
def apply_preset(name: str, body: PresetApplyRequest = PresetApplyRequest(), db: Session = Depends(get_db)):
    if name not in PRESETS:
        raise HTTPException(status_code=404, detail=f"Unknown preset: {name}")

    preset = PRESETS[name]
    cfg = _get_or_create(db)
    cfg.provider = preset["provider"]
    cfg.base_url = preset["base_url"]
    cfg.model = preset["model"]
    cfg.max_context_tokens = preset["max_context_tokens"]
    if body.api_key:
        cfg.api_key = body.api_key
    cfg.updated_at = datetime.now(timezone.utc)
    db.commit()
    from services.llm import invalidate_llm_config_cache
    invalidate_llm_config_cache()
    return {"message": f"Applied preset: {name}", "provider": cfg.provider}
