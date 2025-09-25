from abc import ABC, abstractmethod
from typing import Optional, Dict, Any
import anthropic
from fastapi import HTTPException
import asyncio
import logging

# Abstract base class for AI providers
class AIProvider(ABC):
    @abstractmethod
    async def generate_response(self, message: str, **kwargs) -> str:
        pass

class ClaudeProvider(AIProvider):
    def __init__(self, api_key: str, model_name: str = "claude-3-5-sonnet-20241022"):
        """
        Initialize Claude provider
        
        Args:
            api_key: Your Anthropic API key
            model_name: Model to use (default: claude-3-5-sonnet-20241022)
        """
        self.api_key = api_key
        self.model_name = model_name
        self._configure_client()
    
    def _configure_client(self):
        """Configure the Anthropic client"""
        try:
            self.client = anthropic.AsyncAnthropic(api_key=self.api_key)
            logging.info(f"Claude configured with model: {self.model_name}")
        except Exception as e:
            logging.error(f"Failed to configure Claude: {e}")
            raise HTTPException(status_code=500, detail="Failed to initialize AI provider")
    
    async def generate_response(
        self, 
        message: str, 
        temperature: float = 0.7,
        max_tokens: Optional[int] = 1024,
        **kwargs
    ) -> str:
        """
        Generate response using Claude
        
        Args:
            message: User's message
            temperature: Sampling temperature (0-1)
            max_tokens: Maximum tokens to generate
            **kwargs: Additional parameters
            
        Returns:
            Generated response string
        """
        try:
            response = await self.client.messages.create(
                model=self.model_name,
                max_tokens=max_tokens or 1024,
                temperature=temperature,
                messages=[
                    {
                        "role": "user",
                        "content": message
                    }
                ]
            )
            
            return response.content[0].text
            
        except Exception as e:
            logging.error(f"Error generating response: {e}")
            raise HTTPException(
                status_code=500, 
                detail=f"Failed to generate response: {str(e)}"
            )

# Factory function for easy dependency injection
def create_ai_provider(provider_type: str = "claude", **config) -> AIProvider:
    """
    Factory function to create AI providers
    
    Args:
        provider_type: Type of provider ("claude", etc.)
        **config: Provider-specific configuration
        
    Returns:
        AIProvider instance
    """
    if provider_type.lower() == "claude":
        api_key = config.get("api_key")
        if not api_key:
            raise ValueError("Claude requires 'api_key' in config")
        
        model_name = config.get("model_name", "claude-3-5-sonnet-20241022")
        return ClaudeProvider(api_key=api_key, model_name=model_name)
    
    else:
        raise ValueError(f"Unsupported provider type: {provider_type}")