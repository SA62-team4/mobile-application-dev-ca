"""Knowledge base loading and chunking.

@author Tiong Zhong Cheng
"""

from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class KnowledgeChunk:
    id: str
    title: str
    source_file: str
    chunk_index: int
    text: str
    snippet: str


def load_chunks(kb_dir: str, chunk_words: int = 420) -> list[KnowledgeChunk]:
    root = Path(kb_dir)
    if not root.exists():
        return []

    chunks: list[KnowledgeChunk] = []
    for path in sorted(root.glob("*.md")):
        raw = path.read_text(encoding="utf-8")
        title = _extract_title(raw, path.stem.replace("-", " ").title())
        body = _extract_body_text(raw)
        words = body.split()
        if not words:
            continue
        for index in range(0, len(words), chunk_words):
            text = " ".join(words[index:index + chunk_words])
            chunk_index = index // chunk_words
            chunks.append(
                KnowledgeChunk(
                    id=f"{path.stem}-{chunk_index}",
                    title=title,
                    source_file=path.name,
                    chunk_index=chunk_index,
                    text=text,
                    snippet=text[:240],
                )
            )
    return chunks


def _extract_title(raw: str, fallback: str) -> str:
    for line in raw.splitlines():
        if line.startswith("# "):
            return line[2:].strip()
    return fallback


def _extract_body_text(raw: str) -> str:
    """Remove document boilerplate before embedding and source snippets."""
    body_lines: list[str] = []
    for line in raw.splitlines():
        stripped = line.strip()
        lowered = stripped.lower()
        if not stripped:
            continue
        if stripped.startswith("# "):
            continue
        if stripped.startswith("#"):
            stripped = stripped.lstrip("#").strip()
            lowered = stripped.lower()
        if lowered.startswith("source note:"):
            continue
        if lowered.startswith("reference note:"):
            continue
        if lowered.startswith("this app provides general wellness education"):
            continue
        body_lines.append(stripped)
    return "\n".join(body_lines)
