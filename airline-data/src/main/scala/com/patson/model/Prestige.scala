package com.patson.model

import com.patson.model.Airline
import com.patson.model.Airport
import com.patson.model.AirportFeatureType
import com.patson.model.PrestigeFeature
import com.patson.data.PrestigeSource
import com.patson.data.CycleSource

object Prestige {
  def processPrestige(airline : Airline) = {
    var airport = airline.getHeadQuarter().get.airport
    var prestigePoints = PrestigePoints.getPoints(airline.getReputation())
    if (prestigePoints > 0) {
      PrestigeSource.createPrestige(airline.id, airport.id, airline.name, prestigePoints, CycleSource.loadCycle())
      airline.setPrestigePoints(airline.getPrestigePoints() + prestigePoints)
    }
  }

  /**
   * Update the prestige charm at an airport based on total prestige points
   * If total prestige is 0, removes the prestige charm
   * Otherwise, upserts the prestige charm with the total as strength
   */
  def updatePrestigeCharmForAirport(airportId: Int): Unit = {
    val prestigeFromTable = PrestigeSource.sumPrestigePointsByAirport(airportId)
    val prestigeFromAirlines = com.patson.data.AirlineSource.sumPrestigePointsByHeadquarterAirport(airportId)
    val totalPrestige = prestigeFromTable + prestigeFromAirlines

    if (totalPrestige == 0) {
      com.patson.data.AirportSource.deleteAirportFeature(airportId, AirportFeatureType.PRESTIGE_CHARM)
    } else {
      val prestigeCharm = PrestigeFeature(totalPrestige)
      com.patson.data.AirportSource.saveAirportFeature(airportId, prestigeCharm)
    }
  }
}

object PrestigePoints {
  val thresholds = List(
    600 -> 1,
    1100 -> 2,
    1500 -> 3,
    1800 -> 4,
    2000 -> 5
  )

  def getPoints(reputation: Double): Int = {
    var points = 0
    for ((threshold, thresholdPoints) <- thresholds) {
      if (reputation >= threshold) {
        points = thresholdPoints
      } else {
        return points
      }
    }
    points
  }
}