package com.patson.data.airplane

import scala.collection.mutable.ListBuffer
import com.patson.data.Constants._
import com.patson.model.airplane._
import com.patson.data.Meta

import java.sql.ResultSet
import scala.util.Using

object ModelSource {
  private[this] val BASE_QUERY = "SELECT * FROM " + AIRPLANE_MODEL_TABLE

  def loadAllModels() = {
      loadModelsByCriteria(List.empty)
  }

  def loadModelsByCriteria(criteria : List[(String, Any)]) = {
    val queryString = new StringBuilder(BASE_QUERY)

    if (!criteria.isEmpty) {
      queryString.append(" WHERE ")
      for (i <- 0 until criteria.size - 1) {
        queryString.append(criteria(i)._1 + " = ? AND ")
      }
      queryString.append(criteria.last._1 + " = ?")
    }
    loadModelsByQuery(queryString.toString, criteria.map(_._2))
  }

  def loadModelsByQuery(queryString : String, parameters : Seq[Any] = Seq.empty) = {
    Using.resource(Meta.getConnection()) { connection =>
      Using.resource(connection.prepareStatement(queryString)) { preparedStatement =>
        for (i <- 0 until parameters.size) {
          preparedStatement.setObject(i + 1, parameters(i))
        }
        Using.resource(preparedStatement.executeQuery()) { resultSet =>
          val models = new ListBuffer[Model]()
          while (resultSet.next()) {
            models += getModelFromRow(resultSet)
          }
          models.toList
        }
      }
    }
  }

  def getModelFromRow(resultSet : ResultSet) = {
     val model = Model(
          resultSet.getString("name"),
          resultSet.getString("family"),
          resultSet.getInt("capacity"),
          resultSet.getInt("quality"),
          resultSet.getDouble("ascent_burn"),
          resultSet.getDouble("cruise_burn"),
          resultSet.getInt("speed"),
          resultSet.getInt("fly_range"),
          resultSet.getInt("price"),
          resultSet.getInt("lifespan"),
          resultSet.getInt("construction_time"),
          Manufacturer(resultSet.getString("manufacturer"), resultSet.getString("country_code")),
          imageUrl = resultSet.getString("image_url"),
          runwayRequirement = resultSet.getInt("runway_requirement")
          )
     model.id = resultSet.getInt("id")
     model
  }

  def loadModelById(id : Int) = {
      val result = loadModelsByCriteria(List(("id", id)))
      if (result.isEmpty) {
        None
      } else {
        Some(result(0))
      }
  }

  def loadModelsWithinRange(range : Int) = {
    val queryString = new StringBuilder(BASE_QUERY)

    queryString.append(" WHERE fly_range >= ?")
    loadModelsByQuery(queryString.toString, Seq(range))
  }

  def deleteAllModels() = {
    Using.resource(Meta.getConnection()) { connection =>
      Using.resource(connection.prepareStatement("DELETE FROM  " + AIRPLANE_MODEL_TABLE)) { preparedStatement =>
        val deletedCount = preparedStatement.executeUpdate()
        println("Deleted " + deletedCount + " model records")
        deletedCount
      }
    }
  }

  def updateModels(models : List[Model]) = {
    Using.resource(Meta.getConnection()) { connection =>
      connection.setAutoCommit(false)
      Using.resource(connection.prepareStatement("UPDATE " + AIRPLANE_MODEL_TABLE + " SET capacity = ?, quality = ?, ascent_burn = ?, cruise_burn = ?, speed = ?, fly_range = ?, price = ?, lifespan = ?, construction_time = ?, country_code = ?, manufacturer = ?, image_url = ?, family = ?, runway_requirement = ? WHERE name = ?")) { preparedStatement =>
        models.foreach { model =>
          preparedStatement.setString(15, model.name)
          preparedStatement.setInt(1, model.capacity)
          preparedStatement.setInt(2, model.quality)
          preparedStatement.setDouble(3, model.ascentBurn)
          preparedStatement.setDouble(4, model.cruiseBurn)
          preparedStatement.setInt(5, model.speed)
          preparedStatement.setInt(6, model.range)
          preparedStatement.setInt(7, model.price)
          preparedStatement.setInt(8, model.lifespan)
          preparedStatement.setInt(9, model.constructionTime)
          preparedStatement.setString(10, model.manufacturer.countryCode)
          preparedStatement.setString(11, model.manufacturer.name)
          preparedStatement.setString(12, model.imageUrl)
          preparedStatement.setString(13, model.family)
          preparedStatement.setInt(14, model.runwayRequirement)
          preparedStatement.executeUpdate()
        }
      }
      connection.commit()
    }
  }


  def saveModels(models : List[Model]) = {
    Using.resource(Meta.getConnection()) { connection =>
      connection.setAutoCommit(false)
      Using.resource(connection.prepareStatement("INSERT INTO " + AIRPLANE_MODEL_TABLE + "(name, capacity, quality, ascent_burn, cruise_burn, speed, fly_range, price, lifespan, construction_time, country_code, manufacturer, image_url, family, runway_requirement) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) { preparedStatement =>
        models.foreach { model =>
          preparedStatement.setString(1, model.name)
          preparedStatement.setInt(2, model.capacity)
          preparedStatement.setInt(3, model.quality)
          preparedStatement.setDouble(4, model.ascentBurn)
          preparedStatement.setDouble(5, model.cruiseBurn)
          preparedStatement.setInt(6, model.speed)
          preparedStatement.setInt(7, model.range)
          preparedStatement.setInt(8, model.price)
          preparedStatement.setInt(9, model.lifespan)
          preparedStatement.setInt(10, model.constructionTime)
          preparedStatement.setString(11, model.manufacturer.countryCode)
          preparedStatement.setString(12, model.manufacturer.name)
          preparedStatement.setString(13, model.imageUrl)
          preparedStatement.setString(14, model.family)
          preparedStatement.setInt(15, model.runwayRequirement)
          preparedStatement.executeUpdate()
        }
      }
      connection.commit()
    }
  }

}
