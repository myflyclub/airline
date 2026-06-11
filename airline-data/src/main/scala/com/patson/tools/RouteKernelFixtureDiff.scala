package com.patson.tools

import com.patson.data.{AirlineSource, AirportSource, AllianceSource, CycleSource, LinkSource, Meta}
import com.patson.model._
import com.patson.model.FlightPreferenceType._
import com.patson.{CompactRouteKernel, PassengerDemandFixture, PassengerSimulation, RouteKernelMode, SimulationConfig}

import scala.collection.mutable

object RouteKernelFixtureDiff {
  private val CostScale = 1000000000L

  private case class LegSnapshot(linkId: Int,
                                 fromAirportId: Int,
                                 toAirportId: Int,
                                 airlineId: Int,
                                 linkClass: String,
                                 inverted: Boolean,
                                 cost: Long)

  private case class RouteSnapshot(totalCost: Long, legs: List[LegSnapshot])

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
    SimulationConfig.logRuntimeContext("route-fixture-diff")
    SimulationConfig.validateClonedSchemaForExperiment("route fixture diff")

    val fixturePath = args.headOption
      .orElse(sys.env.get("ROUTE_KERNEL_DIFF_FIXTURE"))
      .getOrElse(throw new IllegalArgumentException("Missing fixture path. Pass it as the first argument or ROUTE_KERNEL_DIFF_FIXTURE."))
    val loop = args.lift(1)
      .orElse(sys.env.get("ROUTE_KERNEL_DIFF_LOOP"))
      .map(_.toInt)
      .getOrElse(0)
    val groupLimit = args.lift(2)
      .orElse(sys.env.get("ROUTE_KERNEL_DIFF_GROUP_LIMIT"))
      .map(_.toInt)
      .getOrElse(0)
    val candidateMode = sys.env.get("ROUTE_KERNEL_DIFF_CANDIDATE")
      .map(RouteKernelMode.parse)
      .getOrElse(RouteKernelMode.COMPACT)

    if (loop != 0) {
      throw new IllegalArgumentException("RouteKernelFixtureDiff currently supports loop 0 only, before any seat mutation.")
    }
    if (candidateMode == RouteKernelMode.LEGACY) {
      throw new IllegalArgumentException("RouteKernelFixtureDiff candidate must be compact or adjacency.")
    }

    val cycle = CycleSource.loadCycle()
    println(s"routeFixtureDiff phase=start cycle=$cycle fixture=$fixturePath loop=$loop candidate=$candidateMode groupLimit=$groupLimit")

    val links: List[Transport] = LinkSource.loadAllLinks(LinkSource.FULL_LOAD)
    val airportById = AirportSource.loadAllAirports(true).map(airport => airport.id -> airport).toMap ++
      PassengerDemandFixture.buildAirportLookup(List.empty, links)
    val fixture = PassengerDemandFixture.load(fixturePath, airportById)

    val minSeats = (9 - loop) % 3 * 4
    val availableLinks = links.filter(_.getTotalAvailableSeats > minSeats * 3)
    val filteredDemandChunks = fixture.demandChunks.filter {
      case (passengerGroup, _, chunkSize) =>
        chunkSize > minSeats && passengerGroup.preference.getPreferenceType.priority <= 3
    }

    val activeAirlineIds = links.map(_.airline.id).toSet
    val establishedAllianceIdByAirlineId = establishedAllianceMap(activeAirlineIds)
    val externalCostModifier = externalCostModifiers(activeAirlineIds, cycle)
    val requiredRoutes = mutable.LinkedHashMap[PassengerGroup, mutable.Set[Airport]]()

    filteredDemandChunks.foreach {
      case (passengerGroup, toAirport, _) =>
        requiredRoutes.getOrElseUpdate(passengerGroup, mutable.Set[Airport]()).add(toAirport)
    }

    println(
      s"routeFixtureDiff phase=prepared demandChunks=${fixture.demandChunks.size} " +
        s"filteredDemandChunks=${filteredDemandChunks.size} passengerGroups=${requiredRoutes.size} availableLinks=${availableLinks.size}"
    )

    val groupsToInspect =
      if (groupLimit > 0) requiredRoutes.iterator.take(groupLimit)
      else requiredRoutes.iterator

    var inspected = 0
    groupsToInspect.foreach {
      case (passengerGroup, toAirports) =>
        inspected += 1
        if (inspected % 100 == 0) {
          println(s"routeFixtureDiff phase=progress inspectedPassengerGroups=$inspected")
        }

        val legacyRoutes = PassengerSimulation.findRoutesByPassengerGroupLegacy(
          passengerGroup,
          toAirports,
          availableLinks,
          PassengerSimulation.countryOpenness,
          establishedAllianceIdByAirlineId,
          Some(externalCostModifier),
          iterationCount = 3,
          isSingleTicket = true
        )
        val candidateRoutes = candidateMode match {
          case RouteKernelMode.COMPACT =>
            CompactRouteKernel.findRoutesByPassengerGroup(
              passengerGroup = passengerGroup,
              toAirports = toAirports,
              linksList = availableLinks,
              countryOpenness = PassengerSimulation.countryOpenness,
              establishedAllianceIdByAirlineId = establishedAllianceIdByAirlineId,
              externalCostModifier = Some(externalCostModifier),
              iterationCount = 3,
              isSingleTicket = true,
              transferBaseSpecializationDiscounts = PassengerSimulation.transferBaseSpecializationDiscounts
            )
          case RouteKernelMode.ADJACENCY =>
            CompactRouteKernel.findRoutesByPassengerGroupAdjacency(
              passengerGroup,
              toAirports,
              availableLinks,
              PassengerSimulation.countryOpenness,
              establishedAllianceIdByAirlineId,
              Some(externalCostModifier),
              iterationCount = 3,
              isSingleTicket = true
            )
          case RouteKernelMode.LEGACY =>
            throw new IllegalStateException("Legacy cannot be used as candidate mode.")
        }

        val legacySnapshot = snapshotRoutes(legacyRoutes)
        val candidateSnapshot = snapshotRoutes(candidateRoutes)
        if (legacySnapshot != candidateSnapshot) {
          printMismatch(inspected, passengerGroup, toAirports, legacySnapshot, candidateSnapshot)
          throw new IllegalStateException(s"Route fixture mismatch after inspecting $inspected passenger groups.")
        }
    }

    println(s"routeFixtureDiff phase=complete inspectedPassengerGroups=$inspected mismatches=0")
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

  private def snapshotRoutes(routes: Map[Airport, Route]): Map[Int, RouteSnapshot] = {
    routes.map {
      case (airport, route) =>
        airport.id -> RouteSnapshot(
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
  }

  private def printMismatch(inspected: Int,
                            passengerGroup: PassengerGroup,
                            toAirports: mutable.Set[Airport],
                            legacySnapshot: Map[Int, RouteSnapshot],
                            candidateSnapshot: Map[Int, RouteSnapshot]): Unit = {
    val allDestinationIds = legacySnapshot.keySet ++ candidateSnapshot.keySet
    val firstDifferentDestination = allDestinationIds.toList.sorted.find { airportId =>
      legacySnapshot.get(airportId) != candidateSnapshot.get(airportId)
    }

    println(
      s"routeFixtureDiff phase=mismatch inspectedPassengerGroups=$inspected " +
        s"fromAirport=${passengerGroup.fromAirport.id} passengerType=${passengerGroup.passengerType.id} " +
        s"preferenceType=${passengerGroup.preference.getPreferenceType.id} preferredClass=${passengerGroup.preference.preferredLinkClass.code} " +
        s"requestedDestinations=${toAirports.toList.map(_.id).sorted.mkString(",")}"
    )
    firstDifferentDestination.foreach { destinationId =>
      println(s"routeFixtureDiff mismatchDestination=$destinationId")
      println(s"routeFixtureDiff legacyRoute=${legacySnapshot.get(destinationId)}")
      println(s"routeFixtureDiff candidateRoute=${candidateSnapshot.get(destinationId)}")
    }
  }

  private def costSnapshot(cost: Double): Long = Math.round(cost * CostScale)

  private def shutdown(exitCode: Int): Unit = {
    try {
      if (!Meta.dataSource.isClosed) {
        Meta.dataSource.close()
      }
    } catch {
      case e: Exception => println(s"routeFixtureDiff phase=shutdown warning=${e.getMessage}")
    }
    System.exit(exitCode)
  }
}
