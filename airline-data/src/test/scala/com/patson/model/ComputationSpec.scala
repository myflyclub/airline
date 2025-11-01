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
import com.patson.model.airplane.Model
 
class ComputationSpec(_system: ActorSystem) extends TestKit(_system) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll {
 
  def this() = this(ActorSystem("MySpec"))
 
  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }
 
  val smallAirplaneModel = Model.modelByName("Cessna 208 Caravan")
  val mediumAirplaneModel = Model.modelByName("Airbus A320")
  val largeAirplaneModel = Model.modelByName("Boeing 787-8 Dreamliner")
  val extraLargeAirplaneModel = Model.modelByName("Boeing 777-300ER")

  "computePassengerSatisfaction".must {
    "increase satisfaction with lower load factor".in {
      val cost = 80.0
      val standardPrice = 100
      val delayPercent = 0.0

      println("crowded test")

      val highCrowdSatisfaction = Computation.computePassengerSatisfaction(cost, standardPrice, crowdedPercent = 1.0, delayPercent)
      val mediumCrowdSatisfaction = Computation.computePassengerSatisfaction(cost, standardPrice, crowdedPercent = 0.6, delayPercent)
      val lowCrowdSatisfaction = Computation.computePassengerSatisfaction(cost, standardPrice, crowdedPercent = 0.2, delayPercent)

      println(s"$highCrowdSatisfaction, $mediumCrowdSatisfaction, $lowCrowdSatisfaction")
      lowCrowdSatisfaction.shouldBe( > (mediumCrowdSatisfaction))
      mediumCrowdSatisfaction.shouldBe( > (highCrowdSatisfaction))
    }
    "increase satisfaction with fewer delays".in {
      val cost = 80.0
      val standardPrice = 100
      val crowdedPercent = 0.0

      println("delays test")

      val highDelaySatisfaction = Computation.computePassengerSatisfaction(cost, standardPrice, crowdedPercent, delayPercent = 0.8)
      val mediumDelaySatisfaction = Computation.computePassengerSatisfaction(cost, standardPrice, crowdedPercent, delayPercent = 0.4)
      val noDelaySatisfaction = Computation.computePassengerSatisfaction(cost, standardPrice, crowdedPercent, delayPercent = 0.0)

      println(s"$highDelaySatisfaction, $mediumDelaySatisfaction, $noDelaySatisfaction")
      noDelaySatisfaction.shouldBe( > (mediumDelaySatisfaction))
      mediumDelaySatisfaction.shouldBe( > (highDelaySatisfaction))
    }
    "confirm both low load factor and no delays yield highest satisfaction".in {
      val cost = 70.0
      val standardPrice = 100

      val bestScenario = Computation.computePassengerSatisfaction(cost, standardPrice, crowdedPercent = 0.0, delayPercent = 0.0)
      val worstScenario = Computation.computePassengerSatisfaction(cost, standardPrice, crowdedPercent = 1.0, delayPercent = 1.0)

      bestScenario.shouldBe( > (worstScenario))
    }
  }

  "calculateDuration".must {
    "output duration by distance".in {
      val distances = List(50, 100, 250, 500, 1000, 2000, 4000, 8000)
      var lastDuration = 0
      for (distance <- distances) {
        val duration = Computation.computeStandardFlightDuration(distance)
        println(s"$distance km: $duration")
        lastDuration.shouldBe( < (duration))
        lastDuration = duration
      }
    }
    "returns longer duration for slower speed craft".in {
      val smallAirplaneDuration = Computation.calculateDuration(smallAirplaneModel, 500)
      val largeAirplaneDuration = Computation.calculateDuration(largeAirplaneModel, 500)
      smallAirplaneDuration.shouldBe( > (largeAirplaneDuration))
    }
    "slower average speed for shorter distance (due to takeoff)".in {
      val shortAverageSpeed = 500 / Computation.calculateDuration(smallAirplaneModel, 500)
      val longAverageSpeed = 1000 / Computation.calculateDuration(largeAirplaneModel, 1000)
      longAverageSpeed.shouldBe( > (shortAverageSpeed))
    }
  }
  "calculateMaxFrequency".must {
    "returns higher frequency for smaller craft on very short route".in {
      val small = Computation.calculateMaxFrequency(smallAirplaneModel, 500)
      val medium = Computation.calculateMaxFrequency(mediumAirplaneModel, 500)
      val large = Computation.calculateMaxFrequency(largeAirplaneModel, 500)
      val extraLarge = Computation.calculateMaxFrequency(extraLargeAirplaneModel, 500)
      small.shouldBe( > (medium))
      medium.shouldBe( > (large))
      large.shouldBe( > (extraLarge))
    }
    "returns highest frequency for medium craft on short route".in {
      val small = Computation.calculateMaxFrequency(smallAirplaneModel, 2000)
      val medium = Computation.calculateMaxFrequency(mediumAirplaneModel, 2000)
      val large = Computation.calculateMaxFrequency(largeAirplaneModel, 2000)
      val extraLarge = Computation.calculateMaxFrequency(extraLargeAirplaneModel, 2000)
      medium.shouldBe( > (small))
      medium.shouldBe( > (large))
      large.shouldBe( > (extraLarge))
    }
    "returns highest frequency for large+ craft on long+ route".in {
      val small = Computation.calculateMaxFrequency(smallAirplaneModel, 8000)
      val medium = Computation.calculateMaxFrequency(mediumAirplaneModel, 8000)
      val large = Computation.calculateMaxFrequency(largeAirplaneModel, 8000)
      val extraLarge = Computation.calculateMaxFrequency(extraLargeAirplaneModel, 8000)
      small.shouldBe(0)
      medium.shouldBe(0)
      large.shouldBe( > (0))
      extraLarge.shouldBe( > (0))
      println(s"frequency: $large")
      println(s"frequency: $extraLarge")
    }
    
  }
  
  "calculateAffinityValue".must {
//    "output airport pairs with high affinity value".in {
//      import com.patson.data.{AirportSource,CountrySource}
//      val airports = AirportSource.loadAllAirports(false, false).toIndexedSeq
//      val countryRelationships = CountrySource.getCountryMutualRelationships()
//      for (i <- airports.indices; j <- i + 1 until airports.length) {
//        val a1 = airports(i)
//        val a2 = airports(j)
//        val relationship = countryRelationships.getOrElse((a1.countryCode, a2.countryCode), 0)
//        val affinity = Computation.calculateAffinityValue(a1.zone, a2.zone, relationship)
//        val distance = Computation.calculateDistance(a1, a2)
//        if (relationship >= 5 && affinity > 7) {
//          println(s"${a1.iata},${a1.countryCode},${a2.iata},${a2.countryCode},$affinity,domestic,$distance")
//        } else if (relationship < 5 && affinity > 4) {
//          println(s"${a1.iata},${a1.countryCode},${a2.iata},${a2.countryCode},$affinity,international,$distance")
//        }
//      }
//    }
    "ensure size 7+ airports have at least 3 trade affinities".in {
      import com.patson.data.AirportSource
      val airports = AirportSource.loadAllAirports(false, false)
      val largeAirports = airports.filter(_.size >= 7)
      
      val airportsWithInsufficientTradeAffinities = largeAirports.filter { airport =>
        val affinities = airport.zone.split("-")
        val tradeAffinities = affinities.filterNot(_.contains("|"))
        tradeAffinities.length < 3
      }
      
      if (airportsWithInsufficientTradeAffinities.nonEmpty) {
        println(s"\nAirports size 7+ with fewer than 3 trade affinities:")
        println(s"IATA, City, Size, Trade Affinity Count, Trade Affinities")
        airportsWithInsufficientTradeAffinities.sortBy(_.iata).foreach { airport =>
          val affinities = airport.zone.split("-").filter(_ != "None|")
          val tradeAffinities = affinities.filterNot(_.contains("|"))
          println(s"${airport.iata}, ${airport.size}, ${airport.name}, ${tradeAffinities.length}, ${tradeAffinities.mkString(", ")}")
        }
      }
      
      airportsWithInsufficientTradeAffinities.isEmpty shouldBe true
    }
  }
}
