package com.patson.init

import com.patson.data.AirportSource
import com.patson.init.GeoDataGenerator.CsvAirport
import com.patson.model._

import scala.concurrent.Await
import scala.concurrent.duration.Duration

/**
  * Use {@link AirportGeoPatcher} instead
  */
object AirportRunwayPatcher extends App {

  //implicit val materializer = FlowMaterializer()

  mainFlow

  def mainFlow() {
    val airports = AirportSource.loadAllAirports(true)
    // Runway management is now handled separately via AirportSource helper functions
    // This patcher is deprecated - use AirportGeoPatcher instead
    airports.foreach { airport =>
      val runways = AirportSource.loadAirportRunways(airport.id)
      AirportSource.updateAirportRunways(airport.id, runways)
    }
    AirportSource.updateAirports(airports)

    Await.result(actorSystem.terminate(), Duration.Inf)

  }


}