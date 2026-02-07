from pydantic import BaseModel, Field
from typing import Optional


class RouteCreate(BaseModel):
    from_airport_id: int
    to_airport_id: int
    frequency: int = Field(7, ge=1, le=28)
    price_economy: int = Field(0, ge=0)
    price_business: int = Field(0, ge=0)
    price_first: int = Field(0, ge=0)
    price_discount: int = Field(0, ge=0)
    capacity_economy: int = Field(0, ge=0)
    capacity_business: int = Field(0, ge=0)
    capacity_first: int = Field(0, ge=0)
    capacity_discount: int = Field(0, ge=0)
    raw_quality: int = Field(50, ge=1, le=100)


class RouteUpdate(BaseModel):
    frequency: Optional[int] = Field(None, ge=1, le=28)
    price_economy: Optional[int] = Field(None, ge=0)
    price_business: Optional[int] = Field(None, ge=0)
    price_first: Optional[int] = Field(None, ge=0)
    price_discount: Optional[int] = Field(None, ge=0)
    capacity_economy: Optional[int] = Field(None, ge=0)
    capacity_business: Optional[int] = Field(None, ge=0)
    capacity_first: Optional[int] = Field(None, ge=0)
    capacity_discount: Optional[int] = Field(None, ge=0)
    raw_quality: Optional[int] = Field(None, ge=1, le=100)


class RouteResponse(BaseModel):
    id: int
    from_airport_id: int
    to_airport_id: int
    airline_id: int
    distance: int
    flight_number: int
    frequency: int
    duration: int
    is_active: bool
    price_economy: int
    price_business: int
    price_first: int
    price_discount: int
    capacity_economy: int
    capacity_business: int
    capacity_first: int
    capacity_discount: int
    raw_quality: int

    # Enriched fields
    from_airport_iata: str = ""
    from_airport_name: str = ""
    to_airport_iata: str = ""
    to_airport_name: str = ""
    airline_name: str = ""

    class Config:
        from_attributes = True


class RouteMapData(BaseModel):
    """Lightweight route data for map arc rendering."""
    id: int
    from_lat: float
    from_lon: float
    to_lat: float
    to_lon: float
    from_iata: str
    to_iata: str
    airline_id: int
    airline_color: str = "#0066cc"

    class Config:
        from_attributes = True


class AssignAircraftRequest(BaseModel):
    aircraft_id: int
    frequency: int = Field(1, ge=1)


class PriceSuggestion(BaseModel):
    """Suggested pricing based on distance and class."""
    economy: int
    business: int
    first_class: int
    discount: int
