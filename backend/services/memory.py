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

MEMORY_EXTRACT_PROMPT = """从以下内容中提取值得记住的信息。尽可能多提取，不要遗漏。返回JSON数组。

每个记忆条目：
{
  "content": "记忆内容，简洁明确，包含关键细节（人名、时间、金额、地点）",
  "category": "preference(偏好) / fact(事实) / schedule(日程) / interest(兴趣) / habit(习惯) / people(人物关系) / project(项目) / finance(财务)",
  "memory_type": "short_term(48小时内有效) / long_term(长期保留)",
  "importance": 0.0到1.0
}

提取规则（尽量多提取）：
- 人名、联系方式、公司名称 → long_term, category=people
- 项目名称、招标信息、资质要求 → long_term, category=project
- 金额、支付、捐赠 → long_term, category=finance
- 日期、行程、截止时间 → long_term, category=schedule
- 用户偏好、习惯 → long_term, category=preference
- 当前讨论的话题 → short_term
- 截图中的关键事实 → long_term, category=fact
- 只有纯寒暄（你好/谢谢）才不提取

返回空数组 [] 仅当内容完全无信息价值。

只返回JSON数组。"""


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
