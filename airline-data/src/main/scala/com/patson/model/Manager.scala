package com.patson.model

import com.patson.model.campaign.Campaign

case class Manager(airline : Airline, assignedTask : ManagerTask, availableCycle : Option[Int], var id : Int = 0) extends IdObject {
  val taskCompleted = availableCycle.isDefined
}

object Manager {
  val TOOLTIP = List(
    "Managers are the key currency of the game. You need them to build new bases and open links. You get more as your reputation increases.",
    s"Any \"actioning\" manager generates ${ManagerBaseTask.GENERATION_RATE} action points per week / cycle, accruing up to a maximum of ${ManagerBaseTask.MAX_CYCLES_STORED_THRESHOLD} weeks / cycles * actioning manager.",
    s"When assigned to tasks, such as improving county relations \"public affairs\" or manufacturer relations, they gain experience and level up automatically at the rate of ${LevelingManagerTask.LEVEL_CYCLE_THRESHOLDS(0)}, ${LevelingManagerTask.LEVEL_CYCLE_THRESHOLDS(1)}, ${LevelingManagerTask.LEVEL_CYCLE_THRESHOLDS(2)}, ${LevelingManagerTask.LEVEL_CYCLE_THRESHOLDS(3)} weeks for each level respectively.",
  )
}

abstract class ManagerTask(startCycle : Int, taskType : ManagerTaskType.Value) {
  val description : String
  val getTaskType = taskType
  val getStartCycle = startCycle
}

object ManagerTask {
  val country = (startCycle: Int, country: Country) => CountryManagerTask(startCycle, country)
  val campaign = (startCycle: Int, campaign: Campaign) => CampaignManagerTask(startCycle, campaign)
  val aircraftModel = (startCycle: Int, modelId: Int, modelName: String) => AircraftModelManagerTask(startCycle, modelId, modelName)
}

case class CountryManagerTask(startCycle : Int, country: Country) extends LevelingManagerTask(startCycle, ManagerTaskType.COUNTRY) {
  override val description: String = s"Develop relationship with ${country.name}"
}

object CountryManagerTask {
  val MAX_MANAGERS_PER_COUNTRY = 6
}

case class CampaignManagerTask(startCycle : Int, campaign : Campaign) extends LevelingManagerTask(startCycle, ManagerTaskType.CAMPAIGN) {
  override val description: String = s"Advertising in the area around ${campaign.principalAirport.displayText}"
}

object LevelingManagerTask {
  val LEVEL_CYCLE_THRESHOLDS: List[Int] = List(4, 4 + 1 * 48, 4 + 3 * 48, 4 + 10 * 48)
}

abstract class LevelingManagerTask(startCycle : Int, managerTaskType: ManagerTaskType.Value) extends ManagerTask(startCycle, managerTaskType) {

  val LEVEL_CYCLE_THRESHOLDS: List[Int] = LevelingManagerTask.LEVEL_CYCLE_THRESHOLDS
  val level = (currentCycle: Int) => {
    var levelWalker = 0
    val taskDuration = currentCycle - startCycle
    LEVEL_CYCLE_THRESHOLDS.find(threshold => {
      val higherThanThisLevel = taskDuration >= threshold
      if (higherThanThisLevel) {
        levelWalker = levelWalker + 1
      }
      !higherThanThisLevel
    })
    levelWalker
  }
  val nextLevelCycleCount = (currentCycle: Int) => {
    val currentLevel : Int = level(currentCycle)
    val taskDuration = currentCycle - startCycle
    if (currentLevel >= LEVEL_CYCLE_THRESHOLDS.length) //max level already
      None
    else
      Some(LEVEL_CYCLE_THRESHOLDS(currentLevel) - taskDuration)
  }

  val levelDescription = (currentCycle: Int) => {
    level(currentCycle) match {
      case 0 => "Trainee"
      case 1 => "Novice"
      case 2 => "Junior"
      case 3 => "Senior"
      case _ => "Director"
    }

  }
}

case class ManagerBaseTask() extends ManagerTask(0, ManagerTaskType.MANAGER_BASE) {
  override val description: String = "Managing base"
}

object ManagerBaseTask {
  val GENERATION_RATE = 0.1
  val INEFFICIENT_CYCLE_THRESHOLD = 8 * 2
  val MAX_CYCLES_STORED_THRESHOLD = 24 * 2
}

case class AircraftModelManagerTask(startCycle : Int, modelId : Int, modelName : String) extends LevelingManagerTask(startCycle, ManagerTaskType.MANAGER_AIRCRAFT_MODEL) {
  override val description: String = s"Managing manufacturer relationship for $modelName"
}

object AircraftModelManagerTask {
  val MAX_MANAGERS_PER_MODEL = 2
}

object ManagerTaskType extends Enumeration {
  type ManagerTaskType = Value
  val COUNTRY, CAMPAIGN, MANAGER_BASE, MANAGER_AIRCRAFT_MODEL = Value
}