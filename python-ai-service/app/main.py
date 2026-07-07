"""FastAPI entry point for RAG chatbot and agentic AI features.

@author Tiong Zhong Cheng
"""

import logging

import json

import httpx
from fastapi import FastAPI, HTTPException
from fastapi.responses import StreamingResponse

from app.agent_service import AgentService
from app.backend_client import BackendClient
from app.config import get_settings
from app.models import RagChatRequest, RagChatResponse, RecommendationResponse
from app.ollama_client import OllamaClient
from app.rag_service import RagService
from app.tracing import configure_tracing

logger = logging.getLogger("wellness.ai")

settings = get_settings()
# Configure tracing before constructing services.
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
        # Reindex uses Ollama embeddings.
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
        # Return a clear Ollama failure.
        logger.exception("Chat could not reach the AI model service at %s", settings.ollama_base_url)
        raise HTTPException(
            status_code=502,
            detail=f"Could not reach the AI model service (Ollama) at {settings.ollama_base_url}: {exc}",
        ) from exc


@app.post("/rag/chat/stream")
async def chat_stream(request: RagChatRequest) -> StreamingResponse:
    async def event_stream():
        try:
            async for frame in rag.chat_stream(request):
                yield frame
        except httpx.HTTPError as exc:
            # Streaming responses can only report failure as an SSE event.
            logger.exception("Chat stream could not reach the AI model service at %s", settings.ollama_base_url)
            message = f"Could not reach the AI model service (Ollama) at {settings.ollama_base_url}: {exc}"
            yield f"data: {json.dumps({'type': 'error', 'message': message})}\n\n"

    return StreamingResponse(event_stream(), media_type="text/event-stream")


@app.post("/agent/recommendation/{user_id}", response_model=RecommendationResponse)
async def recommendation(user_id: int) -> RecommendationResponse:
    try:
        return await agent.generate_recommendation(user_id)
    except httpx.HTTPError as exc:
        # Return a clear Spring/Ollama failure.
        logger.exception("Recommendation agent workflow failed for backend %s or Ollama %s",
                         settings.backend_base_url, settings.ollama_base_url)
        raise HTTPException(
            status_code=502,
            detail=(
                "Recommendation agent workflow failed while calling Spring internal APIs "
                f"or local Ollama: {exc}"
            ),
        ) from exc
