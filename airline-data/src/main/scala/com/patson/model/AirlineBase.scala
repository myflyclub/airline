package com.patson.model

import com.patson.data.{AirlineSource, AirportSource, AllianceSource}
import com.patson.model.airplane.Model
import com.patson.util.AllianceCache


case class AirlineBase(airline : Airline, airport : Airport, countryCode : String, scale : Int, foundedCycle : Int, headquarter : Boolean = false) {

  lazy val getValue : Long = {
    calculateUpgradeCost()
  }

  def calculateUpgradeCost (scale: Int = scale, airlineType: AirlineType = airline.airlineType): Long = {
    val mappedAdjustedScale = Math.min(2.2 * scale - 1.2, 0.4 * scale + 9.6) //for non-existing base, calculate as if the base is 1, cap at 6
    val airportSizeDiscount =  (Math.max(6, airport.size).toDouble / 2) * 0.1
    val baseCost = airport.rating.overallDifficulty * 105000

    if (headquarter && scale == 1) {
      //free to start HQ
      0
    } else if (airlineType == MegaHqAirline && headquarter) {
      //mega hq hq
      val cost: Long = baseCost.toLong * Math.pow(0.92 + airportSizeDiscount, mappedAdjustedScale).toLong
      Math.max(10_000_000, cost - 50_000_000)
    } else if (airlineType == MegaHqAirline && ! headquarter) {
      //mega hq base
      val cost: Long = (baseCost.toLong * Math.pow(1.2 + airportSizeDiscount, mappedAdjustedScale)).toLong
      cost + 30_000_000
    } else {
      //regular
      val cost: Long = (baseCost.toLong * Math.pow(1.1 + airportSizeDiscount, mappedAdjustedScale)).toLong
      cost
    }
  }

  lazy val getUpkeep : Long = {
    calculateUpkeep(scale)
  }

  def calculateUpkeep (scale: Int, airlineType: AirlineType = airline.airlineType): Long = {
    val mappedAdjustedScale = (19.0 * Math.max(1, scale) - 10.0) / 9.0 //for non-existing base, calculate as if the base is 1
    val baseUpkeep = airport.rating.overallDifficulty * 68
    val airportSizeMod =  (airport.size.toDouble / 2) * 0.1

    if (airlineType == MegaHqAirline && headquarter) {
      //mega hq hq
      (baseUpkeep.toLong * Math.pow(mappedAdjustedScale, 1.84)).toLong
    } else if (airlineType == MegaHqAirline && ! headquarter) {
      //mega hq base
      (baseUpkeep.toLong * Math.pow(mappedAdjustedScale, 2.0 + airportSizeMod)).toLong
    } else {
      //regular
      (baseUpkeep.toLong * Math.pow(mappedAdjustedScale, 1.9 + airportSizeMod)).toLong
    }
  }

  val getOfficeStaffCapacity = AirlineBase.getOfficeStaffCapacity(scale, headquarter)


  def getOvertimeCompensation(staffRequired: Double) = {
    if (getOfficeStaffCapacity >= staffRequired) {
      0
    } else {
      val delta = staffRequired.toInt - getOfficeStaffCapacity
      var compensation = 0
      compensation += (delta.toDouble * (50000 + airport.income.toDouble * 0.8) / 52 * 10).toInt //weekly compensation, *10, as otherwise it's too low

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
        200 * scale
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




