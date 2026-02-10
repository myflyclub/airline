package com.patson.data

import com.patson.data.Constants._
import com.patson.model.{AirportStatistics, AirportStatisticsUpdate}

import scala.collection.mutable.ListBuffer

object AirportStatisticsSource {

  def loadAirportStatsById(airportId: Int): Option[AirportStatistics] = {
    val connection = Meta.getConnection()
    try {
      val preparedStatement = connection.prepareStatement("SELECT * FROM " + AIRPORT_STATISTICS_TABLE + " WHERE airport = ?")
      preparedStatement.setInt(1, airportId)
      val resultSet = preparedStatement.executeQuery()
      
      if (resultSet.next()) {
        Some(AirportStatistics(
          airportId = resultSet.getInt("airport"),
          baselineDemand = resultSet.getInt("baseline_demand"),
          fromPax = resultSet.getInt("from_pax"),
          congestion = resultSet.getDouble("congestion"),
          reputation = resultSet.getDouble("reputation"),
          travelRate = resultSet.getDouble("travel_rate")
        ))
      } else {
        None
      }
    } finally {
      connection.close()
    }
  }

  def loadAllAirportStats(): List[AirportStatistics] = {
    val connection = Meta.getConnection()
    val stats = ListBuffer[AirportStatistics]()
    try {
      val preparedStatement = connection.prepareStatement("SELECT * FROM " + AIRPORT_STATISTICS_TABLE)
      val resultSet = preparedStatement.executeQuery()
      
      while (resultSet.next()) {
        stats += AirportStatistics(
          airportId = resultSet.getInt("airport"),
          baselineDemand = resultSet.getInt("baseline_demand"),
          fromPax = resultSet.getInt("from_pax"),
          congestion = resultSet.getDouble("congestion"),
          reputation = resultSet.getDouble("reputation"),
          travelRate = resultSet.getDouble("travel_rate")
        )
      }
      
      stats.toList
    } finally {
      connection.close()
    }
  }

  def saveAllAirportStats(stats: List[AirportStatistics]): Unit = {
    val connection = Meta.getConnection()
    try {
      connection.setAutoCommit(false)
      val preparedStatement = connection.prepareStatement("REPLACE INTO " + AIRPORT_STATISTICS_TABLE + " (airport, baseline_demand, from_pax, congestion, reputation, travel_rate) VALUES(?,?,?,?,?,?)")
      
      stats.foreach { stat =>
        preparedStatement.setInt(1, stat.airportId)
        preparedStatement.setInt(2, stat.baselineDemand)
        preparedStatement.setInt(3, stat.fromPax)
        preparedStatement.setDouble(4, stat.congestion)
        preparedStatement.setDouble(5, stat.reputation)
        preparedStatement.setDouble(6, stat.travelRate)
        preparedStatement.addBatch()
      }
      
      preparedStatement.executeBatch()
      preparedStatement.close()
      connection.commit()
    } finally {
      connection.close()
    }
  }

  def updateBaselineStats(stats: List[AirportStatistics]): Unit = {
    val connection = Meta.getConnection()
    try {
      connection.setAutoCommit(false)
      val preparedStatement = connection.prepareStatement(
        "INSERT INTO " + AIRPORT_STATISTICS_TABLE + " (airport, baseline_demand, congestion, from_pax, reputation, travel_rate) VALUES(?,?,?,0,0.0,0.0) " +
        "ON DUPLICATE KEY UPDATE baseline_demand = VALUES(baseline_demand), congestion = VALUES(congestion)")

      stats.foreach { stat =>
        preparedStatement.setInt(1, stat.airportId)
        preparedStatement.setInt(2, stat.baselineDemand)
        preparedStatement.setDouble(3, stat.congestion)
        preparedStatement.addBatch()
      }

      preparedStatement.executeBatch()
      preparedStatement.close()
      connection.commit()
    } finally {
      connection.close()
    }
  }

  def updateAllAirportStats(stats: List[AirportStatisticsUpdate]): Unit = {
    val connection = Meta.getConnection()
    try {
      connection.setAutoCommit(false)
      val preparedStatement = connection.prepareStatement("UPDATE " + AIRPORT_STATISTICS_TABLE + " SET from_pax = ?, congestion = ?, reputation = ?, travel_rate = ? WHERE airport = ?")
      
      stats.foreach { stat =>
        preparedStatement.setInt(1, stat.fromPax)
        preparedStatement.setDouble(2, stat.congestion)
        preparedStatement.setDouble(3, stat.reputation)
        preparedStatement.setDouble(4, stat.travelRate)
        preparedStatement.setInt(5, stat.airportId)
        preparedStatement.addBatch()
      }
      
      preparedStatement.executeBatch()
      preparedStatement.close()
      connection.commit()
    } finally {
      connection.close()
    }
  }
}