from abc import ABC, abstractmethod
from fastapi import HTTPException
import asyncio
import logging
from pathlib import Path
from litellm.exceptions import InternalServerError
import uuid
from google.adk.agents import LlmAgent
from google.adk.models.lite_llm import LiteLlm
from google.adk.runners import Runner
from google.adk.sessions import InMemorySessionService
from google.genai import types
from litellm.exceptions import InternalServerError
import litellm

litellm._turn_on_debug()

class AIProvider(ABC):
    @abstractmethod
    async def generate_response(self, message: str, **kwargs) -> str:
        pass

class ClaudeProvider(AIProvider):
    def __init__(self, api_key: str, model_name: str):
        self.api_key = api_key
        self.model_name = model_name
        self.session_service = InMemorySessionService()
        self.system_prompt = self._load_system_prompt()
        self._configure_client()

    def _load_system_prompt(self) -> str:
        """Load system prompt from ./prompts/system_prompt file"""
        try:
            prompt_path = Path("./prompts/system_prompt")
            if prompt_path.exists():
                with open(prompt_path, 'r', encoding='utf-8') as f:
                    prompt = f.read().strip()
                    logging.info(f"Loaded system prompt from {prompt_path}")
                    return prompt
            else:
                logging.warning(f"System prompt file not found at {prompt_path}, using default")
                return "You are an assistant powered by Claude."
        except Exception as e:
            logging.error(f"Failed to load system prompt: {e}")
            return "You are an assistant powered by Claude."

    def _configure_client(self):
        try:
            self.agent = LlmAgent(
                model=LiteLlm(model=self.model_name),
                name="claude_agent",
                instruction=self.system_prompt,
                generate_content_config=types.GenerateContentConfig(
                    temperature=0.7,
                    max_output_tokens=2048,
                ),
            )
            self.runner = Runner(agent=self.agent, app_name="my_app", session_service=self.session_service)
            logging.info(f"Claude configured with ADK (model={self.model_name})")
        except Exception as e:
            logging.error(f"Failed to configure Claude (ADK): {e}")
            raise HTTPException(status_code=500, detail="Failed to initialize Claude provider")

    async def generate_response(self, message: str, **kwargs) -> str:
        try:
            session_id = str(uuid.uuid4())
            user_id = "user1"

            await self.session_service.create_session(
                app_name="my_app",
                user_id=user_id,
                session_id=session_id
            )

            content = types.Content(role="user", parts=[types.Part(text=message)])
            loop = asyncio.get_event_loop()
            try:
                events = await loop.run_in_executor(
                    None,
                    lambda: self.runner.run(
                        user_id=user_id,
                        session_id=session_id,
                        new_message=content
                    )
                )
            except InternalServerError as e:
                logging.error(f"Anthropic 500 error: {e}")
                raise HTTPException(status_code=502, detail="Claude backend error, please retry")
            for event in events:
                if event.is_final_response() and event.content:
                    return event.content.parts[0].text
            return "[No response]"

        except Exception as e:
            logging.error(f"Claude (ADK) error: {e}")
            raise HTTPException(status_code=500, detail=f"Claude generation error: {str(e)}")