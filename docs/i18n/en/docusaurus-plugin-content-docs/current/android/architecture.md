---
sidebar_position: 2
title: MVVM Architecture
description: Android client MVVM architecture details
---

# MVVM Architecture

Evatar Android uses the classic MVVM (Model-View-ViewModel) architecture, implementing reactive data flow through Jetpack Compose's `StateFlow` + `collectAsState()`.

## Architecture Layers

```
┌─────────────────────────────────────────────────┐
│  View Layer (Compose)                           │
│  OnboardingScreen / ChatTab / DynamicTab / ...  │
│  Subscribes to StateFlow via collectAsState()   │
├─────────────────────────────────────────────────┤
│  ViewModel Layer                                │
│  ChatViewModel / DynamicViewModel / ...         │
│  MutableStateFlow<UiState> + viewModelScope     │
├─────────────────────────────────────────────────┤
│  Model Layer                                    │
│  ApiClient (Singleton) + SyncManager            │
│  OkHttp sync calls + Dispatchers.IO coroutines  │
└─────────────────────────────────────────────────┘
```

## State Management Pattern

All ViewModels follow a unified state management pattern using immutable `UiState` data classes, internal `MutableStateFlow`, `.copy()` updates, and `collectAsState()` subscriptions in Compose.

## ApiClient Singleton

`ApiClient` is the app's sole HTTP client entry point, using double-checked locking singleton pattern with OkHttp (15s connect, 120s write, 180s read timeouts).

### Retry Mechanism (executeWithRetry)

Retry only for `SocketTimeoutException` and `ConnectException`, other exceptions throw directly. Exponential backoff: 1000ms -> 2000ms -> 4000ms.

## Key Components

| Component | File | Responsibility |
|-----------|------|---------------|
| **ChatViewModel** | `viewmodel/ChatViewModel.kt` | Chat state: conversations, messages, active conversation |
| **DynamicViewModel** | `viewmodel/DynamicViewModel.kt` | Dynamics state: items, pagination, filters, cache |
| **SettingsViewModel** | `viewmodel/SettingsViewModel.kt` | Settings state: server URL, connection status, sync control |
| **SyncManager** | `sync/SyncManager.kt` | Core sync coordinator: MediaStore scan + concurrent upload |
| **WorkScheduler** | `sync/WorkScheduler.kt` | WorkManager periodic task scheduler |

## Concurrency Control

Uses `kotlinx.coroutines.sync.Semaphore` to limit max concurrent uploads to 3.

## content:// URI Handling

API 29+ MediaStore returns `content://` URIs instead of file paths, requiring copy to temp file before upload.
