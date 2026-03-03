package controllers

import com.patson.AirportSimulation
import com.patson.data.{AirportSource, AirportStatisticsSource, CountrySource, LinkStatisticsSource, LoyalistSource}
import com.patson.model._
import com.patson.util.{AirlineCache, AirportCache, AirportChampionInfo, ChampionUtil, CountryCache}
import models.AirportWithChampionAndStats
import websocket.MyWebSocketActor

import scala.collection.MapView
import scala.jdk.CollectionConverters._

object AirportUtil {
  var cachedAirportChampions : List[AirportWithChampionAndStats] = getAirportChampions()

  def getAirportChampions() : List[AirportWithChampionAndStats] = {
    val latestHistoryCycle = AirportSimulation.getHistoryCycle(MyWebSocketActor.lastSimulatedCycle, 0)
    val compareToCycle = AirportSimulation.getHistoryCycle(MyWebSocketActor.lastSimulatedCycle, -2)
    val cycleDelta = latestHistoryCycle - compareToCycle
    val allAirportsLatestLoyalists : MapView[Int, Map[Int, Int]] = LoyalistSource.loadLoyalistHistoryByCycle(latestHistoryCycle).groupBy(_.entry.airport.id).view.mapValues(_.map(history => (history.entry.airline.id, history.entry.amount)).toMap)
    val allAirportsPreviousLoyalists : MapView[Int, Map[Int, Int]] = LoyalistSource.loadLoyalistHistoryByCycle(compareToCycle).groupBy(_.entry.airport.id).view.mapValues(_.map(history => (history.entry.airline.id, history.entry.amount)).toMap)
    val loyalistByAirportId : Map[Int, List[AirportChampionInfo]] = ChampionUtil.loadAirportChampionInfo().groupBy(_.loyalist.airport.id)

    val allAirports = AirportCache.getAllAirports(fullLoad = true)
    
    allAirports.sortBy(_.popMiddleIncome).map { airport =>
      val stats = AirportStatisticsSource.loadAirportStatsById(airport.id).getOrElse(AirportStatistics(0,0,0,0,0,0))
      val travelRate = (stats.travelRate * 100).toInt
      val congestion = if (stats.congestion < 0.2) None else Some((stats.congestion * 100).toInt)
      loyalistByAirportId.get(airport.id) match {
        case Some(loyalists) =>
          val airlinesSortByRank = loyalists.sortBy(_.ranking).map(_.loyalist.airline)
          val championAirline = airlinesSortByRank.headOption
          val contestingAirline = {
            if (airlinesSortByRank.size < 2) {
              None
            } else {
              val championCurrentLoyalistCount = getLoyalistCount(allAirportsLatestLoyalists, airport, airlinesSortByRank(0))
              val contenderCurrentLoyalistCount = getLoyalistCount(allAirportsLatestLoyalists, airport, airlinesSortByRank(1))
              val championLoyalistDeltaPerCycle = (championCurrentLoyalistCount - getLoyalistCount(allAirportsPreviousLoyalists, airport, airlinesSortByRank(0))).toDouble / cycleDelta
              val contenderLoyalistDeltaPerCycle = (contenderCurrentLoyalistCount - getLoyalistCount(allAirportsPreviousLoyalists, airport, airlinesSortByRank(1))).toDouble / cycleDelta

              val predictionDuration = 200 //what about 200 cycles from now?
              val championPredictedLoyalistCount = championCurrentLoyalistCount + predictionDuration * championLoyalistDeltaPerCycle
              val contenderPredictedLoyalistCount = contenderCurrentLoyalistCount + predictionDuration * contenderLoyalistDeltaPerCycle
              if (contenderPredictedLoyalistCount > championPredictedLoyalistCount) {
                Some(airlinesSortByRank(1))
              } else {
                None
              }
            }
          }
          
          AirportWithChampionAndStats(airport, travelRate, stats.reputation, congestion, championAirline, contestingAirline)
        case None =>
          AirportWithChampionAndStats(airport, travelRate, stats.reputation, congestion, None, None)
      }
    }
  }

  def getLoyalistCount(allAirportsLoyalists : MapView[Int, Map[Int, Int]], airport : Airport, airline : Airline) : Int = {
    allAirportsLoyalists.get(airport.id) match {
      case Some(loyalistsByAirline) => loyalistsByAirline.getOrElse(airline.id, 0)
      case None => 0
    }
  }

  def refreshAirports() = {
    cachedAirportChampions = getAirportChampions()
  }
}

