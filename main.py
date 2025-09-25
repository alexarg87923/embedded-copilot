from fastapi import FastAPI, Depends, HTTPException
from fastapi.responses import HTMLResponse
from pydantic import BaseModel
import os
from ai_providers import create_ai_provider, AIProvider
from dotenv import load_dotenv
from typing import Optional

app = FastAPI()

load_dotenv()

AI_CONFIG = {
    "provider_type": "claude",
    "api_key": os.getenv("ANTHROPIC_API_KEY"),
    "model_name": "claude-3-5-sonnet-20241022"
}

ai_provider = create_ai_provider(**AI_CONFIG)

def get_ai_provider() -> AIProvider:
    return ai_provider

@app.get("/", response_class=HTMLResponse)
async def read_root():
    with open("index.html", "r") as f:
        return HTMLResponse(content=f.read())

class ChatMessage(BaseModel):
    message: str
    temperature: float = 0.7
    max_tokens: Optional[int] = None

@app.post("/chat")
async def chat_endpoint(
    chat_message: ChatMessage, 
    ai: AIProvider = Depends(get_ai_provider)
):
    """
    Chat endpoint that uses injected AI provider
    """
    try:
        response = await ai.generate_response(
            message=chat_message.message,
            temperature=chat_message.temperature,
            max_tokens=chat_message.max_tokens
        )
        return {"response": response}
    
    except HTTPException:
        # Re-raise HTTP exceptions
        raise
    except Exception as e:
        # Handle unexpected errors
        raise HTTPException(
            status_code=500, 
            detail=f"Unexpected error: {str(e)}"
        )