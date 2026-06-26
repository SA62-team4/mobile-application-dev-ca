"""Agentic recommendation workflow.

@author SA62 Team
"""

from app.backend_client import BackendClient
from app.models import InternalRecommendationRequest, RecommendationResponse
from app.ollama_client import OllamaClient
from app.rag_service import RagService


class AgentService:
    def __init__(self, backend: BackendClient, rag: RagService, ollama: OllamaClient):
        self.backend = backend
        self.rag = rag
        self.ollama = ollama

    async def generate_recommendation(self, user_id: int) -> RecommendationResponse:
        records = await self.backend.recent_records(user_id)
        focus = self._choose_focus(records)
        trend_summary = self._trend_summary(records, focus)
        chunks = await self.rag.retrieve(focus)
        context = "\n\n".join(f"{chunk.title}: {chunk.text}" for chunk in chunks)
        prompt = f"""Generate a short personalised wellness recommendation.
The recommendation must be educational, practical, and non-medical.

Focus: {focus}
Trend summary: {trend_summary}
Supporting context:
{context}

Return:
Title:
Recommendation:
Action items:
- item 1
- item 2
- item 3
"""
        generated = await self.ollama.generate(prompt)
        title, recommendation_text, action_items = self._parse_generated(generated, focus)
        saved = await self.backend.save_recommendation(
            user_id,
            InternalRecommendationRequest(
                title=title,
                trendSummary=trend_summary,
                recommendationText=recommendation_text,
                actionItems=action_items,
            ),
        )
        return saved

    def _choose_focus(self, records: list[dict]) -> str:
        if len(records) < 3:
            return "consistent wellness tracking"
        average_sleep = sum(float(record.get("sleepHours", 0)) for record in records) / len(records)
        exercise_days = sum(1 for record in records if int(record.get("exerciseMinutes", 0)) > 0)
        average_mood = sum(int(record.get("moodScore", 3)) for record in records) / len(records)
        if average_sleep < 7:
            return "sleep consistency"
        if exercise_days < 3:
            return "light activity routine"
        if average_mood <= 2:
            return "stress and mood support"
        return "maintaining balanced habits"

    def _trend_summary(self, records: list[dict], focus: str) -> str:
        if not records:
            return "No recent wellness records were found, so the recommendation focuses on building a tracking habit."
        average_sleep = sum(float(record.get("sleepHours", 0)) for record in records) / len(records)
        exercise_days = sum(1 for record in records if int(record.get("exerciseMinutes", 0)) > 0)
        average_mood = sum(int(record.get("moodScore", 3)) for record in records) / len(records)
        return (
            f"Based on {len(records)} recent records, average sleep is {average_sleep:.1f} hours, "
            f"exercise appears on {exercise_days} days, and average mood is {average_mood:.1f}/5. "
            f"The agent selected {focus} as the focus."
        )

    def _parse_generated(self, generated: str, focus: str) -> tuple[str, str, list[str]]:
        lines = [line.strip() for line in generated.splitlines() if line.strip()]
        title = f"Focus on {focus.title()}"
        recommendation = generated.strip() or "Start with one small, consistent wellness habit this week."
        action_items: list[str] = []
        for line in lines:
            lowered = line.lower()
            if lowered.startswith("title:"):
                title = line.split(":", 1)[1].strip() or title
            elif line.startswith("-"):
                action_items.append(line.lstrip("- ").strip())
        if len(action_items) < 3:
            action_items = [
                "Record one wellness log today",
                "Choose one small habit to repeat tomorrow",
                "Review your progress after three days",
            ]
        return title, recommendation, action_items[:3]

