package com.patson.model

import com.patson.data.{AirportSource, CountrySource, LinkStatisticsSource}
import com.patson.model.AirportFeatureType._
import com.patson.util.{AirportCache, CountryCache}

case class AirportRating(economicPowerRating: Int, countryPowerRating: Int, featurePower: Int) {
  /**
   * Return 1 - 100 difficulty rating.
   * Used by airlineBase and profile app to price cost and starting money
   */
  val overallDifficulty = Math.max(1.0, Math.min(100.0, economicPowerRating * 0.6 + countryPowerRating * 0.2 + featurePower * 0.7)).toInt
}

object AirportRating {
  private val modelAirportPower: Long = 24_767_260 //Use static value here, HND before boost
  // By adding 'L', we ensure the multiplication is done using Longs, preventing overflow
  private val modelCountryPower: Long = 341465079L * 89702 //Use static value here, USA before boost

  def rateAirport(airport: Airport): AirportRating = {
    val detailedAirport =
      if (!airport.isFeaturesLoaded) {
        AirportCache.getAirport(airport.id, true).get
      } else {
        airport
      }
    val country = CountryCache.getCountry(airport.countryCode).get
    // Perform clamping on Doubles, then convert to Int at the end
    val ratioToModelAirportPower = Math.max(1.0, Math.min(100.0, 100.0 * airport.popMiddleIncome / modelAirportPower)).toInt
    val ratioToModelCountryPower = Math.max(0.0, Math.min(100.0, 100.0 * country.income * country.airportPopulation / modelCountryPower)).toInt

    val airportScalePower = if (detailedAirport.size >= 7) 25 else 0
    val featurePower = detailedAirport.getFeatures().map { feature =>
      feature.featureType match {
        case FINANCIAL_HUB => feature.strength.toDouble / 3
        case ELITE_CHARM => feature.strength.toDouble / 2
        case INTERNATIONAL_HUB => feature.strength.toDouble / 4
        case VACATION_HUB => feature.strength.toDouble / 6
        case ISOLATED_TOWN => -3 * feature.strength
        case DOMESTIC_AIRPORT => -10
        case GATEWAY_AIRPORT => ratioToModelCountryPower.toDouble / 3
        case _ => 0
      }
    }.sum.toInt

    AirportRating(ratioToModelAirportPower, ratioToModelCountryPower, featurePower + airportScalePower)
  }
}