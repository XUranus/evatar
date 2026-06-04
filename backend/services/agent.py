"""Agent service: LLM chat with RAG and web search tools."""

import asyncio
import json
import logging
from sqlalchemy.orm import Session
from sqlalchemy.orm import joinedload

from models import Conversation, ChatMessage, SessionLocal
from config import settings
from services.llm import call_llm
from services.rag import search_screenshots
from services.search import web_search

logger = logging.getLogger("evatar.agent")

SYSTEM_PROMPT = """你是 Evatar，一个智能个人助手。你的能力：

1. **知识库查询 (search_knowledge)**: 搜索用户手机截图中的信息。
2. **互联网搜索 (web_search)**: 搜索互联网获取最新信息。

使用规则：
- 用户问截图相关内容 → search_knowledge
- 需要实时信息/不确定的事实 → web_search
- 可同时使用多个工具
- 用中文回答，简洁有条理
- 引用找到的截图信息"""

TOOLS = [
    {
        "type": "function",
        "function": {
            "name": "search_knowledge",
            "description": "搜索用户手机截图知识库。",
            "parameters": {
                "type": "object",
                "properties": {"query": {"type": "string", "description": "搜索关键词"}},
                "required": ["query"]
            }
        }
    },
    {
        "type": "function",
        "function": {
            "name": "web_search",
            "description": "搜索互联网获取最新信息。",
            "parameters": {
                "type": "object",
                "properties": {"query": {"type": "string", "description": "搜索查询"}},
                "required": ["query"]
            }
        }
    }
]


async def chat(
    conversation_id: str,
    user_message: str,
    db: Session,
    skill_prompt: str | None = None,
    file_info: dict | None = None,
) -> dict:
    """Process a user message and return the assistant response."""
    conv = db.query(Conversation).filter(Conversation.id == conversation_id).first()
    if not conv:
        conv = Conversation(id=conversation_id, title=user_message[:50])
        db.add(conv)
        db.flush()

    user_msg = ChatMessage(conversation_id=conversation_id, role="user", content=user_message)
    db.add(user_msg)
    db.commit()

    history = _build_history(db, conversation_id)

    # Multimodal: if image attached, replace last user message content
    if file_info and file_info.get("base64"):
        mime = file_info["mime"]
        b64 = file_info["base64"]
        if mime.startswith("image/"):
            user_content = [
                {"type": "text", "text": user_message or "请分析这张图片"},
                {"type": "image_url", "image_url": {"url": f"data:{mime};base64,{b64}"}},
            ]
        else:
            user_content = f"{user_message}\n\n[附件: {file_info['filename']} ({file_info['size']} bytes)]"
        if history and history[-1]["role"] == "user":
            history[-1]["content"] = user_content

    # Agent loop
    for round_num in range(settings.agent_max_rounds):
        system_content = SYSTEM_PROMPT
        if skill_prompt and round_num == 0:
            system_content += f"\n\n## 当前技能指令\n{skill_prompt}"

        # Inject user memories into system prompt
        if round_num == 0:
            try:
                from services.memory import get_memories_as_context
                memories_ctx = get_memories_as_context(db, conv.device_id or "", limit=8)
                if memories_ctx:
                    system_content += f"\n\n{memories_ctx}"
            except Exception as e:
                logger.warning(f"Failed to load memories: {e}")

        full_messages = [{"role": "system", "content": system_content}] + history
        response = await call_llm(full_messages, tools=TOOLS)
        assistant_content = response.get("content", "")
        tool_calls = response.get("tool_calls", [])

        assistant_msg = ChatMessage(
            conversation_id=conversation_id, role="assistant",
            content=assistant_content,
            tool_calls=json.dumps(tool_calls, ensure_ascii=False) if tool_calls else None,
        )
        db.add(assistant_msg)
        db.commit()

        if not tool_calls:
            if conv.title == "新对话" and user_message:
                conv.title = user_message[:50]
            db.commit()

            # Extract memories from this conversation turn (async, with own session)
            turn_text = f"用户: {user_message}\n助手: {assistant_content[:500]}"
            device = conv.device_id or ""
            asyncio.create_task(_extract_memories_async(turn_text, conversation_id, device))

            return {"role": "assistant", "content": assistant_content, "tool_calls": []}

        history.append({"role": "assistant", "content": assistant_content, "tool_calls": tool_calls})

        for tc in tool_calls:
            func_name = tc["function"]["name"]
            try:
                args = json.loads(tc["function"]["arguments"])
            except json.JSONDecodeError:
                args = {}

            result = await _execute_tool(func_name, args, db)

            tool_msg = ChatMessage(
                conversation_id=conversation_id, role="tool",
                content=json.dumps(result, ensure_ascii=False),
                tool_name=func_name, tool_call_id=tc.get("id", ""),
            )
            db.add(tool_msg)
            history.append({
                "role": "tool",
                "content": json.dumps(result, ensure_ascii=False),
                "tool_call_id": tc.get("id", ""),
            })

        db.commit()

    return {"role": "assistant", "content": "抱歉，处理超时，请重试。", "tool_calls": []}


async def _execute_tool(name: str, args: dict, db: Session) -> dict:
    if name == "search_knowledge":
        results = search_screenshots(db, args.get("query", ""), limit=8)
        return {"tool": "search_knowledge", "query": args.get("query"), "results": results}
    elif name == "web_search":
        results = await web_search(args.get("query", ""), num_results=5)
        return {"tool": "web_search", "query": args.get("query"), "results": results}
    return {"error": f"Unknown tool: {name}"}


def _build_history(db: Session, conversation_id: str) -> list[dict]:
    messages = (
        db.query(ChatMessage)
        .filter(ChatMessage.conversation_id == conversation_id)
        .order_by(ChatMessage.created_at)
        .all()
    )

    history = []
    for msg in messages:
        entry: dict = {"role": msg.role, "content": msg.content or ""}
        if msg.role == "assistant" and msg.tool_calls:
            entry["tool_calls"] = json.loads(msg.tool_calls)
        if msg.role == "tool" and msg.tool_call_id:
            entry["tool_call_id"] = msg.tool_call_id
        history.append(entry)

    return history[-settings.agent_history_limit:]


async def _extract_memories_async(text: str, conversation_id: str, device_id: str):
    """Background task for memory extraction with its own DB session."""
    from models import SessionLocal as _SessionLocal
    from services.memory import extract_memories_from_text
    db = _SessionLocal()
    try:
        await extract_memories_from_text(text, "chat", conversation_id, device_id, db)
    except Exception as e:
        logger.warning(f"Background memory extraction failed: {e}")
    finally:
        db.close()
