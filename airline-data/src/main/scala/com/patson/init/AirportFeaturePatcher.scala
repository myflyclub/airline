package com.patson.init

import com.patson.model._
import com.patson.data.{AirportSource, DestinationSource, GameConstants}

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

object AirportFeaturePatcher extends App {

  import AirportFeatureType._

  lazy val featureList = Map(
GATEWAY_AIRPORT -> getGatewayAirports().map(iata => (iata, 0)),
ELITE_CHARM -> getEliteDestinations()
)

  patchFeatures()

  def patchFeatures() = {
    val allAirports = AirportSource.loadAllAirports()
    val airportById = allAirports.map(a => a.id -> a).toMap

    val resourceDir = new java.io.File("src/main/resources/features")
    resourceDir.mkdirs()

    // Write gateway_airports.csv from DB-computed gateway list
    val gatewayIatas = getGatewayAirports().toSet
    val gatewayFile = new java.io.File(resourceDir, "gateway_airports.csv")
    val gatewayWriter = new java.io.PrintWriter(gatewayFile)
    try {
      gatewayWriter.println("iata")
      gatewayIatas.foreach(iata => gatewayWriter.println(iata))
    } finally {
      gatewayWriter.close()
    }
    println(s"Wrote ${gatewayIatas.size} gateway airports to ${gatewayFile.getAbsolutePath}")

    // Write isolated_towns.csv from DB-computed isolation levels
    val isolatedFile = new java.io.File(resourceDir, "isolated_towns.csv")
    val isolatedWriter = new java.io.PrintWriter(isolatedFile)
    try {
      isolatedWriter.println("iata,strength")
      IsolatedAirportPatcher.computeIsolation(allAirports).foreach { case (airportId, isolationLevel) =>
        airportById.get(airportId).foreach { airport =>
          isolatedWriter.println(s"${airport.iata},$isolationLevel")
          println(s"${airport} isolation level $isolationLevel")
        }
      }
    } finally {
      isolatedWriter.close()
    }
    println(s"Wrote isolated towns to ${isolatedFile.getAbsolutePath}")
  }

  def getEliteDestinations() : Map[String, Int] = {
    val destinations = DestinationSource.loadAllDestinations()
    val iataMap = destinations.groupBy(_.airport.iata).view.mapValues(_.length).toMap
    println("inserting elite destinations to features...")
    println(iataMap)
    iataMap
  }

  def getGatewayAirports() : List[String] = {
    //The most powerful airport of every country
    val airportsByCountry = AirportSource.loadAllAirports().groupBy(_.countryCode).filter(_._2.length > 0)
    val topAirportByCountry = airportsByCountry.view.mapValues(_.sortBy(_.basePopMiddleIncome).last)

    val baseList = topAirportByCountry.values.map(_.iata).toList

    val list: mutable.ListBuffer[String] = collection.mutable.ListBuffer(baseList:_*)

    list -= "CGO" //China
    list -= "OSS" //Uzbekistan
    list += "SKD"
    list -= "LHE" //Pakistan
    list -= "OKZ"
    list -= "MUX"
    list += "ISB"
    list += "VTE" //Laos
    list -= "PKZ"
    list -= "GYE" //Ecuador
    list += "UIO"
    list -= "THR" //Iran
    list += "IKA"
    list -= "RUH" //Saudi
    list += "JED"
    list -= "KRT"
    list -= "OND" //Namibia
    list += "WDH"
    list -= "NBJ" //Angola
    list += "NBJ"
    list -= "ZND" //Mali
    list += "NIM"
    list -= "BYK" //Ivory Coast
    list += "ABJ"
    list -= "DLA" //Cameroon
    list += "NSI"
    list -= "MQQ" //Chad
    list += "NDJ"
    list -= "BLZ" //Malawi
    list += "LLW"
    list -= "KGA" //DRC
    list -= "MJM"
    list += "FIH"
    list -= "KAN" //Nigeria
    list -= "APL" //Mozambique
    list += "MPM"
    list -= "MWZ" //Tanzania
    list += "DAR"
    list -= "HGU" //PNG
    list += "POM"
    list -= "STX" //US VI
    list += "STT"
    list -= "XSC" //
    list += "PLS"
    list += "NZF" //Pegasus Airfield, AQ (fictional IATA)
    list += "PPT"
    list += "NOU"
    list += "GOH" //Greenland
    list += "NAN" //Fiji
    list -= "SUV"
    list -= "AEP" //Argentina
    list += "EZE"
    list -= "CIS" //KI
    list -= "SKB" //Remove minor Caribbean ones
    list -= "SVD"
    list -= "DCF"
    list -= "EUN"
    list -= "CKY"
    list -= "FNA"
    list -= "BJL"
    list -= "MSQ" //Remove some EU ones
    list -= "MCM"
    list -= "BTS"
    list -= "RMO"
    list -= "LJU"
    list -= "MCM"

    //add extra ones for bigger countries
    list.appendAll(List(
      "NZF", //McMurdo AQ
      "CAN", //China
      "PVG",
      "PEK",
      "JFK", //US
      "LAX",
      "SFO",
      "MIA",
      "ATL",
      "BOM", //India
      "RUH", //Saudi
      "AUH", //UAE
      "CPT", //South Africa
      "LOS", //Nigeria
      "ABV",
      "GIG", //Brazil
      "GRU",
      "MDE", //Colombia
      "NRT", //Japan
      "HND",
      "KIX",
      "SVO", //Russia
      "DME",
      "LED",
      "FCO", //Italy
      "MXP",
      "GOH", //Greenland / DK
      "LGW", //UK
      "RUN", //France Reunion
      "MAD", //Spain
      "BCN",
      "FRA", //Germany
      "MUC",
      "SYD", //Australia
      "MEL",
      "PER",
      "YVR", //Canada
      "YUL",
      "YYZ",
      "NLU" //Mexico
    ))
    list.toList
  }
}
