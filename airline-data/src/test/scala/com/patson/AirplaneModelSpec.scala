package com.patson

import com.patson.model._
import com.patson.model.airplane.Model.Type._
import com.patson.model.airplane._
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
 
class AirplaneModelSpec extends WordSpecLike with Matchers with BeforeAndAfterAll {
  private val GOOD_PROFIT_MARGIN = Map(PROPELLER_SMALL -> 0.2, SMALL -> 0.20, PROPELLER_MEDIUM -> 0.1, REGIONAL -> 0.2, MEDIUM -> 0.1, MEDIUM_XL -> 0.1, LARGE -> 0.05, JUMBO -> -0.05, JUMBO_XL -> -0.05, SUPERSONIC -> -0.05, HELICOPTER -> -0.05, AIRSHIP -> -0.05)
  private val MAX_PROFIT_MARGIN = Map(PROPELLER_SMALL -> 0.45, SMALL -> 0.45, PROPELLER_MEDIUM -> 0.45, REGIONAL -> 0.45, MEDIUM -> 0.35, MEDIUM_XL -> 0.35, LARGE -> 0.35, JUMBO -> 0.35, JUMBO_XL -> 0.3, SUPERSONIC -> 0.4, HELICOPTER -> 0.4, AIRSHIP -> 0.2)

  override protected def beforeAll() : Unit = {
    super.beforeAll()
  }

  override protected def afterAll() : Unit = {
    super.afterAll()
  }

  
  "all airplane models".must {
    "Generate good profit at MAX LF at suitable range".in {
      Model.models.foreach { airplaneModel =>
        val distances = List(15000, 12000, 9000, 6000, 3000, 2000, 1200, 800, 400).reverse
        var outputString = airplaneModel.name
        distances.foreach { distance =>
          if (airplaneModel.range >= distance) {
            val (marginDiscount, marginEcon, marginBiz, marginFirst) = simulateProfitMargin(airplaneModel, 1, distance)
            outputString += ", " + marginDiscount + ", " + marginEcon + ", " + marginBiz + ", " + marginFirst
          }
        }
        println(outputString)
      }
    }
  }
  
  def simulateProfitMargin(airplaneModel : Model, loadFactor : Double, distance : Int): (Double, Double, Double, Double) = {
//    println(consumptionDetails)
    val discount = simulateStandard(airplaneModel, loadFactor, distance, linkClass = DISCOUNT_ECONOMY)
    val econ = simulateStandard(airplaneModel, loadFactor, distance)
    val biz = simulateStandard(airplaneModel, loadFactor, distance, linkClass = BUSINESS)
    val first = simulateStandard(airplaneModel, loadFactor, distance, linkClass = FIRST)
    (discount.profit.toDouble / discount.revenue, econ.profit.toDouble / econ.revenue, biz.profit.toDouble / biz.revenue, first.profit.toDouble / first.revenue)
  }
  
  def simulateStandard(airplaneModel : Model, loadFactor : Double, distance : Int, linkClass: LinkClass = ECONOMY) : LinkConsumptionDetails = {
    val (flightType, airportSize) = airplaneModel.airplaneType match {
      case PROPELLER_SMALL => (FlightCategory.DOMESTIC, 2)
      case SMALL => (FlightCategory.DOMESTIC, 3)
      case PROPELLER_MEDIUM => (FlightCategory.DOMESTIC, 4)
      case REGIONAL => (FlightCategory.DOMESTIC, 6)
      case MEDIUM => (FlightCategory.DOMESTIC, 7)
      case MEDIUM_XL => (FlightCategory.DOMESTIC, 7)
      case LARGE => (FlightCategory.DOMESTIC, 8)
      case EXTRA_LARGE => (FlightCategory.DOMESTIC, 8)
      case JUMBO => (FlightCategory.DOMESTIC, 8)
      case JUMBO_XL => (FlightCategory.DOMESTIC, 8)
      case SUPERSONIC => (FlightCategory.DOMESTIC, 8)
      case _ => (FlightCategory.DOMESTIC, 8)
    }
    val duration = Computation.calculateDuration(airplaneModel, distance)
    val frequency = Computation.calculateMaxFrequency(airplaneModel, distance)
    val capacity = frequency * (airplaneModel.capacity / linkClass.spaceMultiplier).toInt
    val income = 40000
    val fromAirport = Airport.fromId(1).copy(size = airportSize, baseIncome = income, basePopulation = 1)
    fromAirport.initAirlineBases(List())
    val toAirport = Airport.fromId(2).copy(size = airportSize)
    toAirport.initAirlineBases(List())
    var price = Pricing.computeStandardPriceForAllClass(distance, flightType, PassengerType.TRAVELER, income)
    if (airplaneModel.airplaneType == SUPERSONIC) {
      price *= 1.8
    }
    if (linkClass == FIRST) {
      price *= 1.3 //assume have lounge etc
    } else if (linkClass == BUSINESS) {
      price *= 1.25
    }
    val airline = Airline.fromId(1)


    val linkClassValues = LinkClassValues.getInstanceByMap(Map(linkClass -> capacity))
    val rawQuality = linkClass match {
      case DISCOUNT_ECONOMY => 20
      case FIRST => 80
      case BUSINESS => 60
      case _ => 20
    }
    val airplaneConfiguration: AirplaneConfiguration = linkClass match {
      case FIRST => AirplaneConfiguration.first(airline, airplaneModel)
      case BUSINESS => AirplaneConfiguration.business(airline, airplaneModel)
      case _ => AirplaneConfiguration.economy(airline, airplaneModel)
    }

    val link = Link(fromAirport, toAirport, airline, price = price, distance = distance, linkClassValues, rawQuality, duration, frequency)
    val airplane = Airplane(airplaneModel, airline, constructedCycle = 0 , purchasedCycle = 0, Airplane.MAX_CONDITION - 1, depreciationRate = 0, value = airplaneModel.price, configuration = airplaneConfiguration)

    airplane.setTestUtilizationRate(1)
    val updatedAirplane = AirplaneSimulation.decayAirplanesByAirline(Map(airplane -> LinkAssignments(Map())), airline)(0)
    link.setTestingAssignedAirplanes(Map(updatedAirplane -> frequency))
    link.addSoldSeats(LinkClassValues.getInstanceByMap(Map(linkClass -> (capacity * loadFactor).toInt)))
    
    LinkSimulation.computeFlightLinkConsumptionDetail(link, 0)
    
    val consumptionResult = LinkSimulation.computeFlightLinkConsumptionDetail(link , 0)
//    println(consumptionResult)
    consumptionResult
  }
}
