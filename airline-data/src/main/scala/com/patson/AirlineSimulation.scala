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
    val currentInterestRate = BankSource.loadLoanInterestRateByCycle(cycle).getOrElse(LoanInterestRateSimulation.MAX_RATE)
    //purge old ledger entries
    AirlineSource.deleteLedgerEntries(cycle - BOOKKEEPING_ENTRIES_COUNT)

    val flightLinkResultByAirline = flightLinkResult.groupBy(_.link.airline.id)

    val airplanesByAirline = airplanes.groupBy(_.owner.id)

    val loungesByAirlineId = AirlineSource.loadAllLounges().groupBy(_.airline.id)

    val assetsByAirlineId = AirportAssetSource.loadAirportAssetsByAssetCriteria(List.empty).groupBy(_.airline.get.id) //load all owned assets

    val allIncomes = ListBuffer[AirlineIncome]()
    val allLedgerEntries = ListBuffer[AirlineLedgerEntry]()
    val allAirlineStats = ListBuffer[AirlineStat]()

    val currentCycle = MainSimulation.currentWeek
    val airportChampionsByAirlineId : immutable.Map[Int, List[AirportChampionInfo]] = ChampionUtil.loadAirportChampionInfo().groupBy(_.loyalist.airline.id)

    val allAirlineStatsByAirlineId: immutable.Map[Int, List[AirlineStat]] = AirlineStatisticsSource.loadAirlineStatsForAirlineIds(allAirlines.map(_.id)).groupBy(_.airlineId) //used for stock price
    val latestQuarterStatsByAirlineId: immutable.Map[Int, AirlineStat] = allAirlineStatsByAirlineId.flatMap {
        case (airlineId, stats) => stats.filter(_.period == Period.QUARTER).maxByOption(_.cycle).map(stat => (airlineId, stat))
    }

    var startTime = System.currentTimeMillis()
    val leaderboardsByAirlineId = RankingSimulation.process(cycle, allAirlines, flightLinkResult, loungeResult, paxStats)
    Util.outputTimeDiff(startTime, "Generating rankings took")

    val fuelContractsByAirlineId = OilSource.loadAllOilContracts().groupBy(contract => contract.airline.id)
    val fuelInventoryPolicyByAirlineId = OilSource.loadAllOilInventoryPolicies.map(policy => (policy.airline.id, policy)).toMap
    val currentFuelPrice = OilSource.loadOilPriceByCycle(cycle).get.price
    val oilConsumptionEntries = ListBuffer[OilConsumptionHistory]()

    allAirlines.foreach { airline =>
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
          val linksDepreciation = linkConsumptions.foldLeft(0L)(_ + _.depreciation)
          val linksLoungeCost = linkConsumptions.foldLeft(0L)(_ + _.loungeCost)
          val linksRevenue = linkConsumptions.foldLeft(0L)(_ + _.revenue)

          val linksExpense = linksAirportFee + linksCrewCost + linksFuelCost + linksFuelTax + linksInflightCost + linksDelayCompensation + linksMaintenanceCost + linksDepreciation + linksLoungeCost

          LinksIncome(airline.id, profit = linksProfit, revenue = linksRevenue, expense = linksExpense, ticketRevenue = linksRevenue, airportFee = -1 * linksAirportFee, fuelCost = -1 * linksFuelCost, fuelTax = -1 * linksFuelTax, crewCost = -1 * linksCrewCost, inflightCost = -1 * linksInflightCost, delayCompensation = -1 * linksDelayCompensation, maintenanceCost = -1 * linksMaintenanceCost, loungeCost = -1 * linksLoungeCost, depreciation = -1 * linksDepreciation, cycle = currentCycle)
        }
        case None => LinksIncome(airline.id, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, cycle = currentCycle)
      }


      val baseUpkeep = airline.bases.foldLeft(0L)((upkeep, base) => {
        upkeep + base.getUpkeep
      })
      othersSummary.put(OtherIncomeItemType.BASE_UPKEEP, -1 * baseUpkeep) //negative number

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

      val negativeCashInterest = if (airlineValue.existingBalance < 0) {
        (airlineValue.existingBalance * LoanInterestRateSimulation.HIGH_RATE_THRESHOLD / 52).toLong //give high interest
      } else {
        0L
      }

      val (loanPayment, interestPayment) = updateLoans(airline, currentCycle + 1) //have to plus one here, as this is supposed to be done postcycle, but for accounting purpose we have to put it here
      othersSummary.put(OtherIncomeItemType.LOAN_INTEREST, -1 * interestPayment + negativeCashInterest)

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
      othersSummary.put(OtherIncomeItemType.LOUNGE_UPKEEP, -1 * loungeCost + -1 * loungeUpkeep)
      othersSummary.put(OtherIncomeItemType.LOUNGE_INCOME, loungeIncome)

      val (assetExpense, assetRevenue) = assetsByAirlineId.get(airline.id) match {
        case Some(assets) => (assets.map(_.expense).sum, assets.map(_.revenue).sum)
        case None => (0L, 0L)
      }

      othersSummary.put(OtherIncomeItemType.ASSET_EXPENSE, -1 * assetExpense)
      othersSummary.put(OtherIncomeItemType.ASSET_REVENUE, assetRevenue)

      //calculate extra cash flow due to difference in fuel cost
      val barrelsUsed = (linksIncome.fuelCost / OilPrice.DEFAULT_PRICE).abs.toInt
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
            val sellPrice = currentFuelPrice * 0.7
            val excessBarrles = totalVolumeFromContract - barrelsUsed
            oilConsumptionEntries += OilConsumptionHistory(airline, sellPrice, excessBarrles * -1, OilConsumptionType.EXCESS, currentCycle)
            totalPaymentFromContract - excessBarrles * sellPrice
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
      val fuelCostDescription = {
        val perCost = (actualFuelCost / barrelsUsed * 100).toInt.toDouble / 100
        s"$barrelsUsed barrels at ~ $perCost average"
      }
      val carbonTaxDescription = {
        val taxRate = airline.fuelTaxRate
        s"Paying ${taxRate} rate%"
      }

      othersSummary.put(OtherIncomeItemType.FUEL_COST, actualFuelCost.toLong)

      val advertisementCost = DelegateSource.loadCampaignTasksByAirlineId(airline.id).map(_.cost).sum
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
        , loungeIncome = othersSummary.getOrElse(OtherIncomeItemType.LOUNGE_INCOME, 0)
        , assetExpense = othersSummary.getOrElse(OtherIncomeItemType.ASSET_EXPENSE, 0)
        , assetRevenue = othersSummary.getOrElse(OtherIncomeItemType.ASSET_REVENUE, 0)
        , fuelProfit = othersSummary.getOrElse(OtherIncomeItemType.FUEL_COST, 0)
        , cycle = currentCycle
      )

      val airlineRevenue = linksIncome.revenue + othersIncome.revenue
      val airlineExpense = linksIncome.expense + othersIncome.expense
      val airlineProfit = airlineRevenue - airlineExpense

      val loanPrincipal = loanPayment - interestPayment

      // Record weekly ledger entries
      if (!isBankrupt) {
        val weeklyLedger = List(
            AirlineLedgerEntry(airline.id, currentCycle, LedgerType.LINK_REVENUE, linksIncome.revenue),
            AirlineLedgerEntry(airline.id, currentCycle, LedgerType.LINK_CREW_COST, linksIncome.crewCost),
            AirlineLedgerEntry(airline.id, currentCycle, LedgerType.LINK_AIRPORT_FEE, linksIncome.airportFee),
            AirlineLedgerEntry(airline.id, currentCycle, LedgerType.LINK_INFLIGHT_COST, linksIncome.inflightCost),
            AirlineLedgerEntry(airline.id, currentCycle, LedgerType.LINK_MAINTENANCE_COST, linksIncome.maintenanceCost),
            AirlineLedgerEntry(airline.id, currentCycle, LedgerType.LINK_LOUNGE_COST, linksIncome.loungeCost),
            AirlineLedgerEntry(airline.id, currentCycle, LedgerType.LINK_DELAY_COMPENSATION, linksIncome.delayCompensation),
            //don't record fuel or deprecation here as they're purchased outside links         
            AirlineLedgerEntry(airline.id, currentCycle, LedgerType.BASE_UPKEEP, othersSummary.getOrElse(OtherIncomeItemType.BASE_UPKEEP, 0L)),
            AirlineLedgerEntry(airline.id, currentCycle, LedgerType.OVERTIME_COMPENSATION, othersSummary.getOrElse(OtherIncomeItemType.OVERTIME_COMPENSATION, 0L)),
            AirlineLedgerEntry(airline.id, currentCycle, LedgerType.LOUNGE_UPKEEP, othersSummary.getOrElse(OtherIncomeItemType.LOUNGE_UPKEEP, 0L)),
            AirlineLedgerEntry(airline.id, currentCycle, LedgerType.LOUNGE_INCOME, othersSummary.getOrElse(OtherIncomeItemType.LOUNGE_INCOME, 0L)),
            AirlineLedgerEntry(airline.id, currentCycle, LedgerType.ASSET_EXPENSE, othersSummary.getOrElse(OtherIncomeItemType.ASSET_EXPENSE, 0L)),
            AirlineLedgerEntry(airline.id, currentCycle, LedgerType.ASSET_INCOME, othersSummary.getOrElse(OtherIncomeItemType.ASSET_REVENUE, 0L)),
            AirlineLedgerEntry(airline.id, currentCycle, LedgerType.ADVERTISEMENT, othersSummary.getOrElse(OtherIncomeItemType.ADVERTISEMENT, 0L)),
            AirlineLedgerEntry(airline.id, currentCycle, LedgerType.FUEL_COST, othersSummary.getOrElse(OtherIncomeItemType.FUEL_COST, 0L), Some(fuelCostDescription)),
            AirlineLedgerEntry(airline.id, currentCycle, LedgerType.CARBON_TAX, linksIncome.fuelTax, Some(carbonTaxDescription)),
            AirlineLedgerEntry(airline.id, currentCycle, LedgerType.LOAN_PAYMENT, -1 * loanPayment)
        )

        allLedgerEntries ++= weeklyLedger
      }
      //saving all at the very end

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
            calculatedCASK = (linksIncome.expense + -1 * (othersIncome.baseUpkeep + othersIncome.overtimeCompensation) + linksIncome.fuelTax).toDouble / totalASK //others are negative but should be positive, so they add; fuelTax is already negative so it will subtract
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
        StockModel.updateStockPrice(airline.getStockPrice(), airlineWeeklyStats, latestQuarterStatsByAirlineId.get(airline.id), currentInterestRate)
      }
      airline.setStockPrice(stockPrice)

      val airlineWeeklyIncome = AirlineIncome(airline.id, airlineProfit, airlineRevenue, airlineExpense, stockPrice, airlineValue.overall, linksIncome, othersIncome, cycle = currentCycle)
      allIncomes += airlineWeeklyIncome
    } //end each airline
    
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


    // Save weekly data first
    IncomeSource.saveIncomes(allIncomes.toList)
    AirlineSource.saveLedgerEntries(allLedgerEntries.toList)

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
    
    //update Oil consumption history
    println(s"Saving ${oilConsumptionEntries.size} oil consumption entries")
    OilSource.saveOilConsumptionHistory(oilConsumptionEntries.toList)
    OilSource.deleteOilConsumptionHistoryBeforeCycle(currentCycle - 10)
  }

  def computeAndSaveAccumulation(cycle: Int, allAirlines: List[Airline], period: Period.Value): Unit = {
    val periodWeeks = Period.numberWeeks(period)
    val startCycle = cycle - (cycle % periodWeeks) + 1
    val endCycle = cycle

    // Batch load all weekly incomes for all airlines in this period
    val weeklyIncomesByAirline = IncomeSource.loadWeeklyIncomesByCycleRange(startCycle, endCycle).groupBy(_.airlineId)

    val periodIncomes = scala.collection.mutable.ListBuffer[AirlineIncome]()

    allAirlines.foreach { airline =>
      weeklyIncomesByAirline.get(airline.id).foreach { weeks =>
        if (weeks.nonEmpty) {
          val summed = weeks.reduce { (acc, week) =>
            AirlineIncome(
              airline.id,
              profit = acc.profit + week.profit,
              revenue = acc.revenue + week.revenue,
              expense = acc.expense + week.expense,
              stockPrice = acc.stockPrice + week.stockPrice,
              totalValue = acc.totalValue + week.totalValue,
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
              others = acc.others.copy(
                profit = acc.others.profit + week.others.profit,
                revenue = acc.others.revenue + week.others.revenue,
                expense = acc.others.expense + week.others.expense,
                loanInterest = acc.others.loanInterest + week.others.loanInterest,
                baseUpkeep = acc.others.baseUpkeep + week.others.baseUpkeep,
                overtimeCompensation = acc.others.overtimeCompensation + week.others.overtimeCompensation,
                advertisement = acc.others.advertisement + week.others.advertisement,
                loungeUpkeep = acc.others.loungeUpkeep + week.others.loungeUpkeep,
                loungeIncome = acc.others.loungeIncome + week.others.loungeIncome,
                assetExpense = acc.others.assetExpense + week.others.assetExpense,
                assetRevenue = acc.others.assetRevenue + week.others.assetRevenue,
                fuelProfit = acc.others.fuelProfit + week.others.fuelProfit,
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
    }

    IncomeSource.saveIncomes(periodIncomes.toList)
  }
  
  def computeAndSaveStats(cycle: Int, allAirlines: List[Airline], period: Period.Value): Unit = {
    val periodWeeks = Period.numberWeeks(period)
    val startCycle = cycle - (cycle % periodWeeks) + 1
    val endCycle = cycle
    
    // Batch load all weekly stats for all airlines in this period
    val weeklyStatsByAirline = AirlineStatisticsSource
      .loadAirlineStatsByCycleRange(startCycle, endCycle, Period.WEEKLY)
      .groupBy(_.airlineId)

    val periodStats = scala.collection.mutable.ListBuffer[AirlineStat]()

    allAirlines.foreach { airline =>
      weeklyStatsByAirline.get(airline.id).foreach { weeks =>
        if (weeks.nonEmpty) {
          val summed = weeks.reduce { (acc, week) =>
            acc.update(week)
          }
          periodStats += summed.toAverage(weeks.length).copy(period = period, cycle = cycle)
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
