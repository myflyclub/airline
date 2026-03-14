package com.patson.init

import com.patson.model._
import com.patson.data._
import com.patson.Util
import scala.collection.mutable.Map

object IsolatedAirportPatcher {

  /**
   * Computes isolation levels for all airports based on surrounding population.
   * Returns a map of airport ID -> isolation level, only for airports with level > 0.
   * Does not write to the database.
   */
  def computeIsolation(allAirports: List[Airport]): Map[Int, Int] = {
    val LOOK_RANGE = Array(300, 600, 1200, 2400)
    val isolationByAirportId = Map[Int, Int]()

    allAirports.foreach { airport =>
      var isolationLevel : Int = 0

      val boundaryLongitude = GeoDataGenerator.calculateLongitudeBoundary(airport.latitude, airport.longitude, LOOK_RANGE.last)
      for (i <- 0 until LOOK_RANGE.size) {
        val threshold = LOOK_RANGE(i)
        val populationWithinRange: Long = allAirports.filter { targetAirport =>
          val distance = Util.calculateDistance(airport.latitude, airport.longitude, targetAirport.latitude, targetAirport.longitude)
          distance < threshold && targetAirport.longitude >= boundaryLongitude._1 && targetAirport.longitude <= boundaryLongitude._2
        }.map(_.basePopulation.toLong).sum + airport.basePopulation.toLong
        if (populationWithinRange < 100_000L) { //very isolated
          isolationLevel += 3
        } else if (populationWithinRange < 500_000L) {
          isolationLevel += 2
        } else if (populationWithinRange < 3_000_000L) { //kinda isolated
          isolationLevel += 1
        }
      }
      isolationLevel = (Math.floor( isolationLevel / 2 )).toInt
      if (GameConstants.ISOLATED_COUNTRIES.contains(airport.countryCode) && airport.size <= 4 || GameConstants.ISOLATED_ISLAND_AIRPORTS.contains(airport.iata)) {
        isolationLevel += 1
      }
      if (airport.iata == "IPC") {
        isolationLevel = 9 //this is to give it range to reach SCL
      }
      if (isolationLevel > 0) {
        isolationByAirportId.put(airport.id, isolationLevel)
      }
    }

    isolationByAirportId
  }
}