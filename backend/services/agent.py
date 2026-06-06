"""Agent service: LLM chat with RAG and web search tools."""

import asyncio
import json
import logging
from datetime import datetime, timezone
from sqlalchemy.orm import Session
from sqlalchemy.orm import joinedload

from models import Conversation, ChatMessage, SessionLocal
from config import settings
from services.llm import call_llm
from services.rag import search_screenshots
from services.search import web_search

logger = logging.getLogger("evatar.agent")

# Track background memory extraction tasks to prevent fire-and-forget
_memory_tasks: set[asyncio.Task] = set()
_memory_semaphore = asyncio.Semaphore(2)

SYSTEM_PROMPT = """你是 Evatar，一个智能个人助手。你拥有用户的手机截图知识库，包含用户截图的分析结果（聊天记录、网页、通知、金融信息等）。

## 工具使用策略

### search_knowledge — 单关键词搜索
- 用核心关键词搜索（如"股票"、"火车"、"招标"）
- 搜不到就换同义词重试（"股价"→"行情"→"金融"→"NVDA"）

### search_multi — 多关键词同时搜索（推荐）
- 一次传入多个相关关键词，自动合并去重
- 适合用户问题涉及多个主题时使用
- 例：问"出行"→ queries: ["火车", "高铁", "导航", "12306", "机票"]
- 例：问"财务"→ queries: ["支付", "捐赠", "转账", "银行", "红包"]
- 例：问"工作"→ queries: ["招标", "项目", "工程", "投标", "资质"]

### get_recent — 获取最近截图（无需搜索词）
- 返回最近N条截图分析结果
- 适合"我最近截了什么"、"帮我整理最近的内容"等宽泛问题

### web_search — 搜索互联网
- 需要实时信息、新闻等使用

## 搜索策略
1. 用户问题具体 → search_knowledge 用核心词
2. 用户问题宽泛 → search_multi 用多个相关词
3. 用户问"最近" → get_recent 先看近期内容
4. 第一次搜不到 → 换词重试，不要直接说"没找到"
5. 找到部分信息 → 告诉用户找到了什么，即使不完全匹配

## 回答风格
- 用中文回答，简洁有条理
- 用Markdown表格整理结构化数据
- 引用截图中的具体信息（时间、金额、人名等）
- 如果找到了相关信息，一定要展示出来"""

TOOLS = [
    {
        "type": "function",
        "function": {
            "name": "search_knowledge",
            "description": "搜索用户手机截图知识库。如果第一次没搜到，换关键词重试。",
            "parameters": {
                "type": "object",
                "properties": {"query": {"type": "string", "description": "搜索关键词，用空格分隔多个词"}},
                "required": ["query"]
            }
        }
    },
    {
        "type": "function",
        "function": {
            "name": "search_multi",
            "description": "同时用多个关键词搜索知识库，合并去重返回。适合一次查多个相关主题。",
            "parameters": {
                "type": "object",
                "properties": {
                    "queries": {
                        "type": "array",
                        "items": {"type": "string"},
                        "description": "搜索关键词列表，如 [\"股票\", \"火车票\", \"招标\"]"
                    }
                },
                "required": ["queries"]
            }
        }
    },
    {
        "type": "function",
        "function": {
            "name": "get_recent",
            "description": "获取用户最近的截图分析结果，无需搜索词。适合宽泛问题。",
            "parameters": {
                "type": "object",
                "properties": {
                    "limit": {"type": "integer", "description": "返回数量，默认10"}
                }
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
    # Check LLM config first
    from services.llm import check_llm_config
    config_check = check_llm_config()
    if not config_check["ok"]:
        return {
            "role": "assistant",
            "content": f"⚠️ {config_check['error']}",
            "tool_calls": [],
            "error_type": "llm_not_configured",
        }

    conv = db.query(Conversation).filter(Conversation.id == conversation_id).first()
    if not conv:
        conv = Conversation(id=conversation_id, title=user_message[:50])
        db.add(conv)
        db.flush()

    user_msg = ChatMessage(conversation_id=conversation_id, role="user", content=user_message)
    db.add(user_msg)
    conv.updated_at = datetime.now(timezone.utc)
    db.flush()

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

        try:
            response = await call_llm(full_messages, tools=TOOLS)
        except Exception as e:
            error_msg = _format_llm_error(e)
            logger.error(f"LLM call failed: {error_msg}")
            return {
                "role": "assistant",
                "content": f"⚠️ {error_msg}",
                "tool_calls": [],
                "error_type": "llm_error",
            }

        assistant_content = response.get("content", "")
        tool_calls = response.get("tool_calls", [])

        assistant_msg = ChatMessage(
            conversation_id=conversation_id, role="assistant",
            content=assistant_content,
            tool_calls=json.dumps(tool_calls, ensure_ascii=False) if tool_calls else None,
        )
        db.add(assistant_msg)

        if not tool_calls:
            if conv.title == "新对话" and user_message:
                conv.title = user_message[:50]
            conv.updated_at = datetime.now(timezone.utc)
            db.commit()

            # Extract memories from this conversation turn (async, with own session)
            turn_text = f"用户: {user_message}\n助手: {assistant_content[:500]}"
            device = conv.device_id or ""
            task = asyncio.create_task(_extract_memories_async(turn_text, conversation_id, device))
            _memory_tasks.add(task)
            task.add_done_callback(_memory_tasks.discard)

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

        conv.updated_at = datetime.now(timezone.utc)
        db.commit()

    return {"role": "assistant", "content": "抱歉，处理超时，请重试。", "tool_calls": []}


async def _execute_tool(name: str, args: dict, db: Session) -> dict:
    if name == "search_knowledge":
        results = search_screenshots(db, args.get("query", ""), limit=8)
        return {"tool": "search_knowledge", "query": args.get("query"), "count": len(results), "results": results}
    elif name == "search_multi":
        queries = args.get("queries", [])
        seen_ids = set()
        all_results = []
        for q in queries[:5]:
            for r in search_screenshots(db, q, limit=5):
                rid = r.get("analysis_id")
                if rid not in seen_ids:
                    seen_ids.add(rid)
                    all_results.append(r)
        return {"tool": "search_multi", "queries": queries, "count": len(all_results), "results": all_results[:12]}
    elif name == "get_recent":
        from services.rag import get_recent_analyses
        limit = min(args.get("limit", 10), 20)
        results = get_recent_analyses(db, limit=limit)
        return {"tool": "get_recent", "count": len(results), "results": results}
    elif name == "web_search":
        results = await web_search(args.get("query", ""), num_results=5)
        return {"tool": "web_search", "query": args.get("query"), "results": results}
    return {"error": f"Unknown tool: {name}"}


def _build_history(db: Session, conversation_id: str) -> list[dict]:
    limit = settings.agent_history_limit
    messages = (
        db.query(ChatMessage)
        .filter(ChatMessage.conversation_id == conversation_id)
        .order_by(ChatMessage.created_at.desc())
        .limit(limit)
        .all()
    )
    messages.reverse()  # Restore chronological order

    history = []
    for msg in messages:
        entry: dict = {"role": msg.role, "content": msg.display_content or ""}
        if msg.role == "assistant" and msg.tool_calls:
            entry["tool_calls"] = json.loads(msg.tool_calls)
        if msg.role == "tool" and msg.tool_call_id:
            entry["tool_call_id"] = msg.tool_call_id
        history.append(entry)

    return history


async def _extract_memories_async(text: str, conversation_id: str, device_id: str):
    """Background task for memory extraction with its own DB session."""
    from models import SessionLocal as _SessionLocal
    from services.memory import extract_memories_from_text
    async with _memory_semaphore:
        db = _SessionLocal()
        try:
            await extract_memories_from_text(text, "chat", conversation_id, device_id, db)
        except Exception as e:
            logger.warning(f"Background memory extraction failed: {e}")
        finally:
            db.close()


def _format_llm_error(e: Exception) -> str:
    """Format LLM errors into user-friendly messages."""
    import httpx

    if isinstance(e, httpx.HTTPStatusError):
        status = e.response.status_code
        try:
            body = e.response.json()
            detail = body.get("error", {}).get("message", "") or body.get("detail", "")
        except Exception:
            detail = e.response.text[:200]

        if status == 401:
            return "LLM API Key 无效或已过期。请在设置页面检查 API Key。"
        elif status == 403:
            return "LLM API 访问被拒绝。请检查 API Key 权限。"
        elif status == 404:
            return f"LLM 模型未找到。请检查模型名称配置。({detail})"
        elif status == 429:
            return "LLM API 请求频率超限，请稍后重试。"
        elif status >= 500:
            return f"LLM 服务端错误 (HTTP {status})。请稍后重试。({detail})"
        else:
            return f"LLM 请求失败 (HTTP {status}): {detail}"
    elif isinstance(e, httpx.ConnectError):
        return "无法连接 LLM 服务。请检查网络和服务地址配置。"
    elif isinstance(e, httpx.TimeoutException):
        return "LLM 请求超时。请稍后重试或检查网络连接。"
    elif isinstance(e, httpx.ConnectTimeout):
        return "连接 LLM 服务超时。请检查服务地址是否正确。"
    else:
        return f"LLM 调用异常: {type(e).__name__}: {str(e)[:200]}"
