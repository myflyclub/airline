package controllers

import com.patson.data.{AirlineSource, CycleSource, LinkSource, LoyalistSource, NotificationSource, TutorialSource}
import com.patson.model.{Airline, Notification, NotificationCategory}
import com.patson.model.tutorial.Tutorial

import scala.collection.mutable.ListBuffer

case class Prompts(notices: ListBuffer[Notification], tutorials: ListBuffer[AirlineTutorial]) {
  def append(that: Prompts): Unit = {
    this.notices.appendAll(that.notices)
    this.tutorials.appendAll(that.tutorials)
  }
}
case class AirlineTutorial(airline: Airline, tutorial: Tutorial)

object PromptUtil {

  def getPrompts(airline: Airline): Prompts = {
    val currentCycle = CycleSource.loadCycle()
    val result = Prompts(ListBuffer(), ListBuffer())
    result.notices.appendAll(getLevelUpNotifications(airline, currentCycle))
    result.notices.appendAll(getLoyalistNotifications(airline, currentCycle))
    result.notices.appendAll(getTrackingNotifications(airline))
    if (!airline.isSkipTutorial) {
      val completedTutorials = TutorialSource.loadCompletedTutorialsByAirline(airline.id)
      result.tutorials.appendAll(getTutorialSteps(airline, completedTutorials))
    }
    result
  }

  private def getLevelUpNotifications(airline: Airline, currentCycle: Int): List[Notification] = {
    if (airline.airlineGrade.level > airline.notifiedLevel) {
      AirlineSource.saveNotifiedLevel(airline.id, airline.airlineGrade.level)
      val description = airline.airlineGrade.description
      val n = NotificationSource.insertNotificationReturning(
        Notification(airline.id, NotificationCategory.LEVEL_UP,
          s"${airline.name} reached level ${airline.airlineGrade.level}: $description",
          currentCycle, level = Some(airline.airlineGrade.level))
      )
      List(n)
    } else List.empty
  }

  private def getLoyalistNotifications(airline: Airline, currentCycle: Int): List[Notification] = {
    val totalLoyalist = LoyalistSource.loadLoyalistsByCriteria(List(("airline", airline.id))).map(_.amount).sum
    val currentLevel = Math.log10(totalLoyalist.toDouble).toInt
    if (currentLevel > airline.notifiedLoyalistLevel) {
      AirlineSource.saveNotifiedLoyalistLevel(airline.id, currentLevel)
      val threshold = Math.pow(10, currentLevel).toLong
      val n = NotificationSource.insertNotificationReturning(
        Notification(airline.id, NotificationCategory.LOYALIST,
          s"Reached $threshold Loyalists!",
          currentCycle, level = Some(currentLevel))
      )
      List(n)
    } else List.empty
  }

  private def getTrackingNotifications(airline: Airline): List[Notification] =
    NotificationSource.loadUnreadByCategories(airline.id,
      Seq(NotificationCategory.GAME_OVER, NotificationCategory.OLYMPICS_PRIZE))

  def getTutorialSteps(airline: Airline, completedTutorials: List[Tutorial]): List[AirlineTutorial] = {
    val completed = completedTutorials.map(t => t.category + ":" + t.id).toSet

    val steps = ListBuffer[AirlineTutorial]()
    val routes = LinkSource.loadFlightLinksByCriteria(List(("airline", airline.id)), LinkSource.FULL_LOAD)

    // Step 1: No HQ — guide to airport
    if (!completed("worldMap:viewAirport") && airline.getHeadQuarter().isEmpty) {
      steps += AirlineTutorial(airline, Tutorial("worldMap", "viewAirport", Some("[data-link=map]")))
    }
    // Step 2: Has HQ, no links — guide to route planning
    else if (!completed("planLink:setupLink") && airline.getHeadQuarter().isDefined && routes.isEmpty) {
      steps += AirlineTutorial(airline, Tutorial("planLink", "setupLink", Some("[data-link=flights]")))
    }
    // Step 3: Has routes, no airplane assigned — guide to fleet
    else if (!completed("airline:fleet") && routes.nonEmpty && !routes.exists(_.getAssignedAirplanes().nonEmpty)) {
      steps += AirlineTutorial(airline, Tutorial("airline", "fleet", Some("[data-link=hangar]")))
    }
    // Step 4: Has everything running, nudge toward oil futures
    else if (!completed("oil:intro") && airline.airlineGrade.level >= 2) {
      steps += AirlineTutorial(airline, Tutorial("oil", "intro", Some("[data-link=oil]")))
    }
    steps.toList
  }
}
