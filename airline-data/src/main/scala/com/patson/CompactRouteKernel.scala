package com.patson

import java.util

import com.patson.model._

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

object CompactRouteKernel {
  private val InitialDistance = 10000000.0
  private val GenericTransitId = TransportType.GENERIC_TRANSIT.id
  private val FlightId = TransportType.FLIGHT.id
  private val LinkClassCount = 4
  private val PassengerTypeCount = PassengerType.values.size

  final case class LoopContext(links: Array[Transport],
                               linkId: Array[Int],
                               linkFromAirportId: Array[Int],
                               linkToAirportId: Array[Int],
                               linkAirlineId: Array[Int],
                               linkAllianceId: Array[Int],
                               linkDistance: Array[Int],
                               linkDuration: Array[Int],
                               linkFrequency: Array[Int],
                               linkComputedQuality: Array[Int],
                               linkStandardDuration: Array[Int],
                               linkStandardPrice: Array[Int],
                               linkTravelerZeroIncomeStandardPrice: Array[Int],
                               linkTransportType: Array[Int],
                               linkFromAirportSize: Array[Int],
                               linkToAirportSize: Array[Int],
                               linkFromCountryCode: Array[String],
                               linkToCountryCode: Array[String],
                               directedEdgeLinkIndex: Array[Int],
                               directedEdgeFromAirportId: Array[Int],
                               directedEdgeToAirportId: Array[Int],
                               directedEdgeInverted: Array[Boolean],
                               adjacencyOffsets: Array[Int],
                               adjacencyEdges: Array[Int],
                               maxAirportId: Int,
                               allianceIdByAirlineId: java.util.Map[Int, Int],
                               routeCostSeed: Option[Long])

  private final class PrimitiveRouteScratch {
    var edgeLink = new Array[Transport](0)
    var edgeLinkClass = new Array[LinkClass](0)
    var edgeInverted = new Array[Boolean](0)
    var edgeFromAirportId = new Array[Int](0)
    var edgeToAirportId = new Array[Int](0)
    var edgeLinkId = new Array[Int](0)
    var edgeLinkFromAirportId = new Array[Int](0)
    var edgeLinkToAirportId = new Array[Int](0)
    var edgeAirlineId = new Array[Int](0)
    var edgeTransportType = new Array[Int](0)
    var edgeFrequency = new Array[Int](0)
    var edgeTransferBaseCost = new Array[Double](0)
    var edgeTransferBaseCostGeneration = new Array[Int](0)
    var edgeFreedomGeneration = new Array[Int](0)
    var edgeHasFreedom = new Array[Boolean](0)
    var edgeCostGroup = new Array[Int](0)
    var groupCostComputed = new Array[Boolean](0)
    var groupCost = new Array[Double](0)
    var linkStateGeneration = new Array[Int](0)
    var linkHasSeats = new Array[Boolean](0)
    var linkClassByIndex = new Array[LinkClass](0)
    var linkFrequencyByIndex = new Array[Int](0)
    var linkCostGeneration = new Array[Int](0)
    var linkCost = new Array[Double](0)
    var distance = new Array[Double](0)
    var predecessorEdge = new Array[Int](0)
    var predecessorCost = new Array[Double](0)
    var nextPredecessorEdge = new Array[Int](0)
    var nextPredecessorCost = new Array[Double](0)
    var activeAirports = new Array[Boolean](0)
    var nextActiveAirports = new Array[Boolean](0)
    var outgoingCounts = new Array[Int](0)
    var adjacencyOffsets = new Array[Int](0)
    var adjacencyWriteOffsets = new Array[Int](0)
    var adjacencyEdges = new Array[Int](0)
    var activeAirportList = new Array[Int](0)
    var nextActiveAirportList = new Array[Int](0)
    var activeEdges = new Array[Int](0)
    var activeEdgeBits = new Array[Long](0)
    private var currentGeneration = 1

    def ensureEdgeCapacity(capacity: Int): Unit = {
      if (edgeFromAirportId.length < capacity) {
        val newCapacity = math.max(capacity, math.max(16, edgeFromAirportId.length * 2))
        edgeLink = new Array[Transport](newCapacity)
        edgeLinkClass = new Array[LinkClass](newCapacity)
        edgeInverted = new Array[Boolean](newCapacity)
        edgeFromAirportId = new Array[Int](newCapacity)
        edgeToAirportId = new Array[Int](newCapacity)
        edgeLinkId = new Array[Int](newCapacity)
        edgeLinkFromAirportId = new Array[Int](newCapacity)
        edgeLinkToAirportId = new Array[Int](newCapacity)
        edgeAirlineId = new Array[Int](newCapacity)
        edgeTransportType = new Array[Int](newCapacity)
        edgeFrequency = new Array[Int](newCapacity)
        edgeTransferBaseCost = new Array[Double](newCapacity)
        edgeTransferBaseCostGeneration = new Array[Int](newCapacity)
        edgeFreedomGeneration = new Array[Int](newCapacity)
        edgeHasFreedom = new Array[Boolean](newCapacity)
        edgeCostGroup = new Array[Int](newCapacity)
        adjacencyEdges = new Array[Int](newCapacity)
        activeEdges = new Array[Int](newCapacity)
        activeEdgeBits = new Array[Long]((newCapacity + 63) >>> 6)
      } else {
        val activeEdgeWordCapacity = (capacity + 63) >>> 6
        if (activeEdgeBits.length < activeEdgeWordCapacity) {
          activeEdgeBits = new Array[Long](activeEdgeWordCapacity)
        }
      }
    }

    def ensureGroupCapacity(capacity: Int): Unit = {
      if (groupCost.length < capacity) {
        val newCapacity = math.max(capacity, math.max(16, groupCost.length * 2))
        groupCostComputed = new Array[Boolean](newCapacity)
        groupCost = new Array[Double](newCapacity)
        linkStateGeneration = new Array[Int](newCapacity)
        linkHasSeats = new Array[Boolean](newCapacity)
        linkClassByIndex = new Array[LinkClass](newCapacity)
        linkFrequencyByIndex = new Array[Int](newCapacity)
        linkCostGeneration = new Array[Int](newCapacity)
        linkCost = new Array[Double](newCapacity)
      }
    }

    def ensureAirportCapacity(capacity: Int): Unit = {
      if (distance.length < capacity) {
        val newCapacity = math.max(capacity, math.max(16, distance.length * 2))
        distance = new Array[Double](newCapacity)
        predecessorEdge = new Array[Int](newCapacity)
        predecessorCost = new Array[Double](newCapacity)
        nextPredecessorEdge = new Array[Int](newCapacity)
        nextPredecessorCost = new Array[Double](newCapacity)
        activeAirports = new Array[Boolean](newCapacity)
        nextActiveAirports = new Array[Boolean](newCapacity)
        outgoingCounts = new Array[Int](newCapacity)
        adjacencyOffsets = new Array[Int](newCapacity + 1)
        adjacencyWriteOffsets = new Array[Int](newCapacity + 1)
        activeAirportList = new Array[Int](newCapacity)
        nextActiveAirportList = new Array[Int](newCapacity)
      }
    }

    def nextGeneration(): Int = {
      if (currentGeneration == Int.MaxValue) {
        java.util.Arrays.fill(edgeTransferBaseCostGeneration, 0)
        java.util.Arrays.fill(edgeFreedomGeneration, 0)
        java.util.Arrays.fill(linkStateGeneration, 0)
        java.util.Arrays.fill(linkCostGeneration, 0)
        currentGeneration = 1
      }
      val generation = currentGeneration
      currentGeneration += 1
      generation
    }
  }

  private val primitiveRouteScratch = new ThreadLocal[PrimitiveRouteScratch] {
    override def initialValue(): PrimitiveRouteScratch = new PrimitiveRouteScratch
  }

  def buildLoopContext(linksList: List[Transport],
                       allianceIdByAirlineId: java.util.Map[Int, Int] = util.Collections.emptyMap[Int, Int](),
                       standardDurationForDistance: Int => Int = Computation.computeStandardFlightDuration): LoopContext = {
    val links = new Array[Transport](linksList.size)
    val linkId = new Array[Int](links.length)
    val linkFromAirportId = new Array[Int](links.length)
    val linkToAirportId = new Array[Int](links.length)
    val linkAirlineId = new Array[Int](links.length)
    val linkAllianceId = new Array[Int](links.length)
    val linkDistance = new Array[Int](links.length)
    val linkDuration = new Array[Int](links.length)
    val linkFrequency = new Array[Int](links.length)
    val linkComputedQuality = new Array[Int](links.length)
    val linkStandardDuration = new Array[Int](links.length)
    val linkStandardPrice = new Array[Int](links.length * PassengerTypeCount * LinkClassCount)
    val linkTravelerZeroIncomeStandardPrice = new Array[Int](links.length * LinkClassCount)
    val linkTransportType = new Array[Int](links.length)
    val linkFromAirportSize = new Array[Int](links.length)
    val linkToAirportSize = new Array[Int](links.length)
    val linkFromCountryCode = new Array[String](links.length)
    val linkToCountryCode = new Array[String](links.length)

    var remainingLinks = linksList
    var index = 0
    var maxAirportId = 0
    while (remainingLinks.nonEmpty) {
      val link = remainingLinks.head
      links(index) = link
      linkId(index) = link.id
      linkFromAirportId(index) = link.from.id
      linkToAirportId(index) = link.to.id
      linkAirlineId(index) = link.airline.id
      linkAllianceId(index) = allianceValue(allianceIdByAirlineId, linkAirlineId(index))
      linkDistance(index) = link.distance
      linkDuration(index) = link.duration
      linkFrequency(index) = link.frequency
      linkComputedQuality(index) = link.computedQuality()
      linkStandardDuration(index) = standardDurationForDistance(link.distance)
      PassengerType.values.foreach { passengerType =>
        LinkClass.values.foreach { linkClass =>
          linkStandardPrice(standardPriceOffset(index, passengerType, linkClass)) = link.standardPrice(linkClass, passengerType)
        }
      }
      val flightCategory = Computation.getFlightCategory(link.from, link.to)
      LinkClass.values.foreach { linkClass =>
        linkTravelerZeroIncomeStandardPrice(travelerZeroIncomeStandardPriceOffset(index, linkClass)) =
          Pricing.computeStandardPrice(link.distance, flightCategory, linkClass, PassengerType.TRAVELER, 0)
      }
      linkTransportType(index) = link.transportType.id
      linkFromAirportSize(index) = link.from.size
      linkToAirportSize(index) = link.to.size
      linkFromCountryCode(index) = link.from.countryCode
      linkToCountryCode(index) = link.to.countryCode
      if (linkFromAirportId(index) > maxAirportId) {
        maxAirportId = linkFromAirportId(index)
      }
      if (linkToAirportId(index) > maxAirportId) {
        maxAirportId = linkToAirportId(index)
      }
      remainingLinks = remainingLinks.tail
      index += 1
    }

    val directedEdgeCount = links.length * 2
    val directedEdgeLinkIndex = new Array[Int](directedEdgeCount)
    val directedEdgeFromAirportId = new Array[Int](directedEdgeCount)
    val directedEdgeToAirportId = new Array[Int](directedEdgeCount)
    val directedEdgeInverted = new Array[Boolean](directedEdgeCount)

    var directedEdgeIndex = 0
    index = 0
    while (index < links.length) {
      directedEdgeLinkIndex(directedEdgeIndex) = index
      directedEdgeFromAirportId(directedEdgeIndex) = linkFromAirportId(index)
      directedEdgeToAirportId(directedEdgeIndex) = linkToAirportId(index)
      directedEdgeInverted(directedEdgeIndex) = false
      directedEdgeIndex += 1

      directedEdgeLinkIndex(directedEdgeIndex) = index
      directedEdgeFromAirportId(directedEdgeIndex) = linkToAirportId(index)
      directedEdgeToAirportId(directedEdgeIndex) = linkFromAirportId(index)
      directedEdgeInverted(directedEdgeIndex) = true
      directedEdgeIndex += 1
      index += 1
    }

    val airportCapacity = maxAirportId + 1
    val outgoingCounts = new Array[Int](airportCapacity)
    directedEdgeIndex = 0
    while (directedEdgeIndex < directedEdgeCount) {
      outgoingCounts(directedEdgeFromAirportId(directedEdgeIndex)) += 1
      directedEdgeIndex += 1
    }

    val adjacencyOffsets = new Array[Int](airportCapacity + 1)
    index = 0
    while (index < airportCapacity) {
      adjacencyOffsets(index + 1) = adjacencyOffsets(index) + outgoingCounts(index)
      index += 1
    }

    val adjacencyWriteOffsets = adjacencyOffsets.clone()
    val adjacencyEdges = new Array[Int](directedEdgeCount)
    directedEdgeIndex = 0
    while (directedEdgeIndex < directedEdgeCount) {
      val fromAirportId = directedEdgeFromAirportId(directedEdgeIndex)
      val position = adjacencyWriteOffsets(fromAirportId)
      adjacencyEdges(position) = directedEdgeIndex
      adjacencyWriteOffsets(fromAirportId) = position + 1
      directedEdgeIndex += 1
    }

    LoopContext(
      links,
      linkId,
      linkFromAirportId,
      linkToAirportId,
      linkAirlineId,
      linkAllianceId,
      linkDistance,
      linkDuration,
      linkFrequency,
      linkComputedQuality,
      linkStandardDuration,
      linkStandardPrice,
      linkTravelerZeroIncomeStandardPrice,
      linkTransportType,
      linkFromAirportSize,
      linkToAirportSize,
      linkFromCountryCode,
      linkToCountryCode,
      directedEdgeLinkIndex,
      directedEdgeFromAirportId,
      directedEdgeToAirportId,
      directedEdgeInverted,
      adjacencyOffsets,
      adjacencyEdges,
      maxAirportId,
      allianceIdByAirlineId,
      SimulationConfig.passengerRouteCostSeed
    )
  }

  def findRoutesByPassengerGroup(passengerGroup: PassengerGroup,
                                 toAirports: mutable.Set[Airport],
                                 linksList: List[Transport],
                                 countryOpenness: Map[String, Int],
                                 establishedAllianceIdByAirlineId: java.util.Map[Int, Int],
                                 externalCostModifier: Option[CostModifier],
                                 iterationCount: Int,
                                 isSingleTicket: Boolean,
                                 transferBaseSpecializationDiscounts: Map[(Int, Int), TransferSpecialization]): Map[Airport, Route] = {
    findRoutesByPassengerGroup(
      buildLoopContext(linksList, establishedAllianceIdByAirlineId),
      passengerGroup,
      toAirports,
      countryOpenness,
      establishedAllianceIdByAirlineId,
      externalCostModifier,
      iterationCount,
      isSingleTicket,
      transferBaseSpecializationDiscounts
    )
  }

  def findRoutesByPassengerGroup(loopContext: LoopContext,
                                 passengerGroup: PassengerGroup,
                                 toAirports: scala.collection.Set[Airport],
                                 countryOpenness: Map[String, Int],
                                 establishedAllianceIdByAirlineId: java.util.Map[Int, Int],
                                 externalCostModifier: Option[CostModifier],
                                 iterationCount: Int,
                                 isSingleTicket: Boolean,
                                 transferBaseSpecializationDiscounts: Map[(Int, Int), TransferSpecialization] = PassengerSimulation.transferBaseSpecializationDiscounts): Map[Airport, Route] = {
    findShortestRoutePrimitive(
      loopContext,
      passengerGroup,
      toAirports,
      countryOpenness,
      establishedAllianceIdByAirlineId,
      externalCostModifier,
      iterationCount,
      isSingleTicket,
      transferBaseSpecializationDiscounts
    )
  }

  def findRoutesByPassengerGroupAdjacency(passengerGroup: PassengerGroup,
                                          toAirports: mutable.Set[Airport],
                                          linksList: List[Transport],
                                          countryOpenness: Map[String, Int],
                                          establishedAllianceIdByAirlineId: java.util.Map[Int, Int],
                                          externalCostModifier: Option[CostModifier],
                                          iterationCount: Int,
                                          isSingleTicket: Boolean,
                                          transferBaseSpecializationDiscounts: Map[(Int, Int), TransferSpecialization] = PassengerSimulation.transferBaseSpecializationDiscounts): Map[Airport, Route] = {
    val (linkConsiderations, activeAirports) = buildRouteInput(passengerGroup, toAirports, linksList, countryOpenness, externalCostModifier)
    findShortestRouteAdjacency(passengerGroup, toAirports, activeAirports, linkConsiderations, establishedAllianceIdByAirlineId, iterationCount, isSingleTicket, transferBaseSpecializationDiscounts)
  }

  private def buildRouteInput(passengerGroup: PassengerGroup,
                              toAirports: mutable.Set[Airport],
                              linksList: List[Transport],
                              countryOpenness: Map[String, Int],
                              externalCostModifier: Option[CostModifier],
                              collectActiveAirports: Boolean = true): (util.ArrayList[LinkConsideration], mutable.Set[Int]) = {
    val preferredLinkClass = passengerGroup.preference.preferredLinkClass
    val linkConsiderations = new util.ArrayList[LinkConsideration]()
    val activeAirports = mutable.Set[Int]()
    val furthestDistance = PassengerSimulation.LINK_DISTANCE_TOLERANCE_FACTOR * PassengerSimulation.findFurthestAirportDistance(passengerGroup.fromAirport, toAirports)

    var remainingLinks = linksList
    while (remainingLinks.nonEmpty) {
      val link = remainingLinks.head
      if (link.distance <= furthestDistance) {
        link.availableSeatsAtOrBelowClass(preferredLinkClass).foreach {
          case (matchingLinkClass, _) =>
            val costProvider = CostStoreProvider()
            val linkConsideration1 = LinkConsideration(link, matchingLinkClass, false, passengerGroup, externalCostModifier, costProvider)
            val linkConsideration2 = LinkConsideration(link, matchingLinkClass, true, passengerGroup, externalCostModifier, costProvider)

            if (PassengerSimulation.hasFreedom(linkConsideration1, passengerGroup.fromAirport, countryOpenness, link.from.size)) {
              linkConsiderations.add(linkConsideration1)
              if (collectActiveAirports) {
                activeAirports.add(link.from.id)
                activeAirports.add(link.to.id)
              }
            }
            if (PassengerSimulation.hasFreedom(linkConsideration2, passengerGroup.fromAirport, countryOpenness, link.to.size)) {
              linkConsiderations.add(linkConsideration2)
              if (collectActiveAirports) {
                activeAirports.add(link.from.id)
                activeAirports.add(link.to.id)
              }
            }
        }
      }
      remainingLinks = remainingLinks.tail
    }

    (linkConsiderations, activeAirports)
  }

  private def findShortestRoutePrimitive(loopContext: LoopContext,
                                         passengerGroup: PassengerGroup,
                                         toAirports: scala.collection.Set[Airport],
                                         countryOpenness: Map[String, Int],
                                         allianceIdByAirlineId: java.util.Map[Int, Int],
                                         externalCostModifier: Option[CostModifier],
                                         maxIteration: Int,
                                         isSingleTicket: Boolean,
                                         transferBaseSpecializationDiscounts: Map[(Int, Int), TransferSpecialization]): Map[Airport, Route] = {
    if (loopContext.links.isEmpty) {
      return Map.empty
    }

    val scratch = primitiveRouteScratch.get()
    scratch.ensureEdgeCapacity(loopContext.directedEdgeLinkIndex.length)
    scratch.ensureGroupCapacity(loopContext.links.length)
    val generation = scratch.nextGeneration()

    val preferredLinkClass = passengerGroup.preference.preferredLinkClass
    val passengerType = passengerGroup.passengerType
    val sourceAirport = passengerGroup.fromAirport
    val sourceAirportId = sourceAirport.id
    val furthestDistance = PassengerSimulation.LINK_DISTANCE_TOLERANCE_FACTOR * findFurthestAirportDistance(sourceAirport, toAirports)
    val connectionIncomeMultiplier = Math.min(1.0, 0.4 + 0.6 * sourceAirport.income.toDouble / Airport.HIGH_INCOME)
    val connectionClassMultiplier = preferredLinkClass.spaceMultiplier
    val connectionPreferenceMultiplier = passengerGroup.preference.connectionCostRatio
    val linkAllianceId =
      if (loopContext.allianceIdByAirlineId eq allianceIdByAirlineId) loopContext.linkAllianceId else null

    var maxAirportId = math.max(sourceAirportId, loopContext.maxAirportId)
    var toAirportIterator = toAirports.iterator
    while (toAirportIterator.hasNext) {
      val airportId = toAirportIterator.next().id
      if (airportId > maxAirportId) {
        maxAirportId = airportId
      }
    }

    val airportCapacity = maxAirportId + 1
    scratch.ensureAirportCapacity(airportCapacity)
    val distance = scratch.distance
    java.util.Arrays.fill(distance, 0, airportCapacity, InitialDistance)
    distance(sourceAirportId) = 0

    var predecessorEdge = scratch.predecessorEdge
    var predecessorCost = scratch.predecessorCost
    var nextPredecessorEdge = scratch.nextPredecessorEdge
    var nextPredecessorCost = scratch.nextPredecessorCost
    var nextActiveAirports = scratch.nextActiveAirports
    java.util.Arrays.fill(predecessorEdge, 0, airportCapacity, -1)
    java.util.Arrays.fill(predecessorCost, 0, airportCapacity, 0.0)

    var activeAirportList = scratch.activeAirportList
    var nextActiveAirportList = scratch.nextActiveAirportList
    var activeAirportCount = 1
    activeAirportList(0) = sourceAirportId
    val activeEdgeBits = scratch.activeEdgeBits
    val activeEdgeWordCount = (loopContext.directedEdgeLinkIndex.length + 63) >>> 6

    var iteration = 0
    while (iteration < maxIteration && activeAirportCount > 0) {
      System.arraycopy(predecessorEdge, 0, nextPredecessorEdge, 0, airportCapacity)
      System.arraycopy(predecessorCost, 0, nextPredecessorCost, 0, airportCapacity)
      java.util.Arrays.fill(nextActiveAirports, 0, airportCapacity, false)
      java.util.Arrays.fill(activeEdgeBits, 0, activeEdgeWordCount, 0L)

      var activeAirportCursor = 0
      while (activeAirportCursor < activeAirportCount) {
        val activeAirportId = activeAirportList(activeAirportCursor)
        if (activeAirportId <= loopContext.maxAirportId) {
          var adjacencyCursor = loopContext.adjacencyOffsets(activeAirportId)
          val adjacencyEnd = loopContext.adjacencyOffsets(activeAirportId + 1)
          while (adjacencyCursor < adjacencyEnd) {
            val activeEdge = loopContext.adjacencyEdges(adjacencyCursor)
            activeEdgeBits(activeEdge >>> 6) |= 1L << (activeEdge & 63)
            adjacencyCursor += 1
          }
        }
        activeAirportCursor += 1
      }

      var nextActiveAirportCount = 0
      var activeEdgeWordIndex = 0
      while (activeEdgeWordIndex < activeEdgeWordCount) {
        var activeEdgeWord = activeEdgeBits(activeEdgeWordIndex)
        while (activeEdgeWord != 0L) {
          val edgeIndex = (activeEdgeWordIndex << 6) + java.lang.Long.numberOfTrailingZeros(activeEdgeWord)
          activeEdgeWord &= activeEdgeWord - 1
          val linkIndex = loopContext.directedEdgeLinkIndex(edgeIndex)
          val fromAirportId = loopContext.directedEdgeFromAirportId(edgeIndex)
          val toAirportId = loopContext.directedEdgeToAirportId(edgeIndex)
          if (distance(fromAirportId) < distance(toAirportId) &&
              loopContext.linkDistance(linkIndex) <= furthestDistance &&
              selectedLinkClass(scratch, generation, loopContext, linkIndex, preferredLinkClass) != null &&
              hasFreedomForDirectedEdgeCached(loopContext, scratch, generation, edgeIndex, sourceAirport, countryOpenness)) {
            val previousEdgeIndex = predecessorEdge(fromAirportId)
            var connectionCost = 0.0
            var isValid = true

            if (previousEdgeIndex >= 0) {
              val previousLinkIndex = loopContext.directedEdgeLinkIndex(previousEdgeIndex)
              if (loopContext.linkId(linkIndex) == loopContext.linkId(previousLinkIndex)) {
                isValid = false
              } else if (loopContext.linkTransportType(previousLinkIndex) == GenericTransitId && loopContext.linkTransportType(linkIndex) == GenericTransitId) {
                isValid = false
              } else if (loopContext.linkTransportType(previousLinkIndex) == GenericTransitId) {
                if (loopContext.linkFromAirportId(previousLinkIndex) == sourceAirportId || loopContext.linkToAirportId(previousLinkIndex) == sourceAirportId) {
                  connectionCost = 0
                } else {
                  connectionCost = 250
                }
              } else if (loopContext.linkTransportType(linkIndex) == GenericTransitId) {
                connectionCost = 0
              } else {
                connectionCost = transferBaseCostForDirectedEdge(loopContext, scratch, generation, edgeIndex, passengerType, transferBaseSpecializationDiscounts)

                val frequency = Math.max(
                  selectedLinkFrequency(scratch, generation, loopContext, previousLinkIndex, preferredLinkClass),
                  selectedLinkFrequency(scratch, generation, loopContext, linkIndex, preferredLinkClass)
                )
                if (frequency < 7) {
                  connectionCost += 165 + (7 - frequency) * 10
                } else if (frequency < 14) {
                  connectionCost += 65 + (14 - frequency) * 10
                } else if (frequency <= 49) {
                  connectionCost += (98 - frequency * 2)
                }

                val previousLinkAirlineId = loopContext.linkAirlineId(previousLinkIndex)
                val currentLinkAirlineId = loopContext.linkAirlineId(linkIndex)
                val previousAllianceId =
                  if (linkAllianceId != null) linkAllianceId(previousLinkIndex) else allianceValue(allianceIdByAirlineId, previousLinkAirlineId)
                val currentAllianceId =
                  if (linkAllianceId != null) linkAllianceId(linkIndex) else allianceValue(allianceIdByAirlineId, currentLinkAirlineId)
                if (previousLinkAirlineId != currentLinkAirlineId && (previousAllianceId == 0 || previousAllianceId != currentAllianceId)) {
                  connectionCost += 40
                  if (isSingleTicket) {
                    isValid = false
                  }
                }
              }
              connectionCost *= connectionIncomeMultiplier
              connectionCost *= connectionClassMultiplier
              connectionCost *= connectionPreferenceMultiplier
            }

            if (isValid && distance(fromAirportId) + connectionCost < distance(toAirportId)) {
              val cost = Math.max(0, routeCost(loopContext, scratch, generation, linkIndex, passengerGroup, externalCostModifier, preferredLinkClass) + connectionCost)
              val newCost = distance(fromAirportId) + cost
              if (newCost < distance(toAirportId)) {
                distance(toAirportId) = newCost
                nextPredecessorEdge(toAirportId) = edgeIndex
                nextPredecessorCost(toAirportId) = cost
                if (!nextActiveAirports(toAirportId)) {
                  nextActiveAirports(toAirportId) = true
                  nextActiveAirportList(nextActiveAirportCount) = toAirportId
                  nextActiveAirportCount += 1
                }
              }
            }
          }
        }
        activeEdgeWordIndex += 1
      }

      val predecessorEdgeSwap = predecessorEdge
      predecessorEdge = nextPredecessorEdge
      nextPredecessorEdge = predecessorEdgeSwap

      val predecessorCostSwap = predecessorCost
      predecessorCost = nextPredecessorCost
      nextPredecessorCost = predecessorCostSwap

      val activeAirportListSwap = activeAirportList
      activeAirportList = nextActiveAirportList
      nextActiveAirportList = activeAirportListSwap
      activeAirportCount = nextActiveAirportCount
      iteration += 1
    }

    val resultMap = mutable.Map[Airport, Route]()
    toAirportIterator = toAirports.iterator
    while (toAirportIterator.hasNext) {
      val to = toAirportIterator.next()
      if (to.id <= maxAirportId) {
        var walkerAirportId = to.id
        var noSolution = false
        var foundSolution = false
        var hasFlight = false
        val route = ListBuffer[LinkConsideration]()

        while (!foundSolution && !noSolution) {
          val edge = predecessorEdge(walkerAirportId)
          if (edge >= 0) {
            val linkIndex = loopContext.directedEdgeLinkIndex(edge)
            route.prepend(LinkConsideration(
              loopContext.links(linkIndex),
              selectedLinkClass(scratch, generation, loopContext, linkIndex, preferredLinkClass),
              loopContext.directedEdgeInverted(edge),
              passengerGroup,
              externalCostModifier,
              SimpleCostProvider(predecessorCost(walkerAirportId))
            ))
            if (loopContext.linkTransportType(linkIndex) == FlightId) {
              hasFlight = true
            }
            walkerAirportId = loopContext.directedEdgeFromAirportId(edge)
            if (loopContext.directedEdgeFromAirportId(edge) == sourceAirportId && hasFlight) {
              foundSolution = true
            }
          } else {
            noSolution = true
          }
        }

        if (foundSolution) {
          resultMap.put(to, Route(route.toList, distance(to.id)))
        }
      }
    }

    resultMap.toMap
  }

  def findShortestRoute(passengerGroup: PassengerGroup,
                        toAirports: mutable.Set[Airport],
                        allVertices: mutable.Set[Int],
                        linkConsiderations: java.util.List[LinkConsideration],
                        allianceIdByAirlineId: java.util.Map[Int, Int],
                        maxIteration: Int,
                        isSingleTicket: Boolean = false,
                        transferBaseSpecializationDiscounts: Map[(Int, Int), TransferSpecialization] = PassengerSimulation.transferBaseSpecializationDiscounts): Map[Airport, Route] = {
    if (linkConsiderations.isEmpty) {
      return Map.empty
    }

    val edgeCount = linkConsiderations.size()
    val edgeFromAirportId = new Array[Int](edgeCount)
    val edgeToAirportId = new Array[Int](edgeCount)
    val edgeLinkId = new Array[Int](edgeCount)
    val edgeLinkFromAirportId = new Array[Int](edgeCount)
    val edgeLinkToAirportId = new Array[Int](edgeCount)
    val edgeAirlineId = new Array[Int](edgeCount)
    val edgeTransportType = new Array[Int](edgeCount)
    val edgeFrequency = new Array[Int](edgeCount)
    var maxAirportId = passengerGroup.fromAirport.id
    toAirports.foreach { toAirport =>
      if (toAirport.id > maxAirportId) {
        maxAirportId = toAirport.id
      }
    }

    var edgeIndex = 0
    while (edgeIndex < edgeCount) {
      val linkConsideration = linkConsiderations.get(edgeIndex)
      edgeFromAirportId(edgeIndex) = linkConsideration.from.id
      edgeToAirportId(edgeIndex) = linkConsideration.to.id
      edgeLinkId(edgeIndex) = linkConsideration.link.id
      edgeLinkFromAirportId(edgeIndex) = linkConsideration.link.from.id
      edgeLinkToAirportId(edgeIndex) = linkConsideration.link.to.id
      edgeAirlineId(edgeIndex) = linkConsideration.link.airline.id
      edgeTransportType(edgeIndex) = linkConsideration.link.transportType.id
      edgeFrequency(edgeIndex) = linkConsideration.link.frequencyByClass(linkConsideration.linkClass)
      if (edgeFromAirportId(edgeIndex) > maxAirportId) {
        maxAirportId = edgeFromAirportId(edgeIndex)
      }
      if (edgeToAirportId(edgeIndex) > maxAirportId) {
        maxAirportId = edgeToAirportId(edgeIndex)
      }
      edgeIndex += 1
    }

    val distance = Array.fill(maxAirportId + 1)(InitialDistance)
    distance(passengerGroup.fromAirport.id) = 0

    var predecessorEdge = Array.fill(maxAirportId + 1)(-1)
    var predecessorCost = Array.fill(maxAirportId + 1)(0.0)
    var activeAirports = new Array[Boolean](maxAirportId + 1)
    activeAirports(passengerGroup.fromAirport.id) = true

    var iteration = 0
    while (iteration < maxIteration) {
      val previousPredecessorEdge = predecessorEdge
      val previousPredecessorCost = predecessorCost
      val newPredecessorEdge = predecessorEdge.clone()
      val newPredecessorCost = predecessorCost.clone()
      val newActiveAirports = new Array[Boolean](maxAirportId + 1)

      edgeIndex = 0
      while (edgeIndex < edgeCount) {
        val fromAirportId = edgeFromAirportId(edgeIndex)
        if (activeAirports(fromAirportId)) {
          val previousEdgeIndex = previousPredecessorEdge(fromAirportId)
          var connectionCost = 0.0
          var isValid = true

          if (previousEdgeIndex >= 0) {
            if (edgeLinkId(edgeIndex) == edgeLinkId(previousEdgeIndex)) {
              isValid = false
            } else if (edgeTransportType(previousEdgeIndex) == TransportType.GENERIC_TRANSIT.id && edgeTransportType(edgeIndex) == TransportType.GENERIC_TRANSIT.id) {
              isValid = false
            } else if (edgeTransportType(previousEdgeIndex) == TransportType.GENERIC_TRANSIT.id) {
              if (edgeLinkFromAirportId(previousEdgeIndex) == passengerGroup.fromAirport.id || edgeLinkToAirportId(previousEdgeIndex) == passengerGroup.fromAirport.id) {
                connectionCost = 0
              } else {
                connectionCost = 250
              }
            } else if (edgeTransportType(edgeIndex) == TransportType.GENERIC_TRANSIT.id) {
              connectionCost = 0
            } else {
              val transferBaseDiscounts = transferBaseSpecializationDiscounts.get((edgeFromAirportId(edgeIndex), edgeAirlineId(edgeIndex)))
              connectionCost = if (transferBaseDiscounts.exists(_.paxType.contains(passengerGroup.passengerType))) {
                24 * TravelerTransferSpecialization.transferCostDiscount
              } else {
                24
              }

              val frequency = Math.max(edgeFrequency(previousEdgeIndex), edgeFrequency(edgeIndex))
              if (frequency < 7) {
                connectionCost += 165 + (7 - frequency) * 10
              } else if (frequency < 14) {
                connectionCost += 65 + (14 - frequency) * 10
              } else if (frequency <= 49) {
                connectionCost += (98 - frequency * 2)
              }

              val previousLinkAirlineId = edgeAirlineId(previousEdgeIndex)
              val currentLinkAirlineId = edgeAirlineId(edgeIndex)
              val previousAllianceId = allianceId(allianceIdByAirlineId, previousLinkAirlineId)
              val currentAllianceId = allianceId(allianceIdByAirlineId, currentLinkAirlineId)
              if (previousLinkAirlineId != currentLinkAirlineId && (previousAllianceId.isEmpty || previousAllianceId != currentAllianceId)) {
                connectionCost += 40
                if (isSingleTicket) {
                  isValid = false
                }
              }
            }
            connectionCost *= Math.min(1.0, 0.4 + 0.6 * passengerGroup.fromAirport.income.toDouble / Airport.HIGH_INCOME)
            connectionCost *= passengerGroup.preference.preferredLinkClass.spaceMultiplier
            connectionCost *= passengerGroup.preference.connectionCostRatio
          }

          if (isValid) {
            val cost = Math.max(0, linkConsiderations.get(edgeIndex).cost + connectionCost)
            val toAirportId = edgeToAirportId(edgeIndex)
            val newCost = distance(fromAirportId) + cost
            if (newCost < distance(toAirportId)) {
              distance(toAirportId) = newCost
              newPredecessorEdge(toAirportId) = edgeIndex
              newPredecessorCost(toAirportId) = cost
              newActiveAirports(toAirportId) = true
            }
          }
        }
        edgeIndex += 1
      }

      predecessorEdge = newPredecessorEdge
      predecessorCost = newPredecessorCost
      activeAirports = newActiveAirports
      iteration += 1
    }

    val resultMap = mutable.Map[Airport, Route]()
    toAirports.foreach { to =>
      if (to.id <= maxAirportId) {
        var walkerAirportId = to.id
        var noSolution = false
        var foundSolution = false
        var hasFlight = false
        val route = ListBuffer[LinkConsideration]()

        while (!foundSolution && !noSolution) {
          val edge = predecessorEdge(walkerAirportId)
          if (edge >= 0) {
            route.prepend(linkConsiderations.get(edge).copyWithCost(predecessorCost(walkerAirportId)))
            if (edgeTransportType(edge) == TransportType.FLIGHT.id) {
              hasFlight = true
            }
            walkerAirportId = edgeFromAirportId(edge)
            if (edgeFromAirportId(edge) == passengerGroup.fromAirport.id && hasFlight) {
              foundSolution = true
            }
          } else {
            noSolution = true
          }
        }

        if (foundSolution) {
          resultMap.put(to, Route(route.toList, distance(to.id)))
        }
      }
    }

    resultMap.toMap
  }

  def findShortestRouteAdjacency(passengerGroup: PassengerGroup,
                                 toAirports: mutable.Set[Airport],
                                 allVertices: mutable.Set[Int],
                                 linkConsiderations: java.util.List[LinkConsideration],
                                 allianceIdByAirlineId: java.util.Map[Int, Int],
                                 maxIteration: Int,
                                 isSingleTicket: Boolean = false,
                                 transferBaseSpecializationDiscounts: Map[(Int, Int), TransferSpecialization] = PassengerSimulation.transferBaseSpecializationDiscounts): Map[Airport, Route] = {
    if (linkConsiderations.isEmpty || allVertices.isEmpty) {
      return Map.empty
    }

    val vertexIds = allVertices.toArray
    val vertexIndexByAirportId = new util.HashMap[Integer, Integer](Math.max(16, vertexIds.length * 2))
    var vertexIndex = 0
    while (vertexIndex < vertexIds.length) {
      vertexIndexByAirportId.put(vertexIds(vertexIndex), vertexIndex)
      vertexIndex += 1
    }

    val sourceVertexBox = vertexIndexByAirportId.get(passengerGroup.fromAirport.id)
    if (sourceVertexBox == null) {
      return Map.empty
    }

    val edgeCount = linkConsiderations.size()
    val edgeFromAirportId = new Array[Int](edgeCount)
    val edgeToAirportId = new Array[Int](edgeCount)
    val edgeFromVertex = new Array[Int](edgeCount)
    val edgeToVertex = new Array[Int](edgeCount)
    val edgeLinkId = new Array[Int](edgeCount)
    val edgeLinkFromAirportId = new Array[Int](edgeCount)
    val edgeLinkToAirportId = new Array[Int](edgeCount)
    val edgeAirlineId = new Array[Int](edgeCount)
    val edgeTransportType = new Array[Int](edgeCount)
    val edgeFrequency = new Array[Int](edgeCount)

    var edgeIndex = 0
    while (edgeIndex < edgeCount) {
      val linkConsideration = linkConsiderations.get(edgeIndex)
      edgeFromAirportId(edgeIndex) = linkConsideration.from.id
      edgeToAirportId(edgeIndex) = linkConsideration.to.id
      edgeFromVertex(edgeIndex) = vertexIndexByAirportId.get(edgeFromAirportId(edgeIndex))
      edgeToVertex(edgeIndex) = vertexIndexByAirportId.get(edgeToAirportId(edgeIndex))
      edgeLinkId(edgeIndex) = linkConsideration.link.id
      edgeLinkFromAirportId(edgeIndex) = linkConsideration.link.from.id
      edgeLinkToAirportId(edgeIndex) = linkConsideration.link.to.id
      edgeAirlineId(edgeIndex) = linkConsideration.link.airline.id
      edgeTransportType(edgeIndex) = linkConsideration.link.transportType.id
      edgeFrequency(edgeIndex) = linkConsideration.link.frequencyByClass(linkConsideration.linkClass)
      edgeIndex += 1
    }

    val sourceVertex = sourceVertexBox.intValue()
    val vertexCount = vertexIds.length
    val distance = Array.fill(vertexCount)(InitialDistance)
    distance(sourceVertex) = 0

    val outgoingCounts = new Array[Int](vertexCount)
    edgeIndex = 0
    while (edgeIndex < edgeCount) {
      outgoingCounts(edgeFromVertex(edgeIndex)) += 1
      edgeIndex += 1
    }

    val adjacencyOffsets = new Array[Int](vertexCount + 1)
    vertexIndex = 0
    while (vertexIndex < vertexCount) {
      adjacencyOffsets(vertexIndex + 1) = adjacencyOffsets(vertexIndex) + outgoingCounts(vertexIndex)
      vertexIndex += 1
    }

    val adjacencyWriteOffsets = adjacencyOffsets.clone()
    val adjacencyEdges = new Array[Int](edgeCount)
    edgeIndex = 0
    while (edgeIndex < edgeCount) {
      val fromVertex = edgeFromVertex(edgeIndex)
      val position = adjacencyWriteOffsets(fromVertex)
      adjacencyEdges(position) = edgeIndex
      adjacencyWriteOffsets(fromVertex) = position + 1
      edgeIndex += 1
    }

    var predecessorEdge = Array.fill(vertexCount)(-1)
    var predecessorCost = Array.fill(vertexCount)(0.0)
    var activeVertexList = new Array[Int](vertexCount)
    var activeVertexCount = 1
    activeVertexList(0) = sourceVertex

    var iteration = 0
    while (iteration < maxIteration && activeVertexCount > 0) {
      val previousPredecessorEdge = predecessorEdge
      val newPredecessorEdge = predecessorEdge.clone()
      val newPredecessorCost = predecessorCost.clone()
      val newActiveVertices = new Array[Boolean](vertexCount)
      val newActiveVertexList = new Array[Int](vertexCount)
      var newActiveVertexCount = 0

      var activeEdgeCount = 0
      var activeVertexCursor = 0
      while (activeVertexCursor < activeVertexCount) {
        val activeVertex = activeVertexList(activeVertexCursor)
        activeEdgeCount += adjacencyOffsets(activeVertex + 1) - adjacencyOffsets(activeVertex)
        activeVertexCursor += 1
      }

      val activeEdges = new Array[Int](activeEdgeCount)
      var activeEdgeCursor = 0
      activeVertexCursor = 0
      while (activeVertexCursor < activeVertexCount) {
        val activeVertex = activeVertexList(activeVertexCursor)
        var adjacencyCursor = adjacencyOffsets(activeVertex)
        val adjacencyEnd = adjacencyOffsets(activeVertex + 1)
        while (adjacencyCursor < adjacencyEnd) {
          activeEdges(activeEdgeCursor) = adjacencyEdges(adjacencyCursor)
          activeEdgeCursor += 1
          adjacencyCursor += 1
        }
        activeVertexCursor += 1
      }
      java.util.Arrays.sort(activeEdges)

      activeEdgeCursor = 0
      while (activeEdgeCursor < activeEdgeCount) {
        edgeIndex = activeEdges(activeEdgeCursor)
        val fromVertex = edgeFromVertex(edgeIndex)
        val previousEdgeIndex = previousPredecessorEdge(fromVertex)
        var connectionCost = 0.0
        var isValid = true

        if (previousEdgeIndex >= 0) {
          if (edgeLinkId(edgeIndex) == edgeLinkId(previousEdgeIndex)) {
            isValid = false
          } else if (edgeTransportType(previousEdgeIndex) == TransportType.GENERIC_TRANSIT.id && edgeTransportType(edgeIndex) == TransportType.GENERIC_TRANSIT.id) {
            isValid = false
          } else if (edgeTransportType(previousEdgeIndex) == TransportType.GENERIC_TRANSIT.id) {
            if (edgeLinkFromAirportId(previousEdgeIndex) == passengerGroup.fromAirport.id || edgeLinkToAirportId(previousEdgeIndex) == passengerGroup.fromAirport.id) {
              connectionCost = 0
            } else {
              connectionCost = 250
            }
          } else if (edgeTransportType(edgeIndex) == TransportType.GENERIC_TRANSIT.id) {
            connectionCost = 0
          } else {
            val transferBaseDiscounts = transferBaseSpecializationDiscounts.get((edgeFromAirportId(edgeIndex), edgeAirlineId(edgeIndex)))
            connectionCost = if (transferBaseDiscounts.exists(_.paxType.contains(passengerGroup.passengerType))) {
              24 * TravelerTransferSpecialization.transferCostDiscount
            } else {
              24
            }

            val frequency = Math.max(edgeFrequency(previousEdgeIndex), edgeFrequency(edgeIndex))
            if (frequency < 7) {
              connectionCost += 165 + (7 - frequency) * 10
            } else if (frequency < 14) {
              connectionCost += 65 + (14 - frequency) * 10
            } else if (frequency <= 49) {
              connectionCost += (98 - frequency * 2)
            }

            val previousLinkAirlineId = edgeAirlineId(previousEdgeIndex)
            val currentLinkAirlineId = edgeAirlineId(edgeIndex)
            val previousAllianceId = allianceId(allianceIdByAirlineId, previousLinkAirlineId)
            val currentAllianceId = allianceId(allianceIdByAirlineId, currentLinkAirlineId)
            if (previousLinkAirlineId != currentLinkAirlineId && (previousAllianceId.isEmpty || previousAllianceId != currentAllianceId)) {
              connectionCost += 40
              if (isSingleTicket) {
                isValid = false
              }
            }
          }
          connectionCost *= Math.min(1.0, 0.4 + 0.6 * passengerGroup.fromAirport.income.toDouble / Airport.HIGH_INCOME)
          connectionCost *= passengerGroup.preference.preferredLinkClass.spaceMultiplier
          connectionCost *= passengerGroup.preference.connectionCostRatio
        }

        if (isValid) {
          val cost = Math.max(0, linkConsiderations.get(edgeIndex).cost + connectionCost)
          val toVertex = edgeToVertex(edgeIndex)
          val newCost = distance(fromVertex) + cost
          if (newCost < distance(toVertex)) {
            distance(toVertex) = newCost
            newPredecessorEdge(toVertex) = edgeIndex
            newPredecessorCost(toVertex) = cost
            if (!newActiveVertices(toVertex)) {
              newActiveVertices(toVertex) = true
              newActiveVertexList(newActiveVertexCount) = toVertex
              newActiveVertexCount += 1
            }
          }
        }

        activeEdgeCursor += 1
      }

      predecessorEdge = newPredecessorEdge
      predecessorCost = newPredecessorCost
      activeVertexList = newActiveVertexList
      activeVertexCount = newActiveVertexCount
      iteration += 1
    }

    val resultMap = mutable.Map[Airport, Route]()
    toAirports.foreach { to =>
      val toVertexBox = vertexIndexByAirportId.get(to.id)
      if (toVertexBox != null) {
        val toVertex = toVertexBox.intValue()
        var walkerVertex = toVertex
        var noSolution = false
        var foundSolution = false
        var hasFlight = false
        val route = ListBuffer[LinkConsideration]()

        while (!foundSolution && !noSolution) {
          val edge = predecessorEdge(walkerVertex)
          if (edge >= 0) {
            route.prepend(linkConsiderations.get(edge).copyWithCost(predecessorCost(walkerVertex)))
            if (edgeTransportType(edge) == TransportType.FLIGHT.id) {
              hasFlight = true
            }
            walkerVertex = edgeFromVertex(edge)
            if (edgeFromAirportId(edge) == passengerGroup.fromAirport.id && hasFlight) {
              foundSolution = true
            }
          } else {
            noSolution = true
          }
        }

        if (foundSolution) {
          resultMap.put(to, Route(route.toList, distance(toVertex)))
        }
      }
    }

    resultMap.toMap
  }

  private def addPrimitiveEdge(scratch: PrimitiveRouteScratch,
                               edgeIndex: Int,
                               link: Transport,
                               linkClass: LinkClass,
                               inverted: Boolean,
                               fromAirportId: Int,
                               toAirportId: Int,
                               linkFromAirportId: Int,
                               linkToAirportId: Int,
                               airlineId: Int,
                               transportTypeId: Int,
                               costGroup: Int,
                               passengerType: PassengerType.Value,
                               transferBaseSpecializationDiscounts: Map[(Int, Int), TransferSpecialization] = PassengerSimulation.transferBaseSpecializationDiscounts): Unit = {
    scratch.edgeLink(edgeIndex) = link
    scratch.edgeLinkClass(edgeIndex) = linkClass
    scratch.edgeInverted(edgeIndex) = inverted
    scratch.edgeFromAirportId(edgeIndex) = fromAirportId
    scratch.edgeToAirportId(edgeIndex) = toAirportId
    scratch.edgeLinkId(edgeIndex) = link.id
    scratch.edgeLinkFromAirportId(edgeIndex) = linkFromAirportId
    scratch.edgeLinkToAirportId(edgeIndex) = linkToAirportId
    scratch.edgeAirlineId(edgeIndex) = airlineId
    scratch.edgeTransportType(edgeIndex) = transportTypeId
    scratch.edgeFrequency(edgeIndex) = link.frequencyByClass(linkClass)
    scratch.edgeTransferBaseCost(edgeIndex) = transferBaseCost(fromAirportId, airlineId, passengerType, transferBaseSpecializationDiscounts)
    scratch.edgeCostGroup(edgeIndex) = costGroup
  }

  private def hasFreedomPrimitive(fromCountryCode: String,
                                  toCountryCode: String,
                                  fromAirportId: Int,
                                  airlineId: Int,
                                  originatingAirport: Airport,
                                  countryOpenness: Map[String, Int],
                                  airportSize: Int): Boolean = {
    if (fromCountryCode == toCountryCode) {
      true
    } else if (fromCountryCode == originatingAirport.countryCode) {
      true
    } else if (airportSize >= 7) {
      true
    } else if (PassengerSimulation.megaHqHeadquarterAirports.contains((airlineId, fromAirportId))) {
      true
    } else {
      countryOpenness(fromCountryCode) >= Country.SIXTH_FREEDOM_MIN_OPENNESS
    }
  }

  private def hasFreedomForDirectedEdge(loopContext: LoopContext,
                                        edgeIndex: Int,
                                        originatingAirport: Airport,
                                        countryOpenness: Map[String, Int]): Boolean = {
    val linkIndex = loopContext.directedEdgeLinkIndex(edgeIndex)
    if (loopContext.directedEdgeInverted(edgeIndex)) {
      hasFreedomPrimitive(
        loopContext.linkToCountryCode(linkIndex),
        loopContext.linkFromCountryCode(linkIndex),
        loopContext.directedEdgeFromAirportId(edgeIndex),
        loopContext.linkAirlineId(linkIndex),
        originatingAirport,
        countryOpenness,
        loopContext.linkToAirportSize(linkIndex)
      )
    } else {
      hasFreedomPrimitive(
        loopContext.linkFromCountryCode(linkIndex),
        loopContext.linkToCountryCode(linkIndex),
        loopContext.directedEdgeFromAirportId(edgeIndex),
        loopContext.linkAirlineId(linkIndex),
        originatingAirport,
        countryOpenness,
        loopContext.linkFromAirportSize(linkIndex)
      )
    }
  }

  private def hasFreedomForDirectedEdgeCached(loopContext: LoopContext,
                                              scratch: PrimitiveRouteScratch,
                                              generation: Int,
                                              edgeIndex: Int,
                                              originatingAirport: Airport,
                                              countryOpenness: Map[String, Int]): Boolean = {
    if (scratch.edgeFreedomGeneration(edgeIndex) != generation) {
      scratch.edgeHasFreedom(edgeIndex) = hasFreedomForDirectedEdge(loopContext, edgeIndex, originatingAirport, countryOpenness)
      scratch.edgeFreedomGeneration(edgeIndex) = generation
    }
    scratch.edgeHasFreedom(edgeIndex)
  }

  private def selectedLinkClass(scratch: PrimitiveRouteScratch,
                                generation: Int,
                                loopContext: LoopContext,
                                linkIndex: Int,
                                preferredLinkClass: LinkClass): LinkClass = {
    if (scratch.linkStateGeneration(linkIndex) != generation) {
      loopContext.links(linkIndex).availableSeatsAtOrBelowClass(preferredLinkClass) match {
        case Some((matchingLinkClass, _)) =>
          scratch.linkHasSeats(linkIndex) = true
          scratch.linkClassByIndex(linkIndex) = matchingLinkClass
          scratch.linkFrequencyByIndex(linkIndex) = loopContext.links(linkIndex).frequencyByClass(matchingLinkClass)
        case None =>
          scratch.linkHasSeats(linkIndex) = false
          scratch.linkClassByIndex(linkIndex) = null
          scratch.linkFrequencyByIndex(linkIndex) = 0
      }
      scratch.linkStateGeneration(linkIndex) = generation
    }
    if (scratch.linkHasSeats(linkIndex)) scratch.linkClassByIndex(linkIndex) else null
  }

  private def selectedLinkFrequency(scratch: PrimitiveRouteScratch,
                                    generation: Int,
                                    loopContext: LoopContext,
                                    linkIndex: Int,
                                    preferredLinkClass: LinkClass): Int = {
    selectedLinkClass(scratch, generation, loopContext, linkIndex, preferredLinkClass)
    scratch.linkFrequencyByIndex(linkIndex)
  }

  private def routeCost(loopContext: LoopContext,
                        scratch: PrimitiveRouteScratch,
                        generation: Int,
                        linkIndex: Int,
                        passengerGroup: PassengerGroup,
                        externalCostModifier: Option[CostModifier],
                        preferredLinkClass: LinkClass): Double = {
    if (scratch.linkCostGeneration(linkIndex) != generation) {
      val link = loopContext.links(linkIndex)
      val linkClass = selectedLinkClass(scratch, generation, loopContext, linkIndex, preferredLinkClass)
      val modifier = externalCostModifier match {
        case Some(costModifier) => costModifier.value(link, linkClass, passengerGroup.passengerType)
        case None => 1.0
      }
      scratch.linkCost(linkIndex) = passengerGroup.preference match {
        case _: DealPreference | _: LastMinutePreference | _: AppealPreference =>
          productionRouteCost(loopContext, linkIndex, linkClass, passengerGroup, modifier)
        case _ =>
          passengerGroup.preference.computeCost(link, linkClass, passengerGroup.passengerType, modifier)
      }
      scratch.linkCostGeneration(linkIndex) = generation
    }
    scratch.linkCost(linkIndex)
  }

  private def productionRouteCost(loopContext: LoopContext,
                                  linkIndex: Int,
                                  linkClass: LinkClass,
                                  passengerGroup: PassengerGroup,
                                  externalCostModifier: Double): Double = {
    val preference = passengerGroup.preference
    val preferredLinkClass = preference.preferredLinkClass
    val passengerType = passengerGroup.passengerType
    val standardPrice = loopContext.linkStandardPrice(standardPriceOffset(linkIndex, passengerType, preferredLinkClass))
    var cost = standardPrice * priceAdjustRatio(loopContext, linkIndex, linkClass, passengerType, preference, standardPrice)
    cost = (cost * qualityAdjustRatio(loopContext, linkIndex, preferredLinkClass, passengerType, preference)).toInt
    cost = (cost * frequencyAdjustRatio(loopContext, linkIndex, linkClass, passengerType, preference)).toInt
    cost = (cost * tripDurationAdjustRatio(loopContext, linkIndex, linkClass, passengerType)).toInt

    if (preference.loyaltySensitivity > 0) {
      cost = (cost * loyaltyAdjustRatio(loopContext, linkIndex, preference)).toInt
    }

    cost = loungeAdjust(cost, loopContext.links(linkIndex), preference.loungeLevelRequired, preferredLinkClass)
    cost *= externalCostModifier
    cost *= preference.COST_BASIS
    productionPreferenceFinalCost(loopContext, preference, cost, loopContext.links(linkIndex), linkClass)
  }

  private def productionPreferenceFinalCost(loopContext: LoopContext,
                                            preference: FlightPreference,
                                            baseCost: Double,
                                            link: Transport,
                                            linkClass: LinkClass): Double = {
    val noise = preference match {
      case _: DealPreference =>
        0.9 + flatTopBellRandom(loopContext, preference, 0.2, 0.2, link, linkClass)
      case _: LastMinutePreference | _: AppealPreference =>
        0.8 + flatTopBellRandom(loopContext, preference, 0.3, 0.3, link, linkClass)
      case _ =>
        return preference.computeCost(baseCost, link, linkClass)
    }
    Math.max(1, baseCost * noise)
  }

  private def flatTopBellRandom(loopContext: LoopContext,
                                preference: FlightPreference,
                                topWidth: Double,
                                bellExtension: Double,
                                link: Transport,
                                linkClass: LinkClass): Double = {
    loopContext.routeCostSeed match {
      case Some(seed) =>
        val random = preference.deterministicRouteCostRandomForKernel(seed, "flatTopBell", link, linkClass, -1)
        topWidth / 2 - random.nextDouble() * topWidth + random.nextGaussian() * bellExtension
      case None =>
        preference.getFlatTopBellRandom(topWidth, bellExtension)
    }
  }

  private def priceAdjustRatio(loopContext: LoopContext,
                               linkIndex: Int,
                               linkClass: LinkClass,
                               passengerType: PassengerType.Value,
                               preference: FlightPreference,
                               standardPrice: Int): Double = {
    val classAdjustedPrice = priceAdjustedByLinkClassDiff(loopContext, linkIndex, linkClass, preference.preferredLinkClass)
    val deltaFromStandardPrice = classAdjustedPrice - standardPrice
    val preferenceType = preference.getPreferenceType
    val priceSensitivityModifier =
      if (deltaFromStandardPrice < 0 && (preferenceType == FlightPreferenceType.FREQUENT || preferenceType == FlightPreferenceType.BRAND || preferenceType == FlightPreferenceType.LAST_MINUTE)) {
        0.7 * preference.priceSensitivity * classAdjustedPrice.toDouble / standardPrice
      } else {
        preference.priceSensitivity
      }
    1.0 + deltaFromStandardPrice * priceSensitivityModifier / standardPrice
  }

  private def priceAdjustedByLinkClassDiff(loopContext: LoopContext,
                                           linkIndex: Int,
                                           linkClass: LinkClass,
                                           preferredLinkClass: LinkClass): Int = {
    val link = loopContext.links(linkIndex)
    val cost = link.cost(linkClass)
    if (preferredLinkClass.level != 0 && linkClass.level < preferredLinkClass.level) {
      val shortDistanceModified = 0.4 + 0.6 * Math.min(1, loopContext.linkDistance(linkIndex).toDouble / 800)
      val classDiffMultiplier = 1 + (preferredLinkClass.level - linkClass.level) * 0.35 * shortDistanceModified
      val basePreferredClassPrice = loopContext.linkTravelerZeroIncomeStandardPrice(travelerZeroIncomeStandardPriceOffset(linkIndex, preferredLinkClass))
      val baseLinkClassPrice = loopContext.linkTravelerZeroIncomeStandardPrice(travelerZeroIncomeStandardPriceOffset(linkIndex, linkClass))
      val preferredClassPriceRatio = basePreferredClassPrice.toDouble / baseLinkClassPrice

      (cost * preferredClassPriceRatio * classDiffMultiplier).toInt
    } else {
      cost
    }
  }

  private def qualityAdjustRatio(loopContext: LoopContext,
                                 linkIndex: Int,
                                 preferredLinkClass: LinkClass,
                                 passengerType: PassengerType.Value,
                                 preference: FlightPreference): Double = {
    val qualityDelta = loopContext.linkComputedQuality(linkIndex) - expectedQuality(preference.homeAirportForKernel, loopContext.linkDistance(linkIndex), preferredLinkClass)
    val goodQualityDelta = if (passengerType == PassengerType.ELITE) 30 else 20

    val priceAdjust =
      if (qualityDelta < 0 - goodQualityDelta) {
        1 - qualityDelta.toDouble / Link.MAX_QUALITY * 1.6
      } else if (qualityDelta < 0) {
        1 - qualityDelta.toDouble / Link.MAX_QUALITY * 1.1
      } else if (qualityDelta < goodQualityDelta) {
        1 - qualityDelta.toDouble / Link.MAX_QUALITY * 0.5
      } else {
        val extraDelta = qualityDelta - goodQualityDelta
        1 - goodQualityDelta.toDouble / Link.MAX_QUALITY * 0.5 - extraDelta.toDouble / Link.MAX_QUALITY * 0.2
      }

    if (loopContext.linkTransportType(linkIndex) == GenericTransitId) {
      1.0
    } else {
      1 + (priceAdjust - 1) * preference.qualitySensitivity
    }
  }

  private def expectedQuality(homeAirport: Airport, distance: Int, linkClass: LinkClass): Int = {
    val classBaseline = linkClass match {
      case FIRST => 40
      case BUSINESS => 25
      case ECONOMY => 15
      case DISCOUNT_ECONOMY => -5
    }
    val distanceClassMultiplier = linkClass match {
      case FIRST | BUSINESS => 3
      case ECONOMY | DISCOUNT_ECONOMY => 2
    }
    val distanceMod = Math.min(10.0, distance / 1000.0) * distanceClassMultiplier
    val incomeBaseline = Math.min((homeAirport.baseIncome.toDouble / Airport.HIGH_INCOME * 30).toInt, 30)

    Math.max(0, Math.min(100, classBaseline + incomeBaseline + distanceMod).toInt)
  }

  private def frequencyAdjustRatio(loopContext: LoopContext,
                                   linkIndex: Int,
                                   linkClass: LinkClass,
                                   passengerType: PassengerType.Value,
                                   preference: FlightPreference): Double = {
    val frequencySensitivity = passengerType match {
      case PassengerType.TRAVELER => 0.2
      case PassengerType.BUSINESS => 0.6
      case PassengerType.ELITE => 0.3
      case _ => 0.15
    }
    val distanceModifier =
      if (frequencySensitivity == 0) {
        0
      } else {
        1.0 - Math.min(0.85, loopContext.linkDuration(linkIndex).toDouble / 180.0)
      }

    val frequencyRange = preference.frequencyThreshold * 2
    val frequencyThresholdPerPax = loopContext.routeCostSeed match {
      case Some(seed) => preference.deterministicRouteCostRandomForKernel(seed, "frequencyThreshold", loopContext.links(linkIndex), linkClass, passengerType.id).nextInt(frequencyRange)
      case None => java.util.concurrent.ThreadLocalRandom.current().nextInt(frequencyRange)
    }

    val delta = Math.max(-4, (frequencyThresholdPerPax - loopContext.linkFrequency(linkIndex)).toDouble / frequencyThresholdPerPax)
    if (delta < 0) {
      1 + Math.max(-1 * frequencySensitivity * distanceModifier, delta * distanceModifier)
    } else {
      1 + Math.min(frequencySensitivity * distanceModifier, delta * distanceModifier)
    }
  }

  private def tripDurationAdjustRatio(loopContext: LoopContext,
                                      linkIndex: Int,
                                      preferredLinkClass: LinkClass,
                                      passengerType: PassengerType.Value): Double = {
    val classModifier = preferredLinkClass match {
      case FIRST => 0.5
      case BUSINESS => 0.35
      case _ => 0.1
    }
    val flightDurationSensitivity = passengerType match {
      case PassengerType.ELITE => 0.7
      case PassengerType.BUSINESS => 0.2 + classModifier
      case PassengerType.OLYMPICS => 0.1 + classModifier
      case _ => 0 + classModifier
    }
    val flightDurationRatioDelta =
      if (flightDurationSensitivity == 0) {
        0
      } else {
        val flightDurationThreshold = loopContext.linkStandardDuration(linkIndex)
        Math.min(flightDurationSensitivity, (loopContext.linkDuration(linkIndex) - flightDurationThreshold).toFloat / flightDurationThreshold * flightDurationSensitivity)
      }
    val maxDiscount = flightDurationSensitivity * -1
    val finalDelta = Math.max(maxDiscount, flightDurationRatioDelta)
    Math.min(1.6, 1 + finalDelta)
  }

  private def loyaltyAdjustRatio(loopContext: LoopContext,
                                 linkIndex: Int,
                                 preference: FlightPreference): Double = {
    val appeal = preference.appealList.getOrElse(loopContext.linkAirlineId(linkIndex), AirlineAppeal(0))
    val loyalty = appeal.loyalty
    val base = 1 + (-0.1 + loyalty / preference.maxLoyalty / 2.25) * preference.loyaltySensitivity
    1 / base
  }

  private def loungeAdjust(cost: Double, link: Transport, loungeLevelRequired: Int, linkClass: LinkClass): Double = {
    if (linkClass.level < BUSINESS.level) {
      cost
    } else {
      val fromLounge = link.from.getLounge(link.airline.id, link.airline.getAllianceId, activeOnly = true)
      val toLounge = link.to.getLounge(link.airline.id, link.airline.getAllianceId, activeOnly = true)

      val fromLoungeLevel = fromLounge.map(_.level).getOrElse(0)
      val toLoungeLevel = toLounge.map(_.level).getOrElse(0)

      var newCost = Lounge.priceAdjust(cost, fromLoungeLevel, loungeLevelRequired)
      newCost = Lounge.priceAdjust(newCost, toLoungeLevel, loungeLevelRequired)

      Math.max(1, newCost)
    }
  }

  private def transferBaseCostForDirectedEdge(loopContext: LoopContext,
                                              scratch: PrimitiveRouteScratch,
                                              generation: Int,
                                              edgeIndex: Int,
                                              passengerType: PassengerType.Value,
                                              transferBaseSpecializationDiscounts: Map[(Int, Int), TransferSpecialization]): Double = {
    if (scratch.edgeTransferBaseCostGeneration(edgeIndex) != generation) {
      val linkIndex = loopContext.directedEdgeLinkIndex(edgeIndex)
      scratch.edgeTransferBaseCost(edgeIndex) = transferBaseCost(
        loopContext.directedEdgeFromAirportId(edgeIndex),
        loopContext.linkAirlineId(linkIndex),
        passengerType,
        transferBaseSpecializationDiscounts
      )
      scratch.edgeTransferBaseCostGeneration(edgeIndex) = generation
    }
    scratch.edgeTransferBaseCost(edgeIndex)
  }

  private def transferBaseCost(fromAirportId: Int, airlineId: Int, passengerType: PassengerType.Value, transferBaseSpecializationDiscounts: Map[(Int, Int), TransferSpecialization]): Double = {
    val transferBaseDiscounts = transferBaseSpecializationDiscounts.get((fromAirportId, airlineId))
    if (transferBaseDiscounts.exists(_.paxType.contains(passengerType))) {
      24 * TravelerTransferSpecialization.transferCostDiscount
    } else {
      24
    }
  }

  private def findFurthestAirportDistance(fromAirport: Airport, toAirports: scala.collection.Set[Airport]): Int = {
    var maxDistance = 0
    val iterator = toAirports.iterator
    while (iterator.hasNext) {
      val distance = Computation.calculateDistance(fromAirport, iterator.next())
      if (distance > maxDistance) {
        maxDistance = distance
      }
    }
    maxDistance
  }

  private def standardPriceOffset(linkIndex: Int, passengerType: PassengerType.Value, linkClass: LinkClass): Int = {
    ((linkIndex * PassengerTypeCount + passengerType.id) * LinkClassCount) + linkClass.level
  }

  private def travelerZeroIncomeStandardPriceOffset(linkIndex: Int, linkClass: LinkClass): Int = {
    linkIndex * LinkClassCount + linkClass.level
  }

  private def allianceValue(allianceIdByAirlineId: java.util.Map[Int, Int], airlineId: Int): Int = {
    val id = allianceIdByAirlineId.get(airlineId)
    if (id == null.asInstanceOf[Int]) 0 else id
  }

  private def allianceId(allianceIdByAirlineId: java.util.Map[Int, Int], airlineId: Int): Option[Int] = {
    val id = allianceIdByAirlineId.get(airlineId)
    if (id == null.asInstanceOf[Int]) None else Some(id)
  }
}
