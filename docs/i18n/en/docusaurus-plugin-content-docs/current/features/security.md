---
sidebar_position: 8
title: Security
description: Authentication, encryption and protection strategies
---

# Security

## Overview

Evatar employs multi-layer security protection including API Key authentication, SSRF protection, rate limiting, file upload validation, path traversal protection, and data encryption.

## Authentication

- Bearer Token authentication via `Authorization` header
- Uses `hmac.compare_digest` for time-constant comparison (prevents timing attacks)
- Health check endpoints (`/`, `/api/health`) exempt from authentication

## SSRF Protection

LLM Base URL configuration includes strict SSRF protection with 6 layers:
1. Protocol check (force HTTPS)
2. Regex match for private IP patterns
3. IPv6 private address check
4. IPv4-mapped IPv6 check
5. `ipaddress` module validation
6. DNS resolution verification (prevents DNS rebinding)

## Rate Limiting

| Endpoint | Limit | Window |
|----------|-------|--------|
| `/api/chat/send` | 10 requests | 1 minute |
| `/api/chat/send-with-file` | 10 requests | 1 minute |
| `/api/dynamics/trigger` | 10 requests | 1 minute |

## Data Encryption

- Fernet symmetric encryption (AES-128-CBC + HMAC-SHA256)
- Encrypts sensitive fields: chat messages, memory entries
- Supports key rotation
- Transparent decryption via `display_content` property

## Security Checklist

| Check | Status |
|-------|--------|
| API Key authentication | Implemented |
| SSRF protection | Implemented |
| Rate limiting | Implemented |
| File type validation | Implemented |
| File size limits | Implemented |
| Path traversal protection | Implemented |
| Input format validation | Implemented |
| Data encryption | Implemented |
| CORS restriction | Implemented |
| Data retention | Implemented |
| HTTPS enforcement | Implemented |
