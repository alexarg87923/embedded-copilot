from abc import ABC, abstractmethod
from typing import Optional
from fastapi import HTTPException
import asyncio
import logging
from google.ai.generativelanguage import GenerativeModel
import google.ai.generativelanguage as glm



class AIProvider(ABC):
    @abstractmethod
    async def generate_response(self, message: str, **kwargs) -> str:
        pass


class ClaudeProvider(AIProvider):
    def __init__(self, api_key: str, model_name: str = "claude-3-sonnet-20240229"):
        """
        Initialize Claude provider via Google ADK.
        
        Args:
            api_key: Your Anthropic API key
            model_name: Claude model (default: claude-3-sonnet-20240229)
        """
        self.api_key = api_key
        self.model_name = model_name
        self._configure_client()
    
    def _configure_client(self):
        """Configure the Claude client via ADK"""
        try:
            glm.configure(
                api_key=self.api_key,
                provider="anthropic",
            )
            self.model = GenerativeModel(self.model_name)
            logging.info(f"Claude configured with model: {self.model_name}")
        except Exception as e:
            logging.error(f"Failed to configure Claude: {e}")
            raise HTTPException(status_code=500, detail="Failed to initialize Claude provider")
    
    async def generate_response(
        self, 
        message: str, 
        temperature: float = 0.7,
        max_tokens: Optional[int] = 500,
        **kwargs
    ) -> str:
        """
        Generate response from Claude via ADK
        """
        try:
            generation_config = {
                "temperature": temperature,
                "max_output_tokens": max_tokens,
            }
            
            loop = asyncio.get_event_loop()
            response = await loop.run_in_executor(
                None, 
                lambda: self.model.generate_content(
                    message,
                    generation_config=generation_config
                )
            )
            
            return response.text
        except Exception as e:
            logging.error(f"Error generating response from Claude: {e}")
            raise HTTPException(
                status_code=500, 
                detail=f"Claude generation error: {str(e)}"
            )

def create_ai_provider(provider_type: str = "claude", **config) -> AIProvider:
    if provider_type.lower() == "claude":
        api_key = config.get("api_key")
        if not api_key:
            raise ValueError("Claude requires 'api_key' in config")
        
        model_name = config.get("model_name", "claude-3-sonnet-20240229")
        return ClaudeProvider(api_key=api_key, model_name=model_name)
    
    else:
        raise ValueError(f"Unsupported provider type: {provider_type}")