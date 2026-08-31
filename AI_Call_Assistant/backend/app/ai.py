import os, json, httpx

SYSTEM = """You are an AI phone secretary answering a call on behalf of the phone's owner.
Be concise, warm and useful. Never claim to be a human.
If the caller asks for sensitive account information, say the owner must handle it directly.
Always reply with strict JSON only, no extra text, with keys: reply, intent.
intent must be exactly one of: important, business, delivery, appointment, spam, personal, unknown.
"""

SUMMARY_SYSTEM = """You summarize a phone call transcript in 1-2 short sentences for the phone
owner to read later. Be factual and specific about what the caller wanted. Reply with plain text only,
no JSON, no preamble."""


def demo_reply(message: str, language: str):
    m = message.lower()
    if any(x in m for x in ["delivery", "parcel", "courier", "order"]):
        intent = "delivery"
        reply = "I can note that this is about a delivery. Please tell me the order or reference number."
    elif any(x in m for x in ["meeting", "appointment", "schedule"]):
        intent = "appointment"
        reply = "I can note the appointment request. Please tell me the preferred date and time."
    elif any(x in m for x in ["offer", "loan", "credit", "promotion", "insurance"]):
        intent = "spam"
        reply = "Thank you. I will record the purpose of your call."
    else:
        intent = "unknown"
        reply = "Thank you for calling. Please briefly tell me why you are calling."
    if language == "hi":
        translations = {
            "delivery": "मैं नोट कर सकता हूँ कि यह डिलीवरी के बारे में है। कृपया ऑर्डर या रेफरेंस नंबर बताइए।",
            "appointment": "मैं अपॉइंटमेंट का अनुरोध नोट कर सकता हूँ। कृपया तारीख और समय बताइए।",
            "spam": "धन्यवाद। मैं आपके कॉल का उद्देश्य रिकॉर्ड कर रहा हूँ।",
            "unknown": "धन्यवाद। कृपया संक्षेप में बताइए कि आप किस कारण से फोन कर रहे हैं।",
        }
        reply = translations[intent]
    return {"reply": reply, "intent": intent}


def _ai_configured():
    base = os.getenv("AI_BASE_URL", "").strip()
    key = os.getenv("AI_API_KEY", "").strip()
    model = os.getenv("AI_MODEL", "").strip()
    return base, key, model


async def ai_reply(message: str, language: str, history: list[dict] | None = None):
    base, key, model = _ai_configured()
    if not (base and key and model):
        return demo_reply(message, language)

    messages = [
        {"role": "system", "content": SYSTEM + f"\nRespond in {'Hindi' if language == 'hi' else 'English'}."}
    ]
    for turn in (history or [])[-8:]:
        messages.append(turn)
    messages.append({"role": "user", "content": message})

    payload = {"model": model, "messages": messages, "temperature": 0.2}
    async with httpx.AsyncClient(timeout=30) as client:
        r = await client.post(
            base.rstrip("/") + "/chat/completions",
            headers={"Authorization": f"Bearer {key}"},
            json=payload,
        )
        r.raise_for_status()
        content = r.json()["choices"][0]["message"]["content"]
    try:
        obj = json.loads(content)
        return {"reply": obj.get("reply", content), "intent": obj.get("intent", "unknown")}
    except Exception:
        return {"reply": content, "intent": "unknown"}


async def ai_summarize(transcript_lines: list[str], language: str):
    base, key, model = _ai_configured()
    if not transcript_lines:
        return "No conversation recorded."
    if not (base and key and model):
        # Demo mode: naive summary from the transcript we already have.
        caller_lines = [l.split(":", 1)[1].strip() for l in transcript_lines if l.startswith("Caller:")]
        if not caller_lines:
            return "Call ended with no caller input."
        return f"Caller said: {'; '.join(caller_lines[:3])}" + (" ..." if len(caller_lines) > 3 else "")

    payload = {
        "model": model,
        "messages": [
            {"role": "system", "content": SUMMARY_SYSTEM + f"\nRespond in {'Hindi' if language == 'hi' else 'English'}."},
            {"role": "user", "content": "\n".join(transcript_lines)},
        ],
        "temperature": 0.2,
    }
    async with httpx.AsyncClient(timeout=30) as client:
        r = await client.post(
            base.rstrip("/") + "/chat/completions",
            headers={"Authorization": f"Bearer {key}"},
            json=payload,
        )
        r.raise_for_status()
        return r.json()["choices"][0]["message"]["content"].strip()
