"""Memory service: extract, store, retrieve, and decay agent memories."""

import hashlib
import json
import logging
from datetime import datetime, timedelta, timezone

from sqlalchemy import desc
from sqlalchemy.orm import Session

from models import Memory
from services.llm import call_llm
from services.utils import strip_code_fences, clamp
from services.encryption import is_encryption_enabled, encrypt_field

logger = logging.getLogger("evatar.memory")

MEMORY_EXTRACT_PROMPT = """Extract memories from the content below. Return ONLY a JSON array, no other text.

Format:
[{"content":"memory text","category":"fact|people|project|finance|schedule|preference|interest|habit","memory_type":"short_term|long_term","importance":0.5}]

Rules:
- Names/companies → long_term, people
- Money/payments → long_term, finance
- Dates/deadlines → long_term, schedule
- Project info → long_term, project
- Only return [] if truly no info
- Return ONLY the JSON array, nothing else."""


async def extract_memories_from_text(
    text: str,
    source_type: str,
    source_id: str,
    device_id: str,
    db: Session,
) -> list[dict]:
    """Extract memories from text content using LLM."""
    try:
        result = await call_llm([
            {"role": "system", "content": MEMORY_EXTRACT_PROMPT},
            {"role": "user", "content": text[:6000]},
        ], temperature=0.2, max_tokens=2048)

        content = strip_code_fences(result["content"])

        # Extract JSON array from response (handle reasoning models that prepend text)
        import re
        json_match = re.search(r'\[.*\]', content, re.DOTALL)
        if json_match:
            content = json_match.group(0)

        entries = json.loads(content)
        if not isinstance(entries, list):
            return []

        saved = []
        now = datetime.now(timezone.utc).replace(tzinfo=None)
        for entry in entries:
            if not entry.get("content"):
                continue

            mem_type = entry.get("memory_type", "short_term")
            expires = now + timedelta(hours=48) if mem_type == "short_term" else None

            # Dedup: check with normalized content hash
            normalized = entry["content"].lower().strip().rstrip("。").rstrip(".")
            content_hash = hashlib.md5(normalized.encode("utf-8")).hexdigest()
            existing = db.query(Memory).filter(
                Memory.device_id == device_id,
                Memory.content_hash == content_hash,
            ).first()
            if existing:
                existing.access_count += 1
                existing.last_accessed = now
                continue

            importance = clamp(entry.get("importance", 0.5))

            mem_content = entry["content"]
            enc_content = None
            if is_encryption_enabled():
                enc_content = encrypt_field(mem_content)

            memory = Memory(
                content=mem_content,
                encrypted_content=enc_content,
                content_hash=content_hash,
                memory_type=mem_type,
                source_type=source_type,
                source_id=source_id,
                category=entry.get("category", "fact"),
                importance=importance,
                device_id=device_id,
                created_at=now,
                last_accessed=now,
                expires_at=expires,
            )
            db.add(memory)
            saved.append({"content": memory.content, "category": memory.category})

        db.commit()
        logger.info(f"Extracted {len(saved)} memories from {source_type} {source_id}")
        return saved

    except json.JSONDecodeError:
        logger.warning("Failed to parse memory extraction response as JSON")
        return []
    except Exception as e:
        logger.error(f"Memory extraction error: {e}", exc_info=True)
        return []


def get_relevant_memories(db: Session, device_id: str, limit: int = 10) -> list[dict]:
    """Get recent relevant memories for a device (short-term + long-term, pruned)."""
    now = datetime.now(timezone.utc).replace(tzinfo=None)

    # Get top memories by importance * recency, excluding expired
    memories = (
        db.query(Memory)
        .filter(Memory.device_id == device_id)
        .filter(
            (Memory.expires_at.is_(None)) | (Memory.expires_at >= now),
        )
        .order_by(desc(Memory.importance), desc(Memory.last_accessed))
        .limit(limit)
        .all()
    )

    return [
        {"id": m.id, "content": m.display_content, "category": m.category,
         "memory_type": m.memory_type, "importance": m.importance}
        for m in memories
    ]


def get_memories_as_context(db: Session, device_id: str, limit: int = 8) -> str:
    """Get memories formatted as context string for agent system prompt."""
    memories = get_relevant_memories(db, device_id, limit)
    if not memories:
        return ""

    lines = ["## 用户记忆"]
    for m in memories:
        tag = "📌" if m["memory_type"] == "long_term" else "⏱️"
        lines.append(f"- {tag} [{m['category']}] {m['content']}")
    return "\n".join(lines)


def decay_memories(db: Session):
    """Run memory decay: reduce importance of rarely-accessed long-term memories."""
    now = datetime.now(timezone.utc).replace(tzinfo=None)

    # Delete expired short-term
    deleted = db.query(Memory).filter(
        Memory.expires_at.isnot(None),
        Memory.expires_at < now,
    ).delete()

    # Decay long-term memories not accessed in 7 days
    week_ago = now - timedelta(days=7)
    stale = db.query(Memory).filter(
        Memory.memory_type == "long_term",
        Memory.last_accessed < week_ago,
    ).all()
    for m in stale:
        m.importance = max(0.1, m.importance * 0.9)

    db.commit()
    logger.info(f"Memory decay: deleted {deleted} expired, decayed {len(stale)} stale")
