package com.patson.model.campaign

import com.patson.model._

case class Campaign(airline: Airline, principalAirport : Airport, radius : Int, populationCoverage : Long, area : List[Airport], var id: Int = 0) extends IdObject {

  def getAirlineBonus(airline: Airline, targetAirport: Airport, campaignManagerTasks : List[CampaignManagerTask], currentCycle : Int) : AirlineBonus = {
    if (area.map(_.id).contains(targetAirport.id)) { //safety check
      var totalLoyaltyBonus = 0.0
      campaignManagerTasks.map(task => getAirlineBonus(task, currentCycle)).foreach {
        case AirlineAppeal(loyalty) =>
          totalLoyaltyBonus += loyalty
      }
      AirlineBonus(BonusType.CAMPAIGN, AirlineAppeal(totalLoyaltyBonus), None)
    } else {
      AirlineBonus(BonusType.CAMPAIGN, AirlineAppeal(0), None)
    }
  }

  private def getAirlineBonus(campaignManagerTask: CampaignManagerTask, currentCycle : Int) : AirlineAppeal = {
    val luxuryAirlineBonus: Long = if (airline.airlineType == LuxuryAirline) 2 else 1
    Campaign.getAirlineBonus(populationCoverage / luxuryAirlineBonus, campaignManagerTask.level(currentCycle))
  }
}

object Campaign {
  val SAMPLE_POP_COVERAGE = 2000000
  val LOYALTY_BASE_BONUS = 8 //loyalty boost for 1M pop coverage for each manager level 
  val LOYALTY_MAX_BONUS_PER_MANAGER = 20

  def getAirlineBonus(populationCoverage: Long, managerTaskLevel: Int) : AirlineAppeal = {
    val popCoverageRatio = SAMPLE_POP_COVERAGE.toDouble / populationCoverage
    val loyaltyBonus = Math.min(LOYALTY_MAX_BONUS_PER_MANAGER, LOYALTY_BASE_BONUS * popCoverageRatio * managerTaskLevel)
    AirlineAppeal(loyalty = loyaltyBonus)
  }

  def getCost(populationCoverage: Long) : Int = {
    (populationCoverage / 50_000 + 50_000).toInt
  }

  val fromId = (id : Int) => Campaign(Airline.fromId(0), Airport.fromId(0), radius = 0, populationCoverage = 0, area = List.empty, id = id)
}

