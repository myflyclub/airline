package com.patson.data
import java.sql.Statement
import com.patson.data.Constants._
import com.patson.data.airplane.ModelSource
import com.patson.model.airplane._
import com.patson.model.{Airline, Airport}
import com.patson.util.{AirlineCache, AirplaneOwnershipCache}

import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.util.Using

object AirplaneSource {
  val LINK_ID_LOAD : Map[DetailType.Value, Boolean] = Map.empty
  val allModels = ModelSource.loadAllModels().map(model => (model.id, model)).toMap
  private[this] val BASE_QUERY = "SELECT owner, a.id as id, a.model as model, name, capacity, quality, ascent_burn, cruise_burn, speed, fly_range, price, constructed_cycle, purchased_cycle, airplane_condition, purchase_price, is_sold, configuration, a.home as home, a.version as version, economy, business, first, is_default FROM " + AIRPLANE_TABLE + " a LEFT JOIN " + AIRPLANE_MODEL_TABLE + " m ON a.model = m.id LEFT JOIN " + AIRPLANE_CONFIGURATION_TABLE + " c ON c.airplane = a.id LEFT JOIN " + AIRPLANE_CONFIGURATION_TEMPLATE_TABLE + " t ON c.configuration = t.id"


  def loadAirplanesCriteria(criteria : List[(String, Any)]) = {
    var queryString = BASE_QUERY

    if (!criteria.isEmpty) {
      queryString += " WHERE "
      for (i <- 0 until criteria.size - 1) {
        queryString += criteria(i)._1 + " = ? AND "
      }
      queryString += criteria.last._1 + " = ?"
    }

    loadAirplanesByQueryString(queryString, criteria.map(_._2))
  }

  def loadAirplanesByQueryString(queryString : String, parameters : List[Any]) = {
    Using.resource(Meta.getConnection()) { connection =>
      Using.resource(connection.prepareStatement(queryString)) { preparedStatement =>
        for (i <- 0 until parameters.size) {
          preparedStatement.setObject(i + 1, parameters(i))
        }
        Using.resource(preparedStatement.executeQuery()) { resultSet =>
          val airplanes = new ListBuffer[Airplane]()
          val currentCycle = CycleSource.loadCycle()
          while (resultSet.next()) {
            val airlineId = resultSet.getInt("owner")
            val airline = AirlineCache.getAirline(airlineId).getOrElse(Airline.fromId(airlineId))
            val model = allModels(resultSet.getInt("a.model"))
            val configuration = AirplaneConfiguration(resultSet.getInt("economy"), resultSet.getInt("business"), resultSet.getInt("first"), airline, model, resultSet.getBoolean("is_default"), id = resultSet.getInt("configuration"))
            val isSold = resultSet.getBoolean("is_sold")
            val constructedCycle = resultSet.getInt("constructed_cycle")
            val isReady = !isSold && currentCycle >= constructedCycle
            val version = resultSet.getInt("version")
            val airplane = Airplane(model, airline, constructedCycle, resultSet.getInt("purchased_cycle"), resultSet.getDouble("airplane_condition"), purchasePrice = resultSet.getInt("purchase_price"), isSold = isSold, configuration = configuration, home = Airport.fromId(resultSet.getInt("home")), isReady = isReady, version = version)
            airplane.id = resultSet.getInt("id")
            airplanes.append(airplane)
          }
          //println("Loaded " + airplanes.length + " airplane records")
          airplanes.toList
        }
      }
    }
  }


  def loadAllAirplanes() = {
    loadAirplanesCriteria(List.empty)
  }

  def loadAirplanesByOwner(ownerId : Int, isSold : Boolean = false) = {
    loadAirplanesCriteria(List(("owner", ownerId), ("is_sold", isSold)))
  }

  def loadAirplaneById(id : Int) : Option[Airplane] = {
    val result = loadAirplanesCriteria(List(("a.id", id)))
    if (result.isEmpty) None else Some(result(0))
  }

  def loadAirplanesByIds(ids : List[Int]) : List[Airplane] = {
    if (ids.isEmpty) {
      List.empty
    } else {
      val queryString = new StringBuilder(BASE_QUERY + " where a.id IN (");
      for (i <- 0 until ids.size - 1) {
            queryString.append("?,")
      }

      queryString.append("?)")
      loadAirplanesByQueryString(queryString.toString(), ids)
    }
  }

  def loadAirplaneLinkAssignmentsByOwner(ownerId : Int)  : Map[Int, LinkAssignments] = {
    loadAirplaneLinkAssignmentsByCriteria(List(("owner", ownerId)), joinAirplaneTable = true)
  }

  /**
    *
    * @param linkId
    *  @return Map[airplaneId, LinkAssignment]
    */
  def loadAirplaneLinkAssignmentsByLinkId(linkId : Int) : Map[Int, LinkAssignment] = {
    val result: Map[Int, LinkAssignments] = loadAirplaneLinkAssignmentsByCriteria(List(("link", linkId)), joinAirplaneTable = false) //Map[airplaneId, linkAssignments]
    if (result.isEmpty) {
      Map.empty
    } else {
      result.view.mapValues { linkAssignmentsOfThisAirplane =>
        linkAssignmentsOfThisAirplane.assignments(linkId)
      }.toMap
    }
  }

  def loadAirplaneLinkAssignmentsByAirplaneId(airplaneId : Int) : LinkAssignments = {
    val result = loadAirplaneLinkAssignmentsByCriteria(List(("airplane", airplaneId)), joinAirplaneTable = false)
    if (result.isEmpty) LinkAssignments(Map.empty) else result(airplaneId)
   }

  /**
    *
    * @param criteria
    * @param joinAirplaneTable whether the query require join on Airplane Table
    * @param loadDetails
    * @return Map[airplaneId, LinkAssignments]
    */
  def loadAirplaneLinkAssignmentsByCriteria(criteria : List[(String, Any)], joinAirplaneTable : Boolean = false, loadDetails : Map[DetailType.Value, Boolean] = LINK_ID_LOAD) : Map[Int, LinkAssignments]= {
    var queryString =
      if (joinAirplaneTable) {
        "SELECT airplane, link, frequency, flight_minutes FROM " + LINK_ASSIGNMENT_TABLE + " l LEFT JOIN " + AIRPLANE_TABLE + " a ON l.airplane = a.id"
      } else {
        "SELECT airplane, link, frequency, flight_minutes FROM " + LINK_ASSIGNMENT_TABLE
      }

    if (!criteria.isEmpty) {
      queryString += " WHERE "
      for (i <- 0 until criteria.size - 1) {
        queryString += criteria(i)._1 + " = ? AND "
      }
      queryString += criteria.last._1 + " = ?"
    }

    Using.resource(Meta.getConnection()) { connection =>
      Using.resource(connection.prepareStatement(queryString)) { preparedStatement =>
        for (i <- 0 until criteria.size) {
          preparedStatement.setObject(i + 1, criteria(i)._2)
        }
        Using.resource(preparedStatement.executeQuery()) { resultSet =>
          val airplanesWithAssignedLink = new mutable.HashMap[Int, mutable.HashMap[Int, LinkAssignment]]()
          while (resultSet.next()) {
            val airplaneId = resultSet.getInt("airplane")
            val linkId =  resultSet.getInt("link")
            val assignedLinks = airplanesWithAssignedLink.getOrElseUpdate(airplaneId, new mutable.HashMap[Int, LinkAssignment]())
            assignedLinks.put(linkId, LinkAssignment(resultSet.getInt("frequency"), resultSet.getInt("flight_minutes")))
          }
          //println("Loaded " + airplanesWithAssignedLink.length + " airplane records (with assigned link)")
          airplanesWithAssignedLink.view.mapValues( mutableMap => LinkAssignments(mutableMap.toMap)).toMap
        }
      }
    }
  }

 def deleteAllAirplanes() = {
    deleteAirplanesByCriteria(List.empty)
 }

 def deleteAirplane(airplaneId : Int, airplaneVersion : Option[Int]) = {
    airplaneVersion match {
      case Some(version) => deleteAirplanesByCriteria(List(("a.id", airplaneId), ("version", version)))
      case None => deleteAirplanesByCriteria(List(("a.id", airplaneId)))
    }
 }

 def deleteAirplanesByCriteria(criteria : List[(String, Any)]) = {
   val airplanes = loadAirplanesCriteria(criteria)
   if (airplanes.isEmpty) {
     0
   } else {
     val idsString = airplanes.map(_.id).mkString(",")
     val queryString = s"DELETE FROM $AIRPLANE_TABLE WHERE id IN ($idsString)"
     Using.resource(Meta.getConnection()) { connection =>
       Using.resource(connection.prepareStatement(queryString)) { preparedStatement =>
         val deleteCount = preparedStatement.executeUpdate()
         airplanes.map(_.owner.id).distinct.foreach { airlineId =>
           AirplaneOwnershipCache.invalidate(airlineId)
         }
         deleteCount
       }
     }
   }
 }

  def saveAirplanes(airplanes : List[Airplane]) = {
    var updateCount = 0
    Using.resource(Meta.getConnection()) { connection =>
      connection.setAutoCommit(false)
      Using.resource(connection.prepareStatement("INSERT INTO " + AIRPLANE_TABLE + "(owner, model, constructed_cycle, purchased_cycle, airplane_condition, purchase_price, is_sold, home, version) VALUES(?,?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) { preparedStatement =>
        Using.resource(connection.prepareStatement("REPLACE INTO " + AIRPLANE_CONFIGURATION_TABLE + "(airplane, configuration) VALUES(?,?)")) { configurationStatement =>
          airplanes.foreach { airplane =>
            preparedStatement.setInt(1, airplane.owner.id)
            preparedStatement.setInt(2, airplane.model.id)
            preparedStatement.setInt(3, airplane.constructedCycle)
            preparedStatement.setInt(4, airplane.purchasedCycle)
            preparedStatement.setDouble(5, airplane.condition)
            preparedStatement.setInt(6, airplane.purchasePrice)
            preparedStatement.setBoolean(7, airplane.isSold)
            preparedStatement.setInt(8, airplane.home.id)
            preparedStatement.setInt(9, airplane.version)
            updateCount += preparedStatement.executeUpdate()

            Using.resource(preparedStatement.getGeneratedKeys) { generatedKeys =>
              if (generatedKeys.next()) {
                val generatedId = generatedKeys.getInt(1)
                airplane.id = generatedId //assign id back to the airplane

                if (airplane.configuration.id != 0) {
                  configurationStatement.setInt(1, airplane.id)
                  configurationStatement.setInt(2, airplane.configuration.id)
                  configurationStatement.executeUpdate()
                }
              }
            }
          }
        }
      }
      connection.commit()
      airplanes.map(_.owner.id).distinct.foreach { airlineId =>
        AirplaneOwnershipCache.invalidate(airlineId)
      }
    }
    updateCount
  }

  def updateAirplanes(airplanes : List[Airplane], versionCheck : Boolean = false) = {
    var updateCount = 0
    Using.resource(Meta.getConnection()) { connection =>
      connection.setAutoCommit(false)
      var updateStatement = "UPDATE " + AIRPLANE_TABLE + " SET owner = ?, airplane_condition = ?, purchase_price = ?, constructed_cycle = ?, purchased_cycle = ?, is_sold = ?, home = ?, version = ? WHERE id = ?"
      if (versionCheck) {
        updateStatement += " AND version = ?"
      }
      Using.resource(connection.prepareStatement(updateStatement)) { preparedStatement =>
        Using.resource(connection.prepareStatement("REPLACE INTO " + AIRPLANE_CONFIGURATION_TABLE + "(airplane, configuration) VALUES(?,?)")) { configurationStatement =>
          Using.resource(connection.prepareStatement("DELETE FROM " + AIRPLANE_CONFIGURATION_TABLE + " WHERE airplane = ?")) { purgeConfigurationStatement =>
            airplanes.foreach { airplane =>
              preparedStatement.setInt(1, airplane.owner.id)
              preparedStatement.setDouble(2, airplane.condition)
              preparedStatement.setInt(3, airplane.purchasePrice)
              preparedStatement.setInt(4, airplane.constructedCycle)
              preparedStatement.setInt(5, airplane.purchasedCycle)
              preparedStatement.setBoolean(6, airplane.isSold)
              preparedStatement.setInt(7, airplane.home.id)
              preparedStatement.setInt(8, airplane.version + 1)
              preparedStatement.setInt(9, airplane.id)
              if (versionCheck) {
                preparedStatement.setInt(10, airplane.version)
              }

              updateCount += preparedStatement.executeUpdate()

              if (airplane.configuration.id == 0) {
                purgeConfigurationStatement.setInt(1, airplane.id)
                purgeConfigurationStatement.executeUpdate()
              } else {
                configurationStatement.setInt(1, airplane.id)
                configurationStatement.setInt(2, airplane.configuration.id)
                configurationStatement.executeUpdate()
              }
            }
          }
        }
      }
      connection.commit()
      airplanes.map(_.owner.id).distinct.foreach { airlineId =>
        AirplaneOwnershipCache.invalidate(airlineId)
      }
    }
    updateCount
  }

  /**
   * Update an airplane's details except owner, construction_cycle and isSold information
   */
   def updateAirplanesDetails(airplanes : List[Airplane], versionCheck : Boolean = false) : List[Airplane] = {
    val updatedAirplanes = ListBuffer[Airplane]()
    Using.resource(Meta.getConnection()) { connection =>
      connection.setAutoCommit(false)
      var statement = "UPDATE " + AIRPLANE_TABLE + " SET airplane_condition = ?, purchase_price = ?, home = ?, version = ? WHERE id = ?"
      if (versionCheck) {
        statement += " AND version = ?"
      }
      Using.resource(connection.prepareStatement(statement)) { preparedStatement =>
        airplanes.foreach { airplane =>
          preparedStatement.setDouble(1, airplane.condition)
          preparedStatement.setInt(2, airplane.purchasePrice)
          preparedStatement.setInt(3, airplane.home.id)
          preparedStatement.setInt(4, airplane.version + 1)
          preparedStatement.setInt(5, airplane.id)
          if (versionCheck) {
            preparedStatement.setInt(6, airplane.version)
          }

          val updateResult = preparedStatement.executeUpdate()
          if (updateResult > 0) {
            updatedAirplanes += airplane.copy(version = airplane.version + 1)
          }
        }
      }
      connection.commit()
      airplanes.map(_.owner.id).distinct.foreach { airlineId =>
        AirplaneOwnershipCache.invalidate(airlineId)
      }
    }
    updatedAirplanes.toList
  }


  def saveAirplaneConfigurations(configurations : List[AirplaneConfiguration]) = {
    var updateCount = 0
    Using.resource(Meta.getConnection()) { connection =>
      connection.setAutoCommit(false)
      Using.resource(connection.prepareStatement("INSERT INTO " + AIRPLANE_CONFIGURATION_TEMPLATE_TABLE + "(airline, model, economy, business, first, is_default) VALUES(?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) { preparedStatement =>
        configurations.foreach { configuration =>
          preparedStatement.setInt(1, configuration.airline.id)
          preparedStatement.setInt(2, configuration.model.id)
          preparedStatement.setInt(3, configuration.economyVal)
          preparedStatement.setInt(4, configuration.businessVal)
          preparedStatement.setInt(5, configuration.firstVal)
          preparedStatement.setBoolean(6, configuration.isDefault)
          updateCount += preparedStatement.executeUpdate()

          Using.resource(preparedStatement.getGeneratedKeys) { generatedKeys =>
            if (generatedKeys.next()) {
              val generatedId = generatedKeys.getInt(1)
              configuration.id = generatedId //assign id back to the configuration
            }
          }
        }
      }
      connection.commit()
    }
    updateCount
  }

  def updateAirplaneConfiguration(configuration: AirplaneConfiguration) = {
    Using.resource(Meta.getConnection()) { connection =>
      connection.setAutoCommit(false)
      Using.resource(connection.prepareStatement("UPDATE " + AIRPLANE_CONFIGURATION_TEMPLATE_TABLE + " SET economy = ?, business = ?, first = ?, is_default = ? WHERE id = ? AND airline = ? AND model = ?")) { preparedStatement =>
        preparedStatement.setInt(1, configuration.economyVal)
        preparedStatement.setDouble(2, configuration.businessVal)
        preparedStatement.setInt(3, configuration.firstVal)
        preparedStatement.setBoolean(4, configuration.isDefault)
        preparedStatement.setInt(5, configuration.id)
        preparedStatement.setInt(6, configuration.airline.id) //not necessary but just to play safe...
        preparedStatement.setInt(7, configuration.model.id)//not necessary but just to play safe...
        preparedStatement.executeUpdate()
      }
      connection.commit()
    }
  }

  def deleteAirplaneConfiguration(configuration: AirplaneConfiguration) = {
    Using.resource(Meta.getConnection()) { connection =>
      connection.setAutoCommit(false)
      Using.resource(connection.prepareStatement("DELETE FROM " + AIRPLANE_CONFIGURATION_TEMPLATE_TABLE + " WHERE id = ? AND airline = ? AND model = ?")) { preparedStatement =>
        preparedStatement.setInt(1, configuration.id)
        preparedStatement.setInt(2, configuration.airline.id) //not necessary but just to play safe...
        preparedStatement.setInt(3, configuration.model.id)//not necessary but just to play safe...
        preparedStatement.executeUpdate()
      }
      connection.commit()
    }
  }

  def loadAirplaneConfigurationById(airlineId : Int, modelId : Int) = {
    loadAirplaneConfigurationsByCriteria(List(("airline", airlineId), ("model", modelId)))
  }

  def loadAirplaneConfigurationById(id : Int) = {
    val result = loadAirplaneConfigurationsByCriteria(List(("id", id)))
    if (result.isEmpty) None else Some(result(0))
  }


  def loadAirplaneConfigurationsByCriteria(criteria : List[(String, Any)]) = {
    var queryString = "SELECT * FROM " + AIRPLANE_CONFIGURATION_TEMPLATE_TABLE

    if (!criteria.isEmpty) {
      queryString += " WHERE "
      for (i <- 0 until criteria.size - 1) {
        queryString += criteria(i)._1 + " = ? AND "
      }
      queryString += criteria.last._1 + " = ?"
    }

    loadAirplaneConfigurationsByQueryString(queryString, criteria.map(_._2))
  }



  def loadAirplaneConfigurationsByQueryString(queryString : String, parameters : List[Any]) = {
    Using.resource(Meta.getConnection()) { connection =>
      Using.resource(connection.prepareStatement(queryString)) { preparedStatement =>
        for (i <- 0 until parameters.size) {
          preparedStatement.setObject(i + 1, parameters(i))
        }
        Using.resource(preparedStatement.executeQuery()) { resultSet =>
          val configurations = new ListBuffer[AirplaneConfiguration]()
          while (resultSet.next()) {
            val configuration = AirplaneConfiguration(resultSet.getInt("economy"), resultSet.getInt("business"), resultSet.getInt("first"), Airline.fromId(resultSet.getInt("airline")), Model.fromId(resultSet.getInt("model")), resultSet.getBoolean("is_default"), id = resultSet.getInt("id"))
            configurations.append(configuration)
          }
          configurations.toList
        }
      }
    }
  }

  /**
    * Efficiently counts airplanes per model ID using SQL GROUP BY.
    *
    * @return Map of modelId -> count
    */
  def loadAirplaneModelCounts(): Map[Int, Int] = {
    val query = s"SELECT model, COUNT(*) AS cnt FROM $AIRPLANE_TABLE GROUP BY model"
    Using.resource(Meta.getConnection()) { connection =>
      Using.resource(connection.prepareStatement(query)) { preparedStatement =>
        Using.resource(preparedStatement.executeQuery()) { resultSet =>
          val counts = new mutable.HashMap[Int, Int]()
          while (resultSet.next()) {
            counts.put(resultSet.getInt("model"), resultSet.getInt("cnt"))
          }
          counts.toMap
        }
      }
    }
  }

  /**
    * Efficiently swap airplane models in a batch operation.
    * Deletes old airplanes and inserts new ones in a single transaction.
    *
    * @param airplaneIdsToDelete List of airplane IDs to delete (the old model airplanes)
    * @param newAirplanesToCreate List of new Airplane objects to insert
    * @return Tuple of (deleteCount, insertCount)
    */
  def swapAirplanesBatch(airplaneIdsToDelete: List[Int], newAirplanesToCreate: List[Airplane]): (Int, Int) = {
    var deleteCount = 0
    var insertCount = 0
    Using.resource(Meta.getConnection()) { connection =>
      connection.setAutoCommit(false)

      // Delete old airplanes
      if (airplaneIdsToDelete.nonEmpty) {
        val idsString = airplaneIdsToDelete.mkString(",")
        val deleteQuery = s"DELETE FROM $AIRPLANE_TABLE WHERE id IN ($idsString)"
        Using.resource(connection.prepareStatement(deleteQuery)) { deleteStatement =>
          deleteCount = deleteStatement.executeUpdate()
        }
      }

      // Insert new airplanes
      if (newAirplanesToCreate.nonEmpty) {
        Using.resource(connection.prepareStatement(
          "INSERT INTO " + AIRPLANE_TABLE +
          "(owner, model, constructed_cycle, purchased_cycle, airplane_condition, purchase_price, is_sold, home, version) " +
          "VALUES(?,?,?,?,?,?,?,?,?)",
          Statement.RETURN_GENERATED_KEYS
        )) { insertStatement =>
          Using.resource(connection.prepareStatement(
            "REPLACE INTO " + AIRPLANE_CONFIGURATION_TABLE + "(airplane, configuration) VALUES(?,?)"
          )) { configurationStatement =>
            newAirplanesToCreate.foreach { airplane =>
              insertStatement.setInt(1, airplane.owner.id)
              insertStatement.setInt(2, airplane.model.id)
              insertStatement.setInt(3, airplane.constructedCycle)
              insertStatement.setInt(4, airplane.purchasedCycle)
              insertStatement.setDouble(5, airplane.condition)
              insertStatement.setInt(6, airplane.purchasePrice)
              insertStatement.setBoolean(7, airplane.isSold)
              insertStatement.setInt(8, airplane.home.id)
              insertStatement.setInt(9, airplane.version)
              insertCount += insertStatement.executeUpdate()

              Using.resource(insertStatement.getGeneratedKeys) { generatedKeys =>
                if (generatedKeys.next()) {
                  val generatedId = generatedKeys.getInt(1)
                  airplane.id = generatedId

                  if (airplane.configuration.id != 0) {
                    configurationStatement.setInt(1, airplane.id)
                    configurationStatement.setInt(2, airplane.configuration.id)
                    configurationStatement.executeUpdate()
                  }
                }
              }
            }
          }
        }
      }

      connection.commit()

      // Invalidate cache for affected airlines
      newAirplanesToCreate.map(_.owner.id).distinct.foreach { airlineId =>
        AirplaneOwnershipCache.invalidate(airlineId)
      }

      (deleteCount, insertCount)
    }
  }

  object DetailType extends Enumeration {
    val LINK = Value
  }
}
