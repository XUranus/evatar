import enum
from datetime import datetime, timezone
from sqlalchemy import (
    Column, Integer, String, Float, DateTime, Text, Enum, ForeignKey, create_engine, UniqueConstraint
)
from sqlalchemy.orm import DeclarativeBase, Session, relationship, sessionmaker

from config import settings


class Base(DeclarativeBase):
    pass


class AnalysisStatus(str, enum.Enum):
    PENDING = "pending"
    PROCESSING = "processing"
    DONE = "done"
    ERROR = "error"


class Photo(Base):
    __tablename__ = "photos"
    __table_args__ = (
        UniqueConstraint("device_id", "local_media_store_id", name="uq_device_local_id"),
    )

    id = Column(Integer, primary_key=True, autoincrement=True)
    local_media_store_id = Column(String(128), nullable=True)  # MediaStore ID from phone
    filename = Column(String(512), nullable=False)
    original_path = Column(String(1024), nullable=False)
    thumbnail_path = Column(String(1024), nullable=True)
    file_size = Column(Integer, default=0)
    width = Column(Integer, nullable=True)
    height = Column(Integer, nullable=True)
    mime_type = Column(String(64), default="image/jpeg")
    source_type = Column(String(64), default="screenshot")  # screenshot, shared, camera
    device_id = Column(String(256), nullable=True)
    device_name = Column(String(256), nullable=True)        # Human-readable device name
    original_timestamp = Column(DateTime, nullable=True)    # Timestamp from the phone
    sync_time = Column(DateTime, default=lambda: datetime.now(timezone.utc))
    created_at = Column(DateTime, default=lambda: datetime.now(timezone.utc))

    analysis = relationship("Analysis", back_populates="photo", uselist=False)


class Analysis(Base):
    __tablename__ = "analyses"

    id = Column(Integer, primary_key=True, autoincrement=True)
    photo_id = Column(Integer, ForeignKey("photos.id"), nullable=False)
    status = Column(Enum(AnalysisStatus), default=AnalysisStatus.PENDING)
    llm_response = Column(Text, nullable=True)
    app_name = Column(String(256), nullable=True)        # which app the screenshot is from
    content_category = Column(String(256), nullable=True) # chat, webpage, notification, etc.
    intent = Column(String(256), nullable=True)           # reminder, research, reference, ignore
    summary = Column(Text, nullable=True)                 # Chinese summary
    entities = Column(Text, nullable=True)                # JSON: dates, names, amounts, etc.
    confidence = Column(Float, nullable=True)
    error_message = Column(Text, nullable=True)
    created_at = Column(DateTime, default=lambda: datetime.now(timezone.utc))
    completed_at = Column(DateTime, nullable=True)

    photo = relationship("Photo", back_populates="analysis")


class DeviceSyncState(Base):
    """Tracks the latest synced timestamp per device. Server is the source of truth."""
    __tablename__ = "device_sync_state"

    device_id = Column(String(256), primary_key=True)
    device_name = Column(String(256), nullable=True)
    last_synced_timestamp = Column(DateTime, nullable=True)  # latest original_timestamp successfully stored
    last_sync_time = Column(DateTime, nullable=True)          # when the last sync happened
    total_synced = Column(Integer, default=0)


class Conversation(Base):
    __tablename__ = "conversations"

    id = Column(String(64), primary_key=True)  # UUID
    title = Column(String(256), default="新对话")
    device_id = Column(String(256), nullable=True)  # which device created it
    created_at = Column(DateTime, default=lambda: datetime.now(timezone.utc))
    updated_at = Column(DateTime, default=lambda: datetime.now(timezone.utc))

    messages = relationship("ChatMessage", back_populates="conversation",
                            order_by="ChatMessage.created_at", cascade="all, delete-orphan")


class ChatMessage(Base):
    __tablename__ = "chat_messages"

    id = Column(Integer, primary_key=True, autoincrement=True)
    conversation_id = Column(String(64), ForeignKey("conversations.id"), nullable=False)
    role = Column(String(32), nullable=False)  # user, assistant, system, tool
    content = Column(Text, nullable=True)
    tool_name = Column(String(64), nullable=True)     # for tool messages
    tool_call_id = Column(String(128), nullable=True)  # for tool messages
    tool_calls = Column(Text, nullable=True)            # JSON: tool calls from assistant
    created_at = Column(DateTime, default=lambda: datetime.now(timezone.utc))

    conversation = relationship("Conversation", back_populates="messages")


class Skill(Base):
    """User-defined prompt templates / workflows."""
    __tablename__ = "skills"

    id = Column(String(64), primary_key=True)  # slug
    name = Column(String(128), nullable=False)
    description = Column(Text, nullable=True)
    system_prompt = Column(Text, nullable=False)  # the prompt injected when skill is active
    icon = Column(String(16), default="⚡")
    enabled = Column(Integer, default=1)
    created_at = Column(DateTime, default=lambda: datetime.now(timezone.utc))


class MCPServer(Base):
    """External MCP (Model Context Protocol) server connections."""
    __tablename__ = "mcp_servers"

    id = Column(String(64), primary_key=True)  # slug
    name = Column(String(128), nullable=False)
    url = Column(String(512), nullable=False)  # MCP server endpoint
    description = Column(Text, nullable=True)
    enabled = Column(Integer, default=1)
    tools_json = Column(Text, nullable=True)  # cached tool definitions from the server
    created_at = Column(DateTime, default=lambda: datetime.now(timezone.utc))


class LLMConfig(Base):
    """Singleton table (always id=1) storing LLM provider settings."""
    __tablename__ = "llm_config"

    id = Column(Integer, primary_key=True, default=1)
    provider = Column(String(64), default="mimo")          # mimo, openai, qwen, claude, glm, kimi, custom
    base_url = Column(String(512), default="https://token-plan-cn.xiaomimimo.com/v1")
    api_key = Column(String(512), default="")
    model = Column(String(128), default="mimo-v2.5")
    max_context_tokens = Column(Integer, default=1048576)   # 1M default
    temperature = Column(Float, default=0.1)
    updated_at = Column(DateTime, default=lambda: datetime.now(timezone.utc),
                        onupdate=lambda: datetime.now(timezone.utc))


# Database setup
engine = create_engine(settings.db_url, echo=False)
SessionLocal = sessionmaker(bind=engine)


def init_db():
    Base.metadata.create_all(engine)


def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()
