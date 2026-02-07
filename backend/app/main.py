"""FlightForge V2 - Modern Airline Simulation Game

Backend powered by FastAPI, replacing the legacy Play Framework.
"""
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.config import get_settings
from app.database import init_db, SessionLocal
from app.utils.data_loader import load_airports_from_csv, seed_aircraft_models
from app.routers import auth, airports, airlines, aircraft, routes, websocket

settings = get_settings()


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Initialize database and seed data on startup."""
    init_db()

    # Seed airport data from CSV if database is empty
    db = SessionLocal()
    try:
        from app.models.airport import Airport
        count = db.query(Airport).count()
        if count == 0:
            print("Seeding airport data from CSV...")
            loaded = load_airports_from_csv(db, settings.airport_csv_path)
            print(f"Loaded {loaded} airports")

        from app.models.aircraft import AircraftModel
        model_count = db.query(AircraftModel).count()
        if model_count == 0:
            print("Seeding aircraft models...")
            added = seed_aircraft_models(db)
            print(f"Added {added} aircraft models")
    finally:
        db.close()

    yield


app = FastAPI(
    title=settings.app_name,
    description="Modern airline simulation game - manage routes, fleets, and finances",
    version="2.0.0",
    lifespan=lifespan,
)

# CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.cors_origins,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Register routers
app.include_router(auth.router)
app.include_router(airports.router)
app.include_router(airlines.router)
app.include_router(aircraft.router)
app.include_router(routes.router)
app.include_router(websocket.router)


@app.get("/")
def root():
    return {
        "name": settings.app_name,
        "version": "2.0.0",
        "status": "running",
    }


@app.get("/api/health")
def health():
    return {"status": "healthy"}


@app.get("/api/game/status")
def game_status():
    from app.services.simulation import simulation_engine
    return {
        "current_cycle": simulation_engine.current_cycle,
        "is_running": simulation_engine.is_running,
        "cycle_length_minutes": settings.game_cycle_minutes,
    }
