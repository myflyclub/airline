package com.patson.model

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.testkit.{ImplicitSender, TestKit}
import com.patson.Util
import com.patson.model.airplane.{Airplane, AirplaneConfiguration, LinkAssignment, Model}
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.collection.immutable.Map

class LinkSpec(_system: ActorSystem) extends TestKit(_system) with ImplicitSender
  with AnyWordSpecLike with Matchers with BeforeAndAfterAll with BeforeAndAfterEach {
 
  def this() = this(ActorSystem("MySpec"))

  val testAirline1 = Airline("airline 1", id = 1)
  val fromAirport = Airport("", "", "From Airport", 0, 0, "", "", "", 1, baseIncome = 40000, basePopulation = 1, 0, 0)
  val toAirport = Airport("", "", "To Airport", 0, 180, "", "", "", 1, baseIncome = 40000, basePopulation = 1, 0, 0)
  val distance = Util.calculateDistance(fromAirport.latitude, fromAirport.longitude, toAirport.latitude, toAirport.longitude).toInt
  val flightType = Computation.getFlightCategory(fromAirport, toAirport)
  val defaultPrice = Pricing.computeStandardPriceForAllClass(distance, FlightCategory.INTERNATIONAL, PassengerType.TRAVELER, Airport.HIGH_INCOME / 2)
  val model = Model.modelByName("Boeing 737 MAX 9")




  "frequencyByClass".must {
    "compute correct frequency".in {

      val config1 = AirplaneConfiguration(100, 0, 0, testAirline1, model, false)
      val config2 = AirplaneConfiguration(50, 25, 0, testAirline1, model, false)
      val config3 = AirplaneConfiguration(50, 10, 5, testAirline1, model, false)
      val airline1Link = Link(fromAirport, toAirport, testAirline1, defaultPrice, distance = distance, LinkClassValues.getInstance(200, 35, 5) * 10, rawQuality = 0, 600, frequency = 30)

      airline1Link.setAssignedAirplanes(
        scala.collection.immutable.Map(
          Airplane(model, testAirline1, 0, purchasedCycle = 0, 100, 0, 0, configuration = config1) -> LinkAssignment(10, 6000)
        , Airplane(model, testAirline1, 0, purchasedCycle = 0, 100, 0, 0, configuration = config2) -> LinkAssignment(10, 6000)
        , Airplane(model, testAirline1, 0, purchasedCycle = 0, 100, 0, 0, configuration = config3) -> LinkAssignment(10, 6000)))

      assert(airline1Link.frequencyByClass(ECONOMY) == 30)
      assert(airline1Link.frequencyByClass(BUSINESS) == 20)
      assert(airline1Link.frequencyByClass(FIRST) == 10)
    }
  }

  "airport base upkeep cost for link".must {
    "calculate upkeep cost proportional to link staff for different airline types".in {
      val testAirport = Airport("TEST", "TEST", "Test Airport", 0, 0, "US", "Test City", "", 6, baseIncome = 50000, basePopulation = 5000000, 0, 0, id = 1)

      val legacyAirline = Airline("Legacy Test", id = 101)
      legacyAirline.airlineType = LegacyAirline

      val megaHqAirline = Airline("Mega HQ Test", id = 102)
      megaHqAirline.airlineType = MegaHqAirline

      val regionalAirline = Airline("Regional Test", id = 103)
      regionalAirline.airlineType = RegionalAirline

      val linkDistance = 1000
      val linkCapacity = LinkClassValues.getInstance(3000, 0, 0)
      val linkFrequency = 30
      val baseScale = 6
      val flightCategory = FlightCategory.INTERNATIONAL

      val legacyBase = AirlineBase(legacyAirline, testAirport, "US", baseScale, 0, headquarter = true)
      val megaHqBase = AirlineBase(megaHqAirline, testAirport, "US", baseScale, 0, headquarter = true)
      val regionalBase = AirlineBase(regionalAirline, testAirport, "US", baseScale, 0, headquarter = true)

      val staffSchemeBreakdownLegacy = Link.getStaffRequired(linkDistance, flightCategory, LegacyAirline)
      val legacyLinkStaffRequired = Math.max(3, (staffSchemeBreakdownLegacy.basicStaff + staffSchemeBreakdownLegacy.perFrequency * linkFrequency + staffSchemeBreakdownLegacy.per500Pax * linkCapacity.total / 500))
      val staffSchemeBreakdownMegaHQ = Link.getStaffRequired(linkDistance, flightCategory, MegaHqAirline)
      val megaHQLinkStaffRequired = Math.max(3, (staffSchemeBreakdownMegaHQ.basicStaff + staffSchemeBreakdownMegaHQ.perFrequency * linkFrequency + staffSchemeBreakdownMegaHQ.per500Pax * linkCapacity.total / 500))
      val staffSchemeBreakdownRegional = Link.getStaffRequired(linkDistance, flightCategory, RegionalAirline)
      val regionalLinkStaffRequired = Math.max(3, (staffSchemeBreakdownRegional.basicStaff + staffSchemeBreakdownRegional.perFrequency * linkFrequency + staffSchemeBreakdownRegional.per500Pax * linkCapacity.total / 500))

      val baseStaffCapacity = AirlineBase.getOfficeStaffCapacity(baseScale, isHeadquarters = true)

      val legacyUpkeep = legacyBase.calculateUpkeep(baseScale, LegacyAirline)
      val megaHqUpkeep = megaHqBase.calculateUpkeep(baseScale, MegaHqAirline)
      val regionalUpkeep = regionalBase.calculateUpkeep(baseScale, RegionalAirline)

      val legacyUpkeepForLink = (legacyUpkeep * legacyLinkStaffRequired / baseStaffCapacity).toLong
      val megaHqUpkeepForLink = (megaHqUpkeep * megaHQLinkStaffRequired / baseStaffCapacity).toLong
      val regionalUpkeepForLink = (regionalUpkeep * regionalLinkStaffRequired / baseStaffCapacity).toLong

      println(s"Legacy Base Upkeep: $legacyUpkeep, Link Upkeep: $legacyUpkeepForLink, staff: $legacyLinkStaffRequired")
      println(s"Mega HQ Base Upkeep: $megaHqUpkeep, Link Upkeep: $megaHqUpkeepForLink, staff: $megaHQLinkStaffRequired")
      println(s"Regional Base Upkeep: $regionalUpkeep, Link Upkeep: $regionalUpkeepForLink, staff: $regionalLinkStaffRequired")

      assert(legacyUpkeepForLink > 0)
      assert(megaHqUpkeepForLink > 0)
      assert(regionalUpkeepForLink > 0)
    }
  }
}
