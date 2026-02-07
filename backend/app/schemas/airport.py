from pydantic import BaseModel
from typing import Optional


class AirportBase(BaseModel):
    iata: str
    icao: str = ""
    name: str
    latitude: float
    longitude: float
    country_code: str
    city: str = ""
    size: int = 3
    airport_type: str = "medium_airport"


class AirportCreate(AirportBase):
    pass


class AirportResponse(AirportBase):
    id: int
    elevation: int = 0
    region: str = ""
    state_code: str = ""
    base_income: int = 0
    base_population: int = 0
    runway_length: int = 5000

    class Config:
        from_attributes = True


class AirportMapPoint(BaseModel):
    """Lightweight airport data for map rendering."""
    id: int
    iata: str
    name: str
    latitude: float
    longitude: float
    size: int
    country_code: str
    city: str = ""

    class Config:
        from_attributes = True


class AirportSearchResult(BaseModel):
    id: int
    iata: str
    name: str
    city: str
    country_code: str
    size: int

    class Config:
        from_attributes = True
