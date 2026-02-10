package com.patson.data

import com.patson.data.Constants._

import java.util.concurrent.ThreadLocalRandom

object CycleSource {
  def loadCycle() = {
    val connection = Meta.getConnection() 
    try {  
      var queryString = "SELECT cycle FROM " + CYCLE_TABLE
      
      val preparedStatement = connection.prepareStatement(queryString)
      val resultSet = preparedStatement.executeQuery()
      val cycle = if (resultSet.next()) { resultSet.getInt("cycle") } else 1
      
      resultSet.close()
      preparedStatement.close()
      cycle
    } finally {
      connection.close()
    }
  }
  
  def setCycle(cycle : Int) = {
    val connection = Meta.getConnection() 
    
    try {
      connection.setAutoCommit(false)
      val queryString = "DELETE FROM " + CYCLE_TABLE
      val deleteStatement = connection.prepareStatement(queryString)
      deleteStatement.executeUpdate()
      deleteStatement.close()
      
      val insertStatement = connection.prepareStatement("INSERT INTO " + CYCLE_TABLE + "(cycle) VALUES(?)");
      insertStatement.setInt(1, cycle)
      insertStatement.executeUpdate()
      insertStatement.close()
      
      connection.commit()
    } finally {
      connection.close()
    }
  }

  /**
   *
   * @return cycle phase length
   */
  def loadAndUpdateCyclePhase(): Int = synchronized {
    val connection = Meta.getConnection()
    try {
      val preparedStatement = connection.prepareStatement("SELECT * FROM " + CYCLE_PHASE_TABLE)
      val resultSet = preparedStatement.executeQuery()
      val (phaseLength, phaseIndex) = if (resultSet.next() && resultSet.getInt("cycle_phase_index") > 0) {
        (resultSet.getInt("cycle_phase_length"), resultSet.getInt("cycle_phase_index") - 1)
      } else {
        val newLength = ThreadLocalRandom.current().nextInt(40, 50)
        (newLength, newLength)
      }

      val deleteStatement = connection.prepareStatement("DELETE FROM " + CYCLE_PHASE_TABLE)
      deleteStatement.executeUpdate()
      deleteStatement.close()

      val insertStatement = connection.prepareStatement("INSERT INTO " + CYCLE_PHASE_TABLE + "(cycle_phase_length, cycle_phase_index) VALUES(?,?)");
      insertStatement.setInt(1, phaseLength)
      insertStatement.setInt(2, phaseIndex)
      insertStatement.executeUpdate()

      resultSet.close()
      preparedStatement.close()
      phaseLength
    } finally {
      connection.close()
    }
  }
}