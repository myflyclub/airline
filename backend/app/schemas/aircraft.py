from pydantic import BaseModel
from typing import Optional


class AircraftModelResponse(BaseModel):
    id: int
    name: str
    family: str = ""
    manufacturer: str = ""
    capacity: int
    quality: int
    speed: int
    fuel_burn: float
    range_miles: int
    price: int
    lifespan: int
    construction_time: int
    runway_requirement: int
    aircraft_type: str
    image_url: str = ""

    class Config:
        from_attributes = True


class AircraftPurchase(BaseModel):
    model_id: int
    quantity: int = 1
    home_base_id: Optional[int] = None


class AircraftResponse(BaseModel):
    id: int
    model_id: int
    model_name: str = ""
    airline_id: int
    condition: float
    age_cycles: int
    is_active: bool
    home_base_id: Optional[int] = None
    purchase_price: int = 0

    class Config:
        from_attributes = True


class AircraftDetail(AircraftResponse):
    model: Optional[AircraftModelResponse] = None
    current_value: int = 0
    assigned_route_id: Optional[int] = None
