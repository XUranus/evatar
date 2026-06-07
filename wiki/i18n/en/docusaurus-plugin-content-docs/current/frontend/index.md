---
sidebar_position: 1
title: Frontend Overview
description: Web frontend overview
---

# Frontend Overview

The Web frontend is built with **React 19** + **TypeScript** + **Vite** + **Tailwind CSS 4**, providing screenshot management, AI chat, dynamic browsing, and system settings interfaces.

## Project Structure

```
frontend/
├── vite.config.ts           # Vite config (proxy, plugins)
├── package.json             # Dependencies and scripts
├── tsconfig.json            # TypeScript config
├── src/
│   ├── index.css            # Global styles (CSS variables, design system)
│   ├── api/
│   │   └── client.ts        # API client wrapper (Axios + TypeScript types)
│   ├── i18n/
│   │   ├── index.ts         # i18next initialization
│   │   └── locales/
│   │       ├── zh-CN.json   # Chinese translations
│   │       └── en.json      # English translations
│   └── (page components)
└── public/
```

## Key Technologies

| Technology | Purpose | Version |
|-----------|---------|---------|
| React | UI framework | 19.x |
| TypeScript | Type safety | 6.x |
| Vite | Dev server and build | 8.x |
| Tailwind CSS | Atomic CSS framework | 4.x |
| Axios | HTTP requests | 1.x |
| i18next | Internationalization | 26.x |
| react-markdown | Markdown rendering | 10.x |
| lucide-react | Icon library | 1.x |
