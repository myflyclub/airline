package com.patson.model

import com.patson.model.FlightCategory.FlightCategory

/**
 * Cost base model
 *
 * rates at 400, 1200, 5000, 8000
 */
object Pricing {
  val modifierBrackets: Map[LinkClass, List[(Int, Double)]] = Map(
    DISCOUNT_ECONOMY  -> List((400, 0.13), (800, 0.070), (3800, 0.071), (3000, 0.09), (Int.MaxValue, 0.15)),
    ECONOMY           -> List((400, 0.16), (800, 0.077), (3800, 0.076), (3000, 0.09), (Int.MaxValue, 0.16)),
    BUSINESS          -> List((400, 0.39), (800, 0.240), (3800, 0.173), (3000, 0.27), (Int.MaxValue, 0.27)),
    FIRST             -> List((400, 1.17), (800, 0.356), (3800, 0.465), (3000, 0.58), (Int.MaxValue, 0.62))
  )
  val INTERNATIONAL_PRICE_MULTIPLIER = 1.05
  val PRICE_BASE = 15

  def computeStandardPrice(link : Transport, linkClass : LinkClass, paxType: PassengerType.Value) : Int = {
    val flightCategory = Computation.getFlightCategory(link.from, link.to)
    computeStandardPrice(link.distance, flightCategory, linkClass, paxType, link.from.baseIncome)
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