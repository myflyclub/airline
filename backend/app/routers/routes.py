from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session

from app.database import get_db
from app.models.route import Route, RouteAssignment, calculate_distance
from app.models.airport import Airport
from app.models.airline import Airline
from app.models.aircraft import Aircraft
from app.models.user import User
from app.schemas.route import (
    RouteCreate, RouteUpdate, RouteResponse, RouteMapData,
    AssignAircraftRequest, PriceSuggestion,
)
from app.utils.auth import get_current_user

router = APIRouter(prefix="/api/routes", tags=["routes"])

# Price brackets per mile per class
PRICE_BRACKETS = {
    "discount": [(400, 0.10), (800, 0.069), (3800, 0.070), (3000, 0.09), (99999, 0.15)],
    "economy": [(400, 0.16), (800, 0.077), (3800, 0.076), (3000, 0.09), (99999, 0.16)],
    "business": [(400, 0.42), (800, 0.239), (3800, 0.173), (3000, 0.27), (99999, 0.27)],
    "first": [(400, 1.24), (800, 0.354), (3800, 0.465), (3000, 0.58), (99999, 0.62)],
}
PRICE_BASE = 15


def calculate_price(distance: int, fare_class: str) -> int:
    brackets = PRICE_BRACKETS.get(fare_class, PRICE_BRACKETS["economy"])
    total = PRICE_BASE
    remaining = distance
    for bracket_dist, rate in brackets:
        if remaining <= 0:
            break
        segment = min(remaining, bracket_dist)
        total += segment * rate
        remaining -= segment
    return int(total)


@router.get("/suggest-price/{from_id}/{to_id}", response_model=PriceSuggestion)
def suggest_price(from_id: int, to_id: int, db: Session = Depends(get_db)):
    from_ap = db.query(Airport).filter(Airport.id == from_id).first()
    to_ap = db.query(Airport).filter(Airport.id == to_id).first()
    if not from_ap or not to_ap:
        raise HTTPException(status_code=404, detail="Airport not found")

    dist = calculate_distance(from_ap.latitude, from_ap.longitude, to_ap.latitude, to_ap.longitude)
    return PriceSuggestion(
        economy=calculate_price(dist, "economy"),
        business=calculate_price(dist, "business"),
        first_class=calculate_price(dist, "first"),
        discount=calculate_price(dist, "discount"),
    )


@router.get("/airline/{airline_id}", response_model=list[RouteResponse])
def get_airline_routes(airline_id: int, db: Session = Depends(get_db)):
    routes = db.query(Route).filter(Route.airline_id == airline_id).all()
    return _enrich_routes(routes, db)


@router.get("/airport/{airport_id}", response_model=list[RouteResponse])
def get_airport_routes(airport_id: int, db: Session = Depends(get_db)):
    routes = db.query(Route).filter(
        (Route.from_airport_id == airport_id) | (Route.to_airport_id == airport_id)
    ).all()
    return _enrich_routes(routes, db)


@router.get("/map/{airline_id}", response_model=list[RouteMapData])
def get_route_map_data(airline_id: int, db: Session = Depends(get_db)):
    """Get lightweight route data for map arc rendering."""
    routes = db.query(Route).filter(Route.airline_id == airline_id, Route.is_active).all()
    result = []
    for r in routes:
        from_ap = db.query(Airport).filter(Airport.id == r.from_airport_id).first()
        to_ap = db.query(Airport).filter(Airport.id == r.to_airport_id).first()
        airline = db.query(Airline).filter(Airline.id == r.airline_id).first()
        if from_ap and to_ap:
            result.append(RouteMapData(
                id=r.id,
                from_lat=from_ap.latitude,
                from_lon=from_ap.longitude,
                to_lat=to_ap.latitude,
                to_lon=to_ap.longitude,
                from_iata=from_ap.iata,
                to_iata=to_ap.iata,
                airline_id=r.airline_id,
                airline_color=airline.color if airline else "#0066cc",
            ))
    return result


@router.post("/", response_model=RouteResponse, status_code=status.HTTP_201_CREATED)
def create_route(
    data: RouteCreate,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
):
    airline = db.query(Airline).filter(Airline.owner_id == user.id, Airline.is_active).first()
    if not airline:
        raise HTTPException(status_code=404, detail="No active airline found")

    from_ap = db.query(Airport).filter(Airport.id == data.from_airport_id).first()
    to_ap = db.query(Airport).filter(Airport.id == data.to_airport_id).first()
    if not from_ap or not to_ap:
        raise HTTPException(status_code=404, detail="Airport not found")

    if from_ap.id == to_ap.id:
        raise HTTPException(status_code=400, detail="Origin and destination must be different")

    # Check for existing route
    existing = db.query(Route).filter(
        Route.airline_id == airline.id,
        Route.from_airport_id == from_ap.id,
        Route.to_airport_id == to_ap.id,
        Route.is_active,
    ).first()
    if existing:
        raise HTTPException(status_code=400, detail="Route already exists")

    distance = calculate_distance(from_ap.latitude, from_ap.longitude, to_ap.latitude, to_ap.longitude)
    duration = int(distance / 500 * 60)  # Rough estimate at 500mph

    route = Route(
        from_airport_id=from_ap.id,
        to_airport_id=to_ap.id,
        airline_id=airline.id,
        distance=distance,
        frequency=data.frequency,
        duration=duration,
        price_economy=data.price_economy or calculate_price(distance, "economy"),
        price_business=data.price_business or calculate_price(distance, "business"),
        price_first=data.price_first or calculate_price(distance, "first"),
        price_discount=data.price_discount or calculate_price(distance, "discount"),
        capacity_economy=data.capacity_economy,
        capacity_business=data.capacity_business,
        capacity_first=data.capacity_first,
        capacity_discount=data.capacity_discount,
        raw_quality=data.raw_quality,
    )
    db.add(route)
    db.commit()
    db.refresh(route)

    return _enrich_route(route, db)


@router.patch("/{route_id}", response_model=RouteResponse)
def update_route(
    route_id: int,
    data: RouteUpdate,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
):
    airline = db.query(Airline).filter(Airline.owner_id == user.id, Airline.is_active).first()
    if not airline:
        raise HTTPException(status_code=404, detail="No active airline found")

    route = db.query(Route).filter(Route.id == route_id, Route.airline_id == airline.id).first()
    if not route:
        raise HTTPException(status_code=404, detail="Route not found")

    for field, value in data.model_dump(exclude_unset=True).items():
        setattr(route, field, value)

    db.commit()
    db.refresh(route)
    return _enrich_route(route, db)


@router.delete("/{route_id}")
def delete_route(
    route_id: int,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
):
    airline = db.query(Airline).filter(Airline.owner_id == user.id, Airline.is_active).first()
    if not airline:
        raise HTTPException(status_code=404, detail="No active airline found")

    route = db.query(Route).filter(Route.id == route_id, Route.airline_id == airline.id).first()
    if not route:
        raise HTTPException(status_code=404, detail="Route not found")

    route.is_active = False
    db.commit()
    return {"message": "Route deactivated"}


@router.post("/{route_id}/assign", response_model=dict)
def assign_aircraft(
    route_id: int,
    data: AssignAircraftRequest,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
):
    airline = db.query(Airline).filter(Airline.owner_id == user.id, Airline.is_active).first()
    if not airline:
        raise HTTPException(status_code=404, detail="No active airline found")

    route = db.query(Route).filter(Route.id == route_id, Route.airline_id == airline.id).first()
    if not route:
        raise HTTPException(status_code=404, detail="Route not found")

    aircraft = db.query(Aircraft).filter(
        Aircraft.id == data.aircraft_id,
        Aircraft.airline_id == airline.id,
    ).first()
    if not aircraft:
        raise HTTPException(status_code=404, detail="Aircraft not found in your fleet")

    assignment = RouteAssignment(
        route_id=route_id,
        aircraft_id=data.aircraft_id,
        frequency=data.frequency,
    )
    db.add(assignment)
    db.commit()
    return {"message": "Aircraft assigned to route"}


def _enrich_routes(routes: list[Route], db: Session) -> list[RouteResponse]:
    return [_enrich_route(r, db) for r in routes]


def _enrich_route(route: Route, db: Session) -> RouteResponse:
    from_ap = db.query(Airport).filter(Airport.id == route.from_airport_id).first()
    to_ap = db.query(Airport).filter(Airport.id == route.to_airport_id).first()
    airline = db.query(Airline).filter(Airline.id == route.airline_id).first()

    return RouteResponse(
        id=route.id,
        from_airport_id=route.from_airport_id,
        to_airport_id=route.to_airport_id,
        airline_id=route.airline_id,
        distance=route.distance,
        flight_number=route.flight_number,
        frequency=route.frequency,
        duration=route.duration,
        is_active=route.is_active,
        price_economy=route.price_economy,
        price_business=route.price_business,
        price_first=route.price_first,
        price_discount=route.price_discount,
        capacity_economy=route.capacity_economy,
        capacity_business=route.capacity_business,
        capacity_first=route.capacity_first,
        capacity_discount=route.capacity_discount,
        raw_quality=route.raw_quality,
        from_airport_iata=from_ap.iata if from_ap else "",
        from_airport_name=from_ap.name if from_ap else "",
        to_airport_iata=to_ap.iata if to_ap else "",
        to_airport_name=to_ap.name if to_ap else "",
        airline_name=airline.name if airline else "",
    )
