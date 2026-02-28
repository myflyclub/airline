package com.patson.data

import com.patson.data.Constants._
import scala.collection.mutable.ListBuffer


object PrestigeSource {
  def createPrestige(airlineId: Int, airportId: Int, airlineName: String, prestigePoints: Int, cycle: Int) = {
    val connection = Meta.getConnection()
    val statement = connection.prepareStatement("INSERT INTO " + PRESTIGE_TABLE + " (airline, airport, airline_name, prestige_points, cycle) VALUES(?,?,?,?,?)")
    try {
      statement.setInt(1, airlineId)
      statement.setInt(2, airportId)
      statement.setString(3, airlineName)
      statement.setInt(4, prestigePoints)
      statement.setInt(5, cycle)

      statement.executeUpdate()
    } finally {
      statement.close()
      connection.close()
    }
  }

  def unlinkPrestige(airlineId: Int) = {
    val connection = Meta.getConnection()
    val statement = connection.prepareStatement("UPDATE " + PRESTIGE_TABLE + " SET airline = null WHERE airline = ?")
    try {
      statement.setInt(1, airlineId)

      statement.executeUpdate()
    } finally {
      statement.close()
      connection.close()
    }
  }

  /**
   * Sum all prestige points from the prestige table for a given airport
   */
  def sumPrestigePointsByAirport(airportId: Int): Int = {
    val connection = Meta.getConnection()
    try {
      val statement = connection.prepareStatement("SELECT SUM(prestige_points) FROM " + PRESTIGE_TABLE + " WHERE airport = ?")
      statement.setInt(1, airportId)
      val resultSet = statement.executeQuery()
      if (resultSet.next()) {
        val sum = resultSet.getInt(1)
        resultSet.close()
        statement.close()
        sum
      } else {
        resultSet.close()
        statement.close()
        0
      }
    } finally {
      connection.close()
    }
  }
}
