import math
from sqlalchemy import Column, Integer, String, Float, ForeignKey, Boolean
from sqlalchemy.orm import relationship

from app.database import Base


class Route(Base):
    """A flight route (link) between two airports operated by an airline."""
    __tablename__ = "routes"

    id = Column(Integer, primary_key=True, index=True)
    from_airport_id = Column(Integer, ForeignKey("airports.id"), nullable=False)
    to_airport_id = Column(Integer, ForeignKey("airports.id"), nullable=False)
    airline_id = Column(Integer, ForeignKey("airlines.id"), nullable=False)
    distance = Column(Integer, nullable=False)  # miles
    flight_number = Column(Integer, default=0)
    frequency = Column(Integer, default=7)  # flights per week
    duration = Column(Integer, default=0)  # minutes
    is_active = Column(Boolean, default=True)

    # Pricing per class
    price_economy = Column(Integer, default=0)
    price_business = Column(Integer, default=0)
    price_first = Column(Integer, default=0)
    price_discount = Column(Integer, default=0)

    # Capacity per flight per class
    capacity_economy = Column(Integer, default=0)
    capacity_business = Column(Integer, default=0)
    capacity_first = Column(Integer, default=0)
    capacity_discount = Column(Integer, default=0)

    # Quality
    raw_quality = Column(Integer, default=50)  # 1-100

    # Relationships
    from_airport = relationship("Airport", foreign_keys=[from_airport_id], back_populates="routes_from")
    to_airport = relationship("Airport", foreign_keys=[to_airport_id], back_populates="routes_to")
    airline = relationship("Airline", back_populates="routes")
    assignments = relationship("RouteAssignment", back_populates="route", cascade="all, delete-orphan")
    statistics = relationship("RouteStatistics", back_populates="route", cascade="all, delete-orphan")

    @property
    def total_capacity(self) -> int:
        return self.capacity_economy + self.capacity_business + self.capacity_first + self.capacity_discount

    @property
    def is_international(self) -> bool:
        if self.from_airport and self.to_airport:
            return self.from_airport.country_code != self.to_airport.country_code
        return False

    def __repr__(self):
        return f"<Route {self.id}: {self.from_airport_id} -> {self.to_airport_id}>"


class RouteAssignment(Base):
    """Links aircraft to a route with frequency allocation."""
    __tablename__ = "route_assignments"

    id = Column(Integer, primary_key=True, index=True)
    route_id = Column(Integer, ForeignKey("routes.id"), nullable=False)
    aircraft_id = Column(Integer, ForeignKey("aircraft.id"), nullable=False)
    frequency = Column(Integer, default=1)

    route = relationship("Route", back_populates="assignments")
    aircraft = relationship("Aircraft", back_populates="route_assignments")


class RouteStatistics(Base):
    """Per-cycle statistics for a route."""
    __tablename__ = "route_statistics"

    id = Column(Integer, primary_key=True, index=True)
    route_id = Column(Integer, ForeignKey("routes.id"), nullable=False)
    cycle = Column(Integer, nullable=False)
    passengers_economy = Column(Integer, default=0)
    passengers_business = Column(Integer, default=0)
    passengers_first = Column(Integer, default=0)
    revenue = Column(Integer, default=0)
    fuel_cost = Column(Integer, default=0)
    load_factor = Column(Float, default=0.0)  # 0-1

    route = relationship("Route", back_populates="statistics")


def calculate_distance(lat1: float, lon1: float, lat2: float, lon2: float) -> int:
    """Calculate great-circle distance between two points in miles."""
    R = 3959  # Earth radius in miles
    lat1_r, lat2_r = math.radians(lat1), math.radians(lat2)
    dlat = math.radians(lat2 - lat1)
    dlon = math.radians(lon2 - lon1)
    a = math.sin(dlat / 2) ** 2 + math.cos(lat1_r) * math.cos(lat2_r) * math.sin(dlon / 2) ** 2
    c = 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))
    return int(R * c)
