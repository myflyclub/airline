package com.patson.data

import com.patson.data.Constants._

import scala.collection.mutable.ListBuffer

case class MissedDemandEntry(fromAirportId: Int, toAirportId: Int, passengerType: Int,
                              preferenceType: Int, preferredLinkClass: String, passengerCount: Int)

object MissedDemandSource {

  def deleteAndSave(entries: Iterable[MissedDemandEntry]): Unit = {
    val connection = Meta.getConnection()
    try {
      connection.setAutoCommit(false)
      val truncStmt = connection.createStatement()
      truncStmt.executeUpdate("TRUNCATE TABLE " + PASSENGER_MISSED_DEMAND_TABLE)
      truncStmt.close()

      if (entries.nonEmpty) {
        val insertStmt = connection.prepareStatement(
          "INSERT INTO " + PASSENGER_MISSED_DEMAND_TABLE +
            " (from_airport, to_airport, passenger_type, preference_type, preferred_link_class, passenger_count)" +
            " VALUES (?, ?, ?, ?, ?, ?)"
        )
        var batchCount = 0
        entries.foreach { e =>
          insertStmt.setInt(1, e.fromAirportId)
          insertStmt.setInt(2, e.toAirportId)
          insertStmt.setInt(3, e.passengerType)
          insertStmt.setInt(4, e.preferenceType)
          insertStmt.setString(5, e.preferredLinkClass)
          insertStmt.setInt(6, e.passengerCount)
          insertStmt.addBatch()
          batchCount += 1
          if (batchCount % 1000 == 0) insertStmt.executeBatch()
        }
        insertStmt.executeBatch()
        insertStmt.close()
      }
      connection.commit()
    } catch {
      case e: Exception =>
        println(s"MissedDemandSource.deleteAndSave failed: ${e.getMessage}")
        connection.rollback()
    } finally {
      connection.close()
    }
  }

  def loadByFromAirport(fromAirportId: Int): List[MissedDemandEntry] = {
    val connection = Meta.getConnection()
    try {
      val stmt = connection.prepareStatement(
        "SELECT from_airport, to_airport, passenger_type, preference_type, preferred_link_class, passenger_count" +
          " FROM " + PASSENGER_MISSED_DEMAND_TABLE +
          " WHERE from_airport = ?"
      )
      stmt.setInt(1, fromAirportId)
      val rs = stmt.executeQuery()
      val result = ListBuffer[MissedDemandEntry]()
      while (rs.next()) {
        result += MissedDemandEntry(
          fromAirportId = rs.getInt("from_airport"),
          toAirportId   = rs.getInt("to_airport"),
          passengerType = rs.getInt("passenger_type"),
          preferenceType = rs.getInt("preference_type"),
          preferredLinkClass = rs.getString("preferred_link_class"),
          passengerCount = rs.getInt("passenger_count")
        )
      }
      rs.close()
      stmt.close()
      result.toList
    } catch {
      case e: Exception =>
        println(s"MissedDemandSource.loadByFromAirport failed: ${e.getMessage}")
        List.empty
    } finally {
      connection.close()
    }
  }
}
