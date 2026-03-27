package com.patson.model

case class StockMetric(value: Int, floor: Double, target: Double)

object StockModel {
  val STOCK_BROKER_FEE = 0.07
  val STOCK_BROKER_FEE_BASE = 250_000
  val STOCK_BUYBACK_MIN_CHANGE = 0.05
  val STOCK_BUYBACK_MAX_CHANGE = 0.14
  val TOOLTIP_STOCK_EPS = List(
    "Earnings per share is the primary metric with a 6x weight.",
    "Increasing your revenue and decreasing the shares outstanding will cause EPS to go up."
  )
  val TOOLTIP_STOCK_PASK = List(
    "Profit per Available Seat KM is the second most important metric with a 3x weight.",
    "Increase your Profit per Available Seat KM, or",
    "Lower your CASK – which includes all flight costs except fuel taxes and also includes base upkeep and base overtime.",
  )

  val TOOLTIP_STOCK_DIVIDENDS = List(
    "Dividends per share reward consistent cash returns to shareholders with a 4x weight.",
    "Set a weekly dividend payout. It will be cancelled if your balance drops below 100x the amount."
  )

  val allMetrics: Map[String, StockMetric] = Map(
    "eps" ->                  StockMetric(35, 0.0, 25.0),
    "pask" ->                 StockMetric(25, 0.05, 0.1),
    "interest" ->             StockMetric(10, 0.22, 0), //there's an extra 10 here
    "satisfaction" ->         StockMetric(5, 0.5, 0.9),
    "link_count" ->           StockMetric(5, 50, 400),
    "on_time" ->              StockMetric(6, 0.7, 0.98),
    "airport" ->              StockMetric(6, 20, 500),
    "codeshares" ->           StockMetric(6, 200, 100000),
    "rep_leaderboards" ->     StockMetric(6, 0, 200),
    "months_cash_on_hand" ->  StockMetric(6, 48, 12),
    "dividends" ->            StockMetric(4, 0.0, 2.0),  // floor=$0/share/week, target=$2/share/week
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

  /**
   * Returns 0 - 100 after adding all the metrics
   **/
  def getStockScore(stats: AirlineStat, currentInterestRate: Double): Double = {
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
      StockModel.getMetricValue("interest", currentInterestRate) * sizeAdjust,
      StockModel.getMetricValue("dividends", stats.dividendsPerShare)
    ).sum
  }

  def updateStockPrice(stockPrice: Double, weeklyStats: AirlineStat, quarterStats: Option[AirlineStat], currentInterestRate: Double): Double = {
    val weeklyScore = getStockScore(weeklyStats, currentInterestRate)
    val quarterScore = quarterStats.map(getStockScore(_, currentInterestRate)).getOrElse(weeklyScore)

    val quarterlyBlendWeight = 0.6
    val blendedScore = weeklyScore * (1 - quarterlyBlendWeight) + quarterScore * quarterlyBlendWeight // 0.0 .. 100.0

    // Exponential mapping from score range [0, 100] to price range [0.01, 2000.0]
    val gamma = 0.65
    val t = (blendedScore / 100.0).max(0.0).min(1.0)
    val targetPrice = 0.01 * Math.pow(2000 / 0.01, Math.pow(t, gamma))

    val result =
      if (targetPrice > stockPrice) {
        stockPrice * 0.15 + targetPrice * 0.85
      } else {
        // price falls slower, making buybacks more impactful
        stockPrice * 0.9 + targetPrice * 0.1
      }

    BigDecimal(result).setScale(3, BigDecimal.RoundingMode.HALF_UP).toDouble
  }


}