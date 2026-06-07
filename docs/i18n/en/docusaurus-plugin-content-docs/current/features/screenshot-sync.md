---
sidebar_position: 2
title: Screenshot Sync
description: Screenshot auto-sync feature details
---

# Screenshot Sync

## Overview

Screenshot sync is Evatar's core entry feature. The Android client automatically discovers new screenshots by monitoring the system MediaStore, uploads them to the backend after incremental comparison. The backend handles file storage, thumbnail generation, and automatic AI analysis pipeline triggering.

## Key Features

- **MediaStore Monitoring**: ContentResolver queries the system MediaStore for screenshots
- **Incremental Sync**: Server-driven timestamp approach -- only uploads screenshots after last sync time
- **Deduplication**: `device_id` + `local_media_store_id` unique constraint prevents duplicates
- **Concurrent Upload**: Kotlin Coroutines `Semaphore` limits concurrency to 3
- **App Exclusion**: Users can exclude specific apps' screenshots from sync
- **Dual Scheduling**: Foreground Service (60s) + WorkManager (30min)

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/photos/upload` | Upload single screenshot |
| `POST` | `/api/photos/upload-batch` | Batch upload (up to 50) |
| `GET` | `/api/photos/sync-state` | Query device sync state |
| `POST` | `/api/photos/sync-state` | Set sync start time |
| `GET` | `/api/photos` | Paginated screenshot list |
| `GET` | `/api/photos/{id}` | Get screenshot details with analysis |
| `GET` | `/api/photos/{id}/image` | Get original image file |
| `GET` | `/api/photos/{id}/thumbnail` | Get thumbnail file |
| `GET` | `/api/photos/devices` | List all synced devices |
| `DELETE` | `/api/photos/{id}` | Delete screenshot and files |
