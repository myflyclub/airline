package com.patson.model

import com.patson.model.FlightCategory.FlightCategory

/**
 * Cost base model
 *
 * rates at 400, 1200, 5000, 8000
 */
object Pricing {
  val modifierBrackets: Map[LinkClass, List[(Int, Double)]] = Map(
    DISCOUNT_ECONOMY  -> List((400, 0.13), (800, 0.070), (3800, 0.071), (3000, 0.08), (Int.MaxValue, 0.14)),
    ECONOMY           -> List((400, 0.16), (800, 0.079), (3800, 0.074), (3000, 0.09), (Int.MaxValue, 0.15)),
    BUSINESS          -> List((400, 0.38), (800, 0.239), (3800, 0.168), (3000, 0.27), (Int.MaxValue, 0.27)),
    FIRST             -> List((400, 1.17), (800, 0.331), (3800, 0.405), (3000, 0.59), (Int.MaxValue, 0.61))
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