import asyncio
import json
import logging
from datetime import datetime, timezone

from sqlalchemy.orm import Session

from models import Analysis, AnalysisStatus, Photo, SessionLocal
from services.llm import analyze_image

logger = logging.getLogger("evatar.pipeline")


async def process_photo(photo_id: int):
    """Run LLM analysis on a photo. Called as a background task."""
    db = SessionLocal()
    try:
        analysis = db.query(Analysis).filter(Analysis.photo_id == photo_id).first()
        if not analysis:
            logger.error(f"No analysis record for photo {photo_id}")
            return

        analysis.status = AnalysisStatus.PROCESSING
        db.commit()

        photo = db.query(Photo).filter(Photo.id == photo_id).first()
        if not photo:
            logger.error(f"Photo {photo_id} not found")
            return

        # Use original image for analysis
        image_path = photo.original_path

        try:
            result = await analyze_image(image_path)

            analysis.app_name = result.get("app_name", "unknown")
            analysis.content_category = result.get("content_category", "other")
            analysis.intent = result.get("intent", "ignore")
            analysis.summary = result.get("summary", "")
            analysis.entities = json.dumps(result.get("entities", []), ensure_ascii=False)
            analysis.confidence = result.get("confidence", 0.5)
            analysis.llm_response = json.dumps(result, ensure_ascii=False)
            analysis.status = AnalysisStatus.DONE
            analysis.completed_at = datetime.now(timezone.utc)
            logger.info(f"Photo {photo_id} analyzed: intent={analysis.intent}, category={analysis.content_category}")

        except Exception as e:
            analysis.status = AnalysisStatus.ERROR
            analysis.error_message = str(e)
            analysis.completed_at = datetime.now(timezone.utc)
            logger.error(f"Failed to analyze photo {photo_id}: {e}")

        db.commit()

    finally:
        db.close()


def enqueue_analysis(photo_id: int):
    """Schedule async LLM analysis for a photo."""
    asyncio.create_task(process_photo(photo_id))
