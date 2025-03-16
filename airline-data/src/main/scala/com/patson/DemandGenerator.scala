package com.patson

import java.util.{ArrayList, Collections}
import com.patson.data.{AirportSource, CountrySource, CycleSource, DestinationSource, EventSource, GameConstants}
import com.patson.model.event.{EventType, Olympics}
import com.patson.model.{PassengerType, _}

import java.util.concurrent.ThreadLocalRandom
import scala.collection.immutable.Map
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.collection.parallel.CollectionConverters._
import scala.util.Random


object DemandGenerator {

  val FIRST_CLASS_INCOME_MAX = 125_000
  val FIRST_CLASS_PERCENTAGE_MAX: Map[PassengerType.Value, Double] = Map(PassengerType.TRAVELER -> 0, PassengerType.BUSINESS -> 0.12, PassengerType.TOURIST -> 0, PassengerType.ELITE -> 1, PassengerType.OLYMPICS -> 0)
  val BUSINESS_CLASS_INCOME_MAX = 125_000
  val BUSINESS_CLASS_PERCENTAGE_MAX: Map[PassengerType.Value, Double] = Map(PassengerType.TRAVELER -> 0.16, PassengerType.BUSINESS -> 0.49, PassengerType.TOURIST -> 0.1, PassengerType.ELITE -> 0, PassengerType.OLYMPICS -> 0.25)
  val DISCOUNT_CLASS_PERCENTAGE_MAX: Map[PassengerType.Value, Double] = Map(PassengerType.TRAVELER -> 0.38, PassengerType.BUSINESS -> 0, PassengerType.TOURIST -> 0.6, PassengerType.ELITE -> 0, PassengerType.OLYMPICS -> 0)
  val MIN_DISTANCE = 250 //does not apply to islands
  val HIGH_INCOME_RATIO_FOR_BOOST = 0.7 //at what percent of high income does demand change
  val PRICE_DISCOUNT_PLUS_MULTIPLIER = 1.05 //multiplier on base price
  val PRICE_LAST_MIN_MULTIPLIER = 1.11
  val PRICE_LAST_MIN_DEAL_MULTIPLIER = 0.9
//  val launchDemandFactor : Double = if (CycleSource.loadCycle() <= 1) 1.0 else Math.min(1, (45 + CycleSource.loadCycle().toDouble / 24) / 100)
  val launchDemandFactor : Double = 1.0
//  val baseLaunchDemandFactor : Double = if (CycleSource.loadCycle() <= 1) 1.0 else Math.min(1, (55 + CycleSource.loadCycle().toDouble / 48) / 100)
  val baseLaunchDemandFactor : Double = 1.0
  val demandRandomizer: Int = CycleSource.loadCycle() % 3

  import scala.jdk.CollectionConverters._



  def computeDemand(cycle: Int) = {
    println("Loading airports")
    //val allAirports = AirportSource.loadAllAirports(true)
    val airports: List[Airport] = AirportSource.loadAllAirports(true).filter { airport => (airport.iata != "" || airport.popMiddleIncome > 0) && airport.power > 0 }
    println("Loaded " + airports.size + " airports")
    
    val allDemands = new ArrayList[(Airport, List[(Airport, (PassengerType.Value, LinkClassValues))])]()
	  
	  val countryRelationships = CountrySource.getCountryMutualRelationships()
    val destinationList = DestinationSource.loadAllEliteDestinations()
	  airports.foreach {  fromAirport =>
	    val demandList = Collections.synchronizedList(new ArrayList[(Airport, (PassengerType.Value, LinkClassValues))]())

      airports.par.foreach { toAirport =>
        val distance = Computation.calculateDistance(fromAirport, toAirport)
        if (fromAirport != toAirport && (distance >= MIN_DISTANCE || GameConstants.connectsIsland(fromAirport, toAirport))) {
          val relationship = countryRelationships.getOrElse((fromAirport.countryCode, toAirport.countryCode), 0)
          val affinity = Computation.calculateAffinityValue(fromAirport.zone, toAirport.zone, relationship)

          val demand = computeBaseDemandBetweenAirports(fromAirport, toAirport, affinity, distance)
          val cutoff = fromAirport.id % 2 + demandRandomizer
          if (demand.travelerDemand.total > cutoff + 1) {
            demandList.add((toAirport, (PassengerType.TRAVELER, demand.travelerDemand)))
          }
          if (demand.businessDemand.total > cutoff) {
            demandList.add((toAirport, (PassengerType.BUSINESS, demand.businessDemand)))
          }
          if (demand.touristDemand.total > cutoff * 2) {
            demandList.add((toAirport, (PassengerType.TOURIST, demand.touristDemand)))
          }
        }
      }
      val eliteDemand = generateEliteDemand(fromAirport, destinationList)
      val fromDemand = demandList.asScala.toList ++ eliteDemand.getOrElse(List.empty)

	    allDemands.add((fromAirport, fromDemand))
    }

    val allDemandsAsScala = allDemands.asScala
    println(s"generated ${allDemandsAsScala.length} base demand groups; min feature size = $demandRandomizer")

    val eventDemand = generateEventDemand(cycle, airports)
    allDemandsAsScala.appendAll(eventDemand)
    println(s"generated ${eventDemand.length} event demand groups")

//    println("generating elite demand...")
//    val eliteDemand = generateEliteDemand(airports)
//    allDemandsAsScala.appendAll(eliteDemand)
//    println(s"generated ${eliteDemand.length} elite demand groups")

	  val baseDemandChunkSize = 10
	  
	  val allDemandChunks = ListBuffer[(PassengerGroup, Airport, Int)]()
    var oneCount = 0
	  allDemandsAsScala.foreach {
	    case (fromAirport, toAirportsWithDemand) =>
        //for each city generate different preferences
        val flightPreferencesPool = getFlightPreferencePoolOnAirport(fromAirport)

        toAirportsWithDemand.foreach {
          case (toAirport, (passengerType, demand)) =>
            LinkClass.values.foreach { linkClass =>
              if (demand(linkClass) > 0) {
                var remainingDemand = demand(linkClass)
                var demandChunkSize = baseDemandChunkSize + ThreadLocalRandom.current().nextInt(baseDemandChunkSize)
                while (remainingDemand > demandChunkSize) {
                  allDemandChunks.append((PassengerGroup(fromAirport, flightPreferencesPool.draw(passengerType, linkClass, fromAirport, toAirport), passengerType), toAirport, demandChunkSize))
                  remainingDemand -= demandChunkSize
                  demandChunkSize = baseDemandChunkSize + ThreadLocalRandom.current().nextInt(baseDemandChunkSize)
                }
                allDemandChunks.append((PassengerGroup(fromAirport, flightPreferencesPool.draw(passengerType, linkClass, fromAirport, toAirport), passengerType), toAirport, remainingDemand)) // don't forget the last chunk
              }
            }
        }

	  }

    allDemandChunks.toList
  }

  def computeDemandBetweenAirports(fromAirport : Airport, toAirport : Airport, affinity : Int, distance : Int) : Demand = {
    val demand = if (fromAirport != toAirport && fromAirport.population != 0 && toAirport.population != 0 && distance >= MIN_DISTANCE) {
      computeBaseDemandBetweenAirports(fromAirport: Airport, toAirport: Airport, affinity: Int, distance: Int): Demand
    } else {
      Demand(
        LinkClassValues(0, 0, 0),
        LinkClassValues(0, 0, 0),
        LinkClassValues(0, 0, 0)
      )
    }
    demand
  }

  def computeDemandWithPreferencesBetweenAirports(fromAirport: Airport, toAirport: Airport, affinity: Int, distance: Int): Map[(LinkClass, FlightPreference, PassengerType.Value), Int] = {
    val demand = computeDemandBetweenAirports(fromAirport, toAirport, affinity, distance)
    val flightPreferencesPool = getFlightPreferencePoolOnAirport(fromAirport)
    val demandByPreference = mutable.Map[(LinkClass, FlightPreference, PassengerType.Value), Int]()
    
    def processLinkClassDemand(passengerType: PassengerType.Value, linkClassValues: LinkClassValues) = {
      LinkClass.values.foreach { linkClass =>
        val demandForClass = linkClassValues(linkClass)
        if (demandForClass > 0) {
          var remainingDemand = demandForClass
          while (remainingDemand > 0) {
            val groupSize = Math.min(remainingDemand, 10 + ThreadLocalRandom.current().nextInt(10)) //random group size between 10 and 20
            val preference = flightPreferencesPool.draw(passengerType, linkClass, fromAirport, toAirport)
            val key = (linkClass, preference, passengerType)
            demandByPreference(key) = demandByPreference.getOrElse(key, 0) + groupSize
            remainingDemand -= groupSize
          }
        }
      }
    }
    
    processLinkClassDemand(PassengerType.BUSINESS, demand.businessDemand)
    processLinkClassDemand(PassengerType.TOURIST, demand.touristDemand)
    processLinkClassDemand(PassengerType.TRAVELER, demand.travelerDemand)
    
    demandByPreference.map { case ((linkClass, preference, passengerType), total) =>
      ((linkClass, preference, passengerType), total)
    }.toMap
  }

  def computeBaseDemandBetweenAirports(fromAirport : Airport, toAirport : Airport, affinity : Int, distance : Int) : Demand = {
    val hasFirstClass = fromAirport.countryCode != toAirport.countryCode && distance >= 1500 || distance >= 3000
    val fromPopIncomeAdjusted = if (fromAirport.popMiddleIncome > 0) fromAirport.popMiddleIncome else 1
    val demand = computeRawDemandBetweenAirports(fromAirport : Airport, toAirport : Airport, affinity : Int, distance : Int)

    //modeling provincial travel dynamics where folks go from small city to big city, but not for tourists
    val maxBonus = 1.5
    val travelerProvincialBonus = if (distance < 2500 && affinity > 1 && toAirport.population > fromPopIncomeAdjusted) {
      math.min(maxBonus, math.pow(toAirport.population / fromPopIncomeAdjusted.toDouble, .1))
    } else {
      1.0
    }
    //lower demand to (boosted) poor places, but not applied on tourists
    val toIncomeAdjust = Math.min(1.0, (toAirport.income.toDouble + 12_000) / 54_000)

    val percentTraveler = Math.min(0.7, fromAirport.income.toDouble / 40_000)

    val demands = Map(PassengerType.TRAVELER -> demand * percentTraveler * travelerProvincialBonus * toIncomeAdjust, PassengerType.BUSINESS -> (demand * (1 - percentTraveler - 0.1) * toIncomeAdjust), PassengerType.TOURIST -> demand * 0.1)

    val featureAdjustedDemands = demands.map { case (passengerType, demand) =>
      val fromAdjustments = fromAirport.getFeatures().map(feature => feature.demandAdjustment(demand, passengerType, fromAirport.id, fromAirport, toAirport, affinity, distance))
      val toAdjustments = toAirport.getFeatures().map(feature => feature.demandAdjustment(demand, passengerType, toAirport.id, fromAirport, toAirport, affinity, distance))
      (passengerType, fromAdjustments.sum + toAdjustments.sum + demand)
    }

    //for each trade affinity, add base "trade demand" to biz demand, modded by distance
    val affinityTradeAdjust = if (distance > 400 && (fromAirport.population >= 30000 || toAirport.population >= 30000)) {
      val baseTradeDemand = 7 + (12 - fromAirport.size.toDouble) * 1.5
      val distanceMod = Math.min(1.0, 5000.0 / distance)
      val matchOnlyTradeAffinities = 5
      (baseTradeDemand * distanceMod * Computation.affinityToSet(fromAirport.zone, toAirport.zone, matchOnlyTradeAffinities).length).toInt
    } else {
      0
    }

    Demand(
      computeClassCompositionFromIncome(featureAdjustedDemands.getOrElse(PassengerType.TRAVELER, 0.0), fromAirport.income, PassengerType.TRAVELER, hasFirstClass),
      computeClassCompositionFromIncome(featureAdjustedDemands.getOrElse(PassengerType.BUSINESS, 0.0) + affinityTradeAdjust, fromAirport.income, PassengerType.BUSINESS, hasFirstClass),
      computeClassCompositionFromIncome(featureAdjustedDemands.getOrElse(PassengerType.TOURIST, 0.0), fromAirport.income, PassengerType.TOURIST, hasFirstClass)
    )
  }

  //adds more demand
  private def addToVeryLowIncome(fromPop: Long, airportScale: Int): Int = {
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


  private def computeRawDemandBetweenAirports(fromAirport : Airport, toAirport : Airport, affinity : Int, distance : Int) : Int = {
    val fromPopIncomeAdjusted = if (fromAirport.popMiddleIncome > 0) fromAirport.popMiddleIncome else 1
    val toPopIncomeAdjusted = 0.6 * toAirport.popMiddleIncome + 0.4 * toAirport.population

    val distanceReducerExponent: Double =
      if (distance < 350 && ! GameConstants.ISOLATED_COUNTRIES.contains(fromAirport.countryCode) && ! GameConstants.isIsland(fromAirport.iata) && ! GameConstants.isIsland(toAirport.iata)) {
        distance.toDouble / 350
      } else if (distance > 4000) {
        0.975 - distance.toDouble / 40000 * (1 - affinity.toDouble / 10.0) * Math.max(5.5 - toAirport.size.toDouble * 0.5, 0) //affinity & scale affects perceived distance
      } else if (distance > 1000) {
        1.05 - distance.toDouble / 10000 * (1 - affinity.toDouble / 10.0) //affinity affects perceived distance
      } else {
        1
      }

    //domestic/foreign/affinity relation multiplier
    val airportAffinityMutliplier: Double =
      if (affinity >= 5) (affinity - 5) * 0.05 + 1 //domestic+
      else if (affinity < 0) 0.025
      else affinity * 0.1 + 0.025

    val specialCountryModifier =
      if (fromAirport.countryCode == "AU" || fromAirport.countryCode == "NZ") {
        if (fromAirport.countryCode == toAirport.countryCode) {
          12.0 //they travel a lot; difficult to model
        } else {
          8.0
        }
      } else if (fromAirport.countryCode == "ZA") {
        if (fromAirport.countryCode != toAirport.countryCode) {
          5.0
        } else {
          9.0 //much southern africa & int'l
        }
      } else if (fromAirport.countryCode == "NO" && toAirport.countryCode == "NO") {
        6.5 //very busy domestic routes
      } else if (List("BZ", "CY", "FO", "GL", "GU", "GR", "IS").contains(fromAirport.countryCode)) {
        2.25 //very high per capita flights https://ourworldindata.org/grapher/air-trips-per-capita
      } else if (List("AE", "BS", "CH", "CL", "DK", "FJ", "GB", "KR", "IE", "LU", "QA", "SE").contains(fromAirport.countryCode)) {
        1.5 // high per capita flights
      } else if (List("CD", "CG", "CV", "CI", "GN", "GW", "LR", "ML", "MR", "NE", "SD", "SO", "SS", "TD", "TG").contains(fromAirport.countryCode)) {
        4.0 //very poor roads but unstable governance
      } else if (List("AO", "BI", "BJ", "BW", "CM", "CV", "DJ", "ET", "GA", "GH", "GM", "GQ", "KE", "KM", "LS", "MG", "MU", "MW", "MZ", "NA", "NG", "RW", "SC", "SL", "SN", "ST", "SZ", "TZ", "UG", "ZA", "ZM", "ZW").contains(fromAirport.countryCode)) {
        6.0 //very poor roads
      } else if (fromAirport.countryCode == "IN" && toAirport.countryCode == "IN") {
        0.67 //pops are just very large
      } else if (fromAirport.countryCode == "CN") {
        (distance.toDouble / 1600) * 0.65 //China has a very extensive highspeed rail network, pops are just very large
      } else if (fromAirport.countryCode == "JP" && toAirport.countryCode == "JP") {
        if (distance < 500) {
          0.1 //HSR / rail
        } else {
          2.25 //but otherwise quite high per captia flights
        }
      } else if (List("FR","IT","ES","NL","BE").contains(fromAirport.countryCode) && distance < 700 && toAirport.countryCode != "GB" && ! GameConstants.isIsland(fromAirport.iata) && ! GameConstants.isIsland(toAirport.iata)) {
        0.2 //EU rail & climate demand mod
      } else if (distance < 260 && fromAirport.zone.contains("EU")) {
        0.6
      } else 1.0

    //set very low income floor, specifically traffic to/from central airports that is otherwise missing
    val buffLowIncomeAirports = if (fromAirport.income <= 6000 && toAirport.income <= 8000 && distance <= 3000 && (toAirport.size >= 4 || fromAirport.size >= 4)) addToVeryLowIncome(fromAirport.population, fromAirport.size) else 0

    //always have some demand between gateways and very large airports to all other nearby domestic airports
    val domesticDemandFloor = if (distance > 300 && distance < 1800 && affinity >= 5 && ( toAirport.isGateway() || toAirport.size - fromAirport.size >= 6)) {
      20 + ThreadLocalRandom.current().nextInt(40)
    } else {
      0
    }

    val baseDemand : Double = Math.max(domesticDemandFloor, baseLaunchDemandFactor * specialCountryModifier * airportAffinityMutliplier * fromPopIncomeAdjusted * toPopIncomeAdjusted / 250_000 / 250_000) + buffLowIncomeAirports
    Math.pow(baseDemand, Math.max(0, distanceReducerExponent)).toInt
  }

  private def computeClassCompositionFromIncome(demand: Double, income: Int, passengerType: PassengerType.Value, hasFirstClass: Boolean) : LinkClassValues = {
    val firstClassDemand = if (hasFirstClass) {
        if (income > FIRST_CLASS_INCOME_MAX) {
          demand * FIRST_CLASS_PERCENTAGE_MAX(passengerType)
        } else {
          demand * FIRST_CLASS_PERCENTAGE_MAX(passengerType) * income.toDouble / FIRST_CLASS_INCOME_MAX
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
    val businessClassCutoff = if (businessClassDemand > 2) businessClassDemand else 0
    val discountClassCutoff = if (discountClassDemand > 15) discountClassDemand else 0

    val economyClassDemand = Math.max(0, demand - firstClassDemand - businessClassCutoff - discountClassCutoff)
    LinkClassValues.getInstance(economyClassDemand.toInt, businessClassCutoff.toInt, firstClassCutoff.toInt, discountClassCutoff.toInt)
  }

  val ELITE_MIN_GROUP_SIZE = 5
  val ELITE_MAX_GROUP_SIZE = 9
  val CLOSE_DESTINATIONS_RADIUS = 1800

  private def generateEliteDemand(fromAirport : Airport, destinationList : List[Destination] ) : Option[List[(Airport, (PassengerType.Value, LinkClassValues))]] = {
//    val eliteDemands = new ArrayList[(Airport, List[(Airport, (PassengerType.Value, LinkClassValues))])]()

    if (fromAirport.popElite > 0) {
      val demandList = new java.util.ArrayList[(Airport, (PassengerType.Value, LinkClassValues))]()
      val groupSize = ThreadLocalRandom.current().nextInt(ELITE_MIN_GROUP_SIZE, ELITE_MAX_GROUP_SIZE)
      val closeDestinations = destinationList.filter { destination =>
        val distance = Computation.calculateDistance(fromAirport, destination.airport)
        distance >= 100 && distance <= CLOSE_DESTINATIONS_RADIUS
      }
      val farAwayDestinations = destinationList.filter { destination =>
        val distance = Computation.calculateDistance(fromAirport, destination.airport)
        distance > CLOSE_DESTINATIONS_RADIUS
      }

      var numberDestinations = Math.ceil(launchDemandFactor * 0.75 * fromAirport.popElite / groupSize.toDouble).toInt

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

  def generateEventDemand(cycle : Int, airports : List[Airport]) : List[(Airport, List[(Airport, (PassengerType.Value, LinkClassValues))])] = {
    val eventDemand = ListBuffer[(Airport, List[(Airport, (PassengerType.Value, LinkClassValues))])]()
    EventSource.loadEvents().filter(_.isActive(cycle)).foreach { event =>
      event match {
        case olympics : Olympics => eventDemand.appendAll(generateOlympicsDemand(cycle, olympics, airports))
        case _ => //
      }

    }
    eventDemand.toList
  }


  val OLYMPICS_DEMAND_BASE = 50000
  def generateOlympicsDemand(cycle: Int, olympics : Olympics, airports : List[Airport]) : List[(Airport, List[(Airport, (PassengerType.Value, LinkClassValues))])]  = {
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

  def generateOlympicsDemand(cycle: Int, demandMultiplier : Int, olympicsAirports : List[Airport], allAirports : List[Airport]) : List[(Airport, List[(Airport, (PassengerType.Value, LinkClassValues))])]  = {
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
        val computedDemand = computeClassCompositionFromIncome(baseDemand, fromAirport.income, PassengerType.OLYMPICS, true)
          if (computedDemand.total > 1 + demandRandomizer) {
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
        appealPreference(homeAirport, FIRST, 1.0, loungeLevelRequired = 4, loyaltyRatio = 1.15, weight = 1),
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

  sealed case class Demand(travelerDemand: LinkClassValues, businessDemand : LinkClassValues, touristDemand : LinkClassValues)
  def addUpDemands(demand: Demand): Int = {
    (demand.travelerDemand.totalwithSeatSize + demand.businessDemand.totalwithSeatSize + demand.touristDemand.totalwithSeatSize).toInt
  }
}
