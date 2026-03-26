package com.patson.init

import scala.concurrent.Future
import scala.util.{Failure, Random, Success}

import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.io.Source

import scala.concurrent.duration.Duration
import scala.concurrent.Await
import com.patson.data.Constants._
import com.patson.model.City
import com.patson.model.Airport
import com.patson.data.AirportSource
import com.patson.data.CitySource
import com.patson.Util
import com.patson.model.Runway
import com.patson.model.RunwayType
import com.patson.model.Computation

import scala.collection.mutable.ArrayBuffer
import com.patson.model.Country
import com.patson.data.CountrySource

object GeoDataGenerator extends App {

  import actorSystem.dispatcher

  def countryCodeConvert(countryCode: String): String = {
    if (List("CX", "CC", "NF").contains(countryCode)) {
      "AU"
//    } else if (List("NU", "CK").contains(countryCode)) {
//      "NZ"
    } else if (List("PM", "WF", "GF", "GP", "MF", "MQ", "PM", "BL", "RE", "NC", "PF", "YT").contains(countryCode)) {
      "FR"
    } else if (List("BQ").contains(countryCode)) {
      "NL"
    } else if (List("IM", "JE", "GI", "FK", "GG", "SH", "MS", "AI").contains(countryCode)) {
      "GB"
    } else if (List("GL", "FO").contains(countryCode)) {
      "DK"
    } else if (List("UM").contains(countryCode)) {
      "US"
    } else if (List("EH").contains(countryCode)) {
      "MA"
    } else {
      countryCode
    }
  }

  def main() {
    val cities = AdditionalLoader.loadAdditionalCities()

    //make sure cities are saved first as we need the id for airport info
    try {
      AirportSource.deleteAllAirports()
      CitySource.deleteAllCitites()
      CitySource.saveCities(cities)
    } catch {
      case e : Throwable => e.printStackTrace()
    }

    val airports = buildAirportData(getAirport(), getRunway(), cities)

    buildCountryData(airports)

    AirportStatsInit.initAirportStats()

    GenericTransitGenerator.generateGenericTransit()

    Await.result(actorSystem.terminate(), Duration.Inf)
  }

  def getRunway() : Future[Map[Int, List[Runway]]] = {
    Future {
      val result = scala.collection.mutable.HashMap[Int, collection.mutable.ListBuffer[Runway]]()
      val asphaltPattern = "(asp.*)".r
      val concretePattern = "(con.*|pem.*)".r
      val gravelPattern = "(gvl.*|.*gravel.*)".r
      for (line : String <- Source.fromFile("runways.csv").getLines) {
        val info = line.split(",(?=([^\"]*\"[^\"]*\")*[^\"]*$)", -1).map { token =>
          if (token.startsWith("\"") && token.endsWith("\"")) {
            token.substring(1, token.length() - 1)
          } else {
            token
          }
        }

        val lighted = info(6) == "1"

        try {
          var length = (info(3).toInt * 0.3048).toInt
          if (length % 10 == 9) { //somehow the data is off my 1 meter
            length += 1
          }
          val csvAirportId = info(1).toInt
          var codeTokens = ListBuffer[String](info(8).trim, info(14).trim)
          codeTokens = codeTokens.filterNot(token => "XX".equals(token) || "".equals(token))
          val code = codeTokens.mkString("/")

          val runwayOption =
            info(5).toLowerCase() match {
              case asphaltPattern(_) =>
                Some(Runway(length, code, RunwayType.Asphalt, lighted))
              case concretePattern(_) => Some(Runway(length, code, RunwayType.Concrete, lighted))
              case gravelPattern(_) => Some(Runway(length, code, RunwayType.Gravel, lighted))
              case _ => Some(Runway(length, code, RunwayType.Unknown, lighted))
            }
          runwayOption.foreach {
            case (runway) =>
              val list = result.getOrElseUpdate(csvAirportId, ListBuffer[Runway]())
              list += runway
          }
        } catch {
          case _ : NumberFormatException => None
        }
      }


      val iataToCsvId = mutable.HashMap[Iata, Int]()
      //process patches, unfortunately to avoid flow, we have to load the csv airport ID here
      Await.result(GeoDataGenerator.getAirport(), Duration.Inf).map { csvAirport =>
        val rawAirport = csvAirport.airport
        val csvAirportId = csvAirport.csvAirportId
        iataToCsvId.put(rawAirport.iata, csvAirportId)
      }

      loadPatchRunways().foreach {
        case (iata, patchRunways) =>
          iataToCsvId.get(iata) match {
            case Some(csvId) =>
              val list = result.getOrElseUpdate(csvId, ListBuffer[Runway]())
              //make sure no duplicates
              patchRunways.foreach { patchRunway =>
                list.find(_.code.equals(patchRunway.code)) match {
                  case Some(duplicate) =>
//                    println(s"Skipping patch runways for $iata as same code for runway $duplicate is already found for $patchRunway!")
                  case None =>
                    list += patchRunway
                }
              }

            case None =>
              println(s"Cannot patch runways for $iata as CSV id not found!")
          }

      }
      result.view.mapValues(_.toList).toMap
    }
  }

  type Iata = String

  def getAirport() : Future[List[CsvAirport]] = {
    Future {
      val airportCityMap: Map[String, String] = scala.io.Source.fromFile("fcairports.csv").getLines().map(_.split(",", -1)).map { tokens =>
        (tokens(4), tokens(3))
      }.toMap
      val airportNameOverrideMap: Map[String, String] = scala.io.Source.fromFile("airport-name-override.csv").getLines().map(_.split(",", -1)).map { tokens =>
        (tokens(1), tokens(4))
      }.toMap

      println("loading affinity-patch-list.csv")
      val airportZoneList = scala.io.Source.fromFile("affinity-patch-list.csv").getLines().map(_.split(",", -1)).map { tokens =>
          (tokens(0),tokens(1))
        }.toList

      println("setting zones via country-data.csv")
      //csv = country-code,country-name,openness,gini,nominal-to-real-conversion-ratio,(5)zone,group1,lang1,group2,lang2,lang3
      val countryZoneMap = scala.io.Source.fromFile("country-data.csv").getLines().map(_.split(",", -1)).map { tokens =>
        val innerString = List(
          if (tokens(5).isEmpty) "" else tokens(5),
          if (tokens(6).isEmpty) "" else tokens(6),
          if (tokens(7).isEmpty) "" else tokens(7),
          if (tokens(8).isEmpty) "" else tokens(8)
        ).filter(_.nonEmpty).mkString("-")

        if (innerString.nonEmpty) (tokens(0), innerString) else (tokens(0), "None")
      }.toMap

      val result = ListBuffer[CsvAirport]()
      for (line : String <- Source.fromFile("airports.csv").getLines) {
        val info = line.split(",(?=([^\"]*\"[^\"]*\")*[^\"]*$)", -1).map { token =>
          if (token.startsWith("\"") && token.endsWith("\"")) {
            token.substring(1, token.length() - 1)
          } else {
            token
          }
        }

        val airportZones = airportZoneList.filter(_._1 == info(13)).map(zone => s"-${zone._2}")
        val zone = countryZoneMap.getOrElse(info(8), info(7)) + airportZones.mkString

        val iata = info(13)
        val airportName = airportNameOverrideMap.getOrElse(iata, info(3))
        val cityName = airportCityMap.getOrElse(iata, info(10))

        val airportSize =
          info(2) match {
            case "heliport" => 1
            case "small_airport" => 1
            case "medium_airport" => 2
            case "large_airport" => 3
            case _ => 0
          }

        //0 - csvId, 2 - size, 3 - name, 4 - lat, 5 - long, 7 - zone, 8 - country, 10 - city, 11 - scheduled service, 12 - code1, 13- code2
        result += CsvAirport(airport = new Airport(iata, info(12), airportName, info(4).toDouble, info(5).toDouble, countryCodeConvert(info(8)), cityName, zone, airportSize, 0, 0, 0, 0, 0),
          csvAirportId = info(0).toInt, scheduledService = "yes".equals(info(11)))
      }
      result.toList
    }
  }

  def buildAirportData(airportFuture : Future[List[CsvAirport]], runwayFuture : Future[Map[Int, List[Runway]]], citites : List[City]) : List[Airport] = {
    val combinedFuture = Future.sequence(Seq(airportFuture, runwayFuture))
    val results = Await.result(combinedFuture, Duration.Inf)

    val rawAirportResult : List[CsvAirport] = results(0).asInstanceOf[List[CsvAirport]]
    val runwayResult : Map[Int, List[Runway]] = results(1).asInstanceOf[Map[Int, List[Runway]]]

    println(rawAirportResult.size + " airports")
    println(runwayResult.size + " solid runways")
    println(citites.size + " cities")
    val (airports, cityAirportRelationshipsByIata) = generateAirportData(rawAirportResult, runwayResult, citites)

    AirportSource.deleteAllAirports()
    AirportSource.saveAirports(airports)

    // Resolve IATA keys to DB-assigned airport IDs (assigned during saveAirports)
    val airportByIata = airports.groupBy(_.iata)
    val cityAirportRelationships = cityAirportRelationshipsByIata.flatMap {
      case (iata, relationships) =>
        airportByIata.get(iata).map(a => (a.head.id, relationships))
    }.toMap
    AirportSource.saveCityAirportRelationships(cityAirportRelationships)
    
    saveAirportRunways(rawAirportResult, runwayResult, airports)

    DestinationsPatcher.loadDestinations()

    airports
  }



  def generateAirportData(rawAirportResult : List[CsvAirport], runwayResult : Map[Int, List[Runway]], cities : List[City]) : (List[Airport], Map[String, List[(City, Double)]]) = {
    val removalAirportIatas = AdditionalLoader.loadRemovalAirportIatas()

    println(s">> ${removalAirportIatas.size} removal iatas")
//    removalAirportIatas.foreach(println)

    setAirportRunwayDetails(rawAirportResult, runwayResult)

    var airportResult = adjustAirportByRunway(rawAirportResult.filter { case(CsvAirport(airport, _, scheduledService)) =>
      airport.iata != "" && scheduledService && airport.size > 0 && !removalAirportIatas.contains(airport.iata)
    }, runwayResult)

    airportResult = adjustAirportSize(airportResult)

    val additionalAirports : List[Airport] = AdditionalLoader.loadAdditionalAirports()

    airportResult = airportResult ++ additionalAirports

    println(s"Sorting cities to airports!")
    val airportsSortedByLongitude = airportResult.groupBy(_.iata).values.map(_.head).toList.sortBy(_.longitude)
    val citiesSortedByLongitude = cities.sortBy(_.longitude)

    var counter = 0;
    var progressCount = 0;

    val airportCityRelationships = mutable.Map[String, ListBuffer[(City, Double)]]()

    for (city <- citiesSortedByLongitude) {
      //calculate max and min longitude that we should kick off the calculation
      val boundaryLongitude = calculateLongitudeBoundary(city.latitude, city.longitude, 300)
      val potentialAirports = scala.collection.mutable.ListBuffer[(Airport, Double)]()
      for (airport <- airportsSortedByLongitude) {
        if (airport.size > 0 &&
          airport.countryCode == city.countryCode &&
          airport.longitude >= boundaryLongitude._1 && airport.longitude <= boundaryLongitude._2) {
          val distance = Util.calculateDistance(city.latitude, city.longitude, airport.latitude, airport.longitude)
          if (airport.airportRadius >= distance) {
            //println(city.name + " => " + airport.name)
            potentialAirports += Tuple2(airport, distance)
          }
        }
      }

      if (potentialAirports.size == 1) {
        val airport = potentialAirports.head._1
        val relationships = airportCityRelationships.getOrElseUpdate(airport.iata, ListBuffer[(City, Double)]())
        relationships += ((city, 1.0))
      } else if (potentialAirports.size > 1) {

        val dominateAirportSize : Int = potentialAirports.filter(_._2 <= 125).map(_._1).reduceLeftOption { (largestAirport, airport) =>
          if (largestAirport.size < airport.size) airport else largestAirport
        }.fold(0)(_.size)

        val validAirports = if (dominateAirportSize >= 7) {
          potentialAirports.filter(_._1.size >= 3)
        } else potentialAirports //there's a super airport within 125km, then other airports can only get some share if it's size >= 3

        val airportWeights = validAirports.foldRight(List[(Airport, Int)]()) {
          case (Tuple2(airport, distance), airportWeightList) =>
            val thisAirportWeight = (if (distance <= 25) 250 else if (distance <= 50) 125 else if (distance <= 100) 50 else if (distance <= 200) 4 else 1) * airport.size * airport.size
            (airport, thisAirportWeight) :: airportWeightList
        }.sortBy(_._2).takeRight(3) //take the largest 3

        val totalWeight = airportWeights.foldRight(0)(_._2 + _)

        airportWeights.foreach {
          case Tuple2(airport, weight) =>
            val relationships = airportCityRelationships.getOrElseUpdate(airport.iata, ListBuffer[(City, Double)]())
            relationships += ((city, weight.toDouble / totalWeight))
        }
      }

      val progressChunk = citiesSortedByLongitude.size / 100
      counter += 1
      if (counter % progressChunk == 0) {
        progressCount += 1;
        print(".")
        if (progressCount % 10 == 0) {
          print(progressCount + "% ")
        }
      }
    }

    println("Added all cities!")
    val cityAirportRelationshipsByIata = airportCityRelationships.map {
      case (iata, relationships) => (iata, relationships.toList)
    }.toMap

    println()
    //country-code,country-name,openness,gini,nominal-to-real-conversion-ratio,zone,group1,group2,lang1,lang2,lang3
    println("loading country-data.csv")

    val nominalToRealRatioMap = scala.io.Source.fromFile("country-data.csv").getLines().map(_.split(",", -1)).map { tokens =>
      (tokens(0), if (tokens(4).isEmpty) 1.1 else tokens(4).toDouble)
    }.toMap

    val giniMap = scala.io.Source.fromFile("country-data.csv").getLines().map(_.split(",", -1)).map { tokens =>
      (tokens(0), if (tokens(3).isEmpty) 39.0 else tokens(3).toDouble)
    }.toMap

    println(s"Calculating incomes with gini: ${giniMap}")

    val popOverrideMap: Map[String, (Int, Int, Int)] = scala.io.Source.fromFile("population_override.csv").getLines().map(_.split(",", -1)).map { tokens =>
      val basePopulation = if (tokens(4).isEmpty) 0 else tokens(4).toInt
      val elitePopulation = if (tokens(5).isEmpty) 0 else tokens(5).toInt
      val baseIncome = if (tokens(2).isEmpty) 0 else tokens(2).toInt
      (tokens(0), (basePopulation, elitePopulation, baseIncome))
    }.toMap
    println(s">>> manually set population override ${popOverrideMap.size}")

    val airports = airportResult.map { airport =>
      val citiesServed = airportCityRelationships.getOrElse(airport.iata, ListBuffer[(City, Double)]())
      val power = citiesServed.foldLeft(0.toLong) {
        case (foldLong, (city, weight)) => foldLong + (city.population.toLong * weight).toLong * city.income
      }
      val population = citiesServed.foldLeft(0.toLong) {
        case (foldLong, (city, weight)) => foldLong + (city.population.toLong * weight).toLong
      }

      if (popOverrideMap.contains(airport.iata)) {
        val airportCopy = airport.copy(baseIncome = popOverrideMap.getOrElse(airport.iata, (0, 0, 0))._3, basePopulation = popOverrideMap.getOrElse(airport.iata, (0, 0, 0))._1, basePopMiddleIncome = popOverrideMap.getOrElse(airport.iata, (0, 0, 0))._1, basePopElite = popOverrideMap.getOrElse(airport.iata, (0, 0, 0))._2)
        // Runway data is now managed separately in the database
        airportCopy
      } else if (population == 0) {
        airport
      } else {
        val nominalToRealRatio = nominalToRealRatioMap.getOrElse(airport.countryCode, 1.0)
        val normalizedIncome = Math.max(1000, (power / population * nominalToRealRatio).toInt)
        /**
         * Inelegantly adding more inequality here to poor countries (except IN & ZA output is already very high) to create peaks in key cities
         * Ideally, would create in the "cities" calculating the density of each city, then output a "gini" depending on national gini + local density
         *
         * Starting with small airports, then larger ones, in arbitrary income bands
         */
        //https://en.wikipedia.org/wiki/List_of_countries_by_number_of_millionaires
        val underRepresentedCountries = List("ES", "PT", "GB", "FR", "BE", "NL", "LU", "CH", "DE", "AT", "DK", "NO", "SE", "FI", "IT", "GR", "MT", "CA", "CN", "HK", "MO", "TW", "KR", "JP", "MA", "NA", "AE")
        //https://www.henleyglobal.com/publications/wealthiest-cities
        val nationalCenters = List("ALA","TAS","IKA","KHI","DEL","BOM","PVG","FNJ","ICN","GMP","HND","NRT","KIX","ITM","KUL","SGN","CGK","DPS","YYZ","YVR","SFO","MIA","FLL","PBI","MAD","BCN","LIS","LHR","LGW","LTN","EDI","CDG","ORY","LUX","CPH","ARN","FCO","CIA","MXP","BGY","LIN","BUD","ACC","KGL","LUN","MPM","NBO")

        val gini = if (normalizedIncome <= 4000 && airport.countryCode != "IN" && airport.countryCode != "ZA") {
            giniMap.getOrElse(airport.countryCode, 69.0) + 17 //need to account for global inequality? output is better this way
          } else if (normalizedIncome <= 9000 && population <= 8_000_000 && airport.countryCode != "IN" && airport.countryCode != "ZA" ) {
            giniMap.getOrElse(airport.countryCode, 69.0) + 12
          } else if (normalizedIncome < 6000 && population > 8_000_000 && airport.countryCode != "IN" && airport.countryCode != "ZA") {
            giniMap.getOrElse(airport.countryCode, 69.0) + 9
          } else if (normalizedIncome < 9000 && population > 8_000_000 && airport.countryCode != "IN" && airport.countryCode != "ZA") {
            giniMap.getOrElse(airport.countryCode, 69.0) + 7
          } else if (normalizedIncome < 15000 && population > 8_000_000 && airport.countryCode != "IN" && airport.countryCode != "ZA" && airport.countryCode != "BR" ) {
            giniMap.getOrElse(airport.countryCode, 69.0) + 5
          } else if (nationalCenters.contains(airport.iata)) {
            giniMap.getOrElse(airport.countryCode, 69.0) + 6 //more inequality & wealth in national centers
          } else if (List("HYD","BLR","MAA","PEK","PKX","SHA","CAN","SZX","MNL","BKK","CAI","ADD","MEX","YYC","SJC","JFK","EWR","LGA","IAD","IAH","LAS","LAX").contains(airport.iata)) {
            giniMap.getOrElse(airport.countryCode, 69.0) + 2.5
          } else {
            giniMap.getOrElse(airport.countryCode, 69.0)
          }

        val eliteThreshold = if (airport.countryCode == "US") {
          3_225_000 //USA is just OP
        } else if (underRepresentedCountries.contains(airport.countryCode)) {
          2_275_000
        } else {
          3_175_000
        }

        val elitePop = Computation.populationAboveThreshold(normalizedIncome, population.toInt, gini, eliteThreshold)
        /**
         * inelegantly adding more elites here to get the distribution closer to real
         *
         * Having "density" in the cities would help a lot
         */
        val elitePopAdjusted : Int = if (List("GVA", "NCE", "LCY", "SYD", "MEL", "AVV").contains(airport.iata)) {
            ((elitePop + 389) * 11.9).toInt
          } else if (List("DOH", "SZG", "ACH", "BRN", "LUG", "INN", "MIA", "PER", "BNE", "OOL", "YYZ", "YVR").contains(airport.iata)) {
            ((elitePop + 289) * 4.9).toInt
          } else if (List("NRT", "ITM", "KIX", "FUK", "CTS", "BSL", "ZRH", "VCE", "BZO", "TRN", "FLO", "BRU", "AKL", "CNS", "SJC", "SBA", "PBI", "XNA", "PSP", "HTO", "PBI", "HNL", "OGG", "KOA", "CPT", "SIN").contains(airport.iata)) {
            (elitePop + 179) * 3
          } else if (List("HKG", "PEK", "PVG", "ARN", "OSL", "TRD", "MXP", "LIN", "BGY", "FCO", "SEA", "IAH", "ASE", "JAC", "YUL", "YYC", "TLV").contains(airport.iata)) {
            ((elitePop + 149) * 2.25).toInt
          } else if (List("HND", "ICN", "MFM", "HGH", "PKX", "SHA", "BOM", "AUH", "LIS", "MAD", "BCN", "CPH", "FRA", "CDG", "AMS", "LHR", "LGW", "LTN", "STN", "SBH", "BLL", "AAL", "GOT", "MMX", "BGO", "SVG", "BOS", "HOU", "SFO", "STS", "SNA", "BUR", "ASP", "ISP", "HTO", "HPN", "HVN", "BDL").contains(airport.iata)) {
            ((elitePop + 99) * 1.5).toInt
          } else if (elitePop > 0 && elitePop < 100 && underRepresentedCountries.contains(airport.countryCode) && airport.basePopulation > 5000) {
            Math.min((population * 0.6).toInt, Math.max(Random.nextInt(10) * 10, elitePop * 5))
          } else if ( elitePop < 10) {
            0
          } else {
            elitePop
          }

        val middleIncomePop = Math.max(0, -1 * elitePopAdjusted + Computation.populationAboveThreshold(normalizedIncome, population.toInt, gini, 31_500))
        val airportCopy = airport.copy(baseIncome = normalizedIncome , basePopulation = population.toInt, basePopMiddleIncome = middleIncomePop, basePopElite = elitePopAdjusted)
        airportCopy
      }
    }.filter(_.basePopulation != 0)
    .sortBy(_.basePopMiddleIncome)

    println(s"Calculated all airport pops & income")

    (airports, cityAirportRelationshipsByIata)
  }


  def calculateLongitudeBoundary(latInDegree : Double, lonInDegree : Double, maxDistance : Double) = {
    val lat = Math.toRadians(latInDegree)
    val lon = Math.toRadians(lonInDegree)

    val resultLon = Math.acos((Math.cos(maxDistance / 6371) - Math.sin(lat) * Math.sin(lat)) / (Math.cos(lat) * Math.cos(lat))) + lon
    val resultLonInDegree = resultLon.toDegrees
    if (resultLonInDegree > lonInDegree) {
      (2 * lonInDegree - resultLonInDegree, resultLonInDegree)
    } else {
      (resultLonInDegree, 2 * lonInDegree - resultLonInDegree)
    }
  }

  case class CsvAirport(airport : Airport, csvAirportId : Int, scheduledService : Boolean)

  def setAirportRunwayDetails(csvAirports : List[CsvAirport], runwaysByCsvId : Map[Int, List[Runway]]) : Unit = {
    csvAirports.foreach {
      case (CsvAirport(airport, csvId, _)) =>
        val runways = runwaysByCsvId.get(csvId).getOrElse(List.empty)
        setRunwaysForAirport(airport, runways)
    }
  }

  def setRunwaysForAirport(airport: Airport, runways: List[Runway]): Unit = {
    if (runways.nonEmpty) {
      val maxRunwayLength = runways.sortBy(_.length).reverse.head.length
      airport.runwayLength = maxRunwayLength.toInt
    }
    if (airport.runwayLength < Airport.MIN_RUNWAY_LENGTH) {
      if (airport.name.contains("Heli")) {
        airport.runwayLength = 10
      } else {
        airport.runwayLength = Airport.MIN_RUNWAY_LENGTH
      }
    }
  }

  def saveAirportRunways(rawAirportResult: List[CsvAirport], runwayResult: Map[Int, List[Runway]], airports: List[Airport]): Unit = {
    // Create a mapping from IATA to Airport (with database ID)
    val iataToAirport = airports.map(airport => airport.iata -> airport).toMap
    
    // Create a mapping from CSV ID to IATA
    val csvIdToIata = rawAirportResult.map(csvAirport => csvAirport.csvAirportId -> csvAirport.airport.iata).toMap
    
    println("Saving airport runways...")
    var savedCount = 0
    
    runwayResult.foreach { case (csvId, runways) =>
      csvIdToIata.get(csvId).flatMap(iataToAirport.get) match {
        case Some(airport) =>
          AirportSource.saveAirportRunways(airport.id, runways)
          savedCount += runways.size
        case None =>
      }
    }
    
    println(s"Saved $savedCount runways for ${runwayResult.size} airports")
  }

  def adjustAirportByRunway(rawAirportResult : List[CsvAirport], runwayResult : Map[Int, List[Runway]]) : List[Airport] = {
    rawAirportResult.map {
      case (CsvAirport(rawAirport, csvAirportId, _)) =>
        val increment : Int = runwayResult.get(csvAirportId) match {
          case Some(runways) =>
            var longRunway = 0
            var veryLongRunway = 0
            var megaRunway = 0
            runways.filter(_.lighted).foreach { runway => //only count lighted runways
              if (runway.length >= 10000 * 0.3048) { //old logic (for example 10000, was in feet) while runway.length is in meter now
                megaRunway += 1
              } else if (runway.length >= 9000 * 0.3048) {
                veryLongRunway += 1
              } else if (runway.length >= 7000 * 0.3048) {
                longRunway += 1
              }
            }

            if (megaRunway > 0) {
              3
            } else if (veryLongRunway > 0) {
              if (veryLongRunway > 1) { //2 very long runway
                2
              } else if (longRunway > 0) { //1 very long 1+ long
                2
              } else {
                1
              }
            } else if (longRunway >= 1) {
              1
            } else {
              0
            }
          case None => 0 //no change
        }
        rawAirport.size = rawAirport.size + increment

        rawAirport
    }
  }

  def adjustAirportSize(airports : List[Airport]) : List[Airport] = {
    airports.foreach { airport =>
      AirportSizeAdjust.sizeList.get(airport.iata).foreach { newSize =>
        airport.size = newSize
      }
    }
    airports
  }

  def buildCountryData(airports : Seq[Airport], update : Boolean = false) {
    val airportsByCountry : Map[String, Seq[Airport]] = airports.groupBy(_.countryCode)

    val countryCodeToNameMap = scala.io.Source.fromFile("country-data.csv").getLines().map(_.split(",", -1)).map { tokens =>
      val countryCode = tokens(0)
      val name = tokens(1)
      (countryCode, name)
    }.toMap

    val giniMap = scala.io.Source.fromFile("country-data.csv").getLines().map(_.split(",", -1)).map { tokens =>
      (tokens(0), if(tokens(3).isEmpty) 39.0 else tokens(3).toDouble)
    }.toMap

    val opennessMap = scala.io.Source.fromFile("country-data.csv").getLines().map(_.split(",", -1)).map { tokens =>
      (tokens(0), if(tokens(2).isEmpty) 4 else tokens(2).toInt)
    }.toMap

    val countries = ArrayBuffer[Country]()
//    println(airportsByCountry)
    airportsByCountry.foreach {
      case (countryCode, airports) =>
        val totalAirportPopulation: Long = airports.map {
          _.basePopulation
        }.sum
        val averageIncome = if (totalAirportPopulation == 0) {
          0
        } else {
          airports.map { airport =>
            airport.baseIncome * airport.basePopulation.toLong
          }.sum / totalAirportPopulation
        }
        countries += Country(countryCode, countryCodeToNameMap(countryCode), totalAirportPopulation.toInt, averageIncome.toInt, opennessMap.getOrElse(countryCode,4), giniMap.getOrElse(countryCode,39.0))
    }
    if (update) {
      CountrySource.updateCountries(countries.toList)
      println("Updated all countries!")

    } else {
      CountrySource.purgeAllCountries()
      println("Truncated all countries")
      CountrySource.saveCountries(countries.toList)
      println(s"Saved ${countries.length} countries")
    }
  }


  def loadPatchRunways() : Map[Iata, List[Runway]] = {
    val patchRunwayFiles = List("runway-patch-2024-jul.csv")

    val result = scala.collection.mutable.HashMap[String, collection.mutable.ListBuffer[Runway]]()
    patchRunwayFiles.foreach { file =>
      for (line : String <- Source.fromFile(file).getLines) {
        val info = line.split(",(?=([^\"]*\"[^\"]*\")*[^\"]*$)", -1).map { token =>
          if (token.startsWith("\"") && token.endsWith("\"")) {
            token.substring(1, token.length() - 1)
          } else {
            token
          }
        }

        try {
          val length = info(1).toInt

          val iata = info(0)
          val code = info(3)

          val runway = Runway(length, code, RunwayType.withName(info(2)), lighted = false)
          val list = result.getOrElseUpdate(iata, ListBuffer[Runway]())
          list += runway
        } catch {
          case _ : NumberFormatException => None
        }
      }
    }
    result.view.mapValues(_.toList).toMap
  }
}