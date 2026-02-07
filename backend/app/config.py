from pydantic_settings import BaseSettings
from functools import lru_cache
import os


class Settings(BaseSettings):
    app_name: str = "FlightForge V2"
    debug: bool = True
    database_url: str = "sqlite:///./flightforge.db"
    secret_key: str = "change-me-in-production-use-a-real-secret"
    algorithm: str = "HS256"
    access_token_expire_minutes: int = 1440  # 24 hours
    cors_origins: list[str] = ["http://localhost:5173", "http://localhost:3000"]

    # Game settings
    game_cycle_minutes: int = 29  # Simulation cycle length
    starting_balance: int = 50_000_000  # $50M starting cash
    max_loan_amount: int = 100_000_000

    # Airport data
    airport_csv_path: str = os.path.join(
        os.path.dirname(__file__), "..", "data", "airports.csv"
    )

    class Config:
        env_file = ".env"


@lru_cache
def get_settings() -> Settings:
    return Settings()
