from sqlalchemy import Column, Integer, String, Float, ForeignKey, Table
from sqlalchemy.orm import relationship

from app.database import Base


class Airport(Base):
    __tablename__ = "airports"

    id = Column(Integer, primary_key=True, index=True)
    iata = Column(String(10), unique=True, index=True, nullable=False)
    icao = Column(String(10), index=True, default="")
    name = Column(String(200), nullable=False)
    latitude = Column(Float, nullable=False)
    longitude = Column(Float, nullable=False)
    elevation = Column(Integer, default=0)
    airport_type = Column(String(50), default="medium_airport")
    region = Column(String(10), default="")
    country_code = Column(String(10), nullable=False, index=True)
    state_code = Column(String(20), default="")
    city = Column(String(200), default="")
    size = Column(Integer, default=3)  # 1-8 scale
    base_income = Column(Integer, default=0)
    base_population = Column(Integer, default=0)
    runway_length = Column(Integer, default=5000)

    # Relationships
    runways = relationship("Runway", back_populates="airport", cascade="all, delete-orphan")
    features = relationship("AirportFeature", back_populates="airport", cascade="all, delete-orphan")
    bases = relationship("AirlineBase", back_populates="airport")
    routes_from = relationship("Route", foreign_keys="Route.from_airport_id", back_populates="from_airport")
    routes_to = relationship("Route", foreign_keys="Route.to_airport_id", back_populates="to_airport")

    @property
    def power(self) -> float:
        return self.base_income * self.base_population

    def __repr__(self):
        return f"<Airport {self.iata} - {self.name}>"


class Runway(Base):
    __tablename__ = "runways"

    id = Column(Integer, primary_key=True, index=True)
    airport_id = Column(Integer, ForeignKey("airports.id"), nullable=False)
    length_ft = Column(Integer, default=0)
    width_ft = Column(Integer, default=0)
    surface = Column(String(50), default="")
    lighted = Column(Integer, default=0)

    airport = relationship("Airport", back_populates="runways")


class AirportFeature(Base):
    __tablename__ = "airport_features"

    id = Column(Integer, primary_key=True, index=True)
    airport_id = Column(Integer, ForeignKey("airports.id"), nullable=False)
    feature_type = Column(String(50), nullable=False)  # e.g., "international_hub", "domestic_hub"
    strength = Column(Float, default=1.0)

    airport = relationship("Airport", back_populates="features")
