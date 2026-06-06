"""LLM service: shared HTTP client for all LLM calls."""

import base64
import json
import logging

import httpx

from config import settings

logger = logging.getLogger("evatar.llm")

# Module-level reusable async HTTP client (avoids per-request TCP/TLS overhead)
_client: httpx.AsyncClient | None = None


def _get_client() -> httpx.AsyncClient:
    global _client
    if _client is None or _client.is_closed:
        _client = httpx.AsyncClient(timeout=180.0)
    return _client


async def close_client():
    """Shutdown hook: close the shared HTTP client."""
    global _client
    if _client is not None and not _client.is_closed:
        await _client.aclose()
        _client = None


SYSTEM_PROMPT = """你是一个截图分析助手。分析手机截图内容，返回结构化JSON。

严格按此JSON格式返回，不要返回其他内容：
{
  "app_name": "应用名称（微信/支付宝/12306/高德地图/抖音等）",
  "content_category": "chat / webpage / notification / social_media / finance / education / shopping / entertainment / tool / other",
  "intent": "reminder / research / reference / note / ignore",
  "relevance": "high / medium / low",
  "summary": "用中文总结截图核心内容，2-3句话，包含关键细节",
  "entities": [
    {"type": "人名/公司/金额/日期/时间/地点/车次/航班/电话/链接/股票/项目/其他", "value": "具体值"}
  ],
  "confidence": 0.0到1.0
}

分析规则：
1. 包含明确时间/日期/截止时间 → intent=reminder
2. 知识/教程/研报/招标文件 → intent=research 或 reference
3. 聊天记录中有人名和具体内容 → intent=reference，entities提取人名
4. 支付/转账/捐赠 → intent=reference，entities提取金额和对方
5. 锁屏、桌面壁纸、广告、无意义截图 → relevance=low, intent=ignore, confidence<0.2

entities提取要求（尽量多提取）：
- 所有人名（联系人、发送者、乘车人）
- 所有金额（价格、支付金额、工资）
- 所有日期时间
- 所有地点、地址
- 所有电话号码
- 所有股票代码/名称
- 所有项目名称
- 所有车次/航班号"""


def _get_llm_config() -> dict:
    """Read LLM config from DB, falling back to env-based settings."""
    try:
        from models import SessionLocal, LLMConfig
        db = SessionLocal()
        try:
            cfg = db.query(LLMConfig).filter(LLMConfig.id == 1).first()
            if cfg and cfg.api_key:
                return {
                    "base_url": cfg.base_url,
                    "api_key": cfg.api_key,
                    "model": cfg.model,
                    "temperature": cfg.temperature,
                    "max_tokens": settings.llm_max_tokens,
                }
        finally:
            db.close()
    except Exception as e:
        logger.warning(f"Failed to read LLM config from DB, using env: {e}")

    return {
        "base_url": settings.llm_base_url,
        "api_key": settings.llm_api_key,
        "model": settings.llm_model,
        "temperature": settings.llm_temperature,
        "max_tokens": settings.llm_max_tokens,
    }


def check_llm_config() -> dict:
    """Check if LLM is properly configured. Returns {ok, error_message}."""
    llm = _get_llm_config()
    if not llm["api_key"]:
        return {"ok": False, "error": "LLM API Key 未配置。请在设置页面配置 LLM 服务商和 API Key。"}
    if not llm["base_url"]:
        return {"ok": False, "error": "LLM 服务地址未配置。请在设置页面选择一个 LLM 服务商。"}
    return {"ok": True, "error": None}


async def call_llm(
    messages: list[dict],
    tools: list[dict] | None = None,
    max_tokens: int | None = None,
    temperature: float | None = None,
) -> dict:
    """Shared LLM call. Returns {"content": str, "tool_calls": list}."""
    llm = _get_llm_config()

    payload: dict = {
        "model": llm["model"],
        "messages": messages,
        "max_tokens": max_tokens or llm["max_tokens"],
        "temperature": temperature if temperature is not None else llm["temperature"],
    }
    if tools:
        payload["tools"] = tools

    logger.info(f"LLM call: model={llm['model']}, msgs={len(messages)}")

    client = _get_client()
    resp = await client.post(
        f"{llm['base_url']}/chat/completions",
        json=payload,
        headers={
            "Authorization": f"Bearer {llm['api_key']}",
            "Content-Type": "application/json",
        },
    )
    try:
        resp.raise_for_status()
    except httpx.HTTPStatusError as e:
        logger.error(f"LLM API error: {e.response.status_code} - {e.response.text[:500]}")
        raise
    data = resp.json()

    message = data["choices"][0]["message"]
    content = message.get("content") or message.get("reasoning_content") or ""
    raw_tool_calls = message.get("tool_calls") or []

    normalized_calls = []
    for tc in raw_tool_calls:
        normalized_calls.append({
            "id": tc.get("id", ""),
            "type": "function",
            "function": {
                "name": tc["function"]["name"],
                "arguments": tc["function"]["arguments"],
            }
        })

    return {"content": content, "tool_calls": normalized_calls}


def encode_image_base64(image_path: str) -> tuple[str, str]:
    """Read image file and return (base64_data, mime_type).
    Resizes images larger than 2048px to fit within 2048x2048."""
    from PIL import Image
    import io

    ext = image_path.lower().rsplit(".", 1)[-1] if "." in image_path else "jpg"
    mime_map = {"jpg": "image/jpeg", "jpeg": "image/jpeg", "png": "image/png",
                "webp": "image/webp", "gif": "image/gif"}
    mime = mime_map.get(ext, "image/jpeg")

    img = Image.open(image_path)
    if img.width > 2048 or img.height > 2048:
        img.thumbnail((2048, 2048), Image.Resampling.LANCZOS)
        buf = io.BytesIO()
        save_fmt = "PNG" if ext == "png" else "JPEG"
        img.save(buf, format=save_fmt, quality=85)
        b64 = base64.b64encode(buf.getvalue()).decode("utf-8")
    else:
        with open(image_path, "rb") as f:
            b64 = base64.b64encode(f.read()).decode("utf-8")

    return b64, mime
