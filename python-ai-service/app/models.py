"""Pydantic request and response models.

@author SA62 Team
"""

from pydantic import BaseModel, Field


class RecentRecord(BaseModel):
    recordDate: str
    sleepHours: float
    exerciseType: str | None = None
    exerciseMinutes: int
    moodScore: int


class SourceSnippet(BaseModel):
    title: str
    snippet: str


class RagChatRequest(BaseModel):
    userId: int
    question: str = Field(min_length=1)
    recentRecords: list[RecentRecord] = []


class RagChatResponse(BaseModel):
    answer: str
    sources: list[SourceSnippet]
    modelName: str


class RecommendationResponse(BaseModel):
    id: int | None = None
    title: str
    trendSummary: str
    recommendationText: str
    actionItems: list[str]
    generatedBy: str = "python-agent"
    createdAt: str | None = None


class InternalRecommendationRequest(BaseModel):
    title: str
    trendSummary: str
    recommendationText: str
    actionItems: list[str]
    generatedBy: str = "python-agent"

