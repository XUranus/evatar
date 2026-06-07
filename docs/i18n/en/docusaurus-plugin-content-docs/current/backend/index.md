---
sidebar_position: 1
title: Backend Overview
description: Evatar backend service introduction
---

# Backend Overview

Evatar backend is a Python Web service based on **FastAPI** + **SQLAlchemy** + **SQLite**, responsible for screenshot sync, AI analysis, chat assistant, dynamic generation, memory management, and push notifications.

## Project Structure

```
backend/
├── main.py                  # FastAPI app entry, middleware registration, lifespan management
├── config.py                # Settings config class (Pydantic BaseSettings)
├── models.py                # SQLAlchemy data model definitions, database initialization
├── api/                     # API Route Layer (request handling)
│   ├── photos.py            # Screenshot upload, list, detail, delete, sync state
│   ├── analysis.py          # Analysis list, reprocess, stats
│   ├── chat.py              # Chat send, conversation management
│   ├── dynamics.py          # Dynamics list, mark read, pin, manual trigger
│   ├── memories.py          # Memory list, stats
│   ├── config.py            # LLM config management, presets, SSRF protection
│   ├── push.py              # Push device registration, list, test
│   └── skills.py            # Skills & MCP server management
├── services/                # Business Logic Layer
│   ├── agent.py             # Chat Agent (tool call loop, memory injection)
│   ├── llm.py               # LLM HTTP client (shared httpx.AsyncClient)
│   ├── memory.py            # Memory extraction, retrieval, decay
│   ├── pipeline.py          # Screenshot analysis Pipeline (async tasks, retry)
│   ├── rag.py               # RAG search (FTS5 + keyword fallback)
│   ├── reasoner.py          # Background intent reasoning (article generation)
│   ├── push.py              # Push notification service (Webhook)
│   ├── search.py            # Internet search (Tavily / Brave)
│   ├── storage.py           # File storage & thumbnail generation
│   ├── encryption.py        # Fernet encryption service
│   ├── retention.py         # Data expiration cleanup
│   ├── scheduler.py         # Background scheduled task scheduler
│   └── utils.py             # Utility functions
└── tests/                   # Tests
```

## Layered Architecture

```mermaid
graph TB
    subgraph "Clients"
        A[Android App]
        B[Web Dashboard]
    end

    subgraph "FastAPI Middleware"
        C[auth_middleware<br/>API Key Auth]
        D[rate_limit_middleware<br/>Rate Limit 10/min]
        E[CORS Middleware]
    end

    subgraph "API Route Layer (api/)"
        F[photos.py]
        G[chat.py]
        H[dynamics.py]
        I[memories.py]
        J[config.py]
        K[push.py]
        L[skills.py]
        M[analysis.py]
    end

    subgraph "Service Layer (services/)"
        N[agent.py<br/>Chat Agent]
        O[llm.py<br/>LLM Client]
        P[rag.py<br/>RAG Search]
        Q[pipeline.py<br/>Analysis Pipeline]
        R[reasoner.py<br/>Intent Reasoner]
        S[memory.py<br/>Memory Management]
        T[push.py<br/>Push Notifications]
        U[search.py<br/>Web Search]
        V[storage.py<br/>File Storage]
        W[encryption.py<br/>Encryption]
        X[retention.py<br/>Data Cleanup]
        Y[scheduler.py<br/>Scheduler]
    end

    subgraph "Data Layer"
        Z[(SQLite<br/>WAL Mode)]
        AA[File System<br/>data/photos/]
    end

    A --> C
    B --> C
    C --> D --> E --> F & G & H & I & J & K & L & M
    F --> Q & V & Z
    G --> N
    N --> O & P & U & S
    H --> R
    R --> O & S
    Q --> O
    Y --> R & S & X
    S --> O
    P --> Z
    V --> AA
```

## Key Design Decisions

| Design | Description |
|--------|-------------|
| **StaticPool** | SQLite single-connection pool, avoids multi-connection contention |
| **WAL Mode** | Via `PRAGMA journal_mode=WAL` + `busy_timeout=5000` for concurrent read/write |
| **expire_on_commit=False** | Attributes accessible after commit |
| **Independent Sessions** | Background tasks use independent `SessionLocal()` sessions |
