package com.patson.tools

import com.patson.data.{AirlineSource, AirportSource, AllianceSource, CycleSource, LinkSource, Meta}
import com.patson.model.FlightPreferenceType._
import com.patson.model._
import com.patson.{CompactRouteKernel, PassengerDemandFixture, PassengerSimulation, RouteKernelMode, SimulationConfig}

import scala.collection.mutable

object PassengerLoopFixtureDiff {
  private val CostScale = 1000000000L

  private case class LegSnapshot(linkId: Int,
                                 fromAirportId: Int,
                                 toAirportId: Int,
                                 airlineId: Int,
                                 linkClass: String,
                                 inverted: Boolean,
                                 cost: Long)

  private case class RouteSnapshot(totalCost: Long, legs: List[LegSnapshot])

  private case class ChunkEvent(consumedPassengerCount: Int,
                                remainingPassengerCount: Int,
                                missedPassengerCount: Int,
                                route: Option[RouteSnapshot])

  private case class LoopContext(name: String,
                                 links: List[Transport],
                                 routeFinder: (PassengerGroup, mutable.Set[Airport], List[Transport], Int, Boolean) => Map[Airport, Route])

  private case class LoopResult(inspectedDemandChunks: Int,
                                remainingDemandChunks: List[(PassengerGroup, Airport, Int)])

  def main(args: Array[String]): Unit = {
    var exitCode = 0
    try {
      run(args)
    } catch {
      case e: Throwable =>
        exitCode = 1
        e.printStackTrace()
    } finally {
      shutdown(exitCode)
    }
  }

  private def run(args: Array[String]): Unit = {
    SimulationConfig.logRuntimeContext("passenger-loop-fixture-diff")
    SimulationConfig.validateClonedSchemaForExperiment("passenger loop fixture diff")

    val fixturePath = args.headOption
      .orElse(sys.env.get("PASSENGER_LOOP_DIFF_FIXTURE"))
      .getOrElse(throw new IllegalArgumentException("Missing fixture path. Pass it as the first argument or PASSENGER_LOOP_DIFF_FIXTURE."))
    val loop = args.lift(1)
      .orElse(sys.env.get("PASSENGER_LOOP_DIFF_LOOP"))
      .map(_.toInt)
      .getOrElse(0)
    val chunkLimit = args.lift(2)
      .orElse(sys.env.get("PASSENGER_LOOP_DIFF_CHUNK_LIMIT"))
      .map(_.toInt)
      .getOrElse(0)
    val candidateMode = sys.env.get("PASSENGER_LOOP_DIFF_CANDIDATE")
      .map(RouteKernelMode.parse)
      .getOrElse(RouteKernelMode.COMPACT)

    if (loop != -1 && loop != 0) {
      throw new IllegalArgumentException("PassengerLoopFixtureDiff currently supports loop 0 or loop -1 for all loops.")
    }
    if (candidateMode == RouteKernelMode.LEGACY) {
      throw new IllegalArgumentException("PassengerLoopFixtureDiff candidate must be compact or adjacency.")
    }

    val cycle = CycleSource.loadCycle()
    println(s"passengerLoopFixtureDiff phase=start cycle=$cycle fixture=$fixturePath loop=$loop candidate=$candidateMode chunkLimit=$chunkLimit")

    val fixtureLinks = LinkSource.loadAllLinks(LinkSource.FULL_LOAD)
    val airportById = AirportSource.loadAllAirports(true).map(airport => airport.id -> airport).toMap ++
      PassengerDemandFixture.buildAirportLookup(List.empty, fixtureLinks)
    val fixture = PassengerDemandFixture.load(fixturePath, airportById)

    val activeAirlineIds = fixtureLinks.map(_.airline.id).toSet
    val establishedAllianceIdByAirlineId = establishedAllianceMap(activeAirlineIds)
    val externalCostModifier = externalCostModifiers(activeAirlineIds, cycle)

    val legacyContext = context(
      name = "legacy",
      candidateMode = RouteKernelMode.LEGACY,
      establishedAllianceIdByAirlineId,
      externalCostModifier
    )
    val candidateContext = context(
      name = candidateMode.toString.toLowerCase,
      candidateMode,
      establishedAllianceIdByAirlineId,
      externalCostModifier
    )

    val loops = if (loop == -1) 0 to 9 else loop to loop
    val boundedRouteScope = loop == -1 && chunkLimit > 0
    var demandChunks = fixture.demandChunks
    var totalInspected = 0

    loops.foreach { loopIndex =>
      val result = runLoop(
        loopIndex,
        demandChunks,
        legacyContext,
        candidateContext,
        chunkLimit,
        boundedRouteScope
      )
      totalInspected += result.inspectedDemandChunks
      demandChunks = result.remainingDemandChunks
    }

    println(s"passengerLoopFixtureDiff phase=complete loops=${loops.mkString(",")} inspectedDemandChunks=$totalInspected mismatches=0 finalRemainingDemandChunks=${demandChunks.size}")
  }

  private def context(name: String,
                      candidateMode: RouteKernelMode.Value,
                      establishedAllianceIdByAirlineId: java.util.Map[Int, Int],
                      externalCostModifier: PassengerSimulation.ExternalCostModifier): LoopContext = {
    val links = LinkSource.loadAllLinks(LinkSource.FULL_LOAD)
    val routeFinder: (PassengerGroup, mutable.Set[Airport], List[Transport], Int, Boolean) => Map[Airport, Route] =
      candidateMode match {
        case RouteKernelMode.LEGACY =>
          (passengerGroup, toAirports, links, maxHops, isSingleTicket) =>
            PassengerSimulation.findRoutesByPassengerGroupLegacy(
              passengerGroup,
              toAirports,
              links,
              PassengerSimulation.countryOpenness,
              establishedAllianceIdByAirlineId,
              Some(externalCostModifier),
              iterationCount = maxHops,
              isSingleTicket = isSingleTicket
            )
        case RouteKernelMode.COMPACT =>
          (passengerGroup, toAirports, links, maxHops, isSingleTicket) =>
            CompactRouteKernel.findRoutesByPassengerGroup(
              passengerGroup = passengerGroup,
              toAirports = toAirports,
              linksList = links,
              countryOpenness = PassengerSimulation.countryOpenness,
              establishedAllianceIdByAirlineId = establishedAllianceIdByAirlineId,
              externalCostModifier = Some(externalCostModifier),
              iterationCount = maxHops,
              isSingleTicket = isSingleTicket,
              transferBaseSpecializationDiscounts = PassengerSimulation.transferBaseSpecializationDiscounts
            )
        case RouteKernelMode.ADJACENCY =>
          (passengerGroup, toAirports, links, maxHops, isSingleTicket) =>
            CompactRouteKernel.findRoutesByPassengerGroupAdjacency(
              passengerGroup,
              toAirports,
              links,
              PassengerSimulation.countryOpenness,
              establishedAllianceIdByAirlineId,
              Some(externalCostModifier),
              iterationCount = maxHops,
              isSingleTicket = isSingleTicket
            )
      }

    LoopContext(name, links, routeFinder)
  }

  private def runLoop(loop: Int,
                      demandChunks: List[(PassengerGroup, Airport, Int)],
                      legacyContext: LoopContext,
                      candidateContext: LoopContext,
                      chunkLimit: Int,
                      boundedRouteScope: Boolean): LoopResult = {
    val minSeats = (9 - loop) % 3 * 4
    val (filteredDemandChunks, demandChunksForLater) =
      if (loop >= 5) {
        demandChunks.partition {
          case (_, _, chunkSize) => chunkSize > minSeats
        }
      } else {
        demandChunks.partition {
          case (passengerGroup, _, chunkSize) =>
            chunkSize > minSeats && passengerGroup.preference.getPreferenceType.priority <= 3
        }
      }

    val chunksToInspect =
      if (chunkLimit > 0) filteredDemandChunks.take(chunkLimit)
      else filteredDemandChunks

    val requiredRouteDemandChunks =
      if (boundedRouteScope) chunksToInspect
      else filteredDemandChunks

    val requiredRoutes = mutable.LinkedHashMap[PassengerGroup, mutable.Set[Airport]]()
    requiredRouteDemandChunks.foreach {
      case (passengerGroup, toAirport, _) =>
        requiredRoutes.getOrElseUpdate(passengerGroup, mutable.Set[Airport]()).add(toAirport)
    }

    val maxHops =
      if (loop < 5) 3
      else if (loop < 7) 4
      else 5
    val isSingleTicket = loop == 0 || loop == 5
    val legacyRouteCache = mutable.HashMap[PassengerGroup, Map[Airport, Route]]()
    val candidateRouteCache = mutable.HashMap[PassengerGroup, Map[Airport, Route]]()
    val legacyAvailableLinks = legacyContext.links.filter(_.getTotalAvailableSeats > minSeats * 3)
    val candidateAvailableLinks = candidateContext.links.filter(_.getTotalAvailableSeats > minSeats * 3)

    if (legacyAvailableLinks.size != candidateAvailableLinks.size) {
      throw new IllegalStateException(s"Available link count mismatch at loop $loop: legacy=${legacyAvailableLinks.size} candidate=${candidateAvailableLinks.size}")
    }

    println(
      s"passengerLoopFixtureDiff phase=loopStart loop=$loop inputDemandChunks=${demandChunks.size} " +
        s"filteredDemandChunks=${filteredDemandChunks.size} passengerGroups=${requiredRoutes.size} " +
        s"availableLinks=${legacyAvailableLinks.size} minSeats=$minSeats maxHops=$maxHops singleTicket=$isSingleTicket " +
        s"boundedRouteScope=$boundedRouteScope"
    )

    val remainingDemandChunks = mutable.ListBuffer[(PassengerGroup, Airport, Int)]()
    remainingDemandChunks ++= demandChunksForLater

    var inspected = 0
    chunksToInspect.foreach { demandChunk =>
      inspected += 1
      if (inspected % 10000 == 0) {
        println(s"passengerLoopFixtureDiff phase=progress loop=$loop inspectedDemandChunks=$inspected")
      }

      val legacyEvent = consumeChunk(legacyContext, legacyRouteCache, legacyAvailableLinks, demandChunk, requiredRoutes, maxHops, isSingleTicket)
      val candidateEvent = consumeChunk(candidateContext, candidateRouteCache, candidateAvailableLinks, demandChunk, requiredRoutes, maxHops, isSingleTicket)
      if (legacyEvent != candidateEvent) {
        printMismatch(loop, inspected, demandChunk, legacyEvent, candidateEvent)
        throw new IllegalStateException(s"Passenger loop fixture mismatch at loop $loop after inspecting $inspected demand chunks.")
      }

      if (legacyEvent.remainingPassengerCount > 0) {
        val (passengerGroup, toAirport, _) = demandChunk
        remainingDemandChunks += ((passengerGroup, toAirport, legacyEvent.remainingPassengerCount))
      }
    }

    val legacySeatState = seatState(legacyContext.links)
    val candidateSeatState = seatState(candidateContext.links)
    if (legacySeatState != candidateSeatState) {
      val firstDifferentLink = legacySeatState.keySet.toList.sorted.find(linkId => legacySeatState.get(linkId) != candidateSeatState.get(linkId))
      println(s"passengerLoopFixtureDiff phase=seatMismatch loop=$loop inspectedDemandChunks=$inspected firstDifferentLink=${firstDifferentLink.getOrElse("")}")
      firstDifferentLink.foreach { linkId =>
        println(s"passengerLoopFixtureDiff legacySeatState=${legacySeatState.get(linkId)}")
        println(s"passengerLoopFixtureDiff candidateSeatState=${candidateSeatState.get(linkId)}")
      }
      throw new IllegalStateException(s"Passenger loop fixture seat-state mismatch at loop $loop after inspecting $inspected demand chunks.")
    }

    if (chunkLimit > 0 && filteredDemandChunks.size > chunkLimit) {
      remainingDemandChunks ++= filteredDemandChunks.drop(chunkLimit)
    }

    println(
      s"passengerLoopFixtureDiff phase=loopComplete loop=$loop inspectedDemandChunks=$inspected " +
        s"remainingDemandChunks=${remainingDemandChunks.size}"
    )
    LoopResult(inspected, remainingDemandChunks.toList)
  }

  private def consumeChunk(context: LoopContext,
                           routeCache: mutable.HashMap[PassengerGroup, Map[Airport, Route]],
                           availableLinks: List[Transport],
                           demandChunk: (PassengerGroup, Airport, Int),
                           requiredRoutes: mutable.LinkedHashMap[PassengerGroup, mutable.Set[Airport]],
                           maxHops: Int,
                           isSingleTicket: Boolean): ChunkEvent = {
    val (passengerGroup, toAirport, chunkSize) = demandChunk
    val toAirportRouteMap = routeCache.getOrElseUpdate(
      passengerGroup,
      context.routeFinder(passengerGroup, requiredRoutes(passengerGroup), availableLinks, maxHops, isSingleTicket)
    )

    toAirportRouteMap.get(toAirport) match {
      case Some(route) =>
        PassengerSimulation.getRouteRejection(
          route,
          passengerGroup.fromAirport,
          toAirport,
          passengerGroup.preference.preferredLinkClass,
          passengerGroup.passengerType
        ) match {
          case None =>
            val consumptionSize = route.links.foldLeft(chunkSize) {
              case (candidateSize, linkConsideration) =>
                Math.min(candidateSize, linkConsideration.link.availableSeats(linkConsideration.linkClass))
            }
            if (consumptionSize > 0) {
              route.links.foreach { linkConsideration =>
                linkConsideration.link.addSoldSeatsByClass(linkConsideration.linkClass, consumptionSize)
              }
            }
            ChunkEvent(
              consumedPassengerCount = consumptionSize,
              remainingPassengerCount = Math.max(0, chunkSize - consumptionSize),
              missedPassengerCount = 0,
              route = Some(snapshotRoute(route))
            )
          case Some(PassengerSimulation.RouteRejectionReason.TOTAL_COST) =>
            ChunkEvent(0, 0, chunkSize, Some(snapshotRoute(route)))
          case Some(PassengerSimulation.RouteRejectionReason.DISTANCE) | Some(PassengerSimulation.RouteRejectionReason.LINK_COST) =>
            ChunkEvent(0, chunkSize, 0, Some(snapshotRoute(route)))
          case Some(rejection) =>
            throw new IllegalStateException(s"Unexpected route rejection reason: $rejection")
        }
      case None =>
        ChunkEvent(0, 0, chunkSize, None)
    }
  }

  private def establishedAllianceMap(activeAirlineIds: Set[Int]): java.util.Map[Int, Int] = {
    val establishedAllianceIdByAirlineId = new java.util.HashMap[Int, Int]()
    AllianceSource.loadAllAlliances().filter(_.status == AllianceStatus.ESTABLISHED).foreach { alliance =>
      alliance.members.filter { member =>
        member.role != AllianceRole.APPLICANT && activeAirlineIds.contains(member.airline.id)
      }.foreach(member => establishedAllianceIdByAirlineId.put(member.airline.id, alliance.id))
    }
    establishedAllianceIdByAirlineId
  }

  private def externalCostModifiers(activeAirlineIds: Set[Int], cycle: Int): PassengerSimulation.ExternalCostModifier = {
    val airlineCostModifiers = AirlineSource.loadAirlineModifiers().filter {
      case (airlineId, modifier) => modifier.modifierType == AirlineModifierType.NERFED && activeAirlineIds.contains(airlineId)
    }.map {
      case (airlineId, modifier) => (airlineId, modifier.asInstanceOf[NerfedAirlineModifier].costMultiplier(cycle))
    }.toMap

    val specializationCostModifiers: Map[(Int, Int), PassengerSimulation.SpecializationModifier] =
      AirportSource.loadAllAirportBaseSpecializations.filter {
        case (airline, _, specialization) =>
          activeAirlineIds.contains(airline.id) && specialization.getType == BaseSpecializationType.BRANDING
      }.map {
        case (airline, airport, brandingSpecialization) =>
          ((airline.id, airport.id), PassengerSimulation.SpecializationModifier(brandingSpecialization.asInstanceOf[BrandSpecialization].deltaByLinkClassAndPassengerType))
      }.toMap

    PassengerSimulation.ExternalCostModifier(airlineCostModifiers, specializationCostModifiers)
  }

  private def snapshotRoute(route: Route): RouteSnapshot = {
    RouteSnapshot(
      totalCost = costSnapshot(route.totalCost),
      legs = route.links.map { linkConsideration =>
        LegSnapshot(
          linkId = linkConsideration.link.id,
          fromAirportId = linkConsideration.from.id,
          toAirportId = linkConsideration.to.id,
          airlineId = linkConsideration.link.airline.id,
          linkClass = linkConsideration.linkClass.code,
          inverted = linkConsideration.inverted,
          cost = costSnapshot(linkConsideration.cost)
        )
      }
    )
  }

  private def seatState(links: List[Transport]): Map[Int, (Int, Int, Int, Int, Int, Int, Int, Int)] = {
    links.map { link =>
      link.id -> (
        link.soldSeats(ECONOMY),
        link.availableSeats(ECONOMY),
        link.soldSeats(BUSINESS),
        link.availableSeats(BUSINESS),
        link.soldSeats(FIRST),
        link.availableSeats(FIRST),
        link.soldSeats(DISCOUNT_ECONOMY),
        link.availableSeats(DISCOUNT_ECONOMY)
      )
    }.toMap
  }

  private def printMismatch(loop: Int,
                            inspected: Int,
                            demandChunk: (PassengerGroup, Airport, Int),
                            legacyEvent: ChunkEvent,
                            candidateEvent: ChunkEvent): Unit = {
    val (passengerGroup, toAirport, chunkSize) = demandChunk
    println(
      s"passengerLoopFixtureDiff phase=mismatch loop=$loop inspectedDemandChunks=$inspected chunkSize=$chunkSize " +
        s"fromAirport=${passengerGroup.fromAirport.id} toAirport=${toAirport.id} passengerType=${passengerGroup.passengerType.id} " +
        s"preferenceType=${passengerGroup.preference.getPreferenceType.id} preferredClass=${passengerGroup.preference.preferredLinkClass.code}"
    )
    println(s"passengerLoopFixtureDiff legacyEvent=$legacyEvent")
    println(s"passengerLoopFixtureDiff candidateEvent=$candidateEvent")
  }

  private def costSnapshot(cost: Double): Long = Math.round(cost * CostScale)

  private def shutdown(exitCode: Int): Unit = {
    try {
      if (!Meta.dataSource.isClosed) {
        Meta.dataSource.close()
      }
    } catch {
      case e: Exception => println(s"passengerLoopFixtureDiff phase=shutdown warning=${e.getMessage}")
    }
    System.exit(exitCode)
  }
}
