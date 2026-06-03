"""Analysis pipeline: processes photos through the LLM."""

import asyncio
import json
import logging
from datetime import datetime, timezone

from sqlalchemy.orm import Session

from models import Analysis, AnalysisStatus, Photo, SessionLocal
from config import settings

logger = logging.getLogger("evatar.pipeline")

# Track running tasks to prevent fire-and-forget
_running_tasks: set[asyncio.Task] = set()


async def process_photo(photo_id: int):
    """Run LLM analysis on a photo."""
    db = SessionLocal()
    try:
        analysis = db.query(Analysis).filter(Analysis.photo_id == photo_id).first()
        if not analysis:
            logger.error(f"No analysis record for photo {photo_id}")
            return

        photo = db.query(Photo).filter(Photo.id == photo_id).first()
        if not photo:
            logger.error(f"Photo {photo_id} not found")
            return

        analysis.status = AnalysisStatus.PROCESSING
        db.commit()

        from services.llm import call_llm, encode_image_base64, SYSTEM_PROMPT

        b64, mime = encode_image_base64(photo.original_path)

        messages = [
            {"role": "system", "content": SYSTEM_PROMPT},
            {
                "role": "user",
                "content": [
                    {"type": "text", "text": "请分析这张手机截图："},
                    {"type": "image_url", "image_url": {"url": f"data:{mime};base64,{b64}"}},
                ],
            },
        ]

        result = await call_llm(messages)
        content = result["content"]

        # Parse JSON from response
        content = content.strip()
        if content.startswith("```"):
            lines = content.split("\n")
            lines = [l for l in lines if not l.strip().startswith("```")]
            content = "\n".join(lines).strip()

        try:
            parsed = json.loads(content)
        except json.JSONDecodeError:
            parsed = {
                "app_name": "unknown", "content_category": "other", "intent": "ignore",
                "summary": content[:500], "entities": [], "confidence": 0.3,
                "raw_response": content,
            }

        analysis.app_name = parsed.get("app_name", "unknown")
        analysis.content_category = parsed.get("content_category", "other")
        analysis.intent = parsed.get("intent", "ignore")
        analysis.summary = parsed.get("summary", "")
        analysis.entities = json.dumps(parsed.get("entities", []), ensure_ascii=False)
        analysis.confidence = parsed.get("confidence", 0.5)
        analysis.llm_response = json.dumps(parsed, ensure_ascii=False)
        analysis.status = AnalysisStatus.DONE
        analysis.completed_at = datetime.now(timezone.utc)
        logger.info(f"Photo {photo_id} analyzed: intent={analysis.intent}")

    except Exception as e:
        logger.error(f"Failed to analyze photo {photo_id}: {e}", exc_info=True)
        if analysis:
            analysis.status = AnalysisStatus.ERROR
            analysis.error_message = str(e)[:500]
            analysis.completed_at = datetime.now(timezone.utc)
    finally:
        db.commit()
        db.close()


def enqueue_analysis(photo_id: int):
    """Schedule async LLM analysis with proper task tracking."""
    task = asyncio.create_task(_safe_process(photo_id))
    _running_tasks.add(task)
    task.add_done_callback(_running_tasks.discard)


async def _safe_process(photo_id: int):
    """Wrapper with retry logic."""
    for attempt in range(3):
        try:
            await process_photo(photo_id)
            return
        except Exception as e:
            if attempt < 2:
                wait = 2 ** attempt
                logger.warning(f"Retry {attempt + 1}/3 for photo {photo_id} in {wait}s: {e}")
                await asyncio.sleep(wait)
            else:
                logger.error(f"All retries exhausted for photo {photo_id}: {e}")
