---
sidebar_position: 4
title: Sync Mechanism
description: Android screenshot sync implementation details
---

# Sync Mechanism

Evatar's sync mechanism automatically detects and uploads device screenshots to the backend. The entire sync flow is coordinated by `SyncManager`, triggered through `WorkScheduler` (WorkManager scheduled tasks) and `SyncService` (foreground Service continuous loop).

## Overall Flow

```mermaid
sequenceDiagram
    participant App as Evatar App
    participant MS as MediaStore
    participant SM as SyncManager
    participant API as Evatar Server

    Note over App: Scheduled trigger (30min WorkManager)<br/>or Foreground Service (60s loop)<br/>or Manual sync

    App->>SM: runSync()
    SM->>API: GET /api/photos/sync-state?device_id=xxx
    API-->>SM: { last_synced_ts_ms, total_synced }

    SM->>MS: ContentResolver.query()<br/>RELATIVE_PATH LIKE '%Screenshots%'<br/>AND DATE_ADDED > sinceMs
    MS-->>SM: Cursor (new screenshots)

    loop Each new screenshot (Semaphore max 3)
        SM->>SM: isExcludedByPath() filter exclusions
        alt API 29+ (content:// URI)
            SM->>MS: openInputStream(content://...)
            SM->>SM: Copy to temp file
        end
        SM->>API: POST /api/photos/upload<br/>(Multipart: file + metadata)
        API-->>SM: { id: 123 }
        SM->>SM: Delete temp file
    end

    SM-->>App: SyncResult(success, failed, total)
```

## Sync Methods

| Method | Mechanism | Interval | Description |
|--------|-----------|----------|-------------|
| Foreground Service | `SyncService` (LifecycleService) | 60 seconds | Persistent background, shows notification |
| WorkManager | `SyncWorker` (CoroutineWorker) | 30 minutes | System-scheduled, requires network |

## App Exclusion

Users can exclude specific apps' screenshots from sync. `AppExclusionManager` checks the screenshot's `RELATIVE_PATH` against excluded package names.

## Device Identifier

Each device is uniquely identified by `SyncManager.deviceId`:
```
"{MANUFACTURER}_{MODEL}_{ANDROID_ID}"
```
