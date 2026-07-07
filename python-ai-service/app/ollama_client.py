"""Small Ollama HTTP client.

@author Tiong Zhong Cheng
"""

import json
from collections.abc import AsyncIterator

import httpx
from langsmith import traceable

from app.config import Settings
from app.tracing import strip_self


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

    @traceable(run_type="embedding", name="ollama.embed", process_inputs=strip_self)
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

    @traceable(run_type="llm", name="ollama.generate", process_inputs=strip_self)
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
                        # Smaller KV cache speeds up local CPU inference.
                        "num_ctx": 1024,
                    },
                },
            )
            self._raise_for_status(response)
            return response.json().get("response", "").strip()

    @traceable(
        run_type="llm",
        name="ollama.generate.stream",
        process_inputs=strip_self,
        # Store one combined streamed answer in LangSmith.
        reduce_fn=lambda fragments: "".join(fragments),
    )
    async def generate_stream(self, prompt: str, num_predict: int = 220) -> AsyncIterator[str]:
        """Yield generation tokens as they arrive from Ollama.

        Ollama sends newline-delimited JSON with a ``response`` fragment.
        """
        async with httpx.AsyncClient(timeout=180) as client:
            async with client.stream(
                "POST",
                f"{self.settings.ollama_base_url}/api/generate",
                json={
                    "model": self.settings.generation_model,
                    "prompt": prompt,
                    "stream": True,
                    "options": {
                        "num_predict": num_predict,
                        "temperature": 0.3,
                        # Keep local CPU inference responsive.
                        "num_ctx": 1024,
                    },
                },
            ) as response:
                if response.status_code >= 400:
                    await response.aread()
                    self._raise_for_status(response)
                async for line in response.aiter_lines():
                    if not line.strip():
                        continue
                    chunk = json.loads(line)
                    fragment = chunk.get("response", "")
                    if fragment:
                        yield fragment
