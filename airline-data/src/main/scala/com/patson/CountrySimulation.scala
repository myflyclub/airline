package com.patson

import com.patson.data._
import com.patson.model._
import com.patson.util.CountryChampionInfo

import scala.collection.mutable.ListBuffer


object CountrySimulation {
  val MAX_NATIONAL_AIRLINE_COUNT = 3 //What US will have
  val MAX_PARTNERED_AIRLINE_COUNT = 5 //What US will have

  def computeNationalAirlineCount(country: Country) : Int = {
    val ratioToModelPower = country.airportPopulation * country.income.toDouble / Computation.MODEL_COUNTRY_POWER

    val ratio: Double = math.log10(ratioToModelPower * 100) / 2

    val result = Math.round(MAX_NATIONAL_AIRLINE_COUNT * ratio).toInt
    if (result <= 0) 1 else result
  }

  def computePartneredAirlineCount(country: Country) : Int = {
    val ratioToModelPower = country.airportPopulation * country.income.toDouble / Computation.MODEL_COUNTRY_POWER

    val ratio: Double = math.log10(ratioToModelPower * 100) / 2

    val result = Math.round(MAX_PARTNERED_AIRLINE_COUNT * ratio).toInt
    if (result <= 0) 1 else result
  }

  def simulate(cycle: Int) = {
    println("starting country simulation")
    val marketSharesByCountryCode: Map[String, Map[Int, Long]] = CountrySource.loadMarketSharesByCriteria(List.empty).map(marketShare => (marketShare.countryCode, marketShare.airlineShares)).toMap
    val countriesByCode = CountrySource.loadAllCountries().map(entry => (entry.countryCode, entry)).toMap
    val airlinesById = AirlineSource.loadAllAirlines(fullLoad = false).map(airline => (airline.id, airline)).toMap
    val countryAirlineTitles = ListBuffer[CountryAirlineTitle]()
    val countryChampionInfoEntries = ListBuffer[CountryChampionInfo]()
    marketSharesByCountryCode.foreach {
      case (countryCode, marketSharesByAirline) =>
        val country = countriesByCode(countryCode)

        // Champion ranking by market share (unchanged)
        val orderedByMarketShare: List[(Int, Long)] = marketSharesByAirline.toList.sortBy(_._2).reverse
        orderedByMarketShare.zipWithIndex.foreach { case ((airlineId, pax), index) =>
          countryChampionInfoEntries.append(CountryChampionInfo(airlinesById(airlineId), country, pax, index + 1))
        }

        // Title assignment: top X airlines by AirlineCountryRelationship.relationship
        val orderedByRelationship: List[Airline] =
          marketSharesByAirline.keys.flatMap(airlinesById.get).toList
            .map(airline => (airline, AirlineCountryRelationship.getAirlineCountryRelationship(countryCode, airline).relationship))
            .sortBy(-_._2)
            .map(_._1)

        val nationalCount = computeNationalAirlineCount(country)
        val partneredCount = computePartneredAirlineCount(country)

        orderedByRelationship.zipWithIndex.foreach { case (airline, index) =>
          if (index < nationalCount) {
            countryAirlineTitles.append(CountryAirlineTitle(Country.fromCode(countryCode), airline, Title.NATIONAL_AIRLINE))
          } else if (index < nationalCount + partneredCount) {
            countryAirlineTitles.append(CountryAirlineTitle(Country.fromCode(countryCode), airline, Title.PARTNERED_AIRLINE))
          }
        }
    }

    val result = countryAirlineTitles.toList
    CountrySource.saveCountryAirlineTitles(result)
  }
}