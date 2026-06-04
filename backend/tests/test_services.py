"""Service-layer tests for the Evatar backend."""

import io
import json
from datetime import datetime, timedelta, timezone

import pytest
from PIL import Image

from models import (
    AnalysisStatus,
    Analysis,
    Memory,
    Photo,
)


# ---------------------------------------------------------------------------
# Utilities (services.utils)
# ---------------------------------------------------------------------------

class TestStripCodeFences:
    """Test the ``strip_code_fences`` helper."""

    def test_json_fences(self):
        from services.utils import strip_code_fences
        assert strip_code_fences('```json\n{"key": "value"}\n```') == '{"key": "value"}'

    def test_python_fences(self):
        from services.utils import strip_code_fences
        assert strip_code_fences('```python\nprint("hi")\n```') == 'print("hi")'

    def test_no_fences(self):
        from services.utils import strip_code_fences
        assert strip_code_fences('{"key": "value"}') == '{"key": "value"}'

    def test_empty_string(self):
        from services.utils import strip_code_fences
        assert strip_code_fences("") == ""

    def test_fences_with_surrounding_text(self):
        from services.utils import strip_code_fences
        result = strip_code_fences("```json\n[1,2,3]\n```")
        assert result == "[1,2,3]"

    def test_strip_only_opening_fence(self):
        from services.utils import strip_code_fences
        # Only an opening fence with no closing should still strip the fence line
        result = strip_code_fences("```\nraw text")
        assert "```" not in result


# ---------------------------------------------------------------------------
# Memory extraction (services.memory)
# ---------------------------------------------------------------------------

class TestMemoryExtraction:

    @pytest.mark.asyncio
    async def test_extract_memories_from_text(self, db_session, monkeypatch):
        from services.memory import extract_memories_from_text

        canned_memories = [
            {
                "content": "User prefers dark mode",
                "category": "preference",
                "memory_type": "long_term",
                "importance": 0.9,
            },
            {
                "content": "User mentioned an upcoming meeting on Friday",
                "category": "schedule",
                "memory_type": "short_term",
                "importance": 0.7,
            },
        ]

        async def _fake_llm(messages, tools=None, max_tokens=None, temperature=None):
            return {
                "content": json.dumps(canned_memories, ensure_ascii=False),
                "tool_calls": [],
            }

        monkeypatch.setattr("services.memory.call_llm", _fake_llm)

        saved = await extract_memories_from_text(
            text="I prefer dark mode and I have a meeting on Friday",
            source_type="chat",
            source_id="conv-test-1",
            device_id="test-device",
            db=db_session,
        )

        assert len(saved) == 2
        contents = [m["content"] for m in saved]
        assert "User prefers dark mode" in contents
        assert "User mentioned an upcoming meeting on Friday" in contents

        # Verify persisted in DB
        rows = db_session.query(Memory).all()
        assert len(rows) == 2
        long_term = [r for r in rows if r.memory_type == "long_term"]
        assert len(long_term) == 1
        assert long_term[0].category == "preference"

    @pytest.mark.asyncio
    async def test_extract_memories_empty_llm_response(self, db_session, monkeypatch):
        from services.memory import extract_memories_from_text

        async def _empty_llm(messages, tools=None, max_tokens=None, temperature=None):
            return {"content": "[]", "tool_calls": []}

        monkeypatch.setattr("services.memory.call_llm", _empty_llm)

        saved = await extract_memories_from_text(
            text="hello",
            source_type="chat",
            source_id="conv-2",
            device_id="dev-1",
            db=db_session,
        )
        assert saved == []
        assert db_session.query(Memory).count() == 0


# ---------------------------------------------------------------------------
# RAG search (services.rag)
# ---------------------------------------------------------------------------

class TestRAGSearch:

    def test_search_empty_db(self, db_session):
        from services.rag import search_screenshots
        assert search_screenshots(db_session, "anything") == []

    def test_search_blank_query(self, db_session):
        from services.rag import search_screenshots
        assert search_screenshots(db_session, "   ") == []

    def test_search_with_data(self, db_session):
        from services.rag import search_screenshots

        photo = Photo(
            filename="screenshot.png",
            original_path="/tmp/screenshot.png",
            file_size=2048,
            device_id="dev-1",
        )
        db_session.add(photo)
        db_session.flush()

        analysis = Analysis(
            photo_id=photo.id,
            status=AnalysisStatus.DONE,
            summary="A recipe for chocolate cake with ingredients and steps",
            app_name="CookingApp",
            content_category="webpage",
            intent="reference",
            entities='[{"type": "food", "value": "chocolate cake"}]',
            confidence=0.95,
        )
        db_session.add(analysis)
        db_session.commit()

        results = search_screenshots(db_session, "chocolate")
        assert len(results) >= 1
        assert any("chocolate" in r["summary"].lower() for r in results)

    def test_search_no_match(self, db_session):
        from services.rag import search_screenshots

        photo = Photo(
            filename="img.png",
            original_path="/tmp/img.png",
            file_size=100,
            device_id="dev-1",
        )
        db_session.add(photo)
        db_session.flush()

        analysis = Analysis(
            photo_id=photo.id,
            status=AnalysisStatus.DONE,
            summary="Weather forecast for tomorrow",
            app_name="WeatherApp",
            content_category="other",
            intent="ignore",
            entities="[]",
            confidence=0.5,
        )
        db_session.add(analysis)
        db_session.commit()

        results = search_screenshots(db_session, "blockchain cryptocurrency")
        assert results == []


# ---------------------------------------------------------------------------
# Storage (services.storage)
# ---------------------------------------------------------------------------

class TestStorageSave:

    def test_save_photo_creates_files(self, monkeypatch):
        from services.storage import save_photo
        import tempfile, os
        from pathlib import Path

        photos_dir = Path(tempfile.mkdtemp(prefix="evatar_storage_test_"))
        monkeypatch.setattr("services.storage.settings.photos_dir", photos_dir)

        img = Image.new("RGB", (20, 15), color="green")
        buf = io.BytesIO()
        img.save(buf, format="PNG")
        image_bytes = buf.getvalue()

        original_path, thumb_path, file_size, width, height = save_photo(
            image_bytes, "photo.png",
        )

        assert file_size == len(image_bytes)
        assert width == 20
        assert height == 15
        assert original_path.endswith(".png")
        assert thumb_path.endswith("_thumb.jpg")
        assert os.path.isfile(original_path)
        assert os.path.isfile(thumb_path)

    def test_save_photo_thumbnail_size(self, monkeypatch):
        from services.storage import save_photo
        import tempfile
        from pathlib import Path

        photos_dir = Path(tempfile.mkdtemp(prefix="evatar_thumb_test_"))
        monkeypatch.setattr("services.storage.settings.photos_dir", photos_dir)

        # Large image should be thumbnailed down
        img = Image.new("RGB", (1024, 768), color="yellow")
        buf = io.BytesIO()
        img.save(buf, format="PNG")
        image_bytes = buf.getvalue()

        _, thumb_path, _, _, _ = save_photo(image_bytes, "big.png")

        thumb = Image.open(thumb_path)
        assert thumb.width <= 512
        assert thumb.height <= 512


# ---------------------------------------------------------------------------
# Retention cleanup (services.retention)
# ---------------------------------------------------------------------------

class TestRetentionCleanup:

    def test_cleanup_old_data(self, db_session):
        from services.retention import cleanup_old_data

        now = datetime.now(timezone.utc).replace(tzinfo=None)
        old = now - timedelta(days=60)

        # Old photo
        photo = Photo(
            filename="old.png",
            original_path="/tmp/evatar_nonexistent_old.png",
            file_size=500,
            device_id="dev-old",
            created_at=old,
        )
        db_session.add(photo)
        db_session.flush()

        # Old analysis tied to the photo
        analysis = Analysis(
            photo_id=photo.id,
            status=AnalysisStatus.DONE,
            summary="Old analysis",
            created_at=old,
        )
        db_session.add(analysis)

        # Old conversation + messages
        from models import Conversation, ChatMessage
        conv = Conversation(id="old-conv", title="old", created_at=old)
        db_session.add(conv)
        db_session.flush()
        msg = ChatMessage(
            conversation_id="old-conv",
            role="user",
            content="old message",
            created_at=old,
        )
        db_session.add(msg)

        db_session.commit()

        counts = cleanup_old_data(db_session, days=30)

        assert counts["photos"] == 1
        # Analysis is cascade-deleted with the photo (via relationship cascade),
        # so the separate analysis cleanup may find 0 rows.
        assert counts["messages"] >= 1
        assert db_session.query(Photo).count() == 0
        assert db_session.query(Analysis).count() == 0

    def test_cleanup_preserves_recent_data(self, db_session):
        from services.retention import cleanup_old_data

        now = datetime.now(timezone.utc).replace(tzinfo=None)

        photo = Photo(
            filename="recent.png",
            original_path="/tmp/evatar_nonexistent_recent.png",
            file_size=300,
            device_id="dev-new",
            created_at=now,
        )
        db_session.add(photo)
        db_session.commit()

        counts = cleanup_old_data(db_session, days=30)

        assert counts["photos"] == 0
        assert db_session.query(Photo).count() == 1
