package com.patson

import java.util.{ArrayList, Collections}
import java.util.concurrent.atomic.AtomicInteger
import com.patson.data.{AirlineSource, AirportSource, AllianceSource, CountrySource, CycleSource, LinkSource, WorldStatisticsSource}
import com.patson.model.{TransferSpecialization, _}
import FlightPreferenceType._

import java.util
import scala.collection.mutable
import scala.collection.mutable.{ListBuffer, Set}
import scala.util.Random
import scala.collection.parallel.CollectionConverters._
import scala.jdk.CollectionConverters._

object PassengerSimulation {

  val countryOpenness : Map[String, Int] = CountrySource.loadAllCountries().map( country => (country.countryCode, country.openness)).toMap
  val transferBaseSpecializationDiscounts: Map[(Int, Int), TransferSpecialization] = {
    val transferSpecializations = AirlineBaseSpecialization.byType(BaseSpecializationType.TRANSFER_DISCOUNT).collect { case ts: TransferSpecialization => ts }
    AirportSource.loadAirportBaseSpecializationsByTypes(transferSpecializations)
      .collect { case (airline, airport, spec: TransferSpecialization) => ((airport.id, airline.id), spec) }
      .toMap
  }
  
  case class PassengerConsumptionResult(consumptionByRoutes: Map[(PassengerGroup, Airport, Route), Int], missedDemand: Map[(PassengerGroup, Airport), Int], worldStats: WorldStatistics)

  def passengerConsume[T <: Transport](demand : List[(PassengerGroup, Airport, Int)], links : List[T]) : PassengerConsumptionResult = {
    val consumptionResult = Collections.synchronizedList(new ArrayList[(PassengerGroup, Airport, Int, Route)]())
    val missedDemandChunks = Collections.synchronizedList(new ArrayList[(PassengerGroup, Airport, Int)]())
    val consumptionCycleMax = 9; //try and rebuild routes 10 times
    var consumptionCycleCount = 0;

    //find all active Airports
    val activeAirportIds = Set[Int]()
    val activeAirlineIds = Set[Int]()
    links.foreach { link =>
      activeAirportIds.add(link.from.id)
      activeAirportIds.add(link.to.id)
      activeAirlineIds.add(link.airline.id)
    }
    println("Total active airports: " + activeAirportIds.size)

    println(">> Transfer base specs: " + transferBaseSpecializationDiscounts.size)
    println(transferBaseSpecializationDiscounts)

    println("Remove demand groups not covered by active airports, before " + demand.size);

    //randomize the demand chunks so later on it's consumed in a random (relatively even) manner
    var demandChunks = Random.shuffle(demand.filter { demandChunk =>
      val (passengerGroup, toAirport, chunkSize) = demandChunk
      val isConnected = activeAirportIds.contains(passengerGroup.fromAirport.id) && activeAirportIds.contains(toAirport.id)
      if (!isConnected) {
        missedDemandChunks.add(demandChunk)
      }
      isConnected
    }).sortWith {
      case ((pg1, _, demand1), (pg2, _, demand2)) =>
        pg1.preference.getPreferenceType.priority < pg2.preference.getPreferenceType.priority //sort by pax preference purchase order
    }

    println("After pruning : " + demandChunks.size);

    val establishedAlliances = AllianceSource.loadAllAlliances().filter(_.status == AllianceStatus.ESTABLISHED)
    val establishedAllianceIdByAirlineId : java.util.Map[Int, Int] = new java.util.HashMap[Int, Int]()

    establishedAlliances.foreach { alliance =>
      alliance.members.filter { member =>
        member.role != AllianceRole.APPLICANT && activeAirlineIds.contains(member.airline.id)
      }.foreach(member => establishedAllianceIdByAirlineId.put(member.airline.id, alliance.id))
    }

    val currentCycle = CycleSource.loadCycle()
    val airlineCostModifiers = AirlineSource.loadAirlineModifiers().filter {
      case (airlineId, modifier) => modifier.modifierType == AirlineModifierType.NERFED && activeAirlineIds.contains(airlineId)
    }.map {
      case (airlineId, modifier) => (airlineId, modifier.asInstanceOf[NerfedAirlineModifier].costMultiplier(currentCycle))
    }.toMap

    println(s"Simple Cost modifiers $airlineCostModifiers")

    val specializationCostModifiers: Map[(Int, Int), SpecializationModifier] = AirportSource.loadAllAirportBaseSpecializations.filter {
      case (airline, airport, specialization) =>
        activeAirlineIds.contains(airline.id) && specialization.getType == BaseSpecializationType.BRANDING
    }.map {
      case (airline, airport, brandingSpecialization) => ((airline.id, airport.id), SpecializationModifier(brandingSpecialization.asInstanceOf[BrandSpecialization].deltaByLinkClassAndPassengerType))
    }.toMap

    val externalCostModifier = ExternalCostModifier(airlineCostModifiers, specializationCostModifiers)

    while (consumptionCycleCount <= consumptionCycleMax) {
      println(s"Run loop $consumptionCycleCount for ${demandChunks.size} demand chunks")

      //using minSeats to have pax book together and decrease consumptions
      val minSeats: Int = (consumptionCycleMax - consumptionCycleCount) % 3 * 4
      //remove links without enough seats
      val availableLinks = links.filter {
        _.getTotalAvailableSeats > minSeats * 3
      }
      println(s"available links: ${availableLinks.size} of ${links.size}")
      
      val (filteredDemandChunks, demandChunksForLater) =
        if (consumptionCycleCount >= 5) { //don't ticket everyone to start
          demandChunks.partition {
            case (_, _, chunkSize) => chunkSize > minSeats
          }
        } else {
          demandChunks.partition {
            case (paxGroup, _, chunkSize) => chunkSize > minSeats && paxGroup.preference.getPreferenceType.priority <= 3
          }
        }
      val remainingDemandChunks = Collections.synchronizedList(new util.ArrayList[(PassengerGroup, Airport, Int)]())
      remainingDemandChunks.addAll(demandChunksForLater.asJava)
      println("Demand chunks saved for later: " + remainingDemandChunks.size)
      println("Demand chunks already removed: " + missedDemandChunks.size)

      //find out required routes - which "to airports" does each passengerGroup has
      print("Find required routes...")
      val requiredRoutes = scala.collection.mutable.Map[PassengerGroup, Set[Airport]]()
      filteredDemandChunks.foreach {
        case (passengerGroup, toAirport, _) =>
          val toAirports: Set[Airport] = requiredRoutes.getOrElseUpdate(passengerGroup, scala.collection.mutable.Set[Airport]())
          toAirports.add(toAirport)
      }
      println("Done!")


      //og AC at 4, 5, 6
      val iterationCount =
        if (consumptionCycleCount < 5) 3
        else if (consumptionCycleCount < 7) 4
        else 5
      val isSingleTicket = if (consumptionCycleCount == 0 || consumptionCycleCount == 5) true else false
      val allRoutesMap = mutable.HashMap[PassengerGroup, Map[Airport, Route]]()

      //start consuming routes
      //       println()
      //       print("Start to go through demand chunks and comsume...nom nom nom...")

      println("Total passenger groups: " + requiredRoutes.size)
      println(s"Hops: $iterationCount; calculating chunks >= $minSeats size")
      val counter = new AtomicInteger(0)
      val progressCount = new AtomicInteger(0)
      val progressChunk = requiredRoutes.size / 100

      filteredDemandChunks.par.foreach {
        case (passengerGroup, toAirport, chunkSize) =>
          var hasComputedRouteMap = false
          val toAirportRouteMap = allRoutesMap.getOrElseUpdate(passengerGroup, {
            hasComputedRouteMap = true
            findRoutesByPassengerGroup(passengerGroup, toAirports = requiredRoutes(passengerGroup), availableLinks, PassengerSimulation.countryOpenness, establishedAllianceIdByAirlineId, Some(externalCostModifier), iterationCount, isSingleTicket)
          })

          toAirportRouteMap.get(toAirport) match {
            case Some(pickedRoute) =>
              //println("picked route info" + passengerGroup + " " + pickedRoute.links(0).airline)
              val fromAirport = passengerGroup.fromAirport
              val rejection = getRouteRejection(pickedRoute, fromAirport, toAirport, passengerGroup.preference.preferredLinkClass, passengerGroup.passengerType)
              rejection match {
                case None =>
                  synchronized {
                    val consumptionSize = pickedRoute.links.foldLeft(chunkSize) { (foldInt, linkConsideration) =>
                      val actualLinkClass = linkConsideration.linkClass
                      val availableSeats = linkConsideration.link.availableSeats(actualLinkClass)
                      if (availableSeats < foldInt) {
                        availableSeats
                      } else {
                        foldInt
                      }
                    }
                    //some capacity available on all the links, consume them NOMNOM NOM!
                    if (consumptionSize > 0) {
                      pickedRoute.links.foreach { linkConsideration =>
                        val actualLinkClass = linkConsideration.linkClass
                        linkConsideration.link.addSoldSeatsByClass(actualLinkClass, consumptionSize)
                      }

                      consumptionResult.add((passengerGroup, toAirport, consumptionSize, pickedRoute))
                    }
                    //update the remaining demand chunk list
                    if (consumptionSize < chunkSize) { //not enough capacity to completely fill
                      //put a updated demand chunk
                      remainingDemandChunks.add((passengerGroup, toAirport, chunkSize - consumptionSize));
                    }
                  }
                case Some(rejection) =>
                  import RouteRejectionReason._
                  rejection match {
                    case TOTAL_COST => // do not retry
                      missedDemandChunks.add((passengerGroup, toAirport, chunkSize));
                    case DISTANCE => //try again to see if there's any route within reasonable route distance
                      remainingDemandChunks.add((passengerGroup, toAirport, chunkSize));
                    case LINK_COST => //try again to see if there's any route with better links
                      remainingDemandChunks.add((passengerGroup, toAirport, chunkSize));
                  }
              }
            case None => //no route
              missedDemandChunks.add((passengerGroup, toAirport, chunkSize));
          }

          if (hasComputedRouteMap) {
            if (progressChunk == 0 || counter.incrementAndGet() % progressChunk == 0) {
              print(".")
              if (progressCount.incrementAndGet() % 10 == 0) {
                print(progressCount.get + "% ")
              }
            }
          }
      }
      println("Done!")

      //now process the remainingDemandChunks in next cycle
      demandChunks = remainingDemandChunks.asScala.toList
      consumptionCycleCount += 1
    }

    println("Total chunks that consume something " + consumptionResult.size)

    val totalPax = consumptionResult.asScala.map(_._3).sum
    println("Total transported pax " + totalPax)

    val totalTicketsSold = consumptionResult.asScala.map { case (_,_, passengerCount, route) =>
      passengerCount * route.links.count { link =>
        link.link.transportType == TransportType.FLIGHT
      }
    }.sum
    println("Total tickets sold " + totalTicketsSold)

    val totalEmptySeats = links.filter(_.transportType == TransportType.FLIGHT).map(_.getTotalAvailableSeats).sum
    println("Total empty seats " + totalEmptySeats)
    val loadFactor = if (totalTicketsSold + totalEmptySeats > 0) totalTicketsSold.toDouble / (totalTicketsSold + totalEmptySeats) else 0
    println(f"Global load factor $loadFactor%.2f")

    val missedPax = missedDemandChunks.asScala.map(_._3).sum
    println("Total missed pax " + missedPax)

    val worldStatistics = WorldStatistics(currentCycle, Period.WEEKLY, totalTicketsSold, missedPax, loadFactor)
    WorldStatisticsSource.saveWorldStats(List(worldStatistics))

    //collapse it now
    val collapsedMap = consumptionResult.asScala.foldLeft(Map[(PassengerGroup, Airport, Route), Int]()) {
      case (accumulatorMap, (passengerGroup, toAirport, passengerCount, route)) =>
        val key = (passengerGroup, toAirport, route)
        val currentCount = accumulatorMap.getOrElse(key, 0)
        accumulatorMap.updated(key, currentCount + passengerCount)
    }

    val missedMap = missedDemandChunks.asScala.foldLeft(Map[(PassengerGroup, Airport), Int]()) {
      case (accumulatorMap, (passengerGroup, toAirport, passengerCount)) =>
        val key = (passengerGroup, toAirport)
        val currentCount = accumulatorMap.getOrElse(key, 0)
        accumulatorMap.updated(key, currentCount + passengerCount)
    }

    PassengerConsumptionResult(collapsedMap, missedMap, worldStatistics)
  }

  val LINK_COST_TOLERANCE_FACTOR = 1.0 //used by computePassengerSatisfaction
  val LINK_DISTANCE_TOLERANCE_FACTOR = 1.6
  val ROUTE_DISTANCE_TOLERANCE_FACTOR = 3.25


  object RouteRejectionReason extends Enumeration {
    type RouteRejectionReason = Value
    val TOTAL_COST, LINK_COST, DISTANCE = Value
  }

  def getRouteRejection(route: Route, fromAirport: Airport, toAirport: Airport, preferredLinkClass : LinkClass, paxType: PassengerType.Value) : Option[RouteRejectionReason.Value] = {
    import RouteRejectionReason._
    val routeDisplacement = Computation.calculateDistance(fromAirport, toAirport)
    val routeDistance = route.links.foldLeft(0)(_ + _.link.distance)

    if (routeDistance > routeDisplacement * ROUTE_DISTANCE_TOLERANCE_FACTOR) {
      return Some(DISTANCE)
    }

    val routeCostTolerance = PassengerType.routeCostTolerance(paxType)
    val routeAffordableCost = Pricing.computeStandardPrice(routeDisplacement, Computation.getFlightCategory(fromAirport, toAirport), preferredLinkClass, paxType, fromAirport.income) * routeCostTolerance
    if (route.totalCost > routeAffordableCost) {
      //println(s"rejected affordable: $routeAffordableCost, cost : , ${route.totalCost}  $route" )
      return Some(TOTAL_COST)
    }

    //now check individual link
    val unaffordableLink = route.links.find { linkConsideration => //find links that are too expensive
      val link = linkConsideration.link
      val linkAffordableCost = link.standardPrice(preferredLinkClass, paxType) * LINK_COST_TOLERANCE_FACTOR
      linkConsideration.cost > linkAffordableCost
    }

    if (unaffordableLink.isDefined) {
      return Some(LINK_COST)
    }
    return None
  }

  case class SpecializationModifier(deltaByLinkClassAndPassengerType: Map[(LinkClass, PassengerType.Value), Double]) {
    val value = (preferredLinkClass: LinkClass, paxType: PassengerType.Value) => {
      1.0 + deltaByLinkClassAndPassengerType.getOrElse((preferredLinkClass, paxType), 0.0)
    }
  }

  case class ExternalCostModifier(airlineCostModifiers : Map[Int, Double] = Map.empty,
                                  specializationCostModifiers : Map[(Int, Int), SpecializationModifier] = Map.empty) extends CostModifier { //(airlineId , airportId) -> modifier)
    override def value(link : Transport, linkClass : LinkClass, paxType : PassengerType.Value) : Double = {
      var modifier = 1.0
      if (airlineCostModifiers.contains(link.airline.id)) {
        modifier *= airlineCostModifiers(link.airline.id)
      }

      val airlineFromAirportTuple = (link.airline.id, link.from.id)
      if (specializationCostModifiers.contains(airlineFromAirportTuple)) {
        modifier *= specializationCostModifiers(airlineFromAirportTuple).value(linkClass, paxType)
      }
      val airlineToAirportTuple = (link.airline.id, link.to.id)
      if (specializationCostModifiers.contains(airlineToAirportTuple)) {
        modifier *= specializationCostModifiers(airlineToAirportTuple).value(linkClass, paxType)
      }
      modifier
    }
  }
  
   /**
   * Return all routes if available, with destination defined in the input Map's value, the Input map key indicates various Passenger Group
   * 
   * Returned value is in form of Future[Map[PassengerGroup, Map[Airport, Route]]], which the Map key should always present even if no valid route is found at all
   * for that particular PassengerGroup, in such a case the map value will just me an empty map.
   * 
   * Take note that when finding routes, in order to be considered as a valid route, this method considers:
   * 1. whether the link has available capacity left for the PassengerGroup's link Class, all the links in between 2 points should have capacity for the correct class
   *
   */
  def findFurthestAirportDistance(fromAirport: Airport, toAirports: Set[Airport]): Int = {
    val distances = toAirports.map(toAirport => Computation.calculateDistance(fromAirport, toAirport))
    distances.maxOption.getOrElse(0)
  }

  def findRoutesByPassengerGroup(passengerGroup: PassengerGroup,
                                 toAirports: Set[Airport],
                                 linksList: List[Transport],
                                 countryOpenness: Map[String, Int] = PassengerSimulation.countryOpenness,
                                 establishedAllianceIdByAirlineId: java.util.Map[Int, Int] = Collections.emptyMap[Int, Int](),
                                 externalCostModifier: Option[CostModifier] = None,
                                 iterationCount: Int = 4,
                                 isSingleTicket: Boolean = false) : Map[Airport, Route] = {

    val preferredLinkClass = passengerGroup.preference.preferredLinkClass
    //remove links that's unknown to this airport then compute cost for each link. Cost is adjusted by the PassengerGroup's preference
    val linkConsiderations = new ArrayList[LinkConsideration]()
    val activeAirports = Set[Int]()
    val furthestDistance = LINK_DISTANCE_TOLERANCE_FACTOR * findFurthestAirportDistance(passengerGroup.fromAirport, toAirports)

//    linksList.foreach { link =>
      linksList.filter(_.distance <= furthestDistance).foreach { link =>

      //see if there are any seats for that class (or lower) left
      link.availableSeatsAtOrBelowClass(preferredLinkClass).foreach {
        case (matchingLinkClass, seatsLeft) =>
          //2 instance of the link, one for each direction. Take note that the underlying link is the same, hence capacity and other params is shared properly!
          val costProvider = CostStoreProvider() //use same instance of costProvider so this is only computed once
          val linkConsideration1 = LinkConsideration(link, matchingLinkClass, false, passengerGroup, externalCostModifier, costProvider)
          val linkConsideration2 = LinkConsideration(link, matchingLinkClass, true, passengerGroup, externalCostModifier, costProvider)
          val isHeadquarters = link.from.getAirlineBase(link.airline.id) match {
            case Some(airlineBase) => airlineBase.headquarter
            case None => false
          }
          if (hasFreedom(linkConsideration1, passengerGroup.fromAirport, countryOpenness, link.from.size)) {
            linkConsiderations.add(linkConsideration1)
            activeAirports.add(link.from.id)
            activeAirports.add(link.to.id)
          }
          if (hasFreedom(linkConsideration2, passengerGroup.fromAirport, countryOpenness, link.to.size)) {
            linkConsiderations.add(linkConsideration2)
            activeAirports.add(link.from.id)
            activeAirports.add(link.to.id)
          }
      }
    }
    //val links = linksList.toArray
    findShortestRoute(passengerGroup, toAirports, allVertices = activeAirports, linkConsiderations, establishedAllianceIdByAirlineId, iterationCount, isSingleTicket)
  }

  
  
  
  def hasFreedom(linkConsideration : LinkConsideration, originatingAirport : Airport, countryOpenness : Map[String, Int], airportSize : Int) : Boolean = {
    if (linkConsideration.from.countryCode == linkConsideration.to.countryCode) { //domestic flight is always ok
      true
    } else if (linkConsideration.from.countryCode == originatingAirport.countryCode) { //always ok if link flying out from same country as the originate airport
      true
    } else if (airportSize >= 7) { //large airports can handle transfers
      true
    } else { //international to international, decide base on openness
      countryOpenness(linkConsideration.from.countryCode) >= Country.SIXTH_FREEDOM_MIN_OPENNESS
    }
  }

  /**
   * Find the shortest routes from the fromAirport to ALL the toAirport
   * Returns a map with valid route in format of
   * Map[toAiport, Route]
   */
  def findShortestRoute(passengerGroup : PassengerGroup, toAirports : Set[Airport], allVertices : Set[Int], linkConsiderations : java.util.List[LinkConsideration], allianceIdByAirlineId : java.util.Map[Int, Int], maxIteration : Int, isSingleTicket: Boolean = false) : Map[Airport, Route] = {

    val from = passengerGroup.fromAirport

    //     // Step 1: initialize graph
//   for each vertex v in vertices:
//       if v is source then distance[v] := 0
//       else distance[v] := inf
//       predecessor[v] := null
    //val allVertices = allVerticesSource.map { _.id }
    
    val distanceMap = new java.util.HashMap[Int, Double]()
    var predecessorMap = new java.util.HashMap[Int, LinkConsideration]()
    var activeVertices = new java.util.HashSet[Int]()
    val assetDiscountByAirportId = mutable.HashMap[Int, Option[(Double, List[AirportAsset])]]() //key is airport
    activeVertices.add(from.id)
    allVertices.foreach { vertex => 
      if (vertex == from.id) {
        distanceMap.put(vertex, 0)
      } else {
        distanceMap.put(vertex, 10000000)
      }
    }

   // Step 2: relax edges repeatedly
//   for i from 1 to size(vertices)-1:
//       for each edge (u, v) with weight w in edges:
//           if distance[u] + w < distance[v]:
//               distance[v] := distance[u] + w
//               predecessor[v] := u
    for (i <- 0 until maxIteration) {
      val newPredecessorMap = new java.util.HashMap[Int, LinkConsideration](predecessorMap)
      val newActiveVertices = new java.util.HashSet[Int]()
      //create a clone of last run, we update this map, but for lookup we use the previous one
      //this is necessary to avoid "previous leg replacement problem"
      //for example on first iteration, there is F0, T1 and T2. If there are links:
      // Link Consideration 1 : Airline A, F0 -> T1, cost 50
      // Link Consideration 2 : Airline A, T1 -> T2, cost 50
      // Link Consideration 3 : Airline B, F0 -> T1, cost 40
      //If we do NOT use a clone and lookup the current predecessorMap, from F0
      // It will be:
      // 1. Process Link Consideration 1, predecessorMap: T1 -> (Link 1, 50)
      // 2. Process Link Consideration 2, predecessorMap: T1 -> (Link 1, 50), T2 -> (Link 2, 50 + 50 + Connection cost of SAME airline)
      // 3. Process Link Consideration 3, predecessorMap: T1 -> (Link 3, 40), T2 -> (Link 2, 50 + 50 + Connection cost of SAME airline)
      // At the end it will be wrong as solution for route from F0 to T2, will be Link 3 and Link 2 while the final cost is incorrect
      // This also create the shuttle from other alliance problem
      //The fix for this is never use the current predecessorMap for lookup, instead, use the previous map

      val linkConsiderationsIterator = linkConsiderations.iterator()
      while (linkConsiderationsIterator.hasNext) {
        val linkConsideration = linkConsiderationsIterator.next
        if (activeVertices.contains(linkConsideration.from.id)) { //optimization - only need to re-run if the vertex was update in last iteration
          val predecessorLinkConsideration = predecessorMap.get(linkConsideration.from.id)
          var connectionCost = 0.0

          var isValid : Boolean = true
          var flightTransit = false
          if (predecessorLinkConsideration != null) { //then it should be a connection flight
            val predecessorLink = predecessorLinkConsideration.link

            if (linkConsideration.link.id == predecessorLink.id) { //going back and forth on the same link
              isValid = false
            } else if (predecessorLink.transportType == TransportType.GENERIC_TRANSIT && linkConsideration.link.transportType == TransportType.GENERIC_TRANSIT) {
              isValid = false //don't allow ground 2 ground connections
            } else if (predecessorLink.transportType == TransportType.GENERIC_TRANSIT) {
              if (predecessorLink.from.id == passengerGroup.fromAirport.id || predecessorLink.to.id == passengerGroup.fromAirport.id) {
                connectionCost = 0 //origin ground link only incurs link cost
              } else {
                connectionCost = 250 //middle "leave the airport" ground connections are v expensive; note this has to be 2x expensive as other connection ground cost was free
              }
            } else if (linkConsideration.link.transportType == TransportType.GENERIC_TRANSIT) {
              connectionCost = 0 //ground link is free, which may be destination (via ground) OR if there's then an additional flight connection it's caught above with a very expensive connection cost
            } else {
              val transferBaseDiscounts = transferBaseSpecializationDiscounts.get((linkConsideration.from.id, linkConsideration.link.airline.id))
              connectionCost = if (transferBaseDiscounts.exists(_.paxType.contains(passengerGroup.passengerType))) 24 * TravelerTransferSpecialization.transferCostDiscount else 24

              //now look at the frequency of the link arriving at this FromAirport and the link (current link) leaving this FromAirport. check frequency
              val frequency = Math.max(predecessorLink.frequencyByClass(predecessorLinkConsideration.linkClass), linkConsideration.link.frequencyByClass(linkConsideration.linkClass))

              if (frequency < 7) {
                connectionCost += 165 + (7 - frequency ) * 10 //possible overnight stay //$175 @ 6; $235 @ 1
              } else if (frequency < 14) {
                connectionCost += 65 + (14 - frequency) * 10 //$135 @ 7; $75 @ 13
              } else if (frequency <= 49) {
                connectionCost += (98 - frequency * 2) //$70 @ 14; $56 @ 21; $42 @ 28
              }

              val previousLinkAirlineId = predecessorLink.airline.id
              val currentLinkAirlineId = linkConsideration.link.airline.id
              if (previousLinkAirlineId != currentLinkAirlineId && (allianceIdByAirlineId.get(previousLinkAirlineId) == null.asInstanceOf[Int] || allianceIdByAirlineId.get(previousLinkAirlineId) != allianceIdByAirlineId.get(currentLinkAirlineId))) { //switch airline, impose extra cost
                connectionCost += 40
                if (isSingleTicket) {
                  isValid = false //is not valid on passes looking for a single ticket,
                }
              }
              flightTransit = true
            }
            connectionCost *= Math.min(1.0, 0.4 + 0.6 * passengerGroup.fromAirport.income.toDouble / Airport.HIGH_INCOME)
            connectionCost *= passengerGroup.preference.preferredLinkClass.spaceMultiplier
            connectionCost *= passengerGroup.preference.connectionCostRatio

            if (flightTransit) {
              val waitTimeDiscount = Math.min(0.5, linkConsideration.from.computeTransitDiscount(
                predecessorLinkConsideration,
                linkConsideration,
                passengerGroup))
              connectionCost *= (1 - waitTimeDiscount)
            }

          }

          if (isValid) {
            val cost = Math.max(0, linkConsideration.cost + connectionCost) //just to avoid loop in graph
            val fromCost = distanceMap.get(linkConsideration.from.id)
            var newCost = fromCost + cost

            assetDiscountByAirportId.getOrElseUpdate(linkConsideration.to.id, linkConsideration.to.computePassengerCostAssetDiscount(linkConsideration, passengerGroup)).foreach {
              case(discount, _) =>
                //discount scale by cost travelled so far ie bigger impact for pax from far away
                //however the discount should never be more than half the new cost, otherwise it COULD potentially get cheaper the further it goes
                val costDiscount = Math.min(cost * 0.5, newCost * discount)
                newCost -= costDiscount
            }

            if (newCost < distanceMap.get(linkConsideration.to.id)) {
              distanceMap.put(linkConsideration.to.id, newCost)
              newPredecessorMap.put(linkConsideration.to.id, linkConsideration.copyWithCost(cost)) //clone it, do not modify the existing linkWithCost
              newActiveVertices.add(linkConsideration.to.id)
            }
          }
        }
      }
      predecessorMap = newPredecessorMap
      activeVertices = newActiveVertices
    }

    val resultMap : scala.collection.mutable.Map[Airport, Route] = scala.collection.mutable.Map[Airport, Route]()

    toAirports.foreach{ to =>
      var walker = to.id
      var noSolution = false;
      var foundSolution = false
      var hasFlight = false
      val route = ListBuffer[LinkConsideration]()
      val visitedAssetsListBuffer = ListBuffer[AirportAsset]()
      while (!foundSolution && !noSolution) {
        val link = predecessorMap.get(walker)
        if (link != null) {
          route.prepend(link)
          if (link.link.transportType == TransportType.FLIGHT) {
            hasFlight = true
          }
          assetDiscountByAirportId.get(walker).foreach { _.foreach {
              case(_, visitedAssetsOfThisAirport) => visitedAssetsListBuffer.addAll(visitedAssetsOfThisAirport)
            }
          }
          walker = link.from.id
          if (walker == from.id && hasFlight) { //at least 1 leg has to be a flight. We don't want route with no flights
            foundSolution = true
          }
        } else { 
            noSolution = true
        }
      }
      if (foundSolution) {
        if (visitedAssetsListBuffer.isEmpty) {
          resultMap.put(to, Route(route.toList, distanceMap.get(to.id)))
        } else {
          resultMap.put(to, Route(route.toList, distanceMap.get(to.id), visitedAssetsListBuffer.toList))
        }

      }  
    }
    
    resultMap.toMap
  }

}
