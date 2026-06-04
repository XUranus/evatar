import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI, Request, HTTPException
from fastapi.middleware.cors import CORSMiddleware

from config import settings
from models import init_db
from api.photos import router as photos_router
from api.analysis import router as analysis_router
from api.config import router as config_router
from api.chat import router as chat_router
from api.skills import router as skills_router
from api.dynamics import router as dynamics_router
from api.memories import router as memories_router

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(name)s] %(levelname)s: %(message)s",
)
logger = logging.getLogger("evatar")


@asynccontextmanager
async def lifespan(app: FastAPI):
    logger.info("Starting Evatar backend...")
    init_db()
    logger.info(f"Database initialized at {settings.db_path}")
    logger.info(f"LLM: {settings.llm_base_url}, model: {settings.llm_model}")

    # Start background scheduler
    from services.scheduler import start_scheduler, stop_scheduler
    start_scheduler()
    logger.info("Background scheduler started")

    yield

    stop_scheduler()
    logger.info("Shutting down Evatar backend...")


app = FastAPI(
    title="Evatar",
    description="Screenshot sync & AI analysis backend",
    version="0.3.0",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"] if not settings.api_key else [
        "http://localhost:3000", "http://localhost:5173",
    ],
    allow_credentials=False,
    allow_methods=["*"],
    allow_headers=["*"],
)

EXEMPT_PATHS = {"/", "/api/health"}


@app.middleware("http")
async def auth_middleware(request: Request, call_next):
    if settings.api_key and request.url.path not in EXEMPT_PATHS:
        auth = request.headers.get("Authorization", "")
        key_from_header = auth.removeprefix("Bearer ").strip() if auth.startswith("Bearer ") else ""
        key_from_query = request.query_params.get("api_key", "")
        if key_from_header != settings.api_key and key_from_query != settings.api_key:
            raise HTTPException(status_code=401, detail="Invalid or missing API key")
    return await call_next(request)


app.include_router(photos_router)
app.include_router(analysis_router)
app.include_router(config_router)
app.include_router(chat_router)
app.include_router(skills_router)
app.include_router(dynamics_router)
app.include_router(memories_router)


@app.get("/")
async def root():
    return {"name": "Evatar", "version": "0.3.0", "status": "running"}


@app.get("/api/health")
async def health():
    return {"status": "ok"}
