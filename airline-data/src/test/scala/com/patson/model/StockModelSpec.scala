package com.patson.model

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class StockModelSpec extends AnyFunSuite with Matchers {

  private def createStat(
                          eps: Double = 1.0,
                          RASK: Double = 0.05,
                          CASK: Double = 0.04,
                          satisfaction: Double = 0.5,
                          onTime: Double = 0.7,
                          linkCount: Int = 50
                        ): AirlineStat = {
    AirlineStat(
      airlineId = 1,
      cycle = 1,
      tourists = 0,
      elites = 0,
      business = 0,
      total = 0,
      codeshares = 100,
      RASK = RASK,
      CASK = CASK,
      satisfaction = satisfaction,
      loadFactor = 0.0,
      onTime = onTime,
      cashOnHand = 192,
      eps = eps,
      linkCount = linkCount,
      repTotal = 0,
      repLeaderboards = 0
    )
  }

  test("getMetricValue should not return NaN for airlineValue = 0") {
    val result = StockModel.getMetricValue("eps", 0)
    result.isNaN shouldBe false
  }

  test("getMetricValue should return 0 for value below floor") {
    val result = StockModel.getMetricValue("eps", -10)
    result shouldBe 0
  }

  test("getMetricValue should return metric value for value at or above target") {
    StockModel.getMetricValue("eps", 25) shouldBe 24
    StockModel.getMetricValue("eps", 30) shouldBe 24
  }

  test("getMetricValue should interpolate between floor and target") {
    val midValue = 25.0 / 2
    val result = StockModel.getMetricValue("eps", midValue)
    result should (be > 0.0 and be < 24.0)
  }

  test("getMetricValue should work for codeshares metric") {
    StockModel.getMetricValue("codeshares", 0) shouldBe 0
    StockModel.getMetricValue("codeshares", 100) shouldBe 0.0
    StockModel.getMetricValue("codeshares", 100000) shouldBe 4
    StockModel.getMetricValue("codeshares", 200000) shouldBe 4
  }

  test("getMetricValue should work for airport metric") {
    StockModel.getMetricValue("airport", 0.0) shouldBe 0
    StockModel.getMetricValue("airport", 5.0) shouldBe 0.0
    StockModel.getMetricValue("airport", 500.0) shouldBe 4
    StockModel.getMetricValue("airport", 1000.0) shouldBe 4
  }

  test("updateStockPrice should return positive price even with zero stats") {
    val price = StockModel.updateStockPrice(1.0, createStat(), None, 0.05)
    price should be > 0.0
  }

  test("updateStockPrice should increase with higher EPS (primary driver, weight=24)") {
    val lowPrice = StockModel.updateStockPrice(1.0, createStat(eps = 1.0), None, 0.05)
    val highPrice = StockModel.updateStockPrice(1.0, createStat(eps = 20.0), None, 0.05)
    highPrice should be > lowPrice
  }

  test("updateStockPrice should increase with higher PASK (RASK - CASK, weight=12)") {
    val lowPask = createStat(RASK = 0.05, CASK = 0.04)
    val highPask = createStat(RASK = 0.10, CASK = 0.03)

    val lowPrice = StockModel.updateStockPrice(1.0, lowPask, None, 0.05)
    val highPrice = StockModel.updateStockPrice(1.0, highPask, None, 0.05)

    highPrice should be > lowPrice
  }

  test("updateStockPrice should increase with higher satisfaction (weight=4, floor=0.5, target=0.95)") {
    val lowPrice = StockModel.updateStockPrice(1.0, createStat(satisfaction = 0.5), None, 0.05)
    val midPrice = StockModel.updateStockPrice(1.0, createStat(satisfaction = 0.75), None, 0.05)
    val highPrice = StockModel.updateStockPrice(1.0, createStat(satisfaction = 0.95), None, 0.05)

    highPrice should be > midPrice
    midPrice should be > lowPrice
  }

  test("updateStockPrice should increase with higher on-time ratio (weight=4, floor=0.7, target=0.98)") {
    val lowPrice = StockModel.updateStockPrice(1.0, createStat(onTime = 0.7, eps = 1.0), None, 0.05)
    val highPrice = StockModel.updateStockPrice(1.0, createStat(onTime = 0.98, eps = 1.0), None, 0.05)

    highPrice should be > lowPrice
  }

  test("updateStockPrice should increase with more links (weight=4, floor=50, target=400)") {
    val lowPrice = StockModel.updateStockPrice(1.0, createStat(linkCount = 50), None, 0.05)
    val highPrice = StockModel.updateStockPrice(1.0, createStat(linkCount = 400), None, 0.05)

    highPrice should be > lowPrice
  }

  test("updateStockPrice should rise faster than it falls (asymmetric momentum)") {
    val goodStats = createStat(eps = 15.0, satisfaction = 0.9, RASK = 0.08, CASK = 0.02)
    val badStats = createStat(eps = 0.1, satisfaction = 0.5)

    val risingPrice = StockModel.updateStockPrice(1.0, goodStats, None, 0.05)
    val fallingPrice = StockModel.updateStockPrice(10.0, badStats, None, 0.05)

    val riseRatio = risingPrice / 1.0
    val fallRatio = 10.0 / fallingPrice

    riseRatio should be > fallRatio
  }

  test("updateStockPrice should show sizeAdjust limits on-time and interest metrics for small airlines") {
    val smallAirline = createStat(eps = 0.06, onTime = 0.98)
    val largeAirline = createStat(eps = 6.0, onTime = 0.98)

    val smallPrice = StockModel.updateStockPrice(1.0, smallAirline, None, 0.05)
    val largePrice = StockModel.updateStockPrice(1.0, largeAirline, None, 0.05)

    largePrice should be > smallPrice
  }

  test("updateStockPrice should benefit from lower interest rates (weight=4, floor=0.19, target=0)") {
    val highInterest = StockModel.updateStockPrice(1.0, createStat(eps = 1.0), None, 0.19)
    val lowInterest = StockModel.updateStockPrice(1.0, createStat(eps = 1.0), None, 0.02)

    lowInterest should be > highInterest
  }

  test("verify satisfaction metric uses correct range (0.5 to 0.95)") {
    StockModel.getMetricValue("satisfaction", 0.4) shouldBe 0.0
    StockModel.getMetricValue("satisfaction", 0.5) shouldBe 0.0
    StockModel.getMetricValue("satisfaction", 0.95) shouldBe 4.0
    StockModel.getMetricValue("satisfaction", 1.0) shouldBe 4.0
  }

  test("low EPS airline can still get very high stock price from PASK + on_time") {
    val lowEpsHighPask = createStat(eps = 0.06, RASK = 0.08, CASK = 0.0, onTime = 0.98)
    val lowEpsLowPask = createStat(eps = 0.06, RASK = 0.03, CASK = 0.0, onTime = 0.98)

    val highPaskPrice = StockModel.updateStockPrice(0.0, lowEpsHighPask, None, currentInterestRate = 0.19)
    val lowPaskPrice = StockModel.updateStockPrice(0.0, lowEpsLowPask, None, currentInterestRate = 0.19)

    highPaskPrice should be > (lowPaskPrice * 20)
  }

  test("on_time is dampened by sizeAdjust, but undampened PASK remains dominant at low EPS") {
    val lowEpsHighOnTimeLowPask = createStat(eps = 0.06, RASK = 0.03, CASK = 0.0, onTime = 0.98)
    val lowEpsLowOnTimeLowPask = createStat(eps = 0.06, RASK = 0.03, CASK = 0.0, onTime = 0.70)
    val lowEpsHighOnTimeHighPask = createStat(eps = 0.06, RASK = 0.08, CASK = 0.0, onTime = 0.98)

    val onTimeOnlyBoost =
      StockModel.updateStockPrice(0.0, lowEpsHighOnTimeLowPask, None, currentInterestRate = 0.19) -
        StockModel.updateStockPrice(0.0, lowEpsLowOnTimeLowPask, None, currentInterestRate = 0.19)

    val paskPlusOnTimeBoost =
      StockModel.updateStockPrice(0.0, lowEpsHighOnTimeHighPask, None, currentInterestRate = 0.19) -
        StockModel.updateStockPrice(0.0, lowEpsHighOnTimeLowPask, None, currentInterestRate = 0.19)

    paskPlusOnTimeBoost should be > (onTimeOnlyBoost * 10)
  }
}