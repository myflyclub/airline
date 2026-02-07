"""Game simulation engine - runs periodic cycles to simulate the airline economy."""
import asyncio
import random
from sqlalchemy.orm import Session

from app.database import SessionLocal
from app.models.airline import Airline, FinancialRecord, Loan
from app.models.aircraft import Aircraft, AircraftModel
from app.models.route import Route, RouteStatistics


class SimulationEngine:
    """Core simulation engine that processes game cycles."""

    def __init__(self):
        self.current_cycle = 0
        self.is_running = False

    async def run_cycle(self):
        """Run a single game simulation cycle."""
        db = SessionLocal()
        try:
            self.current_cycle += 1
            self._simulate_routes(db)
            self._simulate_aircraft_aging(db)
            self._simulate_finances(db)
            self._simulate_loans(db)
            self._simulate_reputation(db)
            db.commit()
        finally:
            db.close()

    def _simulate_routes(self, db: Session):
        """Simulate passenger demand and revenue for all active routes."""
        routes = db.query(Route).filter(Route.is_active).all()
        for route in routes:
            total_capacity = (
                route.capacity_economy + route.capacity_business
                + route.capacity_first + route.capacity_discount
            ) * route.frequency

            if total_capacity == 0:
                continue

            # Demand based on airport sizes and distance
            from_ap = route.from_airport
            to_ap = route.to_airport
            if not from_ap or not to_ap:
                continue

            base_demand = (from_ap.size + to_ap.size) * 50
            distance_factor = max(0.3, 1.0 - route.distance / 10000)
            demand = int(base_demand * distance_factor * random.uniform(0.7, 1.3))

            # Calculate load factor
            load_factor = min(1.0, demand / max(1, total_capacity))

            # Revenue calculation
            pax_economy = int(route.capacity_economy * route.frequency * load_factor)
            pax_business = int(route.capacity_business * route.frequency * load_factor * 0.7)
            pax_first = int(route.capacity_first * route.frequency * load_factor * 0.5)

            revenue = (
                pax_economy * route.price_economy
                + pax_business * route.price_business
                + pax_first * route.price_first
            )

            # Fuel cost estimate
            fuel_cost = int(route.distance * route.frequency * 5.5)

            # Record statistics
            stat = RouteStatistics(
                route_id=route.id,
                cycle=self.current_cycle,
                passengers_economy=pax_economy,
                passengers_business=pax_business,
                passengers_first=pax_first,
                revenue=revenue,
                fuel_cost=fuel_cost,
                load_factor=load_factor,
            )
            db.add(stat)

            # Update airline balance
            airline = db.query(Airline).filter(Airline.id == route.airline_id).first()
            if airline:
                airline.balance += revenue - fuel_cost

    def _simulate_aircraft_aging(self, db: Session):
        """Age aircraft and reduce condition."""
        aircraft_list = db.query(Aircraft).filter(Aircraft.is_active).all()
        for ac in aircraft_list:
            ac.age_cycles += 1
            ac.condition = max(0, ac.condition - random.uniform(0.1, 0.5))
            model = db.query(AircraftModel).filter(AircraftModel.id == ac.model_id).first()
            if model and ac.age_cycles >= model.lifespan:
                ac.is_active = False

    def _simulate_finances(self, db: Session):
        """Record financial summaries for each airline."""
        airlines = db.query(Airline).filter(Airline.is_active).all()
        for airline in airlines:
            routes = db.query(Route).filter(
                Route.airline_id == airline.id, Route.is_active
            ).all()

            fleet = db.query(Aircraft).filter(
                Aircraft.airline_id == airline.id, Aircraft.is_active
            ).all()

            # Maintenance costs
            maintenance = 0
            for ac in fleet:
                model = db.query(AircraftModel).filter(AircraftModel.id == ac.model_id).first()
                if model:
                    maintenance += int(model.price * 0.01 / 52)

            # Salary costs based on service quality
            salary = int(airline.service_quality * len(fleet) * 5000)

            airline.balance -= maintenance + salary

            record = FinancialRecord(
                airline_id=airline.id,
                cycle=self.current_cycle,
                maintenance_cost=maintenance,
                salary_cost=salary,
                expense=maintenance + salary,
            )
            db.add(record)

    def _simulate_loans(self, db: Session):
        """Process loan repayments."""
        loans = db.query(Loan).filter(Loan.remaining_cycles > 0).all()
        for loan in loans:
            payment = int(loan.remaining / loan.remaining_cycles)
            interest = int(loan.remaining * loan.interest_rate / 52)
            total_payment = payment + interest

            airline = db.query(Airline).filter(Airline.id == loan.airline_id).first()
            if airline:
                airline.balance -= total_payment

            loan.remaining -= payment
            loan.remaining_cycles -= 1

    def _simulate_reputation(self, db: Session):
        """Slowly adjust airline reputation based on performance."""
        airlines = db.query(Airline).filter(Airline.is_active).all()
        for airline in airlines:
            route_count = db.query(Route).filter(
                Route.airline_id == airline.id, Route.is_active
            ).count()
            target_rep = min(100, route_count * 2 + airline.service_quality * 0.5)
            airline.reputation += (target_rep - airline.reputation) * 0.1


# Singleton
simulation_engine = SimulationEngine()
