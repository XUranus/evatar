import os
from pathlib import Path
from pydantic_settings import BaseSettings

BASE_DIR = Path(__file__).resolve().parent


class Settings(BaseSettings):
    # Server
    host: str = "0.0.0.0"
    port: int = 8421

    # Auth
    api_key: str = ""  # Set via EVATAR_API_KEY env var; empty = no auth (dev only)
    dev_mode: bool = False  # Set EVATAR_DEV_MODE=true for local development

    # Storage
    data_dir: Path = BASE_DIR / "data"
    photos_dir: Path = BASE_DIR / "data" / "photos"
    db_path: str = str(BASE_DIR / "data" / "evatar.db")
    max_upload_bytes: int = 50 * 1024 * 1024  # 50MB per file

    # LLM
    llm_base_url: str = ""
    llm_api_key: str = ""  # MUST be set via EVATAR_LLM_API_KEY env var
    llm_model: str = "mimo-v2.5"
    llm_max_tokens: int = 4096
    llm_temperature: float = 0.1

    # Agent
    agent_max_rounds: int = 3       # Reduced from 5 for faster responses
    agent_history_limit: int = 20

    # Web search (optional)
    tavily_api_key: str = ""
    brave_api_key: str = ""

    # Encryption (optional - auto-generates key if not set)
    encryption_key: str = ""

    # CORS (comma-separated origins; empty = localhost defaults)
    cors_origins: str = ""

    # Data retention
    retention_days: int = 30

    # Push notifications (FCM HTTP v1)
    fcm_project_id: str = ""
    fcm_credentials_json: str = ""  # Path to service account JSON or inline JSON
    push_webhook_url: str = ""      # Fallback webhook URL if FCM not configured

    class Config:
        env_prefix = "EVATAR_"

    @property
    def db_url(self) -> str:
        return f"sqlite:///{self.db_path}"


settings = Settings()

# Ensure directories exist
settings.photos_dir.mkdir(parents=True, exist_ok=True)
settings.data_dir.mkdir(parents=True, exist_ok=True)
