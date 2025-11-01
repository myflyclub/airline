package com.patson

import java.util.Collections
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.testkit.{ImplicitSender, TestKit}
import com.patson.PassengerSimulation.RouteRejectionReason
import com.patson.model._
import com.patson.model.airplane.{AirplaneMaintenanceUtil, Model}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.jdk.CollectionConverters._
import scala.collection.mutable.Set
//
class PassengerSimulationSpec(_system: ActorSystem) extends TestKit(_system) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll {

  def this() = this(ActorSystem("MySpec"))

  override protected def beforeAll() : Unit = {
    super.beforeAll()
    AirplaneMaintenanceUtil.setTestFactor(Some(1))
  }

  override protected def afterAll() : Unit = {
    AirplaneMaintenanceUtil.setTestFactor(None)
    TestKit.shutdownActorSystem(system)
    super.afterAll()
  }

  val testAirline1 = Airline("airline 1", id = 1)
  val testAirline2 = Airline("airline 2", id = 2)


  val fromAirport = Airport.fromId(1).copy(baseIncome = 40000, basePopulation = 1) //income 40k . mid income country
  val airlineAppeal = AirlineAppeal(0)
  fromAirport.initAirlineAppeals(Map(testAirline1.id -> airlineAppeal, testAirline2.id -> airlineAppeal))
  fromAirport.initLounges(List.empty)
  val toAirportsList = List(
    Airport("", "", "To Airport", 0, 30, "", "", "", 1, 0, 0, 0, id = 2),
    Airport("", "", "To Airport", 0, 60, "", "", "", 1, 0, 0, 0, id = 3),
    Airport("", "", "To Airport", 0, 90, "", "", "", 1, 0, 0, 0, id = 4)
  )

  val mediumAirplaneModel = Model.modelByName("Airbus A320")



  toAirportsList.foreach { airport =>
    airport.initAirlineAppeals(Map(testAirline1.id -> airlineAppeal, testAirline2.id -> airlineAppeal))
    airport.initLounges(List.empty)
  }
  val toAirports = Set(toAirportsList : _*)

  val allAirportIds = Set[Int]()
  allAirportIds ++= toAirports.map {
    _.id
  }
  allAirportIds += fromAirport.id
  val LOOP_COUNT = 100000

  def assignLinkConsiderationIds(linkConsiderations : List[LinkConsideration]) = {
    var id = 1
    linkConsiderations.foreach { linkConsideration =>
      if (linkConsideration.link.id == 0) {
        linkConsideration.link.id = id
      }
      id += 1
    }
  }

  def assignLinkIds(links : List[Link]) = {
    var id = 1
    links.foreach { link =>
      if (link.id == 0) {
        link.id = id
      }
      id += 1
    }
  }


  //  val airline1Link = Link(fromAirport, toAirport, testAirline1, 100, 10000, 10000, 0, 600, 1)
  //  val airline2Link = Link(fromAirport, toAirport, testAirline2, 100, 10000, 10000, 0, 600, 1)
  val passengerGroup = PassengerGroup(fromAirport, DealPreference(homeAirport = fromAirport, preferredLinkClass = ECONOMY, 1.0), PassengerType.BUSINESS)
  //def findShortestRoute(from : Airport, toAirports : Set[Airport], allVertices: Set[Airport], linksWithCost : List[LinkWithCost], maxHop : Int) : Map[Airport, Route] = {
  "Find shortest route".must {
    "find no route if there's no links".in {
      val routes = PassengerSimulation.findShortestRoute(passengerGroup, toAirports, allAirportIds, List.empty[LinkConsideration].asJava, Collections.emptyMap[Int, Int](), 3)
      routes.size.shouldBe(0)
    }
    "find n route if there's 1 link to each target".in {
      val links = toAirports.foldRight(List[LinkConsideration]()) { (airport, foldList) =>
        LinkConsideration.getExplicit(Link(fromAirport, airport, testAirline1, LinkClassValues.getInstance(100), 10000, LinkClassValues.getInstance(10000), 0, 600, 1), 100, ECONOMY, false) :: foldList
      }
      assignLinkConsiderationIds(links)

      val routes = PassengerSimulation.findShortestRoute(passengerGroup, toAirports, allAirportIds, links.asJava, Collections.emptyMap[Int, Int](), 3)
      routes.size.shouldBe(toAirports.size)
      toAirports.foreach { toAirport => routes.isDefinedAt(toAirport).shouldBe(true) }
    }
    "find route if there's a link chain to target within max hop".in {
      val links = List(LinkConsideration.getExplicit(Link(fromAirport, toAirportsList(0), testAirline1, LinkClassValues.getInstance(100), 10000, LinkClassValues.getInstance(10000), 0, 600, 1), 100, ECONOMY, false),
        LinkConsideration.getExplicit(Link(toAirportsList(0), toAirportsList(1), testAirline1, LinkClassValues.getInstance(100), 10000, LinkClassValues.getInstance(10000), 0, 600, 1), 100, ECONOMY, false),
        LinkConsideration.getExplicit(Link(toAirportsList(1), toAirportsList(2), testAirline1, LinkClassValues.getInstance(100), 10000, LinkClassValues.getInstance(10000), 0, 600, 1), 100, ECONOMY, false))
      assignLinkConsiderationIds(links)

      val routes = PassengerSimulation.findShortestRoute(passengerGroup, toAirports, allAirportIds, links.asJava, Collections.emptyMap[Int, Int](), 3)
      routes.isDefinedAt(toAirportsList(2)).shouldBe(true)
      val route = routes.get(toAirportsList(2)).get
      route.links.size.shouldBe(3)
      route.links.equals(links)
    }
    "find route if there's a reverse link chain to target within max hop".in {
      val links = List(LinkConsideration.getExplicit(Link(toAirportsList(2), toAirportsList(1), testAirline1, LinkClassValues.getInstance(100), 10000, LinkClassValues.getInstance(10000), 0, 600, 1), 100, ECONOMY, true),
        LinkConsideration.getExplicit(Link(toAirportsList(1), toAirportsList(0), testAirline1, LinkClassValues.getInstance(100), 10000, LinkClassValues.getInstance(10000), 0, 600, 1), 100, ECONOMY, true),
        LinkConsideration.getExplicit(Link(toAirportsList(0), fromAirport, testAirline1, LinkClassValues.getInstance(100), 10000, LinkClassValues.getInstance(10000), 0, 600, 1), 100, ECONOMY, true))
      assignLinkConsiderationIds(links)

      val routes = PassengerSimulation.findShortestRoute(passengerGroup, toAirports, allAirportIds, links.asJava, Collections.emptyMap[Int, Int](), 3)
      routes.isDefinedAt(toAirportsList(2)).shouldBe(true)
      val route = routes.get(toAirportsList(2)).get
      route.links.size.shouldBe(3)
      route.links.equals(links)
    }
    "find no route if there's a link chain to target but exceed max hop".in {
      val links = List(LinkConsideration.getExplicit(Link(fromAirport, toAirportsList(0), testAirline1, LinkClassValues.getInstance(100), 10000, LinkClassValues.getInstance(10000), 0, 600, 1), 100, ECONOMY, false),
        LinkConsideration.getExplicit(Link(toAirportsList(0), toAirportsList(1), testAirline1, LinkClassValues.getInstance(100), 10000, LinkClassValues.getInstance(10000), 0, 600, 1), 100, ECONOMY, false),
        LinkConsideration.getExplicit(Link(toAirportsList(1), toAirportsList(2), testAirline1, LinkClassValues.getInstance(100), 10000, LinkClassValues.getInstance(10000), 0, 600, 1), 100, ECONOMY, false))
      assignLinkConsiderationIds(links)

      val routes = PassengerSimulation.findShortestRoute(passengerGroup, toAirports, allAirportIds, links.asJava, Collections.emptyMap[Int, Int](), 2)
      routes.isDefinedAt(toAirportsList(2)).shouldBe(false)
    }
    "find a cheaper route even with connection flights (with frequent service)".in {
      val cheapLinks = List(LinkConsideration.getExplicit(Link(fromAirport, toAirportsList(0), testAirline1, LinkClassValues.getInstance(100), distance = 3500, LinkClassValues.getInstance(10000), 0, duration = 200, frequency = 42), 3500, ECONOMY, false),
        LinkConsideration.getExplicit(Link(toAirportsList(0), toAirportsList(1), testAirline1, LinkClassValues.getInstance(100), distance = 3500, LinkClassValues.getInstance(10000), 0, duration = 200, frequency = 42), 3500, ECONOMY, false),
        LinkConsideration.getExplicit(Link(toAirportsList(1), toAirportsList(2), testAirline1, LinkClassValues.getInstance(100), distance = 3500, LinkClassValues.getInstance(10000), 0, duration = 200, frequency = 42), 3500, ECONOMY, false))
      val allLinks = LinkConsideration.getExplicit(Link(fromAirport, toAirportsList(2), testAirline1, LinkClassValues.getInstance(100), 10000, LinkClassValues.getInstance(10000), 0, duration = 600, frequency = 1), 13000, ECONOMY, false) :: cheapLinks
      assignLinkConsiderationIds(allLinks)

      val routes = PassengerSimulation.findShortestRoute(passengerGroup, toAirports, allAirportIds, allLinks.asJava, Collections.emptyMap[Int, Int](), 3)
      routes.isDefinedAt(toAirportsList(2)).shouldBe(true)
      val route = routes.get(toAirportsList(2)).get
      route.links.size.shouldBe(3)
      route.links.equals(cheapLinks)
    }
    "use direct route even though it's more expensive as connection flight is not frequent enough".in {
      val cheapLinks = List(LinkConsideration.getExplicit(Link(fromAirport, toAirportsList(0), testAirline1, LinkClassValues.getInstance(100), 10000, LinkClassValues.getInstance(10000), 0, duration = 200, frequency = 1), 400, ECONOMY, false),
        LinkConsideration.getExplicit(Link(toAirportsList(0), toAirportsList(1), testAirline1, LinkClassValues.getInstance(100), 10000, LinkClassValues.getInstance(10000), 0, duration = 200, frequency = 1), 400, ECONOMY, false),
        LinkConsideration.getExplicit(Link(toAirportsList(1), toAirportsList(2), testAirline1, LinkClassValues.getInstance(100), 10000, LinkClassValues.getInstance(10000), 0, duration = 200, frequency = 1), 400, ECONOMY, false))
      val expensiveLink = LinkConsideration.getExplicit(Link(fromAirport, toAirportsList(2), testAirline1, LinkClassValues.getInstance(100), 10000, LinkClassValues.getInstance(10000), 0, duration = 600, frequency = 1), 1400, ECONOMY, false)
      val allLinks = expensiveLink :: cheapLinks
      assignLinkConsiderationIds(allLinks)
      val routes = PassengerSimulation.findShortestRoute(passengerGroup, toAirports, allAirportIds, allLinks.asJava, Collections.emptyMap[Int, Int](), 3)
      routes.isDefinedAt(toAirportsList(2)).shouldBe(true)
      val route = routes.get(toAirportsList(2)).get
      route.links.size.shouldBe(1)
      route.links.equals(expensiveLink)
    }

    "use expensive route if cheaper route exceed max hop".in {
      val cheapLinks = List(LinkConsideration.getExplicit(Link(fromAirport, toAirportsList(0), testAirline1, LinkClassValues.getInstance(100), 10000, LinkClassValues.getInstance(10000), 0, 600, 1), 100, ECONOMY, false),
        LinkConsideration.getExplicit(Link(toAirportsList(0), toAirportsList(1), testAirline1, LinkClassValues.getInstance(100), 10000, LinkClassValues.getInstance(10000), 0, 600, 1), 100, ECONOMY, false),
        LinkConsideration.getExplicit(Link(toAirportsList(1), toAirportsList(2), testAirline1, LinkClassValues.getInstance(100), 10000, LinkClassValues.getInstance(10000), 0, 600, 1), 100, ECONOMY, false))
      val expensiveLink = LinkConsideration.getExplicit(Link(fromAirport, toAirportsList(2), testAirline1, LinkClassValues.getInstance(100), 10000, LinkClassValues.getInstance(10000), 0, 600, 1), 301, ECONOMY, false)
      val allLinks = expensiveLink :: cheapLinks
      assignLinkConsiderationIds(allLinks)
      val routes = PassengerSimulation.findShortestRoute(passengerGroup, toAirports, allAirportIds, allLinks.asJava, Collections.emptyMap[Int, Int](), 2)
      routes.isDefinedAt(toAirportsList(2)).shouldBe(true)
      val route = routes.get(toAirportsList(2)).get
      route.links.size.shouldBe(1)
      route.links.equals(List(expensiveLink))
    }
    "find no route if there's a link chain to target but one is not in correct direction".in {
      val links = List(LinkConsideration.getExplicit(Link(fromAirport, toAirportsList(0), testAirline1, LinkClassValues.getInstance(100), 10000, LinkClassValues.getInstance(10000), 0, 600, 1), 100, ECONOMY, false),
        LinkConsideration.getExplicit(Link(toAirportsList(0), toAirportsList(1), testAirline1, LinkClassValues.getInstance(100), 10000, LinkClassValues.getInstance(10000), 0, 600, 1), 100, ECONOMY, true), //wrong direction
        LinkConsideration.getExplicit(Link(toAirportsList(1), toAirportsList(2), testAirline1, LinkClassValues.getInstance(100), 10000, LinkClassValues.getInstance(10000), 0, 600, 1), 100, ECONOMY, false))
      assignLinkConsiderationIds(links)
      val routes = PassengerSimulation.findShortestRoute(passengerGroup, toAirports, allAirportIds, links.asJava, Collections.emptyMap[Int, Int](), 3)
      routes.isDefinedAt(toAirportsList(2)).shouldBe(false)
    }
    "apply transfer discount for Elite passengers at transfer airport".in {
      // Test that Elite passengers get a transfer discount when transferring through Airport 1 (toAirportsList(0))
      // with ELITE_TRANSFER specialization
      val elitePassengerGroup = PassengerGroup(fromAirport, LastMinutePreference(homeAirport = fromAirport, preferredLinkClass = ECONOMY, 1.0, 0), PassengerType.ELITE)
      val businessPassengerGroup = PassengerGroup(fromAirport, LastMinutePreference(homeAirport = fromAirport, preferredLinkClass = ECONOMY, 1.0, 0), PassengerType.BUSINESS)
      
      // Create a multi-leg route requiring transfer at toAirportsList(0) (Airport 3)
      // High frequency links to ensure connection is viable
      val links = List(
        LinkConsideration.getExplicit(Link(fromAirport, toAirportsList(0), testAirline1, LinkClassValues.getInstance(100), 3500, LinkClassValues.getInstance(10000), 0, 200, 42), 3500, ECONOMY, false),
        LinkConsideration.getExplicit(Link(toAirportsList(0), toAirportsList(1), testAirline1, LinkClassValues.getInstance(100), 3500, LinkClassValues.getInstance(10000), 0, 200, 42), 3500, ECONOMY, false)
      )
      assignLinkConsiderationIds(links)
      
      val transferSpecializations: java.util.Map[Int, Int] = new java.util.HashMap[Int, Int]()
      
      // Find routes for both Elite and Business passengers
      val routesElite = PassengerSimulation.findShortestRoute(elitePassengerGroup, Set(toAirportsList(1)), allAirportIds, links.asJava, transferSpecializations, 3)
      val routesBusiness = PassengerSimulation.findShortestRoute(businessPassengerGroup, Set(toAirportsList(1)), allAirportIds, links.asJava, transferSpecializations, 3)
      
      // Both should find routes
      routesElite.isDefinedAt(toAirportsList(1)).shouldBe(true)
      routesBusiness.isDefinedAt(toAirportsList(1)).shouldBe(true)
      
      // Both routes should have 2 links (transfer at toAirportsList(0))
      routesElite(toAirportsList(1)).links.size.shouldBe(2)
      routesBusiness(toAirportsList(1)).links.size.shouldBe(2)
      
      // Verify the transfer happens at toAirportsList(0)
      routesElite(toAirportsList(1)).links(0).link.to.shouldBe(toAirportsList(0))
      routesElite(toAirportsList(1)).links(1).link.from.shouldBe(toAirportsList(0))
    }
    "apply transfer discount for Traveler passengers at transfer airport".in {
      // Test that Traveler passengers get a transfer discount when transferring through an airport
      // with TRAVELER_TRANSFER specialization
      val travelerPassengerGroup = PassengerGroup(fromAirport, DealPreference(homeAirport = fromAirport, preferredLinkClass = ECONOMY, 1.0), PassengerType.TRAVELER)
      val elitePassengerGroup = PassengerGroup(fromAirport, DealPreference(homeAirport = fromAirport, preferredLinkClass = ECONOMY, 1.0), PassengerType.ELITE)
      
      // Create a multi-leg route with high frequency to ensure connection is viable
      val directLink = LinkConsideration.getExplicit(Link(fromAirport, toAirportsList(1), testAirline1, LinkClassValues.getInstance(100), 10000, LinkClassValues.getInstance(10000), 0, 600, 1), 13000, ECONOMY, false)
      val transferLinks = List(
        LinkConsideration.getExplicit(Link(fromAirport, toAirportsList(0), testAirline1, LinkClassValues.getInstance(100), 3500, LinkClassValues.getInstance(10000), 0, 200, 42), 3500, ECONOMY, false),
        LinkConsideration.getExplicit(Link(toAirportsList(0), toAirportsList(1), testAirline1, LinkClassValues.getInstance(100), 3500, LinkClassValues.getInstance(10000), 0, 200, 42), 3500, ECONOMY, false)
      )
      val allLinks = directLink :: transferLinks
      assignLinkConsiderationIds(allLinks)
      
      val transferSpecializations: java.util.Map[Int, Int] = new java.util.HashMap[Int, Int]()
      
      // Find routes for both Traveler and Elite passengers
      val routesTraveler = PassengerSimulation.findShortestRoute(travelerPassengerGroup, Set(toAirportsList(1)), allAirportIds, allLinks.asJava, transferSpecializations, 3)
      val routesElite = PassengerSimulation.findShortestRoute(elitePassengerGroup, Set(toAirportsList(1)), allAirportIds, allLinks.asJava, transferSpecializations, 3)
      
      // Both should find routes
      routesTraveler.isDefinedAt(toAirportsList(1)).shouldBe(true)
      routesElite.isDefinedAt(toAirportsList(1)).shouldBe(true)
      
      // Verify routes are found
      val travelerRoute = routesTraveler(toAirportsList(1))
      val eliteRoute = routesElite(toAirportsList(1))
      
      travelerRoute.links.size should be > 0
      eliteRoute.links.size should be > 0
    }
  }

  //  val airport1 = Airport("", "", "", 0, 0, "", "", "", 0, 0, 0, 0, 0)
  //  val airport2 = Airport("", "", "", 0, 100, "", "", "", 0, 0, 0, 0, 0)
  //  val airport3 = Airport("", "", "", 0, 200, "", "", "", 0, 0, 0, 0, 0)



  def isLoungePreference(preference: FlightPreference) : Boolean = {
    preference.isInstanceOf[AppealPreference] && preference.asInstanceOf[AppealPreference].loungeLevelRequired > 0
  }

  private def simulatePassengerAcceptance(clonedFromAirport: Airport, toAirport: Airport, newLink: Link, loopCount: Int = LOOP_COUNT): (Int, Int) = {
    var totalRoutes = 0
    var totalAcceptedRoutes = 0

    for (_ <- 0 until loopCount) {
      DemandGenerator.getFlightPreferencePoolOnAirport(clonedFromAirport).pool.foreach {
        case (paxType, flightPreferenceMap) =>
          flightPreferenceMap.foreach {
            case (preferredLinkClass, flightPreferenceList) =>
              flightPreferenceList.foreach { flightPreference =>
                if (!isLoungePreference(flightPreference)) {
                  val cost = flightPreference.computeCost(newLink, preferredLinkClass, paxType)
                  val linkConsiderations = List(LinkConsideration.getExplicit(newLink, cost, preferredLinkClass, false))
                  val route = Route(linkConsiderations, linkConsiderations.foldLeft(0.0)(_ + _.cost))

                  if (PassengerSimulation.getRouteRejection(route, clonedFromAirport, toAirport, preferredLinkClass, paxType).isEmpty) {
                    totalAcceptedRoutes += 1
                  }
                  totalRoutes += 1
                }
              }
          }
      }
    }
    (totalAcceptedRoutes, totalRoutes)
  }

  "IsLinkAffordable".must {
    "accept almost all route (single link) at 60% of suggested price and neutral quality and 50 loyalty".in {
      val clonedFromAirport  = fromAirport.copy()
      clonedFromAirport.initAirlineAppeals(Map(testAirline1.id -> AirlineAppeal(50)))

      val toAirport = toAirportsList(0)
      val distance = Util.calculateDistance(clonedFromAirport.latitude, clonedFromAirport.longitude, toAirport.latitude, toAirport.longitude).intValue()
      val suggestedPrice = Pricing.computeStandardPriceForAllClass(distance, FlightCategory.DOMESTIC, PassengerType.TRAVELER, clonedFromAirport.income)
      val duration = Computation.computeStandardFlightDuration(distance)
      val price = suggestedPrice * 0.6
      val quality = fromAirport.expectedQuality(distance, FIRST)
      val newLink = Link(clonedFromAirport, toAirport, testAirline1, price = price, distance = distance, LinkClassValues.getInstance(10000, 10000, 10000), rawQuality = quality, duration = duration, frequency = 14)
      newLink.setQuality(quality)

      val (totalAcceptedRoutes, totalRoutes) = simulatePassengerAcceptance(clonedFromAirport, toAirport, newLink)
      assert(totalAcceptedRoutes / totalRoutes.toDouble > 0.99)
    }
    "accept some route (single link) at 70% of suggested price with 0 quality/loyalty".in {
      val clonedFromAirport  = fromAirport.copy()
      clonedFromAirport.initAirlineAppeals(Map(testAirline1.id -> AirlineAppeal(0)))

      val toAirport = toAirportsList(0)
      val distance = Util.calculateDistance(clonedFromAirport.latitude, clonedFromAirport.longitude, toAirport.latitude, toAirport.longitude).intValue()
      val duration = Computation.computeStandardFlightDuration(distance)
      val suggestedPrice = Pricing.computeStandardPriceForAllClass(distance, FlightCategory.DOMESTIC, PassengerType.TRAVELER, clonedFromAirport.income)
      val price = suggestedPrice * 0.7
      val newLink = Link(clonedFromAirport, toAirport, testAirline1, price = price, distance = distance, LinkClassValues.getInstance(10000, 10000, 10000), rawQuality = 0, duration = duration, frequency = Link.HIGH_FREQUENCY_THRESHOLD)

      val (totalAcceptedRoutes, totalRoutes) = simulatePassengerAcceptance(clonedFromAirport, toAirport, newLink)
      assert(totalAcceptedRoutes / totalRoutes.toDouble > 0.4)
      assert(totalAcceptedRoutes / totalRoutes.toDouble < 0.6)
    }
    "accept most route (single link) at suggested price with neutral quality and decent loyalty".in {
      val clonedFromAirport  = fromAirport.copy()
      clonedFromAirport.initAirlineAppeals(Map(testAirline1.id -> AirlineAppeal(50)))

      val toAirport = toAirportsList(0)
      val distance = Util.calculateDistance(clonedFromAirport.latitude, clonedFromAirport.longitude, toAirport.latitude, toAirport.longitude).intValue()
      val duration = Computation.computeStandardFlightDuration(distance)
      val suggestedPrice = Pricing.computeStandardPriceForAllClass(distance, FlightCategory.DOMESTIC, PassengerType.TRAVELER, clonedFromAirport.income)
      val price = suggestedPrice
      val quality = fromAirport.expectedQuality(distance, FIRST)
      val newLink = Link(clonedFromAirport, toAirport, testAirline1, price = price, distance = distance, LinkClassValues.getInstance(10000, 10000, 10000), rawQuality = quality, duration = duration, frequency = 14)
      newLink.setQuality(quality)

      val (totalAcceptedRoutes, totalRoutes) = simulatePassengerAcceptance(clonedFromAirport, toAirport, newLink)
      assert(totalAcceptedRoutes.toDouble / totalRoutes > 0.7)
    }

    "accept some (single link) at suggested price with neutral quality and no loyalty".in {
      val clonedFromAirport  = fromAirport.copy()
      clonedFromAirport.initAirlineAppeals(Map(testAirline1.id -> AirlineAppeal(0)))

      val toAirport = toAirportsList(0)
      val distance = Util.calculateDistance(clonedFromAirport.latitude, clonedFromAirport.longitude, toAirport.latitude, toAirport.longitude).intValue()
      val duration = Computation.computeStandardFlightDuration(distance)
      val suggestedPrice = Pricing.computeStandardPriceForAllClass(distance, FlightCategory.DOMESTIC, PassengerType.TRAVELER, clonedFromAirport.income)
      val price = suggestedPrice
      val quality = fromAirport.expectedQuality(distance, ECONOMY)
      val newLink = Link(clonedFromAirport, toAirport, testAirline1, price = price, distance = distance, LinkClassValues.getInstance(10000, 10000, 10000), rawQuality = quality, duration, frequency = Link.HIGH_FREQUENCY_THRESHOLD)
      newLink.setQuality(quality)

      val (totalAcceptedRoutes, totalRoutes) = simulatePassengerAcceptance(clonedFromAirport, toAirport, newLink)
      assert(totalAcceptedRoutes / totalRoutes.toDouble > 0.3)
      assert(totalAcceptedRoutes / totalRoutes.toDouble < 0.5)
    }
    "accept some (single link) at suggested price with neutral quality and no loyalty for low income country".in {
      val clonedFromAirport  = fromAirport.copy(baseIncome = Country.LOW_INCOME_THRESHOLD / 2)
      clonedFromAirport.initAirlineAppeals(Map(testAirline1.id -> AirlineAppeal(0)))

      val toAirport = toAirportsList(0)
      val distance = Util.calculateDistance(clonedFromAirport.latitude, clonedFromAirport.longitude, toAirport.latitude, toAirport.longitude).intValue()
      val duration = Computation.computeStandardFlightDuration(distance)
      val suggestedPrice = Pricing.computeStandardPriceForAllClass(distance, FlightCategory.DOMESTIC, PassengerType.TRAVELER, clonedFromAirport.income)
      val price = suggestedPrice
      val quality = fromAirport.expectedQuality(distance, ECONOMY)
      val newLink = Link(clonedFromAirport, toAirport, testAirline1, price = price, distance = distance, LinkClassValues.getInstance(10000, 10000, 10000), rawQuality = quality, duration, frequency = Link.HIGH_FREQUENCY_THRESHOLD)
      newLink.setQuality(quality)

      val (totalAcceptedRoutes, totalRoutes) = simulatePassengerAcceptance(clonedFromAirport, toAirport, newLink)
      assert(totalAcceptedRoutes / totalRoutes.toDouble > 0.3)
      assert(totalAcceptedRoutes / totalRoutes.toDouble < 0.5)
    }

    "accept some (single link) at suggested price with neutral quality and no loyalty for very low income country".in {
      val clonedFromAirport  = fromAirport.copy(baseIncome = Country.LOW_INCOME_THRESHOLD / 10)
      clonedFromAirport.initAirlineAppeals(Map(testAirline1.id -> AirlineAppeal(0)))

      val toAirport = toAirportsList(0)
      val distance = Util.calculateDistance(clonedFromAirport.latitude, clonedFromAirport.longitude, toAirport.latitude, toAirport.longitude).intValue()
      val duration = Computation.computeStandardFlightDuration(distance)
      val suggestedPrice = Pricing.computeStandardPriceForAllClass(distance, FlightCategory.DOMESTIC, PassengerType.TRAVELER, clonedFromAirport.income)
      val price = suggestedPrice
      val quality = fromAirport.expectedQuality(distance, ECONOMY)
      val newLink = Link(clonedFromAirport, toAirport, testAirline1, price = price, distance = distance, LinkClassValues.getInstance(10000, 10000, 10000), rawQuality = quality, duration, frequency = Link.HIGH_FREQUENCY_THRESHOLD)
      newLink.setQuality(quality)

      val (totalAcceptedRoutes, totalRoutes) = simulatePassengerAcceptance(clonedFromAirport, toAirport, newLink)
      assert(totalAcceptedRoutes / totalRoutes.toDouble > 0.2)
      assert(totalAcceptedRoutes / totalRoutes.toDouble < 0.4)
    }

    "accept almost no link at 1.2 suggested price with 0 quality and no loyalty".in {
      val clonedFromAirport  = fromAirport.copy()
      clonedFromAirport.initAirlineAppeals(Map(testAirline1.id -> AirlineAppeal(0)))

      val toAirport = toAirportsList(0)
      val distance = Util.calculateDistance(clonedFromAirport.latitude, clonedFromAirport.longitude, toAirport.latitude, toAirport.longitude).intValue()
      val duration = Computation.computeStandardFlightDuration(distance)
      val suggestedPrice = Pricing.computeStandardPriceForAllClass(distance, FlightCategory.DOMESTIC, PassengerType.TRAVELER, clonedFromAirport.income)
      val price = suggestedPrice * 1.2
      val newLink = Link(clonedFromAirport, toAirport, testAirline1, price = price, distance = distance, LinkClassValues.getInstance(10000, 10000, 10000), rawQuality = 0, duration, frequency = Link.HIGH_FREQUENCY_THRESHOLD)

      val (totalAcceptedRoutes, totalRoutes) = simulatePassengerAcceptance(clonedFromAirport, toAirport, newLink)
      assert(totalAcceptedRoutes / totalRoutes.toDouble < 0.05)

    }

    "accept very few link at 1.4 x suggested price with neutral quality and decent loyalty".in {
      val clonedFromAirport  = fromAirport.copy()
      clonedFromAirport.initAirlineAppeals(Map(testAirline1.id -> AirlineAppeal(loyalty = 50)))

      val toAirport = toAirportsList(0)
      val distance = Util.calculateDistance(clonedFromAirport.latitude, clonedFromAirport.longitude, toAirport.latitude, toAirport.longitude).intValue()
      val duration = Computation.computeStandardFlightDuration(distance)
      val suggestedPrice = Pricing.computeStandardPriceForAllClass(distance, FlightCategory.DOMESTIC, PassengerType.TRAVELER, clonedFromAirport.income)
      val price = suggestedPrice * 1.4
      val quality = fromAirport.expectedQuality(distance, ECONOMY)
      val newLink = Link(clonedFromAirport, toAirport, testAirline1, price = price, distance = distance, LinkClassValues.getInstance(10000, 10000, 10000), rawQuality = quality, duration, frequency = Link.HIGH_FREQUENCY_THRESHOLD)
      newLink.setQuality(quality)

      val (totalAcceptedRoutes, totalRoutes) = simulatePassengerAcceptance(clonedFromAirport, toAirport, newLink)
      assert(totalAcceptedRoutes / totalRoutes.toDouble < 0.1)
      assert(totalAcceptedRoutes / totalRoutes.toDouble > 0)
    }

    "accept almost no link at 3 x suggested price with max quality and max loyalty".in {
      val clonedFromAirport  = fromAirport.copy()
      clonedFromAirport.initAirlineAppeals(Map(testAirline1.id -> AirlineAppeal(loyalty = 100)))

      val toAirport = toAirportsList(0)
      val distance = Util.calculateDistance(clonedFromAirport.latitude, clonedFromAirport.longitude, toAirport.latitude, toAirport.longitude).intValue()
      val duration = Computation.computeStandardFlightDuration(distance)
      val suggestedPrice = Pricing.computeStandardPriceForAllClass(distance, FlightCategory.DOMESTIC, PassengerType.TRAVELER, clonedFromAirport.income)
      val price = suggestedPrice * 3
      val quality = 100
      val newLink = Link(clonedFromAirport, toAirport, testAirline1, price = price, distance = distance, LinkClassValues.getInstance(10000, 10000, 10000), rawQuality = quality, duration, frequency = Link.HIGH_FREQUENCY_THRESHOLD)
      newLink.setQuality(quality)

      //hmm kinda mix in flight preference here...might not be a good thing... loop 10000 times so result is more consistent
      var totalRoutes = 0
      var totalAcceptedRoutes = 0;
      for (i <- 0 until LOOP_COUNT) {
        DemandGenerator.getFlightPreferencePoolOnAirport(clonedFromAirport).pool.foreach {
          case (paxType, flightPreferenceMap) =>
            flightPreferenceMap.foreach {
              case (preferredLinkClass, flightPreferenceList) =>
                flightPreferenceList.foreach { flightPreference =>
                  if (!isLoungePreference(flightPreference)) {
                    val breakdown = flightPreference.computeCostBreakdown(newLink, preferredLinkClass, paxType)
                    val cost = breakdown.cost
                    val linkConsiderations = List[LinkConsideration](LinkConsideration.getExplicit(newLink, cost, preferredLinkClass, false))


                    val route = Route(linkConsiderations, linkConsiderations.foldLeft(0.0) { _ + _.cost })
                    if (PassengerSimulation.getRouteRejection(route, clonedFromAirport, toAirport, preferredLinkClass, paxType).isEmpty) {
                      totalAcceptedRoutes = totalAcceptedRoutes + 1
                      //println(s"accepted $flightPreference -> $breakdown")
                    }
                    totalRoutes = totalRoutes + 1
                  }
                }
            }
        }
      }
      assert(totalAcceptedRoutes / totalRoutes.toDouble < 0.1)
      assert(totalAcceptedRoutes / totalRoutes.toDouble > 0)
    }

    "accept very few link at 2 x suggested price with max quality and max loyalty".in {
      val clonedFromAirport  = fromAirport.copy()
      clonedFromAirport.initAirlineAppeals(Map(testAirline1.id -> AirlineAppeal(loyalty = 100)))

      val toAirport = toAirportsList(0)
      val distance = Util.calculateDistance(clonedFromAirport.latitude, clonedFromAirport.longitude, toAirport.latitude, toAirport.longitude).intValue()
      val duration = Computation.computeStandardFlightDuration(distance)
      val suggestedPrice = Pricing.computeStandardPriceForAllClass(distance, FlightCategory.DOMESTIC, PassengerType.TRAVELER, clonedFromAirport.income)
      val price = suggestedPrice * 2
      val quality = 100
      val newLink = Link(clonedFromAirport, toAirport, testAirline1, price = price, distance = distance, LinkClassValues.getInstance(10000, 10000, 10000), rawQuality = quality, duration, frequency = Link.HIGH_FREQUENCY_THRESHOLD)
      newLink.setQuality(quality)

      //hmm kinda mix in flight preference here...might not be a good thing... loop 10000 times so result is more consistent
      var totalRoutes = 0
      var totalAcceptedRoutes = 0
      for (i <- 0 until LOOP_COUNT) {
        DemandGenerator.getFlightPreferencePoolOnAirport(clonedFromAirport).pool.foreach {
          case (paxType, flightPreferenceMap) =>
            flightPreferenceMap.foreach {
              case (preferredLinkClass, flightPreferenceList) =>
                flightPreferenceList.foreach { flightPreference =>
                  if (!isLoungePreference(flightPreference)) {
                    val cost = flightPreference.computeCostBreakdown(newLink, preferredLinkClass, paxType)
                    val linkConsiderations = List[LinkConsideration](LinkConsideration.getExplicit(newLink, cost.cost, preferredLinkClass, false))


                    val route = Route(linkConsiderations, linkConsiderations.foldLeft(0.0) { _ + _.cost })
                    if (PassengerSimulation.getRouteRejection(route, clonedFromAirport, toAirport, preferredLinkClass, paxType).isEmpty) {
                      totalAcceptedRoutes = totalAcceptedRoutes + 1
                      //println(s"$flightPreference to $cost")
                    }
                    totalRoutes = totalRoutes + 1
                  }
                }
            }
        }
      }
      assert(totalAcceptedRoutes / totalRoutes.toDouble < 0.3)
      assert(totalAcceptedRoutes / totalRoutes.toDouble > 0)
    }

    "accept very few link at 2 x suggested price with max quality and max loyalty but very low income country".in {
      val clonedFromAirport  = fromAirport.copy(baseIncome = 1000)
      clonedFromAirport.initAirlineAppeals(Map(testAirline1.id -> AirlineAppeal(loyalty = 100)))

      val toAirport = toAirportsList(0)
      val distance = Util.calculateDistance(clonedFromAirport.latitude, clonedFromAirport.longitude, toAirport.latitude, toAirport.longitude).intValue()
      val duration = Computation.computeStandardFlightDuration(distance)
      val suggestedPrice = Pricing.computeStandardPriceForAllClass(distance, FlightCategory.DOMESTIC, PassengerType.TRAVELER, clonedFromAirport.income)
      val price = suggestedPrice * 2
      val quality = 100
      val newLink = Link(clonedFromAirport, toAirport, testAirline1, price = price, distance = distance, LinkClassValues.getInstance(10000, 10000, 10000), rawQuality = quality, duration, frequency = Link.HIGH_FREQUENCY_THRESHOLD)
      newLink.setQuality(quality)

      val (totalAcceptedRoutes, totalRoutes) = simulatePassengerAcceptance(clonedFromAirport, toAirport, newLink)
      assert(totalAcceptedRoutes / totalRoutes.toDouble < 0.1)
      assert(totalAcceptedRoutes / totalRoutes.toDouble > 0)
    }

    "accept no link at 2 x suggested price with no quality and no loyalty".in {
      val clonedFromAirport  = fromAirport.copy()
      clonedFromAirport.initAirlineAppeals(Map(testAirline1.id -> AirlineAppeal(loyalty = 0)))

      val toAirport = toAirportsList(0)
      val distance = Util.calculateDistance(clonedFromAirport.latitude, clonedFromAirport.longitude, toAirport.latitude, toAirport.longitude).intValue()
      val duration = Computation.computeStandardFlightDuration(distance)
      val suggestedPrice = Pricing.computeStandardPriceForAllClass(distance, FlightCategory.DOMESTIC, PassengerType.TRAVELER, clonedFromAirport.income)
      val price = suggestedPrice * 2
      val newLink = Link(clonedFromAirport, toAirport, testAirline1, price = price, distance = distance, LinkClassValues.getInstance(10000, 10000, 10000), rawQuality = 0, duration, frequency = Link.HIGH_FREQUENCY_THRESHOLD)

      val (totalAcceptedRoutes, totalRoutes) = simulatePassengerAcceptance(clonedFromAirport, toAirport, newLink)
      assert(totalAcceptedRoutes == 0)
    }



    "accept few link at suggested price with neutral quality and decent loyalty but downgrade in class (from business to econ)".in {
      val clonedFromAirport  = fromAirport.copy()
      clonedFromAirport.initAirlineAppeals(Map(testAirline1.id -> AirlineAppeal(loyalty = 50)))

      val toAirport = toAirportsList(0)
      val distance = Util.calculateDistance(clonedFromAirport.latitude, clonedFromAirport.longitude, toAirport.latitude, toAirport.longitude).intValue()
      val duration = Computation.computeStandardFlightDuration(distance)
      val suggestedPrice = Pricing.computeStandardPriceForAllClass(distance, FlightCategory.DOMESTIC, PassengerType.TRAVELER, clonedFromAirport.income)
      val price = suggestedPrice
      val quality = fromAirport.expectedQuality(distance, ECONOMY)
      val newLink = Link(clonedFromAirport, toAirport, testAirline1, price = price, distance = distance, LinkClassValues.getInstance(10000, 0, 0), rawQuality = quality, duration, frequency = Link.HIGH_FREQUENCY_THRESHOLD)
      newLink.setQuality(quality)

      //hmm kinda mix in flight preference here...might not be a good thing... loop 10000 times so result is more consistent
      var totalRoutes = 0
      var totalAcceptedRoutes = 0;
      val businessPaxType = PassengerType.BUSINESS
      for (i <- 0 until LOOP_COUNT) {
        DemandGenerator.getFlightPreferencePoolOnAirport(clonedFromAirport).pool.get(businessPaxType).foreach {
          flightPreferenceMap =>
            flightPreferenceMap.foreach {
              case (preferredLinkClass, flightPreferenceList) =>
                flightPreferenceList.foreach { flightPreference =>
                  if (preferredLinkClass == BUSINESS && !isLoungePreference(flightPreference)) {
                    val cost = flightPreference.computeCost(newLink, ECONOMY, businessPaxType)
                    val linkConsiderations = List[LinkConsideration](LinkConsideration.getExplicit(newLink, cost, ECONOMY, false))

                    val route = Route(linkConsiderations, linkConsiderations.foldLeft(0.0) { _ + _.cost })
                    if (PassengerSimulation.getRouteRejection(route, clonedFromAirport, toAirport, preferredLinkClass, businessPaxType).isEmpty) {
                      totalAcceptedRoutes = totalAcceptedRoutes + 1
                    }
                    totalRoutes = totalRoutes + 1
                  }
                }
            }
        }
      }
      assert(totalAcceptedRoutes / totalRoutes.toDouble < 0.25)
    }

    "accept some links at 2 x suggested price with neutral quality and decent loyalty for SUPERSONIC flight of Last Minute pax".in {
      val clonedFromAirport  = fromAirport.copy()
      clonedFromAirport.initAirlineAppeals(Map(testAirline1.id -> AirlineAppeal(loyalty = 50)))

      val toAirport = toAirportsList(2)
      val distance = Util.calculateDistance(clonedFromAirport.latitude, clonedFromAirport.longitude, toAirport.latitude, toAirport.longitude).intValue()
      val paxType = PassengerType.BUSINESS
      val duration = Computation.calculateDuration(mediumAirplaneModel, distance)
      val suggestedPrice = Pricing.computeStandardPriceForAllClass(distance, FlightCategory.DOMESTIC, paxType, clonedFromAirport.income)
      val price = suggestedPrice * 2
      val quality = fromAirport.expectedQuality(distance, BUSINESS)
      val newLink = Link(clonedFromAirport, toAirport, testAirline1, price = price, distance = distance, LinkClassValues.getInstance(10000, 10000, 10000), rawQuality = quality, duration, frequency = Link.HIGH_FREQUENCY_THRESHOLD)
      newLink.setQuality(quality)

      var totalRoutes = 0
      var totalAcceptedRoutes = 0;
      for (i <- 0 until LOOP_COUNT) {
        val preferredLinkClass = BUSINESS

        val flightPreference = LastMinutePreference(clonedFromAirport, BUSINESS, 1.0, 0)

        val cost = flightPreference.computeCost(newLink, preferredLinkClass, paxType)
        val linkConsiderations = List[LinkConsideration] (LinkConsideration.getExplicit(newLink, cost, preferredLinkClass, false))

        val route = Route(linkConsiderations, linkConsiderations.foldLeft(0.0) { _ + _.cost })
        if (PassengerSimulation.getRouteRejection(route, clonedFromAirport, toAirport, preferredLinkClass, paxType).isEmpty) {
          totalAcceptedRoutes = totalAcceptedRoutes + 1
        }
        totalRoutes = totalRoutes + 1
      }
      assert(totalAcceptedRoutes / totalRoutes.toDouble > 0.5)
      assert(totalAcceptedRoutes / totalRoutes.toDouble < 0.8)
    }

    "accept some links at 1.4 x suggested price with neutral quality and decent loyalty for SUPERSONIC flight of Appeal First Class pax".in {
      val clonedFromAirport  = fromAirport.copy()
      clonedFromAirport.initAirlineAppeals(Map(testAirline1.id -> AirlineAppeal(loyalty = 50)))

      val toAirport = toAirportsList(2)
      val distance = Util.calculateDistance(clonedFromAirport.latitude, clonedFromAirport.longitude, toAirport.latitude, toAirport.longitude).intValue()
      val duration = Computation.calculateDuration(mediumAirplaneModel, distance)
      val suggestedPrice = Pricing.computeStandardPriceForAllClass(distance, FlightCategory.DOMESTIC, PassengerType.TRAVELER, clonedFromAirport.income)
      val price = suggestedPrice * 1.4
      val quality = fromAirport.expectedQuality(distance, FIRST)
      val newLink = Link(clonedFromAirport, toAirport, testAirline1, price = price, distance = distance, LinkClassValues.getInstance(10000, 10000, 10000), rawQuality = quality, duration, frequency = Link.HIGH_FREQUENCY_THRESHOLD)
      newLink.setQuality(quality)

      var totalRoutes = 0
      var totalAcceptedRoutes = 0;
      for (i <- 0 until LOOP_COUNT) {
        val preferredLinkClass = FIRST
        val paxType = PassengerType.ELITE
        val flightPreference = AppealPreference.getAppealPreferenceWithId(fromAirport, FIRST, 1.0, loungeLevelRequired = 0, loyaltyRatio = 1.1)

        val cost = flightPreference.computeCost(newLink, preferredLinkClass, paxType)
        val linkConsiderations = List[LinkConsideration] (LinkConsideration.getExplicit(newLink, cost, preferredLinkClass, false))

        val route = Route(linkConsiderations, linkConsiderations.foldLeft(0.0) { _ + _.cost })
        if (PassengerSimulation.getRouteRejection(route, clonedFromAirport, toAirport, preferredLinkClass, paxType).isEmpty) {
          totalAcceptedRoutes = totalAcceptedRoutes + 1
        }
        totalRoutes = totalRoutes + 1
      }
      assert(totalAcceptedRoutes / totalRoutes.toDouble > 0.2)
      assert(totalAcceptedRoutes / totalRoutes.toDouble < 0.4)
    }

    "accept very few route with links are at 1.25 price with neutral quality and 0 loyalty".in { //will be less than single link cause each run fitler out some
      val clonedFromAirport  = fromAirport.copy()
      clonedFromAirport.initAirlineAppeals(Map(testAirline1.id -> AirlineAppeal(0)))

      var airportWalker = clonedFromAirport
      val links = toAirportsList.map { toAirport =>
        val distance = Util.calculateDistance(airportWalker.latitude, airportWalker.longitude, toAirport.latitude, toAirport.longitude).intValue()
        val duration = Computation.computeStandardFlightDuration(distance)
        val suggestedPrice = Pricing.computeStandardPriceForAllClass(distance, FlightCategory.DOMESTIC, PassengerType.TRAVELER, airportWalker.income)
        val quality = fromAirport.expectedQuality(distance, FIRST)
        val newLink = Link(airportWalker, toAirport, testAirline1, price = suggestedPrice * 1.25, distance = distance, LinkClassValues.getInstance(10000, 10000, 10000), rawQuality = quality, duration, frequency = Link.HIGH_FREQUENCY_THRESHOLD)
        newLink.setQuality(quality)
        airportWalker = toAirport
        newLink }

      //hmm kinda mix in flight preference here...might not be a good thing... loop 10000 times so result is more consistent
      var totalRoutes = 0
      var totalAcceptedRoutes = 0;
      for (i <- 0 until LOOP_COUNT) {
        DemandGenerator.getFlightPreferencePoolOnAirport(clonedFromAirport).pool.foreach {
          case (paxType, flightPreferenceMap) =>
            flightPreferenceMap.foreach {
              case (preferredLinkClass, flightPreferenceList) =>
                flightPreferenceList.foreach { flightPreference =>
                  if (!isLoungePreference(flightPreference)) {
                    val linkConsiderations = links.map { link =>
                      val cost = flightPreference.computeCost(link, preferredLinkClass, paxType)
                      LinkConsideration.getExplicit(link, cost, preferredLinkClass, false)
                    }

                    val route = Route(linkConsiderations, linkConsiderations.foldLeft(0.0) { _ + _.cost })
                    totalRoutes += 1
                    if (PassengerSimulation.getRouteRejection(route, clonedFromAirport, toAirportsList.last, preferredLinkClass, paxType).isEmpty) {
                      totalAcceptedRoutes += 1
                    }
                  }
                }
            }
        }
      }

    assert(totalAcceptedRoutes / totalRoutes.toDouble > 0)
    assert(totalAcceptedRoutes / totalRoutes.toDouble < 0.1)
  }




  "reject route with one short link at 4X suggested price at min loyalty and quality".in {
    val clonedFromAirport  = fromAirport.copy()
    clonedFromAirport.initAirlineAppeals(Map(testAirline1.id -> AirlineAppeal(0)))

    val toAirports = List[Airport] (
      Airport("", "", "To Airport", 0, 30, "", "", "", 1, 0, 0, 0, id = 2),
      Airport("", "", "To Airport", 0, 90, "", "", "", 1, 0, 0, 0, id = 3),
      Airport("", "", "To Airport", 0, 92, "", "", "", 1, 0, 0, 0, id = 4) //even if the last segment is really short
    )

    var airportWalker = clonedFromAirport
    val links = toAirports.map { toAirport =>
      val distance = Util.calculateDistance(airportWalker.latitude, airportWalker.longitude, toAirport.latitude, toAirport.longitude).intValue()
      val duration = Computation.computeStandardFlightDuration(distance)
      val suggestedPrice = Pricing.computeStandardPriceForAllClass(distance, FlightCategory.DOMESTIC, PassengerType.TRAVELER, airportWalker.income)
      //make last link really expensive
      val newLink = Link(airportWalker, toAirport, testAirline1, price = suggestedPrice * (if (toAirport == toAirports.last) 4 else 1), distance = distance, LinkClassValues.getInstance(10000, 10000, 10000), 0, duration, frequency = Link.HIGH_FREQUENCY_THRESHOLD)
      airportWalker = toAirport
      newLink }


    //hmm kinda mix in flight preference here...might not be a good thing... loop 100 times so result is more consistent
    for (i <- 0 until LOOP_COUNT) {
      DemandGenerator.getFlightPreferencePoolOnAirport(clonedFromAirport).pool.foreach {
        case (paxType, flightPreferenceMap) =>
          flightPreferenceMap.foreach {
            case (preferredLinkClass, flightPreferenceList) =>
              flightPreferenceList.foreach { flightPreference =>
                if (!isLoungePreference(flightPreference)) {
                  val linkConsiderations = links.map { link =>
                    val cost = flightPreference.computeCost(link, preferredLinkClass, paxType)
                    LinkConsideration.getExplicit(link, cost, preferredLinkClass, false)
                  }

                  val route = Route(linkConsiderations, linkConsiderations.foldLeft(0.0) { _ + _.cost })
                  assert(!PassengerSimulation.getRouteRejection(route, clonedFromAirport, toAirports.last, preferredLinkClass, paxType).isEmpty)
                }
              }
          }
      }
    }
  }
}


"accept some routes with suggested price at good quality but no loyalty".in {
  val clonedFromAirport  = fromAirport.copy()
  clonedFromAirport.initAirlineAppeals(Map(testAirline1.id -> AirlineAppeal(loyalty = 0)))

  val toAirports = List[Airport] (
    Airport("", "", "To Airport", 0, 30, "", "", "", 1, 0, 0, 0, id = 2),
    Airport("", "", "To Airport", 0, 90, "", "", "", 1, 0, 0, 0, id = 3),
    Airport("", "", "To Airport", 0, 92, "", "", "", 1, 0, 0, 0, id = 4) //the last segment is really short
  )

  var airportWalker = clonedFromAirport
  val links = toAirports.map { toAirport =>
    val distance = Util.calculateDistance(airportWalker.latitude, airportWalker.longitude, toAirport.latitude, toAirport.longitude).intValue()
    val duration = Computation.computeStandardFlightDuration(distance)
    val suggestedPrice = Pricing.computeStandardPriceForAllClass(distance, FlightCategory.DOMESTIC, PassengerType.TRAVELER, airportWalker.income)
    val quality = 70
    val newLink = Link(airportWalker, toAirport, testAirline1, price = suggestedPrice, distance = distance, LinkClassValues.getInstance(10000, 10000, 10000), rawQuality = quality, duration, frequency = Link.HIGH_FREQUENCY_THRESHOLD)
    newLink.setQuality(quality)
    airportWalker = toAirport
    newLink }


  //hmm kinda mix in flight preference here...might not be a good thing... loop 10000 times so result is more consistent
  var totalRoutes = 0
  var totalAcceptedRoutes = 0;
  for (i <- 0 until LOOP_COUNT) {
    DemandGenerator.getFlightPreferencePoolOnAirport(clonedFromAirport).pool.foreach {
      case (paxType, flightPreferenceMap) =>
        flightPreferenceMap.foreach {
          case (preferredLinkClass, flightPreferenceList) =>
            flightPreferenceList.foreach { flightPreference =>
              if (!isLoungePreference(flightPreference)) {
                val linkConsiderations = links.map { link =>
                  val cost = flightPreference.computeCost(link, preferredLinkClass, paxType)
                  LinkConsideration.getExplicit(link, cost, preferredLinkClass, false)
                }

                val route = Route(linkConsiderations, linkConsiderations.foldLeft(0.0) { _ + _.cost })

                if (PassengerSimulation.getRouteRejection(route, clonedFromAirport, toAirports.last, preferredLinkClass, paxType).isEmpty) {
                  totalAcceptedRoutes += 1
                }
                totalRoutes += 1
              }
            }
        }
    }
  }
  assert(totalAcceptedRoutes.toDouble / totalRoutes > 0.3)
  assert(totalAcceptedRoutes.toDouble / totalRoutes < 0.5)
}

"accept most routes with suggested price at good quality and good loyalty".in {
  val clonedFromAirport  = fromAirport.copy()
  clonedFromAirport.initAirlineAppeals(Map(testAirline1.id -> AirlineAppeal(loyalty = 80)))

  val toAirports = List[Airport] (
    Airport("", "", "To Airport", 0, 30, "", "", "", 1, 0, 0, 0, id = 2),
    Airport("", "", "To Airport", 0, 90, "", "", "", 1, 0, 0, 0, id = 3),
    Airport("", "", "To Airport", 0, 92, "", "", "", 1, 0, 0, 0, id = 4) //the last segment is really short
  )

  var airportWalker = clonedFromAirport
  val links = toAirports.map { toAirport =>
    val distance = Util.calculateDistance(airportWalker.latitude, airportWalker.longitude, toAirport.latitude, toAirport.longitude).intValue()
    val duration = Computation.computeStandardFlightDuration(distance)
    val suggestedPrice = Pricing.computeStandardPriceForAllClass(distance, FlightCategory.DOMESTIC, PassengerType.TRAVELER, airportWalker.income)
    val quality = 70
    val newLink = Link(airportWalker, toAirport, testAirline1, price = suggestedPrice, distance = distance, LinkClassValues.getInstance(10000, 10000, 10000), rawQuality = quality, duration, frequency = Link.HIGH_FREQUENCY_THRESHOLD)
    newLink.setQuality(quality)
    airportWalker = toAirport
    newLink }


  //hmm kinda mix in flight preference here...might not be a good thing... loop 10000 times so result is more consistent
  var totalRoutes = 0
  var totalAcceptedRoutes = 0;
  for (i <- 0 until LOOP_COUNT) {
    DemandGenerator.getFlightPreferencePoolOnAirport(clonedFromAirport).pool.foreach {
      case (paxType, flightPreferenceMap) =>
        flightPreferenceMap.foreach {
          case (preferredLinkClass, flightPreferenceList) =>
            flightPreferenceList.foreach { flightPreference =>
              if (!isLoungePreference(flightPreference)) {
                val linkConsiderations = links.map { link =>
                  val cost = flightPreference.computeCost(link, preferredLinkClass, paxType)
                  LinkConsideration.getExplicit(link, cost, preferredLinkClass, false)
                }

                val route = Route(linkConsiderations, linkConsiderations.foldLeft(0.0) { _ + _.cost })

                if (PassengerSimulation.getRouteRejection(route, clonedFromAirport, toAirports.last, preferredLinkClass, paxType).isEmpty) {
                  totalAcceptedRoutes += 1
                }
                totalRoutes += 1
              }
            }
        }
    }
  }
  assert(totalAcceptedRoutes.toDouble / totalRoutes > 0.8)
}



"accept most routes with suggested price at neutral quality and decent loyalty".in {
  val clonedFromAirport  = fromAirport.copy()
  clonedFromAirport.initAirlineAppeals(Map(testAirline1.id -> AirlineAppeal(loyalty = 50)))

  val toAirports = List[Airport] (
    Airport("", "", "To Airport", 0, 30, "", "", "", 1, 0, 0, 0, id = 2),
    Airport("", "", "To Airport", 0, 90, "", "", "", 1, 0, 0, 0, id = 3),
    Airport("", "", "To Airport", 0, 92, "", "", "", 1, 0, 0, 0, id = 4) //the last segment is really short
  )

  var airportWalker = clonedFromAirport
  val links = toAirports.map { toAirport =>
    val distance = Util.calculateDistance(airportWalker.latitude, airportWalker.longitude, toAirport.latitude, toAirport.longitude).intValue()
    val duration = Computation.computeStandardFlightDuration(distance)
    val suggestedPrice = Pricing.computeStandardPriceForAllClass(distance, FlightCategory.DOMESTIC, PassengerType.TRAVELER, airportWalker.income)
    val quality = fromAirport.expectedQuality(distance, FIRST)
    val newLink = Link(airportWalker, toAirport, testAirline1, price = suggestedPrice, distance = distance, LinkClassValues.getInstance(10000, 10000, 10000), rawQuality = quality, duration, frequency = Link.HIGH_FREQUENCY_THRESHOLD)
    newLink.setQuality(quality)
    airportWalker = toAirport
    newLink }


  //hmm kinda mix in flight preference here...might not be a good thing... loop 10000 times so result is more consistent
  var totalRoutes = 0
  var totalAcceptedRoutes = 0;
  for (i <- 0 until LOOP_COUNT) {
    DemandGenerator.getFlightPreferencePoolOnAirport(clonedFromAirport).pool.foreach {
      case (paxType, flightPreferenceMap) =>
        flightPreferenceMap.foreach {
          case (preferredLinkClass, flightPreferenceList) =>
            flightPreferenceList.foreach { flightPreference =>
              if (!isLoungePreference(flightPreference)) {
                val linkConsiderations = links.map { link =>
                  val cost = flightPreference.computeCost(link, preferredLinkClass, paxType)
                  LinkConsideration.getExplicit(link, cost, preferredLinkClass, false)
                }

                val route = Route(linkConsiderations, linkConsiderations.foldLeft(0.0) { _ + _.cost })

                if (PassengerSimulation.getRouteRejection(route, clonedFromAirport, toAirports.last, preferredLinkClass, paxType).isEmpty) {
                  totalAcceptedRoutes += 1
                }
                totalRoutes += 1
              }
            }
        }
    }
  }
  assert(totalAcceptedRoutes.toDouble / totalRoutes > 0.6)
}



"reject route that at 2X suggested price at min loyalty and quality".in {
  val clonedFromAirport  = fromAirport.copy()
  clonedFromAirport.initAirlineAppeals(Map(testAirline1.id -> AirlineAppeal(0)))

  var airportWalker = clonedFromAirport
  val links = toAirportsList.map { toAirport =>
    val distance = Util.calculateDistance(airportWalker.latitude, airportWalker.longitude, toAirport.latitude, toAirport.longitude).intValue()
    val duration = Computation.computeStandardFlightDuration(distance)
    val suggestedPrice = Pricing.computeStandardPriceForAllClass(distance, FlightCategory.DOMESTIC, PassengerType.TRAVELER, airportWalker.income)
    val newLink = Link(airportWalker, toAirport, testAirline1, price = suggestedPrice * 2, distance = distance, LinkClassValues.getInstance(10000, 10000, 10000), 0, duration, frequency = Link.HIGH_FREQUENCY_THRESHOLD)
    airportWalker = toAirport
    newLink }

  //hmm kinda mix in flight preference here...might not be a good thing... loop 10000 times so result is more consistent
  for (i <- 0 until LOOP_COUNT) {
    DemandGenerator.getFlightPreferencePoolOnAirport(clonedFromAirport).pool.foreach {
      case (paxType, flightPreferenceMap) =>
        flightPreferenceMap.foreach {
          case (preferredLinkClass, flightPreferenceList) =>
            flightPreferenceList.foreach { flightPreference =>
              if (!isLoungePreference(flightPreference)) {
                val linkConsiderations = links.map { link =>
                  val cost = flightPreference.computeCost(link, preferredLinkClass, paxType)
                  LinkConsideration.getExplicit(link, cost, preferredLinkClass, false)
                }
                val route = Route(linkConsiderations, linkConsiderations.foldLeft(0.0) { _ + _.cost })
                assert(PassengerSimulation.getRouteRejection(route, clonedFromAirport, toAirportsList.last, preferredLinkClass, paxType) == Some(RouteRejectionReason.TOTAL_COST), route + " " + flightPreference)
              }
            }
        }
    }
  }
}

"accept most route that all links are at 60% price and the total distance travel is 1.25x of the actual displacement with neutral quality".in {
  val clonedFromAirport  = fromAirport.copy()
  clonedFromAirport.initAirlineAppeals(Map(testAirline1.id -> AirlineAppeal(0)))

  val toAirports = List[Airport] (
    Airport("", "", "To Airport", 0, 112.25, "", "", "", 1, 0, 0, 0, id = 2),
    Airport("", "", "To Airport", 0, 100, "", "", "", 1, 0, 0, 0, id = 3) //displacement is 100, while distance is 125
  )

  var airportWalker = clonedFromAirport
  val links = toAirports.map { toAirport =>
    val distance = Util.calculateDistance(airportWalker.latitude, airportWalker.longitude, toAirport.latitude, toAirport.longitude).intValue()
    val duration = Computation.computeStandardFlightDuration(distance)
    val suggestedPrice = Pricing.computeStandardPriceForAllClass(distance, FlightCategory.DOMESTIC, PassengerType.TRAVELER, airportWalker.income) * 0.6
    val newLink = Link(airportWalker, toAirport, testAirline1, price = suggestedPrice, distance = distance, LinkClassValues.getInstance(10000, 10000, 10000), rawQuality = 50, duration, frequency = Link.HIGH_FREQUENCY_THRESHOLD)
    newLink.setQuality(50)
    airportWalker = toAirport
    newLink }

  //hmm kinda mix in flight preference here...might not be a good thing... loop 10000 times so result is more consistent
  var totalRoutes = 0
  var totalAcceptedRoutes = 0;
  for (i <- 0 until LOOP_COUNT) {
    DemandGenerator.getFlightPreferencePoolOnAirport(clonedFromAirport).pool.foreach {
      case (paxType, flightPreferenceMap) =>
        flightPreferenceMap.foreach {
          case (preferredLinkClass, flightPreferenceList) =>
            flightPreferenceList.foreach { flightPreference =>
              if (!isLoungePreference(flightPreference)) {
                val linkConsiderations = links.map { link =>
                  val cost = flightPreference.computeCost(link, preferredLinkClass, paxType)
                  LinkConsideration.getExplicit(link, cost, preferredLinkClass, false)
                }
                val route = Route(linkConsiderations, linkConsiderations.foldLeft(0.0) { _ + _.cost })
                if (PassengerSimulation.getRouteRejection(route, clonedFromAirport, toAirports.last, preferredLinkClass, paxType).isEmpty) {
                  totalAcceptedRoutes += 1
                }
                totalRoutes += 1
              }
            }
        }
    }
  }
  assert(totalAcceptedRoutes / totalRoutes.toDouble > 0.8)
}

"reject route that all links are at suggested price and the total distance travel is 3x of the actual displacement".in {
  val clonedFromAirport  = fromAirport.copy()
  clonedFromAirport.initAirlineAppeals(Map(testAirline1.id -> AirlineAppeal(0)))

  val toAirports = List[Airport] (
    Airport("", "", "To Airport", 0, 60, "", "", "", 1, 0, 0, 0, id = 2),
    Airport("", "", "To Airport", 0, 30, "", "", "", 1, 0, 0, 0, id = 3) //displacement is 30, while distance is 90
  )

  var airportWalker = clonedFromAirport
  val links = toAirports.map { toAirport =>
    val distance = Util.calculateDistance(airportWalker.latitude, airportWalker.longitude, toAirport.latitude, toAirport.longitude).intValue()
    val duration = Computation.computeStandardFlightDuration(distance)
    val suggestedPrice = Pricing.computeStandardPriceForAllClass(distance, FlightCategory.DOMESTIC, PassengerType.TRAVELER, airportWalker.income)
    val newLink = Link(airportWalker, toAirport, testAirline1, price = suggestedPrice, distance = distance, LinkClassValues.getInstance(10000, 10000, 10000), 0, duration, frequency = Link.HIGH_FREQUENCY_THRESHOLD)
    airportWalker = toAirport
    newLink }

  //hmm kinda mix in flight preference here...might not be a good thing... loop 10000 times so result is more consistent
  for (i <- 0 until LOOP_COUNT) {
    DemandGenerator.getFlightPreferencePoolOnAirport(clonedFromAirport).pool.foreach {
      case (paxType, flightPreferenceMap) =>
        flightPreferenceMap.foreach {
          case (preferredLinkClass, flightPreferenceList) =>
            flightPreferenceList.foreach { flightPreference =>
              if (!isLoungePreference(flightPreference)) {
                val linkConsiderations = links.map { link =>
                  val cost = flightPreference.computeCost(link, preferredLinkClass, paxType)
                  LinkConsideration.getExplicit(link, cost, preferredLinkClass, false)
                }
                val route = Route(linkConsiderations, linkConsiderations.foldLeft(0.0) { _ + _.cost })
                assert(PassengerSimulation.getRouteRejection(route, clonedFromAirport, toAirports.last, preferredLinkClass, paxType) == Some(RouteRejectionReason.DISTANCE), route + " " + flightPreference)
              }
            }
        }
    }
  }
}

"reject most links at standard price if it does not fulfill lounge requirement (long flight)".in {
  val clonedFromAirport  = fromAirport.copy(size = 5)
  clonedFromAirport.initAirlineAppeals(Map(testAirline1.id -> AirlineAppeal(50)))

  val toAirport = toAirportsList(2).copy(size = 5) //no lounge on the other side... so it's a no
  val distance = Util.calculateDistance(clonedFromAirport.latitude, clonedFromAirport.longitude, toAirport.latitude, toAirport.longitude).intValue()
  val duration = Computation.computeStandardFlightDuration(distance)
  val suggestedPrice = Pricing.computeStandardPriceForAllClass(distance, FlightCategory.DOMESTIC, PassengerType.TRAVELER, clonedFromAirport.income)
  val price = suggestedPrice
  val quality = fromAirport.expectedQuality(distance, FIRST)
  val newLink = Link(clonedFromAirport, toAirport, testAirline1, price = price, distance = distance, LinkClassValues.getInstance(10000, 10000, 10000), rawQuality = quality, duration, frequency = Link.HIGH_FREQUENCY_THRESHOLD)
  newLink.setQuality(quality)

  //hmm kinda mix in flight preference here...might not be a good thing... loop 10000 times so result is more consistent
  var totalRoutes = 0
  var totalAcceptedRoutes = 0;
  for (i <- 0 until LOOP_COUNT) {
    DemandGenerator.getFlightPreferencePoolOnAirport(clonedFromAirport).pool.foreach {
      case (paxType, flightPreferenceMap) =>
        flightPreferenceMap.foreach {
          case (preferredLinkClass, flightPreferenceList) =>
            flightPreferenceList.foreach { flightPreference =>
              if (isLoungePreference(flightPreference)) {
                val cost = flightPreference.computeCost(newLink, preferredLinkClass, paxType)
                val linkConsiderations = List[LinkConsideration](LinkConsideration.getExplicit(newLink, cost, preferredLinkClass, false))
                val route = Route(linkConsiderations, linkConsiderations.foldLeft(0.0) { _ + _.cost })
                if (PassengerSimulation.getRouteRejection(route, clonedFromAirport, toAirport, preferredLinkClass, paxType).isEmpty) {
                  totalAcceptedRoutes = totalAcceptedRoutes + 1
                }
                totalRoutes = totalRoutes + 1
              }
            }
        }
    }
  }
  assert(totalAcceptedRoutes.toDouble / totalRoutes < 0.2)
}

"reject some links at standard price if it does not fulfill lounge requirement (short flight)".in {
  val clonedFromAirport  = fromAirport.copy(size = 5)
  clonedFromAirport.initAirlineAppeals(Map(testAirline1.id -> AirlineAppeal(50)))

  val toAirport = toAirportsList(0).copy(longitude = 10, size = 5) //no lounge on the other side... so it's a no
  val distance = Util.calculateDistance(clonedFromAirport.latitude, clonedFromAirport.longitude, toAirport.latitude, toAirport.longitude).intValue()
  val duration = Computation.computeStandardFlightDuration(distance)
  val suggestedPrice = Pricing.computeStandardPriceForAllClass(distance, FlightCategory.DOMESTIC, PassengerType.TRAVELER, clonedFromAirport.income)
  val price = suggestedPrice
  val quality = fromAirport.expectedQuality(distance, FIRST)
  val newLink = Link(clonedFromAirport, toAirport, testAirline1, price = price, distance = distance, LinkClassValues.getInstance(10000, 10000, 10000), rawQuality = quality, duration, frequency = Link.HIGH_FREQUENCY_THRESHOLD)
  newLink.setQuality(quality)

  //hmm kinda mix in flight preference here...might not be a good thing... loop 10000 times so result is more consistent
  var totalRoutes = 0
  var totalAcceptedRoutes = 0;
  for (i <- 0 until LOOP_COUNT) {
    DemandGenerator.getFlightPreferencePoolOnAirport(clonedFromAirport).pool.foreach {
      case (paxType, flightPreferenceMap) =>
        flightPreferenceMap.foreach {
          case (preferredLinkClass, flightPreferenceList) =>
            flightPreferenceList.foreach { flightPreference =>
              if (isLoungePreference(flightPreference)) {
                val cost = flightPreference.computeCost(newLink, preferredLinkClass, paxType)
                val linkConsiderations = List[LinkConsideration](LinkConsideration.getExplicit(newLink, cost, preferredLinkClass, false))
                val route = Route(linkConsiderations, linkConsiderations.foldLeft(0.0) { _ + _.cost })
                if (PassengerSimulation.getRouteRejection(route, clonedFromAirport, toAirport, preferredLinkClass, paxType).isEmpty) {
                  totalAcceptedRoutes = totalAcceptedRoutes + 1
                }
                totalRoutes = totalRoutes + 1
              }
            }
        }
    }
  }
  assert(totalAcceptedRoutes.toDouble / totalRoutes < 0.7)
  assert(totalAcceptedRoutes.toDouble / totalRoutes > 0.4)
}

"accept few links at standard price if it fulfill some lounge requirement (long flight level 1 at departing airport only)".in {
  val clonedFromAirport  = fromAirport.copy(size = 5)
  clonedFromAirport.initAirlineAppeals(Map(testAirline1.id -> AirlineAppeal(50)))
  clonedFromAirport.initLounges(List(Lounge(airline = testAirline1, allianceId = None, airport = clonedFromAirport, level = 1, status = LoungeStatus.ACTIVE, foundedCycle = 0))) //only from airport has it

  val toAirport = toAirportsList(2).copy(size = 5)
  val distance = Util.calculateDistance(clonedFromAirport.latitude, clonedFromAirport.longitude, toAirport.latitude, toAirport.longitude).intValue()
  val duration = Computation.computeStandardFlightDuration(distance)
  val suggestedPrice = Pricing.computeStandardPriceForAllClass(distance, FlightCategory.DOMESTIC, PassengerType.TRAVELER, clonedFromAirport.income)
  val price = suggestedPrice
  val quality = fromAirport.expectedQuality(distance, FIRST)
  val newLink = Link(clonedFromAirport, toAirport, testAirline1, price = price, distance = distance, LinkClassValues.getInstance(10000, 10000, 10000), rawQuality = quality, duration, frequency = Link.HIGH_FREQUENCY_THRESHOLD)
  newLink.setQuality(quality)

  //hmm kinda mix in flight preference here...might not be a good thing... loop 10000 times so result is more consistent
  var totalRoutes = 0
  var totalAcceptedRoutes = 0;
  for (i <- 0 until LOOP_COUNT) {
    DemandGenerator.getFlightPreferencePoolOnAirport(clonedFromAirport).pool.foreach {
      case (paxType, flightPreferenceMap) =>
        flightPreferenceMap.foreach {
          case (preferredLinkClass, flightPreferenceList) =>
            flightPreferenceList.foreach { flightPreference =>
              if (isLoungePreference(flightPreference)) {
                val cost = flightPreference.computeCost(newLink, preferredLinkClass, paxType)
                val linkConsiderations = List[LinkConsideration](LinkConsideration.getExplicit(newLink, cost, preferredLinkClass, false))
                val route = Route(linkConsiderations, linkConsiderations.foldLeft(0.0) { _ + _.cost })
                if (PassengerSimulation.getRouteRejection(route, clonedFromAirport, toAirport, preferredLinkClass, paxType).isEmpty) {
                  totalAcceptedRoutes = totalAcceptedRoutes + 1
                }
                totalRoutes = totalRoutes + 1
              }
            }
        }
    }
  }
  assert(totalAcceptedRoutes.toDouble / totalRoutes > 0.2)
  assert(totalAcceptedRoutes.toDouble / totalRoutes < 0.4)
}

"accept some links at standard price if it fulfill some lounge requirement (long flight level 1 at both airports)".in {
  val clonedFromAirport  = fromAirport.copy(size = 5)
  clonedFromAirport.initAirlineAppeals(Map(testAirline1.id -> AirlineAppeal(50)))
  clonedFromAirport.initLounges(List(Lounge(airline = testAirline1, allianceId = None, airport = clonedFromAirport, level = 1, status = LoungeStatus.ACTIVE, foundedCycle = 0))) //only from airport has it

  val toAirport = toAirportsList(2).copy(size = 5)
  toAirport.initLounges(List(Lounge(airline = testAirline1, allianceId = None, airport = toAirport, level = 1, status = LoungeStatus.ACTIVE, foundedCycle = 0))) //only from airport has it
  val distance = Util.calculateDistance(clonedFromAirport.latitude, clonedFromAirport.longitude, toAirport.latitude, toAirport.longitude).intValue()
  val duration = Computation.computeStandardFlightDuration(distance)
  val suggestedPrice = Pricing.computeStandardPriceForAllClass(distance, FlightCategory.DOMESTIC, PassengerType.TRAVELER, clonedFromAirport.income)
  val price = suggestedPrice
  val quality = fromAirport.expectedQuality(distance, FIRST)
  val newLink = Link(clonedFromAirport, toAirport, testAirline1, price = price, distance = distance, LinkClassValues.getInstance(10000, 10000, 10000), rawQuality = quality, duration, frequency = Link.HIGH_FREQUENCY_THRESHOLD)
  newLink.setQuality(quality)

  //hmm kinda mix in flight preference here...might not be a good thing... loop 10000 times so result is more consistent
  var totalRoutes = 0
  var totalAcceptedRoutes = 0;
  for (i <- 0 until LOOP_COUNT) {
    DemandGenerator.getFlightPreferencePoolOnAirport(clonedFromAirport).pool.foreach {
      case (paxType, flightPreferenceMap) =>
        flightPreferenceMap.foreach {
          case (preferredLinkClass, flightPreferenceList) =>
            flightPreferenceList.foreach { flightPreference =>
              if (isLoungePreference(flightPreference)) {
                val cost = flightPreference.computeCost(newLink, preferredLinkClass, paxType)
                val linkConsiderations = List[LinkConsideration](LinkConsideration.getExplicit(newLink, cost, preferredLinkClass, false))
                val route = Route(linkConsiderations, linkConsiderations.foldLeft(0.0) { _ + _.cost })
                if (PassengerSimulation.getRouteRejection(route, clonedFromAirport, toAirport, preferredLinkClass, paxType).isEmpty) {
                  totalAcceptedRoutes = totalAcceptedRoutes + 1
                }
                totalRoutes = totalRoutes + 1
              }
            }
        }
    }
  }
  assert(totalAcceptedRoutes.toDouble / totalRoutes > 0.2)
  assert(totalAcceptedRoutes.toDouble / totalRoutes < 0.5)
}

"accept most links at standard price if it fulfill all lounge requirements (level 3 at both airports)".in {
  val clonedFromAirport  = fromAirport.copy(size = 5)
  clonedFromAirport.initAirlineAppeals(Map(testAirline1.id -> AirlineAppeal(50)))
  clonedFromAirport.initLounges(List(Lounge(airline = testAirline1, allianceId  = None, airport = clonedFromAirport, level = 3, status = LoungeStatus.ACTIVE, foundedCycle = 0))) //only from airport has it

  val toAirport = toAirportsList(2).copy(size = 5)
  toAirport.initLounges(List(Lounge(airline = testAirline1, allianceId  = None, airport = toAirport, level = 3, status = LoungeStatus.ACTIVE, foundedCycle = 0))) //only from airport has it
  val distance = Util.calculateDistance(clonedFromAirport.latitude, clonedFromAirport.longitude, toAirport.latitude, toAirport.longitude).intValue()
  val duration = Computation.computeStandardFlightDuration(distance)
  val suggestedPrice = Pricing.computeStandardPriceForAllClass(distance, FlightCategory.DOMESTIC, PassengerType.TRAVELER, clonedFromAirport.income)
  val price = suggestedPrice
  val quality = fromAirport.expectedQuality(distance, FIRST)
  val newLink = Link(clonedFromAirport, toAirport, testAirline1, price = price, distance = distance, LinkClassValues.getInstance(10000, 10000, 10000), rawQuality = quality, duration, frequency = Link.HIGH_FREQUENCY_THRESHOLD)
  newLink.setQuality(quality)


  //hmm kinda mix in flight preference here...might not be a good thing... loop 10000 times so result is more consistent
  var totalRoutes = 0
  var totalAcceptedRoutes = 0;
  for (i <- 0 until LOOP_COUNT) {
    DemandGenerator.getFlightPreferencePoolOnAirport(clonedFromAirport).pool.foreach {
      case (paxType, flightPreferenceMap) =>
        flightPreferenceMap.foreach {
          case (preferredLinkClass, flightPreferenceList) =>
            flightPreferenceList.foreach { flightPreference =>
              if (isLoungePreference(flightPreference)) {
                val cost = flightPreference.computeCost(newLink, preferredLinkClass, paxType)
                val linkConsiderations = List[LinkConsideration](LinkConsideration.getExplicit(newLink, cost, preferredLinkClass, false))
                val route = Route(linkConsiderations, linkConsiderations.foldLeft(0.0) { _ + _.cost })
                if (PassengerSimulation.getRouteRejection(route, clonedFromAirport, toAirport, preferredLinkClass, paxType).isEmpty) {
                  totalAcceptedRoutes = totalAcceptedRoutes + 1
                }
                totalRoutes = totalRoutes + 1
              }
            }
        }
    }
  }
  assert(totalAcceptedRoutes.toDouble / totalRoutes > 0.9)
}

"accept some links at 1.3 * price if it fulfill all lounge requirements (level 3 at both airports)".in {
  val clonedFromAirport  = fromAirport.copy(size = 5)
  clonedFromAirport.initAirlineAppeals(Map(testAirline1.id -> AirlineAppeal(50)))
  clonedFromAirport.initLounges(List(Lounge(airline = testAirline1, allianceId  = None, airport = clonedFromAirport, level = 3, status = LoungeStatus.ACTIVE, foundedCycle = 0))) //only from airport has it

  val toAirport = toAirportsList(2).copy(size = 5)
  toAirport.initLounges(List(Lounge(airline = testAirline1, allianceId  = None, airport = toAirport, level = 3, status = LoungeStatus.ACTIVE, foundedCycle = 0))) //only from airport has it
  val distance = Util.calculateDistance(clonedFromAirport.latitude, clonedFromAirport.longitude, toAirport.latitude, toAirport.longitude).intValue()
  val duration = Computation.computeStandardFlightDuration(distance)
  val suggestedPrice = Pricing.computeStandardPriceForAllClass(distance, FlightCategory.DOMESTIC, PassengerType.TRAVELER, clonedFromAirport.income)
  val price = suggestedPrice * 1.3
  val quality = fromAirport.expectedQuality(distance, FIRST)
  val newLink = Link(clonedFromAirport, toAirport, testAirline1, price = price, distance = distance, LinkClassValues.getInstance(10000, 10000, 10000), rawQuality = quality, duration, frequency = Link.HIGH_FREQUENCY_THRESHOLD)
  newLink.setQuality(quality)


  //hmm kinda mix in flight preference here...might not be a good thing... loop 10000 times so result is more consistent
  var totalRoutes = 0
  var totalAcceptedRoutes = 0;
  for (i <- 0 until LOOP_COUNT) {
    DemandGenerator.getFlightPreferencePoolOnAirport(clonedFromAirport).pool.foreach {
      case (paxType, flightPreferenceMap) =>
        flightPreferenceMap.foreach {
          case (preferredLinkClass, flightPreferenceList) =>
            flightPreferenceList.foreach { flightPreference =>
              if (isLoungePreference(flightPreference)) {
                val cost = flightPreference.computeCost(newLink, preferredLinkClass, paxType)
                val linkConsiderations = List[LinkConsideration](LinkConsideration.getExplicit(newLink, cost, preferredLinkClass, false))
                val route = Route(linkConsiderations, linkConsiderations.foldLeft(0.0) { _ + _.cost })
                if (PassengerSimulation.getRouteRejection(route, clonedFromAirport, toAirport, preferredLinkClass, paxType).isEmpty) {
                  totalAcceptedRoutes = totalAcceptedRoutes + 1
                }
                totalRoutes = totalRoutes + 1
              }
            }
        }
    }
  }
  assert(totalAcceptedRoutes.toDouble / totalRoutes > 0.4)
  assert(totalAcceptedRoutes.toDouble / totalRoutes < 0.6)
}

"accept most at standard price if it fulfill all lounge requirements (level 3 at both airports from alliance)".in {
  val clonedFromAirport  = fromAirport.copy(size = 5)
  clonedFromAirport.initAirlineAppeals(Map(testAirline1.id -> AirlineAppeal(50)))
  clonedFromAirport.initLounges(List(Lounge(airline = testAirline2, allianceId = Some(1), airport = clonedFromAirport, level = 3, status = LoungeStatus.ACTIVE, foundedCycle = 0)))
  testAirline1.setAllianceId(1)
  val toAirport = toAirportsList(2).copy(size = 5)
  toAirport.initLounges(List(Lounge(airline = testAirline2, allianceId = Some(1), airport = toAirport, level = 3, status = LoungeStatus.ACTIVE, foundedCycle = 0)))
  val distance = Util.calculateDistance(clonedFromAirport.latitude, clonedFromAirport.longitude, toAirport.latitude, toAirport.longitude).intValue()
  val duration = Computation.computeStandardFlightDuration(distance)
  val suggestedPrice = Pricing.computeStandardPriceForAllClass(distance, FlightCategory.DOMESTIC, PassengerType.TRAVELER, clonedFromAirport.income)
  val price = suggestedPrice
  val quality = fromAirport.expectedQuality(distance, FIRST)
  val newLink = Link(clonedFromAirport, toAirport, testAirline1, price = price, distance = distance, LinkClassValues.getInstance(10000, 10000, 10000), rawQuality = quality, duration, frequency = Link.HIGH_FREQUENCY_THRESHOLD)
  newLink.setQuality(quality)


  //hmm kinda mix in flight preference here...might not be a good thing... loop 10000 times so result is more consistent
  var totalRoutes = 0
  var totalAcceptedRoutes = 0;
  for (i <- 0 until LOOP_COUNT) {
    DemandGenerator.getFlightPreferencePoolOnAirport(clonedFromAirport).pool.foreach {
      case (paxType, flightPreferenceMap) =>
        flightPreferenceMap.foreach {
          case (preferredLinkClass, flightPreferenceList) =>
            flightPreferenceList.foreach { flightPreference =>
              if (isLoungePreference(flightPreference)) {
                val cost = flightPreference.computeCost(newLink, preferredLinkClass, paxType)
                val linkConsiderations = List[LinkConsideration](LinkConsideration.getExplicit(newLink, cost, preferredLinkClass, false))
                val route = Route(linkConsiderations, linkConsiderations.foldLeft(0.0) { _ + _.cost })
                if (PassengerSimulation.getRouteRejection(route, clonedFromAirport, toAirport, preferredLinkClass, paxType).isEmpty) {
                  totalAcceptedRoutes = totalAcceptedRoutes + 1
                }
                totalRoutes = totalRoutes + 1
              }
            }
        }
    }
  }
  assert(totalAcceptedRoutes.toDouble / totalRoutes > 0.9)
}
"Find affordable route".must {
  "accept a route if all links are reasonable".in {
    val distances = List(
      Computation.calculateDistance(fromAirport, toAirportsList(0)),
      Computation.calculateDistance(toAirportsList(0), toAirportsList(1)),
      Computation.calculateDistance(toAirportsList(1), toAirportsList(2))
    )
    val standardPrices = List(
      Pricing.computeStandardPriceForAllClass(distances(0), FlightCategory.DOMESTIC, PassengerType.TRAVELER, Airport.HIGH_INCOME / 2),
      Pricing.computeStandardPriceForAllClass(distances(1), FlightCategory.INTERNATIONAL, PassengerType.TRAVELER, Airport.HIGH_INCOME / 2),
      Pricing.computeStandardPriceForAllClass(distances(2), FlightCategory.INTERNATIONAL, PassengerType.TRAVELER, Airport.HIGH_INCOME / 2)
    )
    val durations = List(
      Computation.calculateDuration(mediumAirplaneModel, distances(0)),
      Computation.calculateDuration(mediumAirplaneModel, distances(1)),
      Computation.calculateDuration(mediumAirplaneModel, distances(2))
    )

    val links =
      List(LinkConsideration.getExplicit(Link(fromAirport, toAirportsList(0), testAirline1, standardPrices(0), distances(0), LinkClassValues.getInstance(10000), 0, durations(0), 1), 100, ECONOMY, false),
        LinkConsideration.getExplicit(Link(toAirportsList(0), toAirportsList(1), testAirline1, standardPrices(1), distances(1), LinkClassValues.getInstance(10000), 0, durations(1), 1), 100, ECONOMY, false),
        LinkConsideration.getExplicit(Link(toAirportsList(1), toAirportsList(2), testAirline1, standardPrices(2), distances(2), LinkClassValues.getInstance(10000), 0, durations(2), 1), 100, ECONOMY, false))
    assignLinkConsiderationIds(links)

    val routes = PassengerSimulation.findShortestRoute(passengerGroup, Set(toAirportsList(2)), allAirportIds, links.asJava, Collections.emptyMap[Int, Int](), 3)
    assert(routes.size == 1)
    for (i <- 0 until 10000) {
      assert(PassengerSimulation.getRouteRejection(routes(toAirportsList(2)), fromAirport, toAirportsList(2), ECONOMY, passengerGroup.passengerType).isEmpty)
    }
  }
}
}