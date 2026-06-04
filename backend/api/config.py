import re
from datetime import datetime, timezone
from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel
from sqlalchemy.orm import Session

from models import get_db, LLMConfig

_PRIVATE_HOST = re.compile(
    r'(localhost|127\.0\.0\.1|0\.0\.0\.0|169\.254\.\d+\.\d+|'
    r'10\.\d+\.\d+\.\d+|192\.168\.\d+\.\d+|172\.(1[6-9]|2\d|3[01])\.\d+\.\d+)'
)

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
    """Reject SSRF-prone URLs."""
    if not url.startswith("https://"):
        raise HTTPException(status_code=400, detail="base_url must use https://")
    if _PRIVATE_HOST.search(url):
        raise HTTPException(status_code=400, detail="base_url must not point to a private/localhost address")


@router.put("/llm")
def update_llm_config(body: LLMConfigUpdate, db: Session = Depends(get_db)):
    if body.base_url is not None:
        _validate_base_url(body.base_url)
    cfg = _get_or_create(db)
    for field, value in body.model_dump(exclude_none=True).items():
        setattr(cfg, field, value)
    cfg.updated_at = datetime.now(timezone.utc)
    db.commit()
    return {"message": "Updated", "provider": cfg.provider}


@router.get("/llm/presets")
def list_presets():
    return {"presets": PRESETS}


@router.post("/llm/presets/{name}/apply")
def apply_preset(name: str, api_key: str = "", db: Session = Depends(get_db)):
    if name not in PRESETS:
        raise HTTPException(status_code=404, detail=f"Unknown preset: {name}")

    preset = PRESETS[name]
    cfg = _get_or_create(db)
    cfg.provider = preset["provider"]
    cfg.base_url = preset["base_url"]
    cfg.model = preset["model"]
    cfg.max_context_tokens = preset["max_context_tokens"]
    if api_key:
        cfg.api_key = api_key
    cfg.updated_at = datetime.now(timezone.utc)
    db.commit()
    return {"message": f"Applied preset: {name}", "provider": cfg.provider}
