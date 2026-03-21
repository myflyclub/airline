package com.patson.model

import com.patson.data.{AirlineSource, AirportSource, AllianceSource}
import com.patson.model.airplane.Model
import com.patson.util.AllianceCache


case class AirlineBase(airline : Airline, airport : Airport, countryCode : String, scale : Int, foundedCycle : Int, headquarter : Boolean = false) {
  private def computeEffectiveScale(s: Int): Double = if (s <= 6) {
    Math.max(1.0, s * 2.0)
  } else {
    12.0 + 0.2 * (s - 6.0)
  }

  lazy val getValue : Long = {
    calculateUpgradeCost()
  }

  def calculateUpgradeCost(scale: Int = scale, airlineType: AirlineType = airline.airlineType): Long = {
    val effectiveScale = computeEffectiveScale(scale)
    val airportSizeDiscount = Math.max(6.0, airport.size.toDouble) * 0.05
    val baseCost = airport.rating.overallDifficulty * 200000 // Doubled

    if (headquarter && scale == 1) {
      0
    } else if (airlineType == MegaHqAirline && headquarter) {
      val cost: Long = (baseCost * Math.pow(0.95 + airportSizeDiscount, effectiveScale)).toLong
      Math.max(12_000_000L, cost - 60_000_000L) // Modifiers doubled
    } else if (airlineType == MegaHqAirline && !headquarter) {
      val cost: Long = (baseCost * Math.pow(1.2 + airportSizeDiscount, effectiveScale)).toLong
      cost + 60_000_000L // Modifier doubled
    } else {
      (baseCost * Math.pow(1.1 + airportSizeDiscount, effectiveScale)).toLong
    }
  }

  lazy val getUpkeep : Long = {
    calculateUpkeep(scale)
  }

  def calculateUpkeep(scale: Int, airlineType: AirlineType = airline.airlineType): Long = {
    val effectiveScale = computeEffectiveScale(scale)
    val baseUpkeep = airport.rating.overallDifficulty * 135
    val airportSizeMod = airport.size.toDouble * 0.05

    if (airlineType == MegaHqAirline && headquarter) {
      (baseUpkeep * Math.pow(effectiveScale, 1.7 + airportSizeMod / 2)).toLong
    } else if (airlineType == MegaHqAirline && !headquarter) {
      (baseUpkeep * Math.pow(effectiveScale, 1.9 + airportSizeMod)).toLong
    } else {
      (baseUpkeep * Math.pow(effectiveScale, 1.8 + airportSizeMod)).toLong
    }
  }

  val getOfficeStaffCapacity = AirlineBase.getOfficeStaffCapacity(scale, headquarter)


  def getOvertimeCompensation(staffRequired: Double) = {
    if (getOfficeStaffCapacity >= staffRequired) {
      0
    } else {
      val delta = staffRequired.toInt - getOfficeStaffCapacity
      var compensation = 0
      compensation += (delta.toDouble * (50000 + airport.income.toDouble * 0.8) / Period.yearLength * 10).toInt //weekly compensation, *10, as otherwise it's too low

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
    val flightTypeSpecializations = specializations.collect { case spec: FlightTypeSpecialization => spec }
    if (flightTypeSpecializations.isEmpty) {
      1
    } else {
      flightTypeSpecializations.map(_.staffModifier(flightCategory, model, serviceStars)).sum - (flightTypeSpecializations.size - 1)
    }
  }

  lazy val specializations : List[AirlineBaseSpecialization] = {
    (AirlineBaseSpecialization.values.filter(_.free) ++
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
  val BASE_STAFF_PER_LEVEL = 200
  def getOfficeStaffCapacity(scale : Int, isHeadquarters : Boolean) = {
    val base =
      if (isHeadquarters) {
        30
      } else {
        0
      }
    val scaleBonus =
      if (isHeadquarters) {
        220 * scale
      } else {
        BASE_STAFF_PER_LEVEL * scale
      }

    base + scaleBonus
  }

  /**
   * used frontend
   *
   * @param airline
   * @param targetBase
   * @return
   */
  def validAllianceBasesAtAirport(airline: Airline, targetBase: AirlineBase): Option[String] = {
    AllianceSource.loadAllianceMemberByAirline(airline).filter(_.role != AllianceRole.APPLICANT).foreach { allianceMember =>
      AllianceCache.getAlliance(allianceMember.allianceId, fullLoad = true).foreach { alliance =>
        // Tally what type of airline bases belong to
        var baseRegionalBonus = 0
        var baseOther = 0
        alliance.members.flatMap { allianceMember =>
          allianceMember.airline.getBases().map { base =>
            if (!base.headquarter && base.airline.airlineType == RegionalAirline && baseRegionalBonus < RegionalAirline.extraSharedBaseLimit && targetBase.airport.id == base.airport.id) {
              baseRegionalBonus = baseRegionalBonus + 1
            } else if (!base.headquarter && targetBase.airport.id == base.airport.id) {
              baseOther = baseOther + 1
            }
          }
        }

        if (airline.airlineType == RegionalAirline) {// For regional airlines, allow up to 1 existing alliance base
          if (baseOther + baseRegionalBonus >= 1 + RegionalAirline.extraSharedBaseLimit) {
            return Some(s"There are too many alliance member bases at this airport, even for a regional airline.")
          }
        } else {
          if (baseOther >= 1) {
            return Some(s"There can only be ${baseOther} alliance member base per airport.")
          }
        }
      }
    }
    None // Return none if no issues found
  }
}




