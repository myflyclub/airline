from sqlalchemy import Column, Integer, String, Float, Boolean, ForeignKey, BigInteger
from sqlalchemy.orm import relationship

from app.database import Base


class Airline(Base):
    __tablename__ = "airlines"

    id = Column(Integer, primary_key=True, index=True)
    name = Column(String(100), nullable=False, index=True)
    airline_code = Column(String(5), unique=True, nullable=False, index=True)
    country_code = Column(String(10), default="")
    airline_type = Column(String(30), default="BEGINNER")  # LEGACY, BEGINNER, DISCOUNT, LUXURY, REGIONAL
    balance = Column(BigInteger, default=50_000_000)
    reputation = Column(Float, default=0.0)
    service_quality = Column(Float, default=50.0)
    target_service_quality = Column(Integer, default=50)
    stock_price = Column(Float, default=0.0)
    logo_url = Column(String(500), default="")
    color = Column(String(10), default="#0066cc")
    slogan = Column(String(200), default="")
    is_active = Column(Boolean, default=True)
    owner_id = Column(Integer, ForeignKey("users.id"), nullable=True)

    # Relationships
    owner = relationship("User", back_populates="airlines")
    bases = relationship("AirlineBase", back_populates="airline", cascade="all, delete-orphan")
    aircraft = relationship("Aircraft", back_populates="airline", cascade="all, delete-orphan")
    routes = relationship("Route", back_populates="airline", cascade="all, delete-orphan")
    finances = relationship("FinancialRecord", back_populates="airline", cascade="all, delete-orphan")
    loans = relationship("Loan", back_populates="airline", cascade="all, delete-orphan")

    def __repr__(self):
        return f"<Airline {self.airline_code} - {self.name}>"


class AirlineBase(Base):
    __tablename__ = "airline_bases"

    id = Column(Integer, primary_key=True, index=True)
    airline_id = Column(Integer, ForeignKey("airlines.id"), nullable=False)
    airport_id = Column(Integer, ForeignKey("airports.id"), nullable=False)
    base_type = Column(String(20), default="secondary")  # "headquarters", "hub", "secondary"
    scale = Column(Integer, default=1)  # 1-12
    founded_cycle = Column(Integer, default=0)

    airline = relationship("Airline", back_populates="bases")
    airport = relationship("Airport", back_populates="bases")


class FinancialRecord(Base):
    __tablename__ = "financial_records"

    id = Column(Integer, primary_key=True, index=True)
    airline_id = Column(Integer, ForeignKey("airlines.id"), nullable=False)
    cycle = Column(Integer, nullable=False)
    revenue = Column(BigInteger, default=0)
    expense = Column(BigInteger, default=0)
    fuel_cost = Column(BigInteger, default=0)
    maintenance_cost = Column(BigInteger, default=0)
    salary_cost = Column(BigInteger, default=0)
    infrastructure_cost = Column(BigInteger, default=0)
    loan_interest = Column(BigInteger, default=0)
    profit = Column(BigInteger, default=0)

    airline = relationship("Airline", back_populates="finances")


class Loan(Base):
    __tablename__ = "loans"

    id = Column(Integer, primary_key=True, index=True)
    airline_id = Column(Integer, ForeignKey("airlines.id"), nullable=False)
    principal = Column(BigInteger, nullable=False)
    remaining = Column(BigInteger, nullable=False)
    interest_rate = Column(Float, nullable=False)
    term_cycles = Column(Integer, nullable=False)
    remaining_cycles = Column(Integer, nullable=False)
    creation_cycle = Column(Integer, nullable=False)

    airline = relationship("Airline", back_populates="loans")
