import base64
import json
import logging

import httpx

from config import settings

logger = logging.getLogger("evatar.llm")


SYSTEM_PROMPT = """你是一个截图分析助手。用户会给你手机截图，你需要分析截图内容并返回结构化信息。

请严格按以下JSON格式返回，不要返回其他内容：
{
  "app_name": "截图来自的应用名称，如微信、浏览器、抖音等",
  "content_category": "内容分类：chat(聊天) / webpage(网页) / notification(通知) / social_media(社交媒体) / finance(金融) / education(教育) / shopping(购物) / entertainment(娱乐) / tool(工具) / other(其他)",
  "intent": "推断用户意图：reminder(需要提醒) / research(需要深入研究) / reference(收藏参考) / note(记录笔记) / ignore(无需处理)",
  "summary": "用中文简洁总结截图内容，2-3句话",
  "entities": [
    {"type": "日期/时间/人物/地点/金额/链接/其他", "value": "具体值"}
  ],
  "confidence": 0.0到1.0之间的置信度
}

分析规则：
1. 如果截图包含明确的时间/日期/截止时间，intent应为reminder
2. 如果截图是知识/教程/研报类内容，intent应为research或reference
3. 如果截图是普通聊天或无意义内容，intent应为ignore
4. entities只提取有实际意义的信息
5. summary要简洁，突出关键信息"""


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
                }
        finally:
            db.close()
    except Exception:
        pass
    # Fallback to env settings
    return {
        "base_url": settings.llm_base_url,
        "api_key": settings.llm_api_key,
        "model": settings.llm_model,
        "temperature": 0.1,
    }


async def analyze_image(image_path: str) -> dict:
    """Call the multimodal LLM to analyze a screenshot image."""
    llm = _get_llm_config()

    with open(image_path, "rb") as f:
        image_bytes = f.read()

    b64_image = base64.b64encode(image_bytes).decode("utf-8")

    # Detect mime type from extension
    ext = image_path.lower().rsplit(".", 1)[-1] if "." in image_path else "jpg"
    mime_map = {"jpg": "image/jpeg", "jpeg": "image/jpeg", "png": "image/png", "webp": "image/webp", "gif": "image/gif"}
    mime = mime_map.get(ext, "image/jpeg")

    payload = {
        "model": llm["model"],
        "messages": [
            {"role": "system", "content": SYSTEM_PROMPT},
            {
                "role": "user",
                "content": [
                    {"type": "text", "text": "请分析这张手机截图："},
                    {
                        "type": "image_url",
                        "image_url": {"url": f"data:{mime};base64,{b64_image}"},
                    },
                ],
            },
        ],
        "max_tokens": 4096,
        "temperature": llm["temperature"],
    }

    logger.info(f"Calling LLM: {llm['base_url']}, model={llm['model']}")

    async with httpx.AsyncClient(timeout=120.0) as client:
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
    # Some reasoning models put the response in reasoning_content with content empty
    content = message.get("content") or message.get("reasoning_content") or ""

    # Try to extract JSON from the response
    # LLM might wrap it in ```json ... ``` blocks
    content = content.strip()
    if content.startswith("```"):
        # Remove markdown code fences
        lines = content.split("\n")
        lines = [l for l in lines if not l.strip().startswith("```")]
        content = "\n".join(lines).strip()

    try:
        result = json.loads(content)
    except json.JSONDecodeError:
        # If JSON parsing fails, return a basic structure
        result = {
            "app_name": "unknown",
            "content_category": "other",
            "intent": "ignore",
            "summary": content[:500],  # Use raw response as summary
            "entities": [],
            "confidence": 0.3,
            "raw_response": content,
        }

    return result
