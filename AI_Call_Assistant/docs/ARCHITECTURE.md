# Architecture

```text
                     ┌──────────────────────┐
Cellular / VoIP ---> │ Telephony Provider   │
                     │ Media Stream         │
                     └──────────┬───────────┘
                                │ WebSocket
                                ▼
                     ┌──────────────────────┐
                     │ FastAPI Backend      │
                     │                      │
                     │ STT                  │
                     │ AI Brain             │
                     │ Intent Classifier    │
                     │ TTS                  │
                     │ Call Summary         │
                     └──────────┬───────────┘
                                │ REST/WebSocket
                                ▼
                     ┌──────────────────────┐
                     │ Android App          │
                     │ Transcript           │
                     │ Join/Take Over UI    │
                     │ History              │
                     └──────────────────────┘
```

The Android `CallScreeningService` handles call identification/screening. The true AI voice bridge is server-side.

For a production deployment, add:
- authenticated WebSocket connections
- TLS/WSS
- encrypted recordings
- consent/recording announcements where legally required
- rate limiting
- user accounts
- PostgreSQL
- background job queue
- provider-specific telephony adapter
