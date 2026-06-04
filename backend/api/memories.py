"""Memories API: view and manage agent memories."""

from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy.orm import Session
from sqlalchemy import desc, func

from models import get_db, Memory

router = APIRouter(prefix="/api/memories", tags=["memories"])


@router.get("")
def list_memories(
    page: int = Query(1, ge=1),
    page_size: int = Query(50, ge=1, le=200),
    memory_type: str = None,
    category: str = None,
    device_id: str = None,
    db: Session = Depends(get_db),
):
    query = db.query(Memory).order_by(desc(Memory.importance), desc(Memory.created_at))
    if memory_type:
        query = query.filter(Memory.memory_type == memory_type)
    if category:
        query = query.filter(Memory.category == category)
    if device_id:
        query = query.filter(Memory.device_id == device_id)

    total = query.count()
    items = query.offset((page - 1) * page_size).limit(page_size).all()

    return {
        "total": total,
        "page": page,
        "page_size": page_size,
        "items": [
            {
                "id": m.id, "content": m.content, "memory_type": m.memory_type,
                "source_type": m.source_type, "category": m.category,
                "importance": m.importance, "access_count": m.access_count,
                "created_at": m.created_at.isoformat() if m.created_at else None,
                "expires_at": m.expires_at.isoformat() if m.expires_at else None,
            }
            for m in items
        ],
    }


@router.get("/stats")
def memory_stats(db: Session = Depends(get_db)):
    from datetime import datetime, timezone
    now = datetime.now(timezone.utc).replace(tzinfo=None)

    total = db.query(Memory).count()
    short_term = db.query(Memory).filter(Memory.memory_type == "short_term").count()
    long_term = db.query(Memory).filter(Memory.memory_type == "long_term").count()

    categories = {}
    for cat, count in db.query(Memory.category, func.count(Memory.id)).group_by(Memory.category).all():
        categories[cat or "unknown"] = count

    return {
        "total": total, "short_term": short_term, "long_term": long_term,
        "categories": categories,
    }


@router.delete("/{memory_id}")
def delete_memory(memory_id: int, db: Session = Depends(get_db)):
    memory = db.query(Memory).filter(Memory.id == memory_id).first()
    if not memory:
        raise HTTPException(status_code=404, detail="Memory not found")
    db.delete(memory)
    db.commit()
    return {"message": "Deleted"}
