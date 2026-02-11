package com.patson.model

sealed trait AirlineType {
  def id: Int
  def label: String
  def description: List[String]
  def airportRepRatio: Int = 0
  def elitesRepPerLevel: Int = 0
  def touristTravelerRepPerLevel: Int = 0
  def stockRepPerLevel: Int = 0
}

case object LegacyAirline extends AirlineType {
  val id = 0
  val label = "Legacy"
  override val airportRepRatio = 1
  override val stockRepPerLevel = 75
  val description = List[String](
    "Earn reputation by being highly profitable and doing stock buy backs, increasing your stock price.",
    "Earn additionally reputation by capturing airports!"
  )
}

case object BeginnerAirline extends AirlineType {
  val id = 1
  val label = "Beginner"
  override val airportRepRatio = 2
  override val stockRepPerLevel = 75
  val description = List[String](
    "Cannot grow to more than 250 reputation",
    s"Base crew costs are ${DiscountAirline.crewRatio * 100}% the cost of other airlines.",
    s"30% lower service costs.",
    "Win double reputation from airports!"
  )
}

case object NonPlayerAirline extends AirlineType {
  val id = 2
  val label = "Non-Player"
  override val airportRepRatio = 1
  override val stockRepPerLevel = 75
  val description = List[String](
    "",
  )
}

case object DiscountAirline extends AirlineType {
  val id = 3
  val label = "Discount"
  override val touristTravelerRepPerLevel = 70
  override val stockRepPerLevel = 50
  val crewRatio = 0.4
  val description = List[String](
    "You can never add business or first class!",
    "Earn reputation by moving lots of traveler and tourist passengers.",
    "Earn additional rep by managing your finances and increasing your stock price.",
    s"Base crew costs are ${DiscountAirline.crewRatio * 100}% the cost of other airlines."
  )
}

case object LuxuryAirline extends AirlineType {
  val id = 4
  val label = "Luxury"
  val extraLoyalty = 12
  val staffFreqRatio = 0.4
  override val elitesRepPerLevel = 70
  override val stockRepPerLevel = 50
  val description = List[String](
    "You can never add economy class!",
    "Earn reputation by moving lots of elite passengers.",
    "Earn additional rep by managing your finances and increasing your stock price.",
    s"Need ${(1.0 - staffFreqRatio) * 100}% less staff to support frequency.",
    s"Receive $extraLoyalty loyalty at airports where you have a base.",
  )
}

case object RegionalAirline extends AirlineType {
  val id = 5
  val label = "Regional Partner"
  override val airportRepRatio = 1
  override val stockRepPerLevel = 50
  val extraSharedBaseLimit = 1
  val modelMaxSize = 0.1 //used in web app to set allowed planes
  val staffFreqRatio = 0.2
  val staffReductionRange = 2000
  val staffReductionRangeFadeTo = 4000
  val description = List[String](
    "Can only buy small, regional aircraft!",
    "Earn reputation by creating high frequency links and capturing airports!",
    "Earn additional rep by managing your finances and increasing your stock price.",
    s"Need ${(1.0 - staffFreqRatio) * 100}% less staff to support frequency on routes under ${staffReductionRange} km (staff reduction fades up to $staffReductionRangeFadeTo km.",
    "May build bases overlapping with alliance mates (one plus per airport)."
  )
}

case object MegaHqAirline extends AirlineType {
  val id = 6
  val label = "Mega-Base State-Owned"
  override val airportRepRatio = 2
  override val elitesRepPerLevel = 50
  override val touristTravelerRepPerLevel = 50
  val description = List[String](
    "Build a giant mega-base!",
    "Earn reputation by moving lots of elite passengers AND tourist and traveler passengers.",
    "Additionally earn double rep from winning airports, but none from stock price.",
    "Upgrading & upkeep any additional bases is extremely expensive.",
  )
}

object AirlineType {
  def fromId(id: Int): AirlineType = id match {
    case 0 => LegacyAirline
    case 1 => BeginnerAirline
    case 2 => NonPlayerAirline
    case 3 => DiscountAirline
    case 4 => LuxuryAirline
    case 5 => RegionalAirline
    case 6 => MegaHqAirline
    case _ => throw new IllegalArgumentException("Invalid AirlineType ID: " + id)
  }
} 