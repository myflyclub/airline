package com.patson.model

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class StockModelSpec extends AnyFunSuite with Matchers {
  test("getMetricValue should not return NaN for airlineValue = 0") {
    val result = StockModel.getMetricValue("eps", 0)
    result.isNaN shouldBe false
  }

  test("getMetricValue should return 0 for value below floor") {
    val result = StockModel.getMetricValue("eps", -10)
    result shouldBe 0
  }

  test("getMetricValue should return metric value for value at or above target") {
    val result = StockModel.getMetricValue("eps", 25)
    result shouldBe 24
    val resultAbove = StockModel.getMetricValue("eps", 30)
    resultAbove shouldBe 24
  }

  test("getMetricValue should interpolate between floor and target") {
    val midValue = (25.0 + 0.0) / 2
    val result = StockModel.getMetricValue("eps", midValue)
    result should (be > 0.0 and be < 24.0)
  }

  test("getMetricValue should work for codeshares metric") {
    val belowFloor = StockModel.getMetricValue("codeshares", 0)
    belowFloor shouldBe 0
    val atFloor = StockModel.getMetricValue("codeshares", 100)
    atFloor shouldBe 0.0
    val atTarget = StockModel.getMetricValue("codeshares", 100000)
    atTarget shouldBe 4
    val aboveTarget = StockModel.getMetricValue("codeshares", 200000)
    aboveTarget shouldBe 4
  }

  test("getMetricValue should work for airport metric") {
    val belowFloor = StockModel.getMetricValue("airport", 0.0)
    belowFloor shouldBe 0
    val atFloor = StockModel.getMetricValue("airport", 5.0)
    atFloor shouldBe 0.0
    val atTarget = StockModel.getMetricValue("airport", 500.0)
    atTarget shouldBe 4
    val aboveTarget = StockModel.getMetricValue("airport", 1000.0)
    aboveTarget shouldBe 4
  }
}
