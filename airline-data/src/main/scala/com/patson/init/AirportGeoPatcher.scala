package com.patson.init

import com.patson.data.{AirportSource, CitySource, CountrySource}
import com.patson.init.GeoDataGenerator.{CsvAirport}
import com.patson.model._

import scala.collection.mutable.{ArrayBuffer, ListBuffer}
import scala.concurrent.Await
import scala.concurrent.duration.Duration

/**
  * Regenerate ALL airport data (pops, runway etc) without wiping the existing airport DB
  *
  * It will attempt to update the airport if it's already existed and insert airport otherwise
  *
  * it will NOT purge airports that no longer in the CSV file tho
  *
  */
object AirportGeoPatcher extends App {

  mainFlow

  def mainFlow() {
    val existingAirports = AirportSource.loadAllAirports(false)
    val iataToGeneratedId : Map[String, Int] = existingAirports.map(airport => (airport.iata, airport.id)).toMap //just load to get IATA to our generated ID

    val csvAirports : List[CsvAirport] = Await.result(GeoDataGenerator.getAirport(), Duration.Inf).map { csvAirport =>
      val rawAirport = csvAirport.airport
      val csvAirportId = csvAirport.csvAirportId
      val scheduleService = csvAirport.scheduledService

      iataToGeneratedId.get(rawAirport.iata) match {
        case Some(savedId) => CsvAirport(rawAirport.copy(id = savedId), csvAirportId, scheduleService)
        case None => csvAirport
      }
    }
    val runways : Map[Int, List[Runway]] = Await.result(GeoDataGenerator.getRunway(), Duration.Inf)

    val cities = AdditionalLoader.loadAdditionalCities()
    //make sure cities are saved first as we need the id for airport info
    try {
      CitySource.deleteAllCitites()
      CitySource.saveCities(cities)
    } catch {
      case e : Throwable => e.printStackTrace()
    }

    val (computedAirports, cityAirportRelationshipsByIata) = GeoDataGenerator.generateAirportData(csvAirports, runways, cities)

    val newAirports = computedAirports.filter(_.id == 0)
    val updatingAirports = computedAirports.filter(_.id > 0)

    zeroRemovalAirportPopulations() //GeoDataGenerator.generateAirportData() ignores removal iata, so let's just zero them

    GeoDataGenerator.setAirportRunwayDetails(csvAirports, runways)
    println(s"Updating ${updatingAirports.length} Airports")
    AirportSource.updateAirports(updatingAirports)

    println(s"Creating ${newAirports.length} Airports")
    AirportSource.saveAirports(newAirports)

    val airportByIata = computedAirports.groupBy(_.iata)
    val cityAirportRelationships = cityAirportRelationshipsByIata.flatMap {
      case (iata, relationships) =>
        airportByIata.get(iata).map(a => (a.head.id, relationships))
    }.toMap
    AirportSource.saveCityAirportRelationships(cityAirportRelationships)

    val deletingAirportIds = existingAirports.map(_.id).diff(computedAirports.map(_.id))
    println(s"Could delete ${deletingAirportIds.length} Airports")
    val maybeDeleteAirports = AirportSource.loadAirportsByIds(deletingAirportIds)
    println(maybeDeleteAirports.map(_.iata).mkString(", "))

    val reloadAirports = AirportSource.loadAllAirports()
    GeoDataGenerator.buildCountryData(reloadAirports, update = true)

    //Features
    DestinationsPatcher.loadDestinations()
    AirportFeaturePatcher.patchFeatures()

    Await.result(actorSystem.terminate(), Duration.Inf)
  }

  /**
    * Zero out population values for airports in the removal list
    * This sets basePopulation, basePopMiddleIncome, and basePopElite to 0 for all airports
    * listed in the removal-airports.csv file
    */
  def zeroRemovalAirportPopulations(): Unit = {
    val removalAirportIatas = AdditionalLoader.loadRemovalAirportIatas()
    
    println(s"Loading ${removalAirportIatas.size} removal airports to zero their populations")
    
    val airportsToUpdate = removalAirportIatas.flatMap { iata =>
      AirportSource.loadAirportByIata(iata, fullLoad = false) match {
        case Some(airport) =>
          // Create a copy with zeroed population values
          val updatedAirport = airport.copy(
            basePopulation = 0,
            basePopMiddleIncome = 0,
            basePopElite = 0
          )
          Some(updatedAirport)
        case None =>
//          println(s"Warning: Airport with IATA $iata not found in database")
          None
      }
    }
    
    if (airportsToUpdate.nonEmpty) {
      println(s"Updating ${airportsToUpdate.size} airports to zero population values")
      AirportSource.updateAirports(airportsToUpdate)
      println("Successfully zeroed population values for removal airports")
    } else {
      println("No airports found to update")
    }
  }

}