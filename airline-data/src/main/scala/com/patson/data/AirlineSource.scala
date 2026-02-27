package com.patson.data

import scala.collection.mutable.ListBuffer
import scala.collection.mutable.Map
import scala.collection.immutable
import com.patson.data.Constants._
import com.patson.model._
import com.patson.MainSimulation

import java.sql.{Blob, Date, Statement}
import java.io.ByteArrayInputStream
import com.patson.util.{AirlineCache, AirportCache}

import java.util.Date
import scala.util.Using


object AirlineSource {
  private[this] val BASE_QUERY = "SELECT a.id AS id, a.name AS name, a.airline_type AS airline_type, ai.* FROM " + AIRLINE_TABLE + " a JOIN " + AIRLINE_INFO_TABLE + " ai ON a.id = ai.airline "
  def loadAllAirlines(fullLoad : Boolean = false) = {
      loadAirlinesByCriteria(List.empty, fullLoad)
  }

  def loadAirlinesByIds(ids : List[Int], fullLoad : Boolean = false) = {
    if (ids.isEmpty) {
      List.empty
    } else {
      val queryString = new StringBuilder(BASE_QUERY + " where id IN (");
      for (i <- 0 until ids.size - 1) {
            queryString.append("?,")
      }

      queryString.append("?)")
      loadAirlinesByQueryString(queryString.toString(), ids, fullLoad)
    }
  }

  def loadAirlinesByCriteria(criteria : List[(String, Any)], fullLoad : Boolean = false) = {
      var queryString = BASE_QUERY

      if (!criteria.isEmpty) {
        queryString += " WHERE "
        for (i <- 0 until criteria.size - 1) {
          queryString += criteria(i)._1 + " = ? AND "
        }
        queryString += criteria.last._1 + " = ?"
      }
      loadAirlinesByQueryString(queryString, criteria.map(_._2), fullLoad)
  }

  private def loadAirlinesByQueryString(queryString : String, parameters : List[Any], fullLoad : Boolean = false) : List[Airline] = {
    Using.resource(Meta.getConnection()) { connection =>
      val airlines = Using.resource(connection.prepareStatement(queryString)) { preparedStatement =>
        for (i <- 0 until parameters.size) {
          preparedStatement.setObject(i + 1, parameters(i))
        }
        Using.resource(preparedStatement.executeQuery()) { resultSet =>
          val airlines = new ListBuffer[Airline]()
          while (resultSet.next()) {
            val airlineType = AirlineType.fromId(resultSet.getInt("airline_type"))
            val airline = Airline(resultSet.getString("name"), airlineType)
            airline.id = resultSet.getInt("id")
            airline.setBalance(resultSet.getLong("balance"))
            airline.setReputation(resultSet.getDouble("reputation"))
            airline.setCurrentServiceQuality(resultSet.getDouble("service_quality"))
            airline.setTargetServiceQuality(resultSet.getInt("target_service_quality"))
            airline.setStockPrice(resultSet.getDouble("stock_price"))
            airline.setSharesOutstanding(resultSet.getInt("shares_outstanding"))
            airline.setAirlineCode(resultSet.getString("airline_code"))
            airline.setMinimumRenewalBalance(resultSet.getLong("minimum_renewal_balance"))
            val countryCode = resultSet.getString("country_code")
            if (countryCode != null) {
              airline.setCountryCode(countryCode)
            }
            airline.setSkipTutorial(resultSet.getBoolean("skip_tutorial"))
            airline.setInitialized(resultSet.getBoolean("initialized"))
            airlines += airline
          }
          airlines.toList
        }
      }

      val allianceMembers = AllianceSource.loadAllianceMemberByAirlines(airlines)
      airlines.foreach { airline =>
        allianceMembers.get(airline) match {
          case Some(allianceMember) =>
            if (allianceMember.role != AllianceRole.APPLICANT) {
              airline.setAllianceId(allianceMember.allianceId)
            }
          case None => //do nothing
        }
      }

      if (fullLoad) {
        val airlineBases : scala.collection.immutable.Map[Int, List[AirlineBase]] = loadAirlineBasesByAirlines(airlines).groupBy(_.airline.id)
        airlines.foreach { airline =>
          airline.setBases(airlineBases.getOrElse(airline.id, List.empty))
        }

        val stats = AirlineStatisticsSource.loadAirlineStatsForAirlines(airlines)
        airlines.foreach { airline =>
          val airlineStat = stats.find(_.airlineId == airline.id).getOrElse(AirlineStat(airline.id, 0, Period.WEEKLY, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0))
          airline.setStats(airlineStat)
        }
      }

      airlines
    }
  }


  def loadAirlineById(id : Int, fullLoad : Boolean = false) = {
      val result = loadAirlinesByCriteria(List(("id", id)), fullLoad)
      if (result.isEmpty) {
        None
      } else {
        Some(result(0))
      }
  }


  def saveAirlines(airlines : List[Airline]) = {
    Using.resource(Meta.getConnection()) { connection =>
      connection.setAutoCommit(false)
      Using.resource(connection.prepareStatement("INSERT INTO " + AIRLINE_TABLE + "(name, airline_type) VALUES(?,?)", Statement.RETURN_GENERATED_KEYS)) { preparedStatement =>
        airlines.foreach { airline =>
          preparedStatement.setString(1, airline.name)
          preparedStatement.setInt(2, airline.airlineType.id)
          preparedStatement.executeUpdate()
          val generatedKeys = preparedStatement.getGeneratedKeys
          if (generatedKeys.next()) {
            val generatedId = generatedKeys.getInt(1)
            println("Id is " + generatedId)
            airline.id = generatedId

            Using.resource(connection.prepareStatement("INSERT INTO " + AIRLINE_INFO_TABLE + "(airline, balance, service_quality, target_service_quality, stock_price, shares_outstanding, reputation, country_code, airline_code, minimum_renewal_balance) VALUES(?,?,?,?,?,?,?,?,?,?)")) { infoStatement =>
              infoStatement.setInt(1, airline.id)
              infoStatement.setLong(2, airline.getBalance())
              infoStatement.setDouble(3, airline.getCurrentServiceQuality())
              infoStatement.setInt(4, airline.getTargetServiceQuality())
              infoStatement.setDouble(5, airline.getStockPrice())
              infoStatement.setInt(6, airline.getSharesOutstanding())
              infoStatement.setDouble(7, airline.getReputation())
              infoStatement.setString(8, airline.getCountryCode().orNull)
              infoStatement.setString(9, airline.getAirlineCode())
              infoStatement.setLong(10, airline.getMinimumRenewalBalance())
              infoStatement.executeUpdate()
            }
          }
        }
      }
      connection.commit()
    }
    airlines
  }

  def updateAirlineType(airlineId : Int, airlineType : Int) = {
    this.synchronized {
      Using.resource(Meta.getConnection()) { connection =>
        Using.resource(connection.prepareStatement("UPDATE " + AIRLINE_TABLE + " SET airline_type = ? WHERE id = ?")) { updateStatement =>
          updateStatement.setInt(1, airlineType)
          updateStatement.setInt(2, airlineId)
          updateStatement.executeUpdate()
        }
        AirlineCache.invalidateAirline(airlineId)
      }
    }
  }

  def adjustAirlineBalance(airlineId : Int, delta : Long) = {
    this.synchronized {
      Using.resource(Meta.getConnection()) { connection =>
        Using.resource(connection.prepareStatement("UPDATE " + AIRLINE_INFO_TABLE + " SET balance = balance + ? WHERE airline = ?")) { updateStatement =>
          updateStatement.setLong(1, delta)
          updateStatement.setInt(2, airlineId)
          updateStatement.executeUpdate()
        }
        AirlineCache.invalidateAirline(airlineId)
      }
    }
  }

  def adjustAirlineReputation(airlineId : Int, delta : Double) = {
    this.synchronized {
      Using.resource(Meta.getConnection()) { connection =>
        Using.resource(connection.prepareStatement("UPDATE " + AIRLINE_INFO_TABLE + " SET reputation = reputation + ? WHERE airline = ?")) { updateStatement =>
          updateStatement.setDouble(1, delta)
          updateStatement.setInt(2, airlineId)
          updateStatement.executeUpdate()
        }
        AirlineCache.invalidateAirline(airlineId)
      }
    }
  }


  def saveAirlineInfo(airline : Airline, updateBalance : Boolean = true) = {
    this.synchronized {
      var query = "UPDATE " + AIRLINE_INFO_TABLE + " SET "
      if (updateBalance) {
        query += "balance = ?, "
      }
      query += "service_quality = ?, target_service_quality = ?, stock_price = ?, shares_outstanding = ?, reputation = ?, country_code = ?, airline_code = ?, skip_tutorial = ?, initialized = ?, minimum_renewal_balance = ? WHERE airline = ?"

      Using.resource(Meta.getConnection()) { connection =>
        Using.resource(connection.prepareStatement(query)) { updateStatement =>
          var index = 0

          if (updateBalance) {
            index += 1
            updateStatement.setLong(index, airline.getBalance())
          }
          index += 1
          updateStatement.setDouble(index, airline.getCurrentServiceQuality())
          index += 1
          updateStatement.setInt(index, airline.getTargetServiceQuality())
          index += 1
          updateStatement.setDouble(index, airline.getStockPrice())
          index += 1
          updateStatement.setInt(index, airline.getSharesOutstanding())
          index += 1
          updateStatement.setDouble(index, airline.getReputation())
          index += 1
          updateStatement.setString(index, airline.getCountryCode().orNull)
          index += 1
          updateStatement.setString(index, airline.getAirlineCode())
          index += 1
          updateStatement.setBoolean(index, airline.isSkipTutorial)
          index += 1
          updateStatement.setBoolean(index, airline.isInitialized)
          index += 1
          updateStatement.setLong(index, airline.getMinimumRenewalBalance())
          index += 1
          updateStatement.setInt(index, airline.id)
          updateStatement.executeUpdate()
        }
        AirlineCache.invalidateAirline(airline.id)
      }
    }
  }

  def saveAirlineCode(airlineId : Int, airlineCode : String) = {
    this.synchronized {
      Using.resource(Meta.getConnection()) { connection =>
        Using.resource(connection.prepareStatement("UPDATE " + AIRLINE_INFO_TABLE + " SET airline_code = ? WHERE airline = ?")) { updateStatement =>
          updateStatement.setString(1, airlineCode)
          updateStatement.setInt(2, airlineId)
          updateStatement.executeUpdate()
        }
        AirlineCache.invalidateAirline(airlineId)
      }
    }
  }

  def saveAirlinesInfo(airlines : List[Airline]) = {
    this.synchronized {
      val query = "UPDATE " + AIRLINE_INFO_TABLE + " SET service_quality = ?, target_service_quality = ?, stock_price = ?, shares_outstanding = ?, reputation = ?, minimum_renewal_balance = ? WHERE airline = ?"

      Using.resource(Meta.getConnection()) { connection =>
        connection.setAutoCommit(false)
        Using.resource(connection.prepareStatement(query)) { updateStatement =>
          airlines.foreach { airline =>
            var index = 0
            index += 1
            updateStatement.setDouble(index, airline.getCurrentServiceQuality())
            index += 1
            updateStatement.setInt(index, airline.getTargetServiceQuality())
            index += 1
            updateStatement.setDouble(index, airline.getStockPrice())
            index += 1
            updateStatement.setInt(index, airline.getSharesOutstanding())
            index += 1
            updateStatement.setDouble(index, airline.getReputation())
            index += 1
            updateStatement.setLong(index, airline.getMinimumRenewalBalance())
            index += 1
            updateStatement.setInt(index, airline.id)
            updateStatement.addBatch()
            AirlineCache.invalidateAirline(airline.id)
          }
          updateStatement.executeBatch()
        }
        connection.commit()
      }
    }
  }

  def deleteAirline(airlineId : Int) = {
    deleteAirlinesByCriteria(List(("id", airlineId)))
    FileSource.deleteLogo("airline", airlineId)
    AirlineCache.invalidateAirline(airlineId)
  }

  def deleteAirlinesByCriteria(criteria : List[(String, Any)]) = {
    Using.resource(Meta.getConnection()) { connection =>
      var queryString = "DELETE FROM " + AIRLINE_TABLE

      if (!criteria.isEmpty) {
        queryString += " WHERE "
        for (i <- 0 until criteria.size - 1) {
          queryString += criteria(i)._1 + " = ? AND "
        }
        queryString += criteria.last._1 + " = ?"
      }

      Using.resource(connection.prepareStatement(queryString)) { preparedStatement =>
        for (i <- 0 until criteria.size) {
          preparedStatement.setObject(i + 1, criteria(i)._2)
        }
        val deletedCount = preparedStatement.executeUpdate()
        println("Deleted " + deletedCount + " airline records")
        deletedCount
      }
    }
  }

  def loadAirlineBasesByAirport(airportId : Int) : List[AirlineBase] = {
    loadAirlineBasesByCriteria(List(("airport", airportId)))
  }

  def loadAirlineBasesByAirline(airlineId : Int) : List[AirlineBase] = {
    loadAirlineBasesByCriteria(List(("airline", airlineId)))
  }

  def loadAirlineBasesByAirlines(airlines : scala.collection.immutable.List[Airline]) : List[AirlineBase] = {
    if (airlines.isEmpty) {
      List.empty
    } else {
      val queryString = new StringBuilder("SELECT * FROM " + AIRLINE_BASE_TABLE + " where airline IN (");
      for (i <- 0 until airlines.size - 1) {
            queryString.append("?,")
      }

      queryString.append("?)")
      loadAirlineBasesByQueryString(queryString.toString(), airlines.map(_.id), airlines = collection.mutable.Map(airlines.map(airline => (airline.id, airline)).toMap.toSeq: _*))
    }
  }


  def loadAirlineBasesByCountryCode(countryCode : String) : List[AirlineBase] = {
    loadAirlineBasesByCriteria(List(("country", countryCode)))
  }

  def loadAirlineHeadquarter(airlineId : Int) : Option[AirlineBase] = {
    val result = loadAirlineBasesByCriteria(List(("airline", airlineId), ("headquarter", true)))
    if (result.isEmpty) {
      None
    } else {
      Some(result(0))
    }
  }

  def loadAirlineBaseByAirlineAndAirport(airlineId : Int, airportId : Int) : Option[AirlineBase] = {
    val result = loadAirlineBasesByCriteria(List(("airline", airlineId), ("airport", airportId)))
    if (result.isEmpty) {
      None
    } else {
      Some(result(0))
    }
  }

  /**
   * Provide the airlines map for quicker load
   */
  def loadAirlineBasesByCriteria(criteria : List[(String, Any)], airlines : Map[Int, Airline] = Map()) : List[AirlineBase] = {
    var queryString = "SELECT * FROM " + AIRLINE_BASE_TABLE

    if (!criteria.isEmpty) {
      queryString += " WHERE "
      for (i <- 0 until criteria.size - 1) {
        queryString += criteria(i)._1 + " = ? AND "
      }
      queryString += criteria.last._1 + " = ?"
    }
    loadAirlineBasesByQueryString(queryString, criteria.map(_._2), airlines)
  }

  def loadAirlineBasesByQueryString(queryString : String, parameters : List[Any], airlines : Map[Int, Airline] = Map()) : List[AirlineBase] = {
    Using.resource(Meta.getConnection()) { connection =>
      val (rows, airportIds) = Using.resource(connection.prepareStatement(queryString)) { preparedStatement =>
        for (i <- 0 until parameters.size) {
          preparedStatement.setObject(i + 1, parameters(i))
        }
        Using.resource(preparedStatement.executeQuery()) { resultSet =>
          case class BaseRow(airlineId: Int, airportId: Int, scale: Int, foundedCycle: Int, headquarter: Boolean, countryCode: String)
          val rows = new ListBuffer[BaseRow]()
          val airportIds = scala.collection.mutable.Set[Int]()
          while (resultSet.next()) {
            val airportId = resultSet.getInt("airport")
            airportIds.add(airportId)
            rows += BaseRow(resultSet.getInt("airline"), airportId, resultSet.getInt("scale"), resultSet.getInt("founded_cycle"), resultSet.getBoolean("headquarter"), resultSet.getString("country"))
          }
          (rows, airportIds)
        }
      }

      val airports = AirportCache.getAirports(airportIds.toList)

      val bases = new ListBuffer[AirlineBase]()
      rows.foreach { row =>
        val airline = airlines.getOrElseUpdate(row.airlineId, AirlineCache.getAirline(row.airlineId, false).getOrElse(Airline.fromId(row.airlineId)))
        val airport = airports(row.airportId)
        bases += AirlineBase(airline, airport, row.countryCode, row.scale, row.foundedCycle, row.headquarter)
      }

      bases.toList
    }
  }


  //case class AirlineBase(airline : Airline, airport : Airport, scale : Int, headQuarter : Boolean = false, var id : Int = 0) extends IdObject
  def saveAirlineBase(airlineBase : AirlineBase) = {
    Using.resource(Meta.getConnection()) { connection =>
      Using.resource(connection.prepareStatement("REPLACE INTO " + AIRLINE_BASE_TABLE + "(airline, airport, scale, founded_cycle, headquarter, country) VALUES(?, ?, ?, ?, ?, ?)")) { preparedStatement =>
        preparedStatement.setInt(1, airlineBase.airline.id)
        preparedStatement.setInt(2, airlineBase.airport.id)
        preparedStatement.setInt(3, airlineBase.scale)
        preparedStatement.setInt(4, airlineBase.foundedCycle)
        preparedStatement.setBoolean(5, airlineBase.headquarter)
        preparedStatement.setString(6, airlineBase.countryCode)
        preparedStatement.executeUpdate()
      }
      AirlineCache.invalidateAirline(airlineBase.airline.id)
      AirportCache.refreshAirport(airlineBase.airport.id)
    }
  }

  def deleteAirlineBase(airlineBase : AirlineBase) = {
    deleteAirlineBaseByCriteria(List(("airline", airlineBase.airline.id), ("airport", airlineBase.airport.id)))
  }

  def deleteAirlineBaseByCriteria(criteria : List[(String, Any)]) = {
    val deletingBases = loadAirlineBasesByCriteria(criteria)
    Using.resource(Meta.getConnection()) { connection =>
      var queryString = "DELETE FROM " + AIRLINE_BASE_TABLE

      if (!criteria.isEmpty) {
        queryString += " WHERE "
        for (i <- 0 until criteria.size - 1) {
          queryString += criteria(i)._1 + " = ? AND "
        }
        queryString += criteria.last._1 + " = ?"
      }

      Using.resource(connection.prepareStatement(queryString)) { preparedStatement =>
        for (i <- 0 until criteria.size) {
          preparedStatement.setObject(i + 1, criteria(i)._2)
        }
        val deletedCount = preparedStatement.executeUpdate()
        deletingBases.foreach { base =>
          AirlineCache.invalidateAirline(base.airline.id)
          AirportCache.refreshAirport(base.airport.id)
          println(s"Purged from cache base record $base")
        }
        println("Deleted " + deletedCount + " airline base records")
        deletedCount
      }
    }
  }

  //
  def loadAllLounges() : List[Lounge] = {
    loadLoungesByCriteria(List())
  }


  def loadLoungesByAirportId(airportId : Int) : List[Lounge] = {
    loadLoungesByCriteria(List(("airport", airportId)))
  }

  def loadLoungesByAirport(airport : Airport) : List[Lounge] = {
    loadLoungesByCriteria(List(("airport", airport.id)), Map(airport.id -> airport))
  }

  def loadLoungesByAirline(airlineId : Int) : List[Lounge] = {
    loadLoungesByCriteria(List(("airline", airlineId)))
  }


  def loadLoungesByCountryCode(countryCode : String) : List[Lounge] = {
    loadLoungesByCriteria(List(("country", countryCode)))
  }

  def loadLoungeByAirlineAndAirport(airlineId : Int, airportId : Int) : Option[Lounge] = {
    val result = loadLoungesByCriteria(List(("airline", airlineId), ("airport", airportId)))
    if (result.isEmpty) {
      None
    } else {
      Some(result(0))
    }
  }

  def loadLoungesByCriteria(criteria : List[(String, Any)], airports : Map[Int, Airport] = Map[Int, Airport]()) : List[Lounge] = {
    Using.resource(Meta.getConnection()) { connection =>
      var queryString = "SELECT * FROM " + LOUNGE_TABLE

      if (!criteria.isEmpty) {
        queryString += " WHERE "
        for (i <- 0 until criteria.size - 1) {
          queryString += criteria(i)._1 + " = ? AND "
        }
        queryString += criteria.last._1 + " = ?"
      }

      Using.resource(connection.prepareStatement(queryString)) { preparedStatement =>
        for (i <- 0 until criteria.size) {
          preparedStatement.setObject(i + 1, criteria(i)._2)
        }
        Using.resource(preparedStatement.executeQuery()) { resultSet =>
          val lounges = new ListBuffer[Lounge]()
          val airlines = Map[Int, Airline]()
          while (resultSet.next()) {
            val airlineId = resultSet.getInt("airline")
            val airline = airlines.getOrElseUpdate(airlineId, AirlineCache.getAirline(airlineId, false).getOrElse(Airline.fromId(airlineId)))
            AllianceSource.loadAllianceMemberByAirline(airline).foreach { member =>
              if (member.role != AllianceRole.APPLICANT) {
                airline.setAllianceId(member.allianceId)
              }
            }
            val airportId = resultSet.getInt("airport")
            val airport = airports.getOrElseUpdate(airportId, AirportCache.getAirport(airportId, true).get)
            val name = resultSet.getString("name")
            val level = resultSet.getInt("level")
            val foundedCycle = resultSet.getInt("founded_cycle")
            val status = resultSet.getString("status")
            lounges += Lounge(airline, airline.getAllianceId(), airport, name, level, LoungeStatus.withName(status), foundedCycle)
          }
          lounges.toList
        }
      }
    }
  }


  //case class AirlineBase(airline : Airline, airport : Airport, scale : Int, headQuarter : Boolean = false, var id : Int = 0) extends IdObject
  def saveLounge(lounge : Lounge) = {
    Using.resource(Meta.getConnection()) { connection =>
      Using.resource(connection.prepareStatement("REPLACE INTO " + LOUNGE_TABLE + "(airline, airport, name, level, status, founded_cycle) VALUES(?, ?, ?, ?, ?, ?)")) { preparedStatement =>
        preparedStatement.setInt(1, lounge.airline.id)
        preparedStatement.setInt(2, lounge.airport.id)
        preparedStatement.setString(3, lounge.name)
        preparedStatement.setInt(4, lounge.level)
        preparedStatement.setString(5, lounge.status.toString())
        preparedStatement.setInt(6, lounge.foundedCycle)
        preparedStatement.executeUpdate()
      }
      AirlineCache.invalidateAirline(lounge.airline.id)
      AirportCache.refreshAirport(lounge.airport.id)
    }
  }

  def deleteLounge(lounge : Lounge) = {
    deleteLoungeByCriteria(List(("airline", lounge.airline.id), ("airport", lounge.airport.id)))
    AirlineCache.invalidateAirline(lounge.airline.id)
    AirportCache.refreshAirport(lounge.airport.id)
  }

  def deleteLoungeByCriteria(criteria : List[(String, Any)]) = {
    Using.resource(Meta.getConnection()) { connection =>
      var queryString = "DELETE FROM " + LOUNGE_TABLE

      if (!criteria.isEmpty) {
        queryString += " WHERE "
        for (i <- 0 until criteria.size - 1) {
          queryString += criteria(i)._1 + " = ? AND "
        }
        queryString += criteria.last._1 + " = ?"
      }

      Using.resource(connection.prepareStatement(queryString)) { preparedStatement =>
        for (i <- 0 until criteria.size) {
          preparedStatement.setObject(i + 1, criteria(i)._2)
        }
        val deletedCount = preparedStatement.executeUpdate()
        println("Deleted " + deletedCount + " lounge records")
        deletedCount
      }
    }
  }


  def saveTransaction(transaction : AirlineTransaction) = {
    Using.resource(Meta.getConnection()) { connection =>
      Using.resource(connection.prepareStatement("INSERT INTO " + AIRLINE_TRANSACTION_TABLE + " VALUES(?, ?, ?, ?)")) { preparedStatement =>
        preparedStatement.setInt(1, transaction.airlineId)
        preparedStatement.setInt(2, transaction.transactionType.id)
        preparedStatement.setDouble(3, transaction.amount)
        //cannot use MainSimulation.currentWeek as this could be called from other projects apart from simulation
        preparedStatement.setInt(4, CycleSource.loadCycle())
        preparedStatement.executeUpdate()
      }
    }
  }

  def loadTransactions(cycle : Int) : List[AirlineTransaction] = {
    Using.resource(Meta.getConnection()) { connection =>
      Using.resource(connection.prepareStatement("SELECT * FROM " + AIRLINE_TRANSACTION_TABLE + " WHERE cycle = ?")) { preparedStatement =>
        preparedStatement.setInt(1, cycle)
        Using.resource(preparedStatement.executeQuery()) { resultSet =>
          val transactions = new ListBuffer[AirlineTransaction]()
          while (resultSet.next()) {
            transactions += AirlineTransaction(resultSet.getInt("airline"), TransactionType(resultSet.getInt("transaction_type")), resultSet.getLong("amount"))
          }
          transactions.toList
        }
      }
    }
  }

  def deleteTransactions(cycleAndBefore : Int) = {
    Using.resource(Meta.getConnection()) { connection =>
      Using.resource(connection.prepareStatement("DELETE FROM " + AIRLINE_TRANSACTION_TABLE + " WHERE cycle <= ?")) { preparedStatement =>
        preparedStatement.setInt(1, cycleAndBefore)
        preparedStatement.executeUpdate()
      }
    }
  }


  def saveCashFlowItem(cashFlowItem : AirlineCashFlowItem) = {
    Using.resource(Meta.getConnection()) { connection =>
      Using.resource(connection.prepareStatement("INSERT INTO " + AIRLINE_CASH_FLOW_ITEM_TABLE + " VALUES(?, ?, ?, ?)")) { preparedStatement =>
        preparedStatement.setInt(1, cashFlowItem.airlineId)
        preparedStatement.setInt(2, cashFlowItem.cashFlowType.id)
        preparedStatement.setDouble(3, cashFlowItem.amount)
        //cannot use MainSimulation.currentWeek as this could be called from other projects apart from simulation
        preparedStatement.setInt(4, CycleSource.loadCycle())
        preparedStatement.executeUpdate()
      }
    }
  }

  def loadCashFlowItems(cycle : Int) : List[AirlineCashFlowItem] = {
    Using.resource(Meta.getConnection()) { connection =>
      Using.resource(connection.prepareStatement("SELECT * FROM " + AIRLINE_CASH_FLOW_ITEM_TABLE + " WHERE cycle = ?")) { preparedStatement =>
        preparedStatement.setInt(1, cycle)
        Using.resource(preparedStatement.executeQuery()) { resultSet =>
          val transactions = new ListBuffer[AirlineCashFlowItem]()
          while (resultSet.next()) {
            transactions += AirlineCashFlowItem(resultSet.getInt("airline"), CashFlowType(resultSet.getInt("cash_flow_type")), resultSet.getLong("amount"))
          }
          transactions.toList
        }
      }
    }
  }

  def deleteCashFlowItems(cycleAndBefore : Int) = {
    Using.resource(Meta.getConnection()) { connection =>
      Using.resource(connection.prepareStatement("DELETE FROM " + AIRLINE_CASH_FLOW_ITEM_TABLE + " WHERE cycle <= ?")) { preparedStatement =>
        preparedStatement.setInt(1, cycleAndBefore)
        preparedStatement.executeUpdate()
      }
    }
  }

  def deleteGeneratedAirlines(fromId : Int) = {
    Using.resource(Meta.getConnection()) { connection =>
      Using.resource(connection.prepareStatement("DELETE FROM " + AIRLINE_TABLE + " WHERE id >= ?")) { preparedStatement =>
        preparedStatement.setInt(1, fromId)
        preparedStatement.executeUpdate()
      }
    }
  }


  def saveSlogan(airlineId : Int, slogan : String) = {
    Using.resource(Meta.getConnection()) { connection =>
      Using.resource(connection.prepareStatement("REPLACE INTO " + AIRLINE_SLOGAN_TABLE + " VALUES(?, ?)")) { preparedStatement =>
        preparedStatement.setInt(1, airlineId)
        preparedStatement.setString(2, slogan)
        preparedStatement.executeUpdate()
      }
    }
  }


  def loadSlogan(airlineId : Int) : Option[String] = {
    Using.resource(Meta.getConnection()) { connection =>
      Using.resource(connection.prepareStatement(s"SELECT * FROM $AIRLINE_SLOGAN_TABLE WHERE airline = ?")) { preparedStatement =>
        preparedStatement.setInt(1, airlineId)
        Using.resource(preparedStatement.executeQuery()) { resultSet =>
          if (resultSet.next()) {
            Some(resultSet.getString("slogan"))
          } else {
            None
          }
        }
      }
    }
  }

  def updateAirlineName(airlineId : Int, oldName: String, newName : String) : Unit = {
    Using.resource(Meta.getConnection()) { connection =>
      Using.resource(connection.prepareStatement(s"UPDATE ${AIRLINE_TABLE} SET name = ? WHERE id = ?")) { setNameStatement =>
        setNameStatement.setString(1, newName)
        setNameStatement.setInt(2, airlineId)
        setNameStatement.executeUpdate()
      }
      Using.resource(connection.prepareStatement("INSERT INTO " + AIRLINE_NAME_HISTORY_TABLE + "(airline, name) VALUES(?, ?)")) { nameHistoryStatement =>
        nameHistoryStatement.setInt(1, airlineId)
        nameHistoryStatement.setString(2, oldName)
        nameHistoryStatement.executeUpdate()
      }
      AirlineCache.invalidateAirline(airlineId)
    }
  }

  def loadPreviousNameHistory(airlineId : Int) : List[NameHistory] = {
    Using.resource(Meta.getConnection()) { connection =>
      Using.resource(connection.prepareStatement(s"SELECT * FROM $AIRLINE_NAME_HISTORY_TABLE WHERE airline = ?")) { preparedStatement =>
        preparedStatement.setInt(1, airlineId)
        Using.resource(preparedStatement.executeQuery()) { resultSet =>
          val result = ListBuffer[NameHistory]()
          while (resultSet.next()) {
            result.append(NameHistory(resultSet.getString("name"), new java.util.Date(resultSet.getTimestamp("update_timestamp").getTime)))
          }
          result.toList
        }
      }
    }
  }


  def saveColor(airlineId : Int, color : String) = {
    Using.resource(Meta.getConnection()) { connection =>
      Using.resource(connection.prepareStatement("UPDATE " + AIRLINE_INFO_TABLE + " SET color = ? WHERE airline = ?")) { preparedStatement =>
        preparedStatement.setString(1, color)
        preparedStatement.setInt(2, airlineId)
        preparedStatement.executeUpdate()
      }
      AirlineCache.invalidateAirline(airlineId)
    }
  }

  def getColors() = {
    Using.resource(Meta.getConnection()) { connection =>
      Using.resource(connection.prepareStatement("SELECT airline, color FROM " + AIRLINE_INFO_TABLE + " WHERE color IS NOT NULL")) { preparedStatement =>
        Using.resource(preparedStatement.executeQuery()) { resultSet =>
          val colors = scala.collection.mutable.Map[Int, String]()
          while (resultSet.next()) {
            colors.put(resultSet.getInt("airline"), resultSet.getString("color"))
          }
          colors.toMap
        }
      }
    }
  }

  def deleteAirplaneRenewal(airlineId : Int) = {
    Using.resource(Meta.getConnection()) { connection =>
      Using.resource(connection.prepareStatement("DELETE FROM " + AIRPLANE_RENEWAL_TABLE + " WHERE airline = ?")) { preparedStatement =>
        preparedStatement.setInt(1, airlineId)
        preparedStatement.executeUpdate()
      }
    }
  }

  def saveAirplaneRenewal(airlineId : Int, threshold : Int) = {
    Using.resource(Meta.getConnection()) { connection =>
      Using.resource(connection.prepareStatement("REPLACE INTO " + AIRPLANE_RENEWAL_TABLE + "(airline, threshold) VALUES(?, ?)")) { preparedStatement =>
        preparedStatement.setInt(1, airlineId)
        preparedStatement.setInt(2, threshold)
        preparedStatement.executeUpdate()
      }
    }
  }

  def loadAirplaneRenewal(airlineId : Int) : Option[Int] = {
    Using.resource(Meta.getConnection()) { connection =>
      Using.resource(connection.prepareStatement("SELECT threshold FROM " + AIRPLANE_RENEWAL_TABLE + " WHERE airline = ?")) { preparedStatement =>
        preparedStatement.setInt(1, airlineId)
        Using.resource(preparedStatement.executeQuery()) { resultSet =>
          if (resultSet.next()) {
            Some(resultSet.getInt("threshold"))
          } else {
            None
          }
        }
      }
    }
  }

  def loadAirplaneRenewals() : scala.collection.immutable.Map[Int, Int] = {
    Using.resource(Meta.getConnection()) { connection =>
      Using.resource(connection.prepareStatement("SELECT * FROM " + AIRPLANE_RENEWAL_TABLE)) { preparedStatement =>
        Using.resource(preparedStatement.executeQuery()) { resultSet =>
          val result : Map[Int, Int] = Map[Int, Int]()
          while (resultSet.next()) {
            result.put(resultSet.getInt("airline"), resultSet.getInt("threshold"))
          }
          result.toMap
        }
      }
    }
  }

  def loadReputationBreakdowns(airlineId : Int) = {
    Using.resource(Meta.getConnection()) { connection =>
      Using.resource(connection.prepareStatement(s"SELECT * FROM $AIRLINE_REPUTATION_BREAKDOWN WHERE airline = ?")) { preparedStatement =>
        preparedStatement.setInt(1, airlineId)
        Using.resource(preparedStatement.executeQuery()) { resultSet =>
          val result = ListBuffer[ReputationBreakdown]()
          while (resultSet.next()) {
            val breakdown = ReputationBreakdown(
              ReputationType.withName(resultSet.getString("reputation_type")),
              resultSet.getDouble("rep_value"),
              resultSet.getLong("quantity_value"),
            )
            if (breakdown.quantityValue > 0) {
              result.append(breakdown)
            }
          }
          ReputationBreakdowns(result.toList)
        }
      }
    }
  }

  def deleteReputationBreakdowns(airlineId : Int) = {
    Using.resource(Meta.getConnection()) { connection =>
      connection.setAutoCommit(false)
      Using.resource(connection.prepareStatement(s"DELETE FROM $AIRLINE_REPUTATION_BREAKDOWN WHERE airline = ?")) { preparedStatement =>
        preparedStatement.setInt(1, airlineId)
        preparedStatement.executeUpdate()
      }
      connection.commit()
    }
  }

  def updateReputationBreakdowns(airlineId : Int, breakdowns : ReputationBreakdowns): Unit = {
    val UnsignedIntMax: Long = 4294967295L
    Using.resource(Meta.getConnection()) { connection =>
      connection.setAutoCommit(false)
      Using.resource(connection.prepareStatement(s"DELETE FROM $AIRLINE_REPUTATION_BREAKDOWN WHERE airline = ?")) { deleteStatement =>
        deleteStatement.setInt(1, airlineId)
        deleteStatement.executeUpdate()
      }
      Using.resource(connection.prepareStatement(s"INSERT INTO $AIRLINE_REPUTATION_BREAKDOWN(airline, reputation_type, rep_value, quantity_value) VALUES(?,?,?,?)")) { preparedStatement =>
        breakdowns.breakdowns.foreach { breakdown =>
          preparedStatement.setInt(1, airlineId)
          preparedStatement.setString(2, breakdown.reputationType.toString)
          preparedStatement.setDouble(3, breakdown.value)
          if (breakdown.quantityValue < 0 || breakdown.quantityValue > UnsignedIntMax) {
            println(s"Reputation breakdown quantity value ${breakdown.quantityValue} for airline $airlineId type ${breakdown.reputationType} is out of range, clamping it")
          }
          preparedStatement.setLong(4, math.max(0, math.min(breakdown.quantityValue, UnsignedIntMax)))
          preparedStatement.addBatch()
        }
        preparedStatement.executeBatch()
      }
      connection.commit()
    }
  }


  def deleteAirlineModifier(airlineId : Int, modifierType : AirlineModifierType.Value) = {
    Using.resource(Meta.getConnection()) { connection =>
      Using.resource(connection.prepareStatement(s"DELETE FROM $AIRLINE_MODIFIER_TABLE WHERE airline = ? AND modifier_name = ?")) { preparedStatement =>
        preparedStatement.setInt(1, airlineId)
        preparedStatement.setString(2, modifierType.toString)
        preparedStatement.executeUpdate()
      }
    }
  }


  def deleteAirlineModifierByExpiry(cutoff : Int) = {
    Using.resource(Meta.getConnection()) { connection =>
      Using.resource(connection.prepareStatement(s"DELETE FROM $AIRLINE_MODIFIER_TABLE WHERE expiry IS NOT NULL && expiry <= ?")) { preparedStatement =>
        preparedStatement.setInt(1, cutoff)
        preparedStatement.executeUpdate()
      }
    }
  }

  def saveAirlineModifier(airlineId : Int, modifier : AirlineModifier) = {
    Using.resource(Meta.getConnection()) { connection =>
      Using.resource(connection.prepareStatement(s"INSERT INTO $AIRLINE_MODIFIER_TABLE (airline, modifier_name, creation, expiry) VALUES(?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) { preparedStatement =>
        preparedStatement.setInt(1, airlineId)
        preparedStatement.setString(2, modifier.modifierType.toString)
        preparedStatement.setInt(3, modifier.creationCycle)
        modifier.expiryCycle match {
          case Some(expiryCycle) => preparedStatement.setInt(4, expiryCycle)
          case None => preparedStatement.setNull(4, java.sql.Types.INTEGER)
        }
        preparedStatement.executeUpdate()
        val generatedKeys = preparedStatement.getGeneratedKeys
        if (generatedKeys.next()) {
          val generatedId = generatedKeys.getInt(1)
          modifier.id = generatedId
        }
      }
      saveAirlineModifierProperties(modifier)
      AirlineCache.invalidateAirline(airlineId)
    }
  }

  private[this] def saveAirlineModifierProperties(modifier : AirlineModifier) = {
    Using.resource(Meta.getConnection()) { connection =>
      Using.resource(connection.prepareStatement(s"REPLACE INTO $AIRLINE_MODIFIER_PROPERTY_TABLE (id, name, value) VALUES(?, ?, ?)")) { preparedStatement =>
        modifier.properties.foreach {
          case (propertyType, value) =>
            preparedStatement.setInt(1, modifier.id)
            preparedStatement.setString(2, propertyType.toString)
            preparedStatement.setLong(3, value)
            preparedStatement.executeUpdate()
        }
      }
    }
  }

  def loadAirlineModifierProperties() = {
    Using.resource(Meta.getConnection()) { connection =>
      Using.resource(connection.prepareStatement("SELECT * FROM " + AIRLINE_MODIFIER_PROPERTY_TABLE)) { preparedStatement =>
        Using.resource(preparedStatement.executeQuery()) { resultSet =>
          val result : Map[Int, Map[AirlineModifierPropertyType.Value, Long]] = Map()
          while (resultSet.next()) {
            val propertyType = AirlineModifierPropertyType.withName(resultSet.getString("name"))
            val value = resultSet.getLong("value")
            val id = resultSet.getInt("id")
            val map = result.getOrElseUpdate(id, Map())
            map.put(propertyType, value)
          }
          result.view.mapValues(_.toMap).toMap
        }
      }
    }
  }


  def loadAirlineModifiers() : List[(Int, AirlineModifier)] = { //_1 is airline Id
    Using.resource(Meta.getConnection()) { connection =>
      val propertiesById = loadAirlineModifierProperties()

      Using.resource(connection.prepareStatement("SELECT * FROM " + AIRLINE_MODIFIER_TABLE)) { preparedStatement =>
        Using.resource(preparedStatement.executeQuery()) { resultSet =>
          val result : ListBuffer[(Int, AirlineModifier)] = ListBuffer[(Int, AirlineModifier)]()
          while (resultSet.next()) {
            val expiryObject = resultSet.getObject("expiry")
            val id = resultSet.getInt("id")
            val airlineModifier = AirlineModifier.fromValues(
              AirlineModifierType.withName(resultSet.getString("modifier_name")),
              resultSet.getInt("creation"),
              if (expiryObject == null) None else Some(expiryObject.asInstanceOf[Int]),
              propertiesById.get(id).getOrElse(immutable.Map.empty)
            )
            airlineModifier.id = id
            result.append((resultSet.getInt("airline"), airlineModifier))
          }
          result.toList
        }
      }
    }
  }

  def loadAirlineModifierByAirlineId(airlineId : Int) : List[AirlineModifier] = {
    Using.resource(Meta.getConnection()) { connection =>
      Using.resource(connection.prepareStatement("SELECT * FROM " + AIRLINE_MODIFIER_TABLE + " WHERE airline = ?")) { preparedStatement =>
        preparedStatement.setInt(1, airlineId)
        Using.resource(preparedStatement.executeQuery()) { resultSet =>
          val result = ListBuffer[AirlineModifier]()
          while (resultSet.next()) {
            val expiryObject = resultSet.getObject("expiry")
            val airlineModifier = AirlineModifier.fromValues(
              AirlineModifierType.withName(resultSet.getString("modifier_name")),
              resultSet.getInt("creation"),
              if (expiryObject == null) None else Some(expiryObject.asInstanceOf[Int]),
              loadAirlineModifierPropertiesById(resultSet.getInt("id"))
            )
            result.append(airlineModifier)
          }
          result.toList
        }
      }
    }
  }

  def loadAirlineModifierPropertiesById(id : Int) = {
    Using.resource(Meta.getConnection()) { connection =>
      Using.resource(connection.prepareStatement("SELECT * FROM " + AIRLINE_MODIFIER_PROPERTY_TABLE + " WHERE id = ?")) { preparedStatement =>
        preparedStatement.setInt(1, id)
        Using.resource(preparedStatement.executeQuery()) { resultSet =>
          val result = Map[AirlineModifierPropertyType.Value, Long]()
          while (resultSet.next()) {
            val propertyType = AirlineModifierPropertyType.withName(resultSet.getString("name"))
            val value = resultSet.getLong("value")
            result.put(propertyType, value)
          }
          result.toMap
        }
      }
    }
  }
}
