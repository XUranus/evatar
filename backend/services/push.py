"""Push notification service: broadcast to all registered devices."""

import logging
from datetime import datetime, timezone

import httpx

from config import settings
from models import SessionLocal, DeviceToken

logger = logging.getLogger("evatar.push")


async def broadcast_push(title: str, body: str, data: dict = None) -> int:
    """Send a push notification to ALL registered devices.

    Returns number of devices notified.
    """
    db = SessionLocal()
    try:
        devices = db.query(DeviceToken).all()
        if not devices:
            logger.info("No registered devices, skipping push")
            return 0

        success = 0
        for device in devices:
            ok = await _send_to_device(device, title, body, data)
            if ok:
                success += 1

        logger.info(f"Push broadcast: {success}/{len(devices)} devices notified")
        return success
    finally:
        db.close()


async def send_push(device_id: str, title: str, body: str, data: dict = None) -> bool:
    """Send a push notification to a specific device."""
    db = SessionLocal()
    try:
        device = db.query(DeviceToken).filter(DeviceToken.device_id == device_id).first()
        if not device:
            logger.warning(f"Device {device_id} not registered")
            return False
        return await _send_to_device(device, title, body, data)
    finally:
        db.close()


async def _send_to_device(device, title: str, body: str, data: dict = None) -> bool:
    """Send notification to a single device via webhook or FCM."""
    webhook_url = settings.push_webhook_url
    if not webhook_url:
        logger.info(f"Push webhook not configured, logging: device={device.device_id}, title={title}")
        return True

    payload = {
        "device_id": device.device_id,
        "device_name": device.device_name or device.device_id,
        "platform": device.platform,
        "token": device.token,
        "title": title,
        "body": body,
        "data": data or {},
    }

    try:
        async with httpx.AsyncClient(timeout=30.0) as client:
            resp = await client.post(webhook_url, json=payload)
            resp.raise_for_status()
        logger.info(f"Push sent to {device.device_id}: {title}")
        return True
    except Exception as e:
        logger.error(f"Push failed for {device.device_id}: {e}")
        return False
