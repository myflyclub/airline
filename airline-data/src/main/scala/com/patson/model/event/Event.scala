package com.patson.model.event

import com.patson.data.{AirlineSource, AirportSource, CycleSource, EventSource}
import com.patson.model._
import com.patson.util.AirportCache

import scala.collection.mutable

abstract class Event(val eventType : EventType.Value, val startCycle : Int, val duration : Int, var id : Int = 0) {
  val isActive = (currentCycle : Int) => startCycle + duration > currentCycle
}

case class Olympics(override val startCycle : Int, override val duration : Int = Period.yearLength * 4, var olympicsId : Int = 0) extends Event(EventType.OLYMPICS, startCycle, duration, olympicsId) {
  val currentYear: Int => Int = (currentCycle : Int) => {
    (currentCycle - startCycle) /  Period.yearLength + 1
  } //start from 1 to 4
  val isNewYear: Int => Boolean = (currentCycle : Int) => currentWeek(currentCycle) == 0
  val currentWeek: Int => Int = (currentCycle : Int) => (currentCycle - startCycle) % Period.yearLength //start from 0 to Period.yearLength

  import OlympicsStatus._
  val status: Int => Value = (currentCycle : Int) =>
    if (isActive(currentCycle)) {
      currentYear(currentCycle) match {
        case 1 => VOTING
        case 2 => HOST_CITY_SELECTED
        case 3 => PREPARATION
        case 4 =>
          val weeksBeforeGames = Period.yearLength - Olympics.GAMES_DURATION - currentWeek(currentCycle)
          if (weeksBeforeGames > 0) {
            OLYMPICS_YEAR
          } else {
            IN_PROGRESS
          }
        case _ => UNKNOWN
      }
    } else {
      CONCLUDED
    }
}

object OlympicsStatus extends Enumeration {
  type RewardCategory = Value
  val VOTING, HOST_CITY_SELECTED, PREPARATION, OLYMPICS_YEAR, IN_PROGRESS, CONCLUDED, UNKNOWN = Value
}



object Olympics {
  val TOOLTIP = List(
    "Olympics are scored the final year of each 4 year Olympic cycle, with more passengers traveling the very last 4 weeks.",
    "When it starts, you will be given a \"goal\" score based on how many passengers you recently transported.",
    "You get one point for every passenger you transport from origin to Olympics (regardless of type), and a partial proportional point if you fulfilled part of the journey.",
    "If you make your goal, you get a prize, and you get a bigger prize the more you over-deliver."
  )
  val GAMES_DURATION = 4
  def getCandidates(eventId : Int) : List[Airport] = {
    EventSource.loadOlympicsCandidates(eventId)
  }

  def getAirlineVotes(eventId : Int) : Map[Airline, OlympicsAirlineVote] = {
    EventSource.loadOlympicsAirlineVotes(eventId)
  }

  def getVoteRounds(eventId : Int) : List[OlympicsVoteRound] = {
    EventSource.loadOlympicsVoteRounds(eventId)
  }

  def getSelectedAirport(eventId : Int) : Option[Airport] = {
    val voteRounds = getVoteRounds(eventId : Int);
    if (voteRounds.isEmpty) {
      None
    } else {
      Some(voteRounds.last.votes.toList.sortBy(_._2).last._1)
    }
  }

  def getSelectedAffectedAirports(eventId : Int) : List[Airport] = {
    Olympics.getSelectedAirport(eventId) match {
      case Some(principalAirport) => Olympics.getAffectedAirport(eventId, principalAirport)
      case None => List.empty
    }
  }

  def getAffectedAirport(eventId : Int) : Map[Airport, List[Airport]] = {
    EventSource.loadOlympicsAffectedAirports(eventId)
  }

  val VOTE_REPUTATION_THRESHOLD = 25

  def getAffectedAirport(eventId : Int, principalAirport : Airport) : List[Airport] = {
    EventSource.loadOlympicsAffectedAirports(eventId)
      .find(_._1.id == principalAirport.id)
      .map(_._2.flatMap(stub => AirportCache.getAirport(stub.id)))
      .getOrElse(List.empty)
  }

  def getVoteWeight(airline : Airline) : Int = {
    val isNational = CountryAirlineTitle.getTopTitlesByAirline(airline.id).exists(_.title == Title.NATIONAL_AIRLINE)
    computeVoteWeight(airline, isNational)
  }
  private def computeVoteWeight(airline : Airline, isNationalAirline : Boolean): Int = {
    var voteWeight =
      if (airline.getReputation() >= VOTE_REPUTATION_THRESHOLD)
        1
      else
        0

    if (isNationalAirline) {
      voteWeight += 1
    }
    voteWeight
  }

  def getVoteWeights() : Map[Airline, Int] = {
    AirlineSource.loadAllAirlines().map { airline =>
      val isNational = CountryAirlineTitle.getTopTitlesByAirline(airline.id).exists(_.title == Title.NATIONAL_AIRLINE)
      (airline, computeVoteWeight(airline, isNational))
    }.toMap
  }

  def getGoalByCycle(eventId : Int, airlineId : Int, cycle : Int): Option[Int] = {
    EventSource.loadEventById(eventId) match {
      case Some(olympics: Olympics) =>
        EventSource.loadOlympicsAirlineGoal(eventId, airlineId) match {
          case Some(goal) =>
            Some(goal * getDemandMultiplier(olympics.currentWeek(cycle)) / demandMultiplierSum)
          case None => None
        }
      case _ =>
        None
    }
  }

  val voteRewardOptions : List[EventReward] = List(OlympicsVoteCashReward(), OlympicsVoteLoyaltyReward())
  val passengerRewardOptions : List[EventReward] = List(OlympicsPassengerCashReward(), OlympicsPassengerLoyaltyReward(), OlympicsPassengerReputationReward(), OlympicsPassengerActionPointReward())

  val getDemandMultiplier = (weekOfYear: Int) => {
      if (weekOfYear < Period.yearLength - Olympics.GAMES_DURATION * 12) {
        1
      } else if (weekOfYear < Period.yearLength - Olympics.GAMES_DURATION * 3) { //3 months before the game
        2
      } else if (weekOfYear < Period.yearLength - Olympics.GAMES_DURATION) { //1 momnth beofre the game
        4
      } else if (weekOfYear < Period.yearLength) { //game is on
        10
      } else {
        0
      }
  }
  val demandMultiplierSum = (0 until Period.yearLength).map(getDemandMultiplier(_)).sum
}

/**
  *
  * @param airline
  * @param voteList from the most favored to the least
  */
case class OlympicsAirlineVote(airline : Airline, voteList : List[Airport]) {
  def withWeight(weight: Int) = {
    OlympicsAirlineVoteWithWeight(airline, weight, voteList)
  }
}

case class OlympicsAirlineVoteWithWeight(airline: Airline, weight: Int, voteList : List[Airport])

/**
  * Starting from round 1
  * @param round
  */
case class OlympicsVoteRound(round : Int, votes : Map[Airport, Int])


object EventType extends Enumeration {
    type EventType = Value
    val OLYMPICS = Value
}

object RewardCategory extends Enumeration {
  type RewardCategory = Value
  val OLYMPICS_VOTE, OLYMPICS_PASSENGER = Value
}

object RewardOption extends Enumeration {
  type RewardOption = Value
  val CASH, LOYALTY, REPUTATION, ACTION_POINTS = Value
}

abstract class EventReward(val eventType : EventType.Value, val rewardCategory : RewardCategory.Value, val rewardOption : RewardOption.Value) {
  EventReward.lookup.put((rewardCategory, rewardOption), this)

  def apply(event: Event, airline : Airline): Unit = {
    applyReward(event, airline)
    EventSource.savePickedRewardOption(event.id, airline.id, this)
  }
  protected def applyReward(event: Event, airline : Airline)

  val description : String
  def redeemDescription(eventId: Int, airlineId: Int) = description
}

object EventReward {
  private val lookup = mutable.HashMap[(RewardCategory.Value, RewardOption.Value), EventReward]()
  def fromId(categoryId : Int, optionId : Int) = {
    lookup.get((RewardCategory(categoryId), RewardOption(optionId)))
  }
}

case class OlympicsVoteCashReward() extends EventReward(EventType.OLYMPICS, RewardCategory.OLYMPICS_VOTE, RewardOption.CASH) {
  val CASH_BONUS = 10000000 //10 millions
  override def applyReward(event: Event, airline : Airline) = {
    AirlineSource.saveLedgerEntry(AirlineLedgerEntry(airline.id, CycleSource.loadCycle(), LedgerType.PRIZE, CASH_BONUS, Some("Olympics Vote Cash Reward")))
  }

  override val description: String = "$10,000,000 subsidy in cash"
}

case class OlympicsVoteLoyaltyReward() extends EventReward(EventType.OLYMPICS, RewardCategory.OLYMPICS_VOTE, RewardOption.LOYALTY) {
  val LOYALTY_BONUS = 10
  override def applyReward(event: Event, airline : Airline) = {
    val bonus = AirlineBonus(BonusType.OLYMPICS_VOTE, AirlineAppeal(loyalty = LOYALTY_BONUS), Some(event.startCycle + event.duration))
    Olympics.getAffectedAirport(event.id, Olympics.getSelectedAirport(event.id).get).foreach { affectedAirport =>
      AirportSource.saveAirlineAppealBonus(affectedAirport.id, airline.id, bonus)
    }
  }

  override val description: String = s"+$LOYALTY_BONUS loyalty bonus on airports around the host city until the end of Olympics"
}

case class OlympicsPassengerCashReward() extends EventReward(EventType.OLYMPICS, RewardCategory.OLYMPICS_PASSENGER, RewardOption.CASH) {
  val MIN_CASH_REWARD = 20000000 //20 millions

  def computeReward(eventId: Int, airlineId : Int) = {
    val stats: Map[Int, BigDecimal] = EventSource.loadOlympicsAirlineStats (eventId, airlineId).toMap
    val totalScore = stats.view.values.sum
    Math.max((totalScore * 1500).toLong, MIN_CASH_REWARD)
  }

  override def applyReward(event: Event, airline : Airline) = {
    val reward = computeReward(event.id, airline.id)
    AirlineSource.saveLedgerEntry(AirlineLedgerEntry(airline.id, CycleSource.loadCycle(), LedgerType.PRIZE, reward, Some("Olympics Winner Reward")))
  }

  override val description: String = "$20,000,000 or $1500 * score (whichever is higher) cash reward"
  override def redeemDescription(eventId: Int, airlineId : Int) = s"$$${java.text.NumberFormat.getIntegerInstance.format(computeReward(eventId, airlineId))} cash reward"

}

case class OlympicsPassengerLoyaltyReward() extends EventReward(EventType.OLYMPICS, RewardCategory.OLYMPICS_PASSENGER, RewardOption.LOYALTY) {
  val LOYALTY_BONUS = 18
  override def applyReward(event: Event, airline : Airline) = {
    val bonus = AirlineBonus(BonusType.OLYMPICS_PASSENGER, AirlineAppeal(loyalty = LOYALTY_BONUS), Some(event.startCycle + event.duration * 4))
    Olympics.getAffectedAirport(event.id, Olympics.getSelectedAirport(event.id).get).foreach { affectedAirport =>
      AirportSource.saveAirlineAppealBonus(affectedAirport.id, airline.id, bonus)
    }
  }

  override val description: String = s"+$LOYALTY_BONUS loyalty bonus on airports around the host city for 12 years after the Olympics Games ended"
}

case class OlympicsPassengerReputationReward() extends EventReward(EventType.OLYMPICS, RewardCategory.OLYMPICS_PASSENGER, RewardOption.REPUTATION) {
  val REPUTATION_BONUS = 75
  override def applyReward(event: Event, airline : Airline) = {
    AirlineSource.adjustAirlineReputation(airline.id, REPUTATION_BONUS)
  }

  override val description: String = s"+$REPUTATION_BONUS reputation boost (one time only, reputation will eventually drop back to normal level)"
}

case class OlympicsPassengerActionPointReward() extends EventReward(EventType.OLYMPICS, RewardCategory.OLYMPICS_PASSENGER, RewardOption.ACTION_POINTS) {
  val BASE_BONUS = 36
  val MAX_BONUS = 120

  override def applyReward(event: Event, airline : Airline) = {
    val cycle = CycleSource.loadCycle()
    val bonus = computeReward(event.id, airline.id)
    AirlineSource.adjustAirlineActionPoints(airline, bonus.toDouble)
  }

  def computeReward(eventId: Int, airlineId : Int) = {
    val stats: Map[Int, BigDecimal] = EventSource.loadOlympicsAirlineStats (eventId, airlineId).toMap
    val totalScore = stats.view.values.sum
    EventSource.loadOlympicsAirlineGoal(eventId, airlineId) match {
      case Some(goal) =>
        val overachieverRatio = Math.min(1.0, totalScore.toDouble / goal / 4) //at 400% then it claim 1.0 overachiever ratio
        val extraBonus = ((MAX_BONUS - BASE_BONUS) * overachieverRatio).toInt //could be 0
        BASE_BONUS + extraBonus
      case None => 0
    }

  }

  override val description: String = s"+$BASE_BONUS to $MAX_BONUS action points (based on your score and how much you overachieved your goal)"
  override def redeemDescription(eventId: Int, airlineId : Int) = s"${computeReward(eventId, airlineId)} free action points"
}

