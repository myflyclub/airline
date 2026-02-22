package controllers

import com.patson.DemandGenerator
import com.patson.model.airplane.Airplane
import com.patson.model.{FlightPreferenceType, _}
import com.patson.util.AirportCache

import scala.collection.mutable.ListBuffer
import scala.collection.{MapView, immutable, mutable}
import scala.util.Random

/**
 * Link comment generation utility.
 *
 * Weights comments based on actual lever impact from FlightPreference analysis:
 *
 * BY PREFERENCE TYPE (ECONOMY, TRAVELER):
 *   DEAL:         Price 34%, Quality  4%, Freq  0%, Loyalty  0%, Lounge  0%
 *   BRAND:        Price 19%, Quality 14%, Freq  0%, Loyalty 16%, Lounge  0%
 *   FREQUENT:     Price 18%, Quality 11%, Freq  7%, Loyalty 45%, Lounge  0%
 *   LAST_MINUTE:  Price 19%, Quality 10%, Freq  0%, Loyalty  0%, Lounge  0%
 *   LAST_MIN_DEAL:Price 30%, Quality  3%, Freq  1%, Loyalty  0%, Lounge  0%
 *
 * BY PASSENGER TYPE (ECONOMY):
 *   TOURIST:      Price 21%, Quality 16%, Freq  0%, Loyalty 45%
 *   TRAVELER:     Price 18%, Quality 14%, Freq  1%, Loyalty 44%
 *   ELITE:        Price 15%, Quality 18%, Freq  1%, Loyalty 45%
 *   BUSINESS:     Price 14%, Quality 14%, Freq  6%, Loyalty 45%
 *
 * BY LINK CLASS:
 *   ECONOMY:      Price 18%, Quality 15%, Freq  1%, Loyalty 45%, Lounge  0%
 *   BUSINESS:     Price 16%, Quality 17%, Freq  1%, Loyalty 44%, Lounge 24%
 *   FIRST:        Price 14%, Quality 24%, Freq  0%, Loyalty 45%, Lounge 14%
 */
object LinkCommentUtil {
  val SIMULATE_AIRPORT_COUNT = 5

  /**
   * Context for generating personalized comments
   */
  case class CommentContext(
                             paxType: PassengerType.Value,
                             linkClass: LinkClass,
                             fromAirport: Airport,
                             toAirport: Airport,
                             airlineName: String,
                             distance: Int
                           ) {
    lazy val destName: String = toAirport.city
    lazy val originName: String = fromAirport.city
    lazy val isShortHaul: Boolean = distance < 800
    lazy val isLongHaul: Boolean = distance > 4000
    lazy val isUltraLong: Boolean = distance > 8000
  }

  def simulateComments(consumptionEntries: List[LinkConsumptionHistory], airline: Airline, link: Link): Map[(LinkClass, FlightPreferenceType.Value, PassengerType.Value), LinkCommentSummary] = {
    val random = new Random(airline.id + link.id)

    val topConsumptionEntriesByHomeAirport: List[(Int, List[LinkConsumptionHistory])] = consumptionEntries.groupBy(_.homeAirport.id).toList.sortBy(_._2.map(_.passengerCount).sum).takeRight(SIMULATE_AIRPORT_COUNT)
    val result = mutable.Map[(LinkClass, FlightPreferenceType.Value, PassengerType.Value), ListBuffer[LinkComment]]()
    topConsumptionEntriesByHomeAirport.foreach {
      case (airportId, consumptions) => AirportCache.getAirport(airportId, true).foreach { homeAirport =>
        val pool: immutable.Map[(LinkClass, FlightPreferenceType.Value), List[FlightPreference]] = DemandGenerator.getFlightPreferencePoolOnAirport(homeAirport).pool.toList.flatMap {
          case (passengerType, preferencesByLinkClass) =>
            preferencesByLinkClass.flatMap {
              case (linkClass, preferences) => preferences.groupBy(_.getPreferenceType).map {
                case (preferenceType, group) => ((linkClass, preferenceType), group)
              }
            }
        }.toMap
        consumptions.foreach { consumption =>
          pool.get((consumption.preferredLinkClass, consumption.preferenceType)).foreach { preferences =>
            result.getOrElseUpdate((consumption.preferredLinkClass, consumption.preferenceType, consumption.passengerType), ListBuffer()).appendAll(
              generateCommentsPerConsumption(preferences, consumption, homeAirport, airline, link, random))
          }
        }
      }
    }

    val sampleSizeGrouping: MapView[(LinkClass, FlightPreferenceType.Value, PassengerType.Value), Int] = topConsumptionEntriesByHomeAirport.flatMap(_._2).groupBy(entry => (entry.preferredLinkClass, entry.preferenceType, entry.passengerType)).view.mapValues(_.map(_.passengerCount).sum)
    result.map {
      case (key, comments) =>
        val sampleSize = sampleSizeGrouping(key)
        (key, LinkCommentSummary(comments.toList, sampleSize))
    }.toMap
  }

  val MAX_SAMPLE_SIZE_PER_CONSUMPTION = 20

  sealed trait CommentMetric {
    def impactMagnitude: Double
    def isPositive: Boolean
  }

  case class RatioMetric(ratio: Double) extends CommentMetric {
    override def impactMagnitude: Double = Math.abs((1 - ratio) * 100)
    override def isPositive: Boolean = ratio < 1.0
  }

  case class TargetMetric(actual: Double, target: Double, higherIsBetter: Boolean = true) extends CommentMetric {
    private val delta = if (higherIsBetter) actual - target else target - actual
    override def impactMagnitude: Double = Math.abs(delta / Math.max(1, target) * 100)
    override def isPositive: Boolean = delta >= 0
  }

  case class CommentWeight(commentGroup: LinkCommentGroup.Value, baseWeight: Int, metric: CommentMetric) {
    def effectiveWeight: Int = Math.max(0, (baseWeight * metric.impactMagnitude / 100).toInt)
  }

  case class CommentWeightedPool(weights: List[CommentWeight]) {
    val totalWeights: Int = weights.map(_.effectiveWeight).sum
    private var weightMarkerWalker = 0
    val weightMarkers: List[(CommentWeight, Int)] = weights.map { weight =>
      weightMarkerWalker = weightMarkerWalker + weight.effectiveWeight
      (weight, weightMarkerWalker)
    }

    def drawCommentWeight(random: Random): Option[CommentWeight] = {
      if (totalWeights == 0) None
      else {
        val target = random.nextInt(totalWeights)
        weightMarkers.find(weightMarker => weightMarker._2 >= target).map(_._1)
      }
    }
  }

  def getImpactWeights(preferenceType: FlightPreferenceType.Value, linkClass: LinkClass, paxType: PassengerType.Value): Map[LinkCommentGroup.Value, Int] = {
    import LinkCommentGroup._

    val baseWeights: Map[LinkCommentGroup.Value, Int] = preferenceType match {
      case FlightPreferenceType.DEAL =>
        Map(PRICE -> 34, QUALITY -> 4, FREQUENCY -> 0, LOYALTY -> 0, DURATION -> 5, LOUNGE -> 0)
      case FlightPreferenceType.BRAND =>
        Map(PRICE -> 19, QUALITY -> 14, FREQUENCY -> 0, LOYALTY -> 16, DURATION -> 5, LOUNGE -> 0)
      case FlightPreferenceType.FREQUENT =>
        Map(PRICE -> 18, QUALITY -> 11, FREQUENCY -> 7, LOYALTY -> 45, DURATION -> 5, LOUNGE -> 0)
      case FlightPreferenceType.LAST_MINUTE =>
        Map(PRICE -> 19, QUALITY -> 10, FREQUENCY -> 0, LOYALTY -> 0, DURATION -> 10, LOUNGE -> 0)
      case FlightPreferenceType.LAST_MINUTE_DEAL =>
        Map(PRICE -> 30, QUALITY -> 3, FREQUENCY -> 1, LOYALTY -> 0, DURATION -> 5, LOUNGE -> 0)
    }

    val loungeWeight = linkClass match {
      case BUSINESS => 24
      case FIRST => 14
      case _ => 0
    }

    val qualityBoost = linkClass match {
      case FIRST => 10
      case BUSINESS => 3
      case _ => 0
    }

    val freqBoost = paxType match {
      case PassengerType.BUSINESS => 5
      case _ => 0
    }

    baseWeights ++ Map(
      LOUNGE -> loungeWeight,
      QUALITY -> (baseWeights.getOrElse(QUALITY, 0) + qualityBoost),
      FREQUENCY -> (baseWeights.getOrElse(FREQUENCY, 0) + freqBoost)
    )
  }

  def getLoungeMetric(loungeLevelRequired: Int, link: Link, airline: Airline): TargetMetric = {
    val fromLounge = link.from.getLounge(airline.id, airline.getAllianceId, activeOnly = true)
    val toLounge = link.to.getLounge(airline.id, airline.getAllianceId, activeOnly = true)
    val avgLoungeLevel = (fromLounge.map(_.level).getOrElse(0) + toLounge.map(_.level).getOrElse(0)) / 2.0
    val effectiveRequirement = Math.max(1.0, loungeLevelRequired.toDouble)
    TargetMetric(avgLoungeLevel, effectiveRequirement, higherIsBetter = true)
  }

  def generateCommentsPerConsumption(preferences: List[FlightPreference], consumption: LinkConsumptionHistory, homeAirport: Airport, airline: Airline, link: Link, random: Random): ListBuffer[LinkComment] = {
    implicit val randomImplicit: Random = random

    val linkClass = consumption.linkClass
    val paxType = consumption.passengerType
    val preferredLinkClass = consumption.preferredLinkClass
    val sampleSize = Math.min(MAX_SAMPLE_SIZE_PER_CONSUMPTION, consumption.passengerCount)
    val allComments = ListBuffer[LinkComment]()
    val standardDuration = Computation.computeStandardFlightDuration(link.distance)

    // Create context for personalized comments
    val ctx = CommentContext(
      paxType = paxType,
      linkClass = preferredLinkClass,
      fromAirport = link.from,
      toAirport = link.to,
      airlineName = airline.name,
      distance = link.distance
    )

    import LinkCommentGroup._

    val poolByPreference: Map[FlightPreference, CommentWeightedPool] = preferences.map { preference =>
      val impactWeights = getImpactWeights(preference.getPreferenceType, preferredLinkClass, paxType)

      val metricsWithWeights: List[CommentWeight] = List(
        CommentWeight(PRICE, impactWeights.getOrElse(PRICE, 0),
          RatioMetric(preference.priceAdjustRatio(link, linkClass, paxType))),
        CommentWeight(LOYALTY, impactWeights.getOrElse(LOYALTY, 0),
          RatioMetric(preference.loyaltyAdjustRatio(link))),
        CommentWeight(QUALITY, impactWeights.getOrElse(QUALITY, 0),
          RatioMetric(preference.qualityAdjustRatio(homeAirport, link, preferredLinkClass, paxType))),
        CommentWeight(DURATION, impactWeights.getOrElse(DURATION, 0),
          RatioMetric(preference.tripDurationAdjustRatio(link, preferredLinkClass, paxType))),
        CommentWeight(FREQUENCY, impactWeights.getOrElse(FREQUENCY, 0),
          RatioMetric(preference.frequencyAdjustRatio(link, preferredLinkClass, paxType))),
        CommentWeight(LOUNGE, impactWeights.getOrElse(LOUNGE, 0),
          getLoungeMetric(preference.loungeLevelRequired, link, airline))
      )

      (preference, CommentWeightedPool(metricsWithWeights))
    }.toMap

    val satisfactionDeviation = Math.abs(consumption.satisfaction - 0.5)
    val commentGenerationCount = if (satisfactionDeviation < 0.2) 1 else if (satisfactionDeviation < 0.3) 2 else 3

    for (_ <- 0 until sampleSize) {
      val preference = preferences(random.nextInt(preferences.length))
      val commentsOfThisSample = ListBuffer[LinkComment]()

      for (_ <- 0 until commentGenerationCount) {
        poolByPreference(preference).drawCommentWeight(random).foreach { weight =>
          val comments = weight.commentGroup match {
            case PRICE =>
              generateCommentsForPrice(weight.metric.asInstanceOf[RatioMetric].ratio, ctx)
            case LOYALTY =>
              generateCommentsForLoyalty(weight.metric.asInstanceOf[RatioMetric].ratio, ctx)
            case QUALITY =>
              generateCommentsForQuality(link.computedQuality(), link.rawQuality, airline.getCurrentServiceQuality(),
                link.getAssignedAirplanes().keys.toList, homeAirport.expectedQuality(link.distance, linkClass), ctx)
            case DURATION =>
              generateCommentsForFlightDuration(link.duration, standardDuration, ctx)
            case FREQUENCY =>
              generateCommentsForFlightFrequency(link.duration, link.frequency, preference.frequencyThreshold, ctx)
            case LOUNGE =>
              generateCommentsForLounge(preference.loungeLevelRequired, link.from, link.to, airline.id, airline.getAllianceId(), ctx)
            case _ => List.empty
          }
          comments.foreach { comment =>
            if (!commentsOfThisSample.map(_.category).contains(comment.category)) {
              commentsOfThisSample.append(comment)
            }
          }
        }
      }
      allComments.appendAll(commentsOfThisSample)
    }
    allComments
  }

  def generateCommentsForPrice(ratio: Double, ctx: CommentContext)(implicit random: Random): List[LinkComment] = {
    val expectedRatio = com.patson.Util.getBellRandom(1, 0.4, Some(random.nextInt()))
    List(LinkComment(CommentCategory.Price, ratio, expectedRatio, ctx)).flatten
  }

  def generateCommentsForLoyalty(ratio: Double, ctx: CommentContext)(implicit random: Random): List[LinkComment] = {
    val expectedRatio = com.patson.Util.getBellRandom(1, 0.4, Some(random.nextInt()))
    List(LinkComment(CommentCategory.Loyalty, ratio, expectedRatio, ctx)).flatten
  }

  def generateCommentsForQuality(computedQuality: Int, rawQuality: Int, serviceQuality: Double, airplanes: List[Airplane],
                                 expectedQuality: Int, ctx: CommentContext)(implicit random: Random): List[LinkComment] = {
    val qualityDelta = computedQuality - expectedQuality
    List(
      generateCommentForRawQuality(rawQuality, qualityDelta, ctx),
      generateCommentForQuality(qualityDelta, computedQuality, ctx),
      generateCommentForAirplaneCondition(airplanes, qualityDelta, ctx)).flatten
  }

  def generateCommentForRawQuality(rawQuality: Int, qualityDelta: Int, ctx: CommentContext)(implicit random: Random): List[LinkComment] = {
    List(LinkComment(CommentCategory.RawQuality, rawQuality, qualityDelta, ctx)).flatten
  }

  def generateCommentForQuality(qualityDelta: Int, computedQuality: Int, ctx: CommentContext)(implicit random: Random): List[LinkComment] = {
    List(LinkComment(CommentCategory.ServiceQuality, qualityDelta, computedQuality, ctx)).flatten
  }

  def generateCommentForAirplaneCondition(airplanes: List[Airplane], qualityDelta: Int, ctx: CommentContext)(implicit random: Random): List[LinkComment] = {
    val pickedAirplane = airplanes(Random.nextInt(airplanes.length))
    List(LinkComment(CommentCategory.AirplaneCondition, pickedAirplane.condition, pickedAirplane.model.quality, qualityDelta, ctx)).flatten
  }

  def generateCommentsForFlightFrequency(flightDuration: Int, frequency: Int, expectedFrequency: Int, ctx: CommentContext)(implicit random: Random): List[LinkComment] = {
    val adjustedExpectedFrequency = (expectedFrequency * com.patson.Util.getBellRandom(1, 0.7, Some(random.nextInt()))).toInt
    List(LinkComment(CommentCategory.Frequency, frequency, adjustedExpectedFrequency, flightDuration, ctx)).flatten
  }

  def generateCommentsForFlightDuration(flightDuration: Int, expectedDuration: Int, ctx: CommentContext)(implicit random: Random): List[LinkComment] = {
    val adjustedExpectedDuration = (expectedDuration * com.patson.Util.getBellRandom(1, 0.7, Some(random.nextInt()))).toInt
    List(LinkComment(CommentCategory.FlightDuration, flightDuration, adjustedExpectedDuration, ctx)).flatten
  }

  def generateCommentsForLounge(loungeRequirement: Int, fromAirport: Airport, toAirport: Airport, airlineId: Int,
                                allianceIdOption: Option[Int], ctx: CommentContext)(implicit random: Random): List[LinkComment] = {
    List(
      LinkComment(CommentCategory.Lounge, loungeRequirement, fromAirport, airlineId, allianceIdOption, ctx),
      LinkComment(CommentCategory.Lounge, loungeRequirement, toAirport, airlineId, allianceIdOption, ctx)).flatten
  }
}

case class LinkCommentSummary(comments: List[LinkComment], sampleSize: Int)
case class LinkComment(description: String, category: LinkCommentType.Value, positive: Boolean)

sealed trait CommentCategory
object CommentCategory {
  case object Price extends CommentCategory
  case object Loyalty extends CommentCategory
  case object RawQuality extends CommentCategory
  case object ServiceQuality extends CommentCategory
  case object AirplaneCondition extends CommentCategory
  case object Frequency extends CommentCategory
  case object FlightDuration extends CommentCategory
  case object Lounge extends CommentCategory
}

object LinkComment {
  import LinkCommentUtil.CommentContext

  def apply(category: CommentCategory, params: Any*)(implicit random: Random): Option[LinkComment] = {
    val (description, positive) = (category, params.toList) match {
      case (CommentCategory.Price, List(priceRatio: Double, expectationRatio: Double, ctx: CommentContext)) =>
        generatePriceComment(priceRatio, expectationRatio, ctx)
      case (CommentCategory.Loyalty, List(ratio: Double, expectedRatio: Double, ctx: CommentContext)) =>
        generateLoyaltyComment(ratio, expectedRatio, ctx)
      case (CommentCategory.RawQuality, List(rawQuality: Int, qualityDelta: Int, ctx: CommentContext)) =>
        generateRawQualityComment(rawQuality, qualityDelta, ctx)
      case (CommentCategory.ServiceQuality, List(qualityDelta: Double, computedQuality: Int, ctx: CommentContext)) =>
        generateServiceQualityComment(qualityDelta, computedQuality, ctx)
      case (CommentCategory.AirplaneCondition, List(condition: Double, quality: Double, delta: Int, ctx: CommentContext)) =>
        generateAirplaneConditionComment(condition, quality, delta, ctx)
      case (CommentCategory.Frequency, List(frequency: Double, expectedFrequency: Double, duration: Int, ctx: CommentContext)) =>
        generateFrequencyComment(frequency, expectedFrequency, duration, ctx)
      case (CommentCategory.FlightDuration, List(duration: Int, expectedDuration: Int, ctx: CommentContext)) =>
        generateFlightDurationComment(duration, expectedDuration, ctx)
      case (CommentCategory.Lounge, List(requirement: Int, airport: Airport, airlineId: Int, allianceId: Option[Int @unchecked], ctx: CommentContext)) =>
        generateLoungeComment(requirement, airport, airlineId, allianceId, ctx)
      case _ => (None, false)
    }

    val commentType = category match {
      case CommentCategory.Price => LinkCommentType.PRICE
      case CommentCategory.Loyalty => LinkCommentType.LOYALTY
      case CommentCategory.RawQuality => LinkCommentType.RAW_QUALITY
      case CommentCategory.ServiceQuality => LinkCommentType.SERVICE_QUALITY
      case CommentCategory.AirplaneCondition => LinkCommentType.AIRPLANE_CONDITION
      case CommentCategory.Frequency => LinkCommentType.FREQUENCY
      case CommentCategory.FlightDuration => LinkCommentType.FLIGHT_DURATION
      case CommentCategory.Lounge => LinkCommentType.LOUNGE
    }

    description.map(desc => LinkComment(desc, commentType, positive))
  }

  // ============ PRICE COMMENTS ============
  private def generatePriceComment(priceRatio: Double, expectationRatio: Double, ctx: CommentContext)(implicit random: Random): (Option[String], Boolean) = {
    val delta = priceRatio - expectationRatio
    import PassengerType._

    val comment: Option[String] = (delta, ctx.paxType, ctx.linkClass) match {
      // AMAZING DEALS (delta < -0.7)
      case (d, TOURIST, _) if d < -0.7 => pick(
        s"Can't believe a vacation in ${ctx.destName} could be this cheap!",
        s"I told everyone at work about this deal. Now half my office is flying to ${ctx.destName} too!",
        s"The ticket to ${ctx.destName} cost less than my airport sandwich. I bought two sandwiches to celebrate."
      )
      case (d, BUSINESS, _) if d < -0.7 => pick(
        "My company's finance department sent me a thank-you email. That's never happened before.",
        s"At this price, I can expense the flight AND take myself to dinner in ${ctx.destName}.",
        "The CFO high-fived me in the elevator. Apparently we're 'optimizing travel spend.'"
      )
      case (d, ELITE, FIRST) if d < -0.7 => pick(
        "First class at these prices? Don't tell my accountant.",
        s"Flying first to ${ctx.destName} for less than some people pay for economy. Delightful.",
        "I actually checked the decimal point three times. Still a deal. Extraordinary."
      )
      case (d, _, _) if d < -0.7 => pick(
        s"Wow! ${ctx.airlineName}'s ticket is an absolute steal!",
        "I checked three times to make sure this price was real.",
        s"Flying to ${ctx.destName} for this price should be illegal. Don't change it though!",
        s"Booked it before the price changed. Fastest I've moved since my divorce lawyer called."
      )

      // GOOD DEALS (delta < -0.5)
      case (d, TOURIST, _) if d < -0.5 => pick(
        s"The savings on this ${ctx.destName} flight paid for a hotel upgrade!",
        s"${ctx.airlineName}'s prices means more vacations for the whole family!",
        s"I'm framing this receipt. Cheapest flight to ${ctx.destName} in living memory."
      )
      case (d, BUSINESS, _) if d < -0.5 => pick(
        "Under budget! My manager will be thrilled.",
        s"Finance will actually approve the ${ctx.destName} trip this time. Unprecedented."
      )
      case (d, _, _) if d < -0.5 => pick(
        s"Good money saver! Thanks ${ctx.airlineName}.",
        s"${ctx.destName} bound without breaking the bank!",
        "At this price I booked it before thinking. Zero regrets."
      )

      // REASONABLE (delta < -0.3)
      case (d, TOURIST, _) if d < -0.3 => pick(
        s"The price is very reasonable. Happy to recommend ${ctx.airlineName}.",
        "Decent value. Will book again.",
        s"Not a deal, not a rip-off — the Goldilocks of airfare to ${ctx.destName}."
      )
      case (d, BUSINESS, _) if d < -0.3 => pick(
        "Reasonable business travel rates.",
        s"Fair price on ${ctx.originName} to ${ctx.destName}.",
        "Expense report approved on the first submission. A personal best."
      )
      case (d, _, _) if d < -0.3 => pick(
        "The ticket price is very reasonable."
      )

      case (d, _, _) if d < 0 => pick(
        "The ticket price is quite reasonable."
      )

      // EXPENSIVE (delta < 0.2)
      case (d, TOURIST, ECONOMY) if d < 0.2 => pick(
        "Not cheap, but I really wanted to go.",
        s"Paid more than I'd like for ${ctx.destName}, but vacation awaits!",
        s"My budget cried a little. ${ctx.destName} had better be worth the tears."
      )
      case (d, BUSINESS, _) if d < 0.2 => pick(
        "Pushing the expense policy limits here.",
        "Not the cheapest, but I need to be there.",
        "Technically within policy. Technically."
      )
      case (d, _, _) if d < 0.2 => pick(
        "A bit pricey for my taste.",
        "Could be cheaper."
      )

      // VERY EXPENSIVE (delta < 0.4)
      case (d, TOURIST, _) if d < 0.4 => pick(
        s"My wallet is crying! ${ctx.destName} better be worth it!",
        "I'm going to need a vacation from this ticket price."
      )
      case (d, BUSINESS, _) if d < 0.4 => pick(
        "My expense report is going to raise eyebrows.",
        s"For this price, ${ctx.airlineName} better have amazing coffee.",
      )
      case (d, ELITE, _) if d < 0.4 => pick(
        "Even I think this is overpriced, and I literally burn money just for fun!",
        s"Expensive even by my gilded standards. ${ctx.airlineName} is testing my patience."
      )
      case (d, _, _) if d < 0.4 => pick(
        "The ticket is expensive."
      )

      // HIGHWAY ROBBERY (delta < 0.6)
      case (d, TOURIST, _) if d < 0.6 => pick(
        "I could have chartered a yacht for this much!",
        s"This ticket cost more than my entire ${ctx.destName} trip budget!",
        s"I had to explain to my kids why we can't afford Christmas AND ${ctx.destName}. I chose ${ctx.destName}. They'll understand someday."
      )
      case (d, BUSINESS, _) if d < 0.6 => pick(
        "I may need to explain this ticket price to the CEO personally.",
        "Submitted my expense report and immediately updated my LinkedIn."
      )
      case (d, ELITE, FIRST) if d < 0.6 => pick(
        "For this price, I expect the pilot to personally carry my luggage.",
        "I could buy the plane for a few more flights at this rate.",
        s"My financial advisor saw the charge and called an emergency meeting."
      )
      case (d, _, _) if d < 0.6 => pick(
        "The ticket is very expensive!",
        "This price is outrageous!",
        "Way too expensive."
      )

      // EXTREME HIGHWAY ROBBERY (delta >= 0.6)
      case (_, TOURIST, _) => pick(
        s"Insane prices! I'm never flying to ${ctx.destName} again!",
        "This airline thinks we print money at home!",
        s"Sold a kidney. Still short. ${ctx.destName} was not worth it.",
        s"My grandchildren will be paying off this ticket. Hi grandchildren."
      )
      case (_, BUSINESS, _) => pick(
        "Our CFO is going to have an aneurysm.",
        "I'm billing this directly to the client. With an apology letter.",
        s"Filed this under 'act of God' on the expense report. Let the auditors argue with fate."
      )
      case (_, ELITE, _) => pick(
        "I've bought sports cars for less than this flight.",
        "Absolutely unconscionable pricing. My lawyers will be in touch.",
        s"I don't get outrage. I get injunctions. See you in court, ${ctx.airlineName}."
      )
      case _ => pick(
        "Insane! This is highway robbery!",
        "The price alone traumatized me.",
        "I've seen ransom notes with more reasonable numbers."
      )
    }
    (comment, delta < 0)
  }

  // ============ LOYALTY COMMENTS ============
  private def generateLoyaltyComment(ratio: Double, expectedRatio: Double, ctx: CommentContext)(implicit random: Random): (Option[String], Boolean) = {
    val delta = ratio - expectedRatio
    import PassengerType._

    val comment: Option[String] = (delta, ctx.paxType) match {
      // EXTREMELY LOYAL (delta < -0.2)
      case (d, BUSINESS) if d < -0.2 => pick(
        s"I fly ${ctx.airlineName} so much, the crew knows my coffee order.",
        s"${ctx.airlineName} is basically family.",
        s"${ctx.airlineName} upgraded me without asking. That's true love."
    )
      case (d, ELITE) if d < -0.2 => pick(
        s"${ctx.airlineName} and I have a beautiful relationship. They fly, I pay, everyone's happy.",
        "I would never cheat on my airline. That's a sacred bond.",
        s"I forget my kids' birthdays and yet I can recite my ${ctx.airlineName} loyalty number from memory.",
        s"My ${ctx.airlineName} platinum card is shinier than my wedding ring."
      )
      case (d, TOURIST) if d < -0.2 => pick(
        s"I got ${ctx.airlineName} tattooed on my butt! We're forever baby",
      )
      case (d, _) if d < -0.2 => pick(
        "I would never travel with any airline other than yours!",
        s"${ctx.airlineName} forever!",
        "Die-hard fan since day one."
      )

      // LOYAL (delta < -0.1)
      case (d, BUSINESS) if d < -0.1 => pick(
        s"${ctx.airlineName} is my go-to for business travel.",
        "The consistent service keeps me coming back.",
        s"If the company ever lets me pick my own airline, it's ${ctx.airlineName}. Always."
      )
      case (d, ELITE) if d < -0.1 => pick(
        "The recognition as a loyal customer is appreciated.",
        s"Status benefits on ${ctx.airlineName} make the difference.",
        "They remember my preferences. In a world of strangers, that matters enormously."
      )
      case (d, _) if d < -0.1 => pick(
        s"I am a fan of ${ctx.airlineName}!"
      )

      // SOMEWHAT POSITIVE (delta < 0)
      case (d, _) if d < 0 => pick(
        s"I have heard some nice things about ${ctx.airlineName}.",
        s"${ctx.airlineName} is building trust with me, one flight at a time."
      )

      // NEUTRAL TO SLIGHTLY NEGATIVE (delta < 0.2)
      case (d, BUSINESS) if d < 0.2 => pick(
        "I fly whoever has the best schedule, honestly.",
        s"${ctx.airlineName} is fine, but I'm not married to them.",
        s"I want to be loyal, but I'm still getting to know ${ctx.airlineName}."
      )
      case (d, _) if d < 0.2 => pick(
        s"I'm not really a fan of ${ctx.airlineName}.",
        "They're an airline. They fly planes. That's about it."
      )

      // NEGATIVE (delta >= 0.2)
      case (d, BUSINESS) if d >= 0.2 => pick(
        s"My assistant knows to never book ${ctx.airlineName} unless everything else is on fire; they just haven't won my loyalty yet."
      )
      case (d, ELITE) if d >= 0.2 => pick(
        "I have status with three other airlines. This one doesn't make the cut.",
      )
      case (d, TOURIST) if d >= 0.2 => pick(
        "My cousin told me horror stories about this airline."
      )
      case _ => pick(
        "I'm looking for my forever airline, and I'd rather travel with literally anyone else.",
        s"${ctx.airlineName} needs to do better to earn my loyalty."
      )
    }
    (comment, delta < 0)
  }

  // ============ RAW QUALITY COMMENTS ============
  private def generateRawQualityComment(rawQuality: Int, qualityDelta: Int, ctx: CommentContext)(implicit random: Random): (Option[String], Boolean) = {
    import PassengerType._

    val comment: Option[String] = (rawQuality, qualityDelta, ctx.paxType, ctx.linkClass) match {
      // ULTRA LOW COST (rawQuality <= 20)
      case (q, d, TOURIST, _) if q <= 20 && d < -15 => pick(
        "The 'seat' was a plastic board with dreams of being furniture.",
        s"I'm basically cargo. Cargo that paid good money for ${ctx.destName}.",
        "No legroom, no snacks, no mercy. But hey, cheap tickets!",
        "I've sat on bleachers at youth soccer games with more cushioning and dignity.",
        s"The overhead bin above me had a rat in it. Not a metaphor. An actual rat. A big one. We made eye contact."
      )
      case (q, d, BUSINESS, _) if q <= 20 && d < -15 => pick(
        s"I billed my company for therapy after ${ctx.airlineName}'s non-existent service.",
        "The WiFi costs more than the ticket. Make it make sense.",
        "Tried to work. Couldn't. The seat was vibrating. The whole seat. For four hours.",
        "I filed a formal complaint on behalf of my lumbar spine."
      )
      case (q, d, _, _) if q <= 20 && d < -10 => pick(
        "They had ads playing on the headrest. I couldn't escape.",
        "Fees fees and more fees!",
        "They charged me to bring my purse!",
        "At this quality level, I expected rats. I was not disappointed.",
        "The seat-back pocket had been repurposed as a rat hotel. Based on the evidence, recently."
      )
      case (q, d, _, _) if q <= 20 && d > 10 => pick(
        "Ultra low cost, but surprisingly decent service!",
        "Fees for everything, but the crew was genuinely nice.",
        "You get what you pay for, but the basics were solid."
      )

      // STANDARD LOW COST (rawQuality 21-40)
      case (q, d, TOURIST, _) if q <= 40 && d < -15 => pick(
        "The seat was smaller than my patience, which is saying something.",
        s"Cramped flight to ${ctx.destName}. At least it was short. Wait, it wasn't.",
        "I've had more legroom on a bicycle.",
        s"Knees touching the seat in front. Not my knees. The passenger behind me's knees. In MY back. For ${ctx.distance / 800 + 1} hours.",
        "My seatmate was a rat. He had the window seat. He seemed pleased about it."
      )
      case (q, d, BUSINESS, _) if q <= 40 && d < -10 => pick(
        "Had to work on my laptop at a 45-degree angle. My neck is ruined.",
        "The service had all the warmth and charm of a federal subpoena.",
        "I've had better working conditions in a bus station. I've measured.",
        "Budget cabin on a business ticket. I'd complain but frankly I'm just tired."
      )
      case (q, d, _, _) if q <= 40 && d > 10 => pick(
        "Budget airline but premium attitude from the crew!",
        "The flight attendants made it feel way more upscale than it was.",
        "Good service despite the no-frills setup."
      )

      // STANDARD (rawQuality 41-60)
      case (q, d, ELITE, _) if q <= 60 && d < -15 => pick(
        "Standard service isn't standard enough for me.",
        "I expected more. As an elite, I always expect more.",
        "Adequate. I despise adequate. Adequate is what happens when nobody is trying.",
        "They served the same food as economy. In business class. I need a moment."
      )
      case (q, d, _, _) if q <= 60 && d > 20 => pick(
        "Pleasantly surprised! Better than expected.",
        "The little touches made a difference.",
        "Standard flight, above-standard service."
      )

      // PREMIUM (rawQuality 61-80)
      case (q, d, TOURIST, _) if q <= 80 && d < -10 => pick(
        "Fancy seats but the food was airplane food. You know what I mean.",
        "Premium everything except the boarding process. Still a cattle call.",
        "Great amenities but slow WiFi. Can't have it all, I guess.",
        "Lovely plane, shame about the turbulence over the Rockies that sent my champagne into my neighbor's lap."
      )
      case (q, d, ELITE, _) if q <= 80 && d > 15 => pick(
        "Finally, an airline that understands premium service!",
        "The attention to detail was exquisite.",
        s"From ${ctx.originName} to ${ctx.destName} in style. This is how it should be.",
        "I've flown a lot of premium cabins. This one remembered my name without being told. Chilling. Wonderful."
      )
      case (q, d, _, _) if q <= 80 && d > 15 => pick(
        "Loved the unlimited drinks selection!",
        "Wonderful entertainment options!",
        "I slept so well! Those seats are amazing.",
        "They brought warm towels twice. Twice! Nobody does that anymore."
      )

      // LUXURY (rawQuality 81-100)
      case (q, d, ELITE, FIRST) if q > 80 && d < -10 => pick(
        "At this price point, the caviar should be beluga, not whatever that was.",
        "The champagne was merely adequate. I've had better on yachts.",
        "First class means FIRST class. This was 'first class with asterisks.'",
        "The suite door didn't close properly. The suite DOOR. In first class. I'm composing a strongly worded letter as we speak.",
        "I half-expected them to bring me a rat on a silver platter. The way this night was going, it would have fit right in."
      )
      case (q, d, BUSINESS, BUSINESS) if q > 80 && d < -10 => pick(
        "The lie-flat seat didn't actually lie flat. Lies!",
        "Premium cabin food shouldn't need salt. This needed therapy.",
        "Paid premium, received adequate. The ancient insult of business travel."
      )
      case (q, d, ELITE, FIRST) if q > 80 && d > 20 => pick(
        s"The caviar to ${ctx.destName} was exquisite. Chef's kiss.",
        "Pajamas! They gave me PAJAMAS! Peak aviation luxury.",
        s"${ctx.airlineName} first class is art. I'm merely a passenger in a masterpiece.",
        "They delivered my pre-ordered meal with a handwritten note. I'm framing it."
      )
      case (q, d, _, FIRST) if q > 80 && d > 20 => pick(
        "I feel like I've lived a week in absolute comfort.",
        "Everything was perfect. The seat, the food, the crew, everything.",
        "This is why I saved up for first class. Worth every penny.",
        s"I boarded looking like a normal person and deplaned looking like someone who belongs in ${ctx.destName}."
      )
      case (q, d, _, BUSINESS) if q > 80 && d > 15 => pick(
        "Business class done right. Arrived refreshed and ready.",
        "The amenity kit is nicer than my actual bathroom products.",
        "Productivity in the sky! Great workspace setup.",
        "They dimmed the cabin at exactly the right moment. I didn't ask. They just knew. Eerie."
      )

      case _ => None
    }
    (comment, qualityDelta > 0)
  }

  // ============ SERVICE QUALITY COMMENTS ============
  private def generateServiceQualityComment(qualityDelta: Double, computedQuality: Int, ctx: CommentContext)(implicit random: Random): (Option[String], Boolean) = {
    import PassengerType._

    val comment: Option[String] = (qualityDelta, computedQuality, ctx.paxType, ctx.isLongHaul) match {
      // TERRIBLE SERVICE (qualityDelta < -25)
      case (d, _, TOURIST, false) if d < -25 => pick(
        s"Short flight but ${ctx.airlineName} managed to ruin our entire vacation.",
        "I've gotten better service from a vending machine.",
        "My cat is friendlier than these flight attendants, and my cat hates everyone.",
        s"If ${ctx.airlineName} put this much effort into being rude at a desk job, they'd be HR directors by now.",
        "I paid to be ignored, jostled, and served a warm soda with the enthusiasm of someone disposing of evidence."
      )
      case (d, _, BUSINESS, _) if d < -25 => pick(
        "Probably the worst flight I have ever taken. Completely unprofessional.",
        s"No WiFi, horrible service, coffee was like moldy drain water.",
        s"Arriving in ${ctx.destName} stressed instead of prepared. Thanks for nothing.",
        s"The flight attendant sighed at me. Just sighed. Like I personally ruined their day by existing on their aircraft.",
        s"I've had better service at a DMV in ${ctx.originName}. And that's a statement I never thought I'd make."
      )
      case (d, _, ELITE, _) if d < -25 => pick(
        s"I'm friends with ${ctx.airlineName}'s CEO, and I'm still suing over the horrible service.",
        s"Yuck yuck yuck! ${ctx.airlineName} is the worst.",
        "When I say champagne I mean champagne, not sparkling dishwater in a flute!",
        s"I've been insulted in fourteen countries. ${ctx.airlineName} just made the list.",
        "The service was so bad I briefly considered apologizing to the crew for witnessing it."
      )
      case (d, _, _, true) if d < -25 => pick(
        s"Why do I remember that the flight to ${ctx.destName} was ${ctx.distance} km? Because with the slop ${ctx.airlineName} was serving us, the ensuing projectile situation, the overflowing toilets, and the screaming flight attendants, I remember every single kilometer.",
        s"Long hauls are tough. ${ctx.airlineName}'s are a veritable nightmare of interminable bad service.",
        s"${ctx.distance} km is a long time to feel forgotten. ${ctx.airlineName} made sure we didn't feel forgotten — we felt actively resented.",
        "By hour eight the crew had stopped making eye contact. By hour eleven so had I."
      )
      case (d, _, _, _) if d < -25 => pick(
        "Never again. Striking incompetence at every turn.",
        "Taking this flight was the worst decision I've made, and I've been to jail five times.",
        s"I've seen better organized chaos at a toddler birthday party. At least the toddlers try.",
        "They ran out of everything. Water. Snacks. Goodwill. All gone within the first hour."
      )

      // BAD SERVICE (qualityDelta < -10)
      case (d, _, TOURIST, false) if d < -10 => pick(
        "You wouldn't think things could go so wrong on such a short flight!",
        s"At least ${ctx.destName} will be fun. This flight certainly wasn't.",
        "Short flight, long face. That's the ${ctx.airlineName} guarantee apparently.",
        s"The crew looked at me like I was a rat who'd wandered into the cabin. Maybe I had. I honestly don't know anymore."
      )
      case (d, _, BUSINESS, _) if d < -10 => pick(
        s"Broken WiFi and ${ctx.airlineName} wouldn't give me a refund. Unacceptable.",
        s"Had to reschedule my ${ctx.destName} meeting because the WiFi was broken.",
        "The service quality matched my hopes: technically present but functionally useless.",
        s"Business class service at economy effort. ${ctx.airlineName} is truly innovating in disappointment."
      )
      case (d, _, ELITE, _) if d < -10 => pick(
        "The seats were upholstered in a fabric that felt like a punishment for my many past sins.",
        "The service was underwhelming. Reminded me of my father's butler, who we fired.",
        "I tipped generously hoping for improvement. The next drink arrived colder and slower.",
        "At my loyalty tier, 'fine' is a four-letter word. And they served me fine."
      )
      case (d, _, _, true) if d < -10 => pick(
        "Long flight, bad service, short tempers all around.",
        s"${ctx.distance} km flight and every time they came around they spilled water on me!",
        "The entertainment system crashed twice. Just like my expectations.",
        "I've been on camping trips with better logistics. And I don't camp."
      )
      case (d, _, _, _) if d < -10 => pick(
        "I have low expectations, and I was still disappointed.",
        "The vibe was 'Soviet Dentist Waiting Room'. Grim.",
        s"${ctx.airlineName} lost my luggage.",
        "The snack was a single thin pretzel in a bag that was mostly air. The air was mediocre too."
      )

      // GOOD SERVICE (qualityDelta > 15)
      case (d, _, TOURIST, false) if d > 25 => pick(
        s"Even on this short hop to ${ctx.destName}, they treated us like royalty!",
        "The crew gave my kid wings! He's been 'flying' around the house for days.",
        "Short flight, big smiles all around!",
        "I didn't expect much on a 45-minute flight and they blew me away. Actual warm cookies."
      )
      case (d, _, TOURIST, true) if d > 25 => pick(
        s"${ctx.airlineName}'s amazing service was the beginning of an amazing vacation!",
        s"I arrived in ${ctx.destName} already feeling like I was on holiday. That's rare.",
        "The crew learned my name and my kids' names by hour two. They're magic."
      )
      case (d, _, BUSINESS, _) if d > 25 => pick(
        "Productive flight! Got everything done and still had time for a movie.",
        s"Arrived in ${ctx.destName} ready for my meetings. Exactly what I needed.",
        "The crew understood that sometimes you just need silence and coffee. Perfect.",
        "They proactively brought me a charger before my laptop died. Mind-reading. Or just good training.",
        s"I gave ${ctx.airlineName} five stars and then felt bad it wasn't six. There should be a six."
      )
      case (d, _, ELITE, _) if d > 25 => pick(
        s"${ctx.airlineName} does premium travel right!",
        s"${ctx.airlineName} made me feel like the royalty I am! ${ctx.destName}, I have arrived!",
        "Flawless. The word I use sparingly. Today I mean it.",
        "They anticipated every need before I felt it. This is either service excellence or surveillance."
      )
      case (d, _, _, true) if d > 25 => pick(
        "Long flight but it felt like a spa day at 35,000 feet.",
        "Because it was a long flight, they had extra surprises for us!",
        "The time zones hit hard, but the flight was heaven.",
        s"I barely noticed crossing nine time zones. The crew kept me blissfully distracted.",
        "I landed in better shape than I boarded. That should be illegal."
      )
      case (d, _, _, _) if d > 15 => pick(
        "Great experience! Wow everyone was so nice!",
        "Felt like they went the extra mile!",
        s"Wonderful service on the way to ${ctx.destName}.",
        "The kind of flight you actually tell people about."
      )

      // MODERATE SERVICE
      case (d, _, TOURIST, _) if d > 0 => pick(
        s"Acceptable flight to ${ctx.destName}."
      )
      case (d, _, BUSINESS, _) if d > 0 => pick(
        "Met expectations. Got my work done.",
        "Efficient and unmemorable."
      )
      case (d, _, ELITE, _) if d > 0 => pick(
        "It was good service, just meeting my expectations.",
        "Satisfactory. Nothing more, nothing less."
      )

      case _ => pick(
        "Standard service.",
        "Nothing notable."
      )
    }
    (comment, qualityDelta > 0)
  }

  // ============ AIRPLANE CONDITION COMMENTS ============
  private def generateAirplaneConditionComment(condition: Double, quality: Double, delta: Int, ctx: CommentContext)(implicit random: Random): (Option[String], Boolean) = {
    import PassengerType._

    val planeDelta = condition / 100 + quality / 5
    val comment: Option[String] = (condition, delta, ctx.paxType) match {
      // NEW PLANES (condition >= 85)
      case (c, d, TOURIST) if c >= 85 && d > 10 => pick(
        "Love that new airplane smell!",
        "The screens are crisp, the seats are clean, the future is now!",
        "This plane is so new the flight attendants hadn't found all the storage compartments yet.",
        "I had a whole new plane to myself. Well, and the other passengers. But emotionally, it was mine."
      )
      case (c, d, BUSINESS) if c >= 85 && d > 10 => pick(
        "New plane means new amenities. USB-C in the seat? About time!",
        "These modern planes are so quiet. Actually got work done!",
        "Fastest WiFi I've had at 35,000 feet. Faster than my office, honestly.",
        "New aircraft, new possibilities. I closed a deal mid-Atlantic and felt unstoppable."
      )
      case (c, d, ELITE) if c >= 85 && d > 10 => pick(
        "Finally, an airline investing in its fleet. Noticed and appreciated.",
        s"${ctx.airlineName} keeping the fleet fresh. This is why I'm loyal.",
        "The new suite configuration is divine. I could live here. I've considered it.",
        "Fresh aircraft, flawless finish. Even the headrests smelled like optimism."
      )

      // AGING FLEET (condition < 40)
      case (c, d, TOURIST) if c < 40 && d < -10 => pick(
        "Is it safe to fly with this old airplane? Asking for my life.",
        "The seat cushion has seen things. Many things. Too many things.",
        "The overhead bins had duct tape. DUCT TAPE.",
        "The tray table had names carved into it going back decades. I added mine.",
        "I'm pretty sure this plane was originally sold to a different airline in a different century.",
        "A rat ran across the aisle during boarding. The gate agent called it the 'co-pilot.' I'm still processing this.",
        "The seat pocket contained a very old boarding pass, a prayer card, and what appeared to be a rat nest. Cozy, in a concerning way."
      )
      case (c, d, BUSINESS) if c < 40 && d < -10 => pick(
        "The entertainment system is older than my intern.",
        "No power outlets on this antique. Laptop died mid-presentation prep.",
        "This plane belongs in a museum, not on a runway.",
        "The armrest wobbles. The tray table wobbles. At this point I suspect the wings wobble too.",
        "Nothing says 'we respect your time' like serving you in a cabin last renovated under a previous head of state.",
        "Spotted a rat in the galley. The flight attendant called him Gerald. Gerald has been here longer than the crew."
      )
      case (c, d, ELITE) if c < 40 && d < -10 => pick(
        "For these prices, I expect a plane built this century!",
        "A classic aircraft, but not in a good way.",
        "My country club has newer golf carts than this plane.",
        "The 'premium' cabin smelled like 1987. Not the good parts of 1987.",
        "I paid first-class prices to sit in what I can only describe as a flying antique shop.",
        "I was escorted to my seat by a rat. He wore the airline livery. His service was impeccable. Fire whoever did his performance review."
      )

      // CRITICAL CONDITION (condition < 20)
      case (c, d, _) if c < 20 && d < -20 => pick(
        "A panel fell off mid-flight! FELL. OFF.",
        "I swear I heard rattling the entire flight. The ENTIRE flight.",
        "The tray table was held on by hope and dreams. Mostly hope.",
        "This airplane should be retired. Immediately. Yesterday, actually.",
        "At one point something skittered under my seat that was decidedly not the beverage cart.",
        "A rat emerged from beneath the floor panel mid-flight, surveyed us all with quiet authority, and retreated. We understood the assignment.",
        "The pilot reassured us the rattling was normal. Then a second pilot came back to see what the rattling was. Invigorating."
      )

      // MODERATE AGING
      case (c, d, _) if c < 60 && d < 0 => pick(
        "This airplane has shown signs of age.",
        "The seats have definitely seen better days.",
        "Could use a refresh. The carpet tells a thousand stories.",
        "The IFE keeps freezing. I've restarted it four times. The movie is now mostly vibes.",
        "Not decrepit. Just... tired. I felt a kinship with this aircraft by the end.",
        "Something in the overhead compartment smelled like a small rodent had recently called it home. I chose not to investigate."
      )

      case _ => None
    }
    (comment, planeDelta > 1)
  }

  // ============ FREQUENCY COMMENTS ============
  private def generateFrequencyComment(frequency: Double, expectedFrequency: Double, duration: Int, ctx: CommentContext)(implicit random: Random): (Option[String], Boolean) = {
    import PassengerType._
    val delta = frequency - expectedFrequency

    val comment: Option[String] = (delta, duration, ctx.paxType, frequency) match {
      // LOW FREQUENCY - SHORT HAUL
      case (d, dur, BUSINESS, _) if d < -10 && dur < 120 => pick(
        s"Only one flight to ${ctx.destName} today? My meetings don't schedule themselves!",
        s"Had to wake up at 4am because ${ctx.airlineName} doesn't have an afternoon option.",
        "Need more flights on this route! My clients are waiting!",
        s"One departure a day means I spend the night in ${ctx.destName} for a 2-hour meeting. I've filed my grievances internally.",
        s"Chose ${ctx.airlineName} over the competitor because they had the only seat. Not a ringing endorsement of market competition."
      )
      case (d, dur, _, _) if d < -10 && dur < 120 => pick(
        s"Really wish flights to ${ctx.destName} ran more frequently!",
        "Missed the only flight. Ruined my whole day.",
        "One flight a day is not enough for this route!",
        s"Miss this one and you're camping in ${ctx.originName} overnight. I've done it. Once.",
        s"One flight a day to ${ctx.destName} is a scheduling choice I choose to call 'bold.'"
      )
      case (d, dur, BUSINESS, _) if d <= -7 && dur < 120 => pick(
        "I'd pay more if there was another flight option in the afternoon.",
        "The early bird schedule doesn't work for everyone.",
        s"Add more ${ctx.originName} to ${ctx.destName} flights, please!",
        s"The 6am departure is fine if you enjoy airports in the dark and eating a sad muffin alone."
      )
      case (d, dur, _, _) if d <= -7 && dur < 120 => pick(
        "I'd pay more if there was another flight daily!",
        "More frequency would make this route perfect.",
        "Limited flight options forced some inconvenient choices.",
        s"I rearranged my entire week around this one departure. ${ctx.airlineName} owes me a hug."
      )

      // HIGH FREQUENCY - SHORT HAUL
      case (d, dur, BUSINESS, f) if f >= 21 && d > 7 && dur < 120 => pick(
        "Love how frequently this runs! Missed my meeting? Just catch the next one!",
        s"Flights to ${ctx.destName} every hour. This is civilization!",
        s"${ctx.airlineName}'s high frequency makes this route work!",
        s"Hourly flights to ${ctx.destName}. I've started commuting. Don't tell my family."
      )
      case (d, dur, BUSINESS, f) if f >= 14 && d > 1 && dur < 120 => pick(
        s"${ctx.airlineName}'s flight suits my schedule and that's huge!",
        "Multiple daily options. Exactly what business travel needs.",
        s"Flexible timing to ${ctx.destName}. Much appreciated.",
        "Having options is the whole ballgame. This route delivers."
      )
      case (d, _, _, f) if f >= 14 && d > 1 => pick(
        "This flight fits my schedule perfectly.",
        "Convenient timing.",
        "Found a departure within 20 minutes of when I wanted to leave. Remarkable."
      )

      case _ => None
    }
    (comment, delta > 0)
  }

  // ============ FLIGHT DURATION COMMENTS ============
  private def generateFlightDurationComment(duration: Int, expectedDuration: Int, ctx: CommentContext)(implicit random: Random): (Option[String], Boolean) = {
    import PassengerType._
    val deltaRatio = (duration - expectedDuration).toDouble / expectedDuration

    val comment: Option[String] = (deltaRatio, ctx.paxType, ctx.linkClass) match {
      // FAST FLIGHTS
      case (d, BUSINESS, _) if d < -0.5 => pick(
        "My time is valuable and this fast flight delivers!",
        s"Shortest flight from ${ctx.originName} to ${ctx.destName}. Impressive!",
        "Quick flight means more time for meetings. Perfect.",
        "I checked my watch twice expecting more time to pass. It didn't. Efficient.",
        s"Blinked and we were over ${ctx.destName}. Booked the return already."
      )
      case (d, ELITE, FIRST) if d < -0.5 => pick(
        "Swift and luxurious. Exactly what premium travel should be.",
        s"We arrived in ${ctx.destName} before my champagne got warm!",
        "Speed AND comfort. Finally, an airline that understands.",
        "Short flight, long nap, life is excellent."
      )
      case (d, TOURIST, _) if d < -0.5 => pick(
        "Quick flight means more vacation time. Love it!",
        s"We landed in ${ctx.destName} so fast I thought the pilot took a shortcut through Canada.",
        "Kids didn't even finish a full movie. That's a miracle."
      )
      case (d, _, _) if d < -0.3 => pick(
        "Faster flight than expected, nice surprise.",
        "Speedy journey!",
        "Was still on my first coffee when we started descending. High praise."
      )

      // SLOW FLIGHTS
      case (d, BUSINESS, _) if d > 0.3 => pick(
        s"This route to ${ctx.destName} takes forever. Is there a shortcut?",
        "Lost productivity hours on this slow routing.",
        "Why does this take so long? Are we going via Antarctica?",
        "By the time we landed I'd aged visibly and reconsidered several career choices.",
        s"I arrived in ${ctx.destName} technically on the same day I left. Technically."
      )
      case (d, ELITE, _) if d > 0.5 => pick(
        s"There must be a faster way to ${ctx.destName}.",
        "Time is money, and this flight cost me both.",
        "I began composing my memoirs. Finished the first chapter somewhere over the ocean.",
        "Premium seats, coach-era routing. My patience departed somewhere over the Atlantic."
      )
      case (d, TOURIST, _) if d > 0.5 => pick(
        "Kids asked 'are we there yet' approximately 847 times.",
        s"I watched three movies, took a nap, read a book, and we were still over the ocean.",
        "At some point I stopped counting time zones and just started grieving.",
        "The flight was so long we made friends, had a falling out, and reconciled before landing."
      )
      case (d, _, _) if d > 0.3 => pick(
        "Flight took longer than expected.",
        "Slow routing today.",
        "Could have been quicker.",
        "At hour three I started wondering if the plane was sightseeing."
      )

      case _ => None
    }
    (comment, deltaRatio < 0)
  }

  // ============ LOUNGE COMMENTS ============
  private def generateLoungeComment(requirement: Int, airport: Airport, airlineId: Int, allianceId: Option[Int], ctx: CommentContext)(implicit random: Random): (Option[String], Boolean) = {
    import PassengerType._
    val loungeOption = airport.getLounge(airlineId, allianceId, activeOnly = true)
    val loungeLevel = loungeOption.map(_.level).getOrElse(0)
    val adjustedReq = Math.max(1, Math.min(Lounge.MAX_LEVEL, (requirement + com.patson.Util.getBellRandom(0, Lounge.MAX_LEVEL, Some(random.nextInt()))).toInt))
    val delta = loungeLevel - adjustedReq

    val comment: Option[String] = (delta, ctx.paxType, loungeOption) match {
      // NO LOUNGE
      case (d, ELITE, None) if d < 0 => pick(
        s"No lounge at ${airport.displayText}? Had to sit with the commoners at the gate!",
        s"I specifically checked for lounge access. ${airport.displayText} has NONE. Barbaric.",
        "Without a lounge, I had to buy my own overpriced airport coffee like a peasant.",
        s"${airport.displayText} needs a lounge. I need a lounge. We all need a lounge.",
        s"No lounge. No refuge. Just me, a plastic seat, and a rat eating someone's abandoned croissant three gates down.",
        s"I asked the gate agent where the lounge was. She laughed. Long after she'd stopped I was still standing there. Waiting for a different answer."
      )
      case (d, BUSINESS, None) if d < 0 => pick(
        s"No lounge at ${airport.displayText}. Had to work at a noisy gate. Not ideal.",
        "A lounge would really help with pre-flight prep. Just saying.",
        s"${airport.displayText} could use a quiet space for business travelers.",
        s"Took a conference call from the gate at ${airport.displayText}. The announcements did not respect my professionalism.",
        "Ate a $22 sandwich at the gate like an animal. I have a title."
      )
      case (d, _, None) if d < 0 => pick(
        s"I am disappointed with the lack of lounge at ${airport.displayText}.",
        "No lounge access. Had to entertain myself with airport people-watching.",
        s"A lounge at ${airport.displayText} would be nice.",
        s"Spent the layover at ${airport.displayText} communing with strangers and fluorescent lighting.",
        "I cuddled a rat for warmth in the terminal. He was soft. He was judgmental. He was right to be."
      )

      // LOUNGE EXISTS BUT NOT GOOD ENOUGH
      case (d, ELITE, Some(lounge)) if d < -2 => pick(
        s"The lounge at ${airport.displayText} is beneath my standards.",
        s"Meal service at ${lounge.name} consisted of peanuts. Literally peanuts.",
        s"${lounge.airline.name} calls this a premium lounge?",
        s"${lounge.name} was full of people who appeared to be eating soup directly from sadness.",
        s"I've been to better lounges in smaller airports in colder countries at 6am. That's the bar. ${lounge.name} didn't clear it.",
        s"The lounge felt like it was decorated by someone who'd read about luxury once in an airport novel."
      )
      case (d, BUSINESS, Some(lounge)) if d < -1 => pick(
        s"${lounge.airline.name}'s lounge at ${airport.displayText} was crowded. Couldn't find a quiet corner.",
        s"WiFi at ${lounge.name} was slower than my patience.",
        "Lounge food was airplane food. Defeats the purpose.",
        s"The ${lounge.name} had one power outlet for forty people. A man was guarding it.",
        "Hot food ran out. Cold food was unspeakable. The bar had a queue. I sat in existential silence.",
        s"I spotted a rat in the ${lounge.name} buffet. Not near the food. In the food. He was served before me."
      )
      case (d, _, Some(lounge)) if d < 0 => pick(
        s"${lounge.name} from ${lounge.airline.name} was very... okay.",
        s"Was expecting a better lounge at ${airport.displayText}. One more knock against ${lounge.airline.name}.",
        s"The ${lounge.name} gave off strong 'upgraded airport waiting room' energy.",
        s"Fine lounge. Nothing to write home about. I am writing home about it. To document the disappointment."
      )

      // GREAT LOUNGE
      case (d, ELITE, Some(lounge)) if d >= 2 => pick(
        s"${lounge.name} is divine! The spa treatments alone were worth the ticket!",
        "Five-star lounge experience. Top shelf whiskey delivered poolside. Yes, there was a pool.",
        s"Best lounge between ${ctx.originName} and ${ctx.destName}. ${lounge.airline.name} knows luxury.",
        s"I missed my first flight on purpose just to stay in ${lounge.name} longer. Zero regrets, maximum class.",
        s"${lounge.name} is so good it briefly made me forget I had a destination. I was already there."
      )
      case (d, BUSINESS, Some(lounge)) if d >= 1 => pick(
        s"Great lounge at ${airport.displayText}! Got all my emails done in peace.",
        s"${lounge.name} has excellent coffee and faster WiFi than my office.",
        s"The ${lounge.name} made the layover actually enjoyable. Productive even!",
        s"Two hours in ${lounge.name} and I boarded the plane calmer than I've been all quarter.",
        s"Hot food, fast WiFi, real glasses. ${lounge.name} understands what traveling humans need.",
        s"The ${lounge.name} bartender remembered my order from my last connection. I don't know how. I'm choosing to be moved."
      )
      case (d, _, Some(lounge)) if d >= 0 => pick(
        "The excellent lounge was a pleasant surprise!",
        s"${lounge.name} exceeded expectations. Set them low, received something nice.",
        s"Perfectly adequate lounge. Infinitely better than the gate. Low bar, warmly cleared."
      )

      case _ => None
    }
    (comment, delta >= 0)
  }

  // Helper to pick a random comment
  private def pick(comments: String*)(implicit random: Random): Option[String] = {
    if (comments.isEmpty) None
    else Some(comments(random.nextInt(comments.length)))
  }
}

object LinkCommentGroup extends Enumeration {
  type LinkCommentGroup = Value
  val PRICE, LOYALTY, QUALITY, DURATION, FREQUENCY, LOUNGE, OTHER = Value
}

object LinkCommentType extends Enumeration {
  type LinkCommentType = Value
  val PRICE, LOYALTY, RAW_QUALITY, SERVICE_QUALITY, AIRPLANE_CONDITION, FREQUENCY, FLIGHT_DURATION, LOUNGE = Value
}