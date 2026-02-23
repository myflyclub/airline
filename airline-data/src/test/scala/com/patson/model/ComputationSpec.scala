package com.patson.model

import scala.collection.mutable.Map
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.testkit.ImplicitSender
import org.apache.pekko.testkit.TestKit
import com.patson.Util
import com.patson.model.airplane.Model
 
class ComputationSpec(_system: ActorSystem) extends TestKit(_system) with ImplicitSender
  with AnyWordSpecLike with Matchers with BeforeAndAfterAll {
 
  def this() = this(ActorSystem("MySpec"))
 
  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }
 
  val smallAirplaneModel = Model.modelByName("Cessna 208 Caravan")
  val mediumAirplaneModel = Model.modelByName("Airbus A320")
  val largeAirplaneModel = Model.modelByName("Boeing 787-8 Dreamliner")
  val extraLargeAirplaneModel = Model.modelByName("Boeing 777-300ER")

  // LINK_COST_TOLERANCE_FACTOR = 1.0
  // SATISFACTION_MAX_PRICE_RATIO_THRESHOLD = 0.7
  // Base satisfaction = (1.0 - ratio) / 0.3 where ratio = cost/standardPrice
  // At ratio 0.7: base = 1.0, at ratio 1.0: base = 0.0

  "computePassengerSatisfaction base calculation".must {
    "return 1.0 satisfaction at optimal price ratio (0.7) with neutral modifiers".in {
      val standardPrice = 100
      val cost = 70.0 // ratio = 0.7
      // At onTimeRatio = 0.8, modifier = 1.25 * 0.8 = 1.0 (neutral)
      val result = Computation.computePassengerSatisfaction(cost, standardPrice, crowdedPercent = 0.0, onTimeRatio = 0.8)
      // base satisfaction = (1.0 - 0.7) / 0.3 = 1.0
      // result = 1.0 * 1.0 - 0 = 1.0
      println(s"Optimal price (ratio 0.7), neutral on-time: $result")
      result shouldBe 1.0
    }
    "return 0 satisfaction at price ratio 1.0 (cost = standardPrice)".in {
      val standardPrice = 100
      val cost = 100.0 // ratio = 1.0
      val result = Computation.computePassengerSatisfaction(cost, standardPrice, crowdedPercent = 0.0, onTimeRatio = 1.0)
      // base satisfaction = (1.0 - 1.0) / 0.3 = 0.0
      println(s"At-cost price (ratio 1.0): $result")
      result shouldBe 0.0
    }
    "show base satisfaction calculation for various price ratios".in {
      val standardPrice = 100
      println("\nBase satisfaction by price ratio (full on-time, no crowding):")
      println("Ratio | Cost | Base Sat | Final (onTime=0.8 neutral)")
      for (ratio <- List(0.5, 0.6, 0.7, 0.8, 0.9, 0.95, 1.0)) {
        val cost = ratio * standardPrice
        val baseSat = (1.0 - ratio) / 0.3
        val result = Computation.computePassengerSatisfaction(cost, standardPrice, 0.0, 0.8)
        println(f"$ratio%.2f  | $cost%.0f  | $baseSat%.3f    | $result%.3f")
      }
      true shouldBe true
    }
  }

  "computePassengerSatisfaction onTimeRatio modifier".must {
    "return zero satisfaction when all flights cancelled (onTimeRatio=0)".in {
      val cost = 50.0
      val standardPrice = 100
      // onTimeRatio = 0 means all flights cancelled
      val result = Computation.computePassengerSatisfaction(cost, standardPrice, crowdedPercent = 0.0, onTimeRatio = 0.0)
      println(s"All cancelled (onTimeRatio=0) satisfaction: $result")
      result shouldBe 0.0
    }
    "return highest satisfaction when all flights on-time (onTimeRatio=1)".in {
      val cost = 80.0
      val standardPrice = 100
      // onTimeRatio = 1 means all flights on-time
      val allOnTime = Computation.computePassengerSatisfaction(cost, standardPrice, crowdedPercent = 0.0, onTimeRatio = 1.0)
      val allCancelled = Computation.computePassengerSatisfaction(cost, standardPrice, crowdedPercent = 0.0, onTimeRatio = 0.0)
      println(s"All on-time: $allOnTime, All cancelled: $allCancelled")
      allOnTime.shouldBe( > (allCancelled))
    }
    "provide 25% bonus at full on-time vs 80% on-time baseline".in {
      val cost = 90.0
      val standardPrice = 100
      val crowdedPercent = 0.0

      // At onTimeRatio = 1.0: modifier = 1.25 (25% bonus)
      // At onTimeRatio = 0.8: modifier = 1.0 (neutral)
      val fullOnTime = Computation.computePassengerSatisfaction(cost, standardPrice, crowdedPercent, onTimeRatio = 1.0)
      val neutral = Computation.computePassengerSatisfaction(cost, standardPrice, crowdedPercent, onTimeRatio = 0.8)

      println(s"100% on-time: $fullOnTime, 80% on-time (neutral): $neutral")
      val ratio = fullOnTime / neutral
      println(s"Ratio: $ratio (expected 1.25)")
      ratio shouldBe (1.25 +- 0.01)
    }
    "show on-time ratio modifier effect".in {
      val cost = 80.0
      val standardPrice = 100
      println("\nOnTimeRatio modifier effect (cost=80, no crowding):")
      println("OnTime | Modifier | Satisfaction")
      for (onTime <- List(1.0, 0.9, 0.8, 0.7, 0.6, 0.5, 0.4, 0.2, 0.0)) {
        val modifier = 1.25 * onTime
        val result = Computation.computePassengerSatisfaction(cost, standardPrice, 0.0, onTime)
        println(f"$onTime%.1f    | $modifier%.3f    | $result%.3f")
      }
      true shouldBe true
    }
  }

  "computePassengerSatisfaction crowded modifier".must {
    "increase satisfaction with lower load factor".in {
      val cost = 80.0
      val standardPrice = 100
      val onTimeRatio = 0.8

      val highCrowd = Computation.computePassengerSatisfaction(cost, standardPrice, crowdedPercent = 1.0, onTimeRatio)
      val neutralCrowd = Computation.computePassengerSatisfaction(cost, standardPrice, crowdedPercent = 0.8, onTimeRatio)
      val lowCrowd = Computation.computePassengerSatisfaction(cost, standardPrice, crowdedPercent = 0.0, onTimeRatio)

      println(s"Crowded satisfaction (100%/80%/0%): $highCrowd, $neutralCrowd, $lowCrowd")
      lowCrowd.shouldBe( > (neutralCrowd))
      neutralCrowd.shouldBe( > (highCrowd))
    }
    "have neutral point at 80% load factor".in {
      val cost = 80.0
      val standardPrice = 100
      val onTimeRatio = 0.8

      // At 80% LF, crowd modifier should be 1.0 (neutral)
      val at80 = Computation.computePassengerSatisfaction(cost, standardPrice, crowdedPercent = 0.8, onTimeRatio)
      // Base satisfaction without crowd modifier would be: satisfaction * 1.25 * 0.8 = satisfaction * 1.0
      val baseSat = (1.0 - cost/standardPrice) / 0.3  // 0.667
      val expected = baseSat * 1.0  // with onTimeRatio=0.8, modifier = 1.25 * 0.8 = 1.0

      println(s"At 80% LF (neutral): $at80, expected base: $expected")
      at80 shouldBe (expected +- 0.01)
    }
    "give 25% bonus at 0% LF and 25% penalty at 100% LF (symmetric around 80%)".in {
      val cost = 80.0
      val standardPrice = 100
      val onTimeRatio = 0.8

      val empty = Computation.computePassengerSatisfaction(cost, standardPrice, crowdedPercent = 0.0, onTimeRatio)
      val neutral = Computation.computePassengerSatisfaction(cost, standardPrice, crowdedPercent = 0.8, onTimeRatio)
      val full = Computation.computePassengerSatisfaction(cost, standardPrice, crowdedPercent = 1.0, onTimeRatio)

      println(s"Empty (0%): $empty, Neutral (80%): $neutral, Full (100%): $full")

      val bonus = (empty - neutral) / neutral
      val penalty = (neutral - full) / neutral

      println(f"Bonus at 0%%: ${bonus * 100}%.1f%%, Penalty at 100%%: ${penalty * 100}%.1f%%")

      // Both should be ~25%
      bonus shouldBe (0.25 +- 0.02)
      penalty shouldBe (0.25 +- 0.02)
    }
    "show load factor curve from 0% to 100%".in {
      val cost = 80.0
      val standardPrice = 100
      val onTimeRatio = 0.8
      val neutral = Computation.computePassengerSatisfaction(cost, standardPrice, 0.8, onTimeRatio)

      println("\nLoad factor curve (0.8 is neutral):")
      println("LF%  | Modifier | Satisfaction | vs Neutral")
      for (lf <- List(0.0, 0.2, 0.4, 0.6, 0.8, 0.9, 1.0)) {
        val sat = Computation.computePassengerSatisfaction(cost, standardPrice, lf, onTimeRatio)
        val modifier = if (lf <= 0.8) 1.0 + (0.8 - lf) * 0.3125 else 1.0 - (lf - 0.8) * 1.25
        val vsNeutral = if (neutral > 0) (sat - neutral) / neutral * 100 else 0
        println(f"$lf%.1f  | $modifier%.3f    | $sat%.3f        | ${if (vsNeutral >= 0) "+" else ""}$vsNeutral%.1f%%")
      }
      true shouldBe true
    }
  }

  "computePassengerSatisfaction combined scenarios".must {
    "confirm best and worst scenarios".in {
      val cost = 70.0
      val standardPrice = 100

      // Best: good price, no crowding, all on-time
      val best = Computation.computePassengerSatisfaction(cost, standardPrice, crowdedPercent = 0.0, onTimeRatio = 1.0)
      // Worst: good price, full crowding, all cancelled
      val worst = Computation.computePassengerSatisfaction(cost, standardPrice, crowdedPercent = 1.0, onTimeRatio = 0.0)

      println(s"Best scenario: $best, Worst scenario: $worst")
      best.shouldBe( > (worst))
    }
    "show realistic scenarios matrix".in {
      val standardPrice = 100
      println("\nRealistic scenario matrix:")
      println("Cost | Crowd | OnTime | Satisfaction")
      val costs = List(70.0, 80.0, 90.0)
      val crowds = List(0.2, 0.5, 0.8)
      val onTimes = List(1.0, 0.9, 0.7)

      for (cost <- costs; crowd <- crowds; onTime <- onTimes) {
        val result = Computation.computePassengerSatisfaction(cost, standardPrice, crowd, onTime)
        println(f"$cost%.0f  | $crowd%.1f   | $onTime%.1f    | $result%.3f")
      }
      true shouldBe true
    }
  }

  "getDelayRatio semantic verification".must {
    "explain getDelayRatio formula".in {
      // getDelayRatio formula from Transport.scala:
      // Math.max(0, 1 - (minorDelayCount * 0.2 + majorDelayCount * 0.8 + cancellationCount * 1.0) / frequency)
      //
      // All on-time: minorDelayCount=0, majorDelayCount=0, cancellationCount=0
      //   → 1 - 0 = 1.0 (high value = good)
      //
      // All cancelled: cancellationCount = frequency
      //   → 1 - (frequency * 1.0)/frequency = 0.0 (low value = bad)
      //
      // getDelayRatio returns an "on-time ratio" (1.0 = best, 0.0 = worst)
      println("getDelayRatio returns on-time ratio:")
      println("  All on-time → 1.0 (high = good)")
      println("  All cancelled → 0.0 (low = bad)")
      true shouldBe true
    }
    "verify satisfaction correctly uses on-time ratio (FIXED)".in {
      val cost = 80.0
      val standardPrice = 100

      // Now the formula correctly interprets on-time ratio:
      // onTimeRatio = 1.0 (all on-time) → modifier = 1.25 (best)
      // onTimeRatio = 0.0 (all cancelled) → modifier = 0 (worst)
      val allOnTime = Computation.computePassengerSatisfaction(cost, standardPrice, 0.0, onTimeRatio = 1.0)
      val allCancelled = Computation.computePassengerSatisfaction(cost, standardPrice, 0.0, onTimeRatio = 0.0)

      println(s"All on-time (onTimeRatio=1.0) → satisfaction: $allOnTime")
      println(s"All cancelled (onTimeRatio=0.0) → satisfaction: $allCancelled")

      // Now correctly: allOnTime > allCancelled
      allOnTime.shouldBe( > (allCancelled))
      allCancelled shouldBe 0.0
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
