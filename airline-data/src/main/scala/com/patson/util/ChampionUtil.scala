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

  val reputationBoostTop10 : Map[Int, Double] = Map(
    1 -> 1,
    2 -> 0.5,
    3 -> 0.3,
    4 -> 0.2,
    5 -> 0.1,
    6 -> 0.08,
    7 -> 0.06,
    8 -> 0.04,
    9 -> 0.03,
    10 -> 0.02
  )

  def computeAirportChampionInfo(loyalists: List[Loyalist]) = {
    val result = ListBuffer[AirportChampionInfo]()
    val allAirportReps = AirportStatisticsSource.loadAllAirportStats().map(stats => stats.airportId -> stats.reputation).toMap

    loyalists.groupBy(_.airport.id).foreach {
      case (airportId, loyalists) =>
        val airport = AirportCache.getAirport(airportId).get
        val championCount = airport.size
        val loyalistToPopRatio = Math.min(1, loyalists.map(_.amount).sum.toDouble / airport.popMiddleIncome) //just in case the loyalist is out of wack, ie > pop
        val topAirlineWithSortedIndex : List[(Loyalist, Int)] = loyalists.sortBy(_.amount)(Ordering.Int.reverse).take(championCount).zipWithIndex

        val championInfoForThisAirport = topAirlineWithSortedIndex.map {
          case(loyalist, index) =>
            val ranking = index + 1
            val reputationBoost = allAirportReps.getOrElse(airport.id, 0.0) * loyalistToPopRatio * reputationBoostTop10(ranking)
            Some(AirportChampionInfo(loyalist, ranking, reputationBoost))
        }
        result ++= championInfoForThisAirport.flatten
    }
    result.toList
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