package models

import com.patson.model.Airline
import com.patson.model.Airport

case class AirportWithChampionAndStats(airport: Airport, travelRate: Int, reputation: Double, congestion: Option[Int], champion: Option[Airline], contested: Option[Airline])
