"""LangSmith tracing setup.

LangChain reads its tracing configuration from process environment variables at
runtime. This module pushes the values from our pydantic ``Settings`` into the
standard ``LANGSMITH_*`` variables so tracing can be driven through the same
config/compose mechanism as the rest of the service.

@author Tiong Zhong Cheng
"""

import logging
import os
from typing import Any

from app.config import Settings

logger = logging.getLogger("wellness.ai")


def strip_self(inputs: dict[str, Any]) -> dict[str, Any]:
    """Drop the bound ``self`` arg from ``@traceable`` inputs.

    Keeps LangSmith traces readable by not serialising the service instance
    (which holds Chroma/HTTP clients) as a run input.
    """
    return {key: value for key, value in inputs.items() if key != "self"}


def configure_tracing(settings: Settings) -> None:
    """Enable LangSmith tracing when configured, otherwise leave it off.

    No-op (with a warning) when tracing is requested but no API key is set, so
    the service still runs fully local/offline.
    """
    if not settings.langsmith_tracing:
        # Explicitly off so a stray env var elsewhere cannot silently enable it.
        os.environ["LANGSMITH_TRACING"] = "false"
        return

    if not settings.langsmith_api_key:
        logger.warning("LANGSMITH_TRACING is enabled but LANGSMITH_API_KEY is empty; tracing disabled.")
        os.environ["LANGSMITH_TRACING"] = "false"
        return

    os.environ["LANGSMITH_TRACING"] = "true"
    os.environ["LANGSMITH_API_KEY"] = settings.langsmith_api_key
    os.environ["LANGSMITH_PROJECT"] = settings.langsmith_project
    os.environ["LANGSMITH_ENDPOINT"] = settings.langsmith_endpoint
    logger.info("LangSmith tracing enabled for project '%s'.", settings.langsmith_project)
