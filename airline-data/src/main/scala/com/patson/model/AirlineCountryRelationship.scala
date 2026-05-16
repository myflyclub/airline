package com.patson.model

import com.patson.data.{AllianceSource, CountrySource, CycleSource, ManagerSource}

import scala.collection.mutable

case class AirlineCountryRelationship(airline : Airline, country : Country, factors : Map[RelationshipFactor, Int]) {
  val relationship = factors.values.sum
}

abstract class RelationshipFactor {
  val getDescription : String
}

object AirlineCountryRelationship {
  val countryRelationships = CountrySource.getCountryMutualRelationships()
  val countryMap = CountrySource.loadAllCountries().map(country => (country.countryCode, country)).toMap
  val HOME_COUNTRY = (homeCountry : Country, targetCountry : Country, relationship : Int) => new RelationshipFactor {
    override val getDescription: String = {
      val relationshipString = relationship match {
        case x if x >= 5 => "Home Market"
        case 4 => "Alliance"
        case 3 => "Close"
        case 2 => "Friendly"
        case 1 => "Warm"
        case 0 => "Neutral"
        case -1 => "Cold"
        case -2 => "Hostile"
        case -3 => "In Conflict"
        case _ => "War"
      }
      if (homeCountry.countryCode == targetCountry.countryCode) {
        s"Your home country ${homeCountry.name}"
      } else {
        s"Relationship between your home country ${homeCountry.name} and ${targetCountry.name} : ${relationshipString}"
      }
    }
  }


  val MARKET_SHARE = (percentage : BigDecimal) => new RelationshipFactor {
    override val getDescription: String = {
      s"${percentage}% of market share"
    }
  }

  val TITLE = (title : CountryAirlineTitle) => new RelationshipFactor {
    override val getDescription: String = {
      title.description
    }
  }

  val ALLIANCE_MEMBER_TITLE = (title : CountryAirlineTitle) => new RelationshipFactor {
    override val getDescription: String = {
      s"Alliance member ${title.airline.name} is ${title.description}"
    }
  }

  val MANAGER = (managerLevel : Int) => new RelationshipFactor {
    override val getDescription: String = {
      s"Total manager level ${managerLevel}"
    }
  }

  val HOME_COUNTRY_POSITIVE_RELATIONSHIP_MULTIPLIER = 4
  val HOME_COUNTRY_NEGATIVE_RELATIONSHIP_MULTIPLIER = 15
  val HOME_COUNTRY_HIGH_RELATIONSHIP_BONUS = 15 // applied when home->target relationship >= 5; privileged threshold is 35

  // Shared with CountryAirlineTitle so title ranking and the displayed relationship use
  // identical math. TITLE/ALLIANCE_MEMBER_TITLE are excluded — they depend on title state
  // and would be circular during title computation.
  def baseFactors(
    airline: Airline,
    targetCountry: Country,
    marketSharePct: Option[BigDecimal],
    managerLevel: Int,
    managerMultiplier: Double
  ): mutable.LinkedHashMap[RelationshipFactor, Int] = {
    val factors = mutable.LinkedHashMap[RelationshipFactor, Int]()
    airline.getCountryCode().foreach { homeCode =>
      val rel = countryRelationships.getOrElse((homeCode, targetCountry.countryCode), 0)
      val mult = if (rel >= 0) HOME_COUNTRY_POSITIVE_RELATIONSHIP_MULTIPLIER else HOME_COUNTRY_NEGATIVE_RELATIONSHIP_MULTIPLIER
      val bonus = if (rel >= 5) HOME_COUNTRY_HIGH_RELATIONSHIP_BONUS else 0
      factors.put(HOME_COUNTRY(countryMap(homeCode), targetCountry, rel), rel * mult + bonus)
    }
    marketSharePct.foreach { pct =>
      factors.put(MARKET_SHARE(pct), getMarketShareRelationshipBonus(pct))
    }
    factors.put(MANAGER(managerLevel), Math.round(managerLevel * managerMultiplier).toInt)
    factors
  }

  def getAirlineCountryRelationship(countryCode: String, airline: Airline): AirlineCountryRelationship = {
    val targetCountry = countryMap(countryCode)

    //country relationship
    if (airline.getCountryCode().isEmpty) {
      return AirlineCountryRelationship(airline, targetCountry, Map.empty)
    }

    //market share
    val marketSharePct: Option[BigDecimal] =
      CountrySource.loadMarketSharesByCountryCode(countryCode).flatMap { ms =>
        ms.airlineShares.get(airline.id).map { pax =>
          BigDecimal(pax.toDouble / ms.airlineShares.values.sum * 100).setScale(2, BigDecimal.RoundingMode.HALF_UP)
        }
      }

    //manager
    val currentCycle = CycleSource.loadCycle()
    val managerLevel = ManagerSource.loadCountryDelegateByAirlineAndCountry(airline.id, countryCode).map(_.assignedTask.asInstanceOf[CountryManagerTask].level(currentCycle)).sum
    val managerMultiplier = getManagerBonusMultiplier(targetCountry)

    val factors = baseFactors(airline, targetCountry, marketSharePct, managerLevel, managerMultiplier)

    //if alliance member has NATIONAL/PARTNERED, you get at most (PRIVILEGED - 5)/ESTABLISHED bonus.
    airline.getAllianceId().foreach { allianceId =>
      val allianceMemberIds: List[Int] = AllianceSource.loadAllianceById(allianceId).get.members.filter(_.airline.id != airline.id).map(_.airline.id)
      val allTitles = CountryAirlineTitle.getTopTitlesByCountry(countryCode)
      val currentRelationship = factors.values.sum
      allTitles.find(t => t.title == Title.NATIONAL_AIRLINE && allianceMemberIds.contains(t.airline.id)) match {
        case Some(nationalTitle) =>
          val bonus = Math.max(0, Math.min(CountryAirlineTitle.PRIVILEGED_AIRLINE_RELATIONSHIP_THRESHOLD - 5, CountryAirlineTitle.PRIVILEGED_AIRLINE_RELATIONSHIP_THRESHOLD - currentRelationship - 5))
          if (bonus > 0) factors.put(ALLIANCE_MEMBER_TITLE(nationalTitle), bonus)
        case None =>
          allTitles.find(t => t.title == Title.PARTNERED_AIRLINE && allianceMemberIds.contains(t.airline.id))
            .foreach { partneredTitle =>
              val bonus = Math.max(0, Math.min(CountryAirlineTitle.ESTABLISHED_AIRLINE_RELATIONSHIP_THRESHOLD, CountryAirlineTitle.ESTABLISHED_AIRLINE_RELATIONSHIP_THRESHOLD - currentRelationship))
              if (bonus > 0) factors.put(ALLIANCE_MEMBER_TITLE(partneredTitle), bonus)
            }
      }
    }

    AirlineCountryRelationship(airline, targetCountry, factors.toMap)
  }

  def getMarketShareRelationshipBonus(percentage: BigDecimal): Int = percentage match {
    case x if x >= 50 => 40
    case x if x >= 25 => 30
    case x if x >= 10 => 25
    case x if x >= 5  => 20
    case x if x >= 2  => 15
    case x if x >= 1  => 10
    case x if x >= 0.5 => 8
    case x if x >= 0.1 => 6
    case x if x >= 0.02 => (x * 50).toInt
    case _             => 1
  }

  val getManagerBonusMultiplier = (country : Country) => {
    val ratioToModelPower = Computation.MODEL_COUNTRY_POWER / (country.airportPopulation * country.income.toDouble).toLong
    val logRatio = Math.max(0.1, Math.log10(ratioToModelPower * 100) / 2) //0.1 to 1
    val levelMultiplier = CountryManagerTask.MAX_MANAGER_POWER / logRatio
    Math.min(CountryManagerTask.MAX_MANAGER_POWER, BigDecimal(levelMultiplier).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble)
  }
}

