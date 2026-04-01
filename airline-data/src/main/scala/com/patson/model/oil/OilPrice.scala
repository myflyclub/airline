package com.patson.model.oil

case class OilPrice(price : Double, cycle : Int)

object OilPrice {
  val DEFAULT_UNIT_COST = 0.08
  val DEFAULT_PRICE : Double = 70
}