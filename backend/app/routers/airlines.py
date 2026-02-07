from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session

from app.database import get_db
from app.models.airline import Airline, AirlineBase, FinancialRecord, Loan
from app.models.aircraft import Aircraft
from app.models.route import Route
from app.models.user import User
from app.models.airport import Airport
from app.schemas.airline import (
    AirlineCreate, AirlineUpdate, AirlineResponse, AirlineSummary,
    BaseCreate, BaseResponse, LoanRequest, LoanResponse,
)
from app.utils.auth import get_current_user
from app.config import get_settings

router = APIRouter(prefix="/api/airlines", tags=["airlines"])
settings = get_settings()


@router.get("/", response_model=list[AirlineResponse])
def list_airlines(db: Session = Depends(get_db)):
    return db.query(Airline).filter(Airline.is_active).all()


@router.post("/", response_model=AirlineResponse, status_code=status.HTTP_201_CREATED)
def create_airline(
    data: AirlineCreate,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
):
    # Check user doesn't already have an active airline
    existing = db.query(Airline).filter(Airline.owner_id == user.id, Airline.is_active).first()
    if existing:
        raise HTTPException(status_code=400, detail="You already have an active airline")

    if db.query(Airline).filter(Airline.airline_code == data.airline_code.upper()).first():
        raise HTTPException(status_code=400, detail="Airline code already taken")

    airline = Airline(
        name=data.name,
        airline_code=data.airline_code.upper(),
        country_code=data.country_code,
        balance=settings.starting_balance,
        owner_id=user.id,
    )
    db.add(airline)
    db.commit()
    db.refresh(airline)
    return airline


@router.get("/mine", response_model=AirlineSummary)
def get_my_airline(
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
):
    airline = db.query(Airline).filter(Airline.owner_id == user.id, Airline.is_active).first()
    if not airline:
        raise HTTPException(status_code=404, detail="No airline found. Create one first.")

    fleet_size = db.query(Aircraft).filter(Aircraft.airline_id == airline.id, Aircraft.is_active).count()
    route_count = db.query(Route).filter(Route.airline_id == airline.id, Route.is_active).count()

    return AirlineSummary(
        id=airline.id,
        name=airline.name,
        airline_code=airline.airline_code,
        balance=airline.balance,
        reputation=airline.reputation,
        fleet_size=fleet_size,
        route_count=route_count,
    )


@router.get("/{airline_id}", response_model=AirlineResponse)
def get_airline(airline_id: int, db: Session = Depends(get_db)):
    airline = db.query(Airline).filter(Airline.id == airline_id).first()
    if not airline:
        raise HTTPException(status_code=404, detail="Airline not found")
    return airline


@router.patch("/{airline_id}", response_model=AirlineResponse)
def update_airline(
    airline_id: int,
    data: AirlineUpdate,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
):
    airline = db.query(Airline).filter(Airline.id == airline_id, Airline.owner_id == user.id).first()
    if not airline:
        raise HTTPException(status_code=404, detail="Airline not found or not owned by you")

    for field, value in data.model_dump(exclude_unset=True).items():
        setattr(airline, field, value)

    db.commit()
    db.refresh(airline)
    return airline


# --- Bases ---

@router.get("/{airline_id}/bases", response_model=list[BaseResponse])
def get_bases(airline_id: int, db: Session = Depends(get_db)):
    bases = db.query(AirlineBase).filter(AirlineBase.airline_id == airline_id).all()
    result = []
    for base in bases:
        airport = db.query(Airport).filter(Airport.id == base.airport_id).first()
        result.append(BaseResponse(
            id=base.id,
            airport_id=base.airport_id,
            base_type=base.base_type,
            scale=base.scale,
            airport_iata=airport.iata if airport else "",
            airport_name=airport.name if airport else "",
        ))
    return result


@router.post("/{airline_id}/bases", response_model=BaseResponse, status_code=status.HTTP_201_CREATED)
def create_base(
    airline_id: int,
    data: BaseCreate,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
):
    airline = db.query(Airline).filter(Airline.id == airline_id, Airline.owner_id == user.id).first()
    if not airline:
        raise HTTPException(status_code=404, detail="Airline not found or not owned by you")

    airport = db.query(Airport).filter(Airport.id == data.airport_id).first()
    if not airport:
        raise HTTPException(status_code=404, detail="Airport not found")

    existing = db.query(AirlineBase).filter(
        AirlineBase.airline_id == airline_id,
        AirlineBase.airport_id == data.airport_id,
    ).first()
    if existing:
        raise HTTPException(status_code=400, detail="Base already exists at this airport")

    # Check if trying to set HQ when one already exists
    if data.base_type == "headquarters":
        existing_hq = db.query(AirlineBase).filter(
            AirlineBase.airline_id == airline_id,
            AirlineBase.base_type == "headquarters",
        ).first()
        if existing_hq:
            raise HTTPException(status_code=400, detail="Headquarters already established")

    base_cost = airport.size * 1_000_000
    if airline.balance < base_cost:
        raise HTTPException(status_code=400, detail=f"Insufficient funds. Base costs ${base_cost:,}")

    airline.balance -= base_cost
    base = AirlineBase(
        airline_id=airline_id,
        airport_id=data.airport_id,
        base_type=data.base_type,
    )
    db.add(base)
    db.commit()
    db.refresh(base)

    return BaseResponse(
        id=base.id,
        airport_id=base.airport_id,
        base_type=base.base_type,
        scale=base.scale,
        airport_iata=airport.iata,
        airport_name=airport.name,
    )


# --- Loans ---

@router.get("/{airline_id}/loans", response_model=list[LoanResponse])
def get_loans(airline_id: int, db: Session = Depends(get_db)):
    return db.query(Loan).filter(Loan.airline_id == airline_id).all()


@router.post("/{airline_id}/loans", response_model=LoanResponse, status_code=status.HTTP_201_CREATED)
def take_loan(
    airline_id: int,
    data: LoanRequest,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
):
    airline = db.query(Airline).filter(Airline.id == airline_id, Airline.owner_id == user.id).first()
    if not airline:
        raise HTTPException(status_code=404, detail="Airline not found or not owned by you")

    if data.amount > settings.max_loan_amount:
        raise HTTPException(status_code=400, detail=f"Maximum loan amount is ${settings.max_loan_amount:,}")

    # Interest rate based on reputation
    base_rate = 0.12
    reputation_discount = airline.reputation * 0.0005
    interest_rate = max(0.03, base_rate - reputation_discount)

    loan = Loan(
        airline_id=airline_id,
        principal=data.amount,
        remaining=data.amount,
        interest_rate=interest_rate,
        term_cycles=data.term_cycles,
        remaining_cycles=data.term_cycles,
        creation_cycle=0,
    )
    airline.balance += data.amount
    db.add(loan)
    db.commit()
    db.refresh(loan)
    return loan
