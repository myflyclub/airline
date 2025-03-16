package com.patson.init

import com.patson.{Authentication, DemandGenerator, Util}
import com.patson.data._
import com.patson.data.airplane._
import com.patson.init.GeoDataGenerator.calculateLongitudeBoundary
import com.patson.model._
import com.patson.model.airplane._

import java.util.Calendar
import scala.collection.mutable
import scala.collection.mutable.{ListBuffer, Set}
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.Random


object GenericTransitGenerator {

  val TRANSIT_MANUAL_LINKS = Map(
    "HND" -> "NRT",
    "KIX" -> "KIX",
    "KOJ" -> "KMI",
    "KPO" -> "USN",
    "TAE" -> "USN",
    "TAE" -> "PUS",
    "AOG" -> "SHE",
    "PEK" -> "PKX",
    "PKX" -> "TSN",
    "PEK" -> "TSN",
    "PEK" -> "TVS",
    "WUX" -> "SHA",
    "WUX" -> "PVG",
    "WUX" -> "CZX",
    "YTY" -> "CZX",
    "WHA" -> "NKG",
    "SZX" -> "CAN",
    "JJN" -> "XMN",
    "CTU" -> "MIG",
    "MIG" -> "TFU",
    "CTU" -> "TFU",
    "YPB" -> "TFU",
    "YPB" -> "CTU",
    "KGT" -> "TFU",
    "KGT" -> "CTU",
    "KJH" -> "KWE",
    "AMD" -> "BDQ",
    "STV" -> "BDQ",
    "PNQ" -> "BOM",
    "SDW" -> "GOI",
    "PNY" -> "MAA",
    "TIR" -> "MAA",
    "CCJ" -> "COK",
    "CCJ" -> "CJB",
    "CJB" -> "COK",
    "IXP" -> "DHM",
    "LWN" -> "EVN",
    "IST" -> "SAW",
    "SAW" -> "YEI",
    "EWR" -> "TTN",
    "HPN" -> "EWR",
    "HVN" -> "HPN",
    "TTN" -> "PHL",
    "PHL" -> "ACY",
    "PHL" -> "LNS",
    "JFK" -> "ISP",
    "HTO" -> "ISP",
    "BOS" -> "PSM",
    "BOS" -> "MHT",
    "BOS" -> "ORH",
    "ORH" -> "MHT",
    "ORH" -> "BDL",
    "HVN" -> "BDL",
    "BOS" -> "PVD",
    "MIA" -> "PBI",
    "FLL" -> "PBI",
    "MCO" -> "DAB",
    "MCO" -> "LAL",
    "MCO" -> "VRB",
    "MCO" -> "MLB",
    "VRB" -> "MLB",
    "JAX" -> "GNV",
    "JAX" -> "UST",
    "DAB" -> "UST",
    "VPS" -> "ECP",
    "VPS" -> "PNS",
    "GPT" -> "MSY",
    "BTR" -> "MSY",
    "BTR" -> "LFT",
    "IAH" -> "BTP",
    "SAF" -> "ABQ",
    "ELP" -> "LRU",
    "SLC" -> "PVU",
    "MWA" -> "CGI",
    "MWA" -> "PAH",
    "LAX" -> "ONT",
    "LAX" -> "SBA",
    "SNA" -> "ONT",
    "SBD" -> "PSP",
    "MRY" -> "SJC",
    "SMF" -> "SCK",
    "OAK" -> "SCK",
    "SFO" -> "SCK",
    "ISP" -> "JFK",
    "LGA" -> "SWF",
    "CAK" -> "CLE",
    "FNT" -> "DTW",
    "TOL" -> "DTW",
    "LAF" -> "IND",
    "LAF" -> "ORD",
    "LAF" -> "MDW",
    "SBN" -> "ORD",
    "SBN" -> "MDW",
    "RFD" -> "ORD",
    "MKE" -> "ORD",
    "SWO" -> "TUL",
    "SWO" -> "OKC",
    "STL" -> "BLV",
    "SEA" -> "PAE",
    "PDX" -> "SLE",
    "SLE" -> "EUG",
    "KOA" -> "ITO",
    "YYZ" -> "YKF",
    "YYZ" -> "YHM",
    "YYZ" -> "YKF",
    "YVR" -> "YXX",
    "FAI" -> "CKX",
    "NLU" -> "TLC",
    "MEX" -> "TLC",
    "MEX" -> "PBC",
    "AAZ" -> "GUA",
    "GRU" -> "VCP",
    "CGH" -> "VCP",
    "SNN" -> "ORK",
    "LGW" -> "LTN",
    "STN" -> "LHR",
    "STN" -> "LGW",
    "LBA" -> "MAN",
    "NCL" -> "MME",
    "GLA" -> "EDI",
    "AMS" -> "EIN",
    "EIN" -> "MST",
    "RTM" -> "EIN",
    "EIN" -> "ANR",
    "OST" -> "BRU",
    "LGG" -> "BRU",
    "XCR" -> "CDG",
    "DOL" -> "CDG",
    "CDG" -> "BVA",
    "ORY" -> "BVA",
    "EGC" -> "BOD",
    "CMF" -> "LYS",
    "MRS" -> "TLN",
    "MRS" -> "FNI",
    "BZR" -> "PGF",
    "BCN" -> "REU",
    "BCN" -> "ILD",
    "BCN" -> "GRO",
    "SDR" -> "BIO",
    "VGO" -> "SCQ",
    "BRN" -> "ZRH",
    "CDT" -> "VLC",
    "RMU" -> "ALC",
    "GRX" -> "AGP",
    "OPO" -> "VSE",
    "DUS" -> "NRN",
    "DUS" -> "CGN",
    "DUS" -> "DTM",
    "CGN" -> "DTM",
    "FMO" -> "DTM",
    "PAD" -> "DTM",
    "PAD" -> "KSF",
    "HAM" -> "BRE",
    "BLL" -> "KRP",
    "AGH" -> "CPH",
    "OSL" -> "TRF",
    "ARN" -> "VST",
    "ARN" -> "NYO",
    "GVA" -> "BRN",
    "BSL" -> "BRN",
    "ZRH" -> "BSL",
    "RMI" -> "AOI",
    "BGY" -> "MXP",
    "PSA" -> "FLR",
    "BLQ" -> "FRL",
    "TPS" -> "PMO",
    "CTA" -> "CIY",
    "TZL" -> "SJJ",
    "OMO" -> "SJJ",
    "ZIA" -> "SVO",
    "DME" -> "SVO",
    "CSY" -> "KZN",
    "ULV" -> "KZN",
    "NBC" -> "KZN",
    "ULV" -> "KUF",
    "GSV" -> "BWO",
    "SVX" -> "CEK",
    "NJC" -> "SGC",
    "KGO" -> "SGC",
    "NJC" -> "KGP",
    "KEJ" -> "TOF",
    "BAX" -> "OVB",
    "MEL" -> "AVV",
    "PMR" -> "WLG"
  )

  def main(args : Array[String]) : Unit = {
    generateGenericTransit()
    Await.result(actorSystem.terminate(), Duration.Inf)
  }

  def generateGenericTransit(range : Int = 60) : Unit = {
    LinkSource.deleteLinksByCriteria(List(("transport_type", TransportType.GENERIC_TRANSIT.id)))

    val airports = AirportSource.loadAllAirports(true)
      .filter(_.population >= 500)
      .filter(_.runwayLength >= 500)
      .filter { airport => !GameConstants.ISOLATED_COUNTRIES.contains(airport.countryCode) }
      .filter { airport => !GameConstants.isIsland(airport.iata) }
      .sortBy { _.power }

    var counter = 0
    var progressCount = 0

    val processed = mutable.HashSet[(Int, Int)]()
    def addProcessedPair(id1: Int, id2: Int) = {
      processed.add((id1, id2))
      processed.add((id2, id1))
    }
    
    val countryRelationships = CountrySource.getCountryMutualRelationships()
    
    for (airport <- airports) {
      val boundaryLongitude = calculateLongitudeBoundary(airport.latitude, airport.longitude, range)
      val airportsInRange = scala.collection.mutable.ListBuffer[(Airport, Double)]()
      
      for (targetAirport <- airports) {
        // Skip if already processed this pair
        if (!processed.contains((airport.id, targetAirport.id))) {
          val isManualLink = TRANSIT_MANUAL_LINKS.get(airport.iata).contains(targetAirport.iata) || 
                            TRANSIT_MANUAL_LINKS.get(targetAirport.iata).contains(airport.iata)
          
          if (isManualLink || (
              airport.id != targetAirport.id &&
              targetAirport.popMiddleIncome > 2500 &&
              airport.longitude >= boundaryLongitude._1 && 
              airport.longitude <= boundaryLongitude._2 &&
              countryRelationships.getOrElse((airport.countryCode, targetAirport.countryCode), 0) >= 2
          )) {
            val distance = Util.calculateDistance(airport.latitude, airport.longitude, 
                                               targetAirport.latitude, targetAirport.longitude).toInt
            if (isManualLink || range >= distance) {
              airportsInRange += Tuple2(targetAirport, distance)
            }
          }
          // Mark as processed after checking
          addProcessedPair(airport.id, targetAirport.id)
        }
      }

      airportsInRange.foreach { case (targetAirport, distance) =>
        val isDomesticAirport = if(targetAirport.isDomesticAirport() || airport.isDomesticAirport()) 2 else 0
        val isGatewayAirport = if(targetAirport.isGateway() || airport.isGateway()) 3.5 else 0
        val multiplier = Math.min(airport.size, targetAirport.size) + isDomesticAirport + isGatewayAirport
        val capacity = (multiplier * 12000).toInt
        
        try {
          val genericTransit = GenericTransit(
            from = airport, 
            to = targetAirport, 
            distance = distance.toInt, 
            capacity = LinkClassValues.getInstance(
              economy = capacity, 
              business = (capacity * 0.3).toInt
            )
          )
          LinkSource.saveLink(genericTransit)
          println(s"${airport.iata}, ${airport.countryCode}, ${targetAirport.iata}, ${targetAirport.countryCode}, $distance, $capacity")
        } catch {
          case e: Exception => 
            println(s"Error saving link between ${airport.iata} and ${targetAirport.iata}: ${e.getMessage}")
        }
      }

      val progressChunk = airports.size / 100
      counter += 1
      if (counter % progressChunk == 0) {
        progressCount += 1
        print(".")
        if (progressCount % 10 == 0) {
          print(progressCount + "% ")
        }
      }
    }
  }
}