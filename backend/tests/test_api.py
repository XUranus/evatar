"""API endpoint tests for the Evatar backend."""

import io
import json
from unittest.mock import patch

import pytest
from PIL import Image


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _make_test_png(width: int = 1, height: int = 1, color: str = "red") -> bytes:
    """Create a minimal PNG image in memory."""
    img = Image.new("RGB", (width, height), color=color)
    buf = io.BytesIO()
    img.save(buf, format="PNG")
    return buf.getvalue()


TEST_IMAGE_BYTES = _make_test_png()


# ---------------------------------------------------------------------------
# Health
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_health(client):
    resp = await client.get("/api/health")
    assert resp.status_code == 200
    assert resp.json()["status"] == "ok"


# ---------------------------------------------------------------------------
# Photos
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_upload_photo(client, mock_llm):
    with patch("api.photos.enqueue_analysis"):
        resp = await client.post(
            "/api/photos/upload",
            files={"file": ("test.png", TEST_IMAGE_BYTES, "image/png")},
            data={"device_id": "test-device", "device_name": "TestPhone"},
        )
    assert resp.status_code == 200
    data = resp.json()
    assert "id" in data
    assert data["filename"] == "test.png"
    assert data.get("dedup") in (None, False)


@pytest.mark.asyncio
async def test_list_photos(client, mock_llm):
    # Upload a photo first
    with patch("api.photos.enqueue_analysis"):
        await client.post(
            "/api/photos/upload",
            files={"file": ("test.png", TEST_IMAGE_BYTES, "image/png")},
            data={"device_id": "test-device"},
        )

    resp = await client.get("/api/photos")
    assert resp.status_code == 200
    body = resp.json()
    assert body["total"] >= 1
    assert len(body["items"]) >= 1
    first = body["items"][0]
    assert "id" in first
    assert "filename" in first


@pytest.mark.asyncio
async def test_get_photo(client, mock_llm):
    # Upload a photo first
    with patch("api.photos.enqueue_analysis"):
        upload_resp = await client.post(
            "/api/photos/upload",
            files={"file": ("test.png", TEST_IMAGE_BYTES, "image/png")},
            data={"device_id": "test-device"},
        )
    photo_id = upload_resp.json()["id"]

    resp = await client.get(f"/api/photos/{photo_id}")
    assert resp.status_code == 200
    data = resp.json()
    assert data["id"] == photo_id
    assert data["filename"] == "test.png"
    assert "analysis" in data


@pytest.mark.asyncio
async def test_delete_photo(client, mock_llm):
    # Upload a photo first
    with patch("api.photos.enqueue_analysis"):
        upload_resp = await client.post(
            "/api/photos/upload",
            files={"file": ("test.png", TEST_IMAGE_BYTES, "image/png")},
            data={"device_id": "test-device"},
        )
    photo_id = upload_resp.json()["id"]

    resp = await client.delete(f"/api/photos/{photo_id}")
    assert resp.status_code == 200
    assert resp.json()["message"] == "Deleted"

    # Verify it is gone
    resp2 = await client.get(f"/api/photos/{photo_id}")
    assert resp2.status_code == 404


@pytest.mark.asyncio
async def test_check_duplicates(client, mock_llm):
    """Uploading the same photo twice with the same local_media_store_id
    should return a dedup response on the second attempt."""
    upload_data = {
        "device_id": "test-device",
        "local_media_store_id": "media_001",
    }

    with patch("api.photos.enqueue_analysis"):
        resp1 = await client.post(
            "/api/photos/upload",
            files={"file": ("dup.png", TEST_IMAGE_BYTES, "image/png")},
            data=upload_data,
        )
    assert resp1.status_code == 200
    first_id = resp1.json()["id"]

    with patch("api.photos.enqueue_analysis"):
        resp2 = await client.post(
            "/api/photos/upload",
            files={"file": ("dup.png", TEST_IMAGE_BYTES, "image/png")},
            data=upload_data,
        )
    assert resp2.status_code == 200
    assert resp2.json().get("dedup") is True
    assert resp2.json()["id"] == first_id


# ---------------------------------------------------------------------------
# Chat
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_chat_send(client, mock_llm):
    resp = await client.post(
        "/api/chat/send",
        json={"message": "Hello, what can you do?"},
    )
    assert resp.status_code == 200
    data = resp.json()
    assert "conversation_id" in data
    msg = data["message"]
    assert msg["role"] == "assistant"
    assert msg["content"]  # non-empty


@pytest.mark.asyncio
async def test_conversations_list(client):
    resp = await client.get("/api/chat/conversations")
    assert resp.status_code == 200
    body = resp.json()
    assert "conversations" in body
    assert isinstance(body["conversations"], list)


# ---------------------------------------------------------------------------
# Config
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_config_get(client):
    resp = await client.get("/api/config/llm")
    assert resp.status_code == 200
    data = resp.json()
    assert "provider" in data
    assert "model" in data
    assert "api_key_set" in data


@pytest.mark.asyncio
async def test_config_update(client):
    resp = await client.put(
        "/api/config/llm",
        json={"provider": "openai", "model": "gpt-4o"},
    )
    assert resp.status_code == 200
    assert resp.json()["provider"] == "openai"

    # Verify the change persisted
    resp2 = await client.get("/api/config/llm")
    assert resp2.json()["model"] == "gpt-4o"
    assert resp2.json()["provider"] == "openai"


# ---------------------------------------------------------------------------
# Skills
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_skills_list(client):
    resp = await client.get("/api/skills")
    assert resp.status_code == 200
    data = resp.json()
    assert "skills" in data
    assert len(data["skills"]) >= 1  # default skills are auto-created


# ---------------------------------------------------------------------------
# Dynamics
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_dynamics_empty(client):
    resp = await client.get("/api/dynamics")
    assert resp.status_code == 200
    body = resp.json()
    assert body["total"] == 0
    assert body["items"] == []


# ---------------------------------------------------------------------------
# Memories
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_memories_empty(client):
    resp = await client.get("/api/memories")
    assert resp.status_code == 200
    body = resp.json()
    assert body["total"] == 0
    assert body["items"] == []


@pytest.mark.asyncio
async def test_memory_stats(client):
    resp = await client.get("/api/memories/stats")
    assert resp.status_code == 200
    data = resp.json()
    assert data["total"] == 0
    assert data["short_term"] == 0
    assert data["long_term"] == 0
    assert data["categories"] == {}
