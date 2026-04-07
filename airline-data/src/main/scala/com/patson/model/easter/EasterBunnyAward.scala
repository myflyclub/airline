package com.patson.model.easter

import com.patson.data.{AirlineSource, AirportSource, ChristmasSource, CycleSource}
import com.patson.model.christmas.SantaClausAward.{Difficulty, getDifficultyLevel}
import com.patson.model.christmas.{SantaClausAward, SantaClausAwardType, SantaClausInfo}
import com.patson.model.{Airline, AirlineAppeal, AirlineBonus, AirlineLedgerEntry, BonusType, LedgerType, Period}
import com.patson.util.AirlineCache

abstract class EasterBunnyAward(info: SantaClausInfo) {
  val getType : SantaClausAwardType.Value
  val difficultyMultiplier : Int = getDifficultyLevel(info) match {
    case Some(Difficulty.NORMAL) => 1
    case Some(Difficulty.HARD) => 2
    case _ => 1
  }
  def apply: Unit = {
    applyAward()
    ChristmasSource.updateSantaClausInfo(info.copy(found = true, pickedAward = Some(getType)))
  }
  protected def applyAward() : Unit

  val description : String
  val integerFormatter = java.text.NumberFormat.getIntegerInstance
}


class EasterCashAward(info: SantaClausInfo) extends EasterBunnyAward(info) {
  override val getType: SantaClausAwardType.Value = SantaClausAwardType.CASH
  val CASH_AMOUNT = 15000000 * difficultyMultiplier
  override def applyAward(): Unit = {
    AirlineSource.saveLedgerEntry(AirlineLedgerEntry(info.airline.id, CycleSource.loadCycle(), LedgerType.PRIZE, CASH_AMOUNT))
  }
  override val description: String = s"The Easter Bunny crammed a golden egg into your briefcase! He's giving you $$${integerFormatter.format(CASH_AMOUNT)} cold, hard cash! 🥚💰"
}

class EasterServiceQualityAward(info: SantaClausInfo) extends EasterBunnyAward(info) {
  override val getType: SantaClausAwardType.Value = SantaClausAwardType.SERVICE_QUALITY
  val BONUS = 10 * difficultyMultiplier
  override def applyAward(): Unit = {
    val newQuality = Math.min(info.airline.getCurrentServiceQuality() + BONUS, Airline.EQ_MAX)
    info.airline.setCurrentServiceQuality(newQuality)
    AirlineSource.saveAirlineInfo(info.airline)
  }
  override val description: String = s"The Easter Bunny sprinkled enchanted egg dust all over your fleet! ✨ Overall Airline Service Quality bonus +$BONUS! (not permanent — the magic wears off eventually)"
}

class EasterHqLoyaltyAward(info: SantaClausInfo) extends EasterBunnyAward(info) {
  override val getType: SantaClausAwardType.Value = SantaClausAwardType.HQ_LOYALTY
  val BONUS = 15 * difficultyMultiplier
  override def applyAward(): Unit = {
    AirlineCache.getAirline(info.airline.id, fullLoad = true).foreach { airline =>
      airline.getHeadQuarter().foreach { hq =>
        val bonus = AirlineBonus(BonusType.EASTER_BUNNY, AirlineAppeal(loyalty = BONUS), Some(CycleSource.loadCycle() + 5 * Period.yearLength))
        AirportSource.saveAirlineAppealBonus(hq.airport.id, airline.id, bonus)
      }
    }
  }
  override val description: String = {
    val hq = AirlineCache.getAirline(info.airline.id, fullLoad = true).get.getHeadQuarter().get.airport
    s"The Easter Bunny is hosting an egg hunt at your HQ for ${5 * Period.yearLength} weeks! 🐣 Loyalty bonus +$BONUS at ${hq.displayText}!"
  }
}

class EasterAirportLoyaltyAward(info: SantaClausInfo) extends EasterBunnyAward(info) {
  override val getType: SantaClausAwardType.Value = SantaClausAwardType.AIRPORT_LOYALTY
  val BONUS = 30 * difficultyMultiplier
  override def applyAward(): Unit = {
    val bonus = AirlineBonus(BonusType.EASTER_BUNNY, AirlineAppeal(loyalty = BONUS), Some(CycleSource.loadCycle() + 5 * Period.yearLength))
    AirportSource.saveAirlineAppealBonus(info.airport.id, info.airline.id, bonus)
  }
  override val description: String = s"The Easter Bunny stamped your airline logo on every egg being delivered to ${info.airport.displayText}! 🐰 Loyalty bonus +$BONUS at ${info.airport.displayText} for ${5 * Period.yearLength} weeks!"
}

class EasterReputationAward(info: SantaClausInfo) extends EasterBunnyAward(info) {
  override val getType: SantaClausAwardType.Value = SantaClausAwardType.REPUTATION
  val BONUS = 25 * difficultyMultiplier
  override def applyAward(): Unit = {
    val newReputation = info.airline.getReputation() + BONUS
    info.airline.setReputation(newReputation)
    AirlineSource.saveAirlineInfo(info.airline)
  }
  override val description: String = s"The Easter Bunny made you the grand marshal of the Easter parade! 🎉 Your airline reputation gets a boost of +$BONUS! (not permanent — the spotlight fades eventually)"
}

class EasterActionPointAward(info: SantaClausInfo) extends EasterBunnyAward(info) {
  override val getType: SantaClausAwardType.Value = SantaClausAwardType.ACTION_POINT
  val actionPoints = 15 * difficultyMultiplier
  override def applyAward(): Unit = {
    AirlineSource.adjustAirlineActionPoints(info.airline, actionPoints.toDouble)
  }
  override val description: String = s"The Easter Bunny stuffed an action point egg into your basket! 🥚 You receive $actionPoints action points!"
}

object EasterBunnyAward {
  def getAllRewards(info : SantaClausInfo) = {
    SantaClausAwardType.values.toList.map { getRewardByType(info, _) }
  }

  def getRewardByType(info : SantaClausInfo, rewardType : SantaClausAwardType.Value) = {
    rewardType match {
      case SantaClausAwardType.CASH => new EasterCashAward(info)
      case SantaClausAwardType.ACTION_POINT => new EasterActionPointAward(info)
      case SantaClausAwardType.SERVICE_QUALITY => new EasterServiceQualityAward(info)
      case SantaClausAwardType.HQ_LOYALTY => new EasterHqLoyaltyAward(info)
      case SantaClausAwardType.AIRPORT_LOYALTY => new EasterAirportLoyaltyAward(info)
      case SantaClausAwardType.REPUTATION => new EasterReputationAward(info)
    }
  }
}
