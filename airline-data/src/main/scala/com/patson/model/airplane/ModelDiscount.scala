package com.patson.model.airplane

import com.patson.data.{AirplaneSource, CycleSource, ManagerSource}
import com.patson.model.{AircraftModelManagerTask, Manager, ManagerTaskType}
import com.patson.model.airplane.Model.Category
import com.patson.util.{AirplaneModelCache, AirplaneOwnershipCache}

import scala.collection.MapView
import scala.collection.mutable.ListBuffer

case class ModelDiscount(modelId : Int, discount : Double, discountType : DiscountType.Value, discountReason : DiscountReason.Value) {
  val description = discountReason match {
    case DiscountReason.PREFERRED_SUPPLIER => s"${(discount * 100).toInt}% off ${DiscountType.description(discountType)} for having one preferred supplier."
    case DiscountReason.LOW_DEMAND => s"${(discount * 100).toInt}% off ${DiscountType.description(discountType)} from manufacturer relationship."
  }
}

object ModelDiscount {
  val MIN_PRICE_DISCOUNT_PERCENTAGE = 10
  val MAX_PRICE_DISCOUNT_PERCENTAGE = 50
  val MIN_CONSTRUCTION_TIME_DISCOUNT_PERCENTAGE = 30
  val MAX_CONSTRUCTION_TIME_DISCOUNT_PERCENTAGE = 100
  val TOOLTIP = List(
    s"There are two types of discounts: Preferred Supplier and Manufacturer Relationship.",
    s"Preferred Suppler discounts are gained by having only one supplier for a plane category, as listed below.",
    s"Manufacturer Relationship is improved by attaching a manager to a model. Managers will level up over time, increasing the discount and lowering delivery time, of at least ${MIN_PRICE_DISCOUNT_PERCENTAGE}% price discount when the manager is fully leveled up and up to ${MAX_PRICE_DISCOUNT_PERCENTAGE}% when fewer of that model are in service.",
  )

  val getModelLowDemandDiscountThreshold = (category: Model.Category.Value) => {
    category match {
      case Model.Category.SMALL => 450
      case Model.Category.REGIONAL => 700
      case Model.Category.MEDIUM => 600
      case Model.Category.LARGE => 180
      case _ => 140
    }
  }

  private def computeManagerMultiplier(managers: List[Manager], currentCycle: Int): Double = {
    val totalLevel = managers.flatMap { delegate =>
      delegate.assignedTask match {
        case task: AircraftModelManagerTask => Some(task.level(currentCycle))
        case _ => None
      }
    }.sum
    Math.min(1.0, totalLevel * 0.125)
  }

  private def computeLowDemandDiscounts(model: Model, totalOwnedCount: Int, multiplier: Double): List[ModelDiscount] = {
    if (multiplier <= 0.0) return Nil

    val threshold = getModelLowDemandDiscountThreshold(model.category).toDouble

    if (totalOwnedCount >= threshold) return List(
      ModelDiscount(model.id, MIN_PRICE_DISCOUNT_PERCENTAGE * multiplier / 100, DiscountType.PRICE, DiscountReason.LOW_DEMAND),
      ModelDiscount(model.id, MIN_CONSTRUCTION_TIME_DISCOUNT_PERCENTAGE * multiplier / 100, DiscountType.CONSTRUCTION_TIME, DiscountReason.LOW_DEMAND)
    )

    // Clamped to 1.0 to prevent >MAX discounts if totalOwnedCount is somehow negative.
    val unmetFraction = Math.min(1.0, (threshold - totalOwnedCount) / threshold)

    val priceBaseDiscount = MIN_PRICE_DISCOUNT_PERCENTAGE + ((MAX_PRICE_DISCOUNT_PERCENTAGE - MIN_PRICE_DISCOUNT_PERCENTAGE) * unmetFraction)
    val finalPriceDiscount = priceBaseDiscount * multiplier / 100

    val timeBaseDiscount = MIN_CONSTRUCTION_TIME_DISCOUNT_PERCENTAGE + ((MAX_CONSTRUCTION_TIME_DISCOUNT_PERCENTAGE - MIN_CONSTRUCTION_TIME_DISCOUNT_PERCENTAGE) * unmetFraction)
    val finalTimeDiscount = timeBaseDiscount * multiplier / 100

    List(
      ModelDiscount(model.id, finalPriceDiscount, DiscountType.PRICE, DiscountReason.LOW_DEMAND),
      ModelDiscount(model.id, finalTimeDiscount, DiscountType.CONSTRUCTION_TIME, DiscountReason.LOW_DEMAND)
    )
  }

  /**
   * Used on frontend
   * @param model
   * @param totalOwnedCount
   * @return
   */
  def computeMaxLowDemandPriceDiscountPct(model: Model, totalOwnedCount: Int): Double = {
    val threshold = getModelLowDemandDiscountThreshold(model.category)
    val delta = threshold - totalOwnedCount
    if (delta <= 0) 0.0
    else Math.max(
      MIN_PRICE_DISCOUNT_PERCENTAGE.toDouble,
      Math.min(MAX_PRICE_DISCOUNT_PERCENTAGE.toDouble, delta.toDouble / threshold * MAX_PRICE_DISCOUNT_PERCENTAGE)
    )
  }

  def computeMaxLowDemandConstructionTimeDiscountPct(model: Model, totalOwnedCount: Int): Double = {
    val threshold = getModelLowDemandDiscountThreshold(model.category)
    val delta = threshold - totalOwnedCount
    if (delta <= 0) 0.0
    else Math.max(
      MIN_CONSTRUCTION_TIME_DISCOUNT_PERCENTAGE.toDouble,
      Math.min(MAX_CONSTRUCTION_TIME_DISCOUNT_PERCENTAGE.toDouble, delta.toDouble / threshold * MAX_CONSTRUCTION_TIME_DISCOUNT_PERCENTAGE)
    )
  }
  /**
   * Used on frontend
   * @param airlineId
   * @return
   */
  def getAllCombinedDiscountsByAirlineId(airlineId : Int) : Map[Int, List[ModelDiscount]] = {
    val supplierDiscountInfoByCategory : Map[Model.Category.Value, PreferredSupplierDiscountInfo] = getPreferredSupplierDiscounts(airlineId)
    val countsByModelId = AirplaneSource.loadAirplaneModelCounts()
    val currentCycle = CycleSource.loadCycle()

    val managersByModelId: Map[Int, List[Manager]] = ManagerSource.loadBusyDelegatesByAirline(airlineId)
      .filter(_.assignedTask.getTaskType == ManagerTaskType.MANAGER_AIRCRAFT_MODEL)
      .groupBy(_.assignedTask.asInstanceOf[AircraftModelManagerTask].modelId)

    AirplaneModelCache.allModels.values.map { model =>
      val discounts = ListBuffer[ModelDiscount]()

      val managers = managersByModelId.getOrElse(model.id, List.empty)
      val multiplier = computeManagerMultiplier(managers, currentCycle)
      discounts.appendAll(computeLowDemandDiscounts(model, countsByModelId.getOrElse(model.id, 0), multiplier))

      getPreferredSupplierDiscountByModelId(supplierDiscountInfoByCategory, model.id).foreach(discounts.append)

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

    val managers = ManagerSource.loadAircraftModelDelegatesByAirlineAndModel(airlineId, modelId)
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
            case Some(discount) => Some(ModelDiscount(modelId, discount, DiscountType.PRICE, DiscountReason.PREFERRED_SUPPLIER))
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
