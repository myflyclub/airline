package com.patson.data.airplane

import scala.collection.mutable.ListBuffer
import com.patson.data.Constants._
import com.patson.model.airplane._
import com.patson.data.Meta
import com.patson.util.AirplaneModelDiscountCache

import java.sql.{ResultSet, Types}
import scala.collection.mutable
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

  def saveFavoriteModelId(airlineId : Int, modelId : Int, startCycle: Int): Unit = {
    Using.resource(Meta.getConnection()) { connection =>
      connection.setAutoCommit(false)
      Using.resource(connection.prepareStatement("REPLACE INTO " + AIRPLANE_MODEL_FAVORITE_TABLE + "(airline, model, start_cycle) VALUES(?,?,?)")) { preparedStatement =>
        preparedStatement.setInt(1, airlineId)
        preparedStatement.setInt(2, modelId)
        preparedStatement.setInt(3, startCycle)
        preparedStatement.executeUpdate()
      }
      connection.commit()
    }
  }

  def loadFavoriteModelId(airlineId : Int) : Option[(Int, Int)] = {
    Using.resource(Meta.getConnection()) { connection =>
      Using.resource(connection.prepareStatement("SELECT * FROM " + AIRPLANE_MODEL_FAVORITE_TABLE + " WHERE airline = ?")) { preparedStatement =>
        preparedStatement.setInt(1, airlineId)
        Using.resource(preparedStatement.executeQuery()) { resultSet =>
          if (resultSet.next()) {
            Some((resultSet.getInt("model"), resultSet.getInt("start_cycle")))
          } else {
            None
          }
        }
      }
    }
  }

  def deleteFavoriteModelId(airlineId : Int) = {
    Using.resource(Meta.getConnection()) { connection =>
      Using.resource(connection.prepareStatement("DELETE FROM " + AIRPLANE_MODEL_FAVORITE_TABLE + " WHERE airline = ?")) { preparedStatement =>
        preparedStatement.setInt(1, airlineId)
        preparedStatement.executeUpdate()
      }
    }
  }

  def saveAirlineDiscount(airlineId : Int, discount : ModelDiscount): Unit = {
    Using.resource(Meta.getConnection()) { connection =>
      connection.setAutoCommit(false)
      Using.resource(connection.prepareStatement("REPLACE INTO " + AIRPLANE_MODEL_AIRLINE_DISCOUNT_TABLE + "(airline, model, discount, discount_type, discount_reason, expiration_cycle) VALUES(?,?,?,?,?,?)")) { preparedStatement =>
        preparedStatement.setInt(1, airlineId)
        preparedStatement.setInt(2, discount.modelId)
        preparedStatement.setDouble(3, discount.discount)
        preparedStatement.setInt(4, discount.discountType.id)
        preparedStatement.setInt(5, discount.discountReason.id)
        discount.expirationCycle match {
          case Some(expirationCycle) => preparedStatement.setInt(6, expirationCycle)
          case None => preparedStatement.setNull(6, Types.INTEGER)
        }
        preparedStatement.executeUpdate()
      }
      connection.commit()
    }
  }

  def deleteAirlineDiscount(airlineId : Int, modelId : Int, discountReason : DiscountReason.Value) = {
    Using.resource(Meta.getConnection()) { connection =>
      connection.setAutoCommit(false)
      Using.resource(connection.prepareStatement("DELETE FROM " + AIRPLANE_MODEL_AIRLINE_DISCOUNT_TABLE + " WHERE airline = ? AND model = ? AND discount_reason = ?")) { preparedStatement =>
        preparedStatement.setInt(1, airlineId)
        preparedStatement.setInt(2, modelId)
        preparedStatement.setInt(3, discountReason.id)
        preparedStatement.executeUpdate()
      }
      connection.commit()
    }
  }

  def deleteAllAirlineDiscounts() = {
    Using.resource(Meta.getConnection()) { connection =>
      Using.resource(connection.prepareStatement("DELETE FROM " + AIRPLANE_MODEL_AIRLINE_DISCOUNT_TABLE)) { preparedStatement =>
        preparedStatement.executeUpdate()
      }
    }
  }

  /**
    *
    * @return Map[airlineId, discounts]
    */
  def loadAllAirlineDiscounts() : Map[Int, List[ModelDiscount]]= {
    loadAirlineDiscountsByCriteria(List.empty)
  }

  def loadAirlineDiscountsByAirlineId(airlineId : Int) : List[ModelDiscount]= {
    val result = loadAirlineDiscountsByCriteria(List(("airline", airlineId)))
    result.getOrElse(airlineId, List.empty)
  }
  def loadAirlineDiscountsByAirlineIdAndModelId(airlineId : Int, modelId : Int) : List[ModelDiscount]= {
    val result = loadAirlineDiscountsByCriteria(List(("airline", airlineId), ("model", modelId)))
    result.getOrElse(airlineId, List.empty)
  }

  def loadAirlineDiscountsByCriteria(criteria : List[(String, Any)]) = {
    val queryString = new StringBuilder("SELECT * FROM " + AIRPLANE_MODEL_AIRLINE_DISCOUNT_TABLE)

    if (!criteria.isEmpty) {
      queryString.append(" WHERE ")
      for (i <- 0 until criteria.size - 1) {
        queryString.append(criteria(i)._1 + " = ? AND ")
      }
      queryString.append(criteria.last._1 + " = ?")
    }
    loadAirlineDiscountsByQuery(queryString.toString, criteria.map(_._2))
  }

  /**
    *
    * @param queryString
    * @param parameters
    * @return Map[airlineId, discounts]
    */
  def loadAirlineDiscountsByQuery(queryString : String, parameters : Seq[Any] = Seq.empty) = {
    Using.resource(Meta.getConnection()) { connection =>
      Using.resource(connection.prepareStatement(queryString)) { preparedStatement =>
        for (i <- 0 until parameters.size) {
          preparedStatement.setObject(i + 1, parameters(i))
        }
        Using.resource(preparedStatement.executeQuery()) { resultSet =>
          val discountsByAirlineId = new mutable.HashMap[Int, ListBuffer[ModelDiscount]]()
          while (resultSet.next()) {
            val airlineId = resultSet.getInt("airline")
            val discounts = discountsByAirlineId.getOrElseUpdate(airlineId, ListBuffer())
            val expirationCycleObject = resultSet.getObject("expiration_cycle")
            val expirationCycle = if (expirationCycleObject == null) None else Some(expirationCycleObject.asInstanceOf[Int])
            discounts.append(ModelDiscount(
              resultSet.getInt("model"),
              resultSet.getDouble("discount"),
              DiscountType(resultSet.getInt("discount_type")),
              DiscountReason(resultSet.getInt("discount_reason")),
              expirationCycle
            ))
          }
          discountsByAirlineId.view.mapValues(_.toList).toMap
        }
      }
    }
  }

  def updateModelDiscounts(discounts : List[ModelDiscount]): Unit = {
    Using.resource(Meta.getConnection()) { connection =>
      Using.resource(connection.prepareStatement("DELETE FROM " + AIRPLANE_MODEL_DISCOUNT_TABLE)) { purgeStatement =>
        purgeStatement.executeUpdate()
      }
      connection.setAutoCommit(false)
      Using.resource(connection.prepareStatement("REPLACE INTO " + AIRPLANE_MODEL_DISCOUNT_TABLE + "(model, discount, discount_type, discount_reason, expiration_cycle) VALUES(?,?,?,?,?)")) { preparedStatement =>
        discounts.foreach { discount =>
          preparedStatement.setInt(1, discount.modelId)
          preparedStatement.setDouble(2, discount.discount)
          preparedStatement.setInt(3, discount.discountType.id)
          preparedStatement.setInt(4, discount.discountReason.id)
          discount.expirationCycle match {
            case Some(expirationCycle) => preparedStatement.setInt(5, expirationCycle)
            case None => preparedStatement.setNull(5, Types.INTEGER)
          }
          preparedStatement.executeUpdate()
        }
      }
      AirplaneModelDiscountCache.updateModelDiscounts(discounts)
      connection.commit()
    }
  }


  /**
    *
    * @return Map[airlineId, discounts]
    */
  def loadAllModelDiscounts() : List[ModelDiscount]= {
    loadModelDiscountsByCriteria(List.empty)
  }

  def loadModelDiscountsByModelId(modelId : Int) : List[ModelDiscount]= {
    loadModelDiscountsByCriteria(List(("model", modelId)))
  }

  def loadModelDiscountsByCriteria(criteria : List[(String, Any)]) = {
    val queryString = new StringBuilder("SELECT * FROM " + AIRPLANE_MODEL_DISCOUNT_TABLE)

    if (!criteria.isEmpty) {
      queryString.append(" WHERE ")
      for (i <- 0 until criteria.size - 1) {
        queryString.append(criteria(i)._1 + " = ? AND ")
      }
      queryString.append(criteria.last._1 + " = ?")
    }
    loadModelDiscountsByQuery(queryString.toString, criteria.map(_._2))
  }

  /**
    *
    * @param queryString
    * @param parameters
    * @return Map[airlineId, discounts]
    */
  def loadModelDiscountsByQuery(queryString : String, parameters : Seq[Any] = Seq.empty) = {
    Using.resource(Meta.getConnection()) { connection =>
      Using.resource(connection.prepareStatement(queryString)) { preparedStatement =>
        for (i <- 0 until parameters.size) {
          preparedStatement.setObject(i + 1, parameters(i))
        }
        Using.resource(preparedStatement.executeQuery()) { resultSet =>
          val discounts = ListBuffer[ModelDiscount]()
          while (resultSet.next()) {
            val expirationCycleObject = resultSet.getObject("expiration_cycle")
            val expirationCycle = if (expirationCycleObject == null) None else Some(expirationCycleObject.asInstanceOf[Int])
            discounts.append(ModelDiscount(
              resultSet.getInt("model"),
              resultSet.getDouble("discount"),
              DiscountType(resultSet.getInt("discount_type")),
              DiscountReason(resultSet.getInt("discount_reason")),
              expirationCycle
            ))
          }
          discounts.toList
        }
      }
    }
  }
}
