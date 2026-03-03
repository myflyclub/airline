package com.patson.init

import com.patson.DemandGenerator
import com.patson.data.{AirportStatisticsSource}
import com.patson.model._

import scala.concurrent.Await
import scala.concurrent.duration.Duration

/**
  *
  */
object AirportStatsInit extends App {

  mainFlow

  def mainFlow() {

    initAirportStats()

    Await.result(actorSystem.terminate(), Duration.Inf)
  }

  def initAirportStats() = {
    val demands = DemandGenerator.computeDemand(0, Map(0 -> AirportStatistics(0,0,0,0.0,0.0,0.0)))
    val totalDemandByAirport = demands.foldLeft(Map[Airport, Int]().withDefaultValue(0)) {
      case (acc, (passengerGroup, toAirport, demand)) =>
        // Add the demand to the total for the 'from' airport
        acc.updated(passengerGroup.fromAirport, acc(passengerGroup.fromAirport) + demand)
    }
    val initAirportStats: List[AirportStatistics] = totalDemandByAirport.map { case (airport, demand) =>
      AirportStatistics(airport.id, demand, 0, 0.0, 0.0, 0.0)
    }.toList
    AirportStatisticsSource.updateBaselineStats(initAirportStats)
  }

}