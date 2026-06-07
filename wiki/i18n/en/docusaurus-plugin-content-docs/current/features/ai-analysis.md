---
sidebar_position: 3
title: AI Analysis
description: Screenshot AI analysis feature details
---

# AI Analysis

## Overview

AI analysis is Evatar's core intelligence capability. Every uploaded screenshot automatically enters the analysis pipeline, where LLM performs multimodal analysis to extract structured information including app name, content category, user intent, summary, entities, and confidence.

## Analysis Fields

| Field | Type | Description | Example |
|-------|------|-------------|---------|
| `app_name` | String | Screenshot source app name | "WeChat", "Alipay", "12306" |
| `content_category` | String | Content category | "chat", "webpage", "finance", "notification" |
| `intent` | String | User intent | "reminder", "research", "reference", "note", "ignore" |
| `relevance` | String | Relevance level | "high", "medium", "low" |
| `summary` | String | Summary (2-3 sentences) | "User discussed tomorrow's meeting with Zhang San in WeChat" |
| `entities` | Array | Extracted entity list | `[{"type":"name","value":"Zhang San"}]` |
| `confidence` | Float | Confidence (0.0-1.0) | 0.85 |

## Analysis Pipeline Flow

1. Screenshot upload completes
2. `enqueue_analysis` creates async task
3. `_safe_process` with 3 retries (exponential backoff)
4. `process_photo()` checks idempotency, encodes image, calls LLM
5. Parses JSON response, saves Analysis record
6. If relevance is not low and confidence >= 0.3, extracts memory
7. Every 3 completed analyses triggers intent reasoning

## Analysis Status Flow

| Status | Description |
|--------|-------------|
| `pending` | Waiting for analysis |
| `processing` | Currently analyzing |
| `done` | Analysis complete |
| `error` | Analysis failed (retryable) |
