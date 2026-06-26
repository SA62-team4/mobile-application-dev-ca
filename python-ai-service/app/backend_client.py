"""Client for backend internal APIs.

@author SA62 Team
"""

import httpx

from app.config import Settings
from app.models import InternalRecommendationRequest, RecommendationResponse


class BackendClient:
    def __init__(self, settings: Settings):
        self.settings = settings

    async def recent_records(self, user_id: int, days: int = 14) -> list[dict]:
        async with httpx.AsyncClient(timeout=30) as client:
            response = await client.get(
                f"{self.settings.backend_base_url}/api/internal/users/{user_id}/wellness-records",
                params={"days": days},
                headers={"X-Internal-Service-Token": self.settings.internal_service_token},
            )
            response.raise_for_status()
            return response.json()

    async def save_recommendation(
            self,
            user_id: int,
            recommendation: InternalRecommendationRequest,
    ) -> RecommendationResponse:
        async with httpx.AsyncClient(timeout=30) as client:
            response = await client.post(
                f"{self.settings.backend_base_url}/api/internal/users/{user_id}/recommendations",
                json=recommendation.model_dump(),
                headers={"X-Internal-Service-Token": self.settings.internal_service_token},
            )
            response.raise_for_status()
            return RecommendationResponse.model_validate(response.json())

