package com.patson.init

import com.patson.data.{AirlineSource, AirportSource, ChristmasSource, Meta}
import com.patson.model.{Airport, Computation}
import com.patson.model.christmas.SantaClausInfo
import com.patson.model.lostchild.LostChildInfo

import scala.collection.mutable.ListBuffer
import scala.util.Random

object LostChildPatcher extends App {
  val MIN_DISTANCE_FROM_HQ = 6000

  val choice = args.headOption.map(_.trim).getOrElse("")
  if (choice != "1" && choice != "2") {
    println("Usage: LostChildPatcher <1|2>")
    println("  1 - Full reset: clear ALL existing data and assign for every airline")
    println("  2 - Incremental: only assign for airlines that don't have an entry yet")
    System.exit(1)
  }

  val allAirports = AirportSource.loadAllAirports()
  val airportById = allAirports.map(a => a.id -> a).toMap

  val candidateAirports = allAirports.filter(a => LostChildInfo.CULTURAL_AFFINITIES.exists(ca => a.zone.contains(ca))).filter(_.size >= LostChildInfo.AIRPORT_SIZE_THRESHOLD)

  val allAirlines = AirlineSource.loadAllAirlines(true)

  val airlinesToProcess = if (choice == "1") {
    println("Clearing all existing lost child data...")
    val connection = Meta.getConnection()
    Meta.createSantaClaus(connection)
    connection.close()
    allAirlines
  } else {
    val existingAirlineIds = ChristmasSource.loadSantaClausInfoByCriteria(List.empty).map(_.airline.id).toSet
    println(s"Found ${existingAirlineIds.size} airlines already assigned — skipping them.")
    allAirlines.filterNot(a => existingAirlineIds.contains(a.id))
  }

  println(s"Found ${candidateAirports.size} candidate airports with qualifying cultural affinities.")

  val entries = ListBuffer[SantaClausInfo]()
  airlinesToProcess.foreach { airline =>
    val targets = airline.getHeadQuarter() match {
      case Some(hq) =>
        val hqAirport = airportById.getOrElse(hq.airport.id, hq.airport)
        val farEnough = candidateAirports
          .filterNot(_.id == hq.airport.id)
          .filter(a => Computation.calculateDistance(hqAirport, a) >= MIN_DISTANCE_FROM_HQ)
        if (farEnough.nonEmpty) farEnough
        else {
          println(s"  Warning: no targets ≥${MIN_DISTANCE_FROM_HQ}km from HQ for airline ${airline.name} — using all candidates")
          candidateAirports.filterNot(_.id == hq.airport.id)
        }
      case None => candidateAirports
    }
    val targetAirport = targets(Random.nextInt(targets.size))
    entries.append(SantaClausInfo(targetAirport, airline, LostChildInfo.MAX_ATTEMPTS, List.empty, found = false, None))
  }

  ChristmasSource.saveSantaClausInfo(entries.toList)
  println(s"Done — saved ${entries.size} entries.")
}
