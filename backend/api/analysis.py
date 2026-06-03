from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session
from sqlalchemy import desc, func

from models import get_db, Photo, Analysis, AnalysisStatus
from services.pipeline import enqueue_analysis

router = APIRouter(prefix="/api", tags=["analysis"])


@router.get("/analysis")
async def list_analyses(
    page: int = 1,
    page_size: int = 20,
    status: str = None,
    intent: str = None,
    db: Session = Depends(get_db),
):
    """List all analyses with filters."""
    query = db.query(Analysis).join(Photo).order_by(desc(Analysis.created_at))

    if status:
        query = query.filter(Analysis.status == status)
    if intent:
        query = query.filter(Analysis.intent == intent)

    total = query.count()
    analyses = query.offset((page - 1) * page_size).limit(page_size).all()

    items = []
    for a in analyses:
        items.append({
            "id": a.id,
            "photo_id": a.photo_id,
            "status": a.status.value,
            "app_name": a.app_name,
            "content_category": a.content_category,
            "intent": a.intent,
            "summary": a.summary,
            "entities": a.entities,
            "confidence": a.confidence,
            "error_message": a.error_message,
            "created_at": a.created_at.isoformat() if a.created_at else None,
            "completed_at": a.completed_at.isoformat() if a.completed_at else None,
            "photo_filename": a.photo.filename if a.photo else None,
        })

    return {"total": total, "page": page, "page_size": page_size, "items": items}


@router.post("/analysis/{photo_id}/reprocess")
async def reprocess_photo(photo_id: int, db: Session = Depends(get_db)):
    """Re-trigger LLM analysis for a photo."""
    photo = db.query(Photo).filter(Photo.id == photo_id).first()
    if not photo:
        raise HTTPException(status_code=404, detail="Photo not found")

    analysis = photo.analysis
    if not analysis:
        analysis = Analysis(photo_id=photo_id)
        db.add(analysis)
        db.commit()
    else:
        analysis.status = AnalysisStatus.PENDING
        analysis.error_message = None
        db.commit()

    enqueue_analysis(photo_id)
    return {"message": "Reprocessing queued"}


@router.get("/stats")
async def get_stats(db: Session = Depends(get_db)):
    """Get dashboard statistics."""
    total_photos = db.query(func.count(Photo.id)).scalar()
    total_analyses = db.query(func.count(Analysis.id)).scalar()
    done = db.query(func.count(Analysis.id)).filter(Analysis.status == AnalysisStatus.DONE).scalar()
    pending = db.query(func.count(Analysis.id)).filter(Analysis.status == AnalysisStatus.PENDING).scalar()
    processing = db.query(func.count(Analysis.id)).filter(Analysis.status == AnalysisStatus.PROCESSING).scalar()
    errors = db.query(func.count(Analysis.id)).filter(Analysis.status == AnalysisStatus.ERROR).scalar()

    # Intent distribution
    intent_counts = {}
    rows = (
        db.query(Analysis.intent, func.count(Analysis.id))
        .filter(Analysis.status == AnalysisStatus.DONE)
        .group_by(Analysis.intent)
        .all()
    )
    for intent, count in rows:
        intent_counts[intent or "unknown"] = count

    # Category distribution
    category_counts = {}
    rows = (
        db.query(Analysis.content_category, func.count(Analysis.id))
        .filter(Analysis.status == AnalysisStatus.DONE)
        .group_by(Analysis.content_category)
        .all()
    )
    for cat, count in rows:
        category_counts[cat or "unknown"] = count

    return {
        "total_photos": total_photos,
        "total_analyses": total_analyses,
        "done": done,
        "pending": pending,
        "processing": processing,
        "errors": errors,
        "intent_distribution": intent_counts,
        "category_distribution": category_counts,
    }
