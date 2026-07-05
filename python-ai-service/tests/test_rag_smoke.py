"""CI-safe RAG indexing + retrieval smoke test (Ollama mocked).

Verifies the T-403 acceptance criteria without a live Ollama server:
- KB chunks are produced with metadata
- the Chroma index builds
- an embedding is requested for every chunk
- retrieval returns the relevant chunk
- chat returns an answer with sources

@author JustinChua97
"""

from app.knowledge_base import load_chunks
from app.models import RagChatRequest


def test_kb_chunks_have_metadata(settings):
    chunks = load_chunks(settings.knowledge_base_dir)

    assert chunks, "expected knowledge-base chunks to be produced"
    for chunk in chunks:
        assert chunk.id
        assert chunk.title.strip()
        assert chunk.source_file.endswith(".md")
        assert chunk.text.strip()
        assert 0 < len(chunk.snippet) <= 240


async def test_reindex_builds_chroma_index(rag_service, fake_ollama):
    result = await rag_service.reindex()
    expected = len(load_chunks(rag_service.settings.knowledge_base_dir))

    assert result == {"chunks": expected}
    assert rag_service.collection.count() == expected
    # Every chunk was embedded via (the fake) Ollama.
    assert fake_ollama.embed_calls == expected

    stored = rag_service.collection.get()
    assert len(stored["metadatas"]) == expected
    for metadata in stored["metadatas"]:
        assert metadata["title"]
        assert metadata["source_file"].endswith(".md")
        assert "snippet" in metadata


async def test_retrieve_returns_matching_chunk_for_its_own_text(rag_service):
    await rag_service.reindex()
    target = load_chunks(rag_service.settings.knowledge_base_dir)[0]

    # A chunk's own text must embed to itself, so it is the nearest neighbour.
    top = await rag_service.retrieve(target.text, top_k=4)

    assert top, "retrieval returned no chunks"
    assert len(top) <= 4
    assert top[0].id == target.id
    assert top[0].source_file == target.source_file
    assert top[0].title == target.title


async def test_retrieve_by_keyword_finds_expected_doc(rag_service):
    await rag_service.reindex()

    results = await rag_service.retrieve("how many hours of sleep and rest at night", top_k=4)

    assert results
    assert any(chunk.source_file == "sleep-hygiene.md" for chunk in results)


async def test_retrieve_lazily_reindexes_when_collection_empty(rag_service):
    assert rag_service.collection.count() == 0  # fresh, unindexed collection

    results = await rag_service.retrieve("sleep", top_k=4)

    assert results
    assert rag_service.collection.count() > 0


async def test_chat_returns_answer_with_sources(rag_service, fake_ollama):
    response = await rag_service.chat(
        RagChatRequest(userId=1, question="How can I sleep better?")
    )

    assert response.answer.strip()
    assert response.sources
    assert response.modelName == rag_service.settings.generation_model
    assert fake_ollama.generate_calls == 1
