package com.patson.model

import scala.collection.mutable.HashMap
import java.util.concurrent.ConcurrentHashMap
import com.patson.model.Scheduling.TimeSlot
import com.patson.model.airplane.{Airplane, LinkAssignment, Model}
import com.patson.util.AirportCache
import com.patson.model

/**
 *
 * Frequency sum of all assigned plane
 */
case class Link(from : Airport, to : Airport, airline: Airline, price : LinkClassValues, distance : Int, var capacity: LinkClassValues, rawQuality : Int, duration : Int, var frequency : Int, var flightNumber : Int = 0, var id : Int = 0) extends Transport {
  override val transportType = TransportType.FLIGHT
  override val cost = price
  @volatile override var cancellationCount = 0
  @volatile override var majorDelayCount = 0
  @volatile override var minorDelayCount = 0
  @volatile private var assignedAirplanes : Map[Airplane, LinkAssignment] = Map.empty
  @volatile private var assignedModel : Option[Model] = None

  @volatile private var hasComputedQuality = false
  @volatile private var computedQualityStore : Int = 0

  var inServiceAirplanes : Map[Airplane, LinkAssignment] = Map.empty

  def setAssignedAirplanes(assignedAirplanes : Map[Airplane, LinkAssignment]) = {
    this.assignedAirplanes = assignedAirplanes
    if (!assignedAirplanes.isEmpty) {
      assignedModel = Some(assignedAirplanes.toList(0)._1.model)
    }
    inServiceAirplanes = this.assignedAirplanes.filter(_._1.isReady)
    recomputeCapacityAndFrequency()
  }

  /**
    * for testing only. would not recompute frequency and capacity
    * @param assignedAirplanes
    */
  def setTestingAssignedAirplanes(assignedAirplanes : Map[Airplane, Int]) = {
    this.assignedAirplanes = assignedAirplanes.toList.map {
      case (airplane, frequency) => (airplane, LinkAssignment(frequency, Computation.calculateFlightMinutesRequired(airplane.model, distance)))
    }.toMap

    if (!assignedAirplanes.isEmpty) {
      assignedModel = Some(assignedAirplanes.toList(0)._1.model)
    }
    inServiceAirplanes = this.assignedAirplanes.filter(_._1.isReady)
  }

  def getAssignedAirplanes() = {
    assignedAirplanes
  }

  def getAssignedModel() : Option[Model] = {
    assignedModel
  }

  def setAssignedModel(model : Model) = {
    this.assignedModel = Some(model)
  }

  override def computedQuality() : Int= {
    if (!hasComputedQuality) {

      if (inServiceAirplanes.isEmpty) {
        0
      } else {
        val airplaneConditionQuality = inServiceAirplanes.toList.map {
          case ((airplane, assignmentPerAirplane)) => 20 * airplane.condition / Airplane.MAX_CONDITION * assignmentPerAirplane.frequency
        }.sum / frequency
        val airplaneTypeQuality = getAssignedModel() match {
          case Some(model) => Math.pow(model.quality + 1, 1.7) - 9
          case None => 0
        }
        val serviceQuality = 30 * (rawQuality.toDouble - 20) / (Link.MAX_QUALITY - 20)
        val laborQuality = 30 * (airline.airlineInfo.currentServiceQuality / Airline.EQ_MAX)
        computedQualityStore = (airplaneTypeQuality + serviceQuality + laborQuality + airplaneConditionQuality).toInt
        hasComputedQuality = true
        computedQualityStore
      }
    } else {
      computedQualityStore
    }
  }

  def setQuality(quality : Int) = {
    computedQualityStore = quality
    hasComputedQuality = true
  }

  def capacityPerFlight() = {
    if (frequency > 0) {
      capacity / frequency
    } else { //error 
      LinkClassValues.getInstance()
    }
  }

  lazy val getFutureOfficeStaffRequired : Int = {
    getOfficeStaffRequired(from, to, futureFrequency(), futureCapacity(), airline, rawQuality, getAssignedModel())
  }

  lazy val getFutureOfficeStaffBreakdown : StaffBreakdown = {
    getOfficeStaffBreakdown(from, to, futureFrequency(), futureCapacity(), airline, rawQuality, getAssignedModel())
  }

  lazy val getCurrentOfficeStaffRequired : Int = {
    getOfficeStaffRequired(from, to, frequency, capacity, airline, rawQuality, getAssignedModel())
  }

  var loadedFrequencyByClass : LinkClassValues = LinkClassValues.getInstance()
  var frequencyByClassLoaded = false

  /**
    * Recomputes capacity base on assigned airplanes
    */
  private def recomputeCapacityAndFrequency() = {
    var newCapacity = LinkClassValues.getInstance()
    var newFrequency = 0
    val newFrequencyByClass = HashMap[LinkClass, Int]()

    inServiceAirplanes.foreach {
      case(airplane, assignment) =>
        newCapacity = newCapacity + (LinkClassValues(airplane.configuration.economyVal, airplane.configuration.businessVal, airplane.configuration.firstVal) * assignment.frequency)
        newFrequency += assignment.frequency

        LinkClass.values.foreach { linkClass =>
          if (airplane.configuration(linkClass) > 0) {
            newFrequencyByClass.put(linkClass, newFrequencyByClass.getOrElse(linkClass, 0) + assignment.frequency)
          }
        }
    }
    capacity = newCapacity
    frequency = newFrequency
    loadedFrequencyByClass = LinkClassValues.getInstanceByMap(newFrequencyByClass.toMap)
    frequencyByClassLoaded = true
  }

  def futureCapacity() = {
    var futureCapacity = LinkClassValues.getInstance()
    assignedAirplanes.foreach {
      case(airplane, assignment) => futureCapacity = futureCapacity + (LinkClassValues(airplane.configuration.economyVal, airplane.configuration.businessVal, airplane.configuration.firstVal) * assignment.frequency)
    }
    futureCapacity
  }

  def futureFrequency() = {
    assignedAirplanes.values.map(_.frequency).sum
  }

  override def toString() = {
    s"Flight $id; ${airline.name}; ${from.city}(${from.iata}) => ${to.city}(${to.iata}); distance $distance; freq $frequency; capacity $capacity; price $price"
  }

  lazy val schedule : Seq[TimeSlot] = Scheduling.getLinkSchedule(this)

  lazy val getOfficeStaffRequired = (from : Airport, to : Airport, frequency : Int, capacity : LinkClassValues, airline : Airline, rawQuality : Int, model : Option[Model]) => {
    getOfficeStaffBreakdown(from, to, frequency, capacity, airline, rawQuality, model).total
  }

  lazy val getOfficeStaffBreakdown = (from : Airport, to : Airport, frequency : Int, capacity : LinkClassValues, airline : Airline, rawQuality : Int, modelOption : Option[Model]) => {
    val flightCategory = Computation.getFlightCategory(from, to)
    val model: Model.Type.Value = modelOption match {
      case Some(model) => model.airplaneType
      case None => Model.Type.SMALL
    }
    val airlineBaseModifier : Double = AirportCache.getAirport(from.id, fullLoad = true).get.getAirlineBase(airline.id).map(_.getStaffModifier(flightCategory, model, rawQuality)).getOrElse(1)
    if (frequency == 0) { //future flights
      StaffBreakdown(0, 0, 0, airlineBaseModifier)
    } else {
      val StaffSchemeBreakdown(basicStaff, perFrequencyStaff, per500PaxStaff) = Link.getStaffRequired(distance, flightCategory, airline.airlineType)
      StaffBreakdown(basicStaff, perFrequencyStaff * frequency, per500PaxStaff * capacity.totalwithSeatSize / 500, airlineBaseModifier)
    }
  }
  override val frequencyByClass = (linkClass : LinkClass) => {
    if (frequencyByClassLoaded) {
      loadedFrequencyByClass(linkClass)
    } else {
      frequency
    }
  }

}

object Link {
  val MAX_QUALITY = 100
  val HIGH_FREQUENCY_THRESHOLD = 21
  val LINK_NEGOTIATION_COOL_DOWN = 6
  def fromId(id : Int) : Link = {
    Link(from = Airport.fromId(0), to = Airport.fromId(0), Airline.fromId(0), price = LinkClassValues.getInstance(), distance = 0, capacity = LinkClassValues.getInstance(), rawQuality = 0, duration = 0, frequency = 0, id = id)
  }

  def getStaffRequired(distance: Int, flightCategory: FlightCategory.Value, airlineType: AirlineType.AirlineType) : StaffSchemeBreakdown = {
    val base = if (flightCategory == FlightCategory.INTERNATIONAL) 6 else 3
    val multiplier = if (flightCategory == FlightCategory.INTERNATIONAL) {
      Math.pow(distance + 1600, 0.16) - 1.5
    } else {
      Math.pow(distance + 1000, 0.15) - 2
    }
    val staffPerFrequency = if (airlineType == AirlineType.REGIONAL) {0.1 * multiplier} else {0.5 * multiplier}
    val staffPer500Pax = 1.25 * multiplier
    StaffSchemeBreakdown(base, staffPerFrequency, staffPer500Pax)
  }
}

case class StaffBreakdown(basicStaff : Int, frequencyStaff : Double, capacityStaff : Double, modifier : Double) {
  val total = (math.round(basicStaff + frequencyStaff + capacityStaff) * modifier).toInt
}
case class StaffSchemeBreakdown(basic : Int, perFrequency : Double, per500Pax : Double)

trait CostModifier {
  def value(link : Transport, linkClass : LinkClass, paxType: PassengerType.Value) : Double
}

object ExplicitLinkConsideration {

}

object LinkConsideration {
  val DUMMY_PASSENGER_GROUP  = PassengerGroup(Airport.fromId(0), new DealPreference(Airport.fromId(0), ECONOMY, 1.0), PassengerType.TRAVELER)
  def getExplicit(link : Transport, cost : Double, linkClass : LinkClass, inverted : Boolean, id : Int = 0) : LinkConsideration = {
    LinkConsideration(link, linkClass, inverted, DUMMY_PASSENGER_GROUP, None, SimpleCostProvider(cost), id)
  }
}


/**
 * Cost is the adjusted price
 */
case class LinkConsideration(link : Transport,
                             linkClass : LinkClass,
                             inverted : Boolean,
                             passengerGroup : PassengerGroup,
                             modifier : Option[CostModifier],
                             costProvider : CostProvider,
                             var id : Int = 0) extends IdObject {
    lazy val from : Airport = if (inverted) link.to else link.from
    lazy val to : Airport = if (inverted) link.from else link.to

    override def toString() : String = {
      s"Consideration [${linkClass} -  Flight $id; ${link.airline.name}; ${from.city}(${from.iata}) => ${to.city}(${to.iata}); capacity ${link.capacity}; price ${link.price}; cost: $cost]"
    }


    lazy val cost : Double = costProvider(this)

      //costSet.getOrElse()

    def copyWithCost(explicitCost : Double) : LinkConsideration = {
      this.copy(costProvider = SimpleCostProvider(explicitCost))
    }
}

trait CostProvider {
  def apply(linkConsideration: LinkConsideration) : Double
}
case class SimpleCostProvider(cost : Double) extends CostProvider{
  override def apply(linkConsideration: LinkConsideration) : Double = cost
}
case class CostStoreProvider() extends CostProvider {
  var computed = false
  var computedValue : Double = 0
  override def apply(linkConsideration: LinkConsideration) : Double = {
    //this.synchronized { //no sync as it does not have to be threadsafe
      if (!computed) {
        computedValue = linkConsideration.passengerGroup.preference.computeCost(
          linkConsideration.link,
          linkConsideration.linkClass,
          linkConsideration.passengerGroup.passengerType,
          linkConsideration.modifier.map(_.value(linkConsideration.link, linkConsideration.linkClass, linkConsideration.passengerGroup.passengerType)).getOrElse(1.0))
        computed = true
      }
    //}
    computedValue
  }


}


sealed abstract class LinkClass(val code : String, val spaceMultiplier : Double, val resourceMultiplier : Double, val priceSensitivity : Double, val level : Int) {
  def label : String //level for sorting/comparison purpose
}
case object FIRST extends LinkClass("F", spaceMultiplier = 6, resourceMultiplier = 3.6, priceSensitivity = 0.75, level = 3) {
  override def label = "first"
}
case object BUSINESS extends LinkClass("J", spaceMultiplier = 2.5, resourceMultiplier = 1.5, priceSensitivity = 0.85, level = 2) {
  override def label = "business"
}
case object ECONOMY extends LinkClass("Y", spaceMultiplier = 1, resourceMultiplier = 1.1, priceSensitivity = 0.95, level = 1) {
  override def label = "economy"
}
case object DISCOUNT_ECONOMY extends LinkClass("D", spaceMultiplier = 1, resourceMultiplier = 1.0, priceSensitivity = 1.05, level = 0) {
  override def label = "discountEconomy"
}
object LinkClass {
  val values = List(FIRST, BUSINESS, ECONOMY, DISCOUNT_ECONOMY)

  val fromCode : String => LinkClass = (code : String) => {
    values.find { _.code == code }.get
  }

  val fromLevel : Int => LinkClass = (level : Int) => {
    values.find { _.level  == level}.get
  }
}
