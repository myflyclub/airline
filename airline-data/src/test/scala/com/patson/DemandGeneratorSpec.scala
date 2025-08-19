package com.patson

import com.patson.DemandGenerator.{Demand, HUB_AIRPORTS_MAX_RADIUS, MIN_DISTANCE, canHaveDemand, computeBaseDemandBetweenAirports, computeDemandWithPreferencesBetweenAirports, demandRandomizer, generateChunksForPassengerType, generateHubAirportDemand, getHubAirports}
import com.patson.data.{AirportSource, CountrySource, GameConstants}
import com.patson.model.{PassengerType, _}
import org.scalatest.{Matchers, WordSpecLike}

import java.util
import java.util.{ArrayList, Collections}
import scala.collection.mutable.ListBuffer
import scala.collection.mutable.ListBuffer
import scala.collection.parallel.CollectionConverters._

class DemandGeneratorSpec extends WordSpecLike with Matchers {

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
       val demand = computeDemandWithPreferencesBetweenAirports(fromAirport, toAirport, affinity, distance).foldLeft(0) { (acc, demand) =>
         acc + demand._2
       }
       println(demand)
       assert(demand > 0)
     }
    "Size 9 should find 13 hub airports".in {
      val fromAirport = AirportSource.loadAirportByIata("add", true).get
      val hubAirports: List[(Airport, Double)] = DemandGenerator.getHubAirports(fromAirport, 0)
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
      val hubAirports: List[(Airport, Double)] = DemandGenerator.getHubAirports(fromAirport, 0)
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
      val hubAirports: List[(String, LinkClassValues)] = DemandGenerator.generateHubAirportDemand(fromAirport).toList
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

    // "find top 20 routes for each airport".in {
    //   val demands = DemandGenerator.computeDemand(0)

    //   // Group demands by fromAirport
    //   val groupedDemands = demands.groupBy(_._1.fromAirport)

    //   // Calculate top 10 routes for each airport
    //   val topRoutesByFromAirport = groupedDemands.map { case (fromAirport, demandList) =>
    //     val demandByToAirport = demandList.groupBy(_._2).map { case (toAirport, groupedDemands) =>
    //       val totalDemand = groupedDemands.map(_._3).sum // Sum up the demand for each toAirport
    //       (toAirport, totalDemand)
    //     }

    //     // Sort by total demand and take the top 10 routes
    //     val topRoutes = demandByToAirport.toList.sortBy(-_._2).take(20)
    //     (fromAirport, topRoutes)
    //   }

    //   // Print the top 10 routes for each airport
    //   topRoutesByFromAirport.foreach { case (fromAirport, topRoutes) =>
    //     val csvLine = new StringBuilder(fromAirport.iata)
    //     csvLine.append(s",${fromAirport.countryCode}")
    //     topRoutes.foreach { case (toAirport, totalDemand) =>
    //       csvLine.append(s",${toAirport.iata},$totalDemand")
    //     }
    //     println(csvLine.toString)
    //   }
    // }
//     "find airport demand totals".in {
//       val demands = DemandGenerator.computeDemand(0, Map().empty)
//
//       // Group by the fromAirport in PassengerGroup and calculate total demand
//       val totalFromDemandByAirport = demands.foldLeft(Map[Airport, Int]().withDefaultValue(0)) {
//         case (acc, (passengerGroup, toAirport, demand)) =>
//           // Add the demand to the total for the 'from' airport
//           acc.updated(passengerGroup.fromAirport, acc(passengerGroup.fromAirport) + demand)
//       }.toList.sortBy(_._2).reverse
//       val totalToDemandByAirport = demands.foldLeft(Map[Airport, Int]().withDefaultValue(0)) {
//         case (acc, (passengerGroup, toAirport, demand)) =>
//           acc.updated(toAirport, acc(toAirport) + demand)
//       }
//       // Print the total demand for each airport
//       totalFromDemandByAirport.foreach { case (airport, fromDemand) =>
//         val toDemand = totalToDemandByAirport(airport)
//         println(s"${airport.iata}, ${airport.countryCode}, ${fromDemand}, $toDemand")
//       }
//     }
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
  }
}