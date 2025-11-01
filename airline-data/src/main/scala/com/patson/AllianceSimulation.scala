package com.patson

import com.patson.data.{AirlineSource, AllianceSource}
import com.patson.model._
import com.patson.model.alliance._
import com.patson.util.{AirlineCache, AirportChampionInfo, CountryChampionInfo}

import scala.collection.{immutable, mutable}

object AllianceSimulation {
  def simulate(flightLinkResult: List[LinkConsumptionDetails], loungeResult: List[LoungeConsumptionDetails], paxStatsByAirlineId: immutable.Map[Int, AirlinePaxStat], airportChampionInfo: List[AirportChampionInfo], cycle: Int): Unit = {
    println("Tallying alliance stats...")
    val allActiveAlliances = AllianceSource.loadAllAlliancesEstablished(true)
    val airportRepByAirlineId: Map[Int, Double] = airportChampionInfo.groupBy(_.loyalist.airline.id).mapValues(_.map(_.reputationBoost).sum).toMap
    val loungePaxByAirlineId: Map[Int, Int] = loungeResult.map(info => info.lounge.airline.id -> info.allianceVisitors).toMap
    val flightProfitsByAirlineId: Map[Int, Int] = flightLinkResult.map(linkConsumption => linkConsumption.link.airline.id -> linkConsumption.profit).toMap

    val allianceStatsList: List[AllianceStats] = allActiveAlliances.map { alliance =>
      // Fold member stats into a single 8-tuple of totals
      val memberTotals: (Long, Long, Long, Long, Double, Long, Long, Long) = alliance.members.foldLeft((0L, 0L, 0L, 0L, 0.0, 0L, 0L, 0L)) {
        case ((travTot, busTot, eliteTot, tourTot, repTot, capTot, loungeTot, profitTot), allianceMember) =>
          val airlineId = allianceMember.airline.id
          val paxStats = paxStatsByAirlineId.getOrElse(airlineId, AirlinePaxStat.empty)
          val traveler = paxStats.total - (paxStats.business + paxStats.tourists + paxStats.elites)
          val airportRep = airportRepByAirlineId.getOrElse(airlineId, 0.0)
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
  }

}
