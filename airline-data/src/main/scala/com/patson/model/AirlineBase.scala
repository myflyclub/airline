package com.patson.model

import com.patson.data.{AirlineSource, AirportSource, AllianceSource}
import com.patson.model.airplane.Model
import com.patson.util.AllianceCache


case class AirlineBase(airline : Airline, airport : Airport, countryCode : String, scale : Int, foundedCycle : Int, headquarter : Boolean = false) {

  lazy val getValue : Long = {
    calculateUpgradeCost()
  }

  def calculateUpgradeCost (scale: Int = scale, airlineType: AirlineType = airline.airlineType): Long = {
    val adjustedScale = Math.min(12 + 0.2 * (scale - 12), Math.max(1, scale)) //for non-existing base, calculate as if the base is 1, cap at 12
    val airportSizeDiscount =  (Math.max(4, airport.size).toDouble / 2) * 0.1
    val baseCost = airport.rating.overallDifficulty * 105000

    if (headquarter && scale == 1) {
      //free to start HQ
      0
    } else if (airlineType == MegaHqAirline && headquarter) {
      //mega hq hq
      val cost: Long = baseCost.toLong * Math.pow(0.93 + airportSizeDiscount, adjustedScale).toLong
      Math.max(5_000_000, cost - 40_000_000)
    } else if (airlineType == MegaHqAirline && ! headquarter) {
      //mega hq base
      val cost: Long = (baseCost.toLong * Math.pow(1.2 + airportSizeDiscount, adjustedScale)).toLong
      cost + 30_000_000
    } else {
      //regular
      val cost: Long = (baseCost.toLong * Math.pow(1.1 + airportSizeDiscount, adjustedScale)).toLong
      cost
    }
  }

  lazy val getUpkeep : Long = {
    calculateUpkeep(scale)
  }

  def calculateUpkeep (scale: Int, airlineType: AirlineType = airline.airlineType): Long = {
    val adjustedScale = Math.max(1, scale) //for non-existing base, calculate as if the base is 1
    val baseUpkeep = airport.rating.overallDifficulty * 65
    val airportSizeMod =  (airport.size.toDouble / 2) * 0.1

    if (airlineType == MegaHqAirline && headquarter) {
      //mega hq hq
      (baseUpkeep.toLong * Math.pow(adjustedScale, 1.86)).toLong
    } else if (airlineType == MegaHqAirline && ! headquarter) {
      //mega hq base
      (baseUpkeep.toLong * Math.pow(adjustedScale, 2.0 + airportSizeMod)).toLong
    } else {
      //regular
      (baseUpkeep.toLong * Math.pow(adjustedScale, 1.9 + airportSizeMod)).toLong
    }
  }

  val getOfficeStaffCapacity = AirlineBase.getOfficeStaffCapacity(scale, headquarter)

  val delegatesRequired = {
    if (headquarter) {
      Math.max(0, Math.ceil(scale.toDouble / 2) - 1)
    } else {
      Math.ceil(scale.toDouble / 2)
    }
  }.toInt

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




