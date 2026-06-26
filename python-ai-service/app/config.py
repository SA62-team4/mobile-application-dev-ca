"""Configuration for the Python AI service.

@author SA62 Team
"""

from functools import lru_cache
from pydantic import Field
from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    ollama_base_url: str = Field("http://localhost:11434", alias="OLLAMA_BASE_URL")
    generation_model: str = Field("llama3.2:3b", alias="OLLAMA_GENERATION_MODEL")
    embedding_model: str = Field("nomic-embed-text", alias="OLLAMA_EMBEDDING_MODEL")
    chroma_persist_dir: str = Field("./chroma-data", alias="CHROMA_PERSIST_DIR")
    knowledge_base_dir: str = Field("../rag-knowledge-base", alias="KNOWLEDGE_BASE_DIR")
    backend_base_url: str = Field("http://localhost:8080", alias="BACKEND_BASE_URL")
    internal_service_token: str = Field("dev_internal_token", alias="INTERNAL_SERVICE_TOKEN")


@lru_cache
def get_settings() -> Settings:
    return Settings()
