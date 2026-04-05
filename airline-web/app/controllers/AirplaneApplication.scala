package controllers

import scala.math.BigDecimal.int2bigDecimal
import com.patson.data.{AirlineSource, AirplaneSource, CountrySource, CycleSource, LinkSource}
import com.patson.data.airplane.{AirplaneModelMetaSource, ModelSource}
import com.patson.model.airplane.{Model, _}
import com.patson.model._
import play.api.libs.json.{JsArray, JsBoolean, JsNumber, JsObject, JsString, JsValue, Json, Writes}
import play.api.mvc._

import scala.collection.mutable.ListBuffer
import controllers.AuthenticationObject.AuthenticatedAirline
import com.patson.model.airplane.Model.Category
import com.patson.util.{AirplaneOwnershipCache, AirplaneModelCache}

import javax.inject.Inject
import scala.collection.{MapView, mutable}


class AirplaneApplication @Inject()(cc: ControllerComponents) extends AbstractController(cc) {
  implicit object LinkAssignmentWrites extends Writes[LinkAssignments] {
    def writes(linkAssignments: LinkAssignments): JsValue = {
      var result = Json.arr()
      linkAssignments.assignments.foreach {
        case (linkId, assignment) =>
          val link = LinkSource.loadFlightLinkById(linkId, LinkSource.SIMPLE_LOAD).getOrElse(Link.fromId(linkId))
          result = result.append(Json.obj("link" -> Json.toJson(link), "frequency" -> assignment.frequency))
      }
      result
    }
  }

  implicit object AirplaneWithAssignedLinkWrites extends Writes[(Airplane, LinkAssignments)] {
    def writes(airplaneWithAssignedLink: (Airplane, LinkAssignments)): JsValue = {
      val airplane = airplaneWithAssignedLink._1
      val jsObject = Json.toJson(airplane).asInstanceOf[JsObject]
      jsObject + ("links" -> Json.toJson(airplaneWithAssignedLink._2))
    }
  }

  implicit object AirplaneModelWithDiscountsWrites extends Writes[ModelWithDiscounts] {
    def writes(airplaneModelWithDiscounts: ModelWithDiscounts): JsValue = {
      if (airplaneModelWithDiscounts.discounts.isEmpty) {
        Json.toJson(airplaneModelWithDiscounts.originalModel)
      } else {
        val discountedModel = airplaneModelWithDiscounts.originalModel.applyDiscount(airplaneModelWithDiscounts.discounts)
        var result = Json.toJson(discountedModel).asInstanceOf[JsObject]
        if (discountedModel.price != airplaneModelWithDiscounts.originalModel.price) {
          result = result + ("originalPrice" -> JsNumber(airplaneModelWithDiscounts.originalModel.price))
        }
        if (discountedModel.constructionTime != airplaneModelWithDiscounts.originalModel.constructionTime) {
          result = result + ("originalConstructionTime" -> JsNumber(airplaneModelWithDiscounts.originalModel.constructionTime))
        }

        var discountsJson = Json.obj()
        airplaneModelWithDiscounts.discounts.groupBy(_.discountType).foreach {
          case (discountType, discounts) =>
            var discountsByTypeJson = Json.arr()
            discounts.foreach { discount =>
              discountsByTypeJson = discountsByTypeJson.append(Json.obj("discountDescription" -> discount.description, "discountPercentage" -> (discount.discount * 100).toInt))
            }
            val typeLabel = discountType.toString.toLowerCase
            discountsJson = discountsJson + (typeLabel -> discountsByTypeJson)
        }
        result = result + ("discounts" -> discountsJson)
        result
      }
    }
  }

  case class ModelWithDiscounts(originalModel: Model, discounts: List[ModelDiscount])

  sealed case class AirplanesByModel(model: Model, assignedAirplanes: List[Airplane], availableAirplanes: List[Airplane], constructingAirplanes: List[Airplane])

  object AirplanesByModelWrites extends Writes[List[AirplanesByModel]] {
    def writes(airplanesByModelList: List[AirplanesByModel]): JsValue = {
      var result = Json.obj()
      airplanesByModelList.foreach { airplanesByModel =>
        val airplaneJson = Json.obj(
          ("assignedAirplanes" -> Json.toJson(airplanesByModel.assignedAirplanes)),
          ("availableAirplanes" -> Json.toJson(airplanesByModel.availableAirplanes)),
          ("constructingAirplanes" -> Json.toJson(airplanesByModel.constructingAirplanes)))
        result = result + (String.valueOf(airplanesByModel.model.id) -> airplaneJson)
      }
      result
    }
  }

  object AirplanesByModelSimpleWrites extends Writes[List[AirplanesByModel]] {
    def writes(airplanesByModelList: List[AirplanesByModel]): JsValue = {
      var result = Json.obj()
      airplanesByModelList.foreach { airplanesByModel =>
        val airplaneJson = Json.obj(
          ("assignedAirplanes" -> Json.toJson(airplanesByModel.assignedAirplanes)(SimpleAirplanesWrites)),
          ("availableAirplanes" -> Json.toJson(airplanesByModel.availableAirplanes)(SimpleAirplanesWrites)),
          ("constructingAirplanes" -> Json.toJson(airplanesByModel.constructingAirplanes)(SimpleAirplanesWrites)))
        result = result + (String.valueOf(airplanesByModel.model.id) -> airplaneJson)
      }
      result
    }
  }

  object SimpleAirplanesWrites extends Writes[List[Airplane]] {
    override def writes(airplanes: List[Airplane]): JsValue = {
      var result = Json.arr()
      airplanes.foreach { airplane =>
        result = result.append(Json.toJson(airplane)(SimpleAirplaneWrite))
      }
      result
    }
  }

  /**
   * Static airplane data
   */
  def getAirplaneModels() = Action {
    val jsonData = Json.toJson(allAirplaneModels)
    Ok(jsonData)
      .withHeaders(
        CACHE_CONTROL -> "public, max-age=2419200",
        ETAG -> s""""$currentApiVersion"""", // Use version as ETag
        EXPIRES -> java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME
          .format(java.time.ZonedDateTime.now().plusWeeks(4))
      )
  }

  val MODEL_TOP_N = 10

  def getAirplaneModelStatsByAirline(airlineId: Int, modelId: Int) = AuthenticatedAirline(airlineId) { request =>
    Ok(getAirplaneModelStatsJson(modelId, Some(airlineId)))
  }

  def getAirplaneModelStats(modelId: Int) = Action {
    //load usage
    Ok(getAirplaneModelStatsJson(modelId, None))
  }

  def getAirplaneModelStatsJson(modelId: Int, airlineIdOption: Option[Int]) = {
    val airplanes = AirplaneSource.loadAirplanesCriteria(List(("a.model", modelId)))

    var result = Json.obj("total" -> airplanes.length)
    var topAirlinesJson = Json.arr()
    val airplanesCountByOwnerId: Map[Int, Int] = airplanes.filter(!_.isSold).groupBy(_.owner).view.map {
      case (airline, airplanes) => (airline.id, airplanes.length)
    }.toMap
    airplanesCountByOwnerId.toList.sortBy(_._2).reverse.take(MODEL_TOP_N).foreach {
      case (airlineId, airplaneCount) =>
        //load the airline name
        val airline = AirlineSource.loadAirlineById(airlineId)
        topAirlinesJson = topAirlinesJson.append(Json.obj("airline" -> Json.toJson(airline), "airplaneCount" -> airplaneCount))
    }

    result = result + ("topAirlines" -> topAirlinesJson)

    AirplaneModelMetaSource.loadLaunchCustomer(modelId).foreach { name =>
      result = result + ("launchCustomer" -> JsString(name))
    }

    result
  }

  /**
   * minimal payload for airplane listing, only things that change within a cycle
   *
   * @param airlineId
   * @return
   */
  def getAirplaneModelsByAirline(airlineId: Int) = AuthenticatedAirline(airlineId) { request =>
    val discountsByModelId: Map[Int, List[ModelDiscount]] = ModelDiscount.getAllCombinedDiscountsByAirlineId(airlineId)
    val countsByModel: Map[Int, Int] = AirplaneSource.loadAirplaneModelCounts()
    val launchCustomers: Map[Int, String] = AirplaneModelMetaSource.loadAllLaunchCustomers()
    // Batch all rejection checks: computes country relationships and owned models once for all models
    val rejections: Map[Model, Option[String]] = getRejections(allAirplaneModels, request.user)

    val modelsJson = allAirplaneModels.map { model =>
      val modelId = model.id
      val discounts = discountsByModelId.getOrElse(modelId, Nil)
      val discountsJson = JsObject(
        discounts.groupBy(_.discountType).toSeq.map { case (discountType, grouped) =>
          discountType.toString.toLowerCase -> Json.toJson(grouped.map { d =>
            Json.obj(
              "discountDescription" -> d.description,
              "discountPercentage" -> (d.discount * 100).toInt
            )
          })
        }
      )
      val total = countsByModel.getOrElse(modelId, 0).toInt
      val rejection = rejections.get(model).flatten
      val discountedModel = if (discounts.nonEmpty) model.applyDiscount(discounts) else model

      var baseJson = Json.obj(
        "id" -> modelId,
        "discounts" -> discountsJson,
        "total" -> JsNumber(total)
      )

      rejection.foreach { reason =>
        baseJson = baseJson + ("rejection" -> JsString(reason))
      }

      // Discounted price fields — enables strikethrough display on frontend
      if (discountedModel.price != model.price) {
        baseJson = baseJson +
          ("price" -> JsNumber(discountedModel.price)) +
          ("originalPrice" -> JsNumber(model.price))
      }
      if (discountedModel.constructionTime != model.constructionTime) {
        baseJson = baseJson +
          ("constructionTime" -> JsNumber(discountedModel.constructionTime)) +
          ("originalConstructionTime" -> JsNumber(model.constructionTime))
      }

      // Manager discount projection
      val maxManagerPct = ModelDiscount.computeMaxLowDemandPriceDiscountPct(model, total)
      if (maxManagerPct > 0) {
        baseJson = baseJson +
          ("maxManagerPriceDiscountPct" -> JsNumber(BigDecimal(maxManagerPct).setScale(1, BigDecimal.RoundingMode.HALF_UP))) +
          ("discountPerManagerLevelPct" -> JsNumber(BigDecimal(maxManagerPct * 0.125).setScale(1, BigDecimal.RoundingMode.HALF_UP)))
      }
      val maxManagerTimePct = ModelDiscount.computeMaxLowDemandConstructionTimeDiscountPct(model, total)
      if (maxManagerTimePct > 0) {
        baseJson = baseJson +
          ("maxManagerConstructionTimeDiscountPct" -> JsNumber(BigDecimal(maxManagerTimePct).setScale(1, BigDecimal.RoundingMode.HALF_UP))) +
          ("discountPerManagerConstructionTimeLevelPct" -> JsNumber(BigDecimal(maxManagerTimePct * 0.125).setScale(1, BigDecimal.RoundingMode.HALF_UP)))
      }

      launchCustomers.get(modelId).foreach { name =>
        baseJson = baseJson + ("launchCustomer" -> JsString(name))
      }

      baseJson
    }

    Ok(Json.toJson(modelsJson))
  }

  def getRejections(models: List[Model], airline: Airline): Map[Model, Option[String]] = {
    val allManufacturingCountries = models.map(_.countryCode).toSet

    val countryRelations: Map[String, AirlineCountryRelationship] = allManufacturingCountries.map { countryCode =>
      (countryCode, AirlineCountryRelationship.getAirlineCountryRelationship(countryCode, airline))
    }.toMap

    val ownedModels = AirplaneOwnershipCache.getOwnership(airline.id).map(_.model).toSet


    models.map { model =>
      (model, getRejection(model, 1, countryRelations(model.countryCode), ownedModels, airline, checkBalance = false))
    }.toMap

  }

  def getRejection(model: Model, quantity: Int, airline: Airline): Option[String] = {
    val relationship = AirlineCountryRelationship.getAirlineCountryRelationship(model.countryCode, airline)

    val ownedModels = AirplaneOwnershipCache.getOwnership(airline.id).map(_.model).toSet
    getRejection(model, quantity, relationship, ownedModels, airline)
  }

  def getRejection(model: Model, quantity: Int, relationship: AirlineCountryRelationship, ownedModels: Set[Model], airline: Airline, checkBalance: Boolean = true): Option[String] = {
    if (quantity > 10) {
      return Some("Cannot order more than 10 planes in one order")
    }
    if (airline.getHeadQuarter().isEmpty) { //no HQ
      return Some("Must build HQs before purchasing any airplanes")
    }

    val airlineValidations = model.validateForAirline(airline, relationship.relationship)
    if (airlineValidations.nonEmpty) return Some(airlineValidations.head)

    val ownedModelFamilies = ownedModels.map(_.family)

    if (!ownedModelFamilies.contains(model.family) && ownedModelFamilies.size >= airline.airlineGrade.getModelFamilyLimit) {
      val familyToken = if (ownedModelFamilies.size <= 1) "family" else "families"
      return Some("Can only own up to " + airline.airlineGrade.getModelFamilyLimit + " different airplane " + familyToken + " at current airline grade")
    }

    if (checkBalance) {
      val discountedModel = model.applyDiscount(ModelDiscount.getCombinedDiscountsByModelId(airline.id, model.id))
      val cost: Long = discountedModel.price * quantity
      if (cost > airline.getBalance()) {
        return Some(s"Not enough cash to purchase $quantity ${model.name} for $cost")
      }
    }

    None
  }

  def getUsedRejections(usedAirplanes: List[Airplane], model: Model, airline: Airline): Map[Airplane, String] = {
    if (airline.getHeadQuarter().isEmpty) { //no HQ
      return usedAirplanes.map((_, "Must build HQs before purchasing any airplanes")).toMap
    }

    val airlineValidations = model.validateForAirline(airline)
    if (airlineValidations.nonEmpty) {
      return usedAirplanes.map((_, airlineValidations.head)).toMap
    }

    val ownedModels = AirplaneOwnershipCache.getOwnership(airline.id).map(_.model).toSet
    val ownedModelFamilies = ownedModels.map(_.family)
    if (!ownedModelFamilies.contains(model.family) && ownedModelFamilies.size >= airline.airlineGrade.getModelFamilyLimit) {
      val familyToken = if (ownedModelFamilies.size <= 1) "family" else "families"
      val rejection = "Can only own up to " + airline.airlineGrade.getModelFamilyLimit + " different airplane " + familyToken + " at current airline grade"
      return usedAirplanes.map((_, rejection)).toMap
    }

    val rejections = scala.collection.mutable.Map[Airplane, String]()
    usedAirplanes.foreach { airplane =>
      if (airplane.dealerValue > airline.getBalance()) {
        rejections.put(airplane, "Not enough cash to purchase this airplane")
      }
    }
    return rejections.toMap
  }

  def getOwnedAirplanes(airlineId: Int, simpleResult: Boolean, groupedResult: Boolean) = {
    getAirplanes(airlineId, None, simpleResult, groupedResult)
  }

  def getOwnedAirplanesWithModelId(airlineId: Int, modelId: Int) = {
    getAirplanes(airlineId, Some(modelId), simpleResult = false, groupedResult = true)
  }


  private def getAirplanes(airlineId: Int, modelIdOption: Option[Int], simpleResult: Boolean, groupedResult: Boolean) = AuthenticatedAirline(airlineId) {
    val queryCriteria = ListBuffer(("owner", airlineId), ("is_sold", false))
    modelIdOption.foreach { modelId =>
      queryCriteria.append(("a.model", modelId))
    }

    val ownedAirplanes: List[Airplane] = AirplaneSource.loadAirplanesCriteria(queryCriteria.toList)
    val linkAssignments = AirplaneSource.loadAirplaneLinkAssignmentsByOwner(airlineId)
    if (groupedResult) {
      //now split the list of airplanes by with and w/o assignedLinks
      val airplanesByModel: Map[Model, (List[Airplane], List[Airplane])] = ownedAirplanes.groupBy(_.model).view.mapValues {
        airplanes => airplanes.partition(airplane => linkAssignments.isDefinedAt(airplane.id) && airplane.isReady) //for this list do NOT include assigned airplanes that are still under construction, as it's already under the construction list
        //TODO the front end should do the splitting...
      }.toMap

      val airplanesByModelList = airplanesByModel.toList.map {
        case (model, (assignedAirplanes, freeAirplanes)) => AirplanesByModel(model, assignedAirplanes, availableAirplanes = freeAirplanes.filter(_.isReady), constructingAirplanes = freeAirplanes.filter(!_.isReady))
      }
      var result =
        if (simpleResult) {
          Json.toJson(airplanesByModelList)(AirplanesByModelSimpleWrites)
        } else {
          Json.toJson(airplanesByModelList)(AirplanesByModelWrites)
        }
      Ok(result)
    } else {
      val airplanesWithLink: List[(Airplane, LinkAssignments)] = ownedAirplanes.map { airplane =>
        (airplane, linkAssignments.getOrElse(airplane.id, LinkAssignments.empty))
      }
      Ok(Json.toJson(airplanesWithLink))
    }
  }

  def getUsedAirplanes(airlineId: Int, modelId: Int) = AuthenticatedAirline(airlineId) { request =>
    ModelSource.loadModelById(modelId) match {
      case Some(model) =>
        val usedAirplanes = AirplaneSource.loadAirplanesCriteria(List(("a.model", modelId), ("is_sold", true)))

        val rejections = getUsedRejections(usedAirplanes, model, request.user)
        var result = Json.arr()
        usedAirplanes.foreach { airplane =>
          var airplaneJson = Json.toJson(airplane).asInstanceOf[JsObject]
          if (rejections.contains(airplane)) {
            airplaneJson = airplaneJson + ("rejection" -> JsString(rejections(airplane)))
          }
          result = result :+ airplaneJson
        }
        Ok(result)
      case None => BadRequest("model not found")
    }
  }

  def buyUsedAirplane(airlineId: Int, airplaneId: Int, homeAirportId: Int, configurationId: Int) = AuthenticatedAirline(airlineId) { request =>
    this.synchronized {
      AirplaneSource.loadAirplaneById(airplaneId) match {
        case Some(airplane) =>
          val airline = request.user
          getUsedRejections(List(airplane), airplane.model, airline).get(airplane) match {
            case Some(rejection) => BadRequest(rejection)
            case None =>
              if (!airplane.isSold) {
                BadRequest("Airplane is no longer for sale " + airlineId)
              } else {
                val homeBase = request.user.getBases().find(_.airport.id == homeAirportId)
                homeBase match {
                  case None =>
                    BadRequest(s"Home airport ID $homeAirportId is not valid")
                  case Some(homeBase) =>
                    val configuration: Option[AirplaneConfiguration] =
                      if (configurationId == -1) {
                        None
                      } else {
                        AirplaneSource.loadAirplaneConfigurationById(configurationId)
                      }

                    if (configuration.isDefined && (configuration.get.airline.id != airlineId || configuration.get.model.id != airplane.model.id)) {
                      BadRequest("Configuration is not owned by this airline/model")
                    } else {
                      val currentCycle = CycleSource.loadCycle()
                      val dealerValue = airplane.dealerValue
                      airplane.buyFromDealer(airline, currentCycle)
                      airplane.home = homeBase.airport
                      configuration.foreach { configuration =>
                        airplane.configuration = configuration
                      }

                      if (AirplaneSource.updateAirplanes(List(airplane)) == 1) {
                        val buyDesc = s"${airplane.model.name} @ ${airplane.condition} condition"
                        AirlineSource.saveLedgerEntry(AirlineLedgerEntry(airlineId, currentCycle, LedgerType.BUY_AIRPLANE, -1 * dealerValue, Some(buyDesc)))
                        Ok(Json.obj())
                      } else {
                        BadRequest("Failed to buy used airplane " + airlineId)
                      }
                    }
                }
              }
          }

        case None => BadRequest("airplane not found")
      }
    }
  }


  def getAirplane(airlineId: Int, airplaneId: Int) = AuthenticatedAirline(airlineId) {
    AirplaneSource.loadAirplaneById(airplaneId) match {
      case Some(airplane) =>
        if (airplane.owner.id == airlineId) {
          //load link assignments
          val airplaneWithLinkAssignments: (Airplane, LinkAssignments) = (airplane, AirplaneSource.loadAirplaneLinkAssignmentsByAirplaneId(airplane.id))
          Ok(Json.toJson(airplaneWithLinkAssignments))
        } else {
          Forbidden
        }
      case None =>
        BadRequest("airplane not found")
    }
  }

  // Helper function that handles the core airplane selling logic
  private def processSellAirplane(airplane: Airplane, airlineId: Int): Either[String, (Airplane, Long)] = {
    val sellValue = Computation.calculateAirplaneSellValue(airplane)

    val updateCount =
      if (airplane.condition >= Airplane.BAD_CONDITION) {
        airplane.sellToDealer(currentCycle)
        AirplaneSource.updateAirplanes(List(airplane.copy()), true)
      } else {
        AirplaneSource.deleteAirplane(airplane.id, Some(airplane.version))
      }

    if (updateCount == 1) {
      val buyDesc = if (updateCount == 1) airplane.model.name else s"${airplane.model.name} @ ${airplane.condition} condition"
      AirlineSource.saveLedgerEntry(AirlineLedgerEntry(airlineId, currentCycle, LedgerType.SELL_AIRPLANE, sellValue, Some(buyDesc)))
      Right((airplane, sellValue))
    } else {
      Left("Update failed")
    }
  }

  def sellAirplane(airlineId: Int, airplaneId: Int) = AuthenticatedAirline(airlineId) {
    AirplaneSource.loadAirplaneById(airplaneId) match {
      case Some(airplane) =>
        if (airplane.owner.id != airlineId || airplane.isSold) {
          Forbidden
        } else if (!airplane.isReady) {
          BadRequest("Airplane is not yet constructed or is sold")
        } else {
          val linkAssignments = AirplaneSource.loadAirplaneLinkAssignmentsByAirplaneId(airplaneId)
          if (!linkAssignments.isEmpty) { //still assigned to some link, do not allow selling
            BadRequest("Airplane " + airplane + " still assigned to link " + linkAssignments)
          } else {
            processSellAirplane(airplane, airlineId) match {
              case Right((soldAirplane, _)) => Ok(Json.toJson(soldAirplane))
              case Left(error) => BadRequest(error)
            }
          }
        }
      case None =>
        BadRequest("airplane not found")
    }
  }

  def sellUnassignedAirplanes(airlineId: Int) = AuthenticatedAirline(airlineId) { request =>
    val ownedAirplanes: List[Airplane] = AirplaneSource.loadAirplanesCriteria(List(("owner", airlineId), ("is_sold", false)))
    val linkAssignments = AirplaneSource.loadAirplaneLinkAssignmentsByOwner(airlineId)

    val unassignedAirplanes = ownedAirplanes.filter { airplane =>
      airplane.isReady && !linkAssignments.isDefinedAt(airplane.id)
    }

    if (unassignedAirplanes.isEmpty) {
      Ok(Json.obj("message" -> "No unassigned airplanes found to sell", "soldCount" -> 0, "soldAirplanes" -> Json.arr()))
    } else {
      var soldAirplanes = List[Airplane]()
      var totalSellValue: Long = 0
      var failedSales = List[(Airplane, String)]()

      unassignedAirplanes.foreach { airplane =>
        processSellAirplane(airplane, airlineId) match {
          case Right((soldAirplane, sellValue)) =>
            soldAirplanes = soldAirplane :: soldAirplanes
            totalSellValue += sellValue
          case Left(error) =>
            failedSales = (airplane, error) :: failedSales
        }
      }

      val result = Json.obj(
        "message" -> s"Successfully sold ${soldAirplanes.length} unassigned airplanes",
        "soldCount" -> soldAirplanes.length,
        "totalSellValue" -> totalSellValue,
        "soldAirplanes" -> Json.toJson(soldAirplanes.reverse)
      )

      if (failedSales.nonEmpty) {
        val failedJson = failedSales.foldLeft(Json.arr()) { case (acc, (airplane, error)) =>
          acc.append(Json.obj("airplane" -> Json.toJson(airplane), "error" -> error))
        }
        Ok(result + ("failedSales" -> failedJson))
      } else {
        Ok(result)
      }
    }
  }

  def replaceAirplane(airlineId: Int, airplaneId: Int) = AuthenticatedAirline(airlineId) { request =>
    AirplaneSource.loadAirplaneById(airplaneId) match {
      case Some(airplane) =>
        if (airplane.owner.id == airlineId) {
          val currentCycle = CycleSource.loadCycle()
          if (!airplane.isReady) {
            BadRequest("airplane is not yet constructed")
          } else if (airplane.purchasedCycle > (currentCycle - airplane.model.constructionTime)) {
            BadRequest("airplane is not yet ready to be replaced")
          } else {
            val sellValue = Computation.calculateAirplaneSellValue(airplane)

            val originalModel = airplane.model

            val model = originalModel.applyDiscount(ModelDiscount.getCombinedDiscountsByModelId(airlineId, originalModel.id))

            val replaceCost = model.price - sellValue
            if (request.user.airlineInfo.balance < replaceCost) { //not enough money!
              BadRequest("Not enough money")
            } else {
              //               if (airplane.condition >= Airplane.BAD_CONDITION) { //create a clone as the sold airplane
              //                  AirplaneSource.saveAirplanes(List(airplane.copy(isSold = true, dealerRatio = Airplane.DEFAULT_DEALER_RATIO, id = 0)))
              //               }

              val replacingAirplane = airplane.copy(constructedCycle = currentCycle, purchasedCycle = currentCycle, condition = Airplane.MAX_CONDITION, purchasePrice = model.price)

              val updateCount = AirplaneSource.updateAirplanes(List(replacingAirplane), true)
              if (updateCount == 1) {
                AirlineSource.saveLedgerEntries(List(
                  AirlineLedgerEntry(airlineId, currentCycle, LedgerType.SELL_AIRPLANE, sellValue, Some(originalModel.name)),
                  AirlineLedgerEntry(airlineId, currentCycle, LedgerType.BUY_AIRPLANE, -1 * model.price, Some(originalModel.name))
                ))

                Ok(Json.toJson(airplane))
              } else {
                BadRequest("Something went wrong, try again!")
              }
            }
          }
        } else {
          Forbidden
        }
      case None =>
        BadRequest("airplane not found")
    }
  }

  def addAirplane(airlineId: Int, modelId: Int, quantity: Int, homeAirportId: Int, configurationId: Int) = AuthenticatedAirline(airlineId) { request =>
    ModelSource.loadModelById(modelId) match {
      case None =>
        BadRequest("unknown model or airline")
      case Some(originalModel) =>
        //now check for discounts
        val model = originalModel.applyDiscount(ModelDiscount.getCombinedDiscountsByModelId(airlineId, originalModel.id))

        val airline = request.user
        val currentCycle = CycleSource.loadCycle()
        val constructedCycle = currentCycle + model.constructionTime
        val homeBase = request.user.getBases().find(_.airport.id == homeAirportId)

        homeBase match {
          case None =>
            BadRequest(s"Home airport ID $homeAirportId is not valid")
          case Some(homeBase) =>
            val airplane = Airplane(model, airline, constructedCycle = constructedCycle, purchasedCycle = currentCycle, Airplane.MAX_CONDITION, purchasePrice = model.price, home = homeBase.airport)

            val rejectionOption = getRejection(model, quantity, airline)
            if (rejectionOption.isDefined) {
              BadRequest(rejectionOption.get)
            } else {
              val airplanes = ListBuffer[Airplane]()
              for (i <- 0 until quantity) {
                airplanes.append(airplane.copy())
              }

              val configuration: Option[AirplaneConfiguration] =
                if (configurationId == -1) {
                  None
                } else {
                  AirplaneSource.loadAirplaneConfigurationById(configurationId)
                }

              if (configuration.isDefined && (configuration.get.airline.id != airlineId || configuration.get.model.id != modelId)) {
                BadRequest("Configuration is not owned by this airline/model")
              } else {
                airplanes.foreach { airplane =>
                  configuration match {
                    case None => airplane.assignDefaultConfiguration()
                    case Some(configuration) => airplane.configuration = configuration
                  }
                }
                val prePurchaseCount = AirplaneSource.loadAirplaneModelCounts().getOrElse(modelId, 0)
                val updateCount = AirplaneSource.saveAirplanes(airplanes.toList)
                if (updateCount > 0) {
                  val amount: Long = -1 * airplane.model.price.toLong * updateCount
                  val buyDesc = if (updateCount == 1) model.name else s"${updateCount}x ${model.name}"
                  AirlineSource.saveLedgerEntry(AirlineLedgerEntry(airlineId, currentCycle, LedgerType.BUY_AIRPLANE, amount, Some(buyDesc)))

                  val isLaunchCustomer = prePurchaseCount == 0
                  if (isLaunchCustomer) {
                    AirplaneModelMetaSource.saveLaunchCustomer(modelId, airline.name)
                  }

                  var result = Json.obj("updateCount" -> updateCount)
                  if (isLaunchCustomer) {
                    result = result + ("isLaunchCustomer" -> JsBoolean(true)) + ("modelName" -> JsString(originalModel.name))
                  }
                  Accepted(result)
                } else {
                  UnprocessableEntity("Cannot save airplane")
                }
              }
            }
        }
    }
  }

  def swapAirplane(airlineId: Int, fromAirplaneId: Int, toAirplaneId: Int) = AuthenticatedAirline(airlineId) { request =>
    val fromAirplaneOption = AirplaneSource.loadAirplaneById(fromAirplaneId)
    val toAirplaneOption = AirplaneSource.loadAirplaneById(toAirplaneId)

    if (fromAirplaneOption.isDefined && toAirplaneOption.isDefined) {
      val fromAirplane = fromAirplaneOption.get
      val toAirplane = toAirplaneOption.get
      if (fromAirplane.owner.id == airlineId && toAirplane.owner.id == airlineId && fromAirplane.model.id == toAirplane.model.id) {
        val fromConstructedCycle = fromAirplane.constructedCycle
        val fromPurchaseCycle = fromAirplane.purchasedCycle
        val fromCondition = fromAirplane.condition
        val fromPurchasePrice = fromAirplane.purchasePrice

        val toConstructedCycle = toAirplane.constructedCycle
        val toPurchaseCycle = toAirplane.purchasedCycle
        val toCondition = toAirplane.condition
        val toPurchasePrice = toAirplane.purchasePrice

        val swappedFromAirplane = fromAirplane.copy(constructedCycle = toConstructedCycle, purchasedCycle = toPurchaseCycle, condition = toCondition, purchasePrice = toPurchasePrice)
        val swappedToAirplane = toAirplane.copy(constructedCycle = fromConstructedCycle, purchasedCycle = fromPurchaseCycle, condition = fromCondition, purchasePrice = fromPurchasePrice)

        AirplaneSource.updateAirplanes(List(swappedFromAirplane, swappedToAirplane))
        LinkUtil.adjustLinksAfterAirplaneConfigurationChange(swappedFromAirplane.id)
        LinkUtil.adjustLinksAfterAirplaneConfigurationChange(swappedToAirplane.id)

        Ok(Json.toJson(fromAirplane))
      } else {
        Forbidden
      }
    } else {
      BadRequest("airplane not found")
    }
  }

  /**
   * Swap airplane models. Sells existing airplanes and purchases new models.
   * If isEstimate is true, returns only the cost difference without processing the change.
   *
   * Uses efficient batch database operations to minimize transaction overhead.
   */
  def swapAirplaneModels(airlineId: Int) = AuthenticatedAirline(airlineId) { request =>
    val bodyOpt = request.body.asJson
    bodyOpt match {
      case None => BadRequest("Request body must be JSON")
      case Some(body) =>
        val airplaneIdsOpt = (body \ "airplaneIds").asOpt[List[Int]]
        val newModelIdOpt = (body \ "newModelId").asOpt[Int]
        val isEstimateOpt = (body \ "isEstimate").asOpt[Boolean]

        if (airplaneIdsOpt.isEmpty || newModelIdOpt.isEmpty) {
          BadRequest("Missing required fields: airplaneIds, newModelId")
        } else if (airplaneIdsOpt.get.isEmpty) {
          BadRequest("No airplanes provided to swap")
        } else {
          val airplaneIds = airplaneIdsOpt.get
          val newModelId = newModelIdOpt.get
          val isEstimate = isEstimateOpt.getOrElse(false)

          // Load all airplanes
          val airplanesToSwap = airplaneIds.flatMap(AirplaneSource.loadAirplaneById)
          if (airplanesToSwap.length != airplaneIds.length) {
            BadRequest("One or more airplanes not found")
          } else if (!airplanesToSwap.forall(_.owner.id == airlineId)) {
            BadRequest("Not all airplanes belong to this airline")
          } else if (airplanesToSwap.map(_.model.id).distinct.size > 1) {
            BadRequest("All airplanes must be of the same model")
          } else {
            // Validate all airplanes are ready and not sold
            val unreadyAirplanes = airplanesToSwap.filter(a => !a.isReady || a.isSold)
            if (unreadyAirplanes.nonEmpty) {
              BadRequest(s"${unreadyAirplanes.length} airplanes are not ready or sold")
            } else {
              // Load new model
              val newModelOpt = ModelSource.loadModelById(newModelId)
              if (newModelOpt.isEmpty) {
                BadRequest(s"Model $newModelId not found")
              } else {
                val newModel = newModelOpt.get
                var validationError: Option[String] = None

                if (Model.Type.size(newModel.airplaneType) > Model.Type.size(airplanesToSwap.head.model.airplaneType)) {
                  val oldModel = airplanesToSwap.head.model
                  validationError = Some(s"Can only swap planes if they're of the same or smaller size-classification type, and ${newModel.name} is a ${newModel.airplaneType} whereas ${oldModel.name} is a ${oldModel.airplaneType}.")
                }


                // Check for mixed aircraft on links: get all links where these airplanes are assigned
                val airplaneIdSet = airplaneIds.toSet
                val allLinksWithAssignedAirplanes = LinkSource.loadFlightLinksByCriteria(List(("airline", airlineId)), LinkSource.FULL_LOAD)
                val linksOfSwappingAirplanes = allLinksWithAssignedAirplanes.filter { link =>
                  (link.getAssignedAirplanes().keys.map(_.id).toSet intersect airplaneIdSet).nonEmpty
                }

                // Pre-load airplane link assignments (airplane-centric, one DB call).
                // Done here so the data is available for home-airport fallback and link update.
                val ownerLinkAssignments = AirplaneSource.loadAirplaneLinkAssignmentsByOwner(airlineId)
                val oldAirplaneAssignments: Map[Int, LinkAssignments] = airplanesToSwap.map { a =>
                  (a.id, ownerLinkAssignments.getOrElse(a.id, LinkAssignments(Map.empty)))
                }.toMap

                val swappingLinkIds = oldAirplaneAssignments.values.flatMap(_.assignments.keys).toSet
                val linkById: Map[Int, Link] = allLinksWithAssignedAirplanes
                  .filter(l => swappingLinkIds.contains(l.id) || linksOfSwappingAirplanes.exists(_.id == l.id))
                  .map(l => (l.id, l.asInstanceOf[Link]))
                  .toMap

                // 1. Mixed aircraft check
                for (link <- linksOfSwappingAirplanes if validationError.isEmpty) {
                  val assignedAirplaneIds = link.getAssignedAirplanes().keys.map(_.id).toSet
                  if (!(assignedAirplaneIds subsetOf airplaneIdSet)) {
                    validationError = Some(s"Cannot swap aircraft on link ${link.from.iata}-${link.to.iata}: would create mixed aircraft. All assigned airplanes on this link must be swapped together.")
                  }
                }

                // 2. Requirements envelope check
                if (validationError.isEmpty && linksOfSwappingAirplanes.nonEmpty) {
                  val maxDistance = linksOfSwappingAirplanes.map(_.distance).max
                  val minRunwayAvailable = linksOfSwappingAirplanes.flatMap(l => List(l.from.runwayLength, l.to.runwayLength)).min
                  
                  if (newModel.range < maxDistance) {
                    validationError = Some(s"New model ${newModel.name} has insufficient range (${newModel.range}km) for the longest existing route (${maxDistance}km).")
                  } else if (newModel.runwayRequirement > minRunwayAvailable) {
                    validationError = Some(s"New model ${newModel.name} requires ${newModel.runwayRequirement}m runway, but some airports on existing routes only have ${minRunwayAvailable}m.")
                  } else {
                    // Use abstracted validation for each link for thorough check (relationship, customs, etc.)
                    for (link <- linksOfSwappingAirplanes if validationError.isEmpty) {
                      val reasons = LinkApplication.validateModelForLink(newModel, request.user, link.from, link.to)
                      if (reasons.nonEmpty) {
                        validationError = Some(s"Cannot swap to ${newModel.name} for route ${link.from.iata}-${link.to.iata}: ${reasons.head}")
                      }
                    }
                  }
                }

                if (validationError.isDefined) {
                  BadRequest(validationError.get)
                } else {
                  // Calculate action point cost: 1 per 400 total capacity or 1 per 5 aircraft, whichever is more rounded down, at least 1
                  val totalCapacity = airplanesToSwap.map(_.model.capacity).sum
                  val apCost = Math.max(Math.max(totalCapacity / 400, airplanesToSwap.length / 5), 1)

                  // Calculate total sell value
                  val totalSellValue: Long = airplanesToSwap.map(airplane =>
                    Computation.calculateAirplaneSellValue(airplane).toLong
                  ).sum

                  // Apply discounts to new model
                  val newModelWithDiscounts = newModel.applyDiscount(
                    ModelDiscount.getCombinedDiscountsByModelId(airlineId, newModel.id)
                  )
                  val totalBuyCost = newModelWithDiscounts.price.toLong * airplanesToSwap.length

                  // Per-airplane utilization check: detect if new model would push any airplane over 100%.
                  // airplaneFrequencyCuts: (airplaneId, linkId) -> new (reduced) frequency
                  val airplaneFrequencyCuts = scala.collection.mutable.Map[(Int, Int), Int]()
                  for (airplane <- airplanesToSwap) {
                    val assignments = oldAirplaneAssignments.getOrElse(airplane.id, LinkAssignments(Map.empty)).assignments
                    if (assignments.nonEmpty) {
                      val newTotalFM = assignments.iterator.map { case (linkId, assignment) =>
                        linkById.get(linkId).map { link =>
                          assignment.frequency * Computation.calculateFlightMinutesRequired(newModelWithDiscounts, link.distance)
                        }.getOrElse(0)
                      }.sum
                      if (newTotalFM > Airplane.MAX_FLIGHT_MINUTES) {
                        val scale = Airplane.MAX_FLIGHT_MINUTES.toDouble / newTotalFM
                        for ((linkId, assignment) <- assignments) {
                          airplaneFrequencyCuts((airplane.id, linkId)) = (scale * assignment.frequency).toInt
                        }
                      }
                    }
                  }

                  // Aggregate per-airplane cuts into per-link totals for display
                  val linkFrequencyChanges: List[(Link, Int, Int)] = {
                    val affectedLinkIds = airplaneFrequencyCuts.keys.map(_._2).toSet
                    affectedLinkIds.flatMap { linkId =>
                      linkById.get(linkId).map { link =>
                        val oldTotalFreq = airplanesToSwap.flatMap { a =>
                          oldAirplaneAssignments.get(a.id).flatMap(_.assignments.get(linkId)).map(_.frequency)
                        }.sum
                        val newTotalFreq = airplanesToSwap.map { a =>
                          oldAirplaneAssignments.get(a.id).flatMap(_.assignments.get(linkId)).map { asgn =>
                            airplaneFrequencyCuts.getOrElse((a.id, linkId), asgn.frequency)
                          }.getOrElse(0)
                        }.sum
                        (link, oldTotalFreq, newTotalFreq)
                      }
                    }.toList.sortBy(_._1.id)
                  }

                  // If estimate mode, just return the cost difference and envelope
                  if (isEstimate) {
                    val costDifference = totalBuyCost - totalSellValue
                    val maxDistance = if (linksOfSwappingAirplanes.nonEmpty) linksOfSwappingAirplanes.map(_.distance).max else 0
                    val maxRunwayRequired = if (linksOfSwappingAirplanes.nonEmpty) linksOfSwappingAirplanes.flatMap(l => List(l.from.runwayLength, l.to.runwayLength)).min else 0
                    val hasCustomsRestriction = linksOfSwappingAirplanes.exists(l => Computation.getFlightCategory(l.from, l.to) == FlightCategory.INTERNATIONAL && (l.from.isDomesticAirport() || l.to.isDomesticAirport()))
                    val frequencyCutsJson = linkFrequencyChanges.map { case (link, oldFreq, newFreq) =>
                      Json.obj("linkId" -> link.id, "fromAirport" -> link.from.iata, "toAirport" -> link.to.iata, "oldFrequency" -> oldFreq, "newFrequency" -> newFreq)
                    }

                    Ok(Json.obj(
                      "sellValue" -> totalSellValue,
                      "buyCost" -> totalBuyCost,
                      "costDifference" -> costDifference,
                      "actionPointCost" -> apCost,
                      "isEstimate" -> true,
                      "envelope" -> Json.obj(
                        "maxDistance" -> maxDistance,
                        "minRunway" -> maxRunwayRequired,
                        "hasCustomsRestriction" -> hasCustomsRestriction,
                        "customsMaxCapacity" -> DomesticAirportFeature.internationalMaxCapacity
                      ),
                      "frequencyCuts" -> JsArray(frequencyCutsJson)
                    ))
                  } else if (request.user.getBalance() < (totalBuyCost - totalSellValue)) {
                    BadRequest(s"Insufficient balance to perform swap. Required: ${totalBuyCost - totalSellValue}, available: ${request.user.getBalance()}")
                  } else if (request.user.getActionPoints() < apCost) {
                    BadRequest(s"Insufficient action points to perform swap. Required: $apCost, available: ${request.user.getActionPoints().toInt}")
                  } else {
                    // Process the actual swap using batch operations
                    val airplanesToDelete = scala.collection.mutable.ListBuffer[Int]()
                    val airplanesToSellToDealer = scala.collection.mutable.ListBuffer[Airplane]()

                    // Separate airplanes by condition - determine which to delete vs mark as sold
                    for (airplane <- airplanesToSwap) {
                      if (airplane.condition >= Airplane.BAD_CONDITION) {
                        airplane.sellToDealer(currentCycle)
                        airplanesToSellToDealer += airplane
                      } else {
                        airplanesToDelete += airplane.id
                      }
                    }

                    // Create new airplanes with the new model
                    val newAirplanesToCreate = scala.collection.mutable.ListBuffer[Airplane]()

                    val hqAirport = request.user.getHeadQuarter().map(_.airport)
                    for (i <- 0 until airplanesToSwap.length) {
                      val oldAirplane = airplanesToSwap(i)
                      val homeAirport = if (oldAirplane.home.id != 0) {
                        oldAirplane.home
                      } else {
                        // Tier 1: use FROM airport of any assigned link (actual base, not necessarily HQ)
                        val linkFromFallback = oldAirplaneAssignments.get(oldAirplane.id)
                          .flatMap(_.assignments.keys.headOption)
                          .flatMap(linkId => linkById.get(linkId))
                          .map(_.from)
                        // Tier 2: HQ
                        linkFromFallback.orElse(hqAirport).getOrElse(Airport.fromId(0))
                      }
                      val newAirplane: Airplane = Airplane(
                        newModelWithDiscounts,
                        oldAirplane.owner,
                        constructedCycle = currentCycle,
                        purchasedCycle = currentCycle,
                        Airplane.MAX_CONDITION,
                        purchasePrice = newModelWithDiscounts.price,
                        home = homeAirport
                      )
                      newAirplane.assignDefaultConfiguration()
                      newAirplanesToCreate += newAirplane
                    }

                    // Update airplanes that are being sold to dealer
                    if (airplanesToSellToDealer.nonEmpty) {
                      AirplaneSource.updateAirplanes(airplanesToSellToDealer.toList, versionCheck = true)
                    }

                    // Perform batch swap: delete old airplanes and insert new ones
                    val (deleteCount, insertCount) = AirplaneSource.swapAirplanesBatch(
                      airplanesToDelete.toList, 
                      newAirplanesToCreate.toList
                    )

                    if ((deleteCount + airplanesToSellToDealer.length) != airplanesToSwap.length || insertCount != airplanesToSwap.length) {
                      BadRequest("Swap operation failed: mismatch in affected row counts")
                    } else {
                      // Update financials
                      val netCost = totalBuyCost - totalSellValue
                      val swapCount = airplanesToSwap.length
                      val countPrefix = if (swapCount == 1) "" else s"${swapCount}x "
                      AirlineSource.saveLedgerEntries(List(
                        AirlineLedgerEntry(airlineId, currentCycle, LedgerType.SELL_AIRPLANE, totalSellValue, Some(s"${countPrefix}${airplanesToSwap.head.model.name}")),
                        AirlineLedgerEntry(airlineId, currentCycle, LedgerType.BUY_AIRPLANE, -1 * totalBuyCost, Some(s"${countPrefix}${newModelWithDiscounts.name}"))
                      ))

                      // Update link assignments using airplane-centric pre-loaded data (avoids stale cache).
                      val newAssignmentsByLink = scala.collection.mutable.Map[Int, scala.collection.mutable.Map[Airplane, LinkAssignment]]()

                      airplanesToSwap.zip(newAirplanesToCreate.toList).foreach { case (oldAirplane, newAirplane) =>
                        oldAirplaneAssignments.getOrElse(oldAirplane.id, LinkAssignments(Map.empty)).assignments.foreach {
                          case (linkId, assignment) =>
                            val newFreq = airplaneFrequencyCuts.getOrElse((oldAirplane.id, linkId), assignment.frequency)
                            if (newFreq > 0) {
                              linkById.get(linkId).foreach { link =>
                                val newFlightMinutes = Computation.calculateFlightMinutesRequired(newModelWithDiscounts, link.distance) * newFreq
                                newAssignmentsByLink
                                  .getOrElseUpdate(linkId, scala.collection.mutable.Map())
                                  .put(newAirplane, LinkAssignment(newFreq, newFlightMinutes))
                              }
                            }
                        }
                      }
                      // Ensure links where all planes were cut to 0 still get their assignments cleaned up
                      swappingLinkIds.foreach { linkId =>
                        if (!newAssignmentsByLink.contains(linkId)) {
                          newAssignmentsByLink(linkId) = scala.collection.mutable.Map()
                        }
                      }

                      newAssignmentsByLink.foreach { case (linkId, newAssignments) =>
                        linkById.get(linkId).foreach { link =>
                          val updatedLink = link.copy(duration = Computation.calculateDuration(newModelWithDiscounts, link.distance))
                          updatedLink.setAssignedModel(newModelWithDiscounts)
                          updatedLink.setAssignedAirplanes(newAssignments.toMap)
                          LinkSource.updateLink(updatedLink)
                          LinkSource.updateAssignedPlanes(linkId, newAssignments.toMap)
                        }
                      }

                      AirlineSource.adjustAirlineActionPoints(request.user, -apCost.toDouble)

                      Ok(Json.obj(
                        "swappedCount" -> airplanesToSwap.length,
                        "sellValue" -> totalSellValue,
                        "buyCost" -> totalBuyCost,
                        "netCost" -> netCost,
                        "actionPointCost" -> apCost,
                        "newAirplanes" -> Json.toJson(newAirplanesToCreate.toList)
                      ))
                    }
                  }
                }
              }
            }
          }
        }
    }
  }

  def updateAirplaneHome(airlineId: Int, airplaneId: Int, airportId: Int) = AuthenticatedAirline(airlineId) { request =>
    AirplaneSource.loadAirplaneById(airplaneId) match {
      case Some(airplane) =>
        if (airplane.owner.id != airlineId) {
          BadRequest(s"Cannot update Home on airplane $airplane as it is not owned by ${request.user.name}")
        } else {
          if (!AirplaneSource.loadAirplaneLinkAssignmentsByAirplaneId(airplane.id).isEmpty) {
            BadRequest(s"Cannot update Home on airplane $airplane as it has assigned links")
          } else {
            request.user.getBases().find(_.airport.id == airportId) match {
              case Some(base) =>
                airplane.home = base.airport
                AirplaneSource.updateAirplanesDetails(List(airplane))
                Ok(Json.toJson(airplane))
              case None =>
                BadRequest(s"Cannot update Home on airplane $airplaneId as base $airportId is not found")
            }
          }
        }
      case None => BadRequest(s"Cannot update Configuration on airplane $airplaneId as it is not found")
    }
  }

  def getPreferredSuppliers(airlineId: Int) = AuthenticatedAirline(airlineId) { request =>
    val ownedModelsByCategory: MapView[Model.Category.Value, List[Model]] = AirplaneOwnershipCache.getOwnership(airlineId).groupBy(_.model.category).view.mapValues(_.map(_.model).distinct)

    var categoryJson = Json.obj()
    val supplierDiscountInfo = ModelDiscount.getPreferredSupplierDiscounts(airlineId)

    Category.grouping.foreach {
      case (category, airplaneTypes) =>
        var categoryInfoJson = Json.obj("types" -> airplaneTypes.map(Model.Type.label(_)))
        var ownershipJson = Json.obj()
        ownedModelsByCategory.get(category) match {
          case Some(ownedModels) =>
            ownedModels.groupBy(_.manufacturer).foreach {
              case (manufacturer, ownedModelsByThisManufacturer) => ownershipJson = ownershipJson + (manufacturer.name -> Json.toJson(ownedModelsByThisManufacturer.map(_.family).distinct))
            }
          case None =>
        }
        categoryInfoJson = categoryInfoJson + ("ownership" -> ownershipJson)
        val categoryDiscount = supplierDiscountInfo(category)
        categoryInfoJson = categoryInfoJson + ("discount" -> JsString(categoryDiscount.description))
        val (minCapacity, maxCapacity) = Category.getCapacityRange(category)
        val (minSpeed, maxSpeed) = Category.getSpeedRange(category)
        categoryInfoJson = categoryInfoJson + ("minCapacity" -> JsNumber(minCapacity)) + ("maxCapacity" -> JsNumber(maxCapacity)) + ("minSpeed" -> JsNumber(minSpeed)) + ("maxSpeed" -> JsNumber(maxSpeed))

        categoryJson = categoryJson + (category.toString -> categoryInfoJson)
    }
    Ok(categoryJson)
  }

  def getMaintenanceFactor(airlineId: Int) = AuthenticatedAirline(airlineId) { request =>
    val info = AirplaneOwnershipCache.getOwnershipInfo(airlineId)

    Ok(Json.obj("factor" -> AirplaneMaintenanceUtil.getMaintenanceFactor(airlineId),
      "baseFactor" -> AirplaneMaintenanceUtil.BASE_MAINTENANCE_FACTOR,
      "familyFactor" -> AirplaneMaintenanceUtil.PER_FAMILY_MAINTENANCE_FACTOR,
      "modelFactor" -> AirplaneMaintenanceUtil.PER_MODEL_MAINTENANCE_FACTOR,
      "families" -> Json.toJson(info.families.toList.sorted),
      "models" -> Json.toJson(info.models.map(_.name).toList.sorted),
    ))
  }

}

object AirplaneApplication {
}
