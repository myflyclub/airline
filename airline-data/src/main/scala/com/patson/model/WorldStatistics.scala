package com.patson.model

case class WorldStatistics(
  week: Int,
  period: Period.Value,
  totalPax: Int,
  totalTicketsSold: Int,
  missedPax: Int,
  loadFactor: Double
)