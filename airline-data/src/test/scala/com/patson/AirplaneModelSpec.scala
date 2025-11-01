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

  "calculateFuelCost".must {
    "Calculate fuel cost for a given model and distance".in {
      // Get a sample airplane model (Boeing 737-800)
      val model = Model.models.find(_.name == "Boeing 737-800").get
      val distance = 1000 // km
      val soldSeats = 150
      val capacity = 189
      val frequency = 7
      
      val fuelCost = LinkSimulation.calculateFuelCost(model, distance, soldSeats, capacity, frequency)
      
      // Fuel cost should be positive
      fuelCost should be > 0
      
      // Test with different load factors
      val fuelCostEmpty = LinkSimulation.calculateFuelCost(model, distance, 0, capacity, frequency)
      val fuelCostFull = LinkSimulation.calculateFuelCost(model, distance, capacity, capacity, frequency)
      
      // Full plane should cost more fuel than empty plane
      fuelCostFull should be > fuelCostEmpty
      
      // Test with cancellations
      val fuelCostWithCancellations = LinkSimulation.calculateFuelCost(model, distance, soldSeats, capacity, frequency, cancellationCount = 2)
      
      // Cancellations should reduce fuel cost
      fuelCostWithCancellations should be < fuelCost
    }
    
    "Calculate fuel cost for different distances".in {
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
            
            println(s"${model.name}, ${fuelCostShort.toInt}, ${fuelCostMiddle.toInt}, ${fuelCostLong.toInt}")
            
            // Longer distance should cost more fuel
            fuelCostLong should be > fuelCostMiddle
            fuelCostMiddle should be > fuelCostShort
            
          case None =>
            println(s"WARNING: Aircraft model '$aircraftName' not found in Model.models")
        }
      }
    }
  }
  
  "all airplane models".must {
    "Generate good profit at MAX LF at suitable range".in {
      Model.models.filter(_.range < 8000).filter(_.capacity > 110).foreach { airplaneModel =>
        val distances = List(15000, 12000, 9000, 6000, 4000, 2000, 1200, 800, 400).reverse
        var outputString = airplaneModel.name
        outputString += ", " + airplaneModel.airplaneTypeLabel
        distances.foreach { distance =>
          if (airplaneModel.range >= distance) {
            val (marginDiscount, marginEcon, marginBiz, marginFirst) = simulateProfitMargin(airplaneModel, 1, distance)
            outputString += ", " + marginDiscount + ", " + marginEcon + ", " + marginBiz + ", " + marginFirst
          }
        }
        println(outputString)
      }
    }
    "Generate good profit at MAX LF per staff".in {
      Model.models.foreach { airplaneModel =>
        val distances = List(15000, 12000, 10000, 8000, 6000, 4000, 2000, 1200, 800, 400).reverse
        var outputString = airplaneModel.name
        outputString += ", " + airplaneModel.airplaneTypeLabel
        distances.foreach { distance =>
          if (airplaneModel.range >= distance) {
            val (staff, marginDiscount, marginEcon, marginBiz, marginFirst) = simulateProfitPerStaff(airplaneModel, 1, distance)
            outputString += ", " + staff + ", " + marginEcon + ", " + marginBiz + ", " + marginFirst
          }
        }
        println(outputString)
      }
    }
  }
  
  def simulateProfitMargin(airplaneModel : Model, loadFactor : Double, distance : Int): (Double, Double, Double, Double) = {
    val (discount, discountStaff) = simulateStandard(airplaneModel, if (airplaneModel.quality > 7) 0 else loadFactor, distance, linkClass = DISCOUNT_ECONOMY)
    val (econ, econStaff) = simulateStandard(airplaneModel, if (airplaneModel.quality > 8) 0 else loadFactor, distance)
    val (biz, bizStaff) = simulateStandard(airplaneModel, if (airplaneModel.quality < 4) 0 else loadFactor, distance, linkClass = BUSINESS)
    val (first, firstStaff) = simulateStandard(airplaneModel, if (airplaneModel.quality < 5) 0 else loadFactor, distance, linkClass = FIRST)
    (discount.profit.toDouble / discount.revenue, econ.profit.toDouble / econ.revenue, biz.profit.toDouble / biz.revenue, first.profit.toDouble / first.revenue)
  }

  def simulateProfitPerStaff(airplaneModel : Model, loadFactor : Double, distance : Int) = {
    //setting LF to zero on less relevant classes for cleaner output
    val (discount, discountStaff) = simulateStandard(airplaneModel, if (airplaneModel.quality > 7) 0 else loadFactor, distance, linkClass = DISCOUNT_ECONOMY)
    val (econ, econStaff) = simulateStandard(airplaneModel, if (airplaneModel.quality > 8) 0 else loadFactor, distance)
    val (biz, bizStaff) = simulateStandard(airplaneModel, if (airplaneModel.quality < 4) 0 else loadFactor, distance, linkClass = BUSINESS)
    val (first, firstStaff) = simulateStandard(airplaneModel, if (airplaneModel.quality < 5) 0 else loadFactor, distance, linkClass = FIRST)
    (econStaff, discount.profit.toDouble / discountStaff, econ.profit.toDouble / econStaff, biz.profit.toDouble / bizStaff, first.profit.toDouble / firstStaff)
  }
  
  def simulateStandard(airplaneModel : Model, loadFactor : Double, distance : Int, linkClass: LinkClass = ECONOMY): (LinkConsumptionDetails, Double) = {
    val airportSize = airplaneModel.airplaneType match {
      case PROPELLER_SMALL => 2
      case SMALL => 3
      case PROPELLER_MEDIUM => 4
      case REGIONAL => 6
      case REGIONAL_XL => 6
      case MEDIUM => 7
      case MEDIUM_XL => 7
      case LARGE => 8
      case EXTRA_LARGE => 8
      case JUMBO => 8
      case JUMBO_XL => 8
      case SUPERSONIC => 8
      case _ => 8
    }
    val duration = Computation.calculateDuration(airplaneModel, distance)
    val flightType = if (distance > 5000) FlightCategory.INTERNATIONAL else FlightCategory.DOMESTIC
    val frequency = Computation.calculateMaxFrequency(airplaneModel, distance)
    val capacity = frequency * (airplaneModel.capacity / linkClass.spaceMultiplier).toInt
    val income = 40_000
    val fromAirport = Airport.fromId(1).copy(size = airportSize, baseIncome = income, basePopulation = 1)
    fromAirport.initAirlineBases(List())
    val toAirport = Airport.fromId(2).copy(size = airportSize)
    toAirport.initAirlineBases(List())
    var price = Pricing.computeStandardPriceForAllClass(distance, flightType, PassengerType.TOURIST, income)
    if (airplaneModel.airplaneType == SUPERSONIC) {
      price *= 1.8
    }
    if (linkClass == FIRST) {
      price *= 1.3 //assume have lounge etc
      price += LinkClassValues(0, Lounge.BASE_COST_REDUCTION, Lounge.BASE_COST_REDUCTION)
    } else if (linkClass == BUSINESS) {
      price *= 1.15
      price += LinkClassValues(0, Lounge.BASE_COST_REDUCTION, Lounge.BASE_COST_REDUCTION)
    }
    val airline = if (airplaneModel.airplaneTypeSize <= 0.12) { Airline("regional",RegionalAirline) } else { Airline("legacy",LegacyAirline) }


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
    val updatedAirplane = AirplaneSimulation.decayAirplanesByAirline(Map(airplane -> LinkAssignments(Map())), airline).head
    link.setTestingAssignedAirplanes(Map(updatedAirplane -> frequency))
    link.addSoldSeats(LinkClassValues.getInstanceByMap(Map(linkClass -> (capacity * loadFactor).toInt)))
    
    LinkSimulation.computeFlightLinkConsumptionDetail(link, 0)
    
    val consumptionResult = LinkSimulation.computeFlightLinkConsumptionDetail(link , 0)
    val staffSchemeBreakdown = link.getCurrentOfficeStaffRequired
//    println(consumptionResult)
    (consumptionResult, staffSchemeBreakdown)
  }
}
