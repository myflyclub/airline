package com.patson.model

object Period extends Enumeration {
  type Period = Value
  val WEEKLY, QUARTER, YEAR = Value
  val yearLength = 48

  def numberWeeks(period : Period.Value) = {
    period match {
      case WEEKLY => 1
      case QUARTER => 12
      case YEAR => yearLength
    }
  }
}