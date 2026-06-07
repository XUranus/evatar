---
sidebar_position: 3
title: Screens
description: Android page UI structure and interactions
---

# Screens

## Navigation Architecture (AppNavigation)

The app uses a custom bottom navigation bar with three tabs:

| Tab | Enum | Selected Icon | Unselected Icon | Page |
|-----|------|--------------|----------------|------|
| Dynamics | `DYNAMIC` | `Icons.Filled.Newspaper` | `Icons.Outlined.Newspaper` | `DynamicTab` |
| Chat | `CHAT` | `Icons.Filled.ChatBubble` | `Icons.Outlined.ChatBubble` | `ChatTab` |
| Settings | `SETTINGS` | `Icons.Filled.Settings` | `Icons.Outlined.Settings` | `SettingsTab` |

The bottom navigation bar hides via `AnimatedVisibility` in chat full-screen mode.

---

## OnboardingScreen

First-launch guide that walks users through server configuration and initial sync. 5-step flow: Welcome -> Server Setup -> Sync Time -> Syncing -> Done.

---

## ChatTab

AI chat assistant with multi-conversation management, message send/receive, and failure retry. Two view states: Conversation List and Chat View.

---

## DynamicTab

Displays AI-generated dynamic feed with category filtering, read marking, and infinite scroll loading.

---

## SettingsTab

Manages server configuration, sync control, appearance settings, and system permissions.
