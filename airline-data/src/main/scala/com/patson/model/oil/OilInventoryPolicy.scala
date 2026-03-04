package com.patson.model.oil
import com.patson.model.Airline

object OilInventoryPolicyOption extends Enumeration {
  type OilInventoryPolicyOption = Value
  val CONSERVATIVE, BALANCED, AGGRESSIVE, NONE = Value
}

import OilInventoryPolicyOption._

case class OilInventoryPolicy(airline : Airline, factor : Double, startCycle : Int) {
  val inventoryPrice = (marketPrice : Double) => {
    val deltaFromDefault = marketPrice - OilPrice.DEFAULT_PRICE
    BigDecimal(OilPrice.DEFAULT_PRICE + deltaFromDefault * (1 - factor)).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble
  }
  
  val option = factor match {
    case 0.9 => CONSERVATIVE
    case 0.5 => BALANCED
    case 0.2 => AGGRESSIVE
    case 0 => NONE
  }
}


object OilInventoryPolicy {
  val MIN_CHANGE_DURATION = 48 //how many weeks before one can change the policy again
  val RISK_PREMIUM = 0.05
  def byOption(option : OilInventoryPolicyOption.Value, airline : Airline, startCycle : Int): OilInventoryPolicy = {
    val factor : Double = 
      option match {
        case CONSERVATIVE => 0.9
        case BALANCED => 0.5
        case AGGRESSIVE => 0.2
        case NONE => 0
      }
    
    OilInventoryPolicy(airline, factor, startCycle)
  }
  
  def getDefaultPolicy(airline : Airline): OilInventoryPolicy = {
    byOption(CONSERVATIVE, airline, 0) 
  }
  
  val description = (value : Value) => {
    value match {
        case CONSERVATIVE => s"Conservative - shields from 90% of price fluctuation but 4.5% risk premium."
        case BALANCED => s"Balanced - shields from 50% of price fluctuation but 2.5% risk premium."
        case AGGRESSIVE => s"Aggressive - shields from 20% of price fluctuation but 1.0% risk premium."
        case NONE => "No Inventory - buys all required fuel at market price"
      }
  }
    
}

