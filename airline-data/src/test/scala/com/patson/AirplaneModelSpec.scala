package com.patson

import com.patson.model._
import com.patson.model.airplane._
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.matchers.should.Matchers

class AirplaneModelSpec extends AnyWordSpecLike with Matchers {

  "calculateFuelCost" should {
    "Calculate fuel cost for a given model and distance" in {
      val model = Model.models.find(_.name == "Boeing 737-800").get
      val distance = 1000 // km
      val soldSeats = 150
      val capacity = 189
      val frequency = 7

      val fuelCost = LinkSimulation.calculateFuelCost(model, distance, soldSeats, capacity, frequency)

      fuelCost should be > 0

      val fuelCostEmpty = LinkSimulation.calculateFuelCost(model, distance, 0, capacity, frequency)
      val fuelCostFull = LinkSimulation.calculateFuelCost(model, distance, capacity, capacity, frequency)

      fuelCostFull should be > fuelCostEmpty

      val fuelCostWithCancellations = LinkSimulation.calculateFuelCost(model, distance, soldSeats, capacity, frequency, cancellationCount = 2)

      fuelCostWithCancellations should be < fuelCost
    }

    "Calculate fuel cost for different distances" in {
      val aircraftNames = List(
        "Airbus A321neo",
        "Boeing 737 MAX 10",
        "Airbus A321",
        "Boeing 737-900ER",
        "Boeing 737-600",
        "Boeing 737-400",
        "McDonnell Douglas DC-9-30"
      )

      val frequency = 1
      val shortDistance = 1200
      val middleDistance = 2400
      val longDistance = 4800

      aircraftNames.foreach { aircraftName =>
        val modelOpt = Model.models.find(_.name == aircraftName)

        modelOpt match {
          case Some(model) =>
            val soldSeats = model.capacity
            val capacity = model.capacity

            val fuelCostShort = LinkSimulation.calculateFuelCost(model, shortDistance, soldSeats, capacity, frequency).toDouble / model.capacity
            val fuelCostMiddle = LinkSimulation.calculateFuelCost(model, middleDistance, soldSeats, capacity, frequency).toDouble / model.capacity
            val fuelCostLong = LinkSimulation.calculateFuelCost(model, longDistance, soldSeats, capacity, frequency).toDouble / model.capacity

            fuelCostLong should be > fuelCostMiddle
            fuelCostMiddle should be > fuelCostShort

          case None =>
            fail(s"Aircraft model '$aircraftName' not found in Model.models")
        }
      }
    }
  }

  "airplane costs" should {
    "scale airport fees with model size and airport size" in {
      val smallModel = Model.models.find(_.name == "ATR 72-600").get
      val largeModel = Model.models.find(_.name == "Boeing 787-8 Dreamliner").get
      val airline = Airline("test")

      val smallAirport = Airport.fromId(1).copy(size = 3)
      smallAirport.initAirlineBases(List.empty)
      val largeAirport = Airport.fromId(2).copy(size = 8)
      largeAirport.initAirlineBases(List.empty)

      val smallSlotFee = smallAirport.slotFee(smallModel, airline)
      val largeSlotFee = smallAirport.slotFee(largeModel, airline)
      largeSlotFee should be > smallSlotFee

      val feeAtSmallAirport = smallAirport.slotFee(smallModel, airline)
      val feeAtLargeAirport = largeAirport.slotFee(smallModel, airline)
      feeAtLargeAirport should be > feeAtSmallAirport

      smallAirport.landingFee(100) should be < largeAirport.landingFee(100)
    }

    "calculate maintenance cost based on capacity" in {
      val smallModel = Model.models.find(_.name == "ATR 72-600").get
      val largeModel = Model.models.find(_.name == "Boeing 787-8 Dreamliner").get

      largeModel.baseMaintenanceCost should be > smallModel.baseMaintenanceCost
    }
  }

}