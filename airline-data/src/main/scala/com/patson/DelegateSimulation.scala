package com.patson

import com.patson.data.{AirlineSource, DelegateSource}

object DelegateSimulation {
  def simulate(currentCycle : Int ) = {

    val apDeltas = AirlineSource.loadAllAirlines().flatMap { airline =>
      val delegateInfo = airline.getDelegateInfo()
      val available = Math.max(0, delegateInfo.availableCount)
      val currentAP = airline.getActionPoints()

      val rate =
        if (available == 0 || currentAP > 24.0 * 2 * available) 0.0
        else if (currentAP > 8.0 * 2 * available) 0.08
        else 0.1

      val gained = rate * available
      if (gained > 0) Some(airline -> gained) else None
    }.to(scala.collection.mutable.Map)

    AirlineSource.adjustAirlineActionPointsBatch(apDeltas)
  }
}