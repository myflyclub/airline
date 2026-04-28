package com.patson.data
import java.sql.{Connection, Statement, Types}
import java.util.{Calendar, Date}
import com.patson.data.Constants._
import com.patson.data.LinkSource.DetailType
import com.patson.data.UserSource.dateFormat
import com.patson.model._
import com.patson.model.airplane._
import com.patson.model.history.LinkChange
import com.patson.util.{AirlineCache, AirplaneModelCache, AirportCache}

import scala.collection.mutable
import scala.collection.mutable.{HashMap, HashSet, ListBuffer, Set}
import scala.util.Using
 


object LinkSource {
  val FULL_LOAD = Map(DetailType.AIRLINE -> true, DetailType.AIRPORT -> true, DetailType.AIRPLANE -> true)
  val SIMPLE_LOAD = Map(DetailType.AIRLINE -> false, DetailType.AIRPORT -> false, DetailType.AIRPLANE -> false)
  val ID_LOAD : Map[DetailType.Type, Boolean] = Map.empty
  
  private[this]val BASE_QUERY = "SELECT * FROM " + LINK_TABLE
  
  def loadLinksByCriteria(criteria : List[(String, Any)], loadDetails : Map[DetailType.Value, Boolean] = SIMPLE_LOAD) = {
    var queryString = BASE_QUERY 
      
    if (!criteria.isEmpty) {
      queryString += " WHERE "
      for (i <- 0 until criteria.size - 1) {
        queryString += criteria(i)._1 + " = ? AND "
      }
      queryString += criteria.last._1 + " = ?"
    }
    
    loadLinksByQueryString(queryString, criteria.map(_._2), loadDetails)
  }

  def loadFlightLinksByCriteria(criteria : List[(String, Any)], loadDetails : Map[DetailType.Value, Boolean] = SIMPLE_LOAD) = {
    var queryString = BASE_QUERY

    queryString += " WHERE "
    for (i <- 0 until criteria.size) {
      queryString += criteria(i)._1 + " = ? AND "
    }
    queryString += "transport_type = " + TransportType.FLIGHT.id

    loadLinksByQueryString(queryString, criteria.map(_._2), loadDetails).map(_.asInstanceOf[Link])
  }
  
  def loadLinksByIds(ids : List[Int], loadDetails : Map[DetailType.Value, Boolean] = SIMPLE_LOAD) = {
    if (ids.isEmpty) {
      List.empty
    } else {
      val queryString = new StringBuilder(BASE_QUERY + " where id IN (");
      for (i <- 0 until ids.size - 1) {
            queryString.append("?,")
      }
      
      queryString.append("?)")
      loadLinksByQueryString(queryString.toString(), ids, loadDetails)
    }
  }
  
  def loadLinksByQueryString(queryString : String, parameters : List[Any], loadDetails : Map[DetailType.Value, Boolean] = SIMPLE_LOAD) = {
    val connection = Meta.getConnection()
    
    try {  
      val preparedStatement = connection.prepareStatement(queryString)
      
      for (i <- 0 until parameters.size) {
        preparedStatement.setObject(i + 1, parameters(i))
      }
      
      val resultSet = preparedStatement.executeQuery()
      
      val links = new ListBuffer[Transport]()
      
      case class LinkRow(id: Int, fromAirportId: Int, toAirportId: Int, airlineId: Int, transportType: Int,
        priceEconomy: Int, priceBusiness: Int, priceFirst: Int, distance: Int,
        capacityEconomy: Int, capacityBusiness: Int, capacityFirst: Int,
        quality: Int, duration: Int, frequency: Int, flightNumber: Int, airplaneModel: Int)
      val rows = new ListBuffer[LinkRow]()
      val linkIds : Set[Int] = new HashSet[Int]
      val airportIds : Set[Int] = new HashSet[Int]

      while (resultSet.next()) {
        val id = resultSet.getInt("id")
        linkIds += id
        airportIds += resultSet.getInt("from_airport")
        airportIds += resultSet.getInt("to_airport")
        rows += LinkRow(id, resultSet.getInt("from_airport"), resultSet.getInt("to_airport"), resultSet.getInt("airline"),
          resultSet.getInt("transport_type"),
          resultSet.getInt("price_economy"), resultSet.getInt("price_business"), resultSet.getInt("price_first"),
          resultSet.getInt("distance"),
          resultSet.getInt("capacity_economy"), resultSet.getInt("capacity_business"), resultSet.getInt("capacity_first"),
          resultSet.getInt("quality"), resultSet.getInt("duration"), resultSet.getInt("frequency"),
          resultSet.getInt("flight_number"), resultSet.getInt("airplane_model"))
      }

      resultSet.close()
      preparedStatement.close()

      val assignedAirplaneCache : Map[Int, Map[Airplane, LinkAssignment]] = loadDetails.get(DetailType.AIRPLANE) match {
        case Some(fullLoad) => loadAssignedAirplanesByLinks(connection, linkIds.toList)
        case None => Map.empty
      }

      val airportCache : Map[Int, Airport] = loadDetails.get(DetailType.AIRPORT) match {
        case Some(fullLoad) => AirportCache.getAirports(airportIds.toList, fullLoad)
        case None => airportIds.map(id => (id, Airport.fromId(id))).toMap
      }

      rows.foreach { row =>
        val fromAirport = airportCache.get(row.fromAirportId) //Do not use AirportCache as fullLoad will be slow
        val toAirport = airportCache.get(row.toAirportId) //Do not use AirportCache as fullLoad will be slow
        val airline = loadDetails.get(DetailType.AIRLINE) match {
          case Some(fullLoad) => AirlineCache.getAirline(row.airlineId, fullLoad).orElse(Some(Airline.fromId(row.airlineId)))
          case None => Some(Airline.fromId(row.airlineId))
        }

        if (fromAirport.isDefined && toAirport.isDefined && airline.isDefined) {
          val transportType = TransportType(row.transportType)
          val link = {
            import TransportType._
            transportType match {
              case FLIGHT =>
                Link(
                  fromAirport.get,
                  toAirport.get,
                  airline.get,
                  LinkClassValues.getInstance(row.priceEconomy, row.priceBusiness, row.priceFirst),
                  row.distance,
                  LinkClassValues.getInstance(row.capacityEconomy, row.capacityBusiness, row.capacityFirst),
                  row.quality,
                  row.duration,
                  row.frequency,
                  row.flightNumber)
              case GENERIC_TRANSIT =>
                GenericTransit(
                  fromAirport.get,
                  toAirport.get,
                  row.distance,
                  LinkClassValues.getInstance(row.capacityEconomy, row.capacityBusiness, row.capacityFirst),
                  row.duration
                )
            }
          }
          link.id = row.id

          if (link.isInstanceOf[Link]) {
            assignedAirplaneCache.get(link.id).foreach { airplaneAssignments =>
              link.asInstanceOf[Link].setAssignedAirplanes(airplaneAssignments)
            }
            if (assignedAirplaneCache.isEmpty) {
              AirplaneModelCache.getModel(row.airplaneModel).foreach {
                model => link.asInstanceOf[Link].setAssignedModel(model)
              }
            }
          }

          links += link
        } else {
          println("Failed loading link [" + row.id + "] as some details cannot be loaded " + fromAirport + toAirport + airline)
        }
      }
      links.toList
    } finally {
      connection.close()
    }
  }

  /**
    * Do not put this as a part of the Link instance as this field is not really used most of the time
    * @param linkIds
    * @return
    */
  def loadLinkLastUpdates(linkIds : List[Int]) : Map[Int, Calendar] = {
    if (linkIds.isEmpty) {
      Map.empty
    } else {
      val queryString = new StringBuilder(BASE_QUERY + " where id IN (");
      for (i <- 0 until linkIds.size - 1) {
        queryString.append("?,")
      }

      queryString.append("?)")

      val connection = Meta.getConnection()
      try {
        val preparedStatement = connection.prepareStatement(queryString.toString())

        for (i <- 0 until linkIds.size) {
          preparedStatement.setInt(i + 1, linkIds(i))
        }

        val resultSet = preparedStatement.executeQuery()

        val lastUpdatesByLinkId = HashMap[Int, Calendar]()
        while (resultSet.next()) {
          val lastUpdate = Calendar.getInstance()
          lastUpdate.setTime(dateFormat.get().parse(resultSet.getString("last_update")))

          lastUpdatesByLinkId.put(resultSet.getInt("id"), lastUpdate)
        }
        resultSet.close()
        preparedStatement.close()
        lastUpdatesByLinkId.toMap
      } finally {
        connection.close()
      }
    }
  }

  /**
   * ToDo low: This can probably be removed and instead just have MySQL choose the next flight_number when the link is actually inserted
   */
  def loadFlightNumbers(airlineId : Int) : List[Int] = {
    val connection = Meta.getConnection()
    
    try {  
      val preparedStatement = connection.prepareStatement("SELECT flight_number FROM " + LINK_TABLE + " WHERE airline = ?")
      
      preparedStatement.setInt(1, airlineId)
      
      val resultSet = preparedStatement.executeQuery()
      
      val flightNumbers = ListBuffer[Int]() 
      while (resultSet.next()) {
        flightNumbers.append(resultSet.getInt("flight_number"))
      } 
      resultSet.close()
      preparedStatement.close()
      flightNumbers.toList
    } finally {
      connection.close()
    }
  }
  
  def loadAssignedAirplanesByLinks(connection : Connection, linkIds : List[Int]) : Map[Int, Map[Airplane, LinkAssignment]] = {
    if (linkIds.isEmpty) {
      Map.empty
    } else {
      val queryString = new StringBuilder("SELECT link, airplane, frequency, flight_minutes FROM " + LINK_ASSIGNMENT_TABLE + " WHERE link IN (")
      for (i <- 0 until linkIds.size - 1) {
            queryString.append("?,")
      }

      queryString.append("?)")

      case class AssignmentRow(link: Int, airplane: Int, frequency: Int, flightMinutes: Int)
      val rows = Using.resource(connection.prepareStatement(queryString.toString)) { linkAssignmentStatement =>
        for (i <- 0 until linkIds.size) {
          linkAssignmentStatement.setInt(i + 1, linkIds(i))
        }
        Using.resource(linkAssignmentStatement.executeQuery()) { assignmentResultSet =>
          val rows = new ListBuffer[AssignmentRow]()
          while (assignmentResultSet.next()) {
            rows += AssignmentRow(assignmentResultSet.getInt("link"), assignmentResultSet.getInt("airplane"), assignmentResultSet.getInt("frequency"), assignmentResultSet.getInt("flight_minutes"))
          }
          rows.toList
        }
      }

      val airplaneCache = AirplaneSource.loadAirplanesByIds(rows.map(_.airplane).distinct.toList).map { airplane => (airplane.id, airplane) }.toMap

      val assignments = new HashMap[Int, HashMap[Airplane, LinkAssignment]]()
      rows.foreach { row =>
        airplaneCache.get(row.airplane).foreach { airplane =>
          val airplanesForThisLink = assignments.getOrElseUpdate(row.link, new HashMap[Airplane, LinkAssignment]);
          airplanesForThisLink.put(airplane, LinkAssignment(row.frequency, row.flightMinutes))
        }
      }

      linkIds.foreach { linkId => //fill the link id with no airplane assigned with empty map
        if (!assignments.contains(linkId)) {
          assignments.put(linkId, HashMap.empty)
        }
      }

      assignments.toList.map {
        case (linkId, mutableMap) => (linkId, mutableMap.toMap)
      }.toMap
    }
  }
  
  def loadFlightLinkById(linkId : Int, loadDetails : Map[DetailType.Value, Boolean] = FULL_LOAD) : Option[Link] = {
    val result = loadFlightLinksByCriteria(List(("id", linkId)), loadDetails)
    if (result.isEmpty) {
      None
    } else {
      Some(result(0))
    }
  }
  def loadFlightLinkByAirportsAndAirline(fromAirportId : Int, toAirportId : Int, airlineId : Int, loadDetails : Map[DetailType.Value, Boolean] = FULL_LOAD) : Option[Link] = {
    val result = loadFlightLinksByCriteria(List(("from_airport", fromAirportId), ("to_airport", toAirportId), ("airline", airlineId)), loadDetails)
    if (result.isEmpty) {
      None
    } else {
      Some(result(0))
    }
  }
  def loadFlightLinksByAirports(fromAirportId : Int, toAirportId : Int, loadDetails : Map[DetailType.Value, Boolean] = SIMPLE_LOAD) : List[Link] = {
    loadFlightLinksByCriteria(List(("from_airport", fromAirportId), ("to_airport", toAirportId)), loadDetails)
  }

  def loadAllLinks(loadDetails : Map[DetailType.Value, Boolean] = SIMPLE_LOAD) = {
      loadLinksByCriteria(List.empty, loadDetails)
  }

  def loadAllFlightLinks(loadDetails : Map[DetailType.Value, Boolean] = SIMPLE_LOAD) = {
    loadFlightLinksByCriteria(List.empty, loadDetails)
  }

  def loadFlightLinksByAirlineId(airlineId : Int, loadDetails : Map[DetailType.Value, Boolean] = SIMPLE_LOAD) = {
    loadFlightLinksByCriteria(List(("airline", airlineId)), loadDetails)
  }

  def loadFlightLinksByFromAirport(fromAirportId : Int, loadDetails : Map[DetailType.Value, Boolean] = SIMPLE_LOAD) = {
    loadFlightLinksByCriteria(List(("from_airport", fromAirportId)), loadDetails)
  }

  def loadFlightLinksByFromAirportAndAirlineId(fromAirportId : Int, airlineId: Int, loadDetails : Map[DetailType.Value, Boolean] = SIMPLE_LOAD) = {
    loadFlightLinksByCriteria(List(("from_airport", fromAirportId), ("airline", airlineId)), loadDetails)
  }

  def loadFlightLinksByToAirportAndAirlineId(toAirportId : Int, airlineId: Int, loadDetails : Map[DetailType.Value, Boolean] = SIMPLE_LOAD) = {
    loadFlightLinksByCriteria(List(("to_airport", toAirportId), ("airline", airlineId)), loadDetails)
  }

  def loadFlightLinksByToAirport(toAirportId : Int, loadDetails : Map[DetailType.Value, Boolean] = SIMPLE_LOAD) = {
    loadFlightLinksByCriteria(List(("to_airport", toAirportId)), loadDetails)
  }

//  def saveLink2(link : Link) : Option[Link] = {
//       case Some(generatedId) =>
//         link.id = generatedId
//         Some(link)
//       case None =>
//         None
//     }
//  }

    //[T <: RequestType](t: T)
  def saveLink[T <: Transport](link : T) : Option[T] = {
    val (fromAirportId : Int, toAirportId : Int, airlineId : Int, price : LinkClassValues, distance : Int, capacity : LinkClassValues, rawQuality : Int,  duration : Int, frequency : Int, flightNumber : Int, assignedAirplanes : Map[Airplane, LinkAssignment]) = {
      link.transportType match {
        case TransportType.FLIGHT =>
          val flightLink = link.asInstanceOf[Link]
          (flightLink.from.id, flightLink.to.id, flightLink.airline.id, flightLink.price, flightLink.distance, flightLink.capacity, flightLink.rawQuality, flightLink.duration, flightLink.frequency, flightLink.flightNumber, flightLink.getAssignedAirplanes)
        case TransportType.GENERIC_TRANSIT =>
          val genericTransit = link.asInstanceOf[GenericTransit]
          (genericTransit.from.id, genericTransit.to.id, 0, genericTransit.price, genericTransit.distance, genericTransit.capacity, GenericTransit.QUALITY, genericTransit.duration, genericTransit.frequency, 0, Map.empty)
      }


    }
    //open the hsqldb
    val connection = Meta.getConnection()
    val preparedStatement = connection.prepareStatement("INSERT INTO " + LINK_TABLE + "(from_airport, to_airport, airline, price_economy, price_business, price_first, distance, capacity_economy, capacity_business, capacity_first, quality, duration, frequency, flight_number, airplane_model, from_country, to_country, transport_type) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)

    try {
      preparedStatement.setInt(1, fromAirportId)
      preparedStatement.setInt(2, toAirportId)
      preparedStatement.setInt(3, airlineId)
      preparedStatement.setInt(4, price(ECONOMY))
      preparedStatement.setInt(5, price(BUSINESS))
      preparedStatement.setInt(6, price(FIRST))
      preparedStatement.setDouble(7, distance)
      preparedStatement.setInt(8, capacity(ECONOMY))
      preparedStatement.setInt(9, capacity(BUSINESS))
      preparedStatement.setInt(10, capacity(FIRST))
      preparedStatement.setInt(11, rawQuality)
      preparedStatement.setInt(12, duration)
      preparedStatement.setInt(13, frequency)
      preparedStatement.setInt(14, flightNumber)
      if (link.isInstanceOf[Link]) {
        preparedStatement.setInt(15, link.asInstanceOf[Link].getAssignedModel().map(_.id).getOrElse(0))
      } else {
        preparedStatement.setNull(15, Types.INTEGER)
      }
      preparedStatement.setString(16, link.from.countryCode)
      preparedStatement.setString(17, link.to.countryCode)
      preparedStatement.setInt(18, link.transportType.id)

      val updateCount = preparedStatement.executeUpdate()
      //println("Saved " + updateCount + " link!")

      if (updateCount > 0) {
        val generatedKeys = preparedStatement.getGeneratedKeys
        if (generatedKeys.next()) {
          val generatedId = generatedKeys.getInt(1)
        //  println("Id is " + generatedId)
          //try to save assigned airplanes if any
          updateAssignedPlanes(generatedId, assignedAirplanes)
          link.id = generatedId

          if (link.isInstanceOf[Link]) {
            ChangeHistorySource.saveLinkChange(buildChangeHistory(None, Some(link.asInstanceOf[Link])))
          }

          return Some(link)
        }
      }
      None
    } finally {
      preparedStatement.close()
      connection.close()
    }
  }

  def saveLinks[T <: Transport](links : List[T]) : Int = {
     //open the hsqldb
    val connection = Meta.getConnection()
    val preparedStatement = connection.prepareStatement("INSERT INTO " + LINK_TABLE + "(from_airport, to_airport, airline, price_economy, price_business, price_first, distance, capacity_economy, capacity_business, capacity_first, quality, duration, frequency, flight_number, airplane_model, from_country, to_country, transport_type) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)
    var updateCount = 0
    val changeHistoryEntries = ListBuffer[LinkChange]()
    connection.setAutoCommit(false)
    try {
      links.foreach { link =>
        preparedStatement.setInt(1, link.from.id)
        preparedStatement.setInt(2, link.to.id)
        preparedStatement.setInt(3, link.airline.id)
        preparedStatement.setInt(4, link.price(ECONOMY))
        preparedStatement.setInt(5, link.price(BUSINESS))
        preparedStatement.setInt(6, link.price(FIRST))
        preparedStatement.setDouble(7, link.distance)
        preparedStatement.setInt(8, link.capacity(ECONOMY))
        preparedStatement.setInt(9, link.capacity(BUSINESS))
        preparedStatement.setInt(10, link.capacity(FIRST))
        if (link.isInstanceOf[Link]) {
          preparedStatement.setInt(11, link.asInstanceOf[Link].rawQuality)
        } else {
          preparedStatement.setNull(11, Types.INTEGER)
        }
        preparedStatement.setInt(12, link.duration)
        preparedStatement.setInt(13, link.frequency)
        if (link.isInstanceOf[Link]) {
          preparedStatement.setInt(14, link.asInstanceOf[Link].flightNumber)
          preparedStatement.setInt(15, link.asInstanceOf[Link].getAssignedModel().map(_.id).getOrElse(0))
        } else {
          preparedStatement.setNull(14, Types.INTEGER)
          preparedStatement.setNull(15, Types.INTEGER)
        }
        preparedStatement.setString(16, link.from.countryCode)
        preparedStatement.setString(17, link.to.countryCode)
        preparedStatement.setInt(18, link.transportType.id)


        updateCount += preparedStatement.executeUpdate()
        //println("Saved " + updateCount + " link!")

        if (updateCount > 0) {
          val generatedKeys = preparedStatement.getGeneratedKeys
          if (generatedKeys.next()) {
            val generatedId = generatedKeys.getInt(1)
            link.id = generatedId
            if (link.isInstanceOf[Link]) {
              changeHistoryEntries.append(buildChangeHistory(None, Some(link.asInstanceOf[Link])))
            }
          }
        }
      }
      connection.commit()
    } finally {
      preparedStatement.close()
      connection.close()
    }

    links.filter(_.transportType == TransportType.FLIGHT).foreach { link =>
      updateAssignedPlanes(link.id, link.asInstanceOf[Link].getAssignedAirplanes())
    }

    ChangeHistorySource.saveLinkChanges(changeHistoryEntries.toList)
    updateCount
  }

  def updateLink(link : Transport) = {
    val existingLink = loadFlightLinkById(link.id, ID_LOAD) //use ID load to get the simple freq/capacity of previous state (even tho some airplanes might have just arrived)
    Using.resource(Meta.getConnection()) { connection =>
      Using.resource(connection.prepareStatement("UPDATE " + LINK_TABLE + " SET price_economy = ?, price_business = ?, price_first = ?, capacity_economy = ?, capacity_business = ?, capacity_first = ?, quality = ?, duration = ?, frequency = ?, flight_number = ?, airplane_model = ?, last_update = ? WHERE id = ?")) { preparedStatement =>
        preparedStatement.setInt(1, link.price(ECONOMY))
        preparedStatement.setInt(2, link.price(BUSINESS))
        preparedStatement.setInt(3, link.price(FIRST))
        preparedStatement.setInt(4, link.capacity(ECONOMY))
        preparedStatement.setInt(5, link.capacity(BUSINESS))
        preparedStatement.setInt(6, link.capacity(FIRST))
        if (link.isInstanceOf[Link]) {
          preparedStatement.setInt(7, link.asInstanceOf[Link].rawQuality)
        } else {
          preparedStatement.setNull(7, Types.INTEGER)
        }
        preparedStatement.setInt(8, link.duration)
        preparedStatement.setInt(9, link.frequency)
        if (link.isInstanceOf[Link]) {
          preparedStatement.setInt(10, link.asInstanceOf[Link].flightNumber)
          preparedStatement.setInt(11, link.asInstanceOf[Link].getAssignedModel().map(_.id).getOrElse(0))
        } else {
          preparedStatement.setNull(10, Types.INTEGER)
          preparedStatement.setNull(11, Types.INTEGER)
        }
        preparedStatement.setTimestamp(12, new java.sql.Timestamp(new Date().getTime()))
        preparedStatement.setInt(13, link.id)

        val updateCount = preparedStatement.executeUpdate()
        println("Updated " + updateCount + " link!")

        if (link.isInstanceOf[Link]) {
          if (hasChange(existingLink.get, link)) {
            ChangeHistorySource.saveLinkChange(buildChangeHistory(existingLink.map(_.asInstanceOf[Link]), Some(link.asInstanceOf[Link])))
          }
        }

        updateCount
      }
    }
  }

  def updateLinks[T <: Transport](links : List[T]) = {
    val existingLinks = loadLinksByIds(links.map(_.id)).map(link => (link.id, link)).toMap
    val changeEntries = ListBuffer[LinkChange]()

    Using.resource(Meta.getConnection()) { connection =>
      connection.setAutoCommit(false)
      Using.resource(connection.prepareStatement("UPDATE " + LINK_TABLE + " SET price_economy = ?, price_business = ?, price_first = ?, capacity_economy = ?, capacity_business = ?, capacity_first = ?, quality = ?, duration = ?, frequency = ?, flight_number = ?, airplane_model = ?, last_update = ? WHERE id = ?")) { preparedStatement =>
        links.foreach { link =>
          preparedStatement.setInt(1, link.price(ECONOMY))
          preparedStatement.setInt(2, link.price(BUSINESS))
          preparedStatement.setInt(3, link.price(FIRST))
          preparedStatement.setInt(4, link.capacity(ECONOMY))
          preparedStatement.setInt(5, link.capacity(BUSINESS))
          preparedStatement.setInt(6, link.capacity(FIRST))
          if (link.isInstanceOf[Link]) {
            preparedStatement.setInt(7, link.asInstanceOf[Link].rawQuality)
          } else {
            preparedStatement.setNull(7, Types.INTEGER)
          }
          preparedStatement.setInt(8, link.duration)
          preparedStatement.setInt(9, link.frequency)
          if (link.isInstanceOf[Link]) {
            preparedStatement.setInt(10, link.asInstanceOf[Link].flightNumber)
            preparedStatement.setInt(11, link.asInstanceOf[Link].getAssignedModel().map(_.id).getOrElse(0))
          } else {
            preparedStatement.setNull(10, Types.INTEGER)
            preparedStatement.setNull(11, Types.INTEGER)
          }
          preparedStatement.setTimestamp(12, new java.sql.Timestamp(new Date().getTime()))
          preparedStatement.setInt(13, link.id)
          preparedStatement.addBatch()

          if (link.transportType == TransportType.FLIGHT) {
            val flightLink = link.asInstanceOf[Link]
            if (hasChange(existingLinks.get(link.id).get.asInstanceOf[Link], flightLink)) {
              changeEntries.append(buildChangeHistory(existingLinks.get(flightLink.id).map(_.asInstanceOf[Link]), Some(flightLink)))
            }
          }
        }

        preparedStatement.executeBatch()
        ChangeHistorySource.saveLinkChanges(changeEntries.toList)
      }
      connection.commit()
    }
  }

  def hasChange(existingLink : Transport, newLink : Transport) : Boolean = {
    newLink.capacity.economyVal != existingLink.capacity.economyVal ||
    newLink.capacity.businessVal != existingLink.capacity.businessVal ||
    newLink.capacity.firstVal != existingLink.capacity.firstVal ||
    newLink.price.economyVal != existingLink.price.economyVal ||
    newLink.price.businessVal != existingLink.price.businessVal ||
    newLink.price.firstVal != existingLink.price.firstVal
  }
  
  
  def updateAssignedPlanes(linkId : Int, assignedAirplanes : Map[Airplane, LinkAssignment]) = {
    Using.resource(Meta.getConnection()) { connection =>
      connection.setAutoCommit(false)
      //remove all the existing ones assigned to this link
      Using.resource(connection.prepareStatement("DELETE FROM " + LINK_ASSIGNMENT_TABLE + " WHERE link = ?")) { removeStatement =>
        removeStatement.setInt(1, linkId)
        removeStatement.executeUpdate()
      }
      assignedAirplanes.foreach { case(airplane, assignment) =>
        if (assignment.frequency > 0) {
          Using.resource(connection.prepareStatement("INSERT INTO " + LINK_ASSIGNMENT_TABLE + "(link, airplane, frequency, flight_minutes) VALUES(?,?,?,?)")) { insertStatement =>
            insertStatement.setInt(1, linkId)
            insertStatement.setInt(2, airplane.id)
            insertStatement.setInt(3, assignment.frequency)
            insertStatement.setInt(4, assignment.flightMinutes)
            insertStatement.executeUpdate()
          }
        }
      }
      connection.commit()
    }
  }

  def updateAssignedPlanes(assignedAirplanesByLinkId : Map[Int, Map[Airplane, LinkAssignment]]) = {
    Using.resource(Meta.getConnection()) { connection =>
      connection.setAutoCommit(false)
      Using.resource(connection.prepareStatement("DELETE FROM " + LINK_ASSIGNMENT_TABLE + " WHERE link = ?")) { removeStatement =>
        Using.resource(connection.prepareStatement("INSERT INTO " + LINK_ASSIGNMENT_TABLE + "(link, airplane, frequency, flight_minutes) VALUES(?,?,?,?)")) { insertStatement =>
          assignedAirplanesByLinkId.foreach {
            case (linkId, assignedAirplanes) =>
              //remove all the existing ones assigned to this link
              removeStatement.setInt(1, linkId)
              removeStatement.addBatch()
              assignedAirplanes.foreach { case(airplane, assignment) =>
                if (assignment.frequency > 0) {
                  insertStatement.setInt(1, linkId)
                  insertStatement.setInt(2, airplane.id)
                  insertStatement.setInt(3, assignment.frequency)
                  insertStatement.setInt(4, assignment.flightMinutes)
                  insertStatement.addBatch()
                }
              }
          }
          removeStatement.executeBatch()
          insertStatement.executeBatch()
        }
      }
      connection.commit()
    }
  }

  def deleteLink(linkId : Int) = {
    deleteLinksByCriteria(List(("id", linkId)))
  }
  
  def deleteAllLinks() = {
    deleteLinksByCriteria(List.empty)
  }

  def deleteLinksByAirlineId(airlineId : Int) = {
    deleteLinksByCriteria(List(("airline", airlineId)))
  }
  
  def deleteLinksByCriteria(criteria : List[(String, Any)]) = {
      //open the hsqldb
    val connection = Meta.getConnection()
    try {
      val purgingLinks = loadLinksByCriteria(criteria, Map(DetailType.AIRLINE -> true, DetailType.AIRPORT -> false, DetailType.AIRPLANE -> false)).map(link => (link.id, link)).toMap

      var queryString = "DELETE FROM link "
      
      if (!criteria.isEmpty) {
        queryString += " WHERE "
        for (i <- 0 until criteria.size - 1) {
          queryString += criteria(i)._1 + " = ? AND "
        }
        queryString += criteria.last._1 + " = ?"
      }
      
      val preparedStatement = connection.prepareStatement(queryString)
      
      for (i <- 0 until criteria.size) {
        preparedStatement.setObject(i + 1, criteria(i)._2)
      }

      val deletedCount = preparedStatement.executeUpdate()
      
      preparedStatement.close()

      println("Deleted " + deletedCount + " link records")
      //purge link-cancellation notifications
      val purgingLinkIds = purgingLinks.keys.toSet
      val purgingNotifications = NotificationSource.loadAllByCategory(NotificationCategory.LINK_CANCELLATION)
        .filter(n => n.targetId.flatMap(_.toIntOption).exists(purgingLinkIds.contains))
      NotificationSource.deleteNotifications(purgingNotifications)

      println("Purged " + purgingNotifications.size + " link-cancellation notification records")

      //save changes
      val changeEntries = ListBuffer[LinkChange]()
      purgingLinks.foreach {
        case (linkId, link) =>
          if (link.isInstanceOf[Link]) {
            changeEntries.append(buildChangeHistory(Some(link.asInstanceOf[Link]), None))
          }
      }

      ChangeHistorySource.saveLinkChanges(changeEntries.toList)

      deletedCount
    } finally {
      connection.close()
    }
  }

  def buildChangeHistory(existingLinkOption : Option[Link], newLinkOption : Option[Link]) : LinkChange = {
    val existingPrice = existingLinkOption match { //for new link, the price is not consider as delta
      case Some(existingLink) => existingLink.price
      case None => newLinkOption.map(_.price).getOrElse(LinkClassValues.getInstance())
    }
    val existingCapacity = existingLinkOption match {
      case Some(existingLink) => existingLink.capacity
      case None => LinkClassValues.getInstance()
    }

    val newPrice = newLinkOption match { //for link removal, the price is not consider as delta
      case Some(newLink) => newLink.price
      case None => existingLinkOption.map(_.price).getOrElse(LinkClassValues.getInstance())
    }

    val newCapacity = newLinkOption match {
      case Some(newLink) => newLink.capacity
      case None => LinkClassValues.getInstance()
    }


    val link = existingLinkOption.getOrElse(newLinkOption.get)

    val fromAirport = AirportCache.getAirport(link.from.id).get //in some case it could be just ID, need to reload
    val toAirport =  AirportCache.getAirport(link.to.id).get //in some case it could be just ID, need to reload
    val airline = AirlineCache.getAirline(link.airline.id, true).get

    val entry = LinkChange(
      linkId = link.id,
      price = newPrice,
      priceDelta = newPrice - existingPrice,
      capacity = newCapacity,
      capacityDelta = newCapacity - existingCapacity,
      fromAirport = fromAirport,
      toAirport = toAirport,
      fromCountry = Country.fromCode(fromAirport.countryCode),
      toCountry = Country.fromCode(toAirport.countryCode),
      airline = airline,
      alliance = airline.getAllianceId().map(Alliance.fromId(_)),
      frequency = newLinkOption.map(_.frequency).getOrElse(0),
      flightNumber = link.flightNumber,
      airplaneModel = link.getAssignedModel().getOrElse(Model.fromId(0)),
      rawQuality = link.rawQuality,
      cycle =  CycleSource.loadCycle())

    entry
  }
  
  def saveLinkConsumptions(linkConsumptions: List[LinkConsumptionDetails]) = {
    val (flightConsumptions, transitConsumptions) = linkConsumptions.partition(_.link.transportType == TransportType.FLIGHT)

    Using.resource(Meta.getConnection()) { connection =>
      connection.setAutoCommit(false)
      Using.resource(connection.prepareStatement("REPLACE INTO " + LINK_CONSUMPTION_TABLE + "(link, price_economy, price_business, price_first, capacity_economy, capacity_business, capacity_first, sold_seats_economy, sold_seats_business, sold_seats_first, quality, fuel_cost, fuel_tax, crew_cost, airport_fees, inflight_cost, delay_compensation, maintenance_cost, lounge_cost, depreciation, revenue, profit, minor_delay_count, major_delay_count, cancellation_count, from_airport, to_airport, airline, distance, frequency, duration, flight_number, airplane_model, satisfaction, cycle) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) { preparedStatement =>
        flightConsumptions.foreach { linkConsumption =>
          preparedStatement.setInt(1, linkConsumption.link.id)
          preparedStatement.setInt(2, linkConsumption.link.price(ECONOMY))
          preparedStatement.setInt(3, linkConsumption.link.price(BUSINESS))
          preparedStatement.setInt(4, linkConsumption.link.price(FIRST))
          preparedStatement.setInt(5, linkConsumption.link.capacity(ECONOMY))
          preparedStatement.setInt(6, linkConsumption.link.capacity(BUSINESS))
          preparedStatement.setInt(7, linkConsumption.link.capacity(FIRST))
          preparedStatement.setInt(8, linkConsumption.link.soldSeats(ECONOMY))
          preparedStatement.setInt(9, linkConsumption.link.soldSeats(BUSINESS))
          preparedStatement.setInt(10, linkConsumption.link.soldSeats(FIRST))
          preparedStatement.setInt(11, linkConsumption.link.computedQuality())
          preparedStatement.setInt(12, linkConsumption.fuelCost)
          preparedStatement.setInt(13, linkConsumption.fuelTax)
          preparedStatement.setInt(14, linkConsumption.crewCost)
          preparedStatement.setInt(15, linkConsumption.airportFees)
          preparedStatement.setInt(16, linkConsumption.inflightCost)
          preparedStatement.setInt(17, linkConsumption.delayCompensation)
          preparedStatement.setInt(18, linkConsumption.maintenanceCost)
          preparedStatement.setInt(19, linkConsumption.loungeCost)
          preparedStatement.setInt(20, linkConsumption.depreciation)
          preparedStatement.setInt(21, linkConsumption.revenue)
          preparedStatement.setInt(22, linkConsumption.profit)
          preparedStatement.setInt(23, linkConsumption.link.minorDelayCount)
          preparedStatement.setInt(24, linkConsumption.link.majorDelayCount)
          preparedStatement.setInt(25, linkConsumption.link.cancellationCount)
          preparedStatement.setInt(26, linkConsumption.link.from.id)
          preparedStatement.setInt(27, linkConsumption.link.to.id)
          preparedStatement.setInt(28, linkConsumption.link.airline.id)
          preparedStatement.setInt(29, linkConsumption.link.distance)
          preparedStatement.setInt(30, linkConsumption.link.frequency)
          preparedStatement.setInt(31, linkConsumption.link.duration)
          preparedStatement.setInt(32, linkConsumption.link.asInstanceOf[Link].flightNumber)
          preparedStatement.setInt(33, linkConsumption.link.asInstanceOf[Link].getAssignedModel().map(_.id).getOrElse(0))
          preparedStatement.setDouble(34, linkConsumption.satisfaction)
          preparedStatement.setInt(35, linkConsumption.cycle)
          preparedStatement.addBatch()
        }
        preparedStatement.executeBatch()
      }
      Using.resource(connection.prepareStatement("REPLACE INTO " + TRANSIT_CONSUMPTION_TABLE + "(link, sold_seats_economy, cycle) VALUES(?,?,?)")) { preparedStatement =>
        transitConsumptions.foreach { linkConsumption =>
          preparedStatement.setInt(1, linkConsumption.link.id)
          preparedStatement.setInt(2, linkConsumption.link.soldSeats(ECONOMY))
          preparedStatement.setInt(3, linkConsumption.cycle)
          preparedStatement.addBatch()
        }
        preparedStatement.executeBatch()
      }
      connection.commit()
    }
  }
  def deleteLinkConsumptionsByCycle(cyclesFromLatest : Int) = {
    val connection = Meta.getConnection()
    try {
      val latestCycle = getLatestCycle(connection, LINK_CONSUMPTION_TABLE)

      val deleteFrom = if (latestCycle - cyclesFromLatest < 0) 0 else latestCycle - cyclesFromLatest

      Using.resource(connection.prepareStatement("DELETE FROM " + LINK_CONSUMPTION_TABLE + " WHERE cycle <= ?")) { deleteStatement =>
        deleteStatement.setInt(1, deleteFrom)
        deleteStatement.executeUpdate()
      }
      Using.resource(connection.prepareStatement("DELETE FROM " + TRANSIT_CONSUMPTION_TABLE + " WHERE cycle <= ?")) { deleteStatement =>
        deleteStatement.setInt(1, deleteFrom)
        deleteStatement.executeUpdate()
      }
    } finally {
      connection.close()
    }
  }
  
  def loadLinkConsumptions(cycleCount : Int = 1) = {
    loadLinkConsumptionsByCriteria(List.empty, cycleCount)
  }
  
  def loadLinkConsumptionsByLinkId(linkId : Int, cycleCount : Int = 1) = {
    loadLinkConsumptionsByCriteria(List(("link", linkId)), cycleCount)
  }
  
  def loadLinkConsumptionsByLinksId(linkIds : List[Int], cycleCount : Int = 1) = {
    if (linkIds.isEmpty) {
      List.empty
    } else {
      val placeholders = linkIds.map(_ => "?").mkString(",")
      val flightQuery = s"SELECT * FROM $LINK_CONSUMPTION_TABLE WHERE cycle > ? AND link IN ($placeholders)"
      val flightResults = loadLinkConsumptionsByQuery(flightQuery, linkIds, cycleCount)

      val transitQuery =
        s"SELECT tc.link, tc.sold_seats_economy, tc.cycle, l.from_airport, l.to_airport, l.distance, l.capacity_economy " +
        s"FROM $TRANSIT_CONSUMPTION_TABLE tc JOIN $LINK_TABLE l ON tc.link = l.id WHERE tc.cycle > ? AND tc.link IN ($placeholders)"
      val transitResults = loadTransitConsumptionsByQuery(transitQuery, linkIds, cycleCount)

      flightResults ++ transitResults
    }
  }

  private def getLatestCycle(connection: java.sql.Connection, table: String): Int =
    Using.resource(connection.prepareStatement(s"SELECT MAX(cycle) FROM $table")) { stmt =>
      Using.resource(stmt.executeQuery()) { rs => if (rs.next()) rs.getInt(1) else 0 }
    }

  private def loadTransitConsumptionsByQuery(queryString: String, linkIds: List[Int], cycleCount: Int): List[LinkConsumptionDetails] = {
    Using.resource(Meta.getConnection()) { connection =>
      val latestCycle = getLatestCycle(connection, TRANSIT_CONSUMPTION_TABLE)
      Using.resource(connection.prepareStatement(queryString)) { preparedStatement =>
        preparedStatement.setInt(1, latestCycle - cycleCount)
        for (i <- 0 until linkIds.size) {
          preparedStatement.setObject(i + 2, linkIds(i))
        }
        Using.resource(preparedStatement.executeQuery()) { resultSet =>
          val results = new ListBuffer[LinkConsumptionDetails]()
          while (resultSet.next()) {
            val fromAirport = AirportCache.getAirport(resultSet.getInt("from_airport")).getOrElse(Airport.fromId(resultSet.getInt("from_airport")))
            val toAirport   = AirportCache.getAirport(resultSet.getInt("to_airport")).getOrElse(Airport.fromId(resultSet.getInt("to_airport")))
            val distance    = resultSet.getInt("distance")
            val capacity    = LinkClassValues.getInstance(resultSet.getInt("capacity_economy"))
            val linkId      = resultSet.getInt("link")
            val transit     = GenericTransit(fromAirport, toAirport, distance, capacity, linkId)
            transit.addSoldSeats(LinkClassValues.getInstance(resultSet.getInt("sold_seats_economy")))
            results += LinkConsumptionDetails(link = transit, fuelCost = 0, fuelTax = 0, crewCost = 0, airportFees = 0,
              inflightCost = 0, delayCompensation = 0, maintenanceCost = 0, depreciation = 0, loungeCost = 0,
              revenue = 0, profit = 0, satisfaction = 0, cycle = resultSet.getInt("cycle"))
          }
          results.toList
        }
      }
    }
  }
  
  def loadLinkConsumptionsByAirline(airlineId : Int, cycleCount : Int = 1) = {
    loadLinkConsumptionsByCriteria(List(("airline", airlineId)), cycleCount)
  }
  
   def loadLinkConsumptionsByCriteria(criteria : List[(String, Any)], cycleCount : Int) = {
    var queryString = "SELECT * FROM link_consumption WHERE cycle > ?" 
      
    if (!criteria.isEmpty) {
      queryString += " AND "
      for (i <- 0 until criteria.size - 1) {
        queryString += criteria(i)._1 + " = ? AND "
      }
      queryString += criteria.last._1 + " = ?"
    }
    queryString += " ORDER BY cycle DESC"
    
    loadLinkConsumptionsByQuery(queryString, criteria.map(_._2), cycleCount)
  }
  
  def loadLinkConsumptionsByQuery(queryString: String, parameters : List[Any], cycleCount : Int) = {
    Using.resource(Meta.getConnection()) { connection =>
      val latestCycle = getLatestCycle(connection, LINK_CONSUMPTION_TABLE)

      Using.resource(connection.prepareStatement(queryString)) { preparedStatement =>
        preparedStatement.setInt(1, latestCycle - cycleCount)
        for (i <- 0 until parameters.size) {
          preparedStatement.setObject(i + 2, parameters(i))
        }
        Using.resource(preparedStatement.executeQuery()) { resultSet =>
          val linkConsumptions = new ListBuffer[LinkConsumptionDetails]()
          while (resultSet.next()) {
        val linkId = resultSet.getInt("link")
        //need to update current link with history link data
        val frequency = resultSet.getInt("frequency")
        val price = LinkClassValues.getInstance(resultSet.getInt("price_economy"), resultSet.getInt("price_business"), resultSet.getInt("price_first"))
        val quality = resultSet.getInt("quality")
        val capacity =  LinkClassValues.getInstance(resultSet.getInt("capacity_economy"), resultSet.getInt("capacity_business"),resultSet.getInt("capacity_first"))

        val fromAirport = AirportCache.getAirport(resultSet.getInt("from_airport")).getOrElse(Airport.fromId(resultSet.getInt("from_airport")))
        val toAirport =  AirportCache.getAirport(resultSet.getInt("to_airport")).getOrElse(Airport.fromId(resultSet.getInt("to_airport")))
        val airline = AirlineCache.getAirline(resultSet.getInt("airline")).getOrElse(Airline.fromId(resultSet.getInt("airline")))
        val distance = resultSet.getInt("distance")
        val duration = resultSet.getInt("duration")
        val flightNumber = resultSet.getInt("flight_number")
        val modelId = resultSet.getInt("airplane_model")
        val link = Link(fromAirport, toAirport, airline, price, distance, capacity, 0, duration, frequency, flightNumber, linkId)
        link.setQuality(quality)
        link.setAssignedModel(AirplaneModelCache.getModel(modelId).getOrElse(Model.fromId(modelId)))

        link.addSoldSeats(LinkClassValues.getInstance(resultSet.getInt("sold_seats_economy"), resultSet.getInt("sold_seats_business"), resultSet.getInt("sold_seats_first")))
        link.minorDelayCount = resultSet.getInt("minor_delay_count")
        link.majorDelayCount = resultSet.getInt("major_delay_count")
        link.cancellationCount = resultSet.getInt("cancellation_count")

        if (link.cancellationCount > 0 && link.frequency > 0) {
          link.addCancelledSeats(capacity * link.cancellationCount / frequency)
        }



            linkConsumptions.append(LinkConsumptionDetails(
              link = link,
              fuelCost = resultSet.getInt("fuel_cost"),
              fuelTax = resultSet.getInt("fuel_tax"),
              crewCost = resultSet.getInt("crew_cost"),
              airportFees = resultSet.getInt("airport_fees"),
              inflightCost = resultSet.getInt("inflight_cost"),
              delayCompensation = resultSet.getInt("delay_compensation"),
              maintenanceCost = resultSet.getInt("maintenance_cost"),
              loungeCost = resultSet.getInt("lounge_cost"),
              depreciation = resultSet.getInt("depreciation"),
              revenue = resultSet.getInt("revenue"),
              profit = resultSet.getInt("profit"),
              satisfaction = resultSet.getDouble("satisfaction"),
              cycle = resultSet.getInt("cycle")))
          }
          linkConsumptions.toList
        }
      }
    }
  }

  def saveNegotiationCoolDown(airline : Airline, fromAirport : Airport, toAirport : Airport, expirationCycle : Int) = {
    Using.resource(Meta.getConnection()) { connection =>
      Using.resource(connection.prepareStatement(s"REPLACE INTO $LINK_NEGOTIATION_COOL_DOWN_TABLE (airline, from_airport, to_airport, expiration_cycle) VALUES(?,?,?,?)")) { preparedStatement =>
        preparedStatement.setInt(1, airline.id)
        preparedStatement.setInt(2, fromAirport.id)
        preparedStatement.setInt(3, toAirport.id)
        preparedStatement.setInt(4, expirationCycle)
        preparedStatement.executeUpdate()
      }
    }
  }

  def loadNegotiationCoolDownExpirationCycle(airline : Airline, fromAirport : Airport, toAirport : Airport): Option[Int] = {
    Using.resource(Meta.getConnection()) { connection =>
      Using.resource(connection.prepareStatement(s"SELECT * FROM  $LINK_NEGOTIATION_COOL_DOWN_TABLE WHERE airline = ? AND from_airport = ? AND to_airport = ?")) { preparedStatement =>
        preparedStatement.setInt(1, airline.id)
        preparedStatement.setInt(2, fromAirport.id)
        preparedStatement.setInt(3, toAirport.id)
        Using.resource(preparedStatement.executeQuery()) { resultSet =>
          if (resultSet.next()) {
            Some(resultSet.getInt("expiration_cycle"))
          } else {
            None
          }
        }
      }
    }
  }

  def purgeNegotiationCoolDowns(atOrBeforeCycle : Int) = {
    Using.resource(Meta.getConnection()) { connection =>
      Using.resource(connection.prepareStatement("DELETE FROM " + LINK_NEGOTIATION_COOL_DOWN_TABLE + " where expiration_cycle <= ?")) { preparedStatement =>
        preparedStatement.setInt(1, atOrBeforeCycle)
        preparedStatement.executeUpdate()
      }
    }
  }

  object DetailType extends Enumeration {
    type Type = Value
    val AIRPORT, AIRLINE, AIRPLANE = Value
  }
}