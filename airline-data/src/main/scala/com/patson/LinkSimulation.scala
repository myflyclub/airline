package com.patson

import com.patson.PassengerSimulation.PassengerConsumptionResult
import com.patson.model._
import com.patson.data._

import scala.collection.mutable._
import scala.collection.{immutable, mutable}
import com.patson.model.airplane.{Airplane, AirplaneMaintenanceUtil, LinkAssignments, Model}
import com.patson.model.alliance.AllianceStats
import com.patson.model.event.Olympics

import scala.util.Random
import com.patson.model.oil.OilPrice
import com.patson.util.{AirportCache, AllianceCache}

import java.util.concurrent.ThreadLocalRandom

object LinkSimulation {
  val FUEL_UNIT_COST = OilPrice.DEFAULT_UNIT_COST * 98 //for easier flight monitoring, let's make it the default unit price here
  val FUEL_DISTANCE_EXPONENT = 1.4
  val FUEL_EMPTY_AIRCRAFT_BURN_PERCENT = 0.7
  val CREW_UNIT_COST = 6.75
  val CREW_BASE_COST = 50
  val CREW_EQ_EXPONENT = 1.95


  def linkSimulation(cycle: Int) : (List[LinkConsumptionDetails], List[LoungeConsumptionDetails], immutable.Map[(PassengerGroup, Airport, Route), Int], immutable.Map[Int, AirlinePaxStat]) = {
    var startTime = System.currentTimeMillis()

    println("Loading all links")
    val links = LinkSource.loadAllLinks(LinkSource.FULL_LOAD)
    val flightLinks = links.filter(_.transportType == TransportType.FLIGHT).map(_.asInstanceOf[Link])
    println("Finished loading all links")

    val allAirportStats = AirportStatisticsSource.loadAllAirportStats()
    val airportStatsLookup: immutable.Map[Int, AirportStatistics] = allAirportStats.map(stats => stats.airportId -> stats).toMap

    val demand = DemandGenerator.computeDemand(cycle, airportStatsLookup)
    println("DONE with demand total demand: " + demand.foldLeft(0) {
      case(holder, (_, _, demandValue)) =>  
        holder + demandValue
    })

    val airportWeather = links.flatMap(link => mutable.Seq(link.from, link.to)).toSet.map { airport: Airport =>
      airport.id -> ThreadLocalRandom.current().nextDouble()
    }.toMap //weather to make the RNG more brown-noise, todo: should be exposed to players
    simulateLinkError(flightLinks, airportStatsLookup, airportWeather)
    
    val PassengerConsumptionResult(consumptionResult: scala.collection.immutable.Map[(PassengerGroup, Airport, Route), Int], missedPassengerResult: immutable.Map[(PassengerGroup, Airport), Int], worldStats: WorldStatistics) = PassengerSimulation.passengerConsume(demand, links)

    println("Tallying airline & stats")
    startTime = System.currentTimeMillis()
    val airlineStats = tallyPassengerTypesByAirline(consumptionResult)
    Util.outputTimeDiff(startTime, "Tallying took ")

    println("Tallying flight, airport, country stats")
    startTime = System.currentTimeMillis()

    val flightMovementsByAirport = flightLinks.foldLeft(mutable.Map[Airport, Int]()) { (acc, link) =>
      // Add movements for departure airport (from)
      acc.updateWith(link.from) {
        case Some(existing) => Some(existing + link.frequency)
        case None => Some(link.frequency)
      }
      // Add movements for arrival airport (to)
      acc.updateWith(link.to) {
        case Some(existing) => Some(existing + link.frequency)
        case None => Some(link.frequency)
      }
      acc
    }.toList

    val (linkStatistics, updatedAirportStatistics, countryMarketShares) = generateFlightStatistics(consumptionResult, cycle, flightMovementsByAirport, airportStatsLookup, worldStats)
    Util.outputTimeDiff(startTime, "Tallying took ")

    println("Saving generated stats to DB")
    startTime = System.currentTimeMillis()
    LinkStatisticsSource.deleteLinkStatisticsBeforeCycle(cycle - 2) //was cycle - 5
    LinkStatisticsSource.saveLinkStatistics(linkStatistics)
    AirportStatisticsSource.updateAllAirportStats(updatedAirportStatistics)
    Util.outputTimeDiff(startTime, "Saved all stats. Took ")

    //save country market share (now generated in the same loop)
    println("Saving country market share to DB")
    CountrySource.saveMarketShares(countryMarketShares)

    //generate Olympics stats
    EventSource.loadEvents().filter(_.isActive(cycle)).foreach {
      case olympics: Olympics =>
        println("Generating Olympics stats")
        val olympicsConsumptions = consumptionResult.filter {
          case ((passengerGroup, _, _), _) => passengerGroup.passengerType == PassengerType.OLYMPICS
        }
        val missedOlympicsPassengers = missedPassengerResult.filter {
          case ((passengerGroup, _), _) => passengerGroup.passengerType == PassengerType.OLYMPICS
        }
        val olympicsCountryStats = generateOlympicsCountryStats(cycle, olympicsConsumptions, missedOlympicsPassengers)
        EventSource.saveOlympicsCountryStats(olympics.id, olympicsCountryStats)
        val olympicsAirlineStats = generateOlympicsAirlineStats(cycle, olympicsConsumptions)
        EventSource.saveOlympicsAirlineStats(olympics.id, olympicsAirlineStats)
        println("Generated olympics country stats")
      case _ => //
    }

    //save all consumptions
    startTime = System.currentTimeMillis()
    println("Saving " + consumptionResult.size +  " consumptions")
    ConsumptionHistorySource.updateConsumptions(consumptionResult)
    Util.outputTimeDiff(startTime, "Saved all consumptions. Took ")

    //save top 10 missed demand per origin airport
    startTime = System.currentTimeMillis()
    println("Saving missed demand snapshot")
    val topMissed = missedPassengerResult
      .groupBy { case ((pg, _), _) => pg.fromAirport.id }
      .flatMap { case (fromId, entries) =>
        // Aggregate by composite key first — multiple PassengerGroup objects can share
        // the same (from, to, passengerType, preferenceType, linkClass) but differ in
        // internal preference details, producing duplicate DB keys.
        val aggregated = mutable.HashMap[(Int, Int, Int, Int, String), Int]()
        entries.foreach { case ((pg, toAirport), count) =>
          val key = (fromId, toAirport.id, pg.passengerType.id,
            pg.preference.getPreferenceType.id, pg.preference.preferredLinkClass.code)
          aggregated(key) = aggregated.getOrElse(key, 0) + count
        }
        aggregated.toList.sortBy(-_._2).take(10).map {
          case ((fId, tId, paxType, prefType, linkClass), count) =>
            MissedDemandEntry(fId, tId, paxType, prefType, linkClass, count)
        }
      }
    MissedDemandSource.deleteAndSave(topMissed)
    Util.outputTimeDiff(startTime, "Saved missed demand. Took ")

    println("Calculating profits by links")
    startTime = System.currentTimeMillis()
    val linkConsumptionDetails = ListBuffer[LinkConsumptionDetails]()
    val loungeConsumptionDetails = ListBuffer[LoungeConsumptionDetails]()
    val allAirplaneAssignments: immutable.Map[Int, LinkAssignments] = AirplaneSource.loadAirplaneLinkAssignmentsByCriteria(List.empty)
    //cost by link
    val costByLink = mutable.HashMap[Transport, ListBuffer[PassengerCost]]()
    consumptionResult.foreach {
      case((passengerGroup, airport, route), passengerCount) => route.links.foreach { linkConsideration =>
        costByLink.getOrElseUpdate(linkConsideration.link, ListBuffer[PassengerCost]()).append(PassengerCost(passengerGroup, passengerCount, linkConsideration.cost))
      }
    }

    links.foreach {
      case flightLink : Link =>
        if (flightLink.capacity.total > 0) {
          val (linkResult, loungeResult) = computeLinkAndLoungeConsumptionDetail(flightLink, cycle, allAirplaneAssignments, costByLink.getOrElse(flightLink, List.empty).toList)
          linkConsumptionDetails += linkResult
          loungeConsumptionDetails ++= loungeResult
        }
      case nonFlightLink => //only compute for flights (class Link), need consumptions for transit modal
        linkConsumptionDetails += LinkConsumptionDetails(nonFlightLink, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, cycle)
    }

    purgeAlerts()
    checkLoadFactor(flightLinks, cycle)

    LinkSource.deleteLinkConsumptionsByCycle(300)
    LinkSource.saveLinkConsumptions(linkConsumptionDetails.toList)

    println("Calculating Lounge usage")
    val loungeResult: List[LoungeConsumptionDetails] = loungeConsumptionDetails.groupBy(_.lounge).map{
      case (lounge, consumptionsForThisLounge) =>
        var totalSelfVisitors = 0
        var totalAllianceVisitors = 0
        consumptionsForThisLounge.foreach {
          case LoungeConsumptionDetails(_, selfVisitors, allianceVisitors, _) =>
            totalSelfVisitors += selfVisitors
            totalAllianceVisitors += allianceVisitors
        }
        LoungeConsumptionDetails(lounge, totalSelfVisitors, totalAllianceVisitors, cycle)
    }.toList

    LoungeHistorySource.updateConsumptions(loungeResult)
    LoungeHistorySource.deleteConsumptionsBeforeCycle(cycle)
    Util.outputTimeDiff(startTime, "Finished calculation on profits by links & lounges. Took ")

    (linkConsumptionDetails.toList, loungeResult, consumptionResult, airlineStats)
  }

  case class PassengerCost(group : PassengerGroup, passengerCount : Int, cost : Double)


  /**
   * Cancelled flights / delays
   * - cancellations are calculated before booking (pathfinding is the final journey, maybe not what was originally booked).
   * - lower load factor reduces cancellation costs as we assume passengers were rebooked onto different flight
   * - delays primarily influence satisfaction ... cancellations are very expensive
   */
  val DELAY_MAJOR_FEE = 0.2
  val TOOLTIP_DELAYS = List(
    "Minor delays lower satisfaction but have no direct financial penalty.",
    s"Major delays not only lower satisfaction but airlines must pay ${DELAY_MAJOR_FEE * 100}% of ticket price in fees.",
    "Cancelled flights provide no revenue, and airlines must pay the ticket cost to rebook the customer on another airline, unless there was remaining capacity to rebook. If cancelled flights are likely, having excess capacity may be prudent.",
    "Aircraft age, modified by base specializations like hangars, and airport congestion are the two equal factors for causing delays and flight cancellations. Weather is randomly generated at each airport each cycle which may cause more delays when confounded with congestion and old aircraft."
  )

  def simulateLinkError(links: List[Link], allAirportStats: immutable.Map[Int, AirportStatistics], airportWeather: immutable.Map[Int, Double]): Unit = {
    links.foreach {
      link => {
        val flights: List[Airplane] = link.getAssignedAirplanes().filter(_._1.isReady).flatMap {
          case (airplane, assignment) => List.fill(assignment.frequency)(airplane)
        }.toList

        if (flights.nonEmpty) {
          val congestionFromAirport = allAirportStats.get(link.from.id).map(_.congestion).getOrElse(0.2) * 70 + airportWeather.getOrElse(link.from.id, 0.2) * 3
          val congestionToAirport = allAirportStats.get(link.to.id).map(_.congestion).getOrElse(0.2) * 70 + airportWeather.getOrElse(link.to.id, 0.2) * 3

          val hangarCountFrom = link.from.getAirlineBase(link.airline.id).map(_.specializations.count(_.getType == BaseSpecializationType.HANGAR)).getOrElse(0)
          val hangarCountTo = link.to.getAirlineBase(link.airline.id).map(_.specializations.count(_.getType == BaseSpecializationType.HANGAR)).getOrElse(0)

          flights.foreach { airplane =>
            val airplaneDecay = 100 - Math.min(110, (hangarCountTo + hangarCountFrom) * 5 + airplane.condition) //hangars improve over 100 to 110

            val riskScore = airplaneDecay + congestionFromAirport + congestionToAirport // riskScore is 0-300; higher is bad

            // 1. Calculate standard base rates using the total risk score
            val baseScaledRisk = riskScore / 100.0
            var minorDelayChance = 0.19 * baseScaledRisk
            var majorDelayChance = 0.01 * baseScaledRisk
            var cancellationChance = 0.0

            // 2. Isolate ONLY the values that are over the moderate thresholds
            val moderateExcess =
              Math.max(0.0, airplaneDecay - (100 - Airplane.BAD_CONDITION)) +
              Math.max(0.0, congestionFromAirport - Airport.CONGESTION_MODERATE) +
              Math.max(0.0, congestionToAirport - Airport.CONGESTION_MODERATE)

            // 3. Isolate ONLY the values that are over the critical thresholds
            val criticalExcess =
              Math.max(0.0, airplaneDecay - (100 - Airplane.CRITICAL_CONDITION)) +
              Math.max(0.0, congestionFromAirport - Airport.CONGESTION_HIGH) +
              Math.max(0.0, congestionToAirport - Airport.CONGESTION_HIGH)

            // 4. Apply penalty multipliers strictly to the excess amounts
            minorDelayChance += (0.1 * ((moderateExcess + criticalExcess) / 100.0))
            majorDelayChance += (0.1 * (moderateExcess / 100.0)) + (0.15 * (criticalExcess / 100.0))
            cancellationChance += (0.01 * (moderateExcess / 100.0)) + (0.06 * (criticalExcess / 100.0))

            // 5. Cap maximums to prevent guaranteed failures
            minorDelayChance = Math.min(0.8, minorDelayChance)
            majorDelayChance = Math.min(0.4, majorDelayChance)
            cancellationChance = Math.min(0.2, cancellationChance)

            val rand = scala.util.Random.nextDouble()

            if (rand < cancellationChance) {
              link.cancellationCount += 1
            } else if (rand < cancellationChance + majorDelayChance) {
              link.majorDelayCount += 1
            } else if (rand < cancellationChance + majorDelayChance + minorDelayChance) {
              link.minorDelayCount += 1
            }
          }
        }
      }
    }

    links.foreach { link =>
      if (link.cancellationCount > 0) {
        link.addCancelledSeats(link.capacityPerFlight() * link.cancellationCount)
      }
    }
  }

  def computeCompensation(link : Link) : Int = {
    if (link.majorDelayCount > 0 || link.cancellationCount > 0 ) {
      val howRebookable = if (link.availableSeats.total == 0 || link.frequency <= 14) 1 else Math.min(1, (2.0 * link.cancelledSeats.total) / link.availableSeats.total) //if passengers can be rebooked, there is no added cost
      val soldSeatsPerFlight = link.soldSeats / link.frequency

      var compensation = (link.cancelledSeats * link.price * howRebookable).total  //100% of ticket price, because have to rebook
      compensation = compensation + (soldSeatsPerFlight * link.majorDelayCount * link.price * DELAY_MAJOR_FEE).total
      compensation
    } else {
      0
    }
  }

  /**
    * Only called by test cases
    * @param link
    * @param cycle
    * @return
    */
  def computeFlightLinkConsumptionDetail(link : Link, cycle : Int) : LinkConsumptionDetails = {
    //for testing, assuming all airplanes are only assigned to this link
    val assignmentsToThis = link.getAssignedAirplanes().filter(_._1.isReady).toList.map {
      case(airplane, assignment) => (airplane.id, LinkAssignments(immutable.Map(link.id -> assignment)))
    }.toMap
    computeLinkAndLoungeConsumptionDetail(link, cycle, assignmentsToThis, List.empty)._1
  }

  /**
    * Calculate fuel cost for a flight
    * @param model The airplane model
    * @param distance The flight distance in km
    * @param soldSeats The number of sold seats
    * @param capacity The total capacity
    * @param frequency The flight frequency
    * @param cancellationCount The number of cancellations
    * @return The total fuel cost
    */
  def calculateFuelCost(model: Model, distance: Int, soldSeats: Int, capacity: Double, frequency: Int, cancellationCount: Int = 0): Int = {
    val loadFactor = FUEL_EMPTY_AIRCRAFT_BURN_PERCENT + (1 - FUEL_EMPTY_AIRCRAFT_BURN_PERCENT) * soldSeats.toDouble / capacity
    val distanceFactor = 1 + 0.1 * Math.pow(distance.toDouble / 800, FUEL_DISTANCE_EXPONENT * loadFactor)
    val fuelCostPerRoundTrip = FUEL_UNIT_COST * model.capacity * distanceFactor * (model.ascentBurn * loadFactor + model.cruiseBurn * distance.toDouble / 800)

    (fuelCostPerRoundTrip * (frequency - cancellationCount)).toInt
  }

  def computeLinkAndLoungeConsumptionDetail(link: Link, cycle: Int, allAirplaneAssignments: immutable.Map[Int, LinkAssignments], passengerCostEntries: List[PassengerCost]): (LinkConsumptionDetails, List[LoungeConsumptionDetails]) = {
    val fuelCost = link.getAssignedModel() match {
      case Some(model) =>
        calculateFuelCost(model, link.distance, link.getTotalSoldSeats, link.capacity.totalwithSeatSize, link.frequency, link.cancellationCount)
      case None => 0
    }

    val fuelTaxRate = AirlineGrades.findTaxRate(link.airline.getReputation())
    val fuelTax = (fuelCost * (fuelTaxRate.toDouble / 100)).toInt

    val inServiceAssignedAirplanes = link.getAssignedAirplanes().filter(_._1.isReady)
    //the % of time spent on this link for each airplane
    val assignmentWeights : immutable.Map[Airplane, Double] = { //0 to 1
      inServiceAssignedAirplanes.view.map {
        case(airplane, assignment) =>
          allAirplaneAssignments.get(airplane.id) match {
            case Some(linkAssignmentsToThisAirplane) =>
              val weight : Double = assignment.flightMinutes.toDouble / linkAssignmentsToThisAirplane.assignments.values.map(_.flightMinutes).sum
              (airplane, weight)
            case None => (airplane, 1.0) //100%
          } //it shouldn't be else...but just to play safe, if it's not found in "all" table, assume this is the only link assigned
      }.toMap
    }
    var maintenanceCost = 0
    inServiceAssignedAirplanes.foreach {
      case(airplane, _) =>
        maintenanceCost += (airplane.model.baseMaintenanceCost * assignmentWeights(airplane)).toInt
    }
    maintenanceCost = (maintenanceCost * AirplaneMaintenanceUtil.getMaintenanceFactor(link.airline.id)).toInt


    val airportFees = link.getAssignedModel() match {
      case Some(model) =>
        val airline = link.airline
        (link.from.slotFee(model, airline) + link.to.slotFee(model, airline)) * link.frequency + link.from.landingFee(link.getTotalSoldSeats) + link.to.landingFee(link.getTotalSoldSeats)
      case None => 0
    }

    val depreciation = inServiceAssignedAirplanes.map {
      case (airplane, _) =>
        val depreciableBase = airplane.model.price - airplane.model.price * Airplane.SALVAGE_VALUE_PERCENT
        val standardRate = depreciableBase / airplane.model.lifespan.toDouble
        (standardRate * assignmentWeights(airplane)).toInt
    }.sum

    val targetQualityCost = Math.pow(link.airline.getTargetServiceQuality().toDouble / 22, CREW_EQ_EXPONENT)
    var crewCost = CREW_BASE_COST
    var inflightCost, revenue = 0
    val crewUnitCost = if (link.airline.airlineType == DiscountAirline || link.airline.airlineType == BeginnerAirline) CREW_UNIT_COST * DiscountAirline.crewRatio else CREW_UNIT_COST
    LinkClass.values.foreach { linkClass =>
      val capacity = link.capacity(linkClass)
      val soldSeats = link.soldSeats(linkClass)

      inflightCost += computeInflightCost(linkClass.resourceMultiplier, link, soldSeats)
      crewCost += (targetQualityCost * capacity * linkClass.resourceMultiplier * link.duration / 60).toInt + (crewUnitCost * capacity * linkClass.resourceMultiplier * link.duration / 60).toInt
      revenue += soldSeats * link.price(linkClass)
    }

    // delays incur extra cost
    val delayCompensation = computeCompensation(link)

    // lounge cost
    val fromLounge = link.from.getLounge(link.airline.id, link.airline.getAllianceId(), activeOnly = true)
    val toLounge = link.to.getLounge(link.airline.id, link.airline.getAllianceId(), activeOnly = true)
    var loungeCost = 0
    val loungeConsumptionDetails = ListBuffer[LoungeConsumptionDetails]()
    if (fromLounge.isDefined || toLounge.isDefined) {
      val visitorCount = link.soldSeats(BUSINESS) + link.soldSeats(FIRST)
      if (fromLounge.isDefined) {
        loungeCost += visitorCount * Lounge.PER_VISITOR_CHARGE
        loungeConsumptionDetails += (
          if (fromLounge.get.airline.id == link.airline.id) {
            LoungeConsumptionDetails(fromLounge.get, selfVisitors = visitorCount, allianceVisitors = 0, cycle)
          } else {
            LoungeConsumptionDetails(fromLounge.get, selfVisitors = 0, allianceVisitors = visitorCount, cycle)
          })
      }
      if (toLounge.isDefined) {
        loungeCost += visitorCount * Lounge.PER_VISITOR_CHARGE
        loungeConsumptionDetails += (
          if (toLounge.get.airline.id == link.airline.id) {
            LoungeConsumptionDetails(toLounge.get, selfVisitors = visitorCount, allianceVisitors = 0, cycle)
          } else {
            LoungeConsumptionDetails(toLounge.get, selfVisitors = 0, allianceVisitors = visitorCount, cycle)
          })
      }

    }

    val profit = revenue - fuelCost - fuelTax - maintenanceCost - crewCost - airportFees - inflightCost - delayCompensation - depreciation - loungeCost

    //calculation overall satisfaction
    var satisfactionTotalValue : Double = 0
    var totalPassengerCount = 0
    passengerCostEntries.foreach {
      case PassengerCost(passengerGroup, passengerCount, cost) =>
        val preferredLinkClass = passengerGroup.preference.preferredLinkClass
        val standardPrice = link.standardPrice(preferredLinkClass, passengerGroup.passengerType)
        val satisfaction = Computation.computePassengerSatisfaction(cost, standardPrice, link.getLoadFactor, link.getDelayRatio)
        satisfactionTotalValue += satisfaction * passengerCount
        totalPassengerCount += passengerCount
    }
    val overallSatisfaction = if (totalPassengerCount == 0) 0 else satisfactionTotalValue / totalPassengerCount

    val result = LinkConsumptionDetails(link, fuelCost, fuelTax, crewCost, airportFees, inflightCost, delayCompensation = delayCompensation, maintenanceCost, depreciation = depreciation, loungeCost = loungeCost, revenue, profit, overallSatisfaction, cycle)
    (result, loungeConsumptionDetails.toList)
  }

  //"service supplies"
  val computeInflightCost = (classMultiplier : Double, link : Link, soldSeats : Int) => {
    val durationCostPerHour: Double =
      if (link.rawQuality <= 20) {
        -5 //selling food & credit cards :)
      } else if (link.rawQuality <= 40) {
        -1
      } else if (link.rawQuality <= 60) {
        4
      } else if (link.rawQuality <= 80) {
        9
      } else {
        15
      }
    val airlineTypeMultipler = link.airline.airlineType match {
      case BeginnerAirline => 0.7
      case _ => 1.0
    }

    val costPerPassenger = classMultiplier * durationCostPerHour * airlineTypeMultipler * link.duration.toDouble / 60
    (costPerPassenger * soldSeats).toInt
  }

  val LOAD_FACTOR_ALERT_LINK_COUNT_THRESHOLD = 3 //how many airlines before load factor is checked
  val LOAD_FACTOR_ALERT_THRESHOLD = 0.5 //LF threshold
  val LOAD_FACTOR_ALERT_DURATION = 52

  /**
    * Purge alerts that are no longer valid
    */
  def purgeAlerts() = {
    //only purge link cancellation alerts for now
    val existingAlerts = AlertSource.loadAlertsByCategory(AlertCategory.LINK_CANCELLATION)

    //try to purge the alerts, as some alerts might get inserted while the link is deleted during the simulation time
    val liveLinkIds : List[Int] = LinkSource.loadAllLinks(LinkSource.ID_LOAD).map(_.id)
    val deadAlerts = existingAlerts.filter(alert => alert.targetId.isDefined && !liveLinkIds.contains(alert.targetId.get))
    AlertSource.deleteAlerts(deadAlerts)
    println("Purged alerts with no corresponding links... " + deadAlerts.size)
  }

  def checkLoadFactor(links : List[Link], cycle : Int) = {
    val existingAlerts = AlertSource.loadAlertsByCategory(AlertCategory.LINK_CANCELLATION)

    //group links by from and to airport ID Tuple(id1, id2), smaller ID goes first in the tuple
    val linksByAirportIds = links.filter(_.capacity.total > 0).filter(_.airline.airlineType != NonPlayerAirline).groupBy( link =>
      if (link.from.id < link.to.id) (link.from.id, link.to.id) else (link.to.id, link.from.id)
    )

    val existingAlertsByLinkId : scala.collection.immutable.Map[Int, Alert] = existingAlerts.map(alert => (alert.targetId.get, alert)).toMap

    val updatingAlerts = ListBuffer[Alert]()
    val newAlerts = ListBuffer[Alert]()
    val deletingAlerts = ListBuffer[Alert]()
    val deletingLinks = ListBuffer[Link]()
    val newLogs = ListBuffer[Log]()

    linksByAirportIds.foreach {
      case((airportId1, airportId2), links) =>
        if (links.size >= LOAD_FACTOR_ALERT_LINK_COUNT_THRESHOLD) {
          links.foreach { link =>
            val loadFactor = link.getTotalSoldSeats.toDouble / link.getTotalCapacity
            if (loadFactor < LOAD_FACTOR_ALERT_THRESHOLD) {
              existingAlertsByLinkId.get(link.id) match {
                case Some(existingAlert) => //continue to have problem
                  if (existingAlert.duration <= 1) { //kaboom! deleting
                    deletingAlerts.append(existingAlert)
                    deletingLinks.append(link)
                    val message = "Airport authorities have revoked license of " + link.airline.name + " to operate route between " +  link.from.displayText + " and " + link.to.displayText + " due to prolonged low load factor"
                    newLogs += Log(airline = link.airline, message = message, category = LogCategory.LINK, severity = LogSeverity.WARN, cycle = cycle)
                    //notify competitors too with lower severity
                    links.filter(_.id != link.id).foreach { competitorLink =>
                      newLogs += Log(airline = competitorLink.airline, message = message, category = LogCategory.LINK, severity = LogSeverity.INFO, cycle = cycle)
                    }
                  } else { //clock is ticking!
                     updatingAlerts.append(existingAlert.copy(duration = existingAlert.duration -1))
                  }
                case None => //new warning
                  val message = "Airport authorities have issued warning to " + link.airline.name + " on low load factor of route between " +  link.from.displayText + " and " + link.to.displayText + ". If the load factor remains lower than " + LOAD_FACTOR_ALERT_THRESHOLD * 100 + "% for the remaining duration, the license to operate this route will be revoked!"
                  val alert = Alert(airline = link.airline, message = message, category = AlertCategory.LINK_CANCELLATION, targetId = Some(link.id), cycle = cycle, duration = LOAD_FACTOR_ALERT_DURATION)
                  newAlerts.append(alert)
              }
            } else { //LF good, delete existing alert if any
              existingAlertsByLinkId.get(link.id).foreach { existingAlert =>
                deletingAlerts.append(existingAlert)
              }
            }
          }
        } else { //not enough competitor, check if alert should be removed
          links.foreach { link =>
            existingAlertsByLinkId.get(link.id).foreach { existingAlert =>
              deletingAlerts.append(existingAlert)
            }
          }
        }
    }


    deletingLinks.foreach { link =>
       println("Revoked link: " + link)
       LinkSource.deleteLink(link.id)
    }
    AlertSource.updateAlerts(updatingAlerts.toList)
    AlertSource.insertAlerts(newAlerts.toList)
    AlertSource.deleteAlerts(deletingAlerts.toList)

    LogSource.insertLogs(newLogs.toList)
  }

  def generateFlightStatistics(consumptionResult: immutable.Map[(PassengerGroup, Airport, Route), Int], cycle: Int, flightMovementsByAirport: List[(Airport, Int)], airportStatsLookup: immutable.Map[Int, AirportStatistics], worldStats: WorldStatistics): (List[LinkStatistics], List[AirportStatisticsUpdate], List[CountryMarketShare]) = {
    val airportCongestion: immutable.Map[Int, Double] = flightMovementsByAirport.map {
      case (airport, movements) =>
        airport.id -> BigDecimal(movements.toDouble / (1000 + airport.size * 950)).setScale(3, BigDecimal.RoundingMode.HALF_UP).toDouble
    }.toMap

    val flightStatsBuilder = mutable.Map.empty[LinkStatisticsKey, (Int, Int)]
    val airportStatsBuilder = mutable.Map.empty[Airport, (Int, Int)]
    val countryStatsBuilder = mutable.Map.empty[String, mutable.Map[Int, Long]]

    var processedCount = 0
    val totalEntries = consumptionResult.size
    val reportInterval = Math.max(10000, totalEntries / 20) // Report every 5% or at least every 10000 entries

    // Suggest GC if we have a very large dataset
    if (totalEntries > 100000) {
      println(s"Large dataset detected (${totalEntries} entries). Suggesting GC before processing...")
      System.gc()
    }

    consumptionResult.foreach { case ((paxGroup, _, route), passengerCount) =>
      try {
        processedCount += 1
        if (processedCount % reportInterval == 0) {
          println(s"Processed $processedCount/$totalEntries entries (${(processedCount * 100.0 / totalEntries).toInt}%)")
        }

        val isPremium = paxGroup.preference.preferredLinkClass.level > 1
        val premiumCount = if (isPremium) passengerCount else 0

        route.links.zipWithIndex.foreach { case (link, i) =>
          val isFlipped = link.inverted
          val key = createLinkStatisticsKey(link.link, isFlipped, i, route.links.size)

          // Update flight stats
          val (currentPax, currentPremium) = flightStatsBuilder.getOrElse(key, (0, 0))
          flightStatsBuilder.put(key, (currentPax + passengerCount, currentPremium + premiumCount))

          // Update airport stats
          val isDeparture = i == 0
          val (fromPax, allPax) = airportStatsBuilder.getOrElse(link.from, (0, 0))
          val newFromPax = if (isDeparture) fromPax + passengerCount else fromPax
          val newAllPax = if(link.link.transportType == TransportType.FLIGHT) allPax + passengerCount else allPax
          airportStatsBuilder.put(link.from, (newFromPax, newAllPax))

          // Update country stats
          if (link.link.transportType == TransportType.FLIGHT) {
            val airline = link.link.airline
            val country = link.passengerGroup.fromAirport.countryCode
            val airlinePassengers = countryStatsBuilder.getOrElseUpdate(country, mutable.Map[Int, Long]())
            val currentSum : Long = airlinePassengers.getOrElse(airline.id, 0L)
            airlinePassengers.put(airline.id, currentSum + passengerCount)
          }
        }
      } catch {
        case e: Exception =>
          println(s"Error processing entry at index $processedCount: ${e.getMessage}")
      }
    }

    println(s"Finished processing all ${processedCount} entries")

    val (flightStats, airportStats) = (flightStatsBuilder.toMap, airportStatsBuilder.toMap)
    val linkStatistics = flightStats.map { case (key, (pax, premium)) =>
      LinkStatistics(key, pax, premium, cycle)
    }.toList

    println(s"Processing airport stats for ${airportStats.size} airports...")

    val airportStatistics = if (worldStats.totalPax <= 0) {
      List.empty
    } else {
      airportStats.map { case (airport, (fromPax, allPax)) =>
        val airportStats = airportStatsLookup.getOrElse(airport.id, AirportStatistics(airport.id, 100, 100, 0.0, 0.0, 0.0))
        val smallAirportBoost = 2 - (10 - airport.size).toDouble / 10.0
        val localDemandMet = fromPax.toDouble / airportStats.baselineDemand
        val localTravelRate = Airport.travelRateAdjusted(fromPax, airportStats.baselineDemand, airport.size)
        val percentGlobally = allPax.toDouble / worldStats.totalPax
        val rep = Math.max(airport.size, BigDecimal(Math.pow(localTravelRate, smallAirportBoost) * percentGlobally * Airport.GLOBAL_AIRPORT_REPUTATION_POOL).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble)
        AirportStatisticsUpdate(
          airport.id,
          fromPax,
          airportCongestion.getOrElse(airport.id, 0.0),
          rep,
          localDemandMet
        )
      }.toList
    }

    println(s"Finished building ${airportStatistics.size} airport stats")

    // Generate country market shares from the accumulated data
    val countryMarketShares = countryStatsBuilder.map {
      case (countryCode, airlinePassengers) =>
        CountryMarketShare(countryCode, airlinePassengers.toMap)
    }.toList

    (linkStatistics, airportStatistics, countryMarketShares)
  }

  private def createLinkStatisticsKey(link: Transport, isFlipped: Boolean, index: Int, totalLinks: Int): LinkStatisticsKey = {
    val isDeparture = index == 0
    val isDestination = index == totalLinks - 1
    val from = if (!isFlipped) link.from else link.to
    val to = if (!isFlipped) link.to else link.from
    LinkStatisticsKey(from, to, isDeparture, isDestination, link.airline)
  }

  def tallyPassengerTypesByAirline(consumptionResult: scala.collection.immutable.Map[(PassengerGroup, Airport, Route), Int]): immutable.Map[Int, AirlinePaxStat] = {
    val airlineStatsMap = mutable.Map[Int, (Int, Int, Int, Int, Int)]() // (tourist, elite, business, total, allianceRoute)

    // Group by route to avoid double-counting Codeshares
    val routesByPassengerType = consumptionResult.groupBy(_._1._3)

    routesByPassengerType.foreach {
      case (route, passengerTypes) =>
        // Check if this route has alliance partners for each airline
        def hasAlliancePartners(airlineId: Int) = route.links.exists(linkConsideration =>
          linkConsideration.link.transportType == TransportType.FLIGHT &&
            linkConsideration.link.airline.id == airlineId &&
            linkConsideration.link.airline.getAllianceId().isDefined &&
            AllianceCache.isEstablishedAndValid(linkConsideration.link.airline.getAllianceId().get, airlineId) &&
          route.links.exists(otherLink =>
            otherLink.link.transportType == TransportType.FLIGHT &&
            otherLink.link.airline.id != airlineId &&
            otherLink.link.airline.getAllianceId().isDefined &&
            otherLink.link.airline.getAllianceId() == linkConsideration.link.airline.getAllianceId() &&
            AllianceCache.isEstablishedAndValid(linkConsideration.link.airline.getAllianceId().get, airlineId)
          )
        )

        // Process each airline in the route
        route.links.filter(_.link.transportType == TransportType.FLIGHT).foreach { link =>
          val airlineId = link.link.airline.id
          val currentStats = airlineStatsMap.getOrElse(airlineId, (0, 0, 0, 0, 0))
          val codeshares = if (hasAlliancePartners(airlineId)) passengerTypes.values.sum else 0

          // Process each passenger type for this airline
          val newStats = passengerTypes.foldLeft(currentStats) {
            case ((tourist, elite, business, total, alliance), ((passengerGroup, _, _), passengerCount)) =>
              passengerGroup.passengerType match {
                case PassengerType.TOURIST => (tourist + passengerCount, elite, business, total + passengerCount, alliance)
                case PassengerType.ELITE => (tourist, elite + passengerCount, business, total + passengerCount, alliance)
                case PassengerType.BUSINESS => (tourist, elite, business + passengerCount, total + passengerCount, alliance)
                case _ => (tourist, elite, business, total + passengerCount, alliance)
              }
          }

          // Add Codeshares after processing all passenger types
          airlineStatsMap.put(airlineId, (newStats._1, newStats._2, newStats._3, newStats._4, newStats._5 + codeshares))
        }
    }

    airlineStatsMap.map {
    case (airlineId, (tourist, elite, business, total, allianceRoute)) =>
      airlineId -> AirlinePaxStat(tourist, elite, business, total, allianceRoute)
    }.toMap
  }

  case class PassengerTransportStats(cycle : Int, transported : Int, total : Int)
  /**
    * Stats on how much pax from a country was carried/missed
    * @param olympicsConsumptions
    * @param missedOlympicsPassengers
    * @return Map[countryCode, transportRate]
    */
  def generateOlympicsCountryStats(cycle : Int, olympicsConsumptions: immutable.Map[(PassengerGroup, Airport, Route), Int], missedOlympicsPassengers: immutable.Map[(PassengerGroup, Airport), Int]) : immutable.Map[String, PassengerTransportStats] = {
    val passengersByCountry = mutable.HashMap[String, Int]()
    val missedPassengersByCountry = mutable.HashMap[String, Int]()

    val allCountries = mutable.HashSet[String]()
    olympicsConsumptions.foreach {
      case ((passengerGroup, _, _), passengerCount) =>
        val countryCode = passengerGroup.fromAirport.countryCode
        val currentCount = passengersByCountry.getOrElse(countryCode, 0)
        passengersByCountry.put(countryCode, currentCount + passengerCount)
        allCountries.add(countryCode)
    }

    missedOlympicsPassengers.foreach {
      case ((passengerGroup, _), passengerCount) =>
        val countryCode = passengerGroup.fromAirport.countryCode
        val currentCount = missedPassengersByCountry.getOrElse(countryCode, 0)
        missedPassengersByCountry.put(countryCode, currentCount + passengerCount)
        allCountries.add(countryCode)
    }

    allCountries.map { countryCode =>
      val transportStats =
        passengersByCountry.get(countryCode) match {
          case Some(passengers) => missedPassengersByCountry.get(countryCode) match {
            case Some(missedPassengers) => PassengerTransportStats(cycle, passengers, (passengers + missedPassengers))
            case None => PassengerTransportStats(cycle, passengers, passengers)
          }
          case None => PassengerTransportStats(cycle, 0, missedPassengersByCountry.getOrElse(countryCode, 0))
        }
      (countryCode, transportStats)
    }.toMap
  }

  /**
    *
    * @param olympicsConsumptions
    * @return Map[airline, scope] score if 1 if Airline A has direct flight that takes the pax to olympics city, otherwise each airline in the route get 1 / n, which n is the number of hops
    */
  def generateOlympicsAirlineStats(cycle : Int, olympicsConsumptions: immutable.Map[(PassengerGroup, Airport, Route), Int]) : immutable.Map[Airline, (Int, BigDecimal)] = {
    val scoresByAirline = mutable.HashMap[Airline, BigDecimal]()

    olympicsConsumptions.foreach {
      case ((_, _, Route(links, _, _, _)), passengerCount) =>
        links.foreach { link =>
          if (link.link.transportType == TransportType.FLIGHT) {
            val existingScore : BigDecimal = scoresByAirline.getOrElse(link.link.airline, 0)
            scoresByAirline.put(link.link.airline, existingScore + passengerCount.toDouble / links.size)
          }
        }
    }

    scoresByAirline.view.mapValues( score => (cycle, score)).toMap
  }

  /**
    * Refresh link capacity and frequency if necessary
    */
  def refreshLinksPostCycle() = {
    println("Refreshing link capacity and frequency to find discrepancies")
    val simpleLinks = LinkSource.loadAllLinks(LinkSource.ID_LOAD)
    val fullLinks = LinkSource.loadAllLinks(LinkSource.FULL_LOAD).map(link => (link.id, link)).toMap
    println("Finished loading both the simple and full links")
    //not too ideal, but even if someone update the link assignment when this is in progress, it should be okay, as that assignment
    //is suppose to update the link capacity and frequency anyway
    simpleLinks.foreach { simpleLink =>
      fullLinks.get(simpleLink.id).foreach { fullLink =>
        if (simpleLink.frequency != fullLink.frequency || simpleLink.capacity != fullLink.capacity) {
          println(s"Adjusting capacity/frequency of  $simpleLink to $fullLink")
          LinkSource.updateLink(fullLink)
        }
      }
    }
  }

  def simulatePostCycle(cycle : Int) = {
    //now update the link capacity if necessary
    LinkSimulation.refreshLinksPostCycle()
    purgeNegotiationCoolDowns(cycle)
  }

  def purgeNegotiationCoolDowns(cycle: Int): Unit = {
    LinkSource.purgeNegotiationCoolDowns(cycle)
    NegotiationSource.deleteLinkDiscountBeforeExpiry(cycle)
  }
}
