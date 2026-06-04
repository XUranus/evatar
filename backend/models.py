import enum
from datetime import datetime, timezone
from sqlalchemy import (
    Column, Integer, BigInteger, String, Float, DateTime, Text, Enum,
    Boolean, ForeignKey, create_engine, UniqueConstraint, Index
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
        Index("ix_photos_device_id", "device_id"),
        Index("ix_photos_original_timestamp", "original_timestamp"),
    )

    id = Column(Integer, primary_key=True, autoincrement=True)
    local_media_store_id = Column(String(128), nullable=True)
    filename = Column(String(512), nullable=False)
    original_path = Column(String(1024), nullable=False)
    thumbnail_path = Column(String(1024), nullable=True)
    file_size = Column(BigInteger, default=0)
    width = Column(Integer, nullable=True)
    height = Column(Integer, nullable=True)
    mime_type = Column(String(64), default="image/jpeg")
    source_type = Column(String(64), default="screenshot")
    device_id = Column(String(256), nullable=True)
    device_name = Column(String(256), nullable=True)
    original_timestamp = Column(DateTime, nullable=True)
    sync_time = Column(DateTime, default=lambda: datetime.now(timezone.utc))
    created_at = Column(DateTime, default=lambda: datetime.now(timezone.utc))

    analysis = relationship("Analysis", back_populates="photo", uselist=False)


class Analysis(Base):
    __tablename__ = "analyses"
    __table_args__ = (
        Index("ix_analyses_status", "status"),
        Index("ix_analyses_photo_id", "photo_id"),
    )

    id = Column(Integer, primary_key=True, autoincrement=True)
    photo_id = Column(Integer, ForeignKey("photos.id"), nullable=False)
    status = Column(Enum(AnalysisStatus), default=AnalysisStatus.PENDING)
    llm_response = Column(Text, nullable=True)
    app_name = Column(String(256), nullable=True)
    content_category = Column(String(256), nullable=True)
    intent = Column(String(256), nullable=True)
    summary = Column(Text, nullable=True)
    entities = Column(Text, nullable=True)
    confidence = Column(Float, nullable=True)
    error_message = Column(Text, nullable=True)
    created_at = Column(DateTime, default=lambda: datetime.now(timezone.utc))
    completed_at = Column(DateTime, nullable=True)

    photo = relationship("Photo", back_populates="analysis")


class DeviceSyncState(Base):
    __tablename__ = "device_sync_state"

    device_id = Column(String(256), primary_key=True)
    device_name = Column(String(256), nullable=True)
    last_synced_timestamp = Column(DateTime, nullable=True)
    last_sync_time = Column(DateTime, nullable=True)
    total_synced = Column(Integer, default=0)


class Conversation(Base):
    __tablename__ = "conversations"
    __table_args__ = (
        Index("ix_conversations_device_id", "device_id"),
        Index("ix_conversations_updated_at", "updated_at"),
    )

    id = Column(String(64), primary_key=True)
    title = Column(String(256), default="新对话")
    device_id = Column(String(256), nullable=True)
    created_at = Column(DateTime, default=lambda: datetime.now(timezone.utc))
    updated_at = Column(DateTime, default=lambda: datetime.now(timezone.utc),
                        onupdate=lambda: datetime.now(timezone.utc))

    messages = relationship("ChatMessage", back_populates="conversation",
                            order_by="ChatMessage.created_at", cascade="all, delete-orphan")


class ChatMessage(Base):
    __tablename__ = "chat_messages"
    __table_args__ = (
        Index("ix_chat_messages_conversation_id", "conversation_id"),
    )

    id = Column(Integer, primary_key=True, autoincrement=True)
    conversation_id = Column(String(64), ForeignKey("conversations.id"), nullable=False)
    role = Column(String(32), nullable=False)
    content = Column(Text, nullable=True)
    tool_name = Column(String(64), nullable=True)
    tool_call_id = Column(String(128), nullable=True)
    tool_calls = Column(Text, nullable=True)
    created_at = Column(DateTime, default=lambda: datetime.now(timezone.utc))

    conversation = relationship("Conversation", back_populates="messages")


class Skill(Base):
    __tablename__ = "skills"

    id = Column(String(64), primary_key=True)
    name = Column(String(128), nullable=False)
    description = Column(Text, nullable=True)
    system_prompt = Column(Text, nullable=False)
    icon = Column(String(16), default="⚡")
    enabled = Column(Boolean, default=True)
    created_at = Column(DateTime, default=lambda: datetime.now(timezone.utc))


class MCPServer(Base):
    __tablename__ = "mcp_servers"

    id = Column(String(64), primary_key=True)
    name = Column(String(128), nullable=False)
    url = Column(String(512), nullable=False)
    description = Column(Text, nullable=True)
    enabled = Column(Boolean, default=True)
    tools_json = Column(Text, nullable=True)
    created_at = Column(DateTime, default=lambda: datetime.now(timezone.utc))


class LLMConfig(Base):
    __tablename__ = "llm_config"

    id = Column(Integer, primary_key=True, default=1)
    provider = Column(String(64), default="mimo")
    base_url = Column(String(512), default="https://token-plan-cn.xiaomimimo.com/v1")
    api_key = Column(String(512), default="")
    model = Column(String(128), default="mimo-v2.5")
    max_context_tokens = Column(Integer, default=1048576)
    temperature = Column(Float, default=0.1)
    updated_at = Column(DateTime, default=lambda: datetime.now(timezone.utc),
                        onupdate=lambda: datetime.now(timezone.utc))


class Memory(Base):
    """Agent memory: short-term (48h expiry) and long-term (persistent)."""
    __tablename__ = "memories"
    __table_args__ = (
        UniqueConstraint("device_id", "content", name="uq_memory_device_content"),
        Index("ix_memories_device_type", "device_id", "memory_type"),
        Index("ix_memories_expires", "expires_at"),
    )

    id = Column(Integer, primary_key=True, autoincrement=True)
    content = Column(Text, nullable=False)
    memory_type = Column(String(32), nullable=False)     # short_term / long_term
    source_type = Column(String(32), nullable=False)     # chat / photo / inferred
    source_id = Column(String(128), nullable=True)       # conversation_id or photo_id
    category = Column(String(64), default="fact")        # preference / fact / schedule / interest / habit
    importance = Column(Float, default=0.5)              # 0-1
    access_count = Column(Integer, default=0)
    device_id = Column(String(256), nullable=True)
    created_at = Column(DateTime, default=lambda: datetime.now(timezone.utc))
    last_accessed = Column(DateTime, nullable=True)
    expires_at = Column(DateTime, nullable=True)         # null = never expires


class Dynamic(Base):
    """Articles/notes generated by the background intent reasoner."""
    __tablename__ = "dynamics"
    __table_args__ = (
        Index("ix_dynamics_device_created", "device_id", "created_at"),
    )

    id = Column(Integer, primary_key=True, autoincrement=True)
    title = Column(String(512), nullable=False)
    content = Column(Text, nullable=False)                # Markdown article
    summary = Column(Text, nullable=True)
    category = Column(String(64), default="note")         # insight / reminder / report / note
    source_photo_ids = Column(Text, nullable=True)        # JSON array
    source_conversation_ids = Column(Text, nullable=True) # JSON array
    source_memory_ids = Column(Text, nullable=True)       # JSON array
    confidence = Column(Float, default=0.5)
    is_read = Column(Boolean, default=False)
    is_pinned = Column(Boolean, default=False)
    device_id = Column(String(256), nullable=True)
    created_at = Column(DateTime, default=lambda: datetime.now(timezone.utc))


# Database setup
engine = create_engine(settings.db_url, echo=False, connect_args={"check_same_thread": False})
SessionLocal = sessionmaker(bind=engine, expire_on_commit=False)


def _enable_wal():
    """Enable WAL mode for better concurrent read/write performance."""
    import sqlite3
    try:
        conn = sqlite3.connect(settings.db_path)
        conn.execute("PRAGMA journal_mode=WAL")
        conn.execute("PRAGMA busy_timeout=5000")
        conn.close()
    except Exception:
        pass


def init_db():
    _enable_wal()
    Base.metadata.create_all(engine)


def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()
