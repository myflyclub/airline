package com.patson.model

case class Lounge(airline : Airline, allianceId : Option[Int], airport : Airport, name : String = "", level : Int, status : LoungeStatus.Value, foundedCycle : Int) {
  def getValue : Long = {
    (10_000_000 + Math.pow(level, 2) * 400 * airport.baseIncome).toLong
  }
  
  val getUpkeep : Long = {
    if (status == LoungeStatus.ACTIVE) (50000 + airport.baseIncome) * level else 0 //use base income for calculation here
  }

  val rankingThreshold = Map(
    5 -> 2,
    6 -> 3,
    7 -> 4,
    8 -> 4,
    9 -> 5,
    10 -> 5
  )

  //to be considered active, it should have passenger ranking smaller (ie higher) or equals to this value)
  val getActiveRankingThreshold: Int = {
    rankingThreshold.getOrElse(airport.size, 1)
  }
}

object Lounge {
  val PER_VISITOR_COST = 20 //how much extra cost to serve 1 visitor
  val PER_VISITOR_CHARGE = 45 //how much to charge an airline (self and alliance member) per 1 visitor. This has to be higher to make popular lounge profitable
  val MAX_LEVEL = 4

  def priceAdjustRatio (loungeLevel : Int, loungeLevelRequired : Int, distance : Int): Double = {
    val distanceModifier = Math.min(1.0, Math.max(0.4, distance.toDouble / 4000.0))
    if (loungeLevel < loungeLevelRequired) { //penalty for not having lounge required
      (loungeLevelRequired - loungeLevel) * 0.1 * distanceModifier //0.1 penalty per missing level
    } else if (loungeLevel == loungeLevelRequired) {
      -.025 - 0.025 * distanceModifier //-0.05 discount
    } else {
      -0.05 * distanceModifier - (loungeLevel - loungeLevelRequired) * 0.02 * distanceModifier //-0.5 - 0.2 per level above
    }
  }

  def getBaseScaleRequirement(loungeLevel : Int) = {
    if (loungeLevel == 4) {
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


