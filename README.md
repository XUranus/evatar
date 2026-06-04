# Evatar

Screenshot Sync & AI Analysis Assistant — understand your life through phone screenshots and proactively organize information for you.

## Features

- 📱 **Native Android App**: Background service monitors screenshots and auto-syncs to server
- 🖥️ **Web Dashboard**: View synced photos, LLM analysis results, and statistics
- 🤖 **AI Analysis**: Multimodal LLM parses screenshot content, extracts intent, entities, and summaries
- 💬 **Multi-LLM Support**: Quick switch between MiMo, Qwen, OpenAI, Claude, GLM, Kimi, DeepSeek
- 🌐 **Internationalization**: Web and Android support Chinese/English

## Project Structure

```
evatar/
├── android/          # Native Android app (Kotlin + Jetpack Compose)
├── backend/          # Python backend (FastAPI + SQLite)
└── frontend/         # Web dashboard (React + Vite + Tailwind CSS)
```

## Quick Start

### 1. Start Backend

```bash
cd backend
python3.11 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
python main.py
```

Backend runs at `http://localhost:8000`.

### 2. Start Frontend

```bash
cd frontend
pnpm install
pnpm dev
```

Frontend runs at `http://localhost:3000`, proxying API to the backend.

### 3. Build Android App

```bash
cd android
./gradlew :app:assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

### 4. Usage

1. Open the Android app, configure server URL (default: `http://localhost:8000`)
2. Ensure server is connected (status card shows green)
3. Tap "Start Sync"
4. Screenshots are auto-uploaded and analyzed by the LLM
5. View analysis results in the web dashboard

## LLM Configuration

In the web dashboard's "LLM Config" page, you can:

- Quickly select preset providers (Xiaomi MiMo, Qwen DashScope, OpenAI, Claude, Zhipu GLM, Kimi, DeepSeek)
- Manually configure API URL, Key, and model name
- Set max context tokens and temperature

## Android Keep-alive

- **Foreground Service**: Persistent notification for background survival
- **Overlay Bubble**: Circular semi-transparent bubble showing sync status (green=idle, yellow=syncing, red=error)
- **Battery Optimization**: Guides users to remove the app from battery optimization

## Tech Stack

| Component | Technology |
|-----------|-----------|
| Android | Kotlin, Jetpack Compose, Room, OkHttp |
| Backend | Python, FastAPI, SQLAlchemy, SQLite, httpx |
| Frontend | React, TypeScript, Vite, Tailwind CSS, i18next |
| LLM | OpenAI-compatible API (multimodal) |

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/photos/upload` | Upload a photo |
| GET | `/api/photos` | List photos |
| GET | `/api/photos/{id}` | Photo detail + analysis |
| GET | `/api/photos/{id}/image` | Original image |
| GET | `/api/stats` | Statistics |
| GET | `/api/config/llm` | Get LLM config |
| PUT | `/api/config/llm` | Update LLM config |
| GET | `/api/config/llm/presets` | List presets |
| POST | `/api/config/llm/presets/{name}/apply` | Apply a preset |
