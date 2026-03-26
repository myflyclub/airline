package com.patson

import java.math.MathContext
import com.patson.data._
import com.patson.model.Bank
import com.patson.model.bank.LoanInterestRate

import scala.util.Random

object LoanInterestRateSimulation {

  val PREVIOUS_RATE_ENTRIES_TO_CONSIDER = 20

  def simulate(cycle: Int) : Unit = {
    val fromCycle = cycle - PREVIOUS_RATE_ENTRIES_TO_CONSIDER //last 20 weeks have influence on the rate calculation
    val rates = BankSource.loadLoanInterestRatesFromCycle(fromCycle).sortBy(_.cycle).map(_.annualRate)
    val nextRate = getNextRate(rates)
    if (!rates.isEmpty) {
      println(s"Interest   rate simulation [previous rate : ${rates.last}, new rate : $nextRate]")
    }

    BankSource.saveLoanInterestRate(LoanInterestRate(nextRate, cycle))
    //purge 400 turns ago
    BankSource.deleteLoanInterestRatesUpToCycle(cycle - 400)
  }

  def getNextRate(previousRates : List[BigDecimal]) : BigDecimal = {
    if (previousRates.isEmpty) {
      return Bank.DEFAULT_ANNUAL_RATE
    }
    // Count consecutive cycles at the current rate
    val cycleCount = previousRates.reverse.takeWhile(_ == previousRates.last).length

    //the closer a change has been made the less likely it will change again
    val shouldChange =
      if (cycleCount > 10) { //50% if > 10
        Random.nextBoolean()
      } else if (cycleCount > 5) { //otherwise 1/3
        Random.nextInt(3) == 2
      } else { //low chance
        Random.nextInt(20) <= cycleCount
      }

    if (shouldChange) { //now find out about the velocity
      simulateNextRate(previousRates.last)
    } else { //same rate as previous one
      previousRates.last
    }
  }

  val MAX_DELTA : BigDecimal = 0.025
  val RATE_STEP : BigDecimal = 0.005
  val MIN_RATE : BigDecimal = 0.01 //min rate is -1%
  val MAX_RATE : BigDecimal = 0.34
  val BOUNDARY_ZONE_DELTA_ADJUSTMENT = 0.005 // 0.5% adjustment if it's considered in abnormal range (ie > HIGH or < LOW threshold)
  val HIGH_RATE_THRESHOLD : BigDecimal = BigDecimal("0.3") //above here, dampen further rises
  val LOW_RATE_THRESHOLD  : BigDecimal = BigDecimal("0.08") //below here, dampen further drops
  private val TARGET_RATE : BigDecimal = BigDecimal(Bank.DEFAULT_ANNUAL_RATE.toString)
  private val FULL_RANGE  : BigDecimal = MAX_RATE - MIN_RATE

  def simulateNextRate(previousRate: BigDecimal): BigDecimal = {
     var newDelta = (Random.nextInt((MAX_DELTA / RATE_STEP).toInt) + 1) * RATE_STEP

     // Bias direction toward DEFAULT_ANNUAL_RATE: the further away, the stronger the pull
     val distanceFromTarget = ((previousRate - TARGET_RATE) / FULL_RANGE).toDouble
     val upProbability = (0.5 - distanceFromTarget).max(0.15).min(0.85)
     if (Random.nextDouble() >= upProbability) {
       newDelta *= -1
     }

     //now adjust the delta if it's very close to boundary zone
     if (previousRate <= LOW_RATE_THRESHOLD && newDelta < 0) { //still dropping
       newDelta += BOUNDARY_ZONE_DELTA_ADJUSTMENT
     } else if (previousRate >= HIGH_RATE_THRESHOLD && newDelta > 0) { //still rising
       newDelta -= BOUNDARY_ZONE_DELTA_ADJUSTMENT
     }

    if (newDelta > MAX_DELTA) {
      newDelta = MAX_DELTA
    } else if (newDelta < MAX_DELTA * -1) {
      newDelta = MAX_DELTA * -1
    }

    val newRate = previousRate + newDelta
    newRate.min(MAX_RATE).max(MIN_RATE)
  }
}