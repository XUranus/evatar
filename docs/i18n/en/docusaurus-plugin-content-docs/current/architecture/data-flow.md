---
sidebar_position: 2
title: Data Flow
description: Detailed data flow for core features
---

# Data Flow

This document describes the data flow for each core Evatar feature, helping developers understand the complete request path from client to storage.

---

## Screenshot Sync Flow

Screenshot sync is Evatar's most fundamental feature. The Android client scans screenshot files on the device, uploads them to the backend, which automatically triggers LLM analysis.

```mermaid
sequenceDiagram
    participant App as Android App
    participant SM as SyncManager
    participant API as Backend API
    participant DB as SQLite
    participant FS as File System
    participant LLM as LLM Service

    Note over App: App startup / scheduled task
    App->>SM: SyncService.start()
    SM->>API: GET /api/photos/sync-state?device_id=xxx
    API->>DB: Query DeviceSyncState
    DB-->>API: last_synced_ts_ms
    API-->>SM: {"last_synced_ts_ms": 1700000000000}

    SM->>SM: scanMediaStoreSince(ts)<br/>Query RELATIVE_PATH LIKE '%Screenshots%'

    loop Each new screenshot (Semaphore=3)
        SM->>API: POST /api/photos/upload<br/>MultipartBody: file, device_id,<br/>local_media_store_id, timestamp...

        API->>API: _validate_device_id()
        API->>DB: Dedup check (device_id + local_media_store_id)
        alt Already exists
            API-->>SM: {"id": 123, "dedup": true}
        else New screenshot
            API->>FS: save_photo()<br/>Save original + generate thumbnail (256px)
            FS-->>API: (original_path, thumb_path, size, w, h)
            API->>DB: INSERT Photo + Analysis(status=pending)
            API->>DB: UPDATE DeviceSyncState
            API->>API: enqueue_analysis(photo_id)<br/>Create asyncio.Task
            API-->>SM: {"id": 456, "dedup": false}

            Note over API,LLM: Async analysis (does not block upload response)
            API->>LLM: call_llm(messages)<br/>system: screenshot analysis prompt<br/>user: [base64 image]
            LLM-->>API: JSON {app_name, content_category,<br/>intent, summary, entities, confidence}
            API->>DB: UPDATE Analysis (status=done)
            API->>API: extract_memories_from_text()
            API->>DB: INSERT Memory (dedup, encrypt)
        end
    end

    SM->>API: POST /api/photos/sync-state<br/>(update sync timestamp)
```

---

## Chat Message Flow

The chat system is based on the Agent architecture, supporting multi-turn dialogue and tool calling.

```mermaid
sequenceDiagram
    participant User as User
    participant Android as Android / Web
    participant API as /api/chat/send
    participant Agent as agent.py
    participant LLM as LLM Service
    participant RAG as rag.py (FTS5)
    participant Search as search.py
    participant DB as SQLite

    User->>Android: Input message "Any recent train ticket info?"
    Android->>API: POST /api/chat/send<br/>{message, conversation_id}

    API->>Agent: chat(conversation_id, user_message, db)

    Agent->>DB: Query or create Conversation
    Agent->>DB: INSERT ChatMessage(role=user)
    Agent->>DB: Read history (last 20 messages)
    Agent->>DB: Load user memories (get_memories_as_context)

    Note over Agent: Agent loop (max 3 rounds)

    Agent->>LLM: call_llm(messages, tools=TOOLS)<br/>system: Agent prompt + memory context<br/>+ history

    alt LLM returns tool_calls
        LLM-->>Agent: {tool_calls: [{name: "search_knowledge", args: {"query": "train ticket"}}]}

        Agent->>Agent: _execute_tool("search_knowledge", {"query": "train ticket"})
        Agent->>RAG: search_screenshots(db, "train ticket", limit=8)
        RAG->>DB: FTS5 MATCH query<br/>or fallback to LIKE query
        DB-->>RAG: [Analysis results]
        RAG-->>Agent: [{summary, app_name, entities...}]

        Agent->>DB: INSERT ChatMessage(role=tool, tool_name=search_knowledge)

        Note over Agent: Round 2: Call LLM again with tool results as context
        Agent->>LLM: call_llm(messages + tool_result)

        alt LLM returns plain text
            LLM-->>Agent: "Based on your screenshot records, found the following train ticket info..."
        end
    else LLM returns text directly
        LLM-->>Agent: "Hello! I can help you query screenshot info..."
    end

    Agent->>DB: INSERT ChatMessage(role=assistant)
    Agent->>Agent: Async extract memories (background task)
    Agent-->>API: {"role": "assistant", "content": "..."}
    API-->>Android: JSON response
    Android-->>User: Display Markdown rendered result
```

---

## Memory Extraction Flow

The memory system extracts user information from three sources: chat conversations, screenshot analysis, and reasoning articles.

```mermaid
sequenceDiagram
    participant Source as Source (Chat / Photo / Reasoner)
    participant Memory as memory.py
    participant LLM as LLM Service
    participant DB as SQLite

    Source->>Memory: extract_memories_from_text(text, source_type, source_id, device_id, db)

    Memory->>LLM: call_llm([<br/>{system: MEMORY_EXTRACT_PROMPT},<br/>{user: text[:6000]}<br/>], temperature=0.2)

    LLM-->>Memory: JSON array<br/>[{"content":"...", "category":"fact",<br/>"memory_type":"long_term", "importance":0.8}]

    loop Each memory
        Memory->>Memory: normalize -> MD5 hash
        Memory->>DB: Dedup check (device_id + content_hash)

        alt Already exists
            Memory->>DB: UPDATE access_count +1
        else New memory
            alt Encryption enabled
                Memory->>Memory: encrypt_field(content) -> Fernet encryption
                Memory->>DB: INSERT (content="[encrypted:hash]",<br/>encrypted_content=ciphertext)
            else No encryption
                Memory->>DB: INSERT (content=plaintext)
            end
        end
    end

    Memory-->>Source: Return extracted memory list
```

---

## Intent Reasoning Flow

The intent reasoner (`services/reasoner.py`) is Evatar's "background thinking" module, running once per hour to analyze recent user activity and generate structured notes.

```mermaid
sequenceDiagram
    participant Sched as Scheduler / Auto-trigger
    participant Reasoner as reasoner.py
    participant DB as SQLite
    participant LLM as LLM Service
    participant Push as push.py
    participant Device as Android Device

    Note over Sched: Hourly scheduled trigger<br/>or auto-trigger after every 3 new screenshot analyses

    Sched->>Reasoner: run_reasoning_cycle()

    Reasoner->>DB: Query recent 24h screenshot analyses (max 20)
    Reasoner->>DB: Query recent 24h user chat messages (max 20)
    Reasoner->>DB: Get user memory context (max 10)

    Reasoner->>Reasoner: Build context text<br/>"## Recent Screenshot Analyses\n- [12-15 10:30] App:WeChat..."

    Reasoner->>LLM: call_llm([<br/>{system: REASONING_PROMPT},<br/>{user: context[:8000]}<br/>], temperature=0.3)

    LLM-->>Reasoner: JSON array<br/>[{"title":"...", "summary":"...",<br/>"content":"# Markdown article",<br/>"category":"insight", "confidence":0.8}]

    loop Each article (max 3)
        Reasoner->>DB: INSERT Dynamic (title, content, category...)
        Reasoner->>DB: Record source photo_ids, conversation_ids
    end

    Reasoner->>Reasoner: extract_memories_from_text(articles)

    Reasoner->>Push: broadcast_push(title, body, data)
    Push->>DB: Query all registered DeviceTokens
    loop Each device
        Push->>Device: POST webhook_url<br/>{device_id, title, body, data}
    end

    Reasoner-->>Sched: [{"title":"...", "category":"..."}]
```

---

## RAG Retrieval Flow

When users ask questions in chat, the Agent uses RAG (Retrieval-Augmented Generation) to retrieve relevant information from the screenshot knowledge base.

```mermaid
sequenceDiagram
    participant Agent as Agent
    participant RAG as rag.py
    participant DB as SQLite

    Agent->>RAG: search_screenshots(db, "train ticket", limit=8)

    RAG->>DB: Check if FTS5 index exists
    alt FTS5 index missing or stale
        RAG->>DB: CREATE VIRTUAL TABLE analysis_fts<br/>USING fts5(summary, app_name, content_category,<br/>intent, entities)
        RAG->>DB: INSERT INTO analysis_fts<br/>SELECT FROM analyses WHERE status='done'
    end

    RAG->>DB: SELECT ... FROM analysis_fts<br/>WHERE analysis_fts MATCH :query<br/>ORDER BY rank LIMIT :limit

    alt FTS5 has results
        DB-->>RAG: [Matched results]
    else FTS5 no results
        RAG->>DB: Fallback: LIKE query<br/>WHERE summary LIKE '%train%'<br/>OR entities LIKE '%train%'
        DB-->>RAG: [Fuzzy match results]
    end

    RAG-->>Agent: [{analysis_id, summary, app_name,<br/>content_category, intent, entities,<br/>filename, timestamp}]
```
