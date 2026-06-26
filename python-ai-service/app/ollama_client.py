"""Small Ollama HTTP client.

@author SA62 Team
"""

import httpx

from app.config import Settings


class OllamaClient:
    def __init__(self, settings: Settings):
        self.settings = settings

    async def embed(self, text: str) -> list[float]:
        async with httpx.AsyncClient(timeout=60) as client:
            response = await client.post(
                f"{self.settings.ollama_base_url}/api/embeddings",
                json={"model": self.settings.embedding_model, "prompt": text},
            )
            response.raise_for_status()
            return response.json()["embedding"]

    async def generate(self, prompt: str) -> str:
        async with httpx.AsyncClient(timeout=120) as client:
            response = await client.post(
                f"{self.settings.ollama_base_url}/api/generate",
                json={
                    "model": self.settings.generation_model,
                    "prompt": prompt,
                    "stream": False,
                },
            )
            response.raise_for_status()
            return response.json().get("response", "").strip()

