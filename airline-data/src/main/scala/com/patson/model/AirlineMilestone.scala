package com.patson.model

import com.patson.model.AirlineType

case class MilestoneCondition(
  threshold: Long,
  reward: Int
)

case class Milestone(
  name: String,
  description: String,
  conditions: List[MilestoneCondition],
  airlineTypes: Set[AirlineType] = Set.empty // empty means applies to all types
)

object AirlineMilestones {
  val milestonesByAirlineType: Map[AirlineType, List[Milestone]] = Map(

    LegacyAirline -> List(
      Milestone(
        "MILESTONE_LOAN",
        "Weekly Loan Payment",
        List(
          MilestoneCondition(50000000, 40),
          MilestoneCondition(2500000, 20)
        )
      ),
      Milestone(
        "MILESTONE_DESTINATIONS",
        "Destinations",
        List(
          MilestoneCondition(120, 60),
          MilestoneCondition(30, 30)
        )
      ),
      Milestone(
        "MILESTONE_PASSENGER_KM",
        "Passenger KM",
        List(
          MilestoneCondition(500000000, 40),
          MilestoneCondition(50000, 15)
        )
      ),
      Milestone(
        "MILESTONE_AIRCRAFT_TYPES",
        "Aircraft Types",
        List(
          MilestoneCondition(8, 40),
          MilestoneCondition(4, 15)
        )
      ),
      Milestone(
        "MILESTONE_LEADER_POINTS",
        "Leaderboard Points",
        List(
          MilestoneCondition(160, 40),
          MilestoneCondition(24, 15)
        )
      ),
      Milestone(
        "MILESTONE_BASES",
        "Airport Bases",
        List(
          MilestoneCondition(14, 40),
          MilestoneCondition(6, 15)
        )
      ),
      Milestone(
        "MILESTONE_CODESHARES",
        "Codeshares",
        List(
          MilestoneCondition(125000, 40),
          MilestoneCondition(10000, 20),
        )
      ),
      Milestone(
        "MILESTONE_BUSINESS",
        "Business Passengers",
        List(
          MilestoneCondition(250000, 60),
          MilestoneCondition(75000, 30)
        )
      ),
    ),

    RegionalAirline -> List(
      Milestone(
        "MILESTONE_CODESHARES",
        "Codeshares",
        List(
          MilestoneCondition(150000, 90),
          MilestoneCondition(7500, 30)
        )
      ),
      Milestone(
        "MILESTONE_BASES",
        "Airport Bases",
        List(
          MilestoneCondition(16, 40),
          MilestoneCondition(4, 15)
        )
      ),
      Milestone(
        "MILESTONE_PASSENGER_KM",
        "Passenger KM",
        List(
          MilestoneCondition(250000000, 30),
          MilestoneCondition(100000, 15)
        )
      ),
      Milestone(
        "MILESTONE_DESTINATIONS",
        "Destinations",
        List(
          MilestoneCondition(180, 40),
          MilestoneCondition(30, 15)
        )
      ),
      Milestone(
        "MILESTONE_ON_TIME",
        "On-Time Departures",
        List(
          MilestoneCondition(15000, 40),
          MilestoneCondition(750, 15),
        )
      ),
      Milestone(
        "MILESTONE_AIRCRAFT_TYPES",
        "Aircraft Types",
        List(
          MilestoneCondition(7, 40),
          MilestoneCondition(3, 15)
        )
      )
    ),

    DiscountAirline -> List(
      Milestone(
        "MILESTONE_PASSENGER_KM",
        "Passenger KM",
        List(
          MilestoneCondition(100000000, 60),
          MilestoneCondition(50000, 20)
        )
      ),
      Milestone(
        "MILESTONE_DESTINATIONS",
        "Destinations",
        List(
          MilestoneCondition(120, 40),
          MilestoneCondition(40, 20)
        )
      ),
      Milestone(
        "MILESTONE_ON_TIME",
        "On-Time Departures",
        List(
          MilestoneCondition(24000, 90),
          MilestoneCondition(200, 30)
        )
      ),
      Milestone(
        "MILESTONE_LEADER_POINTS",
        "Leaderboard Points",
        List(
          MilestoneCondition(160, 40),
          MilestoneCondition(24, 20)
        )
      ),
      Milestone(
        "MILESTONE_BASES",
        "Number Bases",
        List(
          MilestoneCondition(18, 90),
          MilestoneCondition(6, 30)
        )
      ),
      Milestone(
        "MILESTONE_BUSINESS",
        "Business Passengers",
        List(
          MilestoneCondition(125000, 40),
          MilestoneCondition(50000, 20)
        )
      )
    ),

    MegaHqAirline -> List(
      Milestone(
        "MILESTONE_DESTINATIONS",
        "Destinations",
        List(
          MilestoneCondition(140, 90),
          MilestoneCondition(20, 20)
        )
      ),
      Milestone(
        "MILESTONE_ON_TIME",
        "On-Time Departures",
        List(
          MilestoneCondition(2400, 60),
          MilestoneCondition(400, 20),
        )
      ),
      Milestone(
        "MILESTONE_LOAN",
        "Weekly Loan Payment",
        List(
          MilestoneCondition(20000000, 60),
          MilestoneCondition(2500000, 20)
        )
      ),
      Milestone(
        "MILESTONE_AIRCRAFT_TYPES",
        "Aircraft Types",
        List(
          MilestoneCondition(8, 60),
          MilestoneCondition(4, 20)
        )
      ),
      Milestone(
        "MILESTONE_CODESHARES",
        "Codeshares",
        List(
          MilestoneCondition(75000, 90),
          MilestoneCondition(10000, 40)
        )
      ),
      Milestone(
        "MILESTONE_BUSINESS",
        "Business Passengers",
        List(
          MilestoneCondition(125000, 60),
          MilestoneCondition(50000, 30)
        )
      ),
      Milestone(
        "MILESTONE_LEADER_POINTS",
        "Leaderboard Points",
        List(
          MilestoneCondition(160, 90),
          MilestoneCondition(24, 30)
        )
      ),
    ),

    LuxuryAirline -> List(
      Milestone(
        "MILESTONE_PASSENGER_KM",
        "Passenger KM",
        List(
          MilestoneCondition(75000000, 40),
          MilestoneCondition(17500, 20)
        )
      ),
      Milestone(
        "MILESTONE_DESTINATIONS",
        "Destinations",
        List(
          MilestoneCondition(140, 90),
          MilestoneCondition(40, 30)
        )
      ),
      Milestone(
        "MILESTONE_LOAN",
        "Weekly Loan Payment",
        List(
          MilestoneCondition(40000000, 40),
          MilestoneCondition(2500000, 20)
        )
      ),
      Milestone(
        "MILESTONE_AIRCRAFT_TYPES",
        "Aircraft Types",
        List(
          MilestoneCondition(9, 60),
          MilestoneCondition(4, 20)
        )
      ),
      Milestone(
        "MILESTONE_LEADER_POINTS",
        "Leaderboard Points",
        List(
          MilestoneCondition(124, 90),
          MilestoneCondition(40, 30),
        )
      ),
      Milestone(
        "MILESTONE_BUSINESS",
        "Business Passengers",
        List(
          MilestoneCondition(125000, 90),
          MilestoneCondition(25000, 30)
        )
      )
    )
  )

  def getMilestonesForAirlineType(airlineType: AirlineType): List[Milestone] = {
    milestonesByAirlineType.getOrElse(airlineType, milestonesByAirlineType(LegacyAirline))
  }

  def evaluateMilestone(milestone: Milestone, value: Long): Int = {
    milestone.conditions
      .find(_.threshold <= value)
      .map(_.reward)
      .getOrElse(0)
  }
} 