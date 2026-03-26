package com.patson.model

import com.patson.LoanInterestRateSimulation
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.matchers.should.Matchers

class BankSpec extends AnyWordSpecLike with Matchers {

  private val principal = 100_000_000L

  private def assertTotalsIncreaseWithTerm(rate: Double): Unit = {
    val loans = Bank.LOAN_TERMS.map { term =>
      Loan(airlineId = 0, principal = principal, annualRate = rate, creationCycle = 0, lastPaymentCycle = 0, term = term)
    }
    loans.sliding(2).foreach {
      case List(shorter, longer) =>
        withClue(s"rate=$rate: term ${longer.term} total (${longer.total}) should exceed term ${shorter.term} total (${shorter.total})") {
          longer.total should be > shorter.total
        }
      case _ =>
    }
  }

  "Bank loan terms" should {
    "produce higher total payment for longer terms at the default rate" in {
      assertTotalsIncreaseWithTerm(Bank.DEFAULT_ANNUAL_RATE)
    }

    "produce higher total payment for longer terms at the high rate threshold" in {
      assertTotalsIncreaseWithTerm(LoanInterestRateSimulation.HIGH_RATE_THRESHOLD.toDouble)
    }

    "produce higher total payment for longer terms at the low rate threshold" in {
      assertTotalsIncreaseWithTerm(LoanInterestRateSimulation.LOW_RATE_THRESHOLD.toDouble)
    }
  }
}
