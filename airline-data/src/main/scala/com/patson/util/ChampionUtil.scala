package com.patson.util

import com.patson.model._
import com.patson.data.{AirlineSource, AirportSource, AirportStatisticsSource, Constants, CountrySource, LoyalistSource}

import scala.collection.mutable.ListBuffer
import scala.math.BigDecimal.RoundingMode

case class CountryChampionInfo(airline : Airline, country : Country, passengerCount : Long, ranking : Int)
case class AirportChampionInfo(loyalist : Loyalist, ranking : Int, reputationBoost : Double)

object ChampionUtil {
  def getCountryChampionInfoByAirlineId(airlineId : Int): List[CountryChampionInfo] = {
    getCountryChampionInfoByFilter(checkAirlineId => airlineId == checkAirlineId, List.empty)
  }

  def getCountryChampionInfoByCountryCode(countryCode : String): List[CountryChampionInfo] = {
    getCountryChampionInfoByFilter(_ => true, List(("country", countryCode)))
  }

  def getCountryChampionInfoByFilter(airlineIdFilter : Int => Boolean, marketShareCriteria : List[(String, Any)]) = {
    val result = ListBuffer[CountryChampionInfo]()

    val allMarketShares = CountrySource.loadMarketSharesByCriteria(marketShareCriteria)

    val airlineIds = allMarketShares.flatMap(_.airlineShares.keys).toList.filter(airlineIdFilter)

    val airlines = AirlineSource.loadAirlinesByIds(airlineIds, false).map(airline => (airline.id, airline)).toMap

    allMarketShares.map {
      case CountryMarketShare(countryCode, airlineShares) => {
        val country = CountryCache.getCountry(countryCode).get
        val topAirlineSharesWithSortedIndex : List[((Int, Long), Int)] = airlineShares.toList.sortBy(_._2)(Ordering.Long.reverse).take(10).zipWithIndex

        val championInfoForThisCountry = topAirlineSharesWithSortedIndex.map {
          case((airlineId, passengerCount), index) => {
            if (airlineIdFilter(airlineId)) {
              val ranking = index + 1
              Some(CountryChampionInfo(airlines.getOrElse(airlineId, Airline.fromId(airlineId)), country, passengerCount, ranking))
            } else {
              None
            }
          }
        }
        result ++= championInfoForThisCountry.flatten
      }
    }
    
    result.toList
  }

  def computeAirportChampionInfo(loyalists: List[Loyalist]): List[AirportChampionInfo] = {
    val allAirportReps = AirportStatisticsSource.loadAllAirportStats()
      .map(stats => stats.airportId -> stats.reputation)
      .toMap

    loyalists.groupBy(_.airport.id).toList.flatMap { case (airportId, airportLoyalists) =>
      AirportCache.getAirport(airportId) match {
        case None => Nil

        case Some(airport) =>
          val airportSize = airport.size + 1
          val totalLoyalistAmount = airportLoyalists.iterator.map(_.amount).sum
          val loyalistToPopRatio = math.min(1.0, totalLoyalistAmount.toDouble / airport.popMiddleIncome)
          val baseReputation = allAirportReps.getOrElse(airportId, 0.0)

          airportLoyalists
            .sortBy(-_.amount)
            .take(airportSize)
            .zipWithIndex
            .map { case (loyalist, index) =>
              val ranking = index + 1

              val rankingScoreRatio = math.exp(-2.6 * (ranking - 1).toDouble / airportSize)
              val reputationBoost = math.min(500.0, baseReputation * loyalistToPopRatio * rankingScoreRatio)

              AirportChampionInfo(loyalist, ranking, reputationBoost)
            }
      }
    }
  }

  def loadAirportChampionInfo() = {
    AirportSource.loadChampionInfoByCriteria(List.empty)
  }

  def loadAirportChampionInfoByAirline(airlineId : Int) = {
    AirportSource.loadChampionInfoByCriteria(List(("airline", airlineId)))
  }

  def loadAirportChampionInfoByAirport(airportId : Int) = {
    AirportSource.loadChampionInfoByCriteria(List(("airport", airportId)))
  }
}