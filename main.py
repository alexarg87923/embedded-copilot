from fastapi import FastAPI, Depends, HTTPException
from fastapi.responses import HTMLResponse
from pydantic import BaseModel
import os
from ai_providers import ClaudeProvider
from dotenv import load_dotenv
from google.adk.models.registry import LLMRegistry
from google.adk.models.anthropic_llm import Claude

class ChatMessage(BaseModel):
  message: str

app = FastAPI()
load_dotenv()

LLMRegistry.register(Claude)

ai_provider = ClaudeProvider(
    api_key=os.getenv("ANTHROPIC_API_KEY"),
    model_name="claude-3-7-sonnet-20250219"
)

def get_ai_provider() -> ClaudeProvider:
  return ai_provider

@app.get("/", response_class=HTMLResponse)
async def read_root():
  with open("./static/index.html", "r") as f:
      return HTMLResponse(content=f.read())

@app.post("/chat")
async def chat_endpoint(
    chat_message: ChatMessage, 
    ai: ClaudeProvider = Depends(get_ai_provider)
):
  """
  Chat endpoint that uses injected AI provider
  """
  try:
    response = await ai.generate_response(message=chat_message.message)
    return {"response": response}

  except HTTPException:
      raise
  except Exception as e:
      raise HTTPException(
          status_code=500, 
          detail=f"Unexpected error: {str(e)}"
      )
