package com.patson.model

import java.util.concurrent.TimeUnit

import com.github.benmanes.caffeine.cache.{CacheLoader, Caffeine, LoadingCache}
import com.patson.data.{CountrySource, CycleSource, ManagerSource}
import com.patson.util.{AirlineCache, CountryCache}

case class CountryAirlineTitle(country : Country, airline : Airline, title : Title.Value, score: Int = 0) {

  lazy val loyaltyBonus : Int = {
    val ratioToModelPower = country.airportPopulation * country.income.toDouble / Computation.MODEL_COUNTRY_POWER

    val ratio: Double = Math.max(0, math.log10(ratioToModelPower * 100) / 2)

    import CountryAirlineTitle._
    val loyaltyBonus = MIN_LOYALTY_BONUS + (MAX_LOYALTY_BONUS - MIN_LOYALTY_BONUS) * (1 - ratio)

    Math.round(loyaltyBonus / (title match {
      case Title.NATIONAL_AIRLINE => 1
      case Title.PARTNERED_AIRLINE => 3
      case _ => 0
    })).toInt
  }

  lazy val description = Title.description(title)
}


object Title extends Enumeration {
  type Title = Value
  val NATIONAL_AIRLINE, PARTNERED_AIRLINE, PRIVILEGED_AIRLINE, ESTABLISHED_AIRLINE, APPROVED_AIRLINE, NONE = Value
  val description = (title : Title.Value) => title match {
    case Title.NATIONAL_AIRLINE => "National"
    case Title.PARTNERED_AIRLINE => "Partnered"
    case Title.PRIVILEGED_AIRLINE => "Privileged"
    case Title.ESTABLISHED_AIRLINE => "Established"
    case Title.APPROVED_AIRLINE => "Approved"
    case Title.NONE => "None"
  }
}

object CountryAirlineTitle {
  val MAX_LOYALTY_BONUS = 30
  val MIN_LOYALTY_BONUS = 10

  val MAX_NATIONAL_AIRLINE_COUNT = 3 // What US will have
  val MAX_PARTNERED_AIRLINE_COUNT = 5 // What US will have

  def computeNationalAirlineCount(country: Country): Int = {
    val ratioToModelPower = country.airportPopulation * country.income.toDouble / Computation.MODEL_COUNTRY_POWER
    val ratio: Double = math.log10(ratioToModelPower * 100) / 2
    val result = Math.round(MAX_NATIONAL_AIRLINE_COUNT * ratio).toInt
    if (result <= 0) 1 else result
  }

  def computePartneredAirlineCount(country: Country): Int = {
    val ratioToModelPower = country.airportPopulation * country.income.toDouble / Computation.MODEL_COUNTRY_POWER
    val ratio: Double = math.log10(ratioToModelPower * 100) / 2
    val result = Math.round(MAX_PARTNERED_AIRLINE_COUNT * ratio).toInt
    if (result <= 0) 1 else result
  }

  val getBonusType : (Title.Value => BonusType.Value) = {
    case Title.NATIONAL_AIRLINE => BonusType.NATIONAL_AIRLINE
    case Title.PARTNERED_AIRLINE => BonusType.PARTNERED_AIRLINE
    case _ => BonusType.NO_BONUS
  }

  val PRIVILEGED_AIRLINE_RELATIONSHIP_THRESHOLD = 35
  val ESTABLISHED_AIRLINE_RELATIONSHIP_THRESHOLD = 20
  val APPROVED_AIRLINE_RELATIONSHIP_THRESHOLD = 5

  private case class CountryTitles(topTitles: List[CountryAirlineTitle], nextInLine: List[CountryAirlineTitle])

  // Caffeine cache: countryCode -> (top titles NATIONAL+PARTNERED, next 5 in line)
  private val countryTitlesCache: LoadingCache[String, CountryTitles] =
    Caffeine.newBuilder()
      .maximumSize(500)
      .expireAfterWrite(30, TimeUnit.MINUTES)
      .build(new CacheLoader[String, CountryTitles] {
        override def load(countryCode: String): CountryTitles = computeAllTitles(countryCode)
      })

  def invalidateAll(): Unit = countryTitlesCache.invalidateAll()

  private def computeAllTitles(countryCode: String): CountryTitles = {
    (CountryCache.getCountry(countryCode), CountrySource.loadMarketSharesByCountryCode(countryCode)) match {
      case (Some(country), Some(marketShares)) => computeAllTitlesFor(countryCode, country, marketShares)
      case _ => CountryTitles(List.empty, List.empty)
    }
  }

  private def computeAllTitlesFor(countryCode: String, country: Country, marketShares: CountryMarketShare): CountryTitles = {
    val totalPax = marketShares.airlineShares.values.sum.toDouble
    if (totalPax == 0) return CountryTitles(List.empty, List.empty)

    val currentCycle = CycleSource.loadCycle()
    val delegateLevels = ManagerSource.loadCountryDelegateLevelsByCountry(countryCode, currentCycle)
    val delegateMultiplier = AirlineCountryRelationship.getDelegateBonusMultiplier(country)
    val countryMutualRelationships = AirlineCountryRelationship.countryRelationships

    // Score each airline using HOME_COUNTRY + MARKET_SHARE + DELEGATE factors (no TITLE — avoids circularity).
    // Score is BigDecimal so pct acts as a tiebreaker within the same integer score.
    // Carry the Airline object to avoid repeated cache lookups during partition and result mapping.
    val airlineScores: List[(Airline, BigDecimal)] = marketShares.airlineShares.map { case (airlineId, pax) =>
      val airline = AirlineCache.getAirline(airlineId).getOrElse(Airline.fromId(airlineId))
      var score = BigDecimal(0)

      // HOME_COUNTRY factor
      airline.getCountryCode().foreach { homeCode =>
        val rel = countryMutualRelationships.getOrElse((homeCode, countryCode), 0)
        val mult = if (rel >= 0) AirlineCountryRelationship.HOME_COUNTRY_POSITIVE_RELATIONSHIP_MULTIPLIER
                   else AirlineCountryRelationship.HOME_COUNTRY_NEGATIVE_RELATIONSHIP_MULTIPLIER
        val bonus = if (rel >= 5) 20 else 0
        score += rel * mult + bonus
      }

      // MARKET_SHARE factor (pct kept as BigDecimal sub-unit for tiebreaking)
      val pct = BigDecimal(pax.toDouble / totalPax * 100).setScale(2, BigDecimal.RoundingMode.HALF_UP)
      score += AirlineCountryRelationship.getMarketShareRelationshipBonus(pct) + pct / 1000

      // DELEGATE factor
      score += Math.round(delegateLevels.getOrElse(airlineId, 0) * delegateMultiplier).toInt

      (airline, score)
    }.toList

    // Separate domestic (HQ in this country) from foreign
    val (domestic, foreign) = airlineScores.partition { case (airline, _) =>
      airline.getCountryCode().contains(countryCode)
    }

    val nationalQuota = computeNationalAirlineCount(country)
    val partneredQuota = computePartneredAirlineCount(country)
    val sortedDomestic = domestic.sortBy(-_._2)
    val sortedOthers = sortedDomestic.drop(nationalQuota) ++ foreign.sortBy(-_._2)

    val topTitles = sortedDomestic.take(nationalQuota).map { case (airline, _) =>
      CountryAirlineTitle(country, airline, Title.NATIONAL_AIRLINE)
    } ++ sortedOthers.take(partneredQuota).map { case (airline, _) =>
      CountryAirlineTitle(country, airline, Title.PARTNERED_AIRLINE)
    }

    val nextInLine = sortedOthers.drop(partneredQuota).take(5).map { case (airline, score) =>
      CountryAirlineTitle(country, airline, Title.PRIVILEGED_AIRLINE, score.toInt)
    }

    CountryTitles(topTitles, nextInLine)
  }

  val getTitle : (String, Airline) => CountryAirlineTitle = (countryCode : String, airline : Airline) => {
    getTopTitle(countryCode, airline).getOrElse {
      //no top country title, check lower ones
      val title : Title.Value = {
        val relationship = AirlineCountryRelationship.getAirlineCountryRelationship(countryCode, airline).relationship
        if (relationship < APPROVED_AIRLINE_RELATIONSHIP_THRESHOLD) {
          Title.NONE
        } else if (relationship < ESTABLISHED_AIRLINE_RELATIONSHIP_THRESHOLD) {
          Title.APPROVED_AIRLINE
        } else {
          if (relationship < PRIVILEGED_AIRLINE_RELATIONSHIP_THRESHOLD) {
            Title.ESTABLISHED_AIRLINE
          } else {
            Title.PRIVILEGED_AIRLINE
          }
        }
      }
      CountryAirlineTitle(CountryCache.getCountry(countryCode).get, airline, title)
    }
  }

  val getTopTitle : (String, Airline) => Option[CountryAirlineTitle] = (countryCode : String, airline : Airline) => {
    getTopTitlesByCountry(countryCode).find(_.airline.id == airline.id)
  }

  val getTopTitlesByCountry : String => List[CountryAirlineTitle] = (countryCode : String) => {
    countryTitlesCache.get(countryCode).topTitles
  }

  val getNextInLineByCountry : String => List[CountryAirlineTitle] = (countryCode : String) => {
    countryTitlesCache.get(countryCode).nextInLine
  }

  val getTopTitlesByAirline : Int => List[CountryAirlineTitle] = (airlineId : Int) => {
    CountrySource.loadMarketSharesByCriteria(List(("airline", airlineId))).flatMap { ms => getTopTitlesByCountry(ms.countryCode).find(_.airline.id == airlineId) }
  }

  import Title._
  val getTitleRequirements = (title : Title.Value, country : Country) => title match  {
    case NATIONAL_AIRLINE =>
      List(s"Relationship score reach top ${computeNationalAirlineCount(country)} among airlines headquartered in ${country.name}")
    case PARTNERED_AIRLINE =>
      List(s"Relationship score reach top ${computePartneredAirlineCount(country)} among all other airlines with presence in ${country.name}")
    case PRIVILEGED_AIRLINE =>
      List(s"Airline reaches relationship $PRIVILEGED_AIRLINE_RELATIONSHIP_THRESHOLD with ${country.name}")
    case ESTABLISHED_AIRLINE =>
      List(s"Airline reaches relationship $ESTABLISHED_AIRLINE_RELATIONSHIP_THRESHOLD with ${country.name}")
    case APPROVED_AIRLINE =>
      List(s"Airline reaches relationship $APPROVED_AIRLINE_RELATIONSHIP_THRESHOLD with ${country.name}")
    case NONE =>
      List(s"Airline relationship with ${country.name} below $APPROVED_AIRLINE_RELATIONSHIP_THRESHOLD")
  }

  val getTitleBonus = (title : Title.Value, country : Country) => title match {
    case NATIONAL_AIRLINE =>
      List(
        s"Loyalty +${CountryAirlineTitle(country, Airline.fromId(0), NATIONAL_AIRLINE).loyaltyBonus} on all airports in ${country.name}",
      )
    case PARTNERED_AIRLINE =>
      List(
        s"Loyalty +${CountryAirlineTitle(country, Airline.fromId(0), PARTNERED_AIRLINE).loyaltyBonus} on all airports in ${country.name}",
      )
    case PRIVILEGED_AIRLINE =>
      List(
        s"May build bases anywhere in ${country.name}."
      )
    case ESTABLISHED_AIRLINE =>
      List(
        s"May build bases in ${country.name}'s gateway airports.",
        s"May open international routes to ${country.name}'s very small airports."
      )
    case APPROVED_AIRLINE =>
      List(s"Easier negotiations in ${country.name}")
    case NONE =>
      List()
  }
}
