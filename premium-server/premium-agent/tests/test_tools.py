"""Tests for the premium weather tools.

@author Tang Chee Seng (with Claude)
"""

import httpx
import pytest
import respx

from app.tools import (
    get_wet_bulb_temperature, get_weather_forecast, haversine, classify_wbgt,
)

WBGT_URL = "https://api-open.data.gov.sg/v2/real-time/api/wet-bulb-temperature"
FORECAST_URL = "https://api-open.data.gov.sg/v2/real-time/api/two-hr-forecast"


def _wbgt_payload():
    return {"data": {"records": [{"item": {"readings": [
        {"station": {"id": "S1", "name": "Marina",
                     "location": {"latitude": 1.30, "longitude": 103.85}},
         "wbgt": 30.5, "heatStress": "moderate"},
        {"station": {"id": "S2", "name": "Jurong",
                     "location": {"latitude": 1.33, "longitude": 103.74}},
         "wbgt": 28.0, "heatStress": "low"},
    ]}}]}}


# --- pure helpers (no HTTP) ---

def test_haversine_zero_distance():
    assert haversine(1.3, 103.8, 1.3, 103.8) == pytest.approx(0.0, abs=1e-6)

def test_haversine_known_distance():
    # ~ a few km between two SG points; assert it's positive and sane.
    assert 0 < haversine(1.30, 103.85, 1.33, 103.74) < 20

@pytest.mark.parametrize("temp,expected_fragment", [
    (34.0, "BLACK"), (31.5, "BROWN"), (29.5, "YELLOW"),
    (27.5, "GREEN"), (25.0, "WHITE"),
])
def test_classify_wbgt_bands(temp, expected_fragment):
    assert expected_fragment in classify_wbgt(temp)


# --- WBGT tool ---

@respx.mock
async def test_wbgt_closest_station_uses_location():
    respx.get(WBGT_URL).mock(return_value=httpx.Response(200, json=_wbgt_payload()))
    tool = get_wet_bulb_temperature(1.30, 103.85)   # near Marina (S1)
    result = await tool.ainvoke({})
    assert "Marina" in result
    assert "30.5" in result

@respx.mock
async def test_wbgt_national_average_when_no_location():
    respx.get(WBGT_URL).mock(return_value=httpx.Response(200, json=_wbgt_payload()))
    tool = get_wet_bulb_temperature(None, None)
    result = await tool.ainvoke({})
    assert "Average WBGT" in result
    assert "29.2" in result or "29.3" in result   # (30.5 + 28.0) / 2

@respx.mock
async def test_wbgt_empty_records_returns_graceful_message():
    respx.get(WBGT_URL).mock(return_value=httpx.Response(200, json={"data": {"records": []}}))
    tool = get_wet_bulb_temperature(1.3, 103.8)
    result = await tool.ainvoke({})
    assert "Unable to retrieve current WBGT" in result

@respx.mock
async def test_wbgt_out_of_range_flagged_as_unreliable():
    """Covers sanity check if the data is outside the expected range."""
    bad = {"data": {"records": [{"item": {"readings": [
        {"station": {"id": "S1", "name": "Marina",
                     "location": {"latitude": 1.30, "longitude": 103.85}},
         "wbgt": 99.0, "heatStress": "n/a"}]}}]}}
    respx.get(WBGT_URL).mock(return_value=httpx.Response(200, json=bad))
    tool = get_wet_bulb_temperature(1.30, 103.85)
    result = await tool.ainvoke({})
    assert "outside the expected range" in result


# --- forecast tool (covers fallback + bad key) ---

@respx.mock
async def test_forecast_returns_fallback_when_area_not_matched():
    payload = {"data": {
        "area_metadata": [{"area": "Bishan", "label_location": {"latitude": 1.35, "longitude": 103.85}}],
        "items": [{"forecasts": [{"area": "Bishan", "forecast": "Cloudy"}]}]}}
    respx.get(FORECAST_URL).mock(return_value=httpx.Response(200, json=payload))
    tool = get_weather_forecast(None, None)          # no location → fallback path
    result = await tool.ainvoke({})
    assert "Bishan" in result and "Cloudy" in result   # never returns None (Fix B)

@respx.mock
async def test_forecast_closest_area_matches_location():
    """With lat/lon, the tool picks the nearest area and returns its forecast."""
    payload = {"data": {
        "area_metadata": [
            {"area": "Jurong", "label_location": {"latitude": 1.33, "longitude": 103.74}},
            {"area": "Bishan", "label_location": {"latitude": 1.35, "longitude": 103.85}},
        ],
        "items": [{"forecasts": [
            {"area": "Jurong", "forecast": "Thundery Showers"},
            {"area": "Bishan", "forecast": "Cloudy"},
        ]}]}}
    respx.get(FORECAST_URL).mock(return_value=httpx.Response(200, json=payload))
    tool = get_weather_forecast(1.33, 103.74)     # sits on Jurong
    result = await tool.ainvoke({})
    assert "Jurong" in result and "Thundery Showers" in result
    assert "closest to user" in result

@respx.mock
async def test_forecast_no_usable_forecasts_message():
    """Areas present but no forecast entries → the 'no usable forecast' message."""
    payload = {"data": {
        "area_metadata": [{"area": "Bishan", "label_location": {"latitude": 1.35, "longitude": 103.85}}],
        "items": [{"forecasts": []}]}}
    respx.get(FORECAST_URL).mock(return_value=httpx.Response(200, json=payload))
    tool = get_weather_forecast(None, None)
    result = await tool.ainvoke({})
    assert "Unable to retrieve a usable two-hour forecast" in result

@respx.mock
async def test_forecast_empty_payload_graceful():
    respx.get(FORECAST_URL).mock(return_value=httpx.Response(200, json={"data": {}}))
    tool = get_weather_forecast(1.3, 103.8)
    result = await tool.ainvoke({})
    assert "Unable to retrieve" in result