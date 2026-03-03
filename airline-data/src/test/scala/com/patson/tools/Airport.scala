package com.patson.tools

import com.patson.data.{AirportSource, AirportStatisticsSource}
import com.patson.model._

object AirportDataExporter extends App {

  println("--- Upkeep and Upgrade Costs ---")
  val iatas = List("JFK", "ADD", "DXB", "MAD", "SCE", "PVG")
  val baseSizes = List(4, 8, 12, 16)
  val airline = Airline("TestAirline", id = 999)
  val airlineMHQ = Airline("TestMegaHQ", id = 1000, airlineType = MegaHqAirline)
  val airlineRegional = Airline("TestMegaHQ", id = 1001, airlineType = RegionalAirline)
  val airports = iatas.flatMap(iata => AirportSource.loadAirportByIata(iata, true))

  println("Airport, BaseSize, Upkeep, UpgradeCost")
  for (airport <- airports; baseSize <- baseSizes) {
    val base = AirlineBase(airline, airport, airport.countryCode, baseSize, foundedCycle = 0, headquarter = false)
    println(s"${airport.iata}, ${airport.rating.overallDifficulty}, $baseSize, ${base.getUpkeep}, ${base.getValue}")

    val baseMHQ = AirlineBase(airlineMHQ, airport, airport.countryCode, baseSize, foundedCycle = 0, headquarter = true)
    println(s"${airport.iata}, ${airport.rating.overallDifficulty}, $baseSize, ${baseMHQ.getUpkeep}, ${baseMHQ.getValue}")

    val baseAirlineRegional = AirlineBase(airlineRegional, airport, airport.countryCode, baseSize, foundedCycle = 0, headquarter = true)
    println(s"${airport.iata}, ${airport.rating.overallDifficulty}, $baseSize, ${baseAirlineRegional.getUpkeep}, ${baseAirlineRegional.getValue}")
  }

  println("\n--- All Travel Rates ---")
  val allAirports: List[Airport] = AirportSource.loadAllAirports().sortBy(_.popMiddleIncome)
  val airportStats = AirportStatisticsSource.loadAllAirportStats().groupBy(_.airportId)

  println("iata, size, travel rate, rep, from pax, from demand")
  allAirports.foreach { airport =>
    airportStats.get(airport.id) match {
      case Some(stat) =>
        val travelRate = Airport.travelRateAdjusted(stat.head.fromPax, stat.head.baselineDemand, airport.size)
        println(s"${airport.iata}, ${airport.size}, $travelRate, ${stat.head.reputation}, ${stat.head.fromPax}, ${stat.head.baselineDemand}")
      case None =>
        println(s"${airport.iata}, ${airport.size}, 0, 0, 0, 0")
    }
  }
}