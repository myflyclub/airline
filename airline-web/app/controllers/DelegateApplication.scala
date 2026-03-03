package controllers

import com.patson.data.{CycleSource, DelegateSource}
import com.patson.data.airplane.ModelSource
import com.patson.model._
import com.patson.util.CountryCache
import controllers.AuthenticationObject.AuthenticatedAirline
import javax.inject.Inject
import play.api.mvc.{request, _}
import play.api.libs.json._


class DelegateApplication @Inject()(cc: ControllerComponents) extends AbstractController(cc) {
  def getDelegateInfo(airlineId : Int) = AuthenticatedAirline(airlineId) { request =>
    Ok(Json.toJson(request.user.getDelegateInfo()))
  }

  def getCountryDelegates(countryCode : String, airlineId : Int) = AuthenticatedAirline(airlineId) { request =>
    val multiplier = AirlineCountryRelationship.getDelegateBonusMultiplier(CountryCache.getCountry(countryCode).get)
    implicit val writes = new CountryDelegateWrites(CycleSource.loadCycle())
    Ok(Json.obj("delegates" -> DelegateSource.loadCountryDelegateByAirlineAndCountry(airlineId, countryCode), "multiplier" -> multiplier))
  }

  def updateCountryDelegates(countryCode : String, airlineId : Int) = AuthenticatedAirline(airlineId) { request =>
    val airline = request.user
    val delegateCount = request.body.asInstanceOf[AnyContentAsJson].json.\("delegateCount").as[Int]
    val existingDelegates = DelegateSource.loadCountryDelegateByAirlineAndCountry(airlineId, countryCode)
    val delta = delegateCount - existingDelegates.length

    if (delegateCount < 0 || delta > airline.getDelegateInfo().permanentAvailableCount) {
      BadRequest(s"Invalid delegate value $delegateCount")
    } else {
      if (delta < 0) { //unassign the most junior ones first
        DelegateSource.deleteBusyDelegates(existingDelegates.sortBy(_.assignedTask.getStartCycle).takeRight(-delta))
      } else if (delta > 0) {
        val task = DelegateTask.country(CycleSource.loadCycle(), CountryCache.getCountry(countryCode).get)
        DelegateSource.saveBusyDelegates((0 until delta).map(_ => BusyDelegate(airline, task, None)).toList)
      }
      Ok(Json.obj())
    }
  }

  def getAircraftModelDelegates(modelId : Int, airlineId : Int) = AuthenticatedAirline(airlineId) { request =>
    val currentCycle = CycleSource.loadCycle()
    implicit val writes = new AircraftModelDelegateWrites(currentCycle)
    val delegates = DelegateSource.loadAircraftModelDelegatesByAirlineAndModel(airlineId, modelId)
    val delegateInfo = request.user.getDelegateInfo()
    Ok(Json.obj(
      "delegates" -> Json.toJson(delegates),
      "availableCount" -> delegateInfo.permanentAvailableCount,
      "maxManagers" -> AircraftModelDelegateTask.MAX_MANAGERS_PER_MODEL
    ))
  }

  def updateAircraftModelDelegates(modelId : Int, airlineId : Int) = AuthenticatedAirline(airlineId) { request =>
    val airline = request.user
    val delegateCount = request.body.asInstanceOf[AnyContentAsJson].json.\("delegateCount").as[Int]
    val existingDelegates = DelegateSource.loadAircraftModelDelegatesByAirlineAndModel(airlineId, modelId).sortBy(_.assignedTask.getStartCycle)
    val delta = delegateCount - existingDelegates.length

    if (delegateCount < 0 || delegateCount > AircraftModelDelegateTask.MAX_MANAGERS_PER_MODEL) {
      BadRequest(s"Invalid manager count $delegateCount (max ${AircraftModelDelegateTask.MAX_MANAGERS_PER_MODEL})")
    } else if (delta > airline.getDelegateInfo().permanentAvailableCount) {
      BadRequest(s"Not enough available delegates")
    } else if (delta < 0) {
      existingDelegates.takeRight(delta * -1).foreach { delegate =>
        DelegateSource.deleteBusyDelegateByCriteria(List(("id", "=", delegate.id)))
      }
      Ok(Json.obj())
    } else if (delta > 0) {
      ModelSource.loadModelById(modelId) match {
        case Some(model) =>
          val delegateTask = DelegateTask.aircraftModel(CycleSource.loadCycle(), modelId, model.name)
          val newDelegates = (0 until delta).map(_ => BusyDelegate(airline, delegateTask, None))
          DelegateSource.saveBusyDelegates(newDelegates.toList)
          Ok(Json.obj())
        case None =>
          BadRequest(s"Aircraft model $modelId not found")
      }
    } else {
      Ok(Json.obj()) // delta == 0
    }
  }

}

class CountryDelegateWrites(currentCycle : Int) extends Writes[BusyDelegate] {
  override def writes(countryDelegate: BusyDelegate): JsValue = {
    var countryDelegateJson = Json.toJson(countryDelegate)(new BusyDelegateWrites(currentCycle)).asInstanceOf[JsObject]
    val task = countryDelegate.assignedTask.asInstanceOf[CountryDelegateTask]
    countryDelegateJson = countryDelegateJson +
      ("countryCode" -> JsString(task.country.countryCode)) +
      ("startCycle" -> JsNumber(task.startCycle)) +
      ("level" -> JsNumber(task.level(currentCycle))) +
      ("levelDescription" -> JsString(task.levelDescription(currentCycle)))

    task.nextLevelCycleCount(currentCycle).foreach {
      value => countryDelegateJson = countryDelegateJson + ("nextLevelCycleCount" -> JsNumber(value))
    }

    countryDelegateJson
  }
}

class AircraftModelDelegateWrites(currentCycle : Int) extends Writes[BusyDelegate] {
  override def writes(delegate: BusyDelegate): JsValue = {
    var json = Json.toJson(delegate)(new BusyDelegateWrites(currentCycle)).asInstanceOf[JsObject]
    val task = delegate.assignedTask.asInstanceOf[AircraftModelDelegateTask]
    json = json +
      ("modelId" -> JsNumber(task.modelId)) +
      ("startCycle" -> JsNumber(task.startCycle)) +
      ("level" -> JsNumber(task.level(currentCycle))) +
      ("levelDescription" -> JsString(task.levelDescription(currentCycle)))

    task.nextLevelCycleCount(currentCycle).foreach {
      value => json = json + ("nextLevelCycleCount" -> JsNumber(value))
    }

    json
  }
}
