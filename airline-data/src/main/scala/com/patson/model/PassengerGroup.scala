package com.patson.model

object PassengerType extends Enumeration {
    val BUSINESS, TOURIST, OLYMPICS, ELITE, TRAVELER, TRAVELER_SMALL_TOWN = Value
    val TRAVELER_SMALL_TOWN_CEILING = 100000

    val label = (paxType : PassengerType.Value) => paxType match {
        case BUSINESS => "Business"
        case TOURIST => "Tourist"
        case OLYMPICS => "Olympic"
        case ELITE => "Elite"
        case TRAVELER => "Traveler"
        case TRAVELER_SMALL_TOWN => "Traveler Small Town"
    }
    val priceAdjust = (paxType: PassengerType.Value) => paxType match {
        case TOURIST => 1.0
        case TRAVELER => 1.05
        case TRAVELER_SMALL_TOWN => 1.1
        case _ => 1.15
    }
    val routeCostTolerance = (paxType: PassengerType.Value) => paxType match {
        case TRAVELER_SMALL_TOWN => 1.75
        case TRAVELER => 1.4
        case BUSINESS => 1.2
        case _ => 1.15
    }
}

case class PassengerGroup(fromAirport : Airport, preference : FlightPreference, passengerType : PassengerType.Value)

