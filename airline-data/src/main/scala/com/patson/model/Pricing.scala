package com.patson.model

import com.patson.model.FlightCategory.FlightCategory

/**
 * Cost base model
 *
 * rates at 400, 2000, 4000, 7000, ∞
 */
object Pricing {
  val modifierBrackets: Map[LinkClass, List[(Int, Double)]] = Map(
    DISCOUNT_ECONOMY  -> List((400, 0.13), (1600, 0.079), (2000, 0.08), (4000, 0.074), (Int.MaxValue, 0.12)),
    ECONOMY           -> List((400, 0.17), (1600, 0.085), (2000, 0.09), (4000, 0.078), (Int.MaxValue, 0.12)),
    BUSINESS          -> List((400, 0.44), (1600, 0.254), (2000, 0.22), (4000, 0.161), (Int.MaxValue, 0.262)),
    FIRST             -> List((400, 1.45), (1600, 0.290), (2000, 0.43), (4000, 0.418), (Int.MaxValue, 0.65))
  )
  val INTERNATIONAL_PRICE_MULTIPLIER = 1.05
  val PRICE_BASE = 27

  def computeStandardPrice(link : Transport, linkClass : LinkClass, paxType: PassengerType.Value) : Int = {
    val flightCategory = Computation.getFlightCategory(link.from, link.to)
    computeStandardPrice(link.distance, flightCategory, linkClass, paxType, link.from.income)
  }
  def computeStandardPrice(distance: Int, flightCategory: FlightCategory.Value, linkClass: LinkClass, paxType: PassengerType.Value, airportIncome: Int) : Int = {
    var remainDistance = distance
    var price: Double = PRICE_BASE
    for (priceBracket <- modifierBrackets(linkClass) if(remainDistance > 0)) {
      if (priceBracket._1 >= remainDistance) {
        price += remainDistance * priceBracket._2
      } else {
        price += priceBracket._1.toDouble * priceBracket._2
      }
      remainDistance -= priceBracket._1
    }
    price = if (flightCategory == FlightCategory.INTERNATIONAL) {
      price * INTERNATIONAL_PRICE_MULTIPLIER
    } else {
      price
    }
    price *= 1 + 0.14 * Math.min(1, airportIncome.toDouble / Airport.HIGH_INCOME)
    price *= PassengerType.priceAdjust(paxType)
    
    price.toInt
  }
  
  def computeStandardPriceForAllClass(distance: Int, flightCategory: FlightCategory.Value, paxType: PassengerType.Value, airportIncome: Int) : LinkClassValues = {
    val priceByLinkClass : List[(LinkClass, Int)] = LinkClass.values.map { linkClass =>
      (linkClass, computeStandardPrice(distance, flightCategory, linkClass, paxType, airportIncome))
    }
    LinkClassValues.getInstanceByMap(priceByLinkClass.toMap)
  }

}