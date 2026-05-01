package com.patson.data

import com.patson.data.Constants._
import com.patson.model._
import com.patson.util.{AirlineCache, AirportCache}

import java.sql.ResultSet
import scala.collection.mutable.ListBuffer
import scala.util.Using

object LoyalistSource {

  private def whereClause(criteria: List[(String, Any)]): String =
    if (criteria.nonEmpty) " WHERE " + criteria.map(_._1 + " = ?").mkString(" AND ") else ""

  private def readLoyalistHistory(rs: ResultSet): LoyalistHistory = {
    val airport = AirportCache.getAirport(rs.getInt("airport")).get
    val airline = AirlineCache.getAirline(rs.getInt("airline")).get
    LoyalistHistory(Loyalist(airport, airline, rs.getInt("amount")), rs.getInt("cycle"))
  }

  def updateLoyalists(loyalistEntries: List[Loyalist]): Unit =
    Using.Manager { use =>
      val connection = use(Meta.getConnection())
      connection.setAutoCommit(false)
      val statement = use(connection.prepareStatement(
        s"REPLACE INTO $LOYALIST_TABLE(airport, airline, amount) VALUES(?,?,?)"
      ))
      loyalistEntries.foreach { case Loyalist(airport, airline, amount) =>
        statement.setInt(1, airport.id)
        statement.setInt(2, airline.id)
        statement.setInt(3, amount)
        statement.addBatch()
      }
      statement.executeBatch()
      connection.commit()
    }.get

  def loadLoyalistsByCriteria(criteria: List[(String, Any)]): List[Loyalist] =
    loadLoyalistsByQueryString(s"SELECT * FROM $LOYALIST_TABLE${whereClause(criteria)}", criteria.map(_._2))

  def loadLoyalistsByAirportId(airportId: Int): List[Loyalist] =
    loadLoyalistsByCriteria(List(("airport", airportId)))

  private def loadLoyalistsByQueryString(queryString: String, parameters: List[Any]): List[Loyalist] =
    Using.Manager { use =>
      val connection = use(Meta.getConnection())
      val stmt = use(connection.prepareStatement(queryString))
      parameters.zipWithIndex.foreach { case (value, i) => stmt.setObject(i + 1, value) }
      val rs = use(stmt.executeQuery())
      val entries = ListBuffer[Loyalist]()
      while (rs.next()) {
        val airport = AirportCache.getAirport(rs.getInt("airport")).get
        val airline = AirlineCache.getAirline(rs.getInt("airline")).get
        entries += Loyalist(airport, airline, rs.getInt("amount"))
      }
      entries.toList
    }.get

  def deleteLoyalistsByAirline(airlineId: Int): Unit =
    Using.Manager { use =>
      val connection = use(Meta.getConnection())
      val statement = use(connection.prepareStatement(s"DELETE FROM $LOYALIST_TABLE WHERE airline = ?"))
      statement.setInt(1, airlineId)
      statement.executeUpdate()
    }.get

  def deleteLoyalists(loyalistEntries: List[Loyalist]): Unit =
    Using.Manager { use =>
      val connection = use(Meta.getConnection())
      connection.setAutoCommit(false)
      val statement = use(connection.prepareStatement(
        s"DELETE FROM $LOYALIST_TABLE WHERE airport = ? AND airline = ?"
      ))
      loyalistEntries.foreach { case Loyalist(airport, airline, _) =>
        statement.setInt(1, airport.id)
        statement.setInt(2, airline.id)
        statement.addBatch()
      }
      statement.executeBatch()
      connection.commit()
    }.get

  def deleteLoyalist(airportId: Int, airlineId: Int): Int =
    Using.Manager { use =>
      val connection = use(Meta.getConnection())
      val stmt = use(connection.prepareStatement(
        s"DELETE FROM $LOYALIST_TABLE WHERE airport = ? AND airline = ?"
      ))
      stmt.setInt(1, airportId)
      stmt.setInt(2, airlineId)
      stmt.executeUpdate()
    }.get

  def updateLoyalistHistory(entries: List[LoyalistHistory]): Unit = {
    val BATCH_SIZE = 10000
    Using.Manager { use =>
      val connection = use(Meta.getConnection())
      connection.setAutoCommit(false)
      val statement = use(connection.prepareStatement(
        s"REPLACE INTO $LOYALIST_HISTORY_TABLE(airport, airline, amount, cycle) VALUES(?,?,?,?)"
      ))
      entries.zipWithIndex.foreach { case (LoyalistHistory(Loyalist(airport, airline, amount), cycle), i) =>
        statement.setInt(1, airport.id)
        statement.setInt(2, airline.id)
        statement.setInt(3, amount)
        statement.setInt(4, cycle)
        statement.addBatch()
        if ((i + 1) % BATCH_SIZE == 0) {
          statement.executeBatch()
          connection.commit()
        }
      }
      if (entries.size % BATCH_SIZE != 0) {
        statement.executeBatch()
        connection.commit()
      }
    }.get
  }

  def loadLoyalistsHistoryByAirportId(airportId: Int): Map[Int, List[LoyalistHistory]] =
    Using.Manager { use =>
      val connection = use(Meta.getConnection())
      val stmt = use(connection.prepareStatement(
        s"SELECT airport, airline, amount, cycle FROM $LOYALIST_HISTORY_TABLE WHERE airport = ?"
      ))
      stmt.setInt(1, airportId)
      val rs = use(stmt.executeQuery())
      val entries = ListBuffer[LoyalistHistory]()
      while (rs.next()) entries += readLoyalistHistory(rs)
      entries.toList.groupBy(_.cycle)
    }.get

  def loadLoyalistHistoryByCriteria(criteria: List[(String, Any)]): List[LoyalistHistory] =
    Using.Manager { use =>
      val connection = use(Meta.getConnection())
      val stmt = use(connection.prepareStatement(
        s"SELECT airport, airline, amount, cycle FROM $LOYALIST_HISTORY_TABLE${whereClause(criteria)}"
      ))
      criteria.zipWithIndex.foreach { case ((_, value), i) => stmt.setObject(i + 1, value) }
      val rs = use(stmt.executeQuery())
      val entries = ListBuffer[LoyalistHistory]()
      while (rs.next()) entries += readLoyalistHistory(rs)
      entries.toList
    }.get

  def loadLoyalistHistoryByCycleAndAirline(cycle: Int, airlineId: Int): List[LoyalistHistory] =
    loadLoyalistHistoryByCriteria(List(("cycle", cycle), ("airline", airlineId)))

  def loadLoyalistHistoryByCycle(cycle: Int): List[LoyalistHistory] =
    loadLoyalistHistoryByCriteria(List(("cycle", cycle)))

  def deleteLoyalistHistoryBeforeCycle(cutoff: Int): Int = {
    val BATCH_SIZE = 10000
    Using.Manager { use =>
      val connection = use(Meta.getConnection())
      val stmt = use(connection.prepareStatement(
        s"DELETE FROM $LOYALIST_HISTORY_TABLE WHERE cycle < ? LIMIT $BATCH_SIZE"
      ))
      stmt.setInt(1, cutoff)
      var totalDeleted = 0
      var deleted = 0
      do {
        deleted = stmt.executeUpdate()
        totalDeleted += deleted
      } while (deleted == BATCH_SIZE)
      totalDeleted
    }.get
  }
}
