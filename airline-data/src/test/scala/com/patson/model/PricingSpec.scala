package com.patson.model

import scala.collection.mutable.Map
import org.scalatest.BeforeAndAfterAll
import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.WordSpecLike
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.testkit.ImplicitSender
import org.apache.pekko.testkit.TestKit
import com.patson.Util
import com.patson.data.AirportSource
 
class PricingSpec(_system: ActorSystem) extends TestKit(_system) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll {
 
  def this() = this(ActorSystem("MySpec"))
 
  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }
 
  //200 km = 150
  //1000 km = 150 + 100 = 250  (800 * 0.125) 
  //2000 km = 250 + 100 = 350  (1000 * 0.1)
  //10000 km = 350 + 400 = 750 (8000 * 0.05)
  "computeStandardPrice".must {
    "generate expected prices at the bucket (domestic)".in {
      val highIncomeAirport = AirportSource.loadAirportByIata("SFO").get
      Pricing.computeStandardPrice(400, FlightCategory.DOMESTIC, DISCOUNT_ECONOMY, PassengerType.TOURIST, 0).shouldBe(67)
      Pricing.computeStandardPrice(400, FlightCategory.DOMESTIC, ECONOMY, PassengerType.TOURIST, 0).shouldBe(79)
      Pricing.computeStandardPrice(400, FlightCategory.DOMESTIC, ECONOMY, PassengerType.TRAVELER, 0).shouldBe(83)
      Pricing.computeStandardPrice(400, FlightCategory.DOMESTIC, ECONOMY, PassengerType.BUSINESS, 0).shouldBe(92)
      println(s"${highIncomeAirport.iata}: ${highIncomeAirport.baseIncome}")
      Pricing.computeStandardPrice(400, FlightCategory.DOMESTIC, ECONOMY, PassengerType.BUSINESS, highIncomeAirport.baseIncome).shouldBe(107)
//      Pricing.computeStandardPrice(400, FlightCategory.DOMESTIC, ECONOMY).shouldBe(91)
//      Pricing.computeStandardPrice(400, FlightCategory.DOMESTIC, BUSINESS).shouldBe(170)
//      Pricing.computeStandardPrice(400, FlightCategory.DOMESTIC, FIRST).shouldBe(518)
//      Pricing.computeStandardPrice(1200, FlightCategory.DOMESTIC, DISCOUNT_ECONOMY).shouldBe(130)
//      Pricing.computeStandardPrice(10000, FlightCategory.DOMESTIC, ECONOMY).shouldBe(750)
//      Pricing.computeStandardPrice(14000, FlightCategory.DOMESTIC, ECONOMY).shouldBe(950)
    }
    "generate expected prices at the bucket (international)".in {
//      Pricing.computeStandardPrice(200, FlightCategory.INTERNATIONAL, ECONOMY).shouldBe((150 * Pricing.INTERNATIONAL_PRICE_MULTIPLIER).toInt)
//      Pricing.computeStandardPrice(1000, FlightCategory.INTERNATIONAL, ECONOMY).shouldBe((250 * Pricing.INTERNATIONAL_PRICE_MULTIPLIER).toInt)
//      Pricing.computeStandardPrice(1500, FlightCategory.INTERNATIONAL, ECONOMY).shouldBe((300 * Pricing.INTERNATIONAL_PRICE_MULTIPLIER).toInt)
//      Pricing.computeStandardPrice(1500, FlightCategory.INTERNATIONAL, ECONOMY).shouldBe((300 * Pricing.INTERNATIONAL_PRICE_MULTIPLIER).toInt)
//      Pricing.computeStandardPrice(2000, FlightCategory.INTERNATIONAL, ECONOMY).shouldBe((350 * Pricing.INTERNATIONAL_PRICE_MULTIPLIER).toInt)
//      Pricing.computeStandardPrice(6000, FlightCategory.INTERNATIONAL, ECONOMY).shouldBe((550 * Pricing.INTERNATIONAL_PRICE_MULTIPLIER).toInt)
//      Pricing.computeStandardPrice(6000, FlightCategory.INTERNATIONAL, ECONOMY).shouldBe((550 * Pricing.INTERNATIONAL_PRICE_MULTIPLIER).toInt)
//      Pricing.computeStandardPrice(10000, FlightCategory.INTERNATIONAL, ECONOMY).shouldBe((750 * Pricing.INTERNATIONAL_PRICE_MULTIPLIER).toInt)
//      Pricing.computeStandardPrice(14000, FlightCategory.INTERNATIONAL, ECONOMY).shouldBe((950 * Pricing.INTERNATIONAL_PRICE_MULTIPLIER).toInt)
    }
  }
}
