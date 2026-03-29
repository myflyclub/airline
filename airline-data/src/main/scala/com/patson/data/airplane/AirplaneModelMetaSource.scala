package com.patson.data.airplane

import com.patson.data.Constants._
import com.patson.data.Meta

import scala.collection.mutable
import scala.util.Using

object AirplaneModelMetaSource {

  def saveLaunchCustomer(modelId: Int, airlineName: String): Unit = {
    Using.resource(Meta.getConnection()) { connection =>
      Using.resource(connection.prepareStatement(
        s"INSERT INTO $AIRPLANE_MODEL_META_TABLE (airplane_model, launch_customer) VALUES (?, ?) ON DUPLICATE KEY UPDATE launch_customer = launch_customer"
      )) { ps =>
        ps.setInt(1, modelId)
        ps.setString(2, airlineName)
        ps.executeUpdate()
      }
    }
  }

  def loadLaunchCustomer(modelId: Int): Option[String] = {
    Using.resource(Meta.getConnection()) { connection =>
      Using.resource(connection.prepareStatement(
        s"SELECT launch_customer FROM $AIRPLANE_MODEL_META_TABLE WHERE airplane_model = ?"
      )) { ps =>
        ps.setInt(1, modelId)
        Using.resource(ps.executeQuery()) { rs =>
          if (rs.next()) Some(rs.getString("launch_customer")) else None
        }
      }
    }
  }

  def loadAllLaunchCustomers(): Map[Int, String] = {
    Using.resource(Meta.getConnection()) { connection =>
      Using.resource(connection.prepareStatement(
        s"SELECT airplane_model, launch_customer FROM $AIRPLANE_MODEL_META_TABLE"
      )) { ps =>
        Using.resource(ps.executeQuery()) { rs =>
          val result = mutable.Map[Int, String]()
          while (rs.next()) {
            result.put(rs.getInt("airplane_model"), rs.getString("launch_customer"))
          }
          result.toMap
        }
      }
    }
  }
}
