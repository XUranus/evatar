---
sidebar_position: 1
title: Getting Started
description: Set up the Evatar development environment and run the project from scratch
---

# Getting Started

This chapter explains how to set up the Evatar development environment from scratch, start the backend, frontend, and Android client, and complete your first screenshot sync.

## Project Structure

```
evatar/
├── backend/          # Python Backend (FastAPI)
│   ├── api/          #   API Routes (photos, chat, dynamics, memories...)
│   ├── services/     #   Business Logic (agent, pipeline, reasoner, memory...)
│   ├── models.py     #   SQLAlchemy Data Models
│   ├── config.py     #   Pydantic Settings Configuration
│   ├── main.py       #   FastAPI Application Entry
│   └── requirements.txt
├── frontend/         # React Web Frontend
│   ├── src/
│   │   ├── pages/    #   Dashboard, Photos, Chat, Dynamics, Settings
│   │   ├── api/      #   Axios API Client
│   │   └── components/
│   └── package.json
├── android/          # Android Client
│   ├── app/src/main/java/com/evatar/app/
│   │   ├── sync/     #   SyncManager, SyncWorker, SyncService
│   │   ├── network/  #   ApiClient (OkHttp)
│   │   ├── ui/       #   Jetpack Compose UI
│   │   └── viewmodel/
│   └── build.gradle.kts
└── wiki/             # This Documentation Site (Docusaurus)
```

---

## Step 1: Clone the Repository

```bash
git clone <repo-url> evatar
cd evatar
```

---

## Step 2: Start the Backend

The backend is based on Python FastAPI, using SQLite as the database -- no additional database installation required.

```bash
cd backend

# Create and activate virtual environment
python -m venv .venv
source .venv/bin/activate    # Linux/macOS
# .venv\Scripts\activate     # Windows

# Install dependencies
pip install -r requirements.txt

# Start in development mode (skip authentication)
EVATAR_DEV_MODE=true python -m uvicorn main:app --host 0.0.0.0 --port 8421 --reload
```

After successful startup, the terminal will display:

```
INFO:     Started server process [xxxxx]
INFO:     Waiting for application startup.
INFO:     Starting Evatar backend...
INFO:     Database initialized at /path/to/backend/data/evatar.db
INFO:     Background scheduler started
INFO:     Application startup complete.
INFO:     Uvicorn running on http://0.0.0.0:8421
```

Verify the backend is running:

```bash
curl http://localhost:8421/api/health
# Expected output: {"status": "ok"}
```

---

## Step 3: Start the Frontend

The frontend uses React + Vite. In development mode, Vite proxy forwards `/api` requests to the backend.

```bash
cd frontend

# Install dependencies
pnpm install

# Start development server
pnpm dev
```

After successful startup, the terminal will display:

```
  VITE v8.x.x  ready in xxx ms

  ➜  Local:   http://localhost:3000/
  ➜  press h + enter to show help
```

Open `http://localhost:3000` in your browser to see the Evatar Web interface.

---

## Step 4: Build the Android APK

The Android client supports minimum API 26 (Android 8.0), compiled with JDK 17.

```bash
cd android

# Build Debug APK
./gradlew assembleDebug

# APK output path
ls app/build/outputs/apk/debug/app-debug.apk
```

Install to device:

```bash
# Install via ADB
adb install app/build/outputs/apk/debug/app-debug.apk

# Or click Run in Android Studio
```

---

## Step 5: Configure Server URL

When opening the Android app for the first time, you'll enter the onboarding flow (`OnboardingScreen`):

1. **Welcome** -- Click "Start Setup"
2. **Server Configuration** -- Enter the backend address, e.g., `http://192.168.0.107:8421`, click "Test Connection" to verify
3. **Sync Range** -- Select sync time range (1 day/3 days/7 days/30 days/all)
4. **Start Sync** -- The app automatically scans and uploads screenshots

:::tip
Make sure your phone and computer are on the same local network. Use `ipconfig` (Windows) or `ifconfig` (Linux/macOS) to check your computer's IP address.
:::

---

## Step 6: First Screenshot Sync

After sync starts, the Android `SyncManager` will:

1. Query the server for the last sync timestamp (`GET /api/photos/sync-state`)
2. Scan `MediaStore` for screenshots within the specified time range (`RELATIVE_PATH LIKE '%Screenshots%'`)
3. Upload screenshots in batches using 3 concurrent semaphores (`Semaphore(MAX_CONCURRENT=3)`)
4. The server saves image files, creates analysis records, and automatically triggers LLM analysis

On the Web frontend's **Dashboard** page, you can see:
- Total synced screenshots
- Analysis status distribution (pending / processing / done / error)
- Content category and intent distribution statistics

---

## Ports & URLs

| Service | Default Port | URL | Description |
|---------|-------------|-----|-------------|
| **Backend API** | `8421` | `http://localhost:8421` | FastAPI service |
| **Backend Health** | `8421` | `http://localhost:8421/api/health` | Returns `{"status":"ok"}` |
| **Frontend Dev** | `3000` | `http://localhost:3000` | Vite dev server |
| **Vite API Proxy** | `3000` | `http://localhost:3000/api/*` | Forwards to `localhost:8421` |
| **Android Client** | - | Configured server address | Runs on Android device |

:::info
CORS default allowed origins: `http://localhost:3000`, `http://localhost:5173`, `http://localhost:8421`. Customize via the `EVATAR_CORS_ORIGINS` environment variable (comma-separated).
:::

---

## Environment Variables Quick Reference

| Variable | Default | Description |
|----------|---------|-------------|
| `EVATAR_DEV_MODE` | `false` | Set to `true` to skip API Key authentication |
| `EVATAR_API_KEY` | empty | API authentication key (required in production) |
| `EVATAR_LLM_API_KEY` | empty | LLM service API Key |
| `EVATAR_LLM_BASE_URL` | empty | LLM service address |
| `EVATAR_HOST` | `0.0.0.0` | Listen address |
| `EVATAR_PORT` | `8421` | Listen port |
| `EVATAR_RETENTION_DAYS` | `30` | Data retention days |

---

## Next Steps

- **[Environment Setup](./prerequisites.md)** -- Detailed installation of all dependencies
- **[First Run](./first-run.md)** -- Complete first-run flow and verification
- **[Architecture Overview](../architecture/index.md)** -- Learn about system design
