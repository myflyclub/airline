package com.patson.data
import com.patson.data.Constants._
import com.patson.model._
import com.patson.util.AirportCache

import scala.collection.mutable.ListBuffer


object ConsumptionHistorySource {
  var MAX_CONSUMPTION_HISTORY_WEEK = 30

  val updateConsumptions = (consumptions : Map[(PassengerGroup, Airport, Route), Int]) => {
    val connection = Meta.getConnection()
    val passengerRouteHistoryStatement = connection.prepareStatement("INSERT INTO " + PASSENGER_ROUTE_HISTORY_TABLE_TEMP + " (route_id, passenger_count, home_country, home_airport, destination_airport, passenger_type, preference_type, preferred_link_class, route_cost) VALUES(?,?,?,?,?,?,?,?,?)")
    val passengerLinkHistoryStatement = connection.prepareStatement("INSERT INTO " + PASSENGER_LINK_HISTORY_TABLE_TEMP + " (route_id, link, link_class, inverted, cost, satisfaction) VALUES(?,?,?,?,?,?)")
    
    connection.setAutoCommit(false)
    connection.createStatement().executeUpdate("DROP TABLE IF EXISTS " + PASSENGER_ROUTE_HISTORY_TABLE_TEMP);
    connection.createStatement().executeUpdate("DROP TABLE IF EXISTS " + PASSENGER_LINK_HISTORY_TABLE_TEMP);
    connection.createStatement().executeUpdate("CREATE TABLE " + PASSENGER_ROUTE_HISTORY_TABLE_TEMP + " LIKE " + PASSENGER_ROUTE_HISTORY_TABLE);
    connection.createStatement().executeUpdate("CREATE TABLE " + PASSENGER_LINK_HISTORY_TABLE_TEMP + " LIKE " + PASSENGER_LINK_HISTORY_TABLE);
    
    var routeId = 0
    val batchSize = 1000
    
    try {
      consumptions.foreach { 
        case((passengerGroup, _, route), passengerCount) => {
          routeId += 1
          
          // Insert route data
          passengerRouteHistoryStatement.setInt(1, routeId)
          passengerRouteHistoryStatement.setInt(2, passengerCount) //passenger_count
          passengerRouteHistoryStatement.setString(3, passengerGroup.fromAirport.countryCode) //home_country
          passengerRouteHistoryStatement.setInt(4, passengerGroup.fromAirport.id) //home_airport
          passengerRouteHistoryStatement.setInt(5, route.links.last.to.id) //destination_airport
          passengerRouteHistoryStatement.setInt(6, passengerGroup.passengerType.id) //passenger_type
          passengerRouteHistoryStatement.setInt(7, passengerGroup.preference.getPreferenceType.id) //preference_type
          passengerRouteHistoryStatement.setString(8, passengerGroup.preference.preferredLinkClass.code) //preferred_link_class
          passengerRouteHistoryStatement.setInt(9, route.totalCost.toInt) //route_cost
          passengerRouteHistoryStatement.addBatch()
          
          // Insert link data
          route.links.foreach { linkConsideration =>
            val preferredLinkClass = passengerGroup.preference.preferredLinkClass
            val satisfaction = Computation.computePassengerSatisfaction(linkConsideration.cost.toInt, linkConsideration.link.standardPrice(preferredLinkClass, passengerGroup.passengerType), linkConsideration.link.getLoadFactor, linkConsideration.link.getDelayRatio)
            
            passengerLinkHistoryStatement.setInt(1, routeId)
            passengerLinkHistoryStatement.setInt(2, linkConsideration.link.id)
            passengerLinkHistoryStatement.setString(3, linkConsideration.linkClass.code)
            passengerLinkHistoryStatement.setBoolean(4, linkConsideration.inverted)
            passengerLinkHistoryStatement.setDouble(5, linkConsideration.cost)
            passengerLinkHistoryStatement.setDouble(6, satisfaction)
            passengerLinkHistoryStatement.addBatch()
          }
          
          if (routeId % batchSize == 0) {
            passengerRouteHistoryStatement.executeBatch()
            passengerLinkHistoryStatement.executeBatch()
          }
        }
      }
      passengerRouteHistoryStatement.executeBatch()
      passengerLinkHistoryStatement.executeBatch()

      //rotate the tables
      println("Rotating tables")
      for (i <- MAX_CONSUMPTION_HISTORY_WEEK to 1 by -1) {
        val fromTableName =
          if (i == 1) {
            PASSENGER_ROUTE_HISTORY_TABLE
          } else {
            PASSENGER_ROUTE_HISTORY_TABLE + "_" + (i - 1)
          }
        val toTableName = PASSENGER_ROUTE_HISTORY_TABLE + "_" + i

        if (Meta.isTableExist(connection, fromTableName)) {
          connection.createStatement().executeUpdate(s"DROP TABLE IF EXISTS $toTableName")
          connection.createStatement().executeUpdate(s"ALTER TABLE $fromTableName RENAME $toTableName")
        }
      }
      
      for (i <- MAX_CONSUMPTION_HISTORY_WEEK to 1 by -1) {
        val fromTableName =
          if (i == 1) {
            PASSENGER_LINK_HISTORY_TABLE
          } else {
            PASSENGER_LINK_HISTORY_TABLE + "_" + (i - 1)
          }
        val toTableName = PASSENGER_LINK_HISTORY_TABLE + "_" + i

        if (Meta.isTableExist(connection, fromTableName)) {
          connection.createStatement().executeUpdate(s"DROP TABLE IF EXISTS $toTableName")
          connection.createStatement().executeUpdate(s"ALTER TABLE $fromTableName RENAME $toTableName")
        }
      }

      connection.createStatement().executeUpdate("DROP TABLE IF EXISTS " + PASSENGER_ROUTE_HISTORY_TABLE);
      connection.createStatement().executeUpdate("ALTER TABLE " + PASSENGER_ROUTE_HISTORY_TABLE_TEMP + " RENAME " + PASSENGER_ROUTE_HISTORY_TABLE)
      connection.createStatement().executeUpdate("DROP TABLE IF EXISTS " + PASSENGER_LINK_HISTORY_TABLE);
      connection.createStatement().executeUpdate("ALTER TABLE " + PASSENGER_LINK_HISTORY_TABLE_TEMP + " RENAME " + PASSENGER_LINK_HISTORY_TABLE)
      connection.commit()
      println("Finished rotating tables")
    } finally {
      passengerRouteHistoryStatement.close()
      passengerLinkHistoryStatement.close()
      connection.close()
    }
  }

  def deleteAllConsumptions() = {
    val connection = Meta.getConnection()

    try {
      for (i <- MAX_CONSUMPTION_HISTORY_WEEK to 1 by -1) {
        val routeTableName = PASSENGER_ROUTE_HISTORY_TABLE + "_" + i
        val linkTableName = PASSENGER_LINK_HISTORY_TABLE + "_" + i

        connection.createStatement().executeUpdate(s"DROP TABLE IF EXISTS $routeTableName")
        connection.createStatement().executeUpdate(s"DROP TABLE IF EXISTS $linkTableName")
      }
      connection.createStatement().executeUpdate(s"TRUNCATE TABLE $PASSENGER_ROUTE_HISTORY_TABLE")
      connection.createStatement().executeUpdate(s"TRUNCATE TABLE $PASSENGER_LINK_HISTORY_TABLE")
    } finally {
      connection.close()
    }
  }

  /**
   * Research, Airport search pair
   *
   * @param fromAirportId
   * @param toAirportId
   * @return
   */
  def loadConsumptionsByAirportPair(fromAirportId : Int, toAirportId : Int) : Map[Route, (PassengerType.Value, Int)] = {
    val connection = Meta.getConnection()
    try {
      // First, get all route IDs matching the airport pair from route history table
      val routeQueryString = s"SELECT route_id, passenger_type, passenger_count, route_cost FROM $PASSENGER_ROUTE_HISTORY_TABLE WHERE home_airport = ? AND destination_airport = ?"
      val routePreparedStatement = connection.prepareStatement(routeQueryString)
      routePreparedStatement.setInt(1, fromAirportId)
      routePreparedStatement.setInt(2, toAirportId)
      val routeResultSet = routePreparedStatement.executeQuery()
      
      val routeConsumptions = new scala.collection.mutable.HashMap[Int, (PassengerType.Value, Int)]()
      val routeIdsWithCosts = scala.collection.mutable.ListBuffer[(Int, Int)]()
      
      while (routeResultSet.next()) {
        val routeId = routeResultSet.getInt("route_id") //route_id, passenger_type, passenger_count, route_cost
        val passengerType = PassengerType.apply(routeResultSet.getInt("passenger_type"))
        val passengerCount = routeResultSet.getInt("passenger_count")
        val withRouteCost = (routeId, routeResultSet.getInt("route_cost"))
        routeIdsWithCosts += withRouteCost
        routeConsumptions.put(routeId, (passengerType, passengerCount))
      }
      
      // Build dynamic IN clause for link query
      if (routeIdsWithCosts.isEmpty) {
        return Map.empty
      }
      
      val linkQueryString = s"SELECT * FROM $PASSENGER_LINK_HISTORY_TABLE WHERE route_id IN (${routeIdsWithCosts.map(_ => "?").mkString(",")})"
      val linkPreparedStatement = connection.prepareStatement(linkQueryString)
      for (i <- routeIdsWithCosts.indices) {
        linkPreparedStatement.setInt(i + 1, routeIdsWithCosts(i)._1)
      }
      val linkResultSet = linkPreparedStatement.executeQuery()

      val linkConsiderationsByRouteId = scala.collection.mutable.Map[Int, ListBuffer[LinkConsideration]]()
      val allLinkIds = scala.collection.mutable.HashSet[Int]()

      while (linkResultSet.next()) {
        val linkId = linkResultSet.getInt("link")
        allLinkIds += linkId
      }

      val linkConsumptionById: Map[Int, LinkConsumptionDetails] = LinkSource.loadLinkConsumptionsByLinksId(allLinkIds.toList).map(entry => (entry.link.id, entry)).toMap

      // Reconstruct routes with link considerations
      linkResultSet.beforeFirst()
      while (linkResultSet.next()) {
        val linkId = linkResultSet.getInt("link")
        val routeId = linkResultSet.getInt("route_id")
        linkConsumptionById.get(linkId).foreach { linkConsumption =>
          val linkConsideration = LinkConsideration.getExplicit(linkConsumption.link, linkResultSet.getInt("cost"), LinkClass.fromCode(linkResultSet.getString("link_class")), linkResultSet.getBoolean("inverted"))
          val existingConsiderationsForThisRoute = linkConsiderationsByRouteId.getOrElseUpdate(routeId, ListBuffer[LinkConsideration]())
          existingConsiderationsForThisRoute += linkConsideration
        }
      }

      val routeIdsCostsMap = routeIdsWithCosts.toMap
      val result: Map[Route, (PassengerType.Value, Int)] = linkConsiderationsByRouteId.view.map {
        case (routeId: Int, considerations: ListBuffer[LinkConsideration]) => (Route(considerations.toList, routeIdsCostsMap(routeId), List.empty, routeId), routeConsumptions(routeId))
      }.toMap

      println(s"Loaded ${result.size} routes for airport pair ${fromAirportId} and ${toAirportId})")

      result
    } finally {
      connection.close()
    }
  }
  /**
   *
   *
   * @param linkId
   * @param cycle
   * @return
   */
  def loadRelatedConsumptionByLinkId(linkId: Int, cycle: Int): Map[Route, (PassengerType.Value, Int)] = {
    val cycleDelta = cycle - CycleSource.loadCycle()

    val linkTableName =
      if (cycleDelta >= 0) {
        PASSENGER_LINK_HISTORY_TABLE
      } else {
        PASSENGER_LINK_HISTORY_TABLE + "_" + (cycleDelta * -1)
      }

    val routeTableName =
      if (cycleDelta >= 0) {
        PASSENGER_ROUTE_HISTORY_TABLE
      } else {
        PASSENGER_ROUTE_HISTORY_TABLE + "_" + (cycleDelta * -1)
      }

    LinkSource.loadFlightLinkById(linkId, LinkSource.SIMPLE_LOAD) match {
      case Some(link) =>
        val connection = Meta.getConnection()
        try {
          if (Meta.isTableExist(connection, linkTableName) && Meta.isTableExist(connection, routeTableName)) {
            // First, find all route IDs that contain this link
            val linkQueryString = s"SELECT DISTINCT route_id FROM $linkTableName WHERE link = ?"
            val linkPreparedStatement = connection.prepareStatement(linkQueryString)
            linkPreparedStatement.setInt(1, linkId)
            val linkResultSet = linkPreparedStatement.executeQuery()

            val relatedRouteIds = new ListBuffer[Int]()
            while (linkResultSet.next()) {
              relatedRouteIds += linkResultSet.getInt("route_id")
            }

            if (relatedRouteIds.isEmpty) {
              Map.empty
            } else {
              // Get route-level data
              val routeQueryString = s"SELECT route_id, passenger_type, passenger_count, route_cost FROM $routeTableName WHERE route_id IN (${relatedRouteIds.map(_ => "?").mkString(",")})"
              val routePreparedStatement = connection.prepareStatement(routeQueryString)
              for (i <- relatedRouteIds.indices) {
                routePreparedStatement.setInt(i + 1, relatedRouteIds(i))
              }
              val routeResultSet = routePreparedStatement.executeQuery()
              
              val routeConsumptions = new scala.collection.mutable.HashMap[Int, (PassengerType.Value, Int)]()
              val routeCosts = new scala.collection.mutable.HashMap[Int, Int]()
              while (routeResultSet.next()) {
                val routeId = routeResultSet.getInt("route_id")
                val passengerType = PassengerType.apply(routeResultSet.getInt("passenger_type"))
                val passengerCount = routeResultSet.getInt("passenger_count")
                val routeCost = routeResultSet.getInt("route_cost")
                routeConsumptions.put(routeId, (passengerType, passengerCount))
                routeCosts.put(routeId, routeCost)
              }

              // Get link-level data for all routes
              val allLinksQueryString = s"SELECT route_id, link FROM $linkTableName WHERE route_id IN (${relatedRouteIds.map(_ => "?").mkString(",")})"
              val allLinksPreparedStatement = connection.prepareStatement(allLinksQueryString)
              for (i <- 0 until relatedRouteIds.size) {
                allLinksPreparedStatement.setInt(i + 1, relatedRouteIds(i))
              }
              val allLinksResultSet = allLinksPreparedStatement.executeQuery()

              val relatedLinkIds = scala.collection.mutable.HashSet[Int]()
              while (allLinksResultSet.next()) {
                relatedLinkIds += allLinksResultSet.getInt("link")
              }
              val linkMap = LinkSource.loadLinksByIds(relatedLinkIds.toList).map(link => (link.id, link)).toMap

              // Get link-level data for reconstruction
              val linkDataQueryString = s"SELECT * FROM $linkTableName WHERE route_id IN (${relatedRouteIds.map(_ => "?").mkString(",")})"
              val linkDataPreparedStatement = connection.prepareStatement(linkDataQueryString)
              for (i <- 0 until relatedRouteIds.size) {
                linkDataPreparedStatement.setInt(i + 1, relatedRouteIds(i))
              }
              val linkDataResultSet = linkDataPreparedStatement.executeQuery()

              val linkConsiderationsByRouteId = scala.collection.mutable.Map[Int, ListBuffer[LinkConsideration]]()

              while (linkDataResultSet.next()) {
                val routeId = linkDataResultSet.getInt("route_id")
                val relatedLinkId = linkDataResultSet.getInt("link")
                val relatedLink = linkMap.getOrElse(relatedLinkId, Link.fromId(relatedLinkId))
                val linkConsideration = LinkConsideration.getExplicit(relatedLink, linkDataResultSet.getDouble("cost"), LinkClass.fromCode(linkDataResultSet.getString("link_class")), linkDataResultSet.getBoolean("inverted"))

                val existingConsiderationsForThisRoute = linkConsiderationsByRouteId.getOrElseUpdate(routeId, ListBuffer[LinkConsideration]())
                existingConsiderationsForThisRoute += linkConsideration
              }

              val result = linkConsiderationsByRouteId.map {
                case (routeId: Int, considerations: ListBuffer[LinkConsideration]) => (Route(considerations.toList, routeCosts(routeId), List.empty, routeId), routeConsumptions(routeId))
              }.toMap

              println("Loaded " + result.size + " routes related to link " + link)

              result
            }
          } else {
            Map.empty
          }
        } finally {
          connection.close()
        }
      case None => Map.empty
    }
  }

  /**
   * Used in survey
   *
   * @param linkId
   * @return
   */
  def loadConsumptionByLinkId(linkId : Int) : List[LinkConsumptionHistory] = {
    LinkSource.loadFlightLinkById(linkId) match {
      case Some(link) => 
        val connection = Meta.getConnection()
        try {
          // Query from link history table with JOIN to route history table
          val queryString = s"""
            SELECT 
              pl.route_id, pl.link_class, pl.inverted, pl.satisfaction,
              pr.passenger_type, pr.passenger_count, pr.home_airport, pr.destination_airport,
              pr.preference_type, pr.preferred_link_class, pr.route_cost
            FROM $PASSENGER_LINK_HISTORY_TABLE pl
            JOIN $PASSENGER_ROUTE_HISTORY_TABLE pr ON pl.route_id = pr.route_id
            WHERE pl.link = ?
          """
          val preparedStatement = connection.prepareStatement(queryString)
          preparedStatement.setInt(1, linkId)
          val resultSet = preparedStatement.executeQuery()
          
          val result = new ListBuffer[LinkConsumptionHistory]()
          while (resultSet.next()) {
            result += LinkConsumptionHistory(link = link,
                passengerCount = resultSet.getInt("passenger_count"), 
                homeAirport = AirportCache.getAirport(resultSet.getInt("home_airport")).get,
                destinationAirport = AirportCache.getAirport(resultSet.getInt("destination_airport")).get,
                passengerType = PassengerType(resultSet.getInt("passenger_type")),
                preferredLinkClass = LinkClass.fromCode(resultSet.getString("preferred_link_class")),
                preferenceType = FlightPreferenceType(resultSet.getInt("preference_type")),
                linkClass = LinkClass.fromCode(resultSet.getString("link_class")),
                satisfaction = resultSet.getDouble("satisfaction"),
                routeCost = resultSet.getInt("route_cost")
            )
          }
          
          result.toList
        } finally {
          connection.close()
        }
      case None => List.empty
    }
  }
}