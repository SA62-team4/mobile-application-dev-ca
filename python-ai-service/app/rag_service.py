"""Basic local RAG pipeline using Ollama embeddings and Chroma.

@author SA62 Team
"""

import chromadb
from fastapi import HTTPException

from app.config import Settings
from app.knowledge_base import KnowledgeChunk, load_chunks
from app.models import RagChatRequest, RagChatResponse, SourceSnippet
from app.ollama_client import OllamaClient


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
                    "snippet": chunk.snippet,
                }],
            )
        return {"chunks": len(chunks)}

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

    async def retrieve(self, question: str, top_k: int = 4) -> list[KnowledgeChunk]:
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
                    text=document,
                    snippet=str(metadata.get("snippet", document[:240])),
                )
            )
        return chunks

    def _build_prompt(self, request: RagChatRequest, chunks: list[KnowledgeChunk]) -> str:
        context = "\n\n".join(f"Source: {chunk.title}\n{chunk.snippet}" for chunk in chunks)
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
