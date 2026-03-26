package com.patson

import com.patson.model.Bank
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.matchers.should.Matchers

class LoanInterestRateSimulationSpec extends AnyWordSpecLike with Matchers {

  private def generateRates(cycles: Int): List[BigDecimal] =
    (1 to cycles).foldLeft(List.empty[BigDecimal]) { (history, _) =>
      LoanInterestRateSimulation.getNextRate(history) :: history
    }.reverse

  "LoanInterestRateSimulation" should {
    "produce rates that average near Bank.DEFAULT_ANNUAL_RATE over 1000 cycles" in {
      val rates   = generateRates(1000)
      val average = rates.sum / rates.length
      val tolerance = BigDecimal("0.03") // within 3 percentage points of the default rate

      withClue(s"Average rate $average should be near ${Bank.DEFAULT_ANNUAL_RATE}") {
        (average - Bank.DEFAULT_ANNUAL_RATE).abs should be <= tolerance
      }
    }

    "never produce rates outside [MIN_RATE, MAX_RATE]" in {
      generateRates(1000).foreach { rate =>
        rate should be >= LoanInterestRateSimulation.MIN_RATE
        rate should be <= LoanInterestRateSimulation.MAX_RATE
      }
    }
  }
}
