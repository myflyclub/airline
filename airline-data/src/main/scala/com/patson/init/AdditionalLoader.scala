package com.patson.init

import com.patson.model.Airport

import scala.io.Source
import com.patson.model.City
import com.patson.model.Country
import com.patson.model.Destination

import scala.collection.mutable.ListBuffer
import scala.util.Try
import com.patson.data.AirportSource
import com.patson.init.GeoDataGenerator.countryCodeConvert

object AdditionalLoader {
  def loadRemovalAirportIatas() : List[String] = {
    val source = scala.io.Source.fromFile("removal-airports.csv")
    try {
      val removalAirportSource = source.getLines()
      val result = removalAirportSource.filter(!_.startsWith("#")).map(_.trim)
      result.toList
    } finally {
      source.close()
    }
  }

  def loadAdditionalAirports() : List[Airport] = {
    val source = scala.io.Source.fromFile("additional-airports.csv")
    try {
      val additionalAirportSource = source.getLines()
      val additionalAirports = ListBuffer[Airport]()
      additionalAirportSource.foreach { line =>
        if (!line.startsWith("#")) {
          val tokens = line.trim().split(",(?=([^\"]*\"[^\"]*\")*[^\"]*$)", -1).transform { token =>
            if (token.startsWith("\"") && token.endsWith("\"")) {
              token.substring(1, token.length() - 1) 
            } else {
              token
            }
          }
          val airport = Airport(iata = tokens(0), icao = tokens(1), name = tokens(2), latitude = tokens(3).toDouble, longitude = tokens(4).toDouble, countryCode = tokens(5), city = tokens(6), zone = tokens(7), size = tokens(8).toInt, basePopulation = 0, basePopMiddleIncome = 0, basePopElite = 0, baseIncome = 0)
          additionalAirports += airport
        }
      }
      
      println("Additional Airports!!: ")
      additionalAirports.foreach(println)
      additionalAirports.toList
    } finally {
      source.close()
    }
  }

  def loadDestinations(): List[Destination] = {
    val source = scala.io.Source.fromFile("destinations.csv")
    try {
      val destinationsSource = source.getLines()
      val destinations = ListBuffer[Destination]()
      var id = 0
      destinationsSource.foreach { line =>
        if (!line.startsWith("#") && line.trim().nonEmpty) {
          import com.patson.model.DestinationType

          val tokens = line.trim().split(",(?=([^\"]*\"[^\"]*\")*[^\"]*$)", -1)
          id += 1
          val airportCode = tokens(1).toString
          val airport = AirportSource.loadAirportByIata(airportCode)
          val name = tokens(0)
          val destinationType = DestinationType.withNameSafe(tokens(5).trim).getOrElse(DestinationType.ELITE_DESTINATION)
          val description = tokens(4)
          val strength = 0
          val latitude = Try(tokens(6).trim.toDouble).getOrElse(0.0)
          val longitude = Try(tokens(7).trim.toDouble).getOrElse(0.0)
          val countryCode = tokens(3)

          airport match {
            case Some(airport) =>
              val destination = Destination(id, airport, name, destinationType, strength, description, latitude, longitude, countryCode)
              destinations += destination
            case None =>
              println(s"Invalid airport type: $airportCode. Skipping this entry.")
          }
        }
      }
      println("Loading destinations...")
      destinations.toList
    } finally {
      source.close()
    }
  }

  def loadAdditionalCities() : List[City] = {
    val source = scala.io.Source.fromFile("additional-cities.csv")
    try {
      val additionalCitySource = source.getLines()
      val additionalCities = ListBuffer[City]()
      additionalCitySource.foreach { line =>
        if (!line.startsWith("#") && line.trim().nonEmpty) {
          
          val tokens = line.trim().split(",(?=([^\"]*\"[^\"]*\")*[^\"]*$)", -1).transform { token =>
  //        val tokens = line.trim().split(",", -1).transform { token =>
            if (token.startsWith("\"") && token.endsWith("\"")) { 
              token.substring(1, token.length() - 1) 
            } else {
              token
            }
          }
          val city = City(
            name = tokens(0),
            latitude = tokens(1).toDouble,
            longitude = tokens(2).toDouble,
            countryCode = countryCodeConvert(tokens(3)),
            population = tokens(4).toInt,
            income = if(tokens(5).nonEmpty){
              tokens(5).toInt
            }  else {
              Country.DEFAULT_UNKNOWN_INCOME
            }
          )
          additionalCities += city
        }
      }
      println("Loading cities... ")
      additionalCities.toList
    } finally {
      source.close()
    }
  }
}