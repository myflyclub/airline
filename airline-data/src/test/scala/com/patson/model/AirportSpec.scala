package com.patson.model

import com.patson.DemandGenerator
import com.patson.DemandGenerator.MIN_DISTANCE
import com.patson.data.{AirportSource, AirportStatisticsSource, GameConstants}

import scala.collection.immutable.Map
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.matchers.should.Matchers
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.testkit.ImplicitSender
import org.apache.pekko.testkit.TestKit
import com.patson.model.airplane.Airplane
import com.patson.model.airplane.Model

class AirportSpec(_system: ActorSystem) extends TestKit(_system) with ImplicitSender
  with AnyWordSpecLike with Matchers with BeforeAndAfterAll with BeforeAndAfterEach {

  def this() = this(ActorSystem("MySpec"))

  val airport : Airport = Airport("A", "", "Airport A", 0, 0, countryCode = "A", "", "", 1, 0, basePopulation = 100)
  airport.country = Some(Country(countryCode = "A", name = "Country A", airportPopulation = 1000000, income = 500000, openness = 10, gini = 0.5))
  val otherAirport : Airport = Airport("B", "", "Airport B", 0, 0, countryCode = "B", "", "", 1, 0, basePopulation = 100)
  otherAirport.country = Some(Country(countryCode = "B", name = "Country B", airportPopulation = 1000000, income = 500000, openness = 3, gini = 0.5))

  val highReputationLocalHqAirline = Airline("airline 1", id = 1)
  highReputationLocalHqAirline.setReputation(125)
  highReputationLocalHqAirline.setCountryCode("A")
  val base1 = AirlineBase(highReputationLocalHqAirline, airport, countryCode = "A", scale = 1, foundedCycle = 1, headquarter = true)
  highReputationLocalHqAirline.setBases(List[AirlineBase](base1))
  val lowReputationLocalHqAirline = Airline("airline 2", id = 2)
  lowReputationLocalHqAirline.setReputation(0)
  val base2 = AirlineBase(lowReputationLocalHqAirline, airport, countryCode = "A", scale = 1, foundedCycle = 1, headquarter = true)
  lowReputationLocalHqAirline.setBases(List[AirlineBase](base2))
  lowReputationLocalHqAirline.setCountryCode("A")

  val highReputationForeignHqAirline = Airline("high rep foreign", id = 3)
  highReputationForeignHqAirline.setReputation(100)
  val base3 = AirlineBase(highReputationForeignHqAirline, otherAirport, countryCode = "B", scale = 1, foundedCycle = 1, headquarter = true)
  highReputationForeignHqAirline.setBases(List[AirlineBase](base3))
  highReputationForeignHqAirline.setCountryCode("B")
  val lowReputationForeignHqAirline = Airline("low rep foreign", id = 4)
  lowReputationForeignHqAirline.setReputation(0)
  val base4 = AirlineBase(lowReputationForeignHqAirline, otherAirport, countryCode = "B", scale = 1, foundedCycle = 1, headquarter = true)
  lowReputationForeignHqAirline.setBases(List[AirlineBase](base4))
  lowReputationForeignHqAirline.setCountryCode("B")

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    airport.initAirlineBases(List(base1, base2))
    airport.initAirlineAppeals(Map())
    otherAirport.initAirlineBases(List(base3, base4))
    otherAirport.initAirlineAppeals(Map())
  }

  override protected def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
    super.afterAll()
  }

  "computeLoyaltyByLoyalist" should {
    "give no loyalty if loyalist is empty" in {
      val airportClone = this.airport.copy()
      airportClone.computeLoyaltyByLoyalist(List.empty) shouldBe empty
    }

    "give 0 loyalty if loyalist is 0" in {
      val airportClone = this.airport.copy()
      airportClone.computeLoyaltyByLoyalist(List(Loyalist(airportClone, Airline.fromId(1), 0)))(1) shouldBe 0.0
    }

    "give 100 loyalty if loyalist is same as pop" in {
      val airportClone = this.airport.copy()
      airportClone.computeLoyaltyByLoyalist(List(Loyalist(airportClone, Airline.fromId(1), airportClone.population.toInt)))(1) shouldBe 100.0
    }

    "give x loyalty if loyalist is 0.5 * pop, which 70 < x < 80" in {
      val airportClone = this.airport.copy()
      val loyalty = airportClone.computeLoyaltyByLoyalist(List(Loyalist(airportClone, Airline.fromId(1), airportClone.population.toInt / 2)))(1)
      loyalty should be > 70.0
      loyalty should be < 80.0
    }

    "give x loyalty if loyalist is 0.2 * pop, which 40 < x < 50" in {
      val airportClone = this.airport.copy()
      val loyalty = airportClone.computeLoyaltyByLoyalist(List(Loyalist(airportClone, Airline.fromId(1), airportClone.population.toInt / 5)))(1)
      loyalty should be > 40.0
      loyalty should be < 50.0
    }
  }

  "islandAirport" should {
    "be island if island country" in {
      val islandCountryAirport = AirportSource.loadAirportByIata("VLI", true).get
      val notIsland = AirportSource.loadAirportByIata("JFK", true).get
      GameConstants.connectsIsland(islandCountryAirport, notIsland) shouldBe true
      GameConstants.connectsIsland(notIsland, islandCountryAirport) shouldBe true
    }

    "be island if island airport" in {
      val islandAirport = AirportSource.loadAirportByIata("ESD", true).get
      val notIsland = AirportSource.loadAirportByIata("JFK", true).get
      GameConstants.connectsIsland(islandAirport, notIsland) shouldBe true
      GameConstants.connectsIsland(notIsland, islandAirport) shouldBe true
    }

    "test nearby island Airports" in {
      val islandAirport = AirportSource.loadAirportByIata("FRD", true).get
      val minDistance = if (GameConstants.isIsland(islandAirport.iata)) 50 else MIN_DISTANCE
      val airports = Computation.getAirportWithinRange(islandAirport, DemandGenerator.HUB_AIRPORTS_MAX_RADIUS, minDistance)
      println(airports.map(_.iata).mkString(", "))
      airports.exists(_.iata == "SEA") shouldBe true
    }
  }

  "Data output scripts" should {
    "output upkeep and upgrade cost for various base sizes and airports" in {
      val iatas = List("JFK", "ADD", "DXB", "MAD", "SCE", "PVG")
      val baseSizes = List(4, 8, 12, 16)
      val airline = Airline("TestAirline", id = 999)
      val airlineMHQ = Airline("TestMegaHQ", id = 1000, airlineType = MegaHqAirline)
      val airlineRegional = Airline("TestMegaHQ", id = 1001, airlineType = RegionalAirline)
      val airports = iatas.flatMap(iata => AirportSource.loadAirportByIata(iata, true))

      println(s"Airport, BaseSize, Upkeep, UpgradeCost")
      for (airport <- airports; baseSize <- baseSizes) {
        val base = AirlineBase(airline, airport, airport.countryCode, baseSize, foundedCycle = 0, headquarter = false)
        println(s"${airport.iata}, ${airport.rating.overallDifficulty}, $baseSize, ${base.getUpkeep}, ${base.getValue}")

        val baseMHQ = AirlineBase(airlineMHQ, airport, airport.countryCode, baseSize, foundedCycle = 0, headquarter = true)
        println(s"${airport.iata}, ${airport.rating.overallDifficulty}, $baseSize, ${baseMHQ.getUpkeep}, ${baseMHQ.getValue}")

        val baseAirlineRegional = AirlineBase(airlineRegional, airport, airport.countryCode, baseSize, foundedCycle = 0, headquarter = true)
        println(s"${airport.iata}, ${airport.rating.overallDifficulty}, $baseSize, ${baseAirlineRegional.getUpkeep}, ${baseAirlineRegional.getValue}")
      }
      succeed
    }

    "output all travel rates" in {
      val allAirports: List[Airport] = AirportSource.loadAllAirports().sortBy(_.popMiddleIncome)
      val airportStats = AirportStatisticsSource.loadAllAirportStats().groupBy(_.airportId)

      println(s"iata, size, travel rate, rep, from pax, from demand")
      allAirports.foreach { airport =>
        airportStats.get(airport.id) match {
          case Some(stat) =>
            val travelRate = Airport.travelRateAdjusted(stat.head.fromPax, stat.head.baselineDemand, airport.size)
            println(s"${airport.iata}, ${airport.size}, $travelRate, ${stat.head.reputation}, ${stat.head.fromPax}, ${stat.head.baselineDemand}")
          case None =>
            println(s"${airport.iata}, ${airport.size}, 0, 0, 0, 0")
        }
      }
      succeed
    }
  }
}