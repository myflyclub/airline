package com.patson

import java.util.{ArrayList, Collections}
import com.patson.data.{AirportSource, CountrySource, CycleSource, DestinationSource, EventSource, GameConstants}
import com.patson.model.event.{EventType, Olympics}
import com.patson.model.{PassengerType, _}
import com.patson.util.AirportCache

import java.util.concurrent.ThreadLocalRandom
import scala.collection.{immutable, mutable}
import scala.collection.mutable.ListBuffer
import scala.collection.parallel.CollectionConverters._

object DemandGenerator {

  val FIRST_CLASS_INCOME_MAX = 125_000
  val FIRST_CLASS_PERCENTAGE_MAX: Map[PassengerType.Value, Double] = Map(PassengerType.TRAVELER -> 0, PassengerType.BUSINESS -> 0.12, PassengerType.TOURIST -> 0, PassengerType.ELITE -> 1, PassengerType.OLYMPICS -> 0)
  val BUSINESS_CLASS_INCOME_MAX = 125_000
  val BUSINESS_CLASS_PERCENTAGE_MAX: Map[PassengerType.Value, Double] = Map(PassengerType.TRAVELER -> 0.16, PassengerType.BUSINESS -> 0.49, PassengerType.TOURIST -> 0.1, PassengerType.ELITE -> 0, PassengerType.OLYMPICS -> 0.25)
  val DISCOUNT_CLASS_PERCENTAGE_MAX: Map[PassengerType.Value, Double] = Map(PassengerType.TRAVELER -> 0.38, PassengerType.BUSINESS -> 0, PassengerType.TOURIST -> 0.62, PassengerType.ELITE -> 0, PassengerType.OLYMPICS -> 0)
  val MIN_DISTANCE = 175 //does not apply to islands
  val HIGH_INCOME_RATIO_FOR_BOOST = 0.7 //at what percent of high income does demand change
  val PRICE_DISCOUNT_PLUS_MULTIPLIER = 1.05 //multiplier on base price
  val PRICE_LAST_MIN_MULTIPLIER = 1.14
  val PRICE_LAST_MIN_DEAL_MULTIPLIER = 0.88
  val HUB_AIRPORTS_MAX_RADIUS = 1400
//  val launchDemandFactor: Double = if (CycleSource.loadCycle() <= 1) 1.0 else Math.min(1, (50 + CycleSource.loadCycle().toDouble / 24) / 100)
  val launchDemandFactor: Double = 1.0
  val baseDemandChunkSize = 23
  val cycle: Int = CycleSource.loadCycle()
  val cyclePhaseLength = CycleSource.loadAndUpdateCyclePhase()
  val baseRandom: Int = 5 + ThreadLocalRandom.current().nextInt(10)

  import scala.jdk.CollectionConverters._

  /**
   * Do these two airports have standard demand between them?
   *
   * @param fromAirport
   * @param toAirport
   * @param distance
   * @return
   */
  def canHaveDemand(fromAirport: Airport, toAirport: Airport, distance: Int): Boolean = {
    fromAirport != toAirport && (distance > MIN_DISTANCE || GameConstants.connectsIsland(fromAirport, toAirport) && distance > 25 || toAirport.hasFeature(AirportFeatureType.BUSH_HUB))
  }

  def demandRandomizerByType(passengerType: PassengerType.Value, demand: Int): Int = {
    val randomizedDemand = if (passengerType == PassengerType.TOURIST) {
      demandRandomizer(demand, cycle, cyclePhaseLength, 3, 24)
    } else if (passengerType == PassengerType.BUSINESS) {
      demandRandomizer(demand, cycle, cyclePhaseLength, 1, 12)
    } else { //traveler, elite
      demandRandomizer(demand, cycle, cyclePhaseLength)
    }
    randomizedDemand
  }

  def demandRandomizer(demand: Int, cycle: Int, frequency: Int, amplitudeRatio: Int = 1, offset: Int = 0): Int = {
    val rng = ThreadLocalRandom.current()
    val sinValue = math.sin(2 * math.Pi * (cycle + offset) / frequency)

    val random = demand * rng.nextDouble(-0.07, 0.07)
    val amplitude = amplitudeRatio * math.max(8, demand * rng.nextDouble(0.11))
    val adjustment = amplitude * sinValue + random
    val newDemand = demand + adjustment.toInt
    if (newDemand <= 0 || (newDemand < 10 && newDemand % 2 == 1)) {
      0 //doing this to reduce the number of groups for the algo to process, subtracting 30
    } else if (newDemand <= 15) {
      newDemand + 3 //but then add 30 back... arbitrary but imperceptible
    } else {
      newDemand
    }
  }

  /**
   * getting the "right" base demand is a complex interplay of charms, pop, income, affinities, etc
   * however do need these locality adjusts or would be much weirder
   * @param fromAirport
   * @param toAirport
   * @param distance
   * @return
   */

  /**
   * min distance is used in two contexts:
   * 1) find nearby domestic between minDistance and 4x minDistance
   *    - standard catchment is 1200km
   * 2) reduce demand under minDistance (if not island or high affinity)
   */
  val localityMinDistanceMap = Map(
      "default" -> 325,
      "CA" -> 400,
      "US" -> 400,
      "DO" -> 100,
      "HT" -> 100,
      "BZ" -> 100,
      "HN" -> 100,
      "CR" -> 100,
      "GT" -> 100,
      "NI" -> 200,
      "HN" -> 200,
      "PA" -> 200,
      "CO" -> 200,
      "PE" -> 250,
      "BR" -> 325,
      "AR" -> 350,
      "ET" -> 210,
      "KE" -> 220,
      "TZ" -> 220,
      "ZW" -> 220,
      "ZA" -> 300,
      "IS" -> 100,
      "GB" -> 400,
      "NL" -> 400,
      "BE" -> 450,
      "DE" -> 350,
      "IT" -> 320,
      "PL" -> 400,
      "NO" -> 300,
      "SE" -> 400,
      "FI" -> 300,
      "RU" -> 375,
      "AE" -> 370,
      "BA" -> 370,
      "QA" -> 370,
      "IQ" -> 50, //turn EBL into an island
      "NP" -> 100,
      "CN" -> 700, //HSR rail
      "TW" -> 220,
      "JP" -> 400,
      "KR" -> 360,
      "AU" -> 360,
    )
    val localityAdjustMap = Map(
      "AO" -> 1.8,
      "BI" -> 2.1,
      "BF" -> 1.9,
      "BJ" -> 2.2,
      "BW" -> 2.1,
      "CD" -> 1.4,
      "CG" -> 1.6,
      "CI" -> 1.6,
      "CM" -> 1.8,
      "CV" -> 2.1,
      "DJ" -> 2.1,
      "EG" -> 0.9,
      "ER" -> 1.9,
      "ET" -> 2.4,
      "GA" -> 2.1,
      "GH" -> 2.1,
      "GM" -> 2.1,
      "GN" -> 1.6,
      "GW" -> 1.6,
      "GQ" -> 2.2,
      "KE" -> 2.1,
      "KM" -> 2.1,
      "LR" -> 1.7,
      "LS" -> 2.4,
      "ML" -> 1.7,
      "MR" -> 1.7,
      "MG" -> 1.6,
      "MU" -> 2.0,
      "MW" -> 2.1,
      "MZ" -> 1.9,
      "NA" -> 2.3,
      "NE" -> 1.7,
      "NG" -> 1.2,
      "SC" -> 2.2,
      "SD" -> 1.7,
      "SL" -> 2.4,
      "SN" -> 2.4,
      "SO" -> 1.5,
      "SS" -> 1.6,
      "ST" -> 2.1,
      "SZ" -> 2.1,
      "TD" -> 1.6,
      "TG" -> 1.6,
      "TZ" -> 1.9,
      "UG" -> 1.7,
      "ZA" -> 2.1,
      "ZM" -> 1.7,
      "ZW" -> 1.6,

      "AR" -> 1.4,
      "BO" -> 2.2,
      "BZ" -> 2.5,
      "BR" -> 1.2,
      "CL" -> 2.8,
      "CO" -> 1.8,
      "CR" -> 2.0,
      "EC" -> 2.4,
      "GY" -> 2.0,
      "HN" -> 2.0,
      "PE" -> 2.7,
      "PA" -> 3.8,
      "PY" -> 2.0,
      "SV" -> 2.0,
      "UY" -> 2.0,

      "AE" -> 1.9,
      "BA" -> 1.8,
      "BD" -> 0.8,
      "CN" -> 0.7,
      "KR" -> 1.7,
      "ID" -> 1.5,
      "IL" -> 1.4,
      "IN" -> 0.5,
      "IR" -> 1.8,
      "LK" -> 1.2,
      "JP" -> 1.2,
      "KW" -> 2.6,
      "MY" -> 1.8,
      "NP" -> 1.7,
      "OM" -> 1.6,
      "PH" -> 1.5,
      "PK" -> 0.7,
      "SA" -> 1.7,
      "SG" -> 1.9,
      "TH" -> 1.0,
      "TR" -> 1.3,
      "TW" -> 1.4,
      "VN" -> 1.7,

      "AU" -> 1.3,
      "NZ" -> 1.9,

      "AL" -> 1.3,
      "AT" -> 1.2,
      "BE" -> 1.1,
      "CH" -> 1.6,
      "CY" -> 1.3,
      "CZ" -> 1.2,
      "DE" -> 1.0,
      "DK" -> 1.2,
      "ES" -> 1.2,
      "FI" -> 1.3,
      "FR" -> 0.9,
      "GB" -> 1.5,
      "GR" -> 1.3,
      "HU" -> 1.2,
      "IE" -> 1.3,
      "IS" -> 1.3,
      "IT" -> 1.2,
      "LU" -> 1.3,
      "MT" -> 1.9,
      "NL" -> 1.0,
      "NO" -> 1.0,
      "PL" -> 1.0,
      "PT" -> 1.3,
      "RO" -> 0.8,
      "RS" -> 1.4,
      "RU" -> 0.8,
      "SE" -> 1.1,

      "BM" -> 1.8,
      "BS" -> 1.3,
      "CA" -> 1.2,
      "DO" -> 1.7,
      "JM" -> 1.4,
      "MX" -> 0.9,
      "PR" -> 1.5,
      "TT" -> 1.2,
      "US" -> 1.0
    )

  /**
   * Hub airports are the most important nearby airports of a given airport
   */
  def getHubAirports(fromAirport: Airport, isIsolatedMultiplier: Int): List[(Airport, Double)] = {
    val SKIP_AIRPORTS = List("PER", "KEF", "RUN" )
    if (SKIP_AIRPORTS.contains(fromAirport.iata)) {
      List.empty
    } else {
      val minDistance = if (GameConstants.isIsland(fromAirport.iata)) 25 else localityMinDistanceMap.getOrElse(fromAirport.countryCode, localityMinDistanceMap("default"))
      val maxDistance = minDistance * 4 + 275 * isIsolatedMultiplier
      //mostly trying to generate a base of domestic demand (also is more performant), but in smaller markets do int'l
      val intlCountries = List("AE","AL","AM","AT","AZ","BD","BY","BE","BJ","BT","BA","BI","BW","CH","CW","CZ","DE","DJ","DM","EE","GB","GE","GM","GH","GD","GN","GY","HK","HR","HU","IE","IL","JM","JO","KI","KW","KG","LV","LS","LR","LI","LT","LU","MO","MT","MD","MK","NA","NL","PR","QA","RW","RS","SG","SK","SI","SR","SY","SX","SZ","TJ","UY","UZ","VU")
      val intlAirports = List("TPE","KHH")
      val isDomestic = if (intlCountries.contains(fromAirport.countryCode) || intlAirports.contains(fromAirport.iata)) false else true
      val airports = Computation.getAirportWithinRange(fromAirport, maxDistance, minDistance, isDomestic)
      val numberDestinations = Math.min(22, (isIsolatedMultiplier + 1) * (fromAirport.size + 6))
      percentagesHubAirports(airports, fromAirport, numberDestinations, isIsolatedMultiplier) //find important airports
    }
  }

  def percentagesHubAirports(hubAirports: List[Airport], fromAirport: Airport, numberDestinations: Int, isIsolatedMultiplier: Int): List[(Airport, Double)] = {
    val distanceFloor = if (isIsolatedMultiplier > 0) 0 else localityMinDistanceMap.getOrElse(fromAirport.countryCode, localityMinDistanceMap("default")).toDouble //island airports will fly to close airports
    if (hubAirports.isEmpty) {
      List.empty
    } else {
      val avgPopMiddleIncome = hubAirports.map(_.popMiddleIncome).sum.toDouble / hubAirports.size
      val avgDistance = hubAirports.map(airport => Math.max(distanceFloor, Computation.calculateDistance(airport, fromAirport))).sum.toDouble / hubAirports.size

      val airportsWithScores = hubAirports.map { airport =>
        val popPercent = Math.min(16 * (isIsolatedMultiplier + 1), airport.popMiddleIncome.toDouble / avgPopMiddleIncome) //pop weight at most 16x
        val distancePercent = 1 - Math.max(distanceFloor, Computation.calculateDistance(airport, fromAirport)).toDouble / avgDistance
        val variability = (airport.id + cycle + 2) % 4 / 4.0
        val weightedScore = if (GameConstants.doesPairExist(fromAirport.iata, airport.iata, GameConstants.railLookupSet)) {
          0
        } else Math.max(0, 0.7 * distancePercent + 0.2 * popPercent + 0.0 * variability * isIsolatedMultiplier) //todo: turn on
        (airport, weightedScore)
      }.sortBy(_._2).takeRight(numberDestinations)

      val totalScore = airportsWithScores.map(_._2).sum

      // Normalize the scores to get percentages that sum to 1
      val normalizedScores = airportsWithScores.map { case (airport, score) =>
        (airport, score / totalScore)
      }

      normalizedScores.sortBy(_._2)
    }
  }

  /**
   * Adds at least 4 discount economy demand to all the fromAirport's "hub" airports
   *
   * @param fromAirport
   * @return map[String IATA, Int demand]
   */
  def generateHubAirportDemand(fromAirport: Airport): Map[String, LinkClassValues] = {
    val isIsolatedMultiplier = if (fromAirport.getFeatures().exists(_.featureType == AirportFeatureType.ISOLATED_TOWN)){
      fromAirport.getFeatures().find(_.featureType == AirportFeatureType.ISOLATED_TOWN).get.strength
    } else {
      0
    }
    val hubAirports: List[(Airport, Double)] = getHubAirports(fromAirport, isIsolatedMultiplier)

    if (hubAirports.isEmpty) {
      Map.empty
    } else {
      val fromDemand = if (hubAirports.length < 3 && fromAirport.popMiddleIncome < 2000) {
        32
      } else if (isIsolatedMultiplier > 0) {
        Math.min(4800, fromAirport.popMiddleIncome.toDouble / (225.0 / (isIsolatedMultiplier + 2) * Math.min(5, fromAirport.size)))
      } else {
        Math.min(4800, fromAirport.popMiddleIncome.toDouble / 950 * Math.min(5, fromAirport.size + 1))
      }

      // Divide the demand among hubAirports based on percentages
      hubAirports.map { airport =>
        val demandForAirport = Math.max(4, (fromDemand * airport._2).toInt) //min demand is 4
        if (isIsolatedMultiplier > 0) {
          (airport._1.iata, LinkClassValues(demandForAirport, 0, 0))
        } else {
          (airport._1.iata, LinkClassValues(0, 0, 0, demandForAirport))
        }

      }.toMap
    }
  }

  def computeDemand(cycle: Int, airportStats: immutable.Map[Int, AirportStatistics]): List[(PassengerGroup, Airport, Int)] = {
    println("Loading airports")
    val airports: List[Airport] = AirportSource.loadAllAirports(true).filter { airport =>
      airport.iata != "" && airport.popMiddleIncome > 0
    }
    println(s"Loaded ${airports.size} airports")
    val countryRelationships = CountrySource.getCountryMutualRelationships()
    val destinationList = DestinationSource.loadAllEliteDestinations()

    val computedDemandChunks = airports.par.flatMap { fromAirport =>
      val flightPreferencesPool = getFlightPreferencePoolOnAirport(fromAirport)
      val hubAirportsDemands = generateHubAirportDemand(fromAirport)

      // Generate chunks for demand to all other regular airports
      val regularDemandChunks = airports.flatMap { toAirport =>
        val distance = Computation.calculateDistance(fromAirport, toAirport)
        if (canHaveDemand(fromAirport, toAirport, distance)) {
          val relationship = countryRelationships.getOrElse((fromAirport.countryCode, toAirport.countryCode), 0)
          val affinity = Computation.calculateAffinityValue(fromAirport.zone, toAirport.zone, relationship)
          
          val demand = computeBaseDemandBetweenAirports(fromAirport, toAirport, affinity, distance)

          // Combine all chunks for this airport pair
          val travelerDemandValue = demand.travelerDemand + hubAirportsDemands.getOrElse(toAirport.iata, LinkClassValues.empty)
          val travelerType = if (PassengerType.TRAVELER_SMALL_TOWN_CEILING > 100000) PassengerType.TRAVELER else PassengerType.TRAVELER_SMALL_TOWN
          val travelerChunks = generateChunksForPassengerType(travelerDemandValue, fromAirport, toAirport, travelerType, flightPreferencesPool, airportStats)
          val businessChunks = generateChunksForPassengerType(demand.businessDemand, fromAirport, toAirport, PassengerType.BUSINESS, flightPreferencesPool, airportStats)
          val touristChunks = generateChunksForPassengerType(demand.touristDemand, fromAirport, toAirport, PassengerType.TOURIST, flightPreferencesPool, airportStats)

          travelerChunks ++ businessChunks ++ touristChunks
        } else {
          List.empty
        }
      }

      val eliteDemand = generateEliteDemand(fromAirport, destinationList).getOrElse(List.empty)
      val eliteDemandChunks = eliteDemand.flatMap {
        case (toAirport, (passengerType, demand)) =>
          generateChunksForPassengerType(demand, fromAirport, toAirport, passengerType, flightPreferencesPool, airportStats)
      }

      // Return all chunks originating from `fromAirport`
      regularDemandChunks ++ eliteDemandChunks
    }.toList // .toList converts the parallel collection back to a standard List

    println(s"Generated ${computedDemandChunks.length} demand chunks from regular/elite demand")

    // Event Demand (can be handled separately as it's smaller) ---
    val eventDemand = generateEventDemand(cycle, airports)
    val eventDemandChunks = eventDemand.flatMap {
      case (fromAirport, toAirportsWithDemand) =>
        val flightPreferencesPool = getFlightPreferencePoolOnAirport(fromAirport)
        toAirportsWithDemand.flatMap {
          case (toAirport, (passengerType, demand)) =>
            generateChunksForPassengerType(demand, fromAirport, toAirport, passengerType, flightPreferencesPool, airportStats)
        }
    }
    println(s"Generated ${eventDemandChunks.length} event demand chunks")

    computedDemandChunks ++ eventDemandChunks
  }


  /**
   * Helper function to generate all chunks for a specific passenger type's demand.
   */
  def generateChunksForPassengerType(demand: LinkClassValues, fromAirport: Airport, toAirport: Airport, passengerType: PassengerType.Value, flightPreferencesPool: FlightPreferencePool, allAirportStats: immutable.Map[Int, AirportStatistics]): List[(PassengerGroup, Airport, Int)] = {
    if (demand.total <= 0) {
      List.empty
    } else {
      LinkClass.values.flatMap { linkClass =>
        //if there's no stats available, assume setup or testcase and generate unaltered demands
        val demandForClass = allAirportStats.get(fromAirport.id) match {
          case Some(stats) if stats.baselineDemand > 0 => 
            val travelRate = Airport.travelRate(stats.fromPax.toDouble / stats.baselineDemand, fromAirport.size)
            demandRandomizerByType(passengerType, (demand(linkClass) * travelRate).toInt)
          case _ =>
            demand(linkClass)
        }
        if (demandForClass > 0) {
          // This helper breaks a single integer demand into chunks
          generateDemandChunks(demandForClass, fromAirport, toAirport, passengerType, linkClass, flightPreferencesPool)
        } else {
          List.empty
        }
      }
    }
  }

  /**
   * Tail-recursive helper to break a single demand value into chunks.
   */
  def generateDemandChunks(totalDemand: Int, fromAirport: Airport, toAirport: Airport, passengerType: PassengerType.Value, linkClass: LinkClass, flightPreferencesPool: FlightPreferencePool): List[(PassengerGroup, Airport, Int)] = {
    @scala.annotation.tailrec
    def loop(remaining: Int, acc: List[(PassengerGroup, Airport, Int)]): List[(PassengerGroup, Airport, Int)] = {
      if (remaining <= 0) {
        acc
      } else {
        val currentChunkSize = if (remaining > baseDemandChunkSize) baseDemandChunkSize else remaining
        val newTuple = (
          PassengerGroup(fromAirport, flightPreferencesPool.draw(passengerType, linkClass), passengerType),
          toAirport,
          currentChunkSize
        )
        loop(remaining - currentChunkSize, newTuple:: acc)
      }
    }
    loop(totalDemand, Nil).reverse
  }

  /**
   * used on the frontend and for tests
   *
   * @param fromAirport
   * @param toAirport
   * @param affinity
   * @param distance
   * @return
   */
  def computeDemandWithPreferencesBetweenAirports(fromAirport: Airport, toAirport: Airport, affinity: Int, distance: Int): Map[(LinkClass, FlightPreference, PassengerType.Value), Int] = {
    if (!canHaveDemand(fromAirport, toAirport, Computation.calculateDistance(fromAirport, toAirport))) {
      Map.empty
    } else {
      val demand = computeBaseDemandBetweenAirports(fromAirport: Airport, toAirport: Airport, affinity: Int, distance: Int)
      val flightPreferencesPool = getFlightPreferencePoolOnAirport(fromAirport)
      val demandByPreference = mutable.Map[(LinkClass, FlightPreference, PassengerType.Value), Int]()

      val isDomestic = fromAirport.countryCode == toAirport.countryCode
      val fromHubAirportsDemands: Map[String, LinkClassValues] = if (isDomestic) {
        generateHubAirportDemand(fromAirport)
      } else Map.empty

      def processLinkClassDemand(passengerType: PassengerType.Value, linkClassValues: LinkClassValues) = {
        LinkClass.values.foreach { linkClass =>
          val demandForClass = linkClassValues(linkClass)
          if (demandForClass > 0) {
            var remainingDemand = demandForClass
            while (remainingDemand > 0) {
              val groupSize = Math.min(remainingDemand, baseDemandChunkSize + ThreadLocalRandom.current().nextInt(baseDemandChunkSize)) //random group size between 10 and 20
              val groupSizeWithMinCheck = if (remainingDemand - groupSize <= 10) remainingDemand else groupSize //if remaining group is small, just combine them
              val preference = flightPreferencesPool.draw(passengerType, linkClass)
              val key = (linkClass, preference, passengerType)
              demandByPreference(key) = demandByPreference.getOrElse(key, 0) + groupSizeWithMinCheck
              remainingDemand -= groupSizeWithMinCheck
            }
          }
        }
      }

      val travelerDemand = demand.travelerDemand + fromHubAirportsDemands.getOrElse(toAirport.iata, LinkClassValues.empty)
      processLinkClassDemand(PassengerType.BUSINESS, demand.businessDemand)
      processLinkClassDemand(PassengerType.TOURIST, demand.touristDemand)
      processLinkClassDemand(PassengerType.TRAVELER, travelerDemand)

      demandByPreference.map { case ((linkClass, preference, passengerType), total) =>
        ((linkClass, preference, passengerType), total)
      }.toList.sortBy(_._2).toMap
    }
  }

  /**
   * compute demand between any two airports, return Demand with pax type & classes
   */
  def computeBaseDemandBetweenAirports(fromAirport: Airport, toAirport: Airport, affinity: Int, distance: Int): Demand = {
    val demand = computeRawDemandBetweenAirports(fromAirport: Airport, toAirport: Airport, affinity: Int, distance: Int)

    //lower demand to (boosted) poor places, but not applied on tourists
    val toIncomeAdjust = Math.min(1.0, (toAirport.income.toDouble + 12_000) / 54_000)

    val percentTraveler = Math.min(0.7, fromAirport.income.toDouble / 40_000)

    val demands = Map(PassengerType.TRAVELER -> demand * percentTraveler * toIncomeAdjust, PassengerType.BUSINESS -> (demand * (1 - percentTraveler - 0.1) * toIncomeAdjust), PassengerType.TOURIST -> demand * 0.1)

    val localityAdjust = localityAdjustMap.getOrElse(fromAirport.countryCode, 1.2) * localityAdjustMap.getOrElse(toAirport.countryCode, 1.2)
    val hasCompetingRail = if (GameConstants.doesPairExist(fromAirport.iata, toAirport.iata, GameConstants.railLookupSet)) 0.2 else 1

    //add charm demand
    val featureAdjustedDemands = demands.map { case (passengerType, demand) =>
      val fromAdjustments = fromAirport.getFeatures().map(feature => feature.demandAdjustment(demand, passengerType, fromAirport.id, fromAirport, toAirport, affinity, distance))
      val toAdjustments = toAirport.getFeatures().map(feature => feature.demandAdjustment(demand, passengerType, toAirport.id, fromAirport, toAirport, affinity, distance))
      (passengerType, localityAdjust * hasCompetingRail * (fromAdjustments.sum + toAdjustments.sum + demand))
    }

    //for each trade affinity, add base "trade demand" to biz demand, modded by distance
    val minDistance = if (GameConstants.isIsland(fromAirport.iata)) 50 else (MIN_DISTANCE * 1.5).toInt
    val affinityTradeAdjust = if (distance > minDistance && (fromAirport.population >= 16000 || toAirport.population >= 16000) && affinity > 1) {
      val baseTradeDemand = 8 + (12 - fromAirport.size.toDouble) * 1.5
      val distanceMod = Math.min(1.0, 3500.0 / distance)
      val matchOnlyTradeAffinities = 5
      (baseTradeDemand * distanceMod * Computation.affinityToSet(fromAirport.zone, toAirport.zone, matchOnlyTradeAffinities).length).toInt
    } else {
      0
    }

    // Save compute by combining small demands into largest paxType
    val consolidatedDemands = {
      val demandsToConsolidate = featureAdjustedDemands.filter(_._2 < 8)
      val remainingDemands = featureAdjustedDemands.filter(_._2 >= 8)

      if (demandsToConsolidate.nonEmpty) {
        val smallDemandsTotal = demandsToConsolidate.values.sum
        val largestCategory = featureAdjustedDemands.maxBy(_._2)._1
        val largestCategoryOriginalValue = featureAdjustedDemands(largestCategory)
        remainingDemands + (largestCategory -> (remainingDemands.getOrElse(largestCategory, largestCategoryOriginalValue) + smallDemandsTotal))
      } else {
        featureAdjustedDemands
      }
    }

    val hasFirstClass = fromAirport.countryCode != toAirport.countryCode && distance >= 1500 || distance >= 3000
    Demand(
      computeClassCompositionFromIncome(consolidatedDemands.getOrElse(PassengerType.TRAVELER, 0.0), fromAirport.income, PassengerType.TRAVELER, hasFirstClass, distance),
      computeClassCompositionFromIncome(consolidatedDemands.getOrElse(PassengerType.BUSINESS, 0.0) + affinityTradeAdjust, fromAirport.income, PassengerType.BUSINESS, hasFirstClass, distance),
      computeClassCompositionFromIncome(consolidatedDemands.getOrElse(PassengerType.TOURIST, 0.0), fromAirport.income, PassengerType.TOURIST, hasFirstClass, distance)
    )
  }

  /**
   * compute raw demand, before classing it
   */
  private def computeRawDemandBetweenAirports(fromAirport: Airport, toAirport: Airport, affinity: Int, distance: Int): Int = {
    val drivingDistance = 1.2 * localityMinDistanceMap.getOrElse(fromAirport.countryCode, localityMinDistanceMap("default"))
    val distanceReducerExponent: Double =
      if (distance < drivingDistance && affinity < 6 && ! GameConstants.ISOLATED_COUNTRIES.contains(fromAirport.countryCode) && ! GameConstants.isIsland(fromAirport.iata) && ! GameConstants.isIsland(toAirport.iata)) {
        distance.toDouble / drivingDistance //don't apply to islands or business shuttle routes
      } else if (distance > 4000) {
        0.85 - distance.toDouble / 40000 * (1 - affinity.toDouble / 10.0) * Math.max(5.5 - toAirport.size.toDouble * 0.5, 0) //affinity & scale affects perceived distance
      } else if (distance > 1000) {
        1.05 - distance.toDouble / 20000 * (1 - affinity.toDouble / 10.0) //affinity affects perceived distance
      } else {
        1
      }
    if (distanceReducerExponent < 0) return 0 //return out early if long distance means no demand

    val toPopIncomeAdjusted = 0.5 * toAirport.popMiddleIncome + 0.5 * toAirport.population

    //domestic/foreign/affinity relation multiplier
    val airportAffinityMultiplier: Double =
      if (affinity >= 5) (affinity - 5) * 0.05 + 1 //domestic+
      else if (affinity < 0) 0.025
      else affinity * 0.1 + 0.025

    //set very low income floor, specifically traffic to/from central airports that is otherwise missing
    def addToVeryLowIncome(fromPop: Long, airportScale: Int): Int = {
      val minPop = 5e5
      val minDenominator = 13000

      val boost = if (fromPop <= minPop) {
        (fromPop / minDenominator).toInt
      } else {
        val logFactor = 1 + Math.log10(fromPop / minPop)
        val adjustedDenominator = (minDenominator * logFactor)
        (fromPop / adjustedDenominator).toInt + 8
      }
      Math.min(575, boost * (airportScale - 0.8)).toInt
    }
    val buffLowIncomeAirports = if (fromAirport.income <= 5000 && toAirport.income <= 8000 && distance <= 3000 && affinity >= 2 && (toAirport.size >= 4 || fromAirport.size >= 4)) addToVeryLowIncome(fromAirport.population, fromAirport.size) else 0
  
    val baseDemand: Double = Math.max(0, airportAffinityMultiplier * fromAirport.popMiddleIncome * toPopIncomeAdjusted / 225_000 / 225_000) + buffLowIncomeAirports
    Math.pow(baseDemand, distanceReducerExponent).toInt
  }

  private def computeClassCompositionFromIncome(demand: Double, income: Int, passengerType: PassengerType.Value, hasFirstClass: Boolean, distance: Int): LinkClassValues = {
    val firstClassDemand = if (hasFirstClass) {
        val distanceMod = distance.toDouble / 6000
        if (income > FIRST_CLASS_INCOME_MAX) {
          demand * FIRST_CLASS_PERCENTAGE_MAX(passengerType) * distanceMod
        } else {
          demand * FIRST_CLASS_PERCENTAGE_MAX(passengerType) * income.toDouble / FIRST_CLASS_INCOME_MAX * distanceMod
        }
      } else {
        0
      }
    val businessClassDemand = if (income > BUSINESS_CLASS_INCOME_MAX) {
        demand * BUSINESS_CLASS_PERCENTAGE_MAX(passengerType)
      } else {
        demand * BUSINESS_CLASS_PERCENTAGE_MAX(passengerType) * income.toDouble / BUSINESS_CLASS_INCOME_MAX
      }
    val discountClassDemand = demand * DISCOUNT_CLASS_PERCENTAGE_MAX(passengerType) * (1 - Math.min(income.toDouble / 30_000, 0.5))
    //adding cutoffs to reduce the tail and have fewer passenger groups to calculate
    val firstClassCutoff = if (firstClassDemand > 1) firstClassDemand else 0
    val businessClassCutoff = if (businessClassDemand > 3) businessClassDemand else 0
    val discountClassCutoff = if (discountClassDemand > 15) discountClassDemand else 0
    val economyClassDemand = Math.max(0, demand - firstClassCutoff - businessClassCutoff - discountClassCutoff)

    LinkClassValues.getInstance(economyClassDemand.toInt, businessClassCutoff.toInt, firstClassCutoff.toInt, discountClassCutoff.toInt)
  }


  val ELITE_MIN_GROUP_SIZE = 5
  val ELITE_MAX_GROUP_SIZE = 9
  val CLOSE_DESTINATIONS_RADIUS = 1800

  private def generateEliteDemand(fromAirport: Airport, destinationList: List[Destination] ): Option[List[(Airport, (PassengerType.Value, LinkClassValues))]] = {

    if (fromAirport.popElite > 0) {
      val demandList = new java.util.ArrayList[(Airport, (PassengerType.Value, LinkClassValues))]()
      val groupSize = ThreadLocalRandom.current().nextInt(ELITE_MIN_GROUP_SIZE, ELITE_MAX_GROUP_SIZE)

      val destinationsByDistance = destinationList.groupBy { destination =>
        val distance = Computation.calculateDistance(fromAirport, destination.airport)
        if (distance >= 100 && distance <= CLOSE_DESTINATIONS_RADIUS) {
          "close"
        } else {
          "far"
        }
      }
      val closeDestinations = destinationsByDistance.getOrElse("close", List.empty)
      val farAwayDestinations = destinationsByDistance.getOrElse("far", List.empty)

      var numberDestinations = Math.ceil(launchDemandFactor * 0.65 * fromAirport.popElite / groupSize.toDouble).toInt

      while (numberDestinations >= 0) {
        val destination = if (numberDestinations % 2 == 1 && closeDestinations.length > 5) {
          closeDestinations(ThreadLocalRandom.current().nextInt(closeDestinations.length))
        } else {
          farAwayDestinations(ThreadLocalRandom.current().nextInt(farAwayDestinations.length))
        }
        numberDestinations -= 1
        demandList.add((destination.airport, (PassengerType.ELITE, LinkClassValues(0, 0, groupSize))))
      }
      Some(demandList.asScala.toList)
    } else {
      None
    }
  }

  def generateEventDemand(cycle: Int, airports: List[Airport]): List[(Airport, List[(Airport, (PassengerType.Value, LinkClassValues))])] = {
    val eventDemand = ListBuffer[(Airport, List[(Airport, (PassengerType.Value, LinkClassValues))])]()
    EventSource.loadEvents().filter(_.isActive(cycle)).foreach { event =>
      event match {
        case olympics: Olympics => eventDemand.appendAll(generateOlympicsDemand(cycle, olympics, airports))
        case _ => //
      }

    }
    eventDemand.toList
  }


  val OLYMPICS_DEMAND_BASE = 50000
  def generateOlympicsDemand(cycle: Int, olympics: Olympics, airports: List[Airport]): List[(Airport, List[(Airport, (PassengerType.Value, LinkClassValues))])]  = {
    if (olympics.currentYear(cycle) == 4) { //only has special demand on 4th year
      val week = (cycle - olympics.startCycle) % Olympics.WEEKS_PER_YEAR //which week is this
      val demandMultiplier = Olympics.getDemandMultiplier(week)
      Olympics.getSelectedAirport(olympics.id) match {
        case Some(selectedAirport) => generateOlympicsDemand(cycle, demandMultiplier, Olympics.getAffectedAirport(olympics.id, selectedAirport), airports)
        case None => List.empty
      }
    } else {
      List.empty
    }
  }

  def generateOlympicsDemand(cycle: Int, demandMultiplier: Int, olympicsAirports: List[Airport], allAirports: List[Airport]): List[(Airport, List[(Airport, (PassengerType.Value, LinkClassValues))])]  = {
    val totalDemand = OLYMPICS_DEMAND_BASE * demandMultiplier

    val countryRelationships = CountrySource.getCountryMutualRelationships()
    //use existing logic, just scale the total back to totalDemand at the end
    val unscaledDemands = ListBuffer[(Airport, List[(Airport, (PassengerType.Value, LinkClassValues))])]()
    val otherAirports = allAirports.filter(airport => !olympicsAirports.map(_.id).contains(airport.id))

    otherAirports.foreach { airport =>
      val unscaledDemandsOfThisFromAirport = ListBuffer[(Airport, (PassengerType.Value, LinkClassValues))]()
      val fromAirport = airport
      olympicsAirports.foreach {  olympicsAirport =>
        val toAirport = olympicsAirport
        val distance = Computation.calculateDistance(fromAirport, toAirport)
        val relationship = countryRelationships.getOrElse((fromAirport.countryCode, toAirport.countryCode), 0)
        val affinity = Computation.calculateAffinityValue(fromAirport.zone, toAirport.zone, relationship)
        val baseDemand = computeRawDemandBetweenAirports(fromAirport, toAirport, affinity, distance)
        val computedDemand = computeClassCompositionFromIncome(baseDemand, fromAirport.income, PassengerType.OLYMPICS, true, distance)
          if (computedDemand.total > 1) {
          unscaledDemandsOfThisFromAirport.append((toAirport, (PassengerType.OLYMPICS, computedDemand)))
        }
      }
      unscaledDemands.append((fromAirport, unscaledDemandsOfThisFromAirport.toList))
    }

    //now scale all the demands based on the totalDemand
    val unscaledTotalDemands = unscaledDemands.map {
      case (toAirport, unscaledDemandsOfThisToAirport) => unscaledDemandsOfThisToAirport.map {
        case (fromAirport, (passengerType, demand)) => demand.total
      }.sum
    }.sum
    val multiplier = totalDemand.toDouble / unscaledTotalDemands
    println(s"olympics scale multiplier is $multiplier")
    val scaledDemands = unscaledDemands.map {
      case (toAirport, unscaledDemandsOfThisToAirport) =>
        (toAirport, unscaledDemandsOfThisToAirport.map {
          case (fromAirport, (passengerType, unscaledDemand)) =>
            (fromAirport, (passengerType, unscaledDemand * multiplier))
        })
    }.toList

    scaledDemands
  }

  def getFlightPreferencePoolOnAirport(homeAirport: Airport): FlightPreferencePool = {
    import FlightPreferenceDefinition._

    /**
     * each class has a total weight of 4, unless "high income" and then 5
     * we use the standardized weighting to estimate demand in the frontend
     */
    val basePreferences: Map[PassengerType.Value, List[(FlightPreference, Int)]] = Map(
      PassengerType.BUSINESS -> List(
        dealPreference(homeAirport, DISCOUNT, 1.0, weight = 2),
        dealPreference(homeAirport, DISCOUNT, PRICE_DISCOUNT_PLUS_MULTIPLIER, weight = 2),
        appealPreference(homeAirport, ECONOMY, 1.0, loungeLevelRequired = 0, weight = 1),
        appealPreference(homeAirport, ECONOMY, 1.0, loungeLevelRequired = 0, loyaltyRatio = 1.1, weight = 1),
        appealPreference(homeAirport, ECONOMY, 1.0, loungeLevelRequired = 0, loyaltyRatio = 1.2, weight = 1),
        lastMinutePreference(homeAirport, ECONOMY, PRICE_LAST_MIN_MULTIPLIER, loungeLevelRequired = 0, weight = 1),
        appealPreference(homeAirport, BUSINESS, 1.0, loungeLevelRequired = 1, weight = 1),
        appealPreference(homeAirport, BUSINESS, 1.0, loungeLevelRequired = 2, loyaltyRatio = 1.15, weight = 1),
        appealPreference(homeAirport, BUSINESS, 1.0, loungeLevelRequired = 2, loyaltyRatio = 1.25, weight = 1),
        lastMinutePreference(homeAirport, BUSINESS, PRICE_LAST_MIN_MULTIPLIER, loungeLevelRequired = 0, weight = 1),
        appealPreference(homeAirport, FIRST, 1.0, loungeLevelRequired = 2, weight = 1),
        appealPreference(homeAirport, FIRST, 1.0, loungeLevelRequired = 3, loyaltyRatio = 1.15, weight = 1),
        appealPreference(homeAirport, FIRST, 1.0, loungeLevelRequired = 3, loyaltyRatio = 1.25, weight = 1),
        lastMinutePreference(homeAirport, FIRST, PRICE_LAST_MIN_MULTIPLIER, loungeLevelRequired = 1, weight = 1),
      ),
      PassengerType.TOURIST -> List(
        dealPreference(homeAirport, DISCOUNT, 1.0, weight = 2),
        dealPreference(homeAirport, DISCOUNT, PRICE_DISCOUNT_PLUS_MULTIPLIER, weight = 2),
        dealPreference(homeAirport, ECONOMY, 1.0, weight = 1),
        appealPreference(homeAirport, ECONOMY, 1.0, loungeLevelRequired = 0, weight = 1),
        appealPreference(homeAirport, ECONOMY, 1.0, loungeLevelRequired = 0, loyaltyRatio = 1.1, weight = 1),
        lastMinutePreference(homeAirport, ECONOMY, PRICE_LAST_MIN_DEAL_MULTIPLIER, loungeLevelRequired = 0, weight = 2),
        dealPreference(homeAirport, BUSINESS, 1.0, weight = 2),
        appealPreference(homeAirport, BUSINESS, 1.0, loungeLevelRequired = 1, weight = 1),
        appealPreference(homeAirport, BUSINESS, 1.0, loungeLevelRequired = 2, loyaltyRatio = 1.15, weight = 1),
        lastMinutePreference(homeAirport, BUSINESS, PRICE_LAST_MIN_DEAL_MULTIPLIER, loungeLevelRequired = 1, weight = 1),
        appealPreference(homeAirport, FIRST, 1.0, loungeLevelRequired = 2, loyaltyRatio = 1.1, weight = 4),
      ),
      PassengerType.TRAVELER -> List(
        dealPreference(homeAirport, DISCOUNT, 1.0, weight = 2),
        dealPreference(homeAirport, DISCOUNT, PRICE_DISCOUNT_PLUS_MULTIPLIER, weight = 2),
        dealPreference(homeAirport, ECONOMY, 1.0, weight = 1),
        appealPreference(homeAirport, ECONOMY, 1.0, loungeLevelRequired = 0, weight = 2),
        appealPreference(homeAirport, ECONOMY, 1.0, loungeLevelRequired = 0, loyaltyRatio = 1.1, weight = 1),
        dealPreference(homeAirport, BUSINESS, 1.0, weight = 1),
        appealPreference(homeAirport, BUSINESS, 1.0, loungeLevelRequired = 1, weight = 2),
        appealPreference(homeAirport, BUSINESS, 1.0, loungeLevelRequired = 1, loyaltyRatio = 1.2, weight = 1),
        appealPreference(homeAirport, FIRST, 1.0, loungeLevelRequired = 2, weight = 4),
      ),
      PassengerType.TRAVELER_SMALL_TOWN -> List(
        dealPreference(homeAirport, DISCOUNT, 1.0, weight = 1),
        dealPreference(homeAirport, ECONOMY, 1.0, weight = 1),
        appealPreference(homeAirport, ECONOMY, 1.0, loungeLevelRequired = 0, weight = 1),
        appealPreference(homeAirport, BUSINESS, 1.0, loungeLevelRequired = 1, weight = 1),
        lastMinutePreference(homeAirport, BUSINESS, PRICE_LAST_MIN_DEAL_MULTIPLIER, loungeLevelRequired = 1, weight = 1),
        appealPreference(homeAirport, FIRST, 1.0, loungeLevelRequired = 2, weight = 1),
      )
    )

    val lastMinutePreferences = List(
      lastMinutePreference(homeAirport, ECONOMY, PRICE_LAST_MIN_MULTIPLIER, loungeLevelRequired = 0, weight = 1),
      lastMinutePreference(homeAirport, BUSINESS, PRICE_LAST_MIN_MULTIPLIER, loungeLevelRequired = 1, weight = 1),
      lastMinutePreference(homeAirport, FIRST, PRICE_LAST_MIN_MULTIPLIER, loungeLevelRequired = 2, weight = 1)
    )

    val flightPreferencesAdjusted = if (homeAirport.income > Airport.HIGH_INCOME * HIGH_INCOME_RATIO_FOR_BOOST) {
      basePreferences.map { case (passengerType, preferences) => passengerType -> (preferences ++ lastMinutePreferences) }
    } else basePreferences

    new FlightPreferencePool(flightPreferencesAdjusted)
  }

  class FlightPreferencePool(preferencesWithWeight: Map[PassengerType.Value, List[(FlightPreference, Int)]]) {
    val pool: Map[PassengerType.Value, Map[LinkClass, List[FlightPreference]]] = preferencesWithWeight.map { case (passengerType, preferenceList) =>
      (passengerType, preferenceList.groupBy { case (flightPreference, weight) =>
        flightPreference.preferredLinkClass
      }.view.mapValues { _.map { case (pref, weight) => pref }.toList }.toMap)
    }

    def draw(passengerType: PassengerType.Value, linkClass: LinkClass): FlightPreference = {
      val poolForPassengerType = pool.getOrElse(passengerType, pool(PassengerType.BUSINESS))
      val poolForClass = poolForPassengerType(linkClass)
      poolForClass(ThreadLocalRandom.current().nextInt(poolForClass.length))
    }
  }

  object FlightPreferenceDefinition {

    def dealPreference(homeAirport: Airport, linkClass: LinkClass, modifier: Double, weight: Int): (FlightPreference, Int) =
      (DealPreference(homeAirport, linkClass, modifier), weight)

    def appealPreference(homeAirport: Airport, linkClass: LinkClass, modifier: Double, loungeLevelRequired: Int, loyaltyRatio: Double = 1.0, weight: Int): (FlightPreference, Int) =
      (AppealPreference.getAppealPreferenceWithId(homeAirport, linkClass, modifier, loungeLevelRequired, loyaltyRatio), weight)

    def lastMinutePreference(homeAirport: Airport, linkClass: LinkClass, modifier: Double, loungeLevelRequired: Int, weight: Int): (FlightPreference, Int) =
      (LastMinutePreference(homeAirport, linkClass, modifier, loungeLevelRequired), weight)

    val ECONOMY = com.patson.model.ECONOMY
    val BUSINESS = com.patson.model.BUSINESS
    val FIRST = com.patson.model.FIRST
    val DISCOUNT = com.patson.model.DISCOUNT_ECONOMY
  }

  sealed case class Demand(travelerDemand: LinkClassValues, businessDemand: LinkClassValues, touristDemand: LinkClassValues)
  def addUpDemands(demand: Demand): Int = {
    (demand.travelerDemand.totalwithSeatSize + demand.businessDemand.totalwithSeatSize + demand.touristDemand.totalwithSeatSize).toInt
  }
}
