"""Memory service: extract, store, retrieve, and decay agent memories."""

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

MEMORY_EXTRACT_PROMPT = """分析以下对话内容，提取值得记住的信息。返回JSON数组。

每个记忆条目：
{
  "content": "记忆内容，简洁明确",
  "category": "preference(偏好) / fact(事实) / schedule(日程) / interest(兴趣) / habit(习惯)",
  "memory_type": "short_term(48小时内有效) / long_term(长期保留)",
  "importance": 0.0到1.0
}

规则：
- 只提取对理解用户有价值的信息
- 用户明确的偏好、习惯、重要日期 → long_term
- 临时话题、当前讨论内容 → short_term
- 无意义的寒暄、重复内容 → 不提取
- 返回空数组 [] 如果没有值得记忆的内容

只返回JSON数组，不要返回其他内容。"""


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
            {"role": "user", "content": text[:4000]},
        ], temperature=0.2, max_tokens=1024)

        content = strip_code_fences(result["content"])
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

            # Dedup: check if similar memory already exists
            existing = db.query(Memory).filter(
                Memory.device_id == device_id,
                Memory.content == entry["content"],
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

    # Clean expired short-term memories
    db.query(Memory).filter(
        Memory.expires_at.isnot(None),
        Memory.expires_at < now,
    ).delete()
    db.commit()

    # Get top memories by importance * recency
    memories = (
        db.query(Memory)
        .filter(Memory.device_id == device_id)
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
