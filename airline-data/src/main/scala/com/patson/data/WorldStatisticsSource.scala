package com.patson.data

import com.patson.data.Constants._
import com.patson.model.{Period, WorldStatistics}

import scala.collection.mutable.ListBuffer

object WorldStatisticsSource {

  def loadWorldStats(cycle: Int): List[WorldStatistics] = {
    val connection = Meta.getConnection()
    val stats = ListBuffer[WorldStatistics]()
    try {
      val preparedStatement = connection.prepareStatement("SELECT * FROM " + WORLD_STATISTICS_TABLE + " WHERE week = ?")
      preparedStatement.setInt(1, cycle)
      val resultSet = preparedStatement.executeQuery()
      
      while (resultSet.next()) {
        stats += WorldStatistics(
          week = resultSet.getInt("week"),
          period = Period(resultSet.getInt("period")),
          totalPax = resultSet.getInt("total_pax"),
          missedPax = resultSet.getInt("missed_pax"),
          loadFactor = resultSet.getDouble("load_factor")
        )
      }
      
      stats.toList
    } finally {
      connection.close()
    }
  }

  def saveWorldStats(stats: List[WorldStatistics]): Unit = {
    val connection = Meta.getConnection()
    try {
      connection.setAutoCommit(false)
      val preparedStatement = connection.prepareStatement("REPLACE INTO " + WORLD_STATISTICS_TABLE + " (week, period, total_pax, missed_pax, load_factor) VALUES(?,?,?,?,?)")
      
      stats.foreach { stat =>
        preparedStatement.setInt(1, stat.week)
        preparedStatement.setInt(2, stat.period.id)
        preparedStatement.setInt(3, stat.totalPax)
        preparedStatement.setInt(4, stat.missedPax)
        preparedStatement.setDouble(5, stat.loadFactor)
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