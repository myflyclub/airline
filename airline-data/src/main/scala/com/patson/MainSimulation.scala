package com.patson

import java.util.concurrent.TimeUnit
import org.apache.pekko.actor.Props
import org.apache.pekko.actor.Actor
import com.patson.data._
import com.patson.stream.{CycleCompleted, CycleStart, SimulationEventStream}
import com.patson.model.CountryAirlineTitle
import com.patson.util.{AirlineCache, AirplaneOwnershipCache, AirportCache, AirportStatisticsCache}

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

object MainSimulation extends App {
  val CYCLE_DURATION : Int = 60 * 4
  val SCHEDULE_BUFFER_SECS : Int = 30
  val SCHEDULE_OVERHEAD_FACTOR : Double = 1.1
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

  // Actor Messages
  case object ExecuteProcessing
  case object BroadcastAndAdvance
  case object ScheduleNext

  /**
    * The simulation can be seen like this:
    * On week(cycle) n. It starts the long simulation (pax simulation) at the "END of the week"
    * when it finishes computing the pax of the past week. It sets the current week to next week (which indicates a beginning of week n + 1)
    * It then runs some postCycle task (these tasks should be short and can be regarded as things to do at the Beginning of a week)
    *
    */
  class MainSimulationActor extends Actor {
    val CYCLE_INTERVAL_MS = CYCLE_DURATION * 1000L
    val DB_REST_BUFFER_MS = SCHEDULE_BUFFER_SECS * 1000L

    var currentWeek = CycleSource.loadCycle()
    var lastExecutionMs: Long = 0L
    var targetDeadline: Long = 0L // In-memory dynamic deadline

    override def preStart(): Unit = {
      // First run executes immediately to update users ASAP
      context.system.scheduler.scheduleOnce(Duration.Zero, self, ExecuteProcessing)
    }

    def receive = {
      case ScheduleNext =>
        status = SimulationStatus.WAITING_CYCLE_START
        val estimatedExecution = (lastExecutionMs * SCHEDULE_OVERHEAD_FACTOR).toLong
        val leadTime = estimatedExecution + DB_REST_BUFFER_MS

        val wakeUpTime = targetDeadline - leadTime
        val delayUntilWakeUp = Math.max(0L, wakeUpTime - System.currentTimeMillis())

        println(s"Next cycle will wake up in ${delayUntilWakeUp / 1000}s (estimated exec: ${estimatedExecution / 1000}s)")
        context.system.scheduler.scheduleOnce(Duration(delayUntilWakeUp, TimeUnit.MILLISECONDS), self, ExecuteProcessing)

      case ExecuteProcessing =>
        status = SimulationStatus.IN_PROGRESS
        val startMs = System.currentTimeMillis()

        try {
          startCycle(currentWeek)
          postCycle(currentWeek + 1)

          lastExecutionMs = System.currentTimeMillis() - startMs

          // Determine DB rest. If first run (targetDeadline is 0), just take the minimum buffer.
          // Otherwise, sync to the target deadline, enforcing the minimum buffer.
          val delayUntilBroadcast = if (targetDeadline == 0L) {
            DB_REST_BUFFER_MS
          } else {
            val timeToDeadline = targetDeadline - System.currentTimeMillis()
            Math.max(timeToDeadline, DB_REST_BUFFER_MS)
          }

          context.system.scheduler.scheduleOnce(Duration(delayUntilBroadcast, TimeUnit.MILLISECONDS), self, BroadcastAndAdvance)

        } catch {
          case e : Exception =>
            println(s"!!!!!!! Cycle $currentWeek failed with exception: ${e.getClass.getSimpleName}: ${e.getMessage}. Retrying in 60s.")
            status = SimulationStatus.WAITING_CYCLE_START
            context.system.scheduler.scheduleOnce(Duration(60, TimeUnit.SECONDS), self, ExecuteProcessing)
        }

      case BroadcastAndAdvance =>
        val endTime = System.currentTimeMillis()
        println("Publish Cycle Complete message")
        SimulationEventStream.publish(CycleCompleted(currentWeek, endTime), None)

        currentWeek += 1
        CycleSource.setCycle(currentWeek)

        targetDeadline = System.currentTimeMillis() + CYCLE_INTERVAL_MS
        self ! ScheduleNext
    }
  }

  var status : SimulationStatus.Value = SimulationStatus.WAITING_CYCLE_START
  object SimulationStatus extends Enumeration {
    type ManagerTaskType = Value
    val IN_PROGRESS, WAITING_CYCLE_START = Value
  }

}