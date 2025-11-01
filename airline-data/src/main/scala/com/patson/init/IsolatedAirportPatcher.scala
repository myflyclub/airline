package com.patson.init

import com.patson.model._
import com.patson.data._
import com.patson.Util
import scala.collection.mutable.Set
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.Map
import scala.collection.mutable.ListBuffer

object IsolatedAirportPatcher {
   

  import IsolatedTownFeature._
  
  def patchIsolatedAirports() = {
    val LOOK_RANGE = Array(300, 600, 1200, 2400)
    val allAirports = AirportSource.loadAllAirports(true)
    val isolationByAirport = Map[Airport, Int]()


    allAirports.foreach { airport =>
      var isolationLevel : Int = 0

      val boundaryLongitude = GeoDataGenerator.calculateLongitudeBoundary(airport.latitude, airport.longitude, LOOK_RANGE.last)
      for (i <- 0 until LOOK_RANGE.size) {
        val threshold = LOOK_RANGE(i)
        val populationWithinRange = allAirports.filter { targetAirport =>
          val distance = Util.calculateDistance(airport.latitude, airport.longitude, targetAirport.latitude, targetAirport.longitude)
          distance < threshold && targetAirport.longitude >= boundaryLongitude._1 && targetAirport.longitude <= boundaryLongitude._2
        }.map(_.population).sum
        if (populationWithinRange < 100000) { //very isolated
          isolationLevel += 3
        } else if (populationWithinRange < 500000) {
          isolationLevel += 2
        } else if (populationWithinRange < 3000000) { //kinda isolated
          isolationLevel += 1
        }
      }
      isolationLevel = (Math.floor( isolationLevel / 2 )).toInt
      if (GameConstants.ISOLATED_COUNTRIES.contains(airport.countryCode) && airport.size <= 4 || GameConstants.ISOLATED_ISLAND_AIRPORTS.contains(airport.iata)) {
        isolationLevel += 1
      }
      if (airport.iata == "IPC") {
        isolationLevel = 15 //this is to give it range to reach SCL
      }
      if (isolationLevel > 0) {
        isolationByAirport.put(airport, isolationLevel)
      }
    }

    isolationByAirport.foreach {
      case (airport,isolationLevel) =>
        val existingFeatures = airport.getFeatures().filter(_.featureType != AirportFeatureType.ISOLATED_TOWN)
        val newFeatures = existingFeatures :+ AirportFeature(AirportFeatureType.ISOLATED_TOWN, isolationLevel)
        //airport.initFeatures(newFeatures) //CANNOT init features here, features can only be init once.
        AirportSource.updateAirportFeatures(airport.id, newFeatures)
        println(s"$airport isolation level $isolationLevel features ${airport.getFeatures()}")
    }



  }
}  
  