"""Data retention service: cleanup old photos, analyses, chat messages, and dynamics."""

import logging
import os
from datetime import datetime, timedelta, timezone

from sqlalchemy.orm import Session

from models import Photo, Analysis, ChatMessage, Conversation, Dynamic
from config import settings

logger = logging.getLogger("evatar.retention")


def cleanup_old_data(db: Session, days: int = None) -> dict:
    """Delete photos, analyses, chat messages, and dynamics older than N days.

    For photos, also deletes files from disk.

    Returns a dict with counts of deleted items.
    """
    if days is None:
        days = settings.retention_days

    now = datetime.now(timezone.utc).replace(tzinfo=None)
    cutoff = now - timedelta(days=days)
    counts = {"photos": 0, "analyses": 0, "messages": 0, "conversations": 0, "dynamics": 0}

    # --- Photos (and their files from disk) ---
    old_photos = db.query(Photo).filter(Photo.created_at < cutoff).all()
    for photo in old_photos:
        for path in (photo.original_path, photo.thumbnail_path):
            if path and os.path.isfile(path):
                try:
                    os.remove(path)
                    logger.debug(f"Deleted file: {path}")
                except OSError as e:
                    logger.warning(f"Failed to delete file {path}: {e}")
        db.delete(photo)
    counts["photos"] = len(old_photos)

    # --- Analyses (cascade handled by FK, but clean up orphans explicitly) ---
    deleted_analyses = db.query(Analysis).filter(Analysis.created_at < cutoff).delete()
    counts["analyses"] = deleted_analyses

    # --- Chat messages ---
    deleted_messages = db.query(ChatMessage).filter(ChatMessage.created_at < cutoff).delete()
    counts["messages"] = deleted_messages

    # --- Empty conversations (no messages left) ---
    old_convs = db.query(Conversation).filter(Conversation.created_at < cutoff).all()
    for conv in old_convs:
        remaining = db.query(ChatMessage).filter(ChatMessage.conversation_id == conv.id).count()
        if remaining == 0:
            db.delete(conv)
            counts["conversations"] += 1

    # --- Dynamics ---
    deleted_dynamics = db.query(Dynamic).filter(Dynamic.created_at < cutoff).delete()
    counts["dynamics"] = deleted_dynamics

    db.commit()
    logger.info(f"Retention cleanup ({days}d): {counts}")
    return counts
