"""Unit tests for knowledge base loading edge cases.

@author Tiong Zhong Cheng
"""

from app.knowledge_base import _extract_title, load_chunks


def test_load_chunks_returns_empty_for_missing_directory(tmp_path):
    assert load_chunks(str(tmp_path / "missing")) == []


def test_load_chunks_skips_empty_files_and_uses_fallback_title(tmp_path):
    (tmp_path / "empty.md").write_text("", encoding="utf-8")
    (tmp_path / "healthy-habits.md").write_text(
        "Small habits repeated daily can support energy mood.",
        encoding="utf-8",
    )

    chunks = load_chunks(str(tmp_path), chunk_words=4)

    assert [chunk.id for chunk in chunks] == ["healthy-habits-0", "healthy-habits-1"]
    assert all(chunk.title == "Healthy Habits" for chunk in chunks)
    assert all(chunk.source_file == "healthy-habits.md" for chunk in chunks)
    assert chunks[0].snippet == chunks[0].text[:240]


def test_extract_title_prefers_first_markdown_h1():
    raw = "intro\n# Sleep Hygiene\nBody"

    assert _extract_title(raw, "Fallback") == "Sleep Hygiene"
