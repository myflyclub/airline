package com.patson

import com.patson.data.{AirlineSource, AllianceSource}
import com.patson.model._
import com.patson.model.alliance._
import com.patson.util.{AirlineCache, AirportChampionInfo, CountryChampionInfo}

import scala.collection.{immutable, mutable}

object AllianceSimulation {
  val BOOKKEEPING_ENTRIES_COUNT = 25

  def simulate(flightLinkResult: List[LinkConsumptionDetails], loungeResult: List[LoungeConsumptionDetails], paxStatsByAirlineId: immutable.Map[Int, AirlinePaxStat], airportChampionInfo: List[AirportChampionInfo], cycle: Int): Unit = {
    println("Tallying alliance stats...")
    val allActiveAlliances = AllianceSource.loadAllAlliancesEstablished(true)
    val airportRepByAirlineId: Map[Int, Int] = airportChampionInfo.groupBy(_.loyalist.airline.id).mapValues(_.map(_.reputationBoost).sum.toInt).toMap
    val loungePaxByAirlineId: Map[Int, Int] = loungeResult.map(info => info.lounge.airline.id -> info.allianceVisitors).toMap
    val flightProfitsByAirlineId: Map[Int, Int] = flightLinkResult.map(linkConsumption => linkConsumption.link.airline.id -> linkConsumption.profit).toMap

    val allianceStatsList: List[AllianceStats] = allActiveAlliances.map { alliance =>
      // Fold member stats into a single 8-tuple of totals
      val memberTotals: (Long, Long, Long, Long, Int, Long, Long, Long) = alliance.members.foldLeft((0L, 0L, 0L, 0L, 0, 0L, 0L, 0L)) {
        case ((travTot, busTot, eliteTot, tourTot, repTot, capTot, loungeTot, profitTot), allianceMember) =>
          val airlineId = allianceMember.airline.id
          val paxStats = paxStatsByAirlineId.getOrElse(airlineId, AirlinePaxStat.empty)
          val traveler = paxStats.total - (paxStats.business + paxStats.tourists + paxStats.elites)
          val airportRep = airportRepByAirlineId.getOrElse(airlineId, 0).toInt
          val marketCap: Long = (allianceMember.airline.getSharesOutstanding().toLong * allianceMember.airline.getStockPrice()).toLong
          val loungePax = loungePaxByAirlineId.getOrElse(airlineId, 0)
          val flightProfit = flightProfitsByAirlineId.getOrElse(airlineId, 0)

          (
            travTot + traveler,
            busTot + paxStats.business,
            eliteTot + paxStats.elites,
            tourTot + paxStats.tourists,
            repTot + airportRep,
            capTot + marketCap,
            loungeTot + loungePax,
            profitTot + flightProfit
          )
      }

      AllianceStats(alliance, memberTotals._1, memberTotals._2, memberTotals._3, memberTotals._4, memberTotals._5, memberTotals._6, memberTotals._7, memberTotals._8, cycle)
    }

    AllianceSource.saveAllianceStats(allianceStatsList)

    // Accumulate at period boundaries
    if ((cycle + 1) % Period.numberWeeks(Period.QUARTER) == 0)
      computeAndSaveAccumulation(cycle, allActiveAlliances, Period.QUARTER)
    if ((cycle + 1) % Period.numberWeeks(Period.YEAR) == 0)
      computeAndSaveAccumulation(cycle, allActiveAlliances, Period.YEAR)

    // Period-aware purging
    AllianceSource.deleteAllianceStatsBefore(cycle - BOOKKEEPING_ENTRIES_COUNT, Period.WEEKLY)
    AllianceSource.deleteAllianceStatsBefore(cycle - BOOKKEEPING_ENTRIES_COUNT * Period.numberWeeks(Period.QUARTER), Period.QUARTER)
    AllianceSource.deleteAllianceStatsBefore(cycle - BOOKKEEPING_ENTRIES_COUNT * Period.numberWeeks(Period.YEAR), Period.YEAR)
  }

  def computeAndSaveAccumulation(cycle: Int, alliances: List[Alliance], period: Period.Value): Unit = {
    val periodWeeks = Period.numberWeeks(period)
    val startCycle = cycle - (cycle % periodWeeks) + 1
    val endCycle = cycle

    val weeklyStatsByAlliance = AllianceSource.loadAllianceStatsByCycleRange(startCycle, endCycle, Period.WEEKLY)
      .groupBy(_.alliance.id)

    val periodStats = alliances.flatMap { alliance =>
      weeklyStatsByAlliance.get(alliance.id).filter(_.nonEmpty).map { weeks =>
        val count = weeks.length
        val summed = weeks.reduce { (acc, week) =>
          AllianceStats(
            alliance = acc.alliance,
            travelerPax = acc.travelerPax + week.travelerPax,
            businessPax = acc.businessPax + week.businessPax,
            elitePax = acc.elitePax + week.elitePax,
            touristPax = acc.touristPax + week.touristPax,
            airportRep = acc.airportRep + week.airportRep,
            airlineMarketCap = acc.airlineMarketCap + week.airlineMarketCap,
            loungeVisit = acc.loungeVisit + week.loungeVisit,
            profit = acc.profit + week.profit,
            cycle = cycle,
            period = period
          )
        }
        // Average the per-member metrics; sum the flow metrics
        summed.copy(
          airportRep = summed.airportRep / count,
          airlineMarketCap = summed.airlineMarketCap / count
        )
      }
    }

    AllianceSource.saveAllianceStats(periodStats)
  }

}
