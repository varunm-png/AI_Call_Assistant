"""
Provider adapter boundary.

A real PSTN AI assistant needs a telephony provider capable of:
- receiving/forwarding the phone call
- answering it
- streaming caller audio to a WebSocket
- accepting synthesized audio from the WebSocket

Implement your provider here. Keep provider credentials on the server.
Do not put them in the Android application.
"""
class TelephonyProvider:
    async def answer(self, call_id: str):
        raise NotImplementedError

    async def hangup(self, call_id: str):
        raise NotImplementedError
