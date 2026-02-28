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
  var stats = AirlineStat(0, 0, Period.WEEKLY, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)

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

  def doStockOp(isSellShares: Int = 1): (Double, Int, Long) = {
    airlineInfo.sharesOutstanding += 1_000_000 * isSellShares
    val balanceChange: Long = 1_000_000 * airlineInfo.stockPrice.toLong * isSellShares
    val fee = StockModel.STOCK_BROKER_FEE_BASE + 1_000_000 * airlineInfo.stockPrice.toLong * StockModel.STOCK_BROKER_FEE.toLong
    airlineInfo.stockPrice *= (1 - isSellShares * ThreadLocalRandom.current().nextDouble(StockModel.STOCK_BUYBACK_MIN_CHANGE, StockModel.STOCK_BUYBACK_MAX_CHANGE))
    airlineInfo.balance += balanceChange - fee
    AirlineSource.saveAirlineInfo(this)
    AirlineSource.saveCashFlowItem(AirlineCashFlowItem(id, CashFlowType.BUY_BACK, balanceChange - fee))
    (airlineInfo.stockPrice, airlineInfo.sharesOutstanding, airlineInfo.balance)
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

  def setAirlineCode(airlineCode : String) = {
    airlineInfo.airlineCode = airlineCode
  }

  def getAirlineCode() = {
    airlineInfo.airlineCode
  }

  def getMinimumRenewalBalance() = {
    airlineInfo.minimumRenewalBalance
  }

  def getPrestigePoints() = {
    airlineInfo.prestigePoints
  }

  def setSkipTutorial(value : Boolean) = {
    airlineInfo.skipTutorial = value
  }

  def isSkipTutorial = {
    airlineInfo.skipTutorial
  }

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

  lazy val slogan = AirlineSource.loadSlogan(id)
  lazy val previousNames = AirlineSource.loadPreviousNameHistory(id).sortBy(_.updateTimestamp.getTime)(Ordering.Long.reverse).map(_.name)

  def getDelegateInfo() : DelegateInfo = {
    val busyDelegates = DelegateSource.loadBusyDelegatesByAirline(id)
    val availableCount = delegateCount - busyDelegates.size

    DelegateInfo(availableCount, delegateBoosts, busyDelegates)
  }

  val BASE_DELEGATE_COUNT = 5
  val DELEGATE_PER_LEVEL = 3
  lazy val delegateCount = BASE_DELEGATE_COUNT +
    airlineGrade.level * DELEGATE_PER_LEVEL +
//disabled currently    AirlineSource.loadAirlineBasesByAirline(id).flatMap(_.specializations).count(_.getType == BaseSpecializationType.DELEGATE) +
    delegateBoosts.map(_.amount).sum
  lazy val delegateBoosts = AirlineSource.loadAirlineModifierByAirlineId(id).filter(_.modifierType == AirlineModifierType.DELEGATE_BOOST).map(_.asInstanceOf[DelegateBoostAirlineModifier])
}

case class DelegateInfo(availableCount : Int, boosts : List[DelegateBoostAirlineModifier], busyDelegates: List[BusyDelegate]) {
  //take away all the boosted ones that are unoccupied, those are not eligible for permanent tasks (country relation/campaign etc)
  val permanentAvailableCount = {
    val cooldownDelegateCount = busyDelegates.filter(_.availableCycle.isDefined).length
    val unoccupiedBonusDelegateCount = boosts.map(_.amount).sum - cooldownDelegateCount
    if (unoccupiedBonusDelegateCount > 0) {
      availableCount - unoccupiedBonusDelegateCount
    } else {
      availableCount
    }
  }

}

case class AirlineInfo(var balance : Long, var currentServiceQuality : Double, var stockPrice : Double, var sharesOutstanding : Int, var targetServiceQuality : Int, var reputation : Double, var minimumRenewalBalance: Long, var prestigePoints: Int,var countryCode : Option[String] = None, var airlineCode : String = "", var skipTutorial : Boolean = false, var initialized : Boolean = false)

object TransactionType extends Enumeration {
  type TransactionType = Value
  val CAPITAL_GAIN, CREATE_LINK = Value
}

object OtherIncomeItemType extends Enumeration {
  type OtherBalanceItemType = Value
  val LOAN_INTEREST, BASE_UPKEEP, OVERTIME_COMPENSATION, LOUNGE_UPKEEP, LOUNGE_COST, LOUNGE_INCOME, ASSET_EXPENSE, ASSET_REVENUE, ADVERTISEMENT, DEPRECIATION, FUEL_PROFIT = Value
}

object CashFlowType extends Enumeration {
  type CashFlowType = Value
  val BASE_CONSTRUCTION, BUY_AIRPLANE, SELL_AIRPLANE, CREATE_LINK, FACILITY_CONSTRUCTION, OIL_CONTRACT, ASSET_TRANSACTION, PRIZE, BUY_BACK = Value
}

object Period extends Enumeration {
  type Period = Value
  val WEEKLY, QUARTER, YEAR = Value

  def numberWeeks(period : Period.Value) = {
    period match {
      case WEEKLY => 1
      case QUARTER => 12
      case YEAR => 48
    }
  }
}


case class AirlineTransaction(airlineId : Int, transactionType : TransactionType.Value, amount : Long, var cycle : Int = 0)
case class AirlineIncome(airlineId : Int, profit : Long = 0, revenue: Long = 0, expense: Long = 0, stockPrice: Double = 0, totalValue: Long = 0, links : LinksIncome, transactions : TransactionsIncome, others : OthersIncome, period : Period.Value = Period.WEEKLY, var cycle : Int = 0) {
  /**
   * Current income is expected to be QUARTER/YEAR. Adds parameter (WEEKLY income) to this current income object and return a new Airline income with period same as this object but cycle as the parameter
   */
  def update(income2 : AirlineIncome) : AirlineIncome = {
    AirlineIncome(airlineId,
        profit = profit + income2.profit,
        revenue = revenue + income2.revenue,
        expense = expense + income2.expense,
        stockPrice = (stockPrice + income2.stockPrice) / 2,
        totalValue = ((totalValue + income2.totalValue).toDouble / 2).toLong,
        links = links.update(income2.links),
        transactions = transactions.update(income2.transactions),
        others = others.update(income2.others),
        period = period,
        cycle = income2.cycle)
  }
}
case class LinksIncome(airlineId : Int, profit : Long = 0, revenue : Long = 0, expense : Long = 0, ticketRevenue: Long = 0, airportFee : Long = 0, fuelCost : Long = 0, fuelTax : Long = 0, crewCost : Long = 0, inflightCost : Long = 0, delayCompensation : Long = 0, maintenanceCost: Long = 0, loungeCost : Long = 0, depreciation : Long = 0, period : Period.Value = Period.WEEKLY, var cycle : Int = 0) {
  def update(income2 : LinksIncome) : LinksIncome = {
    LinksIncome(airlineId,
        profit = profit + income2.profit,
        revenue = revenue + income2.revenue,
        expense = expense + income2.expense,
        ticketRevenue = ticketRevenue + income2.ticketRevenue,
        airportFee = airportFee + income2.airportFee,
        fuelCost = fuelCost + income2.fuelCost,
        fuelTax = fuelTax + income2.fuelTax,
        crewCost = crewCost + income2.crewCost,
        inflightCost = inflightCost + income2.inflightCost,
        delayCompensation = delayCompensation + income2.delayCompensation,
        maintenanceCost = maintenanceCost + income2.maintenanceCost,
        loungeCost = loungeCost + income2.loungeCost,
        depreciation = depreciation + income2.depreciation,
        period = period,
        cycle = income2.cycle)
  }
}
case class TransactionsIncome(airlineId : Int, profit : Long = 0, revenue: Long = 0, expense: Long = 0, capitalGain : Long = 0, createLink : Long = 0,  prize : Long = 0, buyBack : Long = 0, period : Period.Value = Period.WEEKLY, var cycle : Int = 0) {
  def update(income2 : TransactionsIncome) : TransactionsIncome = {
    TransactionsIncome(airlineId,
        profit = profit + income2.profit,
        revenue = revenue + income2.revenue,
        expense = expense + income2.expense,
        capitalGain = capitalGain + income2.capitalGain,
        createLink = createLink + income2.createLink,
        prize = prize + income2.prize,
        buyBack = buyBack + income2.buyBack,
        period = period,
        cycle = income2.cycle)
  }
}
case class OthersIncome(airlineId : Int, profit : Long = 0, revenue: Long = 0, expense: Long = 0, loanInterest : Long = 0, baseUpkeep : Long = 0, overtimeCompensation : Long = 0, advertisement : Long = 0, loungeUpkeep : Long = 0, loungeCost : Long = 0, loungeIncome : Long = 0, assetExpense : Long = 0, assetRevenue : Long = 0, fuelProfit : Long = 0, depreciation : Long = 0, period : Period.Value = Period.WEEKLY, var cycle : Int = 0) {
  def update(income2 : OthersIncome) : OthersIncome = {
    OthersIncome(airlineId,
        profit = profit + income2.profit,
        revenue = revenue + income2.revenue,
        expense = expense + income2.expense,
        loanInterest = loanInterest + income2.loanInterest,
        baseUpkeep = baseUpkeep + income2.baseUpkeep,
        overtimeCompensation = overtimeCompensation + income2.overtimeCompensation,
        advertisement = advertisement + income2.advertisement,
        loungeUpkeep = loungeUpkeep + income2.loungeUpkeep,
        loungeCost = loungeCost + income2.loungeCost,
        loungeIncome = loungeIncome + income2.loungeIncome,
        assetExpense = assetExpense + income2.assetExpense,
        assetRevenue = assetRevenue + income2.assetRevenue,
        fuelProfit = fuelProfit + income2.fuelProfit,
        depreciation = depreciation + income2.depreciation,
        period = period,
        cycle = income2.cycle)
  }
}


case class AirlineCashFlowItem(airlineId : Int, cashFlowType : CashFlowType.Value, amount : Long, var cycle : Int = 0)
case class AirlineCashFlow(airlineId : Int, cashFlow : Long = 0, operation : Long = 0, loanInterest : Long = 0, loanPrincipal : Long = 0, baseConstruction : Long = 0, buyAirplane : Long = 0, sellAirplane : Long = 0,  createLink : Long = 0, facilityConstruction : Long = 0, oilContract : Long = 0, assetTransactions : Long = 0, period : Period.Value = Period.WEEKLY, var cycle : Int = 0) {
/**
   * Current income is expected to be QUARTER/YEAR. Adds parameter (WEEKLY income) to this current income object and return a new Airline income with period same as this object but cycle as the parameter
   */
  def update(cashFlow2 : AirlineCashFlow) : AirlineCashFlow = {
    AirlineCashFlow(airlineId,
        cashFlow = cashFlow + cashFlow2.cashFlow,
        operation = operation + cashFlow2.operation,
        loanInterest = loanInterest + cashFlow2.loanInterest,
        loanPrincipal = loanPrincipal + cashFlow2.loanPrincipal,
        baseConstruction = baseConstruction + cashFlow2.baseConstruction,
        buyAirplane = buyAirplane + cashFlow2.buyAirplane,
        sellAirplane = sellAirplane + cashFlow2.sellAirplane,
        createLink = createLink + cashFlow2.createLink,
        facilityConstruction = facilityConstruction + cashFlow2.facilityConstruction,
        oilContract = oilContract + cashFlow2.oilContract,
        assetTransactions = assetTransactions + cashFlow2.assetTransactions,
        period = period,
        cycle = cashFlow2.cycle)
  }
}

object Airline {
  def fromId(id : Int) = {
    val airlineWithJustId = Airline("<unknown>")
    airlineWithJustId.id = id
    airlineWithJustId
  }
  val EQ_MAX : Double = 100 //employee quality
  val EQ_INTITIAL: Int = 35


  def resetAirline(airlineId : Int, newBalance : Long, resetExtendedInfo : Boolean = false) : Option[Airline] = {
    AirlineSource.loadAirlineById(airlineId, true) match {
      case Some(airline) =>
        // Will need this for prestige charm update after prestige info is processed
        val airlineHQ = airline.getHeadQuarter().get.airport
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
        AirplaneSource.deleteAirplanesByCriteria(List(("owner", airlineId)));
        ModelSource.deleteFavoriteModelId(airlineId);
        //remove all assets
        AirportAssetSource.loadAirportAssetsByAirline(airlineId).foreach { asset =>
          AirportAssetSource.deleteAirportAsset(asset.id)
        }
        //remove all bases
        airline.getBases().foreach(_.delete)
        //remove all loans
        BankSource.loadLoansByAirline(airlineId).foreach { loan =>
          BankSource.deleteLoan(loan.id)
        }
        // update prestige charm for the old airport HQ now prestige has been process and the airlines HQ cleared
        Prestige.updatePrestigeCharmForAirport(airlineHQ.id)
        //remove all oil contract
        OilSource.deleteOilContractByCriteria(List(("airline", airlineId)))
        //remove any temp delegates
        AirlineSource.deleteAirlineModifier(airline.id, AirlineModifierType.DELEGATE_BOOST)

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

        airline.removeCountryCode()
        airline.setTargetServiceQuality(EQ_INTITIAL)
        airline.setCurrentServiceQuality(0)
        airline.setReputation(0)

        LoyalistSource.deleteLoyalistsByAirline(airlineId)

        //reset all busy delegates
        DelegateSource.deleteBusyDelegateByCriteria(List(("airline", "=", airlineId)))

        //reset all campaigns, has to be after delegate
        CampaignSource.deleteCampaignsByAirline(airline.id)

        //reset all notice
        NoticeSource.deleteNoticesByAirline(airline.id)

        AirlineSource.saveAirlineInfo(airline)
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

object AirlineGrades {
  val grades = List(
    25 -> "Embryonic",
    50 -> "Sprout",
    75 -> "Fledgling",
    100 -> "Small Airline",
    125 -> "Minor Airline",
    150 -> "Established Airline",
    175 -> "Networked Airline",
    200 -> "Major Airline",
    240 -> "Leading Airline",
    280 -> "Ascending",
    320 -> "Skybound",
    360 -> "High Flyer",
    400 -> "Stratospheric",
    450 -> "Sub-Orbital",
    500 -> "Orbital",
    600 -> "Exospheric",
    700 -> "Apogee",
    800 -> "Epic",
    900 -> "Ultimate",
    1000 -> "Fabled",
    1100 -> "Legendary",
    1200 -> "Mythic",
    1400 -> "Celestial",
    1600 -> "Empyrean",
    1800 -> "Transcendent",
    2000 -> "Apex Rat"
  )

  val taxRate = List(
    25 -> 0,
    50 -> 0,
    75 -> 1,
    100 -> 1,
    125 -> 1,
    150 -> 2,
    175 -> 3,
    200 -> 4,
    240 -> 5,
    280 -> 6,
    320 -> 7,
    360 -> 8,
    400 -> 10,
    450 -> 13,
    500 -> 16,
    600 -> 19,
    700 -> 22,
    800 -> 24,
    900 -> 26,
    1000 -> 28,
    1100 -> 30,
    1200 -> 32,
    1400 -> 34,
    1600 -> 36,
    1800 -> 38,
    2000 -> 40,
  )

  def findTaxRate(reputation: Double) : Int = {
    taxRate.find(_._1 > reputation).getOrElse(taxRate.last)._2
  }

  def findGrade(value: Double): AirlineGrade = {
    val thresholds = grades.map(_._1.toDouble)
    val descriptions = grades.map(_._2)
    val idx = thresholds.indexWhere(_ > value)
    if (idx == -1) {
      // Above top level: use last grade, ceiling is infinity
      if (grades.isEmpty) {
        AirlineGrade(1, Double.MaxValue, 0.0, "Unknown")
      } else {
        val lastIdx = grades.length - 1
        val floor = thresholds(lastIdx)
        AirlineGrade(lastIdx + 1, Double.MaxValue, floor, descriptions(lastIdx))
      }
    } else {
      val floor = if (idx == 0) 0.0 else thresholds(idx - 1)
      val ceiling = thresholds(idx)
      AirlineGrade(idx + 1, ceiling, floor, descriptions(idx))
    }
  }
}

object AirlineGradeStockPrice {
  val grades: List[(Double, String)] = List(
    0.7 -> "Toilet Paper",
    1.2 -> "Penny Stock",
    3.5 -> "Bargain?",
    7.0 -> "Promising",
    16.0 -> "On the Radar",
    40.0 -> "Industry Disruptor",
    75.0 -> "Blue Chip Beauty",
    125.0 -> "Flying Cash Cow",
    215.0 -> "Russell 2000 Company",
    375.0 -> "Megacorporation",
    625.0 -> "S&P 500 Company",
    1075.0 -> "Wall Street Legend",
    1725.0 -> "Aerial Singularity"
  )

  def findGrade(value: Double): AirlineGrade = {
    val thresholds = grades.map(_._1)
    val descriptions = grades.map(_._2)
    val idx = thresholds.indexWhere(_ > value)
    if (idx == -1) {
      // Above top level: use last grade, ceiling is infinity
      if (grades.isEmpty) {
        // Fallback for empty grades list (defensive)
        AirlineGrade(1, Double.MaxValue, 0.0, "Unknown")
      } else {
        val lastIdx = grades.length - 1
        val floor = thresholds(lastIdx)
        AirlineGrade(lastIdx + 1, Double.MaxValue, floor, descriptions(lastIdx))
      }
    } else {
      val floor = if (idx == 0) 0.0 else thresholds(idx - 1)
      val ceiling = thresholds(idx)
      AirlineGrade(idx + 1, ceiling, floor, descriptions(idx))
    }
  }
}

object AirlineGradeElites {
  val grades = List(
    100 -> "Nothing",
    450 -> "Paper",
    1650 -> "Plastic",
    4400 -> "Iron",
    8000 -> "Steel",
    12800 -> "Aluminum",
    17400 -> "Nickel",
    22000 -> "Copper",
    27000 -> "Gold",
    32000 -> "Platinum",
    37000 -> "Rhenium",
    42000 -> "Painite",
    47000 -> "Rat Fur"
  )

  def findGrade(value: Int): AirlineGrade = {
    val thresholds = grades.map(_._1.toDouble)
    val descriptions = grades.map(_._2)
    val idx = thresholds.indexWhere(_ > value)
    if (idx == -1) {
      // Above top level: use last grade, ceiling is infinity
      if (grades.isEmpty) {
        // Fallback for empty grades list (defensive)
        AirlineGrade(1, Double.MaxValue, 0.0, "Unknown")
      } else {
        val lastIdx = grades.length - 1
        val floor = thresholds(lastIdx)
        AirlineGrade(lastIdx + 1, Double.MaxValue, floor, descriptions(lastIdx))
      }
    } else {
      val floor = if (idx == 0) 0.0 else thresholds(idx - 1)
      val ceiling = thresholds(idx)
      AirlineGrade(idx + 1, ceiling, floor, descriptions(idx))
    }
  }
}

object AirlineGradeTouristsTravelers {
  val grades = List(
    1500 -> "Discount Disaster",
    6000 -> "Leisure Loser",
    22500 -> "Semi Bargain Bin",
    63000 -> "Holiday Hauler",
    126000 -> "Package Deal Pal",
    201000 -> "Resort Runner",
    282000 -> "Deal Seeker Favorite",
    375000 -> "Bargain Bin Bonanza",
    480000 -> "Detours Delight",
    585000 -> "Cheapo Champion",
    690000 -> "Penny Pitchers' Paradise",
    780000 -> "Budget Behemoth",
    900000 -> "Low-Cost Leviathan"
  )

  def findGrade(value: Int): AirlineGrade = {
    val thresholds = grades.map(_._1.toDouble)
    val descriptions = grades.map(_._2)
    val idx = thresholds.indexWhere(_ > value)
    if (idx == -1) {
      // Above top level: use last grade, ceiling is infinity
      if (grades.isEmpty) {
        // Fallback for empty grades list (defensive)
        AirlineGrade(1, Double.MaxValue, 0.0, "Unknown")
      } else {
        val lastIdx = grades.length - 1
        val floor = thresholds(lastIdx)
        AirlineGrade(lastIdx + 1, Double.MaxValue, floor, descriptions(lastIdx))
      }
    } else {
      val floor = if (idx == 0) 0.0 else thresholds(idx - 1)
      val ceiling = thresholds(idx)
      AirlineGrade(idx + 1, ceiling, floor, descriptions(idx))
    }
  }
}

object AirlineModifier {
  def fromValues(modifierType : AirlineModifierType.Value, creationCycle : Int, expiryCycle : Option[Int], properties : Map[AirlineModifierPropertyType.Value, Long]) : AirlineModifier = {
    import AirlineModifierType._
    val modifier = modifierType match {
      case NERFED => NerfedAirlineModifier(creationCycle)
      case DELEGATE_BOOST => DelegateBoostAirlineModifier(
        properties(AirlineModifierPropertyType.STRENGTH).toInt,
        properties(AirlineModifierPropertyType.DURATION).toInt,
        creationCycle)
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

case class DelegateBoostAirlineModifier(amount : Int, duration : Int, override val creationCycle : Int) extends AirlineModifier(AirlineModifierType.DELEGATE_BOOST, creationCycle, Some(creationCycle + duration)) {
  lazy val internalProperties = Map[AirlineModifierPropertyType.Value, Long](AirlineModifierPropertyType.STRENGTH -> amount , AirlineModifierPropertyType.DURATION -> duration)
  override def properties : Map[AirlineModifierPropertyType.Value, Long] = internalProperties
  override def isHidden = true
}

case class BannerLoyaltyAirlineModifier(amount : Int, override val creationCycle : Int) extends AirlineModifier(AirlineModifierType.BANNER_LOYALTY_BOOST, creationCycle, Some(creationCycle +  10 * 52)) {
  lazy val internalProperties = Map[AirlineModifierPropertyType.Value, Long](AirlineModifierPropertyType.STRENGTH -> amount)
  override def properties : Map[AirlineModifierPropertyType.Value, Long] = internalProperties
  override def isHidden = false
}


object AirlineModifierType extends Enumeration {
  type AirlineModifierType = Value
  val NERFED, DELEGATE_BOOST, BANNER_LOYALTY_BOOST = Value
}

object AirlineModifierPropertyType extends Enumeration {
  type AirlineModifierPropertyType = Value
  val STRENGTH, DURATION = Value
}

case class NameHistory(name : String, updateTimestamp : Date)