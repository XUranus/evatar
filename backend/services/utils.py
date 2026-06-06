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


def format_llm_error(e: Exception) -> str:
    """Format LLM/HTTP errors into user-friendly messages."""
    import httpx

    if isinstance(e, httpx.HTTPStatusError):
        status = e.response.status_code
        try:
            body = e.response.json()
            detail = body.get("error", {}).get("message", "") or body.get("detail", "")
        except Exception:
            detail = e.response.text[:200]

        if status == 401:
            return "LLM API Key 无效或已过期。请在设置页面检查 API Key。"
        elif status == 403:
            return "LLM API 访问被拒绝。请检查 API Key 权限。"
        elif status == 404:
            return f"LLM 模型未找到。请检查模型名称配置。({detail})"
        elif status == 429:
            return "LLM API 请求频率超限，请稍后重试。"
        elif status >= 500:
            return f"LLM 服务端错误 (HTTP {status})。请稍后重试。({detail})"
        else:
            return f"LLM 请求失败 (HTTP {status}): {detail}"
    elif isinstance(e, httpx.ConnectError):
        return "无法连接 LLM 服务。请检查网络和服务地址配置。"
    elif isinstance(e, httpx.TimeoutException):
        return "LLM 请求超时。请稍后重试或检查网络连接。"
    elif isinstance(e, httpx.ConnectTimeout):
        return "连接 LLM 服务超时。请检查服务地址是否正确。"
    elif isinstance(e, (FileNotFoundError, PermissionError)):
        return f"文件错误: {e}"
    else:
        return f"{type(e).__name__}: {str(e)[:300]}"
