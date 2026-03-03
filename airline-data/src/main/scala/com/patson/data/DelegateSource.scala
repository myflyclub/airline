package com.patson.data

import java.sql.{Statement, Types}

import com.patson.data.Constants._
import com.patson.data.airplane.ModelSource
import com.patson.model._
import com.patson.model.campaign.Campaign
import com.patson.util.{AirlineCache, AirportCache, CountryCache}

import scala.collection.mutable
import scala.collection.mutable.ListBuffer


object DelegateSource {
  private[this] val BASE_BUSY_DELEGATE_QUERY = "SELECT * FROM " + BUSY_DELEGATE_TABLE
  val TOOLTIP_MANAGERS = List(
    "Managers allow you to open more bases, more routes, lower aircraft prices, and more.",
    "<b>Actioning</b>: Generate action points."
  )


  /**
    * Load either delegates with task in progress or on cool down
    * @param airlineId
    * @return
    */
  def loadBusyDelegatesByAirline(airlineId : Int) : List[BusyDelegate] = {
    loadBusyDelegatesByCriteria(List(("airline", "=", airlineId))).get(airlineId).getOrElse(List.empty)
  }
  

  def loadBusyDelegatesByCriteria(criteria : List[(String, String, Any)]) = {
      var queryString = BASE_BUSY_DELEGATE_QUERY
      
      if (!criteria.isEmpty) {
        queryString += " WHERE "
        for (i <- 0 until criteria.size - 1) {
          queryString += criteria(i)._1 + criteria(i)._2 + " ? AND "
        }
        queryString += criteria.last._1 + criteria.last._2 + " ?"
      }
      loadBusyDelegatesByQueryString(queryString, criteria.map(_._3))
  }

  /**
    *
    * @param queryString
    * @param parameters
    * @return key is airline Id
    */
  def loadBusyDelegatesByQueryString(queryString : String, parameters : List[Any]) : Map[Int, List[BusyDelegate]]= {
    val connection = Meta.getConnection()
    try {
        val preparedStatement = connection.prepareStatement(queryString)
        
        for (i <- 0 until parameters.size) {
          preparedStatement.setObject(i + 1, parameters(i))
        }

        
        val resultSet = preparedStatement.executeQuery()
        
        val result = mutable.Map[Int, ListBuffer[DelegateLoadInfo]]() //key: Airline id


        while (resultSet.next()) {
          val airlineId = resultSet.getInt("airline")
          val delegateId = resultSet.getInt("id")
          val taskType = DelegateTaskType(resultSet.getInt("task_type"))
          val availableCycleObject = resultSet.getObject("available_cycle")
          val availableCycle = if (availableCycleObject == null) None else Some(availableCycleObject.asInstanceOf[Int])

          result.getOrElseUpdate(airlineId, ListBuffer[DelegateLoadInfo]()).append(DelegateLoadInfo(delegateId, taskType, availableCycle))
        }
        
        resultSet.close()
        preparedStatement.close()
        
        result.toList.map {
          case (airlineId, delegateInfoEntries: ListBuffer[DelegateLoadInfo]) => {
            val airline : Airline = AirlineCache.getAirline(airlineId).get
            val delegateTaskByDelegateId = loadDelegateTasks(delegateInfoEntries.map {
              case DelegateLoadInfo(delegateId, taskType, _) => (delegateId, taskType)
            }.toMap)

            val delegates : List[BusyDelegate] = delegateInfoEntries.toList.map {
              case (DelegateLoadInfo(delegateId, _, availableCycle)) => BusyDelegate(airline, delegateTaskByDelegateId(delegateId), availableCycle, delegateId)
            }

            (airlineId, delegates)
          }
        }.toMap
      } finally {
        connection.close()
      }
  }

  case class DelegateLoadInfo(id : Int, taskType : DelegateTaskType.Value, availableCycle : Option[Int])

  /**
    *
    * @param delegateIdAndTaskTypes
    * @return key delegate Id
    */
  def loadDelegateTasks(delegateIdAndTaskTypes : Map[Int, DelegateTaskType.Value]) : Map[Int, DelegateTask] = {
    val result = mutable.HashMap[Int, DelegateTask]()
    delegateIdAndTaskTypes.toList.groupBy(_._2).foreach {
      case(taskType, grouped) => {
        val delegateIdsOfThisTaskType = grouped.map(_._1)
        taskType match {
          case DelegateTaskType.COUNTRY =>
            result.addAll(loadCountryTasks(delegateIdsOfThisTaskType))
          case DelegateTaskType.CAMPAIGN =>
            result.addAll(loadCampaignTasks(delegateIdsOfThisTaskType))
          case DelegateTaskType.MANAGER_BASE =>
            delegateIdsOfThisTaskType.foreach(id => result.put(id, ManagerBaseDelegateTask()))
          case DelegateTaskType.MANAGER_AIRCRAFT_MODEL =>
            result.addAll(loadAircraftModelTasks(delegateIdsOfThisTaskType))
        }
      }
    }
    result.toMap
  }

  /**
    *
    * @param delegateIds
    * @return key - delegateId
    */
  def loadCountryTasks(delegateIds : List[Int]) = {
    val connection = Meta.getConnection()
    try {
      val delegateIdPhrase = delegateIds.mkString(",")
      val preparedStatement = connection.prepareStatement(s"SELECT * FROM $COUNTRY_DELEGATE_TASK_TABLE WHERE delegate IN ($delegateIdPhrase)")
      val resultSet = preparedStatement.executeQuery()

      val result = mutable.Map[Int, DelegateTask]() //key delegateId


      while (resultSet.next()) {
        val delegateId = resultSet.getInt("delegate")
        val country = CountryCache.getCountry(resultSet.getString("country_code")).get
        val startCycle = resultSet.getInt("start_cycle")

        result.put(delegateId, DelegateTask.country(startCycle, country))
      }

      resultSet.close()
      preparedStatement.close()

      result.toMap
    } finally {
      connection.close()
    }

  }

  def loadCampaignTasksByAirlineId(airlineId : Int) : List[CampaignDelegateTask] = {
    val campaignDelegateIds = loadBusyDelegatesByAirline(airlineId).filter(_.assignedTask.getTaskType == DelegateTaskType.CAMPAIGN).map(_.id)
    loadCampaignTasks(campaignDelegateIds).values.map(_.asInstanceOf[CampaignDelegateTask]).toList
  }

  def loadCampaignTasks(delegateIds : List[Int]) = {
    if (delegateIds.isEmpty) {
      Map.empty[Int, DelegateTask]
    } else {
      val connection = Meta.getConnection()
      try {
        val delegateIdPhrase = delegateIds.mkString(",")
        val preparedStatement = connection.prepareStatement(s"SELECT * FROM $CAMPAIGN_DELEGATE_TASK_TABLE WHERE delegate IN ($delegateIdPhrase)")
        val resultSet = preparedStatement.executeQuery()

        val result = mutable.Map[Int, DelegateTask]() //key delegateId

        val campaignCache = mutable.Map[Int, Campaign]()

        while (resultSet.next()) {
          val delegateId = resultSet.getInt("delegate")
          val campaignId = resultSet.getInt("campaign")
          val campaign = campaignCache.getOrElseUpdate(campaignId, CampaignSource.loadCampaignById(campaignId).get)
          val startCycle = resultSet.getInt("start_cycle")

          result.put(delegateId, DelegateTask.campaign(startCycle, campaign))
        }

        resultSet.close()
        preparedStatement.close()

        result.toMap
      } finally {
        connection.close()
      }
    }

  }

  def loadAircraftModelTasks(delegateIds : List[Int]) : Map[Int, DelegateTask] = {
    if (delegateIds.isEmpty) {
      Map.empty[Int, DelegateTask]
    } else {
      val connection = Meta.getConnection()
      try {
        val delegateIdPhrase = delegateIds.mkString(",")
        val preparedStatement = connection.prepareStatement(s"SELECT * FROM $AIRCRAFT_MODEL_DELEGATE_TASK_TABLE WHERE delegate IN ($delegateIdPhrase)")
        val resultSet = preparedStatement.executeQuery()

        val result = mutable.Map[Int, DelegateTask]()
        val modelNameCache = mutable.Map[Int, String]()

        while (resultSet.next()) {
          val delegateId = resultSet.getInt("delegate")
          val modelId = resultSet.getInt("aircraft_model_id")
          val startCycle = resultSet.getInt("start_cycle")
          val modelName = modelNameCache.getOrElseUpdate(modelId, ModelSource.loadModelById(modelId).map(_.name).getOrElse(s"Unknown ($modelId)"))
          result.put(delegateId, DelegateTask.aircraftModel(startCycle, modelId, modelName))
        }

        resultSet.close()
        preparedStatement.close()
        result.toMap
      } finally {
        connection.close()
      }
    }
  }

  private[this] def saveAircraftModelTasks(delegates : List[BusyDelegate]) : Unit = {
    val connection = Meta.getConnection()
    val preparedStatement = connection.prepareStatement(s"INSERT INTO $AIRCRAFT_MODEL_DELEGATE_TASK_TABLE (delegate, aircraft_model_id, start_cycle) VALUES(?,?,?)")
    try {
      delegates.foreach { delegate =>
        preparedStatement.setInt(1, delegate.id)
        preparedStatement.setInt(2, delegate.assignedTask.asInstanceOf[AircraftModelDelegateTask].modelId)
        preparedStatement.setInt(3, delegate.assignedTask.getStartCycle)
        preparedStatement.executeUpdate()
      }
    } finally {
      preparedStatement.close()
      connection.close()
    }
  }

  def loadAircraftModelDelegatesByAirlineAndModel(airlineId : Int, modelId : Int) : List[BusyDelegate] = {
    val connection = Meta.getConnection()
    try {
      val preparedStatement = connection.prepareStatement(
        s"SELECT bd.id, bd.available_cycle, amdt.aircraft_model_id, amdt.start_cycle " +
        s"FROM $BUSY_DELEGATE_TABLE bd " +
        s"JOIN $AIRCRAFT_MODEL_DELEGATE_TASK_TABLE amdt ON bd.id = amdt.delegate " +
        s"WHERE bd.airline = ? AND amdt.aircraft_model_id = ?"
      )
      preparedStatement.setInt(1, airlineId)
      preparedStatement.setInt(2, modelId)
      val resultSet = preparedStatement.executeQuery()

      val result = mutable.ListBuffer[BusyDelegate]()
      val modelName = ModelSource.loadModelById(modelId).map(_.name).getOrElse(s"Unknown ($modelId)")
      val airline = AirlineCache.getAirline(airlineId).get

      while (resultSet.next()) {
        val delegateId = resultSet.getInt("id")
        val startCycle = resultSet.getInt("start_cycle")
        val availableCycleObject = resultSet.getObject("available_cycle")
        val availableCycle = if (availableCycleObject == null) None else Some(availableCycleObject.asInstanceOf[Int])
        val task = DelegateTask.aircraftModel(startCycle, modelId, modelName)
        result.append(BusyDelegate(airline, task, availableCycle, delegateId))
      }

      resultSet.close()
      preparedStatement.close()
      result.toList
    } finally {
      connection.close()
    }
  }

  /**
    *
    * @param campaigns
    * @return key - delegateId
    */
  def loadBusyDelegatesByCampaigns(campaigns : List[Campaign]) : Map[Campaign, List[BusyDelegate]] = {
    if (campaigns.isEmpty) {
      Map.empty
    } else {
      val connection = Meta.getConnection()
      try {
        val campaignsById = campaigns.map(entry => (entry.id, entry)).toMap
        val campaignIdPhrase = campaignsById.keys.mkString(",")
        val taskPreparedStatement = connection.prepareStatement(s"SELECT * FROM $CAMPAIGN_DELEGATE_TASK_TABLE WHERE campaign IN ($campaignIdPhrase)")
        val taskResultSet = taskPreparedStatement.executeQuery()

        val taskByDelegateId = mutable.Map[Int, CampaignDelegateTask]()
        while (taskResultSet.next()) {
          //val delegateId = resultSet.getInt("delegate")
          val campaignId = taskResultSet.getInt("campaign")
          val campaign = campaignsById(campaignId)
          val startCycle = taskResultSet.getInt("start_cycle")
          taskByDelegateId.put(taskResultSet.getInt("delegate"), DelegateTask.campaign(startCycle, campaign))
        }
        taskResultSet.close()
        taskPreparedStatement.close()

        if (taskByDelegateId.isEmpty) {
          Map.empty
        } else {
          val delegatePreparedStatement = connection.prepareStatement(s"SELECT * FROM $BUSY_DELEGATE_TABLE WHERE id IN (${taskByDelegateId.keys.mkString(",")})")
          val delegateResultSet = delegatePreparedStatement.executeQuery()
          val result = mutable.Map[Campaign, ListBuffer[BusyDelegate]]()
          while (delegateResultSet.next()) {
            //val delegateId = resultSet.getInt("delegate")
            val airlineId = delegateResultSet.getInt("airline")
            val delegateId = delegateResultSet.getInt("id")
            val availableCycleObject = delegateResultSet.getObject("available_cycle")
            val availableCycle = if (availableCycleObject == null) None else Some(availableCycleObject.asInstanceOf[Int])

            val task = taskByDelegateId(delegateId)
            val delegate = BusyDelegate(AirlineCache.getAirline(airlineId).get, task, availableCycle, delegateId)

            result.getOrElseUpdate(task.campaign, ListBuffer[BusyDelegate]()).append(delegate)
          }
          delegateResultSet.close()
          delegatePreparedStatement.close()
          result.view.mapValues(_.toList).toMap
        }
      } finally {
        connection.close()
      }
    }
  }

  def updateBusyDelegateAvailableCycle(delegates : List[BusyDelegate]) : Unit = {
    val connection = Meta.getConnection()
    val preparedStatement = connection.prepareStatement(s"UPDATE $BUSY_DELEGATE_TABLE SET available_cycle = ? WHERE id = ?")
    try {
      delegates.foreach { delegate =>
        delegate.availableCycle match {
          case Some(availableCycle) => preparedStatement.setInt(1, availableCycle)
          case None => preparedStatement.setNull(1, Types.INTEGER)
        }
        preparedStatement.setInt(2, delegate.id)
        preparedStatement.executeUpdate()
      }
    } finally {
      preparedStatement.close()
      connection.close()
    }
  }

  def saveBusyDelegates(delegates : List[BusyDelegate]) : Unit = {
    val connection = Meta.getConnection()
    val preparedStatement = connection.prepareStatement("INSERT INTO " + BUSY_DELEGATE_TABLE + "(airline, task_type, available_cycle) VALUES(?,?,?)", Statement.RETURN_GENERATED_KEYS)
    try {
      delegates.foreach { delegate =>
        preparedStatement.setInt(1, delegate.airline.id)
        preparedStatement.setInt(2, delegate.assignedTask.getTaskType.id)
        delegate.availableCycle match {
          case Some(availableCycle) => preparedStatement.setInt(3, availableCycle)
          case None => preparedStatement.setNull(3, Types.INTEGER)
        }

        val updateCount = preparedStatement.executeUpdate()
        if (updateCount > 0) {
          val generatedKeys = preparedStatement.getGeneratedKeys
          if (generatedKeys.next()) {
            val generatedId = generatedKeys.getInt(1)
            //  println("Id is " + generatedId)
            //try to save assigned airplanes if any
            delegate.id = generatedId

          }
        }
      }
      saveDelegateTasks(delegates)
    } finally {
      preparedStatement.close()
      connection.close()
    }
  }


  def deleteBusyDelegates(delegates : List[BusyDelegate]) : Unit = {
    delegates.foreach { delegate =>
      deleteBusyDelegateByCriteria(List(("id", "=", delegate.id)))
    }
  }

  private[this] def saveDelegateTasks(delegates : List[BusyDelegate]) = {
    delegates.groupBy(_.assignedTask.getTaskType).foreach {
      case (taskType, delegatesOfThisTaskType) => {
        taskType match {
          case DelegateTaskType.COUNTRY =>
            saveCountryTasks(delegatesOfThisTaskType)
          case DelegateTaskType.CAMPAIGN =>
            saveCampaignTasks(delegatesOfThisTaskType)
          case DelegateTaskType.MANAGER_BASE =>
            // no per-task table; task_type in busy_delegate is sufficient
          case DelegateTaskType.MANAGER_AIRCRAFT_MODEL =>
            saveAircraftModelTasks(delegatesOfThisTaskType)
        }
      }
    }
  }

  private[this] def saveCountryTasks(delegates : List[BusyDelegate]) = {
    val connection = Meta.getConnection()
    val preparedStatement = connection.prepareStatement("INSERT INTO " + COUNTRY_DELEGATE_TASK_TABLE + "(delegate, country_code, start_cycle) VALUES(?,?,?)")
    try {
      delegates.foreach { delegate =>
        preparedStatement.setInt(1, delegate.id)
        preparedStatement.setString(2, delegate.assignedTask.asInstanceOf[CountryDelegateTask].country.countryCode)
        preparedStatement.setInt(3, delegate.assignedTask.getStartCycle)

        preparedStatement.executeUpdate()
      }
    } finally {
      preparedStatement.close()
      connection.close()
    }
  }

  private[this] def saveCampaignTasks(delegates : List[BusyDelegate]) = {
    val connection = Meta.getConnection()
    val preparedStatement = connection.prepareStatement("INSERT INTO " + CAMPAIGN_DELEGATE_TASK_TABLE + "(delegate, campaign, start_cycle) VALUES(?,?,?)")
    try {
      delegates.foreach { delegate =>
        preparedStatement.setInt(1, delegate.id)
        preparedStatement.setInt(2, delegate.assignedTask.asInstanceOf[CampaignDelegateTask].campaign.id)
        preparedStatement.setInt(3, delegate.assignedTask.getStartCycle)

        preparedStatement.executeUpdate()
      }
    } finally {
      preparedStatement.close()
      connection.close()
    }
  }  
  
  def countManagerBaseDelegatesByAirline(airlineId : Int) : Int = {
    val connection = Meta.getConnection()
    try {
      val preparedStatement = connection.prepareStatement(
        s"SELECT COUNT(*) FROM $BUSY_DELEGATE_TABLE WHERE airline = ? AND task_type = ?"
      )
      preparedStatement.setInt(1, airlineId)
      preparedStatement.setInt(2, DelegateTaskType.MANAGER_BASE.id)
      val rs = preparedStatement.executeQuery()
      val count = if (rs.next()) rs.getInt(1) else 0
      rs.close()
      preparedStatement.close()
      count
    } finally {
      connection.close()
    }
  }

  def deleteManagerBaseDelegates(airlineId : Int, count : Int) : Unit = {
    if (count <= 0) return
    val connection = Meta.getConnection()
    try {
      val preparedStatement = connection.prepareStatement(
        s"DELETE FROM $BUSY_DELEGATE_TABLE WHERE airline = ? AND task_type = ? LIMIT ?"
      )
      preparedStatement.setInt(1, airlineId)
      preparedStatement.setInt(2, DelegateTaskType.MANAGER_BASE.id)
      preparedStatement.setInt(3, count)
      preparedStatement.executeUpdate()
      preparedStatement.close()
    } finally {
      connection.close()
    }
  }

  def deleteBusyDelegateByCriteria(criteria : List[(String, String, Any)]) = {
      //open the hsqldb
    val connection = Meta.getConnection()
    try {
      var queryString = "DELETE FROM " + BUSY_DELEGATE_TABLE
      
      if (!criteria.isEmpty) {
        queryString += " WHERE "
        for (i <- 0 until criteria.size - 1) {
          queryString += criteria(i)._1 + criteria(i)._2 + " ? AND "
        }
        queryString += criteria.last._1 + criteria.last._2 + " ?"
      }
      
      val preparedStatement = connection.prepareStatement(queryString)
      
      for (i <- 0 until criteria.size) {
        preparedStatement.setObject(i + 1, criteria(i)._3)
      }
      
      val deletedCount = preparedStatement.executeUpdate()
      
      preparedStatement.close()
      println("Deleted " + deletedCount + " busy delegate records")
      deletedCount
    } finally {
      connection.close()
    }
  }



  def loadCountryDelegateByAirlineAndCountry(airlineId : Int, countryCode : String) : List[BusyDelegate] = {
    loadCountryDelegateByAirline(airlineId).get(countryCode) match {
      case Some(delegates) => delegates
      case None => List.empty
    }
  }

  /**
    * Batch load of total delegate levels per airline for a given country.
    * Used by CountryAirlineTitle to compute top titles without N+1 queries.
    * @return Map[airlineId, totalDelegateLevel]
    */
  def loadCountryDelegateLevelsByCountry(countryCode: String, currentCycle: Int): Map[Int, Int] = {
    val connection = Meta.getConnection()
    try {
      val preparedStatement = connection.prepareStatement(
        s"SELECT bd.airline, cdt.start_cycle FROM $BUSY_DELEGATE_TABLE bd " +
        s"JOIN $COUNTRY_DELEGATE_TASK_TABLE cdt ON bd.id = cdt.delegate " +
        s"WHERE cdt.country_code = ?"
      )
      preparedStatement.setString(1, countryCode)
      val resultSet = preparedStatement.executeQuery()

      val levelThresholds = LevelingDelegateTask.LEVEL_CYCLE_THRESHOLDS
      def computeLevel(startCycle: Int): Int = {
        val taskDuration = currentCycle - startCycle
        levelThresholds.count(_ <= taskDuration)
      }

      val result = mutable.Map[Int, Int]()
      while (resultSet.next()) {
        val airlineId = resultSet.getInt("airline")
        val startCycle = resultSet.getInt("start_cycle")
        val level = computeLevel(startCycle)
        result.put(airlineId, result.getOrElse(airlineId, 0) + level)
      }

      resultSet.close()
      preparedStatement.close()
      result.toMap
    } finally {
      connection.close()
    }
  }


  /**
    *
    * @param airlineId
    * @return key - country code
    */
  def loadCountryDelegateByAirline(airlineId : Int) : Map[String, List[BusyDelegate]] = {
    val result = loadBusyDelegatesByCriteria(List(("airline", "=", airlineId), ("task_type", "=", DelegateTaskType.COUNTRY.id)))
    result.get(airlineId) match {
      case Some(allCountryDelegates) => allCountryDelegates.groupBy(_.assignedTask.asInstanceOf[CountryDelegateTask].country.countryCode)
      case None => Map.empty
    }
  }
}