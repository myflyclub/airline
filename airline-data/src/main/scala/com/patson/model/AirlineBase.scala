package com.patson.model

import com.patson.data.{AirlineSource, AirportSource, CountrySource}
import com.patson.model.AirlineBaseSpecialization.FlightTypeSpecialization
import com.patson.model.airplane.Model
import com.patson.util.AirportCache


case class AirlineBase(airline : Airline, airport : Airport, countryCode : String, scale : Int, foundedCycle : Int, headquarter : Boolean = false) {
  private lazy val COST_EXPONENTIAL_BASE = if (airline.airlineType == AirlineType.MEGA_HQ && headquarter) {
    1.54
  } else if (airline.airlineType == AirlineType.MEGA_HQ && !headquarter) {
    1.73
  } else {
    1.68
  }

  lazy val getValue : Long = {
    calculateUpgradeCost(scale)
  }

  def calculateUpgradeCost (scale: Int): Long = {
    val adjustedScale = Math.min(12, Math.max(1, scale)) //for non-existing base, calculate as if the base is 1, cap at 12
    if (scale == 0) {
      0
    } else if (headquarter && scale == 1) { //free to start HQ
      0
    } else {
      val baseCost = if (airline.airlineType == AirlineType.MEGA_HQ) {
        if (headquarter) {
          (airport.rating.overallRating * 100000).toLong
        } else {
          (40 * 1000000 + airport.rating.overallRating * 120000).toLong
        }
      } else {
        (1000000 + airport.rating.overallRating * 120000).toLong
      }
      val cost = baseCost * airportTypeMultiplier * airportSizeRatio * Math.pow (COST_EXPONENTIAL_BASE, (adjustedScale - 1) )
      (cost * Math.max(1, 1 + (scale - 12) * 0.2)).toLong
    }
  }

  lazy val getUpkeep : Long = {
    calculateUpkeep(scale)
  }

  def calculateUpkeep (scale: Int): Long = {
    val adjustedScale = Math.min(12, Math.max(1, scale)) //for non-existing base, calculate as if the base is 1, cap at 12
    val baseUpkeep = 3000 + airport.rating.overallRating * 150
    val upkeep = baseUpkeep * airportTypeMultiplier * airportSizeRatio * Math.pow(COST_EXPONENTIAL_BASE, adjustedScale - 1)
    (upkeep * Math.max(1, 1 + (scale - 12) * 0.1)).toLong
  }

  lazy val airportTypeMultiplier =
    if (airport.isDomesticAirport()) {
      0.7
    } else if (airport.isGateway()) {
      1.1
    } else {
      1.0
    }

  lazy val airportSizeRatio =
    if (airport.size > 7) {
      1.0
    } else { //discount for size < 7
      0.3 + airport.size * 0.1
    }

  val getOfficeStaffCapacity = AirlineBase.getOfficeStaffCapacity(scale, headquarter)

  val delegatesRequired = {
    if (headquarter) {
      Math.max(0, Math.ceil(scale.toDouble / 2) - 1)
    } else {
      Math.ceil(scale.toDouble / 2)
    }
  }.toInt

  def getOvertimeCompensation(staffRequired : Int) = {
    if (getOfficeStaffCapacity >= staffRequired) {
      0
    } else {
      val delta = staffRequired - getOfficeStaffCapacity
      var compensation = 0
      val income = CountrySource.loadCountryByCode(countryCode).map(_.income).getOrElse(0)
      compensation += delta * (50000 + income) / 52 * 10 //weekly compensation, *10, as otherwise it's too low

      compensation
    }
  }

  /**
    * if not allowed, return LEFT[the title required]
    */
  lazy val allowAirline : Airline => Either[Title.Value, Title.Value]= (airline : Airline) => {

    val requiredTitle =
      if (airport.isGateway()) {
        Title.ESTABLISHED_AIRLINE
      } else {
        Title.PRIVILEGED_AIRLINE
      }
    val title = CountryAirlineTitle.getTitle(airport.countryCode, airline)
    if (title.title.id <= requiredTitle.id) { //lower id means higher title
      Right(requiredTitle)
    } else {
      Left(requiredTitle)
    }
  }

  lazy val getStaffModifier : ((FlightCategory.Value, Model.Type.Value, Int) => Double) = (flightCategory, model, serviceStars) => {
    val flightTypeSpecializations = specializations.filter(_.getType == BaseSpecializationType.FLIGHT_TYPE).map(_.asInstanceOf[FlightTypeSpecialization])
    if (flightTypeSpecializations.isEmpty) {
      1
    } else {
      flightTypeSpecializations.map(_.staffModifier(flightCategory, model, serviceStars)).sum - (flightTypeSpecializations.size - 1)
    }
  }

  lazy val specializations : List[AirlineBaseSpecialization.Value] = {
    (AirlineBaseSpecialization.values.filter(_.free).toList ++
    AirportSource.loadAirportBaseSpecializations(airport.id, airline.id)).filter(_.scaleRequirement <= scale)
  }

  def delete(): Unit = {
    AirlineSource.loadLoungeByAirlineAndAirport(airline.id, airport.id).foreach { lounge =>
      AirlineSource.deleteLounge(lounge)
    }

    //remove all base spec and bonus since it has no foreign key on base
    specializations.foreach { spec =>
      spec.unapply(airline, airport)
    }
    AirportSource.updateAirportBaseSpecializations(airport.id, airline.id, List.empty)
    //then delete the base itself
    AirlineSource.deleteAirlineBase(this)
  }
}

object AirlineBase {
  def getOfficeStaffCapacity(scale : Int, isHeadquarters : Boolean) = {
    val base =
      if (isHeadquarters) {
        60
      } else {
        0
      }
    val scaleBonus =
      if (isHeadquarters) {
        80 * scale
      } else {
        60 * scale
      }

    base + scaleBonus
  }
}




