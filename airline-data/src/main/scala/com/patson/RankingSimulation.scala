package com.patson

import com.patson.model.{RankingType, _}
import com.patson.model.RankingKey._
import com.patson.UserSimulation.configFactory
import com.patson.data.{AirlineSource, AllianceSource, IncomeSource, LinkSource, RankingLeaderboardSource}

import scala.collection.immutable

object RankingSimulation {
  /**
   * Updates rankings in db and outputs a map of airline id -> reputation value.
   *
   * @param currentCycle
   * @return Map[airlineId, reputationValue]
   */
  def process(currentCycle: Int, allAirlines: List[Airline], flightLinkResult: List[LinkConsumptionDetails], loungeResult: List[LoungeConsumptionDetails], paxStats: immutable.Map[Int, AirlinePaxStat]) : Map[Int, Double] = {
    val currentRankings = updateRankings(currentCycle, allAirlines, flightLinkResult, loungeResult, paxStats)

    // Calculate movements based on the previous cycle's rankings
    val previousCycleRankings = RankingLeaderboardSource.loadRankingsByCycle(currentCycle - 1)
    updateMovements(previousCycleRankings, currentRankings)

    RankingLeaderboardSource.saveRankingsByCycle(currentCycle, currentRankings)
    RankingLeaderboardSource.deleteRankingsByCycle(currentCycle - 2)

    // Load alliances and map allianceId to member airline ids (excluding applicants)
    val alliances = AllianceSource.loadAllAlliances()
    val membersByAllianceId = alliances.map { alliance =>
      (alliance.id, alliance.members.filter(_.role != AllianceRole.APPLICANT).map(_.airline.id))
    }.toMap

    // Flatten rankings and distribute reputation rewards
    // For AirlineKey: award directly to the airline
    // For AllianceKey: award to all member airlines
    currentRankings.values.flatten // Flatten List[List[Ranking]] into List[Ranking]
      .filter(_.reputationPrize.isDefined) // Keep only rankings with a prize
      .flatMap { ranking =>
        val repPrize = ranking.reputationPrize.getOrElse(0)
        ranking.key match {
          case AirlineKey(airlineId) =>
            List((airlineId, repPrize))
          case AllianceKey(allianceId) =>
            membersByAllianceId.getOrElse(allianceId, List.empty).map { airlineId =>
              (airlineId, repPrize)
            }
          case AirlineAirportKey(airlineId, _) =>
            List((airlineId, repPrize))
          case _ => List.empty
        }
      }
      .groupBy(_._1)
      .view.mapValues(_.map(_._2).sum.toDouble)
      .toMap
  }

  def reputationBonus(maxBonus: Int, rank: Int) = {
    val denominator = 16
    val bonus = rank match {
      case 0 => maxBonus
      case 1 => maxBonus * 13 / denominator
      case 2 => maxBonus * 10 / denominator
      case 3 => maxBonus * 8 / denominator
      case 4 => maxBonus * 7 / denominator
      case 5 => maxBonus * 6 / denominator
      case 6 => maxBonus * 5 / denominator
      case 7 => maxBonus * 4 / denominator
      case _ => maxBonus * 3 / denominator
    }
    Some(if (rank >= 20) 0 else bonus)
  }

  def updateRankings(currentCycle: Int, allAirlines: List[Airline], flightLinkResult: List[LinkConsumptionDetails], loungeConsumptions: List[LoungeConsumptionDetails], paxStats: immutable.Map[Int, AirlinePaxStat]): Map[RankingType.Value, List[Ranking]] = {
    val devMode = if (configFactory.hasPath("dev")) configFactory.getBoolean("dev") else false
    val invalidAirlinesIds: Set[Int] = if (devMode) Set.empty else allAirlines.filter(airline => airline.airlineType == NonPlayerAirline || airline.getReputation < 60).map(_.id).toSet
    val airlinesById = allAirlines.filter(airline => ! invalidAirlinesIds.contains(airline.id)).map(airline => (airline.id, airline)).toMap
    val flightConsumptions = flightLinkResult.filter { consumption =>
      consumption.link.transportType == TransportType.FLIGHT && consumption.link.soldSeats.total > 0 && !invalidAirlinesIds.contains(consumption.link.airline.id)
    }
    val flightConsumptionsByAirline = flightConsumptions.groupBy(_.link.airline.id)
    val airlineStats = paxStats.view.filterKeys(id => !invalidAirlinesIds.contains(id))
    val airlineIncomes = IncomeSource.loadAllBalancesByCycle(currentCycle)
    val linksByAirline = LinkSource.loadAllFlightLinks().filterNot(link => invalidAirlinesIds.contains(link.airline.id)).groupBy(_.airline.id)

    val updatedRankings = scala.collection.mutable.Map.empty[RankingType.Value, List[Ranking]]
    updatedRankings.put(RankingType.TOURIST_COUNT, getPaxRanking(airlineStats.map(stat => (stat._1, stat._2.tourists)).toMap, airlinesById, RankingType.TOURIST_COUNT))
    updatedRankings.put(RankingType.BUSINESS_COUNT, getPaxRanking(airlineStats.map(stat => (stat._1, stat._2.business)).toMap, airlinesById, RankingType.BUSINESS_COUNT))
    updatedRankings.put(RankingType.ELITE_COUNT, getPaxRanking(airlineStats.map(stat => (stat._1, stat._2.elites)).toMap, airlinesById, RankingType.ELITE_COUNT))
    updatedRankings.put(RankingType.STOCK_PRICE, getStockRanking(airlinesById))
    updatedRankings.put(RankingType.CODESHARE_COUNT, getPaxRanking(airlineStats.map(stat => (stat._1, stat._2.codeshares)).toMap, airlinesById, RankingType.CODESHARE_COUNT))
    updatedRankings.put(RankingType.PASSENGER_SATISFACTION, getPassengerSFRanking(flightConsumptionsByAirline,airlinesById))
    updatedRankings.put(RankingType.PASSENGER_SPEED, getPassengerSpeedRanking(flightConsumptionsByAirline,airlinesById))
    updatedRankings.put(RankingType.LINK_COUNT_SMALL_TOWN, getSmallTownRanking(linksByAirline, airlinesById))
    updatedRankings.put(RankingType.LINK_COUNT_LOW_INCOME, getLowIncomeRanking(linksByAirline, airlinesById))
    updatedRankings.put(RankingType.UNIQUE_COUNTRIES, getCountriesRanking(linksByAirline, airlinesById))
    updatedRankings.put(RankingType.UNIQUE_IATA, getIataRanking(linksByAirline, airlinesById))
    updatedRankings.put(RankingType.STAFF_REPUTATION, getRepPerStaff(linksByAirline, airlinesById))
    updatedRankings.put(RankingType.LINK_PROFIT, getLinkProfitRanking(flightConsumptions, airlinesById))
    updatedRankings.put(RankingType.LINK_LOSS, getLinkLossRanking(flightConsumptions, airlinesById))
    updatedRankings.put(RankingType.LINKS_COUNT_LOSS, getLinksLossCountRanking(flightConsumptionsByAirline, airlinesById))
    updatedRankings.put(RankingType.LINK_FREQUENCY, getLinkFrequent(flightConsumptions, airlinesById))
    updatedRankings.put(RankingType.PASSENGER_QUALITY, getPassengerQualityRanking(flightConsumptionsByAirline, airlinesById))
    updatedRankings.put(RankingType.LINK_DISTANCE, getLinkLongest(flightConsumptions, airlinesById))
    updatedRankings.put(RankingType.LINK_SHORTEST, getLinkShortest(flightConsumptions, airlinesById))
    updatedRankings.put(RankingType.LOUNGE, getLoungeRanking(loungeConsumptions.filterNot{ loungeConsumption => invalidAirlinesIds.contains(loungeConsumption.lounge.airline.id) }, airlinesById))
//    updatedRankings.put(RankingType.AIRLINE_PROFIT_MARGIN, getMarginRanking(airlineStats.map(stat => (stat.airlineId, (stat.CASK, stat.RASK))).toMap, airlinesById))
    updatedRankings.put(RankingType.AIRPORT_TRAFFIC, getAirportTrafficRanking(flightConsumptionsByAirline, airlinesById))

    //informational rankings
    val (paxByAirport, paxByAirportPair) = getPaxStat(flightConsumptions)
    updatedRankings.put(RankingType.AIRLINE_VALUE, getValueRanking(airlineIncomes, airlinesById))
    updatedRankings.put(RankingType.AIRPORT, getAirportRanking(paxByAirport))
    updatedRankings.put(RankingType.INTERNATIONAL_PAX, getAirportPairRanking(paxByAirportPair, (airport1, airport2) => airport1.countryCode != airport2.countryCode))
    updatedRankings.put(RankingType.DOMESTIC_PAX, getAirportPairRanking(paxByAirportPair, (airport1, airport2) => airport1.countryCode == airport2.countryCode))
    updatedRankings.put(RankingType.PASSENGER_MILE, getPassengerMileRanking(flightConsumptionsByAirline, airlinesById))
    updatedRankings.put(RankingType.LINK_PROFIT_TOTAL, getLinkProfitTotalRanking(flightConsumptions, airlinesById))
    updatedRankings.put(RankingType.AIRLINE_PRESTIGE, getAirlinePrestigeRanking(airlinesById))

    //alliance rankings
    updatedRankings.put(RankingType.ALLIANCE_TRAVELERS, getAllianceTravelersRanking(currentCycle))
    updatedRankings.put(RankingType.ALLIANCE_TOURISTS, getAllianceTouristRanking(currentCycle))
    updatedRankings.put(RankingType.ALLIANCE_ELITE, getAllianceEliteRanking(currentCycle))
    updatedRankings.put(RankingType.ALLIANCE_AIRPORT_REP, getAllianceAirportRepRanking(currentCycle))
    updatedRankings.put(RankingType.ALLIANCE_LOUNGE, getAllianceLoungeRanking(currentCycle))
    updatedRankings.put(RankingType.ALLIANCE_STOCKS, getAllianceStockRanking(currentCycle))

    updatedRankings.toMap
  }

  private[this] def getPaxRanking(pax : Map[Int, Int], airlinesById : Map[Int, Airline], rankingType : RankingType.Value): List[Ranking] = {
    val sortedPassengerByAirline = pax.toList.sortBy(_._2)(Ordering[Int].reverse)

    sortedPassengerByAirline.zipWithIndex.map {
      case ((airlineId, quanity), index) => Ranking(
        rankingType,
        key = AirlineKey(airlineId),
        entry = airlinesById.getOrElse(airlineId, Airline.fromId(airlineId)),
        ranking = index + 1,
        rankedValue = quanity,
        reputationPrize = reputationBonus(24, index)
      )
    }.toList.sortBy(_.ranking).take(200)
  }

  private[this] def getPassengerMileRanking(linkConsumptions : Map[Int, List[LinkConsumptionDetails]], airlinesById : Map[Int, Airline]) : List[Ranking] = {
    val passengerMileByAirline : Map[Int, Long] = linkConsumptions.view.mapValues(_.map { linkConsumption =>
      linkConsumption.link.soldSeats.total.toLong * linkConsumption.link.distance
    }.sum).toMap

    val sortedPassengerMileByAirline= passengerMileByAirline.toList.sortBy(_._2)(Ordering[Long].reverse)  //sort by total passengers of each airline

    sortedPassengerMileByAirline.zipWithIndex.map {
      case((airlineId, passengerKm), index) => Ranking(
        RankingType.PASSENGER_MILE,
        key = AirlineKey(airlineId),
        entry = airlinesById.getOrElse(airlineId, Airline.fromId(airlineId)),
        ranking = index + 1,
        rankedValue = (passengerKm * 0.6213711922).toInt
      )
    }.toList.sortBy(_.ranking).take(200)
  }

  private[this] def getPassengerQualityRanking(linkConsumptions: Map[Int, List[LinkConsumptionDetails]], airlinesById: Map[Int, Airline]): List[Ranking] = {
    val passengerQualityByAirline: Map[Int, Double] = linkConsumptions.view.mapValues { linkConsumption =>
      val paxMiles = linkConsumption.map { linkConsumption =>
        linkConsumption.link.soldSeats.total * linkConsumption.link.distance
      }.sum
      val qualityPaxMiles = linkConsumption.map { linkConsumption =>
        linkConsumption.link.soldSeats.total.toLong * linkConsumption.link.distance * linkConsumption.link.computedQuality
      }.sum
      qualityPaxMiles / paxMiles.toDouble
    }.toMap

    val sortedPassengerByAirline = passengerQualityByAirline.toList.sortBy(_._2)(Ordering[Double].reverse) //sort by total passengers of each airline

    sortedPassengerByAirline.zipWithIndex.map {
      case ((airlineId, quality), index) => Ranking(
        RankingType.PASSENGER_QUALITY,
        key = AirlineKey(airlineId),
        entry = airlinesById.getOrElse(airlineId, Airline.fromId(airlineId)),
        ranking = index + 1,
        rankedValue = BigDecimal(quality).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble,
        reputationPrize = reputationBonus(32, index)
      )
    }.sortBy(_.ranking).take(200)
  }

  private[this] def getPassengerSFRanking(linkConsumptions: Map[Int, List[LinkConsumptionDetails]], airlinesById: Map[Int, Airline]): List[Ranking] = {
    val withoutTinyAirlines = linkConsumptions.filter { case (_, detailsList) =>
      detailsList.size > 10
    }
    val passengerQualityByAirline: Map[Int, Double] = withoutTinyAirlines.view.mapValues { linkConsumption =>
      val paxMiles = linkConsumption.map { linkConsumption =>
        linkConsumption.link.soldSeats.total.toLong * linkConsumption.link.distance
      }.sum
      val qualityPaxMiles = linkConsumption.map { linkConsumption =>
        linkConsumption.link.soldSeats.total.toLong * linkConsumption.link.distance * linkConsumption.satisfaction
      }.sum
      qualityPaxMiles / paxMiles
    }.toMap

    val sortedPassengerByAirline = passengerQualityByAirline.toList.sortBy(_._2)(Ordering[Double].reverse)

    sortedPassengerByAirline.zipWithIndex.map {
      case ((airlineId, satisfaction), index) => Ranking(
        RankingType.PASSENGER_SATISFACTION,
        key = AirlineKey(airlineId),
        entry = airlinesById.getOrElse(airlineId, Airline.fromId(airlineId)),
        ranking = index + 1,
        rankedValue = BigDecimal(satisfaction * 100).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble,
        reputationPrize = reputationBonus(24, index)
      )
    }.toList.sortBy(_.ranking).take(200)
  }

  private[this] def getPassengerSpeedRanking(linkConsumptions: Map[Int, List[LinkConsumptionDetails]], airlinesById: Map[Int, Airline]): List[Ranking] = {
    val withoutTinyAirlines = linkConsumptions.filter { case (_, detailsList) =>
      detailsList.size > 10
    }
    val passengerSpeedByAirline: Map[Int, Int] = withoutTinyAirlines.view.mapValues { linkConsumption =>
      val linkSpeed = linkConsumption.map { linkConsumption =>
        linkConsumption.link.distance / linkConsumption.link.duration * 60
      }.sum
      (linkSpeed.toDouble / linkConsumption.length).toInt
    }.toMap

    val sortedByAirline = passengerSpeedByAirline.toList.sortBy(_._2)

    var prevValue: Int = 0
    var prevRanking: Int = 0
    sortedByAirline.zipWithIndex.map {
      case ((airlineId, speed), index) =>
        prevRanking = if (prevValue == speed) prevRanking else index
        prevValue = speed
        Ranking(RankingType.PASSENGER_SPEED,
        key = AirlineKey(airlineId),
        entry = airlinesById.getOrElse(airlineId, Airline.fromId(airlineId)),
        ranking = prevRanking + 1,
        rankedValue = BigDecimal(speed).toInt,
        reputationPrize = reputationBonus(24, prevRanking)
      )
    }.sortBy(_.ranking).take(200)
  }

  private[this] def getLinkProfitRanking(linkConsumptions : List[LinkConsumptionDetails], airlinesById : Map[Int, Airline]) : List[Ranking] = {
    val mostProfitableLinks : List[LinkConsumptionDetails] = linkConsumptions
      .sortBy(_.profit)(Ordering[Int].reverse)
      .foldLeft(List.empty[LinkConsumptionDetails]) { (acc, linkDetail) =>
        if (acc.exists(_.link.airline.id == linkDetail.link.airline.id)) {
          acc
        } else {
          linkDetail :: acc // Add to the front of the accumulator
        }
      }
      .reverse

    mostProfitableLinks.zipWithIndex.map {
      case(linkConsumption, index) => {
        val airlineId = linkConsumption.link.airline.id
        val ranking = Ranking(
          RankingType.LINK_PROFIT,
          key = AirlineKey(airlineId),
          entry = linkConsumption.link.asInstanceOf[Link].copy(airline = airlinesById.getOrElse(airlineId, Airline.fromId(airlineId))),
          ranking = index + 1,
          rankedValue = linkConsumption.profit,
          reputationPrize = reputationBonus(32, index)
        )
        ranking
      }

    }.toList.sortBy(_.ranking).take(200)
  }

  private[this] def getLinkLossRanking(linkConsumptions: List[LinkConsumptionDetails], airlinesById: Map[Int, Airline]): List[Ranking] = {
    // Keep only the most loss-making link per airline to avoid duplicate (cycle, type, key) in DB
    val leastProfitableLinks: List[LinkConsumptionDetails] = linkConsumptions
      .filter(_.profit < 0)
      .sortBy(_.profit) // ascending: most negative first
      .foldLeft(List.empty[LinkConsumptionDetails]) { (acc, linkDetail) =>
        if (acc.exists(_.link.airline.id == linkDetail.link.airline.id)) acc else linkDetail :: acc
      }
      .reverse

    leastProfitableLinks.zipWithIndex.map {
      case (linkConsumption, index) =>
        val airlineId = linkConsumption.link.airline.id
        Ranking(
          RankingType.LINK_LOSS,
          key = AirlineKey(airlineId),
          entry = linkConsumption.link.asInstanceOf[Link].copy(airline = airlinesById.getOrElse(airlineId, Airline.fromId(airlineId))),
          ranking = index + 1,
          rankedValue = linkConsumption.profit,
          reputationPrize = reputationBonus(-20, index)
        )

    }.sortBy(_.ranking).take(200)
  }

  private[this] def getLinksLossCountRanking(linkConsumptionsByAirline: Map[Int, List[LinkConsumptionDetails]], airlinesById: Map[Int, Airline]): List[Ranking] = {
    val linkLossesByAirline = linkConsumptionsByAirline.view.mapValues { linkConsumptions =>
      linkConsumptions.count(_.profit < 0)
    }.toList.sortBy(_._2).reverse

    linkLossesByAirline.zipWithIndex.map {
      case ((airlineId, linkCount), index) => Ranking(
        RankingType.LINKS_COUNT_LOSS,
        key = AirlineKey(airlineId),
        entry = airlinesById.getOrElse(airlineId, Airline.fromId(airlineId)),
        ranking = index + 1,
        rankedValue = linkCount,
        reputationPrize = reputationBonus(-20, index)
      )
    }.sortBy(_.ranking).take(200)
  }

  private[this] def getLinkProfitTotalRanking(linkConsumptions: List[LinkConsumptionDetails], airlinesById: Map[Int, Airline]): List[Ranking] = {
    // Aggregate total link profit per airline to ensure unique key per airline
    val profitByAirline: List[(Int, Int)] = linkConsumptions
      .groupBy(_.link.airline.id)
      .view
      .mapValues(cons => cons.map(_.profit).sum)
      .toList

    profitByAirline
      .sortBy(_._2)(Ordering[Int].reverse)
      .zipWithIndex
      .map { case ((airlineId, totalProfit), index) =>
        Ranking(
          RankingType.LINK_PROFIT_TOTAL,
          key = AirlineKey(airlineId),
          entry = airlinesById.getOrElse(airlineId, Airline.fromId(airlineId)),
          ranking = index + 1,
          rankedValue = totalProfit,
          reputationPrize = reputationBonus(32, index)
        )
      }
      .take(200)
  }

  private[this] def getLinkShortest(linkConsumptions: List[LinkConsumptionDetails], airlinesById: Map[Int, Airline]): List[Ranking] = {
    val longestLinkPerAirline = linkConsumptions
      .filter(_.link.soldSeats.total > 3000)
      .filter(_.profit > 0)
      .sortBy(_.link.distance)(Ordering[Int])
      .foldLeft(List.empty[LinkConsumptionDetails]) { (acc, linkDetail) =>
        if (acc.exists(_.link.airline.id == linkDetail.link.airline.id)) {
          acc
        } else {
          linkDetail :: acc // Add to the front of the accumulator
        }
      }
      .reverse

    var prevValue: Int = 0
    var prevRanking: Int = 0
    longestLinkPerAirline.zipWithIndex.map {
      case (linkConsumption, index) => {
        prevRanking = if (prevValue == linkConsumption.link.distance) prevRanking else index
        prevValue = linkConsumption.link.distance
        val airlineId = linkConsumption.link.airline.id
        val ranking = Ranking(
          RankingType.LINK_SHORTEST,
          key = AirlineKey(airlineId),
          entry = linkConsumption.link.asInstanceOf[Link].copy(airline = airlinesById.getOrElse(airlineId, Airline.fromId(airlineId))),
          ranking = prevRanking + 1,
          rankedValue = linkConsumption.link.distance,
          reputationPrize = reputationBonus(32, prevRanking)
        )
        ranking
      }

    }.sortBy(_.ranking).take(200)
  }

  private[this] def getLinkLongest(linkConsumptions: List[LinkConsumptionDetails], airlinesById: Map[Int, Airline]): List[Ranking] = {
    val longestLinkPerAirline = linkConsumptions
      .filter(_.link.soldSeats.total > 200)
      .filter(_.profit > 0)
      .sortBy(_.link.distance)(Ordering[Int].reverse)
      .foldLeft(List.empty[LinkConsumptionDetails]) { (acc, linkDetail) =>
        if (acc.exists(_.link.airline.id == linkDetail.link.airline.id)) {
          acc
        } else {
          linkDetail :: acc // Add to the front of the accumulator
        }
      }
      .reverse

    var prevValue: Int = 0
    var prevRanking: Int = 0
    longestLinkPerAirline.zipWithIndex.map {
      case (linkConsumption, index) => {
        prevRanking = if (prevValue == linkConsumption.link.distance) prevRanking else index
        prevValue = linkConsumption.link.distance
        val airlineId = linkConsumption.link.airline.id
        val ranking = Ranking(
          RankingType.LINK_DISTANCE,
          key = AirlineKey(airlineId),
          entry = linkConsumption.link.asInstanceOf[Link].copy(airline = airlinesById.getOrElse(airlineId, Airline.fromId(airlineId))),
          ranking = prevRanking + 1,
          rankedValue = linkConsumption.link.distance,
          reputationPrize = reputationBonus(32, prevRanking)
        )
        ranking
      }

    }.sortBy(_.ranking).take(200)
  }

  private[this] def getLinkFrequent(linkConsumptions: List[LinkConsumptionDetails], airlinesById: Map[Int, Airline]): List[Ranking] = {
    val mostFrequentLinks: List[LinkConsumptionDetails] = linkConsumptions
      .filter(_.link.soldSeats.total > 2400)
      .sortBy(_.link.frequency)(Ordering[Int].reverse)
      .foldLeft(List.empty[LinkConsumptionDetails]) { (acc, linkDetail) =>
        if (acc.exists(_.link.airline.id == linkDetail.link.airline.id)) {
          acc
        } else {
          linkDetail :: acc // Add to the front of the accumulator
        }
      }.reverse

    var prevValue: Int = 0
    var prevRanking: Int = 0
    mostFrequentLinks.zipWithIndex.map {
      case (linkConsumption, index) => {
        prevRanking = if (prevValue == linkConsumption.link.frequency) prevRanking else index
        prevValue = linkConsumption.link.frequency
        val airlineId = linkConsumption.link.airline.id
        val ranking = Ranking(
          RankingType.LINK_FREQUENCY,
          key = AirlineKey(airlineId),
          entry = linkConsumption.link.asInstanceOf[Link].copy(airline = airlinesById.getOrElse(airlineId, Airline.fromId(airlineId))),
          ranking = prevRanking + 1,
          rankedValue = linkConsumption.link.frequency,
          reputationPrize = reputationBonus(32, prevRanking)
        )
        ranking
      }

    }.toList.sortBy(_.ranking).take(200)
  }

  private[this] def getMarginRanking(caskMap : Map[Int, (Double, Double)], airlinesById : Map[Int, Airline]) : List[Ranking] = {
    val sortedByAirline = airlinesById.map { case (airlineId, airline) =>
      val margin: Double = caskMap.getOrElse(airlineId, (0.0,0.0))._2 / caskMap.getOrElse(airlineId, (0.0,1.0))._1
      (airlineId, margin)
    }.toList.filter(_._2 > 0).sortBy(_._2)(Ordering[Double]).reverse

    sortedByAirline.zipWithIndex.map {
      case ((airlineId, quantity), index) => Ranking(
        RankingType.AIRLINE_PROFIT_MARGIN,
        key = AirlineKey(airlineId),
        entry = airlinesById.getOrElse(airlineId, Airline.fromId(airlineId)),
        ranking = index + 1,
        rankedValue = BigDecimal(quantity).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble,
        reputationPrize = reputationBonus(32, index)
      )
    }.sortBy(_.ranking).take(200)
  }

  private[this] def getRepPerStaff(linksByAirline: Map[Int, List[Link]], airlinesById: Map[Int, Airline]): List[Ranking] = {
    val repPerStaff: List[(Int, Double)] = linksByAirline.map { case (airlineId, links) =>
      val totalStaff = links.map(_.getCurrentOfficeStaffRequired).sum
      val totalReputation = airlinesById.getOrElse(airlineId, Airline.fromId(airlineId)).getReputation()
      val ratio = if (totalStaff > 0) totalReputation / totalStaff else 0.0
      (airlineId, ratio)
    }.toList.sortBy(_._2)(Ordering[Double].reverse)

    var prevValue: Double = 0
    var prevRanking: Int = 0
    repPerStaff.zipWithIndex.map {
      case ((airlineId, repPerStaff), index) =>
        prevRanking = if (prevValue == repPerStaff) prevRanking else index
        prevValue = repPerStaff
        Ranking(
          RankingType.STAFF_REPUTATION,
          key = AirlineKey(airlineId),
          entry = airlinesById.getOrElse(airlineId, Airline.fromId(airlineId)),
          ranking = prevRanking + 1,
          rankedValue = BigDecimal(prevValue).setScale(3, BigDecimal.RoundingMode.HALF_UP).toDouble,
          reputationPrize = reputationBonus(32, prevRanking)
        )
    }.take(200)
  }

  private[this] def getLoungeRanking(loungeConsumptions : List[LoungeConsumptionDetails], airlinesById : Map[Int, Airline]) : List[Ranking] = {
    val mostVisitedLounges = loungeConsumptions.groupBy(_.lounge.airline.id)
      .view.mapValues(_.maxBy(entry => entry.selfVisitors + entry.allianceVisitors))
      .values.toList
      .sortBy(details => details.selfVisitors + details.allianceVisitors)(Ordering[Int].reverse)

    mostVisitedLounges.zipWithIndex.map {
      case(details, index) => {
        val lounge = details.lounge
        Ranking(RankingType.LOUNGE,
          key = AirlineKey(lounge.airline.id),
          entry = lounge,
          ranking = index + 1,
          rankedValue = details.selfVisitors + details.allianceVisitors,
          reputationPrize = reputationBonus(32, index)
        )
      }

    }.sortBy(_.ranking)
  }

  private[this] def getCountriesRanking(linksByAirline: Map[Int, List[Link]], airlinesById: Map[Int, Airline]): List[Ranking] = {
    val uniqueAirportCounts : Map[Int, Int] = linksByAirline.mapValues { links =>
      links.map(_.to.countryCode).toSet.size
    }.toMap
    val sortedLinkCountByAirline = uniqueAirportCounts.toList.sortBy(_._2)(Ordering[Int].reverse)
    var prevValue : Int = 0
    var prevRanking : Int = 0
    sortedLinkCountByAirline.zipWithIndex.map {
      case ((airlineId, linkCount), index) => {
        prevRanking = if (prevValue == linkCount) prevRanking else index
        prevValue = linkCount
        Ranking(RankingType.UNIQUE_COUNTRIES,
          key = AirlineKey(airlineId),
          entry = airlinesById.getOrElse(airlineId, Airline.fromId(airlineId)),
          ranking = prevRanking + 1,
          rankedValue = prevValue,
          reputationPrize = reputationBonus(32, prevRanking)
        )
      }
    }.toList.take(200)
  }

  private[this] def getSmallTownRanking(linksByAirline: Map[Int, List[Link]], airlinesById: Map[Int, Airline]): List[Ranking] = {
    val smallTownCounts: Map[Int, Int] = linksByAirline.mapValues { links =>
      val lowPopLinks = links.filter { link => link.to.population <= 500000 || link.from.population <= 500000 }
      lowPopLinks.flatMap(link => Seq(link.from.iata, link.to.iata)).toSet.size
    }.toMap
    val sortedLinkCountByAirline = smallTownCounts.toList.sortBy(_._2)(Ordering[Int].reverse)
    var prevValue: Int = 0
    var prevRanking: Int = 0
    sortedLinkCountByAirline.zipWithIndex.map {
      case ((airlineId, linkCount), index) => {
        prevRanking = if (prevValue == linkCount) prevRanking else index
        prevValue = linkCount
        Ranking(RankingType.LINK_COUNT_SMALL_TOWN,
          key = AirlineKey(airlineId),
          entry = airlinesById.getOrElse(airlineId, Airline.fromId(airlineId)),
          ranking = prevRanking + 1,
          rankedValue = prevValue,
          reputationPrize = reputationBonus(32, prevRanking)
        )
      }
    }.toList.take(200)
  }

  private[this] def getLowIncomeRanking(linksByAirline: Map[Int, List[Link]], airlinesById: Map[Int, Airline]): List[Ranking] = {
    val lowIncomeUniqueIataCounts: Map[Int, Int] = linksByAirline.mapValues { links =>
      val lowIncomeLinks = links.filter { link => link.to.income <= 10000 || link.from.income <= 10000 }
      lowIncomeLinks.flatMap(link => Seq(link.from.iata, link.to.iata)).toSet.size
    }.toMap
    val sortedLinkCountByAirline = lowIncomeUniqueIataCounts.toList.sortBy(_._2)(Ordering[Int].reverse)
    var prevValue: Int = 0
    var prevRanking: Int = 0
    sortedLinkCountByAirline.zipWithIndex.map {
      case ((airlineId, linkCount), index) => {
        prevRanking = if (prevValue == linkCount) prevRanking else index
        prevValue = linkCount
        Ranking(RankingType.LINK_COUNT_LOW_INCOME,
          key = AirlineKey(airlineId),
          entry = airlinesById.getOrElse(airlineId, Airline.fromId(airlineId)),
          ranking = index + 1,
          rankedValue = prevValue,
          reputationPrize = reputationBonus(32, index)
        )
      }
    }.toList.take(200)
  }

  private[this] def getIataRanking(linksByAirline: Map[Int, List[Link]], airlinesById: Map[Int, Airline]): List[Ranking] = {
    val uniqueAirportCounts : Map[Int, Int] = linksByAirline.mapValues { links =>
      links.flatMap(link => Seq(link.from.iata, link.to.iata)).toSet.size
    }.toMap
    val sortedLinkCountByAirline = uniqueAirportCounts.toList.sortBy(_._2)(Ordering[Int].reverse)
    var prevValue: Int = 0
    var prevRanking: Int = 0
    sortedLinkCountByAirline.zipWithIndex.map {
      case ((airlineId, linkCount), index) => {
        prevRanking = if (prevValue == linkCount) prevRanking else index
        prevValue = linkCount
        Ranking(RankingType.UNIQUE_IATA,
          key = AirlineKey(airlineId),
          entry = airlinesById.getOrElse(airlineId, Airline.fromId(airlineId)),
          ranking = prevRanking + 1,
          rankedValue = prevValue,
          reputationPrize = reputationBonus(32, prevRanking)
        )
      }
    }.toList.take(200)
  }

  private[this] def getAirportTrafficRanking(flightConsumptionsByAirline: Map[Int, List[LinkConsumptionDetails]], airlinesById: Map[Int, Airline]): List[Ranking] = {
    // For each airline, count total sold seats at each airport (from and to)
    val busiestAirportPerAirline: Seq[((Int, Int), (Airline, Airport, Long))] = flightConsumptionsByAirline.flatMap { case (airlineId, consumptions) =>
      val airportTraffic: Map[Airport, Long] = consumptions.foldLeft(Map.empty[Airport, Long]) { (acc, consumption) =>
        val fromAirport = consumption.link.from
        val toAirport = consumption.link.to
        val pax = consumption.link.soldSeats.total.toLong
        acc.updated(fromAirport, acc.getOrElse(fromAirport, 0L) + pax).updated(toAirport, acc.getOrElse(toAirport, 0L) + pax)
      }
      if (airportTraffic.nonEmpty) {
        val (busiestAirport, totalPax) = airportTraffic.maxBy(_._2)
        Some(((airlineId, busiestAirport.id), (airlinesById.getOrElse(airlineId, Airline.fromId(airlineId)), busiestAirport, totalPax)))
      } else {
        None
      }
    }.toSeq

    busiestAirportPerAirline
      .sortBy { case (_, (_, _, totalPax)) => -totalPax }
      .zipWithIndex
      .map { case (((airlineId, airportId), (airline, airport, totalPax)), idx) =>
        Ranking(
          RankingType.AIRPORT_TRAFFIC,
          key = AirlineAirportKey(airlineId, airportId),
          entry = (airline, airport),
          ranking = idx + 1,
          rankedValue = totalPax,
          reputationPrize = reputationBonus(32, idx)
        )
      }.toList.take(200)
  }

  private[this] def getValueRanking(incomes: List[AirlineBalance], airlinesById: Map[Int, Airline]): List[Ranking] = {
    incomes.filter { income =>
      airlinesById.contains(income.airlineId)
    }.map { income =>
      (income.airlineId, (income.totalValue / 1000000).toInt)
    }.sortBy(_._2)(Ordering[Int].reverse).zipWithIndex.map {
      case ((airlineId, value), index) => {
        Ranking(
          RankingType.AIRLINE_VALUE,
          key = AirlineKey(airlineId),
          entry = airlinesById.getOrElse(airlineId, Airline.fromId(airlineId)),
          ranking = index + 1,
          rankedValue = value
        )
      }
    }.take(200)
  }

  private[this] def getStockRanking(airlinesById: Map[Int, Airline]): List[Ranking] = {
    airlinesById.map { case (id, airline) =>
      (id, airline.getStockPrice())
    }.toList.filter(_._2 > 0).sortBy(_._2)(Ordering[Double].reverse).zipWithIndex.map {
      case ((airlineId, stockPrice), index) => {
        Ranking(
          RankingType.STOCK_PRICE,
          key = AirlineKey(airlineId),
          entry = airlinesById.getOrElse(airlineId, Airline.fromId(airlineId)),
          ranking = index + 1,
          rankedValue = BigDecimal(stockPrice).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble,
          reputationPrize = reputationBonus(32, index)
        )
      }
    }.toList.take(200)
  }

  private[this] def getAirportRanking(paxByAirport : Map[Airport, Long]) : List[Ranking] = {
    paxByAirport.toList.sortBy(_._2)(Ordering[Long].reverse).zipWithIndex.map {
      case((airport, passengers), index) => Ranking(RankingType.AIRPORT,
        key = AirportKey(airport.id),
        entry = airport,
        ranking = index + 1,
        rankedValue = passengers)
    }.toList.take(20)
  }

  private[this] def getPaxStat(linkConsumptions: List[LinkConsumptionDetails]): (Map[Airport, Long], Map[(Airport, Airport), Long]) = {
    val passengersByAirport = scala.collection.mutable.Map[Airport, Long]()
    val passengersByAirportPair = scala.collection.mutable.Map[(Airport, Airport), Long]()

    linkConsumptions.foreach { consumption =>
      val fromAirport = consumption.link.from
      val toAirport = consumption.link.to
      val pair = if (fromAirport.id < toAirport.id) (fromAirport, toAirport) else (toAirport, fromAirport)
      val passengers = consumption.link.getTotalSoldSeats.toLong
      passengersByAirport.put(fromAirport, passengersByAirport.getOrElse(fromAirport, 0L) + passengers)
      passengersByAirport.put(toAirport, passengersByAirport.getOrElse(toAirport, 0L) + passengers)
      passengersByAirportPair.put(pair, passengersByAirportPair.getOrElse(pair, 0L) + passengers)
    }
    (passengersByAirport.toMap, passengersByAirportPair.toMap)
  }

  private[this] def getAirportPairRanking(paxByAirportPair: Map[(Airport, Airport), Long], pairFilter: (Airport, Airport) => Boolean): List[Ranking] = {
    paxByAirportPair.toList.filter {
      case ((airport1, airport2), _) => pairFilter(airport1, airport2)
    }.sortBy(_._2)(Ordering[Long].reverse).zipWithIndex.map {
      case (((airport1, airport2), passengers), index) => Ranking(
        RankingType.AIRPORT,
        key = AirportPairKey(airport1.id, airport2.id).canonical,
        entry = (airport1, airport2),
        ranking = index + 1,
        rankedValue = passengers)
    }.toList.take(40) //40 max for now
  }

  private[this] def getAirlinePrestigeRanking(airlinesById: Map[Int, Airline]): List[Ranking] = {
    val prestigeByAirline: List[(Int, Int)] = airlinesById.map { case (airlineId, airline) =>
      (airlineId, airline.getPrestigePoints())
    }.toList.sortBy(_._2)(Ordering[Int].reverse)

    prestigeByAirline.zipWithIndex.map {
      case ((airlineId, prestige), index) =>
        Ranking(
          RankingType.AIRLINE_PRESTIGE,
          key = AirlineKey(airlineId),
          entry = airlinesById.getOrElse(airlineId, Airline.fromId(airlineId)),
          ranking = index + 1,
          rankedValue = prestige,
        )
    }.take(200)
  }

  private[this] def updateMovements(previousRankings : Map[RankingType.Value, List[Ranking]], newRankings : Map[RankingType.Value, List[Ranking]]) = {
    val previousRankingsByKey : Map[RankingType.Value, Map[RankingKey, Int]] = previousRankings.view.mapValues { rankings =>
      rankings.map(ranking => (ranking.key, ranking.ranking)).toMap
    }.toMap

    newRankings.foreach {
      case(rankingType, rankings) =>  {
        val previousRankingOfThisType = previousRankingsByKey.get(rankingType)
        if (previousRankingOfThisType.isDefined) {
          rankings.foreach { newRankingEntry =>
            val previousRanking = previousRankingOfThisType.get.get(newRankingEntry.key)
            if (previousRanking.isDefined) {
              newRankingEntry.movement = newRankingEntry.ranking - previousRanking.get
            }
          }
        }
      }
    }
  }

  private[this] def getAllianceTravelersRanking(currentCycle: Int): List[Ranking] = {
    val allianceStats = AllianceSource.loadAllianceStatsByCycle(currentCycle)
    val sortedByTravelers = allianceStats.sortBy(_.travelerPax)(Ordering[Long].reverse)

    sortedByTravelers.zipWithIndex.map {
      case (stats, index) => Ranking(
        RankingType.ALLIANCE_TRAVELERS,
        key = AllianceKey(stats.alliance.id),
        entry = stats.alliance,
        ranking = index + 1,
        rankedValue = stats.travelerPax,
        reputationPrize = reputationBonus(18, index)
      )
    }.sortBy(_.ranking).take(20)
  }

  private[this] def getAllianceTouristRanking(currentCycle: Int): List[Ranking] = {
    val allianceStats = AllianceSource.loadAllianceStatsByCycle(currentCycle)
    val sortedByTourists = allianceStats.sortBy(_.touristPax)(Ordering[Long].reverse)

    sortedByTourists.zipWithIndex.map {
      case (stats, index) => Ranking(
        RankingType.ALLIANCE_TOURISTS,
        key = AllianceKey(stats.alliance.id),
        entry = stats.alliance,
        ranking = index + 1,
        rankedValue = stats.touristPax,
        reputationPrize = reputationBonus(18, index)
      )
    }.sortBy(_.ranking).take(20)
  }

  private[this] def getAllianceEliteRanking(currentCycle: Int): List[Ranking] = {
    val allianceStats = AllianceSource.loadAllianceStatsByCycle(currentCycle)
    val sortedByElite = allianceStats.sortBy(_.elitePax)(Ordering[Long].reverse)

    sortedByElite.zipWithIndex.map {
      case (stats, index) => Ranking(
        RankingType.ALLIANCE_ELITE,
        key = AllianceKey(stats.alliance.id),
        entry = stats.alliance,
        ranking = index + 1,
        rankedValue = stats.elitePax,
        reputationPrize = reputationBonus(18, index)
      )
    }.sortBy(_.ranking).take(20)
  }

  private[this] def getAllianceAirportRepRanking(currentCycle: Int): List[Ranking] = {
    val allianceStats = AllianceSource.loadAllianceStatsByCycle(currentCycle)
    val sortedByAirportRep = allianceStats.sortBy(_.airportRep)(Ordering[Int].reverse)

    sortedByAirportRep.zipWithIndex.map {
      case (stats, index) => Ranking(
        RankingType.ALLIANCE_AIRPORT_REP,
        key = AllianceKey(stats.alliance.id),
        entry = stats.alliance,
        ranking = index + 1,
        rankedValue = BigDecimal(stats.airportRep),
        reputationPrize = reputationBonus(18, index)
      )
    }.sortBy(_.ranking).take(20)
  }

  private[this] def getAllianceLoungeRanking(currentCycle: Int): List[Ranking] = {
    val allianceStats = AllianceSource.loadAllianceStatsByCycle(currentCycle)
    val sortedByLounge = allianceStats.sortBy(_.loungeVisit)(Ordering[Long].reverse)

    sortedByLounge.zipWithIndex.map {
      case (stats, index) => Ranking(
        RankingType.ALLIANCE_LOUNGE,
        key = AllianceKey(stats.alliance.id),
        entry = stats.alliance,
        ranking = index + 1,
        rankedValue = stats.loungeVisit,
        reputationPrize = reputationBonus(18, index)
      )
    }.sortBy(_.ranking).take(20)
  }

  private[this] def getAllianceStockRanking(currentCycle: Int): List[Ranking] = {
    val allianceStats = AllianceSource.loadAllianceStatsByCycle(currentCycle)
    val sorted = allianceStats.sortBy(_.airlineMarketCap)(Ordering[Long].reverse)

    sorted.zipWithIndex.map {
      case (stats, index) => Ranking(
        RankingType.ALLIANCE_STOCKS,
        key = AllianceKey(stats.alliance.id),
        entry = stats.alliance,
        ranking = index + 1,
        rankedValue = stats.airlineMarketCap,
        reputationPrize = reputationBonus(18, index)
      )
    }.sortBy(_.ranking).take(20)
  }
}
