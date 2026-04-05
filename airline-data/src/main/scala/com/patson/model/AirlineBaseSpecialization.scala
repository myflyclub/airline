package com.patson.model

import FlightCategory._
import com.patson.data.AirportSource
import com.patson.model.airplane.Model
import com.patson.util.AirportCache
import com.patson.model.AirportBoostType

// Base sealed trait for all specializations
sealed abstract class AirlineBaseSpecialization {
  val id: String  // This replaces the enumeration ordinal for DB storage
  val getType: BaseSpecializationType.Value
  val scaleRequirement: Int = 2
  val label: String
  def descriptions(airport: Airport): List[String]
  val free: Boolean = false
  def apply(airline: Airline, airport: Airport): Unit = {}
  def unapply(airline: Airline, airport: Airport): Unit = {}
}

// Abstract base classes for different specialization types
sealed abstract class TransferSpecialization extends AirlineBaseSpecialization {
  override val getType = BaseSpecializationType.TRANSFER_DISCOUNT
  val paxType: List[PassengerType.Value]
  val transferCostDiscount = .3
  override def descriptions(airport: Airport) = paxType.map(paxTypeEach => {
    s"${PassengerType.label(paxTypeEach)} are ${(1 - transferCostDiscount) * 100}% more willing to transfer"
  })
}

/**
 * Trait for specializations that contribute to airport boost factors.
 * This allows for extensible airport boost contributions without manual coding in the cache loader.
 */
trait AirportBoostContributor {
  /**
   * Returns the airport boost contributions for this specialization.
   * @param airport The airport where this specialization is applied
   * @param airline The airline that owns this specialization
   * @return Map of boost type to (description, boost value) pairs
   */
  def getAirportBoostContributions(airport: Airport, airline: Airline): Map[AirportBoostType.Value, (String, Double)]
}

sealed abstract class FlightTypeSpecialization extends AirlineBaseSpecialization {
  override val getType = BaseSpecializationType.FLIGHT_TYPE
  val staffModifier: (FlightCategory.Value, Model.Type.Value, Int) => Double
}

sealed trait BrandSpecialization extends AirlineBaseSpecialization {
  override val getType = BaseSpecializationType.BRANDING
  val deltaByLinkClassAndPassengerType: Map[(LinkClass, PassengerType.Value), Double]
}

sealed abstract class PassengerTypeBrandSpecialization extends AirlineBaseSpecialization with BrandSpecialization {
  protected val passengerTypeDeltas: Map[PassengerType.Value, Double]

  override lazy val deltaByLinkClassAndPassengerType: Map[(LinkClass, PassengerType.Value), Double] = {
    val allLinkClasses = List(ECONOMY, BUSINESS, FIRST, DISCOUNT_ECONOMY)
    val allPassengerTypes = PassengerType.values.toList

    (for {
      lc <- allLinkClasses
      pt <- allPassengerTypes
    } yield {
      (lc, pt) -> passengerTypeDeltas.getOrElse(pt, 0.0)
    }).toMap
  }
}

sealed abstract class LinkClassBrandSpecialization extends AirlineBaseSpecialization with BrandSpecialization {
  protected val linkClassDeltas: Map[LinkClass, Double]

  override lazy val deltaByLinkClassAndPassengerType: Map[(LinkClass, PassengerType.Value), Double] = {
    val allLinkClasses = List(ECONOMY, BUSINESS, FIRST, DISCOUNT_ECONOMY)
    val allPassengerTypes = PassengerType.values.toList

    (for {
      lc <- allLinkClasses
      pt <- allPassengerTypes
    } yield {
      (lc, pt) -> linkClassDeltas.getOrElse(lc, 0.0)
    }).toMap
  }
}

sealed abstract class HangarSpecialization extends AirlineBaseSpecialization {
  val HANGAR_CANCELATION_REDUCTION: Double = 0.04
  override val getType = BaseSpecializationType.HANGAR
  def descriptions(airport: Airport) = List(s"${HANGAR_CANCELATION_REDUCTION * 100}% reduction in delays / cancellations.")
}

sealed abstract class NegotiationSpecialization extends AirlineBaseSpecialization {
  override val getType = BaseSpecializationType.NEGOTIATION
}

case object BusinessTransferSpecialization extends TransferSpecialization {
  override val id = "BUSINESS_TRANSFER"
  override val paxType = List(PassengerType.BUSINESS)
  override val scaleRequirement: Int = 8
  override val label = "Airport Train"
}

case object TouristTransferSpecialization extends TransferSpecialization {
  override val id = "TOURIST_TRANSFER"
  override val paxType = List(PassengerType.TOURIST)
  override val scaleRequirement: Int = 8
  override val label = "Airport Movie Theater"
}

case object EliteTransferSpecialization extends TransferSpecialization {
  override val id = "ELITE_TRANSFER"
  override val paxType = List(PassengerType.ELITE)
  override val scaleRequirement: Int = 8
  override val label = "VIP Terminal"
}

case object TravelerTransferSpecialization extends TransferSpecialization {
  override val id = "TRAVELER_TRANSFER"
  override val paxType = List(PassengerType.TRAVELER, PassengerType.TRAVELER_SMALL_TOWN, PassengerType.OLYMPICS)
  override val scaleRequirement: Int = 8
  override val label = "Architect Designed Terminal"
}

// Airport Power Specializations
case object PowerhouseSpecialization extends AirlineBaseSpecialization with AirportBoostContributor {
  override val id = "POWERHOUSE"
  override val getType = BaseSpecializationType.AIRPORT_POWER
  override val label = "Powerhouse"
  override val scaleRequirement: Int = 9
  private val maxPopBoost = 100000
  private val floorPopBoost = 2000
  private val floorIncomeBoost = 3500
  private val percentageBoost = 8

  def incomeBoost(airport: Airport) = {
    val incomeIncrement = airport.baseIncome * percentageBoost.toDouble / 100
    incomeIncrement.toInt + floorIncomeBoost
  }

  def populationBoost(airport: Airport) = {
    val popIncrement = airport.basePopulation * percentageBoost.toDouble / 100
    Math.min(popIncrement, maxPopBoost).toInt + floorPopBoost
  }
  
  override def descriptions(airport: Airport) = {
    List(s"Increase population by ${populationBoost(airport)}", s"Increase income by ${incomeBoost(airport)}")
  }
  
  override def apply(airline: Airline, airport: Airport) = {
    AirportCache.refreshAirport(airport.id)
  }
  
  override def unapply(airline: Airline, airport: Airport) = {
    AirportCache.refreshAirport(airport.id)
  }

  override def getAirportBoostContributions(airport: Airport, airline: Airline): Map[AirportBoostType.Value, (String, Double)] = {
    Map(
      AirportBoostType.INCOME -> (s"${airline.name} Powerhouse", incomeBoost(airport).toDouble),
      AirportBoostType.POPULATION -> (s"${airline.name} Powerhouse", populationBoost(airport).toDouble)
    )
  }
}

case object DormitoriesSpecialization extends AirlineBaseSpecialization with AirportBoostContributor {
  override val id = "DORMITORIES"
  override val getType = BaseSpecializationType.AIRPORT_POWER
  override val label = "Staff Dormitory"
  override val scaleRequirement: Int = 2
  val pop = 1400

  override def descriptions(airport: Airport) = List(s"Increase population by $pop")
  
  override def apply(airline: Airline, airport: Airport) = {
    AirportCache.refreshAirport(airport.id)
  }
  
  override def unapply(airline: Airline, airport: Airport) = {
    AirportCache.refreshAirport(airport.id)
  }

  override def getAirportBoostContributions(airport: Airport, airline: Airline): Map[AirportBoostType.Value, (String, Double)] = {
    Map(
      AirportBoostType.POPULATION -> (s"${airline.name} Dormitory", pop.toDouble),
    )
  }
}

case object TaxHavenSpecialization extends AirlineBaseSpecialization with AirportBoostContributor {
  override val id = "TAX_HAVEN"
  override val getType = BaseSpecializationType.AIRPORT_POWER
  override val label = "In-Terminal Tax Haven"
  override val scaleRequirement: Int = 8
  val eliteBoost = 1200

  override def descriptions(airport: Airport) = List(s"Increase elite population by $eliteBoost")

  override def apply(airline: Airline, airport: Airport) = {
    AirportCache.refreshAirport(airport.id)
  }
  
  override def unapply(airline: Airline, airport: Airport) = {
    AirportCache.refreshAirport(airport.id)
  }

  override def getAirportBoostContributions(airport: Airport, airline: Airline): Map[AirportBoostType.Value, (String, Double)] = {
    Map(
      AirportBoostType.ELITE -> (s"${airline.name}'s Terminal Tax Haven", eliteBoost.toDouble),
    )
  }
}

// Loyalty Specializations
case object LoyaltySpecialization1 extends AirlineBaseSpecialization {
  override val id = "LOYALTY_1"
  override val getType = BaseSpecializationType.LOYALTY
  override val label = "Local Sports Sponsorship"
  val loyaltyBoost = 10
  override val scaleRequirement: Int = 5
  override def descriptions(airport: Airport) = List(s"Boost loyalty of this airport by $loyaltyBoost")

  override def apply(airline: Airline, airport: Airport) = {
    unapply(airline, airport) //unapply first to avoid duplicates
    AirportSource.saveAirlineAppealBonus(airport.id, airline.id, AirlineBonus(BonusType.BASE_SPECIALIZATION_BONUS, AirlineAppeal(loyalty = loyaltyBoost), None))
  }

  override def unapply(airline: Airline, airport: Airport) = {
    AirportSource.loadAirlineAppealBonusByAirportAndAirline(airport.id, airline.id).find(_.bonusType == BonusType.BASE_SPECIALIZATION_BONUS).foreach { existingBonus =>
      AirportSource.deleteAirlineAppealBonus(airport.id, airline.id, BonusType.BASE_SPECIALIZATION_BONUS)
    }
  }
}

case object LoyaltySpecialization2 extends AirlineBaseSpecialization {
  override val id = "LOYALTY_2"
  override val getType = BaseSpecializationType.LOYALTY
  override val label = "Stadium Naming Rights"
  val loyaltyBoost = 12
  override val scaleRequirement: Int = 10
  override def descriptions(airport: Airport) = List(s"Boost loyalty of this airport by $loyaltyBoost")

  override def apply(airline: Airline, airport: Airport) = {
    unapply(airline, airport) //unapply first to avoid duplicates
    AirportSource.saveAirlineAppealBonus(airport.id, airline.id, AirlineBonus(BonusType.BASE_SPECIALIZATION_BONUS, AirlineAppeal(loyalty = loyaltyBoost), None))
  }

  override def unapply(airline: Airline, airport: Airport) = {
    AirportSource.loadAirlineAppealBonusByAirportAndAirline(airport.id, airline.id).find(_.bonusType == BonusType.BASE_SPECIALIZATION_BONUS).foreach { existingBonus =>
      AirportSource.deleteAirlineAppealBonus(airport.id, airline.id, BonusType.BASE_SPECIALIZATION_BONUS)
    }
  }
}

// Flight Type Specializations
case object InternationalSpecialization extends FlightTypeSpecialization {
  override val id = "INTERNATIONAL_HUB"
  override val scaleRequirement: Int = 5
  override val staffModifier: ((FlightCategory.Value, Model.Type.Value, Int) => Double) = {
    case (INTERNATIONAL, _, _) => 0.88
    case _ => 1
  }
  override val label = "International Hub"
  override def descriptions(airport: Airport) = List("Reduce staff for international flights by 12%")
}

case object GateSmallSpecialization extends FlightTypeSpecialization {
  override val id = "GATE_SMALL"
  override val staffModifier: ((FlightCategory.Value, Model.Type.Value, Int) => Double) = {
    case (_, Model.Type.SMALL, _) => 0.85
    case (_, Model.Type.PROPELLER_SMALL, _) => 0.85
    case _ => 1
  }
  override val label = "Apron Boarding Area"
  override def descriptions(airport: Airport) = List("Reduce staff for small aircraft flights by 15%")
}

case object GateRegionalSpecialization extends FlightTypeSpecialization {
  override val id = "GATE_REGIONAL"
  override val scaleRequirement: Int = 3
  override val staffModifier: ((FlightCategory.Value, Model.Type.Value, Int) => Double) = {
    case (_, Model.Type.REGIONAL, _) => 0.9
    case (_, Model.Type.REGIONAL_XL, _) => 0.9
    case (_, Model.Type.PROPELLER_MEDIUM, _) => 0.9
    case _ => 1
  }
  override val label = "Commuter Gate Area"
  override def descriptions(airport: Airport) = List("Reduce staff for regional aircraft flights by 10%")
}

case object GateLargeSpecialization extends FlightTypeSpecialization {
  override val id = "GATE_WIDE"
  override val scaleRequirement: Int = 5
  override val staffModifier: ((FlightCategory.Value, Model.Type.Value, Int) => Double) = {
    case (_, Model.Type.LARGE, _) => 0.9
    case (_, Model.Type.EXTRA_LARGE, _) => 0.9
    case (_, Model.Type.JUMBO, _) => 0.9
    case (_, Model.Type.JUMBO_XL, _) => 0.9
    case _ => 1.02
  }
  override val label = "Extra Large Gates"
  override def descriptions(airport: Airport) = List("Reduce staff for flights with widebody & jumbo aircraft by 10%", "Increase others by 2%")
}

case object GateMediumSpecialization extends FlightTypeSpecialization {
  override val id = "GATE_MEDIUM"
  override val scaleRequirement: Int = 5
  override val staffModifier: ((FlightCategory.Value, Model.Type.Value, Int) => Double) = {
    case (_, Model.Type.MEDIUM, _) => 0.9
    case (_, Model.Type.MEDIUM_XL, _) => 0.9
    case _ => 1
  }
  override val label = "Uniform Ground Handling"
  override def descriptions(airport: Airport) = List("Reduce staff for flights with narrowbody aircraft by 10%")
}

case object GateSSTSpecialization extends FlightTypeSpecialization {
  override val id = "GATE_SST"
  override val scaleRequirement: Int = 6
  override val staffModifier: ((FlightCategory.Value, Model.Type.Value, Int) => Double) = {
    case (_, Model.Type.SUPERSONIC, _) => 0.88
    case _ => 1
  }
  override val label = "SST Gate Area"
  override def descriptions(airport: Airport) = List("Reduce staff for flights with SSTs by 12%")
}

case object ServiceSpecialization1 extends FlightTypeSpecialization {
  override val id = "SERVICE_1"
  override val scaleRequirement: Int = 6
  override val staffModifier: ((FlightCategory.Value, Model.Type.Value, Int) => Double) = {
    case (_, _, 20) => 0.88
    case (_, _, 40) => 0.94
    case _ => 1
  }
  override val label = "Fee Processing Center"
  override def descriptions(airport: Airport) = List("12% less staff on flights with 1 service star.", "6% less staff on flights with 2 service stars.")
}

case object ServiceSpecialization2 extends FlightTypeSpecialization {
  override val id = "SERVICE_2"
  override val scaleRequirement: Int = 6
  override val staffModifier: ((FlightCategory.Value, Model.Type.Value, Int) => Double) = {
    case (_, _, 20) => 0.94
    case (_, _, 40) => 0.94
    case (_, _, 60) => 0.94
    case _ => 1
  }
  override val label = "Automated Baggage Handling"
  override def descriptions(airport: Airport) = List("6% less staff on flights with 1, 2, or 3 service stars.")
}

case object ServiceSpecialization3 extends FlightTypeSpecialization {
  override val id = "SERVICE_3"
  override val scaleRequirement: Int = 4
  override val staffModifier: ((FlightCategory.Value, Model.Type.Value, Int) => Double) = {
    case (_, _, 60) => 0.94
    case (_, _, 80) => 0.94
    case _ => 1
  }
  override val label = "Bulk Catering Facility"
  override def descriptions(airport: Airport) = List("6% less staff on flights with 3 or 4 service stars.")
}

case object ServiceSpecialization4 extends FlightTypeSpecialization {
  override val id = "SERVICE_4"
  override val scaleRequirement: Int = 6
  override val staffModifier: ((FlightCategory.Value, Model.Type.Value, Int) => Double) = {
    case (_, _, 60) => 0.96
    case (_, _, 80) => 0.92
    case (_, _, 100) => 0.96
    case _ => 1
  }
  override val label = "Premium Catering Facility"
  override def descriptions(airport: Airport) = List("8% less staff on flights with 4 service stars.", "5% less staff on flights with 3 or 5 service stars.")
}

case object ServiceSpecialization5 extends FlightTypeSpecialization {
  override val id = "SERVICE_5"
  override val scaleRequirement: Int = 4
  override val staffModifier: ((FlightCategory.Value, Model.Type.Value, Int) => Double) = {
    case (_, _, 100) => 0.88
    case _ => 1
  }
  override val label = "Luxury Outfitter"
  override def descriptions(airport: Airport) = List("10% less staff on flights with 5 service star.")
}

// Brand Specializations
case object WifiSpecialization extends PassengerTypeBrandSpecialization {
  override val id = "BRANDING_WIFI"
  override val scaleRequirement: Int = 3
  override val label = "WiFi & Outlets"
  override def descriptions(airport: Airport) = List("Adds a 3% preference for business pax", "Adds a 2% preference for travelers")
  override protected val passengerTypeDeltas = Map(
    PassengerType.BUSINESS -> -0.03,
    PassengerType.TRAVELER -> -0.02,
    PassengerType.TRAVELER_SMALL_TOWN -> -0.01
  )
}

case object BudgetAirlineSpecialization extends LinkClassBrandSpecialization {
  override val id = "BRANDING_BUDGET"
  override val scaleRequirement: Int = 4
  override val label = "Cheap Fast Food Restaurants"
  override def descriptions(airport: Airport) = List("Adds an 5% preference for discount economy pax", "Adds a 2% preference for economy pax")
  override protected val linkClassDeltas = Map(
    DISCOUNT_ECONOMY -> -0.05,
    ECONOMY -> -0.02
  )
}

case object PremiumAirlineSpecialization extends LinkClassBrandSpecialization {
  override val id = "BRANDING_PREMIUM"
  override val scaleRequirement: Int = 6
  override val label = "Luxury Shops & Dining"
  override def descriptions(airport: Airport) = List("Adds an 4% preference for first class pax", "Adds a 2% preference for business class pax")
  override protected val linkClassDeltas = Map(
    BUSINESS -> -0.02,
    FIRST -> -0.04
  )
}

case object HelpSpecialization extends PassengerTypeBrandSpecialization {
  override val id = "BRANDING_HELP"
  override val scaleRequirement: Int = 3
  override val label = "Help Desks"
  override def descriptions(airport: Airport) = List("Adds a 4% preference for tourists", "Adds a 2% preference for travelers")
  override protected val passengerTypeDeltas = Map(
    PassengerType.TOURIST -> -0.04,
    PassengerType.TRAVELER -> -0.02,
    PassengerType.TRAVELER_SMALL_TOWN -> -0.02,
  )
}

case object VIPSpecialization1 extends PassengerTypeBrandSpecialization {
  override val id = "BRANDING_VIP_1"
  override val scaleRequirement: Int = 3
  override val label = "VIP Chauffeurs"
  override def descriptions(airport: Airport) = List("Adds a 5% preference for elites")
  override protected val passengerTypeDeltas = Map(
    PassengerType.ELITE -> -0.05,
  )
}

case object SecuritySpecialization extends PassengerTypeBrandSpecialization {
  override val id = "BRANDING_SECURITY"
  override val scaleRequirement: Int = 3
  override val label = "Fast Track Security"
  override def descriptions(airport: Airport) = List("Adds a 4% preference for business pax")
  override protected val passengerTypeDeltas = Map(
    PassengerType.BUSINESS -> -0.04,
  )
}

case object PrioritySpecialization extends LinkClassBrandSpecialization {
  override val id = "BRANDING_PRIORITY"
  override val scaleRequirement: Int = 4
  override val label = "Priority Check-In"
  override def descriptions(airport: Airport) = List("Adds a 4% preference for first & business class")
  override protected val linkClassDeltas = Map(
    BUSINESS -> -0.04,
    FIRST -> -0.04
  )
}

case object KidsSpecialization extends PassengerTypeBrandSpecialization {
  override val id = "BRANDING_KIDS"
  override val scaleRequirement: Int = 4
  override val label = "Kids' Playzones"
  override def descriptions(airport: Airport) = List("Adds a 5% preference for tourists", "Adds a 2% preference for travelers")
  override protected val passengerTypeDeltas = Map(
    PassengerType.TOURIST -> -0.05,
    PassengerType.TRAVELER -> -0.02,
    PassengerType.TRAVELER_SMALL_TOWN -> -0.02,
  )
}

case object RatsSpecialization extends LinkClassBrandSpecialization {
  override val id = "BRANDING_RATS"
  override val scaleRequirement: Int = 5
  override val label = "Public Rat Cuddling Lounge"
  override def descriptions(airport: Airport) = List("Adds a 2% preference for all but first class")
  override protected val linkClassDeltas = Map(
    DISCOUNT_ECONOMY -> -0.03,
    ECONOMY -> -0.02,
    BUSINESS -> -0.02,
  )
}

case object VIPSpecialization2 extends PassengerTypeBrandSpecialization {
  override val id = "BRANDING_VIP_2"
  override val scaleRequirement: Int = 4
  override val label = "VIP Suites"
  override def descriptions(airport: Airport) = List("Adds a 4% preference for elites")
  override protected val passengerTypeDeltas = Map(
    PassengerType.ELITE -> -0.04
  )
}

case object OlympicSpecialization extends PassengerTypeBrandSpecialization {
  override val id = "BRANDING_OLYMPIC"
  override val scaleRequirement: Int = 5
  override val label = "Airport Olympic Expo"
  override def descriptions(airport: Airport) = List("Adds a 15% preference for olympic passengers")
  override protected val passengerTypeDeltas = Map(
    PassengerType.OLYMPICS -> -0.15
  )
}

// Hangar Specializations
case object HangarSpecialization0 extends HangarSpecialization {
  override val id = "HANGAR_0"
  override val label = "Maintenance Hangar"
  override val scaleRequirement: Int = 3
}

case object HangarSpecialization1 extends HangarSpecialization {
  override val id = "HANGAR_1"
  override val label = "Maintenance Hangar II"
  override val scaleRequirement: Int = 6
}

case object HangarSpecialization2 extends HangarSpecialization {
  override val id = "HANGAR_2"
  override val label = "Maintenance Hangar III"
  override val scaleRequirement: Int = 8
}

// Hub Feature Boost Specializations
sealed abstract class HubFeatureSpecialization extends AirlineBaseSpecialization with AirportBoostContributor {
  val boostType: AirportBoostType.Value
  val boost: Int = 10
  override val getType = BaseSpecializationType.AIRPORT_POWER
  override val scaleRequirement: Int = 16

  override def getAirportBoostContributions(airport: Airport, airline: Airline): Map[AirportBoostType.Value, (String, Double)] =
    Map(boostType -> (s"${airline.name} ${label}", boost.toDouble))

  override def apply(airline: Airline, airport: Airport): Unit = AirportCache.refreshAirport(airport.id)
  override def unapply(airline: Airline, airport: Airport): Unit = AirportCache.refreshAirport(airport.id)
}

case object InternationalHubBoostSpecialization extends HubFeatureSpecialization {
  override val id = "HUB_INTERNATIONAL"
  override val label = "Global Hub Initiative"
  override val boostType = AirportBoostType.INTERNATIONAL_HUB
  override def descriptions(airport: Airport) = List(s"Adds +$boost to International Hub strength")
}

case object VacationHubBoostSpecialization extends HubFeatureSpecialization {
  override val id = "HUB_VACATION"
  override val label = "Tourism Promotion Bureau"
  override val boostType = AirportBoostType.VACATION_HUB
  override def descriptions(airport: Airport) = List(s"Adds +$boost to Vacation Hub strength")
}

case object FinancialHubBoostSpecialization extends HubFeatureSpecialization {
  override val id = "HUB_FINANCIAL"
  override val label = "Finance District Liaison"
  override val boostType = AirportBoostType.FINANCIAL_HUB
  override def descriptions(airport: Airport) = List(s"Adds +$boost to Financial Hub strength")
}

case object EliteCharmBoostSpecialization extends HubFeatureSpecialization {
  override val id = "HUB_ELITE"
  override val label = "Elite Concierge Program"
  override val boostType = AirportBoostType.ELITE_CHARM
  override def descriptions(airport: Airport) = List(s"Adds +$boost to Elite Charm strength")
}

// Negotiation Specializations
case object NegoHopper extends NegotiationSpecialization {
  override val id = "NEGOTIATION_HOPPER"
  override val label = "Hopper Negotiator"
  override val scaleRequirement: Int = 2
  val maxDistance: Int = 800
  val distanceIncrement: Int = 80
  override def descriptions(airport: Airport) = List(s"Assists creating short-distance, high-frequency links. For each ${distanceIncrement}km under ${maxDistance}km, the base's frequency threshold is increased by 1.")
}

case object NegoSmall extends NegotiationSpecialization {
  override val id = "NEGOTIATION_SMALL"
  override val label = "Small Airport Negotiator"
  override val scaleRequirement: Int = 2
  val maxAirportSize = 4
  override def descriptions(airport: Airport) = List(s"Assists creating links to small airports, reducing difficulty multiplier by 0.5 in $maxAirportSize size airports and smaller.")
}

case object NegoLong extends NegotiationSpecialization {
  override val id = "NEGOTIATION_LONG"
  override val label = "Foreign Mission Negotiator"
  override val scaleRequirement: Int = 2
  val minDistance: Int = 4000
  override def descriptions(airport: Airport) = List(s"Assists creating new foreign routes. Lowers 'New Flight Negotiation' difficulty on international flights longer than $minDistance.")
}

case object NegoSlots extends NegotiationSpecialization {
  override val id = "NEGOTIATION_SLOTS"
  override val label = "Slot Coordinator Negotiator"
  override val scaleRequirement: Int = 2
  override def descriptions(airport: Airport) = List(s"If slot coordination is required (i.e there's airport congestion), reduces slot difficulty by 50%.")
}

// Companion object for registry and utilities
object AirlineBaseSpecialization {
  
  // Registry of all specializations - maintains the ALL_CAPS constants for compatibility
  val INTERNATIONAL_HUB = InternationalSpecialization
  val GATE_SMALL = GateSmallSpecialization
  val GATE_REGIONAL = GateRegionalSpecialization
  val GATE_SST = GateSSTSpecialization
  val GATE_WIDE = GateLargeSpecialization
  val GATE_MEDIUM = GateMediumSpecialization
  val SERVICE_1 = ServiceSpecialization1
  val SERVICE_2 = ServiceSpecialization2
  val SERVICE_3 = ServiceSpecialization3
  val SERVICE_4 = ServiceSpecialization4
  val SERVICE_5 = ServiceSpecialization5
  val SPORTS_SPONSORSHIP_1 = LoyaltySpecialization1
  val SPORTS_SPONSORSHIP_2 = LoyaltySpecialization2
  val BRANDING_BUDGET = BudgetAirlineSpecialization
  val BRANDING_PREMIUM = PremiumAirlineSpecialization
  val BRANDING_WIFI = WifiSpecialization
  val BRANDING_HELP = HelpSpecialization
  val BRANDING_SECURITY = SecuritySpecialization
  val BRANDING_PRIORITY = PrioritySpecialization
  val BRANDING_KIDS = KidsSpecialization
  val BRANDING_RATS = RatsSpecialization
  val BRANDING_VIP_1 = VIPSpecialization1
  val BRANDING_VIP_2 = VIPSpecialization2
  val BRANDING_OLYMPIC = OlympicSpecialization
  val POWERHOUSE = PowerhouseSpecialization
  val DORMITORIES = DormitoriesSpecialization
  val TAX_HAVEN = TaxHavenSpecialization
  val HANGAR_0 = HangarSpecialization0
  val HANGAR_1 = HangarSpecialization1
  val HANGAR_2 = HangarSpecialization2
  val NEGOTIATION_HOPPER = NegoHopper
  val NEGOTIATION_SMALL = NegoSmall
  val NEGOTIATION_LONG = NegoLong
  val NEGOTIATION_SLOTS = NegoSlots
  val TRANSFER_ELITE = EliteTransferSpecialization
  val TRANSFER_BUSINESS = BusinessTransferSpecialization
  val TRANSFER_TOURIST = TouristTransferSpecialization
  val TRANSFER_TRAVELER = TravelerTransferSpecialization
  val HUB_INTERNATIONAL = InternationalHubBoostSpecialization
  val HUB_VACATION = VacationHubBoostSpecialization
  val HUB_FINANCIAL = FinancialHubBoostSpecialization
  val HUB_ELITE = EliteCharmBoostSpecialization

  // All specializations registry for iteration, lookup, etc.
  val allSpecializations: List[AirlineBaseSpecialization] = List(
    INTERNATIONAL_HUB, GATE_SMALL, GATE_REGIONAL, GATE_SST, GATE_WIDE, GATE_MEDIUM,
    SERVICE_1, SERVICE_2, SERVICE_3, SERVICE_4, SERVICE_5,
    SPORTS_SPONSORSHIP_1, SPORTS_SPONSORSHIP_2,
    BRANDING_BUDGET, BRANDING_PREMIUM, BRANDING_WIFI, BRANDING_HELP, BRANDING_SECURITY,
    BRANDING_PRIORITY, BRANDING_KIDS, BRANDING_RATS, BRANDING_VIP_1, BRANDING_VIP_2, BRANDING_OLYMPIC,
    POWERHOUSE, DORMITORIES, TAX_HAVEN,
    HANGAR_0, HANGAR_1, HANGAR_2,
    NEGOTIATION_HOPPER, NEGOTIATION_SMALL, NEGOTIATION_LONG, NEGOTIATION_SLOTS,
    TRANSFER_ELITE, TRANSFER_TOURIST, TRANSFER_TRAVELER, TRANSFER_BUSINESS,
    HUB_INTERNATIONAL, HUB_VACATION, HUB_FINANCIAL, HUB_ELITE
  )

  // Lookup by ID (for database serialization)
  private val byId: Map[String, AirlineBaseSpecialization] = allSpecializations.map(s => s.id -> s).toMap
  
  def fromId(id: String): Option[AirlineBaseSpecialization] = byId.get(id)
  
  // Lookup by type
  def byType(specializationType: BaseSpecializationType.Value): List[AirlineBaseSpecialization] = 
    allSpecializations.filter(_.getType == specializationType)
    
  // For compatibility with existing enumeration-style usage
  def values: List[AirlineBaseSpecialization] = allSpecializations
}

object BaseSpecializationType extends Enumeration {
  type SpecializationType = Value
  val FLIGHT_TYPE, MANAGER, HANGAR, BRANDING, LOYALTY, AIRPORT_POWER, NEGOTIATION, TRANSFER_DISCOUNT = Value
  val COOLDOWN = 12 //change every 100 cycles
}