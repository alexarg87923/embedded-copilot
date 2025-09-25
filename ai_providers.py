from abc import ABC, abstractmethod
from typing import Optional
from fastapi import HTTPException
import asyncio
import logging

from google.adk.agents import LlmAgent
from google.adk.models.lite_llm import LiteLlm
from google.adk.runners import Runner
from google.adk.sessions import InMemorySessionService
from google.genai import types


class AIProvider(ABC):
    @abstractmethod
    async def generate_response(self, message: str, **kwargs) -> str:
        pass


class ClaudeProvider(AIProvider):
    def __init__(self, api_key: str, model_name: str = "anthropic/claude-3-5-sonnet-latest"):
        self.api_key = api_key
        self.model_name = model_name
        self.session_service = InMemorySessionService()
        self._configure_client()

    def _configure_client(self):
        try:
            self.agent = LlmAgent(
                model=LiteLlm(model=self.model_name),
                name="claude_agent",
                instruction="You are an assistant powered by Claude.",
                generate_content_config=types.GenerateContentConfig(
                    temperature=0.7,
                    max_output_tokens=500,
                ),
            )
            self.runner = Runner(agent=self.agent, app_name="my_app", session_service=self.session_service)
            logging.info(f"Claude configured with ADK (model={self.model_name})")
        except Exception as e:
            logging.error(f"Failed to configure Claude (ADK): {e}")
            raise HTTPException(status_code=500, detail="Failed to initialize Claude provider")

    async def generate_response(
        self,
        message: str,
        **kwargs
    ) -> str:
        try:
            session_id = "session1"
            user_id = "user1"

            await self.session_service.create_session(app_name="my_app", user_id=user_id, session_id=session_id)

            content = types.Content(role="user", parts=[types.Part(text=message)])
            loop = asyncio.get_event_loop()
            events = await loop.run_in_executor(
                None,
                lambda: self.runner.run(
                    user_id=user_id,
                    session_id=session_id,
                    new_message=content
                )
            )
            for event in events:
                if event.is_final_response() and event.content:
                    return event.content.parts[0].text
            return "[No response]"
        except Exception as e:
            logging.error(f"Claude (ADK) error: {e}")
            raise HTTPException(status_code=500, detail=f"Claude generation error: {str(e)}")