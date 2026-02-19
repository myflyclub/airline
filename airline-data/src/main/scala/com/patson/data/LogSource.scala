package com.patson.data
import com.patson.data.Constants._
import com.patson.model._
import com.patson.util.AirlineCache

import java.sql.Statement
import scala.collection.immutable
import scala.collection.mutable.{HashMap, ListBuffer, Map}


object LogSource {
  val insertLogs = (logs : List[Log]) => {
    val connection = Meta.getConnection()
    //case class Log(airline : Airline, message : String, cateogry : LogCategory.Value, severity : LogSeverity.Value, cycle : Int)
    val statement = connection.prepareStatement("INSERT INTO " + LOG_TABLE + "(airline, message, category, severity, cycle) VALUES(?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)
    val propertyStatement = connection.prepareStatement(s"INSERT INTO $LOG_PROPERTY_TABLE(log, property, value) VALUES(?,?,?)")
    connection.setAutoCommit(false)

    val pendingProperties = ListBuffer[(Int, immutable.Map[String, String])]()
    try {
      logs.foreach {  
        case Log(airline : Airline, message : String, category : LogCategory.Value, severity : LogSeverity.Value, cycle : Int, properties : immutable.Map[String, String]) => {
          statement.setInt(1, airline.id)
          statement.setString(2, message)
          statement.setInt(3, category.id)
          statement.setInt(4, severity.id)
          statement.setInt(5, cycle)
          statement.execute()

          val generatedKeys = statement.getGeneratedKeys
          if (generatedKeys.next()) {
            val generatedId = generatedKeys.getInt(1)
            pendingProperties.append((generatedId, properties))
          }
        }
      }



      pendingProperties.foreach {
        case(logId, properties) =>
          propertyStatement.setInt(1, logId)
          properties.foreach {
            case(property, value) =>
              propertyStatement.setString(2, property)
              propertyStatement.setString(3, value)
              propertyStatement.addBatch()
          }

      }
      propertyStatement.executeBatch()
      connection.commit()
    } finally {
      statement.close()
      propertyStatement.close()
      connection.close()
    }
  }
  
  def loadLogsByAirline(airlineId : Int, fromCycle : Int, fullLoad : Boolean = false) = {
    var queryString = "SELECT * FROM " + LOG_TABLE + " WHERE airline = ? AND cycle >= ?"
     loadLogsByQueryString(queryString, List(airlineId, fromCycle), fullLoad)
  }
  
  
  def loadLogsByCriteria(criteria : List[(String, Any)], fullLoad : Boolean = false) = {
      var queryString = "SELECT * FROM " + LOG_TABLE
      
      if (!criteria.isEmpty) {
        queryString += " WHERE "
        for (i <- 0 until criteria.size - 1) {
          queryString += criteria(i)._1 + " = ? AND "
        }
        queryString += criteria.last._1 + " = ?"
      }
      loadLogsByQueryString(queryString, criteria.map(_._2), fullLoad)
  }
  
  private def loadLogsByQueryString(queryString : String, parameters : List[Any], fullLoad : Boolean = false) : List[Log] = {
    val connection = Meta.getConnection()
    val preparedStatement = connection.prepareStatement(queryString)

    try {
      for (i <- 0 until parameters.size) {
        preparedStatement.setObject(i + 1, parameters(i))
      }

      val resultSet = preparedStatement.executeQuery()

      case class LogRow(id: Int, airlineId: Int, message: String, category: Int, severity: Int, cycle: Int)
      val logRows = ListBuffer[LogRow]()
      while (resultSet.next()) {
        logRows += LogRow(resultSet.getInt("id"), resultSet.getInt("airline"), resultSet.getString("message"), resultSet.getInt("category"), resultSet.getInt("severity"), resultSet.getInt("cycle"))
      }
      resultSet.close()
      preparedStatement.close()

      if (logRows.isEmpty) {
        return List.empty
      }

      val ids = logRows.map(_.id)
      val propertiesById = HashMap[Int, HashMap[String, String]]()
      val propertyStatement = connection.prepareStatement(s"SELECT * FROM $LOG_PROPERTY_TABLE WHERE log IN (${ids.mkString(",")})")
      try {
        val propertyResultSet = propertyStatement.executeQuery()
        while (propertyResultSet.next()) {
          val logId = propertyResultSet.getInt("log")
          val property = propertyResultSet.getString("property")
          val value = propertyResultSet.getString("value")
          val properties = propertiesById.getOrElseUpdate(logId, HashMap())
          properties.put(property, value)
        }
      } finally {
        propertyStatement.close()
      }

      val airlines = Map[Int, Airline]()
      val logs = ListBuffer[Log]()
      logRows.foreach { row =>
        val airline = airlines.getOrElseUpdate(row.airlineId, AirlineCache.getAirline(row.airlineId, fullLoad).getOrElse(Airline.fromId(row.airlineId)))
        logs += Log(
          airline, row.message, LogCategory(row.category), LogSeverity(row.severity), row.cycle,
          propertiesById.get(row.id) match {
            case Some(properties) => properties.toMap
            case None => scala.collection.immutable.Map.empty
          }
        )
      }

      logs.toList
    } finally {
      preparedStatement.close()

      connection.close()
    }
  }
  
  def deleteLogsBeforeCycle(cutoffCycle : Int) = {
      //open the hsqldb
    val connection = Meta.getConnection()
    try {  
      var queryString = "DELETE FROM " + LOG_TABLE + " WHERE cycle < ?"
      
      val preparedStatement = connection.prepareStatement(queryString)
      
      preparedStatement.setObject(1, cutoffCycle)
      val deletedCount = preparedStatement.executeUpdate()
      
      preparedStatement.close()
      deletedCount
    } finally {
      connection.close()
    }
  }
}