package com.patson.model

import com.patson.model.FlightCategory.FlightCategory

/**
 * Cost base model
 *
 * rates at 400, 1200, 5000, 8000
 */
object Pricing {
  val modifierBrackets: Map[LinkClass, List[(Int, Double)]] = Map(
    DISCOUNT_ECONOMY  -> List((400, 0.13), (800, 0.071), (3800, 0.07), (3000, 0.08), (Int.MaxValue, 0.12)),
    ECONOMY           -> List((400, 0.16), (800, 0.079), (3800, 0.073), (3000, 0.09), (Int.MaxValue, 0.14)),
    BUSINESS          -> List((400, 0.35), (800, 0.234), (3800, 0.155), (3000, 0.15), (Int.MaxValue, 0.19)),
    FIRST             -> List((400, 1.16), (800, 0.309), (3800, 0.355), (3000, 0.33), (Int.MaxValue, 0.48))
  )
  val INTERNATIONAL_PRICE_MULTIPLIER = 1.05

  def computeStandardPrice(link : Transport, linkClass : LinkClass, paxType: PassengerType.Value) : Int = {
    val flightCategory = Computation.getFlightCategory(link.from, link.to)
    computeStandardPrice(link.distance, flightCategory, linkClass, paxType, link.from.baseIncome)
  }
  def computeStandardPrice(distance: Int, flightCategory: FlightCategory.Value, linkClass: LinkClass, paxType: PassengerType.Value, airportIncome: Int) : Int = {
    var remainDistance = distance
    var price = 15.0
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
    price *= 1 + 0.16 * Math.min(1, airportIncome.toDouble / Airport.HIGH_INCOME)
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