"""Unit tests for the deterministic agent workflow.

@author zctiong-iss
"""

from app.agent_service import AgentService
from app.config import Settings
from app.models import RecommendationResponse


class FakeBackend:
    """Backend stand-in that records the recommendation save request."""

    def __init__(self, records: list[dict]):
        self.records = records
        self.saved_user_id: int | None = None
        self.saved_payload = None

    async def recent_records(self, user_id: int) -> list[dict]:
        return self.records

    async def save_recommendation(self, user_id: int, recommendation):
        self.saved_user_id = user_id
        self.saved_payload = recommendation
        return RecommendationResponse(
            id=99,
            title=recommendation.title,
            trendSummary=recommendation.trendSummary,
            recommendationText=recommendation.recommendationText,
            actionItems=recommendation.actionItems,
        )


class FakeRag:
    """RAG stand-in that returns one chunk for the chosen focus."""

    def __init__(self):
        self.last_question: str | None = None

    async def retrieve(self, question: str):
        from app.knowledge_base import KnowledgeChunk

        self.last_question = question
        return [
            KnowledgeChunk(
                id="sleep-0",
                title="Sleep Hygiene",
                source_file="sleep-hygiene.md",
                text="Keep a regular bedtime.",
                snippet="Keep a regular bedtime.",
            )
        ]


class FakeChain:
    """LangChain stand-in for the final generation step."""

    def __init__(self, generated: str):
        self.generated = generated
        self.input_payload = None

    async def ainvoke(self, payload: dict) -> str:
        self.input_payload = payload
        return self.generated


def _service(records: list[dict] | None = None, generated: str = "") -> AgentService:
    from tests.conftest import FakeOllama

    settings = Settings(
        CHROMA_PERSIST_DIR="./unused-test-chroma",
        KNOWLEDGE_BASE_DIR="../rag-knowledge-base",
    )
    ollama = FakeOllama()
    ollama.settings = settings
    service = AgentService(FakeBackend(records or []), FakeRag(), ollama)
    service.chain = FakeChain(generated)
    return service


def test_choose_focus_prefers_tracking_when_records_are_sparse():
    service = _service()

    assert service._choose_focus([{"sleepHours": 8, "exerciseMinutes": 20, "moodScore": 4}]) == (
        "consistent wellness tracking"
    )


def test_choose_focus_rule_order_matches_agent_spec():
    service = _service()

    assert service._choose_focus([
        {"sleepHours": 6.5, "exerciseMinutes": 30, "moodScore": 4},
        {"sleepHours": 6.0, "exerciseMinutes": 20, "moodScore": 4},
        {"sleepHours": 6.8, "exerciseMinutes": 15, "moodScore": 4},
    ]) == "sleep consistency"
    assert service._choose_focus([
        {"sleepHours": 8, "exerciseMinutes": 0, "moodScore": 4},
        {"sleepHours": 8, "exerciseMinutes": 0, "moodScore": 4},
        {"sleepHours": 8, "exerciseMinutes": 25, "moodScore": 4},
    ]) == "light activity routine"
    assert service._choose_focus([
        {"sleepHours": 8, "exerciseMinutes": 15, "moodScore": 2},
        {"sleepHours": 8, "exerciseMinutes": 15, "moodScore": 1},
        {"sleepHours": 8, "exerciseMinutes": 15, "moodScore": 2},
    ]) == "stress and mood support"
    assert service._choose_focus([
        {"sleepHours": 8, "exerciseMinutes": 15, "moodScore": 4},
        {"sleepHours": 7.5, "exerciseMinutes": 15, "moodScore": 5},
        {"sleepHours": 7.2, "exerciseMinutes": 15, "moodScore": 4},
    ]) == "maintaining balanced habits"


def test_trend_summary_describes_empty_and_populated_records():
    service = _service()

    assert "No recent wellness records" in service._trend_summary([], "consistent wellness tracking")

    summary = service._trend_summary(
        [
            {"sleepHours": 6, "exerciseMinutes": 30, "moodScore": 3},
            {"sleepHours": 8, "exerciseMinutes": 0, "moodScore": 5},
        ],
        "sleep consistency",
    )

    assert "average sleep is 7.0 hours" in summary
    assert "exercise appears on 1 days" in summary
    assert "average mood is 4.0/5" in summary
    assert "sleep consistency" in summary


def test_parse_generated_uses_title_and_limits_action_items():
    service = _service()

    title, recommendation, action_items = service._parse_generated(
        """Title: Sleep reset
Recommendation: Keep bedtime steady.
Action items:
- Dim lights
- Set a reminder
- Review your log
- Extra item""",
        "sleep consistency",
    )

    assert title == "Sleep reset"
    assert "Keep bedtime steady" in recommendation
    assert action_items == ["Dim lights", "Set a reminder", "Review your log"]


def test_parse_generated_falls_back_when_output_is_sparse():
    service = _service()

    title, recommendation, action_items = service._parse_generated("", "light activity routine")

    assert title == "Focus on Light Activity Routine"
    assert recommendation == "Start with one small, consistent wellness habit this week."
    assert len(action_items) == 3


async def test_generate_recommendation_fetches_records_retrieves_context_and_saves():
    service = _service(
        records=[
            {"sleepHours": 6, "exerciseMinutes": 20, "moodScore": 3},
            {"sleepHours": 6.5, "exerciseMinutes": 0, "moodScore": 3},
            {"sleepHours": 6.8, "exerciseMinutes": 10, "moodScore": 3},
        ],
        generated="""Title: Sleep consistency plan
Recommendation: Build a steady wind-down routine.
- Pick a bedtime
- Reduce screens
- Track sleep""",
    )

    response = await service.generate_recommendation(7)

    assert response.id == 99
    assert response.title == "Sleep consistency plan"
    assert response.actionItems == ["Pick a bedtime", "Reduce screens", "Track sleep"]
    assert service.backend.saved_user_id == 7
    assert service.backend.saved_payload.generatedBy == "python-agent"
    assert service.rag.last_question == "sleep consistency"
    assert service.chain.input_payload["focus"] == "sleep consistency"
    assert "Sleep Hygiene: Keep a regular bedtime." in service.chain.input_payload["context"]
