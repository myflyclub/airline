package com.patson.model

case class AirportBoost(boostType : AirportBoostType.Value, value : Double) //the value is of 1/100 for some attributes

object AirportBoostType extends Enumeration {
  type AirportBoostType = Value
  val POPULATION, INCOME, ELITE, INTERNATIONAL_HUB, ELITE_CHARM, VACATION_HUB, FINANCIAL_HUB = Value
  val getLabel = (boostType : AirportBoostType.Value) => boostType match {
    case POPULATION => "Airport Population"
    case INCOME => "Airport Income Level"
    case ELITE => "Airport Elite Population"
    case INTERNATIONAL_HUB => "International Hub Strength"
    case ELITE_CHARM => "Elite Strength"
    case VACATION_HUB => "Vacation Hub Strength"
    case FINANCIAL_HUB => "Financial Hub Strength"
  }
}