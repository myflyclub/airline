"""WebSocket endpoint for real-time game updates and chat."""
from fastapi import APIRouter, WebSocket, WebSocketDisconnect
from typing import Dict
import json

router = APIRouter(tags=["websocket"])


class ConnectionManager:
    """Manages WebSocket connections for real-time features."""

    def __init__(self):
        self.active_connections: Dict[int, WebSocket] = {}
        self.chat_connections: list[WebSocket] = []

    async def connect(self, websocket: WebSocket, user_id: int = 0):
        await websocket.accept()
        if user_id:
            self.active_connections[user_id] = websocket
        self.chat_connections.append(websocket)

    def disconnect(self, websocket: WebSocket, user_id: int = 0):
        if user_id and user_id in self.active_connections:
            del self.active_connections[user_id]
        if websocket in self.chat_connections:
            self.chat_connections.remove(websocket)

    async def send_personal(self, user_id: int, message: dict):
        ws = self.active_connections.get(user_id)
        if ws:
            await ws.send_json(message)

    async def broadcast(self, message: dict):
        for ws in self.chat_connections:
            try:
                await ws.send_json(message)
            except Exception:
                pass

    async def broadcast_game_event(self, event_type: str, data: dict):
        await self.broadcast({"type": event_type, "data": data})


manager = ConnectionManager()


@router.websocket("/ws/{user_id}")
async def websocket_endpoint(websocket: WebSocket, user_id: int):
    await manager.connect(websocket, user_id)
    try:
        while True:
            data = await websocket.receive_text()
            try:
                message = json.loads(data)
                msg_type = message.get("type", "chat")

                if msg_type == "chat":
                    await manager.broadcast({
                        "type": "chat",
                        "user_id": user_id,
                        "username": message.get("username", f"Player {user_id}"),
                        "text": message.get("text", ""),
                    })
                elif msg_type == "ping":
                    await websocket.send_json({"type": "pong"})
            except json.JSONDecodeError:
                pass
    except WebSocketDisconnect:
        manager.disconnect(websocket, user_id)
