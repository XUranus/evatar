---
sidebar_position: 3
title: Tech Stack
description: All technologies used in the project with version information
---

# Tech Stack

This document lists all technologies used in the Evatar project, their versions, and purposes, based on the actual `requirements.txt`, `package.json`, and `build.gradle.kts`.

---

## Backend (Python)

The backend is in the `backend/` directory, based on Python FastAPI framework.

| Technology | Version | Purpose |
|-----------|---------|---------|
| **Python** | 3.11+ | Runtime environment, `asyncio` async programming |
| **FastAPI** | 0.115.12 | Web framework, auto-generates OpenAPI docs, dependency injection |
| **Uvicorn** | 0.34.3 | ASGI server, supports WebSocket and HTTP/2 |
| **SQLAlchemy** | 2.0.41 | ORM framework, declarative model definition, query building |
| **SQLite** | Built-in | Embedded database, WAL mode and FTS5 full-text search |
| **httpx** | 0.28.1 | Async HTTP client for LLM API and search API calls |
| **Pillow** | 11.2.1 | Image processing, thumbnail generation and image scaling (>2048px) |
| **Pydantic** | 2.11.3 | Data validation and serialization |
| **pydantic-settings** | 2.9.1 | Environment variable config management, prefix `EVATAR_` |
| **python-multipart** | 0.0.20 | File upload parsing (Multipart form data) |
| **aiofiles** | 24.1.0 | Async file I/O |
| **cryptography** | 44.0.3 | Fernet symmetric encryption for sensitive data fields |

---

## Android Client (Kotlin)

Android client in the `android/` directory, minimum support API 26 (Android 8.0).

| Technology | Version | Purpose |
|-----------|---------|---------|
| **Kotlin** | 2.0+ (plugin.compose) | Primary language, coroutine async programming |
| **Jetpack Compose** | BOM 2024.06.00 | Declarative UI framework |
| **Material3** | compose-bom | Material Design 3 component library |
| **OkHttp** | 4.12.0 | HTTP client, connection pooling, interceptors, auto-retry |
| **Gson** | 2.11.0 | JSON serialization/deserialization |
| **WorkManager** | 2.9.1 | Background scheduled task scheduling (screenshot sync) |
| **Coil** | 2.6.0 | Compose native image loading library |
| **Accompanist** | 0.34.0 | Runtime permission request wrapper |
| **Navigation Compose** | 2.7.7 | Page navigation |
| **Lifecycle** | 2.8.4 | ViewModel + Compose lifecycle management |
| **Coroutines** | 1.8.1 | Kotlin coroutines, async network requests and concurrent uploads |

---

## Web Frontend (React)

Frontend in the `frontend/` directory, built with Vite 8.

| Technology | Version | Purpose |
|-----------|---------|---------|
| **React** | 19.2.6 | UI framework, function components + Hooks |
| **TypeScript** | 6.0.2 | Type-safe JavaScript superset |
| **Vite** | 8.0.12 | Build tool, HMR hot reload, API proxy |
| **Tailwind CSS** | 4.3.0 | Atomic CSS framework |
| **Axios** | 1.16.1 | HTTP client, request/response interceptors |
| **react-markdown** | 10.1.0 | Markdown rendering component (chat messages, dynamic notes) |
| **lucide-react** | 1.17.0 | Icon library |
| **i18next** | 26.3.0 | Internationalization framework |
| **react-i18next** | 17.0.8 | React i18n bindings |
| **i18next-browser-languagedetector** | 8.2.1 | Auto-detect browser language |

---

## Documentation

| Technology | Version | Purpose |
|-----------|---------|---------|
| **Docusaurus** | 3.x | Documentation site generator |
| **Mermaid** | latest | Diagram rendering in markdown |
