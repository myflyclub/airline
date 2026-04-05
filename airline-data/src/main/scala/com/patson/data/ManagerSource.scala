package com.patson.data

import java.sql.{Statement, Types}

import com.patson.data.Constants._
import com.patson.data.airplane.ModelSource
import com.patson.model._
import com.patson.model.campaign.Campaign
import com.patson.util.{AirlineCache, AirportCache, CountryCache}

import scala.collection.mutable
import scala.collection.mutable.ListBuffer


object ManagerSource {
  private[this] val BASE_BUSY_MANAGER_QUERY = "SELECT * FROM " + BUSY_DELEGATE_TABLE
  val TOOLTIP_MANAGERS = List(
    "Managers allow you to open more bases, more routes, lower aircraft prices, and more.",
    "<b>Actioning</b>: Generate action points."
  )


  /**
    * Load either delegates with task in progress or on cool down
    * @param airlineId
    * @return
    */
  def loadBusyManagersByAirline(airlineId : Int) : List[Manager] = {
    loadBusyManagersByCriteria(List(("airline", "=", airlineId))).get(airlineId).getOrElse(List.empty)
  }
  

  def loadBusyManagersByCriteria(criteria : List[(String, String, Any)]) = {
      var queryString = BASE_BUSY_MANAGER_QUERY
      
      if (!criteria.isEmpty) {
        queryString += " WHERE "
        for (i <- 0 until criteria.size - 1) {
          queryString += criteria(i)._1 + criteria(i)._2 + " ? AND "
        }
        queryString += criteria.last._1 + criteria.last._2 + " ?"
      }
      loadBusyManagersByQueryString(queryString, criteria.map(_._3))
  }

  /**
    *
    * @param queryString
    * @param parameters
    * @return key is airline Id
    */
  def loadBusyManagersByQueryString(queryString : String, parameters : List[Any]) : Map[Int, List[Manager]]= {
    val connection = Meta.getConnection()
    try {
        val preparedStatement = connection.prepareStatement(queryString)
        
        for (i <- 0 until parameters.size) {
          preparedStatement.setObject(i + 1, parameters(i))
        }

        
        val resultSet = preparedStatement.executeQuery()
        
        val result = mutable.Map[Int, ListBuffer[ManagerLoadInfo]]() //key: Airline id


        while (resultSet.next()) {
          val airlineId = resultSet.getInt("airline")
          val delegateId = resultSet.getInt("id")
          val taskType = ManagerTaskType(resultSet.getInt("task_type"))
          val availableCycleObject = resultSet.getObject("available_cycle")
          val availableCycle = if (availableCycleObject == null) None else Some(availableCycleObject.asInstanceOf[Int])

          result.getOrElseUpdate(airlineId, ListBuffer[ManagerLoadInfo]()).append(ManagerLoadInfo(delegateId, taskType, availableCycle))
        }
        
        resultSet.close()
        preparedStatement.close()
        
        result.toList.map {
          case (airlineId, delegateInfoEntries: ListBuffer[ManagerLoadInfo]) => {
            val airline : Airline = AirlineCache.getAirline(airlineId).get
            val managerTaskByDelegateId = loadManagerTasks(delegateInfoEntries.map {
              case ManagerLoadInfo(delegateId, taskType, _) => (delegateId, taskType)
            }.toMap)

            val delegates : List[Manager] = delegateInfoEntries.toList.map {
              case (ManagerLoadInfo(delegateId, _, availableCycle)) => Manager(airline, managerTaskByDelegateId(delegateId), availableCycle, delegateId)
            }

            (airlineId, delegates)
          }
        }.toMap
      } finally {
        connection.close()
      }
  }

  case class ManagerLoadInfo(id : Int, taskType : ManagerTaskType.Value, availableCycle : Option[Int])

  /**
    *
    * @param managerIdAndTaskTypes
    * @return key delegate Id
    */
  def loadManagerTasks(managerIdAndTaskTypes : Map[Int, ManagerTaskType.Value]) : Map[Int, ManagerTask] = {
    val result = mutable.HashMap[Int, ManagerTask]()
    managerIdAndTaskTypes.toList.groupBy(_._2).foreach {
      case(taskType, grouped) => {
        val managerIdsOfThisTaskType = grouped.map(_._1)
        taskType match {
          case ManagerTaskType.COUNTRY =>
            result.addAll(loadCountryTasks(managerIdsOfThisTaskType))
          case ManagerTaskType.CAMPAIGN =>
            result.addAll(loadCampaignTasks(managerIdsOfThisTaskType))
          case ManagerTaskType.MANAGER_BASE =>
            managerIdsOfThisTaskType.foreach(id => result.put(id, ManagerBaseTask()))
          case ManagerTaskType.MANAGER_AIRCRAFT_MODEL =>
            result.addAll(loadAircraftModelTasks(managerIdsOfThisTaskType))
        }
      }
    }
    result.toMap
  }

  /**
    *
    * @param managerIds
    * @return key - managerId
    */
  def loadCountryTasks(managerIds : List[Int]) = {
    val connection = Meta.getConnection()
    try {
      val managerIdPhrase = managerIds.mkString(",")
      val preparedStatement = connection.prepareStatement(s"SELECT * FROM $COUNTRY_DELEGATE_TASK_TABLE WHERE delegate IN ($managerIdPhrase)")
      val resultSet = preparedStatement.executeQuery()

      val result = mutable.Map[Int, ManagerTask]() //key managerId


      while (resultSet.next()) {
        val delegateId = resultSet.getInt("delegate")
        val country = CountryCache.getCountry(resultSet.getString("country_code")).get
        val startCycle = resultSet.getInt("start_cycle")

        result.put(delegateId, ManagerTask.country(startCycle, country))
      }

      resultSet.close()
      preparedStatement.close()

      result.toMap
    } finally {
      connection.close()
    }

  }

  def loadCampaignCostsByAirlineId() : Map[Int, Int] = {
    val connection = Meta.getConnection()
    try {
      val preparedStatement = connection.prepareStatement(
        s"""
           |SELECT DISTINCT bd.airline, cdt.campaign
           |FROM $BUSY_DELEGATE_TABLE bd
           |JOIN $CAMPAIGN_DELEGATE_TASK_TABLE cdt ON bd.id = cdt.delegate
           |WHERE bd.task_type = ?
           |""".stripMargin
      )
      preparedStatement.setInt(1, ManagerTaskType.CAMPAIGN.id)

      val resultSet = preparedStatement.executeQuery()

      val campaignIdsByAirlineId = mutable.Map[Int, ListBuffer[Int]]()
      val campaignCache = mutable.Map[Int, Campaign]()

      while (resultSet.next()) {
        val airlineId = resultSet.getInt("airline")
        val campaignId = resultSet.getInt("campaign")
        campaignIdsByAirlineId.getOrElseUpdate(airlineId, ListBuffer[Int]()).append(campaignId)
      }

      resultSet.close()
      preparedStatement.close()

      campaignIdsByAirlineId.view.mapValues { campaignIds =>
        campaignIds.distinct.map { campaignId =>
          val campaign = campaignCache.getOrElseUpdate(campaignId, CampaignSource.loadCampaignById(campaignId).get)
          Campaign.getCost(campaign.populationCoverage)
        }.sum
      }.toMap
    } finally {
      connection.close()
    }
  }

  def loadCampaignTasks(delegateIds : List[Int]) = {
    if (delegateIds.isEmpty) {
      Map.empty[Int, ManagerTask]
    } else {
      val connection = Meta.getConnection()
      try {
        val delegateIdPhrase = delegateIds.mkString(",")
        val preparedStatement = connection.prepareStatement(s"SELECT * FROM $CAMPAIGN_DELEGATE_TASK_TABLE WHERE delegate IN ($delegateIdPhrase)")
        val resultSet = preparedStatement.executeQuery()

        val result = mutable.Map[Int, ManagerTask]() //key delegateId

        val campaignCache = mutable.Map[Int, Campaign]()

        while (resultSet.next()) {
          val delegateId = resultSet.getInt("delegate")
          val campaignId = resultSet.getInt("campaign")
          val campaign = campaignCache.getOrElseUpdate(campaignId, CampaignSource.loadCampaignById(campaignId).get)
          val startCycle = resultSet.getInt("start_cycle")

          result.put(delegateId, ManagerTask.campaign(startCycle, campaign))
        }

        resultSet.close()
        preparedStatement.close()

        result.toMap
      } finally {
        connection.close()
      }
    }

  }

  def loadAircraftModelTasks(delegateIds : List[Int]) : Map[Int, ManagerTask] = {
    if (delegateIds.isEmpty) {
      Map.empty[Int, ManagerTask]
    } else {
      val connection = Meta.getConnection()
      try {
        val delegateIdPhrase = delegateIds.mkString(",")
        val preparedStatement = connection.prepareStatement(s"SELECT * FROM $AIRCRAFT_MODEL_DELEGATE_TASK_TABLE WHERE delegate IN ($delegateIdPhrase)")
        val resultSet = preparedStatement.executeQuery()

        val result = mutable.Map[Int, ManagerTask]()
        val modelNameCache = mutable.Map[Int, String]()

        while (resultSet.next()) {
          val delegateId = resultSet.getInt("delegate")
          val modelId = resultSet.getInt("aircraft_model_id")
          val startCycle = resultSet.getInt("start_cycle")
          val modelName = modelNameCache.getOrElseUpdate(modelId, ModelSource.loadModelById(modelId).map(_.name).getOrElse(s"Unknown ($modelId)"))
          result.put(delegateId, ManagerTask.aircraftModel(startCycle, modelId, modelName))
        }

        resultSet.close()
        preparedStatement.close()
        result.toMap
      } finally {
        connection.close()
      }
    }
  }

  private[this] def saveAircraftModelTasks(managers : List[Manager]) : Unit = {
    val connection = Meta.getConnection()
    val preparedStatement = connection.prepareStatement(s"INSERT INTO $AIRCRAFT_MODEL_DELEGATE_TASK_TABLE (delegate, aircraft_model_id, start_cycle) VALUES(?,?,?)")
    try {
      managers.foreach { manager =>
        preparedStatement.setInt(1, manager.id)
        preparedStatement.setInt(2, manager.assignedTask.asInstanceOf[AircraftModelManagerTask].modelId)
        preparedStatement.setInt(3, manager.assignedTask.getStartCycle)
        preparedStatement.executeUpdate()
      }
    } finally {
      preparedStatement.close()
      connection.close()
    }
  }

  def loadAircraftModelDelegatesByAirlineAndModel(airlineId : Int, modelId : Int) : List[Manager] = {
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

      val result = mutable.ListBuffer[Manager]()
      val modelName = ModelSource.loadModelById(modelId).map(_.name).getOrElse(s"Unknown ($modelId)")
      val airline = AirlineCache.getAirline(airlineId).get

      while (resultSet.next()) {
        val delegateId = resultSet.getInt("id")
        val startCycle = resultSet.getInt("start_cycle")
        val availableCycleObject = resultSet.getObject("available_cycle")
        val availableCycle = if (availableCycleObject == null) None else Some(availableCycleObject.asInstanceOf[Int])
        val task = ManagerTask.aircraftModel(startCycle, modelId, modelName)
        result.append(Manager(airline, task, availableCycle, delegateId))
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
  def loadBusyManagersByCampaigns(campaigns: List[Campaign]) : Map[Campaign, List[Manager]] = {
    if (campaigns.isEmpty) {
      Map.empty
    } else {
      val connection = Meta.getConnection()
      try {
        val campaignsById = campaigns.map(entry => (entry.id, entry)).toMap
        val campaignIdPhrase = campaignsById.keys.mkString(",")
        val taskPreparedStatement = connection.prepareStatement(s"SELECT * FROM $CAMPAIGN_DELEGATE_TASK_TABLE WHERE campaign IN ($campaignIdPhrase)")
        val taskResultSet = taskPreparedStatement.executeQuery()

        val taskByDelegateId = mutable.Map[Int, CampaignManagerTask]()
        while (taskResultSet.next()) {
          //val delegateId = resultSet.getInt("delegate")
          val campaignId = taskResultSet.getInt("campaign")
          val campaign = campaignsById(campaignId)
          val startCycle = taskResultSet.getInt("start_cycle")
          taskByDelegateId.put(taskResultSet.getInt("delegate"), ManagerTask.campaign(startCycle, campaign))
        }
        taskResultSet.close()
        taskPreparedStatement.close()

        if (taskByDelegateId.isEmpty) {
          Map.empty
        } else {
          val delegatePreparedStatement = connection.prepareStatement(s"SELECT * FROM $BUSY_DELEGATE_TABLE WHERE id IN (${taskByDelegateId.keys.mkString(",")})")
          val delegateResultSet = delegatePreparedStatement.executeQuery()
          val result = mutable.Map[Campaign, ListBuffer[Manager]]()
          while (delegateResultSet.next()) {
            val airlineId = delegateResultSet.getInt("airline")
            val delegateId = delegateResultSet.getInt("id")
            val availableCycleObject = delegateResultSet.getObject("available_cycle")
            val availableCycle = if (availableCycleObject == null) None else Some(availableCycleObject.asInstanceOf[Int])

            val task = taskByDelegateId(delegateId)
            val manager = Manager(AirlineCache.getAirline(airlineId).get, task, availableCycle, delegateId)

            result.getOrElseUpdate(task.campaign, ListBuffer[Manager]()).append(manager)
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

  def saveBusyManagers(managers: List[Manager]) : Unit = {
    val connection = Meta.getConnection()
    val preparedStatement = connection.prepareStatement("INSERT INTO " + BUSY_DELEGATE_TABLE + "(airline, task_type, available_cycle) VALUES(?,?,?)", Statement.RETURN_GENERATED_KEYS)
    try {
      managers.foreach { manager =>
        preparedStatement.setInt(1, manager.airline.id)
        preparedStatement.setInt(2, manager.assignedTask.getTaskType.id)
        manager.availableCycle match {
          case Some(availableCycle) => preparedStatement.setInt(3, availableCycle)
          case None => preparedStatement.setNull(3, Types.INTEGER)
        }

        val updateCount = preparedStatement.executeUpdate()
        if (updateCount > 0) {
          val generatedKeys = preparedStatement.getGeneratedKeys
          if (generatedKeys.next()) {
            val generatedId = generatedKeys.getInt(1)
            //try to save assigned airplanes if any
            manager.id = generatedId

          }
        }
      }
      saveDelegateTasks(managers)
    } finally {
      preparedStatement.close()
      connection.close()
    }
  }


  def deleteBusyManagers(managers: List[Manager]) : Unit = {
    managers.foreach { manager =>
      deleteBusyDelegateByCriteria(List(("id", "=", manager.id)))
    }
  }

  private[this] def saveDelegateTasks(managers: List[Manager]) = {
    managers.groupBy(_.assignedTask.getTaskType).foreach {
      case (taskType, delegatesOfThisTaskType) => {
        taskType match {
          case ManagerTaskType.COUNTRY =>
            saveCountryTasks(delegatesOfThisTaskType)
          case ManagerTaskType.CAMPAIGN =>
            saveCampaignTasks(delegatesOfThisTaskType)
          case ManagerTaskType.MANAGER_BASE =>
            // no per-task table; task_type in busy_delegate is sufficient
          case ManagerTaskType.MANAGER_AIRCRAFT_MODEL =>
            saveAircraftModelTasks(delegatesOfThisTaskType)
        }
      }
    }
  }

  private[this] def saveCountryTasks(managers: List[Manager]) = {
    val connection = Meta.getConnection()
    val preparedStatement = connection.prepareStatement("INSERT INTO " + COUNTRY_DELEGATE_TASK_TABLE + "(delegate, country_code, start_cycle) VALUES(?,?,?)")
    try {
      managers.foreach { manager =>
        preparedStatement.setInt(1, manager.id)
        preparedStatement.setString(2, manager.assignedTask.asInstanceOf[CountryManagerTask].country.countryCode)
        preparedStatement.setInt(3, manager.assignedTask.getStartCycle)

        preparedStatement.executeUpdate()
      }
    } finally {
      preparedStatement.close()
      connection.close()
    }
  }

  private[this] def saveCampaignTasks(managers: List[Manager]) = {
    val connection = Meta.getConnection()
    val preparedStatement = connection.prepareStatement("INSERT INTO " + CAMPAIGN_DELEGATE_TASK_TABLE + "(delegate, campaign, start_cycle) VALUES(?,?,?)")
    try {
      managers.foreach { manager =>
        preparedStatement.setInt(1, manager.id)
        preparedStatement.setInt(2, manager.assignedTask.asInstanceOf[CampaignManagerTask].campaign.id)
        preparedStatement.setInt(3, manager.assignedTask.getStartCycle)

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
      preparedStatement.setInt(2, ManagerTaskType.MANAGER_BASE.id)
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
      preparedStatement.setInt(2, ManagerTaskType.MANAGER_BASE.id)
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



  def loadCountryDelegateByAirlineAndCountry(airlineId : Int, countryCode : String) : List[Manager] = {
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

      val levelThresholds = LevelingManagerTask.LEVEL_CYCLE_THRESHOLDS
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
  def loadCountryDelegateByAirline(airlineId : Int) : Map[String, List[Manager]] = {
    val result = loadBusyManagersByCriteria(List(("airline", "=", airlineId), ("task_type", "=", ManagerTaskType.COUNTRY.id)))
    result.get(airlineId) match {
      case Some(allCountryDelegates) => allCountryDelegates.groupBy(_.assignedTask.asInstanceOf[CountryManagerTask].country.countryCode)
      case None => Map.empty
    }
  }
}