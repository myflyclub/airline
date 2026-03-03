package com.patson.model.airplane

import com.patson.data.{AirplaneSource, CycleSource, DelegateSource}
import com.patson.model.{AircraftModelDelegateTask, BusyDelegate, DelegateTaskType}
import com.patson.model.airplane.Model.Category
import com.patson.model.airplane.Model.Type.{JUMBO, LARGE, MEDIUM, MEDIUM_XL, PROPELLER_MEDIUM, PROPELLER_SMALL, REGIONAL, REGIONAL_XL, SMALL}
import com.patson.util.{AirplaneModelCache, AirplaneOwnershipCache}

import scala.collection.MapView
import scala.collection.mutable.ListBuffer

case class ModelDiscount(modelId : Int, discount : Double, discountType : DiscountType.Value, discountReason : DiscountReason.Value, expirationCycle : Option[Int]) {
  val description = discountReason match {
    case DiscountReason.PREFERRED_SUPPLIER => s"${(discount * 100).toInt}% off ${DiscountType.description(discountType)} for being preferred supplier"
    case DiscountReason.LOW_DEMAND => s"${(discount * 100).toInt}% off ${DiscountType.description(discountType)} due to low demand"
  }
}

object ModelDiscount {
  val MIN_PRICE_DISCOUNT_PERCENTAGE = 5
  val MAX_PRICE_DISCOUNT_PERCENTAGE = 50
  val CONSTRUCTION_TIME_DISCOUNT = 99

  val getModelLowDemandDiscountThreshold = (model: Model) => {
    model.airplaneType match {
      case SMALL => 160
      case PROPELLER_SMALL => 160
      case PROPELLER_MEDIUM => 320
      case REGIONAL => 600
      case REGIONAL_XL => 600
      case MEDIUM => 600
      case LARGE => 180
      case _ => 140
    }
  }

  private def computeManagerMultiplier(managers: List[BusyDelegate], currentCycle: Int): Double = {
    if (managers.isEmpty) return 0.0
    val totalLevel = managers.map { delegate =>
      delegate.assignedTask.asInstanceOf[AircraftModelDelegateTask].level(currentCycle)
    }.sum
    Math.min(1.0, totalLevel * 0.125)
  }

  private def computeLowDemandDiscounts(model: Model, totalOwnedCount: Int, multiplier: Double): List[ModelDiscount] = {
    val threshold = getModelLowDemandDiscountThreshold(model)
    val delta = threshold - totalOwnedCount
    val discounts = ListBuffer[ModelDiscount]()
    if (delta > 0 && multiplier > 0) {
      val priceFactor = delta * MAX_PRICE_DISCOUNT_PERCENTAGE * 0.01 / threshold
      if (priceFactor > 0) {
        discounts.append(ModelDiscount(model.id, priceFactor * multiplier, DiscountType.PRICE, DiscountReason.LOW_DEMAND, None))
      }
      val constructionFactor = delta * CONSTRUCTION_TIME_DISCOUNT * 0.01 / threshold
      if (constructionFactor > 0) {
        discounts.append(ModelDiscount(model.id, constructionFactor * multiplier, DiscountType.CONSTRUCTION_TIME, DiscountReason.LOW_DEMAND, None))
      }
    }
    discounts.toList
  }

  def computeMaxLowDemandPriceDiscountPct(model: Model, totalOwnedCount: Int): Double = {
    val threshold = getModelLowDemandDiscountThreshold(model)
    val delta = threshold - totalOwnedCount
    if (delta <= 0) 0.0
    else Math.min(MAX_PRICE_DISCOUNT_PERCENTAGE.toDouble, delta.toDouble / threshold * MAX_PRICE_DISCOUNT_PERCENTAGE)
  }

  def getAllCombinedDiscountsByAirlineId(airlineId : Int) : Map[Int, List[ModelDiscount]] = {
    val supplierDiscountInfoByCategory : Map[Model.Category.Value, PreferredSupplierDiscountInfo] = getPreferredSupplierDiscounts(airlineId)
    val countsByModelId = AirplaneSource.loadAirplaneModelCounts()
    val currentCycle = CycleSource.loadCycle()

    val managersByModelId: Map[Int, List[BusyDelegate]] = DelegateSource.loadBusyDelegatesByAirline(airlineId)
      .filter(_.assignedTask.getTaskType == DelegateTaskType.MANAGER_AIRCRAFT_MODEL)
      .groupBy(_.assignedTask.asInstanceOf[AircraftModelDelegateTask].modelId)

    AirplaneModelCache.allModels.values.map { model =>
      val discounts = ListBuffer[ModelDiscount]()

      val managers = managersByModelId.getOrElse(model.id, List.empty)
      val multiplier = computeManagerMultiplier(managers, currentCycle)
      discounts.appendAll(computeLowDemandDiscounts(model, countsByModelId.getOrElse(model.id, 0), multiplier))

      getPreferredSupplierDiscountByModelId(supplierDiscountInfoByCategory, model.id).foreach(discounts.append(_))

      (model.id, discounts.toList)
    }.toMap
  }

  /**
    * Get discounts including both specific to airline and those blanket to model
    * @param airlineId
    * @param modelId
    * @return
    */
  def getCombinedDiscountsByModelId(airlineId : Int, modelId : Int) : List[ModelDiscount] = {
    val model = AirplaneModelCache.getModel(modelId).get
    val discounts = ListBuffer[ModelDiscount]()

    val managers = DelegateSource.loadAircraftModelDelegatesByAirlineAndModel(airlineId, modelId)
    val currentCycle = CycleSource.loadCycle()
    val multiplier = computeManagerMultiplier(managers, currentCycle)
    val totalOwnedCount = AirplaneSource.loadAirplaneModelCounts().getOrElse(modelId, 0)
    discounts.appendAll(computeLowDemandDiscounts(model, totalOwnedCount, multiplier))

    getPreferredSupplierDiscountByModelId(airlineId, modelId).foreach(discounts.append(_))

    discounts.toList
  }


  def getPreferredSupplierDiscounts(airlineId: Int) : Map[Model.Category.Value, PreferredSupplierDiscountInfo] = {
    val currentSuppliersByCategory : MapView[Model.Category.Value, List[Manufacturer]] = AirplaneOwnershipCache.getOwnership(airlineId).groupBy(_.model.category).view.mapValues(_.map(_.model.manufacturer).distinct)
    Category.values.toList.map { category =>
      val info = currentSuppliersByCategory.get(category) match {
        case None => PreferredSupplierDiscountInfo(None, category, None, "No supplier")
        case Some(currentSuppliers) =>
          if (currentSuppliers.length == 1) {
            val discount = category match {
              case Category.SPECIAL => 0.05
              case Category.SMALL => 0.05
              case Category.REGIONAL => 0.05
              case Category.MEDIUM => 0.03
              case Category.LARGE => 0.02
              case Category.EXTRAORDINARY => 0.02
            }
            if (discount > 0) {
              PreferredSupplierDiscountInfo(Some(discount), category, Some(currentSuppliers(0)), s"${(discount * 100).toInt}% discount")
            } else {
              PreferredSupplierDiscountInfo(None, category, Some(currentSuppliers(0)), s"${category.toString} offers no discount")
            }
          } else {
            PreferredSupplierDiscountInfo(None, category, None, "No discount, because there is more than one supplier")
          }
      }
      (category, info)
    }.toMap
  }
  case class PreferredSupplierDiscountInfo(discount : Option[Double], category: Model.Category.Value, soleSupplier : Option[Manufacturer], description : String)



  def getPreferredSupplierDiscountByModelId(airlineId : Int, modelId : Int) : Option[ModelDiscount] = {
    getPreferredSupplierDiscountByModelId(getPreferredSupplierDiscounts(airlineId), modelId)
  }

  def getPreferredSupplierDiscountByModelId(lookup : Map[Category.Value, PreferredSupplierDiscountInfo], modelId : Int) : Option[ModelDiscount] = {
    val model = AirplaneModelCache.getModel(modelId).get
    val supplierDiscountInfo = lookup(model.category)

    supplierDiscountInfo.soleSupplier match {
      case Some(soleSupplier) =>
        if (soleSupplier == model.manufacturer) {
          supplierDiscountInfo.discount match {
            case Some(discount) => Some(ModelDiscount(modelId, discount, DiscountType.PRICE, DiscountReason.PREFERRED_SUPPLIER, None))
            case None => None
          }
        } else {
          None
        }
      case None => None
    }
  }
}



object DiscountReason extends Enumeration {
  type Type = Value
  val LOW_DEMAND, PREFERRED_SUPPLIER = Value
}

object DiscountType extends Enumeration {
  type Type = Value
  val PRICE, CONSTRUCTION_TIME = Value

  val description = (discountType : DiscountType.Value) => { discountType match {
      case PRICE => "Price"
      case CONSTRUCTION_TIME => "Construction Time"
      case _ => "Unknown"
    }
  }
}
