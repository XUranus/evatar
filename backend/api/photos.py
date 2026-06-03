from datetime import datetime, timezone
from pathlib import Path
from fastapi import APIRouter, Depends, File, UploadFile, Form, HTTPException, Query
from fastapi.responses import FileResponse
from pydantic import BaseModel
from sqlalchemy.orm import Session, joinedload
from sqlalchemy import desc, case
import os

from config import settings
from models import get_db, Photo, Analysis, AnalysisStatus, DeviceSyncState
from services.storage import save_photo
from services.pipeline import enqueue_analysis

router = APIRouter(prefix="/api/photos", tags=["photos"])


# ── Helpers ──

def _save_upload(
    file_bytes: bytes,
    filename: str,
    device_id: str,
    device_name: str,
    source_type: str,
    local_media_store_id: str,
    original_timestamp: str,
    mime_type: str,
    db: Session,
) -> dict:
    """Shared logic for single and batch upload. Returns result dict."""
    if len(file_bytes) > settings.max_upload_bytes:
        raise HTTPException(status_code=413, detail=f"File too large (max {settings.max_upload_bytes // 1024 // 1024}MB)")

    # Dedup
    if local_media_store_id:
        existing = db.query(Photo).filter(
            Photo.device_id == device_id,
            Photo.local_media_store_id == local_media_store_id,
        ).first()
        if existing:
            return {"id": existing.id, "filename": existing.filename, "dedup": True}

    original_path, thumb_path, file_size, width, height = save_photo(file_bytes, filename)

    ts = None
    if original_timestamp:
        try:
            ts = datetime.fromtimestamp(int(original_timestamp) / 1000, tz=timezone.utc)
        except (ValueError, OSError):
            pass

    photo = Photo(
        local_media_store_id=local_media_store_id or None,
        filename=filename, original_path=original_path, thumbnail_path=thumb_path,
        file_size=file_size, width=width, height=height, mime_type=mime_type,
        source_type=source_type, device_id=device_id, device_name=device_name or None,
        original_timestamp=ts,
    )
    db.add(photo)
    db.flush()

    analysis = Analysis(photo_id=photo.id, status=AnalysisStatus.PENDING)
    db.add(analysis)

    # Update sync state
    state = db.query(DeviceSyncState).filter(DeviceSyncState.device_id == device_id).first()
    now = datetime.now(timezone.utc)
    if not state:
        state = DeviceSyncState(device_id=device_id, device_name=device_name,
                                last_synced_timestamp=ts, last_sync_time=now, total_synced=1)
        db.add(state)
    else:
        state.last_sync_time = now
        state.total_synced = (state.total_synced or 0) + 1
        if device_name:
            state.device_name = device_name
        if ts and (state.last_synced_timestamp is None or ts > state.last_synced_timestamp):
            state.last_synced_timestamp = ts

    db.commit()
    enqueue_analysis(photo.id)

    return {"id": photo.id, "filename": photo.filename, "file_size": file_size, "dedup": False}


def _resolve_path(path_str: str, photos_dir: Path) -> str:
    """Validate that a file path is within photos_dir."""
    resolved = Path(path_str).resolve()
    if not str(resolved).startswith(str(photos_dir.resolve())):
        raise HTTPException(status_code=403, detail="Path outside photos directory")
    return str(resolved)


# ── Sync state ──

@router.get("/sync-state")
def get_sync_state(device_id: str, db: Session = Depends(get_db)):
    state = db.query(DeviceSyncState).filter(DeviceSyncState.device_id == device_id).first()
    return {
        "device_id": device_id,
        "last_synced_timestamp": state.last_synced_timestamp.isoformat() if state and state.last_synced_timestamp else None,
        "last_synced_ts_ms": int(state.last_synced_timestamp.timestamp() * 1000) if state and state.last_synced_timestamp else 0,
        "total_synced": state.total_synced if state else 0,
    }


# ── Upload ──

@router.post("/upload")
async def upload_photo(
    file: UploadFile = File(...),
    device_id: str = Form(default="unknown"),
    device_name: str = Form(default=""),
    source_type: str = Form(default="screenshot"),
    local_media_store_id: str = Form(default=""),
    original_timestamp: str = Form(default=""),
    mime_type: str = Form(default="image/jpeg"),
    db: Session = Depends(get_db),
):
    file_bytes = await file.read()
    if not file_bytes:
        raise HTTPException(status_code=400, detail="Empty file")

    result = _save_upload(
        file_bytes, file.filename or "unknown.jpg", device_id, device_name,
        source_type, local_media_store_id, original_timestamp, mime_type, db,
    )
    result["message"] = "Already exists (dedup)" if result.get("dedup") else "Uploaded"
    return result


@router.post("/upload-batch")
async def upload_batch(
    device_id: str = Form(...),
    device_name: str = Form(default=""),
    files: list[UploadFile] = File(...),
    timestamps: str = Form(default=""),
    local_ids: str = Form(default=""),
    mime_types: str = Form(default=""),
    db: Session = Depends(get_db),
):
    ts_list = timestamps.split(",") if timestamps else []
    id_list = local_ids.split(",") if local_ids else []
    mime_list = mime_types.split(",") if mime_types else []

    results = []
    for i, upload_file in enumerate(files):
        file_bytes = await upload_file.read()
        if not file_bytes:
            results.append({"filename": upload_file.filename, "error": "empty"})
            continue

        result = _save_upload(
            file_bytes, upload_file.filename or "unknown.jpg", device_id, device_name,
            "screenshot", id_list[i] if i < len(id_list) else "",
            ts_list[i] if i < len(ts_list) else "",
            mime_list[i] if i < len(mime_list) else "image/jpeg", db,
        )
        results.append(result)

    return {"uploaded": len(results), "results": results}


# ── List & detail ──

@router.get("")
async def list_photos(
    page: int = Query(1, ge=1),
    page_size: int = Query(20, ge=1, le=100),
    status: str = None,
    device_id: str = None,
    db: Session = Depends(get_db),
):
    sort_key = case((Photo.original_timestamp.isnot(None), Photo.original_timestamp), else_=Photo.created_at)
    query = db.query(Photo).options(joinedload(Photo.analysis)).order_by(desc(sort_key))

    if status:
        query = query.join(Analysis).filter(Analysis.status == status)
    if device_id:
        query = query.filter(Photo.device_id == device_id)

    total = query.count()
    photos = query.offset((page - 1) * page_size).limit(page_size).all()

    items = []
    for p in photos:
        a = p.analysis
        items.append({
            "id": p.id, "filename": p.filename, "file_size": p.file_size,
            "width": p.width, "height": p.height, "mime_type": p.mime_type,
            "source_type": p.source_type, "device_id": p.device_id,
            "device_name": p.device_name,
            "original_timestamp": p.original_timestamp.isoformat() if p.original_timestamp else None,
            "created_at": p.created_at.isoformat() if p.created_at else None,
            "analysis_status": a.status.value if a else None,
            "intent": a.intent if a else None,
            "summary": a.summary if a else None,
        })

    return {"total": total, "page": page, "page_size": page_size, "items": items}


@router.get("/devices")
async def list_devices(db: Session = Depends(get_db)):
    states = db.query(DeviceSyncState).all()
    return {"devices": [
        {"device_id": s.device_id, "device_name": s.device_name or s.device_id,
         "last_synced_timestamp": s.last_synced_timestamp.isoformat() if s.last_synced_timestamp else None,
         "last_sync_time": s.last_sync_time.isoformat() if s.last_sync_time else None,
         "total_synced": s.total_synced}
        for s in states
    ]}


@router.get("/{photo_id}")
async def get_photo(photo_id: int, db: Session = Depends(get_db)):
    photo = db.query(Photo).options(joinedload(Photo.analysis)).filter(Photo.id == photo_id).first()
    if not photo:
        raise HTTPException(status_code=404, detail="Photo not found")

    a = photo.analysis
    return {
        "id": photo.id, "local_media_store_id": photo.local_media_store_id,
        "filename": photo.filename, "original_path": photo.original_path,
        "thumbnail_path": photo.thumbnail_path, "file_size": photo.file_size,
        "width": photo.width, "height": photo.height, "mime_type": photo.mime_type,
        "source_type": photo.source_type, "device_id": photo.device_id,
        "device_name": photo.device_name,
        "original_timestamp": photo.original_timestamp.isoformat() if photo.original_timestamp else None,
        "created_at": photo.created_at.isoformat() if photo.created_at else None,
        "analysis": {
            "id": a.id, "status": a.status.value, "app_name": a.app_name,
            "content_category": a.content_category, "intent": a.intent,
            "summary": a.summary, "entities": a.entities, "confidence": a.confidence,
            "llm_response": a.llm_response, "error_message": a.error_message,
            "created_at": a.created_at.isoformat() if a.created_at else None,
            "completed_at": a.completed_at.isoformat() if a.completed_at else None,
        } if a else None,
    }


@router.get("/{photo_id}/image")
async def get_photo_image(photo_id: int, db: Session = Depends(get_db)):
    photo = db.query(Photo).filter(Photo.id == photo_id).first()
    if not photo:
        raise HTTPException(status_code=404, detail="Photo not found")
    safe_path = _resolve_path(photo.original_path, settings.photos_dir)
    return FileResponse(safe_path, media_type=photo.mime_type or "image/jpeg")


@router.get("/{photo_id}/thumbnail")
async def get_photo_thumbnail(photo_id: int, db: Session = Depends(get_db)):
    photo = db.query(Photo).filter(Photo.id == photo_id).first()
    if not photo or not photo.thumbnail_path:
        raise HTTPException(status_code=404, detail="Thumbnail not found")
    safe_path = _resolve_path(photo.thumbnail_path, settings.photos_dir)
    return FileResponse(safe_path, media_type="image/jpeg")


@router.delete("/{photo_id}")
async def delete_photo(photo_id: int, db: Session = Depends(get_db)):
    photo = db.query(Photo).filter(Photo.id == photo_id).first()
    if not photo:
        raise HTTPException(status_code=404, detail="Photo not found")

    for p in [photo.original_path, photo.thumbnail_path]:
        if p and os.path.exists(p):
            resolved = Path(p).resolve()
            if str(resolved).startswith(str(settings.photos_dir.resolve())):
                os.remove(resolved)

    db.delete(photo)
    db.commit()
    return {"message": "Deleted"}
