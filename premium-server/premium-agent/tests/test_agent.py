"""Tests for run_agent wiring (executor mocked — no Ollama needed).

@author Tang Chee Seng (with Claude)
"""

import pytest
import app.agent as agent


async def test_run_agent_extracts_answer_and_model(monkeypatch):
    class FakeExecutor:
        async def ainvoke(self, inputs):
            return {"output": "Stay indoors, BLACK zone.", "intermediate_steps": []}
    monkeypatch.setattr(agent, "create_tool_calling_agent", lambda *a, **k: object())
    monkeypatch.setattr(agent, "AgentExecutor", lambda *a, **k: FakeExecutor())

    result = await agent.run_agent("safe to run?", "ctx", "records", 1.3, 103.8)
    assert result["answer"] == "Stay indoors, BLACK zone."
    assert result["modelName"] == agent.MODEL

async def test_run_agent_empty_output_has_fallback(monkeypatch):
    class FakeExecutor:
        async def ainvoke(self, inputs):
            return {"output": "", "intermediate_steps": []}
    monkeypatch.setattr(agent, "create_tool_calling_agent", lambda *a, **k: object())
    monkeypatch.setattr(agent, "AgentExecutor", lambda *a, **k: FakeExecutor())

    result = await agent.run_agent("q", "", "")
    assert "could not generate" in result["answer"].lower()