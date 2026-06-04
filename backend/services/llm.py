"""LLM service: shared HTTP client for all LLM calls."""

import base64
import json
import logging

import httpx

from config import settings

logger = logging.getLogger("evatar.llm")


SYSTEM_PROMPT = """你是一个截图分析助手。用户会给你手机截图，你需要分析截图内容并返回结构化信息。

请严格按以下JSON格式返回，不要返回其他内容：
{
  "app_name": "截图来自的应用名称",
  "content_category": "chat / webpage / notification / social_media / finance / education / shopping / entertainment / tool / other",
  "intent": "reminder / research / reference / note / ignore",
  "relevance": "high / medium / low",
  "summary": "用中文简洁总结截图内容，2-3句话",
  "entities": [{"type": "类型", "value": "具体值"}],
  "confidence": 0.0到1.0
}

分析规则：
1. 包含明确时间/日期/截止时间 → intent=reminder
2. 知识/教程/研报 → intent=research 或 reference
3. 普通聊天或无意义内容 → intent=ignore
4. entities只提取有实际意义的信息
5. 如果截图是锁屏、桌面壁纸、广告、应用商店推荐等无信息内容，relevance设为low，intent设为ignore，confidence设为0.2以下

不同类型截图的分析指导：
- 聊天截图(chat)：提取对话主题、关键人物、重要约定
- 金融截图(finance)：提取金额、账户、交易类型
- 通知截图(notification)：提取通知来源、关键提醒
- 网页截图(webpage)：提取文章主题、关键信息、URL
- 社交媒体(social_media)：提取发布者、话题、互动内容"""


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

    async with httpx.AsyncClient(timeout=180.0) as client:
        resp = await client.post(
            f"{llm['base_url']}/chat/completions",
            json=payload,
            headers={
                "Authorization": f"Bearer {llm['api_key']}",
                "Content-Type": "application/json",
            },
        )
        resp.raise_for_status()
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
    """Read image file and return (base64_data, mime_type)."""
    with open(image_path, "rb") as f:
        b64 = base64.b64encode(f.read()).decode("utf-8")

    ext = image_path.lower().rsplit(".", 1)[-1] if "." in image_path else "jpg"
    mime_map = {"jpg": "image/jpeg", "jpeg": "image/jpeg", "png": "image/png",
                "webp": "image/webp", "gif": "image/gif"}
    return b64, mime_map.get(ext, "image/jpeg")
