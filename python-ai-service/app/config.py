"""Configuration for the Python AI service.

@author Zhong Cheng
"""

from functools import lru_cache
from pydantic import Field
from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    ollama_base_url: str = Field("http://localhost:11434", alias="OLLAMA_BASE_URL")
    # llama3.2:1b is ~2-3x faster than 3b on CPU-only hosts; override via env for quality.
    generation_model: str = Field("llama3.2:1b", alias="OLLAMA_GENERATION_MODEL")
    embedding_model: str = Field("nomic-embed-text:latest", alias="OLLAMA_EMBEDDING_MODEL")
    chroma_persist_dir: str = Field("./chroma-data", alias="CHROMA_PERSIST_DIR")
    knowledge_base_dir: str = Field("../rag-knowledge-base", alias="KNOWLEDGE_BASE_DIR")
    backend_base_url: str = Field("http://localhost:8080", alias="BACKEND_BASE_URL")
    internal_service_token: str = Field("dev_internal_token", alias="INTERNAL_SERVICE_TOKEN")

    # LangSmith tracing. When enabled, LangChain runs (the recommendation chain)
    # are exported to LangSmith for observability. Disabled by default so the
    # service runs fully local/offline without an API key.
    langsmith_tracing: bool = Field(False, alias="LANGSMITH_TRACING")
    langsmith_api_key: str = Field("", alias="LANGSMITH_API_KEY")
    langsmith_project: str = Field("wellness-agentic-ai", alias="LANGSMITH_PROJECT")
    langsmith_endpoint: str = Field("https://api.smith.langchain.com", alias="LANGSMITH_ENDPOINT")


@lru_cache
def get_settings() -> Settings:
    return Settings()
