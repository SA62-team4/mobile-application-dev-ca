"""Basic local RAG pipeline using Ollama embeddings and Chroma.

@author Tiong Zhong Cheng
"""

import json
from collections.abc import AsyncIterator

import chromadb
from fastapi import HTTPException
from langsmith import traceable

from app.config import Settings
from app.knowledge_base import KnowledgeChunk, load_chunks
from app.models import RagChatRequest, RagChatResponse, SourceSnippet
from app.ollama_client import OllamaClient
from app.tracing import strip_self


def _sse(payload: dict) -> str:
    """Format a payload as a single Server-Sent Events ``data:`` frame."""
    return f"data: {json.dumps(payload)}\n\n"


def _reduce_stream_answer(frames: list[str]) -> str:
    """Rebuild the full answer from streamed SSE frames for a readable trace."""
    parts: list[str] = []
    for frame in frames:
        payload = frame.removeprefix("data:").strip()
        if not payload:
            continue
        chunk = json.loads(payload)
        if chunk.get("type") == "token":
            parts.append(chunk.get("text", ""))
    return "".join(parts)


class RagService:
    def __init__(self, settings: Settings, ollama: OllamaClient):
        self.settings = settings
        self.ollama = ollama
        self.client = chromadb.PersistentClient(path=settings.chroma_persist_dir)
        self.collection = self.client.get_or_create_collection("wellness_kb")

    async def reindex(self) -> dict[str, int]:
        chunks = load_chunks(self.settings.knowledge_base_dir)
        if not chunks:
            raise HTTPException(status_code=400, detail="No knowledge base files found")

        existing = self.collection.get()
        ids = existing.get("ids", [])
        if ids:
            self.collection.delete(ids=ids)

        for chunk in chunks:
            embedding = await self.ollama.embed(chunk.text)
            self.collection.add(
                ids=[chunk.id],
                embeddings=[embedding],
                documents=[chunk.text],
                metadatas=[{
                    "title": chunk.title,
                    "source_file": chunk.source_file,
                    "chunk_index": chunk.chunk_index,
                    "snippet": chunk.snippet,
                }],
            )
        return {"chunks": len(chunks)}

    @traceable(run_type="chain", name="rag.chat", process_inputs=strip_self)
    async def chat(self, request: RagChatRequest) -> RagChatResponse:
        retrieved = await self.retrieve(request.question)
        prompt = self._build_prompt(request, retrieved)
        answer = await self.ollama.generate(prompt, num_predict=220)
        if not answer:
            answer = "I could not generate a response right now. Please try again."
        return RagChatResponse(
            answer=answer,
            sources=[SourceSnippet(title=chunk.title, snippet=chunk.snippet) for chunk in retrieved],
            modelName=self.settings.generation_model,
        )

    @traceable(
        run_type="chain",
        name="rag.chat.stream",
        process_inputs=strip_self,
        reduce_fn=_reduce_stream_answer,
    )
    async def chat_stream(self, request: RagChatRequest) -> AsyncIterator[str]:
        """Stream a grounded answer as Server-Sent Events.

        Retrieval runs first (so sources are known up front), then generation
        tokens are forwarded as they arrive. Each SSE ``data:`` line carries one
        JSON object tagged with a ``type``: ``sources`` once, ``token`` many
        times, then a terminal ``done`` (or ``error`` if generation fails).
        """
        retrieved = await self.retrieve(request.question)
        sources = [{"title": chunk.title, "snippet": chunk.snippet} for chunk in retrieved]
        yield _sse({"type": "sources", "sources": sources})

        prompt = self._build_prompt(request, retrieved)
        produced = False
        async for fragment in self.ollama.generate_stream(prompt, num_predict=220):
            produced = True
            yield _sse({"type": "token", "text": fragment})
        if not produced:
            yield _sse({"type": "token", "text": "I could not generate a response right now. Please try again."})
        yield _sse({"type": "done", "modelName": self.settings.generation_model})

    @traceable(run_type="retriever", name="rag.retrieve", process_inputs=strip_self)
    async def retrieve(self, question: str, top_k: int = 3) -> list[KnowledgeChunk]:
        count = self.collection.count()
        if count == 0:
            await self.reindex()
        embedding = await self.ollama.embed(question)
        results = self.collection.query(query_embeddings=[embedding], n_results=top_k)
        chunks: list[KnowledgeChunk] = []
        for index, chunk_id in enumerate(results.get("ids", [[]])[0]):
            metadata = results.get("metadatas", [[]])[0][index] or {}
            document = results.get("documents", [[]])[0][index] or ""
            chunks.append(
                KnowledgeChunk(
                    id=chunk_id,
                    title=str(metadata.get("title", "Wellness KB")),
                    source_file=str(metadata.get("source_file", "")),
                    chunk_index=int(metadata.get("chunk_index", 0)),
                    text=document,
                    snippet=str(metadata.get("snippet", document[:240])),
                )
            )
        return chunks

    def _build_prompt(self, request: RagChatRequest, chunks: list[KnowledgeChunk]) -> str:
        # Cap each snippet so the grounded prompt stays small and CPU prefill stays fast.
        context = "\n\n".join(
            f"Source: {chunk.title}\n{chunk.snippet[:200]}" for chunk in chunks
        )
        records = "\n".join(
            f"- {record.recordDate}: sleep {record.sleepHours}h, exercise {record.exerciseType or 'none'} "
            f"{record.exerciseMinutes}min, mood {record.moodScore}/5"
            for record in request.recentRecords
        ) or "No recent wellness records were provided."

        return f"""You are a wellness education chatbot for a student project.
Use only the retrieved context and recent wellness records.
Do not diagnose, prescribe treatment, or provide emergency advice.
If a question is outside wellness habits, say the app only supports wellness habit questions.
Keep the answer concise, practical, and friendly.

Retrieved context:
{context}

Recent user records:
{records}

User question:
{request.question}

Answer:"""
