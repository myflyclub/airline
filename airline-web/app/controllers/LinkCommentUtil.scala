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
        LOUNGE -> preference.loungeAdjustRatio(link, preference.loungeLevelRequired, linkClass)
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
            case QUALITY => generateCommentsForQuality(link.computedQuality, link.rawQuality, airline.getCurrentServiceQuality(), link.getAssignedAirplanes().keys.toList, homeAirport.expectedQuality(link.distance, linkClass), paxType, link.distance)
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
    List(LinkComment.priceComment(ratio, expectedRatio)).flatten
  }

  def generateCommentsForLoyalty(ratio : Double)(implicit random : Random) = {
    val expectedRatio = com.patson.Util.getBellRandom(1, 0.4, Some(random.nextInt()))
    List(LinkComment.loyaltyComment(ratio, expectedRatio)).flatten
  }

  def generateCommentsForQuality(computedQuality: Int, rawQuality : Int, serviceQuality : Double, airplanes : List[Airplane], expectedQuality : Int, paxType: PassengerType.Value, distance : Int)(implicit random : Random) = {
    val qualityDelta = computedQuality - expectedQuality
    List(
      generateCommentForRawQuality(rawQuality, qualityDelta, distance), //per route
      generateCommentForQuality(qualityDelta, computedQuality, distance),
      generateCommentForAirplaneCondition(airplanes, qualityDelta)).flatten
  }

  def generateCommentForRawQuality(rawQuality : Int, qualityDelta : Int, distance : Int)(implicit random : Random) : List[LinkComment] = {
    List(LinkComment.rawQualityComment(rawQuality, qualityDelta, distance)).flatten
  }

  def generateCommentForQuality(qualityDelta : Int, computedQuality : Int, distance : Int)(implicit random : Random) : List[LinkComment] = {
    List(LinkComment.qualityComment(qualityDelta, computedQuality, distance)).flatten
  }

  def generateCommentForAirplaneCondition(airplanes : List[Airplane], qualityDelta : Int)(implicit random : Random) = {
    val pickedAirplane = airplanes(Random.nextInt(airplanes.length))
    List(LinkComment.airplaneConditionComment(pickedAirplane.condition, pickedAirplane.model.quality, qualityDelta)).flatten
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

    List(LinkComment.frequencyComment(frequency, adjustedExceptedFrequency, flightDuration)).flatten
  }

  def generateCommentsForFlightDuration(flightDuration : Int, expectedDuration : Int)(implicit random : Random) = {
    val adjustedExpectedDuration = (expectedDuration * com.patson.Util.getBellRandom(1, 0.7, Some(random.nextInt()))).toInt

    List(LinkComment.flightDurationComment(flightDuration, adjustedExpectedDuration)).flatten
   }

  def generateCommentsForLounge(loungeRequirement: Int, fromAirport : Airport, toAirport : Airport, airlineId : Int, allianceIdOption : Option[Int])(implicit random : Random) = {
    List(
      LinkComment.loungeComment(loungeRequirement, fromAirport, airlineId, allianceIdOption),
      LinkComment.loungeComment(loungeRequirement, toAirport, airlineId, allianceIdOption)).flatten
  }
}

case class LinkCommentSummary(comments : List[LinkComment], sampleSize : Int)
case class LinkComment(description : String, category : LinkCommentType.Value, positive : Boolean)

object LinkComment {
  val priceComment = (priceRatio : Double, expectationRatio : Double) => {
    val priceDeltaRatio = priceRatio - expectationRatio
    val comment =
      if (priceDeltaRatio < -0.7) {
        Some("Wow! This ticket is a steal!")
      } else if (priceDeltaRatio < -0.5) {
        Some("Such a money saver!")
      } else if (priceDeltaRatio < -0.3) {
        Some("The ticket price is very reasonable.")
      } else if (priceDeltaRatio < 0) {
        Some("The ticket price is quite reasonable.")
      } else if (priceDeltaRatio < 0.2) {
        Some("This ticket is not cheap.")
      } else if (priceDeltaRatio < 0.4) {
        Some("The ticket is expensive.")
      } else if (priceDeltaRatio < 0.6) {
        Some("The ticket is very expensive!")
      } else {
        Some("Insane! This is highway robbery!")
      }
    comment.map { comment =>
      LinkComment(comment, LinkCommentType.PRICE, priceDeltaRatio < 0)
    }
  }

  val loyaltyComment = (ratio : Double, expectedRatio : Double) => {
    val ratioDelta = ratio - expectedRatio

    val comment =
      if (ratioDelta < -0.2) {
        Some("I would never travel with any airline other than yours!")
      } else if (ratioDelta < -0.1) {
        Some("I am a fan of your airline!")
      } else if (ratioDelta < 0) {
        Some("I have heard some nice things about your airline.")
      } else if (ratioDelta < 0.2) {
        Some("I am not really a fan of your airline.")
      } else  {
        Some("I would rather travel with other airlines!")
      }
    comment.map { comment =>
      LinkComment(comment, LinkCommentType.LOYALTY, ratioDelta < 0)
    }
  }

  val rawQualityComment = (rawQuality: Int, qualityDelta: Int, distance: Int) => {
    val random = Random.nextInt(3)
    val comment = rawQuality match {
      case x if x <= 20 =>
        if (qualityDelta < -10) {
          if (random == 0) {
            Some("They had ads in the toilet!")
          } else if (random == 1) {
            Some("Fees fees and more fees!")
          } else {
            Some("They charged me to bring my purse!")
          }
        } else if (qualityDelta > 10) {
          if (random == 0) {
            Some("Fees on everything but great service otherwise!")
          } else if (random == 1) {
            Some("Bought a snack box and it was great!")
          } else {
            Some("Great service ")
          }
        } else {
          None
        }
      case x if x <= 40 =>
        Some("Great onboard service but everything else was terrible!")
      case x if x <= 60 =>
        Some("Great onboard service but everything else was terrible!")
      case x if x <= 80 =>
        if (qualityDelta < -10) {
          if (random == 0) {
            Some("Great onboard service but everything else was terrible!")
          } else {
            Some("Needed those unlimited drinks with how terrible everything else was!")
          }
        } else if (qualityDelta > 15) {
          if (random == 0) {
            Some("Loved the unlimited caviar")
          } else if (random == 1) {
            Some("Wonderful entertainment options!")
          } else {
            Some("I slept so well!")
          }
        } else {
          None
        }
      case x if x <= 100 =>
        if (qualityDelta < -10) {
          if (random == 0) {
            Some("Great onboard service but everything else was terrible!")
          } else {
            Some("Needed those unlimited drinks with how terrible everything else was!")
          }
        } else if (qualityDelta > 15) {
          if (random == 0) {
            Some("Loved the unlimited caviar")
          } else if (random == 1) {
            Some("Wonderful entertainment options!")
          } else {
            Some("I slept so well!")
          }
        } else {
          None
        }
      case _ =>
        None
    }
    comment.map { comment =>
      LinkComment(comment, LinkCommentType.RAW_QUALITY, qualityDelta > 0)
    }
  }



  val qualityComment = (qualityDelta : Double, computedQuality : Int, distance : Int) => {
    val random = Random.nextInt(3)
    val comment = computedQuality match {
      case x if x < 22 =>
        if (qualityDelta < -25) {
          if (distance < 800) {
            Some("Short flight but a very long list of horrible experiences. Horrible!")
          } else if (random == 0) {
            Some("Probably the worst flight I have ever taken.")
          } else {
            Some("Never again. Striking incompetence at every turn.")
          }
        } else if (qualityDelta < -10) {
          if (distance < 800) {
            Some("You wouldn't think things could go so wrong on such a short flight!")
          } else if (random == 0) {
            Some("I have low expectations, and I have to say this was still just terrible.")
          } else if (random == 1) {
            Some("The toilet was overflowing!")
          } else {
            Some("They lost my luggage.")
          }
        } else if (qualityDelta > 15) {
          Some("I'm happy with the basics!")
        } else {
          Some("No service and that's fine.")
        }
      case x if x < 44 =>
        if (qualityDelta < -25) {
          if (random == 0) {
            Some("Maybe I have high expectations, but I have to say it was horrible!")
          } else {
            Some("I expect very good service and this wasn't it that's for sure!")
          }
        } else if (qualityDelta < -10) {
          if (random == 0) {
            Some("You call this food?! Wouldn't serve it to my rats!")
          } else if (random == 1) {
            Some("Broken WiFi and wouldn't give me refund.")
          } else {
            Some("You call this legroom?!")
          }
        } else if (qualityDelta > 15) {
          if (distance < 800) {
            if (random == 0) {
              Some("Short and sweet flight!")
            } else if (random == 1) {
              Some("Even on the short flight the professionalism shone through!")
            } else {
              None
            }
          } else if (distance > 6000) {
            if (random == 0) {
              Some("Long flight but it felt short!")
            } else if (random == 1) {
              Some("Great experience!")
            } else {
              None
            }
          } else {
            if (random == 0) {
              Some("Wow everyone was so nice!")
            } else if (random == 1) {
              Some("Felt like they went the extra mile!")
            } else {
              None
            }
          }
        } else {
          if (random == 0) {
            Some("Met expectations.")
          } else {
            None
          }
        }
      case x if x > 70 =>
        if (qualityDelta < -25) {
          if (random == 0) {
            Some("Yuck yuck yuck! An insult to premium travel! Thanks for ruining my trip!")
          } else {
            Some("I'm going to personally sue the pilot, the flight attendant, their managers, that whole *$%&@ airline – when I say champagne I mean champagne not $%&@$&$ sparking wine!!!!")
          }
        } else if (qualityDelta < -10) {
          if (random == 0) {
            Some("I was expecting something better.")
          } else {
            Some("The meal service was underwhelming.")
          }
        } else if (qualityDelta > 30) {
          if (random == 0) {
            Some("I've never experienced such good service!")
          } else if (random == 1) {
            Some("They made me feel like a king!")
          } else {
            Some("More luxury than I know what do with!")
          }
        } else if (qualityDelta > 15) {
          if (distance < 800) {
            if (random == 0) {
              Some("Great attention to detail!")
            } else if (random == 1) {
              Some("Even on the short flight the professionalism shone through!")
            } else {
              Some("Felt premium!")
            }
          } else if (distance > 6000) {
            if (random == 0) {
              Some("Because it was a long flight, they had pet rats for everyone to cuddle with!")
            } else if (random == 1) {
              Some("They helped me make my connecting flight – was a big help after the long flight!")
            } else {
              Some("High quality service!")
            }
          } else {
            if (random == 0) {
              Some("High quality service!")
            } else if (random == 1) {
              Some("Felt like they went the extra mile!")
            } else {
              Some("Great attention to detail!")
            }
          }
        } else {
          if (random == 0) {
            Some("It was good service, just meeting my high expectations.")
          } else {
            None
          }
        }
      case _ => // 44 - 70
        if (qualityDelta < -15) {
          if (random == 0) {
            Some("Ooof that was an uncomfortable flight!")
          } else if (random == 1) {
            Some("Broken WiFi and wouldn't give me refund.")
          } else {
            Some("You call this legroom?!")
          }
        } else if (qualityDelta > 15) {
          if (distance < 800) {
            if (random == 0) {
              Some("They had snacks even on the short flight!")
            } else if (random == 1) {
              Some("Even on the short flight the professionalism shone through!")
            } else {
              None
            }
          } else if (distance > 6000) {
            if (random == 0) {
              Some("Long flight but felt short!")
            } else if (random == 1) {
              Some("Great experience!")
            } else {
              None
            }
          } else {
            if (random == 0) {
              Some("Wow everyone was so nice!")
            } else if (random == 1) {
              Some("Felt like they went the extra mile!")
            } else {
              None
            }
          }
        } else {
          if (random == 0) {
            Some("Met expectations.")
          } else {
            None
          }
        }


    }
    comment.map { comment =>
      LinkComment(comment, LinkCommentType.SERVICE_QUALITY, qualityDelta > 0)
    }

  }

  val airplaneConditionComment = (airplaneCondition : Double, planeQuality : Double, qualityDelta : Int) => {
    val planeDelta = airplaneCondition / 100 + planeQuality / 5
    val comment =
      if (airplaneCondition >= 85 && qualityDelta > 10) {
        Some("Love that fresh new airplane smell!")
      } else if (airplaneCondition < 20 && qualityDelta < -20) {
        Some("A bulhead collapsed while we were flying! Worst travel experience of my life!")
      } else if (airplaneCondition < 40 && qualityDelta < -10) {
        Some("Is it safe to fly with this old airplane?")
      } else if (airplaneCondition < 60 && qualityDelta < 0) {
        Some("This airplane has shown signs of age.")
      } else {
        None
      }

    comment.map { comment =>
      LinkComment(comment, LinkCommentType.AIRPLANE_CONDITION, planeDelta > 1)
    }
  }

  val frequencyComment = (frequency : Double, expectedFrequency : Double, duration : Int) => {
    val delta = frequency - expectedFrequency
    val comment =
      if (delta < -10 && duration < 120) {
        Some("Really wish this flight ran much much more frequently!")
      } else if (delta <= - 7 && duration < 120) {
        Some("I'd pay more if there was another flight daily!")
      } else if (frequency >= 14 && duration < 120 && delta > 1) {
        Some("This flight suits my schedule and that's huge!")
      } else if (frequency >= 21 && delta > 7 && duration < 120) {
        Some("Love how frequently this runs!")
      } else if (frequency >= 14 && delta > 1) {
        Some("This flight fits my schedule.")
      } else {
        None
      }
    comment.map { comment =>
      LinkComment(comment, LinkCommentType.FREQUENCY, delta > 0)
    }
  }

  val flightDurationComment = (duration : Int, expectedDuration : Int) => {
    val deltaRatio = (duration - expectedDuration).toDouble / expectedDuration
    val comment =
      if (deltaRatio < -0.5) {
        Some("My time is valuable and I pay extra for speed!")
      } else {
        None
      }
    comment.map { comment =>
      LinkComment(comment, LinkCommentType.FLIGHT_DURATION, deltaRatio < 0)
    }
  }

  def loungeComment(loungeRequirement: Int, airport : Airport, airlineId : Int, allianceIdOption : Option[Int])(implicit random : Random) = {
    val loungeOption = airport.getLounge(airlineId, allianceIdOption, activeOnly = true)
    val loungeLevel = loungeOption.map(_.level).getOrElse(0)
    val adjustedLoungeRequirement = Math.max(1, Math.min(Lounge.MAX_LEVEL, (loungeRequirement + com.patson.Util.getBellRandom(0, Lounge.MAX_LEVEL, Some(random.nextInt()))).toInt))
    val delta = loungeLevel - adjustedLoungeRequirement
    val comment =
      if (delta < 0) { //does not fulfill the req
        loungeOption match {
          case None => s"I am disappointed with the lack of lounge at ${airport.displayText}"
          case Some(lounge) => s"The lounge at ${airport.displayText} from ${lounge.airline.name} does not meet my expectation"
        }
      } else {
        s"I am satisfied with the lounge service at ${airport.displayText} from ${loungeOption.get.airline.name}"
      }
    List(LinkComment(comment, LinkCommentType.LOUNGE, delta >= 0))
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

