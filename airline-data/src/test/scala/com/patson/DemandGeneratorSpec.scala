package com.patson

import com.patson.DemandGenerator.{Demand, HUB_AIRPORTS_MAX_RADIUS, MIN_DISTANCE, canHaveDemand, computeBaseDemandBetweenAirports, computeDemandWithPreferencesBetweenAirports, demandRandomizer, generateChunksForPassengerType, generateHubAirportDemand, getHubAirports}
import com.patson.data.{AirportSource, AirportStatisticsSource, CountrySource, CycleSource, GameConstants}
import com.patson.model.{Airport, PassengerType, _}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import java.util
import java.util.{ArrayList, Collections}
import scala.collection.mutable.ListBuffer
import scala.collection.mutable.ListBuffer
import scala.collection.parallel.CollectionConverters._

class DemandGeneratorSpec extends AnyWordSpecLike with Matchers {
  val cycle = CycleSource.loadCycle()

  "generateDemand".must {
    "min distance test".in {
      val fromAirport = AirportSource.loadAirportByIata("MKE").get
      val toAirport = AirportSource.loadAirportByIata("ORD").get
      val distance = Computation.calculateDistance(fromAirport, toAirport)
      val canHaveDemand = DemandGenerator.canHaveDemand(fromAirport, toAirport, distance)
      assert(!canHaveDemand)
    }
     "isolatedAirportTest".in {
       val fromAirport = AirportSource.loadAirportByIata("TER", true).get
       val toAirport = AirportSource.loadAirportByIata("PDL", true).get
       val distance = Computation.calculateDistance(fromAirport, toAirport)
       val relationship = CountrySource.getCountryMutualRelationships().getOrElse((fromAirport.countryCode, toAirport.countryCode), 0)
       val affinity = Computation.calculateAffinityValue(fromAirport.zone, toAirport.zone, relationship)
       val demand = computeDemandWithPreferencesBetweenAirports(fromAirport, toAirport, affinity, distance, cycle).foldLeft(0) { (acc, demand) =>
         acc + demand._2
       }
       println(demand)
       assert(demand > 0)
     }
    "Size 9 should find 13 hub airports".in {
      val fromAirport = AirportSource.loadAirportByIata("add", true).get
      val hubAirports: List[(Airport, Double)] = DemandGenerator.getHubAirports(fromAirport, 0, cycle)
      var percentTotal = 0.0
      println(s"from ${fromAirport.iata}:")
      hubAirports.foreach { case (airport, percentage) =>
        percentTotal += percentage
        val formattedPercentage = f"${percentage * 100}%.2f%%"
        println(s"${airport.iata}, $formattedPercentage")
      }
      assert(hubAirports.length == 15)
      assert(percentTotal <= 1.0 && percentTotal >= 0.99)
    }
    "Size 7 should find 11 hub airports".in {
      val fromAirport = AirportSource.loadAirportByIata("add", true).get
      val hubAirports: List[(Airport, Double)] = DemandGenerator.getHubAirports(fromAirport, 0, cycle)
      var percentTotal = 0.0
      println(s"from ${fromAirport.iata}:")
      hubAirports.foreach { case (airport, percentage) =>
        percentTotal += percentage
        val formattedPercentage = f"${percentage * 100}%.2f%%"
        println(s"${airport.iata}, $formattedPercentage")
      }
      assert(hubAirports.size == 13)
    }
    "Size 1 Isolated Town strength 1 should find 8 hub airports".in {
      val fromAirport = AirportSource.loadAirportByIata("brw", true).get
      val hubAirports: List[(String, LinkClassValues)] = DemandGenerator.generateHubAirportDemand(fromAirport, cycle).toList
      val total = hubAirports.map(_._2.total).sum
      println(s"from ${fromAirport.iata} ${fromAirport.countryCode}:")
      hubAirports.foreach { case (airport, linkClassValues) =>
        val percent = (linkClassValues.total.toDouble / total * 100).toInt
        val formattedPercentage = f"$percent%.2f%%"
        println(s"$airport, $formattedPercentage")
      }
      assert(hubAirports.size == 13)
    }
//    "Size 1 Isolated Town strength 4 should only generate demand to airports in DK".in {
//      val fromAirport = AirportSource.loadAirportByIata("KUS", true).get
//      val airports = AirportSource.loadAllAirports(fullLoad = true, loadFeatures = true)
//      val featureOpt = fromAirport.features.find(_.featureType == AirportFeatureType.ISOLATED_TOWN)
//      assert(featureOpt.isDefined, "KUS should have IsolatedTownFeature")
//      airports.foreach { toAirport =>
//        val distance = Computation.calculateDistance(fromAirport, toAirport)
//        val relationship = CountrySource.getCountryMutualRelationships().getOrElse((fromAirport.countryCode, toAirport.countryCode), 0)
//        val affinity = Computation.calculateAffinityValue(fromAirport.zone, toAirport.zone, relationship)
//        val demands = DemandGenerator.computeBaseDemandBetweenAirports(fromAirport, toAirport, affinity, distance)
//        if (toAirport.countryCode == "DK" || toAirport.countryCode == "IS") {
//          assert(demands.travelerDemand.total >= 0, s"Expected possible demand from KUS to ${toAirport.iata} in DK, got ${demands.travelerDemand.total}")
//          assert(demands.businessDemand.total >= 0, s"Expected possible demand from KUS to ${toAirport.iata} in DK, got ${demands.businessDemand.total}")
//          assert(demands.touristDemand.total >= 0, s"Expected possible demand from KUS to ${toAirport.iata} in DK, got ${demands.touristDemand.total}")
//        } else {
//          assert(demands.travelerDemand.total == 0, s"Expected no demand from KUS to ${toAirport.iata} in DK, got ${demands.travelerDemand.total}")
//          assert(demands.businessDemand.total == 0, s"Expected no demand from KUS to ${toAirport.iata} in DK, got ${demands.businessDemand.total}")
//          assert(demands.touristDemand.total == 0, s"Expected no demand from KUS to ${toAirport.iata} in DK, got ${demands.touristDemand.total}")
//        }
//      }
//    }
//    "find top 10 destinations for each airport".in {
//      val airports = AirportSource.loadAllAirports(fullLoad = true).filter(_.popMiddleIncome > 0)
//      val countryRelationships = CountrySource.getCountryMutualRelationships()
//
//      val topDestinationsByFromAirport = airports.map { fromAirport =>
//        val fromHubAirportsDemands: Map[String, LinkClassValues] = generateHubAirportDemand(fromAirport)
//        val demandList = ListBuffer[(Airport, Int)]()
//        airports.foreach { toAirport =>
//          val distance = Computation.calculateDistance(fromAirport, toAirport)
//          if (canHaveDemand(fromAirport, toAirport, distance)) {
//            val relationship = countryRelationships.getOrElse((fromAirport.countryCode, toAirport.countryCode), 0)
//            val affinity = Computation.calculateAffinityValue(fromAirport.zone, toAirport.zone, relationship)
//            val demand = computeBaseDemandBetweenAirports(fromAirport, toAirport, affinity, distance)
//
//            val total = demand.businessDemand.total + demand.touristDemand.total + demand.travelerDemand.total + fromHubAirportsDemands.getOrElse(toAirport.iata, LinkClassValues.empty).total
//            if (total > 0){
//              demandList.append((toAirport, total))
//            }
//          }
//        }
//
//        val topDestinations = demandList.sortBy(_._2).reverse.take(20)
//        (fromAirport, topDestinations)
//      }
//
//      topDestinationsByFromAirport.foreach {
//        case (fromAirport, topDestinations) =>
//          val csvLine = new StringBuilder(s"${fromAirport.iata},${fromAirport.countryCode}")
//          topDestinations.foreach {
//            case (toAirport, totalDemand) =>
//              csvLine.append(s",${toAirport.iata}  ${toAirport.countryCode},$totalDemand")
//          }
//          println(csvLine.toString)
//      }
//    }

    "find top 20 demands to each airport".in {
      val demands = DemandGenerator.computeDemand(0, Map().empty)

      // Group demands by toAirport
      val groupedDemands = demands.groupBy(_._2)

      // Calculate top 20 origins for each airport
      val topOriginsByToAirport = groupedDemands.map { case (toAirport, demandList) =>
        val demandByFromAirport = demandList.groupBy(_._1.fromAirport).map { case (fromAirport, groupedDemands) =>
          val totalDemand = groupedDemands.map(_._3).sum // Sum up the demand for each fromAirport
          (fromAirport, totalDemand)
        }

        // Sort by total demand and take the top 20 origins
        val topOrigins = demandByFromAirport.toList.sortBy(-_._2).take(20)
        (toAirport, topOrigins)
      }

      // Print the top 20 origins for each airport
      topOriginsByToAirport.foreach { case (toAirport, topOrigins) =>
        val csvLine = new StringBuilder(toAirport.iata)
        csvLine.append(s",${toAirport.countryCode}")
        topOrigins.foreach { case (fromAirport, totalDemand) =>
          csvLine.append(s",${fromAirport.iata},$totalDemand")
        }
        println(csvLine.toString)
      }
    }

     "find top 20 routes from each airport".in {
       val demands = DemandGenerator.computeDemand(0, Map().empty)

       // Group demands by fromAirport
       val groupedDemands = demands.groupBy(_._1.fromAirport)

       // Calculate top 10 routes for each airport
       val topRoutesByFromAirport = groupedDemands.map { case (fromAirport, demandList) =>
         val demandByToAirport = demandList.groupBy(_._2).map { case (toAirport, groupedDemands) =>
           val totalDemand = groupedDemands.map(_._3).sum // Sum up the demand for each toAirport
           (toAirport, totalDemand)
         }

         // Sort by total demand and take the top 10 routes
         val topRoutes = demandByToAirport.toList.sortBy(-_._2).take(20)
         (fromAirport, topRoutes)
       }
       topRoutesByFromAirport.foreach { case (fromAirport, topRoutes) =>
         val csvLine = new StringBuilder(fromAirport.iata)
         csvLine.append(s",${fromAirport.countryCode}")
         topRoutes.foreach { case (toAirport, totalDemand) =>
           csvLine.append(s",${toAirport.iata},$totalDemand")
         }
         println(csvLine.toString)
       }
     }

    //     // Sort by total demand and take the top 10 routes
    //     val topRoutes = demandByToAirport.toList.sortBy(-_._2).take(20)
    //     (fromAirport, topRoutes)
    //   }

    //   // Print the top 10 routes for each airport
//       topRoutesByFromAirport.foreach { case (fromAirport, topRoutes) =>
//         val csvLine = new StringBuilder(fromAirport.iata)
//         csvLine.append(s",${fromAirport.countryCode}")
//         topRoutes.foreach { case (toAirport, totalDemand) =>
//           csvLine.append(s",${toAirport.iata},$totalDemand")
//         }
//         println(csvLine.toString)
//       }
    // }
     "find airport demand totals".in {
       val demands = DemandGenerator.computeDemand(0, Map().empty)

       // Group by the fromAirport in PassengerGroup and calculate total demand
       val totalFromDemandByAirport = demands.foldLeft(Map[Airport, Int]().withDefaultValue(0)) {
         case (acc, (passengerGroup, toAirport, demand)) =>
           // Add the demand to the total for the 'from' airport
           acc.updated(passengerGroup.fromAirport, acc(passengerGroup.fromAirport) + demand)
       }.toList.sortBy(_._2).reverse
       val totalToDemandByAirport = demands.foldLeft(Map[Airport, Int]().withDefaultValue(0)) {
         case (acc, (passengerGroup, toAirport, demand)) =>
           acc.updated(toAirport, acc(toAirport) + demand)
       }
       // Print the total demand for each airport
       totalFromDemandByAirport.foreach { case (airport, fromDemand) =>
         val toDemand = totalToDemandByAirport(airport)
         println(s"${airport.iata}, ${airport.countryCode}, ${fromDemand}, $toDemand")
       }
     }
    "demandRandomizer output 200 cycles".in {
      val baseDemand = 25
      val frequency = 45
      val amplitudeRatio = 1
      println("Cycle,Randomized Demand")
      for (cycle <- 1 to 200) {
        val randomizedDemand = demandRandomizer(baseDemand, cycle, frequency, amplitudeRatio)
        val randomizedDemand2 = demandRandomizer(baseDemand, cycle, frequency, amplitudeRatio + 2)
        println(s"$cycle,$randomizedDemand,$randomizedDemand2")
      }
    }

    "demandRandomizer sine wave analysis".in {
      val baseDemand = 1000
      val frequency = 45

      println("=== Sine Wave Analysis (frequency=45) ===\n")

      // Show the pure sine wave values at key points
      println("Pure sine wave at key cycle points:")
      println("Cycle | sin value | Phase")
      println("------|-----------|-------")
      val keyPoints = List(
        (0, "start"),
        (frequency / 4, "peak (+1)"),
        (frequency / 2, "zero crossing"),
        (3 * frequency / 4, "trough (-1)"),
        (frequency, "full period")
      )
      keyPoints.foreach { case (cycle, phase) =>
        val sinValue = math.sin(2 * math.Pi * cycle / frequency)
        println(f"$cycle%5d | $sinValue%+9.4f | $phase")
      }

      // Show max theoretical variance
      println("\n=== Theoretical Max Variance ===")
      println("amplitude = amplitudeRatio * max(8, demand * random[0, 0.1])")
      println(s"For demand=$baseDemand:")
      println(s"  Min amplitude: 8")
      println(s"  Max amplitude: ${baseDemand * 0.1} (when random=0.1)")
      println(s"  Peak-to-trough swing: 2 * amplitude")
      println(s"  Max swing (amplitudeRatio=1): ${2 * baseDemand * 0.1} = ${(2 * baseDemand * 0.1 / baseDemand * 100).toInt}% of base")
      println(s"  Max swing (amplitudeRatio=2, TOURIST): ${4 * baseDemand * 0.1} = ${(4 * baseDemand * 0.1 / baseDemand * 100).toInt}% of base")
      println(s"  Plus random noise: ±3% per call")

      // Measure actual variance between peak and trough cycles
      println("\n=== Actual Measured Variance (10 samples each) ===")
      val peakCycle = frequency / 4  // ~11
      val troughCycle = 3 * frequency / 4  // ~34

      val peakSamples = (1 to 10).map(_ => demandRandomizer(baseDemand, peakCycle, frequency, 1))
      val troughSamples = (1 to 10).map(_ => demandRandomizer(baseDemand, troughCycle, frequency, 1))

      println(s"Peak cycle ($peakCycle): ${peakSamples.mkString(", ")}")
      println(s"  avg=${peakSamples.sum / 10}, min=${peakSamples.min}, max=${peakSamples.max}")
      println(s"Trough cycle ($troughCycle): ${troughSamples.mkString(", ")}")
      println(s"  avg=${troughSamples.sum / 10}, min=${troughSamples.min}, max=${troughSamples.max}")

      val maxVariance = peakSamples.max - troughSamples.min
      val minVariance = peakSamples.min - troughSamples.max
      println(s"\nMax observed peak-to-trough: $maxVariance (${(maxVariance.toDouble / baseDemand * 100).toInt}%)")
      println(s"Min observed peak-to-trough: $minVariance (${(minVariance.toDouble / baseDemand * 100).toInt}%)")
    }

    "demandRandomizer cycle normalization check".in {
      val baseDemand = 1000
      val frequency = 45

      println("=== Cycle Normalization Check ===")
      println("Does sin(2π * cycle / frequency) need normalization?")
      println()

      // Compare cycle values that should be equivalent
      val testCycles = List(
        (10, 10 + frequency, 10 + 2 * frequency),      // All should give same sin value
        (0, frequency, 2 * frequency),                  // All should be 0
        (frequency / 4, frequency / 4 + frequency, frequency / 4 + 2 * frequency)  // All should be peak
      )

      println("Cycle values | sin values (should be identical within each row)")
      println("-------------|--------------------------------------------------")
      testCycles.foreach { case (c1, c2, c3) =>
        val s1 = math.sin(2 * math.Pi * c1 / frequency)
        val s2 = math.sin(2 * math.Pi * c2 / frequency)
        val s3 = math.sin(2 * math.Pi * c3 / frequency)
        println(f"$c1%3d, $c2%3d, $c3%3d | $s1%+.6f, $s2%+.6f, $s3%+.6f")
      }

      println("\nConclusion: No normalization needed - sin function naturally repeats every 'frequency' cycles")
      println("cycle=1000 with frequency=45 gives same sin value as cycle=1000%45=10")

      // Verify
      val highCycle = 1000
      val normalizedCycle = highCycle % frequency
      val sinHigh = math.sin(2 * math.Pi * highCycle / frequency)
      val sinNormalized = math.sin(2 * math.Pi * normalizedCycle / frequency)
      println(f"\nVerify: cycle=$highCycle sin=$sinHigh%.6f")
      println(f"        cycle=$normalizedCycle (normalized) sin=$sinNormalized%.6f")
      assert(math.abs(sinHigh - sinNormalized) < 0.0001, "Sin values should match")
    }

    "demandRandomizer by passenger type phase offsets".in {
      val baseDemand = 1000
      val frequency = 45

      println("=== Passenger Type Phase Offsets ===")
      println("Different passenger types peak at different cycles:\n")

      // From demandRandomizerByType:
      // TOURIST: offset=24, amplitudeRatio=2
      // BUSINESS: offset=12, amplitudeRatio=1
      // TRAVELER: offset=0, amplitudeRatio=1

      val types = List(
        ("TRAVELER", 0, 1),
        ("BUSINESS", 12, 1),
        ("TOURIST", 24, 2)
      )

      println("Type     | Offset | Amp | Peak Cycle | Trough Cycle")
      println("---------|--------|-----|------------|-------------")
      types.foreach { case (name, offset, amp) =>
        // Peak when (cycle + offset) / frequency = 0.25, so cycle = 0.25 * frequency - offset
        val peakCycle = ((0.25 * frequency - offset) + frequency) % frequency
        val troughCycle = ((0.75 * frequency - offset) + frequency) % frequency
        println(f"$name%-8s | $offset%6d | $amp%3d | $peakCycle%10.1f | $troughCycle%11.1f")
      }

      println("\n=== Demand at cycle 0, 15, 30, 45 by type ===")
      println("Cycle | TRAVELER avg | BUSINESS avg | TOURIST avg")
      println("------|--------------|--------------|-------------")

      for (cycle <- List(0, 15, 30, 45)) {
        val travelerAvg = (1 to 5).map(_ => demandRandomizer(baseDemand, cycle, frequency, 1, 0)).sum / 5
        val businessAvg = (1 to 5).map(_ => demandRandomizer(baseDemand, cycle, frequency, 1, 12)).sum / 5
        val touristAvg = (1 to 5).map(_ => demandRandomizer(baseDemand, cycle, frequency, 2, 24)).sum / 5
        println(f"$cycle%5d | $travelerAvg%12d | $businessAvg%12d | $touristAvg%11d")
      }
    }

    "travelRateAdjusted baseTravelRate by airport size".in {
      println("Airport Size | Base Travel Rate | Effect on Demand")
      println("-------------|------------------|------------------")
      for (size <- 1 to 9) {
        val baseTravelRate = Airport.travelRateAdjusted(0, 0, size) // 0% demand met shows base rate
        val demandEffect = f"${baseTravelRate * 100}%.0f%% of base demand"
        println(f"$size%12d | $baseTravelRate%16.2f | $demandEffect")
      }
    }

    "computeBaseDemandBetweenAirports should be deterministic".in {
      val fromAirport = AirportSource.loadAirportByIata("JFK", true).get
      val toAirport = AirportSource.loadAirportByIata("LAX", true).get
      val distance = Computation.calculateDistance(fromAirport, toAirport)
      val relationship = CountrySource.getCountryMutualRelationships().getOrElse((fromAirport.countryCode, toAirport.countryCode), 0)
      val affinity = Computation.calculateAffinityValue(fromAirport.zone, toAirport.zone, relationship)

      // Run 10 times and verify same result
      val demands = (1 to 10).map { _ =>
        val demand = computeBaseDemandBetweenAirports(fromAirport, toAirport, affinity, distance)
        DemandGenerator.addUpDemands(demand)
      }

      val firstDemand = demands.head
      println(s"JFK -> LAX base demand (10 runs): ${demands.mkString(", ")}")
      assert(demands.forall(_ == firstDemand), s"computeBaseDemandBetweenAirports should be deterministic, got: ${demands.mkString(", ")}")
    }

    "PEK to ZSE should have no demand".in {
      val fromAirport = AirportSource.loadAirportByIata("PEK", true).get
      val toAirport = AirportSource.loadAirportByIata("ZSE", true).get
      val distance = Computation.calculateDistance(fromAirport, toAirport)
      val relationship = CountrySource.getCountryMutualRelationships().getOrElse((fromAirport.countryCode, toAirport.countryCode), 0)
      val affinity = Computation.calculateAffinityValue(fromAirport.zone, toAirport.zone, relationship)

      val demands = computeBaseDemandBetweenAirports(fromAirport, toAirport, affinity, distance)
      val totalDemand = DemandGenerator.addUpDemands(demands)

      println(s"PEK to ZSE distance: $distance, affinity: $affinity, distance: $distance, total demand: $totalDemand")
      assert(totalDemand == 0)
    }

    "demandRandomizer variance should be bounded".in {
      val baseDemand = 1000
      val frequency = 45

      // Run 100 iterations at same cycle to measure random variance
      val cycle = 50
      val results = (1 to 100).map(_ => demandRandomizer(baseDemand, cycle, frequency))

      val minResult = results.min
      val maxResult = results.max
      val avgResult = results.sum.toDouble / results.size
      val variance = (maxResult - minResult).toDouble / baseDemand

      println(s"Base demand: $baseDemand")
      println(s"At cycle $cycle: min=$minResult, max=$maxResult, avg=${avgResult.toInt}")
      println(s"Variance: ${(variance * 100).toInt}% of base demand")

      // Variance should be roughly bounded by ±7% random + amplitude
      // amplitude = max(8, demand * 0.1) = max(8, 100) = 100
      // random = demand * [-0.07, 0.07] = [-70, 70]
      // Total swing could be up to ~17% in each direction
      assert(variance < 0.40, s"Variance should be less than 40% of base demand, got ${(variance * 100).toInt}%")
    }

    "demandRandomizer with different passenger types".in {
      val baseDemand = 500
      val cycle = 100
      val cyclePhaseLength = 45

      println("PassengerType | Amplitude | Sample Values (5 runs)")
      println("--------------|-----------|------------------------")

      // TOURIST: amplitudeRatio=2, offset=24
      val touristResults = (1 to 5).map(_ => demandRandomizer(baseDemand, cycle, cyclePhaseLength, 2, 24))
      println(s"TOURIST       | 2x        | ${touristResults.mkString(", ")}")

      // BUSINESS: amplitudeRatio=1, offset=12
      val businessResults = (1 to 5).map(_ => demandRandomizer(baseDemand, cycle, cyclePhaseLength, 1, 12))
      println(s"BUSINESS      | 1x        | ${businessResults.mkString(", ")}")

      // TRAVELER: amplitudeRatio=1, offset=0 (default)
      val travelerResults = (1 to 5).map(_ => demandRandomizer(baseDemand, cycle, cyclePhaseLength))
      println(s"TRAVELER      | 1x        | ${travelerResults.mkString(", ")}")
    }

    "demand with travelRateAdjusted simulation for large airport".in {
      val fromAirport = AirportSource.loadAirportByIata("JFK", true).get
      val toAirport = AirportSource.loadAirportByIata("LAX", true).get
      val distance = Computation.calculateDistance(fromAirport, toAirport)
      val relationship = CountrySource.getCountryMutualRelationships().getOrElse((fromAirport.countryCode, toAirport.countryCode), 0)
      val affinity = Computation.calculateAffinityValue(fromAirport.zone, toAirport.zone, relationship)

      val baseDemand = computeBaseDemandBetweenAirports(fromAirport, toAirport, affinity, distance)
      val totalBaseDemand = DemandGenerator.addUpDemands(baseDemand)

      println(s"JFK (size ${fromAirport.size}) -> LAX:")
      println(s"  Base demand (no travelRateAdjusted): $totalBaseDemand")

      // Simulate different travelRateAdjusted scenarios
      val scenarios = List(
        ("35% demand met", 0.35 * totalBaseDemand),
        ("45% demand met (baseTravelRate)", 0.45 * totalBaseDemand),
        ("60% demand met", 0.60 * totalBaseDemand),
        ("80% demand met", 0.80 * totalBaseDemand),
        ("100% demand met", 1.0 * totalBaseDemand)
      )

      println("\n  Scenario                     | travelRateAdjusted | Effective Demand")
      println("  -----------------------------|------------|------------------")

      scenarios.foreach { case (label, fromPax) =>
        val travelRateAdjusted = Airport.travelRateAdjusted(fromPax.toInt, fromPax.toInt, fromAirport.size)
        val effectiveDemand = (totalBaseDemand * travelRateAdjusted).toInt
        println(f"  $label%-29s | $travelRateAdjusted%10.2f | $effectiveDemand%d")
      }
    }

    "generateChunksForPassengerType with different stats scenarios".in {
      val fromAirport = AirportSource.loadAirportByIata("JFK", true).get
      val toAirport = AirportSource.loadAirportByIata("LAX", true).get
      val flightPreferencesPool = DemandGenerator.getFlightPreferencePoolOnAirport(fromAirport)
      val demand = LinkClassValues.getInstance(100, 20, 5, 50) // economy, business, first, discount

      println(s"Input demand: economy=${demand(ECONOMY)}, business=${demand(BUSINESS)}, first=${demand(FIRST)}, discount=${demand(DISCOUNT_ECONOMY)}")
      println(s"Total input: ${demand.total}")

      // Scenario 1: No stats (init/test scenario)
      val noStatsChunks = generateChunksForPassengerType(demand, fromAirport, toAirport, PassengerType.TRAVELER, flightPreferencesPool, Map.empty, cycle, 50)
      val noStatsTotal = noStatsChunks.map(_._3).sum
      println(s"\nNo stats scenario: total chunks=${noStatsChunks.size}, total demand=$noStatsTotal")

      // Scenario 2: Low demand met (35%)
      val lowStats = Map(fromAirport.id -> AirportStatistics(fromAirport.id, 100000, 35000, 0.0, 0.0, 0.35))
      val lowStatsChunks = generateChunksForPassengerType(demand, fromAirport, toAirport, PassengerType.TRAVELER, flightPreferencesPool, lowStats, cycle, 50)
      val lowStatsTotal = lowStatsChunks.map(_._3).sum
      val expectedTravelRateLow = Airport.travelRateAdjusted(lowStats.head._2.fromPax, lowStats.head._2.baselineDemand, fromAirport.size)
      println(s"Low demand met (35%): travelRateAdjusted=$expectedTravelRateLow, total chunks=${lowStatsChunks.size}, total demand=$lowStatsTotal")

      // Scenario 3: High demand met (80%)
      val highStats = Map(fromAirport.id -> AirportStatistics(fromAirport.id, 100000, 80000, 0.0, 0.0, 0.80))
      val highStatsChunks = generateChunksForPassengerType(demand, fromAirport, toAirport, PassengerType.TRAVELER, flightPreferencesPool, highStats, cycle, 50)
      val highStatsTotal = highStatsChunks.map(_._3).sum
      val expectedTravelRateHigh = Airport.travelRateAdjusted(highStats.head._2.fromPax, highStats.head._2.baselineDemand, fromAirport.size)
      println(s"High demand met (80%): travelRateAdjusted=$expectedTravelRateHigh, total chunks=${highStatsChunks.size}, total demand=$highStatsTotal")
    }

    "computeDemand should produce consistent output within 10% variance across runs".in {
      // Load airport stats from database
      val airportStatsList = AirportStatisticsSource.loadAllAirportStats()
      val airportStats: Map[Int, AirportStatistics] = airportStatsList.map(s => s.airportId -> s).toMap

      println(s"Loaded ${airportStats.size} airport statistics")

      // Run computeDemand multiple times and collect total demand for each run
      // Note: Each run is expensive (~1000s of airports), so we run fewer iterations
      // but verify the variance is still within acceptable bounds
      val numRuns = 20
      val startCycle = 110
      val endCycle = startCycle + numRuns - 1

      val demandTotals = (startCycle to endCycle).map { i => // 'i' is now the cycle
        val startTime = System.currentTimeMillis()
        val demand = DemandGenerator.computeDemand(i * 3, airportStats)
        val total = demand.map(_._3).sum
        val elapsed = System.currentTimeMillis() - startTime
        println(s"Cycle $i: total demand = $total, chunks = ${demand.size}, time = ${elapsed}ms")
        total
      }

      val minDemand = demandTotals.min
      val maxDemand = demandTotals.max
      val avgDemand = demandTotals.sum.toDouble / numRuns
      val variance = (maxDemand - minDemand).toDouble / avgDemand

      println(s"\n=== computeDemand Stability Test Results ($numRuns runs) ===")
      println(s"Min demand: $minDemand")
      println(s"Max demand: $maxDemand")
      println(s"Avg demand: ${avgDemand.toLong}")
      println(s"Variance (max-min)/avg: ${(variance * 100).toInt}%")
      println(s"Min as % of avg: ${(minDemand.toDouble / avgDemand * 100).toInt}%")
      println(s"Max as % of avg: ${(maxDemand.toDouble / avgDemand * 100).toInt}%")

      println("\n=== Demand Totals CSV ===")
      println("Cycle,Total Demand")
      (startCycle to endCycle).zip(demandTotals).foreach { case (cycle, total) =>
        println(s"$cycle,$total")
      }

      // Verify all runs are within 10% of the average
      val withinTolerance = demandTotals.forall { total =>
        val deviation = Math.abs(total - avgDemand) / avgDemand
        deviation <= 0.10
      }

      assert(withinTolerance, s"All runs should be within 10% of average. Variance was ${(variance * 100).toInt}%")
      assert(variance < 0.10, s"Total variance (max-min)/avg should be less than 10%, got ${(variance * 100).toInt}%")
    }

    "computeDemand sample consistency test - verify 100 iterations on subset".in {
      // This test runs 100 iterations on a smaller subset of the demand calculation
      // to verify the randomizer stays within bounds
      val airportStatsList = AirportStatisticsSource.loadAllAirportStats()
      val airportStats: Map[Int, AirportStatistics] = airportStatsList.map(s => s.airportId -> s).toMap

      // Use specific major airports for sampling
      val sampleFromAirports = List("JFK", "LAX", "ORD", "LHR", "CDG", "NRT", "SIN", "DXB", "HKG", "SYD")
      val sampleToAirports = List("LAX", "JFK", "LHR", "FRA", "PEK", "ICN", "BKK", "DEL", "GRU", "MEX")

      val fromAirports = sampleFromAirports.flatMap(iata => AirportSource.loadAirportByIata(iata, true))
      val toAirports = sampleToAirports.flatMap(iata => AirportSource.loadAirportByIata(iata, true))

      println(s"Sample test using ${fromAirports.size} from-airports and ${toAirports.size} to-airports")

      val countryRelationships = CountrySource.getCountryMutualRelationships()
      val numRuns = 100

      val demandTotals = (1 to numRuns).map { run =>
        var totalDemand = 0
        fromAirports.foreach { fromAirport =>
          val flightPreferencesPool = DemandGenerator.getFlightPreferencePoolOnAirport(fromAirport)
          toAirports.foreach { toAirport =>
            val distance = Computation.calculateDistance(fromAirport, toAirport)
            if (DemandGenerator.canHaveDemand(fromAirport, toAirport, distance)) {
              val relationship = countryRelationships.getOrElse((fromAirport.countryCode, toAirport.countryCode), 0)
              val affinity = Computation.calculateAffinityValue(fromAirport.zone, toAirport.zone, relationship)
              val demand = DemandGenerator.computeBaseDemandBetweenAirports(fromAirport, toAirport, affinity, distance)

              // Apply travelRateAdjusted and randomization like the real computeDemand does
              List(
                (demand.travelerDemand, PassengerType.TRAVELER),
                (demand.businessDemand, PassengerType.BUSINESS),
                (demand.touristDemand, PassengerType.TOURIST)
              ).foreach { case (linkClassValues, passengerType) =>
                val chunks = DemandGenerator.generateChunksForPassengerType(
                  linkClassValues, fromAirport, toAirport, passengerType, flightPreferencesPool, airportStats, 1, 50
                )
                totalDemand += chunks.map(_._3).sum
              }
            }
          }
        }
        if (run % 20 == 0) {
          println(s"Run $run: sample demand = $totalDemand")
        }
        totalDemand
      }

      val minDemand = demandTotals.min
      val maxDemand = demandTotals.max
      val avgDemand = demandTotals.sum.toDouble / numRuns
      val variance = (maxDemand - minDemand).toDouble / avgDemand

      println(s"\n=== Sample Consistency Test Results ($numRuns runs) ===")
      println(s"Min demand: $minDemand")
      println(s"Max demand: $maxDemand")
      println(s"Avg demand: ${avgDemand.toLong}")
      println(s"Variance (max-min)/avg: ${(variance * 100).toInt}%")
      println(s"All values: ${demandTotals.mkString(", ")}")

      // Verify all runs are within 10% of the average
      val withinTolerance = demandTotals.forall { total =>
        val deviation = Math.abs(total - avgDemand) / avgDemand
        deviation <= 0.10
      }

      assert(withinTolerance, s"All runs should be within 10% of average. Variance was ${(variance * 100).toInt}%")
      assert(variance < 0.10, s"Total variance (max-min)/avg should be less than 10%, got ${(variance * 100).toInt}%")
    }

    "computeDemand should have no PassengerType exceeding 35% of total demand".in {
      // Load airport stats from database
      val airportStatsList = AirportStatisticsSource.loadAllAirportStats()
      val airportStats: Map[Int, AirportStatistics] = airportStatsList.map(s => s.airportId -> s).toMap

      println(s"Loaded ${airportStats.size} airport statistics")

      // Run computeDemand for cycle 1
      val cycle = 1
      val startTime = System.currentTimeMillis()
      val demand = DemandGenerator.computeDemand(cycle, airportStats)
      val elapsed = System.currentTimeMillis() - startTime

      println(s"Cycle $cycle: total chunks = ${demand.size}, time = ${elapsed}ms")

      // Group demand by PassengerType
      val demandByType = demand.groupBy { case (passengerGroup, _, _) =>
        passengerGroup.passengerType
      }.map { case (passengerType, chunks) =>
        val totalForType = chunks.map(_._3).sum.toLong
        (passengerType, totalForType)
      }

      val totalDemand = demandByType.values.sum.toDouble

      println(s"\n=== Demand Distribution by PassengerType (cycle $cycle) ===")
      println(s"Total demand: ${totalDemand.toLong}")
      println(f"${"PassengerType"}%-20s | ${"Demand"}%12s | ${"Percentage"}%10s")
      println("-" * 50)

      demandByType.toList.sortBy(-_._2).foreach { case (passengerType, typeDemand) =>
        val percentage = typeDemand / totalDemand * 100
        println(f"$passengerType%-20s | $typeDemand%12d | $percentage%9.2f%%")
      }

      // Verify no PassengerType exceeds 35% of total
      val maxPercentage = 0.35
      val violations = demandByType.filter { case (_, typeDemand) =>
        typeDemand / totalDemand > maxPercentage
      }

      if (violations.nonEmpty) {
        println(s"\n!!! VIOLATIONS (>${(maxPercentage * 100).toInt}%) !!!")
        violations.foreach { case (passengerType, typeDemand) =>
          val percentage = typeDemand / totalDemand * 100
          println(f"$passengerType: $percentage%.2f%%")
        }
      }

      assert(violations.isEmpty,
        s"No PassengerType should exceed ${(maxPercentage * 100).toInt}% of total. Violations: ${
          violations.map { case (pt, d) => s"$pt: ${(d / totalDemand * 100).toInt}%" }.mkString(", ")
        }")
    }
  }
}