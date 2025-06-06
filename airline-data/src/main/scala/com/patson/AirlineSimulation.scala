package com.patson

import com.patson.model._
import com.patson.data._

import scala.collection.mutable._
import scala.collection.immutable
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import com.patson.model.airplane.Airplane
import com.patson.model.notice.GameOverNotice
import com.patson.model.oil.OilPrice
import com.patson.model.oil.OilInventoryPolicy
import com.patson.model.oil.OilConsumptionHistory
import com.patson.model.oil.OilConsumptionType
import com.patson.model.RankingLeaderboards
import com.patson.util.{AirportChampionInfo, ChampionUtil, CountryChampionInfo}
import java.util.concurrent.ThreadLocalRandom

object AirlineSimulation {
  val MAX_SERVICE_QUALITY_INCREMENT : Double = 0.5
  val MAX_SERVICE_QUALITY_DECREMENT : Double = 10
  val MAX_REPUTATION_DELTA = 1
  val MINIMUM_CASH_BALANCE_STOCKS = 10000000 //10M
  val BANKRUPTCY_ASSETS_THRESHOLD = -50000000 //-50M
  val BANKRUPTCY_CASH_THRESHOLD = -10000000 //-10M
  val BOOKKEEPING_ENTRIES_COUNT = 25

  def airlineSimulation(cycle: Int, flightLinkResult: List[LinkConsumptionDetails], loungeResult: scala.collection.immutable.Map[Lounge, LoungeConsumptionDetails], airplanes: List[Airplane], paxStats: immutable.Map[Int, AirlinePaxStat]) = {
    val allAirlines = AirlineSource.loadAllAirlines(true)
    val allLinks = LinkSource.loadAllLinks(LinkSource.FULL_LOAD)
    val allFlightLinksByAirlineId = allLinks.filter(_.transportType == TransportType.FLIGHT).map(_.asInstanceOf[Link]).groupBy(_.airline.id)
    val allTransactions = AirlineSource.loadTransactions(cycle).groupBy { _.airlineId }
    val allTransactionalCashFlowItems: scala.collection.immutable.Map[Int, List[AirlineCashFlowItem]] = AirlineSource.loadCashFlowItems(cycle).groupBy { _.airlineId }
    //purge the older transactions
    AirlineSource.deleteTransactions(cycle - 1)
    AirlineSource.deleteCashFlowItems(cycle - 1)
    val flightLinkResultByAirline = flightLinkResult.groupBy(_.link.airline.id)

    val airplanesByAirline = airplanes.groupBy(_.owner.id)

    val loungesByAirlineId = AirlineSource.loadAllLounges().groupBy(_.airline.id)

    val assetsByAirlineId = AirportAssetSource.loadAirportAssetsByAssetCriteria(List.empty).groupBy(_.airline.get.id) //load all owned assets

    val allIncomes = ListBuffer[AirlineIncome]()
    val allCashFlows = ListBuffer[AirlineCashFlow]() //cash flow for accounting purpose
    val allAirlineStats = ListBuffer[AirlineStat]()

    val currentCycle = MainSimulation.currentWeek
    //val champions : scala.collection.immutable.Map[Airline, List[ChampionInfo]] = ChampionUtil.getAllCountryChampionInfo().groupBy(_.airline)
    val airportChampionsByAirlineId : immutable.Map[Int, List[AirportChampionInfo]] = ChampionUtil.loadAirportChampionInfo().groupBy(_.loyalist.airline.id)
    val titlesByCountryCodeAndAirlineId : immutable.Map[(String, Int), List[CountryAirlineTitle]]= CountrySource.loadCountryAirlineTitlesByCriteria(List.empty).groupBy(entry => (entry.country.countryCode, entry.airline.id)) //key is (CountryCode, AirlineId)
    val cashFlows = Map[Airline, Long]() //cash flow for actual deduction

    val alliances = AllianceSource.loadAllAlliances()
    val allianceByAirlineId :scala.collection.immutable.Map[Int, Alliance] = alliances.flatMap { alliance => (alliance.members.filter(_.role != AllianceRole.APPLICANT).map(member => (member.airline.id, alliance))) }.toMap
    val allianceRankings = Alliance.getRankings(alliances)

    println("Generating Rankings")
    //todo: pass airline stats so don't need to recalculate
    val rankingsByAirlineId = RankingLeaderboards.getAirlineReputationPrizes(true)

    val fuelContractsByAirlineId = OilSource.loadAllOilContracts().groupBy(contract => contract.airline.id)
    val fuelInventoryPolicyByAirlineId = OilSource.loadAllOilInventoryPolicies.map(policy => (policy.airline.id, policy)).toMap
    val currentFuelPrice = OilSource.loadOilPriceByCycle(cycle).get.price
    val oilConsumptionEntries = ListBuffer[OilConsumptionHistory]()

    allAirlines.foreach { airline =>
        val airlineStat = paxStats.getOrElse(airline.id, AirlinePaxStat(0,0,0,0,0))
        var totalCashRevenue = 0L
        var totalCashExpense = 0L
        var linksDepreciation = 0L
        val othersSummary = Map[OtherIncomeItemType.Value, Long]()
        val airlineValue = Computation.getResetAmount(airline.id)

        //update reputation && stock price
        val currentReputation = airline.getReputation()
        val reputationBreakdowns = ListBuffer[ReputationBreakdown]()

        val reputationByAircraftTypes = airplanesByAirline.get(airline.id) match {
          case Some(airplanes) =>
            val modelSet = airplanes.map(_.model).toSet
            if (modelSet.size >= 12) {
              25
            } else if (modelSet.size >= 8) {
              15
            } else if (modelSet.size >= 4) {
              5
            } else {
              0
            }
          case None =>
            0
        }
        reputationBreakdowns.append(ReputationBreakdown(ReputationType.MILESTONE_AIRCRAFT_TYPES, reputationByAircraftTypes))

      val airlineRegionalBonus = if (airline.airlineType == AirlineType.REGIONAL) 2 else 1
      val reputationBycodeshares =
        if (airlineStat.codeshares > 500_000) {
          80
        } else if (airlineStat.codeshares > 75_000) {
          50
        } else if (airlineStat.codeshares > 5000) {
          30
        } else if (airlineStat.codeshares > 500) {
          15
        } else {
          0
        } * airlineRegionalBonus
      reputationBreakdowns.append(ReputationBreakdown(ReputationType.MILESTONE_codeshares, reputationBycodeshares))

        val reputationByCountries = flightLinkResultByAirline.get(airline.id) match {
          case Some(linkConsumptions) =>
            val uniqueCountryCount = linkConsumptions.map(links => links.link.to.countryCode ++ links.link.from.countryCode).toSet.size
            if (uniqueCountryCount >= 100) {
              75
            } else if (uniqueCountryCount >= 80) {
              45
            } else if (uniqueCountryCount >= 40) {
              30
            } else if (uniqueCountryCount >= 15) {
              15
            } else {
              0
            }
          case None =>
            0
        }
        reputationBreakdowns.append(ReputationBreakdown(ReputationType.MILESTONE_COUNTRIES, reputationByCountries))

        val reputationByPaxKm = flightLinkResultByAirline.get(airline.id) match {
          case Some(linkConsumptions) =>
            val totalPassengerKilometers = linkConsumptions.foldLeft(0L) { (accumulator, linkConsumption) =>
              accumulator + linkConsumption.link.soldSeats.total * linkConsumption.link.distance
            }
            if (totalPassengerKilometers > 2_000_000_000) {
              60
            } else if (totalPassengerKilometers > 500_000_000) {
              45
            } else if (totalPassengerKilometers > 25_000_000) {
              30
            } else if (totalPassengerKilometers > 50_000) {
              15
            } else {
              0
            }
          case None =>
            0
        }
        reputationBreakdowns.append(ReputationBreakdown(ReputationType.MILESTONE_PASSENGERS, reputationByPaxKm))

        val reputationByAirportChampions = airportChampionsByAirlineId.get(airline.id) match {
          case Some(airportChampions) => airportChampions.map(_.reputationBoost).sum
          case None => 0
        }
        reputationBreakdowns.append(ReputationBreakdown(ReputationType.AIRPORT_LOYALIST_RANKING, reputationByAirportChampions))

        val airlineTouristBonus = if (airline.airlineType == AirlineType.DISCOUNT) 2 else 1
        val reputationByTourists = 25 * airlineTouristBonus * AirlineGradeTourists.findGrade(airlineStat.tourists).level
        reputationBreakdowns.append(ReputationBreakdown(ReputationType.TOURISTS, reputationByTourists))

        val airlineEliteBonus = if (airline.airlineType == AirlineType.LUXURY) 2 else 1
        val reputationByElites = 25 * airlineEliteBonus * AirlineGradeElites.findGrade(airlineStat.elites).level
        reputationBreakdowns.append(ReputationBreakdown(ReputationType.ELITES, reputationByElites))

        val reputationBonusFromAlliance: Double = allianceByAirlineId.get(airline.id) match {
          case Some(alliance) => allianceRankings.get(alliance) match {
            case Some((ranking, _)) => Alliance.getReputationBonus(ranking)
            case None => 0.0
          }
          case None => 0.0
        }
        reputationBreakdowns.append(ReputationBreakdown(ReputationType.ALLIANCE_BONUS, reputationBonusFromAlliance))

        val reputationBonusFromLeaderboards: Double = rankingsByAirlineId.getOrElse(airline.id, 0)
        reputationBreakdowns.append(ReputationBreakdown(ReputationType.LEADERBOARD_BONUS, reputationBonusFromLeaderboards))

        //stock price
        val stockPrice = 0 //todo!
        val reputationByStockPrice = 10 * airline.airlineGradeStockPrice.level
        reputationBreakdowns.append(ReputationBreakdown(ReputationType.STOCK_PRICE, reputationByStockPrice))

        //set reputation
        val finalBreakdowns = ReputationBreakdowns(reputationBreakdowns.toList)
        AirlineSource.updateReputationBreakdowns(airline.id, finalBreakdowns)
        val beginnerAirlineCeiling = if (airline.airlineType == AirlineType.BEGINNER) 250 else Int.MaxValue
        var targetReputation = Math.min(beginnerAirlineCeiling, finalBreakdowns.total)
        if (targetReputation > currentReputation && targetReputation - currentReputation > MAX_REPUTATION_DELTA) {
          targetReputation = currentReputation + MAX_REPUTATION_DELTA //make sure it increases/decreases gradually based on passenger volume
        } else if (targetReputation < currentReputation && currentReputation - targetReputation > MAX_REPUTATION_DELTA) {
          targetReputation = currentReputation - MAX_REPUTATION_DELTA
        }
        airline.setReputation(targetReputation)


        //income statement
        val isBankrupt = if (airlineValue.overall < BANKRUPTCY_ASSETS_THRESHOLD && airlineValue.existingBalance < BANKRUPTCY_CASH_THRESHOLD) {
          true
        } else {
          false
        }
        if (isBankrupt) {
          println(s"Resetting $airline due to negative value")
          Airline.resetAirline(airline.id, newBalance = 0, resetExtendedInfo = true)
          NoticeSource.saveTrackingNotice(airline.id, GameOverNotice())
        }

        val linksIncome = flightLinkResultByAirline.get(airline.id) match {
          case Some(linkConsumptions) => {
            val linksProfit = linkConsumptions.foldLeft(0L)(_ + _.profit)
            val linksAirportFee = linkConsumptions.foldLeft(0L)(_ + _.airportFees)
            val linksCrewCost = linkConsumptions.foldLeft(0L)(_ + _.crewCost)
            val linksFuelCost = linkConsumptions.foldLeft(0L)(_ + _.fuelCost)
            val linksFuelTax = linkConsumptions.foldLeft(0L)(_ + _.fuelTax)
            val linksInflightCost = linkConsumptions.foldLeft(0L)(_ + _.inflightCost)
            val linksDelayCompensation = linkConsumptions.foldLeft(0L)(_ + _.delayCompensation)
            val linksMaintenanceCost = linkConsumptions.foldLeft(0L)(_ + _.maintenanceCost)
            linksDepreciation = linkConsumptions.foldLeft(0L)(_ + _.depreciation)
            val linksLoungeCost = linkConsumptions.foldLeft(0L)(_ + _.loungeCost)
            val linksRevenue = linkConsumptions.foldLeft(0L)(_ + _.revenue)

            val linksExpense = linksAirportFee + linksCrewCost + linksFuelCost + linksFuelTax + linksInflightCost + linksDelayCompensation + linksMaintenanceCost + linksDepreciation + linksLoungeCost

            totalCashRevenue += linksRevenue
            totalCashExpense += linksExpense - linksDepreciation //airplane depreciation is already deducted on the plane, not a cash expense
            LinksIncome(airline.id, profit = linksProfit, revenue = linksRevenue, expense = linksExpense, ticketRevenue = linksRevenue, airportFee = -1 * linksAirportFee, fuelCost = -1 * linksFuelCost, fuelTax = -1 * linksFuelTax, crewCost = -1 * linksCrewCost, inflightCost = -1 * linksInflightCost, delayCompensation = -1 * linksDelayCompensation, maintenanceCost= -1 * linksMaintenanceCost, loungeCost= -1 * linksLoungeCost,  depreciation = -1 * linksDepreciation, cycle = currentCycle)
          }
          case None => LinksIncome(airline.id, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, cycle = currentCycle)
        }

        val transactionsIncome = allTransactions.get(airline.id) match {
          case Some(transactions) => {
            var expense = 0L
            var revenue = 0L
            val summary = Map[TransactionType.Value, Long]()
            transactions.foreach { transaction =>
              if (transaction.amount >= 0) {
                revenue += transaction.amount
              } else {
                expense -= transaction.amount
              }

              val existingAmount = summary.getOrElse(transaction.transactionType, 0L)
              summary.put(transaction.transactionType, existingAmount + transaction.amount)
            }
            TransactionsIncome(airline.id, revenue - expense, revenue, expense, capitalGain = summary.getOrElse(TransactionType.CAPITAL_GAIN, 0), createLink = summary.getOrElse(TransactionType.CREATE_LINK, 0), cycle = currentCycle)
          }
          case None => TransactionsIncome(airline.id, 0, 0, 0, capitalGain = 0, createLink = 0, cycle = currentCycle)
        }

        val baseUpkeep = airline.bases.foldLeft(0L)((upkeep, base) => {
          upkeep + base.getUpkeep
        })
        othersSummary.put(OtherIncomeItemType.BASE_UPKEEP, -1 * baseUpkeep) //negative number
        totalCashExpense += baseUpkeep

        //overtime compensation
        val linksByFromAirportId = allFlightLinksByAirlineId.get(airline.id).getOrElse(List.empty).groupBy(_.from.id)
        var overtimeCompensation = 0
        airline.bases.foreach { base =>
          val staffRequired = linksByFromAirportId.get(base.airport.id) match {
            case Some(links) => links.map(_.getCurrentOfficeStaffRequired).sum
            case None => 0
          }
          val titleOption : Option[Title.Value] = titlesByCountryCodeAndAirlineId.get((base.airport.countryCode, airline.id)) match {
            case Some(titles) =>
              if (titles.length > 0) Some(titles(0).title) else None //now only 1 title per airline per country
            case None => None
          }
          val compensationOfThisBase = base.getOvertimeCompensation(staffRequired)
          overtimeCompensation += compensationOfThisBase
        }

        othersSummary.put(OtherIncomeItemType.OVERTIME_COMPENSATION, -1 * overtimeCompensation) //negative number
        totalCashExpense += overtimeCompensation


        val allAirplanesDepreciation = airplanesByAirline.getOrElse(airline.id, List.empty).foldLeft(0L) {
          case(depreciation, airplane) => (depreciation + airplane.depreciationRate)
        }
        val unassignedAirplanesDepreciation = allAirplanesDepreciation - linksDepreciation //account depreciation on planes that are without assigned links
        othersSummary.put(OtherIncomeItemType.DEPRECIATION, -1 * unassignedAirplanesDepreciation) //not a cash expense

        val negativeCashInterest = if (airlineValue.existingBalance < 0) {
          (airlineValue.existingBalance * LoanInterestRateSimulation.HIGH_RATE_THRESHOLD / 52).toLong //give high interest
        } else {
          0L
        }
        totalCashExpense += -1 * negativeCashInterest

        val (loanPayment, interestPayment) = updateLoans(airline, currentCycle + 1) //have to plus one here, as this is supposed to be done postcycle, but for accounting purpose we have to put it here
        othersSummary.put(OtherIncomeItemType.LOAN_INTEREST, -1 * interestPayment + negativeCashInterest)
        totalCashExpense += loanPayment //paying both principle + interest

        val loungeUpkeep = loungesByAirlineId.get(airline.id) match {
          case Some(lounges) => lounges.map(_.getUpkeep).sum
          case None => 0
        }
        var loungeCost = 0L
        var loungeIncome = 0L;

        loungeResult.filter(_._1.airline.id == airline.id).map {
          case (_, LoungeConsumptionDetails(_, selfVisitors, allianceVisitors, _)) => {
            loungeCost += (selfVisitors + allianceVisitors) * Lounge.PER_VISITOR_COST
            loungeIncome += (selfVisitors + allianceVisitors) * Lounge.PER_VISITOR_CHARGE
          }
        }
        othersSummary.put(OtherIncomeItemType.LOUNGE_UPKEEP, -1 * loungeUpkeep)
        othersSummary.put(OtherIncomeItemType.LOUNGE_COST, -1 * loungeCost)
        othersSummary.put(OtherIncomeItemType.LOUNGE_INCOME, loungeIncome)

        totalCashExpense += loungeUpkeep + loungeCost
        totalCashRevenue += loungeIncome

        val (assetExpense, assetRevenue) = assetsByAirlineId.get(airline.id) match {
          case Some(assets) => (assets.map(_.expense).sum, assets.map(_.revenue).sum)
          case None => (0L, 0L)
        }

        othersSummary.put(OtherIncomeItemType.ASSET_EXPENSE, -1 * assetExpense)
        othersSummary.put(OtherIncomeItemType.ASSET_REVENUE, assetRevenue)

        totalCashExpense += assetExpense
        totalCashRevenue += assetRevenue


        //calculate extra cash flow due to difference in fuel cost
        val accountingFuelCost = linksIncome.fuelCost * -1
        val barrelsUsed = (accountingFuelCost / OilPrice.DEFAULT_PRICE).toInt
        val actualFuelCost = fuelContractsByAirlineId.get(airline.id) match {
          case Some(contracts) =>
            val totalPaymentFromContract = contracts.map{ contract =>
                oilConsumptionEntries += OilConsumptionHistory(airline, contract.contractPrice, contract.volume, OilConsumptionType.CONTRACT, currentCycle)
                contract.contractPrice * contract.volume
              }.sum

            val totalVolumeFromContract = contracts.map(_.volume).sum
            if (totalVolumeFromContract <= barrelsUsed) {
              //has to use inventory
              val inventoryPolicy = fuelInventoryPolicyByAirlineId.getOrElse(airline.id, OilInventoryPolicy.getDefaultPolicy(airline))
              val inventoryPrice = inventoryPolicy.inventoryPrice(currentFuelPrice)
              val volumeFromInventory = barrelsUsed - totalVolumeFromContract
              val consumptionType = if (inventoryPolicy.factor == 0) OilConsumptionType.MARKET else OilConsumptionType.INVENTORY
              if (volumeFromInventory > 0) {
                oilConsumptionEntries += OilConsumptionHistory(airline, inventoryPrice, volumeFromInventory, consumptionType, currentCycle)
              }
              totalPaymentFromContract + volumeFromInventory * inventoryPrice
            } else { //excessive
              //sell to someone else
              val sellPrice = currentFuelPrice / 2
              val excessBarrles = totalVolumeFromContract - barrelsUsed
              oilConsumptionEntries += OilConsumptionHistory(airline, sellPrice, excessBarrles * -1, OilConsumptionType.EXCESS, currentCycle)
              totalPaymentFromContract - excessBarrles * sellPrice //and sell the rest half market price
            }
          case None =>
            val inventoryPolicy = fuelInventoryPolicyByAirlineId.getOrElse(airline.id, OilInventoryPolicy.getDefaultPolicy(airline))
            val inventoryPrice = inventoryPolicy.inventoryPrice(currentFuelPrice)
            val consumptionType = if (inventoryPolicy.factor == 0) OilConsumptionType.MARKET else OilConsumptionType.INVENTORY
            if (barrelsUsed > 0) {
              oilConsumptionEntries += OilConsumptionHistory(airline, inventoryPrice, barrelsUsed, consumptionType, currentCycle)
            }
            barrelsUsed * inventoryPrice
        }

        val fuelProfit = accountingFuelCost - actualFuelCost.toLong
        if (fuelProfit > 0) {
          totalCashRevenue += fuelProfit
        } else {
          totalCashExpense += fuelProfit * -1
        }
//        println("airline " + airline)
//        println("barrels used: " + barrelsUsed + " acc. fuel cost " + accountingFuelCost + " actual fuel cost " + actualFuelCost.toLong + " profit " + fuelProfit)

        othersSummary.put(OtherIncomeItemType.FUEL_PROFIT, fuelProfit)

      //campaign/ads
        val advertisementCost = DelegateSource.loadCampaignTasksByAirlineId(airline.id).map(_.cost).sum
        if (advertisementCost > 0) {
          totalCashExpense += advertisementCost
        }
        othersSummary.put(OtherIncomeItemType.ADVERTISEMENT, advertisementCost * -1)


        var othersRevenue = 0L
        var othersExpense = 0L
        othersSummary.foreach {
          case (_, amount) => {
            if (amount >= 0) {
              othersRevenue += amount
            } else {
              othersExpense -= amount
            }
          }
        }

        val othersIncome = OthersIncome(airline.id, othersRevenue - othersExpense, othersRevenue, othersExpense
            , loanInterest = othersSummary.getOrElse(OtherIncomeItemType.LOAN_INTEREST, 0)
            , baseUpkeep = othersSummary.getOrElse(OtherIncomeItemType.BASE_UPKEEP, 0)
            , overtimeCompensation = othersSummary.getOrElse(OtherIncomeItemType.OVERTIME_COMPENSATION, 0)
            , advertisement = othersSummary.getOrElse(OtherIncomeItemType.ADVERTISEMENT, 0)
            , loungeUpkeep = othersSummary.getOrElse(OtherIncomeItemType.LOUNGE_UPKEEP, 0)
            , loungeCost = othersSummary.getOrElse(OtherIncomeItemType.LOUNGE_COST, 0)
            , loungeIncome = othersSummary.getOrElse(OtherIncomeItemType.LOUNGE_INCOME, 0)
            , assetExpense = othersSummary.getOrElse(OtherIncomeItemType.ASSET_EXPENSE, 0)
            , assetRevenue = othersSummary.getOrElse(OtherIncomeItemType.ASSET_REVENUE, 0)
            , fuelProfit = othersSummary.getOrElse(OtherIncomeItemType.FUEL_PROFIT, 0)
            , depreciation = othersSummary.getOrElse(OtherIncomeItemType.DEPRECIATION, 0)
            , cycle = currentCycle
        )


        val airlineRevenue = linksIncome.revenue + transactionsIncome.revenue + othersIncome.revenue
        val airlineExpense = linksIncome.expense + transactionsIncome.expense + othersIncome.expense
        val airlineProfit = airlineRevenue - airlineExpense

        val airlineWeeklyIncome = AirlineIncome(airline.id, airlineProfit, airlineRevenue, airlineExpense, stockPrice, airlineValue.overall, linksIncome, transactionsIncome, othersIncome, cycle = currentCycle)
        allIncomes += airlineWeeklyIncome
        allIncomes ++= computeAccumulateIncome(airlineWeeklyIncome)

        //cash flow computation
        val totalCashFlow = totalCashRevenue - totalCashExpense

        val operationCashFlow = totalCashFlow + loanPayment //exclude both interest and principle here, which WAS included in the total cash flow
        if (!isBankrupt) {
          cashFlows.put(airline, totalCashFlow) //this is week end flow, used for actual adjustment
        }

        //below is for accounting purpose
        //cash flow item that is already applied during this week, still need to load them for accounting purpose
        val transactionalCashFlowItems : scala.collection.immutable.Map[CashFlowType.Value, Long] = allTransactionalCashFlowItems.get(airline.id) match {
          case Some(items) => items.groupBy(_.cashFlowType).view.mapValues( itemsByType => itemsByType.map(_.amount).sum).toMap
          case None => scala.collection.immutable.Map.empty
        }

        //include cash flow during the week, only use for accounting purpose here
        val baseConstruction = transactionalCashFlowItems.getOrElse(CashFlowType.BASE_CONSTRUCTION, 0L)
        val buyAirplane = transactionalCashFlowItems.getOrElse(CashFlowType.BUY_AIRPLANE, 0L)
        val sellAirplane = transactionalCashFlowItems.getOrElse(CashFlowType.SELL_AIRPLANE, 0L)
        val createLink = transactionalCashFlowItems.getOrElse(CashFlowType.CREATE_LINK, 0L)
        val facilityConstruction = transactionalCashFlowItems.getOrElse(CashFlowType.FACILITY_CONSTRUCTION, 0L)
        val oilContract = transactionalCashFlowItems.getOrElse(CashFlowType.OIL_CONTRACT, 0L)
        val assetTransactions = transactionalCashFlowItems.getOrElse(CashFlowType.ASSET_TRANSACTION, 0L)

        val accountingCashFlow = totalCashFlow + baseConstruction + buyAirplane + sellAirplane + createLink + facilityConstruction + oilContract + assetTransactions

        val loanPrincipal = loanPayment - interestPayment
        val airlineWeeklyCashFlow = {
          if (isBankrupt) {
            AirlineCashFlow(airline.id, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, cycle = currentCycle)
          } else {
            AirlineCashFlow(airline.id, cashFlow = accountingCashFlow, operation = operationCashFlow, loanInterest = interestPayment * -1, loanPrincipal = loanPrincipal * -1, baseConstruction = baseConstruction, buyAirplane = buyAirplane, sellAirplane = sellAirplane, createLink = createLink, facilityConstruction = facilityConstruction, oilContract = oilContract, assetTransactions = assetTransactions, cycle = currentCycle)
          }
        }
        allCashFlows += airlineWeeklyCashFlow
        allCashFlows ++= computeAccumulateCashFlow(airlineWeeklyCashFlow)

      // AirlineStats
      var calculatedRASK = 0.0
      var calculatedCASK = 0.0
      var calculatedSatisfaction = 0.0 // Default to 0, could use Option or NaN if preferred
      var calculatedLoadFactor = 0.0
      var calculatedOnTime = 1.0 // Default to 100% on time if no flights

      flightLinkResultByAirline.get(airline.id) match {
        case Some(linkConsumptions) if linkConsumptions.nonEmpty =>
          val intermediateStats = linkConsumptions.foldLeft((0.0, 0.0, 0.0, 0.0, 0.0)) {
            // Accumulator: (ask, satisfactionSum, loadFactorSum, flights, majorDelays, minorDelays)
            case ((askAcc, satAcc, lfAcc, flightsAcc, delayAcc), consumption) =>
              val link = consumption.link
              val ASK = link.capacity.total * link.distance
              (
                askAcc + ASK,
                satAcc + consumption.satisfaction,
                lfAcc + link.soldSeats.total.toDouble / link.capacity.total,
                flightsAcc + link.frequency,
                delayAcc + link.majorDelayCount * 3 + link.minorDelayCount.toDouble / 2 //major 6x minor because that's the cost multiplier, maybe a cascading delay :)
              )
          }

          val totalASK = intermediateStats._1
          val totalSatisfactionSum = intermediateStats._2
          val totalLoadFactorSum = intermediateStats._3
          val totalFlights = intermediateStats._4
          val totalDelays = intermediateStats._5

          val numLinks = linkConsumptions.size

          // Calculate RASK & CASK using linksIncome values & base costs and calculated ASK
          if (totalASK > 0) {
            calculatedRASK = (linksIncome.revenue).toDouble / totalASK
            calculatedCASK = (linksIncome.expense + -1 * (othersIncome.baseUpkeep + othersIncome.overtimeCompensation) + linksIncome.fuelTax).toDouble / totalASK //fuelTax is already negative
          }

          // Calculate Satisfaction & Load Factor averages
          if (numLinks > 0) {
            calculatedSatisfaction = totalSatisfactionSum / numLinks
            calculatedLoadFactor = totalLoadFactorSum / numLinks
          }

          // Calculate On-Time percentage
          if (totalFlights > 0) {
            val onTimeFlights = totalFlights - totalDelays
            calculatedOnTime = Math.max(0.0, onTimeFlights / totalFlights)
          } // Else onTime remains 1.0

        case _ => // No link consumptions for this airline or list is empty
        // All calculated stats remain at their default values (0.0 or 1.0 for onTime)
        // RASK/CASK are 0.0 since linksIncome.revenue/expense would also be 0 in this case.
      }

      // Create and Add AirlineStat Instance using calculated values and paxStat
      val currentAirlineStat = AirlineStat(
        airlineId = airline.id,
        cycle = cycle, // Use the cycle parameter from the function
        period = Period.WEEKLY,
        tourists = airlineStat.tourists,
        elites = airlineStat.elites,
        business = airlineStat.business,
        total = airlineStat.total,
        codeshares = airlineStat.codeshares,
        RASK = calculatedRASK,
        CASK = calculatedCASK,
        satisfaction = calculatedSatisfaction,
        loadFactor = calculatedLoadFactor,
        onTime = calculatedOnTime,
        hubDominance = 0.0 //todo
      )
      allAirlineStats += currentAirlineStat

        //set labor quality
        val targetServiceQuality = airline.getTargetServiceQuality()
        val currentServiceQuality = airline.getCurrentServiceQuality()
        airline.setCurrentServiceQuality(getNewQuality(currentServiceQuality, targetServiceQuality))
//        println(airline + " profit is: " + airlineProfit + " existing balance (not updated yet) " + airline.getBalance() + " reputation " +  airline.getReputation() + " cash flow " + totalCashFlow)
    }
    
    AirlineSource.saveAirlinesInfo(allAirlines)

    AirlineStatisticsSource.saveAirlineStats(allAirlineStats.toList)

    AirlineStatisticsSource.deleteIncomesBefore(currentCycle - BOOKKEEPING_ENTRIES_COUNT, Period.WEEKLY)
    AirlineStatisticsSource.deleteIncomesBefore(currentCycle - BOOKKEEPING_ENTRIES_COUNT * Period.numberWeeks(Period.QUARTER), Period.QUARTER)
    AirlineStatisticsSource.deleteIncomesBefore(currentCycle - BOOKKEEPING_ENTRIES_COUNT * Period.numberWeeks(Period.PERIOD), Period.PERIOD)


    cashFlows.foreach { //for balance it's safer to use adjust instead of setting it directly
      case(airline, cashFlow) => AirlineSource.adjustAirlineBalance(airline.id, cashFlow)
    }
    IncomeSource.saveIncomes(allIncomes.toList)

    //purge previous entry of current year/month
    if (currentCycle % Period.numberWeeks(Period.QUARTER) != 0) { //clear previous temp entry
      IncomeSource.deleteIncomes(currentCycle - 1, Period.QUARTER)
    }
    if (currentCycle % Period.numberWeeks(Period.PERIOD) != 0) { //clear previous temp entry
      IncomeSource.deleteIncomes(currentCycle - 1, Period.PERIOD)
    }
    
    //purge old entries
    IncomeSource.deleteIncomesBefore(currentCycle - BOOKKEEPING_ENTRIES_COUNT, Period.WEEKLY)
    IncomeSource.deleteIncomesBefore(currentCycle - BOOKKEEPING_ENTRIES_COUNT * Period.numberWeeks(Period.QUARTER), Period.QUARTER)
    IncomeSource.deleteIncomesBefore(currentCycle - BOOKKEEPING_ENTRIES_COUNT * Period.numberWeeks(Period.PERIOD), Period.PERIOD)
    
    CashFlowSource.saveCashFlows(allCashFlows.toList)
    //purge previous entry of current year/month
    if (currentCycle % Period.numberWeeks(Period.QUARTER) != 0) { //clear previous temp entry
      CashFlowSource.deleteCashFlows(currentCycle - 1, Period.QUARTER)
    }
    if (currentCycle % Period.numberWeeks(Period.PERIOD) != 0) { //clear previous temp entry
      CashFlowSource.deleteCashFlows(currentCycle - 1, Period.PERIOD)
    }
    
    //purge old entries
    CashFlowSource.deleteCashFlowsBefore(currentCycle - BOOKKEEPING_ENTRIES_COUNT, Period.WEEKLY);
    CashFlowSource.deleteCashFlowsBefore(currentCycle - BOOKKEEPING_ENTRIES_COUNT * Period.numberWeeks(Period.QUARTER), Period.QUARTER);
    CashFlowSource.deleteCashFlowsBefore(currentCycle - BOOKKEEPING_ENTRIES_COUNT * Period.numberWeeks(Period.PERIOD), Period.PERIOD);
    
    //update Oil consumption history
    OilSource.saveOilConsumptionHistory(oilConsumptionEntries.toList)
    OilSource.deleteOilConsumptionHistoryBeforeCycle(currentCycle - 10)
  }

  /**
   * compute monthly and yearly income 
   * 
   * Returns Updating income entries
   *
   */
  def computeAccumulateIncome(weeklyIncome : AirlineIncome) : List[AirlineIncome] = {
    //get existing entry
    val currentWeek = MainSimulation.currentWeek
    val airlineId = weeklyIncome.airlineId
    val currentMonthIncomeOption = if (currentWeek % Period.numberWeeks(Period.QUARTER) == 0) None else IncomeSource.loadIncomeByAirline(airlineId, currentWeek - 1, Period.QUARTER)
    
    val updatedMonthIncome = currentMonthIncomeOption match {
      case Some(income) => {
        income.update(weeklyIncome)
      }
      case None => weeklyIncome.copy(period = Period.QUARTER)//new quarter
    }
    val currentYearIncomeOption = if (currentWeek % Period.numberWeeks(Period.PERIOD) == 0) None else IncomeSource.loadIncomeByAirline(airlineId, currentWeek - 1, Period.PERIOD)
    val updatedYearIncome = currentYearIncomeOption match {
      case Some(income) => {
        income.update(weeklyIncome)
      }
      case None => weeklyIncome.copy(period = Period.PERIOD)//new period
    }
    
    List[AirlineIncome](updatedMonthIncome, updatedYearIncome)
  }
  
  /**
   * compute monthly and yearly cash flow 
   * 
   * Returns Updating cash flow entries
   */
  def computeAccumulateCashFlow(weeklyCashFlow : AirlineCashFlow) : List[AirlineCashFlow] = {
    //get existing entry
    val currentWeek = MainSimulation.currentWeek
    val airlineId = weeklyCashFlow.airlineId
    val currentMonthCashFlowOption = if (currentWeek % Period.numberWeeks(Period.QUARTER) == 0) None else CashFlowSource.loadCashFlowByAirline(airlineId, currentWeek - 1, Period.QUARTER)
    
    val updatedMonthCashFlow = currentMonthCashFlowOption match {
      case Some(cashFlow) => {
        cashFlow.update(weeklyCashFlow)
      }
      case None => weeklyCashFlow.copy(period = Period.QUARTER)
    }
    val currentYearCashFlowOption = if (currentWeek % Period.numberWeeks(Period.PERIOD) == 0) None else CashFlowSource.loadCashFlowByAirline(airlineId, currentWeek - 1, Period.PERIOD)
    val updatedYearCashFlow = currentYearCashFlowOption match {
      case Some(cashFlow) => {
        cashFlow.update(weeklyCashFlow)
      }
      case None => weeklyCashFlow.copy(period = Period.PERIOD)
    }
    
    List[AirlineCashFlow](updatedMonthCashFlow, updatedYearCashFlow)
  }
  
  /**
   * Returns a tuple of (totalLoanRepayment, totalLoanInterest)
   */
  def updateLoans(airline : Airline, paymentCycle : Int) : (Long, Long) = {
    val loans = BankSource.loadLoansByAirline(airline.id)
    var totalLoanPayment = 0L
    var totalLoanInterest = 0L
    loans.foreach { loan =>
      if (loan.lastPaymentCycle >= paymentCycle) { //something wrong with sim, avoid duplicated payment
        println(s"Skipping double payment on $loan")
      } else {
        val weeklyPayment = loan.weeklyPayment
        val weeklyInterest = loan.weeklyInterest(paymentCycle)
        totalLoanPayment = totalLoanPayment + weeklyPayment
        totalLoanInterest = totalLoanInterest + weeklyInterest
      }
      if (loan.remainingTerm(paymentCycle) <= 0) {
        BankSource.deleteLoan(loan.id)
      } else {
        BankSource.updateLoanLastPayment(loan.id, paymentCycle)
      }
    }
    
    (totalLoanPayment, totalLoanInterest)
  }

  val getNewQuality : (Double, Double) => Double = (currentQuality, targetQuality) =>  {
    val delta = targetQuality - currentQuality
    val adjustment = 
      if (delta >= 0) { //going up, slower when current quality is already high
        MAX_SERVICE_QUALITY_INCREMENT * (1 - (currentQuality / Airline.EQ_MAX * 0.9)) //at current quality 0, multiplier 1x; current quality 100, multiplier 0.1x
      } else { //going down, faster when current quality is already high
        -1 * MAX_SERVICE_QUALITY_DECREMENT * (0.1 + (currentQuality / Airline.EQ_MAX * 0.9)) //at current quality 0, multiplier 0.1x; current quality 100, multiplier 1x
      }
    if (adjustment >= 0) {
      if (adjustment + currentQuality >= targetQuality) {
        targetQuality
      } else {
        adjustment + currentQuality
      }
    } else {
      if (currentQuality + adjustment <= targetQuality) {
        targetQuality
      } else {
        currentQuality + adjustment
      }
    } 
  }
  
//  def getChampions(allAirlines : scala.collection.immutable.Map[Int, Airline], allCountries : scala.collection.immutable.Map[String, Country]) : scala.collection.immutable.Map[Airline, List[(Country, Int)]] = {
//    val champions = Map[Airline, ListBuffer[(Country, Int)]]()
//     CountrySource.loadMarketSharesByCriteria(List.empty).foreach { 
//       case CountryMarketShare(countryCode : String, airlineShares) => {
//         val championsForThisCountry = airlineShares.toList.sortBy(_._2)(Ordering[Long].reverse).take(5)
//         for (x <- 0 until championsForThisCountry.size) {
//            val airline = allAirlines((championsForThisCountry(x)._1))
//            val ranking = x + 1
//            val airlineChampionedCountries = champions.getOrElseUpdate(airline, ListBuffer[(Country, Int)]())
//            airlineChampionedCountries += ((allCountries(countryCode), ranking))
//         }
//       }
//     }
//    
//    champions.mapValues(_.toList).toMap
//  }
}
