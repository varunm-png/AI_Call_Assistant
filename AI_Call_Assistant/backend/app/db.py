import os, sqlite3
from datetime import datetime, timezone

DB_PATH = os.getenv("DB_PATH", "./data/calls.db")


def _connect():
    os.makedirs(os.path.dirname(DB_PATH) or ".", exist_ok=True)
    return sqlite3.connect(DB_PATH)


def init_db():
    with _connect() as db:
        db.execute("""
        CREATE TABLE IF NOT EXISTS calls (
            id TEXT PRIMARY KEY,
            number TEXT NOT NULL,
            language TEXT NOT NULL,
            intent TEXT NOT NULL,
            summary TEXT NOT NULL,
            transcript TEXT NOT NULL,
            status TEXT NOT NULL DEFAULT 'in_progress',
            created_at TEXT NOT NULL
        )
        """)
        db.commit()


def create_call(session_id: str, number: str, language: str):
    """Create a new call row when a session starts."""
    created = datetime.now(timezone.utc).isoformat()
    with _connect() as db:
        db.execute(
            "INSERT INTO calls (id, number, language, intent, summary, transcript, status, created_at) "
            "VALUES (?,?,?,?,?,?,?,?)",
            (session_id, number, language, "unknown", "Call in progress...", "", "in_progress", created),
        )
        db.commit()


def update_call(session_id: str, intent: str, summary: str, transcript: str, status: str = "in_progress"):
    """Update an existing call row as the conversation progresses / ends.
    created_at is intentionally left untouched."""
    with _connect() as db:
        db.execute(
            "UPDATE calls SET intent=?, summary=?, transcript=?, status=? WHERE id=?",
            (intent, summary, transcript, status, session_id),
        )
        db.commit()


def get_call(session_id: str):
    with _connect() as db:
        row = db.execute(
            "SELECT id,number,language,intent,summary,transcript,status,created_at FROM calls WHERE id=?",
            (session_id,),
        ).fetchone()
    if not row:
        return None
    return {
        "id": row[0], "number": row[1], "language": row[2], "intent": row[3],
        "summary": row[4], "transcript": row[5], "status": row[6], "created_at": row[7],
    }


def list_calls(limit=100):
    with _connect() as db:
        rows = db.execute(
            "SELECT id,number,summary,intent,status,created_at FROM calls ORDER BY created_at DESC LIMIT ?",
            (limit,),
        ).fetchall()
    return [
        {"id": r[0], "number": r[1], "summary": r[2], "intent": r[3], "status": r[4], "created_at": r[5]}
        for r in rows
    ]
