package com.patson.model

import com.patson.Util
import java.util.concurrent.ThreadLocalRandom

/**
 * Flight preference has a computeCost method that convert the existing price of a link to a "perceived price". The "perceived price" will be refer to "cost" here
 * 
 * When a link contains certain properties that the "Flight preference" likes/hates, it might reduce (if like) or increase (if hate) the "perceived price"  
 */
abstract class FlightPreference(homeAirport : Airport) {
  val COST_BASIS = 0.85
  def computeCost(baseCost : Double, link : Transport, linkClass : LinkClass) : Double
  def preferredLinkClass : LinkClass
  def getPreferenceType : FlightPreferenceType.Value


  def computeCost(link : Transport, linkClass : LinkClass, paxType: PassengerType.Value, externalCostModifier : Double = 1.0) : Double = {
    val standardPrice = link.standardPrice(preferredLinkClass, paxType)
    var cost = standardPrice * priceAdjustRatio(link, linkClass, paxType)
    cost = (cost * qualityAdjustRatio(homeAirport, link, preferredLinkClass, paxType)).toInt

    cost = (cost * frequencyAdjustRatio(link, linkClass, paxType)).toInt

    cost = (cost * tripDurationAdjustRatio(link, linkClass, paxType)).toInt

    if (loyaltySensitivity > 0) {
      cost = (cost * loyaltyAdjustRatio(link)).toInt
    }

    cost = cost * loungeAdjustRatio(link, loungeLevelRequired, preferredLinkClass)

    cost *= externalCostModifier

    cost *= COST_BASIS

    computeCost(cost, link, linkClass)
  }

  /**
    * For testing and debug purpose only
    * @param link
    * @param linkClass
    * @return
    */
  def computeCostBreakdown(link : Transport, linkClass : LinkClass, paxType: PassengerType.Value) : CostBreakdown = {
    val standardPrice = link.standardPrice(preferredLinkClass, paxType)
    val priceAdjust = priceAdjustRatio(link, linkClass, paxType)
    var cost = standardPrice * priceAdjust

    val qualityAdjust = qualityAdjustRatio(homeAirport, link, preferredLinkClass, paxType)
    cost = (cost * qualityAdjust).toInt

    val tripDurationAdjust = tripDurationAdjustRatio(link, preferredLinkClass, paxType)
    cost = (cost * tripDurationAdjust).toInt

    var loyaltyAdjust = 1.0
    if (loyaltySensitivity > 0) {
      loyaltyAdjust = loyaltyAdjustRatio(link)
      cost = (cost * loyaltyAdjust).toInt
    }

    val loungeAdjust = loungeAdjustRatio(link, loungeLevelRequired, linkClass)
    cost = cost * loungeAdjust

    CostBreakdown(computeCost(cost, link, linkClass), priceAdjust, qualityAdjust, tripDurationAdjust, loyaltyAdjust, loungeAdjust)
  }

  /**
    * For debug and testing propose only
    * @param cost
    * @param priceAdjust
    * @param qualityAdjust
    * @param tripDurationAdjust
    * @param loyaltyAdjust
    * @param loungeAdjust
    */
  case class CostBreakdown(cost : Double, priceAdjust : Double, qualityAdjust : Double, tripDurationAdjust : Double, loyaltyAdjust : Double, loungeAdjust : Double)

  val priceSensitivity : Double
  val priceModifier : Double = 1.0
  val qualitySensitivity : Double = 1.0
  val loyaltySensitivity : Double = 0
  val frequencyThreshold : Int = 7
  val flightDurationSensitivity : Double = 0.5
  val loungeLevelRequired : Int = 0

  lazy val appealList : Map[Int, AirlineAppeal] = homeAirport.getAirlineAdjustedAppeals
  val maxLoyalty = AirlineAppeal.MAX_LOYALTY

  /**
   * flattop bell random centered at 0
   */
  def getFlatTopBellRandom(topWidth: Double, bellExtension: Double) = {
    topWidth / 2 - ThreadLocalRandom.current().nextDouble() * topWidth + Util.getBellRandom(0) * bellExtension
  }
  /**
   * priceSensitivity : how sensitive to the price, base value is 1 (100%)
   * 
   * 1 : cost is the same as price no adjustment
   * > 1 : more sensitive to price, a price that is deviated from "standard price" will have its effect amplified, for example a 2 (200%) would mean a $$150 ticket with suggested price of $$100, will be perceived as $$200                      
   * < 1 : less sensitive to price, a price that is deviated from "standard price" will have its effect weakened, for example a 0.5 (50%) would mean a $$150 ticket with suggested price of $$100, will be perceived as $$125
   * 
   * Take note, 0 = a preference that totally ignores price difference (could be dangerous as very expensive ticket will get through)
   */
  def priceAdjustRatio(link: Transport, linkClass: LinkClass, paxType: PassengerType.Value) = {
    val standardPrice = link.standardPrice(preferredLinkClass, paxType)
    val classAdjustedPrice = priceAdjustedByLinkClassDiff(link, linkClass, paxType)
    val deltaFromStandardPrice = classAdjustedPrice - standardPrice
    val priceSensitivityModifier = if (deltaFromStandardPrice < 0 && (getPreferenceType == FlightPreferenceType.FREQUENT || getPreferenceType == FlightPreferenceType.BRAND || getPreferenceType == FlightPreferenceType.LAST_MINUTE)) {
        0.95 * priceSensitivity * classAdjustedPrice.toDouble / standardPrice //low prices impact these preferences less
      } else {
        priceSensitivity
      }
    1.0 + deltaFromStandardPrice * priceSensitivityModifier / standardPrice
  }

  def loyaltyAdjustRatio(link : Transport) = {
    val appeal = appealList.getOrElse(link.airline.id, AirlineAppeal(0))
    val loyalty = appeal.loyalty
    val base =  1 + (-0.1 + loyalty.toDouble / maxLoyalty / 2.25)  * loyaltySensitivity
    //println("factor " + loyaltyRatio + " at loyalty " + loyalty + " : " + adjustment)
    1 / base
  }

  def qualityAdjustRatio(homeAirport : Airport, link : Transport, preferredLinkClass : LinkClass, paxType: PassengerType.Value) : Double = {
    val qualityDelta = link.computedQuality - homeAirport.expectedQuality(link.distance, preferredLinkClass)
    val GOOD_QUALITY_DELTA = if (paxType == PassengerType.ELITE) {
      30
    } else {
      20
    }

    val priceAdjust =
      if (qualityDelta < 0) {
        1 - qualityDelta.toDouble / Link.MAX_QUALITY * 1
      } else if (qualityDelta < GOOD_QUALITY_DELTA) {
        1 - qualityDelta.toDouble / Link.MAX_QUALITY * 0.5
      } else { //reduced benefit on extremely high quality
        val extraDelta = qualityDelta - GOOD_QUALITY_DELTA
        1 - GOOD_QUALITY_DELTA.toDouble / Link.MAX_QUALITY * 0.5 - extraDelta.toDouble / Link.MAX_QUALITY * 0.2
      }

    if  (link.transportType == TransportType.GENERIC_TRANSIT) {
      1.0 //pax are using their own transport
    } else {
      1 + (priceAdjust - 1) * qualitySensitivity
    }
  }

  /**
   * returns cost, adjusted to preferredLinkClass with penalty
   */
  val priceAdjustedByLinkClassDiff = (link : Transport, linkClass : LinkClass, paxType : PassengerType.Value) => {
    val cost = link.cost(linkClass) //use cost here
    if (preferredLinkClass.level != 0 && linkClass.level < preferredLinkClass.level) { //ignore discount_economy
      val shortDistanceModified = 0.5 + 0.5 * Math.min(1, link.distance / 1000)
      val classDiffMultiplier: Double = 1 + (preferredLinkClass.level - linkClass.level) * 0.35 * shortDistanceModified
      
      val flightCategory = Computation.getFlightCategory(link.from, link.to)
      val basePreferredClassPrice = Pricing.computeStandardPrice(link.distance, flightCategory, preferredLinkClass, PassengerType.TRAVELER, 0)
      val baseLinkClassPrice = Pricing.computeStandardPrice(link.distance, flightCategory, linkClass, PassengerType.TRAVELER, 0)
      val preferredClassPriceRatio = basePreferredClassPrice.toDouble / baseLinkClassPrice
      
      (cost * preferredClassPriceRatio * classDiffMultiplier).toInt //have to normalize the price to match the preferred link class, * classDiffMultiplier for unwillingness to downgrade
    } else {
      cost
    }
  }

  val connectionCostRatio = 1.0

  val tripDurationAdjustRatio = (link : Transport, preferredLinkClass : LinkClass, paxType: PassengerType.Value) => {
    val classModifier = preferredLinkClass match {
      case FIRST => 0.35
      case BUSINESS => 0.25
      case _ => 0.1
    }
    val flightDurationSensitivity = paxType match {
      case PassengerType.ELITE => 0.6
      case PassengerType.BUSINESS => 0.4 + classModifier
      case PassengerType.TOURIST => 0 + classModifier
      case _ => 0.1 + classModifier
    }
    val flightDurationRatioDelta = {
      if (flightDurationSensitivity == 0 || link.transportType != TransportType.FLIGHT) {
        0
//      } else if (flightDurationSensitivity <= 0.6 && link.transportType == TransportType.FLIGHT && link.asInstanceOf[Link].getAssignedModel().get.category == Model.Category.SPECIAL ) {
//        0
      } else {
        val flightDurationThreshold = Computation.computeStandardFlightDuration(link.distance)
        Math.min(flightDurationSensitivity, (link.duration - flightDurationThreshold).toFloat / flightDurationThreshold * flightDurationSensitivity)
      }
    }
    val maxDiscount = flightDurationSensitivity * -1
    val finalDelta = Math.max(maxDiscount, flightDurationRatioDelta)
    Math.min(2.0, 1 + finalDelta) //max 2x penalty
  }

  val frequencyAdjustRatio = (link : Transport, linkClass : LinkClass, paxType: PassengerType.Value) => {
    val frequencySensitivity = paxType match {
      case PassengerType.TRAVELER => 0.2
      case PassengerType.BUSINESS => 0.6
      case PassengerType.ELITE => 0.3
      case _ => 0.15
    }
    //shorter duration flights care much more about flight frequency
    val distanceModifier = {
      if (frequencySensitivity == 0) {
        0
      } else {
        1.0 - Math.min(0.85, link.duration.toDouble / 180.0)
      }
    }

    val isFrequency = frequencyThreshold * 2
    val frequencyThresholdperPax = ThreadLocalRandom.current().nextInt(isFrequency)

    val delta = Math.max(-4, (frequencyThresholdperPax - link.frequency).toDouble / frequencyThresholdperPax)
    if (delta < 0) {
      1 + Math.max(-1 * frequencySensitivity * distanceModifier, delta * distanceModifier)
    } else {
      1 + Math.min(frequencySensitivity * distanceModifier, delta * distanceModifier)
    }
  }

  def loungeAdjustRatio(link : Transport, loungeLevelRequired : Int, linkClass: LinkClass) = {
    if (linkClass.level < BUSINESS.level) {
      1.0
    } else {
      val fromLounge = link.from.getLounge(link.airline.id, link.airline.getAllianceId, activeOnly = true)
      val toLounge = link.to.getLounge(link.airline.id, link.airline.getAllianceId, activeOnly = true)

      val fromLoungeLevel = fromLounge.map(_.level).getOrElse(0)
      val toLoungeLevel = toLounge.map(_.level).getOrElse(0)


      val fromLoungeRatioDelta = Lounge.priceAdjustRatio(fromLoungeLevel, loungeLevelRequired, link.distance)

      val toLoungeRatioDelta = Lounge.priceAdjustRatio(toLoungeLevel, loungeLevelRequired, link.distance)

      1 + fromLoungeRatioDelta + toLoungeRatioDelta
    }
  }
}

object FlightPreferenceType extends Enumeration {
  type Preference = Value

  protected case class Val(title: String, description: String, priority: Int) extends super.Val
  implicit def valueToFlightPreferenceTypeVal(x: Value): Val = x.asInstanceOf[Val]

  val DEAL = Val("Deal Seeker", "", 1)
  val BRAND = Val("Brand Sensitive", "", 2)
  val FREQUENT = Val("Frequent Flyer", "", 3)
  val LAST_MINUTE = Val("Last Minute Anything", "", 4)
  val LAST_MINUTE_DEAL = Val("Last Minute Deal", "", 4)
}

case class DealPreference(homeAirport : Airport, preferredLinkClass: LinkClass, override val priceModifier: Double) extends FlightPreference(homeAirport : Airport) {
  override val priceSensitivity = preferredLinkClass.priceSensitivity + 0.15
  override val qualitySensitivity = 0.4
  override val frequencyThreshold = 2

  def computeCost(baseCost : Double, link : Transport, linkClass : LinkClass) = {
    Math.max(1, baseCost)
  }

  val getPreferenceType: FlightPreferenceType.Value = FlightPreferenceType.DEAL
  override val connectionCostRatio = 0.4 //okay with taking connection
}


case class LastMinutePreference(homeAirport : Airport, preferredLinkClass: LinkClass, override val priceModifier : Double, override val loungeLevelRequired : Int) extends FlightPreference(homeAirport : Airport) {
  override val priceSensitivity = preferredLinkClass.priceSensitivity
  override val qualitySensitivity = if (priceModifier < 1) { //LAST_MINUTE_DEAL
    0.3
  } else {
    1.0
  }
  def computeCost(baseCost : Double, link : Transport, linkClass : LinkClass) = {
    baseCost
  }

  val getPreferenceType: FlightPreferenceType.Value = {
    if (priceModifier < 1) {
      FlightPreferenceType.LAST_MINUTE_DEAL
    } else {
      FlightPreferenceType.LAST_MINUTE
    }
  }
  override val connectionCostRatio = {
    if (priceModifier < 1) { //LAST_MINUTE_DEAL
      0.2
    } else { //LAST_MINUTE
      1.0
    }
  }
}


case class AppealPreference(homeAirport : Airport, preferredLinkClass : LinkClass, override val priceModifier : Double, override val loungeLevelRequired : Int, loyaltyRatio : Double, id : Int)  extends FlightPreference(homeAirport) {
  override val loyaltySensitivity = loyaltyRatio
  override val priceSensitivity = preferredLinkClass.priceSensitivity
  override val qualitySensitivity = if (loyaltyRatio > 1) {
    1.1
  } else {
    1.5
  }
  override val frequencyThreshold = if (loyaltyRatio > 1) {
    21
  } else {
    7
  }

  val getPreferenceType = {
   if (loyaltyRatio > 1) {
     FlightPreferenceType.FREQUENT
    } else {
     FlightPreferenceType.BRAND
    }
  }

  override val connectionCostRatio = {
    if (loyaltyRatio > 1) {
      1.8
    } else {
      1.2
    }
  }

  def computeCost(baseCost: Double, link : Transport, linkClass : LinkClass) : Double = {
    val noise = 1.0 + getFlatTopBellRandom(0.35, 0.25)
    val finalCost = baseCost * noise
    Math.max(1, finalCost)
  }
}

object AppealPreference {
  var count: Int = 0
  def getAppealPreferenceWithId(homeAirport : Airport, linkClass : LinkClass, priceModifier : Double, loungeLevelRequired : Int, loyaltyRatio : Double = 1.0) = {
    count += 1
    AppealPreference(homeAirport, linkClass, priceModifier, loungeLevelRequired = loungeLevelRequired, loyaltyRatio = loyaltyRatio, count)
  }
  
}


class FlightPreferencePool(preferencesWithWeight: Map[PassengerType.Value, List[(FlightPreference, Int)]]) { // Change the key
  val pool: Map[PassengerType.Value, Map[LinkClass, List[FlightPreference]]] = preferencesWithWeight.map { case (passengerType, preferenceList) =>
    (passengerType, preferenceList.groupBy { case (flightPreference, weight) =>
      flightPreference.preferredLinkClass
    }.view.mapValues { _.map { case (pref, weight) => pref }.toList }.toMap)
  }

  def draw(passengerType: PassengerType.Value, linkClass: LinkClass, fromAirport: Airport, toAirport: Airport): FlightPreference = {
    val poolForPassengerType = pool.getOrElse(passengerType, pool(PassengerType.BUSINESS))
    val poolForClass = poolForPassengerType(linkClass)
    poolForClass(ThreadLocalRandom.current().nextInt(poolForClass.length))
  }
}
