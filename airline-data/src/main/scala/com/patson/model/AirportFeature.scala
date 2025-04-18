package com.patson.model

import com.patson.model.airplane.Model
import com.patson.model.airplane.Model.Type
import com.patson.DemandGenerator
import com.patson.data.{CycleSource, GameConstants}
import com.patson.model.AirportFeatureType.{AirportFeatureType, DOMESTIC_AIRPORT, ELITE_CHARM, FINANCIAL_HUB, GATEWAY_AIRPORT, INTERNATIONAL_HUB, ISOLATED_TOWN, OLYMPICS_IN_PROGRESS, OLYMPICS_PREPARATIONS, UNKNOWN, VACATION_HUB}
import com.patson.model.IsolatedTownFeature.HUB_RANGE_BRACKETS

abstract class AirportFeature {
  val MAX_STRENGTH = 100
  def strength : Int
  //def airportId : Int
  def featureType : AirportFeatureType.Value
  val strengthFactor : Double = strength.toDouble / MAX_STRENGTH

  def demandAdjustment(rawDemand : Double, passengerType : PassengerType.Value, airportId : Int, fromAirport : Airport, toAirport : Airport, affinity : Int, distance : Int) : Int

  lazy val getDescription = {
    featureType match {
      case INTERNATIONAL_HUB => "Global Vacation Destination - Tourists travel here from everywhere."
      case ELITE_CHARM => "Elite Destination – Elites travel here. "
      case VACATION_HUB => "Local Vacation Destination - Domestic & high-affinity tourists travel here."
      case FINANCIAL_HUB => "Business Hub - Center for business passengers."
      case DOMESTIC_AIRPORT => "Domestic Discount Airport – Lower base upkeep. If flight is international, only accepts small aircraft."
      case ISOLATED_TOWN => s"Isolated - Increased demand within ${this.asInstanceOf[IsolatedTownFeature].boostRange}km."
      case GATEWAY_AIRPORT => "Gateway - Has increased demand with other gateway airports."
      case OLYMPICS_PREPARATIONS => "Preparing the Olympic Games."
      case OLYMPICS_IN_PROGRESS => "Year of the Olympic Games."
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
      case OLYMPICS_PREPARATIONS => OlympicsPreparationsFeature(strength)
      case OLYMPICS_IN_PROGRESS => OlympicsInProgressFeature(strength)
    }
  }
}

sealed case class InternationalHubFeature(baseStrength : Int, boosts : List[AirportBoost] = List.empty) extends AirportFeature {
  val featureType = AirportFeatureType.INTERNATIONAL_HUB
  override def demandAdjustment(rawDemand : Double, passengerType : PassengerType.Value, airportId : Int, fromAirport : Airport, toAirport : Airport, affinity : Int, distance : Int) : Int = {
    if (airportId == toAirport.id  && passengerType == PassengerType.TOURIST) { //only affect if as a destination
      val charmStrength = 0.00041 * strengthFactor
      val incomeModifier = Math.max(fromAirport.baseIncome.toDouble / (Airport.HIGH_INCOME * 0.6), 0.2)
      val distanceModifier = if (distance < 500) {
        distance.toDouble / 500
      } else if (distance < 5500) {
        1.0
      } else {
        5500 / distance.toDouble
      }
      val airportAffinityMutliplier: Double =
        if (affinity >= 5) (affinity - 5) * 0.1 + 1 //domestic+
        else if (affinity == 4) 0.75
        else if (affinity == 3) 0.55
        else if (affinity == 2) 0.35
        else if (affinity == 1) 0.25
        else if (affinity == 0) 0.1
        else 0.05
      val specialCountryModifier =
        if (List("AU","NZ","AE","SG").contains(fromAirport.countryCode)) {
          2.0 //they travel a lot; difficult to model
        } else 1.0

      (DemandGenerator.launchDemandFactor * fromAirport.popMiddleIncome * charmStrength * distanceModifier * airportAffinityMutliplier * incomeModifier * specialCountryModifier).toInt
    } else {
      0
    }
  }

  override lazy val strength = baseStrength + boosts.filter(_.boostType == AirportBoostType.INTERNATIONAL_HUB).map(_.value).sum.toInt
}

sealed case class EliteFeature(baseStrength : Int, boosts : List[AirportBoost] = List.empty) extends AirportFeature {
  val featureType = AirportFeatureType.ELITE_CHARM

  override def demandAdjustment(rawDemand : Double, passengerType : PassengerType.Value, airportId : Int, fromAirport : Airport, toAirport : Airport, affinity : Int, distance : Int) : Int = {
    0
  }

  override lazy val strength = baseStrength + boosts.filter(_.boostType == AirportBoostType.ELITE_CHARM).map(_.value).sum.toInt
}

/**
 * applies strongly to domestic / high affinity matches
 */
sealed case class VacationHubFeature(baseStrength : Int, boosts : List[AirportBoost] = List.empty) extends AirportFeature {
  val featureType = AirportFeatureType.VACATION_HUB

  override def demandAdjustment(rawDemand : Double, passengerType : PassengerType.Value, airportId : Int, fromAirport : Airport, toAirport : Airport, affinity : Int, distance : Int) : Int = {
    if (toAirport.id == airportId && passengerType == PassengerType.TOURIST) { //only affect if as a destination and tourists
      /**
       * based off Disney World, which has 13.6m domestic visitors or 4% of USA population, so at 100 strength charm 4% want to visit or a 1 charm 0.04%
       * (strengthFactor is already a percent)
       */
      val charmStrength = 0.00045 * strengthFactor
      val distanceModifier = if (distance < 400) {
        (distance - 25).toDouble / 400
      } else if (distance < 2200) {
        1.02
      } else {
        Math.max(2200 / distance.toDouble, 0.2)
      }
      val airportAffinityMutliplier: Double =
        if (affinity >= 5) (affinity - 5) * 0.05 + 1.01 //each increases 5%
        else if (affinity == 4) 0.5
        else if (affinity == 3) 0.3
        else if (affinity == 2) 0.1
        else if (affinity == 1) 0.05
        else 0

      val launchDemandFactor = 0.5 + 0.5 * DemandGenerator.launchDemandFactor
      (launchDemandFactor * fromAirport.popMiddleIncome * charmStrength * distanceModifier * airportAffinityMutliplier).toInt
    } else {
      0
    }
  }

  override lazy val strength = baseStrength + boosts.filter(_.boostType == AirportBoostType.VACATION_HUB).map(_.value).sum.toInt
}

sealed case class FinancialHubFeature(baseStrength : Int, boosts : List[AirportBoost] = List.empty) extends AirportFeature {
  val featureType = AirportFeatureType.FINANCIAL_HUB
  override def demandAdjustment(rawDemand : Double, passengerType : PassengerType.Value, airportId : Int, fromAirport : Airport, toAirport : Airport, affinity : Int, distance : Int) : Int = {
    if (passengerType == PassengerType.BUSINESS) {
      val hasFeatureInBothAirports = fromAirport.hasFeature(AirportFeatureType.FINANCIAL_HUB) && toAirport.hasFeature(AirportFeatureType.FINANCIAL_HUB)
      val doubleBonus = if (hasFeatureInBothAirports) 0.75 else 1.25
      val charmStrength =
        if (hasFeatureInBothAirports) {
          0.00026 * strengthFactor
        } else if (toAirport.id == airportId) { //going to business center
          0.00015 * strengthFactor * Math.min(1.5, toAirport.population.toDouble / fromAirport.population)
        } else {
          0.00004 * strengthFactor * Math.min(1.5, toAirport.population.toDouble / fromAirport.population)
        }
      val distanceReducerExponent: Double =
        if (hasFeatureInBothAirports && distance < 500) {
          1.2
        } else if (distance < 500) {
          distance.toDouble / 500
        } else if (distance > 4000) {
          0.975 - distance.toDouble / 40000 * doubleBonus
        } else if (distance > 1000) {
          1.05 - distance.toDouble / 10000
        } else {
          1
        }
      val airportAffinityMutliplier: Double =
        if (affinity >= 5) (affinity - 5) * 0.1 + 1 //domestic+
        else if (affinity == 4) 0.6
        else if (affinity == 3) 0.5
        else if (affinity == 2) 0.4
        else if (affinity == 1) 0.3
        else if (affinity == 0) 0.2
        else 0.1

      Math.max(0, Math.pow(DemandGenerator.launchDemandFactor * fromAirport.popMiddleIncome * charmStrength * airportAffinityMutliplier, distanceReducerExponent).toInt)
    } else {
      0
    }
  }
  override lazy val strength = baseStrength + boosts.filter(_.boostType == AirportBoostType.FINANCIAL_HUB).map(_.value).sum.toInt
}

sealed case class DomesticAirportFeature() extends AirportFeature {
  val featureType = AirportFeatureType.DOMESTIC_AIRPORT
  def strength = 0
  override def demandAdjustment(rawDemand : Double, passengerType : PassengerType.Value, airportId : Int, fromAirport : Airport, toAirport : Airport, affinity : Int, distance : Int) : Int = {
    if (fromAirport.countryCode == "CN") { //otherwise demand gets too big...
      0
    } else if (affinity >= 5) { //domestic
      (DemandGenerator.launchDemandFactor * rawDemand / 2.5).toInt
    } else {
       (-1 * rawDemand / 2).toInt
    }
  }
}

sealed case class GatewayAirportFeature() extends AirportFeature {
  val featureType = AirportFeatureType.GATEWAY_AIRPORT
  def strength = 0
  override def demandAdjustment(rawDemand : Double, passengerType : PassengerType.Value, airportId : Int, fromAirport : Airport, toAirport : Airport, affinity : Int, distance : Int) : Int = {
    if (airportId != fromAirport.id) {
      0
    } else {
      if (fromAirport.hasFeature(AirportFeatureType.GATEWAY_AIRPORT) && toAirport.hasFeature(AirportFeatureType.GATEWAY_AIRPORT) ) { //extra demand if both airports are gateway
        val base = (fromAirport.power + toAirport.power) / 25000 //todo change to popMiddleIncome
        if (base >= 1) {
          val distanceMultiplier = {
            if (distance <= 1250) {
              2.6
            } else if (distance <= 2500) {
              2.2
            } else if (distance <= 5000) {
              1.5
            } else {
              0.25
            }
          }
          val affinityMultiplier = (affinity.toDouble + 1.0) / 4.0 + 0.5
          (Math.log(base) * distanceMultiplier * affinityMultiplier).toInt
        } else {
          0
        }
      } else if (fromAirport.countryCode == toAirport.countryCode && fromAirport.baseIncome <= 25000 && toAirport.hasFeature(AirportFeatureType.GATEWAY_AIRPORT) && ! List("CN","IN","ZA","US").contains(fromAirport.countryCode)) {
        Math.min(200, (rawDemand * 0.2).toInt) //add domestic demand to gateways, but not to rich / high-demand countries
      } else {
        0
      }
    }
  }
}

object IsolatedTownFeature {
  val HUB_RANGE_BRACKETS = Array(800, 1200, 1600, 2200, 3000, 4000)
}

sealed case class IsolatedTownFeature(strength : Int) extends AirportFeature {
  val featureType = AirportFeatureType.ISOLATED_TOWN
  val boostRange =
    if (strength < HUB_RANGE_BRACKETS.size) {
      HUB_RANGE_BRACKETS(strength) //pulling second entry
    } else {
      HUB_RANGE_BRACKETS.last
    }

  import IsolatedTownFeature._
  override def demandAdjustment(rawDemand : Double, passengerType : PassengerType.Value, airportId : Int, fromAirport : Airport, toAirport : Airport, affinity : Int, distance : Int) : Int = {
    if (passengerType == PassengerType.TRAVELER && fromAirport.hasFeature(AirportFeatureType.ISOLATED_TOWN) && affinity >= 2) {
      val affinityMod = if (affinity >= 5) 1.0 else affinity.toDouble / 10
      val tempAdjustment = if (fromAirport.countryCode == "CA") 2.7 else 1 //todo: will degrade & remove this

      val mod = if (distance <= boostRange) {
        val basis = 150.0 + DemandGenerator.demandRandomizer * 5
        val distanceModifier = if (distance >= 1500 && toAirport.size <= 3) {
          0
        } else if (distance >= boostRange) {
           boostRange / distance
        } else {
          1.0
        }

        if (fromAirport.population <= basis || toAirport.population <= basis) {
          2
        } else if (fromAirport.population <= basis * 50 || toAirport.population <= basis * 50) {
          val fromAdjusted = 1 + Math.min(basis - 1, (fromAirport.population.toDouble - basis) / basis)
          val toAdjusted = 1 + Math.min(basis - 1, (toAirport.population.toDouble - basis) / basis)
          Math.min(fromAdjusted, toAdjusted) * distanceModifier
        } else if (fromAirport.population <= basis * 400 || toAirport.population <= basis * 400) {
          val fromAdjusted = 50 + Math.min(basis, (fromAirport.population.toDouble - basis * 50) / (basis * 7))
          val toAdjusted = 50 + Math.min(basis, (toAirport.population.toDouble - basis * 50) / (basis * 8))
          Math.min(fromAdjusted, toAdjusted) * distanceModifier
        } else {
          basis * distanceModifier
        }
      } else if (rawDemand >= 20 && toAirport.isGateway()) {
        rawDemand * 0.05
      } else if (toAirport.isGateway() && fromAirport.population >= 20000) {
        5
      } else {
        0
      }
      (affinityMod * mod * tempAdjustment).toInt
    } else {
      0
    }
  }
}

sealed case class OlympicsPreparationsFeature(strength : Int) extends AirportFeature {
  val featureType = AirportFeatureType.OLYMPICS_PREPARATIONS
  override def demandAdjustment(rawDemand : Double, passengerType : PassengerType.Value, airportId : Int, fromAirport : Airport, toAirport : Airport, affinity : Int, distance : Int) : Int = {
    0
  }
}

sealed case class OlympicsInProgressFeature(strength : Int) extends AirportFeature {
  val featureType = AirportFeatureType.OLYMPICS_IN_PROGRESS
  override def demandAdjustment(rawDemand : Double, passengerType : PassengerType.Value, airportId : Int, fromAirport : Airport, toAirport : Airport, affinity : Int, distance : Int) : Int = {
    0
  }
}


object AirportFeatureType extends Enumeration {
    type AirportFeatureType = Value
    val INTERNATIONAL_HUB, VACATION_HUB, FINANCIAL_HUB, ELITE_CHARM, DOMESTIC_AIRPORT, ISOLATED_TOWN, GATEWAY_AIRPORT, OLYMPICS_PREPARATIONS, OLYMPICS_IN_PROGRESS, UNKNOWN = Value
}