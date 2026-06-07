---
sidebar_position: 1
title: Deployment Guide
description: Evatar development and production deployment guide
---

# Deployment Guide

This document covers Evatar's development environment setup, production deployment, Docker containerization, and environment variable reference.

---

## Development Environment

### Backend

```bash
cd backend
python3.11 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
python main.py
```

### Frontend

```bash
cd frontend
pnpm install
pnpm dev
```

### Android

```bash
cd android
./gradlew :app:assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## Production Deployment

### Backend: Gunicorn + Uvicorn Workers

```bash
gunicorn main:app \
  --workers 4 \
  --worker-class uvicorn.workers.UvicornWorker \
  --bind 0.0.0.0:8421 \
  --timeout 120
```

### Frontend: Static Files + Nginx

```bash
cd frontend
pnpm build
# Deploy frontend/dist/ to Nginx
```

### SQLite Notes

Evatar uses SQLite with WAL mode. Backup strategy:

```bash
sqlite3 /opt/evatar/backend/data/evatar.db ".backup '/backup/evatar-$(date +%Y%m%d).db'"
```

---

## Docker Deployment

Use `docker compose up -d` with the provided `docker-compose.yml`, `Dockerfile.backend`, and `Dockerfile.frontend`.

---

## Environment Variables

See [Configuration](/backend/config) for the complete environment variable reference.

---

## Troubleshooting

| Issue | Solution |
|-------|----------|
| Port conflict | Change `EVATAR_PORT` or stop conflicting process |
| Permission errors | Ensure `data/` directory is writable by the backend process |
| LLM API errors | Check LLM config at `GET /api/config/llm` |
| Database locked | Ensure no external tools are accessing the DB; try WAL checkpoint |
| Android connection failure | Verify server is running, same LAN, firewall allows port 8421 |
| No analysis after sync | Verify LLM config, check backend logs, try manual reprocess |
