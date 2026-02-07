from pydantic import BaseModel, Field
from typing import Optional


class AirlineBase(BaseModel):
    name: str = Field(..., min_length=2, max_length=100)
    airline_code: str = Field(..., min_length=2, max_length=5)
    country_code: str = ""


class AirlineCreate(AirlineBase):
    pass


class AirlineUpdate(BaseModel):
    name: Optional[str] = None
    slogan: Optional[str] = None
    target_service_quality: Optional[int] = Field(None, ge=0, le=100)
    color: Optional[str] = None
    logo_url: Optional[str] = None


class AirlineResponse(AirlineBase):
    id: int
    airline_type: str = "BEGINNER"
    balance: int = 0
    reputation: float = 0.0
    service_quality: float = 50.0
    target_service_quality: int = 50
    stock_price: float = 0.0
    logo_url: str = ""
    color: str = "#0066cc"
    slogan: str = ""
    is_active: bool = True
    owner_id: Optional[int] = None

    class Config:
        from_attributes = True


class AirlineSummary(BaseModel):
    id: int
    name: str
    airline_code: str
    balance: int
    reputation: float
    fleet_size: int = 0
    route_count: int = 0

    class Config:
        from_attributes = True


class BaseCreate(BaseModel):
    airport_id: int
    base_type: str = "secondary"


class BaseResponse(BaseModel):
    id: int
    airport_id: int
    base_type: str
    scale: int
    airport_iata: str = ""
    airport_name: str = ""

    class Config:
        from_attributes = True


class LoanRequest(BaseModel):
    amount: int = Field(..., gt=0)
    term_cycles: int = Field(..., ge=4, le=52)


class LoanResponse(BaseModel):
    id: int
    principal: int
    remaining: int
    interest_rate: float
    term_cycles: int
    remaining_cycles: int

    class Config:
        from_attributes = True
