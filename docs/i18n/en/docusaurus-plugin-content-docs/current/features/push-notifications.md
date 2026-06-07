---
sidebar_position: 7
title: Push Notifications
description: Multi-device push notification system
---

# Push Notifications

## Overview

Evatar's push notification system supports broadcasting messages to all registered devices. When the intent reasoner generates new articles, the system automatically pushes notifications to all devices. Current implementation uses Webhook mode, with FCM HTTP v1 as a reserved extension point.

## Push Triggers

| Trigger | Title | Content |
|---------|-------|---------|
| Reasoner generates articles | "Evatar New Notes" | "Generated N notes: title1, title2" |
| Manual broadcast | Custom | Custom |
| Test push | "Evatar Test Notification" | "This is a test push notification." |

## Configuration

| Variable | Description | Default |
|----------|-------------|---------|
| `EVATAR_PUSH_WEBHOOK_URL` | Webhook push URL | Empty (log only) |
| `EVATAR_FCM_PROJECT_ID` | FCM project ID (reserved) | Empty |
| `EVATAR_FCM_CREDENTIALS_JSON` | FCM service account JSON (reserved) | Empty |

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/push/register` | Register/update device |
| `GET` | `/api/push/devices` | List all registered devices |
| `DELETE` | `/api/push/devices/{device_id}` | Remove device |
| `POST` | `/api/push/test` | Send test push to device |
| `POST` | `/api/push/broadcast` | Broadcast to all devices |
