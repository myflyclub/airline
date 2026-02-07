from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy.orm import Session
from typing import Optional

from app.database import get_db
from app.models.airport import Airport
from app.schemas.airport import AirportResponse, AirportMapPoint, AirportSearchResult

router = APIRouter(prefix="/api/airports", tags=["airports"])


@router.get("/", response_model=list[AirportResponse])
def list_airports(
    country: Optional[str] = None,
    min_size: int = Query(0, ge=0, le=8),
    max_size: int = Query(8, ge=0, le=8),
    limit: int = Query(100, le=5000),
    offset: int = Query(0, ge=0),
    db: Session = Depends(get_db),
):
    query = db.query(Airport).filter(Airport.size >= min_size, Airport.size <= max_size)
    if country:
        query = query.filter(Airport.country_code == country)
    return query.order_by(Airport.size.desc()).offset(offset).limit(limit).all()


@router.get("/map", response_model=list[AirportMapPoint])
def get_map_airports(
    min_size: int = Query(3, ge=0, le=8),
    db: Session = Depends(get_db),
):
    """Get lightweight airport data for map rendering. Filters by min_size for performance."""
    airports = (
        db.query(Airport)
        .filter(Airport.size >= min_size)
        .order_by(Airport.size.desc())
        .all()
    )
    return airports


@router.get("/search", response_model=list[AirportSearchResult])
def search_airports(
    q: str = Query(..., min_length=1),
    limit: int = Query(20, le=100),
    db: Session = Depends(get_db),
):
    """Search airports by IATA code, name, or city."""
    pattern = f"%{q}%"
    airports = (
        db.query(Airport)
        .filter(
            (Airport.iata.ilike(pattern))
            | (Airport.name.ilike(pattern))
            | (Airport.city.ilike(pattern))
        )
        .order_by(Airport.size.desc())
        .limit(limit)
        .all()
    )
    return airports


@router.get("/{airport_id}", response_model=AirportResponse)
def get_airport(airport_id: int, db: Session = Depends(get_db)):
    airport = db.query(Airport).filter(Airport.id == airport_id).first()
    if not airport:
        raise HTTPException(status_code=404, detail="Airport not found")
    return airport


@router.get("/iata/{iata}", response_model=AirportResponse)
def get_airport_by_iata(iata: str, db: Session = Depends(get_db)):
    airport = db.query(Airport).filter(Airport.iata == iata.upper()).first()
    if not airport:
        raise HTTPException(status_code=404, detail="Airport not found")
    return airport
