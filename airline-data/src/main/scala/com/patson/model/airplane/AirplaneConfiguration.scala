package com.patson.model.airplane

import com.patson.model.{AbstractLinkClassValues, Airline, BUSINESS, ECONOMY, FIRST, LinkClassValues}

case class AirplaneConfiguration(economyVal : Int, businessVal : Int, firstVal : Int, airline : Airline, model : Model, isDefault : Boolean, var id : Int = 0) extends AbstractLinkClassValues(economyVal, businessVal, firstVal) {
  lazy val minimized : AirplaneConfiguration = { //config that has least capacity
    val minimizedFirst = (model.capacity / FIRST.spaceMultiplier).toInt
    val minimizedBusiness = ((model.capacity - minimizedFirst * FIRST.spaceMultiplier) / BUSINESS.spaceMultiplier).toInt
    //no eco as user can lock econ to zero
    AirplaneConfiguration(0, minimizedBusiness, minimizedFirst, airline, model, isDefault)
  }
}

object AirplaneConfiguration {
  val empty = AirplaneConfiguration(0, 0, 0, Airline.fromId(0), Model.fromId(0), true)
  val default : ((Airline, Model) => AirplaneConfiguration) = (airline, model) => {
    if (model.quality == 10) {
      if (model.capacity >= 100) {
        val first = ((model.capacity * 0.4) / FIRST.spaceMultiplier).toInt
        val biz = ((model.capacity * 0.6) / BUSINESS.spaceMultiplier).toInt
        AirplaneConfiguration(0, biz, first, airline, model, true)
      } else {
        val biz = ((model.capacity).toDouble / BUSINESS.spaceMultiplier).toInt
        AirplaneConfiguration(0, biz, 0, airline, model, true)
      }
    } else if (model.quality >= 7) {
      if (model.capacity >= 200) {
        val first = ((model.capacity * 0.2) / FIRST.spaceMultiplier).toInt
        val biz = ((model.capacity * 0.3) / BUSINESS.spaceMultiplier).toInt
        val econ = (model.capacity - (biz * BUSINESS.spaceMultiplier + first * FIRST.spaceMultiplier)).toInt
        AirplaneConfiguration(econ, biz, first, airline, model, true)
      } else if (model.capacity <= 40) {
        val biz = ((model.capacity).toDouble / BUSINESS.spaceMultiplier).toInt
        AirplaneConfiguration(0, biz ,0, airline, model, true)
      } else {
        val biz = ((model.capacity * 0.2) / BUSINESS.spaceMultiplier).toInt
        val econ = (model.capacity - biz * BUSINESS.spaceMultiplier).toInt
        AirplaneConfiguration(econ, biz ,0, airline, model, true)
      }
    } else if (model.quality >= 6) {
      if (model.capacity >= 75) {
        val biz = ((model.capacity * 0.2) / BUSINESS.spaceMultiplier).toInt
        val econ = (model.capacity - biz * BUSINESS.spaceMultiplier).toInt
        AirplaneConfiguration(econ, biz , 0, airline, model, true)
      } else {
        AirplaneConfiguration(economyVal = model.capacity, 0 ,0, airline, model, true)
      }
    } else {
      AirplaneConfiguration(economyVal = model.capacity, 0 ,0, airline, model, true)
    }
  }
  val economy : ((Airline, Model) => AirplaneConfiguration) = (airline, model) => AirplaneConfiguration((model.capacity.toDouble / ECONOMY.spaceMultiplier).toInt, 0, 0, airline, model, false)
  val business : ((Airline, Model) => AirplaneConfiguration) = (airline, model) => AirplaneConfiguration(0, (model.capacity.toDouble / BUSINESS.spaceMultiplier).toInt, 0, airline, model, false)
  val first : ((Airline, Model) => AirplaneConfiguration) = (airline, model) => AirplaneConfiguration(0, 0, (model.capacity.toDouble / FIRST.spaceMultiplier).toInt, airline, model, false)
  val MAX_CONFIGURATION_TEMPLATE_COUNT = 5 //per model and airline
  val MIN_SEATS_PER_CLASS = 6
}