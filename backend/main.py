import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from config import settings
from models import init_db
from api.photos import router as photos_router
from api.analysis import router as analysis_router
from api.config import router as config_router
from api.chat import router as chat_router
from api.skills import router as skills_router

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
    logger.info(f"Photos stored at {settings.photos_dir}")
    logger.info(f"LLM endpoint: {settings.llm_base_url}, model: {settings.llm_model}")
    yield
    logger.info("Shutting down Evatar backend...")


app = FastAPI(
    title="Evatar",
    description="Screenshot sync & AI analysis backend",
    version="0.1.0",
    lifespan=lifespan,
)

# CORS for the frontend dashboard
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(photos_router)
app.include_router(analysis_router)
app.include_router(config_router)
app.include_router(chat_router)
app.include_router(skills_router)


@app.get("/")
async def root():
    return {"name": "Evatar", "version": "0.1.0", "status": "running"}


@app.get("/api/health")
async def health():
    return {"status": "ok"}


if __name__ == "__main__":
    import uvicorn
    uvicorn.run("main:app", host=settings.host, port=settings.port, reload=True)
