"""
LangChain setup for Premium agent service - accessing Agent Tools providing weather information (wet bulb temperature and 2-hour forecast) 
for users considering outdoor activities. 

@Author: Tang Chee Seng (with Claude, and Gemini)
"""

import logging
import os
import asyncio

from pyexpat import model
from click import prompt
from dotenv import load_dotenv
from langchain_ollama import ChatOllama
from langchain.agents import AgentExecutor, create_tool_calling_agent
from langchain_core.prompts import ChatPromptTemplate
from app.tools import get_wet_bulb_temperature, get_weather_forecast

logger = logging.getLogger("premium-agent")
model = os.getenv("OLLAMA_GENERATION_MODEL"),
base_url = os.getenv("OLLAMA_LOCAL_SERVER_URL")
llm = ChatOllama(
        llm_model = model,
        llm_base_url = base_url,
        temperature=0.2,
        num_predict=300,       # Cap output length.
    )

def build_agent() -> AgentExecutor:
    """Build the agent executor once at import time."""
    llm = ChatOllama(
        llm_model = model,
        llm_base_url = base_url,
        temperature=0.2,
        num_predict=300,       # Cap output length.
    )
    tools = [get_wet_bulb_temperature, get_weather_forecast]

    prompt = ChatPromptTemplate.from_messages([
        ("system", 
         "You are a weather-aware exercise advisor. You have access to two weather tools: "
         "(1) Wet Bulb Globe Temperature (WBGT) and (2) 2-hour weather forecast."
         "Use it when the user asks for weather information, or indicates they are planning to exercise outdoors."
         "You may suggest alternative exercises if the weather is unsafe for outdoor exercise - e.g. indoor exercises, swimming, or gym workouts."
         "Rules:\n"
         "- Use the provided RAG context and user wellness records to ground your answer.\n"
         "- Do not diagnose, prescribe treatment, or provide emergency advice.\n"
         "- If a question is outside wellness habits, politely refuse the request, and say the app only covers wellness and exercise related topics.\n"
         "- Keep the answer concise, practical, and friendly.\n"
         "- If the user appears to be in distress, advise them to seek help from a medical professional or call emergency services.\n"
         "- If the user appears to share that they have a mental health crisis, or confesses to a deep emotional struggle, advise them to reach out to a trained counselor.\n"
         "- If the user asks for the weather conditions at a specific location that is not provided by phone location data, inform them that you can only provide weather information for locations where you have data.\n"
         "- Always cite the WBGT reading and advisory band when discussing weather.\n\n"
         "RAG context:\n{context}\n\n"
         "Recent user wellness records:\n{records}"),
        ("human", "{input}"),
        ("placeholder", "{agent_scratchpad}"),
    ])
   
async def run_agent(question: str, context: str, records: str, lat: float = None, lon: float = None) -> dict:
    """Run the premium agent and return the answer + model name.
    Args:
        question: The user's original question.
        context:  Pre-built RAG context string (retrieved by the Droplet).
        records:  Pre-formatted recent wellness records string.
    Returns:
        Dict with 'answer' and 'modelName'.
    """
    # Build tools with the user's specific location injected directly
    tools = [get_wet_bulb_temperature(lat, lon), get_weather_forecast(lat, lon)]
    agent = create_tool_calling_agent(llm, tools, prompt)
    executor = AgentExecutor(
                agent=agent,
                tools=tools,
                verbose=True,
                max_iterations=2,          # Stop runaway tool-calling loops.
                max_execution_time=45,     # Hard kill after 60 seconds.
                return_intermediate_steps=True,  # Log what the agent did.
    )
    
    response = await executor.ainvoke({
        "input": question,
        "context": context or "No additional context available.",
        "records": records or "No recent wellness records available.",
    })

    answer = response.get("output", "").strip()
    if not answer:
        answer = "I could not generate a response right now. Please try again."

    # Log intermediate steps for debugging/audit.
    for step in response.get("intermediate_steps", []):
        action, observation = step
        logger.info("Agent tool call: %s → %s", action.tool, observation[:200])
    return {
        "answer": answer,
        "modelName": model,
    }

asyncio.run(run_agent())