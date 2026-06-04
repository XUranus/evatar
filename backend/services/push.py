"""Push notification service: send notifications via webhook or FCM."""

import logging

import httpx

from config import settings

logger = logging.getLogger("evatar.push")


async def send_push(device_id: str, title: str, body: str, data: dict = None) -> bool:
    """Send a push notification to a device.

    Currently implemented as a webhook call to a configurable URL (EVATAR_PUSH_WEBHOOK_URL).
    If no webhook URL is configured, just logs and returns True.

    Args:
        device_id: Target device identifier.
        title: Notification title.
        body: Notification body text.
        data: Optional extra data payload.

    Returns:
        True if sent (or no webhook configured), False on error.
    """
    webhook_url = settings.push_webhook_url
    if not webhook_url:
        logger.info(f"Push webhook not configured, logging notification: device={device_id}, title={title}, body={body}")
        return True

    payload = {
        "device_id": device_id,
        "title": title,
        "body": body,
        "data": data or {},
    }

    try:
        async with httpx.AsyncClient(timeout=30.0) as client:
            resp = await client.post(webhook_url, json=payload)
            resp.raise_for_status()
        logger.info(f"Push sent to device {device_id}: {title}")
        return True
    except Exception as e:
        logger.error(f"Push failed for device {device_id}: {e}", exc_info=True)
        return False
