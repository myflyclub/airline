package controllers

import com.patson.data.{CampaignSource, CycleSource, ManagerSource}
import com.patson.data.airplane.ModelSource
import com.patson.model._
import com.patson.model.campaign.Campaign
import com.patson.model.airplane.{DiscountReason, DiscountType, ModelDiscount}
import com.patson.util.CountryCache
import controllers.AuthenticationObject.AuthenticatedAirline
import javax.inject.Inject
import play.api.mvc.{request, _}
import play.api.libs.json._
import scala.math.BigDecimal.RoundingMode


class ManagerApplication @Inject()(cc: ControllerComponents) extends AbstractController(cc) {
  def getManagerInfo(airlineId : Int) = AuthenticatedAirline(airlineId) { request =>
    Ok(Json.toJson(request.user.getManagerInfo()))
  }

  def getCountryDelegates(countryCode : String, airlineId : Int) = AuthenticatedAirline(airlineId) { request =>
    val multiplier = AirlineCountryRelationship.getDelegateBonusMultiplier(CountryCache.getCountry(countryCode).get)
    implicit val writes = new CountryDelegateWrites(CycleSource.loadCycle())
    Ok(Json.obj(
      "delegates" -> ManagerSource.loadCountryDelegateByAirlineAndCountry(airlineId, countryCode),
      "multiplier" -> multiplier,
      "availableCount" -> request.user.getManagerInfo().availableCount
    ))
  }

  def updateCountryDelegates(countryCode : String, airlineId : Int) = AuthenticatedAirline(airlineId) { request =>
    val airline = request.user
    val delegateCount = request.body.asInstanceOf[AnyContentAsJson].json.\("delegateCount").as[Int]
    val existingDelegates = ManagerSource.loadCountryDelegateByAirlineAndCountry(airlineId, countryCode)
    val delta = delegateCount - existingDelegates.length

    if (delegateCount < 0) {
      BadRequest(s"Invalid manager value $delegateCount")
    } else {
      if (delta < 0) { //unassign the most junior ones first
        ManagerSource.deleteBusyDelegates(existingDelegates.sortBy(_.assignedTask.getStartCycle).takeRight(-delta))
      } else if (delta > 0) {
        val task = ManagerTask.country(CycleSource.loadCycle(), CountryCache.getCountry(countryCode).get)
        ManagerSource.saveBusyDelegates((0 until delta).map(_ => Manager(airline, task, None)).toList)
      }
      Ok(Json.obj())
    }
  }

  def getAircraftModelDelegates(modelId : Int, airlineId : Int) = AuthenticatedAirline(airlineId) { request =>
    val currentCycle = CycleSource.loadCycle()
    implicit val writes = new AircraftModelDelegateWrites(currentCycle)
    val delegates = ManagerSource.loadAircraftModelDelegatesByAirlineAndModel(airlineId, modelId)
    val delegateInfo = request.user.getManagerInfo()
    Ok(Json.obj(
      "delegates" -> Json.toJson(delegates),
      "availableCount" -> delegateInfo.availableCount,
      "maxManagers" -> AircraftModelManagerTask.MAX_MANAGERS_PER_MODEL
    ))
  }

  def updateAircraftModelDelegates(modelId : Int, airlineId : Int) = AuthenticatedAirline(airlineId) { request =>
    val airline = request.user
    val delegateCount = request.body.asInstanceOf[AnyContentAsJson].json.\("delegateCount").as[Int]
    val existingDelegates = ManagerSource.loadAircraftModelDelegatesByAirlineAndModel(airlineId, modelId).sortBy(_.assignedTask.getStartCycle)
    val delta = delegateCount - existingDelegates.length

    if (delegateCount < 0) {
      BadRequest(s"Invalid manager count $delegateCount (max ${AircraftModelManagerTask.MAX_MANAGERS_PER_MODEL})")
    } else if (delta > airline.getManagerInfo().availableCount) {
      BadRequest(s"Not enough available managers")
    } else if (delta < 0) {
      existingDelegates.takeRight(delta * -1).foreach { delegate =>
        ManagerSource.deleteBusyDelegateByCriteria(List(("id", "=", delegate.id)))
      }
      Ok(Json.obj())
    } else if (delta > 0) {
      ModelSource.loadModelById(modelId) match {
        case Some(model) =>
          val managerTask = ManagerTask.aircraftModel(CycleSource.loadCycle(), modelId, model.name)
          val newDelegates = (0 until delta).map(_ => Manager(airline, managerTask, None))
          ManagerSource.saveBusyDelegates(newDelegates.toList)
          Ok(Json.obj())
        case None =>
          BadRequest(s"Aircraft model $modelId not found")
      }
    } else {
      Ok(Json.obj()) // delta == 0
    }
  }

  def getLevelingManagers(airlineId: Int) = AuthenticatedAirline(airlineId) { request =>
    val allManagers = ManagerSource.loadBusyDelegatesByAirline(airlineId)
      .filter(_.assignedTask.isInstanceOf[LevelingManagerTask])

    val campaignById: Map[Int, Campaign] =
      if (allManagers.exists(_.assignedTask.isInstanceOf[CampaignManagerTask]))
        CampaignSource.loadCampaignsByCriteria(List(("airline", airlineId)), loadArea = true)
          .map(c => c.id -> c).toMap
      else Map.empty

    val discountsByModelId: Map[Int, List[ModelDiscount]] =
      if (allManagers.exists(_.assignedTask.isInstanceOf[AircraftModelManagerTask]))
        ModelDiscount.getAllCombinedDiscountsByAirlineId(airlineId)
      else Map.empty

    implicit val writes = new LevelingManagerWrites(currentCycle, campaignById, discountsByModelId)
    Ok(Json.toJson(allManagers))
  }

  def deleteManager(airlineId: Int, managerId: Int) = AuthenticatedAirline(airlineId) { request =>
    val owned = ManagerSource.loadBusyDelegatesByCriteria(
      List(("id", "=", managerId), ("airline", "=", airlineId))
    ).nonEmpty
    if (owned) {
      ManagerSource.deleteBusyDelegateByCriteria(List(("id", "=", managerId)))
      Ok(Json.obj())
    } else {
      Forbidden(s"Manager $managerId not found for airline $airlineId")
    }
  }

}

class CountryDelegateWrites(currentCycle : Int) extends Writes[Manager] {
  override def writes(countryDelegate: Manager): JsValue = {
    val task = countryDelegate.assignedTask.asInstanceOf[CountryManagerTask]
    Json.toJson(countryDelegate)(new BusyDelegateWrites(currentCycle)).asInstanceOf[JsObject] +
      ("countryCode" -> JsString(task.country.countryCode)) +
      ("startCycle" -> JsNumber(task.startCycle))
  }
}

class AircraftModelDelegateWrites(currentCycle : Int) extends Writes[Manager] {
  override def writes(delegate: Manager): JsValue = {
    val task = delegate.assignedTask.asInstanceOf[AircraftModelManagerTask]
    Json.toJson(delegate)(new BusyDelegateWrites(currentCycle)).asInstanceOf[JsObject] +
      ("modelId" -> JsNumber(task.modelId)) +
      ("startCycle" -> JsNumber(task.startCycle))
  }
}

class LevelingManagerWrites(
  currentCycle: Int,
  campaignById: Map[Int, Campaign],
  discountsByModelId: Map[Int, List[ModelDiscount]]
) extends Writes[Manager] {
  override def writes(m: Manager): JsValue = {
    val base = Json.toJson(m)(new BusyDelegateWrites(currentCycle)).asInstanceOf[JsObject]
    m.assignedTask match {
      case task: CountryManagerTask =>
        val mult = AirlineCountryRelationship.getDelegateBonusMultiplier(task.country)
        val contribution = BigDecimal(task.level(currentCycle) * mult).setScale(2, RoundingMode.HALF_UP)
        base +
          ("countryCode"     -> JsString(task.country.countryCode)) +
          ("countryName"     -> JsString(task.country.name)) +
          ("bonusMultiplier" -> JsNumber(BigDecimal(mult).setScale(2, RoundingMode.HALF_UP))) +
          ("contribution"    -> JsNumber(contribution))

      case task: CampaignManagerTask =>
        val campaign = campaignById.getOrElse(task.campaign.id, task.campaign)
        val costPerManager = if (campaign.area.nonEmpty)
          Campaign.getCost(campaign.area.map(_.popMiddleIncome).sum.toLong +
                           campaign.area.map(_.popElite).sum.toLong * 100)
        else 0
        val loyalty = BigDecimal(
          Campaign.getAirlineBonus(campaign.populationCoverage, task.level(currentCycle)).loyalty
        ).setScale(2, RoundingMode.HALF_UP)
        base +
          ("campaignId"      -> JsNumber(task.campaign.id)) +
          ("airportName"     -> JsString(campaign.principalAirport.city)) +
          ("airportIata"     -> JsString(campaign.principalAirport.iata)) +
          ("population"      -> JsNumber(campaign.populationCoverage)) +
          ("loyaltyBonus"    -> JsNumber(loyalty)) +
          ("costPerManager"  -> JsNumber(costPerManager))

      case task: AircraftModelManagerTask =>
        val discounts = discountsByModelId.getOrElse(task.modelId, Nil)
          .filter(_.discountReason == DiscountReason.LOW_DEMAND)
        val pricePct = discounts.find(_.discountType == DiscountType.PRICE)
          .map(d => (d.discount * 100).round.toInt).getOrElse(0)
        val timePct  = discounts.find(_.discountType == DiscountType.CONSTRUCTION_TIME)
          .map(d => (d.discount * 100).round.toInt).getOrElse(0)
        base +
          ("modelId"                    -> JsNumber(task.modelId)) +
          ("modelName"                  -> JsString(task.modelName)) +
          ("currentPriceDiscountPct"    -> JsNumber(pricePct)) +
          ("currentTimeDiscountPct"     -> JsNumber(timePct))

      case _ => base
    }
  }
}
