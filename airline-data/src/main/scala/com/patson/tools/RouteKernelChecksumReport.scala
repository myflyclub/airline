package com.patson.tools

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

import com.patson.SimulationConfig
import com.patson.data.Constants._
import com.patson.data.Meta

object RouteKernelChecksumReport {
  case class TableChecksum(table: String, rows: Long, sha256: String) {
    override def toString: String = s"checksum table=$table rows=$rows sha256=$sha256"
  }

  val defaultTables: List[String] = List(
    LINK_CONSUMPTION_TABLE,
    PASSENGER_ROUTE_HISTORY_TABLE,
    PASSENGER_LINK_HISTORY_TABLE,
    LINK_STATISTICS_TABLE,
    AIRPORT_STATISTICS_TABLE,
    COUNTRY_MARKET_SHARE_TABLE,
    AIRLINE_STATISTICS_TABLE,
    WORLD_STATISTICS_TABLE,
    PASSENGER_MISSED_DEMAND_TABLE,
    LINK_TABLE
  )

  val businessChecksumTables: List[String] = List(
    "passenger_route_history_business",
    "passenger_link_history_business",
    "passenger_link_history_path_business"
  )

  def main(args: Array[String]): Unit = {
    var exitCode = 0
    try {
      SimulationConfig.logRuntimeContext("checksum-report")
      val tables = if (args.isEmpty) defaultTables else args.toList
      report(tables).foreach(result => println(result.toString))
    } catch {
      case e: Throwable =>
        exitCode = 1
        e.printStackTrace()
    } finally {
      try {
        if (!Meta.dataSource.isClosed) {
          Meta.dataSource.close()
        }
      } catch {
        case e: Exception => println(s"checksum phase=shutdown warning=${e.getMessage}")
      }
      System.exit(exitCode)
    }
  }

  def report(tables: Seq[String]): List[TableChecksum] = {
    tables.map(checksum).toList
  }

  private def checksum(name: String): TableChecksum = {
    require(name.matches("[A-Za-z0-9_]+"), s"Unsafe checksum name '$name'")

    name match {
      case "passenger_route_history_business" =>
        checksumRows(
          name,
          s"""
             |SELECT passenger_count, home_country, home_airport, destination_airport,
             |  passenger_type, preference_type, preferred_link_class, route_cost, COUNT(*) AS row_count
             |FROM $PASSENGER_ROUTE_HISTORY_TABLE
             |GROUP BY passenger_count, home_country, home_airport, destination_airport,
             |  passenger_type, preference_type, preferred_link_class, route_cost
             |ORDER BY passenger_count, home_country, home_airport, destination_airport,
             |  passenger_type, preference_type, preferred_link_class, route_cost, row_count
             |""".stripMargin
        )
      case "passenger_link_history_business" =>
        checksumRows(
          name,
          s"""
             |SELECT pr.passenger_count, pr.home_country, pr.home_airport, pr.destination_airport,
             |  pr.passenger_type, pr.preference_type, pr.preferred_link_class, pr.route_cost,
             |  pl.link, pl.link_class, pl.inverted, pl.cost, pl.satisfaction, COUNT(*) AS row_count
             |FROM $PASSENGER_ROUTE_HISTORY_TABLE pr
             |JOIN $PASSENGER_LINK_HISTORY_TABLE pl ON pr.route_id = pl.route_id
             |GROUP BY pr.passenger_count, pr.home_country, pr.home_airport, pr.destination_airport,
             |  pr.passenger_type, pr.preference_type, pr.preferred_link_class, pr.route_cost,
             |  pl.link, pl.link_class, pl.inverted, pl.cost, pl.satisfaction
             |ORDER BY pr.passenger_count, pr.home_country, pr.home_airport, pr.destination_airport,
             |  pr.passenger_type, pr.preference_type, pr.preferred_link_class, pr.route_cost,
             |  pl.link, pl.link_class, pl.inverted, pl.cost, pl.satisfaction, row_count
             |""".stripMargin
        )
      case "passenger_link_history_path_business" =>
        checksumRows(
          name,
          s"""
             |SELECT pr.passenger_count, pr.home_country, pr.home_airport, pr.destination_airport,
             |  pr.passenger_type, pr.preference_type, pr.preferred_link_class, pr.route_cost,
             |  pl.link, pl.link_class, pl.inverted, pl.cost, COUNT(*) AS row_count
             |FROM $PASSENGER_ROUTE_HISTORY_TABLE pr
             |JOIN $PASSENGER_LINK_HISTORY_TABLE pl ON pr.route_id = pl.route_id
             |GROUP BY pr.passenger_count, pr.home_country, pr.home_airport, pr.destination_airport,
             |  pr.passenger_type, pr.preference_type, pr.preferred_link_class, pr.route_cost,
             |  pl.link, pl.link_class, pl.inverted, pl.cost
             |ORDER BY pr.passenger_count, pr.home_country, pr.home_airport, pr.destination_airport,
             |  pr.passenger_type, pr.preference_type, pr.preferred_link_class, pr.route_cost,
             |  pl.link, pl.link_class, pl.inverted, pl.cost, row_count
             |""".stripMargin
        )
      case table => checksumTable(table)
    }
  }

  private def checksumTable(table: String): TableChecksum = {
    Meta.withTimedConnection(s"checksum-$table") { connection =>
      val metadataStatement = connection.createStatement()
      val metadataResultSet = metadataStatement.executeQuery(s"SELECT * FROM `$table` LIMIT 0")
      val metadata = metadataResultSet.getMetaData
      val columns = (1 to metadata.getColumnCount).map(metadata.getColumnName)
      metadataResultSet.close()
      metadataStatement.close()

      val orderBy = columns.map(column => s"`$column`").mkString(", ")
      checksumRows(table, s"SELECT * FROM `$table` ORDER BY $orderBy")
    }
  }

  private def checksumRows(name: String, query: String): TableChecksum = {
    Meta.withTimedConnection(s"checksum-$name") { connection =>
      val digest = MessageDigest.getInstance("SHA-256")
      var rows = 0L
      val statement = connection.createStatement(java.sql.ResultSet.TYPE_FORWARD_ONLY, java.sql.ResultSet.CONCUR_READ_ONLY)
      statement.setFetchSize(Integer.MIN_VALUE)
      val resultSet = statement.executeQuery(query)

      try {
        val metadata = resultSet.getMetaData
        val columnCount = metadata.getColumnCount
        while (resultSet.next()) {
          1.to(columnCount).foreach { index =>
            val value = Option(resultSet.getString(index)).getOrElse("<NULL>")
            digest.update(value.getBytes(StandardCharsets.UTF_8))
            digest.update('|'.toByte)
          }
          digest.update('\n'.toByte)
          rows += 1
        }
      } finally {
        resultSet.close()
        statement.close()
      }

      TableChecksum(name, rows, digest.digest().map(byte => f"${byte & 0xff}%02x").mkString)
    }
  }
}
