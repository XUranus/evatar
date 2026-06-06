import hmac
import logging
import time
from collections import defaultdict
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
from api.push import router as push_router

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(name)s] %(levelname)s: %(message)s",
)
logger = logging.getLogger("evatar")


@asynccontextmanager
async def lifespan(app: FastAPI):
    logger.info("Starting Evatar backend...")
    if not settings.api_key:
        if not settings.dev_mode:
            logger.critical(
                "\n"
                "╔══════════════════════════════════════════════════════════════════╗\n"
                "║  SECURITY WARNING: EVATAR_API_KEY is not set!                  ║\n"
                "║  Running in PRODUCTION mode without authentication.            ║\n"
                "║  All endpoints are publicly accessible.                        ║\n"
                "║  Set EVATAR_API_KEY or EVATAR_DEV_MODE=true immediately.      ║\n"
                "╚══════════════════════════════════════════════════════════════════╝"
            )
        else:
            logger.warning("API key not set! Endpoints are unauthenticated. Set EVATAR_API_KEY for production.")

    if not settings.llm_api_key and not settings.dev_mode:
        logger.critical(
            "\n"
            "╔══════════════════════════════════════════════════════════════════╗\n"
            "║  WARNING: EVATAR_LLM_API_KEY is not set!                       ║\n"
            "║  LLM analysis features will fail without a valid API key.      ║\n"
            "║  Set EVATAR_LLM_API_KEY or EVATAR_DEV_MODE=true.              ║\n"
            "╚══════════════════════════════════════════════════════════════════╝"
        )

    init_db()
    logger.info(f"Database initialized at {settings.db_path}")
    logger.info(f"LLM: {settings.llm_base_url}, model: {settings.llm_model}")

    # Start background scheduler
    from services.scheduler import start_scheduler, stop_scheduler
    await start_scheduler()
    logger.info("Background scheduler started")

    yield

    await stop_scheduler()
    from services.llm import close_client
    await close_client()
    logger.info("Shutting down Evatar backend...")


app = FastAPI(
    title="Evatar",
    description="Screenshot sync & AI analysis backend",
    version="0.3.0",
    lifespan=lifespan,
)

_DEFAULT_CORS = ["http://localhost:3000", "http://localhost:5173", "http://localhost:8421"]
_cors_origins = (
    [o.strip() for o in settings.cors_origins.split(",") if o.strip()]
    if settings.cors_origins
    else _DEFAULT_CORS
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=_cors_origins,
    allow_credentials=False,
    allow_methods=["GET", "POST", "PUT", "DELETE", "OPTIONS"],
    allow_headers=["*"],
)

EXEMPT_PATHS = {"/", "/api/health"}

_RATE_LIMITED_PATHS = {"/api/chat/send", "/api/chat/send-with-file", "/api/dynamics/trigger"}
_rate_limits: dict[str, list[float]] = defaultdict(list)


@app.middleware("http")
async def rate_limit_middleware(request: Request, call_next):
    if request.url.path in _RATE_LIMITED_PATHS:
        client_ip = request.client.host if request.client else "unknown"
        now = time.time()
        _rate_limits[client_ip] = [t for t in _rate_limits[client_ip] if now - t < 60]
        if len(_rate_limits[client_ip]) >= 10:
            raise HTTPException(status_code=429, detail="Rate limit exceeded (10 requests per minute)")
        _rate_limits[client_ip].append(now)
    return await call_next(request)


@app.middleware("http")
async def auth_middleware(request: Request, call_next):
    if settings.api_key and request.url.path not in EXEMPT_PATHS:
        auth = request.headers.get("Authorization", "")
        key_from_header = auth.removeprefix("Bearer ").strip() if auth.startswith("Bearer ") else ""
        if not hmac.compare_digest(key_from_header, settings.api_key):
            raise HTTPException(status_code=401, detail="Invalid or missing API key")
    return await call_next(request)


app.include_router(photos_router)
app.include_router(analysis_router)
app.include_router(config_router)
app.include_router(chat_router)
app.include_router(skills_router)
app.include_router(dynamics_router)
app.include_router(memories_router)
app.include_router(push_router)


@app.get("/")
async def root():
    return {"name": "Evatar", "version": "0.3.0", "status": "running"}


@app.get("/api/health")
async def health():
    return {"status": "ok"}
