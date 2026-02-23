package com.patson.model

import scala.collection.mutable.Map
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.testkit.ImplicitSender
import org.apache.pekko.testkit.TestKit
import com.patson.Util
import com.patson.data.AirportSource
 
class PricingSpec(_system: ActorSystem) extends TestKit(_system) with ImplicitSender
  with AnyWordSpecLike with Matchers with BeforeAndAfterAll {
 
  def this() = this(ActorSystem("MySpec"))
 
  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }
 
  "computeStandardPrice".must {
    "generate expected prices at the bucket (domestic)".in {
      val highIncomeAirport = AirportSource.loadAirportByIata("SFO").get
      Pricing.computeStandardPrice(400, FlightCategory.DOMESTIC, DISCOUNT_ECONOMY, PassengerType.TOURIST, 0).shouldBe(79)
      Pricing.computeStandardPrice(400, FlightCategory.DOMESTIC, ECONOMY, PassengerType.TOURIST, 0).shouldBe(95)
      Pricing.computeStandardPrice(400, FlightCategory.DOMESTIC, ECONOMY, PassengerType.TRAVELER, 0).shouldBe(99)
      Pricing.computeStandardPrice(400, FlightCategory.DOMESTIC, ECONOMY, PassengerType.BUSINESS, 0).shouldBe(109)
      Pricing.computeStandardPrice(400, FlightCategory.DOMESTIC, ECONOMY, PassengerType.BUSINESS, highIncomeAirport.baseIncome).shouldBe(124)
    }
    "compare pricing of two 500km routes vs one 1000km route".in {
      val twoShortRoutes = 2 * Pricing.computeStandardPrice(500, FlightCategory.DOMESTIC, ECONOMY, PassengerType.BUSINESS, 0)
      val oneLongRoute = Pricing.computeStandardPrice(1000, FlightCategory.DOMESTIC, ECONOMY, PassengerType.BUSINESS, 0)
      val priceDifference = twoShortRoutes - oneLongRoute
      
      println(s"Two 500km routes: $twoShortRoutes")
      println(s"One 1000km route: $oneLongRoute")
      println(s"Price difference: $priceDifference")

      assert(twoShortRoutes > oneLongRoute)
    }
  }
}
