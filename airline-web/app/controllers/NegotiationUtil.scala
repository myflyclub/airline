package controllers

import com.patson.data.{AirlineSource, AirportSource, AllianceSource, CountrySource, CycleSource, DelegateSource, LinkSource, NegotiationSource}
import com.patson.model
import com.patson.model.FlightCategory.DOMESTIC
import com.patson.model.Title.APPROVED_AIRLINE
import com.patson.model.airplane.Model.Type.HELICOPTER
import com.patson.model.{LinkNegotiationDelegateTask, _}
import com.patson.model.airplane._
import com.patson.model.negotiation.LinkNegotiationDiscount
import com.patson.util.{AirportCache, ChampionUtil, CountryCache}

import scala.collection.mutable.ListBuffer
import scala.math.BigDecimal.RoundingMode
import scala.util.Random


object NegotiationUtil {
  val MAX_ASSIGNED_DELEGATE = 11
  val GREAT_SUCCESS_THRESHOLD = 0.95 // 5%
  val STARTUP_MAX_REPUTATION = 50

  def negotiate(info: NegotiationInfo, delegateCount: Int): NegotiationResult = {
    val odds = info.odds.get(delegateCount) match {
      case Some(value) => value
      case None => 1.0
    }
    val threshold = Math.random()
    NegotiationResult(1 - odds, threshold)
  }

  val NO_NEGOTIATION_REQUIRED = NegotiationInfo(List (), List (), List (), List (), 0, 0, 0, Map(0 -> 1))

  val normalizedCapacity : LinkClassValues => Double = (capacity : LinkClassValues) => {
    capacity(ECONOMY) * ECONOMY.spaceMultiplier + capacity(BUSINESS) * BUSINESS.spaceMultiplier + capacity(FIRST) * FIRST.spaceMultiplier
  }

  def getMaxFrequencyByFlightType(baseScale : Int, flightCategory: FlightCategory.Value) : Int = {
    getMaxFrequencyByGroup(baseScale, flightCategory)
  }

  def getMaxFrequencyByGroup(baseScale : Int, flightCategory : FlightCategory.Value) : Int = {
    var maxFrequency = flightCategory match {
      case FlightCategory.DOMESTIC => 3 + (baseScale * 2.9).toInt
      case FlightCategory.INTERNATIONAL => 4 + (baseScale * 1.9).toInt
    }

    maxFrequency
  }

  def getRequirementMultiplier(flightCategory : FlightCategory.Value) : Int = {
    flightCategory match {
      case FlightCategory.DOMESTIC => 1
      case FlightCategory.INTERNATIONAL => 2
    }
  }

  def getFromAirportRequirements(airline : Airline, newLink : Link, existingLinkOption : Option[Link], airlineLinks : List[Link]) = {
    import NegotiationRequirementType._
    val requirements = ListBuffer[NegotiationRequirement]()
    val isNewLink = existingLinkOption.isEmpty
    val flightCategory = Computation.getFlightCategory(newLink.from, newLink.to)
    val airport = newLink.from
    val baseOption = airline.getBases().find(_.airport.id == airport.id)

    val officeStaffCount : Int = baseOption.map(_.getOfficeStaffCapacity).getOrElse(0)
    val airlineLinksFromThisAirport = airlineLinks.filter(link => link.from.id == airport.id && (isNewLink || link.id != existingLinkOption.get.id))
    val currentOfficeStaffUsed = airlineLinksFromThisAirport.map(_.getFutureOfficeStaffRequired).sum
    val newOfficeStaffRequired = newLink.getFutureOfficeStaffRequired
    val newTotal = currentOfficeStaffUsed + newOfficeStaffRequired

    if (newTotal < officeStaffCount) {
      requirements.append(NegotiationRequirement(STAFF_CAP, 0, s"Requires ${newOfficeStaffRequired} staff, within your base capacity : ${newTotal} / ${officeStaffCount}"))
    } else {
      val requirement = (newTotal - officeStaffCount).toDouble / 10
      requirements.append(NegotiationRequirement(STAFF_CAP, requirement, s"Requires ${newOfficeStaffRequired} staff, over your base capacity : ${newTotal} / ${officeStaffCount}"))
    }

    val mutualRelationship = CountrySource.getCountryMutualRelationship(newLink.from.countryCode, newLink.to.countryCode)
    if (mutualRelationship < 0) {
      requirements.append(NegotiationRequirement(BAD_MUTUAL_RELATIONSHIP, mutualRelationship * -2, s"Bad mutual relationship between ${newLink.from.countryCode} and ${newLink.to.countryCode}"))
    }

    val newFrequency = newLink.futureFrequency()
    val frequencyDelta = newFrequency - existingLinkOption.map(_.futureFrequency()).getOrElse(0)
    if (frequencyDelta > 0) {
      val helicopterBonus = if(newLink.getAssignedModel().getOrElse(Model.fromId(0)).airplaneType == HELICOPTER) 6 else 0
      val baseLevel = baseOption.map(_.scale).getOrElse(0)
      val maxFrequency = getMaxFrequencyByFlightType(baseLevel, flightCategory) + helicopterBonus
      val multiplier = getRequirementMultiplier(flightCategory)

      if (newFrequency > maxFrequency) {
        requirements.append(NegotiationRequirement(EXCESSIVE_FREQUENCY, (newFrequency - maxFrequency) * multiplier, s"Excessive frequency: $newFrequency is over your level $baseLevel base's $maxFrequency frequency threshold"))
      }
    }


    requirements.toList
  }

  /**
    * What is the max preferred frequency on an airport by the airplane size.
    *
    * The bigger the airport the more "strict" it is - it wants bigger airplane
    * @param airplaneModel
    * @param airport

  def getMaxFrequencyByModel(airplaneModel : Model, airport : Airport) = {
    if (airport.size <= 3) { //too small to care
      None
    } else {
      val thresholdCapacity = 30 * (airport.size - 3) //airport would complain about any model lower than this threshold
      if (airplaneModel.capacity >= thresholdCapacity) {
        None
      } else {
        val frequencyRestriction = 14
        Some(FrequencyRestrictionByModel(thresholdCapacity, frequencyRestriction))
      }
    }

  }
  */

  case class FrequencyRestrictionByModel(threshold : Int, frequencyRestriction : Int)

  def getToAirportRequirements(airline : Airline, newLink : Link, existingLinkOption : Option[Link], airlineLinks : List[Link]) = {
    val newModel : Model = newLink.getAssignedModel().getOrElse(Model.fromId(0))
    val newFrequency = newLink.futureFrequency()
    val flightCategory = Computation.getFlightCategory(newLink.from, newLink.to)

    val existingModel = existingLinkOption match {
      case Some(link) => link.getAssignedModel() match {
        case Some(model) => model
        case None => Model.fromId(0)
      }
      case None => Model.fromId(0)
    }
    val existingFrequency = existingLinkOption.map(_.futureFrequency()).getOrElse(0)

    val aircraftSizeDelta = newModel.airplaneTypeSize * newFrequency - existingModel.airplaneTypeSize * existingFrequency
    val frequencyDelta = newFrequency - existingFrequency
    val requirements = ListBuffer[NegotiationRequirement]()

    val multiplier = if (flightCategory == FlightCategory.INTERNATIONAL) 2 else 1 + Math.floor(newLink.distance / 700) / 10
    val NEW_LINK_BASE_REQUIREMENT = 1
    val UPDATE_BASE_REQUIREMENT = 0.3

    import NegotiationRequirementType._

    if (existingLinkOption.isEmpty) {
      requirements.append(NegotiationRequirement(NEW_LINK, NEW_LINK_BASE_REQUIREMENT * multiplier, "New Flight Negotiation"))
      val mutualRelationship = CountrySource.getCountryMutualRelationship(newLink.from.countryCode, newLink.to.countryCode)
      if (mutualRelationship < 0) {
        requirements.append(NegotiationRequirement(BAD_MUTUAL_RELATIONSHIP, mutualRelationship * -2, s"Bad relationship between ${newLink.from.countryCode} and ${newLink.to.countryCode}"))
      }
    } else {
      requirements.append(NegotiationRequirement(UPDATE_LINK, UPDATE_BASE_REQUIREMENT * multiplier, "Update Flights"))
    }

    if (existingLinkOption.nonEmpty && aircraftSizeDelta > 0 && newModel.airplaneTypeSize != existingModel.airplaneTypeSize) {
      requirements.append(NegotiationRequirement(UPSIZE_GATE, aircraftSizeDelta * multiplier, s"Increase gate size to ${newModel.airplaneTypeLabel}"))
    }

    if (frequencyDelta > 0) {
      val capacityChangeCost = frequencyDelta.toDouble * newModel.airplaneTypeSize
      val slotText = if (frequencyDelta > 1) "gates" else "gate"
      requirements.append(NegotiationRequirement(INCREASE_CAPACITY, capacityChangeCost * multiplier, s"${frequencyDelta} ${newModel.airplaneTypeLabel} $slotText"))
    }

    if (frequencyDelta > 0) {
      val frequencyChangeCost = frequencyDelta.toDouble / 4
      val slotText = if (frequencyDelta > 1) "slots" else "slot"
      requirements.append(NegotiationRequirement(INCREASE_FREQUENCY, frequencyChangeCost, s"$frequencyDelta Landing $slotText"))
    }

    if (newLink.from.countryCode == newLink.to.countryCode && CountryAirlineTitle.getTitle(newLink.to.countryCode, airline).title == APPROVED_AIRLINE ) { //inferring "approved" is lowest other possible title
      requirements.append(NegotiationRequirement(LACK_COUNTRY_TITLE, 1.0, s"Extra regulatory scrutiny! (Does not apply if you have Established country title)"))
    }

    if (flightCategory == FlightCategory.INTERNATIONAL) {
      val airlineCounty = airline.getCountryCode().get
      if (airlineCounty != newLink.to.countryCode) {
        val toCountry = CountryCache.getCountry(newLink.to.countryCode).get
        val fromCountry = CountryCache.getCountry(newLink.from.countryCode).get
        var foreignAirlinePenalty = (12 - toCountry.openness) * 0.25 + (12 - fromCountry.openness) * 0.25
        if (existingLinkOption.isDefined) { //cheaper if it's already established
          foreignAirlinePenalty = foreignAirlinePenalty * 0.5
        }
        requirements.append(NegotiationRequirement(FOREIGN_AIRLINE, foreignAirlinePenalty, "Foreign Airline"))
      }
    }

    newLink.to.getFeatures().find(_.featureType == AirportFeatureType.GATEWAY_AIRPORT) match {
      case Some(_) =>
        val gatewayCost = 0.5
        requirements.append(NegotiationRequirement(GATEWAY, gatewayCost, "Tough Gateway Airport Negotiation"))
      case None =>
    }

    if (airline.getReputation() < STARTUP_MAX_REPUTATION) {
      val requirementTotal = requirements.foldLeft(0.0)((sum, requirement) => sum + requirement.value)
      val adjustment = -1 * Math.pow(requirementTotal, 0.7) + 1
      if (adjustment <= -1) {
        requirements.append(NegotiationRequirement(STARTUP, adjustment, s"Startup Vigor – better at negotiation while under ${STARTUP_MAX_REPUTATION} reputation"))
      }
    }

    existingLinkOption match {
      case Some(link) => //then consider existing load factor of this link
        val loadFactor : Double =(link.getTotalCapacity - link.getTotalSoldSeats).toDouble / link.getTotalCapacity
        if (loadFactor < 0.8) {
          val cost = Math.ceil(1 + (0.8 - loadFactor * 4))
          requirements.append(NegotiationRequirement(LOW_LOAD_FACTOR, cost, s"Low load factor ${BigDecimal(loadFactor * 100).setScale(2)}%"))
        }

      case None => //let's not make it too hard for newcomer for now
      //      case None => //existing competition
      //        //consider how many existing routes - if more than 2 reduce the odds
      //        val competingLinks = LinkSource.loadLinksByAirports(newLink.from.id, newLink.to.id, LinkSource.ID_LOAD) ++ LinkSource.loadLinksByAirports(newLink.to.id, newLink.from.id, LinkSource.ID_LOAD)
      //        val competingLinksCount = competingLinks.filter(_.capacity.total > 0).size
      //        if (competingLinksCount >= 2) {
      //          requirements.append(NegotiationRequirement(EXISTING_COMPETITION, competingLinksCount - 2))
      //        }
    }
    requirements.toList
  }
//  val getStaffRequired = (link : Link) => {
//    Computation.getFlightCategory(link.from, link.to) match {
//      case FlightCategory.DOMESTIC => 3
//      case FlightCategory.INTERNATIONAL => 6
//    }
//  }

  def getNegotiationRequirements(newLink : Link, existingLinkOption : Option[Link], airline : Airline, airlineLinks : List[Link]) = {
    val fromAirportRequirements : List[NegotiationRequirement] = getFromAirportRequirements(airline, newLink, existingLinkOption, airlineLinks)
    val toAirportRequirements : List[NegotiationRequirement] = getToAirportRequirements(airline, newLink, existingLinkOption, airlineLinks)

    (fromAirportRequirements, toAirportRequirements)
  }


  def getAllNegotiationDiscounts(fromAirport : Airport, toAirport : Airport, airline : Airline, allianceMembers : List[AllianceMember]) : (List[NegotiationDiscount], List[NegotiationDiscount]) = {
    val fromAirportDiscounts = getNegotiationDiscountsByAirport(fromAirport, airline, allianceMembers)
    val toAirportDiscounts = getNegotiationDiscountsByAirport(toAirport, airline, allianceMembers)
    val generalDiscounts : List[NegotiationDiscount] = {
      NegotiationSource.loadLinkDiscounts(airline.id, fromAirport.id, toAirport.id).map { discount =>
        PreviousNegotiationDiscount(discount.discount.toDouble, discount.expiry - CycleSource.loadCycle())
      }
    } ++ getMaidenInternationalLinkDiscount(fromAirport, toAirport)
    (fromAirportDiscounts ++ generalDiscounts, toAirportDiscounts ++ generalDiscounts)
  }

  def getMaidenInternationalLinkDiscount(fromAirport: Airport, toAirport: Airport) = {
    if (fromAirport.countryCode == toAirport.countryCode) {
      List.empty
    } else {
      val existingLinks =
        LinkSource.loadFlightLinksByCriteria(List(("from_country", fromAirport.countryCode),("to_country", toAirport.countryCode))) ++
        LinkSource.loadFlightLinksByCriteria(List(("from_country", toAirport.countryCode),("to_country", fromAirport.countryCode)))
      if (existingLinks.isEmpty) {
        List(SimpleNegotiationDiscount(NegotiationDiscountType.MAIDEN_INTERNATIONAL, 0.2))
      } else {
        List.empty
      }
    }
  }

  def getNegotiationDiscountsByAirport(airport : Airport, airline : Airline, allianceMembers : List[AllianceMember]) = {
    val discounts = ListBuffer[NegotiationDiscount]()
    //how busy is this airport
    //val airportLinks =  LinkSource.loadLinksByFromAirport(airport.id) ++ LinkSource.loadLinksByToAirport(airport.id)
    //val totalFrequency = airportLinks.map(_.frequency).sum
    import NegotiationDiscountType._
    /*
    if (totalFrequency <= 50 * airport.size) { //under serve
      discounts.append(NegotiationDiscount(BELOW_CAPACITY, 0.2)) //20%
    } else if (totalFrequency >= 300 * airport.size) {
      //penalty multiplier start from 1 up to 10
      val multiplier = Math.min(10, 1 + (totalFrequency - 300 * airport.size).toDouble / (100 * airport.size))
      discounts.append(NegotiationDiscount(OVER_CAPACITY, -0.2 * multiplier)) //crowded, start from 20% to up to 200% penalty
    }
    */


    // 20 -> 0.2
    // 50 -> 0.2 + 0.15
    // 100 -> 0.35 + 0.15
    val relationship = AirlineCountryRelationship.getAirlineCountryRelationship(airport.countryCode, airline).relationship
    if (relationship >= 0) {
      var discount = relationship * 0.01
      discount = Math.min(discount, 0.4)
      discounts.append(SimpleNegotiationDiscount(COUNTRY_RELATIONSHIP, discount))
    } else if (relationship < 0) { //very penalizing
      val discount = relationship * 0.025
      discounts.append(SimpleNegotiationDiscount(COUNTRY_RELATIONSHIP, discount))
    }

    val loyalty = airport.getAirlineLoyalty(airline.id)
    val MAX_LOYALTY_DISCOUNT = 0.5
    if (loyalty > 0) {
      val discount = Math.min(MAX_LOYALTY_DISCOUNT, loyalty / AirlineAppeal.MAX_LOYALTY)
      discounts.append(SimpleNegotiationDiscount(LOYALTY, discount))
    }

    val airportChampionAirlineIds = ChampionUtil.loadAirportChampionInfoByAirport(airport.id).map(_.loyalist.airline.id)
    val allianceBases = allianceMembers.flatMap(_.airline.getBases()).filter(_.airport.id == airport.id).filter(_.airline != airline.id)

    val championAllianceBases = allianceBases.filter(base => airportChampionAirlineIds.contains(base.airline.id))
    if (championAllianceBases.find(_.headquarter).isDefined) {
      discounts.append(SimpleNegotiationDiscount(ALLIANCE_BASE, 0.3))
    } else if (!championAllianceBases.isEmpty) {
      discounts.append(SimpleNegotiationDiscount(ALLIANCE_BASE, 0.2))
    }

    airport.getAirlineBase(airline.id).foreach {
      base => if (base.scale >= 3) { //to avoid spamming base to increase freq and leave
        val discount = 0.02 * base.scale
        discounts.append(SimpleNegotiationDiscount(BASE, discount))
      }
    }


    discounts.toList
  }

  val MAX_TOTAL_DISCOUNT = 0.8 //at most 80% off

  def getLinkNegotiationInfo(airline : Airline, newLink : Link, existingLinkOption : Option[Link]) : NegotiationInfo = {
    val fromAirport : Airport = newLink.from
    val toAirport : Airport = newLink.to
    val newModel : Model = newLink.getAssignedModel().getOrElse(Model.fromId(0))
    val newFrequency = newLink.futureFrequency()

    val existingModel = existingLinkOption match {
      case Some(link) => link.getAssignedModel() match {
        case Some(model) => model
        case None => Model.fromId(0)
      }
      case None => Model.fromId(0)
    }
    val existingFrequency = existingLinkOption.map(_.futureFrequency()).getOrElse(0)

    val aircraftSizeFreqDelta = Model.Type.size(newModel.airplaneType) * newFrequency - Model.Type.size(existingModel.airplaneType) * existingFrequency
//    println(s"info size adjust: ${aircraftSizeFreqDelta}")
    val frequencyDelta = newFrequency - existingFrequency

    val airlineLinks = LinkSource.loadFlightLinksByAirlineId(airline.id)

    //reduction of service is always okay for now
    if (aircraftSizeFreqDelta <= 0 && frequencyDelta <= 0) {
      return NegotiationUtil.NO_NEGOTIATION_REQUIRED
    }

    val (fromAirportRequirements, toAirportRequirements) = getNegotiationRequirements(newLink, existingLinkOption, airline, airlineLinks)

    val allianceMembers = airline.getAllianceId() match {
      case Some(allianceId) => AllianceSource.loadAllianceById(allianceId, true).get.members
      case None => List.empty
    }
    val (fromAirportDiscounts, toAirportDiscounts) = getAllNegotiationDiscounts(fromAirport, toAirport, airline, allianceMembers)

    val fromRequirementBase = fromAirportRequirements.map(_.value).sum
    val toRequirementBase = toAirportRequirements.map(_.value).sum
    val totalFromDiscount = Math.min(MAX_TOTAL_DISCOUNT, fromAirportDiscounts.map(_.value).sum)
    val totalToDiscount = Math.min(MAX_TOTAL_DISCOUNT, toAirportDiscounts.map(_.value).sum)
    val fromAirportRequirementValue = fromRequirementBase * (1 - totalFromDiscount)
    val toAirportRequirementValue = toRequirementBase * (1 - totalToDiscount)
    val finalRequirementValue = fromAirportRequirementValue + toAirportRequirementValue

    NegotiationInfo(fromAirportRequirements, toAirportRequirements, fromAirportDiscounts, toAirportDiscounts, totalFromDiscount, finalToDiscountValue = totalToDiscount, finalRequirementValue, computeOdds(finalRequirementValue, Math.min(MAX_ASSIGNED_DELEGATE, airline.getDelegateInfo.availableCount)))
  }

  /**
    *
    * @param finalRequirementValue
    * @param maxDelegateCount
    * @return a map of delegate count vs odds, which 0 <= odds <= 1
    */
  def computeOdds(finalRequirementValue : Double, maxDelegateCount : Int) : Map[Int, Double] = {
    val requiredDelegates = finalRequirementValue
    var accumulativeOdds = 0.0
    var foundMax = false
    (0 to maxDelegateCount).map { delegateCount =>
      val base = Math.min(1, (15 - requiredDelegates) * 0.04 / (requiredDelegates / Math.ceil(requiredDelegates)))
      val oddsPerBaseDelegate = base / (Math.max(1, requiredDelegates))
      val oddsForThisDelegateCount : Double =
        if (finalRequirementValue == 0) {
          1
        } else {
          if (delegateCount < requiredDelegates) {
            0
          } else {

            if (delegateCount < requiredDelegates + 1) {
              accumulativeOdds = base
            } else {

              accumulativeOdds = Math.min(1, accumulativeOdds +  oddsPerBaseDelegate * Math.pow(0.95, delegateCount - requiredDelegates.toInt))
            }
            accumulativeOdds
          }
        }
      (delegateCount, oddsForThisDelegateCount)
    }.filter { //keep only the first count that reaches 1
      case (delegateCount, oddsForThisDelegateCount) =>
        if (foundMax) {
          false
        } else {
          foundMax = (oddsForThisDelegateCount == 1)
          true
        }
    }.toMap
  }

  def getLinkBonus(link : Link, monetaryBaseValue : Long, delegates : List[BusyDelegate]) : NegotiationBonus = {
    NegotiationBonus.drawBonus(monetaryBaseValue, delegates, link.to)
  }

  val MAX_NEXT_NEGOTIATION_DISCOUNT = 0.2
  def getNextNegotiationDiscount(link : Link, negotiationResult: NegotiationResult) = {
    if (!negotiationResult.isSuccessful) { //only gives discount if it was unsuccessful
      val ratio = negotiationResult.result / negotiationResult.threshold
      var discount = BigDecimal.valueOf(ratio * MAX_NEXT_NEGOTIATION_DISCOUNT).setScale(2, BigDecimal.RoundingMode.HALF_UP)
      if (discount == 0) { //at least 1%
        discount = BigDecimal(0.01)
      }
      Some(LinkNegotiationDiscount(link.airline, link.from, link.to, discount, CycleSource.loadCycle() + LinkNegotiationDiscount.DURATION))
    } else {
      None
    }
  }

}

//class NegotiationOdds() {
//  private[this] val factors = scala.collection.mutable.LinkedHashMap[NegotationFactor.Value, Double]()
//  def addFactor(factor : NegotationFactor.Value, value : Double) = {
//    factors.put(factor, value)
//  }
//
//  def value = factors.values.sum match {
//    case x if x > 1 => 1.0
//    case x if x < 0 => 0.0
//    case x => x
//  }
//
//  def getFactors : Map[NegotationFactor.Value, Double] = factors.toMap
//}

case class NegotiationInfo(fromAirportRequirements : List[NegotiationRequirement], toAirportRequirements : List[NegotiationRequirement], fromAirportDiscounts : List[NegotiationDiscount], toAirportDiscounts : List[NegotiationDiscount], finalFromDiscountValue : Double, finalToDiscountValue : Double, finalRequirementValue : Double, odds : Map[Int, Double], remarks : Option[String] = None)

object NegotiationRequirementType extends Enumeration {
  type NegotiationRequirementType = Value
  val NEW_LINK, UPDATE_LINK, INCREASE_CAPACITY, UPSIZE_GATE, INCREASE_FREQUENCY, LACK_COUNTRY_TITLE, EXCESSIVE_FREQUENCY, LOW_LOAD_FACTOR, FOREIGN_AIRLINE, GATEWAY, STAFF_CAP, BAD_MUTUAL_RELATIONSHIP, STARTUP, OTHER = Value
}

object NegotiationDiscountType extends Enumeration {
  type NegotiationDiscountType = Value
  val COUNTRY_RELATIONSHIP, BELOW_CAPACITY, OVER_CAPACITY, LOYALTY, BASE, ALLIANCE_BASE, NEW_AIRLINE, PREVIOUS_NEGOTIATION, MAIDEN_INTERNATIONAL = Value
}

case class NegotiationRequirement(requirementType : NegotiationRequirementType.Value, value : Double, description : String) {
}

abstract class NegotiationDiscount(val adjustmentType : NegotiationDiscountType.Value, val value : Double) {
  import controllers.NegotiationDiscountType._
  def description(airport : Airport) =  adjustmentType match {
    case COUNTRY_RELATIONSHIP => s"Country Relationship with ${airport.countryCode}"
    case BELOW_CAPACITY => s"${airport.displayText} is under capacity"
    case OVER_CAPACITY => s"${airport.displayText} is over capacity"
    case LOYALTY => s"Loyalty of ${airport.displayText}"
    case BASE => s"Airline base"
    case ALLIANCE_BASE => s"Alliance partner hq/base is ranked champion "
    case NEW_AIRLINE => s"New airline bonus"
    case MAIDEN_INTERNATIONAL => "No flights between these 2 countries yet"
    case _ => s"Unknown"
  }
}

case class SimpleNegotiationDiscount(override val adjustmentType : NegotiationDiscountType.Value, override val value : Double) extends NegotiationDiscount(adjustmentType, value) {
}
case class PreviousNegotiationDiscount(override val value : Double, duration: Int) extends NegotiationDiscount(NegotiationDiscountType.PREVIOUS_NEGOTIATION, value) {
  override def description(airport : Airport) : String = s"Previous negotiation progress (Expires in $duration week(s))"
}

case class NegotiationResult(threshold : Double, result : Double) {
  val isSuccessful = result >= threshold
  val isGreatSuccess = isSuccessful && result >= NegotiationUtil.GREAT_SUCCESS_THRESHOLD
  println(s"negotiation result: threshold $threshold vs result $result. Great success ? $isGreatSuccess")

  val SESSION_COUNT = 5
  def getNegotiationSessions() : NegotiationSession = {
    //    val BASE_PASSING_SCORE = 100 //make it a more than 0...just for nicer display
    //
    val passingScore = 75 + threshold * 25

    val score = 75 + result * 25

    val average = score / SESSION_COUNT //average score for each session

    val sessionScores = ListBuffer[Double]()
    for (i <- 0 until SESSION_COUNT) {
      sessionScores.append(average)
    }
    //now generate randomness 100+ and 100- randomly assigned to each number
    for (i <- 0 until 10) {
      val index1 = Random.nextInt(SESSION_COUNT)
      val index2 = Random.nextInt(SESSION_COUNT)
      val variation = Random.nextInt(5)
      sessionScores(index1) = sessionScores(index1) + variation
      sessionScores(index2) = sessionScores(index2) - variation
    }
    NegotiationSession(passingScore, sessionScores.toList)
  }
}

case class NegotiationSession(passingScore : Double, sessionScores : List[Double])

object NegotiationBonus {
  val pool = List(
    NegotiationCashBonusTemplate(1), NegotiationCashBonusTemplate(2), NegotiationCashBonusTemplate(5), NegotiationCashBonusTemplate(10),
    NegotiationCooldownBonusTemplate(LinkNegotiationDelegateTask.COOL_DOWN / 2), NegotiationCooldownBonusTemplate(LinkNegotiationDelegateTask.COOL_DOWN / 2), NegotiationCooldownBonusTemplate(LinkNegotiationDelegateTask.COOL_DOWN),
    NegotiationLoyaltyBonusTemplate(1), NegotiationLoyaltyBonusTemplate(2), NegotiationLoyaltyBonusTemplate(5)
  )
  val random = new Random()
  def drawBonus(monetaryBaseValue : Long, delegates: List[BusyDelegate], airport : Airport): NegotiationBonus = {
    pool(random.nextInt(pool.size)).computeBonus(monetaryBaseValue, delegates, airport)
  }
}

abstract class NegotiationBonus {
  def description : String
  def intensity : Int
  def apply(airline : Airline) : Unit
}

case class NegotiationCashBonus(cash : Long, description : String, val intensity: Int) extends NegotiationBonus {
  def apply(airline : Airline) = {
    AirlineSource.adjustAirlineBalance(airline.id, cash)
  }
}

case class NegotiationCooldownBonus(delegates : List[BusyDelegate], cooldownDiscount : Int, description : String, val intensity: Int) extends NegotiationBonus {
  def apply(airline : Airline) = {
    val currentCycle = CycleSource.loadCycle()

    val updatingDelegates = ListBuffer[BusyDelegate]()
    val freeDelegates = ListBuffer[BusyDelegate]()

    delegates.foreach { delegate =>
      delegate.availableCycle.foreach { cycle =>
        val newCycle = cycle - cooldownDiscount
        if (newCycle > currentCycle) {
          updatingDelegates.append(delegate.copy(availableCycle = Some(newCycle)))
        } else {
          freeDelegates.append(delegate)
        }
      }
    }

    DelegateSource.updateBusyDelegateAvailableCycle(updatingDelegates.toList)
    DelegateSource.deleteBusyDelegates(freeDelegates.toList)
  }
}

case class NegotiationLoyaltyBonus(airport : Airport, loyaltyBonus: Double, duration : Int, description : String, val intensity: Int) extends NegotiationBonus {
  def apply(airline : Airline) = {
    val cycle = CycleSource.loadCycle()
    AirportSource.saveAirlineAppealBonus(airport.id, airline.id, AirlineBonus(BonusType.NEGOTIATION_BONUS, AirlineAppeal(loyalty = loyaltyBonus), Some(cycle + duration)))
  }
}

abstract class NegotiationBonusTemplate {
  val INTENSITY_MAX = 5
  val intensity : Int = {
    Math.min(INTENSITY_MAX, intensityCompute)
  }//intensity of the bonus, starting from 1 , max at 5
  val intensityCompute : Int
  def computeBonus(monetaryBaseValue : Long, delegates : List[BusyDelegate], airport : Airport) : NegotiationBonus //monetaryBaseValue : for example for link, it would be standardPrice * capacityChange => 1 week of revenue
}

case class NegotiationCashBonusTemplate(factor : Int) extends NegotiationBonusTemplate {
  val intensityCompute = factor / 2 + 1
  val integerInstance = java.text.NumberFormat.getIntegerInstance

  override def computeBonus(monetaryBaseValue : Long, delegates : List[BusyDelegate], airport : Airport) : NegotiationBonus = {
    val cash = monetaryBaseValue * factor
    val description =
      if (factor <= 1) {
        s"The airport authority decided to provide a modest subsidy of $$${integerInstance.format(cash)}!"
      } else if (factor <= 3) {
        s"The airport authority decided to provide a sizable subsidy of $$${integerInstance.format(cash)}!"
      } else if (factor <= 5) {
        s"The airport authority decided to provide a large subsidy of $$${integerInstance.format(cash)}!"
      } else {
        s"The airport authority decided to provide a generous subsidy of $$${integerInstance.format(cash)}!"
      }
    NegotiationCashBonus(cash, description, intensity)
  }
}

case class NegotiationCooldownBonusTemplate(discountCycle : Int) extends NegotiationBonusTemplate {
  val intensityCompute = if (discountCycle <= 3) 1 else 2
  override def computeBonus(monetaryBaseValue : Long, delegates : List[BusyDelegate], airport : Airport) : NegotiationBonus = {
    val description =
      if (discountCycle < LinkNegotiationDelegateTask.COOL_DOWN) {
        s"The delegates are encouraged by the success, cooldown reduced by $discountCycle weeks!"
      } else {
        s"The delegates are energized by the great success, they are ready for next assignment!"
      }
    NegotiationCooldownBonus(delegates, discountCycle, description, intensity)
  }
}

case class NegotiationLoyaltyBonusTemplate(bonusFactor : Int) extends NegotiationBonusTemplate {
  val duration = 52
  val MAX_BONUS = 20
  val intensityCompute = bonusFactor + 1
  override def computeBonus(monetaryBaseValue : Long, delegates : List[BusyDelegate], airport : Airport) : NegotiationBonus = {
    val loyaltyBonus = BigDecimal(Math.min(20, monetaryBaseValue.toDouble / airport.power * 1000000)).setScale(2, RoundingMode.HALF_UP)
    val description =
      if (loyaltyBonus <= 2) {
        s"Some media coverage increases loyalty by $loyaltyBonus at ${airport.displayText} for $duration weeks"
      } else if (loyaltyBonus <= 5) {
        s"Great media coverage increases loyalty by $loyaltyBonus at ${airport.displayText} for $duration weeks"
      } else {
        s"Extensive media coverage increases loyalty by $loyaltyBonus at ${airport.displayText} for $duration weeks"
      }
    NegotiationLoyaltyBonus(airport, loyaltyBonus.toDouble, duration, description, intensity)
  }
}







