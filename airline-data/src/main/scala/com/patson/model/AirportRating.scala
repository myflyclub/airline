package com.patson.model

import com.patson.data.{AirportSource, CountrySource, LinkStatisticsSource}
import com.patson.model.AirportFeatureType._
import com.patson.util.{AirportCache, CountryCache}

case class AirportRating(economicPowerRating: Int, countryPowerRating: Int, featurePower: Int) {
  val ECONOMIC_POWER_WEIGHT = 0.8
  val COUNTRY_POWER_WEIGHT = 0.25

  val overallDifficulty = Math.max(1.0, Math.min(200.0, economicPowerRating * ECONOMIC_POWER_WEIGHT + countryPowerRating * COUNTRY_POWER_WEIGHT + featurePower)).toInt
}

object AirportRating {
  private val modelAirportPower: Long = 9877905 //Use static value here, HND before boost
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

    val airportScalePower = if (detailedAirport.size >= 7) 15 + 2 * detailedAirport.size else 2 * detailedAirport.size
    val featurePower = detailedAirport.getFeatures().map { feature =>
      feature.featureType match {
        case FINANCIAL_HUB => feature.strength.toDouble / 3
        case ELITE_CHARM => feature.strength.toDouble / 4
        case INTERNATIONAL_HUB => feature.strength.toDouble / 6
        case PRESTIGE_CHARM => feature.strength.toDouble / 5
        case ISOLATED_TOWN => -3 * feature.strength
        case DOMESTIC_AIRPORT => -12
        case GATEWAY_AIRPORT => ratioToModelCountryPower.toDouble / 3
        case _ => 0
      }
    }.sum.toInt

    AirportRating(ratioToModelAirportPower, ratioToModelCountryPower, featurePower + airportScalePower)
  }
}