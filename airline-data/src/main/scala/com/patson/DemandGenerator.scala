package com.patson

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
  val baseDemandChunkSize = 23

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

  def demandRandomizerByType(passengerType: PassengerType.Value, demand: Int, cycle: Int, cyclePhaseLength: Int): Int = {
    val randomizedDemand = if (passengerType == PassengerType.TOURIST) {
      demandRandomizer(demand, cycle, cyclePhaseLength, 2, 24)
    } else if (passengerType == PassengerType.BUSINESS) {
      demandRandomizer(demand, cycle, cyclePhaseLength, 1, 12)
    } else { //traveler, elite
      demandRandomizer(demand, cycle, cyclePhaseLength)
    }
    randomizedDemand
  }

  def demandRandomizer(demand: Int, cycle: Int, frequency: Int, amplitudeRatio: Int = 1, offset: Int = 0): Int = {
    val baseSeasonalPct = 0.07  // The wave naturally swings +/- 8%
    val noisePct = 0.03
    val rng = ThreadLocalRandom.current()

    val amplitude = demand * baseSeasonalPct * amplitudeRatio

    val sinValue = math.sin(2 * math.Pi * (cycle + offset) / frequency)
    val seasonalAdjustment = amplitude * sinValue

    val randomNoise = demand * rng.nextDouble(-noisePct, noisePct)
    val rawNewDemand = Math.max(0, demand + seasonalAdjustment + randomNoise).toInt

    val threshold = 10  // Remove some small passenger groups
    if (rawNewDemand >= threshold) {
      rawNewDemand
    } else {
      if (rng.nextDouble() < 0.40) {
        (rawNewDemand * 2.3).toInt
      } else {
        0
      }
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
   * Hub airports are the most important nearby airports of a given airport
   */
  def getHubAirports(fromAirport: Airport, isIsolatedMultiplier: Int, cycle: Int): List[(Airport, Double)] = {
    val SKIP_AIRPORTS = List("PER", "KEF", "RUN" )
    if (SKIP_AIRPORTS.contains(fromAirport.iata)) {
      List.empty
    } else {
      val localizedDistance = DemandConstants.localityMinDistanceMap.getOrElse(fromAirport.countryCode, DemandConstants.localityMinDistanceMap("default"))
      val minDistance = if (GameConstants.isIsland(fromAirport)) 25 else localizedDistance
      val maxDistance = Math.max(localizedDistance * 4, IsolatedTownFeature.HUB_RANGE_BRACKETS(isIsolatedMultiplier))
      //mostly trying to generate a base of domestic demand (also is more performant), but in smaller markets do int'l
      val intlCountries = List("AE","AL","AM","AT","AZ","BD","BY","BE","BJ","BT","BA","BI","BW","CH","CW","CZ","DE","DJ","DM","EE","GB","GE","GM","GH","GD","GN","GY","HK","HR","HU","IE","IL","JM","JO","KI","KW","KG","LV","LS","LR","LI","LT","LU","MO","MT","MD","MK","NA","NL","PR","QA","RW","RS","SG","SK","SI","SR","SY","SX","SZ","TC","TJ","UY","UZ","VC","VG","VI","VU")
      val intlAirports = List("TPE","KHH")
      val isDomestic = if (intlCountries.contains(fromAirport.countryCode) || intlAirports.contains(fromAirport.iata)) false else true
      val airports = Computation.getAirportWithinRange(fromAirport, maxDistance, minDistance, isDomestic)
      val numberDestinations = Math.min(22, (isIsolatedMultiplier + 1) * (fromAirport.size + 6))
      percentagesHubAirports(airports, fromAirport, numberDestinations, isIsolatedMultiplier, cycle) //find important airports
    }
  }

  def percentagesHubAirports(hubAirports: List[Airport], fromAirport: Airport, numberDestinations: Int, isIsolatedMultiplier: Int, cycle: Int): List[(Airport, Double)] = {
    val distanceFloor = if (isIsolatedMultiplier > 0) 0 else DemandConstants.localityMinDistanceMap.getOrElse(fromAirport.countryCode, DemandConstants.localityMinDistanceMap("default")).toDouble //island airports will fly to close airports
    if (hubAirports.isEmpty) {
      List.empty
    } else {
      val avgPopMiddleIncome = hubAirports.map(_.popMiddleIncome).sum.toDouble / hubAirports.size
      val avgDistance = hubAirports.map(airport => Math.max(distanceFloor, Computation.calculateDistance(airport, fromAirport))).sum.toDouble / hubAirports.size

      val airportsWithScores = hubAirports.map { airport =>
        val popPercent = Math.min(16 * (isIsolatedMultiplier + 1), airport.popMiddleIncome.toDouble / avgPopMiddleIncome) //pop weight at most 16x
        val distancePercent = 1 - Math.max(distanceFloor, Computation.calculateDistance(airport, fromAirport)).toDouble / avgDistance
        val variability = (airport.id + cycle + 2) % 4 / 4.0
        val weightedScore = if (DemandConstants.doesPairExist(fromAirport.iata, airport.iata, DemandConstants.railLookupSet)) {
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
  def generateHubAirportDemand(fromAirport: Airport, cycle: Int): Map[String, LinkClassValues] = {
    val isIsolatedMultiplier = if (fromAirport.getFeatures().exists(_.featureType == AirportFeatureType.ISOLATED_TOWN)){
      fromAirport.getFeatures().find(_.featureType == AirportFeatureType.ISOLATED_TOWN).get.strength
    } else {
      0
    }
    val hubAirports: List[(Airport, Double)] = getHubAirports(fromAirport, isIsolatedMultiplier, cycle)

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
    val cyclePhaseLength = CycleSource.loadAndUpdateCyclePhase()
    println("Loading airports")
    val airports: List[Airport] = AirportSource.loadAllAirports(true).filter { airport =>
      airport.iata != "" && airport.popMiddleIncome > 0
    }
    println(s"Loaded ${airports.size} airports")
    val countryRelationships = CountrySource.getCountryMutualRelationships()
    val destinationList = DestinationSource.loadAllEliteDestinations()

    val computedDemandChunks = airports.par.flatMap { fromAirport =>
      val flightPreferencesPool = getFlightPreferencePoolOnAirport(fromAirport)
      val hubAirportsDemands = generateHubAirportDemand(fromAirport, cycle)

      // Generate chunks for demand to all other regular airports
      val regularDemandChunks = airports.flatMap { toAirport =>
        val distance = Computation.calculateDistance(fromAirport, toAirport)
        if (canHaveDemand(fromAirport, toAirport, distance)) {
          val relationship = countryRelationships.getOrElse((fromAirport.countryCode, toAirport.countryCode), 0)
          val affinity = Computation.calculateAffinityValue(fromAirport.zone, toAirport.zone, relationship)
          
          val demand = computeBaseDemandBetweenAirports(fromAirport, toAirport, affinity, distance)

          // Combine all chunks for this airport pair
          val travelerDemandValue = demand.travelerDemand + hubAirportsDemands.getOrElse(toAirport.iata, LinkClassValues.empty)
          val travelerType = if (fromAirport.population > PassengerType.TRAVELER_SMALL_TOWN_CEILING) PassengerType.TRAVELER else PassengerType.TRAVELER_SMALL_TOWN
          val travelerChunks = generateChunksForPassengerType(travelerDemandValue, fromAirport, toAirport, travelerType, flightPreferencesPool, airportStats, cycle, cyclePhaseLength)
          val businessChunks = generateChunksForPassengerType(demand.businessDemand, fromAirport, toAirport, PassengerType.BUSINESS, flightPreferencesPool, airportStats, cycle, cyclePhaseLength)
          val touristChunks = generateChunksForPassengerType(demand.touristDemand, fromAirport, toAirport, PassengerType.TOURIST, flightPreferencesPool, airportStats, cycle, cyclePhaseLength)

          travelerChunks ++ businessChunks ++ touristChunks
        } else {
          List.empty
        }
      }

      val eliteDemand = generateEliteDemand(fromAirport, destinationList).getOrElse(List.empty)
      val eliteDemandChunks = eliteDemand.flatMap {
        case (toAirport, (passengerType, demand)) =>
          generateChunksForPassengerType(demand, fromAirport, toAirport, passengerType, flightPreferencesPool, Map.empty, cycle, cyclePhaseLength) //pass empty map to bypass travelRate and randomizer
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
            generateChunksForPassengerType(demand, fromAirport, toAirport, passengerType, flightPreferencesPool, Map.empty, cycle, cyclePhaseLength) //pass empty map to bypass travelRate and randomizer
        }
    }
    println(s"Generated ${eventDemandChunks.length} event demand chunks")

    computedDemandChunks ++ eventDemandChunks
  }


  /**
   * Helper function to generate all chunks for a specific passenger type's demand.
   */
  def generateChunksForPassengerType(demand: LinkClassValues, fromAirport: Airport, toAirport: Airport, passengerType: PassengerType.Value, flightPreferencesPool: FlightPreferencePool, allAirportStats: immutable.Map[Int, AirportStatistics], cycle: Int = 1, cyclePhaseLength : Int = 50): List[(PassengerGroup, Airport, Int)] = {
    if (demand.total <= 0) {
      List.empty
    } else {
      LinkClass.values.flatMap { linkClass =>
        //if there's no stats available, assume setup or testcase and generate unaltered demands
        val demandForClass = allAirportStats.get(fromAirport.id) match {
          case Some(stats) if stats.baselineDemand > 0 =>
            val travelRateAdjusted = Airport.travelRateAdjusted(stats.fromPax, stats.baselineDemand, fromAirport.size)
            demandRandomizerByType(passengerType, (demand(linkClass) * travelRateAdjusted).toInt, cycle, cyclePhaseLength)
          case _ =>
            demand(linkClass)
        }
        if (demandForClass > 0) {
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
  def computeDemandWithPreferencesBetweenAirports(fromAirport: Airport, toAirport: Airport, affinity: Int, distance: Int, cycle: Int): Map[(LinkClass, FlightPreference, PassengerType.Value), Int] = {
    if (!canHaveDemand(fromAirport, toAirport, Computation.calculateDistance(fromAirport, toAirport))) {
      Map.empty
    } else {
      val demand = computeBaseDemandBetweenAirports(fromAirport: Airport, toAirport: Airport, affinity: Int, distance: Int)
      val flightPreferencesPool = getFlightPreferencePoolOnAirport(fromAirport)
      val demandByPreference = mutable.Map[(LinkClass, FlightPreference, PassengerType.Value), Int]()

      val isDomestic = fromAirport.countryCode == toAirport.countryCode
      val fromHubAirportsDemands: Map[String, LinkClassValues] = if (isDomestic) {
        generateHubAirportDemand(fromAirport, cycle)
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

    val percentTraveler = Math.min(0.75, fromAirport.income.toDouble / 40_000)

    val demands = Map(PassengerType.TRAVELER -> demand * percentTraveler * toIncomeAdjust, PassengerType.BUSINESS -> (demand * (1 - percentTraveler - 0.1) * toIncomeAdjust), PassengerType.TOURIST -> demand * 0.1)

    val localityAdjust = DemandConstants.localityAdjustMap.getOrElse(fromAirport.countryCode, 1.2) * DemandConstants.localityAdjustMap.getOrElse(toAirport.countryCode, 1.2)
    val hasCompetingRail = if (DemandConstants.doesPairExist(fromAirport.iata, toAirport.iata, DemandConstants.railLookupSet)) 0.4 else 1

    //add charm demand and adjust for locality / competing rail 
    val featureAdjustedDemands = demands.map { case (passengerType, demand) =>
      val fromAdjustments = fromAirport.getFeatures().map(feature => feature.demandAdjustment(demand, passengerType, fromAirport.id, fromAirport, toAirport, affinity, distance))
      val toAdjustments = toAirport.getFeatures().map(feature => feature.demandAdjustment(demand, passengerType, toAirport.id, fromAirport, toAirport, affinity, distance))
      (passengerType, localityAdjust * hasCompetingRail * (fromAdjustments.sum + toAdjustments.sum + demand))
    }

    //for each trade affinity, add base "trade demand" to biz demand, modded by distance
    val minDistance = if (GameConstants.connectsIsland(fromAirport, toAirport)) 50 else (MIN_DISTANCE * 1.5).toInt
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
    val drivingDistance = DemandConstants.localityMinDistanceMap.getOrElse(fromAirport.countryCode, DemandConstants.localityMinDistanceMap("default"))
    val distanceReducerExponent: Double =
      if (distance < drivingDistance && affinity < 6 && ! GameConstants.connectsIsland(fromAirport, toAirport)) {
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
      Math.min(575, boost).toInt
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

      var numberDestinations = Math.ceil(0.6 * fromAirport.popElite / groupSize.toDouble).toInt

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
      val week = (cycle - olympics.startCycle) % Period.yearLength //which week is this
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
