package controllers

import com.patson.data.{AirportSource, CampaignSource, CycleSource, ManagerSource}
import com.patson.model.{Airport, Manager, ManagerTask, CampaignManagerTask, Computation}
import com.patson.model.campaign._
import com.patson.util.AirportCache
import controllers.AuthenticationObject.AuthenticatedAirline
import javax.inject.Inject
import play.api.libs.json._
import play.api.mvc._

import scala.collection.mutable.ListBuffer
import scala.math.BigDecimal.RoundingMode

class CampaignApplication @Inject()(cc: ControllerComponents) extends AbstractController(cc) {
  implicit object CampaignWrites extends Writes[Campaign] {
    def writes(entry : Campaign): JsValue = {
      Json.obj(
        "principalAirport" -> entry.principalAirport,
        "radius" -> entry.radius,
        "population" ->  entry.populationCoverage,
        "area" -> entry.area,
        "id" -> entry.id
      )
    }
  }

  implicit object CampaignDetailsWrites extends Writes[CampaignDetails] {
    def writes(entry : CampaignDetails): JsValue = {
      var managersJson = Json.arr()
      val currentCycle = CycleSource.loadCycle()
      val taskWrites = new CampaignManagerTaskWrites(currentCycle)
      entry.managerTasks.foreach { task =>
        managersJson = managersJson.append(Json.toJson(task)(taskWrites))
      }

      Json.toJson(entry.campaign).as[JsObject] + ("managers" -> managersJson) + ("level" -> JsNumber(entry.managerTasks.map(_.level(currentCycle)).sum))
    }
  }

  class CampaignManagerTaskWrites(currentCycle : Int) extends Writes[CampaignManagerTask] {
    def writes(entry : CampaignManagerTask): JsValue = {
      var result = Json.obj(
        "level" -> entry.level(currentCycle),
        "description" -> entry.description,
        "levelDescription" -> entry.levelDescription(currentCycle),
        "startCycle" -> entry.startCycle,
      )

      entry.nextLevelCycleCount(currentCycle).foreach {
        value => result = result + ("nextLevelCycleCount" -> JsNumber(value))
      }
      result
    }
  }


  def getCampaigns(airlineId : Int, fullLoad : Boolean) = AuthenticatedAirline(airlineId) { request =>
    val campaigns = CampaignSource.loadCampaignsByCriteria(List(("airline", airlineId)), loadArea = fullLoad)
    val result = ManagerSource.loadBusyManagersByCampaigns(campaigns).map {
      case (campaign, managers) => CampaignDetails(campaign, managers.map(_.assignedTask.asInstanceOf[CampaignManagerTask]))
    }

    Ok(Json.toJson(result))
  }

  val MAX_CAMPAIGN_RADIUS = 500
  val MIN_CAMPAIGN_RADIUS = 50
  def getCampaignAirports(airlineId: Int, airportId: Int, radius: Int) = AuthenticatedAirline(airlineId) { request =>
    AirportCache.getAirport(airportId) match {
      case Some(principalAirport) =>
        val areaAirports = Computation.getAirportWithinRange(principalAirport, radius, 0, isDomestic = true)
        val population = areaAirports.map(_.population).sum
        val bonus = Campaign.getAirlineBonus(population, 1)
        val loyaltyBonus = BigDecimal(bonus.loyalty).setScale(2, RoundingMode.HALF_UP)
        val costPerManager = Campaign.getCost(areaAirports.map(_.popMiddleIncome).sum + areaAirports.map(_.popElite).sum * 100)

        Ok(Json.obj(
          "principalAirport" -> principalAirport,
          "radius" -> radius,
          "population" ->  population,
          "bonus" -> Json.obj("loyalty" -> loyaltyBonus),
          "costPerManager" -> costPerManager
        ))
      case None => NotFound(s"airport with id $airportId not found")
    }
  }

  def saveCampaign(airlineId : Int) = AuthenticatedAirline(airlineId) { request =>
    val airline = request.user
    val radius = request.body.asInstanceOf[AnyContentAsJson].json.\("radius").as[Int]
    if (radius < MIN_CAMPAIGN_RADIUS || radius > MAX_CAMPAIGN_RADIUS) {
      BadRequest(s"Invalid radius $radius")
    } else {
      val managerCount = request.body.asInstanceOf[AnyContentAsJson].json.\("managerCount").as[Int]
      val savedCampaignResult : Either[Result, Campaign] = request.body.asInstanceOf[AnyContentAsJson].json.\("campaignId").asOpt[Int] match {
        case Some(campaignId) => { //update
          CampaignSource.loadCampaignById(campaignId) match {
            case Some(campaign) =>
              if (campaign.airline.id != airlineId) {
                Left(BadRequest(s"Trying to modify campaign with $campaignId that is NOT from this airline $airline"))
              } else {
                val newArea = Computation.getAirportWithinRange(campaign.principalAirport, radius, 0, isDomestic = true)
                val newPopulationCoverage = newArea.map(_.population).sum
                CampaignSource.updateCampaign(campaign.copy(radius = radius, populationCoverage = newPopulationCoverage, area = newArea))
                Right(campaign)
              }
            case None => Left(NotFound(s"Campaign with id $campaignId not found"))
          }
        }
        case None => { //new
          val principalAirportId = request.body.asInstanceOf[AnyContentAsJson].json.\("airportId").as[Int]
          AirportCache.getAirport(principalAirportId) match {
            case Some(principalAirport) =>
              val newArea = Computation.getAirportWithinRange(principalAirport, radius, 0, isDomestic = true)
              val newPopulationCoverage = newArea.map(_.population).sum
              val newCampaign = Campaign(airline, principalAirport, radius, newPopulationCoverage, newArea)
              CampaignSource.saveCampaign(newCampaign)
              Right(newCampaign)
            case None => Left(BadRequest(s"Airport $principalAirportId not found!"))
          }

        }
      }

      savedCampaignResult match {
        case Left(badResult) => badResult
        case Right(campaign) =>
          //save delegates
          val existingManagers : List[Manager] = ManagerSource.loadBusyManagersByCampaigns(List(campaign)).getOrElse(campaign, List.empty).sortBy(_.assignedTask.getStartCycle)
          val delta = managerCount - existingManagers.length
          if (managerCount >= 0 && delta <= airline.getManagerInfo().availableCount) {
            if (delta < 0) { //unassign the most junior ones first
              existingManagers.takeRight(delta * -1).foreach { unassigningDelegate =>
                ManagerSource.deleteBusyDelegateByCriteria(List(("id", "=", unassigningDelegate.id)))
              }
            } else if (delta > 0) {
              val managerTask = ManagerTask.campaign(CycleSource.loadCycle(), campaign)
              val newManagers = (0 until delta).map(_ => Manager(airline, managerTask, None))

              ManagerSource.saveBusyManagers(newManagers.toList)
            }
            Ok(Json.obj())
          } else {
            BadRequest(s"Invalid manager value $managerCount")
          }
      }
    }
  }


  def deleteCampaign(airlineId : Int, campaignId : Int) = AuthenticatedAirline(airlineId) { request =>
    val airline = request.user
    CampaignSource.loadCampaignById(campaignId) match {
      case Some(campaign) =>
        if (campaign.airline.id != airlineId) {
          BadRequest(s"Trying to modify campaign with $campaignId that is NOT from this airline $airline")
        } else {
          CampaignSource.deleteCampaign(campaignId)
          Ok(Json.obj())
        }
      case None => NotFound(s"Campaign with id $campaignId not found")
    }
  }

  case class CampaignDetails(campaign: Campaign, managerTasks : List[CampaignManagerTask])

}
