"""RAG service: search over screenshot analyses using FTS5 and keyword matching."""

import json
import logging
from sqlalchemy import text
from sqlalchemy.orm import Session

from models import Analysis, Photo

logger = logging.getLogger("evatar.rag")


def search_screenshots(db: Session, query: str, limit: int = 10) -> list[dict]:
    """Search screenshot analyses by keyword. Returns relevant results."""
    if not query.strip():
        return []

    # Strategy 1: FTS5 on summary (if available)
    results = _fts_search(db, query, limit)
    if results:
        return results

    # Strategy 2: LIKE-based keyword search
    return _keyword_search(db, query, limit)


def _fts_search(db: Session, query: str, limit: int) -> list[dict]:
    """Try FTS5 search on the analysis_fts virtual table."""
    try:
        # Check if FTS table exists
        check = db.execute(
            text("SELECT name FROM sqlite_master WHERE type='table' AND name='analysis_fts'")
        ).fetchone()
        if not check:
            _build_fts_index(db)

        # Tokenize query for FTS5
        tokens = query.replace("'", " ").replace('"', ' ').split()
        fts_query = " OR ".join(tokens)

        rows = db.execute(
            text("""
                SELECT a.id, a.summary, a.app_name, a.content_category, a.intent,
                       a.entities, p.filename, p.original_timestamp,
                       rank
                FROM analysis_fts
                JOIN analyses a ON a.id = analysis_fts.rowid
                JOIN photos p ON p.id = a.photo_id
                WHERE analysis_fts MATCH :query
                ORDER BY rank
                LIMIT :limit
            """),
            {"query": fts_query, "limit": limit}
        ).fetchall()

        return [_row_to_result(r) for r in rows]
    except Exception as e:
        logger.debug(f"FTS search failed, falling back: {e}")
        return []


def _build_fts_index(db: Session):
    """Build FTS5 virtual table for full-text search on analyses."""
    try:
        db.execute(text("DROP TABLE IF EXISTS analysis_fts"))
        db.execute(text("""
            CREATE VIRTUAL TABLE analysis_fts USING fts5(
                summary, app_name, content_category, intent, entities,
                content='analyses',
                content_rowid='id'
            )
        """))
        db.execute(text("""
            INSERT INTO analysis_fts(rowid, summary, app_name, content_category, intent, entities)
            SELECT id, summary, app_name, content_category, intent, entities
            FROM analyses WHERE status = 'done'
        """))
        db.commit()
        logger.info("FTS5 index built")
    except Exception as e:
        logger.warning(f"Failed to build FTS index: {e}")


def _keyword_search(db: Session, query: str, limit: int) -> list[dict]:
    """LIKE-based keyword search as fallback."""
    keywords = query.split()
    if not keywords:
        return []

    conditions = []
    params = {}
    for i, kw in enumerate(keywords[:5]):
        key = f"kw{i}"
        conditions.append(
            f"(a.summary LIKE :{key} OR a.app_name LIKE :{key} OR a.entities LIKE :{key} OR a.content_category LIKE :{key})"
        )
        params[key] = f"%{kw}%"

    where_clause = " AND ".join(conditions)

    rows = db.execute(
        text(f"""
            SELECT a.id, a.summary, a.app_name, a.content_category, a.intent,
                   a.entities, p.filename, p.original_timestamp
            FROM analyses a
            JOIN photos p ON p.id = a.photo_id
            WHERE a.status = 'done' AND {where_clause}
            ORDER BY p.original_timestamp DESC
            LIMIT :limit
        """),
        {**params, "limit": limit}
    ).fetchall()

    return [_row_to_result(r) for r in rows]


def _row_to_result(row) -> dict:
    return {
        "analysis_id": row[0],
        "summary": row[1] or "",
        "app_name": row[2] or "",
        "content_category": row[3] or "",
        "intent": row[4] or "",
        "entities": row[5] or "[]",
        "filename": row[6] or "",
        "timestamp": row[7].isoformat() if row[7] else None,
    }


def get_recent_analyses(db: Session, limit: int = 20) -> list[dict]:
    """Get recent analyses for context."""
    rows = (
        db.query(Analysis, Photo)
        .join(Photo, Analysis.photo_id == Photo.id)
        .filter(Analysis.status == "done")
        .order_by(Photo.original_timestamp.desc())
        .limit(limit)
        .all()
    )
    results = []
    for a, p in rows:
        results.append({
            "summary": a.summary or "",
            "app_name": a.app_name or "",
            "content_category": a.content_category or "",
            "intent": a.intent or "",
            "entities": a.entities or "[]",
            "filename": p.filename or "",
            "timestamp": p.original_timestamp.isoformat() if p.original_timestamp else None,
        })
    return results
