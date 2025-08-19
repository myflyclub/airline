package com.patson.model

case class WorldStatistics(
  week: Int,
  period: Period.Value,
  totalPax: Int,
  missedPax: Int
)