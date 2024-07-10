package com.patson.data.airplane

import scala.collection.mutable.ListBuffer
import com.patson.data.Constants._
import com.patson.model.airplane._
import com.patson.data.Meta
import com.patson.util.AirplaneModelDiscountCache

import java.sql.{ResultSet, Types}
import scala.collection.mutable

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
      //open the hsqldb
      val connection = Meta.getConnection() 
      
      val preparedStatement = connection.prepareStatement(queryString)
      
      for (i <- 0 until parameters.size) {
        preparedStatement.setObject(i + 1, parameters(i))
      }
      
      val resultSet = preparedStatement.executeQuery()
      
      val models = new ListBuffer[Model]()
      while (resultSet.next()) {
        models += getModelFromRow(resultSet)
      }
      
      resultSet.close()
      preparedStatement.close()
      connection.close()
      
      models.toList
  }
  
  def getModelFromRow(resultSet : ResultSet) = {
     val model = Model( 
          resultSet.getString("name"),
          resultSet.getString("family"),
          resultSet.getInt("capacity"),
          resultSet.getInt("max_seats"),
          resultSet.getInt("quality").toDouble / 2.0,
          resultSet.getInt("fuel_burn"),
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
      //open the hsqldb
      val connection = Meta.getConnection()
      
      var queryString = "DELETE FROM  " + AIRPLANE_MODEL_TABLE
      
      val preparedStatement = connection.prepareStatement(queryString)
      
      val deletedCount = preparedStatement.executeUpdate()
      
      preparedStatement.close()
      connection.close()
      
      println("Deleted " + deletedCount + " model records")
      deletedCount
  }
  
  def updateModels(models : List[Model]) = {
    val connection = Meta.getConnection()
        
    val preparedStatement = connection.prepareStatement("UPDATE " + AIRPLANE_MODEL_TABLE + " SET capacity = ?, max_seats = ?, quality = ?, fuel_burn = ?, speed = ?, fly_range = ?, price = ?, lifespan = ?, construction_time = ?, country_code = ?, manufacturer = ?, image_url = ?, family = ?, runway_requirement = ? WHERE name = ?")

    connection.setAutoCommit(false)
    models.foreach { 
      model =>
        preparedStatement.setString(15, model.name)
        preparedStatement.setInt(1, model.capacity)
        preparedStatement.setInt(2, model.maxSeats)
        preparedStatement.setInt(3, model.quality.toInt)
        preparedStatement.setInt(4, model.fuelBurn)
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
    preparedStatement.close()
    connection.commit()
    
    connection.close()
  }
  
  
  def saveModels(models : List[Model]) = {
    val connection = Meta.getConnection()
        
        val preparedStatement = connection.prepareStatement("INSERT INTO " + AIRPLANE_MODEL_TABLE + "(name, capacity, max_seats, quality, fuel_burn, speed, fly_range, price, lifespan, construction_time, country_code, manufacturer, image_url, family, runway_requirement) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")

        connection.setAutoCommit(false)
        models.foreach { 
          model =>
            preparedStatement.setString(1, model.name)
            preparedStatement.setInt(2, model.capacity)
            preparedStatement.setInt(3, model.maxSeats)
            preparedStatement.setInt(4, (model.quality).toInt)
            preparedStatement.setInt(5, model.fuelBurn)
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
        preparedStatement.close()
        connection.commit()
        
        connection.close()
  }

  def saveFavoriteModelId(airlineId : Int, modelId : Int, startCycle: Int): Unit = {
    val connection = Meta.getConnection()

    val preparedStatement = connection.prepareStatement("REPLACE INTO " + AIRPLANE_MODEL_FAVORITE_TABLE + "(airline, model, start_cycle) VALUES(?,?,?)")

    connection.setAutoCommit(false)
    preparedStatement.setInt(1, airlineId)
    preparedStatement.setInt(2, modelId)
    preparedStatement.setInt(3, startCycle)

    preparedStatement.executeUpdate()

    preparedStatement.close()
    connection.commit()
    connection.close()
  }

  def loadFavoriteModelId(airlineId : Int) : Option[(Int, Int)] = {
    val connection = Meta.getConnection()

    val preparedStatement = connection.prepareStatement("SELECT * FROM " + AIRPLANE_MODEL_FAVORITE_TABLE + " WHERE airline = ?")

    try {
      preparedStatement.setInt(1, airlineId)
      val resultSet = preparedStatement.executeQuery()

      val result =
        if (resultSet.next()) {
          Some((resultSet.getInt("model"), resultSet.getInt("start_cycle")))
        } else {
          None
        }
      resultSet.close()
      result
    } finally {
      preparedStatement.close()
      connection.close()
    }
  }

  def deleteAllFavoriteModelIds() = {
    val connection = Meta.getConnection()

    val preparedStatement = connection.prepareStatement("DELETE FROM " + AIRPLANE_MODEL_FAVORITE_TABLE)

    try {
      preparedStatement.executeUpdate()
    } finally {
      preparedStatement.close()
      connection.close()
    }
  }

  def saveAirlineDiscount(airlineId : Int, discount : ModelDiscount): Unit = {
    val connection = Meta.getConnection()

    val preparedStatement = connection.prepareStatement("REPLACE INTO " + AIRPLANE_MODEL_AIRLINE_DISCOUNT_TABLE + "(airline, model, discount, discount_type, discount_reason, expiration_cycle) VALUES(?,?,?,?,?,?)")

    connection.setAutoCommit(false)
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

    preparedStatement.close()
    connection.commit()
    connection.close()
  }

  def deleteAirlineDiscount(airlineId : Int, modelId : Int, discountReason : DiscountReason.Value) = {
    val connection = Meta.getConnection()

    val preparedStatement = connection.prepareStatement("DELETE FROM " + AIRPLANE_MODEL_AIRLINE_DISCOUNT_TABLE + " WHERE airline = ? AND model = ? AND discount_reason = ?")

    connection.setAutoCommit(false)
    preparedStatement.setInt(1, airlineId)
    preparedStatement.setInt(2, modelId)
    preparedStatement.setInt(3, discountReason.id)
    preparedStatement.executeUpdate()

    preparedStatement.close()
    connection.commit()
    connection.close()
  }

  def deleteAllAirlineDiscounts() = {
    val connection = Meta.getConnection()

    val preparedStatement = connection.prepareStatement("DELETE FROM " + AIRPLANE_MODEL_AIRLINE_DISCOUNT_TABLE)

    preparedStatement.executeUpdate()
    connection.close()
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
    //open the hsqldb
    val connection = Meta.getConnection()

    val preparedStatement = connection.prepareStatement(queryString)

    for (i <- 0 until parameters.size) {
      preparedStatement.setObject(i + 1, parameters(i))
    }


    val resultSet = preparedStatement.executeQuery()


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

    resultSet.close()
    preparedStatement.close()
    connection.close()

    discountsByAirlineId.view.mapValues(_.toList).toMap
  }

  def updateModelDiscounts(discounts : List[ModelDiscount]): Unit = {
    val connection = Meta.getConnection()

    val purgeStatement = connection.prepareStatement("DELETE FROM " + AIRPLANE_MODEL_DISCOUNT_TABLE)
    purgeStatement.executeUpdate()
    purgeStatement.close()
    val preparedStatement = connection.prepareStatement("REPLACE INTO " + AIRPLANE_MODEL_DISCOUNT_TABLE + "(model, discount, discount_type, discount_reason, expiration_cycle) VALUES(?,?,?,?,?)")

    connection.setAutoCommit(false)

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
    AirplaneModelDiscountCache.updateModelDiscounts(discounts)

    preparedStatement.close()
    connection.commit()
    connection.close()
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
    //open the hsqldb
    val connection = Meta.getConnection()

    val preparedStatement = connection.prepareStatement(queryString)

    for (i <- 0 until parameters.size) {
      preparedStatement.setObject(i + 1, parameters(i))
    }


    val resultSet = preparedStatement.executeQuery()


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

    resultSet.close()
    preparedStatement.close()
    connection.close()

    discounts.toList
  }
}