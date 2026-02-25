package com.patson.model

import com.patson.model.Airline
import com.patson.model.Airport
import com.patson.data.PrestigeSource
import com.patson.data.CycleSource

object Prestige {
  def processPrestige(airline : Airline) = {
    var airport = airline.getHeadQuarter().get.airport
    var prestigePoints = PrestigePoints.getPoints(airline.getReputation())
    if (prestigePoints > 0) {
      PrestigeSource.createPrestige(airline.id, airport.id, airline.name, prestigePoints, CycleSource.loadCycle())
      airline.setPrestigePoints(airline.getPrestigePoints() + prestigePoints)
      // TODO: Create/update prestige charm
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