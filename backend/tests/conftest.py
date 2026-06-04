"""Pytest configuration and shared fixtures for Evatar backend tests."""

import json
import os
import sys
import tempfile

# ---------------------------------------------------------------------------
# Environment variables MUST be set before any backend module is imported,
# because config.py / models.py create singletons at import time.
# ---------------------------------------------------------------------------

# Use a temp directory inside the project to avoid /tmp quota issues.
_backend_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
_test_root = os.path.join(_backend_dir, ".test_runs")
os.makedirs(_test_root, exist_ok=True)
# Point TMPDIR so that any tempfile calls (including PIL) use this location.
os.environ["TMPDIR"] = _test_root
tempfile.tempdir = _test_root

_test_data_dir = tempfile.mkdtemp(prefix="evatar_test_")

os.environ["EVATAR_LLM_API_KEY"] = "test-key"
os.environ["EVATAR_DATA_DIR"] = _test_data_dir
os.environ["EVATAR_PHOTOS_DIR"] = os.path.join(_test_data_dir, "photos")
os.environ["EVATAR_DB_PATH"] = os.path.join(_test_data_dir, "test.db")
os.environ["EVATAR_ENCRYPTION_KEY"] = ""  # disable custom key so auto-gen works

# Ensure the backend package is importable when running from the repo root.
if _backend_dir not in sys.path:
    sys.path.insert(0, _backend_dir)

# ---------------------------------------------------------------------------
# Now it is safe to import backend modules.
# ---------------------------------------------------------------------------
import pytest
import httpx
from unittest.mock import patch, AsyncMock
from contextlib import asynccontextmanager

from config import settings  # noqa: E402
from models import Base, engine, SessionLocal, get_db  # noqa: E402


# ---- Fixtures ----------------------------------------------------------------

@pytest.fixture()
def db_session():
    """Create all tables, yield a SQLAlchemy session, then drop all tables."""
    Base.metadata.create_all(engine)
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()
        Base.metadata.drop_all(engine)


@pytest.fixture()
async def client(db_session):
    """Async HTTP test client with the DB dependency overridden and the
    background scheduler disabled."""

    # Import app *after* env vars are set so it picks up the test config.
    from main import app  # noqa: E402

    def _override_get_db():
        yield db_session

    app.dependency_overrides[get_db] = _override_get_db

    # Patch the scheduler so it does not start background tasks during tests.
    with (
        patch("services.scheduler.start_scheduler"),
        patch("services.scheduler.stop_scheduler", new_callable=AsyncMock),
    ):
        async with httpx.AsyncClient(
            transport=httpx.ASGITransport(app=app),
            base_url="http://testserver",
        ) as ac:
            yield ac

    app.dependency_overrides.clear()


@pytest.fixture()
def mock_llm(monkeypatch):
    """Patch ``call_llm`` in every module that imports it so that no real LLM
    HTTP request is made.  Returns a canned analysis-style JSON response."""

    async def _fake_call_llm(messages, tools=None, max_tokens=None, temperature=None):
        return {
            "content": json.dumps(
                {
                    "app_name": "TestApp",
                    "content_category": "chat",
                    "intent": "note",
                    "summary": "Test summary from mock LLM",
                    "entities": [{"type": "test", "value": "entity1"}],
                    "confidence": 0.8,
                },
                ensure_ascii=False,
            ),
            "tool_calls": [],
        }

    # Patch at every import site so module-level `from services.llm import call_llm`
    # references are also replaced.
    monkeypatch.setattr("services.llm.call_llm", _fake_call_llm)
    monkeypatch.setattr("services.agent.call_llm", _fake_call_llm)
    monkeypatch.setattr("services.memory.call_llm", _fake_call_llm)
    # services.pipeline imports call_llm locally inside process_photo(), so
    # patching services.llm.call_llm is sufficient for it.
