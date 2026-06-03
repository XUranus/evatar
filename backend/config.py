import os
from pathlib import Path
from pydantic_settings import BaseSettings


BASE_DIR = Path(__file__).resolve().parent


class Settings(BaseSettings):
    # Server
    host: str = "0.0.0.0"
    port: int = 8000

    # Storage
    data_dir: Path = BASE_DIR / "data"
    photos_dir: Path = BASE_DIR / "data" / "photos"
    db_path: str = str(BASE_DIR / "data" / "evatar.db")

    # LLM
    llm_base_url: str = "https://token-plan-cn.xiaomimimo.com/v1"
    llm_api_key: str = "tp-cfnccplzudtfq5ec9h0pm0nvkrsbur576ufr0x8rdolj12h0"
    llm_model: str = "mimo-v2.5"

    # Web search (optional)
    tavily_api_key: str = ""
    brave_api_key: str = ""

    class Config:
        env_prefix = "EVATAR_"

    @property
    def db_url(self) -> str:
        return f"sqlite:///{self.db_path}"


settings = Settings()

# Ensure directories exist
settings.photos_dir.mkdir(parents=True, exist_ok=True)
(settings.data_dir).mkdir(parents=True, exist_ok=True)
