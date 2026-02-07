from sqlalchemy import Column, Integer, String, Float, Boolean, ForeignKey
from sqlalchemy.orm import relationship

from app.database import Base


class AircraftModel(Base):
    """Aircraft type/model definition (e.g., Boeing 737-800)."""
    __tablename__ = "aircraft_models"

    id = Column(Integer, primary_key=True, index=True)
    name = Column(String(100), nullable=False, unique=True)
    family = Column(String(100), default="")
    manufacturer = Column(String(100), default="")
    capacity = Column(Integer, nullable=False)  # total seats
    quality = Column(Integer, default=5)  # 0-10
    speed = Column(Integer, nullable=False)  # mph
    fuel_burn = Column(Float, nullable=False)  # per mile
    range_miles = Column(Integer, nullable=False)
    price = Column(Integer, nullable=False)  # purchase cost
    lifespan = Column(Integer, default=600)  # in game cycles
    construction_time = Column(Integer, default=4)  # in cycles
    runway_requirement = Column(Integer, default=5000)  # feet
    aircraft_type = Column(String(30), default="MEDIUM")
    # Types: PROPELLER_SMALL, PROPELLER_MEDIUM, SMALL, REGIONAL, MEDIUM, LARGE, EXTRA_LARGE, JUMBO, SUPERSONIC
    image_url = Column(String(500), default="")

    # Relationships
    aircraft = relationship("Aircraft", back_populates="model")

    @property
    def maintenance_cost(self) -> int:
        return int(self.price * 0.01 / 52)  # ~1% of price per year, weekly

    @property
    def category(self) -> str:
        if self.capacity <= 15:
            return "Light"
        elif self.capacity <= 50:
            return "Regional"
        elif self.capacity <= 150:
            return "Narrow-body"
        elif self.capacity <= 300:
            return "Wide-body"
        else:
            return "Jumbo"

    def __repr__(self):
        return f"<AircraftModel {self.name}>"


class Aircraft(Base):
    """Individual aircraft instance owned by an airline."""
    __tablename__ = "aircraft"

    id = Column(Integer, primary_key=True, index=True)
    model_id = Column(Integer, ForeignKey("aircraft_models.id"), nullable=False)
    airline_id = Column(Integer, ForeignKey("airlines.id"), nullable=False)
    condition = Column(Float, default=100.0)  # 0-100
    age_cycles = Column(Integer, default=0)
    is_active = Column(Boolean, default=True)
    home_base_id = Column(Integer, ForeignKey("airports.id"), nullable=True)
    purchase_price = Column(Integer, default=0)

    # Relationships
    model = relationship("AircraftModel", back_populates="aircraft")
    airline = relationship("Airline", back_populates="aircraft")
    home_base = relationship("Airport")
    route_assignments = relationship("RouteAssignment", back_populates="aircraft", cascade="all, delete-orphan")

    @property
    def value(self) -> int:
        """Current market value based on age and condition."""
        depreciation = 1.0 - (self.age_cycles / self.model.lifespan * 0.8)
        condition_factor = self.condition / 100
        return int(self.model.price * depreciation * condition_factor)

    def __repr__(self):
        return f"<Aircraft {self.model.name} #{self.id}>"
