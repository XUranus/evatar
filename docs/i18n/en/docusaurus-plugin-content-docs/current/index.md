---
sidebar_position: 1
title: Evatar Documentation
description: Screenshot Sync & AI Analysis Assistant - Complete Technical Documentation
---

# Evatar

**Screenshot Sync & AI Analysis Assistant** -- Understand your digital life through phone screenshots, proactively organize information for you.

---

## Core Features

| Feature | Description |
|---------|-------------|
| Screenshot Sync | Android background auto-syncs screenshots to server, supports incremental sync and deduplication |
| AI Analysis | Multimodal LLM parses screenshot content, extracts intent, entities, and summaries |
| Smart Assistant | Chat-based Agent with knowledge base search, web search, and file upload support |
| Dynamic Notes | Background intent reasoning engine auto-generates articles, pushed to the Dynamics page |

---

## System Architecture

```mermaid
graph TB
    subgraph Android["Android App"]
        A1[Screenshot Sync]
        A2[AI Chat]
        A3[Dynamics Feed]
    end

    subgraph Server["Backend Server"]
        B1[FastAPI]
        B2[Agent]
        B3[Reasoner]
        B4[Memory]
    end

    subgraph Storage["Storage"]
        S1[(SQLite)]
        S2[File System]
    end

    subgraph External["External"]
        E1[LLM API]
        E2[Web Search]
    end

    A1 -->|Upload Screenshots| B1
    A2 -->|Chat Messages| B1
    A3 -->|Browse Dynamics| B1
    B1 --> B2
    B1 --> B3
    B2 --> B4
    B2 --> E1
    B2 --> E2
    B3 --> E1
    B1 --> S1
    B1 --> S2
```

---

## Quick Start

```bash
# 1. Start the backend
cd backend && python3.11 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
EVATAR_LLM_API_KEY="your-key" python main.py

# 2. Start the frontend
cd frontend && pnpm install && pnpm dev

# 3. Build Android
cd android && ./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

See the [Getting Started Guide](/getting-started) for details.

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Android | Kotlin, Jetpack Compose, Material3, OkHttp, WorkManager |
| Backend | Python, FastAPI, SQLAlchemy, SQLite, httpx |
| Frontend | React, TypeScript, Vite, Tailwind CSS |
| AI | Multimodal LLM (MiMo/Qwen/OpenAI/Claude), FTS5 RAG |

---

## Documentation Navigation

| Section | Content |
|---------|---------|
| [Getting Started](/getting-started) | Environment setup, first run |
| [Architecture](/architecture) | System architecture, data flow, tech stack |
| [Backend](/backend) | API reference, data models, service layer |
| [Android](/android) | MVVM architecture, screens, sync mechanism |
| [Frontend](/frontend) | React architecture, page descriptions |
| [Features](/features) | Screenshot sync, AI analysis, chat, dynamics |
| [Deployment](/deployment) | Dev/production/Docker deployment |
| [Contributing](/contributing) | Code standards, Git workflow |
