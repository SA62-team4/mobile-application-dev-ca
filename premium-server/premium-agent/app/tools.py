"""
Premium agent service — Agent Tools providing weather information (wet bulb temperature and 2-hour forecast) 
for users considering outdoor activities. To be accessed via chat.

@Author: Tang Chee Seng (with Claude, and Gemini)
"""

import math
import httpx
from langchain_core.tools import tool

# Timeout for API requests in case of a slow response from Data.gov.sg.
WEATHER_TIMEOUT = 10.0  # seconds

# Sanity bounds for WBGT values in Singapore (in °C). These are used to filter out any erroneous readings.
WBGT_MIN = 20.0
WBGT_MAX = 36.0    # Highest recorded WBGT in Singapore is 35.0°C on 23 Mar 2026, so 36.0°C is a reasonable upper bound at this time.

SAFETY_BANDS = [
    (33.0, "DANGER - BLACK Zone (≥ 33 °C). Suspend all outdoor exercise. Risk of heat stroke is very high."),
    (31.0, "ALERT - BROWN Zone (31 to 32.9 °C). Minimise strenuous outdoor activity. Hydrate every 15 min."),
    (29.0, "ADVISORY - YELLOW Zone (29 to 30.9 °C). Reduce outdoor intensity and take frequent shade breaks."),
    (27.0, "SAFE - GREEN Zone (27 to 28.9 °C). Outdoor exercise is generally safe. Stay hydrated."),
    (0.0,  "SAFE - WHITE Zone (< 27 °C). Low heat stress. Outdoor exercise is safe."),
]

def classify_wbgt(wbgt_val: float) -> str:
    """Return the NEA heat stress advisory band for a WBGT reading."""
    for threshold, message in SAFETY_BANDS:
        if wbgt_val >= threshold:
            return message
    return SAFETY_BANDS[-1][1]


def haversine(user_lat, user_lon, station_lat, station_lon):
    """Calculate the shortest distance between two points on a sphere (i.e. between the user and station) in kilometers."""
    rad = 6371.0  # Earth radius in km

    # Distance between latitudes and longitudes
    dLat = (station_lat - user_lat) * math.pi / 180.0
    dLon = (station_lon - user_lon) * math.pi / 180.0

    # Convert to radians
    user_lat = user_lat * math.pi / 180.0
    station_lat = station_lat * math.pi / 180.0
    
    a = (pow(math.sin(dLat / 2), 2) + pow(math.sin(dLon / 2), 2) * math.cos(user_lat) * math.cos(station_lat))

    c = 2 * math.asin(math.sqrt(a))

    return rad * c

def get_wet_bulb_temperature(user_lat: float | None = None, user_lon: float | None = None):
    @tool
    async def get_wet_bulb_temperature() -> str:
        """Fetch the current Wet Bulb Globe Temperature (WBGT) in Singapore.
        To apply the user's location if available.
        """
        async with httpx.AsyncClient(timeout=WEATHER_TIMEOUT) as client:
            response = await client.get("https://api-open.data.gov.sg/v2/real-time/api/wet-bulb-temperature")
            response.raise_for_status()
            data = response.json().get("data", {})
            records = data.get("records", [])
            
            if not records:
                return "Unable to retrieve current WBGT and heat stress levels. Please try again later."
                   
            # Extract all the station locations into a list, and find the closest one to the user if lat/lon is provided
            all_stations = []

            for record in records:
                for reading in record.get("item", {}).get("readings", []):
                    station_info = reading.get("station", {})
                    if station_info:
                        all_stations.append(station_info)

            # for record in records:
            #     readings = record.get("item", {}).get("readings", [])
            #     for reading in readings:
            #         station_info = reading.get("station", {})
            #         if station_info:
            #             all_stations.append(station_info)
            
            # Find closest station with valid latitude
            if user_lat is not None and user_lon is not None:
                valid_stations = [s for s in all_stations if s.get("location", {}).get("latitude")]
                if valid_stations:
                    closest = min(
                        valid_stations,
                        key=lambda s: haversine(user_lat, user_lon, float(s["location"]["latitude"]), float(s["location"]["longitude"]))
                    )
                    stn_id = closest["id"]
                    stn_name = closest["name"]
                    
                    # Find reading for this station

                    for record in records:
                        for reading in record.get("item", {}).get("readings", []):
                            if reading.get("station", {}).get("id") == stn_id:
                                wbgt_val = float(reading.get("wbgt", 0))
                                # Sanity check now runs before returning.
                                if not (WBGT_MIN <= wbgt_val <= WBGT_MAX):
                                    return (f"WBGT reading ({wbgt_val:.1f} °C) is outside the expected range. Sensor data may be unreliable."
                                            "Advise caution for outdoor exercise."
                                            )
                                heat_stress = reading.get("heatStress")
                                return (f"The current WBGT at {stn_name} (closest to you) is {wbgt_val:.1f}°C. Heat stress level: {heat_stress}.\n"
                                        f"{classify_wbgt(wbgt_val)}"
                                        )
                            
            # Fallback to national average if location is missing
            avg_wbgt_values = []

            for record in records:
                for reading in record.get("item", {}).get("readings", []):
                    if reading.get("wbgt"):
                        avg_wbgt_values.append(float(reading["wbgt"]))
            if not avg_wbgt_values:
                return "Unable to retrieve current WBGT. Advise caution for outdoor exercise."
            avg = sum(avg_wbgt_values) / len(avg_wbgt_values)
            if not (WBGT_MIN <= avg <= WBGT_MAX):
                return (f"Average WBGT reading ({avg:.1f} °C) is outside the expected range. Sensor data may be unreliable."
                        "Advise caution for outdoor exercise."
                        )
            return f"Average WBGT in Singapore is {avg:.1f}°C.\n{classify_wbgt(avg)}"
            
    return get_wet_bulb_temperature

def get_weather_forecast(user_lat: float | None = None, user_lon: float | None = None):
    @tool
    async def get_weather_forecast() -> str:
        """Fetch the two-hour weather forecast for the user's location.
        To apply the user's location if available.
        """
        async with httpx.AsyncClient(timeout=WEATHER_TIMEOUT) as client:
            response = await client.get("https://api-open.data.gov.sg/v2/real-time/api/two-hr-forecast")
            response.raise_for_status()
            data = response.json().get("data", {})
            areas = data.get("area_metadata", [])
            items = data.get("items", [])
            
            if not areas or not items:
                return "Unable to retrieve current two-hour forecast."
                
            forecasts = items[0].get("forecasts", []) if items else []

            # Closest-area path.
            if user_lat is not None and user_lon is not None:
                valid_area = [a for a in areas if a.get("label_location", {}).get("latitude")]
                if valid_area:
                    closest = min(valid_area, 
                                  key=lambda a: haversine(user_lat, user_lon,a["label_location"]["latitude"], a["label_location"]["longitude"]))
                    area_name = closest["area"]                # [FIX C] no bad "name" key.
                    for fc in forecasts:
                        if fc.get("area") == area_name and fc.get("forecast"):
                            return f"The 2-hour forecast for {area_name} (closest to user) is: {fc['forecast']}."

            # Fallback: summarise a few areas instead of returning None.
            sample = [f"{fc['area']}: {fc['forecast']}"
                      for fc in forecasts[:3] if fc.get("area") and fc.get("forecast")]
            if sample:
                return "2-hour forecast (sample areas): " + "; ".join(sample) + "."
            else:
                return "Unable to retrieve a usable two-hour forecast right now."

    return get_weather_forecast