package com.patson.tools

import scala.concurrent.Await
import scala.concurrent.duration.Duration

import com.patson.{SimulationConfig, SimulationCycleRunner, actorSystem}
import com.patson.data.Constants._
import com.patson.data.{CycleSource, Meta}
import com.patson.stream.{CycleCompleted, SimulationEventStream}

object RunOneSimulationCycle {
  private val rowCountTables = List(
    CYCLE_TABLE,
    LINK_TABLE,
    LINK_CONSUMPTION_TABLE,
    PASSENGER_ROUTE_HISTORY_TABLE,
    PASSENGER_LINK_HISTORY_TABLE,
    LINK_STATISTICS_TABLE,
    AIRPORT_STATISTICS_TABLE,
    COUNTRY_MARKET_SHARE_TABLE,
    AIRLINE_STATISTICS_TABLE,
    WORLD_STATISTICS_TABLE,
    PASSENGER_MISSED_DEMAND_TABLE
  )

  def main(args: Array[String]): Unit = {
    var exitCode = 0
    try {
      run(args)
    } catch {
      case e: Throwable =>
        exitCode = 1
        e.printStackTrace()
    } finally {
      shutdown(exitCode)
    }
  }

  private def run(args: Array[String]): Unit = {
    SimulationConfig.logRuntimeContext("run-one-cycle")
    SimulationConfig.validateClonedSchemaForExperiment("one-cycle simulation")
    SimulationConfig.validateRouteKernelSchema()

    val cycle = CycleSource.loadCycle()
    println(s"oneCycle phase=before cycle=$cycle")
    printRowCounts("before")

    val startMs = System.currentTimeMillis()
    SimulationCycleRunner.startCycle(cycle)

    val nextCycle = cycle + 1
    CycleSource.setCycle(nextCycle)
    SimulationCycleRunner.postCycle(nextCycle)
    SimulationEventStream.publish(CycleCompleted(cycle, System.currentTimeMillis()), None)

    val durationMs = System.currentTimeMillis() - startMs
    println(s"oneCycle phase=after cycle=$cycle nextCycle=$nextCycle durationMs=$durationMs")
    printRowCounts("after")
    RouteKernelChecksumReport.report(RouteKernelChecksumReport.defaultTables).foreach(result => println(result.toString))
  }

  private def shutdown(exitCode: Int): Unit = {
    try {
      if (!Meta.dataSource.isClosed) {
        Meta.dataSource.close()
      }
    } catch {
      case e: Exception => println(s"oneCycle phase=shutdown warning=${e.getMessage}")
    }

    actorSystem.terminate()
    try {
      Await.result(actorSystem.whenTerminated, Duration(30, "seconds"))
    } catch {
      case e: Exception => println(s"oneCycle phase=shutdown actorSystemTimeout=${e.getMessage}")
    }
    System.exit(exitCode)
  }

  private def printRowCounts(phase: String): Unit = {
    rowCountTables.foreach { table =>
      println(s"rowCount phase=$phase table=$table rows=${countRows(table)}")
    }
  }

  private def countRows(table: String): Long = {
    require(table.matches("[A-Za-z0-9_]+"), s"Unsafe table name '$table'")
    Meta.withTimedConnection(s"count-$table") { connection =>
      val statement = connection.createStatement()
      val resultSet = statement.executeQuery(s"SELECT COUNT(*) FROM `$table`")
      try {
        resultSet.next()
        resultSet.getLong(1)
      } finally {
        resultSet.close()
        statement.close()
      }
    }
  }
}
