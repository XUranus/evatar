---
sidebar_position: 6
title: Memory System
description: Memory extraction, storage and management
---

# Memory System

## Overview

The memory system is one of Evatar Agent's core capabilities. It automatically extracts important information from chat conversations, screenshot analysis, and reasoning articles, divided into short-term (48-hour expiry) and long-term (permanent) memory. Memories are automatically injected into the Agent's conversation context.

## Memory Types

| Type | Description | Expiry Strategy |
|------|-------------|----------------|
| `short_term` | Short-term memory, temporary info | Auto-deleted after 48 hours |
| `long_term` | Long-term memory, persistent info | Never expires, but decays |

## Memory Categories

| Category | Description | Example |
|----------|-------------|---------|
| `people` | People information | "User's colleague is Zhang San" |
| `finance` | Financial info | "User holds NVDA stock" |
| `schedule` | Schedule/arrangements | "Project review meeting next Wednesday" |
| `project` | Project/work info | "Working on metro engineering bid" |
| `preference` | Preferences | "User prefers Markdown notes" |
| `interest` | Interests | "Follows AI and machine learning" |
| `habit` | Habits | "Takes screenshots at 10pm daily" |
| `fact` | General facts | Default category |

## Deduplication

Memory uses MD5 hashing for deduplication (lowercase, trim whitespace, strip trailing period). Existing memories increment `access_count` instead of creating duplicates.

## Decay Mechanism

- Short-term memory: auto-deleted after 48 hours
- Long-term memory: importance multiplied by 0.9 after 7 days without access
- Importance never drops below 0.1
- Each access refreshes `last_accessed` and increments `access_count`

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/memories` | Paginated memory list |
| `GET` | `/api/memories/stats` | Memory statistics |
| `DELETE` | `/api/memories/{id}` | Delete specific memory |
