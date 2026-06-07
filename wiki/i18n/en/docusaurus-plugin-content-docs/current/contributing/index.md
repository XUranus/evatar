---
sidebar_position: 1
title: Contributing Guide
description: How to contribute to the Evatar project
---

# Contributing Guide

Welcome to contribute to the Evatar project! This document will help you quickly set up the development environment, understand code standards, and submission process.

---

## Development Environment Setup

### Fork and Clone

```bash
# 1. Fork the project repository on GitHub

# 2. Clone your fork
git clone https://github.com/<your-username>/evatar.git
cd evatar

# 3. Add upstream repository
git remote add upstream https://github.com/<original-owner>/evatar.git

# 4. Sync latest code from upstream
git fetch upstream
git rebase upstream/master
```

---

## Code Standards

### Python (Backend)

- Follow [PEP 8](https://peps.python.org/pep-0008/) code style
- Use type hints for function parameters and return values
- Write docstrings explaining function purpose, especially for `services/` modules
- Use `logging` module, not `print()`

### Kotlin (Android)

- Follow [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- UI layer uses Jetpack Compose declarative paradigm, ViewModel manages state
- Network requests use OkHttp + Gson
- Use Coroutines for async operations

### TypeScript (Frontend)

- Use ESLint for code checking
- Functional style + Hooks for components
- Props defined via TypeScript `interface`
- Tailwind CSS atomic classes, no separate style files

---

## Git Workflow

### Branch Naming

| Prefix | Purpose | Example |
|--------|---------|---------|
| `feat/` | New features | `feat/push-notification` |
| `fix/` | Bug fixes | `fix/database-locked` |
| `docs/` | Documentation | `docs/deployment-guide` |
| `refactor/` | Code refactoring | `refactor/sync-service` |
| `test/` | Testing | `test/api-endpoints` |
| `chore/` | Build/tools | `chore/upgrade-deps` |

### Commit Messages

Use [Conventional Commits](https://www.conventionalcommits.org/) format:

```
<type>(<scope>): <description>
```

Types: `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`

Scope suggestions: `backend`, `frontend`, `android`, `wiki`

---

## Pull Request Process

1. Ensure your branch is based on the latest `master`
2. Ensure code passes checks:
   - Backend: `cd backend && pytest tests/ -v`
   - Frontend: `cd frontend && pnpm build`
   - Android: `cd android && ./gradlew :app:assembleDebug`
3. Push branch to your Fork
4. Create Pull Request on GitHub

---

## First PR Suggestions

1. **Documentation improvements**: Fix typos, add explanations, translate missing content
2. **Simple bug fixes**: Check GitHub Issues for `good first issue` labels
3. **Test coverage**: Add test cases for existing API endpoints or service functions
4. **Internationalization**: Supplement missing translations in `frontend/src/i18n/locales/`
