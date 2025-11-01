package com.patson.data

import java.sql.Connection
import com.patson.data.Constants._
import com.patson.model._

import scala.collection.mutable
import scala.collection.mutable.ListBuffer


object AirlineStatisticsSource {

  def saveAirlineStats(stats: List[AirlineStat]) = {
    val queryString = s"REPLACE INTO $AIRLINE_STATISTICS_TABLE (airline, cycle, period, tourists, elites, business, total, codeshares, rask, cask, satisfaction, load_factor, on_time, cash_on_hand, eps, link_count, rep_total, rep_leaderboards) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"
    val connection = Meta.getConnection()
    try {
      connection.setAutoCommit(false)
      val preparedStatement = connection.prepareStatement(queryString)
      stats.foreach { entry =>
        preparedStatement.setInt(1, entry.airlineId)
        preparedStatement.setInt(2, entry.cycle)
        preparedStatement.setInt(3, entry.period.id)
        preparedStatement.setInt(4, entry.tourists)
        preparedStatement.setInt(5, entry.elites)
        preparedStatement.setInt(6, entry.business)
        preparedStatement.setInt(7, entry.total)
        preparedStatement.setInt(8, entry.codeshares)
        preparedStatement.setDouble(9, entry.RASK)
        preparedStatement.setDouble(10, entry.CASK)
        preparedStatement.setDouble(11, BigDecimal(entry.satisfaction).setScale(4, BigDecimal.RoundingMode.HALF_UP).toDouble)
        preparedStatement.setDouble(12, BigDecimal(entry.loadFactor).setScale(4, BigDecimal.RoundingMode.HALF_UP).toDouble)
        preparedStatement.setDouble(13, BigDecimal(entry.onTime).setScale(4, BigDecimal.RoundingMode.HALF_UP).toDouble)
        preparedStatement.setInt(14, entry.cashOnHand)
        preparedStatement.setDouble(15, BigDecimal(entry.eps).setScale(4, BigDecimal.RoundingMode.HALF_UP).toDouble)
        preparedStatement.setInt(16, entry.linkCount)
        preparedStatement.setInt(17, entry.repTotal)
        preparedStatement.setInt(18, entry.repLeaderboards)
        preparedStatement.executeUpdate()
      }
      preparedStatement.close()
      connection.commit()
    } finally {
      connection.close()
    }
  }

  def deleteStatsBefore(cycleAndBefore : Int, period : Period.Value): Unit = {
    val connection = Meta.getConnection()
    try {
      connection.setAutoCommit(false)
      var deleteStatement = connection.prepareStatement("DELETE FROM " + AIRLINE_STATISTICS_TABLE + " WHERE cycle <= ? AND period = ?")
      deleteStatement.setInt(1, cycleAndBefore)
      deleteStatement.setInt(2, period.id)
      deleteStatement.executeUpdate()

      deleteStatement.close()
      connection.commit()
    } finally {
      connection.close()
    }
  }

  def loadAirlineStatsForAirlines(airlines: List[Airline]): List[AirlineStat] = {
    if (airlines.isEmpty) {
      List.empty
    } else {
      val connection = Meta.getConnection()
      val airlineIds = airlines.map(_.id)
      val lastCycle = CycleSource.loadCycle() - 1
      val queryString = new StringBuilder(s"SELECT * FROM $AIRLINE_STATISTICS_TABLE WHERE cycle = ? AND airline IN (");
      for (i <- 0 until airlineIds.size - 1) {
        queryString.append("?,")
      }
      queryString.append("?)") //last item has no comma

      try {
        val preparedStatement = connection.prepareStatement(queryString.toString())
        preparedStatement.setInt(1, lastCycle)
        for (i <- 0 until airlineIds.size) {
          preparedStatement.setObject(i + 2, airlineIds(i))
        }

        val resultSet = preparedStatement.executeQuery()
        val airlineStats = ListBuffer[AirlineStat]()

        while (resultSet.next()) {
          val airlineId = resultSet.getInt("airline")
          val cycle = resultSet.getInt("cycle")
          val period = Period(resultSet.getInt("period"))
          val tourists = resultSet.getInt("tourists")
          val elites = resultSet.getInt("elites")
          val business = resultSet.getInt("business")
          val total = resultSet.getInt("total")
          val codeshares = resultSet.getInt("codeshares")
          val rask = resultSet.getDouble("rask")
          val cask = resultSet.getDouble("cask")
          val satisfaction = resultSet.getDouble("satisfaction")
          val loadFactor = resultSet.getDouble("load_factor")
          val onTime = resultSet.getDouble("on_time")
          val cashOnHand = resultSet.getInt("cash_on_hand")
          val eps = resultSet.getDouble("eps")
          val linkCount = resultSet.getInt("link_count")
          val repTotal = resultSet.getInt("rep_total")
          val repLeaderboards = resultSet.getInt("rep_leaderboards")
          airlineStats += AirlineStat(airlineId, cycle, period, tourists, elites, business, total, codeshares, rask, cask, satisfaction, loadFactor, onTime, cashOnHand, eps, linkCount, repTotal, repLeaderboards)
        }

        airlineStats.toList
      } finally {
        connection.close()
      }
    }
  }

  def loadAirlineStat(airlineId: Int, cycle: Int): Option[AirlineStat] = {
    val airlineStats = loadAirlineStatsByCriteria(List(("airline", airlineId), ("cycle", cycle)))
    airlineStats.headOption
  }

  def loadAirlineStats(airlineId: Int): List[AirlineStat] = {
    loadAirlineStatsByCriteria(List(("airline", airlineId)))
  }

  def loadAirlineStatsByCycle(cycle: Int): List[AirlineStat] = {
    loadAirlineStatsByCriteria(List(("cycle", cycle)))
  }

  def loadAirlineStatsByCriteria(criteria: List[(String, Any)]) = {
    val connection = Meta.getConnection()
    val airlineStats = ListBuffer[AirlineStat]()
    try {
      val airlineStatQuery = getAirlineStatistics(connection, criteria)
      val resultSet = airlineStatQuery.executeQuery()

      while (resultSet.next()) {
        val airlineId = resultSet.getInt("airline")
        val cycle = resultSet.getInt("cycle")
        val period = Period(resultSet.getInt("period"))
        val tourists = resultSet.getInt("tourists")
        val elites = resultSet.getInt("elites")
        val business = resultSet.getInt("business")
        val total = resultSet.getInt("total")
        val codeshares = resultSet.getInt("codeshares")
        val rask = resultSet.getDouble("rask")
        val cask = resultSet.getDouble("cask")
        val satisfaction = resultSet.getDouble("satisfaction")
        val loadFactor = resultSet.getDouble("load_factor")
        val onTime = resultSet.getDouble("on_time")
        val cashOnHand = resultSet.getInt("cash_on_hand")
        val eps = resultSet.getDouble("eps")
        val linkCount = resultSet.getInt("link_count")
        val repTotal = resultSet.getInt("rep_total")
        val repLeaderboards = resultSet.getInt("rep_leaderboards")
        airlineStats += AirlineStat(airlineId, cycle, period, tourists, elites, business, total, codeshares, rask, cask, satisfaction, loadFactor, onTime, cashOnHand, eps, linkCount, repTotal, repLeaderboards)
      }

      airlineStats.toList
    } finally {
      connection.close()
    }
  }

  def getAirlineStatistics(connection: Connection, criteria: List[(String, Any)]) = {
    val queryString = new StringBuilder(s"SELECT * FROM $AIRLINE_STATISTICS_TABLE")

    if (!criteria.isEmpty) {
      queryString.append(" WHERE ")
      for (i <- 0 until criteria.size - 1) {
        queryString.append(criteria(i)._1 + " = ? AND ")
      }
      queryString.append(criteria.last._1 + " = ?")
    }

    val preparedStatement = connection.prepareStatement(queryString.toString())

    for (i <- 0 until criteria.size) {
      preparedStatement.setObject(i + 1, criteria(i)._2)
    }
    preparedStatement
  }

}
