package controllers

import com.patson.DemandGenerator
import com.patson.model.airplane.Airplane
import com.patson.model.{FlightPreferenceType, _}
import com.patson.util.AirportCache

import scala.collection.mutable.ListBuffer
import scala.collection.{MapView, immutable, mutable}
import scala.util.Random

object LinkCommentUtil {
  val SIMULATE_AIRPORT_COUNT = 5

  def simulateComments(consumptionEntries : List[LinkConsumptionHistory], airline : Airline, link : Link) : Map[(LinkClass, FlightPreferenceType.Value, PassengerType.Value), LinkCommentSummary] = {
    val random = new Random(airline.id + link.id) //need a steady generator

    val topConsumptionEntriesByHomeAirport : List[(Int, List[LinkConsumptionHistory])] = consumptionEntries.groupBy(_.homeAirport.id).toList.sortBy(_._2.map(_.passengerCount).sum).takeRight(SIMULATE_AIRPORT_COUNT)
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
            result.getOrElseUpdate((consumption.preferredLinkClass, consumption.preferenceType, consumption.passengerType), ListBuffer()).appendAll(generateCommentsPerConsumption(preferences, consumption, homeAirport, airline, link, random))
          }
        }

      }
    }

    val sampleSizeGrouping : MapView[(LinkClass, FlightPreferenceType.Value, PassengerType.Value), Int] = topConsumptionEntriesByHomeAirport.flatMap(_._2).groupBy(entry => (entry.preferredLinkClass, entry.preferenceType, entry.passengerType)).view.mapValues(_.map(_.passengerCount).sum)
    result.map {
      case(key, comments) =>
        val sampleSize = sampleSizeGrouping(key)
        (key, LinkCommentSummary(comments.toList, sampleSize))
    }.toMap

  }

  val MAX_SAMPLE_SIZE_PER_CONSUMPTION = 20

  case class CommentWeight(commentGroup : LinkCommentGroup.Value, weight : Int, adjustRatio : Double)
  case class CommentWeightedPool(weights : List[CommentWeight]) {
    val totalWeights = weights.map(_.weight).sum
    var weightMarkerWalker = 0
    val weightMarkers = weights.map { weight =>
      weightMarkerWalker = weightMarkerWalker + weight.weight
      (weight, weightMarkerWalker)
    }
    def drawCommentWeight(random : Random): Option[CommentWeight] = {
      if (totalWeights == 0) { //possible if all preference are close to neutral
        None
      } else {
        val target = random.nextInt(totalWeights)
        weightMarkers.find(weightMarker => weightMarker._2 >= target).map(_._1)
      }
    }
  }

  def generateCommentsPerConsumption(preferences : List[FlightPreference], consumption : LinkConsumptionHistory, homeAirport : Airport, airline : Airline, link : Link, random: Random) = {
    implicit val randomImplicit : Random = random
    //pricing
    val linkClass = consumption.linkClass
    val paxType = consumption.passengerType
    val preferredLinkClass = consumption.preferredLinkClass
    val sampleSize = Math.min(MAX_SAMPLE_SIZE_PER_CONSUMPTION, consumption.passengerCount)
    val allComments = ListBuffer[LinkComment]()
    val standardDuration = Computation.computeStandardFlightDuration(link.distance)

    import LinkCommentGroup._
    val poolByPreference : Map[FlightPreference, CommentWeightedPool] = preferences.map { preference =>
      val adjustRatioByGroup : Map[controllers.LinkCommentGroup.Value, Double] = Map(
        PRICE -> preference.priceAdjustRatio(link, linkClass, paxType),
        LOYALTY -> preference.loyaltyAdjustRatio(link),
        QUALITY -> preference.qualityAdjustRatio(homeAirport, link, preferredLinkClass, paxType),
        DURATION -> preference.tripDurationAdjustRatio(link, preferredLinkClass, paxType),
        FREQUENCY -> preference.frequencyAdjustRatio(link, preferredLinkClass, paxType),
        //need to redo all this to support non-ratios...
//        LOUNGE -> preference.loungeAdjust(consumption.link.cost, link, preference.loungeLevelRequired, linkClass)
      )

      val pool = CommentWeightedPool(adjustRatioByGroup.map {
        case((group, ratio)) =>  CommentWeight(group, Math.abs(((1 - ratio) * 100).toInt), ratio)
      }.toList)

      (preference, pool)
    }.toMap

    val satisfactionDeviation = Math.abs(consumption.satisfaction - 0.5)
    val commentGenerationCount = if (satisfactionDeviation < 0.2) 1 else if (satisfactionDeviation < 0.3) 2 else 3
    for (i <- 0 until sampleSize) {
      val preference = preferences(random.nextInt(preferences.length))
      val commentsOfThisSample = ListBuffer[LinkComment]()
      for (j <- 0 until commentGenerationCount) {
        val commentWeight = poolByPreference(preference).drawCommentWeight(random)
//        println(s"${consumption.preferenceType} : $commentWeight")
        commentWeight.foreach { weight =>
          val comments = weight.commentGroup match {
            case PRICE => generateCommentsForPrice(weight.adjustRatio)
            case LOYALTY => generateCommentsForLoyalty(weight.adjustRatio)
            case QUALITY => generateCommentsForQuality(link.computedQuality(), link.rawQuality, airline.getCurrentServiceQuality(), link.getAssignedAirplanes().keys.toList, homeAirport.expectedQuality(link.distance, linkClass), paxType, link.distance)
            case DURATION => generateCommentsForFlightDuration(link.duration, standardDuration)
            case FREQUENCY =>generateCommentsForFlightFrequency(link.duration, link.frequency, preference.frequencyThreshold)
            case LOUNGE => generateCommentsForLounge(preference.loungeLevelRequired, link.from, link.to, airline.id, airline.getAllianceId())
            case _ => List.empty

          }
          comments.foreach { comment =>
            if (!commentsOfThisSample.map(_.category).contains(comment.category)) { //do not add the same category twice
              commentsOfThisSample.append(comment)
            }
          }
        }
      }
      allComments.appendAll(commentsOfThisSample)

    }
    allComments
  }

  def generateCommentsForPrice(ratio : Double)(implicit random : Random) = {
    val expectedRatio = com.patson.Util.getBellRandom(1, 0.4, Some(random.nextInt()))
    List(LinkComment(CommentCategory.Price, ratio, expectedRatio)).flatten
  }

  def generateCommentsForLoyalty(ratio : Double)(implicit random : Random) = {
    val expectedRatio = com.patson.Util.getBellRandom(1, 0.4, Some(random.nextInt()))
    List(LinkComment(CommentCategory.Loyalty, ratio, expectedRatio)).flatten
  }

  def generateCommentsForQuality(computedQuality: Int, rawQuality : Int, serviceQuality : Double, airplanes : List[Airplane], expectedQuality : Int, paxType: PassengerType.Value, distance : Int)(implicit random : Random) = {
    val qualityDelta = computedQuality - expectedQuality
    List(
      generateCommentForRawQuality(rawQuality, qualityDelta, distance), //per route
      generateCommentForQuality(qualityDelta, computedQuality, distance),
      generateCommentForAirplaneCondition(airplanes, qualityDelta)).flatten
  }

  def generateCommentForRawQuality(rawQuality : Int, qualityDelta : Int, distance : Int)(implicit random : Random) : List[LinkComment] = {
    List(LinkComment(CommentCategory.RawQuality, rawQuality, qualityDelta, distance)).flatten
  }

  def generateCommentForQuality(qualityDelta : Int, computedQuality : Int, distance : Int)(implicit random : Random) : List[LinkComment] = {
    List(LinkComment(CommentCategory.ServiceQuality, qualityDelta, computedQuality, distance)).flatten
  }

  def generateCommentForAirplaneCondition(airplanes : List[Airplane], qualityDelta : Int)(implicit random : Random) = {
    val pickedAirplane = airplanes(Random.nextInt(airplanes.length))
    List(LinkComment(CommentCategory.AirplaneCondition, pickedAirplane.condition, pickedAirplane.model.quality, qualityDelta)).flatten
  }

//  def generateCommentsForFrequency(frequency: Int, expectedFrequency : Int, passengerCount : Int, frequencySensitivity: Double)(implicit random : Random) = {
//    (0 until passengerCount).map { i =>
//      //waitDurationSensitivity/frequencySensitivity from 0.02 to 0.2
//      if (random.nextDouble() * 0.4 <= frequencySensitivity) {
//        val adjustedExpectation = (expectedFrequency + com.patson.Util.getBellRandom(0, 40, Some(random.nextInt()))).toInt
//        LinkComment.frequencyComment(frequency, adjustedExpectation)
//      } else {
//        List.empty
//      }
//    }.flatten
//  }

  def generateCommentsForFlightFrequency(flightDuration : Int, frequency: Int, expectedFrequency: Int)(implicit random: Random) = {
    val adjustedExceptedFrequency = (expectedFrequency * com.patson.Util.getBellRandom(1, 0.7, Some(random.nextInt()))).toInt

    List(LinkComment(CommentCategory.Frequency, frequency, adjustedExceptedFrequency, flightDuration)).flatten
  }

  def generateCommentsForFlightDuration(flightDuration : Int, expectedDuration : Int)(implicit random : Random) = {
    val adjustedExpectedDuration = (expectedDuration * com.patson.Util.getBellRandom(1, 0.7, Some(random.nextInt()))).toInt

    List(LinkComment(CommentCategory.FlightDuration, flightDuration, adjustedExpectedDuration)).flatten
   }

  def generateCommentsForLounge(loungeRequirement: Int, fromAirport : Airport, toAirport : Airport, airlineId : Int, allianceIdOption : Option[Int])(implicit random : Random) = {
    List(
      LinkComment(CommentCategory.Lounge, loungeRequirement, fromAirport, airlineId, allianceIdOption),
      LinkComment(CommentCategory.Lounge, loungeRequirement, toAirport, airlineId, allianceIdOption)).flatten
  }
}

case class LinkCommentSummary(comments : List[LinkComment], sampleSize : Int)
case class LinkComment(description : String, category : LinkCommentType.Value, positive : Boolean)

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
  def apply(category: CommentCategory, params: Any*)(implicit random: Random): Option[LinkComment] = {
    val (description, positive) = (category, params) match {
      case (CommentCategory.Price, Seq(priceRatio: Double, expectationRatio: Double)) => 
        generatePriceComment(priceRatio, expectationRatio)
      case (CommentCategory.Loyalty, Seq(ratio: Double, expectedRatio: Double)) => 
        generateLoyaltyComment(ratio, expectedRatio)
      case (CommentCategory.RawQuality, Seq(rawQuality: Int, qualityDelta: Int, distance: Int)) => 
        generateRawQualityComment(rawQuality, qualityDelta, distance)
      case (CommentCategory.ServiceQuality, Seq(qualityDelta: Double, computedQuality: Int, distance: Int)) => 
        generateServiceQualityComment(qualityDelta, computedQuality, distance)
      case (CommentCategory.AirplaneCondition, Seq(condition: Double, quality: Double, delta: Int)) => 
        generateAirplaneConditionComment(condition, quality, delta)
      case (CommentCategory.Frequency, Seq(frequency: Double, expectedFrequency: Double, duration: Int)) => 
        generateFrequencyComment(frequency, expectedFrequency, duration)
      case (CommentCategory.FlightDuration, Seq(duration: Int, expectedDuration: Int)) => 
        generateFlightDurationComment(duration, expectedDuration)
      case (CommentCategory.Lounge, Seq(requirement: Int, airport: Airport, airlineId: Int, allianceId: Option[Int])) => 
        generateLoungeComment(requirement, airport, airlineId, allianceId)
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

  private def generatePriceComment(priceRatio: Double, expectationRatio: Double): (Option[String], Boolean) = {
    val priceDeltaRatio = priceRatio - expectationRatio
    val comment = priceDeltaRatio match {
      case x if x < -0.7 => Some("Wow! This ticket is a steal!")
      case x if x < -0.5 => Some("Such a money saver!")
      case x if x < -0.3 => Some("The ticket price is very reasonable.")
      case x if x < 0 => Some("The ticket price is quite reasonable.")
      case x if x < 0.2 => Some("This ticket is not cheap.")
      case x if x < 0.4 => Some("The ticket is expensive.")
      case x if x < 0.6 => Some("The ticket is very expensive!")
      case _ => Some("Insane! This is highway robbery!")
    }
    (comment, priceDeltaRatio < 0)
  }

  private def generateLoyaltyComment(ratio: Double, expectedRatio: Double): (Option[String], Boolean) = {
    val ratioDelta = ratio - expectedRatio
    val comment = ratioDelta match {
      case x if x < -0.2 => Some("I would never travel with any airline other than yours!")
      case x if x < -0.1 => Some("I am a fan of your airline!")
      case x if x < 0 => Some("I have heard some nice things about your airline.")
      case x if x < 0.2 => Some("I am not really a fan of your airline.")
      case _ => Some("I would rather travel with other airlines!")
    }
    (comment, ratioDelta < 0)
  }

  private def generateRawQualityComment(rawQuality: Int, qualityDelta: Int, distance: Int)(implicit random: Random): (Option[String], Boolean) = {
    val comment = rawQuality match {
      case x if x <= 20 => generateLowQualityComment(qualityDelta)
      case x if x <= 40 => Some("Great onboard service but everything else was terrible!")
      case x if x <= 60 => Some("Great onboard service but everything else was terrible!")
      case x if x <= 80 => generateMediumQualityComment(qualityDelta)
      case x if x <= 100 => generateMediumQualityComment(qualityDelta)
      case _ => None
    }
    (comment, qualityDelta > 0)
  }

  private def generateLowQualityComment(qualityDelta: Int)(implicit random: Random): Option[String] = {
    if (qualityDelta < -10) {
      random.nextInt(3) match {
        case 0 => Some("They had ads in the toilet!")
        case 1 => Some("Fees fees and more fees!")
        case _ => Some("They charged me to bring my purse!")
      }
    } else if (qualityDelta > 10) {
      random.nextInt(3) match {
        case 0 => Some("Fees on everything but great service otherwise!")
        case 1 => Some("Bought a snack box and it was great!")
        case _ => Some("Great service")
      }
    } else None
  }

  private def generateMediumQualityComment(qualityDelta: Int)(implicit random: Random): Option[String] = {
    if (qualityDelta < -10) {
      random.nextInt(2) match {
        case 0 => Some("Great onboard service but everything else was terrible!")
        case _ => Some("Needed those unlimited drinks with how terrible everything else was!")
      }
    } else if (qualityDelta > 15) {
      random.nextInt(3) match {
        case 0 => Some("Loved the unlimited caviar")
        case 1 => Some("Wonderful entertainment options!")
        case _ => Some("I slept so well!")
      }
    } else None
  }

  private def generateServiceQualityComment(qualityDelta: Double, computedQuality: Int, distance: Int)(implicit random: Random): (Option[String], Boolean) = {
    val comment = computedQuality match {
      case x if x < 22 => generateLowServiceQualityComment(qualityDelta, distance)
      case x if x < 44 => generateMediumServiceQualityComment(qualityDelta, distance)
      case x if x > 70 => generateHighServiceQualityComment(qualityDelta, distance)
      case _ => generateStandardServiceQualityComment(qualityDelta, distance)
    }
    (comment, qualityDelta > 0)
  }

  private def generateLowServiceQualityComment(qualityDelta: Double, distance: Int)(implicit random: Random): Option[String] = {
    if (qualityDelta < -25) {
      if (distance < 800) Some("Short flight but a very long list of horrible experiences. Horrible!")
      else random.nextInt(2) match {
        case 0 => Some("Probably the worst flight I have ever taken.")
        case _ => Some("Never again. Striking incompetence at every turn.")
      }
    } else if (qualityDelta < -10) {
      if (distance < 800) Some("You wouldn't think things could go so wrong on such a short flight!")
      else random.nextInt(3) match {
        case 0 => Some("I have low expectations, and I have to say this was still just terrible.")
        case 1 => Some("The toilet was overflowing!")
        case _ => Some("They lost my luggage.")
      }
    } else if (qualityDelta > 15) Some("I'm happy with the basics!")
    else Some("No service and that's fine.")
  }

  private def generateMediumServiceQualityComment(qualityDelta: Double, distance: Int)(implicit random: Random): Option[String] = {
    if (qualityDelta < -25) {
      random.nextInt(2) match {
        case 0 => Some("Maybe I have high expectations, but I have to say it was horrible!")
        case _ => Some("I expect very good service and this wasn't it that's for sure!")
      }
    } else if (qualityDelta < -10) {
      random.nextInt(3) match {
        case 0 => Some("You call this food?! Wouldn't serve it to my rats!")
        case 1 => Some("Broken WiFi and wouldn't give me refund.")
        case _ => Some("You call this legroom?!")
      }
    } else if (qualityDelta > 15) {
      generatePositiveMediumServiceComment(distance)
    } else {
      random.nextInt(2) match {
        case 0 => Some("Met expectations.")
        case _ => None
      }
    }
  }

  private def generatePositiveMediumServiceComment(distance: Int)(implicit random: Random): Option[String] = {
    if (distance < 800) {
      random.nextInt(3) match {
        case 0 => Some("Short and sweet flight!")
        case 1 => Some("Even on the short flight the professionalism shone through!")
        case _ => None
      }
    } else if (distance > 6000) {
      random.nextInt(3) match {
        case 0 => Some("Long flight but it felt short!")
        case 1 => Some("Great experience!")
        case _ => None
      }
    } else {
      random.nextInt(3) match {
        case 0 => Some("Wow everyone was so nice!")
        case 1 => Some("Felt like they went the extra mile!")
        case _ => None
      }
    }
  }

  private def generateHighServiceQualityComment(qualityDelta: Double, distance: Int)(implicit random: Random): Option[String] = {
    if (qualityDelta < -25) {
      random.nextInt(2) match {
        case 0 => Some("Yuck yuck yuck! An insult to premium travel! Thanks for ruining my trip!")
        case _ => Some("I'm going to personally sue the pilot, the flight attendant, their managers, that whole *$%&@ airline – when I say champagne I mean champagne not $%&@$&$ sparking wine!!!!")
      }
    } else if (qualityDelta < -10) {
      random.nextInt(2) match {
        case 0 => Some("I was expecting something better.")
        case _ => Some("The meal service was underwhelming.")
      }
    } else if (qualityDelta > 30) {
      random.nextInt(3) match {
        case 0 => Some("I've never experienced such good service!")
        case 1 => Some("They made me feel like a king!")
        case _ => Some("More luxury than I know what do with!")
      }
    } else if (qualityDelta > 15) {
      generatePositiveHighServiceComment(distance)
    } else {
      random.nextInt(2) match {
        case 0 => Some("It was good service, just meeting my high expectations.")
        case _ => None
      }
    }
  }

  private def generatePositiveHighServiceComment(distance: Int)(implicit random: Random): Option[String] = {
    if (distance < 800) {
      random.nextInt(3) match {
        case 0 => Some("Great attention to detail!")
        case 1 => Some("Even on the short flight the professionalism shone through!")
        case _ => Some("Felt premium!")
      }
    } else if (distance > 6000) {
      random.nextInt(3) match {
        case 0 => Some("Because it was a long flight, they had pet rats for everyone to cuddle with!")
        case 1 => Some("They helped me make my connecting flight – was a big help after the long flight!")
        case _ => Some("High quality service!")
      }
    } else {
      random.nextInt(3) match {
        case 0 => Some("High quality service!")
        case 1 => Some("Felt like they went the extra mile!")
        case _ => Some("Great attention to detail!")
      }
    }
  }

  private def generateStandardServiceQualityComment(qualityDelta: Double, distance: Int)(implicit random: Random): Option[String] = {
    if (qualityDelta < -15) {
      random.nextInt(3) match {
        case 0 => Some("Ooof that was an uncomfortable flight!")
        case 1 => Some("Broken WiFi and wouldn't give me refund.")
        case _ => Some("You call this legroom?!")
      }
    } else if (qualityDelta > 15) {
      generatePositiveMediumServiceComment(distance)
    } else {
      random.nextInt(2) match {
        case 0 => Some("Met expectations.")
        case _ => None
      }
    }
  }

  private def generateAirplaneConditionComment(condition: Double, quality: Double, delta: Int): (Option[String], Boolean) = {
    val planeDelta = condition / 100 + quality / 5
    val comment = (condition, delta) match {
      case (c, d) if c >= 85 && d > 10 => Some("Love that fresh new airplane smell!")
      case (c, d) if c < 20 && d < -20 => Some("A bulhead collapsed while we were flying! Worst travel experience of my life!")
      case (c, d) if c < 40 && d < -10 => Some("Is it safe to fly with this old airplane?")
      case (c, d) if c < 60 && d < 0 => Some("This airplane has shown signs of age.")
      case _ => None
    }
    (comment, planeDelta > 1)
  }

  private def generateFrequencyComment(frequency: Double, expectedFrequency: Double, duration: Int): (Option[String], Boolean) = {
    val delta = frequency - expectedFrequency
    val comment = (delta, duration) match {
      case (d, dur) if d < -10 && dur < 120 => Some("Really wish this flight ran much much more frequently!")
      case (d, dur) if d <= -7 && dur < 120 => Some("I'd pay more if there was another flight daily!")
      case (d, dur) if frequency >= 14 && dur < 120 && d > 1 => Some("This flight suits my schedule and that's huge!")
      case (d, dur) if frequency >= 21 && d > 7 && dur < 120 => Some("Love how frequently this runs!")
      case (d, _) if frequency >= 14 && d > 1 => Some("This flight fits my schedule.")
      case _ => None
    }
    (comment, delta > 0)
  }

  private def generateFlightDurationComment(duration: Int, expectedDuration: Int): (Option[String], Boolean) = {
    val deltaRatio = (duration - expectedDuration).toDouble / expectedDuration
    val comment = if (deltaRatio < -0.5) Some("My time is valuable and I pay extra for speed!") else None
    (comment, deltaRatio < 0)
  }

  private def generateLoungeComment(requirement: Int, airport: Airport, airlineId: Int, allianceId: Option[Int])(implicit random: Random): (Option[String], Boolean) = {
    val loungeOption = airport.getLounge(airlineId, allianceId, activeOnly = true)
    val loungeLevel = loungeOption.map(_.level).getOrElse(0)
    val adjustedLoungeRequirement = Math.max(1, Math.min(Lounge.MAX_LEVEL, 
      (requirement + com.patson.Util.getBellRandom(0, Lounge.MAX_LEVEL, Some(random.nextInt()))).toInt))
    val delta = loungeLevel - adjustedLoungeRequirement
    
    val comment = if (delta < 0) {
      loungeOption match {
        case None => Some(s"I am disappointed with the lack of lounge at ${airport.displayText}")
        case Some(lounge) => Some(s"The lounge at ${airport.displayText} from ${lounge.airline.name} does not meet my expectation")
      }
    } else {
      Some(s"I am satisfied with the lounge service at ${airport.displayText} from ${loungeOption.get.airline.name}")
    }
    (comment, delta >= 0)
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

