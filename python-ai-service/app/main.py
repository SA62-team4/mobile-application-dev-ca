"""FastAPI entry point for RAG chatbot and agentic AI features.

@author SA62 Team
"""

import logging

import httpx
from fastapi import FastAPI, HTTPException

from app.agent_service import AgentService
from app.backend_client import BackendClient
from app.config import get_settings
from app.models import RagChatRequest, RagChatResponse, RecommendationResponse
from app.ollama_client import OllamaClient
from app.rag_service import RagService

logger = logging.getLogger("wellness.ai")

settings = get_settings()
ollama = OllamaClient(settings)
rag = RagService(settings, ollama)
backend = BackendClient(settings)
agent = AgentService(backend, rag, ollama)

app = FastAPI(title="Wellness AI Service", version="0.0.1")


@app.get("/health")
async def health() -> dict[str, str]:
    return {"status": "UP"}


@app.post("/rag/reindex")
async def reindex() -> dict[str, int]:
    try:
        return await rag.reindex()
    except httpx.HTTPError as exc:
        # Reindexing embeds every knowledge-base chunk through Ollama.
        logger.exception("Reindex could not reach the AI model service at %s", settings.ollama_base_url)
        raise HTTPException(
            status_code=502,
            detail=f"Could not reach the AI model service (Ollama) at {settings.ollama_base_url}: {exc}",
        ) from exc


@app.post("/rag/chat", response_model=RagChatResponse)
async def chat(request: RagChatRequest) -> RagChatResponse:
    try:
        return await rag.chat(request)
    except httpx.HTTPError as exc:
        # Chat embeds the question and generates an answer through Ollama; surface a
        # clear, actionable error instead of a blank 500 when it cannot be reached.
        logger.exception("Chat could not reach the AI model service at %s", settings.ollama_base_url)
        raise HTTPException(
            status_code=502,
            detail=f"Could not reach the AI model service (Ollama) at {settings.ollama_base_url}: {exc}",
        ) from exc


@app.post("/agent/recommendation/{user_id}", response_model=RecommendationResponse)
async def recommendation(user_id: int) -> RecommendationResponse:
    try:
        return await agent.generate_recommendation(user_id)
    except httpx.HTTPError as exc:
        # The agent must call back to the Spring Boot backend (recent records and
        # saving the recommendation). Surface a clear, actionable error instead of a
        # blank 500 when that backend cannot be reached or returns an error.
        logger.exception("Recommendation agent could not reach the backend at %s", settings.backend_base_url)
        raise HTTPException(
            status_code=502,
            detail=f"Recommendation agent could not reach the backend at {settings.backend_base_url}: {exc}",
        ) from exc

