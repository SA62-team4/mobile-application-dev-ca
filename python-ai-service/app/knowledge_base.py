"""Knowledge base loading and chunking.

@author SA62 Team
"""

from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class KnowledgeChunk:
    id: str
    title: str
    source_file: str
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
        words = raw.split()
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

