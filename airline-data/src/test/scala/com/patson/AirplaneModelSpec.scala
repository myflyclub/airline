package com.patson

import com.patson.model._
import com.patson.model.airplane.Model.Type._
import com.patson.model.airplane._
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
 
class AirplaneModelSpec extends WordSpecLike with Matchers with BeforeAndAfterAll {
  private val GOOD_PROFIT_MARGIN = Map(LIGHT -> 0.25, SMALL -> 0.20, REGIONAL -> 0.15, MEDIUM -> 0.05, LARGE -> 0.0, X_LARGE -> -0.05, JUMBO -> -0.1, SUPERSONIC -> -0.05)
  private val MAX_PROFIT_MARGIN = Map(LIGHT -> 0.6, SMALL -> 0.55, REGIONAL -> 0.50, MEDIUM -> 0.35, LARGE -> 0.3, X_LARGE -> 0.3, JUMBO -> 0.25, SUPERSONIC -> 0.3)

  override protected def beforeAll() : Unit = {
    super.beforeAll()
//    AirplaneMaintenanceUtil.setTestFactor(Some(1))
  }

  override protected def afterAll() : Unit = {
//    AirplaneMaintenanceUtil.setTestFactor(None)
    super.afterAll()
  }

  
  "all airplane models".must {
    "Generate good profit at MAX LF at suitable range".in {
      Model.models.foreach { airplaneModel =>
        val margin = simulateProfitMargin(airplaneModel, 1)
        println(airplaneModel.name + " => " + margin)
        assert(margin > GOOD_PROFIT_MARGIN(airplaneModel.airplaneType))
        assert(margin < MAX_PROFIT_MARGIN(airplaneModel.airplaneType))
      }
    }
  }
  
  def simulateProfitMargin(airplaneModel : Model, loadFactor : Double) : Double = {
    val consumptionDetails = simulateStandard(airplaneModel, loadFactor)
    println(consumptionDetails)
    consumptionDetails.profit.toDouble / consumptionDetails.revenue
  }
  
  def simulateStandard(airplaneModel : Model, loadFactor : Double) : LinkConsumptionDetails = {
    val distance = if (airplaneModel.range > 10000)  10000 else airplaneModel.range //cap at 10000, otherwise frequency will be very low
    val (flightType, airportSize) = airplaneModel.airplaneType match {
      case LIGHT => (FlightType.SHORT_HAUL_DOMESTIC, 3)
      case SMALL => (FlightType.LONG_HAUL_DOMESTIC, 4)
      case REGIONAL => (FlightType.LONG_HAUL_INTERNATIONAL, 5)
      case MEDIUM => (FlightType.LONG_HAUL_INTERNATIONAL, 7)
      case LARGE => (FlightType.ULTRA_LONG_HAUL_INTERCONTINENTAL, 8)
      case X_LARGE => (FlightType.ULTRA_LONG_HAUL_INTERCONTINENTAL, 8)
      case JUMBO => (FlightType.ULTRA_LONG_HAUL_INTERCONTINENTAL, 8)
      case SUPERSONIC => (FlightType.LONG_HAUL_INTERNATIONAL, 5)
    }
    val duration = Computation.calculateDuration(airplaneModel, distance)
    val frequency = Computation.calculateMaxFrequency(airplaneModel, distance)
    val capacity = frequency * airplaneModel.capacity
    val fromAirport = Airport.fromId(1).copy(size = airportSize, baseIncome = Country.HIGH_INCOME_THRESHOLD, basePopulation = 1)
    fromAirport.initAirlineBases(List())
    val toAirport = Airport.fromId(2).copy(size = airportSize)
    toAirport.initAirlineBases(List())
    var price = Pricing.computeStandardPriceForAllClass(distance, flightType)
    if (airplaneModel.airplaneType == SUPERSONIC) {
      price *= 1.5
    }
    val airline = Airline.fromId(1)

    val link = Link(fromAirport, toAirport, airline, price = price, distance = distance, LinkClassValues.getInstanceByMap(Map(ECONOMY -> capacity)), rawQuality = fromAirport.expectedQuality(flightType, ECONOMY), duration, frequency, flightType)
    val airplane = Airplane(airplaneModel, airline, constructedCycle = 0 , purchasedCycle = 0, Airplane.MAX_CONDITION, depreciationRate = 0, value = airplaneModel.price, configuration  = AirplaneConfiguration.default(airline, airplaneModel))
    airplane.setTestUtilizationRate(1)
    val updatedAirplane = AirplaneSimulation.decayAirplanesByAirline(Map(airplane -> LinkAssignments(Map())), airline)(0)
    link.setTestingAssignedAirplanes(Map(updatedAirplane -> frequency))
    link.addSoldSeats(LinkClassValues.getInstanceByMap(Map(ECONOMY -> (capacity * loadFactor).toInt)))
    
    LinkSimulation.computeFlightLinkConsumptionDetail(link, 0)
    
    val consumptionResult = LinkSimulation.computeFlightLinkConsumptionDetail(link , 0)
    consumptionResult
  }
}
