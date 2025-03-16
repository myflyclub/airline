package com.patson.init

import scala.collection.mutable.Set
import scala.collection.mutable.ListBuffer
import com.patson.util._
import com.patson.data._
import com.patson.data.Constants._
import com.patson.model._
import com.patson.model.airplane._

import java.util.Calendar
import com.patson.Authentication

import scala.util.Random
import com.patson.DemandGenerator
import com.patson.UserSimulation.configFactory
import com.patson.data._
import com.patson.data.airplane._
import com.typesafe.config.ConfigFactory

import java.util.concurrent.ThreadLocalRandom
import scala.collection.mutable
import scala.concurrent.Await
import scala.concurrent.duration.Duration


object AirlineGenerator extends App {
  mainFlow

  private lazy val allModels = ModelSource.loadAllModels()
  private lazy val airports = AirportSource.loadAllAirports(fullLoad = false, loadFeatures = true)
  private lazy val countryRelationships = CountrySource.getCountryMutualRelationships()
  private lazy val bufferOfAirplanes = mutable.Map[Model, (Airplane, Int)]()

  case class LinkConfig(description: String, poolSize: Int, linkCount: Int, rawQuality: Int)
  case class LinkGeneration(fromAirport: Airport, toAirports: List[Airport], models: List[Model], airline: Airline, config: LinkConfig)

  def mainFlow() = {
    deleteAirlines()

    generateUSAirline(List("US"))
    generateCountryAirlines(List("CN","RU","IN","ID","BR"))
    generateSmallCountryAirlines(List("JP","CA","TR","MX","VN"))
    generateRemoteAirlines(List("AU","CA","US","DK","RU"))
    generateAffinityAirlines(List("EU","Banking","Oil","Pharma","Electronics","Copper","Marine"))
    generateAerospaceAirline()
    generateSSTAirline()
    generateLatamAirline()
    generateArabiaAirline()
    generateCaribbeanAirline()
    generatePacificAirline()
    resizeBases()

    println("DONE Creating airlines")
    Await.result(actorSystem.terminate(), Duration.Inf)
  }

  def deleteAirlines() : Unit = {
    println("Deleting airlines...")
    UserSource.deleteGeneratedUsers()
    UserCache.invalidateAll()
    AirlineCache.invalidateAll()
    AirlineSource.deleteAirlinesByCriteria(List(("airline_type", AirlineType.NON_PLAYER.id)))
    AirplaneOwnershipCache.invalidateAll()
  }

  def generateUSAirline(countryCodes : List[String]): Unit = {
    countryCodes.foreach(countryCode => {
      val bases = airports.filterNot(_.isDomesticAirport()).filter(_.countryCode == countryCode).takeRight(11).reverse
      val toAirports = airports.filter(_.countryCode == countryCode)
      generateAirline(
        s"Rats ${countryCode}",
        s"R${countryCode}",
        bases.head,
        bases.tail,
        toAirports,
        List("Boeing 737-800", "Boeing 737-700", "Boeing 737-600", "Boeing 777-300ER"),
        8000,
        60
      )
    })
  }

  def generateCountryAirlines(countryCodes : List[String]): Unit = {
    countryCodes.foreach(countryCode => {
      val bases = airports.filterNot(_.isDomesticAirport()).filter(_.countryCode == countryCode).takeRight(8).reverse
      val toAirports = airports.filter(_.countryCode == countryCode)
      generateAirline(
        s"Rats ${countryCode}",
        s"R${countryCode}",
        bases.head,
        bases.tail,
        toAirports,
        List("Embraer E195-E2", "Embraer E175-E2", "Comac C929-600"),
        8000,
        60
      )
    })
  }

  def generateSmallCountryAirlines(countryCodes : List[String]): Unit = {
    countryCodes.foreach(countryCode => {
      val bases = airports.filterNot(_.isDomesticAirport()).filter(_.countryCode == countryCode).takeRight(6).reverse
      val toAirports = airports.filter(_.countryCode == countryCode)
      generateAirline(
        s"Rats ${countryCode}",
        s"R${countryCode}",
        bases.head,
        bases.tail,
        toAirports,
        List("McDonnell Douglas DC-8-61", "McDonnell Douglas DC-8-62", "McDonnell Douglas DC-8-63", "Boeing 727-100", "Boeing 727-200"),
        8000,
        40
      )
    })
  }

  def generateAffinityAirlines(affinites : List[String]): Unit = {
    affinites.foreach(affinity => {
      val bases = airports.filterNot(_.isDomesticAirport()).filter(_.zone.contains(affinity)).takeRight(11).reverse
      val toAirports = airports.filterNot(_.isDomesticAirport()).filter(_.zone.contains(affinity))
      generateAirline(
        s"${affinity} Rats",
        s"${affinity}",
        bases.head,
        bases.tail,
        toAirports,
        List("Embraer E190", "Embraer E170", "Boeing 787-8 Dreamliner"),
        10000,
        80
      )
    })
  }

  def generateAerospaceAirline(): Unit = {
    val bases = airports.filter(_.zone.contains("Aerospace")).takeRight(11)
    val toAirports = airports.filter(port => port.zone.contains("Aerospace") || port.zone.contains("Space") || port.zone.contains("Software") || port.size >= 7).distinct
    val HQ = airports.find(_.iata == "TLS").getOrElse(bases.head)
    println(HQ)
    generateAirline(
      s"Aerospace Rats",
      s"Aerospace",
      HQ,
      bases,
      toAirports,
      List("Boeing 797-6", "Boeing 797-7", "Boeing 787-8 Dreamliner", "Boeing 787-9 Dreamliner"),
      12000,
      85
    )
  }

  def generateSSTAirline(): Unit = {
    val bases = airports.filter(airport => airport.iata == "ORD" || airport.iata == "SFO" || airport.iata == "BOS" || airport.iata == "FAI" || airport.iata == "ANC" || airport.iata == "BET")
    val HQ = airports.find(_.iata == "JFK").getOrElse(bases.head)
    println(HQ)
    generateAirline(
      s"Y",
      s"y",
      HQ,
      bases,
      airports,
      List("Boeing 2707", "Beechcraft B200 Super King Air", "Boeing 737-800"),
      6000,
      85
    )
  }

  def generatePacificAirline(): Unit = {
    val bases = airports.filter(airport => airport.zone.contains("PIF") && airport.isGateway() && airport.countryCode != "AU" && airport.countryCode != "NZ" && airport.countryCode != "US").takeRight(8).reverse
    generateAirline(
      s"Echo Pacific",
      s"echopacific",
      bases.head,
      bases.tail,
      airports,
      List("Douglas DC-3", "Lockheed Constellation L-749"),
      5000,
      45
    )
  }

  def generateArabiaAirline(): Unit = {
    val bases = airports.filter(airport => airport.zone.contains("Arabic")).takeRight(7).reverse
    val destinations = List("IST", "FRA", "BER", "IKA", "SVO", "LHR", "SIN")
    val toAirports = airports.filter(port => port.zone.contains("Sunni") || destinations.contains(port.iata)).distinct
    generateAirline(
      s"ONE Arabia",
      s"onearabia",
      bases.head,
      bases.tail,
      toAirports,
      List("Airbus A318", "Airbus A320", "Airbus A321neo", "Airbus A321neoXLR"),
      7000,
      70
    )
  }

  def generateLatamAirline(): Unit = {
    val bases = airports.filter(airport => airport.zone.contains("Hispanic") && airport.isGateway() && airport.countryCode != "US").takeRight(7).reverse
    val HQ = airports.find(_.iata == "GRU").getOrElse(bases.head)
    val toAirports = airports.filter(port => port.zone.contains("Hispanic") || port.zone.contains("Lusophone")).distinct
    generateAirline(
      s"Echo LATAM",
      s"echolatam",
      HQ,
      bases,
      toAirports,
      List("De Havilland DHC-7-100", "De Havilland DHC-8-100", "De Havilland Q400", "Boeing 757-200", "Boeing 757-200ER"),
      6000,
      55
    )
  }

  def generateCaribbeanAirline(): Unit = {
    val bases = airports.filter(airport => airport.zone.contains("CC") && airport.isGateway() && airport.countryCode != "US").takeRight(6).reverse
    val HQ = airports.find(_.iata == "MIA").getOrElse(bases.head)
    val toAirports = airports.filter(port => port.zone.contains("CC")).distinct
    generateAirline(
      s"Echo Caribbean",
      s"echocaribbean",
      HQ,
      bases,
      toAirports,
      List("Fokker 70", "Fokker 60", "Fokker 100"),
      6000,
      85
    )
  }

  def generateRemoteAirlines(countryCodes : List[String]): Unit = {
    countryCodes.foreach(countryCode => {
      val bases = airports.filter(_.countryCode == countryCode).filter(_.popMiddleIncome <= 9000).takeRight(7)
      val toAirports = airports.filter(_.countryCode == countryCode).filter(_.popMiddleIncome <= 50000)
      generateAirline(
        s"Rats ${countryCode}",
        s"rr${countryCode}",
        bases.head,
        bases.tail,
        toAirports,
        List("Cessna 208 Caravan", "Cessna 408 Skycourier"),
        1800,
        50
      )
    })
  }

  def generateAirline(name: String, username: String, hqAirport: Airport, bases: List[Airport], toAirports: List[Airport], modelFamily: List[String], linkMaxDistance: Int, targetServiceQuality: Int, initialBalance: Long = 1000000000, currentServiceQuality: Int = 70, reputation: Int = 80): Airline = {
    val user = createUser(username)
    val airline = createAirline(name, hqAirport, targetServiceQuality, currentServiceQuality, reputation, initialBalance)

    AirlineSource.saveAirlines(List(airline))
    UserSource.setUserAirline(user, airline)
    AirlineSource.saveAirlineInfo(airline, false)
    AirlineSource.saveAirplaneRenewal(airline.id, 55)

    // Generate bases & links
    makeBase(airline, hqAirport, true)
    generateLinksForAirline(airline, hqAirport, toAirports, modelFamily, linkMaxDistance + 1000)
    bases.zipWithIndex.foreach { case (baseAirport, index) => makeBase(airline, baseAirport) }
    bases.foreach( airport => generateLinksForAirline(airline, airport, toAirports, modelFamily, linkMaxDistance))
    airline
  }

  private def makeBase(airline: Airline, airport: Airport, isHq: Boolean = false, scale: Int = 7, foundedCycle: Int = 1) = {
    val base = AirlineBase(airline, airport, airport.countryCode, scale, foundedCycle, isHq)
    AirlineSource.saveAirlineBase(base)
  }

  private def createUser(userName: String): User = {
    val user = User(userName = userName, email = "bot", Calendar.getInstance, Calendar.getInstance, UserStatus.ACTIVE, level = 0, None, List.empty)

    val devMode = if (configFactory.hasPath("dev")) configFactory.getBoolean("dev") else false
    val password = if (devMode) "12345" else Random.nextInt(5000).toString
    UserSource.saveUser(user)
    Authentication.createUserSecret(userName, password)

    user
  }

  private def createAirline(name: String, hqAirport: Airport, targetServiceQuality: Int, currentServiceQuality: Int, reputation: Int, initialBalance: Long): Airline = {
    val airline = Airline(name + " [bot]", AirlineType.NON_PLAYER)
    airline.setBalance(initialBalance)
    airline.setTargetServiceQuality(targetServiceQuality)
    airline.setCurrentServiceQuality(currentServiceQuality)
    airline.setReputation(reputation)
    airline.setSkipTutorial(true)
    airline.setCountryCode(hqAirport.countryCode)
    airline.setAirlineCode(airline.getDefaultAirlineCode())
    airline
  }

  private def generateLinksForAirline(airline: Airline, baseAirport: Airport, toAirports: List[Airport], modelNames: List[String], maxDistance: Int): Unit = {
    val models = allModels.filter(model => modelNames.contains(model.name))
    val nearbyFocusAirports = findAirports(toAirports, baseAirport, maxDistance / 4)
    val farAirports = findAirports(airports, baseAirport, maxDistance, maxDistance / 4).filterNot(airport => nearbyFocusAirports.contains(airport))
    bufferOfAirplanes.clear()

    generateLinks(
      LinkGeneration(
        fromAirport = baseAirport,
        toAirports = nearbyFocusAirports,
        models = models,
        airline = airline,
        config = LinkConfig("near", nearbyFocusAirports.size, Math.min(24, nearbyFocusAirports.size), 40)
      )
    )

    generateLinks(
      LinkGeneration(
        fromAirport = baseAirport,
        toAirports = farAirports,
        models = models,
        airline = airline,
        config = LinkConfig("far", farAirports.size, 8, 60)
      )
    )
  }

  private def findAirports(airports: List[Airport], baseAirport: Airport, maxDistance: Int, minDistance: Int = 300): List[Airport] = {
    val relationships = CountrySource.getCountryMutualRelationships()

    airports.filter(airport => {
      val distance = Computation.calculateDistance(baseAirport, airport)
      val relationship = relationships.getOrElse(
        (baseAirport.countryCode, airport.countryCode),
        0
      )

      airport.id != baseAirport.id &&
        relationship >= 0 &&
        distance > minDistance &&
        distance < maxDistance
    }).reverse
  }

  private def generateLinks(config: LinkGeneration): List[Link] = {
    val airports = config.toAirports.takeRight(config.config.poolSize + 1)
    val pickedToAirports = drawFromPool(airports, config.config.poolSize)

    val airplaneModelsLarge = config.models.sortBy(_.capacity).reverse
    val airplaneModelsSmall = config.models.sortBy(_.capacity)
    val newLinks = ListBuffer[Link]()

    var i = 0
    while (newLinks.length < config.config.linkCount && i < config.config.poolSize) {
      val toAirport = pickedToAirports(i)
      i += 1

      val distance = Computation.calculateDistance(config.fromAirport, toAirport)
      val relationship = countryRelationships.getOrElse((config.fromAirport.countryCode, toAirport.countryCode), 0)

      val affinity = Computation.calculateAffinityValue(config.fromAirport.zone, toAirport.zone, relationship)

      val demand = DemandGenerator.computeBaseDemandBetweenAirports(config.fromAirport, toAirport, affinity, distance)
      val targetSeats = (demand.travelerDemand.total + demand.businessDemand.total) * 2

      if (targetSeats > 0) {
        createLink(
          fromAirport = config.fromAirport,
          toAirport = toAirport,
          airline = config.airline,
          distance = distance,
          targetSeats = targetSeats,
          modelsSmall = airplaneModelsSmall,
          modelsLarge = airplaneModelsLarge,
          rawQuality = config.config.rawQuality
        ).foreach(newLinks += _)
      }
    }

    if (newLinks.nonEmpty) {
      LinkSource.saveLinks(newLinks.filter(_.frequency > 0).toList)
    } else {
      println(
        s"No links on ${config.config.description} from ${config.fromAirport.iata} !!!"
      )
    }

    newLinks.toList
  }

  private def createLink(fromAirport: Airport, toAirport: Airport, airline: Airline, distance: Int, targetSeats: Int, modelsSmall: List[Model], modelsLarge: List[Model], rawQuality: Int): Option[Link] = {
    val pickedModel = modelsSmall.find(model =>
        model.capacity * Computation.calculateMaxFrequency(
          model,
          distance
        ) >= targetSeats && model.range >= distance && toAirport.runwayLength >= model.runwayRequirement
      ).orElse(modelsLarge.find(model => model.range >= distance && model.runwayRequirement <= toAirport.runwayLength))

    pickedModel.flatMap { model =>
      val frequency = math.min(
        (targetSeats.toDouble / model.capacity).toInt,
        35
      )

      if (frequency > 0) {
        val maxFrequencyPerAirplane = Computation.calculateMaxFrequency(model, distance)
        val airplanesRequired = math.max(1, frequency / maxFrequencyPerAirplane)

        val assignedAirplanes = createAndAssignAirplanes(
          model = model,
          airline = airline,
          homeAirport = fromAirport,
          frequency = frequency,
          distance = distance,
          airplanesRequired = airplanesRequired,
          maxFrequencyPerAirplane = maxFrequencyPerAirplane
        )

        val priceMod = if (fromAirport.popMiddleIncome < 100_000 || toAirport.popMiddleIncome < 100_000)
          0.8
        else if (fromAirport.popMiddleIncome > 1_000_000 || toAirport.popMiddleIncome > 1_000_000)
          1.2
        else
          1.0

        val econPrice = (priceMod * Pricing.computeStandardPrice(distance, Computation.getFlightCategory(fromAirport, toAirport), ECONOMY, PassengerType.BUSINESS, fromAirport.baseIncome)).toInt
        val bizPrice = (priceMod * 1.1 * Pricing.computeStandardPrice(distance, Computation.getFlightCategory(fromAirport, toAirport), BUSINESS, PassengerType.BUSINESS, fromAirport.baseIncome)).toInt
        val firstPrice = (priceMod * 1.2 * Pricing.computeStandardPrice(distance, Computation.getFlightCategory(fromAirport, toAirport), FIRST, PassengerType.BUSINESS, fromAirport.baseIncome)).toInt

        val duration = Computation.calculateDuration(model, distance)
        val capacity = calculateTotalCapacity(assignedAirplanes)

        val link = Link(
          fromAirport,
          toAirport,
          airline,
          LinkClassValues(econPrice, bizPrice, firstPrice),
          distance,
          capacity,
          rawQuality,
          duration = duration,
          frequency = frequency
        )

        link.setAssignedAirplanes(assignedAirplanes)
        Some(link)
      } else {
//        println(s"Cannot generate link from ${fromAirport.iata} to ${toAirport.iata} frequency is 0")
        None
      }
    }
  }

  private def createAndAssignAirplanes(model: Model, airline: Airline, homeAirport: Airport, frequency: Int, distance: Int, airplanesRequired: Int, maxFrequencyPerAirplane: Int): Map[Airplane, LinkAssignment] = {
    val assignedAirplanes = mutable.Map[Airplane, LinkAssignment]()
    val flightMinutesRequired = Computation.calculateFlightMinutesRequired(model, distance)
    var remainingFrequency = frequency

    for (_ <- 0 until airplanesRequired if remainingFrequency > 0) {
      val matchingPlane = bufferOfAirplanes.getOrElse(model, null)

      val frequencyForThis = if (matchingPlane != null && matchingPlane._2 >= flightMinutesRequired) {
        val usedAirplane = matchingPlane._1
        val frequencyForThis = math.min(remainingFrequency, Math.floor(matchingPlane._2 / flightMinutesRequired)).toInt
        val flightMinutesForThis = frequencyForThis * flightMinutesRequired
        val remainingFlightMinutes = matchingPlane._2 - flightMinutesForThis
        if (remainingFlightMinutes > 120) {
          bufferOfAirplanes(model) = (usedAirplane, remainingFlightMinutes)
        } else {
          bufferOfAirplanes.remove(model)
        }
        assignedAirplanes.put(usedAirplane, LinkAssignment(frequencyForThis, flightMinutesForThis))
        frequencyForThis
      } else {
        val newAirplane = createAirplane(model, airline, homeAirport)
        val frequencyForThis = math.min(remainingFrequency, maxFrequencyPerAirplane)
        val flightMinutesForThis = frequencyForThis * flightMinutesRequired
        if (Airplane.MAX_FLIGHT_MINUTES - flightMinutesForThis > 120) {
          bufferOfAirplanes(model) = (newAirplane, Airplane.MAX_FLIGHT_MINUTES - flightMinutesForThis)
        }
        assignedAirplanes.put(newAirplane, LinkAssignment(frequencyForThis, flightMinutesForThis))
        frequencyForThis
      }


      remainingFrequency -= frequencyForThis
    }

    assignedAirplanes.toMap
  }

  private def createAirplane(model: Model, airline: Airline, homeAirport: Airport): Airplane = {
    val airplane = Airplane(
      model = model,
      owner = airline,
      constructedCycle = 0,
      purchasedCycle = 0,
      condition = Airplane.MAX_CONDITION,
      depreciationRate = 0,
      value = model.price,
      home = homeAirport,
      configuration = AirplaneConfiguration.empty
    )

    airplane.assignDefaultConfiguration()
    AirplaneSource.saveAirplanes(List(airplane))
    airplane
  }

  private def calculateTotalCapacity(assignedAirplanes: Map[Airplane, LinkAssignment]): LinkClassValues = {
    assignedAirplanes.map { case (airplane, assignment) =>
      LinkClassValues(airplane.configuration.economyVal, airplane.configuration.businessVal, airplane.configuration.firstVal) * assignment.frequency
    }.reduce(_ + _)
  }

  private def drawFromPool(poolTopFirst: Seq[Airport], drawSize: Int): Seq[Airport] = {
    if (drawSize >= poolTopFirst.length) {
      poolTopFirst
    } else {
      Random.shuffle(poolTopFirst).take(drawSize)
    }
  }

  private def resizeBases(): Unit = {
    val allAirlines = AirlineSource.loadAllAirlines(true)
    val allLinks = LinkSource.loadAllLinks(LinkSource.SIMPLE_LOAD)
    val allFlightLinksByAirlineId = allLinks.filter(_.transportType == TransportType.FLIGHT).map(_.asInstanceOf[Link]).groupBy(_.airline.id)
    allAirlines.foreach(airline => {
      val linksByFromAirportId = allFlightLinksByAirlineId.get(airline.id).getOrElse(List.empty).groupBy(_.from.id)
      airline.bases.foreach { base =>
        val staffRequired = linksByFromAirportId.get(base.airport.id) match {
          case Some(links) => links.map(_.getCurrentOfficeStaffRequired).sum
          case None => 0
        }
        val idealBaseLevel: Int = if (base.headquarter) Math.round(staffRequired.toDouble / 80).toInt else Math.round(staffRequired.toDouble / 60).toInt
        val updateBase = base.copy(scale = Math.max(1, idealBaseLevel))
        AirlineSource.saveAirlineBase(updateBase)
      }
    })
  }

}
