from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy.orm import Session

from app.database import get_db
from app.models.aircraft import AircraftModel, Aircraft
from app.models.airline import Airline
from app.models.user import User
from app.schemas.aircraft import AircraftModelResponse, AircraftPurchase, AircraftResponse, AircraftDetail
from app.utils.auth import get_current_user

router = APIRouter(prefix="/api/aircraft", tags=["aircraft"])


@router.get("/models", response_model=list[AircraftModelResponse])
def list_aircraft_models(
    aircraft_type: str = None,
    min_range: int = Query(0, ge=0),
    db: Session = Depends(get_db),
):
    query = db.query(AircraftModel)
    if aircraft_type:
        query = query.filter(AircraftModel.aircraft_type == aircraft_type.upper())
    if min_range > 0:
        query = query.filter(AircraftModel.range_miles >= min_range)
    return query.order_by(AircraftModel.capacity).all()


@router.get("/models/{model_id}", response_model=AircraftModelResponse)
def get_aircraft_model(model_id: int, db: Session = Depends(get_db)):
    model = db.query(AircraftModel).filter(AircraftModel.id == model_id).first()
    if not model:
        raise HTTPException(status_code=404, detail="Aircraft model not found")
    return model


@router.get("/fleet/{airline_id}", response_model=list[AircraftResponse])
def get_fleet(airline_id: int, db: Session = Depends(get_db)):
    aircraft_list = (
        db.query(Aircraft)
        .filter(Aircraft.airline_id == airline_id)
        .all()
    )
    result = []
    for ac in aircraft_list:
        model = db.query(AircraftModel).filter(AircraftModel.id == ac.model_id).first()
        result.append(AircraftResponse(
            id=ac.id,
            model_id=ac.model_id,
            model_name=model.name if model else "",
            airline_id=ac.airline_id,
            condition=ac.condition,
            age_cycles=ac.age_cycles,
            is_active=ac.is_active,
            home_base_id=ac.home_base_id,
            purchase_price=ac.purchase_price,
        ))
    return result


@router.post("/purchase", response_model=list[AircraftResponse], status_code=status.HTTP_201_CREATED)
def purchase_aircraft(
    data: AircraftPurchase,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
):
    airline = db.query(Airline).filter(Airline.owner_id == user.id, Airline.is_active).first()
    if not airline:
        raise HTTPException(status_code=404, detail="No active airline found")

    model = db.query(AircraftModel).filter(AircraftModel.id == data.model_id).first()
    if not model:
        raise HTTPException(status_code=404, detail="Aircraft model not found")

    total_cost = model.price * data.quantity
    if airline.balance < total_cost:
        raise HTTPException(
            status_code=400,
            detail=f"Insufficient funds. Need ${total_cost:,}, have ${airline.balance:,}",
        )

    purchased = []
    for _ in range(data.quantity):
        aircraft = Aircraft(
            model_id=model.id,
            airline_id=airline.id,
            condition=100.0,
            home_base_id=data.home_base_id,
            purchase_price=model.price,
        )
        db.add(aircraft)
        purchased.append(aircraft)

    airline.balance -= total_cost
    db.commit()

    result = []
    for ac in purchased:
        db.refresh(ac)
        result.append(AircraftResponse(
            id=ac.id,
            model_id=ac.model_id,
            model_name=model.name,
            airline_id=ac.airline_id,
            condition=ac.condition,
            age_cycles=ac.age_cycles,
            is_active=ac.is_active,
            home_base_id=ac.home_base_id,
            purchase_price=ac.purchase_price,
        ))
    return result


@router.delete("/{aircraft_id}")
def sell_aircraft(
    aircraft_id: int,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
):
    airline = db.query(Airline).filter(Airline.owner_id == user.id, Airline.is_active).first()
    if not airline:
        raise HTTPException(status_code=404, detail="No active airline found")

    aircraft = db.query(Aircraft).filter(
        Aircraft.id == aircraft_id,
        Aircraft.airline_id == airline.id,
    ).first()
    if not aircraft:
        raise HTTPException(status_code=404, detail="Aircraft not found in your fleet")

    # Calculate sale value (depreciated)
    model = db.query(AircraftModel).filter(AircraftModel.id == aircraft.model_id).first()
    depreciation = max(0.2, 1.0 - (aircraft.age_cycles / model.lifespan * 0.8))
    condition_factor = aircraft.condition / 100
    sale_value = int(model.price * depreciation * condition_factor * 0.9)  # 10% broker fee

    airline.balance += sale_value
    db.delete(aircraft)
    db.commit()

    return {"message": f"Aircraft sold for ${sale_value:,}", "sale_value": sale_value}
