"""Shared utilities for backend services."""

import re


def strip_code_fences(text: str) -> str:
    """Remove markdown code fences (```json ... ```) from LLM responses."""
    text = text.strip()
    if text.startswith("```"):
        lines = text.split("\n")
        lines = [l for l in lines if not l.strip().startswith("```")]
        text = "\n".join(lines).strip()
    return text


def clamp(value: float, lo: float = 0.0, hi: float = 1.0) -> float:
    """Clamp a numeric value to [lo, hi]."""
    return max(lo, min(hi, value))


def truncate(text: str, max_len: int = 500) -> str:
    """Truncate text to max_len characters."""
    return text[:max_len] if len(text) > max_len else text
