package com.patson.patch

import com.patson.data.Constants._
import com.patson.data.{AirlineSource, AirplaneSource, LinkSource, Meta, Patchers}
import com.patson.init.{AirportGeoPatcher, AirportStatsInit, actorSystem}
import com.patson.model._
import com.patson.model.airplane._

import java.sql.Connection
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.concurrent.Await
import scala.concurrent.duration.Duration

/**
  * patcher for v4.1
  */
object Version4_1Patcher extends App {
  mainFlow

  def mainFlow() {
    createSchema()
    removeObsoleteBaseSpecializations()
    patchAirlineShares()

    AirportGeoPatcher.mainFlow()
    AirportStatsInit.initAirportStats()

    Patchers.airplaneModelPatcher()
    swap_q400_model()
//    patchLinkFrequency()

    raisePrices()

    Await.result(actorSystem.terminate(), Duration.Inf)
  }

  def createSchema() = {
    var connection: Connection = null
    try {
      connection = Meta.getConnection()

      Meta.createPassengerHistoryTables(connection)
      Meta.createAirportStatistics(connection)
      Meta.createWorldStatistics(connection)
      Meta.createLinkStats(connection)
      Meta.createAirlineStats(connection)
      Meta.createReputationBreakdown(connection)
      Meta.createAirlineStats(connection)
      Meta.createRankingLeaderboard(connection)
      Meta.createAllianceStats(connection)
      Meta.createGoogleResource(connection)

      var statement = connection.prepareStatement("DROP TABLE IF EXISTS " + CYCLE_PHASE_TABLE)
      statement.execute()
      statement.close()
      statement = connection.prepareStatement("CREATE TABLE " + CYCLE_PHASE_TABLE + "(cycle_phase_length INTEGER PRIMARY KEY, cycle_phase_index INTEGER)")
      statement.execute()
      statement.close()

      //remove old alliance mission tables
      statement = connection.prepareStatement("DROP TABLE IF EXISTS " + ALLIANCE_MISSION_PROPERTY_TABLE)
      statement.execute()
      statement.close()
      statement = connection.prepareStatement("DROP TABLE IF EXISTS " + ALLIANCE_MISSION_PROPERTY_HISTORY_TABLE)
      statement.execute()
      statement.close()

      statement = connection.prepareStatement("DROP TABLE IF EXISTS " + ALLIANCE_MISSION_REWARD_PROPERTY_TABLE)
      statement.execute()
      statement.close()

      statement = connection.prepareStatement("DROP TABLE IF EXISTS " + ALLIANCE_MISSION_REWARD_TABLE)
      statement.execute()
      statement.close()

      statement = connection.prepareStatement("DROP TABLE IF EXISTS " + ALLIANCE_MISSION_TABLE)
      statement.execute()
      statement.close()

      statement = connection.prepareStatement("DROP TABLE IF EXISTS " + ALLIANCE_MISSION_STATS_TABLE)
      statement.execute()
      statement.close()

      statement = connection.prepareStatement("TRUNCATE TABLE " + AIRPORT_CITY_SHARE_TABLE)
      statement.execute()
      statement.close()
    } finally {
      if (connection != null) {
        connection.close()
      }
    }
  }

  def patchLinkFrequency() = {
    // frequency adjust, should be per airplane now
    AirlineSource.loadAllAirlines().foreach { airline =>
      val updatingLinks = ListBuffer[Link]()
      val updatingAssignedAirplanes = mutable.HashMap[Int, Map[Airplane, LinkAssignment]]()
      LinkSource.loadFlightLinksByAirlineId(airline.id).foreach { link =>
        if (link.capacity.total > 0) {
          val capacityPerAirplane = link.capacity / link.frequency
          var airplanes = link.getAssignedAirplanes().toList.map(_._1)

          //calculate new frequency and assignment
          val existingFrequency = link.frequency
          val maxFrequencyPerAirplane = Computation.calculateMaxFrequency(link.getAssignedModel().get, link.distance)
          var remainingFrequency = existingFrequency

          val newAirplaneAssignments = new mutable.HashMap[Airplane, LinkAssignment]()
          airplanes.foreach { airplane =>
            val flightMinutesRequired = Computation.calculateFlightMinutesRequired(airplane.model, link.distance)
            val frequencyForThisAirplane =
              if (remainingFrequency > maxFrequencyPerAirplane) {
                maxFrequencyPerAirplane
              } else {
                remainingFrequency
              }
            remainingFrequency -= frequencyForThisAirplane
            if (frequencyForThisAirplane > 0) {
              newAirplaneAssignments.put(airplane, LinkAssignment(frequencyForThisAirplane, frequencyForThisAirplane * flightMinutesRequired))
            }
            //            val availableFlightMinutes = Airplane.MAX_FLIGHT_MINUTES - Computation.calculateFlightMinutesRequired(airplane.model, link.distance) * frequencyForThisAirplane
            //            airplane.copy(availableFlightMinutes = availableFlightMinutes)
          }

          //AirplaneSource.updateAirplanes(airplanes)
          if (remainingFrequency > 0) {
            System.out.println(s"${link.id} has remainingFrequency $remainingFrequency out of $existingFrequency . Distance is ${link.distance}")
          }

          val newLink =
            if (remainingFrequency > 0) { //update frequency and capacity if we cannot accommodate everything
              val newFrequency = existingFrequency - remainingFrequency
              link.copy(capacity = capacityPerAirplane * newFrequency, frequency = newFrequency)
            } else {
              link
            }
            updatingLinks.append(newLink)
            updatingAssignedAirplanes.put(link.id, newAirplaneAssignments.toMap)
//          LinkSource.updateLink(newLink)
//          LinkSource.updateAssignedPlanes(link.id, newAirplaneAssignments.toMap)
        }
        LinkSource.updateLinks(updatingLinks.toList)
        LinkSource.updateAssignedPlanes(updatingAssignedAirplanes.toMap)
      }
      println(s"Updated $airline")
    }

    println("Finished adjusting frequency")
  }

  def columnExists(connection: Connection, tableName: String, columnName: String): Boolean = {
    val metaData = connection.getMetaData
    val columns = metaData.getColumns(null, null, tableName, columnName)
    columns.next()
  }

  def patchAirlineShares() = {
    println("Assigning 400,000 shares to each airline")
    
    val connection = Meta.getConnection()

    try {
      if (columnExists(connection, "airline_info", "weekly_dividends")) {
        println("Renaming weekly_dividends column to shares_outstanding")
        val alterStatement = connection.prepareStatement("ALTER TABLE airline_info CHANGE weekly_dividends shares_outstanding BIGINT")
        alterStatement.executeUpdate()
        alterStatement.close()
      } else {
        println("Column weekly_dividends does not exist in airline_info table. Skipping rename.")
      }

      if (columnExists(connection, "airline_info", "shares_outstanding")) {
        val updateStatement = connection.prepareStatement("UPDATE airline_info SET shares_outstanding = 500000000")
        val updatedRows = updateStatement.executeUpdate()
        updateStatement.close()
        println(s"Updated $updatedRows airlines with 500000000 shares")
      } else {
        println("Column shares_outstanding does not exist in airline_info table. Skipping update.")
      }


      if (!columnExists(connection, "airline_reputation_breakdown", "quantity_value")) {
        println("Adding column quantity_value to airline_reputation_breakdown")
        val alterStatement2 = connection.prepareStatement("ALTER TABLE `airline_reputation_breakdown` ADD COLUMN quantity_value DOUBLE null")
        alterStatement2.executeUpdate()
        alterStatement2.close()
      } else {
        println("Column quantity_value already exists in airline_reputation_breakdown. Skipping add.")
      }

      if (columnExists(connection, "airline_reputation_breakdown", "value")) {
        println("Renaming value column to rep_value")
        val alterStatement3 = connection.prepareStatement("ALTER TABLE airline_reputation_breakdown CHANGE value rep_value DOUBLE")
        alterStatement3.executeUpdate()
        alterStatement3.close()
      } else {
        println("Column value does not exist in airline_reputation_breakdown. Skipping rename.")
      }

    } catch {
      case e: Exception =>
        println(s"Error updating airline shares: ${e.getMessage}")
        e.printStackTrace()
    } finally {
      connection.close()
    }
    
    println("Finished assigning shares to airlines")
  }

  def swap_q400_model() = {
    println("Updating De Havilland Q400 airplanes to Q400 NextGen")
    
    val connection = Meta.getConnection()

    try {
      // Get model IDs from the airplane_model table
      val modelQuery = connection.prepareStatement("SELECT id, name FROM " + AIRPLANE_MODEL_TABLE + " WHERE name IN (?, ?)")
      modelQuery.setString(1, "De Havilland Q400")
      modelQuery.setString(2, "De Havilland Q400 NextGen")
      
      val modelResult = modelQuery.executeQuery()
      var oldModelId: Option[Int] = None
      var newModelId: Option[Int] = None
      
      while (modelResult.next()) {
        val modelName = modelResult.getString("name")
        val modelId = modelResult.getInt("id")
        
        if (modelName == "De Havilland Q400") {
          oldModelId = Some(modelId)
        } else if (modelName == "De Havilland Q400 NextGen") {
          newModelId = Some(modelId)
        }
      }
      
      modelResult.close()
      modelQuery.close()
      
      (oldModelId, newModelId) match {
        case (Some(oldId), Some(newId)) =>
          // Count how many airplanes need to be updated
          val countStatement = connection.prepareStatement(s"SELECT COUNT(*) FROM $AIRPLANE_TABLE WHERE model = ?")
          countStatement.setInt(1, oldId)
          val countResult = countStatement.executeQuery()
          countResult.next()
          val airplaneCount = countResult.getInt(1)
          countResult.close()
          countStatement.close()
          
          if (airplaneCount > 0) {
            // Update all airplanes from old model to new model
            val updateStatement = connection.prepareStatement(s"UPDATE $AIRPLANE_TABLE SET model = ? WHERE model = ?")
            updateStatement.setInt(1, newId)
            updateStatement.setInt(2, oldId)
            
            val updatedRows = updateStatement.executeUpdate()
            updateStatement.close()
            
            println(s"Successfully updated $updatedRows airplanes from 'De Havilland Q400' (ID: $oldId) to 'De Havilland Q400 NextGen' (ID: $newId)")
          } else {
            println("No airplanes found with 'De Havilland Q400' model")
          }

          val countStatement2 = connection.prepareStatement(s"SELECT COUNT(*) FROM $AIRPLANE_CONFIGURATION_TEMPLATE_TABLE WHERE model = ?")
          countStatement2.setInt(1, oldId)
          val countResult2 = countStatement2.executeQuery()
          countResult2.next()
          val airplaneCount2 = countResult2.getInt(1)
          countResult2.close()
          countStatement2.close()

          if (airplaneCount2 > 0) {
            // Update all airplanes from old model to new model
            val updateStatement = connection.prepareStatement(s"UPDATE $AIRPLANE_CONFIGURATION_TEMPLATE_TABLE SET model = ? WHERE model = ?")
            updateStatement.setInt(1, newId)
            updateStatement.setInt(2, oldId)

            val updatedRows = updateStatement.executeUpdate()
            updateStatement.close()

            println(s"Successfully updated $updatedRows airplane configs from 'De Havilland Q400' (ID: $oldId) to 'De Havilland Q400 NextGen' (ID: $newId)")
          } else {
            println("No airplanes found with 'De Havilland Q400' model")
          }

          val countStatement3 = connection.prepareStatement(s"SELECT COUNT(*) FROM $LINK_TABLE WHERE airplane_model = ?")
          countStatement3.setInt(1, oldId)
          val countResult3 = countStatement3.executeQuery()
          countResult3.next()
          val airplaneCount3 = countResult3.getInt(1)
          countResult3.close()
          countStatement3.close()

          if (airplaneCount3 > 0) {
            // Update all airplanes from old model to new model
            val updateStatement = connection.prepareStatement(s"UPDATE $LINK_TABLE SET airplane_model = ? WHERE airplane_model = ?")
            updateStatement.setInt(1, newId)
            updateStatement.setInt(2, oldId)

            val updatedRows = updateStatement.executeUpdate()
            updateStatement.close()

            println(s"Successfully updated $updatedRows link models from 'De Havilland Q400' (ID: $oldId) to 'De Havilland Q400 NextGen' (ID: $newId)")
          } else {
            println("No airplanes found with 'De Havilland Q400' model")
          }
          
        case (None, _) =>
          println("Error: Could not find 'De Havilland Q400' model in database")
        case (_, None) =>
          println("Error: Could not find 'De Havilland Q400 NextGen' model in database")
      }
      
    } catch {
      case e: Exception =>
        println(s"Error updating Q400 models: ${e.getMessage}")
        e.printStackTrace()
    } finally {
      connection.close()
    }
    
    println("Finished updating De Havilland Q400 airplanes")
  }

  def removeObsoleteBaseSpecializations() = {
    println("Removing obsolete airline base specializations")
    
    val connection = Meta.getConnection()

    try {
      // List of specialization IDs to remove
      val specializationsToRemove = List(
        "GATE_REGIONAL",
        "BRANDING_WIFI",
        "BRANDING_VIP_1",
        "BRANDING_SECURITY",
        "BRANDING_PRIORITY",
        "BRANDING_HELP"
      )

      val deleteStatementFirst = connection.prepareStatement(
        s"DELETE FROM $AIRLINE_BASE_SPECIALIZATION_TABLE WHERE specialization_type = 'SPORTS_SPONSORSHIP'"
      )
      deleteStatementFirst.executeUpdate()

      // Query all airline bases (airport, airline, scale)
      val basesQuery = connection.prepareStatement(s"SELECT airport, airline, scale FROM $AIRLINE_BASE_TABLE")
      // Delete specialization for a specific base (match airport + airline)
      val deleteStatement = connection.prepareStatement(
        s"DELETE FROM $AIRLINE_BASE_SPECIALIZATION_TABLE WHERE airport = ? AND airline = ? AND specialization_type = ?"
      )

      var totalRemoved = 0

      val basesRs = basesQuery.executeQuery()
      while (basesRs.next()) {
        val airportId = basesRs.getInt("airport")
        val airlineId = basesRs.getInt("airline")
        val scale = basesRs.getInt("scale")

        if (scale < 6) {
          specializationsToRemove.foreach { specializationId =>
            deleteStatement.setInt(1, airportId)
            deleteStatement.setInt(2, airlineId)
            deleteStatement.setString(3, specializationId)
            val removedCount = deleteStatement.executeUpdate()
            totalRemoved += removedCount
            if (removedCount > 0) {
//              println(s"Removed $removedCount entries for specialization: $specializationId from base (airport=$airportId, airline=$airlineId) with scale $scale")
            }
          }
        } else {
//          println(s"Skipping base (airport=$airportId, airline=$airlineId) with scale $scale")
        }
      }

      basesRs.close()
      basesQuery.close()
      deleteStatement.close()

      println(s"Total removed: $totalRemoved obsolete base specialization entries")
      
    } catch {
      case e: Exception =>
        println(s"Error removing obsolete base specializations: ${e.getMessage}")
        e.printStackTrace()
    } finally {
      connection.close()
    }
    
    println("Finished removing obsolete airline base specializations")
  }

  def raisePrices() = {
    println("Raising link prices")
    
    val connection = Meta.getConnection()

    try {
      // Update price_economy by +20
      val updateEconomyStatement = connection.prepareStatement(s"UPDATE $LINK_TABLE SET price_economy = price_economy + 20")
      val economyRows = updateEconomyStatement.executeUpdate()
      updateEconomyStatement.close()
      println(s"Updated $economyRows links - increased price_economy by 20")
      
      // Update price_business by +40
      val updateBusinessStatement = connection.prepareStatement(s"UPDATE $LINK_TABLE SET price_business = price_business + 40")
      val businessRows = updateBusinessStatement.executeUpdate()
      updateBusinessStatement.close()
      println(s"Updated $businessRows links - increased price_business by 40")
      
      // Update price_first by +40
      val updateFirstStatement = connection.prepareStatement(s"UPDATE $LINK_TABLE SET price_first = price_first + 40")
      val firstRows = updateFirstStatement.executeUpdate()
      updateFirstStatement.close()
      println(s"Updated $firstRows links - increased price_first by 40")
      
    } catch {
      case e: Exception =>
        println(s"Error raising link prices: ${e.getMessage}")
        e.printStackTrace()
    } finally {
      connection.close()
    }
    
    println("Finished raising link prices")
  }
  

}