"""Small Ollama HTTP client.

@author SA62 Team
"""

import httpx

from app.config import Settings


class OllamaClient:
    def __init__(self, settings: Settings):
        self.settings = settings

    @staticmethod
    def _raise_for_status(response: httpx.Response) -> None:
        try:
            response.raise_for_status()
        except httpx.HTTPStatusError as exc:
            body = response.text[:500]
            raise httpx.HTTPStatusError(
                f"{exc}. Response body: {body}",
                request=exc.request,
                response=exc.response,
            ) from exc

    async def embed(self, text: str) -> list[float]:
        async with httpx.AsyncClient(timeout=60) as client:
            try:
                response = await client.post(
                    f"{self.settings.ollama_base_url}/api/embed",
                    json={"model": self.settings.embedding_model, "input": text},
                )
                self._raise_for_status(response)
                return response.json()["embeddings"][0]
            except httpx.HTTPStatusError as first_error:
                if first_error.response.status_code != 404:
                    raise

                response = await client.post(
                    f"{self.settings.ollama_base_url}/api/embeddings",
                    json={"model": self.settings.embedding_model, "prompt": text},
                )
                self._raise_for_status(response)
                return response.json()["embedding"]

    async def generate(self, prompt: str, num_predict: int = 180) -> str:
        async with httpx.AsyncClient(timeout=180) as client:
            response = await client.post(
                f"{self.settings.ollama_base_url}/api/generate",
                json={
                    "model": self.settings.generation_model,
                    "prompt": prompt,
                    "stream": False,
                    "options": {
                        "num_predict": num_predict,
                        "temperature": 0.3,
                    },
                },
            )
            self._raise_for_status(response)
            return response.json().get("response", "").strip()
