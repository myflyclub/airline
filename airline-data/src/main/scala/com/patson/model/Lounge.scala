package com.patson.model

case class Lounge(airline : Airline, allianceId : Option[Int], airport : Airport, name : String = "", level : Int, status : LoungeStatus.Value, foundedCycle : Int) {
  def getValue : Int = {
    (10_000_000 + Math.pow(level, 1.8) * 400 * airport.baseIncome).toInt
  }
  
  val getUpkeep : Long = {
    if (status == LoungeStatus.ACTIVE) (40000 + airport.baseIncome) * level else 0 //use base income for calculation here
  }

  //to be considered active, it should have passenger ranking smaller (ie higher) or equals to this value)
  val getActiveRankingThreshold: Int = {
    airport.size / 2 + 1
  }
}

object Lounge {
  val PER_VISITOR_COST = 12 //how much extra cost to serve 1 visitor
  val PER_VISITOR_CHARGE = 25 //how much to charge an airline (self and alliance member) per 1 visitor.
  val MAX_LEVEL = 5
  val BASE_COST_REDUCTION = 45

  def priceAdjust (cost: Double, loungeLevel : Int, loungeLevelRequired : Int): Double = {
    val ratio = if (loungeLevel < loungeLevelRequired) { //penalty for not having lounge required
      (loungeLevelRequired - loungeLevel) * 0.05 //0.05 penalty per missing level
    } else {
      -1 * ((loungeLevel - loungeLevelRequired) * 0.025) //- 0.025 per level above
    }
    val discrete = if (loungeLevel > 0) BASE_COST_REDUCTION else 0
    Math.max(1, cost * (1 + ratio) - discrete)
  }

  def getBaseScaleRequirement(loungeLevel : Int): Int = {
    if (loungeLevel == 5) {
      18
    } else if (loungeLevel == 4) {
      12
    } else if (loungeLevel == 3) {
      9
    } else if (loungeLevel == 2) {
      6
    } else {
      3
    }
  }
}

object LoungeStatus extends Enumeration {
  type LoungeStatus = Value
  val ACTIVE, INACTIVE = Value
}


