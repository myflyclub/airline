package com.patson.model

import com.patson.model.airplane._
import com.patson.data.{AirlineSource, AirplaneSource, AirportSource, AllianceSource, BankSource, CountrySource, CycleSource, OilSource}
import com.patson.Util
import com.patson.util.{AirlineCache, AirportCache, AllianceRankingUtil}

import java.util.concurrent.ConcurrentHashMap
import scala.collection.mutable.ListBuffer
import scala.collection.mutable


object Computation {
  val MODEL_COUNTRY_CODE = "US"
  lazy val MODEL_COUNTRY_POWER : Double = CountrySource.loadCountryByCode(MODEL_COUNTRY_CODE) match {
    case Some(country) =>
      country.airportPopulation.toDouble * country.income
    case None =>
      println(s"Cannot find $MODEL_COUNTRY_CODE to compute model country_power")
      1
  }

  lazy val MAX_VALUES = getMaxValues()
  lazy val MAX_POPULATION = MAX_VALUES._1
  lazy val MAX_INCOME = MAX_VALUES._2

  val MAX_COMPUTED_DISTANCE = 20000
  lazy val standardFlightDurationCache : Array[Int] = {
    val result = new Array[Int](MAX_COMPUTED_DISTANCE + 1)
    for (i <- 0 to MAX_COMPUTED_DISTANCE) { //should cover everything...
      result(i) =  Computation.internalComputeStandardFlightDuration(i)
    }
    result
  }

  def getMaxValues(): (Long, Int) = {
    val allAirports = AirportCache.getAllAirports()
    //take note that below should NOT use boosted values, should use base, otherwise it will incorrectly load some lazy vals of the Airport that is MAX
    (allAirports.maxBy(_.basePopulation).basePopulation, allAirports.maxBy(_.baseIncome).baseIncome)
  }

  def calculateDuration(airplaneModel: Model, distance : Int) : Int = {
    val timeToCruise: Int = airplaneModel.airplaneType match {
      case Model.Type.PROPELLER_SMALL => Model.TIME_TO_CRUISE_PROPELLER_SMALL
      case Model.Type.PROPELLER_MEDIUM => Model.TIME_TO_CRUISE_PROPELLER_MEDIUM
      case Model.Type.SMALL => Model.TIME_TO_CRUISE_SMALL
      case Model.Type.HELICOPTER | Model.Type.AIRSHIP => Model.TIME_TO_CRUISE_HELICOPTER
      case Model.Type.SUPERSONIC => Model.TIME_TO_CRUISE_OTHER
      case _ => Math.sqrt(airplaneModel.capacity).toInt + 10
    }
    val cruiseTime = distance.toDouble * 60 / airplaneModel.speed
    (timeToCruise + cruiseTime).toInt
  }

  def calculateFlightMinutesRequired(airplaneModel : Model, distance : Int) : Int = {
    val duration = calculateDuration(airplaneModel, distance)
    val roundTripTime = (duration + airplaneModel.turnaroundTime) * 2
    roundTripTime
  }

  def calculateMaxFrequency(airplaneModel : Model, distance : Int) : Int = {
    if (airplaneModel.range < distance) {
      0
    } else {
      val roundTripTime = calculateFlightMinutesRequired(airplaneModel, distance)
      (Airplane.MAX_FLIGHT_MINUTES / roundTripTime).toInt
    }
  }
  

  val SELL_RATE = 0.8

  def calculateAirplaneSellValue(airplane : Airplane) : Int = {
    val salvageValue = airplane.purchasePrice * Airplane.SALVAGE_VALUE_PERCENT
    val maxDepreciableValue = (airplane.purchasePrice * SELL_RATE) - salvageValue
    val conditionRatio = airplane.condition / Airplane.MAX_CONDITION

    val value = salvageValue + (maxDepreciableValue * conditionRatio)
    value.toInt
  }

  def calculateDealerValue(airplane : Airplane) : Int = {
    val value = (airplane.condition / Airplane.MAX_CONDITION) * airplane.purchasePrice
    if (value < 0) 0 else value.toInt
  }
  
  val distanceCache = new ConcurrentHashMap[String, Int]()

  def calculateDistance(fromAirport: Airport, toAirport: Airport) : Int = {
    val key = s"${fromAirport.id}${toAirport.id}"
    distanceCache.computeIfAbsent(key, _ => Util.calculateDistance(fromAirport.latitude, fromAirport.longitude, toAirport.latitude, toAirport.longitude).toInt)
  }

  // is used independent of individual links, so must be globally accessible
  def getFlightCategory(fromAirport : Airport, toAirport : Airport): FlightCategory.Value = {
    //hard-coding home markets into the computation function to allow for independent "relation" values
    val ECAA = List("AL", "AM", "AT", "BA", "BE", "BG", "CH", "CY", "CZ", "DK", "EE", "FI", "FR", "DE", "GE", "GR", "HR", "HU", "IE", "IS", "IT", "LT", "LU", "LV", "MC", "MD", "ME", "MK", "MT", "NL", "NO", "PL", "PT", "RO", "RS", "SI", "SK", "ES", "SE", "UA", "XK") //https://en.wikipedia.org/wiki/European_Common_Aviation_Area
    val USA = List("US", "PR", "VI", "GU", "AS", "MP", "MH", "PW", "FM") //US & COFA Pacific
    val GB = List("GB", "TC", "KY", "VG", "BM")
    val ANZAC = List("AU", "NZ", "CK", "NU")
    val CN = List("CN", "MO", "HK")
    if (fromAirport.countryCode == toAirport.countryCode ||
      ECAA.contains(fromAirport.countryCode) && ECAA.contains(toAirport.countryCode) ||
      USA.contains(fromAirport.countryCode) && USA.contains(toAirport.countryCode) ||
      GB.contains(fromAirport.countryCode) && GB.contains(toAirport.countryCode) ||
      ANZAC.contains(fromAirport.countryCode) && ANZAC.contains(toAirport.countryCode) ||
      CN.contains(fromAirport.countryCode) && CN.contains(toAirport.countryCode)
    ){
      FlightCategory.DOMESTIC
    } else {
      FlightCategory.INTERNATIONAL
    }
  }

  /**
   * Matches shared strings, deliminated by a dash "-"
   * international only, any that start with a "|"
   * diaspora, discards strings with |first-code|second-code| pattern unless toCountryCode & fromCountryCode are also matched (only different for text, not value)
   *
   * @param fromZone
   * @param toZone
   * @param toCountryCode
   * @param relationship
   * @return
   */

def calculateAffinityValue(fromZone : String, toZone : String, relationship : Int) : Int = {
  val relationshipModifier =
    if (relationship >= 5) { //domestic
      5
    } else if (relationship < 0) {
      Math.floor(relationship.toDouble / 2.0).toInt
    } else if (relationship > 0) {
      (relationship / 2).toInt
    } else {
      0
    }

  val affinitySet = affinityToSet(fromZone : String, toZone : String, relationship : Int)
  val baseAffinity = affinitySet.length + relationshipModifier
  if (baseAffinity <= 1 && affinityCountX2(affinitySet) > 0) {
    affinitySet.length + affinityCountX2(affinitySet) + 1 //ensure x2 affinities always hit strong
  } else {
    baseAffinity + affinityCountX2(affinitySet)
  }
}

def affinityToSet(fromZone : String, toZone : String, relationship : Int) = {
  val set1 = if (relationship >= 5) {
    fromZone.split("-").filterNot(_.endsWith("|")).filterNot(_.startsWith("|"))
  } else {
    fromZone.split("-").filter(_ != "None|")
  }
  val set2 = if (relationship >= 5) {
    toZone.split("-").filterNot(_.endsWith("|")).filterNot(_.startsWith("|"))
  } else {
    toZone.split("-").filter(_ != "None|")
  }
  set1.intersect(set2)
}

def affinityCountX2(strings: Array[String]): Int = {
  strings.count { str =>
    if (str.endsWith("|")) {
      str.length >= 5 && str.substring(str.length - 3, str.length - 1) == "x2"
    } else {
      str.length >= 5 && str.substring(str.length - 2, str.length) == "x2"
    }
  }
}

def constructAffinityText(fromZone : String, toZone : String, fromCountry : String, toCountry : String, relationship : Int, affinity : Int) : String = {
  val set1 = if (relationship >= 5) {
    fromZone.split("-").filterNot(_.endsWith("|")).filterNot(_.startsWith("|"))
  } else {
    fromZone.split("-").filter(_ != "None|")
  }
  val set2 = if (relationship >= 5) {
    toZone.split("-").filterNot(_.endsWith("|")).filterNot(_.startsWith("|"))
  } else {
    toZone.split("-").filter(_ != "None|")
  }
  var matchingItems = set1.intersect(set2).toArray

  if (relationship <= 5) {
    matchingItems = matchingItems.map(item => {
      if (item.endsWith("|")) item.dropRight(1)
      else if (item.startsWith("|")) item.drop(4) + " diaspora"
      else item
    })
  }

  if (relationship <= -1) {
    matchingItems = Array("Political Acrimony") ++ matchingItems
  } else if (relationship >= 5) {
    matchingItems = Array("Domestic") ++ matchingItems
  } else if (relationship >= 4) {
    matchingItems = Array("Excellent Relations") ++ matchingItems
  } else if (relationship >= 2) {
    matchingItems = Array("Good Relations") ++ matchingItems
  }

  val introText = if (affinity == 0 && matchingItems.length == 0){
    "Neutral"
  } else if (affinity == 0){
    "Neutral:"
  } else if (affinity > 0){
    s"+${affinity}:"
  } else {
    s"${affinity}:"
  }

  s"${introText} ${matchingItems.mkString(", ")}"
}

  import org.apache.commons.math3.distribution.{LogNormalDistribution, NormalDistribution}

  def populationAboveThreshold(meanIncome: Double, population: Int, gini: Double, threshold: Int): Int = {
    //larger urban areas have much more inequality
    val urbanInequalityModifier = if (population > 16000000) {
      1.6
    } else if (population > 8000000){
      1.5
    } else if (population > 4000000) {
      1.4
    } else if (population > 2000000){
      1.3
    } else if (population > 1000000){
      1.1
    } else if (population > 100000){
      0.9
    } else {
      0.7
    }

    val meanLog = Math.log(meanIncome)
    val sdLog = gini / 100 * urbanInequalityModifier
    val logDistribution = new LogNormalDistribution(meanLog, sdLog)

    val probLognormal = 1.0 - logDistribution.cumulativeProbability(threshold)

    Math.ceil(population * probLognormal).toInt
  }

  def getLinkCreationCost(from: Airport, to: Airport, airplaneGateSize: Double) : Int = {
    val baseCost = 50000 + (from.rating.overallDifficulty * to.rating.overallDifficulty * 1000)

    val gateMultiplier = airplaneGateSize * 6
    val distance = calculateDistance(from, to)
    val distanceMultiplier = Math.max(0.2, distance.toDouble / 5000)
    val internationalMultiplier = if (from.countryCode == to.countryCode) 1 else 1.5

    (baseCost * distanceMultiplier * internationalMultiplier * gateMultiplier).toInt
  }
  
  def getResetAmount(airlineId : Int) : ResetAmountInfo = {
    val currentCycle = CycleSource.loadCycle()
    val amountFromAirplanes = AirplaneSource.loadAirplanesByOwner(airlineId, false).map(Computation.calculateAirplaneSellValue(_).toLong).sum
    val amountFromBases = AirlineSource.loadAirlineBasesByAirline(airlineId).map(_.getValue * 0.2).sum.toLong //only get 20% back
    val amountFromLoans = BankSource.loadLoansByAirline(airlineId).map(_.earlyRepayment(currentCycle) * -1).sum //repay all loans now
//    val amountFromOilContracts = OilSource.loadOilContractsByAirline(airlineId).map(_.contractTerminationPenalty(currentCycle) * -1).sum //termination penalty
    val amountFromOilContracts = 0 //removing because not interesting and burdens DB
    val existingBalance = AirlineCache.getAirline(airlineId).get.airlineInfo.balance
    
    ResetAmountInfo(amountFromAirplanes, amountFromBases, amountFromLoans, amountFromOilContracts, existingBalance)
  }
  
  case class ResetAmountInfo(airplanes : Long, bases : Long, loans : Long, oilContracts : Long, existingBalance : Long) {
    val overall = airplanes + bases + loans + oilContracts + existingBalance
  }

  val LINK_COST_TOLERANCE_FACTOR = 1.0
  val SATISFACTION_MAX_PRICE_RATIO_THRESHOLD: Double = 0.7 //at 100% satisfaction is <= this threshold
  private val SATISFACTION_MIN_PRICE_RATIO_THRESHOLD = LINK_COST_TOLERANCE_FACTOR //0% satisfaction >= this threshold

  /**
   * Satisfaction is calculated after seats are booked – delays and LF don't directly impact whether passengers book.
   *
   * @param cost perceived price
   * @param standardPrice standard link price (for given distance)
   * @param crowdedPercent 0-1 load factor where 0.8 is neutral, 0.0 gives +25% bonus, 1.0 gives -25% penalty
   * @param onTimeRatio 0-1 on-time ratio from getDelayRatio, where 1 is all on-time (best), 0 is all cancelled (worst)
   * @return 0 (not satisfied at all) to 1 (fully satisfied)
   */
  def computePassengerSatisfaction(cost: Double, standardPrice: Int, crowdedPercent: Double, onTimeRatio: Double): Double = {
    val ratio = cost / standardPrice
    val satisfaction = (SATISFACTION_MIN_PRICE_RATIO_THRESHOLD - ratio) / (SATISFACTION_MIN_PRICE_RATIO_THRESHOLD - SATISFACTION_MAX_PRICE_RATIO_THRESHOLD)
    // onTimeRatio: 1.0 = all on-time (25% bonus), 0.0 = all cancelled (0 satisfaction)
    // crowdedPercent: 0.8 = neutral, 0.0 = +25% bonus, 1.0 = -25% penalty
    val crowdModifier = if (crowdedPercent <= 0.8) {
      1.0 + (0.8 - crowdedPercent) * 0.125  // bonus for low LF (0.8 * 0.125 = 0.1 at LF=0)
    } else {
      1.0 - (crowdedPercent - 0.8) * 1.15    // penalty for high LF (0.2 * 1.25 = 0.1 at LF=1)
    }
    Math.min(1.0, Math.max(0.0, satisfaction * (1.3 * onTimeRatio) * crowdModifier))
  }
  val TOOLTIP_SATISFACTION = List(
    s"If a passenger has more than ${SATISFACTION_MAX_PRICE_RATIO_THRESHOLD * 100}% satisfaction they may become your loyalist.",
    "Satisfaction is the ratio of the passenger's perceived price compared to the expected price for that distance.",
    "Additionally load factor and delays modify satisfaction (passengers like empty seats and no delays).",
    "When a passenger traverses multiple links in a journey, the longer links weight loyalist conversion proportionally more than the shorter links."
  )

  val computeStandardFlightDuration: Int => Int = (distance: Int) => {
    if (distance <= MAX_COMPUTED_DISTANCE) {
      standardFlightDurationCache(distance)
    } else {
      println(s"Unexpected distance $distance")
      internalComputeStandardFlightDuration(distance) //just in case
    }
  }

  private def internalComputeStandardFlightDuration(distance : Int) = {
    val min = Model.TIME_TO_CRUISE_SMALL //buff props by setting this to small jets
    val max = Model.TIME_TO_CRUISE_OTHER
    val expectedTimeToCruise = Math.min(max - min, (max - min) * distance / 800) //
    val expectedSpeed = 200 + Math.min(600, 600 * distance / 1200)

    (expectedTimeToCruise + distance.toDouble * 60 / expectedSpeed).toInt
  }

  def getAirportWithinRange(principalAirport: Airport, range: Int, minRange: Int = 0, isDomestic: Boolean = true): List[Airport] = { //range in km
    val affectedAirports = ListBuffer[Airport]()
    val allAirports = if (isDomestic) AirportCache.getAllAirports().filter(_.countryCode == principalAirport.countryCode) else AirportCache.getAllAirports()
    allAirports.foreach { airport =>
      val distance = Computation.calculateDistance(principalAirport, airport)
      if (distance <= range && distance >= minRange) {
        affectedAirports.append(airport)
      }
    }
    affectedAirports.toList
  }

  def cycleToGameDate(cycle: Int): String = {
    s"${cycle % 48}.${cycle / 48}"
  }
}