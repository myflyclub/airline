package com.patson.data
import com.patson.data.Constants._
import com.patson.model._
import com.patson.util.AirportCache

import scala.collection.mutable.ListBuffer
import scala.util.Using


object ConsumptionHistorySource {
  var MAX_CONSUMPTION_HISTORY_WEEK = 30

  val updateConsumptions = (consumptions : Map[(PassengerGroup, Airport, Route), Int]) => {
    Using.resource(Meta.getConnection()) { connection =>
      connection.setAutoCommit(false)
      // Drop and recreate temp tables before the batch insert
      Using.resource(connection.createStatement()) { ddlStatement =>
        ddlStatement.executeUpdate("DROP TABLE IF EXISTS " + PASSENGER_ROUTE_HISTORY_TABLE_TEMP)
        ddlStatement.executeUpdate("DROP TABLE IF EXISTS " + PASSENGER_LINK_HISTORY_TABLE_TEMP)
        ddlStatement.executeUpdate("CREATE TABLE " + PASSENGER_ROUTE_HISTORY_TABLE_TEMP + " LIKE " + PASSENGER_ROUTE_HISTORY_TABLE)
        ddlStatement.executeUpdate("CREATE TABLE " + PASSENGER_LINK_HISTORY_TABLE_TEMP + " LIKE " + PASSENGER_LINK_HISTORY_TABLE)
      }

      var routeId = 0
      val batchSize = 1000

      Using.resource(connection.prepareStatement("INSERT INTO " + PASSENGER_ROUTE_HISTORY_TABLE_TEMP + " (route_id, passenger_count, home_country, home_airport, destination_airport, passenger_type, preference_type, preferred_link_class, route_cost) VALUES(?,?,?,?,?,?,?,?,?)")) { passengerRouteHistoryStatement =>
        Using.resource(connection.prepareStatement("INSERT INTO " + PASSENGER_LINK_HISTORY_TABLE_TEMP + " (route_id, link, link_class, inverted, cost, satisfaction) VALUES(?,?,?,?,?,?)")) { passengerLinkHistoryStatement =>
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
        }
      }

      //rotate the tables
      println("Rotating tables")
      Using.resource(connection.createStatement()) { rotateStatement =>
        for (i <- MAX_CONSUMPTION_HISTORY_WEEK to 1 by -1) {
          val fromTableName =
            if (i == 1) {
              PASSENGER_ROUTE_HISTORY_TABLE
            } else {
              PASSENGER_ROUTE_HISTORY_TABLE + "_" + (i - 1)
            }
          val toTableName = PASSENGER_ROUTE_HISTORY_TABLE + "_" + i

          if (Meta.isTableExist(connection, fromTableName)) {
            rotateStatement.executeUpdate(s"DROP TABLE IF EXISTS $toTableName")
            rotateStatement.executeUpdate(s"ALTER TABLE $fromTableName RENAME $toTableName")
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
            rotateStatement.executeUpdate(s"DROP TABLE IF EXISTS $toTableName")
            rotateStatement.executeUpdate(s"ALTER TABLE $fromTableName RENAME $toTableName")
          }
        }

        rotateStatement.executeUpdate("DROP TABLE IF EXISTS " + PASSENGER_ROUTE_HISTORY_TABLE)
        rotateStatement.executeUpdate("ALTER TABLE " + PASSENGER_ROUTE_HISTORY_TABLE_TEMP + " RENAME " + PASSENGER_ROUTE_HISTORY_TABLE)
        rotateStatement.executeUpdate("DROP TABLE IF EXISTS " + PASSENGER_LINK_HISTORY_TABLE)
        rotateStatement.executeUpdate("ALTER TABLE " + PASSENGER_LINK_HISTORY_TABLE_TEMP + " RENAME " + PASSENGER_LINK_HISTORY_TABLE)
      }
      connection.commit()
      println("Finished rotating tables")
    }
  }

  case class TicketedDemandEntry(toAirportId: Int, passengerType: Int, preferenceType: Int, preferredLinkClass: String, passengerCount: Int, airlineIds: List[Int])

  /**
   * Top individual route journeys from a given origin airport by passenger count.
   * Groups by route_id to collect all airline IDs across every leg of each journey.
   */
  def loadTopConsumptionsByFromAirport(fromAirportId: Int, limit: Int): List[TicketedDemandEntry] = {
    Using.resource(Meta.getConnection()) { connection =>
      Using.resource(connection.prepareStatement(
        s"""WITH TopRoutes AS (
            SELECT route_id, destination_airport, passenger_type, preference_type,
                preferred_link_class, passenger_count
            FROM $PASSENGER_ROUTE_HISTORY_TABLE
            WHERE home_airport = ?
            ORDER BY passenger_count DESC
            LIMIT ?
        )
        SELECT tr.destination_airport, tr.passenger_type, tr.preference_type, tr.preferred_link_class, tr.passenger_count,
            GROUP_CONCAT(DISTINCT l.airline ORDER BY l.airline) AS airline_ids
        FROM TopRoutes tr
        LEFT JOIN $PASSENGER_LINK_HISTORY_TABLE plh ON tr.route_id = plh.route_id
        LEFT JOIN $LINK_TABLE l ON plh.link = l.id
        GROUP BY tr.route_id, tr.destination_airport, tr.passenger_type, tr.preference_type, tr.preferred_link_class, tr.passenger_count
        ORDER BY tr.passenger_count DESC""".stripMargin
      )) { stmt =>
        stmt.setInt(1, fromAirportId)
        stmt.setInt(2, limit)
        Using.resource(stmt.executeQuery()) { rs =>
          val result = scala.collection.mutable.ListBuffer[TicketedDemandEntry]()
          while (rs.next()) {
            val airlineIds = Option(rs.getString("airline_ids"))
              .map(_.split(",").toList.flatMap(s => scala.util.Try(s.trim.toInt).toOption))
              .getOrElse(List.empty)
            result += TicketedDemandEntry(
              toAirportId = rs.getInt("destination_airport"),
              passengerType = rs.getInt("passenger_type"),
              preferenceType = rs.getInt("preference_type"),
              preferredLinkClass = rs.getString("preferred_link_class"),
              passengerCount = rs.getInt("passenger_count"),
              airlineIds = airlineIds
            )
          }
          result.toList
        }
      }
    }
  }

  def deleteAllConsumptions() = {
    Using.resource(Meta.getConnection()) { connection =>
      Using.resource(connection.createStatement()) { stmt =>
        for (i <- MAX_CONSUMPTION_HISTORY_WEEK to 1 by -1) {
          val routeTableName = PASSENGER_ROUTE_HISTORY_TABLE + "_" + i
          val linkTableName = PASSENGER_LINK_HISTORY_TABLE + "_" + i
          stmt.executeUpdate(s"DROP TABLE IF EXISTS $routeTableName")
          stmt.executeUpdate(s"DROP TABLE IF EXISTS $linkTableName")
        }
        stmt.executeUpdate(s"TRUNCATE TABLE $PASSENGER_ROUTE_HISTORY_TABLE")
        stmt.executeUpdate(s"TRUNCATE TABLE $PASSENGER_LINK_HISTORY_TABLE")
      }
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
    case class LinkRow(linkId: Int, routeId: Int, cost: Int, linkClass: String, inverted: Boolean)

    Using.resource(Meta.getConnection()) { connection =>
      // Phase 1: load matching routes
      val routeQueryString = s"SELECT route_id, passenger_type, passenger_count, route_cost FROM $PASSENGER_ROUTE_HISTORY_TABLE WHERE home_airport = ? AND destination_airport = ?"
      val (routeConsumptions, routeIdsWithCosts) = Using.resource(connection.prepareStatement(routeQueryString)) { routePreparedStatement =>
        routePreparedStatement.setInt(1, fromAirportId)
        routePreparedStatement.setInt(2, toAirportId)
        Using.resource(routePreparedStatement.executeQuery()) { routeResultSet =>
          val routeConsumptions = new scala.collection.mutable.HashMap[Int, (PassengerType.Value, Int)]()
          val routeIdsWithCosts = scala.collection.mutable.ListBuffer[(Int, Int)]()
          while (routeResultSet.next()) {
            val routeId = routeResultSet.getInt("route_id")
            val passengerType = PassengerType.apply(routeResultSet.getInt("passenger_type"))
            val passengerCount = routeResultSet.getInt("passenger_count")
            routeIdsWithCosts += ((routeId, routeResultSet.getInt("route_cost")))
            routeConsumptions.put(routeId, (passengerType, passengerCount))
          }
          (routeConsumptions, routeIdsWithCosts)
        }
      }

      // Build dynamic IN clause for link query
      if (routeIdsWithCosts.isEmpty) {
        Map.empty
      } else {
        val linkQueryString = s"SELECT * FROM $PASSENGER_LINK_HISTORY_TABLE WHERE route_id IN (${routeIdsWithCosts.map(_ => "?").mkString(",")})"
        val linkRows = Using.resource(connection.prepareStatement(linkQueryString)) { linkPreparedStatement =>
          for (i <- routeIdsWithCosts.indices) {
            linkPreparedStatement.setInt(i + 1, routeIdsWithCosts(i)._1)
          }
          Using.resource(linkPreparedStatement.executeQuery()) { linkResultSet =>
            val rows = new ListBuffer[LinkRow]()
            while (linkResultSet.next()) {
              rows += LinkRow(linkResultSet.getInt("link"), linkResultSet.getInt("route_id"), linkResultSet.getInt("cost"), linkResultSet.getString("link_class"), linkResultSet.getBoolean("inverted"))
            }
            rows.toList
          }
        }

        val linkConsumptionById: Map[Int, LinkConsumptionDetails] = LinkSource.loadLinkConsumptionsByLinksId(linkRows.map(_.linkId).distinct.toList).map(entry => (entry.link.id, entry)).toMap

        // Reconstruct routes with link considerations
        val linkConsiderationsByRouteId = scala.collection.mutable.Map[Int, ListBuffer[LinkConsideration]]()
        linkRows.foreach { row =>
          linkConsumptionById.get(row.linkId).foreach { linkConsumption =>
            val linkConsideration = LinkConsideration.getExplicit(linkConsumption.link, row.cost, LinkClass.fromCode(row.linkClass), row.inverted)
            val existingConsiderationsForThisRoute = linkConsiderationsByRouteId.getOrElseUpdate(row.routeId, ListBuffer[LinkConsideration]())
            existingConsiderationsForThisRoute += linkConsideration
          }
        }

        val routeIdsCostsMap = routeIdsWithCosts.toMap
        val result: Map[Route, (PassengerType.Value, Int)] = linkConsiderationsByRouteId.view.map {
          case (routeId: Int, considerations: ListBuffer[LinkConsideration]) =>
            val sortedConsiderations = sortLinks(considerations.toList, fromAirportId)
            (Route(sortedConsiderations, routeIdsCostsMap(routeId), List.empty, routeId), routeConsumptions(routeId))
        }.toMap

        println(s"Loaded ${result.size} routes for airport pair ${fromAirportId} and ${toAirportId})")

        result
      }
    }
  }

  def sortLinks(links: List[LinkConsideration], fromAirportId: Int): List[LinkConsideration] = {
    var currentAirportId = fromAirportId
    val sortedLinks = ListBuffer[LinkConsideration]()
    val remainingLinks = scala.collection.mutable.ListBuffer(links: _*)

    while (remainingLinks.nonEmpty) {
      remainingLinks.find(_.from.id == currentAirportId) match {
        case Some(nextLink) =>
          sortedLinks += nextLink
          remainingLinks -= nextLink
          currentAirportId = nextLink.to.id
        case None =>
          // Cannot find next link.
          // Maybe the start airport was wrong?
          // Or the route is disjoint.
          return links // Fallback to unsorted
      }
    }
    sortedLinks.toList
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
        Using.resource(Meta.getConnection()) { connection =>
          if (Meta.isTableExist(connection, linkTableName) && Meta.isTableExist(connection, routeTableName)) {
            // Phase 1: find all route IDs that contain this link
            val relatedRouteIds = Using.resource(connection.prepareStatement(s"SELECT DISTINCT route_id FROM $linkTableName WHERE link = ?")) { linkPreparedStatement =>
              linkPreparedStatement.setInt(1, linkId)
              Using.resource(linkPreparedStatement.executeQuery()) { linkResultSet =>
                val ids = new ListBuffer[Int]()
                while (linkResultSet.next()) {
                  ids += linkResultSet.getInt("route_id")
                }
                ids.toList
              }
            }

            if (relatedRouteIds.isEmpty) {
              Map.empty
            } else {
              val inClause = relatedRouteIds.map(_ => "?").mkString(",")

              // Phase 2: get route-level data
              val (routeConsumptions, routeCosts) = Using.resource(connection.prepareStatement(s"SELECT route_id, passenger_type, passenger_count, route_cost FROM $routeTableName WHERE route_id IN ($inClause)")) { routePreparedStatement =>
                for (i <- relatedRouteIds.indices) {
                  routePreparedStatement.setInt(i + 1, relatedRouteIds(i))
                }
                Using.resource(routePreparedStatement.executeQuery()) { routeResultSet =>
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
                  (routeConsumptions, routeCosts)
                }
              }

              // Phase 3: collect all related link IDs
              val relatedLinkIds = Using.resource(connection.prepareStatement(s"SELECT route_id, link FROM $linkTableName WHERE route_id IN ($inClause)")) { allLinksPreparedStatement =>
                for (i <- 0 until relatedRouteIds.size) {
                  allLinksPreparedStatement.setInt(i + 1, relatedRouteIds(i))
                }
                Using.resource(allLinksPreparedStatement.executeQuery()) { allLinksResultSet =>
                  val ids = scala.collection.mutable.HashSet[Int]()
                  while (allLinksResultSet.next()) {
                    ids += allLinksResultSet.getInt("link")
                  }
                  ids.toList
                }
              }
              val linkMap = LinkSource.loadLinksByIds(relatedLinkIds).map(link => (link.id, link)).toMap

              // Phase 4: reconstruct link considerations per route
              val linkConsiderationsByRouteId = Using.resource(connection.prepareStatement(s"SELECT * FROM $linkTableName WHERE route_id IN ($inClause)")) { linkDataPreparedStatement =>
                for (i <- 0 until relatedRouteIds.size) {
                  linkDataPreparedStatement.setInt(i + 1, relatedRouteIds(i))
                }
                Using.resource(linkDataPreparedStatement.executeQuery()) { linkDataResultSet =>
                  val considerationsByRouteId = scala.collection.mutable.Map[Int, ListBuffer[LinkConsideration]]()
                  while (linkDataResultSet.next()) {
                    val routeId = linkDataResultSet.getInt("route_id")
                    val relatedLinkId = linkDataResultSet.getInt("link")
                    val relatedLink = linkMap.getOrElse(relatedLinkId, Link.fromId(relatedLinkId))
                    val linkConsideration = LinkConsideration.getExplicit(relatedLink, linkDataResultSet.getDouble("cost"), LinkClass.fromCode(linkDataResultSet.getString("link_class")), linkDataResultSet.getBoolean("inverted"))
                    considerationsByRouteId.getOrElseUpdate(routeId, ListBuffer[LinkConsideration]()) += linkConsideration
                  }
                  considerationsByRouteId
                }
              }

              val result = linkConsiderationsByRouteId.map {
                case (routeId, considerations) => (Route(considerations.toList, routeCosts(routeId), List.empty, routeId), routeConsumptions(routeId))
              }.toMap

              println("Loaded " + result.size + " routes related to link " + link)

              result
            }
          } else {
            Map.empty
          }
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
        Using.resource(Meta.getConnection()) { connection =>
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
          Using.resource(connection.prepareStatement(queryString)) { preparedStatement =>
            preparedStatement.setInt(1, linkId)
            Using.resource(preparedStatement.executeQuery()) { resultSet =>
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
            }
          }
        }
      case None => List.empty
    }
  }
}
