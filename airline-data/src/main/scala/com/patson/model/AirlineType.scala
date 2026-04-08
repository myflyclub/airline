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

case object NonPlayerAirline extends AirlineType {
  val id = 1
  val label = "Non-Player"
  override val airportRepRatio = 1
  override val stockRepPerLevel = 75
  val description = List[String](
    "",
  )
}

case object DiscountAirline extends AirlineType {
  val id = 2
  val label = "Discount"
  val staffCapRatio = 0.9
  override val touristTravelerRepPerLevel = 50
  override val stockRepPerLevel = 75
  val crewRatio = 0.4
  val description = List[String](
    "You can never add business or first class!",
    "Earn additional rep by managing your finances and increasing your stock price.",
    "Earn reputation by moving lots of traveler and tourist passengers.",
    s"Need ${Math.round((1.0 - staffCapRatio) * 100)}% less staff to support capacity.",
    s"Base crew costs are ${DiscountAirline.crewRatio * 100}% the cost of other airlines."
  )
}

case object LuxuryAirline extends AirlineType {
  val id = 3
  val label = "Luxury"
  val staffFreqRatio = 0.8
  override val elitesRepPerLevel = 50
  override val stockRepPerLevel = 75
  val description = List[String](
    "You can never add economy class!",
    "Earn reputation by moving lots of elite passengers and by increasing your stock price",
    s"Need ${Math.round((1.0 - staffFreqRatio) * 100)}% less staff to support frequency.",
    "Advertising campaigns are twice as effective.",
    "Have exclusive access to premium luxury aircraft types.",
  )
}

case object RegionalAirline extends AirlineType {
  val id = 4
  val label = "Regional Partner"
  override val airportRepRatio = 1
  override val stockRepPerLevel = 50
  val extraSharedBaseLimit = 1
  val modelMaxSize = 0.13 //used in web app to set allowed planes
  val staffFreqRatio = 0.0
  val staffCapRatio = 1.4
  val staffReductionRange = 1500
  val staffReductionRangeFadeTo = 2000
  val description = List[String](
    "Can only buy small, regional aircraft!",
    "Earn reputation by creating high frequency links and capturing airports!",
    "Earn additional rep by managing your finances and increasing your stock price.",
    s"Requires no staff to support frequency on routes under ${staffReductionRange} km (staff reduction fades up to $staffReductionRangeFadeTo km.",
    s"However capacity support requires ${Math.round(staffCapRatio * 100)}% staff.",
    "May build bases overlapping with alliance mates (one plus per airport)."
  )
}

case object MegaHqAirline extends AirlineType {
  val id = 5
  val label = "Mega-Base State-Owned"
  override val airportRepRatio = 1
  override val elitesRepPerLevel = 50
  override val touristTravelerRepPerLevel = 50
  val description = List[String](
    "Build a giant mega-base!",
    "Earn reputation by moving lots of elite passengers AND tourist and traveler passengers.",
    "Additionally earn rep from winning airports, but none from stock price.",
    "Upgrading & upkeep any additional bases is extremely expensive.",
  )
}

object AirlineType {
  def fromId(id: Int): AirlineType = id match {
    case 0 => LegacyAirline
    case 1 => NonPlayerAirline
    case 2 => DiscountAirline
    case 3 => LuxuryAirline
    case 4 => RegionalAirline
    case 5 => MegaHqAirline
    case _ => throw new IllegalArgumentException("Invalid AirlineType ID: " + id)
  }
}