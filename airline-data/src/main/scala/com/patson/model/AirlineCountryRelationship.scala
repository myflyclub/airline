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

  def getAirlineCountryRelationship(countryCode : String, airline : Airline) : AirlineCountryRelationship = {
    val factors = mutable.LinkedHashMap[RelationshipFactor, Int]()
    val targetCountry = countryMap(countryCode)

    airline.getCountryCode() match {
      case Some(homeCountryCode: String) =>
        //home country vs target country
        val relationship = countryRelationships.getOrElse((homeCountryCode, countryCode), 0)
        val multiplier = if (relationship >= 0) HOME_COUNTRY_POSITIVE_RELATIONSHIP_MULTIPLIER else HOME_COUNTRY_NEGATIVE_RELATIONSHIP_MULTIPLIER
        val home_country_bonus = if (relationship >= 5) CountryAirlineTitle.PRIVILEGED_AIRLINE_RELATIONSHIP_THRESHOLD else 0
          factors.put(HOME_COUNTRY(countryMap(homeCountryCode), targetCountry, relationship), relationship * multiplier + home_country_bonus)

        //market share
        CountrySource.loadMarketSharesByCountryCode(countryCode).foreach {
          marketShares => {
            marketShares.airlineShares.get(airline.id).foreach {
              marketShareOfThisAirline => {
                var percentage = BigDecimal(marketShareOfThisAirline.toDouble / marketShares.airlineShares.values.sum * 100)
                percentage = percentage.setScale(2, BigDecimal.RoundingMode.HALF_UP)
                factors.put(MARKET_SHARE(percentage), getMarketShareRelationshipBonus(percentage))
              }
            }
          }
        }

        //manager
        val currentCycle = CycleSource.loadCycle()
        val totalLevel : Int = ManagerSource.loadCountryDelegateByAirlineAndCountry(airline.id, countryCode).map(_.assignedTask.asInstanceOf[CountryManagerTask].level(currentCycle)).sum
        val levelMultiplier = getDelegateBonusMultiplier(targetCountry)
        factors.put(MANAGER(totalLevel), Math.round(totalLevel * levelMultiplier).toInt)

        //alliance member get at least a title if other alliance member has status
        val allTitles = CountryAirlineTitle.getTopTitlesByCountry(countryCode)
        airline.getAllianceId().foreach { allianceId =>
          val allianceMemberAirlineIds : List[Int] = AllianceSource.loadAllianceById(allianceId).get.members.filter(_.airline.id != airline.id).map(_.airline.id) //make sure it's not the current airline


          val allianceMemberNational = allTitles.find { t =>
            t.title == Title.NATIONAL_AIRLINE && allianceMemberAirlineIds.contains(t.airline.id)
          }
          val currentRelationship = factors.values.sum
          allianceMemberNational match {
            case Some(nationalTitle) =>
              val nationalPartnerBonus = Math.max(0, CountryAirlineTitle.PRIVILEGED_AIRLINE_RELATIONSHIP_THRESHOLD - currentRelationship)
              if (nationalPartnerBonus > 0) {
                factors.put(ALLIANCE_MEMBER_TITLE(nationalTitle), nationalPartnerBonus)
              }
            case None =>
              allTitles.find { t =>
                t.title == Title.PARTNERED_AIRLINE && allianceMemberAirlineIds.contains(t.airline.id)
              }.foreach { partneredTitle =>
                val privilegedPartnerBonus = Math.max(0, 5 + CountryAirlineTitle.ESTABLISHED_AIRLINE_RELATIONSHIP_THRESHOLD - currentRelationship)
                if (privilegedPartnerBonus > 0) {
                  factors.put(ALLIANCE_MEMBER_TITLE(partneredTitle), privilegedPartnerBonus)
                }
              }
          }
        }

      case None =>
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

  val getDelegateBonusMultiplier = (country : Country) => {
    val ratioToModelPower = Computation.MODEL_COUNTRY_POWER / (country.airportPopulation * country.income.toDouble).toLong
    val logRatio = Math.max(0.1, Math.log10(ratioToModelPower * 100) / 2) //0.1 to 1
    val levelMultiplier = CountryManagerTask.MAX_MANAGER_POWER / logRatio
    Math.min(CountryManagerTask.MAX_MANAGER_POWER, BigDecimal(levelMultiplier).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble)
  }
}

