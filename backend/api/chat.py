import uuid
import base64
from datetime import datetime, timezone
from fastapi import APIRouter, Depends, HTTPException, File, UploadFile, Form
from pydantic import BaseModel
from sqlalchemy.orm import Session
from sqlalchemy import desc

from models import get_db, Conversation, ChatMessage, Skill
from services.agent import chat

router = APIRouter(prefix="/api/chat", tags=["chat"])


class SendMessageRequest(BaseModel):
    message: str
    conversation_id: str | None = None
    device_id: str | None = None
    skill_id: str | None = None


class ConversationResponse(BaseModel):
    id: str
    title: str
    device_id: str | None
    created_at: str
    updated_at: str
    message_count: int = 0
    last_message: str = ""


@router.post("/send")
async def send_message(body: SendMessageRequest, db: Session = Depends(get_db)):
    """Send a message and get AI response. Creates conversation if needed."""
    conv_id = body.conversation_id or uuid.uuid4().hex[:16]

    if body.device_id:
        conv = db.query(Conversation).filter(Conversation.id == conv_id).first()
        if conv and not conv.device_id:
            conv.device_id = body.device_id

    # Resolve skill system prompt
    skill_prompt = None
    if body.skill_id:
        skill = db.query(Skill).filter(Skill.id == body.skill_id).first()
        if skill:
            skill_prompt = skill.system_prompt

    result = await chat(conv_id, body.message, db, skill_prompt=skill_prompt)

    return {
        "conversation_id": conv_id,
        "message": result,
    }


@router.post("/send-with-file")
async def send_message_with_file(
    message: str = Form(default=""),
    conversation_id: str = Form(default=""),
    skill_id: str = Form(default=""),
    device_id: str = Form(default=""),
    file: UploadFile = File(default=None),
    db: Session = Depends(get_db),
):
    """Send a message with optional file attachment."""
    conv_id = conversation_id or uuid.uuid4().hex[:16]

    if device_id:
        conv = db.query(Conversation).filter(Conversation.id == conv_id).first()
        if conv and not conv.device_id:
            conv.device_id = device_id

    skill_prompt = None
    if skill_id:
        skill = db.query(Skill).filter(Skill.id == skill_id).first()
        if skill:
            skill_prompt = skill.system_prompt

    # Read file content and encode as base64 for the LLM
    file_info = None
    if file and file.filename:
        file_bytes = await file.read()
        if file_bytes:
            b64 = base64.b64encode(file_bytes).decode()
            mime = file.content_type or "application/octet-stream"
            file_info = {
                "filename": file.filename,
                "mime": mime,
                "size": len(file_bytes),
                "base64": b64,
            }

    result = await chat(conv_id, message or "(发送了附件)", db,
                        skill_prompt=skill_prompt, file_info=file_info)

    return {
        "conversation_id": conv_id,
        "message": result,
    }


@router.get("/conversations")
async def list_conversations(
    device_id: str = None,
    db: Session = Depends(get_db),
):
    """List all conversations, optionally filtered by device."""
    query = db.query(Conversation).order_by(desc(Conversation.updated_at))
    if device_id:
        query = query.filter(Conversation.device_id == device_id)

    convs = query.limit(50).all()

    items = []
    for c in convs:
        last_msg = ""
        msg_count = 0
        if c.messages:
            msg_count = len(c.messages)
            user_msgs = [m for m in c.messages if m.role == "user"]
            if user_msgs:
                last_msg = user_msgs[-1].content or ""

        items.append({
            "id": c.id,
            "title": c.title,
            "device_id": c.device_id,
            "created_at": c.created_at.isoformat() if c.created_at else None,
            "updated_at": c.updated_at.isoformat() if c.updated_at else None,
            "message_count": msg_count,
            "last_message": last_msg[:100],
        })

    return {"conversations": items}


@router.get("/conversations/{conversation_id}")
async def get_conversation(conversation_id: str, db: Session = Depends(get_db)):
    """Get a full conversation with all messages."""
    conv = db.query(Conversation).filter(Conversation.id == conversation_id).first()
    if not conv:
        raise HTTPException(status_code=404, detail="Conversation not found")

    messages = []
    for m in conv.messages:
        msg = {
            "id": m.id,
            "role": m.role,
            "content": m.content,
            "created_at": m.created_at.isoformat() if m.created_at else None,
        }
        if m.tool_calls:
            msg["tool_calls"] = m.tool_calls
        if m.tool_name:
            msg["tool_name"] = m.tool_name
        messages.append(msg)

    return {
        "id": conv.id,
        "title": conv.title,
        "device_id": conv.device_id,
        "created_at": conv.created_at.isoformat() if conv.created_at else None,
        "messages": messages,
    }


@router.delete("/conversations/{conversation_id}")
async def delete_conversation(conversation_id: str, db: Session = Depends(get_db)):
    """Delete a conversation."""
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
    """Get messages, optionally after a given message ID (for polling)."""
    query = (
        db.query(ChatMessage)
        .filter(ChatMessage.conversation_id == conversation_id)
        .order_by(ChatMessage.created_at)
    )
    if after_id:
        query = query.filter(ChatMessage.id > after_id)

    messages = query.all()
    return {
        "messages": [
            {
                "id": m.id,
                "role": m.role,
                "content": m.content,
                "tool_name": m.tool_name,
                "tool_calls": m.tool_calls,
                "created_at": m.created_at.isoformat() if m.created_at else None,
            }
            for m in messages
        ]
    }
