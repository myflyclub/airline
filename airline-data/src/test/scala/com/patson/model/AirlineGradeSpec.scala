package com.patson.model

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class AirlineGradeSpec extends AnyFunSuite with Matchers {
  
  test("AirlineGrades: value below smallest threshold returns level 0 with correct ceiling") {
    val grade = AirlineGrades.findGrade(10.0)
    grade.level shouldBe 0
    grade.reputationCeiling shouldBe 25.0
    grade.reputationFloor shouldBe 0.0
  }

  test("AirlineGrades: value between 2nd and 3rd threshold returns level 2 with correct floor and ceiling") {
    val grade = AirlineGrades.findGrade(60.0) // between 50.0 and 75.0
    grade.level shouldBe 2
    grade.reputationFloor shouldBe 50.0
    grade.reputationCeiling shouldBe 75.0
  }

  test("AirlineGrades: value above all thresholds returns level equal to grades length with MaxValue ceiling") {
    val grade = AirlineGrades.findGrade(9999.0)
    println(s"Grade: $grade")
    grade.level shouldBe AirlineGrades.grades.length
    grade.reputationCeiling shouldBe Double.MaxValue
    grade.reputationFloor shouldBe 2000.0
  }

  test("AirlineGradeStockPrice: value below smallest threshold returns level 0 with correct ceiling") {
    val grade = AirlineGradeStockPrice.findGrade(0.5)
    grade.level shouldBe 0
    grade.reputationCeiling shouldBe 0.7
    grade.reputationFloor shouldBe 0.0
  }

  test("AirlineGradeStockPrice: penultimate value") {
    val grade = AirlineGradeStockPrice.findGrade(1090.0)
    grade.level shouldBe 12
    grade.reputationFloor shouldBe 1075.0
    grade.reputationCeiling shouldBe 1725.0
  }

  test("AirlineGradeStockPrice: value above all thresholds returns level equal to grades length with MaxValue ceiling") {
    val grade = AirlineGradeStockPrice.findGrade(99999.0)
    println(s"Grade: $grade")
    grade.level shouldBe AirlineGradeStockPrice.grades.length
    grade.reputationCeiling shouldBe Double.MaxValue
    grade.reputationFloor shouldBe 1725.0
  }
}
