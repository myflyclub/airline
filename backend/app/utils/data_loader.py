"""Load airport data from the original FlightForge CSV files."""
import csv
import os

from sqlalchemy.orm import Session

from app.models.airport import Airport, Runway
from app.models.aircraft import AircraftModel


def load_airports_from_csv(db: Session, csv_path: str) -> int:
    """Load airports from the original airports.csv file.

    CSV format (no header):
    id, iata, type, name, lat, lon, elevation, region, country, state, city, scheduled, ...
    """
    if not os.path.exists(csv_path):
        print(f"Airport CSV not found at {csv_path}")
        return 0

    # Only import airports that have scheduled service (meaningful for the game)
    airports_added = 0
    size_map = {
        "large_airport": 7,
        "medium_airport": 5,
        "small_airport": 3,
        "heliport": 1,
        "seaplane_base": 2,
        "closed": 0,
    }

    with open(csv_path, "r", encoding="utf-8") as f:
        reader = csv.reader(f)
        for row in reader:
            if len(row) < 11:
                continue

            raw_id = row[0].strip().strip('"')
            iata = row[1].strip().strip('"')
            airport_type = row[2].strip().strip('"')
            name = row[3].strip().strip('"')

            try:
                latitude = float(row[4].strip().strip('"'))
                longitude = float(row[5].strip().strip('"'))
            except (ValueError, IndexError):
                continue

            # Skip airports without IATA codes or with very short codes
            if not iata or len(iata) < 2:
                continue

            # Skip closed airports
            if airport_type == "closed":
                continue

            elevation = 0
            try:
                elevation = int(float(row[6].strip().strip('"')))
            except (ValueError, IndexError):
                pass

            region = row[7].strip().strip('"') if len(row) > 7 else ""
            country_code = row[8].strip().strip('"') if len(row) > 8 else ""
            state_code = row[9].strip().strip('"') if len(row) > 9 else ""
            city = row[10].strip().strip('"') if len(row) > 10 else ""

            size = size_map.get(airport_type, 3)

            # Check for duplicate IATA
            existing = db.query(Airport).filter(Airport.iata == iata).first()
            if existing:
                continue

            airport = Airport(
                iata=iata,
                icao=row[14].strip().strip('"') if len(row) > 14 else "",
                name=name,
                latitude=latitude,
                longitude=longitude,
                elevation=elevation,
                airport_type=airport_type,
                region=region,
                country_code=country_code,
                state_code=state_code,
                city=city,
                size=size,
                base_income=size * 10_000,
                base_population=size * 500_000,
                runway_length=max(size * 1500, 3000),
            )
            db.add(airport)
            airports_added += 1

            # Batch commit every 1000
            if airports_added % 1000 == 0:
                db.commit()

    db.commit()
    return airports_added


def load_runways_from_csv(db: Session, csv_path: str) -> int:
    """Load runway data from runways.csv."""
    if not os.path.exists(csv_path):
        return 0

    runways_added = 0
    with open(csv_path, "r", encoding="utf-8") as f:
        reader = csv.reader(f)
        next(reader, None)  # Skip header
        for row in reader:
            if len(row) < 6:
                continue

            airport_ident = row[2].strip().strip('"')
            airport = db.query(Airport).filter(Airport.iata == airport_ident).first()
            if not airport:
                # Try ICAO
                airport = db.query(Airport).filter(Airport.icao == airport_ident).first()
            if not airport:
                continue

            try:
                length_ft = int(float(row[3].strip().strip('"'))) if row[3].strip().strip('"') else 0
                width_ft = int(float(row[4].strip().strip('"'))) if row[4].strip().strip('"') else 0
            except ValueError:
                continue

            surface = row[5].strip().strip('"') if len(row) > 5 else ""
            lighted = int(row[6].strip().strip('"')) if len(row) > 6 and row[6].strip().strip('"').isdigit() else 0

            runway = Runway(
                airport_id=airport.id,
                length_ft=length_ft,
                width_ft=width_ft,
                surface=surface,
                lighted=lighted,
            )
            db.add(runway)
            runways_added += 1

            if runways_added % 1000 == 0:
                db.commit()

    db.commit()
    return runways_added


def seed_aircraft_models(db: Session) -> int:
    """Seed the database with common aircraft models."""
    models = [
        # Propeller / Regional
        {"name": "ATR 42-600", "family": "ATR", "manufacturer": "ATR", "capacity": 48, "quality": 4, "speed": 357, "fuel_burn": 2.8, "range_miles": 800, "price": 20_000_000, "lifespan": 520, "construction_time": 2, "runway_requirement": 3200, "aircraft_type": "REGIONAL"},
        {"name": "ATR 72-600", "family": "ATR", "manufacturer": "ATR", "capacity": 72, "quality": 4, "speed": 357, "fuel_burn": 3.2, "range_miles": 900, "price": 26_000_000, "lifespan": 520, "construction_time": 2, "runway_requirement": 3600, "aircraft_type": "REGIONAL"},
        {"name": "De Havilland Dash 8-400", "family": "Dash 8", "manufacturer": "De Havilland Canada", "capacity": 90, "quality": 5, "speed": 414, "fuel_burn": 3.5, "range_miles": 1100, "price": 32_000_000, "lifespan": 520, "construction_time": 3, "runway_requirement": 3800, "aircraft_type": "REGIONAL"},
        {"name": "Embraer E175", "family": "E-Jet", "manufacturer": "Embraer", "capacity": 88, "quality": 6, "speed": 530, "fuel_burn": 4.2, "range_miles": 2200, "price": 51_000_000, "lifespan": 600, "construction_time": 3, "runway_requirement": 5500, "aircraft_type": "REGIONAL"},
        {"name": "Embraer E195-E2", "family": "E-Jet E2", "manufacturer": "Embraer", "capacity": 146, "quality": 7, "speed": 530, "fuel_burn": 4.8, "range_miles": 2600, "price": 66_000_000, "lifespan": 600, "construction_time": 3, "runway_requirement": 6500, "aircraft_type": "SMALL"},

        # Narrow-body
        {"name": "Airbus A220-100", "family": "A220", "manufacturer": "Airbus", "capacity": 135, "quality": 7, "speed": 530, "fuel_burn": 4.5, "range_miles": 3400, "price": 81_000_000, "lifespan": 600, "construction_time": 4, "runway_requirement": 5000, "aircraft_type": "SMALL"},
        {"name": "Airbus A220-300", "family": "A220", "manufacturer": "Airbus", "capacity": 160, "quality": 7, "speed": 530, "fuel_burn": 4.9, "range_miles": 3600, "price": 91_000_000, "lifespan": 600, "construction_time": 4, "runway_requirement": 5600, "aircraft_type": "MEDIUM"},
        {"name": "Boeing 737 MAX 8", "family": "737", "manufacturer": "Boeing", "capacity": 189, "quality": 6, "speed": 521, "fuel_burn": 5.3, "range_miles": 3550, "price": 121_000_000, "lifespan": 600, "construction_time": 4, "runway_requirement": 6800, "aircraft_type": "MEDIUM"},
        {"name": "Boeing 737 MAX 10", "family": "737", "manufacturer": "Boeing", "capacity": 230, "quality": 6, "speed": 521, "fuel_burn": 5.7, "range_miles": 3300, "price": 135_000_000, "lifespan": 600, "construction_time": 4, "runway_requirement": 7800, "aircraft_type": "MEDIUM"},
        {"name": "Airbus A320neo", "family": "A320", "manufacturer": "Airbus", "capacity": 195, "quality": 7, "speed": 530, "fuel_burn": 5.1, "range_miles": 3500, "price": 111_000_000, "lifespan": 600, "construction_time": 4, "runway_requirement": 6900, "aircraft_type": "MEDIUM"},
        {"name": "Airbus A321neo", "family": "A320", "manufacturer": "Airbus", "capacity": 244, "quality": 7, "speed": 530, "fuel_burn": 5.8, "range_miles": 4000, "price": 130_000_000, "lifespan": 600, "construction_time": 4, "runway_requirement": 7500, "aircraft_type": "MEDIUM"},
        {"name": "Airbus A321XLR", "family": "A320", "manufacturer": "Airbus", "capacity": 220, "quality": 8, "speed": 530, "fuel_burn": 5.5, "range_miles": 4700, "price": 140_000_000, "lifespan": 600, "construction_time": 5, "runway_requirement": 7500, "aircraft_type": "MEDIUM"},
        {"name": "Boeing 757-200", "family": "757", "manufacturer": "Boeing", "capacity": 239, "quality": 5, "speed": 530, "fuel_burn": 6.8, "range_miles": 4100, "price": 80_000_000, "lifespan": 450, "construction_time": 4, "runway_requirement": 7000, "aircraft_type": "MEDIUM"},
        {"name": "COMAC C919", "family": "C919", "manufacturer": "COMAC", "capacity": 192, "quality": 5, "speed": 520, "fuel_burn": 5.5, "range_miles": 3000, "price": 99_000_000, "lifespan": 550, "construction_time": 4, "runway_requirement": 6800, "aircraft_type": "MEDIUM"},

        # Wide-body
        {"name": "Boeing 787-8", "family": "787", "manufacturer": "Boeing", "capacity": 248, "quality": 8, "speed": 587, "fuel_burn": 6.8, "range_miles": 7355, "price": 248_000_000, "lifespan": 650, "construction_time": 6, "runway_requirement": 9000, "aircraft_type": "LARGE"},
        {"name": "Boeing 787-9", "family": "787", "manufacturer": "Boeing", "capacity": 296, "quality": 8, "speed": 587, "fuel_burn": 7.2, "range_miles": 7635, "price": 282_000_000, "lifespan": 650, "construction_time": 6, "runway_requirement": 9500, "aircraft_type": "LARGE"},
        {"name": "Boeing 787-10", "family": "787", "manufacturer": "Boeing", "capacity": 336, "quality": 8, "speed": 587, "fuel_burn": 7.9, "range_miles": 6430, "price": 306_000_000, "lifespan": 650, "construction_time": 6, "runway_requirement": 9500, "aircraft_type": "LARGE"},
        {"name": "Airbus A330-900neo", "family": "A330", "manufacturer": "Airbus", "capacity": 310, "quality": 7, "speed": 560, "fuel_burn": 7.5, "range_miles": 7200, "price": 296_000_000, "lifespan": 600, "construction_time": 6, "runway_requirement": 8800, "aircraft_type": "LARGE"},
        {"name": "Airbus A350-900", "family": "A350", "manufacturer": "Airbus", "capacity": 325, "quality": 9, "speed": 587, "fuel_burn": 7.0, "range_miles": 8100, "price": 317_000_000, "lifespan": 700, "construction_time": 7, "runway_requirement": 8900, "aircraft_type": "LARGE"},
        {"name": "Airbus A350-1000", "family": "A350", "manufacturer": "Airbus", "capacity": 410, "quality": 9, "speed": 587, "fuel_burn": 8.2, "range_miles": 8700, "price": 366_000_000, "lifespan": 700, "construction_time": 7, "runway_requirement": 9200, "aircraft_type": "EXTRA_LARGE"},
        {"name": "Boeing 777-300ER", "family": "777", "manufacturer": "Boeing", "capacity": 396, "quality": 7, "speed": 554, "fuel_burn": 9.5, "range_miles": 7370, "price": 340_000_000, "lifespan": 600, "construction_time": 7, "runway_requirement": 10500, "aircraft_type": "EXTRA_LARGE"},
        {"name": "Boeing 777X-9", "family": "777X", "manufacturer": "Boeing", "capacity": 426, "quality": 9, "speed": 560, "fuel_burn": 8.0, "range_miles": 7600, "price": 388_000_000, "lifespan": 700, "construction_time": 8, "runway_requirement": 10000, "aircraft_type": "EXTRA_LARGE"},

        # Jumbo
        {"name": "Boeing 747-8", "family": "747", "manufacturer": "Boeing", "capacity": 467, "quality": 6, "speed": 570, "fuel_burn": 11.5, "range_miles": 8000, "price": 400_000_000, "lifespan": 550, "construction_time": 8, "runway_requirement": 10500, "aircraft_type": "JUMBO"},
        {"name": "Airbus A380-800", "family": "A380", "manufacturer": "Airbus", "capacity": 575, "quality": 8, "speed": 560, "fuel_burn": 13.0, "range_miles": 8200, "price": 445_000_000, "lifespan": 550, "construction_time": 9, "runway_requirement": 9800, "aircraft_type": "JUMBO"},
    ]

    added = 0
    for m in models:
        existing = db.query(AircraftModel).filter(AircraftModel.name == m["name"]).first()
        if not existing:
            db.add(AircraftModel(**m))
            added += 1

    db.commit()
    return added
