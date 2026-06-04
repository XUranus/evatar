"""Background Intent Reasoner: analyzes recent content and generates articles/notes.

This is the core "think in the background" module. It runs periodically,
gathers context from recent photos, chats, and memories, then uses the LLM
to identify patterns and generate structured articles.
"""

import json
import logging
from datetime import datetime, timedelta, timezone

from sqlalchemy import desc
from sqlalchemy.orm import Session

from models import Dynamic, Photo, Analysis, ChatMessage, Conversation, Memory, SessionLocal
from services.llm import call_llm
from services.memory import get_memories_as_context
from services.utils import strip_code_fences

logger = logging.getLogger("evatar.reasoner")

REASONING_PROMPT = """你是一个智能个人助手的"意图推理"模块。你的任务是分析用户近期的活动，理解他们的意图和需求，生成有价值的文章或笔记。

## 输入
你会收到：
1. 用户近期截图分析结果（来自手机截图同步）
2. 用户近期的聊天记录摘要
3. 用户的记忆（偏好、习惯、兴趣等）

## 你的任务
分析这些信息，识别：
- **新兴主题/兴趣**：用户最近在关注什么
- **时间敏感事项**：即将到来的事件、截止日期
- **模式识别**：反复出现的行为或关注点
- **有价值的知识整理**：将零散信息整合成结构化笔记

## 输出格式
返回JSON数组，每个元素是一篇文章/笔记：
```json
[
  {
    "title": "文章标题",
    "summary": "一句话摘要",
    "content": "Markdown格式的完整文章，结构清晰，有标题、段落、列表等",
    "category": "insight(洞察) / reminder(提醒) / report(报告) / note(笔记)",
    "confidence": 0.0到1.0
  }
]
```

## 规则
- 只生成有价值的内容，不要为了生成而生成
- 如果没有值得写的内容，返回空数组 []
- 文章要有实际价值，不要泛泛而谈
- 引用具体的截图内容和聊天记录作为依据
- 用中文撰写
- category选择：有时间约束的用reminder，知识整理用note，趋势分析用insight，综合报告用report
- 每个理由最多生成3篇文章

只返回JSON数组，不要返回其他内容。"""


async def run_reasoning_cycle(device_id: str = None) -> list[dict]:
    """Run one reasoning cycle. Gathers context and generates articles."""
    db = SessionLocal()
    try:
        now = datetime.now(timezone.utc).replace(tzinfo=None)
        since = now - timedelta(hours=24)

        # 1. Gather recent photo analyses
        photo_query = (
            db.query(Analysis, Photo)
            .join(Photo, Analysis.photo_id == Photo.id)
            .filter(Analysis.status == "done", Photo.created_at >= since)
        )
        if device_id:
            photo_query = photo_query.filter(Photo.device_id == device_id)
        recent_analyses = photo_query.order_by(desc(Photo.created_at)).limit(20).all()

        if not recent_analyses:
            logger.info("No recent analyses, skipping reasoning cycle")
            return []

        # 2. Gather recent chat messages
        chat_query = (
            db.query(ChatMessage)
            .join(Conversation, ChatMessage.conversation_id == Conversation.id)
            .filter(ChatMessage.role == "user", ChatMessage.created_at >= since)
        )
        if device_id:
            chat_query = chat_query.filter(Conversation.device_id == device_id)
        recent_chats = chat_query.order_by(desc(ChatMessage.created_at)).limit(20).all()

        # 3. Gather memories
        memories_context = get_memories_as_context(db, device_id or "", limit=10)

        # 4. Build context
        context_parts = []

        if recent_analyses:
            context_parts.append("## 用户近期截图分析")
            for a, p in recent_analyses:
                ts = p.original_timestamp.strftime("%m-%d %H:%M") if p.original_timestamp else "?"
                context_parts.append(
                    f"- [{ts}] 应用:{a.app_name} 分类:{a.content_category} "
                    f"意图:{a.intent}\n  摘要:{a.summary}\n  实体:{a.entities}"
                )

        if recent_chats:
            context_parts.append("\n## 用户近期聊天")
            for msg in recent_chats:
                context_parts.append(f"- {msg.content[:200]}")

        if memories_context:
            context_parts.append(f"\n{memories_context}")

        full_context = "\n".join(context_parts)
        if len(full_context) < 50:
            logger.info("Insufficient context for reasoning")
            return []

        logger.info(f"Reasoning context: {len(recent_analyses)} photos, {len(recent_chats)} chats")

        # 5. Call LLM
        result = await call_llm([
            {"role": "system", "content": REASONING_PROMPT},
            {"role": "user", "content": full_context[:8000]},
        ], temperature=0.3, max_tokens=4096)

        content = strip_code_fences(result["content"])
        articles = json.loads(content)
        if not isinstance(articles, list):
            return []

        # 6. Save dynamics
        saved = []
        source_photo_ids = [str(p.id) for _, p in recent_analyses]
        source_conv_ids = list(set(m.conversation_id for m in recent_chats))

        for article in articles[:3]:  # Max 3 per cycle
            if not article.get("title") or not article.get("content"):
                continue

            dynamic = Dynamic(
                title=article["title"],
                content=article["content"],
                summary=article.get("summary", ""),
                category=article.get("category", "note"),
                source_photo_ids=json.dumps(source_photo_ids),
                source_conversation_ids=json.dumps(source_conv_ids),
                confidence=article.get("confidence", 0.5),
                device_id=device_id,
                created_at=now,
            )
            db.add(dynamic)
            saved.append({"title": dynamic.title, "category": dynamic.category})

        db.commit()
        logger.info(f"Generated {len(saved)} dynamics: {[s['title'] for s in saved]}")
        return saved

    except json.JSONDecodeError:
        logger.warning("Failed to parse reasoner response as JSON")
        return []
    except Exception as e:
        logger.error(f"Reasoning cycle error: {e}", exc_info=True)
        return []
    finally:
        db.close()
