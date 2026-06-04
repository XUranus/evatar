"""Encryption service: Fernet-based symmetric encryption for sensitive fields."""

import logging
import os
from pathlib import Path

from cryptography.fernet import Fernet, InvalidToken

from config import settings

logger = logging.getLogger("evatar.encryption")

# Key file path for auto-generated key storage
_KEY_FILE = settings.data_dir / ".encryption_key"

_fernet: Fernet | None = None
_enabled: bool | None = None


def _get_or_create_key() -> str:
    """Get encryption key from env var, or auto-generate and persist one."""
    key = settings.encryption_key
    if key:
        return key

    # Try loading from file
    if _KEY_FILE.exists():
        return _KEY_FILE.read_text().strip()

    # Generate and persist
    key = Fernet.generate_key().decode("utf-8")
    _KEY_FILE.parent.mkdir(parents=True, exist_ok=True)
    _KEY_FILE.write_text(key)
    os.chmod(_KEY_FILE, 0o600)
    logger.warning(
        "⚠️ Auto-generated encryption key stored at %s. "
        "Set EVATAR_ENCRYPTION_KEY environment variable for production.",
        _KEY_FILE,
    )
    return key


def _get_fernet() -> Fernet:
    """Get or initialize the Fernet instance (lazy singleton)."""
    global _fernet
    if _fernet is None:
        key = _get_or_create_key()
        _fernet = Fernet(key.encode("utf-8") if isinstance(key, str) else key)
    return _fernet


def is_encryption_enabled() -> bool:
    """Check if encryption is available."""
    global _enabled
    if _enabled is None:
        try:
            _get_fernet()
            _enabled = True
        except Exception as e:
            logger.warning(f"Encryption not available: {e}")
            _enabled = False
    return _enabled


def encrypt_field(plaintext: str | None, key: str | None = None) -> str | None:
    """Encrypt a plaintext string. Returns base64-encoded ciphertext, or None if input is None."""
    if plaintext is None:
        return None
    if not plaintext:
        return ""
    f = _get_fernet() if key is None else Fernet(key.encode("utf-8") if isinstance(key, str) else key)
    return f.encrypt(plaintext.encode("utf-8")).decode("utf-8")


def decrypt_field(ciphertext: str | None) -> str | None:
    """Decrypt a ciphertext string. Returns plaintext, or None if input is None."""
    if ciphertext is None:
        return None
    if not ciphertext:
        return ""
    try:
        f = _get_fernet()
        return f.decrypt(ciphertext.encode("utf-8")).decode("utf-8")
    except InvalidToken:
        logger.warning("Failed to decrypt field: invalid token or wrong key")
        return "[encrypted: unable to decrypt]"
    except Exception as e:
        logger.warning(f"Decryption error: {e}")
        return "[encrypted: unable to decrypt]"


def rotate_key(old_key: str, new_key: str) -> None:
    """Re-encrypt all encrypted fields with a new key.

    This is a utility for key rotation; callers must provide the old key
    and call this during a maintenance window.
    """
    global _fernet, _enabled
    from models import SessionLocal, ChatMessage, Memory
    db = SessionLocal()
    try:
        old_f = Fernet(old_key.encode("utf-8") if isinstance(old_key, str) else old_key)
        new_f = Fernet(new_key.encode("utf-8") if isinstance(new_key, str) else new_key)

        # Rotate ChatMessage encrypted_content
        messages = db.query(ChatMessage).filter(ChatMessage.encrypted_content.isnot(None)).all()
        for msg in messages:
            if msg.encrypted_content:
                try:
                    plain = old_f.decrypt(msg.encrypted_content.encode("utf-8")).decode("utf-8")
                    msg.encrypted_content = new_f.encrypt(plain.encode("utf-8")).decode("utf-8")
                except InvalidToken:
                    logger.warning(f"Could not rotate key for ChatMessage {msg.id}")

        # Rotate Memory encrypted_content
        memories = db.query(Memory).filter(Memory.encrypted_content.isnot(None)).all()
        for mem in memories:
            if mem.encrypted_content:
                try:
                    plain = old_f.decrypt(mem.encrypted_content.encode("utf-8")).decode("utf-8")
                    mem.encrypted_content = new_f.encrypt(plain.encode("utf-8")).decode("utf-8")
                except InvalidToken:
                    logger.warning(f"Could not rotate key for Memory {mem.id}")

        db.commit()

        # Persist new key to key file
        _KEY_FILE.write_text(new_key)
        os.chmod(_KEY_FILE, 0o600)

        # Reset in-memory state so next call re-initializes with the new key
        _fernet = None
        _enabled = None

        logger.info(f"Key rotation complete: {len(messages)} messages, {len(memories)} memories re-encrypted")
    except Exception:
        db.rollback()
        logger.error("Key rotation failed, rolled back all changes", exc_info=True)
        raise
    finally:
        db.close()
