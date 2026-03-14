package com.patson.model

import com.patson.data.BankSource
import com.patson.data.IncomeSource
import com.patson.data.CycleSource
import com.patson.data.AirlineSource
import com.patson.data.AirplaneSource

object Bank {
  val LOAN_TERMS = List[Int](26, Period.yearLength, 2 * Period.yearLength, 4 * Period.yearLength, 8 * Period.yearLength, 10 * Period.yearLength, 15 * Period.yearLength, 20 * Period.yearLength)
  val MAX_LOANS = 10
  val MIN_LOAN_AMOUNT = 10000
  val MAX_LOAN_AMOUNT : Long = 10000000000L //10b max
  val LOAN_REAPPLY_MIN_INTERVAL = 2
  val DEFAULT_ANNUAL_RATE = 0.11
  def getMaxLoan(airlineId : Int) : LoanReply = {
    val existingLoans = BankSource.loadLoansByAirline(airlineId)
    
    if (existingLoans.size >= MAX_LOANS) {
      return LoanReply(0, Some("Only up to " + MAX_LOANS + " loans are allowed"))
    }
    
    val currentCycle = CycleSource.loadCycle()
    
    existingLoans.sortBy(_.creationCycle).lastOption.foreach { previousLoan =>//check the last loan if there's one
      val weeksFromLastLoan = currentCycle - previousLoan.creationCycle
      if (weeksFromLastLoan < LOAN_REAPPLY_MIN_INTERVAL) {
        return LoanReply(0, Some("Can only apply next loan in " + (LOAN_REAPPLY_MIN_INTERVAL - weeksFromLastLoan) + " weeks"))
      }
    }
    
    val previousMonthCycle = currentCycle - currentCycle % Period.numberWeeks(Period.QUARTER) - 1
    
    val creditFromProfit : Option[Long] = IncomeSource.loadBalanceByAirline(airlineId, previousMonthCycle, Period.QUARTER).map(_.normalizedOperatingIncome * 6 * 2)  //1.5 * yearly link profit * 2
    
    val totalAssets = Computation.getResetAmount(airlineId).overall
    val creditFromAssets = (totalAssets * 0.2).toLong //offer 20% of the assets as credit
    val totalCredit = creditFromAssets + creditFromProfit.getOrElse(0L)
    
    val liability = existingLoans.map(_.remainingPayment(currentCycle)).sum
    
    val availableLoanAmount = Math.min(totalCredit - liability, Math.max(MAX_LOAN_AMOUNT - liability, 0))
    
    if (availableLoanAmount >= MIN_LOAN_AMOUNT) {
      LoanReply(availableLoanAmount, None)
    } else {
      LoanReply(0, Some("Lending you money is considered too high risk."))
    }
  }
 
  def getLoanOptions(principal : Long) : List[Loan] = {
    val currentCycle = CycleSource.loadCycle()
    BankSource.loadLoanInterestRateByCycle(currentCycle) match {
      case Some(currentRate) =>
        getLoanOptions(principal, currentRate, currentCycle)
      case None =>
        List.empty[Loan]
    }
  }

  def getLoanOptions(principal : Long, baseAnnualRate : Double, currentCycle : Int) = {
      LOAN_TERMS.map { term =>
        val annualRateByTerm = ( ( ( DEFAULT_ANNUAL_RATE - baseAnnualRate ) / (Period.yearLength * 20) ) * term + baseAnnualRate )
        Loan(airlineId = 0, principal = principal, annualRate = annualRateByTerm, creationCycle = currentCycle, lastPaymentCycle = currentCycle, term = term)
      }
  }

  def getLoan(airlineId : Int, principal : Long, rate : Double, currentCycle : Int, years : Int): Loan = {
    val term = years * Period.yearLength
    Loan(airlineId = airlineId, principal = principal, annualRate = rate, creationCycle = currentCycle, lastPaymentCycle = currentCycle, term = term)
  }

  case class LoanReply(maxLoan : Long, rejectionOption : Option[String])
}