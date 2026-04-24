package com.patson.init

import com.patson.data.{AirlineSource, ManagerSource}
import com.patson.model.{Manager, ManagerBaseTask}

import scala.concurrent.Await
import scala.concurrent.duration.Duration

object ManagerBasePatcher extends App {
  mainFlow()

  def mainFlow(): Unit = {
    val airlines = AirlineSource.loadAllAirlines(fullLoad = true)

    var corrected = 0
    var unchanged = 0

    airlines.foreach { airline =>
      val baseCount = airline.bases.map(_.scale).sum
      val target = Math.max(0, baseCount - 1)
      val current = ManagerSource.countManagerBaseDelegatesByAirline(airline.id)
      val delta = target - current

      if (delta != 0) {
        println(s"[${airline.id}] ${airline.name}: bases=$baseCount, target MANAGER_BASE=$target, current=$current, delta=$delta")
        if (delta > 0) {
          val newManagers = List.fill(delta)(Manager(airline, ManagerBaseTask(), availableCycle = None))
          ManagerSource.saveBusyManagers(newManagers)
        } else {
          ManagerSource.deleteManagerBaseDelegates(airline.id, -delta)
        }
        corrected += 1
      } else {
        unchanged += 1
      }
    }

    println(s"\nDone. Airlines corrected: $corrected, already correct: $unchanged")
    Await.result(actorSystem.terminate(), Duration.Inf)
  }
}
