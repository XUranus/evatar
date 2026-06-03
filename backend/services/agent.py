"""Agent service: LLM chat with RAG and web search tools."""

import json
import logging
from sqlalchemy.orm import Session

from models import Conversation, ChatMessage, SessionLocal
from services.llm import _get_llm_config
from services.rag import search_screenshots, get_recent_analyses
from services.search import web_search

logger = logging.getLogger("evatar.agent")

SYSTEM_PROMPT = """你是 Evatar，一个智能个人助手。你的能力：

1. **知识库查询 (search_knowledge)**: 搜索用户手机截图中的信息。截图已通过 AI 分析并建立了索引，包括聊天记录、网页内容、通知、金融信息等。
2. **互联网搜索 (web_search)**: 搜索互联网获取最新信息。

使用规则：
- 当用户询问关于他们过去看过/截图过的内容时，使用 search_knowledge
- 当用户需要最新信息、实时数据、或你不确定的事实时，使用 web_search
- 可以同时使用多个工具
- 用中文回答，简洁有条理
- 如果找到了相关的截图信息，引用它们

你可以使用以下工具来帮助回答问题。"""

TOOLS = [
    {
        "type": "function",
        "function": {
            "name": "search_knowledge",
            "description": "搜索用户手机截图知识库。当用户问到他们截图过、看过的内容时使用。",
            "parameters": {
                "type": "object",
                "properties": {
                    "query": {
                        "type": "string",
                        "description": "搜索关键词"
                    }
                },
                "required": ["query"]
            }
        }
    },
    {
        "type": "function",
        "function": {
            "name": "web_search",
            "description": "搜索互联网获取最新信息。当需要实时数据、新闻、或你不确定的事实时使用。",
            "parameters": {
                "type": "object",
                "properties": {
                    "query": {
                        "type": "string",
                        "description": "搜索查询"
                    }
                },
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
    """Process a user message and return the assistant response.

    Args:
        skill_prompt: If set, prepended as a system instruction for this turn.
        file_info: If set, dict with {filename, mime, size, base64} for attached file.

    Returns: {"role": "assistant", "content": "...", "tool_calls": [...]}
    """
    # Get or create conversation
    conv = db.query(Conversation).filter(Conversation.id == conversation_id).first()
    if not conv:
        conv = Conversation(id=conversation_id, title=user_message[:50])
        db.add(conv)
        db.flush()

    # Save user message
    user_msg = ChatMessage(
        conversation_id=conversation_id,
        role="user",
        content=user_message,
    )
    db.add(user_msg)
    db.commit()

    # Build message history
    history = _build_history(db, conversation_id)

    # If file attached, replace the last user message with multimodal content
    if file_info and file_info.get("base64"):
        mime = file_info["mime"]
        b64 = file_info["base64"]
        if mime.startswith("image/"):
            # Image: use multimodal format
            user_content = [
                {"type": "text", "text": user_message or "请分析这张图片"},
                {"type": "image_url", "image_url": {"url": f"data:{mime};base64,{b64}"}},
            ]
        else:
            # Non-image: describe the file
            user_content = f"{user_message}\n\n[附件: {file_info['filename']} ({file_info['size']} bytes)]"

        # Update the last history entry (the user message we just added)
        if history and history[-1]["role"] == "user":
            history[-1]["content"] = user_content

    # Agent loop (max 5 tool call rounds)
    for round_num in range(5):
        response = await _call_llm_with_tools(history, skill_prompt=skill_prompt if round_num == 0 else None)
        assistant_content = response.get("content", "")
        tool_calls = response.get("tool_calls", [])

        # Save assistant message
        assistant_msg = ChatMessage(
            conversation_id=conversation_id,
            role="assistant",
            content=assistant_content,
            tool_calls=json.dumps(tool_calls, ensure_ascii=False) if tool_calls else None,
        )
        db.add(assistant_msg)
        db.commit()

        if not tool_calls:
            # No tool calls, we're done
            # Update conversation title if first message
            if conv.title == "新对话" and user_message:
                conv.title = user_message[:50]
            db.commit()
            return {
                "role": "assistant",
                "content": assistant_content,
                "tool_calls": [],
            }

        # Execute tool calls
        history.append({
            "role": "assistant",
            "content": assistant_content,
            "tool_calls": tool_calls,
        })

        for tc in tool_calls:
            func_name = tc["function"]["name"]
            try:
                args = json.loads(tc["function"]["arguments"])
            except json.JSONDecodeError:
                args = {}

            result = await _execute_tool(func_name, args, db)

            tool_msg = ChatMessage(
                conversation_id=conversation_id,
                role="tool",
                content=json.dumps(result, ensure_ascii=False),
                tool_name=func_name,
                tool_call_id=tc.get("id", ""),
            )
            db.add(tool_msg)

            history.append({
                "role": "tool",
                "content": json.dumps(result, ensure_ascii=False),
                "tool_call_id": tc.get("id", ""),
            })

        db.commit()

    # Max rounds reached
    return {"role": "assistant", "content": "抱歉，处理超时，请重试。", "tool_calls": []}


async def _call_llm_with_tools(messages: list[dict], skill_prompt: str | None = None) -> dict:
    """Call LLM with tool definitions."""
    llm = _get_llm_config()

    system_content = SYSTEM_PROMPT
    if skill_prompt:
        system_content += f"\n\n## 当前技能指令\n{skill_prompt}"

    full_messages = [{"role": "system", "content": system_content}] + messages

    payload = {
        "model": llm["model"],
        "messages": full_messages,
        "tools": TOOLS,
        "max_tokens": 4096,
        "temperature": llm["temperature"],
    }

    import httpx
    async with httpx.AsyncClient(timeout=120.0) as client:
        resp = await client.post(
            f"{llm['base_url']}/chat/completions",
            json=payload,
            headers={
                "Authorization": f"Bearer {llm['api_key']}",
                "Content-Type": "application/json",
            },
        )
        resp.raise_for_status()
        data = resp.json()

    message = data["choices"][0]["message"]
    content = message.get("content") or message.get("reasoning_content") or ""
    tool_calls = message.get("tool_calls") or []

    # Normalize tool calls
    normalized_calls = []
    for tc in tool_calls:
        normalized_calls.append({
            "id": tc.get("id", ""),
            "type": "function",
            "function": {
                "name": tc["function"]["name"],
                "arguments": tc["function"]["arguments"],
            }
        })

    return {"content": content, "tool_calls": normalized_calls}


async def _execute_tool(name: str, args: dict, db: Session) -> dict:
    """Execute a tool call and return the result."""
    if name == "search_knowledge":
        query = args.get("query", "")
        results = search_screenshots(db, query, limit=8)
        return {"tool": "search_knowledge", "query": query, "results": results}

    elif name == "web_search":
        query = args.get("query", "")
        results = await web_search(query, num_results=5)
        return {"tool": "web_search", "query": query, "results": results}

    return {"error": f"Unknown tool: {name}"}


def _build_history(db: Session, conversation_id: str) -> list[dict]:
    """Build message history for the LLM."""
    messages = (
        db.query(ChatMessage)
        .filter(ChatMessage.conversation_id == conversation_id)
        .order_by(ChatMessage.created_at)
        .all()
    )

    history = []
    for msg in messages:
        entry = {"role": msg.role, "content": msg.content or ""}
        if msg.role == "assistant" and msg.tool_calls:
            entry["tool_calls"] = json.loads(msg.tool_calls)
        if msg.role == "tool" and msg.tool_call_id:
            entry["tool_call_id"] = msg.tool_call_id
        history.append(entry)

    return history[-20:]  # Keep last 20 messages for context window
