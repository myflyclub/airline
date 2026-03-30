package com.patson.model

import com.patson.model.airplane.Model
import com.patson.model.airplane.Model.Type
import com.patson.DemandGenerator
import com.patson.data.{CycleSource, GameConstants}
import com.patson.model.AirportFeatureType.{AirportFeatureType, BUSH_HUB, DOMESTIC_AIRPORT, ELITE_CHARM, FINANCIAL_HUB, GATEWAY_AIRPORT, INTERNATIONAL_HUB, ISOLATED_TOWN, OLYMPICS_IN_PROGRESS, OLYMPICS_PREPARATIONS, PRESTIGE_CHARM, UNKNOWN, VACATION_HUB}
import com.patson.model.IsolatedTownFeature.HUB_RANGE_BRACKETS

import java.util.concurrent.ThreadLocalRandom

abstract class AirportFeature {
  val MAX_STRENGTH = 100
  def strength : Int
  def featureType : AirportFeatureType.Value
  val strengthFactor : Double = strength.toDouble / MAX_STRENGTH
  val isDynamic: Boolean = false

  def demandAdjustment(rawDemand: Double, passengerType: PassengerType.Value, airportId: Int, fromAirport: Airport, toAirport: Airport, affinity: Int, distance: Int) : Int

  lazy val getDescription: String = {
    featureType match {
      case INTERNATIONAL_HUB => "Global Vacation Destination - Tourists travel here from everywhere."
      case ELITE_CHARM => "Elite Destination – Elites travel here. "
      case VACATION_HUB => "Local Vacation Destination - Domestic & high-affinity tourists travel here."
      case FINANCIAL_HUB => "Business Hub - Center for business passengers."
      case DOMESTIC_AIRPORT => s"Domestic Airport – Supports higher frequencies with lower base upkeep. If flight is international, only accepts aircraft smaller than ${DomesticAirportFeature.internationalMaxCapacity} capacity"
      case ISOLATED_TOWN => s"Isolated - Increased demand within ${this.asInstanceOf[IsolatedTownFeature].boostRange}km."
      case BUSH_HUB => s"Bush hub - Higher demand to nearby isolated airports."
      case GATEWAY_AIRPORT => "Gateway - Allows foreign airlines to build bases more easily."
      case OLYMPICS_PREPARATIONS => "Preparing the Olympic Games."
      case OLYMPICS_IN_PROGRESS => "Year of the Olympic Games."
      case PRESTIGE_CHARM => "Prestige Charm - has increased demand to other prestigious airports."
      case UNKNOWN => "Unknown"
    }
  }
}

object AirportFeature {
  import AirportFeatureType._
  def apply(featureType : AirportFeatureType, strength : Int) : AirportFeature = {
    featureType match {
      case INTERNATIONAL_HUB => InternationalHubFeature(strength)
      case ELITE_CHARM => EliteFeature(strength)
      case VACATION_HUB => VacationHubFeature(strength)
      case FINANCIAL_HUB => FinancialHubFeature(strength)
      case DOMESTIC_AIRPORT => DomesticAirportFeature()
      case GATEWAY_AIRPORT => GatewayAirportFeature()
      case ISOLATED_TOWN => IsolatedTownFeature(strength)
      case BUSH_HUB => BushHubFeature()
      case OLYMPICS_PREPARATIONS => OlympicsPreparationsFeature(strength)
      case OLYMPICS_IN_PROGRESS => OlympicsInProgressFeature(strength)
      case PRESTIGE_CHARM => PrestigeFeature(strength)
    }
  }
}

sealed case class InternationalHubFeature(baseStrength : Int, boosts : List[AirportBoost] = List.empty, override val isDynamic: Boolean = false) extends AirportFeature {
  val featureType = AirportFeatureType.INTERNATIONAL_HUB
  override def demandAdjustment(rawDemand: Double, passengerType: PassengerType.Value, airportId: Int, fromAirport: Airport, toAirport: Airport, affinity: Int, distance: Int) : Int = {
    if (airportId == toAirport.id  && passengerType == PassengerType.TOURIST) { //only affect if as a destination
      val charmStrength = 0.0004 * strengthFactor
      val incomeModifier = Math.max(fromAirport.income.toDouble / (Airport.HIGH_INCOME * 0.6), 0.2)
      val distanceModifier = if (distance < 500) {
        distance.toDouble / 500
      } else if (distance < 4500) {
        1.0
      } else {
        4500 / distance.toDouble
      }
      val airportAffinityMutliplier: Double =
        if (affinity >= 5) (affinity - 5) * 0.05 + 1.05 //domestic+
        else if (affinity == 4) 0.75
        else if (affinity == 3) 0.55
        else if (affinity == 2) 0.35
        else if (affinity == 1) 0.25
        else if (affinity == 0) 0.1
        else 0.05
      val specialCountryModifier =
        if (List("GB").contains(fromAirport.countryCode) && fromAirport.countryCode != toAirport.countryCode) {
          2.5
        } else if (List("AU","NZ","BE","NL","LU","DE","AT","CH","DK","SE","NO").contains(fromAirport.countryCode)) {
          2.2 //they travel a lot...
        } else if (fromAirport.zone.contains("EU")) {
          1.9
        } else if (List("US","CN","IN").contains(fromAirport.countryCode)) {
          0.55
        } else 1.0

      (fromAirport.popMiddleIncome * charmStrength * distanceModifier * airportAffinityMutliplier * incomeModifier * specialCountryModifier).toInt
    } else {
      0
    }
  }

  override lazy val strength = baseStrength + boosts.filter(_.boostType == AirportBoostType.INTERNATIONAL_HUB).map(_.value).sum.toInt
}

sealed case class EliteFeature(baseStrength : Int, boosts : List[AirportBoost] = List.empty, override val isDynamic: Boolean = false) extends AirportFeature {
  val featureType = AirportFeatureType.ELITE_CHARM

  override def demandAdjustment(rawDemand: Double, passengerType: PassengerType.Value, airportId: Int, fromAirport: Airport, toAirport: Airport, affinity: Int, distance: Int) : Int = {
    0
  }

  override lazy val strength = baseStrength + boosts.filter(_.boostType == AirportBoostType.ELITE_CHARM).map(_.value).sum.toInt
}

/**
 * applies strongly to domestic / high affinity matches
 */
sealed case class VacationHubFeature(baseStrength : Int, boosts : List[AirportBoost] = List.empty, override val isDynamic: Boolean = false) extends AirportFeature {
  val featureType = AirportFeatureType.VACATION_HUB

  override def demandAdjustment(rawDemand: Double, passengerType: PassengerType.Value, airportId: Int, fromAirport: Airport, toAirport: Airport, affinity: Int, distance: Int) : Int = {
    if (toAirport.id == airportId && passengerType == PassengerType.TOURIST) { //only affect if as a destination and tourists
      /**
       * based off Disney World, which has 13.6m domestic visitors or 4% of USA population, so at 100 strength charm 4% want to visit or a 1 charm 0.04%
       * (strengthFactor is already a percent)
       */
      val charmStrength = 0.00045 * strengthFactor
      val distanceModifier = if (distance < 400) {
        (distance - 25).toDouble / 400
      } else if (distance < 2000) {
        1.0
      } else {
        Math.max(2000 / distance.toDouble, 0.2)
      }
      val airportAffinityMutliplier: Double =
        if (affinity >= 5) (affinity - 5) * 0.05 + 1.1 //each increases 5%
        else if (affinity == 4) 0.6
        else if (affinity == 3) 0.25
        else if (affinity == 2) 0.1
        else if (affinity == 1) 0.05
        else 0
      val specialCountryModifier =
        if (List("CA","NO","NZ","JP").contains(fromAirport.countryCode) && fromAirport.countryCode == toAirport.countryCode) {
          2.2 //increase domestic tourism
        } else 1.0

      (specialCountryModifier * fromAirport.popMiddleIncome * charmStrength * distanceModifier * airportAffinityMutliplier).toInt
    } else {
      0
    }
  }

  override lazy val strength = baseStrength + boosts.filter(_.boostType == AirportBoostType.VACATION_HUB).map(_.value).sum.toInt
}

sealed case class FinancialHubFeature(baseStrength : Int, boosts : List[AirportBoost] = List.empty, override val isDynamic: Boolean = false) extends AirportFeature {
  val featureType = AirportFeatureType.FINANCIAL_HUB
  override def demandAdjustment(rawDemand: Double, passengerType: PassengerType.Value, airportId: Int, fromAirport: Airport, toAirport: Airport, affinity: Int, distance: Int) : Int = {
    if (passengerType == PassengerType.BUSINESS) {
      val hasFeatureInBothAirports = fromAirport.hasFeature(AirportFeatureType.FINANCIAL_HUB) && toAirport.hasFeature(AirportFeatureType.FINANCIAL_HUB)

      val matchOnlyTradeAffinities = 5
      val tradeAffinity = Computation.affinityToSet(fromAirport.zone, toAirport.zone, matchOnlyTradeAffinities).length

      val charmStrength =
        if (hasFeatureInBothAirports) {
          0.0002 * strengthFactor
        } else if (toAirport.id == airportId) { //going to business center
          0.00015 * strengthFactor
        } else {
          0.00002 * strengthFactor //small outbound demand from hubs
        }
      val distanceReducerExponent: Double = {
        if (hasFeatureInBothAirports && distance < 500 && distance > 290) {
          1
        } else if (distance < 500) {
          distance.toDouble / 500
        } else if (distance > 4000) {
          0.85 - distance.toDouble / 32000
        } else if (distance > 1000) {
          1.05 - distance.toDouble / 20000
        } else {
          1
        }
      }
      val airportAffinityMutliplier: Double =
        if (affinity >= 5) (affinity - 5) * 0.1 + 1.1 //domestic+
        else if (affinity == 4) 0.7
        else if (affinity == 3) 0.55
        else if (affinity == 2) 0.4
        else if (affinity == 1) 0.3
        else if (affinity == 0) 0.15
        else 0.1

      val baseDemand = Math.pow(fromAirport.popMiddleIncome * charmStrength * airportAffinityMutliplier, distanceReducerExponent)
      val crushMultiplier = if (hasFeatureInBothAirports || tradeAffinity > 0) 1.2 else Math.min(1.0, (toAirport.size / 6.0) * (affinity / 5.0))

      Math.max(0, (baseDemand * crushMultiplier).toInt)
    } else {
      0
    }
  }
  override lazy val strength = baseStrength + boosts.filter(_.boostType == AirportBoostType.FINANCIAL_HUB).map(_.value).sum.toInt
}

object DomesticAirportFeature {
  val internationalMaxCapacity = 130
}

sealed case class DomesticAirportFeature() extends AirportFeature {
  val featureType = AirportFeatureType.DOMESTIC_AIRPORT
  def strength = 0
  override def demandAdjustment(rawDemand: Double, passengerType: PassengerType.Value, airportId: Int, fromAirport: Airport, toAirport: Airport, affinity: Int, distance: Int) : Int = {
    if (fromAirport.countryCode == "CN") { //otherwise demand gets too big...
      0
    } else if (affinity >= 5) { //domestic and EU etc
      (rawDemand / 2.5).toInt
    } else {
       (-1 * rawDemand / 2).toInt
    }
  }
}

sealed case class BushHubFeature() extends AirportFeature {
  val featureType = AirportFeatureType.BUSH_HUB
  def strength = 0
  override def demandAdjustment(rawDemand: Double, passengerType: PassengerType.Value, airportId: Int, fromAirport: Airport, toAirport: Airport, affinity: Int, distance: Int) : Int = {
    0
  }
}

sealed case class GatewayAirportFeature() extends AirportFeature {
  val featureType = AirportFeatureType.GATEWAY_AIRPORT
  def strength = 0
  override def demandAdjustment(rawDemand: Double, passengerType: PassengerType.Value, airportId: Int, fromAirport: Airport, toAirport: Airport, affinity: Int, distance: Int) : Int = {
    if (fromAirport.hasFeature(AirportFeatureType.GATEWAY_AIRPORT) && toAirport.hasFeature(AirportFeatureType.GATEWAY_AIRPORT) ) { //extra demand if both airports are gateway
      val base = (fromAirport.popMiddleIncome + toAirport.popMiddleIncome) / 50_000
      if (base >= 1) {
        val distanceMultiplier = {
          if (distance <= 1250) {
            0.8
          } else if (distance <= 2500) {
            0.6
          } else if (distance <= 5000) {
            0.4
          } else {
            0.1
          }
        }
        val affinityMultiplier = (affinity.toDouble + 1.0) / 4.0 + 0.5
        (Math.log(base) * distanceMultiplier * affinityMultiplier).toInt
      } else {
        0
      }
    } else {
      0
    }
  }
}

object IsolatedTownFeature {
  val HUB_RANGE_BRACKETS = Array(1000, 1100, 1200, 1300, 1400, 1600, 2000, 3000, 4000, 5000)
}

sealed case class IsolatedTownFeature(strength : Int) extends AirportFeature {
  val featureType = AirportFeatureType.ISOLATED_TOWN
  val boostRange =
    if (strength <= HUB_RANGE_BRACKETS.length) {
      HUB_RANGE_BRACKETS(strength - 1)
    } else {
      HUB_RANGE_BRACKETS.last
    }

  import IsolatedTownFeature._
  override def demandAdjustment(rawDemand: Double, passengerType: PassengerType.Value, airportId: Int, fromAirport: Airport, toAirport: Airport, affinity: Int, distance: Int) : Int = {
    if ((passengerType == PassengerType.TRAVELER || passengerType == PassengerType.TRAVELER_SMALL_TOWN) && fromAirport.hasFeature(AirportFeatureType.ISOLATED_TOWN) && affinity >= 2) {
      val affinityMod = affinity / 5.0
      val distanceMod = 1.0 - distance / boostRange.toDouble
      val rng: Int = 4
//      val rng: Int = 4 + ThreadLocalRandom.current().nextInt(10)

      // Most isolated demand is created in the base getHubAirports() function
      if (toAirport.isGateway() && fromAirport.zone.contains("CC") && distance <= boostRange) {
        (rawDemand * affinityMod * distanceMod).toInt //Increase Caribbean demand
      } else if (toAirport.isGateway() && fromAirport.countryCode == toAirport.countryCode && List("GB", "ES", "NL", "FR", "DK", "GR", "JP", "ID", "PH", "MH", "PG", "RU").contains(fromAirport.countryCode)) {
        rng //add demand from territories or islands back to Metropol
      } else if ((toAirport.hasFeature(AirportFeatureType.BUSH_HUB) || fromAirport.hasFeature(AirportFeatureType.BUSH_HUB)) && distance <= boostRange * 2 && affinity >= 4) {
        (rawDemand * distanceMod * affinityMod * 1.5).toInt //Create bush hub demand
      } else if (affinity >= 3 && rawDemand >= 1 && toAirport.size >= 4 && distance <= boostRange) {
        (rawDemand * rng * affinityMod * distanceMod).toInt
      } else {
        0
      }
    } else {
      0
    }
  }
}

sealed case class PrestigeFeature(baseStrength : Int, boosts : List[AirportBoost] = List.empty) extends AirportFeature {
  val featureType = AirportFeatureType.PRESTIGE_CHARM

  override def demandAdjustment(rawDemand: Double, passengerType: PassengerType.Value, airportId: Int, fromAirport: Airport, toAirport: Airport, affinity: Int, distance: Int) : Int = {
    if (fromAirport.hasFeature(AirportFeatureType.PRESTIGE_CHARM) && toAirport.hasFeature(AirportFeatureType.PRESTIGE_CHARM) ) { //extra demand if both airports are gateway
      val distanceMultiplier = {
        if (distance <= 1250) {
          0.2
        } else if (distance <= 2500) {
          0.4
        } else if (distance <= 5000) {
          0.6
        } else {
          0.8
        }
      }
      val affinityMultiplier = (affinity.toDouble + 1.0) / 4.0 + 0.5
      (Math.log(strength) * distanceMultiplier * affinityMultiplier).toInt
    } else {
      0
    }
  }

  override lazy val strength = baseStrength + boosts.filter(_.boostType == AirportBoostType.PRESTIGE_CHARM).map(_.value).sum.toInt
}

sealed case class OlympicsPreparationsFeature(strength : Int) extends AirportFeature {
  val featureType = AirportFeatureType.OLYMPICS_PREPARATIONS
  override val isDynamic = true
  override def demandAdjustment(rawDemand: Double, passengerType: PassengerType.Value, airportId: Int, fromAirport: Airport, toAirport: Airport, affinity: Int, distance: Int) : Int = {
    0
  }
}

sealed case class OlympicsInProgressFeature(strength : Int) extends AirportFeature {
  val featureType = AirportFeatureType.OLYMPICS_IN_PROGRESS
  override val isDynamic = true
  override def demandAdjustment(rawDemand: Double, passengerType: PassengerType.Value, airportId: Int, fromAirport: Airport, toAirport: Airport, affinity: Int, distance: Int) : Int = {
    0
  }
}


object AirportFeatureType extends Enumeration {
    type AirportFeatureType = Value
    val INTERNATIONAL_HUB, VACATION_HUB, FINANCIAL_HUB, ELITE_CHARM, DOMESTIC_AIRPORT, ISOLATED_TOWN, BUSH_HUB, GATEWAY_AIRPORT, OLYMPICS_PREPARATIONS, OLYMPICS_IN_PROGRESS, PRESTIGE_CHARM, UNKNOWN = Value
}