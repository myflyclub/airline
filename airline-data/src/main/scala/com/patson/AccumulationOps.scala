package com.patson

import com.patson.data.{CashFlowSource, IncomeSource}
import com.patson.model.{AirlineCashFlow, AirlineIncome, AirlineStat, Period}

object AccumulationOps {
  trait TypeClass[T] {
    def airlineId(t: T): Int
    def copyWithPeriod(t: T, period: Period.Value): T
    def update(t: T, value: T): T
    def loadPrevious(airlineId: Int, week: Int, period: Period.Value): Option[T]
  }

  implicit object AirlineIncomeAccumulationOps extends TypeClass[AirlineIncome] {
    def airlineId(t: AirlineIncome) = t.airlineId
    def copyWithPeriod(t: AirlineIncome, period: Period.Value) = t.copy(period = period)
    def update(t: AirlineIncome, value: AirlineIncome) = t.update(value)
    def loadPrevious(airlineId: Int, week: Int, period: Period.Value) =
      IncomeSource.loadIncomeByAirline(airlineId, week, period)
  }

  implicit object AirlineCashFlowAccumulationOps extends TypeClass[AirlineCashFlow] {
    def airlineId(t: AirlineCashFlow) = t.airlineId
    def copyWithPeriod(t: AirlineCashFlow, period: Period.Value) = t.copy(period = period)
    def update(t: AirlineCashFlow, value: AirlineCashFlow) = t.update(value)
    def loadPrevious(airlineId: Int, week: Int, period: Period.Value) =
      CashFlowSource.loadCashFlowByAirline(airlineId, week, period)
  }

  implicit object AirlineStatAccumulationOps extends TypeClass[AirlineStat] {
    def airlineId(t: AirlineStat) = t.airlineId
    def copyWithPeriod(t: AirlineStat, period: Period.Value) = t.copy(period = period)
    def update(t: AirlineStat, value: AirlineStat) = t.update(value)
    def loadPrevious(airlineId: Int, week: Int, period: Period.Value) = {
      val stats = com.patson.data.AirlineStatisticsSource.loadAirlineStatsByCriteria(List(("airline", airlineId), ("cycle", week), ("period", period.id)))
      stats.headOption
    }
  }
}
