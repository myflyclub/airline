package com.patson.patch

import com.patson.data.Constants._
import com.patson.data.{AirplaneSource, LinkSource, Meta, UserSource}
import com.patson.model.{DiscountAirline, LuxuryAirline}

import scala.collection.mutable.ListBuffer
import scala.util.Using

/**
  * Finds and fixes airplane configurations that violate airline type rules:
  * - DiscountAirline: cannot have business or first class seats
  * - LuxuryAirline: cannot have economy seats
  * 
  * Can be removed after running, as configs are deleted after airline type changes now.
  *
  * For each violation, reports the airline name, associated user name(s), and number
  * of airplanes affected, then resets those configurations to all-economy (Discount)
  * or all-business (Luxury) using the model's default economy capacity.
  */
object AirlineTypeConfigPatcher extends App {
  mainFlow()

  def mainFlow(): Unit = {
    println("=== AirlineTypeConfigPatcher ===")
    fixDiscountAirlineConfigs()
    fixLuxuryAirlineConfigs()
    println("=== Done ===")
  }

  def fixDiscountAirlineConfigs(): Unit = {
    println("\n--- Checking DiscountAirline configurations (no business/first allowed) ---")

    Using.resource(Meta.getConnection()) { connection =>
      // Find all configurations for DiscountAirlines that have business > 0 or first > 0
      val query =
        s"""SELECT t.id, t.airline, t.model, t.economy, t.business, t.first, t.is_default,
           |       a.name AS airline_name
           |FROM $AIRPLANE_CONFIGURATION_TEMPLATE_TABLE t
           |JOIN $AIRLINE_TABLE a ON a.id = t.airline
           |WHERE a.airline_type = ${DiscountAirline.id}
           |  AND (t.business > 0 OR t.first > 0)""".stripMargin

      Using.resource(connection.prepareStatement(query)) { stmt =>
        Using.resource(stmt.executeQuery()) { rs =>
          val violations = scala.collection.mutable.ListBuffer[(Int, Int, String, Int, Int, Int, Boolean)]()
          while (rs.next()) {
            violations += ((
              rs.getInt("id"),
              rs.getInt("airline"),
              rs.getString("airline_name"),
              rs.getInt("economy"),
              rs.getInt("business"),
              rs.getInt("first"),
              rs.getBoolean("is_default")
            ))
          }

          if (violations.isEmpty) {
            println("No violations found.")
          } else {
            violations.foreach { case (configId, airlineId, airlineName, econ, biz, first, isDefault) =>
              // Look up associated username
              val user = UserSource.loadUserByAirlineId(airlineId)
              val userNames = user.map(_.userName).getOrElse("(no user)")

              // Count airplanes using this configuration
              val airplaneCount = countAirplanesWithConfig(connection, configId)

              println(s"VIOLATION [Discount] airline='$airlineName' (id=$airlineId) user(s)='$userNames' " +
                s"configId=$configId econ=$econ biz=$biz first=$first isDefault=$isDefault " +
                s"airplanesAffected=$airplaneCount")

              // Reset: keep economy seats, zero out business and first
              val fixedEcon = if (econ > 0) econ else {
                // If economy was already 0, put all capacity into economy
                // We need model capacity — load from config template
                loadModelCapacity(connection, configId)
              }
              updateConfiguration(connection, configId, fixedEcon, 0, 0, isDefault)
              println(s"  -> Fixed: economy=$fixedEcon, business=0, first=0")

              if (airplaneCount > 0) {
                val updatedLinks = adjustLinksForConfig(configId)
                println(s"  -> Recalculated capacity on $updatedLinks link(s).")
              }
            }
            println(s"Fixed ${violations.size} Discount airline configuration(s).")
          }
        }
      }
    }
  }

  def fixLuxuryAirlineConfigs(): Unit = {
    println("\n--- Checking LuxuryAirline configurations (no economy allowed) ---")

    Using.resource(Meta.getConnection()) { connection =>
      val query =
        s"""SELECT t.id, t.airline, t.model, t.economy, t.business, t.first, t.is_default,
           |       a.name AS airline_name
           |FROM $AIRPLANE_CONFIGURATION_TEMPLATE_TABLE t
           |JOIN $AIRLINE_TABLE a ON a.id = t.airline
           |WHERE a.airline_type = ${LuxuryAirline.id}
           |  AND t.economy > 0""".stripMargin

      Using.resource(connection.prepareStatement(query)) { stmt =>
        Using.resource(stmt.executeQuery()) { rs =>
          val violations = scala.collection.mutable.ListBuffer[(Int, Int, String, Int, Int, Int, Boolean)]()
          while (rs.next()) {
            violations += ((
              rs.getInt("id"),
              rs.getInt("airline"),
              rs.getString("airline_name"),
              rs.getInt("economy"),
              rs.getInt("business"),
              rs.getInt("first"),
              rs.getBoolean("is_default")
            ))
          }

          if (violations.isEmpty) {
            println("No violations found.")
          } else {
            violations.foreach { case (configId, airlineId, airlineName, econ, biz, first, isDefault) =>
              val user = UserSource.loadUserByAirlineId(airlineId)
              val userNames = user.map(_.userName).getOrElse("(no user)")

              val airplaneCount = countAirplanesWithConfig(connection, configId)

              println(s"VIOLATION [Luxury] airline='$airlineName' (id=$airlineId) user(s)='$userNames' " +
                s"configId=$configId econ=$econ biz=$biz first=$first isDefault=$isDefault " +
                s"airplanesAffected=$airplaneCount")

              // Reset: zero economy, keep/redistribute into business
              val fixedBiz = if (biz > 0) biz else {
                // If business was 0, we need to put something — load model capacity and fill with business
                loadModelBusinessCapacity(connection, configId)
              }
              updateConfiguration(connection, configId, 0, fixedBiz, first, isDefault)
              println(s"  -> Fixed: economy=0, business=$fixedBiz, first=$first")

              if (airplaneCount > 0) {
                val updatedLinks = adjustLinksForConfig(configId)
                println(s"  -> Recalculated capacity on $updatedLinks link(s).")
              }
            }
            println(s"Fixed ${violations.size} Luxury airline configuration(s).")
          }
        }
      }
    }
  }

  /**
    * Mirrors LinkUtil.adjustLinksAfterConfigurationChanges.
    * Must be called AFTER the config template has been committed so that
    * loadAirplanesCriteria picks up the updated economy/business/first values.
    * Returns the number of links updated.
    */
  private def adjustLinksForConfig(configId: Int): Int = {
    val affectedLinkIds = AirplaneSource.loadAirplanesCriteria(List(("configuration", configId))).flatMap { airplane =>
      AirplaneSource.loadAirplaneLinkAssignmentsByAirplaneId(airplane.id).assignments.keys
    }.toSet

    val affectedLinks = ListBuffer[com.patson.model.Link]()
    affectedLinkIds.foreach { linkId =>
      LinkSource.loadFlightLinkById(linkId, LinkSource.FULL_LOAD).foreach { link =>
        affectedLinks.append(link)
      }
    }

    if (affectedLinks.nonEmpty) {
      LinkSource.updateLinks(affectedLinks.toList)
    }
    affectedLinks.size
  }

  private def countAirplanesWithConfig(connection: java.sql.Connection, configId: Int): Int = {
    Using.resource(connection.prepareStatement(
      s"SELECT COUNT(*) FROM $AIRPLANE_CONFIGURATION_TABLE WHERE configuration = ?"
    )) { stmt =>
      stmt.setInt(1, configId)
      Using.resource(stmt.executeQuery()) { rs =>
        if (rs.next()) rs.getInt(1) else 0
      }
    }
  }

  /**
    * Returns the full capacity of the model for a given config template row (as economy seats).
    * Used when the existing economy value is 0 and we need to fill with economy.
    */
  private def loadModelCapacity(connection: java.sql.Connection, configId: Int): Int = {
    Using.resource(connection.prepareStatement(
      s"SELECT m.capacity FROM $AIRPLANE_CONFIGURATION_TEMPLATE_TABLE t JOIN $AIRPLANE_MODEL_TABLE m ON t.model = m.id WHERE t.id = ?"
    )) { stmt =>
      stmt.setInt(1, configId)
      Using.resource(stmt.executeQuery()) { rs =>
        if (rs.next()) rs.getInt("capacity") else 100
      }
    }
  }

  /**
    * Returns a reasonable business seat count for a model — capacity / BUSINESS.spaceMultiplier.
    */
  private def loadModelBusinessCapacity(connection: java.sql.Connection, configId: Int): Int = {
    import com.patson.model.BUSINESS
    Using.resource(connection.prepareStatement(
      s"SELECT m.capacity FROM $AIRPLANE_CONFIGURATION_TEMPLATE_TABLE t JOIN $AIRPLANE_MODEL_TABLE m ON t.model = m.id WHERE t.id = ?"
    )) { stmt =>
      stmt.setInt(1, configId)
      Using.resource(stmt.executeQuery()) { rs =>
        if (rs.next()) (rs.getInt("capacity").toDouble / BUSINESS.spaceMultiplier).toInt else 0
      }
    }
  }

  private def updateConfiguration(connection: java.sql.Connection, configId: Int, economy: Int, business: Int, first: Int, isDefault: Boolean): Unit = {
    Using.resource(connection.prepareStatement(
      s"UPDATE $AIRPLANE_CONFIGURATION_TEMPLATE_TABLE SET economy = ?, business = ?, first = ? WHERE id = ?"
    )) { stmt =>
      stmt.setInt(1, economy)
      stmt.setInt(2, business)
      stmt.setInt(3, first)
      stmt.setInt(4, configId)
      stmt.executeUpdate()
    }
  }
}
