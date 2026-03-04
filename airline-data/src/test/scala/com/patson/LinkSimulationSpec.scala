package com.patson

import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import com.patson.model._
import com.patson.model.airplane.{Airplane, AirplaneMaintenanceUtil, Model}
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.testkit.ImplicitSender
import org.apache.pekko.testkit.TestKit

import java.util.concurrent.ThreadLocalRandom
import scala.collection.mutable
import scala.collection.mutable.ListBuffer

class LinkSimulationSpec(_system: ActorSystem) extends TestKit(_system) with ImplicitSender
  with AnyWordSpecLike with Matchers with BeforeAndAfterAll {

  def this() = this(ActorSystem("MySpec"))

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    AirplaneMaintenanceUtil.setTestFactor(Some(1))
  }

  override protected def afterAll(): Unit = {
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

  val lightModel = Model.modelByName("Cessna 208 Caravan")
  val smallModel = Model.modelByName("ATR 72-600")
  val regionalModel = Model.modelByName("Embraer E190")
  val mediumModel = Model.modelByName("Boeing 737 MAX 7")
  val largeAirplaneModel = Model.modelByName("Boeing 787-8 Dreamliner")
  val extraLargeAirplaneModel = Model.modelByName("Airbus A350-1000")
  val jumboAirplaneModel = Model.modelByName("Boeing 747-400ER")
  val supersonicAirplaneModel = Model.modelByName("Concorde")

  val lightAirplane = Airplane(lightModel, testAirline1, 0, purchasedCycle = 0, 100, lightModel.price)
  val smallAirplane = Airplane(smallModel, testAirline1, 0, purchasedCycle = 0, 100, smallModel.price)
  val regionalAirplane = Airplane(regionalModel, testAirline1, 0, purchasedCycle = 0, 100, regionalModel.price)
  val mediumAirplane = Airplane(mediumModel, testAirline1, 0, purchasedCycle = 0, 100, mediumModel.price)
  val largeAirplane = Airplane(largeAirplaneModel, testAirline1, 0, purchasedCycle = 0, 100, largeAirplaneModel.price)
  val extraLargeAirplane = Airplane(extraLargeAirplaneModel, testAirline1, 0, purchasedCycle = 0, 100, extraLargeAirplaneModel.price)

  import Model.Type._
  private val GOOD_PROFIT_MARGIN = Map(PROPELLER_SMALL -> 0.3, SMALL -> 0.3, REGIONAL -> 0.2, MEDIUM -> 0.2, MEDIUM_XL -> 0.2, LARGE -> 0.15, EXTRA_LARGE -> 0.15, JUMBO -> 0.1, SUPERSONIC -> 0.2)
  private val MAX_PROFIT_MARGIN = Map(PROPELLER_SMALL -> 0.6, SMALL -> 0.6, REGIONAL -> 0.4, MEDIUM -> 0.4, MEDIUM_XL -> 0.3, LARGE -> 0.3, EXTRA_LARGE -> 0.2, JUMBO -> 0.2, SUPERSONIC -> 0.5)

  // assume 50% bad weather
  val airportWeather: Map[Int, Double] = List(
    fromAirport.id -> 0.5,
    toAirport.id -> 0.5,
  ).toMap

  "Compute profit" should {
    "More profitable with more frequency flight (max LF)" in {
      val distance = 200
      val airplane = lightAirplane
      val duration = Computation.calculateDuration(airplane.model, distance)
      val price = Pricing.computeStandardPrice(distance, FlightCategory.INTERNATIONAL, ECONOMY, PassengerType.TRAVELER, Airport.HIGH_INCOME / 2)

      var frequency = Computation.calculateMaxFrequency(airplane.model, distance)
      var capacity = frequency * airplane.model.capacity

      var link = Link(fromAirport, toAirport, testAirline1, LinkClassValues.getInstanceByMap(Map(ECONOMY -> price)), distance, LinkClassValues.getInstanceByMap(Map(ECONOMY -> capacity)), rawQuality = 0, duration, frequency)
      link.addSoldSeats(LinkClassValues.getInstanceByMap(Map(ECONOMY -> capacity)))
      link.setTestingAssignedAirplanes(Map(airplane -> frequency))

      val consumptionResultHighFrequency = LinkSimulation.computeFlightLinkConsumptionDetail(link , 0)

      frequency = 1
      capacity = frequency * airplane.model.capacity
      link = link.copy(capacity = LinkClassValues.getInstanceByMap(Map(ECONOMY -> capacity)), frequency = frequency)
      link.addSoldSeats(LinkClassValues.getInstanceByMap(Map(ECONOMY -> capacity)))
      link.setTestingAssignedAirplanes(Map(airplane -> frequency))
      val consumptionResultLowFrequency = LinkSimulation.computeFlightLinkConsumptionDetail(link , 0)

      consumptionResultHighFrequency.profit should be > consumptionResultLowFrequency.profit
    }

    "Not at all profitable at very low LF (0.1 LF) even at suitable range" in {
      var airplane = lightAirplane
      var consumptionResult = simulateStandard(200, airplane, FlightCategory.DOMESTIC, 0.1, 3)
      consumptionResult.profit should be < 0

      airplane = smallAirplane
      consumptionResult = simulateStandard(1000, airplane, FlightCategory.DOMESTIC, 0.1, 3)
      consumptionResult.profit should be < 0

      airplane = regionalAirplane
      consumptionResult = simulateStandard(4000, airplane, FlightCategory.DOMESTIC, 0.1, 4)
      consumptionResult.profit should be < 0

      airplane = mediumAirplane
      consumptionResult = simulateStandard(8000, airplane, FlightCategory.INTERNATIONAL, 0.1, 5)
      consumptionResult.profit should be < 0

      airplane = largeAirplane
      consumptionResult = simulateStandard(10000, airplane, FlightCategory.INTERNATIONAL, 0.1, 6)
      consumptionResult.profit should be < 0
    }

    "Some profit (but not good) at 0.6 LF at suitable range" in {
      val profits = ListBuffer[Long]()
      val profitMargins = ListBuffer[Double]()
      var airplane = lightAirplane
      var consumptionResult = simulateStandard(1000, airplane, FlightCategory.DOMESTIC, 0.6, airportSize = 3)
      consumptionResult.profit should be > 0
      verifyProfitMargin(consumptionResult, airplane.model, false)
      profits += consumptionResult.profit
      profitMargins += getProfitMargin(consumptionResult)

      airplane = smallAirplane
      consumptionResult = simulateStandard(1500, airplane, FlightCategory.DOMESTIC, 0.6, 4)
      consumptionResult.profit should be > 0
      verifyProfitMargin(consumptionResult, airplane.model, false)
      profits += consumptionResult.profit
      profitMargins += getProfitMargin(consumptionResult)

      airplane = regionalAirplane
      consumptionResult = simulateStandard(4000, airplane, FlightCategory.DOMESTIC, 0.6, 5)
      consumptionResult.profit should be > 0
      verifyProfitMargin(consumptionResult, airplane.model, false)
      profits += consumptionResult.profit
      profitMargins += getProfitMargin(consumptionResult)

      airplane = mediumAirplane
      consumptionResult = simulateStandard(8000, airplane, FlightCategory.DOMESTIC, 0.6, 6)
      consumptionResult.profit should be > 0
      verifyProfitMargin(consumptionResult, airplane.model, false)
      profits += consumptionResult.profit
      profitMargins += getProfitMargin(consumptionResult)

      airplane = largeAirplane
      consumptionResult = simulateStandard(13000, airplane, FlightCategory.DOMESTIC, 0.6, 7)
      consumptionResult.profit should be > 0
      verifyProfitMargin(consumptionResult, airplane.model, false)
      profits += consumptionResult.profit
      profitMargins += getProfitMargin(consumptionResult)

      verifyInDescendingOrder(profitMargins.toList)
    }

    "Good profit at MAX LF at suitable range" in {
      var airplane = lightAirplane
      var consumptionResult = simulateStandard(800, airplane, FlightCategory.DOMESTIC, 1, 3)
      consumptionResult.profit should be > 0
      verifyProfitMargin(consumptionResult, airplane.model, true)

      airplane = smallAirplane
      consumptionResult = simulateStandard(1000, airplane, FlightCategory.DOMESTIC, 1, 3)
      consumptionResult.profit should be > 0
      verifyProfitMargin(consumptionResult, airplane.model, true)

      airplane = regionalAirplane
      consumptionResult = simulateStandard(4000, airplane, FlightCategory.DOMESTIC, 1, 4)
      consumptionResult.profit should be > 0
      verifyProfitMargin(consumptionResult, airplane.model, true)

      airplane = mediumAirplane
      consumptionResult = simulateStandard(8000, airplane, FlightCategory.INTERNATIONAL, 1, 5)
      verifyProfitMargin(consumptionResult, airplane.model, true)

      airplane = largeAirplane
      consumptionResult = simulateStandard(10000, airplane, FlightCategory.INTERNATIONAL, 1, 6)
      verifyProfitMargin(consumptionResult, airplane.model, true)
    }

    "Not profitable at all on very short route < 200 km at large airport (max LF)" in {
      var airplane = mediumAirplane
      var consumptionResult = simulateStandard(200, airplane, FlightCategory.DOMESTIC, 1, 8)
      verifyProfitMargin(consumptionResult, airplane.model, false)

      airplane = largeAirplane
      consumptionResult = simulateStandard(200, airplane, FlightCategory.DOMESTIC, 1, 8)
      verifyProfitMargin(consumptionResult, airplane.model, false)
    }

    "Good profit with smaller jets on very short route < 200 km at small airport (max LF)" in {
      var airplane = lightAirplane
      var consumptionResult = simulateStandard(200, airplane, FlightCategory.DOMESTIC, 1, airportSize = 2)
      verifyProfitMargin(consumptionResult, airplane.model, true)

      airplane = smallAirplane
      consumptionResult = simulateStandard(200, airplane, FlightCategory.DOMESTIC, 1, airportSize = 2)
      verifyProfitMargin(consumptionResult, airplane.model, true)
    }

    "More profit with more jets on route if LF is full" in {
      val onePlaneResult = simulateStandard(200, lightModel, FlightCategory.DOMESTIC, 1, airplaneCount = 1)
      val fivePlaneResult = simulateStandard(200, lightModel, FlightCategory.DOMESTIC, 1, airplaneCount = 5)

      fivePlaneResult.profit should be >= (onePlaneResult.profit * 4.9).toInt // 4.9 as some truncation might make the number off a tiny bit
    }

    "More profit at higher link class at max LF (small plane)" in {
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

      val economyLink = Link(fromAirport, toAirport, testAirline1, LinkClassValues.getInstanceByMap(Map(ECONOMY -> economyPrice)), distance = distance, allEconomyCapacity, rawQuality = fromAirport.expectedQuality(distance, ECONOMY), duration, frequency)
      val businessLink = Link(fromAirport, toAirport, testAirline1, LinkClassValues.getInstanceByMap(Map(BUSINESS -> businessPrice)), distance = distance, allBusinessCapacity, rawQuality = fromAirport.expectedQuality(distance, BUSINESS), duration, frequency)
      val firstLink = Link(fromAirport, toAirport, testAirline1, LinkClassValues.getInstanceByMap(Map(FIRST -> firstPrice)), distance = distance, allFirstCapacity, rawQuality = fromAirport.expectedQuality(distance, FIRST), duration, frequency)

      economyLink.addSoldSeats(allEconomyCapacity)
      businessLink.addSoldSeats(allBusinessCapacity)
      firstLink.addSoldSeats(allFirstCapacity)

      economyLink.setTestingAssignedAirplanes(Map(airplane -> frequency))
      businessLink.setTestingAssignedAirplanes(Map(airplane -> frequency))
      firstLink.setTestingAssignedAirplanes(Map(airplane -> frequency))

      val economyResult = LinkSimulation.computeFlightLinkConsumptionDetail(economyLink , 0)
      val businessResult = LinkSimulation.computeFlightLinkConsumptionDetail(businessLink , 0)
      val firstResult = LinkSimulation.computeFlightLinkConsumptionDetail(firstLink , 0)

      economyResult.profit should be < businessResult.profit
      businessResult.profit should be < firstResult.profit

      (economyResult.profit.toDouble / economyResult.revenue.toDouble) should be > GOOD_PROFIT_MARGIN(airplane.model.airplaneType)
      (businessResult.profit.toDouble / businessResult.revenue.toDouble) should be > GOOD_PROFIT_MARGIN(airplane.model.airplaneType)
      (firstResult.profit.toDouble / firstResult.revenue.toDouble) should be > GOOD_PROFIT_MARGIN(airplane.model.airplaneType)

      (economyResult.profit.toDouble / economyResult.revenue.toDouble) should be < MAX_PROFIT_MARGIN(airplane.model.airplaneType)
      (businessResult.profit.toDouble / businessResult.revenue.toDouble) should be < MAX_PROFIT_MARGIN(airplane.model.airplaneType)
    }

    "More profit at higher link class at max LF (regional plane)" in {
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

      val economyLink = Link(fromAirport, toAirport, testAirline1, LinkClassValues.getInstanceByMap(Map(ECONOMY -> economyPrice)), distance = distance, allEconomyCapacity, rawQuality = fromAirport.expectedQuality(distance, ECONOMY), duration, frequency)
      val businessLink = Link(fromAirport, toAirport, testAirline1, LinkClassValues.getInstanceByMap(Map(BUSINESS -> businessPrice)), distance = distance, allBusinessCapacity, rawQuality = fromAirport.expectedQuality(distance, BUSINESS), duration, frequency)
      val firstLink = Link(fromAirport, toAirport, testAirline1, LinkClassValues.getInstanceByMap(Map(FIRST -> firstPrice)), distance = distance, allFirstCapacity, rawQuality = fromAirport.expectedQuality(distance, FIRST), duration, frequency)

      economyLink.addSoldSeats(allEconomyCapacity)
      businessLink.addSoldSeats(allBusinessCapacity)
      firstLink.addSoldSeats(allFirstCapacity)

      economyLink.setTestingAssignedAirplanes(Map(airplane -> frequency))
      businessLink.setTestingAssignedAirplanes(Map(airplane -> frequency))
      firstLink.setTestingAssignedAirplanes(Map(airplane -> frequency))

      val economyResult = LinkSimulation.computeFlightLinkConsumptionDetail(economyLink , 0)
      val businessResult = LinkSimulation.computeFlightLinkConsumptionDetail(businessLink , 0)
      val firstResult = LinkSimulation.computeFlightLinkConsumptionDetail(firstLink , 0)

      economyResult.profit should be < businessResult.profit
      businessResult.profit should be < firstResult.profit

      (economyResult.profit.toDouble / economyResult.revenue.toDouble) should be > GOOD_PROFIT_MARGIN(airplane.model.airplaneType)
      (businessResult.profit.toDouble / businessResult.revenue.toDouble) should be > GOOD_PROFIT_MARGIN(airplane.model.airplaneType)
      (firstResult.profit.toDouble / firstResult.revenue.toDouble) should be > GOOD_PROFIT_MARGIN(airplane.model.airplaneType)

      (economyResult.profit.toDouble / economyResult.revenue.toDouble) should be < MAX_PROFIT_MARGIN(airplane.model.airplaneType)
      (businessResult.profit.toDouble / businessResult.revenue.toDouble) should be < MAX_PROFIT_MARGIN(airplane.model.airplaneType)
    }

    "More profit at higher link class at max LF (large plane)" in {
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

      val economyLink = Link(fromAirport, toAirport, testAirline1, LinkClassValues.getInstanceByMap(Map(ECONOMY -> economyPrice.toInt)), distance = distance, allEconomyCapacity, rawQuality = fromAirport.expectedQuality(distance, ECONOMY), duration, frequency)
      val businessLink = Link(fromAirport, toAirport, testAirline1, LinkClassValues.getInstanceByMap(Map(BUSINESS -> businessPrice.toInt)), distance = distance, allBusinessCapacity, rawQuality = fromAirport.expectedQuality(distance, BUSINESS), duration, frequency)
      val firstLink = Link(fromAirport, toAirport, testAirline1, LinkClassValues.getInstanceByMap(Map(FIRST -> firstPrice.toInt)), distance = distance, allFirstCapacity, rawQuality = fromAirport.expectedQuality(distance, FIRST), duration, frequency)

      economyLink.addSoldSeats(allEconomyCapacity)
      businessLink.addSoldSeats(allBusinessCapacity)
      firstLink.addSoldSeats(allFirstCapacity)

      economyLink.setTestingAssignedAirplanes(Map(airplane -> frequency))
      businessLink.setTestingAssignedAirplanes(Map(airplane -> frequency))
      firstLink.setTestingAssignedAirplanes(Map(airplane -> frequency))

      val economyResult = LinkSimulation.computeFlightLinkConsumptionDetail(economyLink , 0)
      val businessResult = LinkSimulation.computeFlightLinkConsumptionDetail(businessLink , 0)
      val firstResult = LinkSimulation.computeFlightLinkConsumptionDetail(firstLink , 0)

      economyResult.profit should be < businessResult.profit
      businessResult.profit should be < firstResult.profit

      (economyResult.profit.toDouble / economyResult.revenue.toDouble) should be > GOOD_PROFIT_MARGIN(airplane.model.airplaneType)
      (businessResult.profit.toDouble / businessResult.revenue.toDouble) should be > GOOD_PROFIT_MARGIN(airplane.model.airplaneType)
      (firstResult.profit.toDouble / firstResult.revenue.toDouble) should be > GOOD_PROFIT_MARGIN(airplane.model.airplaneType)

      (economyResult.profit.toDouble / economyResult.revenue.toDouble) should be < MAX_PROFIT_MARGIN(airplane.model.airplaneType)
    }

    "reduce profit on delays" in {
      val distance = 8000
      val airplane = largeAirplane
      val maxFrequencyPerAirplane = Computation.calculateMaxFrequency(airplane.model, distance)
      val airplane2 = airplane.copy(id = 2)
      val airplane3 = airplane.copy(id = 3)
      var airplanes: Map[Airplane, Int] = Map(airplane -> maxFrequencyPerAirplane, airplane2 -> maxFrequencyPerAirplane, airplane3 -> maxFrequencyPerAirplane) // 3 airplanes
      val duration = Computation.calculateDuration(airplane.model, distance)
      val price = Pricing.computeStandardPrice(distance, FlightCategory.INTERNATIONAL, ECONOMY, PassengerType.TRAVELER, Airport.HIGH_INCOME / 2)

      val frequency = airplanes.values.sum
      val capacity = frequency * airplane.model.capacity

      var link = Link(fromAirport, toAirport, testAirline1, LinkClassValues.getInstanceByMap(Map(ECONOMY -> price)), distance, LinkClassValues.getInstanceByMap(Map(ECONOMY -> capacity)), rawQuality = fromAirport.expectedQuality(distance, ECONOMY), duration, frequency)
      println("delay link " + link)
      link.addSoldSeats(LinkClassValues.getInstanceByMap(Map(ECONOMY -> capacity)))
      link.setTestingAssignedAirplanes(airplanes)
      val consumptionResultNoDelays = LinkSimulation.computeFlightLinkConsumptionDetail(link , 0)

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
      link.majorDelayCount = frequency
      val consumptionResultAllMajorDelay = LinkSimulation.computeFlightLinkConsumptionDetail(link , 0)

      link = link.copy(capacity = LinkClassValues.getInstanceByMap(Map(ECONOMY -> capacity)), frequency = frequency)
      link.setTestingAssignedAirplanes(airplanes)
      link.cancellationCount = frequency
      val consumptionResultAllCancelled = LinkSimulation.computeFlightLinkConsumptionDetail(link , 0)

      consumptionResultNoDelays.profit should be > 0

      // more severe the incident, the less profit
      consumptionResultNoDelays.profit should be > consumptionResultSingleMajorDelay.profit
      consumptionResultSingleMajorDelay.profit should be > consumptionResultSingleCancellation.profit

      // at half the incident, it should not be profitable in more severe cases
      consumptionResultHalfCancellation.profit should be < 0

      // at full incident, it should not be profitable in any cases
      consumptionResultAllCancelled.profit should be < 0
    }

    "More profitable to have low quality flight if quality requirement is low" in {
      val airplane = largeAirplane
      val airplaneModel = airplane.model
      val distance = 5000
      val duration = Computation.calculateDuration(airplaneModel, distance)
      val frequency = Computation.calculateMaxFrequency(airplaneModel, distance)
      val maxEconomyCapacity = (airplaneModel.capacity / ECONOMY.spaceMultiplier).toInt * frequency
      val allEconomyCapacity : LinkClassValues = LinkClassValues.getInstance(maxEconomyCapacity, 0, 0)

      val economyPrice = Pricing.computeStandardPrice(distance, FlightCategory.INTERNATIONAL, ECONOMY, PassengerType.TRAVELER, Airport.HIGH_INCOME / 2)

      val economyLink1 = Link(fromAirport, toAirport, testAirline1, LinkClassValues.getInstanceByMap(Map(ECONOMY -> (economyPrice * 1.1).toInt)), distance = distance, allEconomyCapacity, rawQuality = 100, duration, frequency)
      val economyLink2 = Link(fromAirport, toAirport, testAirline1, LinkClassValues.getInstanceByMap(Map(ECONOMY -> economyPrice)), distance = distance, allEconomyCapacity, rawQuality = 20, duration, frequency)

      economyLink1.addSoldSeats(allEconomyCapacity)
      economyLink2.addSoldSeats(allEconomyCapacity)

      economyLink1.setTestingAssignedAirplanes(Map(airplane -> frequency))
      economyLink2.setTestingAssignedAirplanes(Map(airplane -> frequency))

      val economyResult1 = LinkSimulation.computeFlightLinkConsumptionDetail(economyLink1 , 0)
      val economyResult2 = LinkSimulation.computeFlightLinkConsumptionDetail(economyLink2 , 0)

      economyResult1.profit should be < economyResult2.profit
    }

    "Reasonable profit margin for each raw service level (domestic)" in {
      val airplane = largeAirplane
      val airplaneModel = airplane.model
      val distance = 2000
      val duration = Computation.calculateDuration(airplaneModel, distance)
      val frequency = Computation.calculateMaxFrequency(airplaneModel, distance)
      val maxEconomyCapacity = (airplaneModel.capacity / ECONOMY.spaceMultiplier).toInt * frequency
      val allEconomyCapacity : LinkClassValues = LinkClassValues.getInstance(maxEconomyCapacity, 0, 0)

      val economyPrice = Pricing.computeStandardPrice(distance, FlightCategory.DOMESTIC, ECONOMY, PassengerType.TRAVELER, Airport.HIGH_INCOME / 2)

      val economyLink1 = Link(fromAirport, toAirport, testAirline1, LinkClassValues.getInstanceByMap(Map(ECONOMY -> (economyPrice * 1.1).toInt)), distance = distance, allEconomyCapacity, rawQuality = 20, duration, frequency)
      val economyLink2 = Link(fromAirport, toAirport, testAirline1, LinkClassValues.getInstanceByMap(Map(ECONOMY -> economyPrice)), distance = distance, allEconomyCapacity, rawQuality = 40, duration, frequency)
      val economyLink3 = Link(fromAirport, toAirport, testAirline1, LinkClassValues.getInstanceByMap(Map(ECONOMY -> economyPrice)), distance = distance, allEconomyCapacity, rawQuality = 60, duration, frequency)
      val economyLink4 = Link(fromAirport, toAirport, testAirline1, LinkClassValues.getInstanceByMap(Map(ECONOMY -> economyPrice)), distance = distance, allEconomyCapacity, rawQuality = 80, duration, frequency)
      val economyLink5 = Link(fromAirport, toAirport, testAirline1, LinkClassValues.getInstanceByMap(Map(ECONOMY -> economyPrice)), distance = distance, allEconomyCapacity, rawQuality = 100, duration, frequency)

      economyLink1.addSoldSeats(allEconomyCapacity)
      economyLink2.addSoldSeats(allEconomyCapacity)
      economyLink3.addSoldSeats(allEconomyCapacity)
      economyLink4.addSoldSeats(allEconomyCapacity)
      economyLink5.addSoldSeats(allEconomyCapacity)

      economyLink1.setTestingAssignedAirplanes(Map(airplane -> frequency))
      economyLink2.setTestingAssignedAirplanes(Map(airplane -> frequency))
      economyLink3.setTestingAssignedAirplanes(Map(airplane -> frequency))
      economyLink4.setTestingAssignedAirplanes(Map(airplane -> frequency))
      economyLink5.setTestingAssignedAirplanes(Map(airplane -> frequency))

      val economyResult1 = LinkSimulation.computeFlightLinkConsumptionDetail(economyLink1 , 0)
      val economyResult2 = LinkSimulation.computeFlightLinkConsumptionDetail(economyLink2 , 0)
      val economyResult3 = LinkSimulation.computeFlightLinkConsumptionDetail(economyLink3 , 0)
      val economyResult4 = LinkSimulation.computeFlightLinkConsumptionDetail(economyLink4 , 0)
      val economyResult5 = LinkSimulation.computeFlightLinkConsumptionDetail(economyLink5 , 0)

      val profitMargin1 = economyResult1.profit.toDouble / economyResult1.revenue.toDouble
      val profitMargin2 = economyResult2.profit.toDouble / economyResult2.revenue.toDouble
      val profitMargin3 = economyResult3.profit.toDouble / economyResult3.revenue.toDouble
      val profitMargin4 = economyResult4.profit.toDouble / economyResult4.revenue.toDouble
      val profitMargin5 = economyResult5.profit.toDouble / economyResult5.revenue.toDouble

      profitMargin1 should (be > 0.15 and be < 0.3)
      profitMargin2 should (be > 0.05 and be < 0.2)
      profitMargin3 should (be > 0.0 and be < 0.1)
      profitMargin4 should (be > -0.1 and be < 0.05)
      profitMargin5 should (be > -0.3 and be < 0.05)
    }

    "Reasonable profit margin for each raw service level (intercontinental)" in {
      val airplane = largeAirplane
      val airplaneModel = airplane.model
      val distance = 10000
      val duration = Computation.calculateDuration(airplaneModel, distance)
      val frequency = Computation.calculateMaxFrequency(airplaneModel, distance)
      val maxEconomyCapacity = (airplaneModel.capacity / ECONOMY.spaceMultiplier).toInt * frequency
      val allEconomyCapacity : LinkClassValues = LinkClassValues.getInstance(maxEconomyCapacity, 0, 0)

      val economyPrice = Pricing.computeStandardPrice(distance, FlightCategory.INTERNATIONAL, ECONOMY, PassengerType.TRAVELER, Airport.HIGH_INCOME / 2)

      val economyLink1 = Link(fromAirport, toAirport, testAirline1, LinkClassValues.getInstanceByMap(Map(ECONOMY -> economyPrice)), distance = distance, allEconomyCapacity, rawQuality = 20, duration, frequency)
      val economyLink2 = Link(fromAirport, toAirport, testAirline1, LinkClassValues.getInstanceByMap(Map(ECONOMY -> economyPrice)), distance = distance, allEconomyCapacity, rawQuality = 40, duration, frequency)
      val economyLink3 = Link(fromAirport, toAirport, testAirline1, LinkClassValues.getInstanceByMap(Map(ECONOMY -> economyPrice)), distance = distance, allEconomyCapacity, rawQuality = 60, duration, frequency)
      val economyLink4 = Link(fromAirport, toAirport, testAirline1, LinkClassValues.getInstanceByMap(Map(ECONOMY -> economyPrice)), distance = distance, allEconomyCapacity, rawQuality = 80, duration, frequency)
      val economyLink5 = Link(fromAirport, toAirport, testAirline1, LinkClassValues.getInstanceByMap(Map(ECONOMY -> economyPrice)), distance = distance, allEconomyCapacity, rawQuality = 100, duration, frequency)

      economyLink1.addSoldSeats(allEconomyCapacity)
      economyLink2.addSoldSeats(allEconomyCapacity)
      economyLink3.addSoldSeats(allEconomyCapacity)
      economyLink4.addSoldSeats(allEconomyCapacity)
      economyLink5.addSoldSeats(allEconomyCapacity)

      economyLink1.setTestingAssignedAirplanes(Map(airplane -> frequency))
      economyLink2.setTestingAssignedAirplanes(Map(airplane -> frequency))
      economyLink3.setTestingAssignedAirplanes(Map(airplane -> frequency))
      economyLink4.setTestingAssignedAirplanes(Map(airplane -> frequency))
      economyLink5.setTestingAssignedAirplanes(Map(airplane -> frequency))

      val economyResult1 = LinkSimulation.computeFlightLinkConsumptionDetail(economyLink1 , 0)
      val economyResult2 = LinkSimulation.computeFlightLinkConsumptionDetail(economyLink2 , 0)
      val economyResult3 = LinkSimulation.computeFlightLinkConsumptionDetail(economyLink3 , 0)
      val economyResult4 = LinkSimulation.computeFlightLinkConsumptionDetail(economyLink4 , 0)
      val economyResult5 = LinkSimulation.computeFlightLinkConsumptionDetail(economyLink5 , 0)

      val profitMargin1 = economyResult1.profit.toDouble / economyResult1.revenue.toDouble
      val profitMargin2 = economyResult2.profit.toDouble / economyResult2.revenue.toDouble
      val profitMargin3 = economyResult3.profit.toDouble / economyResult3.revenue.toDouble
      val profitMargin4 = economyResult4.profit.toDouble / economyResult4.revenue.toDouble
      val profitMargin5 = economyResult5.profit.toDouble / economyResult5.revenue.toDouble

      profitMargin1 should (be > 0.3 and be < 0.4)
      profitMargin2 should (be > 0.15 and be < 0.3)
      profitMargin3 should (be > 0.05 and be < 0.2)
      profitMargin4 should (be > -0.1 and be < 0.1)
      profitMargin5 should (be > -0.2 and be < 0.0)
    }
  }

  "Simulate link errors" should {
    "Follow the expected rates at Low risk tier (scaledRisk = 1.0)" in {
      val airplane = Airplane(lightModel, testAirline1, 0, purchasedCycle = 0, 80, lightModel.price)
      val frequency = 10000
      val distance = 500
      val duration = Computation.calculateDuration(airplane.model, distance)
      val capacity = frequency * airplane.model.capacity

      val price = Pricing.computeStandardPrice(distance, FlightCategory.DOMESTIC, ECONOMY, PassengerType.TRAVELER, Airport.HIGH_INCOME / 2)
      val link = Link(fromAirport, toAirport, testAirline1, LinkClassValues.getInstanceByMap(Map(ECONOMY -> price)), distance, LinkClassValues.getInstanceByMap(Map(ECONOMY -> capacity)), rawQuality = 0, duration, frequency)
      link.setTestingAssignedAirplanes(Map(airplane -> frequency))

      val airportStats = scala.collection.immutable.Map(
        fromAirport.id -> AirportStatistics(fromAirport.id, 0, 0, 0.5, 0, 0),
        toAirport.id -> AirportStatistics(toAirport.id, 0, 0, 0.5, 0, 0)
      )
      val customWeather = scala.collection.immutable.Map(fromAirport.id -> 1.0, toAirport.id -> 1.0)

      LinkSimulation.simulateLinkError(List(link), airportStats, customWeather)

      val minorRate = link.minorDelayCount.toDouble / frequency
      val majorRate = link.majorDelayCount.toDouble / frequency
      val cancellationRate = link.cancellationCount.toDouble / frequency

      minorRate should be (0.029 +- 0.01)
      majorRate should be (0.0 +- 0.01)
      cancellationRate should be (0.0 +- 0.01)

      val expectedOnTime = 1 - (0.029 * Link.DELAY_MINOR_ONTIME)
      link.getDelayRatio should be (expectedOnTime +- 0.02)
    }

    "Follow the expected rates at Moderate risk tier (scaledRisk = 1.0)" in {
      val airplane = Airplane(lightModel, testAirline1, 0, purchasedCycle = 0, 45, lightModel.price)
      val frequency = 10000
      val distance = 500
      val duration = Computation.calculateDuration(airplane.model, distance)
      val capacity = frequency * airplane.model.capacity

      val price = Pricing.computeStandardPrice(distance, FlightCategory.DOMESTIC, ECONOMY, PassengerType.TRAVELER, Airport.HIGH_INCOME / 2)
      val link = Link(fromAirport, toAirport, testAirline1, LinkClassValues.getInstanceByMap(Map(ECONOMY -> price)), distance, LinkClassValues.getInstanceByMap(Map(ECONOMY -> capacity)), rawQuality = 0, duration, frequency)
      link.setTestingAssignedAirplanes(Map(airplane -> frequency))

      val airportStats = scala.collection.immutable.Map(
        fromAirport.id -> AirportStatistics(fromAirport.id, 0, 0, 0.6, 0, 0),
        toAirport.id -> AirportStatistics(toAirport.id, 0, 0, 0.6, 0, 0)
      )
      val customWeather = scala.collection.immutable.Map(fromAirport.id -> 1.0, toAirport.id -> 1.0)

      LinkSimulation.simulateLinkError(List(link), airportStats, customWeather)

      val minorRate = link.minorDelayCount.toDouble / frequency
      val majorRate = link.majorDelayCount.toDouble / frequency
      val cancellationRate = link.cancellationCount.toDouble / frequency

      minorRate should be (0.22 +- 0.02)
      majorRate should be (0.07 +- 0.02)
      cancellationRate should be (0.01 +- 0.01)

      link.getDelayRatio should be (0.89 +- 0.02)
    }

    "Follow the expected rates at High risk tier (scaledRisk = 1.0)" in {
      val airplane = Airplane(lightModel, testAirline1, 0, purchasedCycle = 0, 15, lightModel.price)
      val frequency = 10000
      val distance = 500
      val duration = Computation.calculateDuration(airplane.model, distance)
      val capacity = frequency * airplane.model.capacity

      val price = Pricing.computeStandardPrice(distance, FlightCategory.DOMESTIC, ECONOMY, PassengerType.TRAVELER, Airport.HIGH_INCOME / 2)
      val link = Link(fromAirport, toAirport, testAirline1, LinkClassValues.getInstanceByMap(Map(ECONOMY -> price)), distance, LinkClassValues.getInstanceByMap(Map(ECONOMY -> capacity)), rawQuality = 0, duration, frequency)
      link.setTestingAssignedAirplanes(Map(airplane -> frequency))

      val airportStats = scala.collection.immutable.Map(
        fromAirport.id -> AirportStatistics(fromAirport.id, 0, 0, 0.6, 0, 0),
        toAirport.id -> AirportStatistics(toAirport.id, 0, 0, 0.6, 0, 0)
      )
      val customWeather = scala.collection.immutable.Map(fromAirport.id -> 1.0, toAirport.id -> 1.0)

      LinkSimulation.simulateLinkError(List(link), airportStats, customWeather)

      val minorRate = link.minorDelayCount.toDouble / frequency
      val majorRate = link.majorDelayCount.toDouble / frequency
      val cancellationRate = link.cancellationCount.toDouble / frequency

      minorRate should be (0.666 +- 0.05)
      majorRate should be (0.182 +- 0.05)
      cancellationRate should be (0.052 +- 0.02)

      val expectedOnTime = 1 - (0.666 * 0.2 + 0.182 * 0.8 + 0.052 * 1.0)
      link.getDelayRatio should be (expectedOnTime +- 0.05)
    }
  }

  def getProfitMargin(consumptionResult : LinkConsumptionDetails): Double = consumptionResult.profit.toDouble / consumptionResult.revenue.toDouble

  def verifyProfitMargin(consumptionResult : LinkConsumptionDetails, model : Model, expectGoodReturn : Boolean): Unit = {
    val profitMargin = getProfitMargin(consumptionResult)
    val lf = (consumptionResult.link.getTotalSoldSeats.toDouble / consumptionResult.link.getTotalCapacity * 100).toInt
    println(f"${model.name}%-25s | LF: $lf%3d%% | Profit: ${consumptionResult.profit}%10d | Margin: ${profitMargin * 100}%6.2f%%")
    if (expectGoodReturn) {
      profitMargin should (be >= GOOD_PROFIT_MARGIN(model.airplaneType) and be <= MAX_PROFIT_MARGIN(model.airplaneType))
    } else {
      profitMargin should be < GOOD_PROFIT_MARGIN(model.airplaneType)
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
      case (index, foldList) => {
        val airplane = Airplane(airplaneModel, testAirline1, 0, purchasedCycle = 0, 100, airplaneModel.price, id = index)
        foldList + ((airplane, maxFrequencyPerAirplane))
      }
    })

    val consumptionResult = LinkSimulation.computeFlightLinkConsumptionDetail(link , 0)
    consumptionResult
  }

  def verifyInAscendingOrder(numbers : List[Long]): Unit = {
    numbers.sorted shouldBe numbers
  }

  def verifyInDescendingOrder(numbers : List[Double]): Unit = {
    numbers.sorted(Ordering[Double].reverse) shouldBe numbers
  }
}