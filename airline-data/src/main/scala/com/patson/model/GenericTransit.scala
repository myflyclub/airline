package com.patson.model

case class GenericTransit(from : Airport, to : Airport, distance : Int, var capacity: LinkClassValues, var id : Int = 0) extends Transport {
  override val transportType : TransportType.Value = TransportType.GENERIC_TRANSIT
  override val duration : Int = (distance.toDouble / 40 * 60).toInt
  override var frequency : Int = 24 * 7
  override def computedQuality() : Int = GenericTransit.QUALITY //constant quality
  override val price : LinkClassValues = LinkClassValues.getInstance()
  val flightCategory = FlightCategory.DOMESTIC

  val localCostRatio = 0.7
  override val cost : LinkClassValues = LinkClassValues.getInstance(
    economy = ((Pricing.computeStandardPrice(distance, FlightCategory.DOMESTIC, ECONOMY, PassengerType.TRAVELER, from.baseIncome) - Pricing.PRICE_BASE) * localCostRatio).toInt,
    business = ((Pricing.computeStandardPrice(distance, FlightCategory.DOMESTIC, BUSINESS, PassengerType.TRAVELER, from.baseIncome) - Pricing.PRICE_BASE) * localCostRatio).toInt,
    first = (Pricing.computeStandardPrice(distance, FlightCategory.DOMESTIC, FIRST, PassengerType.TRAVELER, from.baseIncome) * localCostRatio).toInt
  )

  val upkeep = 0
  override var minorDelayCount : Int = 0
  override var majorDelayCount : Int = 0
  override var cancellationCount : Int = 0

  override def toString() = {
    s"Generic transit $id; ${from.city}(${from.iata}) => ${to.city}(${to.iata}); distance $distance"
  }

  override val frequencyByClass  = (_ : LinkClass) =>  frequency
  override val airline : Airline = GenericTransit.TRANSIT_PROVIDER
}

object GenericTransit {
  val QUALITY = 99 //note, pax preference does not consider quality if local, but setting 99 for filter
  val TRANSIT_PROVIDER = Airline.fromId(0).copy(name = "Local Transit")
}


