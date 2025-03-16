package com.patson

import java.util.Random
import com.patson.data._
import com.patson.model._
import com.patson.util.{AirlineCache, AirportCache, AirportChampionInfo, ChampionUtil}

import java.util.concurrent.ThreadLocalRandom
import scala.collection.{MapView, immutable, mutable}
import scala.collection.mutable.{ListBuffer, Map, Set}
import scala.math.BigDecimal.RoundingMode

object AirportSimulation {
  val LOYALTY_AUTO_INCREMENT_WITH_HQ = 0.05
  val LOYALTY_AUTO_INCREMENT_WITH_BASE = 0.02
  val LOYALTY_AUTO_INCREMENT_MAX_WITH_HQ = 30 //how much loyalty will increment to just because of being a HQ
  val LOYALTY_AUTO_INCREMENT_MAX_WITH_BASE = 15 //how much loyalty will increment to just because of being a HQ
  val LOYALTY_DECREMENT_BY_MINOR_DELAY = 0.5 //if all flights have minor delay
  val LOYALTY_DECREMENT_BY_MAJOR_DELAY = 2 //if all flights have major delay
  val LOYALTY_DECREMENT_BY_CANCELLATION = 4 //if all flights are cancelled
  
  private[patson] val LOYALTY_INCREMENT_BY_FLIGHTS = 1.0
  private[patson] val LOYALTY_DECREMENT_BY_FLIGHTS = 1.0


  def airportSimulation(cycle: Int, flightLinkResult : List[LinkConsumptionDetails], linkRidershipDetails : immutable.Map[(PassengerGroup, Airport, Route), Int]) = {
    println("starting airport simulation")
    println("loading all airports")
    //do decay
    val allAirports = AirportSource.loadAllAirports(true)
    println("finished loading all airports")

    val flightLinks = LinkSource.loadAllLinks(LinkSource.ID_LOAD).filter(_.transportType == TransportType.FLIGHT).map(_.asInstanceOf[Link])

    //update the loyalist on airports based on link consumption
    println("Adjust loyalist by link consumptions")
    val championInfo = simulateLoyalists(allAirports, linkRidershipDetails, cycle)

    //check whether lounge is still active
    updateLoungeStatus(allAirports, linkRidershipDetails)

    println("Finished loyalist simulation")

    AirportSource.purgeAirlineAppealBonus(cycle)

    championInfo
  }

  val LOYALIST_HISTORY_SAVE_INTERVAL = 10 //every 10 cycles
  val LOYALIST_HISTORY_ENTRY_MAX = 50

  def getHistoryCycle(lastCompletedCycle : Int, delta : Int): Int = {
    val baseCycle = lastCompletedCycle - lastCompletedCycle % LOYALIST_HISTORY_SAVE_INTERVAL
    baseCycle + delta * LOYALIST_HISTORY_SAVE_INTERVAL
  }

  def processChampionInfoChanges(previousInfo : List[AirportChampionInfo], newInfo : List[AirportChampionInfo], currentCycle : Int) = {
    val previousInfoByAirlineId : Predef.Map[Int, List[AirportChampionInfo]] = previousInfo.groupBy(_.loyalist.airline.id)
    val newInfoByAirlineId : Predef.Map[Int, List[AirportChampionInfo]] = newInfo.groupBy(_.loyalist.airline.id)

    val maxGeneratedAirlineId = 300 //todo hookup to generated constant
    val airlineIds = (previousInfoByAirlineId.keySet ++ newInfoByAirlineId.keySet).filter(_ > maxGeneratedAirlineId)

    val logs = ListBuffer[Log]()
    airlineIds.foreach { airlineId =>
      val changes = ListBuffer[ChampionInfoChange]()
      previousInfoByAirlineId.get(airlineId) match {
        case Some(previousRanks) =>
          newInfoByAirlineId.get(airlineId) match {
            case Some(newRanks) => //go from airport to airport
              val previousInfoByAirport = previousRanks.groupBy(_.loyalist.airport).view.mapValues(_(0)) //should be exactly one entry
              val newInfoByAirport = newRanks.groupBy(_.loyalist.airport).view.mapValues(_(0)) //should be exactly one entry
              val airportIds = previousInfoByAirport.keySet ++ newInfoByAirport.keySet
              airportIds.foreach { airportId =>
                if (previousInfoByAirport.get(airportId).map(_.ranking).getOrElse(0) != newInfoByAirport.get(airportId).map(_.ranking).getOrElse(0) && newInfoByAirport.get(airportId).map(_.reputationBoost).exists(_ > 0.5)) {
                  changes.append(ChampionInfoChange(previousInfoByAirport.get(airportId), newInfoByAirport.get(airportId)))
                }
              }
            case None => changes.appendAll(previousRanks.map(info => ChampionInfoChange(Some(info), None))) //lost all ranks
          }
        case None => changes.appendAll(newInfoByAirlineId(airlineId).map(info => ChampionInfoChange(None, Some(info)))) //all ranks are new
      }
      val airline = AirlineCache.getAirline(airlineId, false).getOrElse(Airline.fromId(airlineId))

      logs.appendAll(changes.map {
        case ChampionInfoChange(Some(previousRank), Some(newRank)) =>
          val reputationChange = BigDecimal.valueOf(newRank.reputationBoost - previousRank.reputationBoost).setScale(2, RoundingMode.HALF_UP)
          val displayChange =
            if (reputationChange >= 0) {
              "+" + reputationChange
            } else {
              reputationChange.toString
            }
          Log(airline, s"${newRank.loyalist.airport.displayText} ranking ${previousRank.ranking} -> ${newRank.ranking}. Reputation change $displayChange", LogCategory.AIRPORT_RANK_CHANGE, LogSeverity.INFO, currentCycle, immutable.Map("airportId" -> newRank.loyalist.airport.id.toString))
        case ChampionInfoChange(None, Some(newRank)) =>
          Log(airline, s"${newRank.loyalist.airport.displayText} new ranking ${newRank.ranking}. Reputation change +${BigDecimal.valueOf(newRank.reputationBoost).setScale(2, RoundingMode.HALF_UP)}", LogCategory.AIRPORT_RANK_CHANGE, LogSeverity.INFO, currentCycle, immutable.Map("airportId" -> newRank.loyalist.airport.id.toString))
        case ChampionInfoChange(Some(previousRank), None) =>
          Log(airline, s"${previousRank.loyalist.airport.displayText} lost ranking ${previousRank.ranking}. Reputation change -${BigDecimal.valueOf(previousRank.reputationBoost).setScale(2, RoundingMode.HALF_UP)}", LogCategory.AIRPORT_RANK_CHANGE, LogSeverity.INFO, currentCycle, immutable.Map("airportId" -> previousRank.loyalist.airport.id.toString))
        case _ => //should not happen
          Log(airline, s"Unknown rank change", LogCategory.AIRPORT_RANK_CHANGE, LogSeverity.INFO, currentCycle)
      })
    }
    println(s"Ranking changes count : ${logs.size}")
    LogSource.insertLogs(logs.toList)
 }
  case class ChampionInfoChange(previousRank : Option[AirportChampionInfo], newRank : Option[AirportChampionInfo])


  def simulateLoyalists(allAirports : List[Airport], linkRidershipDetails : immutable.Map[(PassengerGroup, Airport, Route), Int], cycle : Int) = {
    var existingLoyalistByAirportId : immutable.Map[Int, List[Loyalist]] = LoyalistSource.loadLoyalistsByCriteria(List.empty).groupBy(_.airport.id)

    existingLoyalistByAirportId = decayLoyalists(allAirports, existingLoyalistByAirportId)

    val (updatingLoyalists,deletingLoyalists) = computeLoyalists(allAirports, linkRidershipDetails, existingLoyalistByAirportId)
    println(s"Updating ${updatingLoyalists.length} loyalists entries")
    LoyalistSource.updateLoyalists(updatingLoyalists)

    println(s"Deleting ${deletingLoyalists.length} loyalists entries")
    LoyalistSource.deleteLoyalists(deletingLoyalists)

    val allLoyalists = LoyalistSource.loadLoyalistsByCriteria(List.empty)
    println(s"Computing loyalist info with ${allLoyalists.length} entries")

    //compute champion info
    val previousInfo = ChampionUtil.loadAirportChampionInfo()
    val newInfo = ChampionUtil.computeAirportChampionInfo(allLoyalists)
    processChampionInfoChanges(previousInfo, newInfo, cycle)
    AirportSource.updateChampionInfo(newInfo)


    println("Done computing champions")

    if (cycle % LOYALIST_HISTORY_SAVE_INTERVAL == 0) {
      val cutoff = cycle - LOYALIST_HISTORY_ENTRY_MAX * LOYALIST_HISTORY_SAVE_INTERVAL
      println(s"Purging loyalist history before cycle $cutoff")
      LoyalistSource.deleteLoyalistHistoryBeforeCycle(cutoff)

      val historyEntries = allLoyalists.map(LoyalistHistory(_, cycle))
      println(s"Saving ${historyEntries.length} loyalist history entries")
      LoyalistSource.updateLoyalistHistory(historyEntries)
    }

    newInfo
  }

  val DECAY_RATE = 0.00025 //1 loyalist disappears per 4000 per week
  private[patson] def decayLoyalists(allAirports : List[Airport], existingLoyalistByAirportId : immutable.Map[Int, List[Loyalist]]) : immutable.Map[Int, List[Loyalist]] = {
    val updatingLoyalists, deletingLoyalists = ListBuffer[Loyalist]()
    val r = new Random()
    val result : immutable.Map[Int, List[Loyalist]] = existingLoyalistByAirportId.view.mapValues { loyalists =>
      loyalists.map {
        loyalist =>
          var decayAmount = (loyalist.amount * DECAY_RATE).toInt
          if (decayAmount == 0) { //less than 1. For populated airport, we should just use 1, this should aid with purging excessive loyalist records
            if (loyalist.airport.basePopulation >= 1_000_000) {
              decayAmount = 1
            } else { //for pop 0, one in 100 chance to lose 1
              if (r.nextInt(100) <= loyalist.airport.basePopulation / 10_000 ) {
                decayAmount = 1
              }
            }
          }

          if (decayAmount == 0) { //no change
            loyalist
          } else {
            val newLoyalist = Loyalist(loyalist.airport, loyalist.airline, loyalist.amount - decayAmount)
            if (newLoyalist.amount == 0) {
              deletingLoyalists.append(newLoyalist)
            } else {
              updatingLoyalists.append(newLoyalist)
            }
            newLoyalist
          }
      }.filter(_.amount > 0)
    }.toMap

    println(s"Decaying (update) ${updatingLoyalists.length} loyalists entries")
    LoyalistSource.updateLoyalists(updatingLoyalists.toList)

    println(s"Decaying (deletion) ${deletingLoyalists.length} loyalists entries")
    LoyalistSource.deleteLoyalists(deletingLoyalists.toList)

    result
  }

  val MAX_LOYALIST_FLIP_RATIO = 1
  val NEUTRAL_SATISFACTION = 0.6
  private[patson] def computeLoyalists(allAirports : List[Airport], linkRidershipDetails : immutable.Map[(PassengerGroup, Airport, Route), Int], existingLoyalistByAirportId : immutable.Map[Int, List[Loyalist]]) = {
    val result = ListBuffer[Loyalist]() //airlineId, amount

    linkRidershipDetails.groupBy(_._1._1.fromAirport).foreach {
      case ((fromAirport, passengersFromThisAirport)) =>
        val loyalistIncrementOfAirlines = Map[Int, Int]() //airlineId, delta
        //passengersFromThisAirport.filter(_._1._1.preference.loyaltySensitivity > 0).toList.foreach { //only count pax that actually cares about loyalty now
        passengersFromThisAirport.toList.foreach {
          case ((passengerGroup, toAirport, route), paxCount) =>
            val totalDistance = route.links.map(_.link.distance).sum
            val flightLinks = route.links.filter(_.link.transportType == TransportType.FLIGHT) //only flights would generate loyalist
            flightLinks.foreach { linkConsideration =>
              val link = linkConsideration.link
              val preferredLinkClass = passengerGroup.preference.preferredLinkClass
              val standardPrice = Pricing.computeStandardPrice(link, preferredLinkClass, passengerGroup.passengerType)

              val satisfaction = Computation.computePassengerSatisfaction(linkConsideration.cost, standardPrice)


              var conversionRatio =
                if (satisfaction < NEUTRAL_SATISFACTION) {
                  0
                } else {
                  (satisfaction - NEUTRAL_SATISFACTION) / (1 - NEUTRAL_SATISFACTION)
                }

              if (link.distance != totalDistance) {
                conversionRatio = conversionRatio * (0.5 / route.links.length  + 0.5 * link.distance / totalDistance) //half depends on # of leg, half proportional to the % of distance travel of the whole route
              }

              val loyalistDelta = (paxCount * conversionRatio).toInt
              val existingDelta = loyalistIncrementOfAirlines.getOrElse(link.airline.id, 0)
              loyalistIncrementOfAirlines.put(link.airline.id, existingDelta + loyalistDelta)
            }
        }

        //put a map of current royalist status to draw which loyalist to flip
        val loyalistDistribution = ListBuffer[(Int, Int)]() //airlineId, threshold
        var walker = 0
        val existingLoyalistOfThisAirport = existingLoyalistByAirportId.get(fromAirport.id).getOrElse(List.empty).map(entry => (entry.airline.id, entry.amount)).toMap
        existingLoyalistOfThisAirport.foreach {
          case(airlineId, loyalistAmount) =>
            walker = walker + loyalistAmount
            loyalistDistribution.append((airlineId, walker))
        }
//        if (loyalistDistribution.length == 4) {
//          println(s"distribution $loyalistDistribution")
//        }

//        val flippedLoyalists = mutable.Map[Int, Int]() //airlineId, flipped amount

        val CHUNK_SIZE = 5
        val updatingLoyalists = mutable.HashMap[Int, Int]() //airlineId, amount

        val totalLoyalist = existingLoyalistOfThisAirport.values.sum
        //now with delta, see what the flips are
        loyalistIncrementOfAirlines.foreach {
          case (gainAirlineId, increment) => //split into chunks for better randomness
            if (increment > 0) {
              var unclaimedLoyalist = (fromAirport.popMiddleIncome - totalLoyalist).toInt
              var remainingIncrement = increment
              while (remainingIncrement > 0) {
                val chunk = if (remainingIncrement <= CHUNK_SIZE) remainingIncrement else CHUNK_SIZE

                //Has to compare pop vs total, as  in rare scenario fromAirport.population < existingLoyalistOfThisAirport.values.sum, for example demolished property that +pop
                val flipTrigger = ThreadLocalRandom.current().nextInt(Math.max(fromAirport.popMiddleIncome, totalLoyalist).toInt)

                val flippedAirlineIdOption = loyalistDistribution.find {
                  case (airlineId : Int, threshold : Int) => flipTrigger < threshold
                }.map(_._1)

                flippedAirlineIdOption match {
                  case Some(flippedAirlineId) => //flip from existing airline - could be itself
                    if (flippedAirlineId == gainAirlineId) { //flipping from itself, ignore

                    } else {
                      val existingAmountOfFlippedAirline : Int = updatingLoyalists.get(flippedAirlineId) match {
                        case Some(existingAmount) => existingAmount
                        case None => existingLoyalistOfThisAirport.getOrElse(flippedAirlineId, 0)
                      }


                      if (existingAmountOfFlippedAirline <= 0) { //cannot flip, ignore...

                      } else {
                        val finalChunk =
                          if (existingAmountOfFlippedAirline < chunk) { //partial flip
                            existingAmountOfFlippedAirline
                          } else {
                            chunk
                          }
                        updatingLoyalists.put(flippedAirlineId, existingAmountOfFlippedAirline - finalChunk)
                        val existingAmountOfGainAirline = updatingLoyalists.getOrElse(gainAirlineId, existingLoyalistOfThisAirport.getOrElse(gainAirlineId, 0))
                        updatingLoyalists.put(gainAirlineId, existingAmountOfGainAirline + finalChunk)
                      }
                    }
                  case None => //flip from unclaimed
                    if (unclaimedLoyalist <= 0) { //ignore, can no longer draw loyalist

                    } else {
                      val finalChunk =
                        if (unclaimedLoyalist < chunk) { //partial flip
                          unclaimedLoyalist
                        } else {
                          chunk
                        }
                      unclaimedLoyalist = unclaimedLoyalist - finalChunk
                      val existingAmountOfGainAirline = updatingLoyalists.getOrElse(gainAirlineId, existingLoyalistOfThisAirport.getOrElse(gainAirlineId, 0))
                      updatingLoyalists.put(gainAirlineId, existingAmountOfGainAirline + finalChunk)
                    }
                }
                remainingIncrement = remainingIncrement - chunk
              }
            }
        }

        //write result of this airport to final result
        updatingLoyalists.foreach {
          case (airlineId, amount) => result.append(Loyalist(fromAirport, AirlineCache.getAirline(airlineId).getOrElse(Airline.fromId(airlineId)), amount))
        }
    }
    result.toList.partition(_.amount > 0)
  }

  def updateLoungeStatus(allAirports : List[Airport], linkRidershipDetails : Predef.Map[(PassengerGroup, Airport, Route), Int]) = {
    println("Checking lounge status")
    val passengersByAirport : MapView[Airport, MapView[Airline, Int]] = linkRidershipDetails.toList.flatMap {
      case ((passengerGroup, airport, route), count) =>
        route.links.filter(route => route.link.transportType == TransportType.FLIGHT && route.linkClass.level > 1).flatMap { linkConsideration =>
          List((linkConsideration.link.airline, linkConsideration.from, count), (linkConsideration.link.airline, linkConsideration.to, count))
        }

    }.groupBy {
      case (airline, airport, count) => airport
    }.view.mapValues { list =>
      list.map {
        case (airline, airport, count) => (airline, count)
      }.groupBy(_._1).view.mapValues(_.map(_._2).sum)
    }


    allAirports.foreach { airport =>
      if (!airport.getLounges().isEmpty) {
        val airlineIdsWithBase = airport.getAirlineBases().keys.toList
        //println(s"AIRPORT $airport : ${passengersByAirport.get(airport).map(_.toList)}")
        val airlinesByPassengers : List[(Airline, Int)] = passengersByAirport.get(airport).map(_.toList).getOrElse(List.empty).filter {
          case (airline, _) => airlineIdsWithBase.contains(airline.id) //only count airlines that has a base here
        }

        val eligibleAirlines = airlinesByPassengers.sortBy(_._2).takeRight(airport.getLounges()(0).getActiveRankingThreshold).map(_._1)
        airport.getLounges().foreach { lounge =>
          val newStatus =
            if (eligibleAirlines.contains(lounge.airline)) {
              LoungeStatus.ACTIVE
            } else {
              LoungeStatus.INACTIVE
            }

          if (lounge.status != newStatus) {
            println(s"Flipping status for lounge $lounge to $newStatus")
            AirlineSource.saveLounge(lounge.copy(status = newStatus))
          }
        }
      }
    }
  }

  private[patson] val getPenalty : Seq[LinkConsumptionDetails] => Double = consumptionDetails => {
      //add penalty for delays and cancellation
      val totalCapacity = consumptionDetails.map { _.link.capacity.total }.sum
      if (totalCapacity > 0) {
        val totalMinorDelayCapacity = consumptionDetails.filter(_.link.frequency > 0).map { linkConsumption => linkConsumption.link.capacity.total * linkConsumption.link.minorDelayCount / linkConsumption.link.frequency }.sum
        val totalMajorDelayCapacity = consumptionDetails.filter(_.link.frequency > 0).map { linkConsumption => linkConsumption.link.capacity.total * linkConsumption.link.majorDelayCount / linkConsumption.link.frequency}.sum
        val totalCancellationCapacity = consumptionDetails.filter(_.link.frequency > 0).map { linkConsumption => linkConsumption.link.capacity.total * linkConsumption.link.cancellationCount / linkConsumption.link.frequency}.sum
      
        val minorDelayPercentage = totalMinorDelayCapacity.toDouble / totalCapacity
        val majorDelayPercentage = totalMajorDelayCapacity.toDouble / totalCapacity
        val cancellationPercentage = totalCancellationCapacity.toDouble / totalCapacity
        
        minorDelayPercentage * LOYALTY_DECREMENT_BY_MINOR_DELAY + majorDelayPercentage * LOYALTY_DECREMENT_BY_MAJOR_DELAY + cancellationPercentage * LOYALTY_DECREMENT_BY_CANCELLATION
      } else {
        0
      }
  }
  
}