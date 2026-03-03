package com.patson.tools

import com.patson.{AirplaneSimulation, LinkSimulation}
import com.patson.model._
import com.patson.model.airplane.Model.Type._
import com.patson.model.airplane._

object AirplaneModelTuner extends App {

  val distances = List(400, 800, 1200, 2000, 4000, 6000, 9000, 12000, 15000)

  // --- Profit margin by distance ---
//  println("model,type,capacity,range,distance,freq,discount_margin,econ_margin,biz_margin,first_margin")
//  Model.models.filter(_.capacity > 100).foreach { m =>
//    var outputString = m.name
//    outputString += ", " + m.airplaneTypeLabel
//    distances.foreach { distance =>
//      if (m.range >= distance) {
//        val (dM, eM, bM, fM) = simulateProfitMargin(m, 1.0, distance)
//        outputString += f",$dM%.3f,$eM%.3f,$bM%.3f,$fM%.3f"
//      }
//    }
//    println(outputString)
//  }

  // --- Profit per staff by distance ---
  println("model,type,capacity,range,distance,staff,discount_per_staff,econ_per_staff,biz_per_staff,first_per_staff")
  Model.models.filter(_.capacity > 130).foreach { m =>
    var outputString = m.name
    outputString += ", " + m.airplaneTypeLabel
    distances.foreach { distance =>
      if (m.range >= distance) {
        val (staff, marginDiscount, marginEcon, marginBiz, marginFirst) = simulateProfitPerStaff(m, 1, distance)
        outputString += ", " + staff + ", " + marginEcon + ", " + marginBiz + ", " + marginFirst
      }
    }
    println(outputString)
  }

  def simulateProfitMargin(airplaneModel: Model, loadFactor: Double, distance: Int): (Double, Double, Double, Double) = {
    val (discount, _) = simulateStandard(airplaneModel, if (airplaneModel.quality > 7) 0 else loadFactor, distance, linkClass = DISCOUNT_ECONOMY)
    val (econ, _)     = simulateStandard(airplaneModel, if (airplaneModel.quality > 8) 0 else loadFactor, distance)
    val (biz, _)      = simulateStandard(airplaneModel, if (airplaneModel.quality < 4) 0 else loadFactor, distance, linkClass = BUSINESS)
    val (first, _)    = simulateStandard(airplaneModel, if (airplaneModel.quality < 5) 0 else loadFactor, distance, linkClass = FIRST)

    val dMargin = if (discount.revenue > 0) discount.profit.toDouble / discount.revenue else 0.0
    val eMargin = if (econ.revenue > 0) econ.profit.toDouble / econ.revenue else 0.0
    val bMargin = if (biz.revenue > 0) biz.profit.toDouble / biz.revenue else 0.0
    val fMargin = if (first.revenue > 0) first.profit.toDouble / first.revenue else 0.0
    (dMargin, eMargin, bMargin, fMargin)
  }

  def simulateProfitPerStaff(airplaneModel: Model, loadFactor: Double, distance: Int): (Double, Double, Double, Double, Double) = {
    val (discount, dStaff) = simulateStandard(airplaneModel, if (airplaneModel.quality > 7) 0 else loadFactor, distance, linkClass = DISCOUNT_ECONOMY)
    val (econ, eStaff)     = simulateStandard(airplaneModel, if (airplaneModel.quality > 8) 0 else loadFactor, distance)
    val (biz, bStaff)      = simulateStandard(airplaneModel, if (airplaneModel.quality < 4) 0 else loadFactor, distance, linkClass = BUSINESS)
    val (first, fStaff)    = simulateStandard(airplaneModel, if (airplaneModel.quality < 5) 0 else loadFactor, distance, linkClass = FIRST)

    val dPS = if (dStaff > 0) discount.profit.toDouble / dStaff else 0.0
    val ePS = if (eStaff > 0) econ.profit.toDouble / eStaff else 0.0
    val bPS = if (bStaff > 0) biz.profit.toDouble / bStaff else 0.0
    val fPS = if (fStaff > 0) first.profit.toDouble / fStaff else 0.0
    (eStaff.toDouble, dPS, ePS, bPS, fPS)
  }

  def simulateStandard(airplaneModel: Model, loadFactor: Double, distance: Int, linkClass: LinkClass = ECONOMY): (LinkConsumptionDetails, Double) = {
    val airportSize = airplaneModel.airplaneType match {
      case PROPELLER_SMALL             => 2
      case SMALL                       => 3
      case PROPELLER_MEDIUM            => 5
      case REGIONAL | REGIONAL_XL      => 6
      case MEDIUM | MEDIUM_XL          => 6
      case _                           => 7
    }
    val duration  = Computation.calculateDuration(airplaneModel, distance)
    val flightType = if (distance > 5000) FlightCategory.INTERNATIONAL else FlightCategory.DOMESTIC
    val frequency  = Computation.calculateMaxFrequency(airplaneModel, distance)
    val capacity   = frequency * (airplaneModel.capacity / linkClass.spaceMultiplier).toInt
    val income     = 40_000
    val fromAirport = Airport.fromId(1).copy(size = airportSize, baseIncome = income, basePopulation = 1)
    fromAirport.initAirlineBases(List())
    val toAirport = Airport.fromId(2).copy(size = airportSize)
    toAirport.initAirlineBases(List())

    var price = Pricing.computeStandardPriceForAllClass(distance, flightType, PassengerType.TOURIST, income)
    if (airplaneModel.airplaneType == SUPERSONIC) price *= 1.8
    if (linkClass == FIRST) {
      price *= 1.3
      price += LinkClassValues(0, Lounge.BASE_COST_REDUCTION, Lounge.BASE_COST_REDUCTION)
    } else if (linkClass == BUSINESS) {
      price *= 1.15
      price += LinkClassValues(0, Lounge.BASE_COST_REDUCTION, Lounge.BASE_COST_REDUCTION)
    }

    val airline = if (airplaneModel.airplaneTypeSize <= 0.12) Airline("regional", RegionalAirline) else Airline("legacy", LegacyAirline)
    val linkClassValues = LinkClassValues.getInstanceByMap(Map(linkClass -> capacity))
    val airplaneConfiguration: AirplaneConfiguration = linkClass match {
      case FIRST     => AirplaneConfiguration.first(airline, airplaneModel)
      case BUSINESS  => AirplaneConfiguration.business(airline, airplaneModel)
      case _         => AirplaneConfiguration.economy(airline, airplaneModel)
    }

    val link = Link(fromAirport, toAirport, airline, price = price, distance = distance, linkClassValues, rawQuality = 20, duration, frequency)
    val airplane = Airplane(airplaneModel, airline, constructedCycle = 0, purchasedCycle = 0, Airplane.MAX_CONDITION - 1, airplaneModel.price, configuration = airplaneConfiguration)
    airplane.setTestUtilizationRate(0.95)
    val updatedAirplane = AirplaneSimulation.decayAirplanesByAirline(Map(airplane -> LinkAssignments(Map())), airline).head
    link.setTestingAssignedAirplanes(Map(updatedAirplane -> frequency))
    link.addSoldSeats(LinkClassValues.getInstanceByMap(Map(linkClass -> (capacity * loadFactor).toInt)))

    val result = LinkSimulation.computeFlightLinkConsumptionDetail(link, 0)
    val staff  = link.getCurrentOfficeStaffRequired

    // Replace LinkSimulation's accounting depreciation with actual buy-sell capital cost:
    // buy at model.price, operate at 95% utilization, sell at 50% condition after lifespan weeks
    val airplaneAt50Pct = Airplane(airplaneModel, airline, constructedCycle = 0, purchasedCycle = 0, Airplane.MAX_CONDITION * 0.5, airplaneModel.price, configuration = airplaneConfiguration)
    val sellValue = Computation.calculateAirplaneSellValue(airplaneAt50Pct)
    val weeklyCapitalCost = (airplaneModel.price - sellValue).toDouble / airplaneModel.lifespan
    val operatingProfit = result.profit + result.depreciation
    val adjustedResult = result.copy(depreciation = weeklyCapitalCost.toInt, profit = operatingProfit - weeklyCapitalCost.toInt)

    (adjustedResult, staff)
  }
}
