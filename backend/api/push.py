"""Push notification API: device registration, listing, management."""

import logging
from datetime import datetime, timezone

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel
from sqlalchemy.orm import Session

from models import get_db, DeviceToken
from services.push import send_push, broadcast_push

logger = logging.getLogger("evatar.api.push")

router = APIRouter(prefix="/api/push", tags=["push"])


# ── Request models ──

class RegisterRequest(BaseModel):
    device_id: str
    token: str
    platform: str = "android"
    device_name: str = ""
    device_model: str = ""
    app_version: str = ""


class TestPushRequest(BaseModel):
    device_id: str


# ── Device registration ──

@router.post("/register")
def register_device(req: RegisterRequest, db: Session = Depends(get_db)):
    """Register or update a device. Called on every app launch."""
    now = datetime.now(timezone.utc).replace(tzinfo=None)

    existing = db.query(DeviceToken).filter(DeviceToken.device_id == req.device_id).first()
    if existing:
        existing.token = req.token
        existing.platform = req.platform
        existing.device_name = req.device_name or existing.device_name
        existing.device_model = req.device_model or existing.device_model
        existing.app_version = req.app_version or existing.app_version
        existing.last_seen = now
    else:
        device = DeviceToken(
            device_id=req.device_id,
            token=req.token,
            platform=req.platform,
            device_name=req.device_name,
            device_model=req.device_model,
            app_version=req.app_version,
            last_seen=now,
            created_at=now,
        )
        db.add(device)
    db.commit()
    logger.info(f"Device registered: {req.device_id} ({req.platform})")
    return {"message": "Device registered", "device_id": req.device_id}


# ── Device listing (for web dashboard) ──

@router.get("/devices")
def list_devices(db: Session = Depends(get_db)):
    """List all registered devices."""
    devices = db.query(DeviceToken).order_by(DeviceToken.last_seen.desc()).all()
    return {
        "devices": [
            {
                "id": d.id,
                "device_id": d.device_id,
                "device_name": d.device_name or d.device_id,
                "device_model": d.device_model,
                "platform": d.platform,
                "app_version": d.app_version,
                "last_seen": d.last_seen.isoformat() if d.last_seen else None,
                "created_at": d.created_at.isoformat() if d.created_at else None,
            }
            for d in devices
        ]
    }


@router.delete("/devices/{device_id}")
def remove_device(device_id: str, db: Session = Depends(get_db)):
    """Remove a registered device."""
    device = db.query(DeviceToken).filter(DeviceToken.device_id == device_id).first()
    if not device:
        raise HTTPException(status_code=404, detail="Device not found")
    db.delete(device)
    db.commit()
    return {"message": "Device removed"}


# ── Push operations ──

@router.post("/test")
async def test_push(req: TestPushRequest):
    """Send a test push to a specific device."""
    success = await send_push(
        device_id=req.device_id,
        title="Evatar 测试通知",
        body="这是一条测试推送通知。",
    )
    if success:
        return {"message": "Test notification sent"}
    raise HTTPException(status_code=500, detail="Failed to send push notification")


@router.post("/broadcast")
async def broadcast(title: str = "Evatar", body: str = "新消息"):
    """Broadcast a push to all registered devices."""
    count = await broadcast_push(title=title, body=body)
    return {"message": f"Pushed to {count} devices", "count": count}
