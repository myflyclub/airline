package com.patson

import com.patson.DemandGenerator.{Demand, MIN_DISTANCE, computeBaseDemandBetweenAirports}
import com.patson.data.{AirportSource, CountrySource}
import com.patson.model.{PassengerType, _}
import org.scalatest.{Matchers, WordSpecLike}

import java.util
import java.util.{ArrayList, Collections}
import scala.collection.mutable.ListBuffer

class DemandGeneratorSpec extends WordSpecLike with Matchers {

  "generateDemand".must {
    "find top 10 destinations for each airport".in {
      val airports = AirportSource.loadAllAirports(fullLoad = false, loadFeatures = true).filter(_.popMiddleIncome > 0)
      val countryRelationships = CountrySource.getCountryMutualRelationships()

      val topDestinationsByFromAirport = airports.map { fromAirport =>
        val demandList = ListBuffer[(Airport, Int)]()
        airports.foreach { toAirport =>
          val distance = Computation.calculateDistance(fromAirport, toAirport)
          if (fromAirport != toAirport && distance >= MIN_DISTANCE) {
            val relationship = countryRelationships.getOrElse((fromAirport.countryCode, toAirport.countryCode), 0)
            val affinity = Computation.calculateAffinityValue(fromAirport.zone, toAirport.zone, relationship)
            val demand = computeBaseDemandBetweenAirports(fromAirport, toAirport, affinity, distance)
            demandList.append((toAirport, demand.total))

          }
        }

        val topDestinations = demandList.sortBy(_._2).reverse.take(10)
        (fromAirport, topDestinations)
      }

      println("From,To1,Demand1,To2,Demand2,To3,Demand3,To4,Demand4,To5,Demand5,To6,Demand6,To7,Demand7,To8,Demand8,To9,Demand9,To10,Demand10")
      topDestinationsByFromAirport.foreach {
        case (fromAirport, topDestinations) =>
          val csvLine = new StringBuilder(fromAirport.iata)
          topDestinations.foreach {
            case (toAirport, totalDemand) =>
              csvLine.append(s",${toAirport.iata},$totalDemand")
          }
          println(csvLine.toString)
      }
    }
  }

  implicit class DemandClassImprovements(demand: Demand) {
    def total: Int = {
      (demand.travelerDemand.total + demand.businessDemand.total + demand.touristDemand.total).toInt
    }
  }

}