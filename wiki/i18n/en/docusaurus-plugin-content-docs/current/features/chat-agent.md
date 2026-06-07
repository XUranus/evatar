---
sidebar_position: 4
title: AI Chat Assistant
description: Intelligent dialogue feature details
---

# AI Chat Assistant

## Overview

Evatar's AI chat assistant is an LLM-based Agent system with Tool Calling capability. It can search the user's screenshot knowledge base, fetch recent screenshots, search the internet, and leverage the memory system to provide personalized answers.

## Available Tools

| Tool | Function |
|------|----------|
| `search_knowledge` | Single keyword search in screenshot knowledge base |
| `search_multi` | Multi-keyword simultaneous search, merge and dedup |
| `get_recent` | Get recent N screenshot analyses (no search term needed) |
| `web_search` | Search the internet |

## Agent Loop

The Agent follows a loop pattern (max 3 rounds):
1. Build history messages (last 20) and inject user memories
2. Call LLM with messages and tools
3. If LLM returns tool_calls, execute tools and continue loop
4. If LLM returns plain text, return final reply
5. Async extract memories after conversation ends

## Key Features

- **Memory Injection**: First round auto-loads user memories as context
- **Skill System**: Inject additional System Prompt instructions for specific tasks
- **File Attachments**: Support multimodal dialogue with image and file uploads
- **RAG Retrieval**: FTS5 full-text search with keyword LIKE fallback
- **Error Handling**: LLM config check, timeout protection, user-friendly error messages

## Conversation Management

| Operation | API | Description |
|-----------|-----|-------------|
| Create/continue conversation | `POST /api/chat/send` | conversation_id optional, auto-generates 16-char hex |
| With attachments | `POST /api/chat/send-with-file` | Supports images and other files |
| List conversations | `GET /api/chat/conversations` | Sorted by update time, with message count |
| Get conversation details | `GET /api/chat/conversations/{id}` | Complete message history |
| Delete conversation | `DELETE /api/chat/conversations/{id}` | Cascade deletes all messages |
