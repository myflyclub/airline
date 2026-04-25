package com.patson

import com.patson.data.AirlineSource
import com.patson.model.ManagerBaseTask

object ManagerSimulation {
  def simulate(currentCycle : Int ) = {

    val apDeltas = AirlineSource.loadAllAirlines().flatMap { airline =>
      val managerInfo = airline.getManagerInfo()
      val available = Math.max(0, managerInfo.availableCount)
      val currentAP = airline.getActionPoints()

      val rate =
        if (available == 0 || currentAP > ManagerBaseTask.MAX_CYCLES_STORED_THRESHOLD * available * ManagerBaseTask.GENERATION_RATE) 0.0
        else if (currentAP > ManagerBaseTask.INEFFICIENT_CYCLE_THRESHOLD * available * ManagerBaseTask.GENERATION_RATE) 0.07
        else ManagerBaseTask.GENERATION_RATE

      val gained = rate * available
      if (gained > 0) Some(airline -> gained) else None
    }.to(scala.collection.mutable.Map)

    AirlineSource.adjustAirlineActionPointsBatch(apDeltas)
  }
}