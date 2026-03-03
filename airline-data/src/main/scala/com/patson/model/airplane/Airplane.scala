package com.patson.model.airplane

import com.patson.data.{AirplaneSource, LinkSource}
import com.patson.model.{Airline, Airport, Computation, IdObject, Link, LinkClassValues, DiscountAirline, LuxuryAirline}

case class Airplane(model : Model, var owner : Airline, constructedCycle : Int, var purchasedCycle : Int, condition : Double, var purchasePrice : Int, var isSold : Boolean = false, var configuration : AirplaneConfiguration = AirplaneConfiguration.empty, var home : Airport = Airport.fromId(0), isReady : Boolean = true, version : Int = 0, var id : Int = 0) extends IdObject {
  def dealerValue : Int = Computation.calculateDealerValue(this)

  def sellToDealer(currentCycle : Int) = {
    purchasedCycle = currentCycle
    isSold = true
    configuration = AirplaneConfiguration.empty
    home = Airport.fromId(0)
  }

  def buyFromDealer(airline : Airline, currentCycle : Int) = {
    purchasePrice = Computation.calculateDealerValue(this)
    owner = airline
    isSold = false

    purchasedCycle = currentCycle
    home = airline.getHeadQuarter().get.airport

    assignDefaultConfiguration()
  }

  def assignDefaultConfiguration(): Unit = {
    val configurationOptions = AirplaneSource.loadAirplaneConfigurationsByCriteria(List(("airline", owner.id), ("model", model.id)))
    val pickedConfiguration =
      if (configurationOptions.isEmpty) { //create one for this airline
        val newConfiguration = if (this.owner.airlineType == DiscountAirline) {
          AirplaneConfiguration.economy(this.owner, this.model)
        } else if (this.owner.airlineType == LuxuryAirline) {
          AirplaneConfiguration.business(this.owner, this.model)
        } else {
          AirplaneConfiguration.default(this.owner, this.model)
        }
        AirplaneSource.saveAirplaneConfigurations(List(newConfiguration))
        newConfiguration
      } else {
        configurationOptions.head //just get the first one
      }
    this.configuration = pickedConfiguration
  }

  lazy val availableFlightMinutes : Int = {
    val occupiedFlightMinutes = AirplaneSource.loadAirplaneLinkAssignmentsByAirplaneId(id).assignments.toList.map(_._2.flightMinutes).sum

    Airplane.MAX_FLIGHT_MINUTES - occupiedFlightMinutes
  }

  lazy val utilizationRate : Double = {
    testUtilizationRate match {
      case Some(rate) => rate
      case None => (Airplane.MAX_FLIGHT_MINUTES - availableFlightMinutes).toDouble / Airplane.MAX_FLIGHT_MINUTES
    }
  }

  var testUtilizationRate : Option[Double] = None
  def setTestUtilizationRate(rate : Double): Unit = {
    testUtilizationRate = Some(rate)
  }

}

object Airplane {
  val MAX_CONDITION = 100
  val BAD_CONDITION = 45
  val CRITICAL_CONDITION = 15
  val MAX_DEALER_WEEKS = 72
  val MAX_FLIGHT_MINUTES : Int = (24 * 60 * 4.5).toInt
  val SALVAGE_VALUE_PERCENT = 0.1
}