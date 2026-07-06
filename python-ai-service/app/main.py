"""FastAPI entry point for RAG chatbot and agentic AI features.

@author Zhong Cheng
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
from app.tracing import configure_tracing

logger = logging.getLogger("wellness.ai")

settings = get_settings()
# Configure LangSmith before building any LangChain runnables so their runs are traced.
configure_tracing(settings)
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
        # The recommendation workflow may fail while calling Spring internal APIs
        # or local Ollama. Surface a clear, actionable error instead of a blank 500.
        logger.exception("Recommendation agent workflow failed for backend %s or Ollama %s",
                         settings.backend_base_url, settings.ollama_base_url)
        raise HTTPException(
            status_code=502,
            detail=(
                "Recommendation agent workflow failed while calling Spring internal APIs "
                f"or local Ollama: {exc}"
            ),
        ) from exc
