import json, uuid
from contextlib import asynccontextmanager
from fastapi import FastAPI, WebSocket, WebSocketDisconnect, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from . import db
from .ai import ai_reply, ai_summarize

# In-memory live session state: session_id -> {number, language, transcript: [str], history: [dict]}
SESSIONS: dict[str, dict] = {}


@asynccontextmanager
async def lifespan(app):
    db.init_db()
    yield


app = FastAPI(title="AI Call Assistant Backend", version="1.1.0", lifespan=lifespan)

# Allows the Android app (and any local dev tooling) to call this API.
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)


class StartSessionRequest(BaseModel):
    number: str = "unknown"
    language: str = "en"


class ChatRequest(BaseModel):
    session_id: str
    message: str


class EndSessionRequest(BaseModel):
    session_id: str


@app.get("/health")
async def health():
    return {"ok": True, "service": "ai-call-assistant"}


@app.get("/api/calls")
async def calls():
    return db.list_calls()


@app.get("/api/calls/{call_id}")
async def call_detail(call_id: str):
    call = db.get_call(call_id)
    if not call:
        raise HTTPException(status_code=404, detail="Call not found")
    return call


@app.post("/api/session/start")
async def start_session(req: StartSessionRequest):
    session_id = str(uuid.uuid4())
    SESSIONS[session_id] = {
        "number": req.number,
        "language": req.language,
        "transcript": [],
        "history": [],
    }
    db.create_call(session_id, req.number, req.language)
    return {"session_id": session_id}


@app.post("/api/chat")
async def chat(req: ChatRequest):
    session = SESSIONS.get(req.session_id)
    if not session:
        # Allow a chat call without an explicit /start (keeps things simple for quick testing).
        session = {"number": "unknown", "language": "en", "transcript": [], "history": []}
        SESSIONS[req.session_id] = session
        db.create_call(req.session_id, "unknown", "en")

    result = await ai_reply(req.message, session["language"], session["history"])

    session["transcript"].append(f"Caller: {req.message}")
    session["transcript"].append(f"AI: {result['reply']}")
    session["history"].append({"role": "user", "content": req.message})
    session["history"].append({"role": "assistant", "content": result["reply"]})

    db.update_call(
        req.session_id,
        intent=result["intent"],
        summary=f"In progress — last topic: {result['intent']}",
        transcript="\n".join(session["transcript"]),
        status="in_progress",
    )
    return result


@app.post("/api/session/end")
async def end_session(req: EndSessionRequest):
    session = SESSIONS.pop(req.session_id, None)
    if not session:
        call = db.get_call(req.session_id)
        if not call:
            raise HTTPException(status_code=404, detail="Session not found")
        return {"summary": call["summary"], "intent": call["intent"]}

    summary = await ai_summarize(session["transcript"], session["language"])
    call = db.get_call(req.session_id)
    intent = call["intent"] if call else "unknown"

    db.update_call(
        req.session_id,
        intent=intent,
        summary=summary,
        transcript="\n".join(session["transcript"]),
        status="ended",
    )
    return {"summary": summary, "intent": intent}


@app.websocket("/ws/telephony")
async def telephony(ws: WebSocket):
    """Provider-neutral real-time media bridge.

    Production provider adapters should:
    1. Authenticate the connection.
    2. Decode provider media frames to PCM.
    3. Send PCM to streaming STT.
    4. Send recognized text to the AI model.
    5. Send synthesized audio frames back in provider format.

    This endpoint currently accepts JSON text frames for development/testing.
    """
    await ws.accept()
    session_id = str(uuid.uuid4())
    transcript: list[str] = []
    history: list[dict] = []
    number = "telephony"
    language = "en"
    started = False
    try:
        while True:
            raw = await ws.receive_text()
            msg = json.loads(raw)
            if msg.get("type") == "start":
                number = msg.get("number", number)
                language = msg.get("language", "en")
                db.create_call(session_id, number, language)
                started = True
                await ws.send_text(json.dumps({"type": "ready", "session_id": session_id}))
            elif msg.get("type") == "text":
                if not started:
                    db.create_call(session_id, number, language)
                    started = True
                text = msg.get("text", "")
                if not text:
                    continue
                transcript.append("Caller: " + text)
                result = await ai_reply(text, language, history)
                transcript.append("AI: " + result["reply"])
                history.append({"role": "user", "content": text})
                history.append({"role": "assistant", "content": result["reply"]})
                db.update_call(
                    session_id,
                    intent=result["intent"],
                    summary=f"In progress — last topic: {result['intent']}",
                    transcript="\n".join(transcript),
                    status="in_progress",
                )
                await ws.send_text(json.dumps({
                    "type": "reply",
                    "text": result["reply"],
                    "intent": result["intent"],
                }))
            elif msg.get("type") == "end":
                if started:
                    summary = await ai_summarize(transcript, language)
                    call = db.get_call(session_id)
                    intent = call["intent"] if call else "unknown"
                    db.update_call(session_id, intent=intent, summary=summary,
                                    transcript="\n".join(transcript), status="ended")
                await ws.send_text(json.dumps({"type": "ended"}))
                break
    except WebSocketDisconnect:
        if started:
            summary = await ai_summarize(transcript, language)
            call = db.get_call(session_id)
            intent = call["intent"] if call else "unknown"
            db.update_call(session_id, intent=intent, summary=summary,
                            transcript="\n".join(transcript), status="ended")
