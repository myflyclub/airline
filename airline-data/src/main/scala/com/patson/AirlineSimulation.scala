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
import com.patson.util.{AirportChampionInfo, ChampionUtil, CountryChampionInfo}

object AirlineSimulation {
  val MAX_SERVICE_QUALITY_INCREMENT : Double = 0.5
  val MAX_SERVICE_QUALITY_DECREMENT : Double = 10
  val MAX_REPUTATION_DELTA = 1
  val BANKRUPTCY_ASSETS_THRESHOLD = -50000000 //-50M
  val BANKRUPTCY_CASH_THRESHOLD = -10000000 //-10M
  val BOOKKEEPING_ENTRIES_COUNT = 25

  def airlineSimulation(cycle: Int, flightLinkResult: List[LinkConsumptionDetails], loungeResult: List[LoungeConsumptionDetails], airplanes: List[Airplane], paxStats: immutable.Map[Int, AirlinePaxStat]) = {
    val allAirlines = AirlineSource.loadAllAirlines(true).filter(_.getHeadQuarter().isDefined)
    val allLinks = LinkSource.loadAllLinks(LinkSource.FULL_LOAD)
    val allFlightLinksByAirlineId = allLinks.filter(_.transportType == TransportType.FLIGHT).map(_.asInstanceOf[Link]).groupBy(_.airline.id)
    val allTransactions = AirlineSource.loadTransactions(cycle).groupBy { _.airlineId }
    val allTransactionalCashFlowItems: scala.collection.immutable.Map[Int, List[AirlineCashFlowItem]] = AirlineSource.loadCashFlowItems(cycle).groupBy { _.airlineId }
    val currentInterestRate = BankSource.loadLoanInterestRateByCycle(cycle).getOrElse(LoanInterestRateSimulation.MAX_RATE)
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
    val airportChampionsByAirlineId : immutable.Map[Int, List[AirportChampionInfo]] = ChampionUtil.loadAirportChampionInfo().groupBy(_.loyalist.airline.id)
    val cashFlows = Map[Airline, Long]() //cash flow for actual deduction

    var startTime = System.currentTimeMillis()
    val leaderboardsByAirlineId = RankingSimulation.process(cycle, allAirlines, flightLinkResult, loungeResult, paxStats)
    Util.outputTimeDiff(startTime, "Generating rankings took")

    val fuelContractsByAirlineId = OilSource.loadAllOilContracts().groupBy(contract => contract.airline.id)
    val fuelInventoryPolicyByAirlineId = OilSource.loadAllOilInventoryPolicies.map(policy => (policy.airline.id, policy)).toMap
    val currentFuelPrice = OilSource.loadOilPriceByCycle(cycle).get.price
    val oilConsumptionEntries = ListBuffer[OilConsumptionHistory]()

    allAirlines.foreach { airline =>
      var totalCashRevenue = 0L
      var totalCashExpense = 0L
      var linksDepreciation = 0L
      val othersSummary = Map[OtherIncomeItemType.Value, Long]()
      val airlineValue = Computation.getResetAmount(airline.id)

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
          LinksIncome(airline.id, profit = linksProfit, revenue = linksRevenue, expense = linksExpense, ticketRevenue = linksRevenue, airportFee = -1 * linksAirportFee, fuelCost = -1 * linksFuelCost, fuelTax = -1 * linksFuelTax, crewCost = -1 * linksCrewCost, inflightCost = -1 * linksInflightCost, delayCompensation = -1 * linksDelayCompensation, maintenanceCost = -1 * linksMaintenanceCost, loungeCost = -1 * linksLoungeCost, depreciation = -1 * linksDepreciation, cycle = currentCycle)
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
      val linksByFromAirportId = allFlightLinksByAirlineId.getOrElse(airline.id, List.empty).groupBy(_.from.id)
      var overtimeCompensation = 0
      airline.bases.foreach { base =>
        val staffRequired = linksByFromAirportId.get(base.airport.id) match {
          case Some(links) => links.map(_.getCurrentOfficeStaffRequired).sum
          case None => 0
        }
        val compensationOfThisBase = base.getOvertimeCompensation(staffRequired.toInt)
        overtimeCompensation += compensationOfThisBase
      }

      othersSummary.put(OtherIncomeItemType.OVERTIME_COMPENSATION, -1 * overtimeCompensation) //negative number
      totalCashExpense += overtimeCompensation


      val allAirplanesDepreciation = airplanesByAirline.getOrElse(airline.id, List.empty).foldLeft(0L) {
        case (depreciation, airplane) => (depreciation + airplane.depreciationRate)
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
      var loungeIncome = 0L

      loungeResult.filter(_.lounge.airline.id == airline.id).foreach {
        case LoungeConsumptionDetails(_, selfVisitors, allianceVisitors, _) =>
          loungeCost += (selfVisitors + allianceVisitors) * Lounge.PER_VISITOR_COST
          loungeIncome += (selfVisitors + allianceVisitors) * Lounge.PER_VISITOR_CHARGE
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
          val totalPaymentFromContract = contracts.map { contract =>
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

      othersSummary.put(OtherIncomeItemType.FUEL_PROFIT, fuelProfit)

      val advertisementCost = DelegateSource.loadCampaignTasksByAirlineId(airline.id).map(_.cost).sum
      if (advertisementCost > 0) {
        totalCashExpense += advertisementCost
      }
      othersSummary.put(OtherIncomeItemType.ADVERTISEMENT, advertisementCost * -1)

      var othersRevenue = 0L
      var othersExpense = 0L
      othersSummary.foreach {
        case (_, amount) =>
          if (amount >= 0) {
            othersRevenue += amount
          } else {
            othersExpense -= amount
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
      //saving airline Income at very end

      //cash flow computation
      val totalCashFlow = totalCashRevenue - totalCashExpense

      val operationCashFlow = totalCashFlow + loanPayment //exclude both interest and principle here, which WAS included in the total cash flow
      if (!isBankrupt) {
        cashFlows.put(airline, totalCashFlow) //this is week end flow, used for actual adjustment
      }

      //below is for accounting purpose
      //cash flow item that is already applied during this week, still need to load them for accounting purpose
      val transactionalCashFlowItems: scala.collection.immutable.Map[CashFlowType.Value, Long] = allTransactionalCashFlowItems.get(airline.id) match {
        case Some(items) => items.groupBy(_.cashFlowType).view.mapValues(itemsByType => itemsByType.map(_.amount).sum).toMap
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
      //saving at very end

      // AirlineStats
      var calculatedRASK = 0.0
      var calculatedCASK = 0.0
      var calculatedSatisfaction = 0.0 // Default to 0, could use Option or NaN if preferred
      var calculatedLoadFactor = 0.0
      var calculatedOnTime = 1.0 // Default to 100% on time if no flights
      val weeksCashOnHand = (airlineValue.existingBalance / Math.max(1, airlineExpense)).toInt
      val sharesOutstanding = airline.getSharesOutstanding()
      val eps = if (sharesOutstanding > 0) airlineProfit.toDouble / sharesOutstanding else 0.0
      val linkCount = allFlightLinksByAirlineId.get(airline.id) match { //used for milestones & stockPrice so we do it here
        case Some(links) =>
          links.map(link => link.to.iata ++ link.from.iata).toSet.size
        case None => 0
      }
      flightLinkResultByAirline.get(airline.id) match {
        case Some(linkConsumptions) if linkConsumptions.nonEmpty && !isBankrupt =>
          val intermediateStats = linkConsumptions.foldLeft((0.0, 0.0, 0.0, 0.0, 0.0)) {
            // Accumulator: (ask, satisfactionSum, loadFactorSum, flights, ontime)
            case ((askAcc, satAcc, lfAcc, flightsAcc, delayAcc), consumption) =>
              val link = consumption.link
              val ASK = link.capacity.total * link.distance
              (
                askAcc + ASK,
                satAcc + consumption.satisfaction,
                lfAcc + link.soldSeats.total.toDouble / link.capacity.total,
                flightsAcc + link.frequency,
                delayAcc + link.cancellationCount + link.majorDelayCount + link.minorDelayCount
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

      //set labor quality
      val targetServiceQuality = airline.getTargetServiceQuality()
      val currentServiceQuality = airline.getCurrentServiceQuality()
      airline.setCurrentServiceQuality(getNewQuality(currentServiceQuality, targetServiceQuality))

      val airlineStat = paxStats.getOrElse(airline.id, AirlinePaxStat(0, 0, 0, 0, 0))
      val currentReputation = airline.getReputation()
      val reputationBreakdowns = ListBuffer[ReputationBreakdown]()

      val reputationBonusFromLeaderboards = leaderboardsByAirlineId.getOrElse(airline.id, 0.0)
      reputationBreakdowns.append(ReputationBreakdown(ReputationType.LEADERBOARD_BONUS, reputationBonusFromLeaderboards, reputationBonusFromLeaderboards.toInt))

      val milestones = AirlineMilestones.getMilestonesForAirlineType(airline.airlineType)

      milestones.foreach { milestone =>
        val (milestoneValue, reputationType) = milestone.name match {
          case "MILESTONE_AIRCRAFT_TYPES" =>
            val value = airplanesByAirline.get(airline.id) match {
              case Some(airplanes) => airplanes.map(_.model).toSet.size.toDouble
              case None => 0.0
            }
            (value, ReputationType.MILESTONE_AIRCRAFT_TYPES)

          case "MILESTONE_CODESHARES" =>
            (airlineStat.codeshares.toDouble, ReputationType.MILESTONE_CODESHARES)

          case "MILESTONE_BUSINESS" =>
            (airlineStat.business.toDouble, ReputationType.MILESTONE_BUSINESS)

          case "MILESTONE_ON_TIME" =>
            val value = allFlightLinksByAirlineId.get(airline.id) match {
              case Some(links) =>
                links.map(_.frequency).sum.toDouble * calculatedOnTime
              case None => 0.0
            }
            (value, ReputationType.MILESTONE_ON_TIME)

          case "MILESTONE_LEADER_POINTS" =>
            (reputationBonusFromLeaderboards, ReputationType.MILESTONE_LEADER_POINTS)

          case "MILESTONE_LOAN" =>
            (loanPayment.toDouble, ReputationType.MILESTONE_LOAN)

          case "MILESTONE_DESTINATIONS" =>
            val value = allFlightLinksByAirlineId.get(airline.id) match {
              case Some(links) =>
                links.flatMap(link => List(link.to.iata, link.from.iata)).toSet.size.toDouble
              case None => 0.0
            }
            (value, ReputationType.MILESTONE_DESTINATIONS)

          case "MILESTONE_LINKS" =>
            (linkCount.toDouble, ReputationType.MILESTONE_LINK_COUNT)

          case "MILESTONE_BASES" =>
            (airline.bases.size.toDouble, ReputationType.MILESTONE_BASES)

          case "MILESTONE_PASSENGER_KM" =>
            val value = flightLinkResultByAirline.get(airline.id) match {
              case Some(linkConsumptions) =>
                linkConsumptions.foldLeft(0L) { (accumulator, linkConsumption) =>
                  accumulator + linkConsumption.link.soldSeats.total * linkConsumption.link.distance
                }.toDouble
              case None => 0.0
            }
            (value, ReputationType.MILESTONE_PASSENGER_KM)
        }

        val reputation = AirlineMilestones.evaluateMilestone(milestone, milestoneValue.toLong)
        reputationBreakdowns.append(ReputationBreakdown(reputationType, reputation, milestoneValue.toLong))
      }


      if (airline.airlineType.airportRepRatio > 0) {
        val reputationByAirportChampions = airportChampionsByAirlineId.get(airline.id) match {
          case Some(airportChampions) => (airportChampions.map(_.reputationBoost).sum * airline.airlineType.airportRepRatio * 100).toInt.toDouble / 100
          case None => 0
        }
        reputationBreakdowns.append(ReputationBreakdown(ReputationType.AIRPORT_LOYALIST_RANKING, reputationByAirportChampions, reputationByAirportChampions.toInt))
      }

      if (airline.airlineType.touristTravelerRepPerLevel > 0) {
        val reputationByTourists = airline.airlineType.touristTravelerRepPerLevel * airline.airlineGradeTouristsTravelers.level
        reputationBreakdowns.append(ReputationBreakdown(ReputationType.TOURISTS, reputationByTourists, airline.stats.total - airline.stats.business - airline.stats.elites))
      }

      if (airline.airlineType.elitesRepPerLevel > 0) {
        val reputationByElites = airline.airlineType.elitesRepPerLevel * airline.airlineGradeElites.level
        reputationBreakdowns.append(ReputationBreakdown(ReputationType.ELITES, reputationByElites, airline.stats.elites))
      }

      if (airline.airlineType.stockRepPerLevel > 0) {
        val reputationByStockPrice = airline.airlineType.stockRepPerLevel * airline.airlineGradeStockPrice.level
        reputationBreakdowns.append(ReputationBreakdown(ReputationType.STOCK_PRICE, reputationByStockPrice, airline.getStockPrice().toLong))
      }

      //set reputation
      val finalBreakdowns = ReputationBreakdowns(reputationBreakdowns.toList)
      AirlineSource.updateReputationBreakdowns(airline.id, finalBreakdowns)
      val beginnerAirlineCeiling = if (airline.airlineType == BeginnerAirline) 250 else Int.MaxValue
      var targetReputation = Math.min(beginnerAirlineCeiling, finalBreakdowns.total)
      if (targetReputation > currentReputation && targetReputation - currentReputation > MAX_REPUTATION_DELTA) {
        targetReputation = currentReputation + MAX_REPUTATION_DELTA //make sure it increases/decreases gradually based on passenger volume
      } else if (targetReputation < currentReputation && currentReputation - targetReputation > MAX_REPUTATION_DELTA) {
        targetReputation = currentReputation - MAX_REPUTATION_DELTA
      }
      airline.setReputation(targetReputation)

      // Create and Add AirlineStat Instance using calculated values and paxStat
      val airlineWeeklyStats = AirlineStat(
        airlineId = airline.id,
        cycle = cycle, // Use the cycle parameter from the function
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
        cashOnHand = weeksCashOnHand,
        eps = eps,
        linkCount = linkCount,
        repTotal = finalBreakdowns.total.toInt,
        repLeaderboards = reputationBonusFromLeaderboards.toInt
      )
      allAirlineStats += airlineWeeklyStats
      val stockPrice = if (isBankrupt) {
         0.0
      } else {
        updateStockPrice(airline.getStockPrice(), airlineWeeklyStats, currentInterestRate)
      }
      airline.setStockPrice(stockPrice)

      val airlineWeeklyIncome = AirlineIncome(airline.id, airlineProfit, airlineRevenue, airlineExpense, stockPrice, airlineValue.overall, linksIncome, transactionsIncome, othersIncome, cycle = currentCycle)
      allIncomes += airlineWeeklyIncome

      allCashFlows += airlineWeeklyCashFlow
    }
    
    AirlineSource.saveAirlinesInfo(allAirlines)

    AirlineStatisticsSource.saveAirlineStats(allAirlineStats.toList)
    
    // Batch compute and save accumulated stats at period boundaries
    if ((currentCycle + 1) % Period.numberWeeks(Period.QUARTER) == 0) {
      computeAndSaveStats(currentCycle, allAirlines, Period.QUARTER)
    }
    if ((currentCycle + 1) % Period.numberWeeks(Period.YEAR) == 0) {
      computeAndSaveStats(currentCycle, allAirlines, Period.YEAR)
    }

    AirlineStatisticsSource.deleteStatsBefore(currentCycle - BOOKKEEPING_ENTRIES_COUNT, Period.WEEKLY)
    AirlineStatisticsSource.deleteStatsBefore(currentCycle - BOOKKEEPING_ENTRIES_COUNT * Period.numberWeeks(Period.QUARTER), Period.QUARTER)
    AirlineStatisticsSource.deleteStatsBefore(currentCycle - BOOKKEEPING_ENTRIES_COUNT * Period.numberWeeks(Period.YEAR), Period.YEAR)


    cashFlows.foreach { //for balance it's safer to use adjust instead of setting it directly
      case(airline, cashFlow) => AirlineSource.adjustAirlineBalance(airline.id, cashFlow)
    }
    
    // Save weekly data first
    IncomeSource.saveIncomes(allIncomes.toList)
    CashFlowSource.saveCashFlows(allCashFlows.toList)
    
    // Batch compute and save accumulated data at period boundaries
    if ((currentCycle + 1) % Period.numberWeeks(Period.QUARTER) == 0) {
      computeAndSaveAccumulation(currentCycle, allAirlines, Period.QUARTER)
    }
    if ((currentCycle + 1) % Period.numberWeeks(Period.YEAR) == 0) {
      computeAndSaveAccumulation(currentCycle, allAirlines, Period.YEAR)
    }

    //purge old entries
    IncomeSource.deleteIncomesBefore(currentCycle - BOOKKEEPING_ENTRIES_COUNT, Period.WEEKLY)
    IncomeSource.deleteIncomesBefore(currentCycle - BOOKKEEPING_ENTRIES_COUNT * Period.numberWeeks(Period.QUARTER), Period.QUARTER)
    IncomeSource.deleteIncomesBefore(currentCycle - BOOKKEEPING_ENTRIES_COUNT * Period.numberWeeks(Period.YEAR), Period.YEAR)

    CashFlowSource.deleteCashFlowsBefore(currentCycle - BOOKKEEPING_ENTRIES_COUNT, Period.WEEKLY)
    CashFlowSource.deleteCashFlowsBefore(currentCycle - BOOKKEEPING_ENTRIES_COUNT * Period.numberWeeks(Period.QUARTER), Period.QUARTER)
    CashFlowSource.deleteCashFlowsBefore(currentCycle - BOOKKEEPING_ENTRIES_COUNT * Period.numberWeeks(Period.YEAR), Period.YEAR)
    
    //update Oil consumption history
    OilSource.saveOilConsumptionHistory(oilConsumptionEntries.toList)
    OilSource.deleteOilConsumptionHistoryBeforeCycle(currentCycle - 10)
  }

  def computeAndSaveAccumulation(cycle: Int, allAirlines: List[Airline], period: Period.Value): Unit = {
    val periodWeeks = Period.numberWeeks(period)
    val startCycle = cycle - (cycle % periodWeeks) + 1
    val endCycle = cycle
    
    // Batch load all weekly incomes and cash flows for all airlines in this period
    val weeklyIncomesByAirline = IncomeSource.loadWeeklyIncomesByCycleRange(startCycle, endCycle).groupBy(_.airlineId)
    val weeklyCashFlowsByAirline = CashFlowSource.loadWeeklyCashFlowsByCycleRange(startCycle, endCycle).groupBy(_.airlineId)
    
    val periodIncomes = scala.collection.mutable.ListBuffer[AirlineIncome]()
    val periodCashFlows = scala.collection.mutable.ListBuffer[AirlineCashFlow]()
    
    allAirlines.foreach { airline =>
      weeklyIncomesByAirline.get(airline.id).foreach { weeks =>
        if (weeks.nonEmpty) {
          val summed = weeks.reduce { (acc, week) =>
            AirlineIncome(
              airline.id,
              profit = acc.profit + week.profit,
              revenue = acc.revenue + week.revenue,
              expense = acc.expense + week.expense,
              stockPrice = acc.stockPrice,
              totalValue = acc.totalValue,
              links = acc.links.copy(
                profit = acc.links.profit + week.links.profit,
                revenue = acc.links.revenue + week.links.revenue,
                expense = acc.links.expense + week.links.expense,
                ticketRevenue = acc.links.ticketRevenue + week.links.ticketRevenue,
                airportFee = acc.links.airportFee + week.links.airportFee,
                fuelCost = acc.links.fuelCost + week.links.fuelCost,
                fuelTax = acc.links.fuelTax + week.links.fuelTax,
                crewCost = acc.links.crewCost + week.links.crewCost,
                inflightCost = acc.links.inflightCost + week.links.inflightCost,
                delayCompensation = acc.links.delayCompensation + week.links.delayCompensation,
                maintenanceCost = acc.links.maintenanceCost + week.links.maintenanceCost,
                loungeCost = acc.links.loungeCost + week.links.loungeCost,
                depreciation = acc.links.depreciation + week.links.depreciation
              ),
              transactions = acc.transactions.copy(
                profit = acc.transactions.profit + week.transactions.profit,
                revenue = acc.transactions.revenue + week.transactions.revenue,
                expense = acc.transactions.expense + week.transactions.expense,
                capitalGain = acc.transactions.capitalGain + week.transactions.capitalGain,
                createLink = acc.transactions.createLink + week.transactions.createLink
              ),
              others = acc.others.copy(
                profit = acc.others.profit + week.others.profit,
                revenue = acc.others.revenue + week.others.revenue,
                expense = acc.others.expense + week.others.expense,
                loanInterest = acc.others.loanInterest + week.others.loanInterest,
                baseUpkeep = acc.others.baseUpkeep + week.others.baseUpkeep,
                overtimeCompensation = acc.others.overtimeCompensation + week.others.overtimeCompensation,
                advertisement = acc.others.advertisement + week.others.advertisement,
                loungeUpkeep = acc.others.loungeUpkeep + week.others.loungeUpkeep,
                loungeCost = acc.others.loungeCost + week.others.loungeCost,
                loungeIncome = acc.others.loungeIncome + week.others.loungeIncome,
                assetExpense = acc.others.assetExpense + week.others.assetExpense,
                assetRevenue = acc.others.assetRevenue + week.others.assetRevenue,
                fuelProfit = acc.others.fuelProfit + week.others.fuelProfit,
                depreciation = acc.others.depreciation + week.others.depreciation
              ),
              period = period,
              cycle = cycle
            )
          }
          // Average stock price and total value across the period
          val avgStockPrice = summed.stockPrice / weeks.length
          val avgTotalValue = summed.totalValue / weeks.length
          periodIncomes += summed.copy(stockPrice = avgStockPrice, totalValue = avgTotalValue)
        }
      }
      
      weeklyCashFlowsByAirline.get(airline.id).foreach { weeks =>
        if (weeks.nonEmpty) {
          val summed = weeks.reduce { (acc, week) =>
            AirlineCashFlow(
              airline.id,
              cashFlow = acc.cashFlow + week.cashFlow,
              operation = acc.operation + week.operation,
              loanInterest = acc.loanInterest + week.loanInterest,
              loanPrincipal = acc.loanPrincipal + week.loanPrincipal,
              baseConstruction = acc.baseConstruction + week.baseConstruction,
              buyAirplane = acc.buyAirplane + week.buyAirplane,
              sellAirplane = acc.sellAirplane + week.sellAirplane,
              createLink = acc.createLink + week.createLink,
              facilityConstruction = acc.facilityConstruction + week.facilityConstruction,
              oilContract = acc.oilContract + week.oilContract,
              assetTransactions = acc.assetTransactions + week.assetTransactions,
              period = period,
              cycle = cycle
            )
          }
          periodCashFlows += summed
        }
      }
    }
    
    IncomeSource.saveIncomes(periodIncomes.toList)
    CashFlowSource.saveCashFlows(periodCashFlows.toList)
  }
  
  def computeAndSaveStats(cycle: Int, allAirlines: List[Airline], period: Period.Value): Unit = {
    val periodWeeks = Period.numberWeeks(period)
    val startCycle = cycle - (cycle % periodWeeks) + 1
    val endCycle = cycle
    
    // Batch load all weekly stats for all airlines in this period
    val weeklyStatsByAirline = AirlineStatisticsSource.loadAirlineStatsByCriteria(List(("period", 0))).filter { stat =>
      stat.cycle >= startCycle && stat.cycle <= endCycle
    }.groupBy(_.airlineId)
    
    val periodStats = scala.collection.mutable.ListBuffer[AirlineStat]()
    
    allAirlines.foreach { airline =>
      weeklyStatsByAirline.get(airline.id).foreach { weeks =>
        if (weeks.nonEmpty) {
          val summed = weeks.reduce { (acc, week) =>
            acc.update(week)
          }
          periodStats += summed.copy(period = period, cycle = cycle)
        }
      }
    }
    
    AirlineStatisticsSource.saveAirlineStats(periodStats.toList)
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

  def updateStockPrice(stockPrice: Double, stats: AirlineStat, currentInterestRate: Double): Double = {
    val sizeAdjust = Math.min(1, Math.max(0.001, stats.eps / 0.6)) //small airlines shouldn't go too high

    val metricEPS = StockModel.getMetricValue("eps", stats.eps)
    val metricPASK = StockModel.getMetricValue("pask", stats.RASK - stats.CASK)
    val metricSF = StockModel.getMetricValue("satisfaction", stats.satisfaction)
    val metricLinks = StockModel.getMetricValue("link_count", stats.linkCount)
    val metricOnTime = StockModel.getMetricValue("on_time", stats.onTime) * sizeAdjust
    val metricCodeshares = StockModel.getMetricValue("codeshares", stats.codeshares)
    val metricLeaderboard = StockModel.getMetricValue("rep_leaderboards", stats.repLeaderboards)
    val metricCash = StockModel.getMetricValue("months_cash_on_hand", stats.cashOnHand / 4)
    val metricInterest = StockModel.getMetricValue("interest", currentInterestRate) * sizeAdjust

    var newPrice = metricEPS
    newPrice += metricPASK
    newPrice += metricSF
    newPrice += metricLinks
    newPrice += metricOnTime
    newPrice += metricCodeshares
    newPrice += metricLeaderboard
    newPrice += metricCash
    newPrice += metricInterest

//    println(s"StockPrice metrics: $newPrice, adjust=$sizeAdjust, EPS=$metricEPS, PASK=$metricPASK, SF=$metricSF, Links=$metricLinks, OnTime=$metricOnTime, Airport=$metricAirport, Codeshares=$metricCodeshares, Leaderboard=$metricLeaderboard, Cash=$metricCash, Interest=$metricInterest")


    val result = if (newPrice > stockPrice) {
      stockPrice * 0.15 + Math.pow(Math.max(0.01, newPrice), StockModel.STOCK_EXPONENT) / 10 * 0.85
    } else {
      //price falls slower, making buybacks more impactful
      stockPrice * 0.6 + Math.pow(Math.max(0.01, newPrice), StockModel.STOCK_EXPONENT) / 10 * 0.4
    }
    BigDecimal(result).setScale(3, BigDecimal.RoundingMode.HALF_UP).toDouble
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
}
