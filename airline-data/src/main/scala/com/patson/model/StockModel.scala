package com.patson.model

case class StockMetric(value: Int, floor: Double, target: Double)
case class TypeBenchmark(pask: StockMetric, dividendsPerShare: StockMetric, codeshares: StockMetric)

object StockModel {

  @volatile var benchmarksByType: Map[String, TypeBenchmark] = Map.empty

  private def percentile(values: Seq[Double], p: Double): Double = {
    if (values.isEmpty) return 0.0
    val sorted = values.sorted
    sorted(((sorted.size - 1) * p).toInt)
  }

  val BENCHMARK_MIN_AIRLINES = 5
  private val participatingTypes = Seq(LegacyAirline, DiscountAirline, LuxuryAirline, RegionalAirline)

  def computeBenchmarks(airlines: List[Airline], latestWeeklyStatsByAirlineId: Map[Int, AirlineStat]): Map[String, TypeBenchmark] = {
    val airlinesByType = airlines.groupBy(_.airlineType)
    participatingTypes.flatMap { airlineType =>
      val typeStats = airlinesByType.getOrElse(airlineType, Nil).flatMap(a => latestWeeklyStatsByAirlineId.get(a.id))
      if (typeStats.size < BENCHMARK_MIN_AIRLINES) None
      else {
        val paskList = typeStats.map(s => s.RASK - s.CASK)
        val divList  = typeStats.map(_.dividendsPerShare)
        val codeshares  = typeStats.map(_.codeshares.toDouble)
        val benchmark = TypeBenchmark(
          pask = allMetrics("pask").copy(
            floor  = percentile(paskList, 0.2),
            target = Math.max(percentile(paskList, 0.8), 0.1)
          ),
          dividendsPerShare = allMetrics("dividends_per_share").copy(
            floor  = percentile(divList, 0.2),
            target = Math.max(percentile(divList, 0.9), 0.1)
          ),
          codeshares = allMetrics("codeshares").copy(
            floor  = percentile(codeshares, 0.2),
            target = Math.max(percentile(codeshares, 0.8), 0.1)
          )
        )
        Some(airlineType.label -> benchmark)
      }
    }.toMap
  }
  val STOCK_BROKER_FEE = 0.06
  val STOCK_BROKER_FEE_BASE = 250_000
  val STOCK_BUYBACK_MIN_CHANGE = 0.03
  val STOCK_BUYBACK_MAX_CHANGE = 0.1
  val TOOLTIP_STOCK_EPS = List(
    "Earnings per share is the primary metric with a 6x weight.",
    "Increasing your revenue and decreasing the shares outstanding will cause EPS to go up."
  )
  val TOOLTIP_STOCK_PASK = List(
    "Profit per Available Seat KM is the second most important metric with a 4x weight.",
    "Target is a race among airlines of your type to have the highest.",
    "CASK includes all your operational costs, excluding tax, interest, dividends, and capital expenses."
  )

  val TOOLTIP_STOCK_DIVIDENDS = List(
    "Set a weekly dividend payout. It will be cancelled if your balance drops below 50x the amount.",
    "Dividends per share reward cash returns to shareholders with a 4x weight.",
    "Target is a race among airlines of your type to have the highest."
  )

  //currently adds up to 115
  val allMetrics: Map[String, StockMetric] = Map(
    "eps" ->                  StockMetric(30, 0.1, 1.0),
    "pask" ->                 StockMetric(25, 0.05, 0.09),
    "dividends_per_share" ->  StockMetric(25, 0.0, 1.0),
    "interest" ->             StockMetric(10, 0.26, 0.6), //there's an extra 10 here
    "satisfaction" ->         StockMetric(5, 0.5, 0.9),
    "link_count" ->           StockMetric(5, 50, 400),
    "on_time" ->              StockMetric(5, 0.75, 0.95),
    "codeshares" ->           StockMetric(5, 200, 100000),
    "rep_leaderboards" ->     StockMetric(5, 0, 200),
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
    getMetricValue(metric, airlineValue)
  }

  def getMetricValue(metric: StockMetric, airlineValue: Double): Double = {
    val targetPercent = 1 - (metric.target - airlineValue) / (metric.target - metric.floor)
    metric.value * Math.max(0, Math.min(1, targetPercent))
  }

  /**
   * Returns 0 - 100 after adding all the metrics
   **/
  def getStockScore(stats: AirlineStat, currentInterestRate: Double, benchmarkOverride: Option[TypeBenchmark] = None): Double = {
    val sizeAdjust = (stats.eps / 0.5).max(0.01).min(1.0)
    val paskMet = benchmarkOverride.map(_.pask).getOrElse(allMetrics("pask"))
    val divMet  = benchmarkOverride.map(_.dividendsPerShare).getOrElse(allMetrics("dividends_per_share"))
    val codeshares = benchmarkOverride.map(_.codeshares).getOrElse(allMetrics("codeshares"))

    Seq(
      getMetricValue("eps", stats.eps),
      getMetricValue(paskMet, stats.RASK - stats.CASK),
      getMetricValue(divMet, stats.dividendsPerShare),
      getMetricValue("satisfaction", stats.satisfaction),
      getMetricValue("link_count", stats.linkCount),
      getMetricValue("on_time", stats.onTime) * sizeAdjust,
      getMetricValue(codeshares, stats.codeshares),
      getMetricValue("rep_leaderboards", stats.repLeaderboards),
      getMetricValue("interest", currentInterestRate) * sizeAdjust
    ).sum
  }

  def updateStockPrice(stockPrice: Double, weeklyStats: AirlineStat, quarterStats: Option[AirlineStat], currentInterestRate: Double, benchmarkOverride: Option[TypeBenchmark] = None): Double = {
    val weeklyScore = getStockScore(weeklyStats, currentInterestRate, benchmarkOverride)
    val quarterScore = quarterStats.map(getStockScore(_, currentInterestRate, benchmarkOverride)).getOrElse(weeklyScore)

    val quarterlyBlendWeight = 0.7
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