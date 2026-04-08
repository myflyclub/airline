package controllers

import com.patson.data.{AirlineSource, AirportSource, CountrySource, CycleSource, ManagerSource, LinkSource, NegotiationSource}
import com.patson.model.Title.APPROVED_AIRLINE
import com.patson.model.{NegoHopper, _}
import com.patson.model.airplane._
import com.patson.model.negotiation.LinkNegotiationDiscount
import com.patson.util.{AirportStatisticsCache, AllianceCache, ChampionUtil, CountryCache}

import scala.collection.mutable.ListBuffer
import scala.math.BigDecimal.RoundingMode
import scala.util.Random


object NegotiationUtil {
  val MAX_ASSIGNED_DELEGATE = 11
  val GREAT_SUCCESS_THRESHOLD = 0.9
  val STARTUP_MAX_REPUTATION = 80
  val BAD_FAILURE_RATIO_THRESHOLD = 0.35

  private def clamp01(value: Double): Double = Math.max(0.0, Math.min(1.0, value))

  /**
   * lower success odds => higher risk => more likely dramatic swing
   */
  def swingTriggerChance(odds: Double, success: Boolean): Double = {
    val risk = clamp01(1 - odds)
    val base = if (success) 0.08 else 0.10
    clamp01(base + 0.35 * Math.pow(risk, 1.4))
  }

  def bigSwingChance(odds: Double): Double = {
    val risk = clamp01(1 - odds)
    clamp01(0.10 + 0.70 * Math.pow(risk, 1.8))
  }

  /**
   * Returns the startup vigor adjustment (always <= 0, i.e. a discount) to add to the
   * requirement total, or None if no adjustment applies.
   *
   * Full vigor:  rep < STARTUP_MAX_REPUTATION
   * Fading:      STARTUP_MAX_REPUTATION <= rep < STARTUP_MAX_REPUTATION * 2
   * None:        rep >= STARTUP_MAX_REPUTATION * 2
   */
  def computeStartupVigorAdjustment(reputation: Double, requirementTotal: Double): Option[Double] = {
    val startupFadeCeiling = STARTUP_MAX_REPUTATION * 2
    val rawAdjustment = -1 * Math.pow(requirementTotal, 0.5) + 0.5
    if (reputation < STARTUP_MAX_REPUTATION) {
      if (rawAdjustment <= 0) Some(rawAdjustment) else None
    } else if (reputation < startupFadeCeiling) {
      val strength = 0.1 + 0.9 * (startupFadeCeiling - reputation) / (startupFadeCeiling - STARTUP_MAX_REPUTATION)
      val fadedAdjustment = rawAdjustment * strength
      if (fadedAdjustment <= 0) Some(fadedAdjustment) else None
    } else {
      None
    }
  }

  def rollSwingEvent(odds: Double, success: Boolean): Boolean = {
    Math.random() < swingTriggerChance(odds, success)
  }

  def isBadFailure(result: NegotiationResult): Boolean = {
    !result.isSuccessful && result.result <= result.threshold * BAD_FAILURE_RATIO_THRESHOLD
  }

  def negotiate(info: NegotiationInfo, delegateCount: Int): NegotiationResult = {
    val odds = info.odds.get(delegateCount) match {
      case Some(value) => value
      case None => 1.0
    }
    val threshold = Math.random()
    NegotiationResult(1 - odds, threshold)
  }

  def getMaxFrequencyByGroup(baseScale: Int, flightCategory: FlightCategory.Value, hasHigherFrequencyBase: Boolean) : Int = {
    val freqBonus = if (hasHigherFrequencyBase) 7 else 0
    val maxFrequency = flightCategory match {
      case FlightCategory.DOMESTIC => 2 + (baseScale * 5.5).toInt
      case FlightCategory.INTERNATIONAL => 1 + (baseScale * 5.4).toInt
    }

    maxFrequency + freqBonus
  }

  def getRequirementMultiplier(flightCategory : FlightCategory.Value) : Int = {
    flightCategory match {
      case FlightCategory.DOMESTIC => 1
      case FlightCategory.INTERNATIONAL => 2
    }
  }

  def getFromAirportRequirements(airline : Airline, newLink : Link, existingLinkOption : Option[Link], airlineLinks : List[Link]) : List[NegotiationRequirement] = {
    import NegotiationRequirementType._

    val requirements = ListBuffer[NegotiationRequirement]()
    val isNewLink = existingLinkOption.isEmpty
    val flightCategory = Computation.getFlightCategory(newLink.from, newLink.to)
    val airport = newLink.from
    val baseOption = airline.getBases().find(_.airport.id == airport.id)
    val baseSpecializations = baseOption.toList.flatMap(_.specializations)

    val newModel = newLink.getAssignedModel().getOrElse(Model.fromId(0))
    val existingModel = existingLinkOption.flatMap(_.getAssignedModel()).getOrElse(Model.fromId(0))
    val existingFrequency = existingLinkOption.map(_.futureFrequency()).getOrElse(0)
    val newFrequency = newLink.futureFrequency()
    val frequencyDelta = newFrequency - existingFrequency
    val aircraftSizeDelta = newModel.airplaneTypeSize * newFrequency - existingModel.airplaneTypeSize * existingFrequency
    if (frequencyDelta < 0 || (frequencyDelta == 0 && aircraftSizeDelta < 0)) {
      return requirements.toList
    } else if (frequencyDelta > 0) {
      val negotiationHopper = if(baseSpecializations.contains(AirlineBaseSpecialization.NEGOTIATION_HOPPER) && newLink.distance < NegoHopper.maxDistance) NegoHopper.maxDistance / NegoHopper.distanceIncrement - newLink.distance / NegoHopper.distanceIncrement else 0
      val baseLevel = baseOption.map(_.scale).getOrElse(0)
      val maxFrequency = getMaxFrequencyByGroup(baseLevel, flightCategory, hasHigherFrequencyBase = airport.isOrangeAirport) + negotiationHopper
      val multiplier = getRequirementMultiplier(flightCategory)

      if (newFrequency > maxFrequency) {
        requirements.append(NegotiationRequirement(EXCESSIVE_FREQUENCY, (newFrequency - maxFrequency) * multiplier, s"Excessive frequency: $newFrequency is over your level $baseLevel base's $maxFrequency frequency threshold"))
      }
    }

    val officeStaffCount : Int = baseOption.map(_.getOfficeStaffCapacity).getOrElse(0)
    val airlineLinksFromThisAirport = airlineLinks.filter(link => link.from.id == airport.id && (isNewLink || link.id != existingLinkOption.get.id))
    val currentOfficeStaffUsed = airlineLinksFromThisAirport.map(_.getFutureOfficeStaffRequired).sum
    val newOfficeStaffRequired = newLink.getFutureOfficeStaffRequired
    val newTotal = currentOfficeStaffUsed + newOfficeStaffRequired
    if (newTotal < officeStaffCount) {
      requirements.append(NegotiationRequirement(STAFF_CAP, 0, s"Requires ${newOfficeStaffRequired} staff, within your base capacity : ${newTotal} / ${officeStaffCount}"))
    } else {
      val requirement = newTotal - officeStaffCount
      requirements.append(NegotiationRequirement(STAFF_CAP, requirement, s"Requires ${newOfficeStaffRequired} staff, over your base capacity : ${newTotal} / ${officeStaffCount}"))
    }

    val mutualRelationship = CountrySource.getCountryMutualRelationship(newLink.from.countryCode, newLink.to.countryCode)
    if (mutualRelationship < 0) {
      requirements.append(NegotiationRequirement(BAD_MUTUAL_RELATIONSHIP, mutualRelationship * -2, s"Bad mutual relationship between ${newLink.from.countryCode} and ${newLink.to.countryCode}"))
    }

    val fromAirportStats = AirportStatisticsCache.getAirportStatistics(airport.id).getOrElse(AirportStatistics(0, 0, 0, 0, 0, 0))
    val fromCongestion = fromAirportStats.congestion
    if (fromCongestion < 0.6) {
      requirements.append(NegotiationRequirement(SLOT_CONTROLLED, 0, "Low congestion: Level 1 Coordination, no slot control"))
    } else if (fromCongestion <= 1.0) {
      requirements.append(NegotiationRequirement(SLOT_CONTROLLED, 0.5, "Moderate congestion: Level 2 Coordination, partial slot control"))
    } else {
      requirements.append(NegotiationRequirement(SLOT_CONTROLLED, 1.5, "High congestion: Level 3 Coordination, full slot control"))
    }

    requirements.toList
  }


  def getToAirportRequirements(airline: Airline, newLink: Link, existingLinkOption: Option[Link], airlineLinks: List[Link]): List[NegotiationRequirement] = {
    val newModel: Model = newLink.getAssignedModel().getOrElse(Model.fromId(0))
    val newFrequency = newLink.futureFrequency()
    val flightCategory = Computation.getFlightCategory(newLink.from, newLink.to)
    val baseSpecializations = newLink.from.getAirlineBase(airline.id).toList.flatMap(_.specializations)

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

    val negotiationSmall = if(baseSpecializations.contains(AirlineBaseSpecialization.NEGOTIATION_SMALL) && newLink.to.size <= NegoSmall.maxAirportSize) 0.5 else 0
    val negotiationSmallText = if(baseSpecializations.contains(AirlineBaseSpecialization.NEGOTIATION_SMALL) && newLink.to.size <= NegoSmall.maxAirportSize) ", Small Airport Negotiator" else ""
    val multiplier = if (flightCategory == FlightCategory.INTERNATIONAL) 2 - negotiationSmall else 1 + Math.floor(newLink.distance / 700) / 10 - negotiationSmall
    val NEW_LINK_BASE_REQUIREMENT = 1
    val UPDATE_BASE_REQUIREMENT = 0.3

    import NegotiationRequirementType._

    if (existingLinkOption.nonEmpty && aircraftSizeDelta > 0 && newModel.airplaneTypeSize != existingModel.airplaneTypeSize) {
      requirements.append(NegotiationRequirement(UPSIZE_GATE, aircraftSizeDelta * multiplier, s"Increase gate size to ${newModel.airplaneTypeLabel}${negotiationSmallText}"))
    }

    if (frequencyDelta != 0) {
      val capacityChangeCost = frequencyDelta.toDouble * newModel.airplaneTypeSize
      val slotText = if (frequencyDelta > 1 || frequencyDelta < 1) "gates" else "gate"
      requirements.append(NegotiationRequirement(INCREASE_CAPACITY, capacityChangeCost * multiplier, s"${frequencyDelta} ${newModel.airplaneTypeLabel} $slotText${negotiationSmallText}"))
    }

    if (frequencyDelta != 0) {
      val frequencyChangeCost = frequencyDelta.toDouble / 4
      val slotText = if (frequencyDelta > 1 || frequencyDelta < 1) "slots" else "slot"
      requirements.append(NegotiationRequirement(INCREASE_FREQUENCY, frequencyChangeCost, s"$frequencyDelta Landing $slotText"))
    }

    if (existingLinkOption.nonEmpty && aircraftSizeDelta < 0 && frequencyDelta <= 0) {
      return requirements.toList //return early if it's a reduction (smaller plane, same or fewer flights)
    }

    if (existingLinkOption.isEmpty) {
      val negotiationLong = if(baseSpecializations.contains(AirlineBaseSpecialization.NEGOTIATION_LONG) && newLink.distance > NegoLong.minDistance) 0.95 - (newLink.distance - NegoLong.minDistance) / NegoLong.minDistance else 1
      val negotiationLongText = if(baseSpecializations.contains(AirlineBaseSpecialization.NEGOTIATION_LONG) && newLink.distance > NegoLong.minDistance) "New Flight – Foreign Mission Negotiator" else "New Flight Negotiation"
      requirements.append(NegotiationRequirement(NEW_LINK, NEW_LINK_BASE_REQUIREMENT * multiplier * negotiationLong, negotiationLongText))
      val mutualRelationship = CountrySource.getCountryMutualRelationship(newLink.from.countryCode, newLink.to.countryCode)
      if (mutualRelationship < 0) {
        requirements.append(NegotiationRequirement(BAD_MUTUAL_RELATIONSHIP, mutualRelationship * -2, s"Bad relationship between ${newLink.from.countryCode} and ${newLink.to.countryCode}"))
      }
    } else {
      requirements.append(NegotiationRequirement(UPDATE_LINK, UPDATE_BASE_REQUIREMENT * multiplier, s"Update Flights${negotiationSmallText}"))
    }

    if (newLink.from.countryCode == newLink.to.countryCode && CountryAirlineTitle.getTitle(newLink.to.countryCode, airline).title == APPROVED_AIRLINE) {
      requirements.append(NegotiationRequirement(LACK_COUNTRY_TITLE, 1.0, s"Extra regulatory scrutiny! (Does not apply if you have Established country title)"))
    }

    if (flightCategory == FlightCategory.INTERNATIONAL) {
      val airlineCounty = airline.getCountryCode().get
      if (airlineCounty != newLink.to.countryCode) {
        val toCountry = CountryCache.getCountry(newLink.to.countryCode).get
        val fromCountry = CountryCache.getCountry(newLink.from.countryCode).get
        var foreignAirlinePenalty = (12 - toCountry.openness) * 0.25 + (12 - fromCountry.openness) * 0.25
        if (existingLinkOption.isDefined) {
          foreignAirlinePenalty = foreignAirlinePenalty * 0.5
        }
        requirements.append(NegotiationRequirement(FOREIGN_AIRLINE, foreignAirlinePenalty, "Foreign Airline"))
      }
    }

    val toAirportStats = AirportStatisticsCache.getAirportStatistics(newLink.to.id).getOrElse(AirportStatistics(0, 0, 0, 0, 0, 0))
    val toCongestion = toAirportStats.congestion
    if (toCongestion < Airport.CONGESTION_MODERATE.toDouble / 100) {
      requirements.append(NegotiationRequirement(SLOT_CONTROLLED, 0, "Low congestion: Level 1 Coordination, no slot control."))
    } else if (toCongestion <= Airport.CONGESTION_HIGH.toDouble / 100) {
      requirements.append(NegotiationRequirement(SLOT_CONTROLLED, 0.5, "Moderate congestion: Level 2 Coordination, partial slot control."))
    } else {
      requirements.append(NegotiationRequirement(SLOT_CONTROLLED, 1.5, "High congestion: Level 3 Coordination, full slot control."))
    }

    val rep = airline.getReputation()
    val requirementTotal = requirements.foldLeft(0.0)((sum, requirement) => sum + requirement.value)

    computeStartupVigorAdjustment(rep, requirementTotal).foreach { adjustment =>
      val label =
        if (rep < STARTUP_MAX_REPUTATION)
          s"Startup Vigor – superior negotiation skills while under ${STARTUP_MAX_REPUTATION.toInt} rep"
        else
          s"Startup Vigor – fading enhanced negotiation skills while under ${(STARTUP_MAX_REPUTATION * 2).toInt} rep"
      requirements.append(NegotiationRequirement(STARTUP, adjustment, label))
    }

    existingLinkOption match {
      case Some(link) =>
        val loadFactor: Double = (link.getTotalCapacity - link.getTotalSoldSeats).toDouble / link.getTotalCapacity
        if (loadFactor < 0.75) {
          val cost = Math.ceil(1 + (0.75 - loadFactor * 4))
          requirements.append(NegotiationRequirement(LOW_LOAD_FACTOR, cost, s"Low load factor ${BigDecimal(loadFactor * 100).setScale(2)}%"))
        }
      case None =>
    }

    requirements.toList
  }

  def getNegotiationRequirements(newLink : Link, existingLinkOption : Option[Link], airline : Airline, airlineLinks : List[Link]) = {
    val fromAirportRequirements : List[NegotiationRequirement] = getFromAirportRequirements(airline, newLink, existingLinkOption, airlineLinks)
    val toAirportRequirements : List[NegotiationRequirement] = getToAirportRequirements(airline, newLink, existingLinkOption, airlineLinks)

    (fromAirportRequirements, toAirportRequirements)
  }

  def getAllNegotiationDiscounts(fromAirport : Airport, toAirport : Airport, airline : Airline, allianceMembers : List[AllianceMember], newLink: Link) : (List[NegotiationDiscount], List[NegotiationDiscount]) = {
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
    import NegotiationDiscountType._

    val relationship = AirlineCountryRelationship.getAirlineCountryRelationship(airport.countryCode, airline).relationship
    if (relationship >= 0) {
      var discount = relationship * 0.01
      discount = Math.min(discount, 0.4)
      discounts.append(SimpleNegotiationDiscount(COUNTRY_RELATIONSHIP, discount))
    } else if (relationship < 0) {
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
      base => if (base.scale >= 3) {
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
    val frequencyDelta = newFrequency - existingFrequency

    val airlineLinks = LinkSource.loadFlightLinksByAirlineId(airline.id)

    def actionPointRefund(difficulty: Double) : Int = {
      Math.floor(Math.abs(difficulty) / 0.5).toInt
    }

    val allianceMembers = airline.getAllianceId() match {
      case Some(allianceId) => AllianceCache.getAlliance(allianceId, fullLoad = true).get.members
      case None => List.empty
    }

    val (fromAirportDiscounts, toAirportDiscounts) = getAllNegotiationDiscounts(fromAirport, toAirport, airline, allianceMembers, newLink)
    val totalFromDiscount = Math.min(MAX_TOTAL_DISCOUNT, fromAirportDiscounts.map(_.value).sum)
    val totalToDiscount = Math.min(MAX_TOTAL_DISCOUNT, toAirportDiscounts.map(_.value).sum)

    val existingLinkCancellationValue = if (existingLinkOption.nonEmpty) {
      val emptyLink = newLink.copy(capacity = LinkClassValues(0,0,0), frequency = 0)
      val (fromAirportRequirements, toAirportRequirements) = getNegotiationRequirements(emptyLink, existingLinkOption, airline, airlineLinks)
      val refund = actionPointRefund(toAirportRequirements.map(_.value).sum * (1 - totalToDiscount) + fromAirportRequirements.map(_.value).sum * (1 - totalFromDiscount))
      Some(refund)
    } else {
      None
    }

    if (aircraftSizeFreqDelta == 0 && frequencyDelta == 0) {
      return NegotiationInfo(List(), List(), List(), List(), 0, 0, 0, Map(0 -> 1), existingLinkCancellationValue)
    }

    val (fromAirportRequirements, toAirportRequirements) = getNegotiationRequirements(newLink, existingLinkOption, airline, airlineLinks)

    val fromRequirementBase = fromAirportRequirements.map(_.value).sum
    val toRequirementBase = toAirportRequirements.map(_.value).sum
    val fromAirportRequirementValue = fromRequirementBase * (1 - totalFromDiscount)
    val toAirportRequirementValue = toRequirementBase * (1 - totalToDiscount)
    val finalRequirementValue = {
      val raw = fromAirportRequirementValue + toAirportRequirementValue
      if (existingLinkOption.isEmpty) Math.max(0.5, raw) else raw
    }
    val hasActionPointRefund = if (finalRequirementValue < 0.0) {
      Some(actionPointRefund(finalRequirementValue))
    } else {
      None
    }

    NegotiationInfo(
      fromAirportRequirements,
      toAirportRequirements,
      fromAirportDiscounts,
      toAirportDiscounts,
      totalFromDiscount,
      finalToDiscountValue = totalToDiscount,
      finalRequirementValue,
      computeOdds(finalRequirementValue, MAX_ASSIGNED_DELEGATE),
      existingLinkCancellationValue,
      hasActionPointRefund
    )
  }

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
              accumulativeOdds = Math.min(1, accumulativeOdds + oddsPerBaseDelegate * Math.pow(0.95, delegateCount - requiredDelegates.toInt))
            }
            accumulativeOdds
          }
        }
      (delegateCount, oddsForThisDelegateCount)
    }.filter {
      case (_, oddsForThisDelegateCount) =>
        if (foundMax) {
          false
        } else {
          foundMax = (oddsForThisDelegateCount == 1)
          true
        }
    }.toMap
  }

  // backward-compatible signature
  def getLinkBonus(link : Link, monetaryBaseValue : Long, delegates : List[Manager]) : NegotiationBonus = {
    getLinkBonus(link, monetaryBaseValue, delegates, odds = 0.5)
  }

  def getLinkBonus(link : Link, monetaryBaseValue : Long, delegates : List[Manager], odds: Double) : NegotiationBonus = {
    val useBigPool = Math.random() < bigSwingChance(odds)
    NegotiationBonus.drawBonus(monetaryBaseValue, delegates, link.to, useHighImpactPool = useBigPool)
  }

  def getLinkNegativeBonus(link: Link, monetaryBaseValue: Long, delegates: List[Manager], odds: Double): NegotiationBonus = {
    val useBigPool = Math.random() < bigSwingChance(odds)
    NegotiationBonus.drawNegativeBonus(monetaryBaseValue, delegates, link.to, useHighImpactPool = useBigPool)
  }

  val MAX_NEXT_NEGOTIATION_DISCOUNT = 0.25
  def getNextNegotiationDiscount(link : Link, negotiationResult: NegotiationResult) = {
    if (!negotiationResult.isSuccessful) {
      val ratio = negotiationResult.result / negotiationResult.threshold
      var discount = BigDecimal.valueOf(ratio * MAX_NEXT_NEGOTIATION_DISCOUNT).setScale(2, BigDecimal.RoundingMode.HALF_UP)
      if (discount == 0) {
        discount = BigDecimal(0.01)
      }
      Some(LinkNegotiationDiscount(link.airline, link.from, link.to, discount, CycleSource.loadCycle() + LinkNegotiationDiscount.DURATION))
    } else {
      None
    }
  }
}

case class NegotiationInfo(
                            fromAirportRequirements : List[NegotiationRequirement],
                            toAirportRequirements : List[NegotiationRequirement],
                            fromAirportDiscounts : List[NegotiationDiscount],
                            toAirportDiscounts : List[NegotiationDiscount],
                            finalFromDiscountValue : Double,
                            finalToDiscountValue : Double,
                            finalRequirementValue : Double,
                            odds : Map[Int, Double],
                            deleteLinkRefund : Option[Int] = None,
                            actionPointRefund : Option[Int] = None
                          )

object NegotiationRequirementType extends Enumeration {
  type NegotiationRequirementType = Value
  val NEW_LINK, UPDATE_LINK, INCREASE_CAPACITY, UPSIZE_GATE, INCREASE_FREQUENCY, LACK_COUNTRY_TITLE, EXCESSIVE_FREQUENCY, LOW_LOAD_FACTOR, FOREIGN_AIRLINE, GATEWAY, STAFF_CAP, BAD_MUTUAL_RELATIONSHIP, STARTUP, SLOT_CONTROLLED, OTHER = Value
}

object NegotiationDiscountType extends Enumeration {
  type NegotiationDiscountType = Value
  val COUNTRY_RELATIONSHIP, BELOW_CAPACITY, OVER_CAPACITY, LOYALTY, BASE, ALLIANCE_BASE, NEW_AIRLINE, PREVIOUS_NEGOTIATION, MAIDEN_INTERNATIONAL = Value
}

case class NegotiationRequirement(requirementType : NegotiationRequirementType.Value, value : Double, description : String)

abstract class NegotiationDiscount(val adjustmentType : NegotiationDiscountType.Value, val value : Double) {
  import controllers.NegotiationDiscountType._
  def description(airport : Airport) = adjustmentType match {
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

case class SimpleNegotiationDiscount(override val adjustmentType : NegotiationDiscountType.Value, override val value : Double)
  extends NegotiationDiscount(adjustmentType, value)

case class PreviousNegotiationDiscount(override val value : Double, duration: Int)
  extends NegotiationDiscount(NegotiationDiscountType.PREVIOUS_NEGOTIATION, value) {
  override def description(airport : Airport) : String = s"Previous negotiation progress (Expires in $duration week(s))"
}

case class NegotiationResult(threshold : Double, result : Double) {
  val isSuccessful = result >= threshold
  val isGreatSuccess = isSuccessful && result >= NegotiationUtil.GREAT_SUCCESS_THRESHOLD
  println(s"negotiation result: threshold $threshold vs result $result. Great success ? $isGreatSuccess")

  val SESSION_COUNT = 5
  def getNegotiationSessions() : NegotiationSession = {
    val passingScore = 75 + threshold * 25
    val score = 75 + result * 25
    val average = score / SESSION_COUNT

    val sessionScores = ListBuffer[Double]()
    for (_ <- 0 until SESSION_COUNT) {
      sessionScores.append(average)
    }

    for (_ <- 0 until 10) {
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
    NegotiationLoyaltyBonusTemplate(1), NegotiationLoyaltyBonusTemplate(2), NegotiationLoyaltyBonusTemplate(5),
    NegotiationActionPointBonusTemplate(1), NegotiationActionPointBonusTemplate(2), NegotiationActionPointBonusTemplate(3)
  )

  val highImpactPool = List(
    NegotiationCashBonusTemplate(10),
    NegotiationLoyaltyBonusTemplate(5),
    NegotiationActionPointBonusTemplate(3)
  )

  val negativePool = List(
    NegotiationNegativeLoyaltyBonusTemplate(1), NegotiationNegativeLoyaltyBonusTemplate(2), NegotiationNegativeLoyaltyBonusTemplate(4)
  )

  val highImpactNegativePool = List(
    NegotiationNegativeLoyaltyBonusTemplate(4)
  )

  val random = new Random()

  def drawBonus(monetaryBaseValue : Long, delegates: List[Manager], airport : Airport, useHighImpactPool: Boolean = false): NegotiationBonus = {
    val sourcePool = if (useHighImpactPool) highImpactPool else pool
    sourcePool(random.nextInt(sourcePool.size)).computeBonus(monetaryBaseValue, delegates, airport)
  }

  def drawNegativeBonus(monetaryBaseValue : Long, delegates: List[Manager], airport : Airport, useHighImpactPool: Boolean = false): NegotiationBonus = {
    val sourcePool = if (useHighImpactPool) highImpactNegativePool else negativePool
    sourcePool(random.nextInt(sourcePool.size)).computeBonus(monetaryBaseValue, delegates, airport)
  }
}

abstract class NegotiationBonus {
  def description : String
  def intensity : Int
  def apply(airline : Airline) : Unit
}

case class NegotiationCashBonus(cash : Long, description : String, val intensity: Int) extends NegotiationBonus {
  def apply(airline : Airline): Unit = {
    AirlineSource.saveLedgerEntry(AirlineLedgerEntry(airline.id, currentCycle, LedgerType.PRIZE, cash, Some(description)))
  }
}

case class NegotiationActionPointBonus(actionPoints: Int, description: String, val intensity: Int) extends NegotiationBonus {
  def apply(airline: Airline): Unit = {
    airline.setActionPoints(airline.getActionPoints() + actionPoints)
    AirlineSource.saveAirlineInfo(airline, updateBalance = false)
  }
}

case class NegotiationLoyaltyBonus(airport : Airport, loyaltyBonus: Double, duration : Int, description : String, val intensity: Int) extends NegotiationBonus {
  def apply(airline : Airline): Unit = {
    val cycle = CycleSource.loadCycle()
    AirportSource.saveAirlineAppealBonus(
      airport.id,
      airline.id,
      AirlineBonus(BonusType.NEGOTIATION_BONUS, AirlineAppeal(loyalty = loyaltyBonus), Some(cycle + duration))
    )
  }
}

abstract class NegotiationBonusTemplate {
  val INTENSITY_MAX = 5
  val intensity : Int = Math.min(INTENSITY_MAX, intensityCompute)
  val intensityCompute : Int
  def computeBonus(monetaryBaseValue : Long, delegates : List[Manager], airport : Airport) : NegotiationBonus
}

case class NegotiationCashBonusTemplate(factor : Int) extends NegotiationBonusTemplate {
  val intensityCompute = factor / 2 + 1
  val integerInstance = java.text.NumberFormat.getIntegerInstance

  override def computeBonus(monetaryBaseValue : Long, delegates : List[Manager], airport : Airport) : NegotiationBonus = {
    val cash = monetaryBaseValue * factor
    val description =
      if (factor <= 1) {
        s"A minor concession package was approved: $$${integerInstance.format(cash)} in support funds."
      } else if (factor <= 3) {
        s"Negotiators secured a meaningful financial package of $$${integerInstance.format(cash)}."
      } else if (factor <= 5) {
        s"A major subsidy was secured: $$${integerInstance.format(cash)}."
      } else {
        s"A blockbuster funding win! Airport authorities committed $$${integerInstance.format(cash)}."
      }
    NegotiationCashBonus(cash, description, intensity)
  }
}

case class NegotiationLoyaltyBonusTemplate(bonusFactor : Int) extends NegotiationBonusTemplate {
  val duration = Period.yearLength
  val intensityCompute = bonusFactor + 1

  override def computeBonus(monetaryBaseValue : Long, delegates : List[Manager], airport : Airport) : NegotiationBonus = {
    val denominator = airport.popMiddleIncome.toDouble * airport.income.toDouble
    val loyaltyBonus = BigDecimal(
      Math.max(0, Math.min(20, monetaryBaseValue.toDouble / denominator * 1000000))
    ).setScale(2, RoundingMode.HALF_UP)

    val description =
      if (loyaltyBonus <= 2) {
        s"A modest PR bump improves loyalty by $loyaltyBonus at ${airport.displayText} for $duration weeks."
      } else if (loyaltyBonus <= 5) {
        s"Strong local coverage boosts loyalty by $loyaltyBonus at ${airport.displayText} for $duration weeks."
      } else {
        s"A headline-grabbing campaign raises loyalty by $loyaltyBonus at ${airport.displayText} for $duration weeks."
      }

    NegotiationLoyaltyBonus(airport, loyaltyBonus.toDouble, duration, description, intensity)
  }
}

case class NegotiationActionPointBonusTemplate(bonusFactor: Int) extends NegotiationBonusTemplate {
  val intensityCompute = bonusFactor + 1

  override def computeBonus(monetaryBaseValue: Long, delegates: List[Manager], airport: Airport): NegotiationBonus = {
    val basePoints = Math.max(1, bonusFactor)
    val delegateSynergy = Math.min(2, delegates.size / 3)
    val points = basePoints + delegateSynergy

    val description =
      if (points <= 2) {
        s"Team momentum grants $points action point(s)."
      } else if (points <= 4) {
        s"Negotiation confidence surge! Gain $points action point(s)."
      } else {
        s"Full strategic breakthrough: gain $points action point(s)!"
      }

    NegotiationActionPointBonus(points, description, intensity)
  }
}

case class NegotiationNegativeLoyaltyBonusTemplate(penaltyFactor: Int) extends NegotiationBonusTemplate {
  val duration = 26
  val intensityCompute = penaltyFactor + 1

  override def computeBonus(monetaryBaseValue: Long, delegates: List[Manager], airport: Airport): NegotiationBonus = {
    val denominator = airport.popMiddleIncome.toDouble * airport.income.toDouble
    val basePenalty = if (denominator <= 0) 1.0 else monetaryBaseValue.toDouble / denominator * 1000000

    val loyaltyPenalty = BigDecimal(
      Math.max(0.5, Math.min(12, basePenalty * penaltyFactor))
    ).setScale(2, RoundingMode.HALF_UP)

    val description =
      if (loyaltyPenalty <= 2) {
        s"A minor diplomatic slip reduces loyalty by $loyaltyPenalty at ${airport.displayText} for $duration weeks."
      } else if (loyaltyPenalty <= 5) {
        s"Your team insulted the locals! Loyalty drops by $loyaltyPenalty at ${airport.displayText} for $duration weeks."
      } else {
        s"A major public-relations fiasco slashes loyalty by $loyaltyPenalty at ${airport.displayText} for $duration weeks."
      }

    NegotiationLoyaltyBonus(airport, -loyaltyPenalty.toDouble, duration, description, intensity)
  }
}