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

    val allBalances = ListBuffer[(AirlineBalance, AirlineBalanceDetails)]()
    val allLedgerEntries = ListBuffer[AirlineLedgerEntry]()
    val allAirlineStats = ListBuffer[AirlineStat]()

    val currentCycle = MainSimulation.currentWeek
    val airportChampionsByAirlineId : immutable.Map[Int, List[AirportChampionInfo]] = ChampionUtil.loadAirportChampionInfo().groupBy(_.loyalist.airline.id)
    val advertisementCostByAirlineId = ManagerSource.loadCampaignCostsByAirlineId()

    val allAirlineStatsByAirlineId: immutable.Map[Int, List[AirlineStat]] = AirlineStatisticsSource.loadAirlineStatsForAirlineIds(allAirlines.map(_.id)).groupBy(_.airlineId) //used for stock price
    val latestQuarterStatsByAirlineId: immutable.Map[Int, AirlineStat] = allAirlineStatsByAirlineId.flatMap {
        case (airlineId, stats) => stats.filter(_.period == Period.QUARTER).maxByOption(_.cycle).map(stat => (airlineId, stat))
    }

    var startTime = System.currentTimeMillis()
    val leaderboardsByAirlineId = RankingSimulation.process(cycle, allAirlines.filter(_.getReputation() > 60), flightLinkResult, loungeResult, paxStats)
    Util.outputTimeDiff(startTime, "Generating rankings took")

    val fuelContractsByAirlineId = OilSource.loadAllOilContracts().groupBy(contract => contract.airline.id)
    val fuelInventoryPolicyByAirlineId = OilSource.loadAllOilInventoryPolicies.map(policy => (policy.airline.id, policy)).toMap
    val currentFuelPrice = OilSource.loadOilPriceByCycle(cycle).get.price
    val oilConsumptionEntries = ListBuffer[OilConsumptionHistory]()

    allAirlines.foreach { airline =>
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

      val (linksRevenue, linksAirportFee, linksCrewCost, linksFuelCost, linksFuelTax, linksInflightCost, linksDelayCompensation, linksMaintenanceCost, linksDepreciation, linksLoungeCost) = flightLinkResultByAirline.get(airline.id) match {
          case Some(lc) =>
            lc.foldLeft((0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L)) {
              case ((r, a, c, f, t, i, d, m, depr, l), lcd) =>
                (r + lcd.revenue, a + lcd.airportFees, c + lcd.crewCost, f + lcd.fuelCost,
                 t + lcd.fuelTax, i + lcd.inflightCost, d + lcd.delayCompensation,
                 m + lcd.maintenanceCost, depr + lcd.depreciation, l + lcd.loungeCost)
            }
          case None => (0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L)
        }

      
      val staffCost = airline.bases.foldLeft(0L)((upkeep, base) => upkeep + base.getUpkeep)

      val linksByFromAirportId = allFlightLinksByAirlineId.getOrElse(airline.id, List.empty).groupBy(_.from.id)
      val staffOvertimeCost = airline.bases.map { base =>
        val staffRequired = linksByFromAirportId.get(base.airport.id)
          .map(_.map(_.getCurrentOfficeStaffRequired).sum)
          .getOrElse(0)
        base.getOvertimeCompensation(staffRequired.toInt)
      }.sum

      val loungeUpkeep = loungesByAirlineId.get(airline.id).map(_.map(_.getUpkeep).sum).getOrElse(0L)
      val (loungeServiceCost, loungeRevenue) = loungeResult
        .filter(_.lounge.airline.id == airline.id)
        .foldLeft((0, 0)) {
          case ((costAcc, revenueAcc), LoungeConsumptionDetails(_, selfVisitors, allianceVisitors, _)) =>
            val visitors = selfVisitors + allianceVisitors
            (
              costAcc + visitors * Lounge.PER_VISITOR_COST,
              revenueAcc + visitors * Lounge.PER_VISITOR_CHARGE
            )
        }
      val loungeCost = -(loungeServiceCost.toLong + loungeUpkeep) // negative
      val loungeTotalCost = loungeServiceCost + loungeUpkeep + linksLoungeCost // positive raw total cost

      val advertisementEntry = -advertisementCostByAirlineId.getOrElse(airline.id, 0).toLong // negative

      //calculate extra cash flow due to difference in fuel cost
      val barrelsUsed = (linksFuelCost / OilPrice.DEFAULT_PRICE).toInt
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
            val sellPrice = currentFuelPrice * 0.7
            val excessBarrels = totalVolumeFromContract - barrelsUsed
            oilConsumptionEntries += OilConsumptionHistory(airline, sellPrice, excessBarrels * -1, OilConsumptionType.EXCESS, currentCycle)
            totalPaymentFromContract - excessBarrels * sellPrice
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
        val perCost = if (barrelsUsed > 0) (actualFuelCost / barrelsUsed * 100).toInt.toDouble / 100 else 0.0
        s"$barrelsUsed barrels at ~ $perCost average"
      }
      val carbonTaxDescription = s"Paying ${airline.fuelTaxRate}% fuel rate"

      val negativeCashInterest = if (airlineValue.existingBalance < 0) {
        (airlineValue.existingBalance * LoanInterestRateSimulation.HIGH_RATE_THRESHOLD / Period.yearLength).toLong
      } else {
        0L
      }
      val (loanPayment, interestPayment) = updateLoans(airline, currentCycle + 1) //have to plus one cycle, as this is supposed to be done postcycle, but accounting is here
      val loanInterestEntry = -interestPayment + negativeCashInterest // negative or zero

      // sum it all up; create vals for EPS etc
      val airlineRevenue = linksRevenue + loungeRevenue
      val airlineExpenseNormalized = staffCost + staffOvertimeCost + linksAirportFee + linksCrewCost + linksFuelCost + linksInflightCost + linksDelayCompensation + linksMaintenanceCost + linksDepreciation + loungeTotalCost + advertisementEntry
      val airlineExpense = airlineExpenseNormalized + -loanInterestEntry + linksFuelTax + (actualFuelCost - linksFuelCost)
      val airlineProfit = airlineRevenue - airlineExpense

      // Record weekly ledger entries
      if (!isBankrupt) {
        val weeklyLedger = List(
            AirlineLedgerEntry(airline.id, currentCycle, LedgerType.FLIGHT_REVENUE, linksRevenue),
            AirlineLedgerEntry(airline.id, currentCycle, LedgerType.FLIGHT_CREW, -linksCrewCost),
            AirlineLedgerEntry(airline.id, currentCycle, LedgerType.AIRPORT_RENTALS, -linksAirportFee),
            AirlineLedgerEntry(airline.id, currentCycle, LedgerType.INFLIGHT_SERVICE, -linksInflightCost),
            AirlineLedgerEntry(airline.id, currentCycle, LedgerType.MAINTENANCE, -linksMaintenanceCost),
            AirlineLedgerEntry(airline.id, currentCycle, LedgerType.PASSENGER_LOUNGE_COSTS, -linksLoungeCost),
            AirlineLedgerEntry(airline.id, currentCycle, LedgerType.DELAY_COMPENSATION, -linksDelayCompensation),
            //don't record fuel or deprecation here as they're purchased outside links
            AirlineLedgerEntry(airline.id, currentCycle, LedgerType.BASE_UPKEEP, -staffCost),
            AirlineLedgerEntry(airline.id, currentCycle, LedgerType.OVERTIME_COMPENSATION, -staffOvertimeCost.toLong),
            AirlineLedgerEntry(airline.id, currentCycle, LedgerType.LOUNGE_COST, loungeCost),
            AirlineLedgerEntry(airline.id, currentCycle, LedgerType.LOUNGE_INCOME, loungeRevenue),
            AirlineLedgerEntry(airline.id, currentCycle, LedgerType.ADVERTISING, advertisementEntry),
            AirlineLedgerEntry(airline.id, currentCycle, LedgerType.FUEL_COST, -actualFuelCost.toLong, Some(fuelCostDescription)),
            AirlineLedgerEntry(airline.id, currentCycle, LedgerType.CARBON_TAX, -linksFuelTax, Some(carbonTaxDescription)),
            AirlineLedgerEntry(airline.id, currentCycle, LedgerType.LOAN_PAYMENT, -loanPayment),
            AirlineLedgerEntry(airline.id, currentCycle, LedgerType.NEGATIVE_BALANCE_LOAN_INTEREST, -negativeCashInterest)
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

          // Calculate RASK & CASK
          if (totalASK > 0) {
            calculatedRASK = linksRevenue.toDouble / totalASK
            calculatedCASK = airlineExpenseNormalized.toDouble / totalASK
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
        // RASK/CASK remain 0.0 since all link metrics are 0.
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
      var targetReputation = finalBreakdowns.total
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
      val stockPrice = if (isBankrupt || airline.airlineType.stockRepPerLevel == 0) {
         0.0
      } else {
        StockModel.updateStockPrice(airline.getStockPrice(), airlineWeeklyStats, latestQuarterStatsByAirlineId.get(airline.id), currentInterestRate)
      }
      airline.setStockPrice(stockPrice)

      val weeklyDetails = AirlineBalanceDetails(
        airlineId = airline.id,
        ticketRevenue = linksRevenue,
        loungeRevenue = loungeRevenue,
        staff = -staffCost,
        staffOvertime = -staffOvertimeCost.toLong,
        flightCrew = -linksCrewCost,
        fuel = -actualFuelCost.toLong,
        fuelTax = -linksFuelTax,
        fuelNormalized = -linksFuelCost, // negative normalized cost at $70/barrel
        deprecation = -linksDepreciation,
        airportRentals = -linksAirportFee,
        inflightService = -linksInflightCost,
        delay = -linksDelayCompensation,
        maintenance = -linksMaintenanceCost,
        lounge = -loungeTotalCost, // per-flight lounge cost + facility upkeep/visitor cost
        advertising = advertisementEntry,
        loanInterest = loanInterestEntry,
        cycle = currentCycle)
      val weeklyBalance = AirlineBalance(
        airlineId = airline.id,
        income = airlineProfit.toLong,
        normalizedOperatingIncome = airlineRevenue - airlineExpenseNormalized,
        cashOnHand = airlineValue.existingBalance,
        totalValue = airlineValue.overall,
        stockPrice = stockPrice,
        cycle = currentCycle)
      allBalances += ((weeklyBalance, weeklyDetails))
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
    IncomeSource.saveBalances(allBalances.toList)
    AirlineSource.saveLedgerEntries(allLedgerEntries.toList)

    // Batch compute and save accumulated data at period boundaries
    if ((currentCycle + 1) % Period.numberWeeks(Period.QUARTER) == 0) {
      computeAndSaveAccumulation(currentCycle, allAirlines, Period.QUARTER)
    }
    if ((currentCycle + 1) % Period.numberWeeks(Period.YEAR) == 0) {
      computeAndSaveAccumulation(currentCycle, allAirlines, Period.YEAR)
    }

    //purge old entries
    IncomeSource.deleteBalancesBefore(currentCycle - BOOKKEEPING_ENTRIES_COUNT, Period.WEEKLY)
    IncomeSource.deleteBalancesBefore(currentCycle - BOOKKEEPING_ENTRIES_COUNT * Period.numberWeeks(Period.QUARTER), Period.QUARTER)
    IncomeSource.deleteBalancesBefore(currentCycle - BOOKKEEPING_ENTRIES_COUNT * Period.numberWeeks(Period.YEAR), Period.YEAR)
    
    //update Oil consumption history
    println(s"Saving ${oilConsumptionEntries.size} oil consumption entries")
    OilSource.saveOilConsumptionHistory(oilConsumptionEntries.toList)
    OilSource.deleteOilConsumptionHistoryBeforeCycle(currentCycle - 10)
  }

  def computeAndSaveAccumulation(cycle: Int, allAirlines: List[Airline], period: Period.Value): Unit = {
    val periodWeeks = Period.numberWeeks(period)
    val startCycle = cycle - (cycle % periodWeeks) + 1
    val endCycle = cycle

    val weeklyByAirline = IncomeSource.loadWeeklyBalancesByCycleRange(startCycle, endCycle).groupBy(_._1.airlineId)
    val periodBalances = scala.collection.mutable.ListBuffer[(AirlineBalance, AirlineBalanceDetails)]()

    allAirlines.foreach { airline =>
      weeklyByAirline.get(airline.id).foreach { weeks =>
        if (weeks.nonEmpty) {
          val n = weeks.length
          val sumBal = weeks.map(_._1).reduce { (a, b) =>
            a.copy(income = a.income + b.income, normalizedOperatingIncome = a.normalizedOperatingIncome + b.normalizedOperatingIncome,
              cashOnHand = a.cashOnHand + b.cashOnHand, totalValue = a.totalValue + b.totalValue, stockPrice = a.stockPrice + b.stockPrice)
          }
          val sumDet = weeks.map(_._2).reduce { (a, b) =>
            a.copy(ticketRevenue = a.ticketRevenue + b.ticketRevenue, loungeRevenue = a.loungeRevenue + b.loungeRevenue,
              staff = a.staff + b.staff, staffOvertime = a.staffOvertime + b.staffOvertime,
              flightCrew = a.flightCrew + b.flightCrew, fuel = a.fuel + b.fuel,
              fuelTax = a.fuelTax + b.fuelTax, fuelNormalized = a.fuelNormalized + b.fuelNormalized,
              deprecation = a.deprecation + b.deprecation, airportRentals = a.airportRentals + b.airportRentals,
              inflightService = a.inflightService + b.inflightService, delay = a.delay + b.delay,
              maintenance = a.maintenance + b.maintenance, lounge = a.lounge + b.lounge,
              advertising = a.advertising + b.advertising, loanInterest = a.loanInterest + b.loanInterest)
          }
          val lastWeek = weeks.maxBy(_._1.cycle)._1
          val periodBal = sumBal.copy(
            cashOnHand = lastWeek.cashOnHand,
            totalValue = lastWeek.totalValue,
            stockPrice = sumBal.stockPrice / n,
            period = period, cycle = cycle)
          val periodDet = sumDet.copy(period = period, cycle = cycle)
          periodBalances += ((periodBal, periodDet))
        }
      }
    }

    IncomeSource.saveBalances(periodBalances.toList)
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
