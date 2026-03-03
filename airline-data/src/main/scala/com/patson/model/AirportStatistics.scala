package com.patson.model

case class AirportStatistics(
  airportId: Int,
  baselineDemand: Int,
  fromPax: Int,
  congestion: Double,
  reputation: Double,
  travelRate: Double
)

case class AirportStatisticsUpdate(
  airportId: Int,
  fromPax: Int,
  congestion: Double,
  reputation: Double,
  travelRate: Double
)