package com.patson.model

import com.patson.data._
import com.patson.data.airplane.ModelSource
import com.patson.util.AirlineCache

import java.util.concurrent.ThreadLocalRandom
import java.util.{Calendar, Date}
import scala.collection.mutable.ListBuffer
import scala.collection.immutable.ListMap

case class Airline(name: String, var airlineType: AirlineType = LegacyAirline, var id : Int = 0) extends IdObject {
  val airlineInfo = AirlineInfo(0, 0, 0, 0, 0, 0, 0, 0)
  var allianceId : Option[Int] = None
  var bases : List[AirlineBase] = List.empty
  var stats = AirlineStat(0, 0, Period.WEEKLY, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)

  /**
   * private method to set balance, only used for resetAirline & AirlineSource. For normal balance change, use AirlineSource.saveLedgerEntry 
   **/
  def setBalance(balance : Long) = {
    airlineInfo.balance = balance
  }

  def setCurrentServiceQuality(serviceQuality : Double) {
    airlineInfo.currentServiceQuality = serviceQuality
  }

  def setTargetServiceQuality(targetServiceQuality : Int) {
    airlineInfo.targetServiceQuality = targetServiceQuality
  }

  def setMinimumRenewalBalance(minimumRenewalBalance: Long) {
    airlineInfo.minimumRenewalBalance = minimumRenewalBalance
  }

  def setPrestigePoints(prestigePoints: Int) {
    airlineInfo.prestigePoints = prestigePoints
  }

  def setReputation(reputation : Double) {
    airlineInfo.reputation = reputation
  }

  def setStockPrice(stockPrice: Double) {
    airlineInfo.stockPrice = stockPrice
  }

  def setSharesOutstanding(sharesOutstanding: Int) {
    airlineInfo.sharesOutstanding = sharesOutstanding
  }

  def getDividends(): Long = airlineInfo.dividends
  def setDividends(dividends: Long): Unit = { airlineInfo.dividends = dividends }

  def doStockOp(isSellShares: Int): (Long, Long) = {
    val shareCost: Long = (1_000_000 * airlineInfo.stockPrice).toLong
    val fee: Long = StockModel.STOCK_BROKER_FEE_BASE + (shareCost * StockModel.STOCK_BROKER_FEE).toLong
    airlineInfo.sharesOutstanding += 1_000_000 * isSellShares
    airlineInfo.stockPrice *= (1 - isSellShares * ThreadLocalRandom.current().nextDouble(StockModel.STOCK_BUYBACK_MIN_CHANGE, StockModel.STOCK_BUYBACK_MAX_CHANGE))
    (shareCost * isSellShares, fee)
  }

  def setStats(stats: AirlineStat) = {
    this.stats = stats
  }

  def removeCountryCode() = {
    airlineInfo.countryCode = None
  }

  def setCountryCode(countryCode : String) = {
    airlineInfo.countryCode = Some(countryCode)
  }

  def getCountryCode() = {
    airlineInfo.countryCode
  }

  def getAirlineCode() = {
    airlineMeta.airlineCode.getOrElse(getDefaultAirlineCode())
  }

  def getMinimumRenewalBalance() = {
    airlineInfo.minimumRenewalBalance
  }

  def getPrestigePoints() = {
    airlineInfo.prestigePoints
  }

  def isSkipTutorial = {
    airlineMeta.skipTutorial
  }

  def notifiedLevel: Int = airlineMeta.notifiedLevel
  def notifiedLoyalistLevel: Int = airlineMeta.notifiedLoyalistLevel

  def setInitialized(value : Boolean) = {
    airlineInfo.initialized = value
  }

  def isInitialized = {
    airlineInfo.initialized
  }


  def setAllianceId(allianceId : Int) = {
    this.allianceId = Some(allianceId)
  }

  def getAllianceId() : Option[Int] = {
    allianceId
  }


  def setBases(bases : List[AirlineBase]) {
    this.bases = bases
  }

  def airlineGrade : AirlineGrade = {
    val reputation = airlineInfo.reputation
    AirlineGrades.findGrade(reputation)
  }

  def fuelTaxRate: Int = {
    AirlineGrades.findTaxRate(airlineInfo.reputation)
  }

  def airlineGradeStockPrice: AirlineGrade = {
    val stockPrice = airlineInfo.stockPrice
    AirlineGradeStockPrice.findGrade(stockPrice)
  }

  def airlineGradeTouristsTravelers: AirlineGrade = {
    val t = stats.total - stats.business - stats.elites
    AirlineGradeTouristsTravelers.findGrade(t)
  }

  def airlineGradeElites: AirlineGrade = {
    val e = stats.elites
    AirlineGradeElites.findGrade(e)
  }


  def getBases() = bases

  def getHeadQuarter() = bases.find(_.headquarter)

  def getBalance() = airlineInfo.balance

  def getActionPoints() = airlineInfo.actionPoints
  /** Updated in-memory by adjustAirlineActionPoints/adjustAirlineActionPointsBatch. Do not call saveAirlineInfo() to persist this value. */
  def setActionPoints(v: Double) = { airlineInfo.actionPoints = v }

  def getCurrentServiceQuality() = airlineInfo.currentServiceQuality

  def getTargetServiceQuality() : Int = airlineInfo.targetServiceQuality

  def getReputation() = airlineInfo.reputation

  def getStockPrice() : Double = airlineInfo.stockPrice

  def getSharesOutstanding() : Int = airlineInfo.sharesOutstanding

  def getDefaultAirlineCode() : String = {
    var code = name.split("\\s+").foldLeft("")((foldString, nameToken) => {
      val firstCharacter = nameToken.charAt(0)
      if (Character.isLetter(firstCharacter)) {
        foldString + firstCharacter.toUpper
      } else {
        foldString
      }
    })

    if (code.length() > 2) {
      code = code.substring(0, 2)
    } else if (code.length() < 2) {
      if (name.length == 1) {
        code = (name.charAt(0).toString + name.charAt(0)).toUpperCase()
      } else {
        code = name.substring(0, 2).toUpperCase()
      }
    }
    code
  }

  lazy val airlineMeta: AirlineMeta = AirlineSource.loadAirlineMeta(id)
  lazy val slogan = AirlineSource.loadSlogan(id)
  lazy val previousNames = AirlineSource.loadPreviousNameHistory(id).sortBy(_.updateTimestamp.getTime)(Ordering.Long.reverse).map(_.name)

  def getManagerInfo() : ManagerInfo = {
    val busyManagers = ManagerSource.loadBusyManagersByAirline(id)
    val availableCount = managerCount - busyManagers.size

    ManagerInfo(availableCount, busyManagers)
  }

  val BASE_MANAGER_COUNT = 7
  val MANAGER_PER_LEVEL = 3
  lazy val managerCount = BASE_MANAGER_COUNT + airlineGrade.level * MANAGER_PER_LEVEL
}

case class ManagerInfo(availableCount : Int, busyManagers: List[Manager])

case class AirlineInfo(var balance : Long, var currentServiceQuality : Double, var stockPrice : Double, var sharesOutstanding : Int, var targetServiceQuality : Int, var reputation : Double, var minimumRenewalBalance: Long, var actionPoints: Double = 0.0, var countryCode : Option[String] = None, var initialized : Boolean = false, var prestigePoints: Int = 0, var dividends: Long = 0)

case class AirlineMeta(airlineCode: Option[String] = None, color: Option[String] = None, skipTutorial: Boolean = false, notifiedLevel: Int = -1, notifiedLoyalistLevel: Int = 0)

object LedgerType extends Enumeration {
  type LedgerType = Value
  // Link operations — recorded as weekly aggregates per airline
  val FLIGHT_REVENUE,
      FLIGHT_CREW,
      AIRPORT_RENTALS,
      INFLIGHT_SERVICE,
      MAINTENANCE,
      PASSENGER_LOUNGE_COSTS,
      DELAY_COMPENSATION = Value
  // Operating overhead — weekly aggregates
  val BASE_UPKEEP,
      OVERTIME_COMPENSATION,
      LOUNGE_COST,
      LOUNGE_INCOME,
      ADVERTISING,
      FUEL_COST,
      CARBON_TAX = Value
  // Financing — weekly aggregates
  val LOAN_PAYMENT,
      NEGATIVE_BALANCE_LOAN_INTEREST,
      LOAN_DISBURSEMENT,
      DIVIDEND_PAYMENT = Value
  // Capital events — one entry per event
  val BUY_AIRPLANE,
      SELL_AIRPLANE,
      BASE_CONSTRUCTION,
      FACILITY_CONSTRUCTION,
      OIL_CONTRACT,
      PRIZE,
      BUY_BACK,
      CREATE_LINK = Value
}

case class AirlineLedgerEntry(airlineId : Int, cycle : Int, entryType : LedgerType.Value, amount : Long, description : Option[String] = None, id : Int = 0)
case class AirlineBalance(airlineId: Int, income: Long, normalizedOperatingIncome: Long,
  cashOnHand: Long, totalValue: Long, stockPrice: Double,
  period: Period.Value = Period.WEEKLY, var cycle: Int = 0)
case class AirlineBalanceDetails(airlineId: Int, ticketRevenue: Long, loungeRevenue: Long,
  staff: Long, staffOvertime: Long, flightCrew: Long, fuel: Long, fuelTax: Long,
  fuelNormalized: Long, deprecation: Long, airportRentals: Long, inflightService: Long,
  delay: Long, maintenance: Long, lounge: Long, advertising: Long, loanInterest: Long,
  dividends: Long = 0,
  period: Period.Value = Period.WEEKLY, var cycle: Int = 0)



object Airline {
  def fromId(id : Int) = {
    val airlineWithJustId = Airline("<unknown>")
    airlineWithJustId.id = id
    airlineWithJustId
  }
  val EQ_MAX : Double = 100 //employee quality
  val EQ_INTITIAL: Int = 35


  /**
   * bankruptcy or rebuild
   * @param airlineId
   * @param newBalance
   * @param resetExtendedInfo
   * @return
   */
  def resetAirline(airlineId: Int, newBalance: Long, resetExtendedInfo: Boolean = false) : Option[Airline] = {
    AirlineSource.loadAirlineById(airlineId, true) match {
      case Some(airline) =>
        // Will need this for prestige charm update after prestige info is processed
        val airlineHQOption = airline.getHeadQuarter().map(_.airport)
        // Update prestige info first before reputation is reset
        if (resetExtendedInfo) {
          airline.setInitialized(false)
          PrestigeSource.unlinkPrestige(airlineId)
          airline.setPrestigePoints(0)
        } else {
          Prestige.processPrestige(airline)
        }

        //remove all links
        LinkSource.deleteLinksByAirlineId(airlineId)
        //remove all airplanes
        AirplaneSource.deleteAirplanesByCriteria(List(("owner", airlineId)))
        AirplaneSource.deleteAirplaneConfigurationsByAirline(airlineId)
        //remove all bases
        airline.getBases().foreach(_.delete)
        //remove all loans
        BankSource.loadLoansByAirline(airlineId).foreach { loan =>
          BankSource.deleteLoan(loan.id)
        }
        // update prestige charm for the old airport HQ now prestige has been process and the airlines HQ cleared
        airlineHQOption.foreach(hq => Prestige.updatePrestigeCharmForAirport(hq.id))
        //remove all oil contract
        OilSource.deleteOilContractByCriteria(List(("airline", airlineId)))

        airline.getAllianceId().foreach { allianceId =>
          AllianceSource.loadAllianceById(allianceId).foreach { alliance =>
            alliance.members.find(_.airline.id == airline.id).foreach { member =>
              alliance.removeMember(member, true)
            }
          }
        }

        AllianceSource.loadAllianceMemberByAirline(airline).foreach { allianceMember =>
          AllianceSource.deleteAllianceMember(airlineId)
          if (allianceMember.role == AllianceRole.LEADER) { //remove the alliance
            AllianceSource.deleteAlliance(allianceMember.allianceId)
          }
        }

        AirlineSource.deleteReputationBreakdowns(airline.id)

        NegotiationSource.deleteLinkDiscountsByAirline(airline.id)

        airline.setBalance(newBalance)
        airline.setDividends(0)

        airline.removeCountryCode()
        airline.setTargetServiceQuality(EQ_INTITIAL)
        airline.setCurrentServiceQuality(0)
        airline.setReputation(0)

        LoyalistSource.deleteLoyalistsByAirline(airlineId)

        //reset all busy managers
        ManagerSource.deleteBusyDelegateByCriteria(List(("airline", "=", airlineId)))
        //reset all campaigns, has to be after manager
        CampaignSource.deleteCampaignsByAirline(airline.id)

        //clear all notifications except GAME_OVER
        NotificationSource.deleteAllExceptGameOver(airlineId)

        //reset notification deduplication state
        AirlineSource.saveNotifiedLevel(airline.id, -1)
        AirlineSource.saveNotifiedLoyalistLevel(airline.id, 0)

        AirlineSource.saveAirlineInfo(airline, updateBalance = true)
        AirlineCache.invalidateAirline(airlineId)
        println(s"!! Reset airline - $airline")
        Some(airline)
      case None =>
        None
    }
  }
}

case class AirlineGrade(level: Int, reputationCeiling: Double, reputationFloor: Double, description: String){

  val getModelFamilyLimit =  {
    Math.max(2, level)
  }
}

trait GradeEvaluator {
  protected val grades: Vector[(Double, String)]

  def findGrade(value: Double): AirlineGrade = {
    val idx = grades.indexWhere(_._1 > value)

    if (idx == -1) {
      // Above highest threshold
      val (lastFloor, lastDesc) = grades.last
      AirlineGrade(grades.length, Double.MaxValue, lastFloor, lastDesc)
    } else {
      val floor = if (idx == 0) 0.0 else grades(idx - 1)._1
      val ceiling = grades(idx)._1
      AirlineGrade(idx, ceiling, floor, grades(idx)._2)
    }
  }
}

object AirlineGrades extends GradeEvaluator {
  override val grades: Vector[(Double, String)] = Vector(
    25.0 -> "Embryonic",
    50.0 -> "Sprout",
    75.0 -> "Fledgling",
    100.0 -> "Small Airline",
    125.0 -> "Minor Airline",
    150.0 -> "Established Airline",
    175.0 -> "Networked Airline",
    200.0 -> "Major Airline",
    240.0 -> "Leading Airline",
    280.0 -> "Ascending",
    320.0 -> "Skybound",
    360.0 -> "High Flyer",
    400.0 -> "Stratospheric",
    450.0 -> "Sub-Orbital",
    500.0 -> "Orbital",
    600.0 -> "Exospheric",
    700.0 -> "Apogee",
    800.0 -> "Epic",
    900.0 -> "Ultimate",
    1000.0 -> "Fabled",
    1100.0 -> "Legendary",
    1200.0 -> "Mythic",
    1400.0 -> "Celestial",
    1600.0 -> "Empyrean",
    1800.0 -> "Transcendent",
    2000.0 -> "Apex Rat"
  )

  val taxRate = List(
    25.0 -> 0,
    50.0 -> 0,
    75.0 -> 1,
    100.0 -> 1,
    125.0 -> 1,
    150.0 -> 2,
    175.0 -> 3,
    200.0 -> 4,
    240.0 -> 5,
    280.0 -> 6,
    320.0 -> 7,
    360.0 -> 8,
    400.0 -> 10,
    450.0 -> 13,
    500.0 -> 16,
    600.0 -> 19,
    700.0 -> 22,
    800.0 -> 24,
    900.0 -> 26,
    1000.0 -> 28,
    1100.0 -> 30,
    1200.0 -> 32,
    1400.0 -> 34,
    1600.0 -> 36,
    1800.0 -> 38,
    2000.0 -> 40,
  )

  def findTaxRate(reputation: Double) : Int = {
    taxRate.find(_._1 > reputation).getOrElse(taxRate.last)._2
  }
}

object AirlineGradeStockPrice extends GradeEvaluator {
  override val grades: Vector[(Double, String)] = Vector(
    0.32 -> "Toilet Paper",
    0.61 -> "Penny Stock",
    1.26 -> "Floundering",
    2.53 -> "Not Bankrupt",
    4.71 -> "Bargain?",
    8.76 -> "Promising",
    16.29 -> "On the Radar",
    30.30 -> "Industry Disruptor",
    56.36 -> "Blue Chip Beauty",
    104.83 -> "Flying Cash Cow",
    194.98 -> "Russell 2000 Company",
    362.66 -> "Megacorporation",
    674.55 -> "S&P 500 Company",
    1254.66 -> "Wall Street Legend",
    2333.67 -> "Aerial Singularity"
  )
}

object AirlineGradeElites extends GradeEvaluator {
  override val grades: Vector[(Double, String)] = Vector(
    100.0 -> "Nothing",
    450.0 -> "Paper",
    1650.0 -> "Nylon",
    4400.0 -> "Plastic",
    8000.0 -> "Fiberglass",
    12800.0 -> "Bronze",
    17400.0 -> "Steel",
    22000.0 -> "Aluminum",
    27000.0 -> "Silver",
    32000.0 -> "Gold",
    37000.0 -> "Platinum",
    42000.0 -> "Rhenium",
    47000.0 -> "Painite",
    52000.0 -> "Graphene",
    60000.0 -> "Aerogel"
  )
}

object AirlineGradeTouristsTravelers extends GradeEvaluator {
  override val grades: Vector[(Double, String)] = Vector(
       5000.0 -> "Discount Disaster",
      20000.0 -> "Leisure Loser",
      40000.0 -> "Semi Bargain Bin",
      80000.0 -> "Holiday Hauler",
     140000.0 -> "Package Deal Pal",
     240000.0 -> "Resort Runner",
     340000.0 -> "Deal Seeker Favorite",
     440000.0 -> "Bargain Bin Bonanza",
     550000.0 -> "Detours Delight",
     660000.0 -> "Cheapo Champion",
     770000.0 -> "Penny Pitchers' Paradise",
     880000.0 -> "Mega Mover",
    1000000.0 -> "Mega Mega Mega Mover",
    1100000.0 -> "Budget Behemoth",
    1200000.0 -> "Low-Cost Leviathan"
  )
}

object AirlineModifier {
  def fromValues(modifierType : AirlineModifierType.Value, creationCycle : Int, expiryCycle : Option[Int], properties : Map[AirlineModifierPropertyType.Value, Long]) : AirlineModifier = {
    import AirlineModifierType._
    val modifier = modifierType match {
      case NERFED => NerfedAirlineModifier(creationCycle)
      case BANNER_LOYALTY_BOOST => BannerLoyaltyAirlineModifier(
        properties(AirlineModifierPropertyType.STRENGTH).toInt,
        creationCycle)
    }

    modifier
  }
}



abstract class AirlineModifier(val modifierType : AirlineModifierType.Value, val creationCycle : Int, val expiryCycle : Option[Int], var id : Int = 0) extends IdObject {
  def properties : Map[AirlineModifierPropertyType.Value, Long]
  def isHidden : Boolean //should it be visible to admin only
}

case class NerfedAirlineModifier(override val creationCycle : Int) extends AirlineModifier(AirlineModifierType.NERFED, creationCycle, None) {
  val FULL_EFFECT_DURATION = 300 //completely kicks in after 100 cycles
  val FULL_COST_MULTIPLIER = 1.25
  val costMultiplier = (currentCycle : Int) => {
    val age = currentCycle - creationCycle
    if (age >= FULL_EFFECT_DURATION) {
      FULL_COST_MULTIPLIER
    } else if (age >= 0) {
      1 + age.toDouble / FULL_EFFECT_DURATION * (FULL_COST_MULTIPLIER - 1)
    } else {
      1
    }
  }

  override def properties : Map[AirlineModifierPropertyType.Value, Long] = Map.empty
  override def isHidden = true
}

case class BannerLoyaltyAirlineModifier(amount : Int, override val creationCycle : Int) extends AirlineModifier(AirlineModifierType.BANNER_LOYALTY_BOOST, creationCycle, Some(creationCycle +  10 * Period.yearLength)) {
  lazy val internalProperties = Map[AirlineModifierPropertyType.Value, Long](AirlineModifierPropertyType.STRENGTH -> amount)
  override def properties : Map[AirlineModifierPropertyType.Value, Long] = internalProperties
  override def isHidden = false
}


object AirlineModifierType extends Enumeration {
  type AirlineModifierType = Value
  val NERFED, DELEGATE_BOOST, BANNER_LOYALTY_BOOST = Value //todo: remove DELEGATE_BOOST
}

object AirlineModifierPropertyType extends Enumeration {
  type AirlineModifierPropertyType = Value
  val STRENGTH, DURATION = Value
}

case class NameHistory(name : String, updateTimestamp : Date)