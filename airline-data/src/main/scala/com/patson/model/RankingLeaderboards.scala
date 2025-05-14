package com.patson.model

import com.patson.data.{AirlineSource, AirlineStatisticsSource, CycleSource, IncomeSource, LinkSource, LoungeHistorySource}
import com.patson.model.AirlineType


object RankingLeaderboards {
  @volatile private var cachedRankings: Map[RankingType.Value, List[Ranking]] =
    Map.empty
  @volatile private var cacheCycle: Int = -1

  def getRankings(isCycleUpdate : Boolean = false) : Map[RankingType.Value, List[Ranking]] = {
    checkOrUpdateCache(isCycleUpdate)
    cachedRankings
  }

  def getAirlineReputationPrizes(forceUpdate: Boolean = false): Map[Int, Double] = {
    val currentRankings = getRankings(forceUpdate)

    currentRankings.values.flatten // Flatten List[List[Ranking]] into List[Ranking]
      .filter(_.reputationPrize.isDefined) // Keep only rankings with a prize
      .groupBy(_.key) // Group by the key (assuming key is airlineId for prize rankings)
      .collect { case (airlineId: Int, rankingsList) => // Ensure key is Int
        (
          airlineId,
          rankingsList.map(_.reputationPrize.getOrElse(0.0)).sum
        )
      }
  }

  def reputationBonus(maxBonus: Int, rank: Int, airlineType: AirlineType) = {
    val isMegaHQAirline = if (airlineType == MegaHqAirline) 3 else 1
    val denominator = 16
    val bonus = rank match {
      case 0 => maxBonus
      case 1 => maxBonus * 12 / denominator
      case 2 => maxBonus * 10 / denominator
      case 3 => maxBonus * 8 / denominator
      case 4 => maxBonus * 6 / denominator
      case 5 => maxBonus * 5 / denominator
      case 6 => maxBonus * 4 / denominator
      case 7 => maxBonus * 3 / denominator
      case _ => maxBonus * 2 / denominator
    }
    Some(if (rank >= 20) 0.0 else bonus.toDouble * isMegaHQAirline)
  }

  private def checkOrUpdateCache(forceUpdate: Boolean): Unit = {
    val currentCycle = CycleSource.loadCycle()
    val targetCycle = currentCycle - 1 // Rankings are based on the last completed cycle

    if (forceUpdate || cacheCycle != targetCycle) {
      synchronized {
        // Double-check inside synchronized block
        if (forceUpdate || cacheCycle != targetCycle) {
          println(s"Updating ranking cache for cycle $targetCycle, previous cycle is $cacheCycle")
          try {
            val newRankings = updateRankings(targetCycle)

            // Calculate movements based on the *previous* cache
            val previousRankingsSnapshot = cachedRankings
            updateMovements(previousRankingsSnapshot, newRankings)

            // Update the cache
            cachedRankings = newRankings
            cacheCycle = targetCycle
            println(s"Ranking cache updated successfully for cycle $targetCycle")
          } catch {
            case e: Exception =>
              println(s"Error updating ranking cache for cycle $targetCycle: ${e.getMessage}")
              e.printStackTrace()
             cachedRankings = Map.empty
          }
        }
      }
    }
  }

  private[this] def updateRankings(currentCycle: Int, isCycleUpdate: Boolean = false) = {
    val airlinesSource = AirlineSource.loadAllAirlines(fullLoad = true)
    val invalidAirlinesIds = airlinesSource.filter(airline => airline.airlineType == NonPlayerAirline || airline.getReputation < 60).map(_.id)
    val airlinesById = airlinesSource.filter(airline => ! invalidAirlinesIds.contains(airline.id)).map(airline => (airline.id, airline)).toMap
    val flightConsumptions = LinkSource.loadLinkConsumptions().filter(_.link.transportType == TransportType.FLIGHT).filter(_.link.soldSeats.total.toLong > 0).filterNot(consumption => invalidAirlinesIds.contains(consumption.link.airline.id))
    val flightConsumptionsByAirline = flightConsumptions.groupBy(_.link.airline.id)
    val loungeConsumptions = LoungeHistorySource.loadAll.filterNot(consumption => invalidAirlinesIds.contains(consumption.lounge.airline.id))
    val airlineStats = AirlineStatisticsSource.loadAirlineStatsByCycle(currentCycle).filterNot(stat => invalidAirlinesIds.contains(stat.airlineId))
    val airlineIncomes = IncomeSource.loadAllByCycle(currentCycle)
    val links = LinkSource.loadAllFlightLinks().filterNot(link => invalidAirlinesIds.contains(link.airline.id)).groupBy(_.airline.id)


    val updatedRankings = scala.collection.mutable.Map[RankingType.Value, List[Ranking]]()
    updatedRankings.put(RankingType.TOURIST_COUNT, getPaxRanking(airlineStats.map(stat => (stat.airlineId, stat.tourists)).toMap, airlinesById, RankingType.TOURIST_COUNT))
    updatedRankings.put(RankingType.BUSINESS_COUNT, getPaxRanking(airlineStats.map(stat => (stat.airlineId, stat.business)).toMap, airlinesById, RankingType.BUSINESS_COUNT))
    updatedRankings.put(RankingType.ELITE_COUNT, getPaxRanking(airlineStats.map(stat => (stat.airlineId, stat.elites)).toMap, airlinesById, RankingType.ELITE_COUNT))
    updatedRankings.put(RankingType.CODESHARE_COUNT, getPaxRanking(airlineStats.map(stat => (stat.airlineId, stat.codeshares)).toMap, airlinesById, RankingType.CODESHARE_COUNT))
    updatedRankings.put(RankingType.PASSENGER_SATISFACTION, getPassengerSFRanking(flightConsumptionsByAirline,airlinesById))
    updatedRankings.put(RankingType.PASSENGER_SPEED, getPassengerSpeedRanking(flightConsumptionsByAirline,airlinesById))
    updatedRankings.put(RankingType.LINK_COUNT_SMALL_TOWN, getSmallTownRanking(links, airlinesById))
    updatedRankings.put(RankingType.LINK_COUNT_LOW_INCOME, getLowIncomeRanking(links, airlinesById))
    updatedRankings.put(RankingType.UNIQUE_COUNTRIES, getCountriesRanking(links, airlinesById))
    updatedRankings.put(RankingType.UNIQUE_IATA, getIataRanking(links, airlinesById))
    updatedRankings.put(RankingType.STOCK_PRICE, getStockRanking(airlinesById))
    updatedRankings.put(RankingType.LINK_PROFIT, getLinkProfitRanking(flightConsumptions, airlinesById))
    updatedRankings.put(RankingType.LINK_LOSS, getLinkLossRanking(flightConsumptions, airlinesById))
    updatedRankings.put(RankingType.LINK_FREQUENCY, getLinkFrequent(flightConsumptions, airlinesById))
    updatedRankings.put(RankingType.PASSENGER_QUALITY, getPassengerQualityRanking(flightConsumptionsByAirline, airlinesById))
    updatedRankings.put(RankingType.LINK_DISTANCE, getLinkLongest(flightConsumptions, airlinesById))
    updatedRankings.put(RankingType.LINK_SHORTEST, getLinkShortest(flightConsumptions, airlinesById))
    updatedRankings.put(RankingType.LOUNGE, getLoungeRanking(loungeConsumptions, airlinesById))
    updatedRankings.put(RankingType.AIRLINE_PROFIT_MARGIN, getMarginRanking(airlineStats.map(stat => (stat.airlineId, (stat.CASK, stat.RASK))).toMap, airlinesById))
    updatedRankings.put(RankingType.AIRPORT_TRAFFIC, getAirportTrafficRanking(flightConsumptionsByAirline, airlinesById))

    //informational rankings
    if(!isCycleUpdate) {
      val (paxByAirport, paxByAirportPair) = getPaxStat(flightConsumptions)
      updatedRankings.put(RankingType.AIRLINE_VALUE, getValueRanking(airlineIncomes, airlinesById))
      updatedRankings.put(RankingType.AIRPORT, getAirportRanking(paxByAirport))
      updatedRankings.put(RankingType.INTERNATIONAL_PAX, getAirportPairRanking(paxByAirportPair, (airport1, airport2) => airport1.countryCode != airport2.countryCode))
      updatedRankings.put(RankingType.DOMESTIC_PAX, getAirportPairRanking(paxByAirportPair, (airport1, airport2) => airport1.countryCode == airport2.countryCode))
      updatedRankings.put(RankingType.PASSENGER_MILE, getPassengerMileRanking(flightConsumptionsByAirline, airlinesById))
      updatedRankings.put(RankingType.LINK_PROFIT_TOTAL, getLinkProfitTotalRanking(flightConsumptions, airlinesById))
    }

    updatedRankings.toMap
  }

  private[this] def getPaxRanking(pax : Map[Int, Int], airlinesById : Map[Int, Airline], rankingType : RankingType.Value): List[Ranking] = {
    val sortedPassengerByAirline = pax.toList.sortBy(_._2)(Ordering[Int].reverse)

    sortedPassengerByAirline.zipWithIndex.map {
      case ((airlineId, quanity), index) => Ranking(
        rankingType,
        key = airlineId,
        entry = airlinesById.getOrElse(airlineId, Airline.fromId(airlineId)),
        ranking = index + 1,
        rankedValue = quanity,
        reputationPrize = reputationBonus(20, index, airlinesById(airlineId).airlineType)
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
        key = airlineId,
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
        key = airlineId,
        entry = airlinesById.getOrElse(airlineId, Airline.fromId(airlineId)),
        ranking = index + 1,
        rankedValue = BigDecimal(quality).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble,
        reputationPrize = reputationBonus(20, index, airlinesById(airlineId).airlineType)
      )
    }.toList.sortBy(_.ranking).take(200)
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
        key = airlineId,
        entry = airlinesById.getOrElse(airlineId, Airline.fromId(airlineId)),
        ranking = index + 1,
        rankedValue = BigDecimal(satisfaction * 100).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble,
        reputationPrize = reputationBonus(16, index, airlinesById(airlineId).airlineType)
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
        key = airlineId,
        entry = airlinesById.getOrElse(airlineId, Airline.fromId(airlineId)),
        ranking = prevRanking + 1,
        rankedValue = BigDecimal(speed).toInt,
        reputationPrize = reputationBonus(12, prevRanking, airlinesById(airlineId).airlineType)
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
          RankingType.PASSENGER_MILE,
          key = airlineId,
          entry = linkConsumption.link.asInstanceOf[Link].copy(airline = airlinesById.getOrElse(airlineId, Airline.fromId(airlineId))),
          ranking = index + 1,
          rankedValue = linkConsumption.profit,
          reputationPrize = reputationBonus(16, index, airlinesById(airlineId).airlineType)
        )
        ranking
      }

    }.toList.sortBy(_.ranking).take(200)
  }

  private[this] def getLinkLossRanking(linkConsumptions: List[LinkConsumptionDetails], airlinesById: Map[Int, Airline]): List[Ranking] = {
    val leastProfitableLinks: List[LinkConsumptionDetails] = linkConsumptions
      .filter(_.profit < 0).sortBy(_.profit)(Ordering[Int])

    leastProfitableLinks.zipWithIndex.map {
      case (linkConsumption, index) => {
        val airlineId = linkConsumption.link.airline.id
        val ranking = Ranking(
          RankingType.PASSENGER_MILE,
          key = airlineId,
          entry = linkConsumption.link.asInstanceOf[Link].copy(airline = airlinesById.getOrElse(airlineId, Airline.fromId(airlineId))),
          ranking = index + 1,
          rankedValue = linkConsumption.profit,
          reputationPrize = reputationBonus(-8, index, airlinesById(airlineId).airlineType)
        )
        ranking
      }

    }.toList.sortBy(_.ranking).take(200)
  }

  private[this] def getLinkProfitTotalRanking(linkConsumptions: List[LinkConsumptionDetails], airlinesById: Map[Int, Airline]): List[Ranking] = {
    val mostProfitableLinks: List[LinkConsumptionDetails] = linkConsumptions.sortBy(_.profit)(Ordering[Int].reverse)

    mostProfitableLinks.zipWithIndex.map {
      case (linkConsumption, index) => {
        val airlineId = linkConsumption.link.airline.id
        val ranking = Ranking(RankingType.PASSENGER_MILE,
          key = airlineId,
          entry = linkConsumption.link.asInstanceOf[Link].copy(airline = airlinesById.getOrElse(airlineId, Airline.fromId(airlineId))),
          ranking = index + 1,
          rankedValue = linkConsumption.profit)
        ranking
      }

    }.toList.sortBy(_.ranking).take(500)
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
          key = airlineId,
          entry = linkConsumption.link.asInstanceOf[Link].copy(airline = airlinesById.getOrElse(airlineId, Airline.fromId(airlineId))),
          ranking = prevRanking + 1,
          rankedValue = linkConsumption.link.distance,
          reputationPrize = reputationBonus(16, prevRanking, airlinesById(airlineId).airlineType)
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
          key = airlineId,
          entry = linkConsumption.link.asInstanceOf[Link].copy(airline = airlinesById.getOrElse(airlineId, Airline.fromId(airlineId))),
          ranking = prevRanking + 1,
          rankedValue = linkConsumption.link.distance,
          reputationPrize = reputationBonus(16, prevRanking, airlinesById(airlineId).airlineType)
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
          key = airlineId,
          entry = linkConsumption.link.asInstanceOf[Link].copy(airline = airlinesById.getOrElse(airlineId, Airline.fromId(airlineId))),
          ranking = prevRanking + 1,
          rankedValue = linkConsumption.link.frequency,
          reputationPrize = reputationBonus(16, prevRanking, airlinesById(airlineId).airlineType)
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
        key = airlineId,
        entry = airlinesById.getOrElse(airlineId, Airline.fromId(airlineId)),
        ranking = index + 1,
        rankedValue = BigDecimal(quantity).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble,
        reputationPrize = reputationBonus(16, index, airlinesById(airlineId).airlineType)
      )
    }.sortBy(_.ranking).take(200)
  }

  private[this] def getLoungeRanking(loungeConsumptions : List[LoungeConsumptionDetails], airlinesById : Map[Int, Airline]) : List[Ranking] = {
    val mostVisitedLounges = loungeConsumptions.groupBy(_.lounge.airline.id)
      .mapValues(_.maxBy(entry => entry.selfVisitors + entry.allianceVisitors))
      .values.toList
      .sortBy(details => details.selfVisitors + details.allianceVisitors)(Ordering[Int].reverse)

    mostVisitedLounges.zipWithIndex.map {
      case(details, index) => {
        val lounge = details.lounge
        Ranking(RankingType.LOUNGE,
          key = lounge.airline.id,
          entry = lounge,
          ranking = index + 1,
          rankedValue = details.selfVisitors + details.allianceVisitors,
          reputationPrize = reputationBonus(16, index, airlinesById(lounge.airline.id).airlineType)
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
          key = airlineId,
          entry = airlinesById.getOrElse(airlineId, Airline.fromId(airlineId)),
          ranking = prevRanking + 1,
          rankedValue = prevValue,
          reputationPrize = reputationBonus(16, prevRanking, airlinesById(airlineId).airlineType)
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
          key = airlineId,
          entry = airlinesById.getOrElse(airlineId, Airline.fromId(airlineId)),
          ranking = prevRanking + 1,
          rankedValue = prevValue,
          reputationPrize = reputationBonus(16, prevRanking, airlinesById(airlineId).airlineType)
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
          key = airlineId,
          entry = airlinesById.getOrElse(airlineId, Airline.fromId(airlineId)),
          ranking = index + 1,
          rankedValue = prevValue,
          reputationPrize = reputationBonus(16, index, airlinesById(airlineId).airlineType)
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
          key = airlineId,
          entry = airlinesById.getOrElse(airlineId, Airline.fromId(airlineId)),
          ranking = prevRanking + 1,
          rankedValue = prevValue,
          reputationPrize = reputationBonus(16, prevRanking, airlinesById(airlineId).airlineType)
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
          key = (airlineId, airportId),
          entry = (airline, airport),
          ranking = idx + 1,
          rankedValue = totalPax,
          reputationPrize = reputationBonus(20, idx, airline.airlineType)
        )
      }.toList.take(200)
  }

  private[this] def getValueRanking(incomes: List[AirlineIncome], airlinesById: Map[Int, Airline]): List[Ranking] = {
    incomes.filter { income =>
      airlinesById.contains(income.airlineId)
    }.map { income =>
      (income.airlineId, (income.totalValue / 1000000).toInt)
    }.sortBy(_._2)(Ordering[Int].reverse).zipWithIndex.map {
      case ((airlineId, value), index) => {
        Ranking(
          RankingType.AIRLINE_VALUE,
          key = airlineId,
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
          key = airlineId,
          entry = airlinesById.getOrElse(airlineId, Airline.fromId(airlineId)),
          ranking = index + 1,
          rankedValue = BigDecimal(stockPrice).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble,
          reputationPrize = reputationBonus(20, index, airlinesById(airlineId).airlineType)
        )
      }
    }.toList.take(200)
  }

  private[this] def getAirportRanking(paxByAirport : Map[Airport, Long]) : List[Ranking] = {
    paxByAirport.toList.sortBy(_._2)(Ordering[Long].reverse).zipWithIndex.map {
      case((airport, passengers), index) => Ranking(RankingType.AIRPORT,
        key = airport.id,
        entry = airport,
        ranking = index + 1,
        rankedValue = passengers)
    }.toList.take(20) //40 max for now
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
        key = (airport1, airport2),
        entry = (airport1, airport2),
        ranking = index + 1,
        rankedValue = passengers)
    }.toList.take(40) //40 max for now
  }

  private[this] def updateMovements(previousRankings : Map[RankingType.Value, List[Ranking]], newRankings : Map[RankingType.Value, List[Ranking]]) = {
    val previousRankingsByKey : Map[RankingType.Value, Map[Any, Int]] = previousRankings.view.mapValues { rankings =>
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
}

object RankingType extends Enumeration {
  type RankingType = Value
  val PASSENGER_MILE, PASSENGER_COUNT, PASSENGER_QUALITY, PASSENGER_SPEED, ELITE_COUNT, TOURIST_COUNT, BUSINESS_COUNT, CODESHARE_COUNT, STOCK_PRICE, AIRLINE_PROFIT_MARGIN, AIRLINE_VALUE, PASSENGER_SATISFACTION, UNIQUE_COUNTRIES, UNIQUE_IATA, LINK_COUNT_SMALL_TOWN, LINK_COUNT_LOW_INCOME, LINK_LOSS, LINK_PROFIT, LINK_PROFIT_TOTAL, LINK_DISTANCE, LINK_SHORTEST, LINK_FREQUENCY, LOUNGE, AIRPORT, AIRPORT_TRANSFERS, INTERNATIONAL_PAX, DOMESTIC_PAX, AIRPORT_TRAFFIC = Value
}


case class Ranking(rankingType : RankingType.Value, key : Any, entry : Any, ranking : Int, rankedValue : Number, var movement : Int = 0, reputationPrize : Option[Double] = None )
