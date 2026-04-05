package com.patson.model

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.util.Calendar

class UserSpec extends AnyFunSuite with Matchers {

  private def makeUser(level: Int) = User("test", "test@test.com", Calendar.getInstance(), Calendar.getInstance(), UserStatus.ACTIVE, level, None, List.empty, id = 1)

  test("maxAirlinesAllowed returns 2 for free user (level=0)") {
    makeUser(level = 0).maxAirlinesAllowed shouldBe 2
  }

  test("maxAirlinesAllowed returns 3 for premium user (level>0)") {
    makeUser(level = 1).maxAirlinesAllowed shouldBe 3
    makeUser(level = 5).maxAirlinesAllowed shouldBe 3
  }

  test("hasAccessToAirline returns true for linked airlines") {
    val user = makeUser(level = 0)
    val airline = Airline("Test Air", id = 10)
    user.setAccesibleAirlines(List(airline))
    user.hasAccessToAirline(10) shouldBe true
  }

  test("hasAccessToAirline returns false for unlinked airlines") {
    val user = makeUser(level = 0)
    val airline = Airline("Test Air", id = 10)
    user.setAccesibleAirlines(List(airline))
    user.hasAccessToAirline(99) shouldBe false
  }

  test("getAccessibleAirlines returns all linked airlines") {
    val user = makeUser(level = 0)
    val airline1 = Airline("Air One", id = 10)
    val airline2 = Airline("Air Two", id = 20)
    user.setAccesibleAirlines(List(airline1, airline2))
    val accessible = user.getAccessibleAirlines()
    accessible.map(_.id).toSet shouldBe Set(10, 20)
  }

  test("setAccesibleAirlines correctly populates internal map") {
    val user = makeUser(level = 0)
    user.getAccessibleAirlines() shouldBe empty
    val airline = Airline("Test Air", id = 5)
    user.setAccesibleAirlines(List(airline))
    user.getAccessibleAirlines().size shouldBe 1
    user.getAccessibleAirlines().head.id shouldBe 5
  }
}
