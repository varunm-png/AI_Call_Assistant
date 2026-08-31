# Windows setup

## 1. Android Studio
Install Android Studio and JDK 17.

Open:
`AI_Call_Assistant\android-app`

Create/run an emulator or connect an Android phone with USB debugging.

## 2. Backend

Install Python 3.11+.

Open Command Prompt:

```bat
cd AI_Call_Assistant\backend
python -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
copy .env.example .env
uvicorn app.main:app --host 0.0.0.0 --port 8000
```

## 3. Phone

If testing on a physical Android phone, change the Android `API_BASE_URL` to the PC's LAN IP.

Example:
`http://192.168.1.20:8000/`

Allow port 8000 through Windows Firewall if necessary.

## 4. Call screening

Open the app and select **Enable Call Screening**. Android will show the system role screen.

The app immediately allows calls in its screening service. Classification and AI conversation are intentionally separated from the 5-second screening decision.

## 5. True AI cellular call

For a production system, configure call forwarding/telephony media streaming to the FastAPI `/ws/telephony` endpoint. The provider adapter must convert the provider's media format to the streaming STT/TTS format used by your chosen AI services.
