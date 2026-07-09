"""Configuration for the Python AI service.

@author Tiong Zhong Cheng, edited by Tang Chee Seng
"""

from functools import lru_cache
from pydantic import Field
from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    ollama_base_url: str = Field("http://ollama:11434", alias="OLLAMA_BASE_URL")
    # Connecting to Chee Seng's local Gemma4:e4b model, routed through Droplet to his PC as local server.
    generation_model: str = Field("gemma4:e4b", alias="OLLAMA_GENERATION_MODEL")
#   backend_base_url: str = Field("http://localhost:8080", alias="BACKEND_BASE_URL")
#   internal_service_token: str = Field("dev_internal_token", alias="INTERNAL_SERVICE_TOKEN")

    # LangSmith tracing. When enabled, LangChain runs (the recommendation chain)
    # are exported to LangSmith for observability. Disabled by default so the
    # service runs fully local/offline without an API key.
    langsmith_tracing: bool = Field(True, alias="LANGSMITH_TRACING")
    langsmith_api_key: str = Field("", alias="LANGSMITH_API_KEY_PREMIUM_AGENT")
    langsmith_project: str = Field("WeatherAgent", alias="LANGSMITH_PROJECT")
    langsmith_endpoint: str = Field("https://api.smith.langchain.com", alias="LANGSMITH_ENDPOINT")


@lru_cache
def get_settings() -> Settings:
    return Settings()
