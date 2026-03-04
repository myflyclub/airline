package com.patson

import com.patson.model._
import com.patson.model.airplane._
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.matchers.should.Matchers

class AirplaneSimulationSpec extends AnyWordSpecLike with Matchers {
  private[this] val model = Model.modelByName("Airbus A320")
  val airline1 = Airline("test-1")
  val airline2 = Airline("test-2")

  val airplane1 = Airplane(model, airline1, 0, 0, 100, model.price, id = 1)
  val airplane2 = Airplane(model, airline1, 0, 0, 100, model.price, id = 2)
  val airport1 = Airport.fromId(1)
  val airport2 = Airport.fromId(2)
  val link1 = Link(airport1, airport2, airline1, LinkClassValues.getInstance(), 0, LinkClassValues.getInstance(), 0, 0, 1)
  val link2 = Link(airport1, airport2, airline2, LinkClassValues.getInstance(), 0, LinkClassValues.getInstance(), 0, 0, 1)

  "decayAirplanesByAirline" should {
    "decay slower if no assigned link" in {
      val a1 = this.airplane1.copy()
      val a2 = this.airplane2.copy()
      a1.setTestUtilizationRate(1)
      a2.setTestUtilizationRate(0)

      val result = AirplaneSimulation.decayAirplanesByAirline(Map(
        a1 -> LinkAssignments(Map()),
        a2 -> LinkAssignments(Map()),
      ), airline1)

      val decayedA1 = result.find(_.id == a1.id).get
      val decayedA2 = result.find(_.id == a2.id).get

      decayedA1.condition should be < decayedA2.condition
      decayedA2.condition should be < 100.0 // even w/o assignment it should still decay
    }

    "decay slower if flying less frequently" in {
      val a1 = this.airplane1.copy()
      val a2 = this.airplane2.copy()
      a1.setTestUtilizationRate(0.1)
      a2.setTestUtilizationRate(1)

      val result = AirplaneSimulation.decayAirplanesByAirline(Map(
        a1 -> LinkAssignments(Map()),
        a2 -> LinkAssignments(Map())
      ), airline1)

      val decayedA1 = result.find(_.id == a1.id).get
      val decayedA2 = result.find(_.id == a2.id).get

      decayedA1.condition should be > decayedA2.condition
    }

    "not decay beyond 0% in lifespan" in {
      val badAirline = Airline("bad-airline")

      var airplane = airplane1.copy(owner = badAirline)
      airplane.setTestUtilizationRate(1)

      for (i <- 0 until airplane.model.lifespan) {
        airplane = AirplaneSimulation.decayAirplanesByAirline(Map(airplane -> LinkAssignments(Map())), badAirline).head
        airplane.setTestUtilizationRate(1)
      }

      airplane.condition should be >= 0.0
      airplane.condition should be < 1.0
    }

    "higher utilization is more profitable than low (lower depreciation per utilized capacity)" in {
      val purchasePrice = model.price

      val highUtil = Airplane(model, airline1, constructedCycle = 0, purchasedCycle = 0, condition = Airplane.MAX_CONDITION, purchasePrice = purchasePrice, id = 101)
      val lowUtil = Airplane(model, airline1, constructedCycle = 0, purchasedCycle = 0, condition = Airplane.MAX_CONDITION, purchasePrice = purchasePrice, id = 102)

      highUtil.setTestUtilizationRate(1.0)
      lowUtil.setTestUtilizationRate(0.2)

      val sellBeforeHigh = Computation.calculateAirplaneSellValue(highUtil)
      val sellBeforeLow = Computation.calculateAirplaneSellValue(lowUtil)

      val decayed = AirplaneSimulation.decayAirplanesByAirline(Map(
        highUtil -> LinkAssignments(Map()),
        lowUtil -> LinkAssignments(Map()),
      ), airline1)

      val decayedHigh = decayed.find(_.id == highUtil.id).get
      val decayedLow = decayed.find(_.id == lowUtil.id).get

      val sellAfterHigh = Computation.calculateAirplaneSellValue(decayedHigh)
      val sellAfterLow = Computation.calculateAirplaneSellValue(decayedLow)

      val depreciationHigh = sellBeforeHigh - sellAfterHigh
      val depreciationLow = sellBeforeLow - sellAfterLow

      val depreciationPerUtilHigh = depreciationHigh.toDouble / highUtil.utilizationRate
      val depreciationPerUtilLow = depreciationLow.toDouble / lowUtil.utilizationRate

      // "profit" proxy: inverse of depreciation per utilized unit
      // higher is better; % more profitable = (high/low - 1) * 100
      val profitIndexHigh = 1.0 / depreciationPerUtilHigh
      val profitIndexLow = 1.0 / depreciationPerUtilLow
      val percentMoreProfitable = (profitIndexHigh / profitIndexLow - 1.0) * 100.0

      println(f"[utilization profitability] highUtil=${highUtil.utilizationRate}%.2f lowUtil=${lowUtil.utilizationRate}%.2f")
      println(f"[utilization profitability] depreciationPerUtil high=$depreciationPerUtilHigh%.4f low=$depreciationPerUtilLow%.4f")
      println(f"[utilization profitability] high is $percentMoreProfitable%.2f%% more profitable (by sell-value depreciation per utilized unit)")

      depreciationPerUtilHigh should be < depreciationPerUtilLow
      percentMoreProfitable should be > 0.0
    }

  }
}