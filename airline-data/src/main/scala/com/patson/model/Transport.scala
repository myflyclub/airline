package com.patson.model

import java.util.concurrent.ConcurrentHashMap

abstract class Transport extends IdObject{
  var id : Int
  val from, to : Airport
  val airline: Airline
  val distance : Int
  var capacity: LinkClassValues
  val duration : Int
  var frequency : Int
  val frequencyByClass : LinkClass => Int
  val transportType : TransportType.Value
//  val transportCategory : TransportCategory.Value
  val price: LinkClassValues
  var availableSeats : LinkClassValues = capacity.copy()
  var minorDelayCount : Int
  var majorDelayCount : Int
  var cancellationCount : Int

  @volatile var soldSeats : LinkClassValues = LinkClassValues.getInstance()
  @volatile var cancelledSeats :  LinkClassValues = LinkClassValues.getInstance()
  private val standardPrice : ConcurrentHashMap[(LinkClass, PassengerType.Value), Int] = new ConcurrentHashMap[(LinkClass, PassengerType.Value), Int]()

  val cost: LinkClassValues //the cost of taking this transport, could just be the price, or with the hidden cost of taking it

  def computedQuality() : Int
  def getTotalCapacity : Int = {
    capacity.total
  }

  def getTotalAvailableSeats : Int = {
    availableSeats.total
  }

  def getTotalSoldSeats : Int = {
    soldSeats.total
  }


  def addSoldSeats(soldSeats : LinkClassValues) = {
    this.soldSeats = this.soldSeats + soldSeats;
    this.availableSeats = this.availableSeats - soldSeats;
  }

  def addSoldSeatsByClass(linkClass : LinkClass, soldSeats : Int) = {
    val soldSeatsClassValues = LinkClassValues.getInstanceByMap(Map(linkClass -> soldSeats))
    addSoldSeats(soldSeatsClassValues)
  }

  def addCancelledSeats(cancelledSeats : LinkClassValues) = {
    this.cancelledSeats = this.cancelledSeats + cancelledSeats;
    this.availableSeats = this.availableSeats - cancelledSeats;
  }

  def standardPrice(linkClass : LinkClass, paxType: PassengerType.Value) : Int = {
    var price = standardPrice.get((linkClass, paxType))
    if (price == null.asInstanceOf[Int]) {
      price = Pricing.computeStandardPrice(distance, Computation.getFlightCategory(from, to), linkClass, paxType, from.baseIncome)
      standardPrice.put((linkClass, paxType), price)
    }
    price
  }

  /**
    * Find seats at or below the requestedLinkClass
    *
    * Returns the tuple of the matching class and seats available for that class
    */
  def availableSeatsAtOrBelowClass(targetLinkClass : LinkClass) : Option[(LinkClass, Int)] = {
    if (targetLinkClass == ECONOMY || targetLinkClass == DISCOUNT_ECONOMY) {
      if (availableSeats(ECONOMY) > 0) {
        return Some(ECONOMY, availableSeats(ECONOMY))
      }
    } else {
      if (availableSeats(targetLinkClass) > 0) {
        return Some(targetLinkClass, availableSeats(targetLinkClass))
      } else  {
        if (targetLinkClass.level > ECONOMY.level) {
          val lowestAcceptableLevel = if (distance < 1000) {
            1 //always accept lowest level (economy)
          } else {
            targetLinkClass.level - 1
          }

          var level = targetLinkClass.level - 1
          while (level >= lowestAcceptableLevel) {
            val lowerClass = LinkClass.fromLevel(level)
            val seatsAvailable = availableSeats(lowerClass)
            if (seatsAvailable > 0) {
              return Some(lowerClass, seatsAvailable)
            }
            level -= 1
          }
        }
      }
    }

    return None
  }
}

object TransportType extends Enumeration {
  type TransportType = Value
  val FLIGHT, GENERIC_TRANSIT = Value
}


