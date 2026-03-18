

package com.patson

import java.util.concurrent.TimeUnit
import org.apache.pekko.actor.Props
import org.apache.pekko.actor.Actor
import com.patson.data._
import com.patson.model.{Link, TransportType}
import com.patson.stream.{CycleCompleted, CycleStart, SimulationEventStream}
import com.patson.model.CountryAirlineTitle
import com.patson.util.{AirlineCache, AirplaneOwnershipCache, AirportCache, AirportStatisticsCache}

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.{Duration, FiniteDuration}

object MainSimulation extends App {
  val CYCLE_DURATION : Int = 60 * 15
  val SCHEDULE_BUFFER_SECS : Int = 30
  val SCHEDULE_OVERHEAD_FACTOR : Double = 1.2
  var currentWeek: Int = 0

  mainFlow

  def mainFlow() = {
    val actor = actorSystem.actorOf(Props[MainSimulationActor])
    Await.result(actorSystem.whenTerminated, Duration.Inf)
  }


  def initializeCaches() = {
    println("Initializing caches...")
    val startTime = System.currentTimeMillis()
    AirportCache.getAllAirports(true)
    val endTime = System.currentTimeMillis()
    println(s"Cache initialization completed in ${endTime - startTime}ms")
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

    println("Airport assets simulation")
    AirportAssetSimulation.simulate(cycle, linkRidershipDetails)
    println("Airport assets simulation done")

    println("Airplane simulation")
    val airplanes = AirplaneSimulation.airplaneSimulation(cycle)
    println("Airplane simulation done")

    println("Airline simulation")
    AirlineSimulation.airlineSimulation(cycle, flightLinkResult, loungeResult, airplanes, paxStatsByAirlineId)
    println("Airline simulation done")

    println("Airplane model simulation")
    AirplaneModelSimulation.simulate(cycle)
    println("Airplane model simulation done")

    //purge log
    println("Purging logs")
    LogSource.deleteLogsBeforeCycle(cycle - com.patson.model.Log.RETENTION_CYCLE)

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
    OilSimulation.simulate(currentCycle) //simulate at the beginning of a new cycle
    println("Loan simulation")
    LoanInterestRateSimulation.simulate(currentCycle) //simulate at the beginning of a new cycle
    //refresh delegates
    println("Manager simulation")
    ManagerSimulation.simulate(currentCycle)

    println("Post cycle link simulation")
    LinkSimulation.simulatePostCycle(currentCycle)

    println(s"Post cycle done $currentCycle")
  }


  /**
    * The simulation can be seen like this:
    * On week(cycle) n. It starts the long simulation (pax simulation) at the "END of the week"
    * when it finishes computing the pax of the past week. It sets the current week to next week (which indicates a beginning of week n + 1)
    * It then runs some postCycle task (these tasks should be short and can be regarded as things to do at the Beginning of a week)
    *
    */
  class MainSimulationActor extends Actor {
    currentWeek = CycleSource.loadCycle()
    private var lastExecutionMs: Long = 0L  // in-memory only; 0 means start immediately

    private def calculateDelay(): FiniteDuration = {
      if (lastExecutionMs == 0L) {
        Duration.Zero
      } else {
        val anticipatedMs = (lastExecutionMs * SCHEDULE_OVERHEAD_FACTOR).toLong + SCHEDULE_BUFFER_SECS * 1000L
        val delayMs = Math.max(0L, CYCLE_DURATION * 1000L - anticipatedMs)
        println(s"Next cycle delay: ${delayMs / 1000}s (anticipated: ${anticipatedMs / 1000}s, last execution: ${lastExecutionMs / 1000}s)")
        Duration(delayMs, TimeUnit.MILLISECONDS)
      }
    }

    override def preStart(): Unit = {
      context.system.scheduler.scheduleOnce(Duration.Zero, self, Start)
    }

    def receive = {
      case Start =>
        status = SimulationStatus.IN_PROGRESS
        val cycleStartTime = System.currentTimeMillis()
        try {
          val endTime = startCycle(currentWeek)

          lastExecutionMs = endTime - cycleStartTime
          currentWeek += 1
          CycleSource.setCycle(currentWeek)
          status = SimulationStatus.WAITING_CYCLE_START
          postCycle(currentWeek) //post cycle do some quick updates, no long simulation

          //notify the websockets via EventStream
          println("Publish Cycle Complete message")
          SimulationEventStream.publish(CycleCompleted(currentWeek - 1, endTime), None)
        } catch {
          case e : Exception =>
            println(s"!!!!!!! Cycle $currentWeek failed with exception: ${e.getClass.getSimpleName}: ${e.getMessage}. Will retry next tick.")
            lastExecutionMs = 0L  // reset so retry fires immediately
            status = SimulationStatus.WAITING_CYCLE_START
        } finally {
          context.system.scheduler.scheduleOnce(calculateDelay(), self, Start)
        }
    }
  }


  case class Start()

  var status : SimulationStatus.Value = SimulationStatus.WAITING_CYCLE_START
  object SimulationStatus extends Enumeration {
    type ManagerTaskType = Value
    val IN_PROGRESS, WAITING_CYCLE_START = Value
  }


}
