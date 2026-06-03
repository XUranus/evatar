from datetime import datetime, timezone
from fastapi import APIRouter, Depends, File, UploadFile, Form, HTTPException
from fastapi.responses import FileResponse
from pydantic import BaseModel
from sqlalchemy.orm import Session
from sqlalchemy import desc, case, func
import os

from models import get_db, Photo, Analysis, AnalysisStatus, DeviceSyncState
from services.storage import save_photo
from services.pipeline import enqueue_analysis

router = APIRouter(prefix="/api/photos", tags=["photos"])


# --- Sync state endpoint ---

@router.get("/sync-state")
def get_sync_state(device_id: str, db: Session = Depends(get_db)):
    """Return the latest synced timestamp for a device.
    The phone uses this to decide which new photos to send."""
    state = db.query(DeviceSyncState).filter(DeviceSyncState.device_id == device_id).first()
    return {
        "device_id": device_id,
        "last_synced_timestamp": state.last_synced_timestamp.isoformat() if state and state.last_synced_timestamp else None,
        "last_synced_ts_ms": int(state.last_synced_timestamp.timestamp() * 1000) if state and state.last_synced_timestamp else 0,
        "total_synced": state.total_synced if state else 0,
    }


# --- Upload ---

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
    """Upload a photo from the Android app. Dedup by (device_id, local_media_store_id)."""
    # Dedup check
    if local_media_store_id:
        existing = (
            db.query(Photo)
            .filter(
                Photo.device_id == device_id,
                Photo.local_media_store_id == local_media_store_id,
            )
            .first()
        )
        if existing:
            return {
                "id": existing.id,
                "filename": existing.filename,
                "file_size": existing.file_size,
                "message": "Already exists (dedup)",
                "dedup": True,
            }

    file_bytes = await file.read()
    if len(file_bytes) == 0:
        raise HTTPException(status_code=400, detail="Empty file")

    # Save to disk
    original_path, thumb_path, file_size, width, height = save_photo(
        file_bytes, file.filename or "unknown.jpg"
    )

    # Parse original timestamp
    ts = None
    if original_timestamp:
        try:
            ts = datetime.fromtimestamp(int(original_timestamp) / 1000, tz=timezone.utc)
        except (ValueError, OSError):
            pass

    # Save photo to DB
    photo = Photo(
        local_media_store_id=local_media_store_id or None,
        filename=file.filename or "unknown.jpg",
        original_path=original_path,
        thumbnail_path=thumb_path,
        file_size=file_size,
        width=width,
        height=height,
        mime_type=mime_type,
        source_type=source_type,
        device_id=device_id,
        device_name=device_name or None,
        original_timestamp=ts,
    )
    db.add(photo)
    db.commit()
    db.refresh(photo)

    # Create pending analysis
    analysis = Analysis(photo_id=photo.id, status=AnalysisStatus.PENDING)
    db.add(analysis)

    # Update device sync state
    _update_device_sync_state(db, device_id, device_name, ts)

    db.commit()

    # Trigger async LLM analysis
    enqueue_analysis(photo.id)

    return {
        "id": photo.id,
        "filename": photo.filename,
        "file_size": file_size,
        "message": "Photo uploaded and analysis queued",
        "dedup": False,
    }


def _update_device_sync_state(db: Session, device_id: str, device_name: str, photo_ts: datetime | None):
    """Update the device's last_synced_timestamp if this photo is newer."""
    state = db.query(DeviceSyncState).filter(DeviceSyncState.device_id == device_id).first()
    now = datetime.now(timezone.utc)

    if not state:
        state = DeviceSyncState(
            device_id=device_id,
            device_name=device_name or None,
            last_synced_timestamp=photo_ts,
            last_sync_time=now,
            total_synced=1,
        )
        db.add(state)
    else:
        state.last_sync_time = now
        state.total_synced = (state.total_synced or 0) + 1
        if device_name:
            state.device_name = device_name
        if photo_ts and (state.last_synced_timestamp is None or photo_ts > state.last_synced_timestamp):
            state.last_synced_timestamp = photo_ts


# --- Batch upload endpoint ---

@router.post("/upload-batch")
async def upload_batch(
    device_id: str = Form(...),
    device_name: str = Form(default=""),
    files: list[UploadFile] = File(...),
    timestamps: str = Form(default=""),        # comma-separated timestamps
    local_ids: str = Form(default=""),         # comma-separated MediaStore IDs
    mime_types: str = Form(default=""),         # comma-separated mime types
    db: Session = Depends(get_db),
):
    """Batch upload multiple photos at once. More efficient for initial sync."""
    ts_list = timestamps.split(",") if timestamps else []
    id_list = local_ids.split(",") if local_ids else []
    mime_list = mime_types.split(",") if mime_types else []

    results = []
    latest_ts = None

    for i, upload_file in enumerate(files):
        local_mid = id_list[i] if i < len(id_list) else ""
        file_ts = ts_list[i] if i < len(ts_list) else ""
        file_mime = mime_list[i] if i < len(mime_list) else "image/jpeg"

        # Dedup
        if local_mid:
            existing = db.query(Photo).filter(
                Photo.device_id == device_id,
                Photo.local_media_store_id == local_mid,
            ).first()
            if existing:
                results.append({"filename": upload_file.filename, "dedup": True, "id": existing.id})
                continue

        file_bytes = await upload_file.read()
        if len(file_bytes) == 0:
            results.append({"filename": upload_file.filename, "error": "empty"})
            continue

        original_path, thumb_path, file_size, width, height = save_photo(
            file_bytes, upload_file.filename or "unknown.jpg"
        )

        ts = None
        if file_ts:
            try:
                ts = datetime.fromtimestamp(int(file_ts) / 1000, tz=timezone.utc)
            except (ValueError, OSError):
                pass

        photo = Photo(
            local_media_store_id=local_mid or None,
            filename=upload_file.filename or "unknown.jpg",
            original_path=original_path,
            thumbnail_path=thumb_path,
            file_size=file_size,
            width=width,
            height=height,
            mime_type=file_mime,
            source_type="screenshot",
            device_id=device_id,
            device_name=device_name or None,
            original_timestamp=ts,
        )
        db.add(photo)
        db.flush()

        analysis = Analysis(photo_id=photo.id, status=AnalysisStatus.PENDING)
        db.add(analysis)

        if ts and (latest_ts is None or ts > latest_ts):
            latest_ts = ts

        results.append({"filename": upload_file.filename, "id": photo.id, "dedup": False})

    # Update sync state once for the whole batch
    _update_device_sync_state(db, device_id, device_name, latest_ts)
    db.commit()

    # Enqueue analysis for all new photos
    for r in results:
        if r.get("id") and not r.get("dedup") and not r.get("error"):
            enqueue_analysis(r["id"])

    return {"uploaded": len(results), "results": results}


# --- List & detail ---

@router.get("")
async def list_photos(
    page: int = 1,
    page_size: int = 20,
    status: str = None,
    device_id: str = None,
    db: Session = Depends(get_db),
):
    """List photos sorted by original_timestamp (phone creation order)."""
    sort_key = case((Photo.original_timestamp.isnot(None), Photo.original_timestamp), else_=Photo.created_at)
    query = db.query(Photo).order_by(desc(sort_key))

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
            "id": p.id,
            "filename": p.filename,
            "file_size": p.file_size,
            "width": p.width,
            "height": p.height,
            "mime_type": p.mime_type,
            "source_type": p.source_type,
            "device_id": p.device_id,
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
    """List all devices with sync info."""
    states = db.query(DeviceSyncState).all()
    devices = []
    for s in states:
        devices.append({
            "device_id": s.device_id,
            "device_name": s.device_name or s.device_id,
            "last_synced_timestamp": s.last_synced_timestamp.isoformat() if s.last_synced_timestamp else None,
            "last_sync_time": s.last_sync_time.isoformat() if s.last_sync_time else None,
            "total_synced": s.total_synced,
        })
    return {"devices": devices}


@router.get("/{photo_id}")
async def get_photo(photo_id: int, db: Session = Depends(get_db)):
    """Get photo detail with full analysis result."""
    photo = db.query(Photo).filter(Photo.id == photo_id).first()
    if not photo:
        raise HTTPException(status_code=404, detail="Photo not found")

    a = photo.analysis
    return {
        "id": photo.id,
        "local_media_store_id": photo.local_media_store_id,
        "filename": photo.filename,
        "original_path": photo.original_path,
        "thumbnail_path": photo.thumbnail_path,
        "file_size": photo.file_size,
        "width": photo.width,
        "height": photo.height,
        "mime_type": photo.mime_type,
        "source_type": photo.source_type,
        "device_id": photo.device_id,
        "device_name": photo.device_name,
        "original_timestamp": photo.original_timestamp.isoformat() if photo.original_timestamp else None,
        "created_at": photo.created_at.isoformat() if photo.created_at else None,
        "analysis": {
            "id": a.id,
            "status": a.status.value,
            "app_name": a.app_name,
            "content_category": a.content_category,
            "intent": a.intent,
            "summary": a.summary,
            "entities": a.entities,
            "confidence": a.confidence,
            "llm_response": a.llm_response,
            "error_message": a.error_message,
            "created_at": a.created_at.isoformat() if a.created_at else None,
            "completed_at": a.completed_at.isoformat() if a.completed_at else None,
        } if a else None,
    }


@router.get("/{photo_id}/image")
async def get_photo_image(photo_id: int, db: Session = Depends(get_db)):
    """Serve the original photo file."""
    photo = db.query(Photo).filter(Photo.id == photo_id).first()
    if not photo:
        raise HTTPException(status_code=404, detail="Photo not found")
    return FileResponse(photo.original_path, media_type=photo.mime_type or "image/jpeg")


@router.get("/{photo_id}/thumbnail")
async def get_photo_thumbnail(photo_id: int, db: Session = Depends(get_db)):
    """Serve the thumbnail file."""
    photo = db.query(Photo).filter(Photo.id == photo_id).first()
    if not photo or not photo.thumbnail_path:
        raise HTTPException(status_code=404, detail="Thumbnail not found")
    return FileResponse(photo.thumbnail_path, media_type="image/jpeg")


@router.delete("/{photo_id}")
async def delete_photo(photo_id: int, db: Session = Depends(get_db)):
    """Delete a photo and its analysis."""
    photo = db.query(Photo).filter(Photo.id == photo_id).first()
    if not photo:
        raise HTTPException(status_code=404, detail="Photo not found")

    if photo.original_path and os.path.exists(photo.original_path):
        os.remove(photo.original_path)
    if photo.thumbnail_path and os.path.exists(photo.thumbnail_path):
        os.remove(photo.thumbnail_path)

    db.delete(photo)
    db.commit()
    return {"message": "Deleted"}
