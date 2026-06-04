"""Background scheduler for periodic tasks: reasoning, memory decay, data retention."""

import asyncio
import logging
from datetime import datetime, timezone

from services.reasoner import run_reasoning_cycle
from services.memory import decay_memories
from services.retention import cleanup_old_data
from models import SessionLocal

logger = logging.getLogger("evatar.scheduler")

# Intervals in seconds
REASONING_INTERVAL = 3600     # 1 hour
MEMORY_DECAY_INTERVAL = 86400 # 24 hours
RETENTION_INTERVAL = 86400    # 24 hours (daily)

_running = False
_task = None
_lock = asyncio.Lock()


async def _scheduler_loop():
    """Main scheduler loop."""
    global _running
    _running = True
    now = datetime.now(timezone.utc).replace(tzinfo=None)
    last_reasoning = now
    last_decay = now
    last_retention = now

    logger.info("Scheduler started")
    while _running:
        now = datetime.now(timezone.utc).replace(tzinfo=None)

        # Reasoning cycle
        if (now - last_reasoning).total_seconds() >= REASONING_INTERVAL:
            logger.info("Starting reasoning cycle...")
            try:
                results = await run_reasoning_cycle()
                logger.info(f"Reasoning cycle produced {len(results)} articles")
            except Exception as e:
                logger.error(f"Reasoning cycle failed: {e}", exc_info=True)
            last_reasoning = now

        # Memory decay
        if (now - last_decay).total_seconds() >= MEMORY_DECAY_INTERVAL:
            db = SessionLocal()
            try:
                decay_memories(db)
            except Exception as e:
                logger.error(f"Memory decay failed: {e}", exc_info=True)
            finally:
                db.close()
            last_decay = now

        # Data retention cleanup
        if (now - last_retention).total_seconds() >= RETENTION_INTERVAL:
            db = SessionLocal()
            try:
                counts = cleanup_old_data(db)
                logger.info(f"Retention cleanup completed: {counts}")
            except Exception as e:
                logger.error(f"Retention cleanup failed: {e}", exc_info=True)
            finally:
                db.close()
            last_retention = now

        await asyncio.sleep(60)  # Check every minute


async def start_scheduler():
    """Start the background scheduler."""
    global _task
    async with _lock:
        if _task is not None:
            return
        _task = asyncio.create_task(_scheduler_loop())
        logger.info("Scheduler task created")


async def stop_scheduler():
    """Stop the scheduler gracefully."""
    global _running, _task
    async with _lock:
        _running = False
        if _task:
            _task.cancel()
            try:
                await _task
            except asyncio.CancelledError:
                pass
            _task = None
        logger.info("Scheduler stopped")


async def trigger_reasoning_now(device_id: str = None) -> list[dict]:
    """Manually trigger a reasoning cycle (for testing or on-demand)."""
    return await run_reasoning_cycle(device_id)
