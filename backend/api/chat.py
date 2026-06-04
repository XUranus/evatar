import uuid
import base64
from fastapi import APIRouter, Depends, HTTPException, File, UploadFile, Form, Query
from pydantic import BaseModel, Field
from sqlalchemy.orm import Session, joinedload
from sqlalchemy import desc, func, select

from models import get_db, Conversation, ChatMessage, Skill
from services.agent import chat

router = APIRouter(prefix="/api/chat", tags=["chat"])


class SendMessageRequest(BaseModel):
    message: str = Field(max_length=32768)
    conversation_id: str | None = None
    device_id: str | None = None
    skill_id: str | None = None


async def _resolve_skill(skill_id: str | None, db: Session) -> str | None:
    if not skill_id:
        return None
    skill = db.query(Skill).filter(Skill.id == skill_id).first()
    return skill.system_prompt if skill else None


def _ensure_conversation(conv_id: str, device_id: str | None, db: Session) -> str:
    conv = db.query(Conversation).filter(Conversation.id == conv_id).first()
    if not conv:
        conv = Conversation(id=conv_id)
        db.add(conv)
        db.flush()
    if device_id and not conv.device_id:
        conv.device_id = device_id
    return conv_id


@router.post("/send")
async def send_message(body: SendMessageRequest, db: Session = Depends(get_db)):
    conv_id = body.conversation_id or uuid.uuid4().hex[:16]
    _ensure_conversation(conv_id, body.device_id, db)
    skill_prompt = await _resolve_skill(body.skill_id, db)
    result = await chat(conv_id, body.message, db, skill_prompt=skill_prompt)
    return {"conversation_id": conv_id, "message": result}


@router.post("/send-with-file")
async def send_message_with_file(
    message: str = Form(default="", max_length=32768),
    conversation_id: str = Form(default=""),
    skill_id: str = Form(default=""),
    device_id: str = Form(default=""),
    file: UploadFile = File(default=None),
    db: Session = Depends(get_db),
):
    conv_id = conversation_id or uuid.uuid4().hex[:16]
    _ensure_conversation(conv_id, device_id or None, db)
    skill_prompt = await _resolve_skill(skill_id or None, db)

    file_info = None
    if file and file.filename:
        file_bytes = await file.read()
        if len(file_bytes) > 20 * 1024 * 1024:
            raise HTTPException(status_code=413, detail="File too large (max 20MB)")
        if file_bytes:
            file_info = {
                "filename": file.filename,
                "mime": file.content_type or "application/octet-stream",
                "size": len(file_bytes),
                "base64": base64.b64encode(file_bytes).decode(),
            }

    result = await chat(conv_id, message or "(发送了附件)", db,
                        skill_prompt=skill_prompt, file_info=file_info)
    return {"conversation_id": conv_id, "message": result}


@router.get("/conversations")
async def list_conversations(
    device_id: str = None,
    page: int = Query(1, ge=1),
    page_size: int = Query(50, ge=1, le=100),
    db: Session = Depends(get_db),
):
    # Subquery: message count per conversation
    msg_count_sq = (
        select(ChatMessage.conversation_id, func.count(ChatMessage.id).label("cnt"))
        .group_by(ChatMessage.conversation_id)
        .subquery()
    )
    # Subquery: last user message content per conversation
    last_msg_sq = (
        select(
            ChatMessage.conversation_id,
            ChatMessage.content,
            ChatMessage.encrypted_content,
            func.row_number().over(
                partition_by=ChatMessage.conversation_id,
                order_by=desc(ChatMessage.created_at),
            ).label("rn"),
        )
        .where(ChatMessage.role == "user")
        .subquery()
    )

    query = (
        db.query(
            Conversation,
            func.coalesce(msg_count_sq.c.cnt, 0).label("message_count"),
            func.coalesce(last_msg_sq.c.content, "").label("last_message"),
            last_msg_sq.c.encrypted_content.label("last_message_encrypted"),
        )
        .outerjoin(msg_count_sq, msg_count_sq.c.conversation_id == Conversation.id)
        .outerjoin(
            last_msg_sq,
            (last_msg_sq.c.conversation_id == Conversation.id) & (last_msg_sq.c.rn == 1),
        )
        .order_by(desc(Conversation.updated_at))
    )

    if device_id:
        query = query.filter(Conversation.device_id == device_id)

    rows = query.offset((page - 1) * page_size).limit(page_size).all()

    items = []
    for c, message_count, last_message, last_message_encrypted in rows:
        preview = last_message or ""
        if last_message_encrypted:
            from services.encryption import decrypt_field
            preview = decrypt_field(last_message_encrypted) or preview
        items.append({
            "id": c.id, "title": c.title, "device_id": c.device_id,
            "created_at": c.created_at.isoformat() if c.created_at else None,
            "updated_at": c.updated_at.isoformat() if c.updated_at else None,
            "message_count": message_count,
            "last_message": preview[:100],
        })

    return {"conversations": items}


@router.get("/conversations/{conversation_id}")
async def get_conversation(conversation_id: str, db: Session = Depends(get_db)):
    conv = db.query(Conversation).options(
        joinedload(Conversation.messages)
    ).filter(Conversation.id == conversation_id).first()
    if not conv:
        raise HTTPException(status_code=404, detail="Conversation not found")

    messages = []
    for m in conv.messages:
        msg = {
            "id": m.id, "role": m.role, "content": m.display_content,
            "created_at": m.created_at.isoformat() if m.created_at else None,
        }
        if m.tool_calls:
            msg["tool_calls"] = m.tool_calls
        if m.tool_name:
            msg["tool_name"] = m.tool_name
        messages.append(msg)

    return {"id": conv.id, "title": conv.title, "device_id": conv.device_id,
            "created_at": conv.created_at.isoformat() if conv.created_at else None,
            "messages": messages}


@router.delete("/conversations/{conversation_id}")
async def delete_conversation(conversation_id: str, db: Session = Depends(get_db)):
    conv = db.query(Conversation).filter(Conversation.id == conversation_id).first()
    if not conv:
        raise HTTPException(status_code=404, detail="Conversation not found")
    db.delete(conv)
    db.commit()
    return {"message": "Deleted"}


@router.get("/conversations/{conversation_id}/messages")
async def get_messages(
    conversation_id: str,
    after_id: int = None,
    db: Session = Depends(get_db),
):
    query = db.query(ChatMessage).filter(
        ChatMessage.conversation_id == conversation_id
    ).order_by(ChatMessage.created_at)

    if after_id:
        query = query.filter(ChatMessage.id > after_id)

    messages = query.all()
    return {"messages": [
        {"id": m.id, "role": m.role, "content": m.content,
         "tool_name": m.tool_name, "tool_calls": m.tool_calls,
         "created_at": m.created_at.isoformat() if m.created_at else None}
        for m in messages
    ]}
