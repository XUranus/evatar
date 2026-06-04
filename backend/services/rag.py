"""RAG service: search over screenshot analyses using FTS5 and keyword matching."""

import re
import logging
from sqlalchemy import text
from sqlalchemy.orm import Session

from models import Analysis, Photo, SessionLocal

logger = logging.getLogger("evatar.rag")

# Chars that could break FTS5 syntax
_FTS_SPECIAL = re.compile(r'[^\w\s一-鿿]')


def _sanitize_fts_query(query: str) -> str:
    """Remove FTS5 special characters from query tokens."""
    tokens = query.split()
    clean = []
    for t in tokens[:10]:
        t = _FTS_SPECIAL.sub("", t)
        if t:
            clean.append(t)
    return " OR ".join(clean)


def search_screenshots(db: Session, query: str, limit: int = 10) -> list[dict]:
    """Search screenshot analyses by keyword."""
    if not query.strip():
        return []

    results = _fts_search(db, query, limit)
    if results:
        return results
    return _keyword_search(db, query, limit)


def _fts_search(db: Session, query: str, limit: int) -> list[dict]:
    try:
        check = db.execute(
            text("SELECT name FROM sqlite_master WHERE type='table' AND name='analysis_fts'")
        ).fetchone()
        if not check:
            _build_fts_index(db)
        else:
            # Check if FTS index is stale (has fewer rows than analyses)
            fts_count = db.execute(text("SELECT count(*) FROM analysis_fts")).scalar()
            analysis_count = db.execute(text("SELECT count(*) FROM analyses WHERE status = 'done'")).scalar()
            if fts_count < analysis_count:
                _build_fts_index(db)

        fts_query = _sanitize_fts_query(query)
        if not fts_query:
            return []

        rows = db.execute(
            text("""
                SELECT a.id, a.summary, a.app_name, a.content_category, a.intent,
                       a.entities, p.filename, p.original_timestamp
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
    # Use a separate session to avoid flushing the caller's pending changes
    rebuild_db = SessionLocal()
    try:
        try:
            rebuild_db.execute(text("DROP TABLE IF EXISTS analysis_fts"))
        except Exception:
            rebuild_db.rollback()
            logger.debug("FTS table did not exist, creating fresh")
        rebuild_db.execute(text("""
            CREATE VIRTUAL TABLE IF NOT EXISTS analysis_fts USING fts5(
                summary, app_name, content_category, intent, entities,
                content='analyses', content_rowid='id'
            )
        """))
        rebuild_db.execute(text("""
            INSERT INTO analysis_fts(rowid, summary, app_name, content_category, intent, entities)
            SELECT id, summary, app_name, content_category, intent, entities
            FROM analyses WHERE status = 'done'
        """))
        rebuild_db.commit()
        logger.info("FTS5 index built")
    except Exception as e:
        rebuild_db.rollback()
        logger.warning(f"Failed to build FTS index: {e}")
    finally:
        rebuild_db.close()


def _keyword_search(db: Session, query: str, limit: int) -> list[dict]:
    keywords = query.split()[:5]
    if not keywords:
        return []

    conditions = []
    params = {}
    for i, kw in enumerate(keywords):
        key = f"kw{i}"
        conditions.append(
            f"(a.summary LIKE :{key} OR a.app_name LIKE :{key} OR a.entities LIKE :{key})"
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
    ts = row[7]
    if ts and isinstance(ts, str):
        timestamp = ts  # Already a string from SQLite
    elif ts and hasattr(ts, "isoformat"):
        timestamp = ts.isoformat()
    else:
        timestamp = None
    return {
        "analysis_id": row[0],
        "summary": row[1] or "",
        "app_name": row[2] or "",
        "content_category": row[3] or "",
        "intent": row[4] or "",
        "entities": row[5] or "[]",
        "filename": row[6] or "",
        "timestamp": timestamp,
    }
