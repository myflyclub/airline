package com.patson.model

import scala.collection.mutable.Map
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.testkit.ImplicitSender
import org.apache.pekko.testkit.TestKit
import com.patson.Util
import scala.collection.mutable.ListBuffer
import com.patson.OilSimulation
import com.patson.model.oil.OilPrice
 
class LoanSpec(_system: ActorSystem) extends TestKit(_system) with ImplicitSender
  with AnyWordSpecLike with Matchers with BeforeAndAfterAll {
 
  def this() = this(ActorSystem("MySpec"))
 
  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "early payment".must {
    "compute right values".in {
      val loan = Loan(airlineId = 0, principal = 1000000, annualRate = 0.1, creationCycle = 0, lastPaymentCycle = 0, term = 4 * Period.yearLength)
      for (i <- 0 to 4 * Period.yearLength) {
        println(s"Remaining principal on week $i : ${loan.remainingPrincipal(i)}, remaining payment : ${loan.remainingPayment(i)}")
      }

      assert(loan.weeklyPayment == 5838)
      assert(loan.interest == 214304)
      assert(loan.remainingPayment(0) == loan.principal + loan.interest)
      assert(loan.remainingPayment(4 * Period.yearLength) <= 0 )
      assert(loan.remainingPayment(4 * Period.yearLength) > -100 ) //could be a small negative due to weekly payment is "ceil"

      assert(loan.earlyRepayment(2 * Period.yearLength) < loan.remainingPayment(2 * Period.yearLength))
      assert(loan.earlyRepayment(2 * Period.yearLength) > loan.remainingPrincipal(2 * Period.yearLength))
    }
  }
}
