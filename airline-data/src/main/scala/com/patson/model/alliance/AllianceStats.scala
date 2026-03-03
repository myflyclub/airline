package com.patson.model.alliance

import com.patson.model.{Alliance, PassengerType, Period}

case class AllianceStats(alliance : Alliance,
                         travelerPax : Long,
                         businessPax : Long,
                         elitePax : Long,
                         touristPax : Long,
                         airportRep : Int,
                         airlineMarketCap : Long,
                         loungeVisit : Long,
                         profit : Long,
                         cycle : Int,
                         period : Period.Value = Period.WEEKLY) {

  lazy val totalPax: Long = travelerPax + businessPax + elitePax + touristPax

  lazy val properties = Map(
    "travelerPax" -> travelerPax,
    "businessPax" -> businessPax,
    "elitePax" -> elitePax,
    "touristPax" -> touristPax,
    "totalAirportRep" -> airportRep,
    "totalAirlineStockPrice" -> airlineMarketCap,
    "profit" -> profit,
    "loungeVisit" -> loungeVisit,
  )
}

object AllianceStats {
  val empty = (alliance: Alliance, cycle: Int) => AllianceStats(
    alliance,
    0L, 0L, 0L, 0L, // passenger types
    0, 0L, // airport rep and stock price
    0L, 0L, // lounge, loyalist, profit
    cycle
  )
}

//intermediate data object
case class AlliancePaxStat(tourists : Int, elites : Int, business : Int, total : Int, codeshares : Int)