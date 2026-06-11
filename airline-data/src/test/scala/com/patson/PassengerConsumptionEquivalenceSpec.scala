package com.patson

import java.nio.file.Files
import java.util.Collections

import com.patson.PassengerSimulation.RouteRejectionReason
import com.patson.model._
import com.patson.model.FlightPreferenceType._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.collection.mutable

class PassengerConsumptionEquivalenceSpec extends AnyWordSpecLike with Matchers {
  private val TestCountryOpenness = Map("TC" -> 100)
  private val CostScale = 1000000000L

  private case class DeterministicPreference(homeAirport: Airport,
                                             override val preferredLinkClass: LinkClass,
                                             preferenceType: FlightPreferenceType.Value,
                                             override val connectionCostRatio: Double) extends FlightPreference(homeAirport) {
    override val priceSensitivity: Double = 1.0
    override val getPreferenceType: FlightPreferenceType.Value = preferenceType

    override def computeCost(baseCost: Double, link: Transport, linkClass: LinkClass): Double = baseCost

    override def computeCost(link: Transport, linkClass: LinkClass, paxType: PassengerType.Value, externalCostModifier: Double = 1.0): Double = {
      link.cost(linkClass) * externalCostModifier
    }
  }

  private case class PassengerGroupSnapshot(fromAirportId: Int,
                                            preferenceType: String,
                                            preferredLinkClass: String,
                                            passengerType: String)

  private case class DemandSnapshot(passengerGroup: PassengerGroupSnapshot,
                                    toAirportId: Int,
                                    passengerCount: Int)

  private case class PreferenceSnapshot(kind: String,
                                        priceModifier: Double,
                                        loungeLevelRequired: Int,
                                        loyaltyRatio: Double,
                                        preferenceId: Int)

  private case class RouteLegSnapshot(linkId: Int,
                                      fromAirportId: Int,
                                      toAirportId: Int,
                                      airlineId: Int,
                                      linkClass: String,
                                      inverted: Boolean,
                                      cost: Long)

  private case class ConsumedDemandSnapshot(demand: DemandSnapshot,
                                            routeCost: Long,
                                            route: Vector[RouteLegSnapshot])

  private case class SeatSnapshot(linkId: Int,
                                  linkClass: String,
                                  soldSeats: Int,
                                  availableSeats: Int)

  private case class ConsumptionLoopSnapshot(loop: Int,
                                             consumedDemandEntries: Vector[ConsumedDemandSnapshot],
                                             remainingDemandEntries: Vector[DemandSnapshot],
                                             missedDemandEntries: Vector[DemandSnapshot],
                                             seatState: Vector[SeatSnapshot],
                                             totalTransportedPassengers: Int,
                                             totalTicketsSold: Int,
                                             totalMissedPassengers: Int)

  private case class ConsumptionFixture(demandAfterPruning: Vector[(PassengerGroup, Airport, Int)],
                                        makeLinks: () => List[Link])

  private case class KernelRun(name: String,
                               findRoutes: (PassengerGroup, mutable.Set[Airport], List[Transport], Int, Boolean) => Map[Airport, Route])

  private val legacyKernel = KernelRun(
    "legacy",
    (passengerGroup, toAirports, links, maxHops, isSingleTicket) =>
      PassengerSimulation.findRoutesByPassengerGroupLegacy(
        passengerGroup,
        toAirports,
        links,
        TestCountryOpenness,
        Collections.emptyMap[Int, Int](),
        None,
        maxHops,
        isSingleTicket,
        Map.empty
      )
  )

  private val compactKernel = KernelRun(
    "compact",
    (passengerGroup, toAirports, links, maxHops, isSingleTicket) => {
      val loopContext = CompactRouteKernel.buildLoopContext(links, Collections.emptyMap[Int, Int](), fixtureFlightDuration)
      CompactRouteKernel.findRoutesByPassengerGroup(
        loopContext,
        passengerGroup,
        toAirports,
        TestCountryOpenness,
        Collections.emptyMap[Int, Int](),
        None,
        maxHops,
        isSingleTicket,
        Map.empty
      )
    }
  )

  private val adjacencyKernel = KernelRun(
    "adjacency",
    (passengerGroup, toAirports, links, maxHops, isSingleTicket) =>
      CompactRouteKernel.findRoutesByPassengerGroupAdjacency(
        passengerGroup,
        toAirports,
        links,
        TestCountryOpenness,
        Collections.emptyMap[Int, Int](),
        None,
        maxHops,
        isSingleTicket,
        Map.empty
      )
  )

  "Deterministic passenger consumption fixture" should {
    "match legacy after every loop for compact route kernels" in {
      val fixture = deterministicFixture()
      val legacySnapshots = runConsumptionFixture(fixture, legacyKernel)

      Seq(compactKernel, adjacencyKernel).foreach { kernel =>
        withClue(s"${kernel.name} consumption snapshots should match legacy") {
          runConsumptionFixture(fixture, kernel) shouldEqual legacySnapshots
        }
      }
    }

    "round-trip production demand fixture rows" in {
      val fixture = deterministicFixture()
      val origin = fixture.demandAfterPruning.head._1.fromAirport
      val destinationA = fixture.demandAfterPruning.head._2
      val destinationB = fixture.demandAfterPruning(1)._2
      val links = fixture.makeLinks()
      val readyDemand = List(
        (PassengerGroup(origin, DealPreference(origin, ECONOMY, 0.88), PassengerType.TRAVELER), destinationA, 11),
        (PassengerGroup(origin, LastMinutePreference(origin, BUSINESS, 1.14, 2), PassengerType.BUSINESS), destinationB, 7)
      )
      val initiallyMissedDemand = List(
        (PassengerGroup(origin, AppealPreference(origin, FIRST, 1.0, 3, 1.2, 99), PassengerType.ELITE), destinationA, 5)
      )
      val path = Files.createTempFile("passenger-demand-fixture", ".tsv")

      try {
        PassengerDemandFixture.save(path.toString, readyDemand, initiallyMissedDemand)
        val loaded = PassengerDemandFixture.load(
          path.toString,
          PassengerDemandFixture.buildAirportLookup(readyDemand ++ initiallyMissedDemand, links)
        )

        loaded.demandChunks.map(fixtureRowSnapshot) shouldEqual readyDemand.map(fixtureRowSnapshot)
        loaded.initiallyMissedDemand.map(fixtureRowSnapshot) shouldEqual initiallyMissedDemand.map(fixtureRowSnapshot)
      } finally {
        Files.deleteIfExists(path)
      }
    }

    "produce stable route costs when route-cost seed is configured" in {
      val fixture = deterministicFixture()
      val origin = fixture.demandAfterPruning.head._1.fromAirport
      val link = fixture.makeLinks().head
      val preference = DealPreference(origin, ECONOMY, 1.0)

      val firstRandom = withRouteCostSeed("4242") {
        preference.deterministicRouteCostRandomForKernel("frequencyThreshold", link, ECONOMY, Some(PassengerType.TRAVELER)).map(_.nextDouble())
      }
      val secondRandom = withRouteCostSeed("4242") {
        preference.deterministicRouteCostRandomForKernel("frequencyThreshold", link, ECONOMY, Some(PassengerType.TRAVELER)).map(_.nextDouble())
      }

      secondRandom shouldEqual firstRandom
    }
  }

  private def runConsumptionFixture(fixture: ConsumptionFixture, kernel: KernelRun): Vector[ConsumptionLoopSnapshot] = {
    val links = fixture.makeLinks()
    val consumedDemand = mutable.ArrayBuffer[(PassengerGroup, Airport, Int, Route)]()
    val missedDemand = mutable.ArrayBuffer[(PassengerGroup, Airport, Int)]()
    val snapshots = mutable.ArrayBuffer[ConsumptionLoopSnapshot]()
    val consumptionCycleMax = 9
    var demandChunks = fixture.demandAfterPruning
    var consumptionCycleCount = 0

    while (consumptionCycleCount <= consumptionCycleMax) {
      val minSeats = (consumptionCycleMax - consumptionCycleCount) % 3 * 4
      val availableLinks: List[Transport] = links.filter(_.getTotalAvailableSeats > minSeats * 3)
      val (filteredDemandChunks, demandChunksForLater) =
        if (consumptionCycleCount >= 5) {
          demandChunks.partition {
            case (_, _, chunkSize) => chunkSize > minSeats
          }
        } else {
          demandChunks.partition {
            case (passengerGroup, _, chunkSize) =>
              chunkSize > minSeats && passengerGroup.preference.getPreferenceType.priority <= 3
          }
        }

      val remainingDemand = mutable.ArrayBuffer[(PassengerGroup, Airport, Int)]()
      remainingDemand ++= demandChunksForLater

      val requiredRoutes = mutable.Map[PassengerGroup, mutable.Set[Airport]]()
      filteredDemandChunks.foreach {
        case (passengerGroup, toAirport, _) =>
          requiredRoutes.getOrElseUpdate(passengerGroup, mutable.Set[Airport]()).add(toAirport)
      }

      val maxHops =
        if (consumptionCycleCount < 5) 3
        else if (consumptionCycleCount < 7) 4
        else 5
      val isSingleTicket = consumptionCycleCount == 0 || consumptionCycleCount == 5
      val routeCache = mutable.HashMap[PassengerGroup, Map[Airport, Route]]()

      filteredDemandChunks.foreach {
        case (passengerGroup, toAirport, chunkSize) =>
          val routesByAirport = routeCache.getOrElseUpdate(
            passengerGroup,
            kernel.findRoutes(passengerGroup, requiredRoutes(passengerGroup), availableLinks, maxHops, isSingleTicket)
          )

          routesByAirport.get(toAirport) match {
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
                    consumedDemand += ((passengerGroup, toAirport, consumptionSize, route))
                  }
                  if (consumptionSize < chunkSize) {
                    remainingDemand += ((passengerGroup, toAirport, chunkSize - consumptionSize))
                  }
                case Some(RouteRejectionReason.TOTAL_COST) =>
                  missedDemand += ((passengerGroup, toAirport, chunkSize))
                case Some(RouteRejectionReason.DISTANCE) | Some(RouteRejectionReason.LINK_COST) =>
                  remainingDemand += ((passengerGroup, toAirport, chunkSize))
                case Some(rejection) =>
                  throw new AssertionError(s"Unexpected route rejection reason in fixture: $rejection")
              }
            case None =>
              missedDemand += ((passengerGroup, toAirport, chunkSize))
          }
      }

      demandChunks = remainingDemand.toVector
      snapshots += snapshot(consumptionCycleCount, consumedDemand.toVector, demandChunks, missedDemand.toVector, links)
      consumptionCycleCount += 1
    }

    snapshots.toVector
  }

  private def snapshot(loop: Int,
                       consumedDemand: Vector[(PassengerGroup, Airport, Int, Route)],
                       remainingDemand: Vector[(PassengerGroup, Airport, Int)],
                       missedDemand: Vector[(PassengerGroup, Airport, Int)],
                       links: List[Link]): ConsumptionLoopSnapshot = {
    ConsumptionLoopSnapshot(
      loop = loop,
      consumedDemandEntries = consumedDemand.map(consumedSnapshot),
      remainingDemandEntries = remainingDemand.map(demandSnapshot),
      missedDemandEntries = missedDemand.map(demandSnapshot),
      seatState = links.toVector.flatMap { link =>
        LinkClass.values.map { linkClass =>
          SeatSnapshot(link.id, linkClass.code, link.soldSeats(linkClass), link.availableSeats(linkClass))
        }
      },
      totalTransportedPassengers = consumedDemand.map(_._3).sum,
      totalTicketsSold = consumedDemand.map {
        case (_, _, passengerCount, route) =>
          passengerCount * route.links.count(_.link.transportType == TransportType.FLIGHT)
      }.sum,
      totalMissedPassengers = missedDemand.map(_._3).sum
    )
  }

  private def consumedSnapshot(entry: (PassengerGroup, Airport, Int, Route)): ConsumedDemandSnapshot = {
    val (passengerGroup, toAirport, passengerCount, route) = entry
    ConsumedDemandSnapshot(
      demand = demandSnapshot((passengerGroup, toAirport, passengerCount)),
      routeCost = costSnapshot(route.totalCost),
      route = route.links.toVector.map { linkConsideration =>
        RouteLegSnapshot(
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

  private def demandSnapshot(entry: (PassengerGroup, Airport, Int)): DemandSnapshot = {
    val (passengerGroup, toAirport, passengerCount) = entry
    DemandSnapshot(
      PassengerGroupSnapshot(
        fromAirportId = passengerGroup.fromAirport.id,
        preferenceType = passengerGroup.preference.getPreferenceType.toString,
        preferredLinkClass = passengerGroup.preference.preferredLinkClass.code,
        passengerType = passengerGroup.passengerType.toString
      ),
      toAirportId = toAirport.id,
      passengerCount = passengerCount
    )
  }

  private def fixtureRowSnapshot(entry: (PassengerGroup, Airport, Int)): (DemandSnapshot, PreferenceSnapshot) = {
    val preferenceSnapshot = entry._1.preference match {
      case DealPreference(_, _, priceModifier) =>
        PreferenceSnapshot("deal", priceModifier, 0, 0.0, 0)
      case LastMinutePreference(_, _, priceModifier, loungeLevelRequired) =>
        PreferenceSnapshot("last_minute", priceModifier, loungeLevelRequired, 0.0, 0)
      case AppealPreference(_, _, priceModifier, loungeLevelRequired, loyaltyRatio, id) =>
        PreferenceSnapshot("appeal", priceModifier, loungeLevelRequired, loyaltyRatio, id)
      case other =>
        throw new AssertionError(s"Unexpected fixture preference: ${other.getClass.getName}")
    }
    (demandSnapshot(entry), preferenceSnapshot)
  }

  private def costSnapshot(cost: Double): Long = Math.round(cost * CostScale)

  private def withRouteCostSeed[T](seed: String)(body: => T): T = {
    val propertyName = "simulation.passengerRouteCostSeed"
    val previousSeed = System.getProperty(propertyName)
    System.setProperty(propertyName, seed)
    try {
      body
    } finally {
      if (previousSeed == null) {
        System.clearProperty(propertyName)
      } else {
        System.setProperty(propertyName, previousSeed)
      }
    }
  }

  private def deterministicFixture(): ConsumptionFixture = {
    val airline1 = Airline("Fixture Airline 1", id = 8101)
    val airline2 = Airline("Fixture Airline 2", id = 8102)
    val origin = airport(8201, "ORG", latitude = 0, longitude = 0, airline1, airline2)
    val hub = airport(8202, "HUB", latitude = 0, longitude = 3, airline1, airline2)
    val destinationA = airport(8203, "A", latitude = 0, longitude = 6, airline1, airline2)
    val destinationB = airport(8204, "B", latitude = 0, longitude = 9, airline1, airline2)
    val destinationC = airport(8205, "C", latitude = 0, longitude = 12, airline1, airline2)
    val isolated = airport(8206, "ISO", latitude = 0, longitude = 15, airline1, airline2)

    val dealPassengerGroup = PassengerGroup(
      origin,
      DeterministicPreference(origin, ECONOMY, FlightPreferenceType.DEAL, connectionCostRatio = 0.6),
      PassengerType.TRAVELER
    )
    val lastMinutePassengerGroup = PassengerGroup(
      origin,
      DeterministicPreference(origin, ECONOMY, FlightPreferenceType.LAST_MINUTE, connectionCostRatio = 1.0),
      PassengerType.TRAVELER
    )

    ConsumptionFixture(
      demandAfterPruning = Vector(
        (dealPassengerGroup, destinationA, 7),
        (dealPassengerGroup, destinationB, 5),
        (dealPassengerGroup, destinationC, 6),
        (lastMinutePassengerGroup, destinationB, 5)
      ),
      makeLinks = () => {
        val links = List(
          link(8301, origin, destinationA, airline1, cost = 150, seats = 4, distance = 660, frequency = 14),
          link(8302, origin, hub, airline1, cost = 40, seats = 20, distance = 330, frequency = 14),
          link(8303, hub, destinationA, airline1, cost = 40, seats = 4, distance = 330, frequency = 14),
          link(8304, origin, destinationB, airline1, cost = 120, seats = 3, distance = 990, frequency = 14),
          link(8305, hub, destinationB, airline2, cost = 40, seats = 20, distance = 660, frequency = 14),
          link(8306, destinationC, isolated, airline1, cost = 40, seats = 20, distance = 330, frequency = 14)
        )
        links.foreach(_.setQuality(50))
        links
      }
    )
  }

  private def airport(id: Int,
                      code: String,
                      latitude: Double,
                      longitude: Double,
                      airlines: Airline*): Airport = {
    val airport = Airport(
      iata = code,
      icao = code,
      name = s"Fixture Airport $code",
      latitude = latitude,
      longitude = longitude,
      countryCode = "TC",
      city = code,
      zone = "fixture",
      size = 7,
      baseIncome = 60000,
      basePopulation = 1000000,
      id = id
    )
    airport.initAirlineAppeals(airlines.map(airline => airline.id -> AirlineAppeal(0)).toMap)
    airport.initAirlineBases(List.empty)
    airport.initLounges(List.empty)
    airport
  }

  private def link(id: Int,
                   from: Airport,
                   to: Airport,
                   airline: Airline,
                   cost: Int,
                   seats: Int,
                   distance: Int,
                   frequency: Int): Link = {
    Link(
      from = from,
      to = to,
      airline = airline,
      price = LinkClassValues.getInstance(economy = cost),
      distance = distance,
      capacity = LinkClassValues.getInstance(economy = seats),
      rawQuality = 50,
      duration = fixtureFlightDuration(distance),
      frequency = frequency,
      id = id
    )
  }

  private def fixtureFlightDuration(distance: Int): Int = {
    Math.max(1, Math.round(distance.toDouble / 800 * 60).toInt)
  }
}
