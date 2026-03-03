package com.patson.model

case class StockMetric(value: Int, floor: Double, target: Double)

object StockModel {
  val STOCK_EXPONENT = 2.3
  val STOCK_BROKER_FEE = 0.08
  val STOCK_BROKER_FEE_BASE = 750_000
  val STOCK_BUYBACK_MIN_CHANGE = 0.03
  val STOCK_BUYBACK_MAX_CHANGE = 0.12
  val TOOLTIP_STOCK_EPS = List(
    "Earnings per share is the primary metric with a 6x weight.",
    "Increasing your revenue and decreasing the shares outstanding will cause EPS to go up."
  )
  val TOOLTIP_STOCK_PASK = List(
    "Profit per Available Seat KM is the second most important metric with a 3x weight.",
    "Increase your Profit per Available Seat KM, or",
    "Lower your CASK – which includes all flight costs except fuel taxes and also includes base upkeep and base overtime.",
  )

  val allMetrics: Map[String, StockMetric] = Map(
    "eps" ->                  StockMetric(24, 0.0, 25.0),
    "pask" ->                 StockMetric(12, 0.03, 0.07),
    "satisfaction" ->         StockMetric(4, 0.5, 0.95),
    "link_count" ->           StockMetric(4, 50, 400),
    "on_time" ->              StockMetric(4, 0.7, 0.98),
    "airport" ->              StockMetric(4, 5, 500),
    "codeshares" ->           StockMetric(4, 100, 100000),
    "rep_leaderboards" ->     StockMetric(4, 0, 200),
    "months_cash_on_hand" ->  StockMetric(4, 48, 12),
    "interest" ->             StockMetric(8, 0.19, 0)
  )

  /**
   * compare value to floor and ceiling of metric, then multiply by its value
   *
   * @param metric
   * @param airlineValue
   * @return Double
   */
  private def getMetricValue(metricKey: String, airlineValue: Double): Double = {
    val metric: StockMetric = allMetrics.getOrElse(metricKey, StockMetric(0, 0, 0))
    val targetPercent = 1 - (metric.target - airlineValue) / (metric.target - metric.floor)
    metric.value * Math.max(0, Math.min(1, targetPercent))
  }

  private def getStockScore(stats: AirlineStat, currentInterestRate: Double): Double = {
    val sizeAdjust = (stats.eps / 0.6).max(0.001).min(1.0)

    Seq(
      StockModel.getMetricValue("eps", stats.eps),
      StockModel.getMetricValue("pask", stats.RASK - stats.CASK),
      StockModel.getMetricValue("satisfaction", stats.satisfaction),
      StockModel.getMetricValue("link_count", stats.linkCount),
      StockModel.getMetricValue("on_time", stats.onTime) * sizeAdjust,
      StockModel.getMetricValue("codeshares", stats.codeshares),
      StockModel.getMetricValue("rep_leaderboards", stats.repLeaderboards),
      StockModel.getMetricValue("months_cash_on_hand", stats.cashOnHand / 4),
      StockModel.getMetricValue("interest", currentInterestRate) * sizeAdjust
    ).sum
  }

  def updateStockPrice(stockPrice: Double, weeklyStats: AirlineStat, quarterStats: Option[AirlineStat], currentInterestRate: Double): Double = {
    val weeklyScore = getStockScore(weeklyStats, currentInterestRate)
    val quarterScore = quarterStats.map(getStockScore(_, currentInterestRate)).getOrElse(weeklyScore)

    val quarterlyBlendWeight = 0.4
    val blendedScore = weeklyScore * (1 - quarterlyBlendWeight) + quarterScore * quarterlyBlendWeight

    val result = if (blendedScore > stockPrice) {
      stockPrice * 0.15 + Math.pow(Math.max(0.01, blendedScore), StockModel.STOCK_EXPONENT) / 10 * 0.85
    } else {
      //price falls slower, making buybacks more impactful
      stockPrice * 0.6 + Math.pow(Math.max(0.01, blendedScore), StockModel.STOCK_EXPONENT) / 10 * 0.4
    }
    BigDecimal(result).setScale(3, BigDecimal.RoundingMode.HALF_UP).toDouble
  }


}