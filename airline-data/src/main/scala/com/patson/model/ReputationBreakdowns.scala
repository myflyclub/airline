package com.patson.model

case class ReputationBreakdowns(breakdowns : List[ReputationBreakdown]) {
  val total = breakdowns.map(_.value).sum
}

case class ReputationBreakdown(reputationType: ReputationType.Value, value: Double, quantityValue: Long)


object ReputationType extends Enumeration {
  type ReputationType = Value
  implicit def valueToReputationType(x: Value) = x.asInstanceOf[AbstractReputationType]

  abstract class AbstractReputationType() extends super.Val {
    val label: String
  }

  val MILESTONE_PASSENGER_KM = new AbstractReputationType {
    override val label = "Milestone Passenger KM"
  }

  val MILESTONE_DESTINATIONS = new AbstractReputationType {
    override val label = "Milestone Destinations"
  }

  val MILESTONE_LINK_COUNT = new AbstractReputationType {
    override val label = "Number of links"
  }

  val MILESTONE_CODESHARES = new AbstractReputationType {
    override val label = "Milestone Codeshares"
  }

  val MILESTONE_BUSINESS = new AbstractReputationType {
    override val label = "Milestone Business Passengers"
  }

  val MILESTONE_AIRCRAFT_TYPES = new AbstractReputationType {
    override val label = "Milestone Aircraft Types"
  }

  val MILESTONE_ON_TIME = new AbstractReputationType {
    override val label = "Milestone On-Time Departures"
  }

  val MILESTONE_LEADER_POINTS = new AbstractReputationType {
    override val label = "Milestone Leaderboard Points"
  }

  val MILESTONE_LOAN = new AbstractReputationType {
    override val label = "Milestone Loan Size"
  }

  val MILESTONE_BASES = new AbstractReputationType {
    override val label = "Milestone Bases"
  }

  val AIRPORT_LOYALIST_RANKING = new AbstractReputationType {
    override val label = "Airports"
  }

  val TOURISTS = new AbstractReputationType {
    override val label = "Tourists & Travelers ticketed"
  }

  val ELITES = new AbstractReputationType {
    override val label = "Elites ticketed"
  }

  val STOCK_PRICE = new AbstractReputationType {
    override val label = "Stock Price"
  }

  val LEADERBOARD_BONUS = new AbstractReputationType {
    override val label = "Leaderboards"
  }
}

