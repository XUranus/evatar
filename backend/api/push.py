"""Push notification API: register device tokens and send test notifications."""

import logging
from datetime import datetime, timezone

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel
from sqlalchemy.orm import Session

from models import get_db, DeviceToken
from services.push import send_push

logger = logging.getLogger("evatar.api.push")

router = APIRouter(prefix="/api/push", tags=["push"])


class RegisterRequest(BaseModel):
    device_id: str
    token: str
    platform: str = "android"


class TestPushRequest(BaseModel):
    device_id: str


@router.post("/register")
def register_device(req: RegisterRequest, db: Session = Depends(get_db)):
    """Register or update a device token for push notifications."""
    existing = db.query(DeviceToken).filter(DeviceToken.device_id == req.device_id).first()
    if existing:
        existing.token = req.token
        existing.platform = req.platform
    else:
        device = DeviceToken(
            device_id=req.device_id,
            token=req.token,
            platform=req.platform,
            created_at=datetime.now(timezone.utc).replace(tzinfo=None),
        )
        db.add(device)
    db.commit()
    logger.info(f"Device registered: {req.device_id} ({req.platform})")
    return {"message": "Device registered", "device_id": req.device_id}


@router.post("/test")
async def test_push(req: TestPushRequest):
    """Send a test push notification to a device."""
    success = await send_push(
        device_id=req.device_id,
        title="Evatar Test",
        body="This is a test push notification from Evatar.",
    )
    if success:
        return {"message": "Test notification sent", "device_id": req.device_id}
    raise HTTPException(status_code=500, detail="Failed to send push notification")
