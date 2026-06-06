"""Analysis pipeline: processes photos through the LLM."""

import asyncio
import json
import logging
import threading
from datetime import datetime, timezone

from sqlalchemy.orm import Session

from models import Analysis, AnalysisStatus, Photo, SessionLocal
from config import settings
from services.utils import strip_code_fences, format_llm_error as _format_analysis_error

logger = logging.getLogger("evatar.pipeline")

# Track running tasks to prevent fire-and-forget
_running_tasks: set[asyncio.Task] = set()


async def process_photo(photo_id: int):
    """Run LLM analysis on a photo."""
    db = SessionLocal()
    analysis = None
    try:
        analysis = db.query(Analysis).filter(Analysis.photo_id == photo_id).first()
        if not analysis:
            logger.error(f"No analysis record for photo {photo_id}")
            return

        # Idempotency: skip if already done
        if analysis.status == AnalysisStatus.DONE:
            logger.info(f"Photo {photo_id} already analyzed, skipping")
            return

        photo = db.query(Photo).filter(Photo.id == photo_id).first()
        if not photo:
            logger.error(f"Photo {photo_id} not found")
            return

        analysis.status = AnalysisStatus.PROCESSING
        db.flush()

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
        content = strip_code_fences(content)
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
        analysis.completed_at = datetime.now(timezone.utc).replace(tzinfo=None)
        logger.info(f"Photo {photo_id} analyzed: intent={analysis.intent}")

    except Exception as e:
        error_msg = _format_analysis_error(e)
        logger.error(f"Failed to analyze photo {photo_id}: {error_msg}", exc_info=True)
        if analysis:
            try:
                analysis.status = AnalysisStatus.ERROR
                analysis.error_message = error_msg[:500]
                analysis.completed_at = datetime.now(timezone.utc)
                db.commit()
            except Exception:
                db.rollback()
        else:
            db.rollback()
    else:
        db.commit()
        # Memory extraction in separate try/except so failures don't affect the analysis commit
        try:
            relevance = parsed.get("relevance", "high")
            confidence = parsed.get("confidence", 1)
            if not (relevance == "low" and confidence < 0.3):
                from services.memory import extract_memories_from_text
                mem_text = f"截图应用:{parsed.get('app_name','')} 分类:{parsed.get('content_category','')} 摘要:{parsed.get('summary','')} 实体:{parsed.get('entities','')}"
                mem_db = SessionLocal()
                try:
                    await extract_memories_from_text(mem_text, "photo", str(photo_id), photo.device_id or "", mem_db)
                finally:
                    mem_db.close()
        except Exception as me:
            logger.warning(f"Memory extraction from photo failed: {me}")
    finally:
        db.close()


_analysis_counter = 0
_counter_lock = threading.Lock()
_REASONING_TRIGGER_EVERY = 3  # Trigger reasoning after every N new analyses


def enqueue_analysis(photo_id: int):
    """Schedule async LLM analysis with proper task tracking."""
    try:
        loop = asyncio.get_running_loop()
    except RuntimeError:
        logger.warning(f"enqueue_analysis({photo_id}) called outside async context; analysis will not run")
        return
    task = loop.create_task(_safe_process(photo_id))
    _running_tasks.add(task)
    task.add_done_callback(_on_analysis_done)


def _on_analysis_done(task: asyncio.Task):
    """Callback when an analysis completes. Triggers reasoning periodically."""
    _running_tasks.discard(task)
    trigger = False
    with _counter_lock:
        global _analysis_counter
        _analysis_counter += 1
        if _analysis_counter >= _REASONING_TRIGGER_EVERY:
            _analysis_counter = 0
            trigger = True
    if trigger:
        asyncio.create_task(_trigger_reasoning())


async def _trigger_reasoning():
    """Trigger reasoning cycle after a batch of new analyses."""
    try:
        from services.reasoner import run_reasoning_cycle
        logger.info("Auto-triggering reasoning after new analyses")
        results = await run_reasoning_cycle()
        logger.info(f"Auto-reasoning produced {len(results)} articles")
    except Exception as e:
        logger.warning(f"Auto-reasoning failed: {e}")


_UNRECOVERABLE = (FileNotFoundError, PermissionError, ValueError, KeyError, TypeError, AttributeError, IndexError)


async def _safe_process(photo_id: int):
    """Wrapper with retry logic. Skips unrecoverable errors."""
    for attempt in range(3):
        try:
            await process_photo(photo_id)
            return
        except _UNRECOVERABLE as e:
            logger.error(f"Unrecoverable error for photo {photo_id}: {e}")
            return
        except Exception as e:
            if attempt < 2:
                wait = 2 ** attempt
                logger.warning(f"Retry {attempt + 1}/3 for photo {photo_id} in {wait}s: {e}")
                await asyncio.sleep(wait)
            else:
                logger.error(f"All retries exhausted for photo {photo_id}: {e}")


