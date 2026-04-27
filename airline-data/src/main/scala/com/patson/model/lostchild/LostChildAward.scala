package com.patson.model.lostchild

import com.patson.data.{AirlineSource, AirportSource, ChristmasSource, CycleSource}
import com.patson.init.AirportWeatherData
import com.patson.model.christmas.{SantaClausAwardType, SantaClausInfo}
import com.patson.model.{Airline, AirlineAppeal, AirlineBonus, AirlineLedgerEntry, Airport, BonusType, Computation, LedgerType, Period}
import com.patson.util.AirlineCache

abstract class LostChildAward(info: SantaClausInfo) {
  val getType: SantaClausAwardType.Value
  def apply: Unit = {
    applyAward()
    ChristmasSource.updateSantaClausInfo(info.copy(found = true, pickedAward = Some(getType)))
  }
  protected def applyAward(): Unit
  val description: String
  val integerFormatter = java.text.NumberFormat.getIntegerInstance
}

class LostChildCashAward(info: SantaClausInfo) extends LostChildAward(info) {
  override val getType: SantaClausAwardType.Value = SantaClausAwardType.CASH
  val CASH_AMOUNT = 75000000
  override def applyAward(): Unit = {
    AirlineSource.saveLedgerEntry(AirlineLedgerEntry(info.airline.id, CycleSource.loadCycle(), LedgerType.PRIZE, CASH_AMOUNT))
  }
  override val description: String = s"The child was a lost princess! The kingdom of ${info.airport.city} grants you a reward of $$${integerFormatter.format(CASH_AMOUNT)}."
}

class LostChildActionPointAward(info: SantaClausInfo) extends LostChildAward(info) {
  override val getType: SantaClausAwardType.Value = SantaClausAwardType.ACTION_POINT
  val actionPoints = 50
  override def applyAward(): Unit = {
    AirlineSource.adjustAirlineActionPoints(info.airline, actionPoints.toDouble)
  }
  override val description: String = s"The wholesome story inspires your staff. Receive +$actionPoints action points!"
}

class LostChildHqLoyaltyAward(info: SantaClausInfo) extends LostChildAward(info) {
  override val getType: SantaClausAwardType.Value = SantaClausAwardType.HQ_LOYALTY
  val BONUS = 10
  override def applyAward(): Unit = {
    AirlineCache.getAirline(info.airline.id, fullLoad = true).foreach { airline =>
      airline.getHeadQuarter().foreach { hq =>
        val bonus = AirlineBonus(BonusType.LOST_CHILD, AirlineAppeal(loyalty = BONUS), Some(CycleSource.loadCycle() + Period.yearLength * 2))
        AirportSource.saveAirlineAppealBonus(hq.airport.id, airline.id, bonus)
      }
    }
  }
  override val description: String = {
    val hq = AirlineCache.getAirline(info.airline.id, fullLoad = true).get.getHeadQuarter().get.airport
    s"Your home town has been following the saga, and on the heatwearming reunion feels +$BONUS increased loyalty for the next ${Period.yearLength * 2} weeks."
  }
}

class LostChildAirportLoyaltyAward(info: SantaClausInfo) extends LostChildAward(info) {
  override val getType: SantaClausAwardType.Value = SantaClausAwardType.AIRPORT_LOYALTY
  val BONUS = 30
  override def applyAward(): Unit = {
    val bonus = AirlineBonus(BonusType.LOST_CHILD, AirlineAppeal(loyalty = BONUS), Some(CycleSource.loadCycle() + Period.yearLength * 2))
    AirportSource.saveAirlineAppealBonus(info.airport.id, info.airline.id, bonus)
  }
  override val description: String = s"The local press ran the reunion story non-stop. Loyalty bonus +$BONUS at ${info.airport.displayText} for ${Period.yearLength * 2} weeks!"
}

object LostChildAward {
  val supportedAwardTypes = List(SantaClausAwardType.CASH, SantaClausAwardType.ACTION_POINT, SantaClausAwardType.HQ_LOYALTY, SantaClausAwardType.AIRPORT_LOYALTY)

  def getAllRewards(info: SantaClausInfo): List[LostChildAward] =
    supportedAwardTypes.map(getRewardByType(info, _))

  def getRewardByType(info: SantaClausInfo, rewardType: SantaClausAwardType.Value): LostChildAward = rewardType match {
    case SantaClausAwardType.CASH            => new LostChildCashAward(info)
    case SantaClausAwardType.ACTION_POINT    => new LostChildActionPointAward(info)
    case SantaClausAwardType.HQ_LOYALTY      => new LostChildHqLoyaltyAward(info)
    case SantaClausAwardType.AIRPORT_LOYALTY => new LostChildAirportLoyaltyAward(info)
  }

  def getInterrogationClueText(interrogationIndex: Int, target: Airport): String = interrogationIndex match {
    case 1 => getPopulationClueBroad(target.population)
    case 2 => getCulturalClue(target.zone)
    case 3 => getIncomeClue(target.income)
    case 4 => getPopulationClueTight(target.population)
    case 5 => getTradeClue(target.zone)
    case _ => ""
  }

  def getFlightClueText(flightIndex: Int, target: Airport, guessAirport: Airport): String = {
    if (target.id == guessAirport.id) "The child hops in joy as they recognize their home airport! You did it!!!"
    else if (flightIndex <= 3 && getUtcOffset(target) != getUtcOffset(guessAirport)) getLongitudeClue(flightIndex, target, guessAirport)
    else getDistanceClue(Computation.calculateDistance(target, guessAirport))
  }

  def getUtcOffset(airport: Airport): Double = airport.countryCode match {
    case "CN"                               => 8.0
    case "IN" | "LK"                        => 5.5
    case "NP"                               => 5.75
    case "IR"                               => 3.5
    case "AF"                               => 4.5
    case "MM"                               => 6.5
    case "MA"                               => 1.0
    case "EG" | "UA"                        => 2.0
    case "ES" | "FR" | "NL" | "BE" | "DE" |
         "NO" | "SE"                        => 1.0
    case "PT"                               => 0.0
    case "MX"                               => -6.0
    case "NZ"                               => 12.0
    case "US" | "CA"                        => Math.round(airport.longitude / 15.0).toDouble + 1
    case _                                  => Math.round(airport.longitude / 15.0).toDouble
  }

  def formatUtcOffset(offset: Double): String = {
    val sign = if (offset >= 0) "+" else "-"
    val abs = Math.abs(offset)
    val hours = abs.toInt
    val minutes = Math.round((abs - hours) * 60).toInt
    if (minutes == 0) s"UTC $sign$hours" else s"UTC $sign$hours:${"%02d".format(minutes)}"
  }

  def getLongitudeClue(flightIndex: Int, target: Airport, guessAirport: Airport): String = {
    val targetOffset = getUtcOffset(target)
    val guessOffset = getUtcOffset(guessAirport)
    val rawDiff = Math.abs(targetOffset - guessOffset)
    val absDiff = if (rawDiff > 12) 24 - rawDiff else rawDiff
    flightIndex match {
      case 1 => getBroadTimezoneClue(absDiff, targetOffset)
      case 2 =>
        val weather = getWeatherObservation(target)
        val tz = getMediumTimezoneClue(absDiff, targetOffset)
        if (weather.nonEmpty) s"$tz $weather" else tz
      case _ => getTightTimezoneClue(absDiff, targetOffset)
    }
  }

  def getBroadTimezoneClue(absDiff: Double, targetOffset: Double): String = {
    val lo = Math.floor(targetOffset / 4) * 4
    val bracket = s"${formatUtcOffset(lo)} to ${formatUtcOffset(lo + 4)}"
    if (absDiff >= 8)
      s"The child pointed at a clock, said 'broken,' and fell asleep standing up. Home timezone: $bracket."
    else if (absDiff >= 4)
      s"The child is upset by the sun's current position and insists it is bedtime. Home timezone: $bracket."
    else if (absDiff >= 1)
      s"The child asked for breakfast, was given lunch, and ate it with deep suspicion. Home timezone: $bracket."
    else
      s"The child adapted immediately and has already located the vending machines. Home timezone: $bracket."
  }

  def getMediumTimezoneClue(absDiff: Double, targetOffset: Double): String = {
    val lo = Math.floor(targetOffset / 2) * 2
    val bracket = s"${formatUtcOffset(lo)} to ${formatUtcOffset(lo + 2)}"
    if (absDiff >= 9)
      s"The child has been awake since 2am by their reckoning and shows no signs of stopping. Home timezone: $bracket."
    else if (absDiff >= 6)
      s"The child fell asleep in the food court and was briefly tagged as unattended luggage. Home timezone: $bracket."
    else if (absDiff >= 3)
      s"The child ate dinner, went to sleep, woke up, and asked for dinner again. Home timezone: $bracket."
    else if (absDiff >= 1)
      s"The child is mildly tired and deeply suspicious of the local snacks. Home timezone: $bracket."
    else
      s"The child seems fine. Suspiciously fine. Home timezone: $bracket."
  }

  def getTightTimezoneClue(absDiff: Double, targetOffset: Double): String = {
    val exact = formatUtcOffset(targetOffset)
    if (absDiff >= 10)
      s"The child called a luggage trolley 'bed' and attempted to sleep in it. Home timezone: approximately $exact."
    else if (absDiff >= 8)
      s"The child announced 'night time' at noon, lay down under the departures board, and went immediately to sleep. Home timezone: approximately $exact."
    else if (absDiff >= 6)
      s"The child remains fussy and confused. Not 100% sure but it's close to $exact timezone."
    else if (absDiff >= 4)
      s"The child asked what time it was, was told, and burst into tears. Home timezone: around $exact."
    else if (absDiff >= 2)
      s"The child keeps checking their wrist. They are not wearing a watch. Home timezone: around $exact."
    else if (absDiff >= 1)
      s"The child seems mildly puzzled about mealtimes but is coping. We think they're from around $exact."
    else
      s"The child seems fully adjusted; around here must be their home timezone or very close to it."
  }

  def getWeatherObservation(target: Airport): String = {
    AirportWeatherData.getAirportWeatherData(target) match {
      case None => ""
      case Some(w) =>
        if (w.snowPerDay > 0.5)
          "Notably, the child keeps asking 'why so green' and complains about being hot."
        else if (w.maxTemperature >= 32)
          "The child was seen revelling in the heat. Could they be from a particularly hot place?"
        else if (w.maxTemperature <= 0)
          "The child arrived in a miniature ski suit and seems to seek cold."
        else if (w.maxTemperature <= 10)
          "Curiously, the child declared this airport 'too hot' and immediately started taking off all their clothes."
        else if (w.sunnyDayPercentage >= 80)
          "By the way, a flight attendant noticed the child had a sun hat and sunscreen."
        else if (w.sunnyDayPercentage <= 20)
          "Curiously, the child expresses shock at seeing the sun."
        else
          "A flight attendant notices the child's clothing is aggressively ordinary, as if they come from a locale with an aggressively mild climate."
    }
  }

  def getDistanceClue(distance: Int): String = {
    if (distance == 0)
        "The child hops in joy as they recognize their home airport! You did it!!!"
    else if (distance >= 6000)
        "Looking around, the child is despondent as they recognize nothing. They must be very far away from home, like at least 6,000 km away."
    else if (distance >= 2000)
        "The child spots a familiar global fast-food logo  but is deeply confused by menu options, implying their home is at least 2,000 km away."
    else if (distance >= 1000)
        "At the airport, the child recognizes airlines' livery – we must be getting closer, probably 1,000 to 2,000 km away."
    else if (distance >= 500)
        "The child possibly recognizes a distant mountain while in-flight, suggesting this airport is a distance of 500 to 1,000 km away from their home."
    else if (distance >= 250)
        "The child thinks they recognize the snack brands in the shop. They're not sure. Within 500 to 1,000 km."
    else
        "As the airplane is landing, the child excitedly points out familiar geography, suggesting their home is less than 250 km away."
  }

  def getPopulationClueBroad(population: Int): String = {
    if (population >= 10000000)
      "Called the food 'mid' and meant it. Is clearly an urban sophisticate from an airport with over 10,000,000 population."
    else if (population >= 1000000)
      "Child was capable of navigating the terminal alone. Is clearly Between 1,000,000 and 10,000,000 population."
    else
      "On the Fascinated by an escalator. Fewer than 1,000,000 population at home."
  }

  def getPopulationClueTight(population: Int): String = {
    if (population >= 12000000)
      "The child called this airport 'very quiet' and asked three times where the other terminals are. Home population: over 12,000,000."
    else if (population >= 6000000)
      "Found the calm unsettling and seemed to be seeking people, as if they . 6,000,000 to 12,000,000."
    else if (population >= 2000000)
      "The child cannot identify a cow but executed a rush-hour tube manoeuvre flawlessly. 2,000,000 to 6,000,000."
    else if (population >= 1000000)
      "The child seems mildly surprised by the lack of lines. Must be from a city with 1,000,000 to 2,000,000 population."
    else if (population >= 500000)
      "The child described their local zoo of only a couple animals, suggesting they're from a smaller airport of 500,000 to 1,000,000 population."
    else if (population >= 250000)
      "The child excitedly drew their city's shopping center, which was quite small really. 250,000 to 500,000 population max."
    else
      "The child is completely awed by all the people and even simple things like elevators. They must be from a city with fewer than 250,000 people."
  }

  def getCulturalClue(zone: String): String = {
    val affinity = LostChildInfo.CULTURAL_AFFINITIES.find(ca => zone.contains(ca))
    affinity match {
      case Some("Arabic|")       => "Was waddling and chewing sticky cardamom candies while muttering in proto-<b>Arabic</b>."
      case Some("Francophonie|") => "Pushed away the snacks with profound <b>Francophonie</b> disdain then proceeded to angrily scream for 'decent' food."
      case Some("Hispanic|")     => "Babbled <b>Hispanic</b>-style directions to three stuffed animals and a cousin in baggage."
      case Some("Lusophone|")    => "Was discovered the child's passionate mumbles are Fado and Samba classics."
      case Some("Russian|")      => "During the entire observation, the child did not smile once and was fixated on a set of dolls that fit inside themselves."
      case Some("Dutch|")        => "Threw a blunt tantrum demanding decent bread and butter and hagelslag."
      case Some("Malay|")        => "Was observed practicing a cheerful <b>Malay</b> 'Selamat pagi'."
      case Some("German|")       => "Non-verbal but threw a fit when the apple juice was two minutes late. Maybe <b>German</b>?"
      case Some("Sinophone|")    => "Stole a flight attendant's phone to watch <b>Sinophone</b> videos."
      case Some("Swahili|")      => "Spends 15 minutes greeting each staff member, possibly in <b>Swahili</b>?"
      case Some("Urdu|")         => "Reciting what staff believe was ghazal poetry, in between demands for sugar packets."
      case Some("Hindi|")        => "Was insconsolable until a <b>Hindi</b>-speaking dadi produced a tiffin from nowhere."
      case Some("CC|")           => "Child was gleefully throwing sticky plantains."
      case Some("Tigrinya|")     => "Limited vocabulary but was constantly asking for injera."
      case Some("Thai|")         => "Observed waddling and waving a <b>Thai</b> 'wai' to everyone."
      case Some("Japanese|")     => "Color-coded all the luggage while practicing <b>Japanese</b> bows."
      case Some("Anglophone|")   => "Appears capable of understanding basic <b>English</b>. You wonder why this observation wasn't made sooner."
      case Some("Sunni|")        => "Seemed disoriented. Was asking when the five daily prayers would be held."
      case _                     => "The toddler is screaming gibberish and aggressively biting. <b>We learned nothing</b>."
    }
  }

  def getIncomeClue(income: Int): String = {
    if (income >= 80000)
      "Described the lounge as 'yucky' and requested a robot to carry their teddy, suggesting they're from a rich city of over $80,000."
    else if (income >= 40000)
      "Child was content with standard snacks. Must be from a city with a per-capita income between $40,000 to $80,000."
    else if (income >= 10000)
      "Made one bag of chips last three hours and asked twice whether the water was complimentary. Cleary come from an airport of $10,000 to $40,000 income."
    else
      "Already competent at collecting free WiFi codes and seems non-pulsed in the terminal and is aggressively collecting loyalty stamps. Income: under $10,000 per capita."
  }

  def getTradeClue(zone: String): String = {
    val excluded = Set(
      "Petrobras", "Pemex", "PDVSA", "Sapeur Network", "Colonial ties bind","Latifundia Legacy","Coal RU", "Coal CN", "Vampiric", "Mineral exploration", "Computers", "Jutland capital journeys", "Throat Singing Circuit", "Golden Triangle x2", "Bollywood 2 Bureaucracy x2", "Zim Connect", "Cryptids", "NIOC"
    )
    zone.split("-").find(e => !e.contains("|") && e.nonEmpty && e != "x2" && !excluded.contains(e)) match {
      case Some(affinity) => s"The child is constantly prattling on about ${affinity} as if they have a deep connection to it."
      case None           => "The child was nonsensical."
    }
  }
}
