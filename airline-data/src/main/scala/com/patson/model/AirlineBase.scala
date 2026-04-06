package com.patson.model

import com.patson.data.{AirlineSource, AirportSource, AllianceSource}
import com.patson.model.airplane.Model
import com.patson.util.AllianceCache


case class AirlineBase(airline : Airline, airport : Airport, countryCode : String, scale : Int, foundedCycle : Int, headquarter : Boolean = false) {
  // Returns a multiplier that starts at 1.0 and smoothly rides the S-Curve to the 'plateau'
  private def computeScaleMultiplier(scale: Int, plateau: Double, steepness: Double, midpoint: Double = 6.0): Double = {
    val rawLogistic = plateau / (1.0 + Math.exp(-steepness * (scale - midpoint)))
    val offset = plateau / (1.0 + Math.exp(-steepness * (1.0 - midpoint)))
    1.0 + rawLogistic - offset
  }

  lazy val getValue : Long = {
    calculateUpgradeCost()
  }

  def calculateUpgradeCost(scale: Int = this.scale, airlineType: AirlineType = airline.airlineType): Long = {
    if (headquarter && scale == 1) {
      0L
    } else {
      val baseCost = airport.rating.overallDifficulty * 400_000
      val (plateau, steepness, midpoint, mod) = (airlineType, headquarter) match {
        case (MegaHqAirline, true)  => (150.0, 0.78, 11.0, 0)
        case (MegaHqAirline, false) => (275.0, 0.65, 8.0, 2000000)
        case _                      => (235.0, 0.65, 9.0, 0)
      }

      val curveMultiplier = computeScaleMultiplier(scale, plateau, steepness, midpoint)
      (baseCost * curveMultiplier + mod).toLong
    }
  }

  lazy val getUpkeep : Long = {
    calculateUpkeep(scale)
  }

  def calculateUpkeep(scale: Int, airlineType: AirlineType = airline.airlineType): Long = {
    val baseUpkeep = 15000 + airport.rating.overallDifficulty * 1600

    val (plateau, steepness, mod) = (airlineType, headquarter) match {
      case (MegaHqAirline, true)  => (70.0, 0.135, -5_000)
      case (MegaHqAirline, false) => (95.0, 0.175, 5_000)
      case _                      => (79.0, 0.155, 0)
    }

    val curveMultiplier = computeScaleMultiplier(scale, plateau, steepness, 7.5)

    val startingDiscount = if (headquarter) scale match {
      case 1 => 0.1
      case 2 => 0.5
      case _ => 1.0
    } else 1.0

    Math.max(10_000 + mod, baseUpkeep * curveMultiplier * startingDiscount + mod).toLong
  }

  val getOfficeStaffCapacity = AirlineBase.getOfficeStaffCapacity(scale, headquarter)

  def getOvertimeCompensation(staffRequired: Int) = {
    if (getOfficeStaffCapacity >= staffRequired) {
      0
    } else {
      val delta = staffRequired - getOfficeStaffCapacity
      val compensation = calculateUpkeep(scale + 1, LegacyAirline).toDouble / AirlineBase.getOfficeStaffCapacity(scale + 1, headquarter)
      (Math.log(delta / 2 + 1) * delta * compensation).toInt
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
        (BASE_STAFF_PER_LEVEL + 25) * scale
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




