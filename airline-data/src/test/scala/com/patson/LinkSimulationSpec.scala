package com.patson

import org.scalatest.BeforeAndAfterAll
import org.scalatest.Matchers
import org.scalatest.WordSpecLike
import com.patson.model._
import com.patson.model.airplane.{Airplane, AirplaneMaintenanceUtil, Model}
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.testkit.ImplicitSender
import org.apache.pekko.testkit.TestKit

import scala.collection.mutable.ListBuffer
 
class LinkSimulationSpec(_system: ActorSystem) extends TestKit(_system) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll {
 
  def this() = this(ActorSystem("MySpec"))
 
  override protected def beforeAll() : Unit = {
    super.beforeAll()
    AirplaneMaintenanceUtil.setTestFactor(Some(1))
  }

  override protected def afterAll() : Unit = {
    AirplaneMaintenanceUtil.setTestFactor(None)
    TestKit.shutdownActorSystem(system)
    super.afterAll()
  }
 
  val testAirline1 = Airline("airline 1")
  val testAirline2 = Airline("airline 2")
  val fromAirport = Airport.fromId(1).copy(size = 3, baseIncome = 80000, basePopulation = 1)
  fromAirport.initAirlineBases(List.empty)
  val toAirport = Airport.fromId(2).copy(size = 3)
  toAirport.initAirlineBases(List.empty)
  
  val lightModel = Model.modelByName("Cessna Caravan")
  val smallModel = Model.modelByName("ATR 72-600")
  val regionalModel = Model.modelByName("Airbus A220-100")
  val mediumModel = Model.modelByName("Airbus A321neo")
  val largeAirplaneModel = Model.modelByName("Boeing 787-8 Dreamliner")
  val extraLargeAirplaneModel = Model.modelByName("Airbus A350-1000")
  val jumboAirplaneModel = Model.modelByName("Boeing 747-400ER")
  val supersonicAirplaneModel = Model.modelByName("Concorde")
                      
  val lightAirplane = Airplane(lightModel, testAirline1, 0, purchasedCycle = 0, 100, AirplaneSimulation.computeDepreciationRate(lightModel, Airplane.MAX_CONDITION.toDouble / lightModel.lifespan), lightModel.price)
  val smallAirplane = Airplane(smallModel, testAirline1, 0, purchasedCycle = 0, 100, AirplaneSimulation.computeDepreciationRate(smallModel, Airplane.MAX_CONDITION.toDouble / smallModel.lifespan), smallModel.price)
  val regionalAirplane = Airplane(regionalModel, testAirline1, 0, purchasedCycle = 0, 100, AirplaneSimulation.computeDepreciationRate(regionalModel, Airplane.MAX_CONDITION.toDouble / regionalModel.lifespan), regionalModel.price)
  val mediumAirplane = Airplane(mediumModel, testAirline1, 0, purchasedCycle = 0, 100, AirplaneSimulation.computeDepreciationRate(mediumModel, Airplane.MAX_CONDITION.toDouble / mediumModel.lifespan), mediumModel.price)
  val largeAirplane = Airplane(largeAirplaneModel, testAirline1, 0, purchasedCycle = 0, 100, AirplaneSimulation.computeDepreciationRate(largeAirplaneModel, Airplane.MAX_CONDITION.toDouble / largeAirplaneModel.lifespan), largeAirplaneModel.price)
  val extraLargeAirplane = Airplane(extraLargeAirplaneModel, testAirline1, 0, purchasedCycle = 0, 100, AirplaneSimulation.computeDepreciationRate(extraLargeAirplaneModel, Airplane.MAX_CONDITION.toDouble / extraLargeAirplaneModel.lifespan), extraLargeAirplaneModel.price)
  
  import Model.Type._
  private val GOOD_PROFIT_MARGIN = Map(PROPELLER_SMALL -> 0.2, SMALL -> 0.2, REGIONAL -> 0.1, MEDIUM -> 0.1, MEDIUM_XL -> 0.1, LARGE -> 0.1, EXTRA_LARGE -> 0.1, JUMBO -> 0.1, SUPERSONIC -> 0.2)
  private val MAX_PROFIT_MARGIN = Map(PROPELLER_SMALL -> 0.4, SMALL -> 0.4, REGIONAL -> 0.3, MEDIUM -> 0.25, MEDIUM_XL -> 0.2, LARGE -> 0.2, EXTRA_LARGE -> 0.2, JUMBO -> 0.2, SUPERSONIC -> 0.5)
  
  "Compute profit".must {
    "More profitable with more frequency flight (max LF)".in {
      val distance = 200
      val airplane = lightAirplane
      val duration = Computation.calculateDuration(airplane.model, distance)
      val price = Pricing.computeStandardPrice(distance, FlightCategory.INTERNATIONAL, ECONOMY, PassengerType.TRAVELER, Airport.HIGH_INCOME / 2)
      
      var frequency = Computation.calculateMaxFrequency(airplane.model, distance)
      var capacity = frequency * airplane.model.capacity
      
      var link = Link(fromAirport, toAirport, testAirline1, LinkClassValues.getInstanceByMap(Map(ECONOMY -> price)), distance, LinkClassValues.getInstanceByMap(Map(ECONOMY -> capacity)), rawQuality = 0, duration, frequency)
      link.addSoldSeats(LinkClassValues.getInstanceByMap(Map(ECONOMY -> capacity)))
      link.setTestingAssignedAirplanes(Map(airplane -> frequency))
      
      val consumptionResultHighFequency = LinkSimulation.computeFlightLinkConsumptionDetail(link , 0)
      
      frequency = 1
      capacity = frequency * airplane.model.capacity
      link = link.copy(capacity = LinkClassValues.getInstanceByMap(Map(ECONOMY -> capacity)), frequency = frequency)
      link.addSoldSeats(LinkClassValues.getInstanceByMap(Map(ECONOMY -> capacity)))
      link.setTestingAssignedAirplanes(Map(airplane -> frequency))
      val consumptionResultLowFequency = LinkSimulation.computeFlightLinkConsumptionDetail(link , 0)
      
      consumptionResultHighFequency.profit.should(be > consumptionResultLowFequency.profit)
      
    }
    "Not at all profitable at very low LF (0.1 LF) even at suitable range".in {
      var airplane = lightAirplane
      var consumptionResult = simulateStandard(200, airplane, FlightCategory.DOMESTIC, 0.1, 3)
      consumptionResult.profit.should(be < 0)
      
      airplane = smallAirplane
      consumptionResult = simulateStandard(1000, airplane, FlightCategory.DOMESTIC, 0.1, 3)
      consumptionResult.profit.should(be < 0)
      
      airplane = regionalAirplane
      consumptionResult = simulateStandard(4000, airplane, FlightCategory.DOMESTIC, 0.1, 4)
      consumptionResult.profit.should(be < 0)
      
      airplane = mediumAirplane
      consumptionResult = simulateStandard(8000, airplane, FlightCategory.INTERNATIONAL, 0.1, 5)
      consumptionResult.profit.should(be < 0)
      
      airplane = largeAirplane
      consumptionResult = simulateStandard(10000, airplane, FlightCategory.INTERNATIONAL, 0.1, 6)
      consumptionResult.profit.should(be < 0)
    }
    
    "Some profit (but not good) at 0.7 LF at suitable range".in {
      val profits = ListBuffer[Long]()
      val profitMargins = ListBuffer[Double]()
      var airplane = lightAirplane
      var consumptionResult = simulateStandard(500, airplane, FlightCategory.DOMESTIC, 0.7, airportSize = 2)
      consumptionResult.profit.should(be > 0)
      verfiyProfitMargin(consumptionResult, airplane.model, false)
      profits += consumptionResult.profit
      profitMargins += getProfitMargin(consumptionResult)
      
      airplane = smallAirplane
      consumptionResult = simulateStandard(1500, airplane, FlightCategory.DOMESTIC, 0.7, 3)
      consumptionResult.profit.should(be > 0)
      verfiyProfitMargin(consumptionResult, airplane.model, false)
      profits += consumptionResult.profit
      profitMargins += getProfitMargin(consumptionResult)
      
      airplane = regionalAirplane
      consumptionResult = simulateStandard(4000, airplane, FlightCategory.INTERNATIONAL, 0.7, 4)
      //consumptionResult.profit.should(be > 0) //need higher price and loyalty to be profitable
      verfiyProfitMargin(consumptionResult, airplane.model, false)
      profits += consumptionResult.profit
      profitMargins += getProfitMargin(consumptionResult)
      
      //medium and large airplanes need full load to be profitable
//      airplane = mediumAirplane
//      consumptionResult = simulateStandard(8000, airplane, FlightCategory.INTERNATIONAL, 0.7, 5)
//      consumptionResult.profit.should(be > 0)
//      verfiyProfitMargin(consumptionResult, airplane.model, false)
//      profits += consumptionResult.profit
//      profitMargins += getProfitMargin(consumptionResult)
//      
//      airplane = largeAirplane
//      consumptionResult = simulateStandard(13000, airplane, FlightCategory.INTERNATIONAL, 0.7, 6)
//      consumptionResult.profit.should(be > 0)
//      verfiyProfitMargin(consumptionResult, airplane.model, false)
//      profits += consumptionResult.profit
//      profitMargins += getProfitMargin(consumptionResult)
      
      //larger plane should make more profit (not necessary now, i want to make large plane hard
//      verifyInAscendingOrder(profits.toList)
      //but larger plan should make less profit Margin
      verifyInDescendingOrder(profitMargins.toList)
    }
    
    "Good profit at MAX LF at suitable range".in {
      var airplane = lightAirplane
      var consumptionResult = simulateStandard(200, airplane, FlightCategory.DOMESTIC, 1, 3)
      consumptionResult.profit.should(be > 0)
//      verfiyReturnRate(consumptionResult, airplane.model, true)
      
      airplane = smallAirplane
      consumptionResult = simulateStandard(1000, airplane, FlightCategory.DOMESTIC, 1, 3)
      consumptionResult.profit.should(be > 0)
//      verfiyReturnRate(consumptionResult, airplane.model, true)
      
      airplane = regionalAirplane
      consumptionResult = simulateStandard(4000, airplane, FlightCategory.DOMESTIC, 1, 4)
      consumptionResult.profit.should(be > 0)
//      verfiyReturnRate(consumptionResult, airplane.model, true)
      
      airplane = mediumAirplane
      consumptionResult = simulateStandard(8000, airplane, FlightCategory.INTERNATIONAL, 1, 5)
      //consumptionResult.profit.should(be > 0) //need higher price and loyalty to be profitable
//      verfiyReturnRate(consumptionResult, airplane.model, true)
      
      airplane = largeAirplane
      consumptionResult = simulateStandard(10000, airplane, FlightCategory.INTERNATIONAL, 1, 6)
      //consumptionResult.profit.should(be > 0) //need higher price and loyalty to be profitable
//      verfiyReturnRate(consumptionResult, airplane.model, false)
    }
    
    
    "Not profitable at all on very short route < 200 km at large airport (max LF)".in {
      //exempt smaller models for check...okay to make profit
//      airplane = lightAirplane
//      consumptionResult = simulateStandard(200, airplane, FlightCategory.DOMESTIC, 1, 8)
//      verfiyProfitMargin(consumptionResult, airplane.model, false)
//      
//      airplane = regionalAirplane
//      consumptionResult = simulateStandard(200, airplane, FlightCategory.DOMESTIC, 1, 8)
//      verfiyProfitMargin(consumptionResult, airplane.model, false)
//      
//      airplane = smallAirplane
//      consumptionResult = simulateStandard(200, airplane, FlightCategory.DOMESTIC, 1, 8)
//      verfiyProfitMargin(consumptionResult, airplane.model, false)
      
      var airplane = mediumAirplane
      var consumptionResult = simulateStandard(200, airplane, FlightCategory.DOMESTIC, 1, 8)
      verfiyProfitMargin(consumptionResult, airplane.model, false)
      
      airplane = largeAirplane
      consumptionResult = simulateStandard(200, airplane, FlightCategory.DOMESTIC, 1, 8)
      verfiyProfitMargin(consumptionResult, airplane.model, false)
    }
    
    "Good profit with smaller jets on very short route < 200 km at small airport (max LF)".in {
      var airplane = lightAirplane
      var consumptionResult = simulateStandard(200, airplane, FlightCategory.DOMESTIC, 1, airportSize = 2)
      verfiyProfitMargin(consumptionResult, airplane.model, true)
      
      airplane = smallAirplane
      consumptionResult = simulateStandard(200, airplane, FlightCategory.DOMESTIC, 1, airportSize = 2)
      verfiyProfitMargin(consumptionResult, airplane.model, true)
    }
    "More profit with more jets on route if LF is full".in {
       val onePlaneResult = simulateStandard(200, lightModel, FlightCategory.DOMESTIC, 1, airplaneCount = 1)
       val fivePlaneResult = simulateStandard(200, lightModel, FlightCategory.DOMESTIC, 1, airplaneCount = 5)
       
       fivePlaneResult.profit.should(be >= (onePlaneResult.profit * 4.9).toInt) //4.9 as some truncation might make the number off a tiny bit 
    }
    "More profit at higher link class at max LF (small plane)".in  {
      val airplane = smallAirplane
      val airplaneModel = airplane.model
      val distance = 2000
      val duration = Computation.calculateDuration(airplaneModel, distance)
      val frequency = Computation.calculateMaxFrequency(airplaneModel, distance)
      val maxEconomyCapacity = (airplaneModel.capacity / ECONOMY.spaceMultiplier).toInt * frequency
      val maxBusinessCapacity = (airplaneModel.capacity / BUSINESS.spaceMultiplier).toInt * frequency
      val maxFirstCapacity = (airplaneModel.capacity / FIRST.spaceMultiplier).toInt * frequency
      val allEconomyCapacity : LinkClassValues = LinkClassValues.getInstance(maxEconomyCapacity, 0, 0)
      val allBusinessCapacity : LinkClassValues = LinkClassValues.getInstance(0, maxBusinessCapacity, 0)
      val allFirstCapacity : LinkClassValues = LinkClassValues.getInstance(0, 0, maxFirstCapacity)
      
      val economyPrice = Pricing.computeStandardPrice(distance, FlightCategory.INTERNATIONAL, ECONOMY, PassengerType.TRAVELER, Airport.HIGH_INCOME / 2)
      val businessPrice = Pricing.computeStandardPrice(distance, FlightCategory.INTERNATIONAL, BUSINESS, PassengerType.TRAVELER, Airport.HIGH_INCOME / 2)
      val firstPrice = Pricing.computeStandardPrice(distance, FlightCategory.INTERNATIONAL, FIRST, PassengerType.TRAVELER, Airport.HIGH_INCOME / 2)
    
      val economylink = Link(fromAirport, toAirport, testAirline1, LinkClassValues.getInstanceByMap(Map(ECONOMY -> economyPrice)), distance = distance, allEconomyCapacity, rawQuality = fromAirport.expectedQuality(distance, ECONOMY), duration, frequency)
      val businessLink = Link(fromAirport, toAirport, testAirline1, LinkClassValues.getInstanceByMap(Map(BUSINESS -> businessPrice)), distance = distance, allBusinessCapacity, rawQuality = fromAirport.expectedQuality(distance, BUSINESS), duration, frequency)
      val firstLink = Link(fromAirport, toAirport, testAirline1, LinkClassValues.getInstanceByMap(Map(FIRST -> firstPrice)), distance = distance, allFirstCapacity, rawQuality = fromAirport.expectedQuality(distance, FIRST), duration, frequency)
    
      economylink.addSoldSeats(allEconomyCapacity) //all consumed
      businessLink.addSoldSeats(allBusinessCapacity) //all consumed
      firstLink.addSoldSeats(allFirstCapacity) //all consumed
    
      economylink.setTestingAssignedAirplanes(Map(airplane -> frequency))
      businessLink.setTestingAssignedAirplanes(Map(airplane -> frequency))
      firstLink.setTestingAssignedAirplanes(Map(airplane -> frequency))
      
    
      val economyResult = LinkSimulation.computeFlightLinkConsumptionDetail(economylink , 0)
      val businessResult = LinkSimulation.computeFlightLinkConsumptionDetail(businessLink , 0)
      val firstResult = LinkSimulation.computeFlightLinkConsumptionDetail(firstLink , 0)
      
      economyResult.profit.should(be < businessResult.profit)
      businessResult.profit.should(be < firstResult.profit)
      
      (economyResult.profit.toDouble / economyResult.revenue.toDouble).should(be > GOOD_PROFIT_MARGIN(airplane.model.airplaneType))      
      (businessResult.profit.toDouble / businessResult.revenue.toDouble).should(be > GOOD_PROFIT_MARGIN(airplane.model.airplaneType))
      (firstResult.profit.toDouble / firstResult.revenue.toDouble).should(be > GOOD_PROFIT_MARGIN(airplane.model.airplaneType))
      
      (economyResult.profit.toDouble / economyResult.revenue.toDouble).should(be < MAX_PROFIT_MARGIN(airplane.model.airplaneType))      
      (businessResult.profit.toDouble / businessResult.revenue.toDouble).should(be < MAX_PROFIT_MARGIN(airplane.model.airplaneType))
      //(firstResult.profit.toDouble / firstResult.revenue.toDouble).should(be < MAX_PROFIT_MARGIN(airplane.model.airplaneType)) OK to make good profit here 
    }
    "More profit at higher link class at max LF (regional plane)".in  {
      val airplane = regionalAirplane
      val airplaneModel = airplane.model
      val distance = 5000
      val duration = Computation.calculateDuration(airplaneModel, distance)
      val frequency = Computation.calculateMaxFrequency(airplaneModel, distance)
      val maxEconomyCapacity = (airplaneModel.capacity / ECONOMY.spaceMultiplier).toInt * frequency
      val maxBusinessCapacity = (airplaneModel.capacity / BUSINESS.spaceMultiplier).toInt * frequency
      val maxFirstCapacity = (airplaneModel.capacity / FIRST.spaceMultiplier).toInt * frequency
      val allEconomyCapacity : LinkClassValues = LinkClassValues.getInstance(maxEconomyCapacity, 0, 0) 
      val allBusinessCapacity : LinkClassValues = LinkClassValues.getInstance(0, maxBusinessCapacity, 0)
      val allFirstCapacity : LinkClassValues = LinkClassValues.getInstance(0, 0, maxFirstCapacity)
      
      val economyPrice = Pricing.computeStandardPrice(distance, FlightCategory.INTERNATIONAL, ECONOMY, PassengerType.TRAVELER, Airport.HIGH_INCOME / 2)
      val businessPrice = Pricing.computeStandardPrice(distance, FlightCategory.INTERNATIONAL, BUSINESS, PassengerType.TRAVELER, Airport.HIGH_INCOME / 2)
      val firstPrice = Pricing.computeStandardPrice(distance, FlightCategory.INTERNATIONAL, FIRST, PassengerType.TRAVELER, Airport.HIGH_INCOME / 2)
    
      val economylink = Link(fromAirport, toAirport, testAirline1, LinkClassValues.getInstanceByMap(Map(ECONOMY -> economyPrice)), distance = distance, allEconomyCapacity, rawQuality = fromAirport.expectedQuality(distance, ECONOMY), duration, frequency)
      val businessLink = Link(fromAirport, toAirport, testAirline1, LinkClassValues.getInstanceByMap(Map(BUSINESS -> businessPrice)), distance = distance, allBusinessCapacity, rawQuality = fromAirport.expectedQuality(distance, BUSINESS), duration, frequency)
      val firstLink = Link(fromAirport, toAirport, testAirline1, LinkClassValues.getInstanceByMap(Map(FIRST -> firstPrice)), distance = distance, allFirstCapacity, rawQuality = fromAirport.expectedQuality(distance, FIRST), duration, frequency)
    
      economylink.addSoldSeats(allEconomyCapacity) //all consumed
      businessLink.addSoldSeats(allBusinessCapacity) //all consumed
      firstLink.addSoldSeats(allFirstCapacity) //all consumed
    
      economylink.setTestingAssignedAirplanes(Map(airplane -> frequency))
      businessLink.setTestingAssignedAirplanes(Map(airplane -> frequency))
      firstLink.setTestingAssignedAirplanes(Map(airplane -> frequency))
      
    
      val economyResult = LinkSimulation.computeFlightLinkConsumptionDetail(economylink , 0)
      val businessResult = LinkSimulation.computeFlightLinkConsumptionDetail(businessLink , 0)
      val firstResult = LinkSimulation.computeFlightLinkConsumptionDetail(firstLink , 0)
      
      economyResult.profit.should(be < businessResult.profit)
      businessResult.profit.should(be < firstResult.profit)
      
      (economyResult.profit.toDouble / economyResult.revenue.toDouble).should(be > GOOD_PROFIT_MARGIN(airplane.model.airplaneType))      
      (businessResult.profit.toDouble / businessResult.revenue.toDouble).should(be > GOOD_PROFIT_MARGIN(airplane.model.airplaneType))
      (firstResult.profit.toDouble / firstResult.revenue.toDouble).should(be > GOOD_PROFIT_MARGIN(airplane.model.airplaneType))
      
      (economyResult.profit.toDouble / economyResult.revenue.toDouble).should(be < MAX_PROFIT_MARGIN(airplane.model.airplaneType))      
      (businessResult.profit.toDouble / businessResult.revenue.toDouble).should(be < MAX_PROFIT_MARGIN(airplane.model.airplaneType))
      //(firstResult.profit.toDouble / firstResult.revenue.toDouble).should(be < MAX_PROFIT_MARGIN(airplane.model.airplaneType)) OK to make good profit here 
    }
    "More profit at higher link class at max LF (large plane)".in  {
      val airplane = largeAirplane
      val airplaneModel = airplane.model
      val distance = 10000
      val duration = Computation.calculateDuration(airplaneModel, distance)
      val frequency = Computation.calculateMaxFrequency(airplaneModel, distance)
      val maxEconomyCapacity = (airplaneModel.capacity / ECONOMY.spaceMultiplier).toInt * frequency
      val maxBusinessCapacity = (airplaneModel.capacity / BUSINESS.spaceMultiplier).toInt * frequency
      val maxFirstCapacity = (airplaneModel.capacity / FIRST.spaceMultiplier).toInt * frequency
      val allEconomyCapacity : LinkClassValues = LinkClassValues.getInstance(maxEconomyCapacity, 0, 0)
      val allBusinessCapacity : LinkClassValues = LinkClassValues.getInstance(0, maxBusinessCapacity, 0)
      val allFirstCapacity : LinkClassValues = LinkClassValues.getInstance(0, 0, maxFirstCapacity)
      
      val economyPrice = Pricing.computeStandardPrice(distance, FlightCategory.INTERNATIONAL, ECONOMY, PassengerType.TRAVELER, Airport.HIGH_INCOME / 2)
      val businessPrice = Pricing.computeStandardPrice(distance, FlightCategory.INTERNATIONAL, BUSINESS, PassengerType.TRAVELER, Airport.HIGH_INCOME / 2)
      val firstPrice = Pricing.computeStandardPrice(distance, FlightCategory.INTERNATIONAL, FIRST, PassengerType.TRAVELER, Airport.HIGH_INCOME / 2)
    
      val economylink = Link(fromAirport, toAirport, testAirline1, LinkClassValues.getInstanceByMap(Map(ECONOMY -> economyPrice.toInt)), distance = distance, allEconomyCapacity, rawQuality = fromAirport.expectedQuality(distance, ECONOMY), duration, frequency)
      val businessLink = Link(fromAirport, toAirport, testAirline1, LinkClassValues.getInstanceByMap(Map(BUSINESS -> businessPrice.toInt)), distance = distance, allBusinessCapacity, rawQuality = fromAirport.expectedQuality(distance, BUSINESS), duration, frequency)
      val firstLink = Link(fromAirport, toAirport, testAirline1, LinkClassValues.getInstanceByMap(Map(FIRST -> firstPrice.toInt)), distance = distance, allFirstCapacity, rawQuality = fromAirport.expectedQuality(distance, FIRST), duration, frequency)
    
      economylink.addSoldSeats(allEconomyCapacity) //all consumed
      businessLink.addSoldSeats(allBusinessCapacity) //all consumed
      firstLink.addSoldSeats(allFirstCapacity) //all consumed
    
      economylink.setTestingAssignedAirplanes(Map(airplane -> frequency))
      businessLink.setTestingAssignedAirplanes(Map(airplane -> frequency))
      firstLink.setTestingAssignedAirplanes(Map(airplane -> frequency))
      
    
      val economyResult = LinkSimulation.computeFlightLinkConsumptionDetail(economylink , 0)
      val businessResult = LinkSimulation.computeFlightLinkConsumptionDetail(businessLink , 0)
      val firstResult = LinkSimulation.computeFlightLinkConsumptionDetail(firstLink , 0)
      
      economyResult.profit.should(be < businessResult.profit)
      businessResult.profit.should(be < firstResult.profit)
      
      (economyResult.profit.toDouble / economyResult.revenue.toDouble).should(be > GOOD_PROFIT_MARGIN(airplane.model.airplaneType))      
      (businessResult.profit.toDouble / businessResult.revenue.toDouble).should(be > GOOD_PROFIT_MARGIN(airplane.model.airplaneType))
      (firstResult.profit.toDouble / firstResult.revenue.toDouble).should(be > GOOD_PROFIT_MARGIN(airplane.model.airplaneType))
      
      (economyResult.profit.toDouble / economyResult.revenue.toDouble).should(be < MAX_PROFIT_MARGIN(airplane.model.airplaneType))      
//      (businessResult.profit.toDouble / businessResult.revenue.toDouble).should(be < MAX_PROFIT_MARGIN(airplane.model.airplaneType)) OK to make good profit if it fills
      //(firstResult.profit.toDouble / firstResult.revenue.toDouble).should(be < MAX_PROFIT_MARGIN(airplane.model.airplaneType)) OK to make good profit if it fills 
    }
    
    "reduce profit on delays".in {
      val distance = 8000
      val airplane = largeAirplane
      val maxFrequencyPerAirplane = Computation.calculateMaxFrequency(airplane.model, distance)
      var airplanes: Map[Airplane, Int] = Map(airplane -> maxFrequencyPerAirplane, airplane -> maxFrequencyPerAirplane, airplane -> maxFrequencyPerAirplane) //3 airplanes
      val duration = Computation.calculateDuration(airplane.model, distance)
      val price = Pricing.computeStandardPrice(distance, FlightCategory.INTERNATIONAL, ECONOMY, PassengerType.TRAVELER, Airport.HIGH_INCOME / 2)
      
      val frequency =  airplanes.toList.map(_._2).sum
      val capacity = frequency * airplane.model.capacity
      
      
      var link = Link(fromAirport, toAirport, testAirline1, LinkClassValues.getInstanceByMap(Map(ECONOMY -> price)), distance, LinkClassValues.getInstanceByMap(Map(ECONOMY -> capacity)), rawQuality = fromAirport.expectedQuality(distance, ECONOMY), duration, frequency)
      println("delay link " + link)
      link.addSoldSeats(LinkClassValues.getInstanceByMap(Map(ECONOMY -> capacity)))
      link.setTestingAssignedAirplanes(airplanes)
      val consumptionResultNoDelays = LinkSimulation.computeFlightLinkConsumptionDetail(link , 0)
      
      link = link.copy(capacity = LinkClassValues.getInstanceByMap(Map(ECONOMY -> capacity)), frequency = frequency)
      link.addSoldSeats(LinkClassValues.getInstanceByMap(Map(ECONOMY -> capacity)))
      link.setTestingAssignedAirplanes(airplanes)
      link.minorDelayCount = 1
      val consumptionResultSingleMinorDelay = LinkSimulation.computeFlightLinkConsumptionDetail(link , 0)
      
      link = link.copy(capacity = LinkClassValues.getInstanceByMap(Map(ECONOMY -> capacity)), frequency = frequency)
      link.addSoldSeats(LinkClassValues.getInstanceByMap(Map(ECONOMY -> capacity)))
      link.setTestingAssignedAirplanes(airplanes)
      link.majorDelayCount = 1
      val consumptionResultSingleMajorDelay = LinkSimulation.computeFlightLinkConsumptionDetail(link , 0)
      
      link = link.copy(capacity = LinkClassValues.getInstanceByMap(Map(ECONOMY -> capacity)), frequency = frequency)
      link.addSoldSeats(LinkClassValues.getInstanceByMap(Map(ECONOMY -> (capacity - airplane.model.capacity * 1))))
      link.setTestingAssignedAirplanes(airplanes)
      link.cancellationCount = 1
      val consumptionResultSingleCancellation = LinkSimulation.computeFlightLinkConsumptionDetail(link , 0)
      
      
      link = link.copy(capacity = LinkClassValues.getInstanceByMap(Map(ECONOMY -> capacity)), frequency = frequency)
      link.addSoldSeats(LinkClassValues.getInstanceByMap(Map(ECONOMY -> capacity)))
      link.setTestingAssignedAirplanes(airplanes)
      link.minorDelayCount = frequency / 2
      val consumptionResultHalfMinorDelay = LinkSimulation.computeFlightLinkConsumptionDetail(link , 0)
      
      link = link.copy(capacity = LinkClassValues.getInstanceByMap(Map(ECONOMY -> capacity)), frequency = frequency)
      link.addSoldSeats(LinkClassValues.getInstanceByMap(Map(ECONOMY -> capacity)))
      link.setTestingAssignedAirplanes(airplanes)
      link.majorDelayCount = frequency / 2
      val consumptionResultHalfMajorDelay = LinkSimulation.computeFlightLinkConsumptionDetail(link , 0)
      
      link = link.copy(capacity = LinkClassValues.getInstanceByMap(Map(ECONOMY -> capacity)), frequency = frequency)
      link.addSoldSeats(LinkClassValues.getInstanceByMap(Map(ECONOMY -> (capacity / 2))))
      link.setTestingAssignedAirplanes(airplanes)
      link.cancellationCount = frequency / 2 
      val consumptionResultHalfCancellation = LinkSimulation.computeFlightLinkConsumptionDetail(link , 0)
      
      link = link.copy(capacity = LinkClassValues.getInstanceByMap(Map(ECONOMY -> capacity)), frequency = frequency)
      link.addSoldSeats(LinkClassValues.getInstanceByMap(Map(ECONOMY -> capacity)))
      link.setTestingAssignedAirplanes(airplanes)
      link.minorDelayCount = frequency 
      val consumptionResultAllMinorDelay = LinkSimulation.computeFlightLinkConsumptionDetail(link , 0)
      
      link = link.copy(capacity = LinkClassValues.getInstanceByMap(Map(ECONOMY -> capacity)), frequency = frequency)
      link.addSoldSeats(LinkClassValues.getInstanceByMap(Map(ECONOMY -> capacity)))
      link.setTestingAssignedAirplanes(airplanes)
      link.majorDelayCount = frequency 
      val consumptionResultAllMajorDelay = LinkSimulation.computeFlightLinkConsumptionDetail(link , 0)
      
      link = link.copy(capacity = LinkClassValues.getInstanceByMap(Map(ECONOMY -> capacity)), frequency = frequency)
      //link.addSoldSeats(LinkClassValues.getInstanceByMap(Map(ECONOMY -> capacity))) no sold seats
      link.setTestingAssignedAirplanes(airplanes)
      link.cancellationCount = frequency 
      val consumptionResultAllCancelled = LinkSimulation.computeFlightLinkConsumptionDetail(link , 0)
      
      assert(consumptionResultNoDelays.profit > 0)
      
      //single minor incident should not make it negative
      assert(consumptionResultSingleMinorDelay.profit > 0)
      //assert(consumptionResultSingleMajorDelay.profit > 0)
      //assert(consumptionResultSingleCancellation.profit > 0)
      
      //more severe the incident, the less profit
      assert(consumptionResultNoDelays.profit > consumptionResultSingleMinorDelay.profit)
      assert(consumptionResultSingleMinorDelay.profit > consumptionResultSingleMajorDelay.profit)
      assert(consumptionResultSingleMajorDelay.profit > consumptionResultSingleCancellation.profit)
      
      //at half the incident, it should not be profitable in more severe cases
//      assert(consumptionResultHalfMinorDelay.profit > 0)
//      assert(consumptionResultHalfMajorDelay.profit > 0)
      assert(consumptionResultHalfCancellation.profit < 0)
      
      //at full incident, it should not be profitable in any cases      
//      assert(consumptionResultAllMinorDelay.profit > 0) 
//      assert(consumptionResultAllMajorDelay.profit < 0)
      assert(consumptionResultAllCancelled.profit < 0)
    }
    
    "More profitable to have low quality flight if quality requirement is low".in  {
      val airplane = largeAirplane
      val airplaneModel = airplane.model
      val distance = 5000
      val duration = Computation.calculateDuration(airplaneModel, distance)
      val frequency = Computation.calculateMaxFrequency(airplaneModel, distance)
      val maxEconomyCapacity = (airplaneModel.capacity / ECONOMY.spaceMultiplier).toInt * frequency
      val maxBusinessCapacity = (airplaneModel.capacity / BUSINESS.spaceMultiplier).toInt * frequency
      val maxFirstCapacity = (airplaneModel.capacity / FIRST.spaceMultiplier).toInt * frequency
      val allEconomyCapacity : LinkClassValues = LinkClassValues.getInstance(maxEconomyCapacity, 0, 0)
      val allBusinessCapacity : LinkClassValues = LinkClassValues.getInstance(0, maxBusinessCapacity, 0)
      val allFirstCapacity : LinkClassValues = LinkClassValues.getInstance(0, 0, maxFirstCapacity)
      
      val economyPrice = Pricing.computeStandardPrice(distance, FlightCategory.INTERNATIONAL, ECONOMY, PassengerType.TRAVELER, Airport.HIGH_INCOME / 2)
      
      val economylink1 = Link(fromAirport, toAirport, testAirline1, LinkClassValues.getInstanceByMap(Map(ECONOMY -> (economyPrice * 1.1).toInt)), distance = distance, allEconomyCapacity, rawQuality = 100, duration, frequency)
      val economylink2 = Link(fromAirport, toAirport, testAirline1, LinkClassValues.getInstanceByMap(Map(ECONOMY -> economyPrice)), distance = distance, allEconomyCapacity, rawQuality = 20, duration, frequency)
    
      economylink1.addSoldSeats(allEconomyCapacity) //all consumed
      economylink2.addSoldSeats(allEconomyCapacity) //all consumed
      
      economylink1.setTestingAssignedAirplanes(Map(airplane -> frequency))
      economylink2.setTestingAssignedAirplanes(Map(airplane -> frequency))
      
    
      val economyResult1 = LinkSimulation.computeFlightLinkConsumptionDetail(economylink1 , 0)
      val economyResult2 = LinkSimulation.computeFlightLinkConsumptionDetail(economylink2 , 0)
      
      economyResult1.profit.should(be < economyResult2.profit)
    }

    "Reasonable profit margin for each raw service level (domestic) ".in  {
      val airplane = largeAirplane
      val airplaneModel = airplane.model
      val distance = 2000
      val duration = Computation.calculateDuration(airplaneModel, distance)
      val frequency = Computation.calculateMaxFrequency(airplaneModel, distance)
      val maxEconomyCapacity = (airplaneModel.capacity / ECONOMY.spaceMultiplier).toInt * frequency
      val allEconomyCapacity : LinkClassValues = LinkClassValues.getInstance(maxEconomyCapacity, 0, 0)

      val economyPrice = Pricing.computeStandardPrice(distance, FlightCategory.DOMESTIC, ECONOMY, PassengerType.TRAVELER, Airport.HIGH_INCOME / 2)

      val economylink1 = Link(fromAirport, toAirport, testAirline1, LinkClassValues.getInstanceByMap(Map(ECONOMY -> (economyPrice * 1.1).toInt)), distance = distance, allEconomyCapacity, rawQuality = 20, duration, frequency)
      val economylink2 = Link(fromAirport, toAirport, testAirline1, LinkClassValues.getInstanceByMap(Map(ECONOMY -> economyPrice)), distance = distance, allEconomyCapacity, rawQuality = 40, duration, frequency)
      val economylink3 = Link(fromAirport, toAirport, testAirline1, LinkClassValues.getInstanceByMap(Map(ECONOMY -> economyPrice)), distance = distance, allEconomyCapacity, rawQuality = 60, duration, frequency)
      val economylink4 = Link(fromAirport, toAirport, testAirline1, LinkClassValues.getInstanceByMap(Map(ECONOMY -> economyPrice)), distance = distance, allEconomyCapacity, rawQuality = 80, duration, frequency)
      val economylink5 = Link(fromAirport, toAirport, testAirline1, LinkClassValues.getInstanceByMap(Map(ECONOMY -> economyPrice)), distance = distance, allEconomyCapacity, rawQuality = 100, duration, frequency)

      economylink1.addSoldSeats(allEconomyCapacity) //all consumed
      economylink2.addSoldSeats(allEconomyCapacity) //all consumed
      economylink3.addSoldSeats(allEconomyCapacity) //all consumed
      economylink4.addSoldSeats(allEconomyCapacity) //all consumed
      economylink5.addSoldSeats(allEconomyCapacity) //all consumed

      economylink1.setTestingAssignedAirplanes(Map(airplane -> frequency))
      economylink2.setTestingAssignedAirplanes(Map(airplane -> frequency))
      economylink3.setTestingAssignedAirplanes(Map(airplane -> frequency))
      economylink4.setTestingAssignedAirplanes(Map(airplane -> frequency))
      economylink5.setTestingAssignedAirplanes(Map(airplane -> frequency))


      val economyResult1 = LinkSimulation.computeFlightLinkConsumptionDetail(economylink1 , 0)
      val economyResult2 = LinkSimulation.computeFlightLinkConsumptionDetail(economylink2 , 0)
      val economyResult3 = LinkSimulation.computeFlightLinkConsumptionDetail(economylink3 , 0)
      val economyResult4 = LinkSimulation.computeFlightLinkConsumptionDetail(economylink4 , 0)
      val economyResult5 = LinkSimulation.computeFlightLinkConsumptionDetail(economylink5 , 0)

      val profitMargin1 = economyResult1.profit.toDouble / economyResult1.revenue.toDouble
      val profitMargin2 = economyResult2.profit.toDouble / economyResult2.revenue.toDouble
      val profitMargin3 = economyResult3.profit.toDouble / economyResult3.revenue.toDouble
      val profitMargin4 = economyResult4.profit.toDouble / economyResult4.revenue.toDouble
      val profitMargin5 = economyResult5.profit.toDouble / economyResult5.revenue.toDouble

      assert(profitMargin1 > 0.15 && profitMargin1 < 0.3)
      assert(profitMargin2 > 0.05 && profitMargin2 < 0.2)
      assert(profitMargin3 > 0.0 && profitMargin3 < 0.1)
      assert(profitMargin4 > -0.1 && profitMargin4 < 0.05) //not profitable with standard price
      assert(profitMargin5 > -0.3 && profitMargin5 < 0.05) //not profitable with standard price
    }

    "Reasonable profit margin for each raw service level (intercontinental) ".in  {
      val airplane = largeAirplane
      val airplaneModel = airplane.model
      val distance = 10000
      val duration = Computation.calculateDuration(airplaneModel, distance)
      val frequency = Computation.calculateMaxFrequency(airplaneModel, distance)
      val maxEconomyCapacity = (airplaneModel.capacity / ECONOMY.spaceMultiplier).toInt * frequency
      val allEconomyCapacity : LinkClassValues = LinkClassValues.getInstance(maxEconomyCapacity, 0, 0)

      val economyPrice = Pricing.computeStandardPrice(distance, FlightCategory.INTERNATIONAL, ECONOMY, PassengerType.TRAVELER, Airport.HIGH_INCOME / 2)

      val economylink1 = Link(fromAirport, toAirport, testAirline1, LinkClassValues.getInstanceByMap(Map(ECONOMY -> economyPrice)), distance = distance, allEconomyCapacity, rawQuality = 20, duration, frequency)
      val economylink2 = Link(fromAirport, toAirport, testAirline1, LinkClassValues.getInstanceByMap(Map(ECONOMY -> economyPrice)), distance = distance, allEconomyCapacity, rawQuality = 40, duration, frequency)
      val economylink3 = Link(fromAirport, toAirport, testAirline1, LinkClassValues.getInstanceByMap(Map(ECONOMY -> economyPrice)), distance = distance, allEconomyCapacity, rawQuality = 60, duration, frequency)
      val economylink4 = Link(fromAirport, toAirport, testAirline1, LinkClassValues.getInstanceByMap(Map(ECONOMY -> economyPrice)), distance = distance, allEconomyCapacity, rawQuality = 80, duration, frequency)
      val economylink5 = Link(fromAirport, toAirport, testAirline1, LinkClassValues.getInstanceByMap(Map(ECONOMY -> economyPrice)), distance = distance, allEconomyCapacity, rawQuality = 100, duration, frequency)

      economylink1.addSoldSeats(allEconomyCapacity) //all consumed
      economylink2.addSoldSeats(allEconomyCapacity) //all consumed
      economylink3.addSoldSeats(allEconomyCapacity) //all consumed
      economylink4.addSoldSeats(allEconomyCapacity) //all consumed
      economylink5.addSoldSeats(allEconomyCapacity) //all consumed

      economylink1.setTestingAssignedAirplanes(Map(airplane -> frequency))
      economylink2.setTestingAssignedAirplanes(Map(airplane -> frequency))
      economylink3.setTestingAssignedAirplanes(Map(airplane -> frequency))
      economylink4.setTestingAssignedAirplanes(Map(airplane -> frequency))
      economylink5.setTestingAssignedAirplanes(Map(airplane -> frequency))


      val economyResult1 = LinkSimulation.computeFlightLinkConsumptionDetail(economylink1 , 0)
      val economyResult2 = LinkSimulation.computeFlightLinkConsumptionDetail(economylink2 , 0)
      val economyResult3 = LinkSimulation.computeFlightLinkConsumptionDetail(economylink3 , 0)
      val economyResult4 = LinkSimulation.computeFlightLinkConsumptionDetail(economylink4 , 0)
      val economyResult5 = LinkSimulation.computeFlightLinkConsumptionDetail(economylink5 , 0)

      val profitMargin1 = economyResult1.profit.toDouble / economyResult1.revenue.toDouble
      val profitMargin2 = economyResult2.profit.toDouble / economyResult2.revenue.toDouble
      val profitMargin3 = economyResult3.profit.toDouble / economyResult3.revenue.toDouble
      val profitMargin4 = economyResult4.profit.toDouble / economyResult4.revenue.toDouble
      val profitMargin5 = economyResult5.profit.toDouble / economyResult5.revenue.toDouble

      assert(profitMargin1 > 0.3 && profitMargin1 < 0.4)
      assert(profitMargin2 > 0.15 && profitMargin2 < 0.3)
      assert(profitMargin3 > 0.05 && profitMargin3 < 0.2)
      assert(profitMargin4 > -0.1 && profitMargin4 < 0.1) //not profitable with standard price
      assert(profitMargin5 > -0.2 && profitMargin5 < 0) //not profitable with standard price
    }
  }


  
  def getProfitMargin(consumptionResult : LinkConsumptionDetails) = consumptionResult.profit.toDouble / consumptionResult.revenue.toDouble
  
  def verfiyProfitMargin(consumptionResult : LinkConsumptionDetails, model : Model, expectGoodReturn : Boolean) = {
    val profitMargin = getProfitMargin(consumptionResult)
    println(consumptionResult.link.soldSeats(ECONOMY) * 100 / consumptionResult.link.capacity(ECONOMY) + "%" + " PM:" +  profitMargin + " " +  model.name + " " + consumptionResult)
    if (expectGoodReturn) {
      profitMargin.should(be >= GOOD_PROFIT_MARGIN(model.airplaneType) and be <= MAX_PROFIT_MARGIN(model.airplaneType))
    } else {
      profitMargin.should(be < GOOD_PROFIT_MARGIN(model.airplaneType))
    }
  }
  def simulateStandard(distance : Int, airplane : Airplane, flightType : FlightCategory.Value, loadFactor : Double) : LinkConsumptionDetails = {
    simulateStandard(distance, airplane.model, flightType, loadFactor, 3, 1)
  }
  def simulateStandard(distance : Int, airplane : Airplane, flightType : FlightCategory.Value, loadFactor : Double, airportSize : Int) : LinkConsumptionDetails = {
    simulateStandard(distance, airplane.model, flightType, loadFactor, airportSize, 1)
  }
  
  def simulateStandard(distance : Int, airplaneModel : Model, flightType : FlightCategory.Value, loadFactor : Double, airportSize : Int = 3, airplaneCount : Int = 1) : LinkConsumptionDetails = {
    val duration = Computation.calculateDuration(airplaneModel, distance)
    val maxFrequencyPerAirplane = Computation.calculateMaxFrequency(airplaneModel, distance)
    val frequency = maxFrequencyPerAirplane * airplaneCount
    val capacity = frequency * airplaneModel.capacity
    val price = Pricing.computeStandardPrice(distance, flightType, ECONOMY, PassengerType.TRAVELER, Airport.HIGH_INCOME / 2)
    
    val fromAirportClone = fromAirport.copy(size = airportSize)
    fromAirportClone.initAirlineBases(fromAirport.getAirlineBases().toList.map(_._2))
    val toAirportClone = toAirport.copy(size = airportSize)
    toAirportClone.initAirlineBases(toAirport.getAirlineBases().toList.map(_._2))
    
    val link = Link(fromAirportClone, toAirportClone, testAirline1, LinkClassValues.getInstanceByMap(Map(ECONOMY -> price)), distance = distance, LinkClassValues.getInstanceByMap(Map(ECONOMY -> capacity)), rawQuality = fromAirport.expectedQuality(distance, ECONOMY), duration, frequency)
    link.addSoldSeats(LinkClassValues.getInstanceByMap(Map(ECONOMY -> (capacity * loadFactor).toInt)))
    
    link.setTestingAssignedAirplanes((0 until airplaneCount).foldRight(Map[Airplane, Int]()) {
      case (_, foldList) => {
        val airplane = Airplane(airplaneModel, testAirline1, 0, purchasedCycle = 0, 100, AirplaneSimulation.computeDepreciationRate(airplaneModel, Airplane.MAX_CONDITION.toDouble / airplaneModel.lifespan), airplaneModel.price)
        foldList + ((airplane, maxFrequencyPerAirplane))
      }
    })
    
    val consumptionResult = LinkSimulation.computeFlightLinkConsumptionDetail(link , 0)
    println(consumptionResult)
    consumptionResult
  }
  
  def verifyInAscendingOrder(numbers : List[Long]) = {
    assert(numbers.sorted == numbers)
  }
  
  def verifyInDescendingOrder(numbers : List[Double]) = {
    assert(numbers.sorted(Ordering[Double].reverse) == numbers)
  }
  
  
}


