"""FastAPI entry point for RAG chatbot and agentic AI features.

@author SA62 Team
"""

from fastapi import FastAPI

from app.agent_service import AgentService
from app.backend_client import BackendClient
from app.config import get_settings
from app.models import RagChatRequest, RagChatResponse, RecommendationResponse
from app.ollama_client import OllamaClient
from app.rag_service import RagService

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
    return await rag.reindex()


@app.post("/rag/chat", response_model=RagChatResponse)
async def chat(request: RagChatRequest) -> RagChatResponse:
    return await rag.chat(request)


@app.post("/agent/recommendation/{user_id}", response_model=RecommendationResponse)
async def recommendation(user_id: int) -> RecommendationResponse:
    return await agent.generate_recommendation(user_id)

