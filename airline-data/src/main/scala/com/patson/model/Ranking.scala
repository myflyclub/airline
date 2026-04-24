package com.patson.model

case class Ranking(rankingType : RankingType.Value, key : RankingKey, entry : Any, ranking : Int, rankedValue : Number, var movement : Int = 0, reputationPrize : Option[Int] = None)

object RankingType extends Enumeration {
  type RankingType = Value
  val PASSENGER_MILE, PASSENGER_QUALITY, PASSENGER_SPEED, ELITE_COUNT, TOURIST_COUNT, BUSINESS_COUNT, CODESHARE_COUNT, STOCK_PRICE, AIRLINE_PROFIT_MARGIN, AIRLINE_VALUE, PASSENGER_SATISFACTION, PASSENGER_DISSATISFACTION, ON_TIME, UNIQUE_COUNTRIES, UNIQUE_IATA, LINK_COUNT_SMALL_TOWN, LINK_COUNT_LOW_INCOME, LINKS_COUNT_LOSS, LINK_LOSS, LINK_PROFIT, LINK_PROFIT_ALL, LINK_DISTANCE, LINK_SHORTEST, LINK_FREQUENCY, LOUNGE, AIRPORT, INTERNATIONAL_PAX, DOMESTIC_PAX, AIRPORT_TRAFFIC, AIRLINE_PRESTIGE, MOST_CONGESTED_AIRPORT, LARGEST_FLEET, ALLIANCE_TRAVELERS, ALLIANCE_STOCKS, ALLIANCE_CODESHARES, ALLIANCE_ELITE, ALLIANCE_AIRPORT_REP, ALLIANCE_LOUNGE = Value

  val fastLookup: Map[String, Value] = values.map(v => v.toString -> v).toMap
}

sealed trait RankingKey
object RankingKey {
  final case class AirlineKey(airlineId: Int) extends RankingKey
  final case class AirportKey(airportId: Int) extends RankingKey
  final case class AllianceKey(allianceId: Int) extends RankingKey
  // Canonicalized (minId, maxId) for undirected pairs
  final case class AirportPairKey(fromAirportId: Int, toAirportId: Int) extends RankingKey {
    def canonical: AirportPairKey = {
      if (fromAirportId <= toAirportId) this else AirportPairKey(toAirportId, fromAirportId)
    }
  }
  // Airline + Airport pair key (for AIRPORT_TRAFFIC and similar)
  final case class AirlineAirportKey(airlineId: Int, airportId: Int) extends RankingKey

  final case class LinkKey(airlineId: Int, linkId: Int) extends RankingKey
}