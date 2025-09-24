from fastapi import FastAPI
from fastapi.responses import HTMLResponse
from pydantic import BaseModel

app = FastAPI()

@app.get("/", response_class=HTMLResponse)
async def read_root():
    with open("./static/index.html", "r") as f:
        return HTMLResponse(content=f.read())

class ChatMessage(BaseModel):
    message: str

@app.post("/chat")
async def chat_endpoint(chat_message: ChatMessage):
    response = f"Echo: {chat_message.message}"
    return {"response": response}