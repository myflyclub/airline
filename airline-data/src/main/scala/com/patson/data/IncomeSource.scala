package com.patson.data
import com.patson.data.Constants._
import scala.collection.mutable.ListBuffer
import java.sql.Connection
import com.patson.model._



object IncomeSource {

  def saveBalances(balances: List[(AirlineBalance, AirlineBalanceDetails)]): Unit = {
    val connection = Meta.getConnection()
    val balStmt = connection.prepareStatement(
      "REPLACE INTO " + BALANCE_TABLE +
      "(airline, income, normalized_operating_income, cash_on_hand, total_value, stock_price, period, cycle)" +
      " VALUES(?,?,?,?,?,?,?,?)")
    val detStmt = connection.prepareStatement(
      "REPLACE INTO " + BALANCE_DETAILS_TABLE +
      "(airline, ticket_revenue, lounge_revenue, staff, staff_overtime, flight_crew, fuel, fuel_tax," +
      " fuel_normalized, deprecation, airport_rentals, inflight_service, delay, maintenance, lounge," +
      " advertising, loan_interest, period, cycle)" +
      " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")
    try {
      connection.setAutoCommit(false)
      balances.foreach { case (bal, det) =>
        balStmt.setInt(1, bal.airlineId)
        balStmt.setLong(2, bal.income)
        balStmt.setLong(3, bal.normalizedOperatingIncome)
        balStmt.setLong(4, bal.cashOnHand)
        balStmt.setLong(5, bal.totalValue)
        balStmt.setDouble(6, bal.stockPrice)
        balStmt.setInt(7, bal.period.id)
        balStmt.setInt(8, bal.cycle)
        balStmt.addBatch()

        detStmt.setInt(1, det.airlineId)
        detStmt.setLong(2, det.ticketRevenue)
        detStmt.setLong(3, det.loungeRevenue)
        detStmt.setLong(4, det.staff)
        detStmt.setLong(5, det.staffOvertime)
        detStmt.setLong(6, det.flightCrew)
        detStmt.setLong(7, det.fuel)
        detStmt.setLong(8, det.fuelTax)
        detStmt.setLong(9, det.fuelNormalized)
        detStmt.setLong(10, det.deprecation)
        detStmt.setLong(11, det.airportRentals)
        detStmt.setLong(12, det.inflightService)
        detStmt.setLong(13, det.delay)
        detStmt.setLong(14, det.maintenance)
        detStmt.setLong(15, det.lounge)
        detStmt.setLong(16, det.advertising)
        detStmt.setLong(17, det.loanInterest)
        detStmt.setInt(18, det.period.id)
        detStmt.setInt(19, det.cycle)
        detStmt.addBatch()
      }
      balStmt.executeBatch()
      detStmt.executeBatch()
      balStmt.close()
      detStmt.close()
      connection.commit()
    } finally {
      connection.close()
    }
  }

  def loadBalancesByAirline(airlineId: Int): List[(AirlineBalance, AirlineBalanceDetails)] = {
    loadBalancesByCriteria(List(("b.airline", airlineId)))
  }

  def loadBalanceByAirline(airlineId: Int, cycle: Int, period: Period.Value): Option[AirlineBalance] = {
    loadBalancesByCriteria(List(("b.airline", airlineId), ("b.cycle", cycle), ("b.period", period.id)))
      .headOption.map(_._1)
  }

  def loadAllBalancesByCycle(cycle: Int): List[AirlineBalance] = {
    loadBalancesByCriteria(List(("b.cycle", cycle), ("b.period", 0))).map(_._1)
  }

  def loadWeeklyBalancesByCycleRange(startCycle: Int, endCycle: Int): List[(AirlineBalance, AirlineBalanceDetails)] = {
    val connection = Meta.getConnection()
    try {
      val sql = "SELECT b.*, d.* FROM " + BALANCE_TABLE + " b" +
        " JOIN " + BALANCE_DETAILS_TABLE + " d ON b.airline = d.airline AND b.period = d.period AND b.cycle = d.cycle" +
        " WHERE b.cycle >= ? AND b.cycle <= ? AND b.period = 0" +
        " ORDER BY b.airline, b.cycle"
      val stmt = connection.prepareStatement(sql)
      stmt.setInt(1, startCycle)
      stmt.setInt(2, endCycle)
      val rs = stmt.executeQuery()
      val result = ListBuffer[(AirlineBalance, AirlineBalanceDetails)]()
      while (rs.next()) result += readBalanceRow(rs)
      stmt.close()
      result.toList
    } finally {
      connection.close()
    }
  }

  def deleteBalancesBefore(cycleAndBefore: Int, period: Period.Value): Unit = {
    val connection = Meta.getConnection()
    try {
      connection.setAutoCommit(false)
      val stmt1 = connection.prepareStatement("DELETE FROM " + BALANCE_TABLE + " WHERE cycle <= ? AND period = ?")
      stmt1.setInt(1, cycleAndBefore); stmt1.setInt(2, period.id); stmt1.executeUpdate(); stmt1.close()
      val stmt2 = connection.prepareStatement("DELETE FROM " + BALANCE_DETAILS_TABLE + " WHERE cycle <= ? AND period = ?")
      stmt2.setInt(1, cycleAndBefore); stmt2.setInt(2, period.id); stmt2.executeUpdate(); stmt2.close()
      connection.commit()
    } finally {
      connection.close()
    }
  }

  def loadStockPriceHistory(airlineIds: List[Int]): List[(Int, Int, Int, Double, Long)] = {
    if (airlineIds.isEmpty) return List.empty
    val connection = Meta.getConnection()
    val placeholders = airlineIds.map(_ => "?").mkString(",")
    val sql = s"SELECT airline, cycle, period, stock_price, total_value FROM $BALANCE_TABLE WHERE airline IN ($placeholders) ORDER BY cycle"
    try {
      val stmt = connection.prepareStatement(sql)
      for (i <- airlineIds.indices) stmt.setInt(i + 1, airlineIds(i))
      val rs = stmt.executeQuery()
      val results = ListBuffer[(Int, Int, Int, Double, Long)]()
      while (rs.next()) {
        results += ((rs.getInt("airline"), rs.getInt("cycle"), rs.getInt("period"),
          rs.getDouble("stock_price"), rs.getLong("total_value")))
      }
      results.toList
    } finally {
      connection.close()
    }
  }

  private def loadBalancesByCriteria(criteria: List[(String, Any)]): List[(AirlineBalance, AirlineBalanceDetails)] = {
    val connection = Meta.getConnection()
    try {
      val whereClause = if (criteria.isEmpty) "" else " WHERE " + criteria.map(_._1 + " = ?").mkString(" AND ")
      val sql = "SELECT b.*, d.* FROM " + BALANCE_TABLE + " b" +
        " JOIN " + BALANCE_DETAILS_TABLE + " d ON b.airline = d.airline AND b.period = d.period AND b.cycle = d.cycle" +
        whereClause
      val stmt = connection.prepareStatement(sql)
      criteria.zipWithIndex.foreach { case ((_, v), i) =>
        v match {
          case n: Int => stmt.setInt(i + 1, n)
          case _ => stmt.setObject(i + 1, v)
        }
      }
      val rs = stmt.executeQuery()
      val result = ListBuffer[(AirlineBalance, AirlineBalanceDetails)]()
      while (rs.next()) result += readBalanceRow(rs)
      stmt.close()
      result.toList
    } finally {
      connection.close()
    }
  }

  private def readBalanceRow(rs: java.sql.ResultSet): (AirlineBalance, AirlineBalanceDetails) = {
    val airlineId = rs.getInt("b.airline")
    val period = Period(rs.getInt("b.period"))
    val cycle = rs.getInt("b.cycle")
    val bal = AirlineBalance(
      airlineId = airlineId,
      income = rs.getLong("b.income"),
      normalizedOperatingIncome = rs.getLong("b.normalized_operating_income"),
      cashOnHand = rs.getLong("b.cash_on_hand"),
      totalValue = rs.getLong("b.total_value"),
      stockPrice = rs.getDouble("b.stock_price"),
      period = period,
      cycle = cycle)
    val det = AirlineBalanceDetails(
      airlineId = airlineId,
      ticketRevenue = rs.getLong("d.ticket_revenue"),
      loungeRevenue = rs.getLong("d.lounge_revenue"),
      staff = rs.getLong("d.staff"),
      staffOvertime = rs.getLong("d.staff_overtime"),
      flightCrew = rs.getLong("d.flight_crew"),
      fuel = rs.getLong("d.fuel"),
      fuelTax = rs.getLong("d.fuel_tax"),
      fuelNormalized = rs.getLong("d.fuel_normalized"),
      deprecation = rs.getLong("d.deprecation"),
      airportRentals = rs.getLong("d.airport_rentals"),
      inflightService = rs.getLong("d.inflight_service"),
      delay = rs.getLong("d.delay"),
      maintenance = rs.getLong("d.maintenance"),
      lounge = rs.getLong("d.lounge"),
      advertising = rs.getLong("d.advertising"),
      loanInterest = rs.getLong("d.loan_interest"),
      period = period,
      cycle = cycle)
    (bal, det)
  }

  object DetailType extends Enumeration {
    type Type = Value
    val AIRPORT, AIRLINE, AIRPLANE = Value
  }
}
