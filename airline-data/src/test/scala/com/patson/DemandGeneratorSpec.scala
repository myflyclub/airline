package com.patson

import com.patson.DemandGenerator.{Demand, HUB_AIRPORTS_MAX_RADIUS, MIN_DISTANCE, canHaveDemand, computeBaseDemandBetweenAirports, computeDemandWithPreferencesBetweenAirports, updateHubAirportsList}
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
    "Size 10 should find 14 hub airports".in {
      val fromAirport = AirportSource.loadAirportByIata("ATL", true).get
      var hubAirports = List[Airport]()
      Computation.getDomesticAirportWithinRange(fromAirport, HUB_AIRPORTS_MAX_RADIUS).filter { airport =>
        canHaveDemand(fromAirport, airport, Computation.calculateDistance(fromAirport, airport))
      }.foreach { airport =>
        hubAirports = updateHubAirportsList(hubAirports, airport, fromAirport)
      }
      val percents = DemandGenerator.percentagesHubAirports(hubAirports, fromAirport)
      percents.foreach { case (airport, percentage) =>
        val formattedPercentage = f"${percentage * 100}%.2f%%"
        println(s"${airport.iata}: $formattedPercentage")
      }

      assert(hubAirports.length == 14)
    }
    "Size 1 should find 5 hub airports".in {
      val fromAirport = AirportSource.loadAirportByIata("LLO", true).get
      var hubAirports = List[Airport]()
      Computation.getDomesticAirportWithinRange(fromAirport, HUB_AIRPORTS_MAX_RADIUS).filter { airport =>
        canHaveDemand(fromAirport, airport, Computation.calculateDistance(fromAirport, airport))
      }.foreach { airport =>
        hubAirports = updateHubAirportsList(hubAirports, airport, fromAirport)
      }
      val percents = DemandGenerator.percentagesHubAirports(hubAirports, fromAirport)
      percents.foreach { case (airport, percentage) =>
        val formattedPercentage = f"${percentage * 100}%.2f%%"
        println(s"${airport.iata}: $formattedPercentage")
      }

      assert(hubAirports.length == 5)
    }
//    "find top 10 destinations for each airport".in {
//      val airports = AirportSource.loadAllAirports(fullLoad = false, loadFeatures = true).filter(_.popMiddleIncome > 0)
//      val countryRelationships = CountrySource.getCountryMutualRelationships()
//
//      val topDestinationsByFromAirport = airports.map { fromAirport =>
//        val demandList = ListBuffer[(Airport, Int)]()
//        airports.foreach { toAirport =>
//          val distance = Computation.calculateDistance(fromAirport, toAirport)
//          if (fromAirport != toAirport && distance >= MIN_DISTANCE) {
//            val relationship = countryRelationships.getOrElse((fromAirport.countryCode, toAirport.countryCode), 0)
//            val affinity = Computation.calculateAffinityValue(fromAirport.zone, toAirport.zone, relationship)
//            val demand = computeBaseDemandBetweenAirports(fromAirport, toAirport, affinity, distance)
//            demandList.append((toAirport, demand.total))
//
//          }
//        }
//
//        val topDestinations = demandList.sortBy(_._2).reverse.take(10)
//        (fromAirport, topDestinations)
//      }
//
////      println("From,To1,Demand1,To2,Demand2,To3,Demand3,To4,Demand4,To5,Demand5,To6,Demand6,To7,Demand7,To8,Demand8,To9,Demand9,To10,Demand10")
//      topDestinationsByFromAirport.foreach {
//        case (fromAirport, topDestinations) =>
//          val csvLine = new StringBuilder(fromAirport.iata)
//          topDestinations.foreach {
//            case (toAirport, totalDemand) =>
//              csvLine.append(s",${toAirport.iata},$totalDemand")
//          }
//          println(csvLine.toString)
//      }
//    }
//    "find top 10 routes for each airport".in {
//      val demands = DemandGenerator.computeDemand(470)
//
//      // Group demands by fromAirport
//      val groupedDemands = demands.groupBy(_._1.fromAirport)
//
//      // Calculate top 10 routes for each airport
//      val topRoutesByFromAirport = groupedDemands.map { case (fromAirport, demandList) =>
//        val demandByToAirport = demandList.groupBy(_._2).map { case (toAirport, groupedDemands) =>
//          val totalDemand = groupedDemands.map(_._3).sum // Sum up the demand for each toAirport
//          (toAirport, totalDemand)
//        }
//
//        // Sort by total demand and take the top 10 routes
//        val topRoutes = demandByToAirport.toList.sortBy(-_._2).take(10)
//        (fromAirport, topRoutes)
//      }
//
//      // Print the top 10 routes for each airport
//      println("From,To1,Demand1,To2,Demand2,To3,Demand3,To4,Demand4,To5,Demand5,To6,Demand6,To7,Demand7,To8,Demand8,To9,Demand9,To10,Demand10")
//      topRoutesByFromAirport.foreach { case (fromAirport, topRoutes) =>
//        val csvLine = new StringBuilder(fromAirport.iata)
//        topRoutes.foreach { case (toAirport, totalDemand) =>
//          csvLine.append(s",${toAirport.iata},$totalDemand")
//        }
//        println(csvLine.toString)
//      }
//    }
//     "find airport demand totals".in {
//       val demands = DemandGenerator.computeDemand(470)
//
//       // Group by the fromAirport in PassengerGroup and calculate total demand
//       val totalDemandByAirport = demands.groupBy(_._1.fromAirport).map { case (fromAirport, demandList) =>
//         val totalDemand = demandList.map(_._3).sum // Sum up the Int values (demand)
//         (fromAirport, totalDemand)
//       }
//
//       // Print the total demand for each airport
//       totalDemandByAirport.foreach { case (airport, totalDemand) =>
//         println(s"${airport.iata}, ${airport.countryCode}, $totalDemand")
//       }
//     }
  }

  implicit class DemandClassImprovements(demand: Demand) {
    def total: Int = {
      (demand.travelerDemand.total + demand.businessDemand.total + demand.touristDemand.total).toInt
    }
  }

}