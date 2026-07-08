"""FastAPI entry point for the premium agent service.

@Author: Tang Chee Seng (with Claude and Gemini)
"""

import logging
import os

from fastapi import FastAPI, HTTPException, Header, Depends
from pydantic import BaseModel, Field
from slowapi import Limiter
from slowapi.util import get_remote_address
from slowapi.errors import RateLimitExceeded
from starlette.requests import Request
from starlette.responses import JSONResponse

from app.agent import run_agent
from app.config import Settings

logger = logging.getLogger("WeatherAgent")

# --- Authentication ---
SECRET = os.getenv("AI_INTERNAL_SECRET", "")


async def verify_secret(x_internal_secret: str = Header(...)):
    """Reject requests that don't carry the correct shared secret.

    This header is injected by Spring Boot on the Droplet.
    It proves the request came through the Droplet, not from a random caller.
    """
    if not SECRET or x_internal_secret != SECRET:
        raise HTTPException(status_code=401, detail="Unauthorized access")


# --- Rate Limiter ---

limiter = Limiter(key_func=get_remote_address)

app = FastAPI(title="Premium Agent Service", version="0.0.1")
app.state.limiter = limiter


@app.exception_handler(RateLimitExceeded)
async def rate_limit_handler(request: Request, exc: RateLimitExceeded):
    return JSONResponse(
        status_code=429,
        content={"detail": "Rate limit exceeded. Try again in a moment."},
    )


# --- Models ---

class PremiumChatRequest(BaseModel):
    question: str = Field(min_length=1, max_length=1000)
    context: str = ""       # Pre-built RAG context from the Droplet.
    records: str = ""       # Pre-formatted wellness records from the Droplet.
    latitude: float | None = None                         
    longitude: float | None = None 

class PremiumChatResponse(BaseModel):
    answer: str
    modelName: str


# --- Routes ---

@app.get("/health")
async def health() -> dict[str, str]:
    return {"status": "UP"}


@app.post(
    "/premium/chat",
    response_model=PremiumChatResponse,
    dependencies=[Depends(verify_secret)],
)
@limiter.limit("10/minute")
async def premium_chat(request: Request, body: PremiumChatRequest) -> PremiumChatResponse:
    """Handle a premium chat request with weather agent capability.

    The Droplet's Spring Boot has already:
    1. Authenticated the user via JWT.
    2. Verified the user is PREMIUM_USER.
    3. Classified the question as exercise-related.
    4. Fetched recent wellness records from MySQL.
    5. Retrieved RAG context from the Droplet's Chroma.
    6. Packaged all of this into this request.

    We just run the agent (with weather tool) and return the answer.
    """
    try:
        result = await run_agent(body.question, body.context, body.records, body.latitude, body.longitude)
        return PremiumChatResponse(**result)
    except Exception:
        logger.exception("Premium agent failed")
        raise HTTPException(status_code=502, detail="Premium agent service unavailable")
    

@app.post(
    "/premium/chat/stream",
    dependencies=[Depends(verify_secret)],
)
@limiter.limit("10/minute")
async def premium_chat_stream(request: Request, body: PremiumChatRequest):
    """Streaming version of /premium/chat.

    Returns Server-Sent Events matching the same protocol the Droplet's
    standard Python service uses: a `sources` frame, multiple `token` frames,
    and a terminal `done` frame. This lets Spring Boot's ChatStreamService
    proxy the stream to Android without any format conversion.
    """
    import asyncio
    import json
    import re
    from starlette.responses import StreamingResponse

    # Pacing for the replay below. The agent returns a complete answer, so we can't
    # stream real tokens; instead we replay the finished string word-by-word with a
    # small gap so it reads like natural typing rather than a single 40-char burst,
    # visually matching the generic path's genuine token stream.
    REPLAY_DELAY_SECONDS = 0.025

    async def event_stream():
        try:
            # Sources frame — empty list since the Droplet already sent sources
            # via the pre-fetched RAG context. Spring Boot has those sources from
            # its own retrieval step and will emit them to Android directly.
            yield f"data: {json.dumps({'type': 'sources', 'sources': []})}\n\n"

            result = await run_agent(body.question, body.context, body.records, body.latitude, body.longitude)
            answer = result.get("answer", "")
            model = result.get("modelName", "gemma2:9b")

            # Replay the finished answer as word-sized token frames. Each piece is a
            # run of non-space chars plus any trailing whitespace, OR a leading run of
            # whitespace — so no character is ever dropped and words never break
            # mid-token. A short sleep paces the emission so it feels like typing.
            # Android concatenates token.text in order, so ''.join(pieces) == answer.
            for piece in re.findall(r"\S+\s*|\s+", answer):
                yield f"data: {json.dumps({'type': 'token', 'text': piece})}\n\n"
                await asyncio.sleep(REPLAY_DELAY_SECONDS)

            yield f"data: {json.dumps({'type': 'done', 'modelName': model})}\n\n"
        except Exception:
            logger.exception("Premium agent stream failed")
            yield f"data: {json.dumps({'type': 'error', 'message': 'Premium agent unavailable'})}\n\n"

    return StreamingResponse(event_stream(), media_type="text/event-stream")
