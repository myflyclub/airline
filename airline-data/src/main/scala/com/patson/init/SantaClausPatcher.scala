package com.patson.init

import java.sql.Connection

import com.mchange.v2.c3p0.ComboPooledDataSource
import com.patson.data.Constants.{DATABASE_CONNECTION, DATABASE_PASSWORD, DATABASE_USER, DB_DRIVER}
import com.patson.data.{AirlineSource, AirportSource, ChristmasSource, LinkSource, Meta}
import com.patson.model.christmas.SantaClausInfo
import com.patson.model.{ECONOMY, Link, LinkClassValues}

import scala.collection.mutable.ListBuffer
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.Random

object SantaClausPatcher extends App {
  Class.forName(DB_DRIVER)
  val dataSource = new ComboPooledDataSource()
  dataSource.setUser(DATABASE_USER)
  dataSource.setPassword(DATABASE_PASSWORD)
  dataSource.setJdbcUrl(DATABASE_CONNECTION)
  dataSource.setMaxPoolSize(10)
  val connection = getConnection()
  createSchema(connection)
  connection.close

  println("Select mode:")
  println("  1 - Full reset: clear ALL existing data and assign for every airline")
  println("  2 - Incremental: only assign for airlines that don't have an entry yet")
  print("Enter choice (1 or 2): ")
  val choice = scala.io.StdIn.readLine().trim

  if (choice != "1" && choice != "2") {
    println("Invalid choice — exiting without changes.")
    System.exit(1)
  }

  //init the data
  val airports = AirportSource.loadAllAirports().filter(_.size > SantaClausInfo.AIRPORT_SIZE_THRESHOLD)
  val allAirlines = AirlineSource.loadAllAirlines(true)

  val airlinesToProcess = if (choice == "1") {
    println("Clearing all existing santa claus data...")
    ChristmasSource.deleteAllSantaClausInfo()
    allAirlines
  } else {
    val existingAirlineIds = ChristmasSource.loadSantaClausInfoByCriteria(List.empty).map(_.airline.id).toSet
    println(s"Found ${existingAirlineIds.size} airlines already assigned — skipping them.")
    allAirlines.filterNot(a => existingAirlineIds.contains(a.id))
  }

  val entries = ListBuffer[SantaClausInfo]()
  airlinesToProcess.foreach { airline =>
    val candidateAirports = airline.getHeadQuarter() match {
      case Some(hq) => airports.filterNot( _.id == hq.airport.id) //do not include hq. otherwise bonus is too powerful
      case None => airports
    }
    val targetAirport = candidateAirports(Random.nextInt(candidateAirports.size))
    entries.append(SantaClausInfo(targetAirport, airline, SantaClausInfo.MAX_ATTEMPTS, List.empty, false, None))
  }

  ChristmasSource.saveSantaClausInfo(entries.toList)
  println(s"Done — saved ${entries.size} entries.")

  def getConnection(enforceForeignKey: Boolean = true) = {
    dataSource.getConnection()
  }

  def createSchema(connection : Connection) = {
    Meta.createSantaClaus(connection)
  }
}