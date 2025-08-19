package com.patson.model

case class StockMetric(value: Int, floor: Double, target: Double)

object StockModel {
  val STOCK_EXPONENT = 2.3
  val STOCK_BROKER_FEE = 0.08
  val STOCK_BROKER_FEE_BASE = 750_000
  val STOCK_BUYBACK_MIN_CHANGE = 0.03
  val STOCK_BUYBACK_MAX_CHANGE = 0.12

  val allMetrics: Map[String, StockMetric] = Map(
    "eps" ->          StockMetric(24, 0.0, 25.0),
    "pask" ->         StockMetric(12, 0.03, 0.07),
    "satisfaction" -> StockMetric(4, 0.5, 0.95),
    "link_count" ->   StockMetric(4, 50, 400),
    "on_time" ->      StockMetric(4, 0.7, 0.98),
    "airport" ->      StockMetric(4, 5, 500),
    "codeshares" ->   StockMetric(4, 100, 100000),
    "rep_leaderboards" ->  StockMetric(4, 0, 200),
    "cash" ->         StockMetric(4, 48, 12),
    "interest" ->     StockMetric(4, 0.19, 0)
  )

  /**
   * compare value to floor and ceiling of metric, then multiply by its value
   *
   * @param metric
   * @param airlineValue
   * @return Double
   */
  def getMetricValue(metricKey: String, airlineValue: Double): Double = {
    val metric: StockMetric = allMetrics.getOrElse(metricKey, StockMetric(0, 0, 0))
    val targetPercent = 1 - (metric.target - airlineValue) / (metric.target - metric.floor)
    metric.value * Math.max(0, Math.min(1, targetPercent))
  }
}