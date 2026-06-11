package com.patson

import com.patson.data._
import com.patson.model.CountryAirlineTitle
import com.patson.stream.{CycleStart, SimulationEventStream}
import com.patson.util.{AirlineCache, AirplaneOwnershipCache, AirportCache, AirportStatisticsCache}

object SimulationCycleRunner {
  def initializeCaches() = {
    println("Initializing caches...")
    val startTime = System.currentTimeMillis()
    AirportCache.getAllAirports(true)
    val endTime = System.currentTimeMillis()
    println(s"Airport cache initialization completed in ${endTime - startTime}ms")
  }

  def invalidateCaches() = {
    AirlineCache.invalidateAll()
    AirportCache.invalidateAll()
    AirportStatisticsCache.invalidateAll()
    AirplaneOwnershipCache.invalidateAll()
    CountryAirlineTitle.invalidateAll()
  }

  def startCycle(cycle : Int) = {
    val cycleStartTime = System.currentTimeMillis()
    SimulationConfig.logRuntimeContext(s"cycle-$cycle")
    println("cycle " + cycle + " starting!")
    if (cycle == 1) { //initialize it
      OilSimulation.simulate(1)
      LoanInterestRateSimulation.simulate(1)
    }

    SimulationEventStream.publish(CycleStart(cycle, cycleStartTime), None)
    invalidateCaches()
    initializeCaches()

    UserSimulation.simulate(cycle)
    println("Event simulation")
    EventSimulation.simulate(cycle)
    println("Event simulation done")

    println("Link simulation starting")
    val (flightLinkResult, loungeResult, linkRidershipDetails, paxStatsByAirlineId) = LinkSimulation.linkSimulation(cycle)
    println("Link simulation done")

    println("Airport simulation")
    val airportChampionInfo = AirportSimulation.airportSimulation(cycle, linkRidershipDetails)
    println("Airport simulation done")

    println("Alliance simulation")
    AllianceSimulation.simulate(flightLinkResult, loungeResult, paxStatsByAirlineId, airportChampionInfo, cycle)
    println("Alliance simulation done")

    println("Airplane simulation")
    val airplanes = AirplaneSimulation.airplaneSimulation(cycle)
    println("Airplane simulation done")

    println("Airline simulation")
    AirlineSimulation.airlineSimulation(cycle, flightLinkResult, loungeResult, airplanes, paxStatsByAirlineId)
    println("Airline simulation done")

    println("Airplane model simulation")
    AirplaneModelSimulation.simulate(cycle)
    println("Airplane model simulation done")

    //purge history
    println("Purging link history")
    ChangeHistorySource.deleteLinkChangeByCriteria(List(("cycle", "<", cycle - 400)))

    //purge airline modifier
    println("Purging airline modifier")
    AirlineSource.deleteAirlineModifierByExpiry(cycle)

    val cycleEnd = System.currentTimeMillis()

    println(">>>>> cycle " + cycle + " spent " + (cycleEnd - cycleStartTime) / 1000 + " secs")
    cycleEnd
  }

  /**
    * Things to be done after cycle ticked. These should be relatively short operations (data reconciliation etc)
    * @param currentCycle
    */
  def postCycle(currentCycle : Int) = {
    println("Oil simulation")
    OilSimulation.simulate(currentCycle)
    println("Loan simulation")
    LoanInterestRateSimulation.simulate(currentCycle)
    println("Add action points")
    ManagerSimulation.simulate(currentCycle)
    println("Post cycle link simulation")
    LinkSimulation.simulatePostCycle(currentCycle)

    println(s"Post cycle done $currentCycle")
  }
}
