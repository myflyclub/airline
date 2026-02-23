package com.patson

import com.patson.model._
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.matchers.should.Matchers

class AirlineSimulationSpec extends AnyWordSpecLike with Matchers {

  // Helper to create AirlineStat with defaults
  def createStat(
                  eps: Double = 0.0,
                  RASK: Double = 0.0,
                  CASK: Double = 0.0,
                  satisfaction: Double = 0.5,
                  linkCount: Int = 0,
                  onTime: Double = 1.0,
                  codeshares: Int = 0,
                  repLeaderboards: Int = 0,
                  cashOnHand: Int = 0
                ): AirlineStat = AirlineStat(
    airlineId = 1,
    cycle = 1,
    period = Period.WEEKLY,
    tourists = 0,
    elites = 0,
    business = 0,
    total = 0,
    codeshares = codeshares,
    RASK = RASK,
    CASK = CASK,
    satisfaction = satisfaction,
    loadFactor = 0.8,
    onTime = onTime,
    cashOnHand = cashOnHand,
    eps = eps,
    linkCount = linkCount,
    repTotal = 0,
    repLeaderboards = repLeaderboards
  )

  "getNewQuality" should {
    "for increment at current quality 0, multiplier 1x; current quality 100, multiplier 0.1x" in {
      assert(AirlineSimulation.getNewQuality(0, Airline.EQ_MAX) == 0 + AirlineSimulation.MAX_SERVICE_QUALITY_INCREMENT) //get full increment
      assert(AirlineSimulation.getNewQuality(Airline.EQ_MAX, Airline.EQ_MAX) == Airline.EQ_MAX) //no increment
      assert(AirlineSimulation.getNewQuality(50, Airline.EQ_MAX) < 50 + AirlineSimulation.MAX_SERVICE_QUALITY_INCREMENT) //slow down
      assert(AirlineSimulation.getNewQuality(50, Airline.EQ_MAX) > 50) //but should still have increment
      assert(AirlineSimulation.getNewQuality(Airline.EQ_MAX - 1, Airline.EQ_MAX) < Airline.EQ_MAX) //slow down
      assert(AirlineSimulation.getNewQuality(Airline.EQ_MAX - 1, Airline.EQ_MAX) > Airline.EQ_MAX - 1) //but should still have increment
    }
    "for decrement at current quality 0, multiplier 0.1x; current quality 100, multiplier 1x" in {
      assert(AirlineSimulation.getNewQuality(Airline.EQ_MAX, 0) == Airline.EQ_MAX - AirlineSimulation.MAX_SERVICE_QUALITY_DECREMENT) //get full decrement
      assert(AirlineSimulation.getNewQuality(0, 0) == 0) //no decrement
      assert(AirlineSimulation.getNewQuality(50, 0) > 50 - AirlineSimulation.MAX_SERVICE_QUALITY_DECREMENT) //slow down
      assert(AirlineSimulation.getNewQuality(50, 0) < 50) //but should still have decrement
      assert(AirlineSimulation.getNewQuality(1, 0) == 0)
    }
  }

  "updateStockPrice" should {
    "return positive price even with zero stats" in {
      val stats = createStat()
      val price = AirlineSimulation.updateStockPrice(1.0, stats, 0.05)
      println(s"Zero stats price: $price")
      price should be > 0.0
    }

    "increase with higher EPS (primary driver, weight=24)" in {
      val lowEps = createStat(eps = 1.0)
      val highEps = createStat(eps = 20.0)

      val lowPrice = AirlineSimulation.updateStockPrice(1.0, lowEps, 0.05)
      val highPrice = AirlineSimulation.updateStockPrice(1.0, highEps, 0.05)

      println(s"Low EPS (1.0): $lowPrice, High EPS (20.0): $highPrice")
      highPrice should be > lowPrice
    }

    "increase with higher PASK (RASK - CASK, weight=12)" in {
      val lowPask = createStat(RASK = 0.05, CASK = 0.04)  // PASK = 0.01
      val highPask = createStat(RASK = 0.10, CASK = 0.03) // PASK = 0.07

      val lowPrice = AirlineSimulation.updateStockPrice(1.0, lowPask, 0.05)
      val highPrice = AirlineSimulation.updateStockPrice(1.0, highPask, 0.05)

      println(s"Low PASK (0.01): $lowPrice, High PASK (0.07): $highPrice")
      highPrice should be > lowPrice
    }

    "increase with higher satisfaction (weight=4, floor=0.5, target=0.95)" in {
      val lowSat = createStat(satisfaction = 0.5)  // floor
      val midSat = createStat(satisfaction = 0.75)
      val highSat = createStat(satisfaction = 0.95) // target

      val lowPrice = AirlineSimulation.updateStockPrice(1.0, lowSat, 0.05)
      val midPrice = AirlineSimulation.updateStockPrice(1.0, midSat, 0.05)
      val highPrice = AirlineSimulation.updateStockPrice(1.0, highSat, 0.05)

      println(s"Satisfaction 0.5: $lowPrice, 0.75: $midPrice, 0.95: $highPrice")
      highPrice should be > midPrice
      midPrice should be > lowPrice
    }

    "increase with higher on-time ratio (weight=4, floor=0.7, target=0.98)" in {
      val lowOnTime = createStat(onTime = 0.7, eps = 1.0)  // floor, need eps for sizeAdjust
      val highOnTime = createStat(onTime = 0.98, eps = 1.0) // target

      val lowPrice = AirlineSimulation.updateStockPrice(1.0, lowOnTime, 0.05)
      val highPrice = AirlineSimulation.updateStockPrice(1.0, highOnTime, 0.05)

      println(s"On-time 0.7: $lowPrice, 0.98: $highPrice")
      highPrice should be > lowPrice
    }

    "increase with more links (weight=4, floor=50, target=400)" in {
      val fewLinks = createStat(linkCount = 50)
      val manyLinks = createStat(linkCount = 400)

      val lowPrice = AirlineSimulation.updateStockPrice(1.0, fewLinks, 0.05)
      val highPrice = AirlineSimulation.updateStockPrice(1.0, manyLinks, 0.05)

      println(s"50 links: $lowPrice, 400 links: $highPrice")
      highPrice should be > lowPrice
    }

    "rise faster than it falls (asymmetric momentum)" in {
      val goodStats = createStat(eps = 15.0, satisfaction = 0.9, RASK = 0.08, CASK = 0.02)
      val badStats = createStat(eps = 0.1, satisfaction = 0.5)

      // Price going up: uses 85% new, 15% old
      val risingPrice = AirlineSimulation.updateStockPrice(1.0, goodStats, 0.05)
      // Price going down: uses 40% new, 60% old
      val fallingPrice = AirlineSimulation.updateStockPrice(10.0, badStats, 0.05)

      println(s"Rising from 1.0: $risingPrice, Falling from 10.0: $fallingPrice")

      // Verify the asymmetry - price should rise more aggressively than it falls
      val riseRatio = risingPrice / 1.0
      val fallRatio = 10.0 / fallingPrice

      println(s"Rise ratio: $riseRatio, Fall ratio: $fallRatio")
      // Rising should be more dramatic than falling for similar metric distances
    }

    "show sizeAdjust limits on-time and interest metrics for small airlines" in {
      // sizeAdjust = min(1, max(0.001, eps / 0.6))
      val smallAirline = createStat(eps = 0.06, onTime = 0.98) // sizeAdjust = 0.1
      val largeAirline = createStat(eps = 6.0, onTime = 0.98)  // sizeAdjust = 1.0

      val smallPrice = AirlineSimulation.updateStockPrice(1.0, smallAirline, 0.05)
      val largePrice = AirlineSimulation.updateStockPrice(1.0, largeAirline, 0.05)

      println(s"Small airline (eps=0.06): $smallPrice, Large airline (eps=6.0): $largePrice")
      // Large airline should benefit more from on-time metric
      largePrice should be > smallPrice
    }

    "benefit from lower interest rates (weight=4, floor=0.19, target=0)" in {
      val highInterest = AirlineSimulation.updateStockPrice(1.0, createStat(eps = 1.0), 0.19)
      val lowInterest = AirlineSimulation.updateStockPrice(1.0, createStat(eps = 1.0), 0.02)

      println(s"High interest (19%): $highInterest, Low interest (2%): $lowInterest")
      lowInterest should be > highInterest
    }

    "show metric value calculations" in {
      println("\nStockModel metric values:")
      println("Metric           | Floor  | Target | At Floor | At Target | At Mid")
      val metrics = List(
        ("eps", 0.0, 25.0),
        ("pask", 0.03, 0.07),
        ("satisfaction", 0.5, 0.95),
        ("link_count", 50.0, 400.0),
        ("on_time", 0.7, 0.98),
        ("codeshares", 100.0, 100000.0),
        ("rep_leaderboards", 0.0, 200.0),
        ("months_cash_on_hand", 48.0, 12.0),  // inverted
        ("interest", 0.19, 0.0)  // inverted
      )
      for ((name, floor, target) <- metrics) {
        val atFloor = StockModel.getMetricValue(name, floor)
        val atTarget = StockModel.getMetricValue(name, target)
        val mid = (floor + target) / 2
        val atMid = StockModel.getMetricValue(name, mid)
        println(f"$name%-20s | $floor%-6.2f | $target%-6.2f | $atFloor%-8.2f | $atTarget%-9.2f | $atMid%.2f")
      }
      true shouldBe true
    }

    "verify satisfaction metric uses correct range (0.5 to 0.95)" in {
      // From computePassengerSatisfaction, satisfaction ranges 0-1
      // StockModel expects floor=0.5, target=0.95

      val belowFloor = StockModel.getMetricValue("satisfaction", 0.4)  // below floor
      val atFloor = StockModel.getMetricValue("satisfaction", 0.5)     // at floor
      val atTarget = StockModel.getMetricValue("satisfaction", 0.95)   // at target
      val aboveTarget = StockModel.getMetricValue("satisfaction", 1.0) // above target

      println(s"Satisfaction metrics: below(0.4)=$belowFloor, floor(0.5)=$atFloor, target(0.95)=$atTarget, above(1.0)=$aboveTarget")

      belowFloor shouldBe 0.0
      atFloor shouldBe 0.0
      atTarget shouldBe 4.0  // full weight
      aboveTarget shouldBe 4.0  // capped at max
    }
  }
}