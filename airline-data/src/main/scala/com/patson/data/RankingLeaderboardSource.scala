package com.patson.data

import com.patson.data.Constants._
import com.patson.model._

import scala.util.Using
import scala.collection.mutable.ListBuffer

object RankingLeaderboardSource {

  def loadRankingsByCycle(cycle: Int): Map[RankingType.Value, List[Ranking]] = {
    val rankingsByType = scala.collection.mutable.Map[RankingType.Value, ListBuffer[Ranking]]()

    Using.Manager { use =>
      // 'use' registers each AutoCloseable resource for guaranteed cleanup
      val connection = use(Meta.getConnection())
      val preparedStatement = use(connection.prepareStatement(
        s"SELECT * FROM $RANKING_LEADERBOARD_TABLE WHERE cycle = ? ORDER BY ranking_type, ranking"
      ))

      preparedStatement.setInt(1, cycle)
      val resultSet = use(preparedStatement.executeQuery())

      while (resultSet.next()) {
        // Fast hash map lookup instead of expensive reflection
        val typeString = resultSet.getString("ranking_type")
        val rankingType = RankingType.fastLookup.getOrElse(typeString,
          throw new IllegalArgumentException(s"Unknown ranking type: $typeString")
        )

        val keyString = resultSet.getString("ranking_key")
        val entryString = resultSet.getString("entry")
        val ranking = resultSet.getInt("ranking")
        val rankedValue = resultSet.getDouble("ranked_value")
        val movement = resultSet.getInt("movement")
        val reputationPrize = Option(resultSet.getInt("reputation_prize")).filter(_ != 0)

        // Parse key and entry based on ranking type
        val (key, entry) = parseKeyAndEntry(rankingType, keyString, entryString)

        val rankingEntry = Ranking(
          rankingType = rankingType,
          key = key,
          entry = entry,
          ranking = ranking,
          rankedValue = rankedValue,
          movement = movement,
          reputationPrize = reputationPrize
        )

        rankingsByType.getOrElseUpdate(rankingType, ListBuffer[Ranking]()) += rankingEntry
      }
    }.recover {
      case e: Exception =>
        println(s"Failed to load rankings for cycle $cycle: ${e.getMessage}")
        throw e
    }

    rankingsByType.view.mapValues(_.toList).toMap
  }

  def saveRankingsByCycle(cycle: Int, rankings: Map[RankingType.Value, List[Ranking]]): Unit = {
    val connection = Meta.getConnection()
    try {
      connection.setAutoCommit(false)

      // Delete any existing rankings for this cycle to handle re-runs after failures
      val deleteStatement = connection.prepareStatement("DELETE FROM " + RANKING_LEADERBOARD_TABLE + " WHERE cycle = ?")
      deleteStatement.setInt(1, cycle)
      deleteStatement.executeUpdate()
      deleteStatement.close()

      val insertStatement = connection.prepareStatement(
        "INSERT INTO " + RANKING_LEADERBOARD_TABLE + 
        " (cycle, ranking_type, key_hash, ranking_key, entry, ranking, ranked_value, movement, reputation_prize) VALUES(?,?,?,?,?,?,?,?,?)"
      )
      
      rankings.foreach { case (rankingType, rankingList) =>
        rankingList.foreach { ranking =>
          val keyString = serializeKey(ranking.key)
          val keyHash = java.security.MessageDigest.getInstance("SHA-256").digest(keyString.getBytes("UTF-8")).map("%02x".format(_)).mkString
          insertStatement.setInt(1, cycle)
          insertStatement.setString(2, rankingType.toString)
          insertStatement.setString(3, keyHash)
          insertStatement.setString(4, keyString)
          insertStatement.setString(5, serializeEntry(ranking.entry))
          insertStatement.setInt(6, ranking.ranking)
          insertStatement.setDouble(7, ranking.rankedValue.doubleValue())
          insertStatement.setInt(8, ranking.movement)
          insertStatement.setInt(9, ranking.reputationPrize.getOrElse(0))
          insertStatement.addBatch()
        }
      }
      
      insertStatement.executeBatch()
      insertStatement.close()
      connection.commit()
    } finally {
      connection.close()
    }
  }

  def deleteRankingsByCycle(cycle: Int): Unit = {
    val connection = Meta.getConnection()
    try {
      val preparedStatement = connection.prepareStatement("DELETE FROM " + RANKING_LEADERBOARD_TABLE + " WHERE cycle = ?")
      preparedStatement.setInt(1, cycle)
      preparedStatement.executeUpdate()
      preparedStatement.close()
    } finally {
      connection.close()
    }
  }

  private def serializeKey(key: RankingKey): String = key match {
    case RankingKey.AirlineKey(airlineId) => s"AIRLINE:$airlineId"
    case RankingKey.AirportKey(airportId) => s"AIRPORT:$airportId"
    case RankingKey.AllianceKey(allianceId) => s"ALLIANCE:$allianceId"
    case pair: RankingKey.AirportPairKey =>
      val c = pair.canonical
      s"AIRPORT_PAIR:${c.fromAirportId}|${c.toAirportId}"
    case RankingKey.AirlineAirportKey(airlineId, airportId) => s"AIRLINE_AIRPORT:$airlineId|$airportId"
    case RankingKey.LinkKey(airlineId, linkId) => s"LINK:$airlineId|$linkId"
  }

  private def serializeEntry(entry: Any): String = {
    // Produce a compact, UI-friendly string that fits within VARCHAR(512)
    val raw: String = entry match {
      case (airline: Airline, airport: Airport) => s"${airline.name}|${airport.iata}" // for AIRPORT_TRAFFIC
      case (a1: Airport, a2: Airport) => s"${a1.iata}|${a2.iata}" // for airport pair rankings
      case link: Link => s"${link.from.iata}-${link.to.iata}" // for link-based rankings
      case alliance: Alliance => alliance.name
      case airline: Airline => airline.name
      case airport: Airport => airport.iata
      case tuple: (Any, Any) => s"${tuple._1}|${tuple._2}" // fallback for other tuples
      case other => other.toString
    }
    // Ensure it cannot exceed the DB column size
    if (raw.length > 512) raw.take(512) else raw
  }

  private def parseKeyAndEntry(rankingType: RankingType.Value, keyString: String, entryString: String): (RankingKey, Any) = {
    // Expect keyString in namespaced format from serializeKey
    if (keyString.startsWith("AIRLINE_AIRPORT:")) {
      val parts = keyString.stripPrefix("AIRLINE_AIRPORT:").split("\\|")
      val airlineId = parts(0).toInt
      val airportId = parts(1).toInt

      // For LOUNGE ranking type, try to load the actual Lounge object
      if (rankingType == RankingType.LOUNGE) {
        val lounge = AirlineSource.loadLoungeByAirlineAndAirport(airlineId, airportId).getOrElse(return (RankingKey.AirlineAirportKey(airlineId, airportId), entryString))
        (RankingKey.AirlineAirportKey(airlineId, airportId), lounge)
      } else {
        // For other types, load airline and airport
        val airline = AirlineSource.loadAirlineById(airlineId).getOrElse(return (RankingKey.AirlineAirportKey(airlineId, airportId), entryString))
        val airport = AirportSource.loadAirportById(airportId).getOrElse(return (RankingKey.AirlineAirportKey(airlineId, airportId), entryString))
        (RankingKey.AirlineAirportKey(airlineId, airportId), (airline, airport))
      }
    } else if (keyString.startsWith("AIRPORT_PAIR:")) {
      val parts = keyString.stripPrefix("AIRPORT_PAIR:").split("\\|")
      val airportId1 = parts(0).toInt
      val airportId2 = parts(1).toInt
      val airport1 = AirportSource.loadAirportById(airportId1).getOrElse(return (RankingKey.AirportPairKey(airportId1, airportId2).canonical, entryString))
      val airport2 = AirportSource.loadAirportById(airportId2).getOrElse(return (RankingKey.AirportPairKey(airportId1, airportId2).canonical, entryString))
      (RankingKey.AirportPairKey(airportId1, airportId2).canonical, (airport1, airport2))
    } else if (keyString.startsWith("ALLIANCE:")) {
      val allianceId = keyString.stripPrefix("ALLIANCE:").toInt
      val alliance = AllianceSource.loadAllianceById(allianceId).getOrElse(return (RankingKey.AllianceKey(allianceId), entryString))
      (RankingKey.AllianceKey(allianceId), alliance)
    } else if (keyString.startsWith("AIRPORT:")) {
      val airportId = keyString.stripPrefix("AIRPORT:").toInt
      val airport = AirportSource.loadAirportById(airportId).getOrElse(return (RankingKey.AirportKey(airportId), entryString))
      (RankingKey.AirportKey(airportId), airport)
    } else if (keyString.startsWith("AIRLINE:")) {
      val airlineId = keyString.stripPrefix("AIRLINE:").toInt
      val airline = AirlineSource.loadAirlineById(airlineId).getOrElse(return (RankingKey.AirlineKey(airlineId), entryString))
      (RankingKey.AirlineKey(airlineId), airline)
    } else if (keyString.startsWith("LINK:")) {
      val parts = keyString.stripPrefix("LINK:").split("\\|")
      val airlineId = parts(0).toInt
      val linkId = parts(1).toInt
      val link = LinkSource.loadFlightLinkById(linkId, LinkSource.FULL_LOAD).getOrElse(return (RankingKey.LinkKey(airlineId, linkId), entryString))
      (RankingKey.LinkKey(airlineId, linkId), link)
    } else {
      // Fallback for very old rows (pre-migration), try to infer by ranking type
      rankingType match {
        case RankingType.AIRPORT => 
          val airportId = entryString.toIntOption.getOrElse(0)
          val airport = AirportSource.loadAirportById(airportId).getOrElse(return (RankingKey.AirportKey(airportId), entryString))
          (RankingKey.AirportKey(airportId), airport)
        case _ => 
          val airlineId = keyString.toIntOption.getOrElse(0)
          val airline = AirlineSource.loadAirlineById(airlineId).getOrElse(return (RankingKey.AirlineKey(airlineId), entryString))
          (RankingKey.AirlineKey(airlineId), airline)
      }
    }
  }
}