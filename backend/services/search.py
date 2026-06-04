"""Web search service using configurable search API."""

import logging
import httpx

from config import settings

logger = logging.getLogger("evatar.search")


async def web_search(query: str, num_results: int = 5) -> list[dict]:
    """Search the web. Returns list of {title, url, snippet}."""
    # Try Tavily first
    if settings.tavily_api_key:
        return await _tavily_search(query, num_results)

    # Fallback: Brave Search
    if settings.brave_api_key:
        return await _brave_search(query, num_results)

    # No search API configured
    return [{"title": "搜索未配置", "snippet": "请在设置中配置 Tavily 或 Brave Search API Key", "url": ""}]


async def _tavily_search(query: str, max_results: int = 5) -> list[dict]:
    """Search using Tavily API."""
    try:
        async with httpx.AsyncClient(timeout=15.0) as client:
            resp = await client.post(
                "https://api.tavily.com/search",
                json={
                    "query": query,
                    "max_results": max_results,
                    "search_depth": "basic",
                },
                headers={"Authorization": f"Bearer {settings.tavily_api_key}"},
            )
            resp.raise_for_status()
            data = resp.json()
            return [
                {"title": r.get("title", ""), "url": r.get("url", ""), "snippet": r.get("content", "")[:300]}
                for r in data.get("results", [])
            ]
    except Exception as e:
        logger.error(f"Tavily search error: {e}")
        return []


async def _brave_search(query: str, count: int = 5) -> list[dict]:
    """Search using Brave Search API."""
    try:
        async with httpx.AsyncClient(timeout=15.0) as client:
            resp = await client.get(
                "https://api.search.brave.com/res/v1/web/search",
                params={"q": query, "count": count},
                headers={
                    "Accept": "application/json",
                    "Accept-Encoding": "gzip",
                    "X-Subscription-Token": settings.brave_api_key,
                },
            )
            resp.raise_for_status()
            data = resp.json()
            return [
                {"title": r.get("title", ""), "url": r.get("url", ""), "snippet": r.get("description", "")[:300]}
                for r in data.get("web", {}).get("results", [])
            ]
    except Exception as e:
        logger.error(f"Brave search error: {e}")
        return []
