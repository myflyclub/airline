package controllers

import com.patson.{AirportSimulation, DemandGenerator, LinkSimulation}
import com.patson.data._
import com.patson.model.Scheduling.{TimeSlot, TimeSlotStatus}
import com.patson.model.airplane.{Airplane, AirplaneConfiguration, Model, ModelDiscount}
import com.patson.model.{Link, _}
import com.patson.model.event.Olympics
import com.patson.util.{AirlineCache, AirportCache, ChampionUtil}
import controllers.AuthenticationObject.AuthenticatedAirline
import play.api.libs.json.{Json, _}
import play.api.mvc._

import javax.inject.Inject
import scala.collection.mutable
import scala.collection.mutable.{ListBuffer, Set}
import scala.math.BigDecimal.RoundingMode


class Application @Inject()(cc: ControllerComponents, val configuration: play.api.Configuration) extends AbstractController(cc) {

  implicit object AirportShareWrites extends Writes[(Airport, Double)] {
    def writes(airportShare: (Airport, Double)): JsValue = {
      JsObject(List(
        "airportName" -> JsString(airportShare._1.name),
        "airportId" -> JsNumber(airportShare._1.id),
        "share" -> JsNumber(BigDecimal(airportShare._2).setScale(4, BigDecimal.RoundingMode.HALF_EVEN))))
    }
  }

  implicit object AirportPassengersWrites extends Writes[(Airport, Int)] {
    def writes(airportPassenger: (Airport, Int)): JsValue = {
      JsObject(List(
        "airportName" -> JsString(airportPassenger._1.name),
        "airportId" -> JsNumber(airportPassenger._1.id),
        "passengers" -> JsNumber(airportPassenger._2)))
    }
  }

  implicit object AirlinePassengersWrites extends Writes[(Airline, Int)] {
    def writes(airlinePassenger: (Airline, Int)): JsValue = {
      JsObject(List(
        "airlineName" -> JsString(airlinePassenger._1.name),
        "airlineId" -> JsNumber(airlinePassenger._1.id),
        "passengers" -> JsNumber(airlinePassenger._2)))
    }
  }

  implicit object TimeSlotAssignmentWrites extends Writes[(TimeSlot, Link, TimeSlotStatus)] {
    def writes(timeSlotAssignment: (TimeSlot, Link, TimeSlotStatus)): JsValue = {
      val link = timeSlotAssignment._2
      JsObject(List(
        "timeSlotDay" -> JsNumber(timeSlotAssignment._1.dayOfWeek),
        "timeSlotTime" -> JsString("%02d".format(timeSlotAssignment._1.hour) + ":" + "%02d".format(timeSlotAssignment._1.minute)),
        "airline" -> JsString(link.airline.name),
        "airlineId" -> JsNumber(link.airline.id),
        "flightCode" -> JsString(LinkUtil.getFlightCode(link.airline, link.flightNumber)),
        "destination" -> JsString(if (link.to.city.nonEmpty) {
          link.to.city
        } else {
          link.to.name
        }),
        "statusCode" -> JsString(timeSlotAssignment._3.code),
        "statusText" -> JsString(timeSlotAssignment._3.text)
      ))
    }
  }


  implicit object LoyalistWrites extends Writes[Loyalist] {
    def writes(loyalist: Loyalist): JsValue = {
      Json.obj(
        "airportId" -> loyalist.airport.id,
        "airlineId" -> loyalist.airline.id,
        "airlineName" -> loyalist.airline.name,
        "amount" -> loyalist.amount
      )
    }
  }

  implicit object LoyalistHistoryWrites extends Writes[LoyalistHistory] {
    def writes(entry: LoyalistHistory): JsValue = {
      Json.toJson(entry.entry).asInstanceOf[JsObject] + ("cycle" -> JsNumber(entry.cycle))
    }
  }

  // path parameter is captured but ignored - page.js handles routing
  def index(path: String = "") = Action { implicit request =>
    implicit lazy val config = configuration
    Ok(views.html.index())
  }
  
  def getCurrentCycle() = Action { request =>
    request.headers.get(IF_NONE_MATCH) match {
      case Some(etag) if etag == s""""$currentCycle"""" =>
        NotModified
      case _ =>
        Ok(Json.obj("cycle" -> currentCycle))
          .withHeaders(
            ETAG -> s""""$currentCycle""""
          )
    }
  }

  /**
   * Static airport data
   */
  def getAirportsStatic(version: String) = Action {
    Ok(Json.toJson(AirportCache.getAllAirports())(AirportsGeoJsonWrites))
      .withHeaders(
        CACHE_CONTROL -> "public, max-age=2419200",
        ETAG -> s""""$currentApiVersion"""", // Use version as ETag
        EXPIRES -> java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME
          .format(java.time.ZonedDateTime.now().plusWeeks(2))
      )
  }

  /**
   * Dynamic airport data
   * - boostFactorsByType: base specialization
   * - loyalist data
   * - linkCount
   *
   */
  @volatile private var airportsJsonCycle: Int = -1
  @volatile private var airportsJson: JsValue = Json.obj()

  private def buildAirportsJson(airportData: List[models.AirportWithChampionAndStats]): JsValue = {
    val championsObject = JsObject(
      airportData.filter(_.champion.isDefined).map { a =>
        var obj = Json.toJson(a)(AirportWithChampionWrites).asInstanceOf[JsObject] +
          ("travelRate" -> JsNumber(a.travelRate)) +
          ("reputation" -> JsNumber(a.reputation))
        a.congestion.foreach { c => obj = obj + ("congestion" -> JsNumber(c)) }
        a.airport.id.toString -> obj
      }.toMap
    )
    val boostsObject = JsObject(
      airportData.filter { a =>
        AirportBoostType.values.exists(a.airport.boostFactorsByType.get(_).nonEmpty)
      }.map { a =>
        a.airport.id.toString -> Json.toJson(a)(AirportBoostOnlyWrites)
      }.toMap
    )
    val dynamicFeaturesObject = JsObject(
      airportData.flatMap { a =>
        val dynFeatures = a.airport.getFeatures().filter(_.isDynamic)
        if (dynFeatures.nonEmpty) {
          Some(a.airport.id.toString -> JsArray(dynFeatures.sortBy(_.featureType.id).map { f =>
            Json.obj("type" -> f.featureType.toString, "strength" -> f.strength, "title" -> f.getDescription)
          }))
        } else None
      }.toMap
    )
    Json.obj("champions" -> championsObject, "boosts" -> boostsObject, "dynamicFeatures" -> dynamicFeaturesObject)
  }

  def getAirports() = Action { request =>
    request.headers.get(IF_NONE_MATCH) match {
      case Some(etag) if etag == s""""$currentCycle"""" =>
        NotModified
      case _ =>
        if (airportsJsonCycle != currentCycle) {
          airportsJson = buildAirportsJson(AirportUtil.cachedAirportChampions)
          airportsJsonCycle = currentCycle
        }
        Ok(airportsJson)
          .withHeaders(
            CACHE_CONTROL -> "no-cache",
            ETAG -> s""""$currentCycle""""
          )
    }
  }

  def getAirportImages(airportId: Int) = Action {
    AirportCache.getAirport(airportId, false) match {
      case Some(airport) =>
        val cityImage = GoogleImageUtil.getCityImage(airport)
        val cityImageUrl: String = if (cityImage != null && cityImage.url != null) cityImage.url.toString else ""
        val cityImageCaption: String = if (cityImage != null && cityImage.placeName != null) cityImage.placeName else ""
        val airportImage = GoogleImageUtil.getAirportImage(airport)
        val airportImageUrl: String = if (airportImage != null && airportImage.url != null) airportImage.url.toString else ""
        val airportImageCaption: String = if (airportImage != null && airportImage.placeName != null) airportImage.placeName else ""

        Ok(Json.obj(
          "cityImageUrl" -> JsString(cityImageUrl),
          "cityImageCaption" -> JsString(cityImageCaption),
          "airportImageUrl" -> JsString(airportImageUrl),
          "airportImageCaption" -> JsString(airportImageCaption),
        ))
          .withHeaders(
            CACHE_CONTROL -> "public, max-age=2419200",
            ETAG -> s""""$currentApiVersion"""",
            EXPIRES -> java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME
              .format(java.time.ZonedDateTime.now().plusWeeks(8))
          )
      case None => NotFound
    }
  }

  /**
   * Extended static airport details; can be cached forever
   *
   * @param airportId
   * @return
   */
  def getAirportDetailStatic(airportId: Int) = Action {
    AirportCache.getAirport(airportId) match {
      case Some(airport) =>
        val destinations = DestinationSource.loadDestinationsByAirport(airportId)
        val cities = AirportSource.loadCitiesServed(airportId)
        val runways: List[Runway] = AirportSource.loadAirportRunways(airportId)
        val dummyLounge: Lounge = Lounge(Airline("airline 1"), None, airport, "", 1, LoungeStatus.INACTIVE, 0)
        val tooltip_lounge = List(
          s"To get lounge approval, your alliance must be in the top ${dummyLounge.getActiveRankingThreshold} by premium passenger count and you must have the most premium passengers in your alliance.",
          "Premium passengers will pay higher ticket prices if you have a lounge",
          "Lounges can be upgraded to level 5; base levels 2, 4, 6, 8, and 10 required"
        )

        Ok(
          Json.toJson(airport)(AirportSimpleWrites).asInstanceOf[JsObject] +
            ("icao" -> Json.toJson(airport.icao)) +
            ("destinations" -> Json.toJson(destinations)) +
            ("citiesServed" -> Json.toJson(cities.map { case (city, cityShare) =>
              Json.toJson(city).as[JsObject] ++ Json.obj("cityShare" -> cityShare)
            })) +
            ("runways" -> JsArray(runways.sortBy(_.length).reverse.map { runway: Runway =>
              Json.obj("type" -> runway.runwayType.toString(), "length" -> runway.length, "code" -> runway.code)
            })) +
            ("tooltipLounge" -> JsArray(tooltip_lounge.map(JsString(_))))
        )
          .withHeaders(
            CACHE_CONTROL -> "public, max-age=2419200",
            ETAG -> s""""$currentApiVersion"""",
            EXPIRES -> java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME
              .format(java.time.ZonedDateTime.now().plusWeeks(8))
          )
      case None => NotFound
    }
  }

  /**
   * Extended airport details for all / any airline; dynamically updates after each cycle
   *
   * @param airportId
   * @return
   */
  def getAirportDetail(airportId: Int) = Action { request =>
    request.headers.get(IF_NONE_MATCH) match {
      case Some(etag) if etag == s""""$currentCycle"""" =>
        NotModified
      case _ =>
        val json: Option[JsValue] = Option(ResponseCache.airportDetailCache.getIfPresent(airportId)).filter(_._1 == currentCycle).map(_._2).orElse {
          computeAirportDetailJson(airportId).map { j => ResponseCache.airportDetailCache.put(airportId, (currentCycle, j)); j }
        }
        json match {
          case Some(j) => Ok(j).withHeaders(CACHE_CONTROL -> "no-cache", ETAG -> s""""$currentCycle"""")
          case None => NotFound
        }
    }
  }

  private def computeAirportDetailJson(airportId: Int): Option[JsValue] = {
    AirportCache.getAirport(airportId, fullLoad = true) match {
      case Some(airport) =>
        //group things up, filtering out local connects (id 0)
        val flightsFromThisAirport = LinkStatisticsSource.loadLinkStatisticsByFromAirport(airportId, LinkStatisticsSource.SIMPLE_LOAD)
        val flightsToThisAirport = LinkStatisticsSource.loadLinkStatisticsByToAirport(airportId, LinkStatisticsSource.SIMPLE_LOAD)
        val connectionPaxGroups = flightsFromThisAirport.filter(_.key.airline.id != 0).filterNot {
          _.key.isDeparture
        } ++ flightsToThisAirport.filter(_.key.airline.id != 0).filterNot {
          _.key.isDestination
        } //airline id == 0 is local connection
        val originPaxGroups = flightsFromThisAirport.filter(_.key.isDeparture).filter(_.key.airline.id != 0)
        val destinationPaxGroups = flightsToThisAirport.filter(_.key.isDestination).filter(_.key.airline.id != 0)

        val departurePassengers = originPaxGroups.map(_.passengers).sum
        val destinationPassengers = destinationPaxGroups.map(_.passengers).sum
        val transitPassengers = connectionPaxGroups.map {
          _.passengers
        }.sum

        val statisticsTotalByAirline: List[(Airline, Int)] = (originPaxGroups ++ destinationPaxGroups ++ connectionPaxGroups).groupBy(_.key.airline).foldRight(List[(Airline, Int)]()) {
          case ((airline, statistics), foldList) =>
            val totalPassengersOfThisAirline = statistics.foldLeft(0)(_ + _.passengers) //all the passengers of this airline
            (airline, totalPassengersOfThisAirline) :: foldList
        }.sortBy(_._2).reverse

        val statisticsOriginPaxByAirline: List[(Airline, Int)] = originPaxGroups.groupBy(_.key.airline).foldRight(List[(Airline, Int)]()) {
          case ((airline, statistics), foldList) =>
            val totalPassengersOfThisAirline = statistics.foldLeft(0)(_ + _.passengers) //all the passengers of this airline, if pax are origin
            (airline, totalPassengersOfThisAirline) :: foldList
        }.sortBy(_._2).reverse

        //        val statisticsDestinationPaxByAirline : List[(Airline, Int)] = destinationPaxGroups.groupBy(_.key.airline).foldRight(List[(Airline, Int)]()) {
        //          case ((airline, statistics), foldList) =>
        //            val totalPassengersOfThisAirline = statistics.foldLeft(0)( _ + _.passengers) //all the passengers of this airline, if pax are origin
        //            (airline, totalPassengersOfThisAirline) :: foldList
        //        }.sortBy(_._2).reverse

        val statisticsPremiumPaxByAirline: List[(Airline, Int)] = (originPaxGroups ++ destinationPaxGroups ++ connectionPaxGroups).groupBy(_.key.airline).foldRight(List[(Airline, Int)]()) {
          case ((airline, statistics), foldList) =>
            val totalPassengersOfThisAirline = statistics.foldLeft(0)(_ + _.premiumPax)
            (airline, totalPassengersOfThisAirline) :: foldList
        }.sortBy(_._2).reverse

        val localPaxByAirport: Map[Airport, Int] =
          flightsFromThisAirport.filter(_.key.airline.id == 0).groupBy(_.key.toAirport).view.mapValues(_.map(_.passengers).sum).toMap ++
            flightsToThisAirport.filter(_.key.airline.id == 0).groupBy(_.key.fromAirport).view.mapValues(_.map(_.passengers).sum).toMap

        val linksFrom = LinkSource.loadFlightLinksByFromAirport(airportId)
        val links = linksFrom ++ LinkSource.loadFlightLinksByToAirport(airportId)

        val airlineStats = links.filter { link =>
          linksFrom.map(_.airline.id).contains(link.airline.id) //only need airlines with from links
        }.groupBy(_.airline.id).map { case (airlineId, airlineLinks) =>
          val linkCount = airlineLinks.size
          val avgDistance = (airlineLinks.map { link =>
            link.distance * link.frequency
          }.sum.toDouble / airlineLinks.map(_.frequency).sum).toInt
          val avgFrequency = (airlineLinks.map(_.frequency).sum.toDouble / airlineLinks.size).toInt
          val airlineSlogan = airlineLinks.head.airline.slogan.getOrElse("")
          val airlineType = airlineLinks.head.airline.airlineType.label

          (airlineId, (airlineType, airlineSlogan, avgDistance, avgFrequency, linkCount))
        }

        val aircraftStats = links.flatMap(link =>
          link.getAssignedModel().map(model => model.name -> link.capacity.total)
        ).groupBy(_._1).mapValues(_.map(_._2).sum).toList.sortBy(_._1).toMap

        val flightFrequency = links.map(_.frequency).sum
        val linkAvgDistance = (links.map(_.distance).sum.toDouble / links.size).toInt
        val linksCapacity = links.map(_.capacity.total).sum
        val linksLF = ((departurePassengers + destinationPassengers + connectionPaxGroups.map(_.passengers).sum).toDouble / links.map(_.capacity.total).sum * 100).toInt
        val linksIntl = (links.filter { link =>
          link.to.countryCode != link.from.countryCode
        }.map(_.capacity.total).sum.toDouble / linksCapacity * 100).toInt

        val servedCountries = mutable.Set[String]()
        val servedAirports = mutable.Set[Airport]()
        val airlineCount = mutable.Set[Int]()
        links.foreach { link =>
          servedCountries.add(link.from.countryCode)
          servedCountries.add(link.to.countryCode)
          airlineCount.add(link.airline.id)
          if (link.from.id != airportId) {
            servedAirports.add(link.from)
          } else {
            servedAirports.add(link.to)
          }
        }

        val loungesStats = LoungeHistorySource.loadLoungeConsumptionsByAirportId(airport.id)
        val loungesWithVisitors = loungesStats.map {
          _.lounge.airline.id
        }
        val emptyLoungesStats = ListBuffer[LoungeConsumptionDetails]()

        AirlineSource.loadLoungesByAirportId(airportId).foreach { lounge =>
          if (!loungesWithVisitors.contains(lounge.airline.id)) {
            emptyLoungesStats += LoungeConsumptionDetails(lounge = lounge, selfVisitors = 0, allianceVisitors = 0, cycle = 0)
          }
        }

        Some(
          Json.toJson(airport)(AirportExtendedWrites).asInstanceOf[JsObject] ++
            Json.obj(
              "connectedCountryCount" -> servedCountries.size,
              "connectedAirportCount" -> (servedAirports.size), //do not count itself
              "airlineCount" -> airlineCount.size,
              "linkCount" -> links.size,
              "linkAvgDistance" -> linkAvgDistance,
              "linkCountByAirline" -> airlineStats.foldLeft(Json.arr()) {
                case (jsonArray, (airlineId, (airlineType, airlineSlogan, avgDistance, avgFrequency, linkCount))) => jsonArray :+ Json.obj("airlineId" -> JsNumber(airlineId), "linkCount" -> JsNumber(linkCount), "avgDistance" -> JsNumber(avgDistance), "avgFrequency" -> JsNumber(avgFrequency), "airlineType" -> JsString(airlineType.toString), "airlineSlogan" -> JsString(airlineSlogan.toString))
              },
              "flightFrequency" -> flightFrequency,
              "bases" -> Json.toJson(airport.getAirlineBases().values),
              "lounges" -> Json.toJson(loungesStats ++ emptyLoungesStats),
              "departurePassengers" -> departurePassengers,
              "destinationPassengers" -> destinationPassengers,
              "transitPassengers" -> transitPassengers,
              "localPaxByAirport" -> Json.toJson(localPaxByAirport),
              "airlinePax" -> statisticsTotalByAirline,
              "airlinePremiumPax" -> statisticsPremiumPaxByAirline,
              "airlineOrigin" -> statisticsOriginPaxByAirline,
              //          "airlineDestination" -> statisticsDestinationPaxByAirline,
              "aircraftStats" -> aircraftStats,
              "totalSeats" -> Json.toJson(linksCapacity),
              "linksLF" -> Json.toJson(linksLF),
              "linksIntl" -> Json.toJson(linksIntl)
            )
        )
        case None => None
    }
  }

  def getAirportLinkConsumptions(fromAirportId: Int, toAirportId: Int) = Action { request =>
    request.headers.get(IF_NONE_MATCH) match {
      case Some(etag) if etag == s""""$currentCycle"""" =>
        NotModified
      case _ =>
        val competitorLinkConsumptions = (LinkSource.loadFlightLinksByAirports(fromAirportId, toAirportId, LinkSource.ID_LOAD) ++ LinkSource.loadFlightLinksByAirports(toAirportId, fromAirportId, LinkSource.ID_LOAD)).flatMap { link =>
          LinkSource.loadLinkConsumptionsByLinkId(link.id, 1)
        }
        Ok(Json.toJson(competitorLinkConsumptions.filter(_.link.capacity.total > 0).map { linkConsumption => Json.toJson(linkConsumption)(SimpleLinkConsumptionWrite) }.toSeq)).withHeaders(
          CACHE_CONTROL -> "no-cache",
          ETAG -> s""""$currentCycle""""
        )
    }
  }

  def getLinksByAirport(airportId: Int) = Action {
    val linksByToAirport = LinkSource.loadFlightLinksByFromAirport(airportId).groupBy(_.to)
    val linksByFromAirport = LinkSource.loadFlightLinksByToAirport(airportId).groupBy(_.from)

    val linksByOtherAirport: Map[Airport, List[Link]] = linksByToAirport ++ linksByFromAirport.map {
      case (airport, links) => (airport, links ++ linksByToAirport.getOrElse(airport, List.empty[Link]))
    }

    Ok(Json.toJson(linksByOtherAirport.toList.sortBy(_._2.map(_.futureCapacity().total).sum).reverse.map {
      case (otherAirport, links) =>
        Json.obj(
          "remoteAirport" -> Json.toJson(otherAirport)(AirportMapWrites),
          "capacity" -> links.map(_.futureCapacity()).foldLeft(LinkClassValues.getInstance())((x, y) => x + y),
          "frequency" -> links.map(_.futureFrequency()).sum,
          "operators" -> Json.toJson(links.sortBy(_.futureCapacity().total).reverse.map { link =>
            Json.obj(
              "airlineId" -> link.airline.id,
              "airlineName" -> link.airline.name,
              "capacity" -> link.futureCapacity(),
              "frequency" -> link.frequency)
          }))
    }))
      .withHeaders(
        CACHE_CONTROL -> "no-cache",
        ETAG -> s""""$currentCycle""""
      )
  }

  val MAX_LOYALIST_HISTORY_AIRLINE = 5

  def getAirportLoyalistData(airportId: Int, airlineIdOption: Option[Int]) = Action { request =>
    request.headers.get(IF_NONE_MATCH) match {
      case Some(etag) if etag == s""""$currentCycle"""" =>
        NotModified
      case _ =>
        val currentLoyalistEntries = LoyalistSource.loadLoyalistsByAirportId(airportId)
        val currentLoyalistByAirlineId = currentLoyalistEntries.map(entry => (entry.airline.id, entry)).toMap
        val historyEntries = LoyalistSource.loadLoyalistsHistoryByAirportId(airportId)
        val airlineDeltas = ListBuffer[(Airline, Int)]()
        val airport = AirportCache.getAirport(airportId).get
        val currentCycle = CycleSource.loadCycle()
        var result =
          historyEntries.toList.sortBy(_._1).lastOption match {
            case Some((lastCycle, lastEntry)) =>
              val topAirlineIds = lastEntry.sortBy(_.entry.amount).takeRight(MAX_LOYALIST_HISTORY_AIRLINE).map(_.entry.airline.id).toSet
              val reportingAirlineIds: List[Int] = airlineIdOption match {
                case Some(airlineId) => (topAirlineIds + airlineId).toList
                case None => topAirlineIds.toList
              }

              val referenceCycle = AirportSimulation.getHistoryCycle(currentCycle, -2) //get something further in the past for more stable number
              val processedEntries: List[(Int, List[LoyalistHistory])] = historyEntries.toList.sortBy(_._1).map {
                case ((cycle, entries)) =>
                  val entriesByAirlineId = entries.map(entry => (entry.entry.airline.id, entry)).toMap
                  val paddedEntries = reportingAirlineIds.map { reportingAirlineId =>
                    entriesByAirlineId.getOrElse(reportingAirlineId, LoyalistHistory(Loyalist(airport, AirlineCache.getAirline(reportingAirlineId).get, 0), cycle)) //pad with zero entries
                  }


                  if (cycle == referenceCycle) {
                    val cycleDelta = currentCycle - cycle
                    reportingAirlineIds.foreach { reportingAirlineId =>
                      val previousLoyalistCount = entriesByAirlineId.get(reportingAirlineId).map(_.entry.amount).getOrElse(0)
                      airlineDeltas.append((AirlineCache.getAirline(reportingAirlineId).get, (currentLoyalistByAirlineId.get(reportingAirlineId).map(_.amount).getOrElse(0) - previousLoyalistCount) / cycleDelta))
                    }
                  }

                  (cycle, paddedEntries)
              }

              Json.obj("current" -> currentLoyalistEntries, "history" -> processedEntries, "airlineDeltas" -> airlineDeltas.toList.sortBy(_._2)(Ordering[Int].reverse))
            case None =>
              Json.obj("current" -> currentLoyalistEntries)
          }

        Ok(result).withHeaders(
          CACHE_CONTROL -> "no-cache",
          ETAG -> s""""$currentCycle""""
        )
    }
  }

  /**
   * game tooltips
   * tooltips written next to game logic to ensure consistency
   */
  def getTooltips(version: String) = Action {

    val tooltips = Json.obj(
      "satisfaction" -> JsArray(Computation.TOOLTIP_SATISFACTION.map(JsString(_))),
      "olympics" -> JsArray(Olympics.TOOLTIP.map(JsString(_))),
      "delays" -> JsArray(LinkSimulation.TOOLTIP_DELAYS.map(JsString(_))),
      "congestion" -> JsArray(Airport.TOOLTIP_CONGESTION.map(JsString(_))),
      "stock_eps" -> JsArray(StockModel.TOOLTIP_STOCK_EPS.map(JsString(_))),
      "stock_pask" -> JsArray(StockModel.TOOLTIP_STOCK_PASK.map(JsString(_))),
      "stock_dividends" -> JsArray(StockModel.TOOLTIP_STOCK_DIVIDENDS.map(JsString(_))),
      "ledger_bankruptcy" -> JsArray(GameConstants.TOOLTIP_BANKRUPTCY.map(JsString(_))),
      "manager" -> JsArray(Manager.TOOLTIP.map(JsString(_))),
      "model_discount" -> JsArray(ModelDiscount.TOOLTIP.map(JsString(_))),
    )

    Ok(tooltips)
      .withHeaders(
        CACHE_CONTROL -> "public, max-age=2419200",
        ETAG -> s""""$currentApiVersion"""", // Use version as ETag
        EXPIRES -> java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME
          .format(java.time.ZonedDateTime.now().plusWeeks(4))
      )
  }

  /**
   * Static game constants / rules
   */
  def getGameRules(version: String) = Action {
    var scaleProgressionResult = Json.arr()
    (1 to 15).foreach { scale =>
      var perScaleResult = Json.obj("scale" -> scale)
      var maxFrequencyJson = Json.obj()
      var maxFrequencyDomesticJson = Json.obj()
      FlightCategory.values.foreach { group =>
        maxFrequencyJson = maxFrequencyJson + (group.toString -> JsNumber(NegotiationUtil.getMaxFrequencyByGroup(scale, group, false)))
        maxFrequencyDomesticJson = maxFrequencyDomesticJson + (group.toString -> JsNumber(NegotiationUtil.getMaxFrequencyByGroup(scale, group, true)))
      }
      perScaleResult = perScaleResult +
        ("maxFrequency" -> maxFrequencyJson) +
        ("maxFrequencyDomestic" -> maxFrequencyDomesticJson) +
        ("baseStaffCapacity" -> JsNumber(AirlineBase.getOfficeStaffCapacity(scale, isHeadquarters = false))) +
        ("headquartersStaffCapacity" -> JsNumber(AirlineBase.getOfficeStaffCapacity(scale, isHeadquarters = true)))
      scaleProgressionResult = scaleProgressionResult.append(perScaleResult)
    }

    val airportFees = Json.obj(
      "airplaneType" -> Airport.SLOT_FEES_AIRPLANE_SIZE.map { case (modelType, fee) => modelType.label.toString -> JsNumber(fee) },
      "airportSize" -> JsArray(Airport.SLOT_FEES_AIRPORT_SIZE.toList.sortBy(_._1).map { case (size, fee) => JsNumber(fee) })
    )

    val linkClasses: List[LinkClass] = List(DISCOUNT_ECONOMY, ECONOMY, BUSINESS, FIRST)
    implicit val linkClassWrites: Writes[LinkClass] = new Writes[LinkClass] {
      def writes(linkClass: LinkClass): JsValue = {
        Json.obj(
          "name" -> linkClass.label,
          "spaceMultiplier" -> linkClass.spaceMultiplier,
          "resourceMultiplier" -> linkClass.resourceMultiplier
        )
      }
    }
    val linkClassJson = Json.toJson(linkClasses)

    val linkPrice = Json.obj(
      "highIncomeRatioForBoost" -> JsNumber(DemandGenerator.HIGH_INCOME_RATIO_FOR_BOOST),
      "priceDiscountPlusMultiplier" -> JsNumber(DemandGenerator.PRICE_DISCOUNT_PLUS_MULTIPLIER),
      "priceLastMinMultiplier" -> JsNumber(DemandGenerator.PRICE_LAST_MIN_MULTIPLIER),
      "priceLastMinDealMultiplier" -> JsNumber(DemandGenerator.PRICE_LAST_MIN_DEAL_MULTIPLIER),
    )

    val aircraft = Json.obj(
      "maxFlightMin" -> JsNumber(Airplane.MAX_FLIGHT_MINUTES),
      "conditionBad" -> JsNumber(Airplane.BAD_CONDITION),
      "conditionCritical" -> JsNumber(Airplane.CRITICAL_CONDITION),
      "minSeatsPerClass" -> JsNumber(AirplaneConfiguration.MIN_SEATS_PER_CLASS),
      "timeToCruise" -> Json.obj(
        "Small Prop" -> JsNumber(Model.TIME_TO_CRUISE_PROPELLER_SMALL),
        "Large Prop" -> JsNumber(Model.TIME_TO_CRUISE_PROPELLER_MEDIUM),
        "Small Jet" -> JsNumber(Model.TIME_TO_CRUISE_SMALL),
        "Regional Jet" -> JsNumber(Model.TIME_TO_CRUISE_REGIONAL),
        "Narrow-body" -> JsNumber(Model.TIME_TO_CRUISE_MEDIUM),
        "Narrow-body XL" -> JsNumber(Model.TIME_TO_CRUISE_MEDIUM),
        "Helicopter" -> JsNumber(Model.TIME_TO_CRUISE_HELICOPTER),
        "Airship" -> JsNumber(Model.TIME_TO_CRUISE_HELICOPTER),
        "Other" -> JsNumber(Model.TIME_TO_CRUISE_OTHER),
      )
    )

    val linkCosts = Json.obj(
      "fuelCost" -> JsNumber(LinkSimulation.FUEL_UNIT_COST),
      "fuelDistanceExponent" -> JsNumber(LinkSimulation.FUEL_DISTANCE_EXPONENT),
      "fuelEmptyAircraftBurnPercent" -> JsNumber(LinkSimulation.FUEL_EMPTY_AIRCRAFT_BURN_PERCENT),
      "crewUnitCost" -> JsNumber(LinkSimulation.CREW_UNIT_COST),
      "crewBaseCost" -> JsNumber(LinkSimulation.CREW_BASE_COST),
      "crewEQExponent" -> JsNumber(LinkSimulation.CREW_EQ_EXPONENT),
    )

    val milestonesJson = JsObject(
      AirlineMilestones.milestonesByAirlineType.map { case (airlineType, milestones) =>
        airlineType.label -> JsArray(
          milestones.map { milestone =>
            Json.obj(
              "name" -> milestone.name,
              "description" -> milestone.description,
              "conditions" -> milestone.conditions.map { cond =>
                Json.obj(
                  "threshold" -> cond.threshold,
                  "reward" -> cond.reward
                )
              }
            )
          }
        )
      }
    )

    val stockModel = JsObject(StockModel.allMetrics.map { case (key, m) => key -> metricJson(m) })
    val stockConsts = Json.obj(
      "brokerFee" -> JsNumber(StockModel.STOCK_BROKER_FEE),
      "brokerFeeBase" -> JsNumber(StockModel.STOCK_BROKER_FEE_BASE),
      "minChange" -> JsNumber(StockModel.STOCK_BUYBACK_MIN_CHANGE),
      "maxChange" -> JsNumber(StockModel.STOCK_BUYBACK_MAX_CHANGE)
    )

    val result = Json.obj(
      "baseScaleProgression" -> scaleProgressionResult,
      "linkPrice" -> linkPrice,
      "linkClassValues" -> linkClassJson,
      "aircraft" -> aircraft,
      "airportFees" -> airportFees,
      "linkCosts" -> linkCosts,
      "milestones" -> milestonesJson,
      "stockMetrics" -> stockModel,
      "stockConsts" -> stockConsts
    )

    Ok(result)
      .withHeaders(
        CACHE_CONTROL -> "public, max-age=2419200",
        ETAG -> s""""$currentApiVersion"""", // Use version as ETag
        EXPIRES -> java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME
          .format(java.time.ZonedDateTime.now().plusWeeks(4))
      )
  }

  private def metricJson(m: StockMetric): JsObject =
    Json.obj("value" -> m.value, "floor" -> m.floor, "target" -> m.target)

  def getStockBenchmarks() = Action {
    val cycle = currentCycle
    val benchmarksJson = ResponseCache.benchmarks match {
      case (`cycle`, json) => json
      case _ =>
        val allAirlines = AirlineSource.loadAllAirlines(fullLoad = true).filter(_.getHeadQuarter().isDefined)
        val latestWeeklyStats: mutable.Map[Int, AirlineStat] = mutable.Map()
        AirlineStatisticsSource.loadAirlineStatsForAirlineIds(allAirlines.map(_.id))
          .filter(_.period == Period.WEEKLY)
          .foreach { s =>
            latestWeeklyStats.get(s.airlineId) match {
              case Some(existing) if existing.cycle >= s.cycle => // keep existing
              case _ => latestWeeklyStats(s.airlineId) = s
            }
          }
        val benchmarks = StockModel.computeBenchmarks(allAirlines, latestWeeklyStats.toMap)
        val json = JsObject(benchmarks.map { case (typeLabel, tb) =>
          typeLabel -> Json.obj(
            "pask"                -> metricJson(tb.pask),
            "dividends_per_share" -> metricJson(tb.dividendsPerShare),
            "codeshares"          -> metricJson(tb.codeshares)
          )
        })
        ResponseCache.benchmarks = (cycle, json)
        json
    }
    Ok(benchmarksJson).withHeaders(
      CACHE_CONTROL -> "public, max-age=1800",
      ETAG -> s""""$currentCycle""""
    )
  }

  def getAirportChampions(airportId: Int, airlineId: Option[Int]) = Action { request =>
    request.headers.get(IF_NONE_MATCH) match {
      case Some(etag) if etag == s""""$currentCycle"""" =>
        NotModified
      case _ =>
        var result = Json.obj()
        var champsJson = Json.arr()

        val airport = AirportCache.getAirport(airportId, true).get
        val champsSortedByRank = ChampionUtil.loadAirportChampionInfoByAirport(airportId).sortBy(_.ranking)
        champsSortedByRank.foreach { info =>
          champsJson = champsJson.append(Json.toJson(info).asInstanceOf[JsObject] + ("loyalty" -> JsNumber(BigDecimal(airport.getAirlineLoyalty(info.loyalist.airline.id)).setScale(2, RoundingMode.HALF_EVEN))))
        }
        result = result + ("champions" -> champsJson)
        airlineId.foreach { airlineId =>
          if (champsSortedByRank.find(_.loyalist.airline.id == airlineId).isEmpty) { //query airline not a champ, now see check ranking
            val loyalistsSorted = LoyalistSource.loadLoyalistsByAirportId(airportId).sortBy(_.amount).reverse
            loyalistsSorted.find(_.airline.id == airlineId) match {
              case Some(entry) =>
                val rank = loyalistsSorted.indexOf(entry) + 1
                val loyalty = BigDecimal(airport.getAirlineLoyalty(airlineId)).setScale(2, RoundingMode.HALF_EVEN)
                result = result + ("currentAirline" -> (Json.toJson(entry).asInstanceOf[JsObject] + ("ranking" -> JsNumber(rank)) + ("loyalty" -> JsNumber(loyalty))))
              case None => //nothing
            }
          }
        }
        Ok(result).withHeaders(
          CACHE_CONTROL -> "no-cache",
          ETAG -> s""""$currentCycle""""
        )
    }
  }


  def getAirportDemand(airportId: Int) = Action { request =>
    request.headers.get(IF_NONE_MATCH) match {
      case Some(etag) if etag == s""""$currentCycle"""" =>
        NotModified
      case _ =>
        val json = Option(ResponseCache.demandCache.getIfPresent(airportId)).filter(_._1 == currentCycle).map(_._2).getOrElse {
          val result = computeAirportDemandJson(airportId, currentCycle)
          ResponseCache.demandCache.put(airportId, (currentCycle, result))
          result
        }
        Ok(json).withHeaders(CACHE_CONTROL -> "no-cache", ETAG -> s""""$currentCycle"""")
    }
  }

  private def computeAirportDemandJson(airportId: Int, cycle: Int): JsValue = {
    AirportCache.getAirport(airportId) match {
      case None => Json.arr()
      case Some(fromAirport) =>
        val ticketed = ConsumptionHistorySource.loadTopConsumptionsByFromAirport(airportId, 35)
        val missed   = MissedDemandSource.loadByFromAirport(airportId)

        // Convert ticketed entries to json candidates
        // airline IDs come from the passenger_link_history JOIN in the query itself
        val ticketedCandidates: List[JsObject] = ticketed.flatMap { e =>
          AirportCache.getAirport(e.toAirportId).map { toAirport =>
            val distance = Computation.calculateDistance(fromAirport, toAirport)
            val flightCategory = Computation.getFlightCategory(fromAirport, toAirport)
            val linkClass = LinkClass.fromCode(e.preferredLinkClass)
            val paxType = PassengerType.apply(e.passengerType)
            val standardPrice = Pricing.computeStandardPrice(distance, flightCategory, linkClass, paxType, fromAirport.income)
            Json.obj(
              "toAirportId"       -> toAirport.id,
              "toAirportName"     -> toAirport.city,
              "toAirportIata"     -> toAirport.iata,
              "passengerCount"    -> e.passengerCount,
              "passengerType"     -> PassengerType.label(paxType),
              "preferredLinkClass"-> linkClass.prettyLabel,
              "standardPrice"     -> standardPrice,
              "isMissed"          -> false,
              "airlineIds"        -> Json.toJson(e.airlineIds)
            )
          }
        }

        // Convert missed entries to json candidates
        val missedCandidates: List[JsObject] = missed.flatMap { e =>
          AirportCache.getAirport(e.toAirportId).map { toAirport =>
            val distance = Computation.calculateDistance(fromAirport, toAirport)
            val flightCategory = Computation.getFlightCategory(fromAirport, toAirport)
            val linkClass = LinkClass.fromCode(e.preferredLinkClass)
            val paxType = PassengerType.apply(e.passengerType)
            val standardPrice = Pricing.computeStandardPrice(distance, flightCategory, linkClass, paxType, fromAirport.income)
            Json.obj(
              "toAirportId"       -> toAirport.id,
              "toAirportName"     -> toAirport.city,
              "toAirportIata"     -> toAirport.iata,
              "passengerCount"    -> e.passengerCount,
              "passengerType"     -> PassengerType.label(paxType),
              "preferredLinkClass"-> linkClass.prettyLabel,
              "standardPrice"     -> standardPrice,
              "isMissed"          -> true,
              "airlineIds"        -> Json.arr()
            )
          }
        }

        val rng = new scala.util.Random(cycle.toLong * airportId)
        val pool = rng.shuffle(ticketedCandidates).take(22) ++ missedCandidates
        Json.toJson(rng.shuffle(pool).take(21))
    }
  }

  def options(path: String): Action[AnyContent] = Action {
    Ok("").withHeaders(
      "Access-Control-Allow-Methods" -> "GET, POST, PUT, DELETE, OPTIONS",
      "Access-Control-Allow-Headers" -> "Accept, Origin, Content-type, X-Json, X-Prototype-Version, X-Requested-With, Authorization",
      "Access-Control-Allow-Credentials" -> "true",
      "Access-Control-Max-Age" -> (60 * 60 * 24).toString
    )
  }

  def redirect(path: String, any: String): Action[AnyContent] = Action {
    Redirect(path)
  }

  case class LinkInfo(fromId: Int, toId: Int, price: Double, capacity: Int)
}
