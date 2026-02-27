package controllers

import com.patson.DemandGenerator
import com.patson.data.{AllianceSource, ConsumptionHistorySource, CountrySource, LinkSource}
import com.patson.model.Scheduling.TimeSlot
import com.patson.model.{PassengerType, _}
import com.patson.util.AirportCache

import javax.inject.Inject
import play.api.libs.json._
import play.api.mvc._

import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.util.Random


class SearchApplication @Inject()(cc: ControllerComponents) extends AbstractController(cc) {

  def searchRoute(fromAirportId : Int, toAirportId : Int) = Action { request =>
    request.headers.get(IF_NONE_MATCH) match {
      case Some(etag) if etag == s""""$currentCycle"""" =>
        NotModified
      case _ =>
        val cacheKey = s"$fromAirportId-$toAirportId"
        val json = Option(ResponseCache.searchRouteCache.getIfPresent(cacheKey)).filter(_._1 == currentCycle).map(_._2).getOrElse {
          val fresh = computeSearchRoute(fromAirportId, toAirportId)
          ResponseCache.searchRouteCache.put(cacheKey, (currentCycle, fresh))
          fresh
        }
        Ok(json).withHeaders(CACHE_CONTROL -> "no-cache", ETAG -> s""""$currentCycle"""")
    }
  }

  private def computeSearchRoute(fromAirportId : Int, toAirportId : Int) : JsValue = {
    val routes: List[(SimpleRoute, PassengerType.Value, Int)] = ConsumptionHistorySource.loadConsumptionsByAirportPair(fromAirportId, toAirportId).toList.sortBy(_._2._2).map {
      case ((route, (passengerType, passengerCount))) =>
        (SimpleRoute(route.links.map(linkConsideration => (linkConsideration.link, linkConsideration.linkClass, linkConsideration.inverted)), route.totalCost.toInt), passengerType, passengerCount)
    }

    val reverseRoutes : List[(SimpleRoute, PassengerType.Value, Int)] = ConsumptionHistorySource.loadConsumptionsByAirportPair(toAirportId, fromAirportId).toList.sortBy(_._2._2).map {
      case ((route, (passengerType, passengerCount))) =>
        (SimpleRoute(route.links.reverse.map(linkConsideration => (linkConsideration.link, linkConsideration.linkClass, !linkConsideration.inverted)), route.totalCost.toInt), passengerType, passengerCount)
    }

    val sortedRoutes: List[(SimpleRoute, Int)] = (routes ++ reverseRoutes).groupBy(_._1).view.mapValues( _.map(_._3).sum).toList.sortBy(_._1.totalPrice)
    val allianceMap = AllianceSource.loadAllAlliances().map(alliance => (alliance.id, alliance)).toMap

    val targetAirlines = mutable.Set[Airline]()
    val targetLinkIds = mutable.Set[Int]()
    val targetAirports = mutable.Set[Airport]()
    //iterate once to gather some summary info
    sortedRoutes.foreach {
      case (route, passengerCount) =>
        targetAirlines.addAll(route.links.map(_._1.airline))
        targetLinkIds.addAll(route.links.map(_._1.id))
        targetAirports.add(route.links(0)._1.from)
        targetAirports.addAll(route.links.map(_._1.to))
    }
    val airlineAllianceMap = AllianceSource.loadAllianceMemberByAirlines(targetAirlines.toList)
    val detailedLinkLookup = LinkSource.loadLinksByIds(targetLinkIds.toList).map(link => (link.id, link)).toMap

    val airlineGroupLookup = targetAirlines.map { airline => //airline => airline + alliance members
      val airlineGroup : List[Airline] =
        airlineAllianceMap.get(airline) match {
          case Some(allianceMember) =>
            if (allianceMember.role != AllianceRole.APPLICANT) {
              val alliance = allianceMap(allianceMember.allianceId)
              if (alliance.status == AllianceStatus.ESTABLISHED) { //yike check should not be done here
                alliance.members.filterNot(_.role == AllianceRole.APPLICANT).map(_.airline) //yike again!
              } else {
                List(airline)
              }
            } else {
              List(airline)
            }
          case None =>
            List(airline)
        }
      (airline, airlineGroup)
    }.toMap

    //[airline => airline + alliance members

    val remarks : Map[SimpleRoute, List[LinkRemark.Value]] = generateRemarks(sortedRoutes)

    //generate the final json
    var resultJson = Json.arr()
    sortedRoutes.foreach {
      case(route, passengerCount) =>
        var routeEntryJson = Json.obj()
        var routeJson = Json.arr()
        var index = 0
        var previousArrivalMinutes = 0
        val schedule : Map[Transport, TimeSlot] = generateRouteSchedule(route.links.map(_._1)).toMap
        var principleAirline = route.links(0)._1.airline
        route.links.foreach {
          case(link, linkClass, inverted) => {
            val (from, to) = if (!inverted) (link.from, link.to) else (link.to, link.from)
            val departureMinutes = adjustMinutes(schedule(link).totalMinutes, previousArrivalMinutes)
            val arrivalMinutes = departureMinutes + link.duration

            val codeShareFlight : Boolean =
              if (index == 0) {
                false
              } else {
                link.airline != principleAirline && airlineGroupLookup(principleAirline).contains(link.airline)
              }

            if (!codeShareFlight) {
              principleAirline = link.airline
            }

            val airline = if (codeShareFlight) principleAirline else link.airline
            var linkJson = Json.obj(
              "airlineName" -> airline.name,
              "airlineId" -> airline.id,
              "linkClass" -> linkClass.label,
              "fromAirportId" -> from.id,
              "fromAirportName" -> from.name,
              "fromAirportIata" -> from.iata,
              "fromAirportCity" -> from.city,
              "fromAirportText" -> from.displayText,
              "fromAirportCountryCode" -> from.countryCode,
              "toAirportId" -> to.id,
              "toAirportName" -> to.name,
              "toAirportIata" -> to.iata,
              "toAirportCity" -> to.city,
              "toAirportText" -> to.displayText,
              "duration" -> link.duration,
              "toAirportCountryCode" -> to.countryCode,
              "price" -> link.price(linkClass),
              "departure" -> departureMinutes,
              "arrival" -> arrivalMinutes,
              "transportType" -> link.transportType.toString
            )

            if (link.isInstanceOf[Link]) {
              linkJson = linkJson + ("flightCode" -> JsString(LinkUtil.getFlightCode(airline, link.asInstanceOf[Link].flightNumber)))
            }

            if (codeShareFlight) {
              linkJson = linkJson + ("operatorAirlineName" -> JsString(link.airline.name)) +  ("operatorAirlineId" -> JsNumber(link.airline.id))
            }

            detailedLinkLookup.get(link.id).foreach { detailedTransport =>
              detailedTransport.transportType match {
                case TransportType.FLIGHT =>
                  val detailedLink = detailedTransport.asInstanceOf[Link]
                  linkJson = linkJson + ("computedQuality" -> JsNumber(detailedLink.computedQuality()))
                  detailedLink.getAssignedModel().foreach { model =>
                    linkJson = linkJson + ("airplaneModelName" -> JsString(model.name))
                  }

                  linkJson = linkJson + ("features" -> Json.toJson(getLinkFeatures(detailedLink).map(_.toString)))
                case TransportType.GENERIC_TRANSIT =>
                  //linkJson = linkJson + ("features" -> Json.toJson(List(LinkFeature.SHUTTLE.toString)))
              }
              linkJson = linkJson + ("transportType" -> JsString(detailedTransport.transportType.toString))
            }
            previousArrivalMinutes = arrivalMinutes

            routeJson = routeJson.append(linkJson)
            index += 1
          }
        }

        remarks.get(route).foreach { remarks =>
          routeEntryJson = routeEntryJson + ("remarks" -> Json.toJson(remarks.map(_.toString)))
        }

        routeEntryJson = routeEntryJson + ("route" -> routeJson)
        resultJson = resultJson.append(routeEntryJson)
    }

    resultJson
  }

  case class SimpleRoute(links: List[(Transport, LinkClass, Boolean)], routeCost: Int) {
    val totalPrice = links.map {
      case (link, linkClass, _) => link.price(linkClass)
    }.sum
  }


  def generateRouteSchedule(links : List[Transport]) : List[(Transport, TimeSlot)] = {
    val scheduleOptions : ListBuffer[List[(Transport, TimeSlot)]] = ListBuffer()
    val random = new Random()
    for (i <- 0 until 7) {
      var previousLinkArrivalTime : TimeSlot = TimeSlot(i, random.nextInt(24), random.nextInt(60))
      val scheduleOption = links.map { link =>
        val departureTime = generateDepartureTime(link, previousLinkArrivalTime.increment(30)) //at least 30 minutes after
        previousLinkArrivalTime = departureTime.increment(link.duration)
        (link, departureTime)
      }
      scheduleOptions.append(scheduleOption)
    }
    val sortedScheduleOptions: mutable.Seq[List[(Transport, TimeSlot)]] = scheduleOptions.sortBy {
      case (scheduleOption) =>  {
        var previousArrivalMinutes = 0
        scheduleOption.foreach {
          case (link, departureTimeSlot) => {
            previousArrivalMinutes = adjustMinutes(departureTimeSlot.totalMinutes, previousArrivalMinutes) + link.duration
          }
        }
        //previousArrivalMinutes should contain the minutes of arrival of last leg
        val totalDuration = previousArrivalMinutes - scheduleOption(0)._2.totalMinutes
        totalDuration
      }
    }
    sortedScheduleOptions(0)//find the one with least total duration
  }

  def generateDepartureTime(link : Transport, after : TimeSlot) : TimeSlot = {
    val availableSlots = Scheduling.getLinkSchedule(link).sortBy(_.totalMinutes)
    availableSlots.find(_.compare(after) > 0).getOrElse(availableSlots(0)) //find the first slot that is right after the "after option"
  }

  /**
    * Adjust currentMinutes so it's always after the referenceMinutes. This is to handle week rollover on TimeSlot
    * @param currentMinutes
    * @param referenceMinutes
    */
  def adjustMinutes(currentMinutes : Int, referenceMinutes : Int) = {
    var result = currentMinutes
    while (result < referenceMinutes) {
      result += 7 * 24 * 60
    }
    result
  }

  def getLinkFeatures(link : Link) = {
    val features = ListBuffer[LinkFeature.Value]()

    import LinkFeature._
    val airlineServiceQuality = link.airline.getCurrentServiceQuality()
    if (link.duration <= 120) { //very short flight
      if (link.rawQuality >= 80) {
        features += BEVERAGE_SERVICE
      }
    } else if (link.duration <= 240) {
      if (link.rawQuality >= 60) {
        features += BEVERAGE_SERVICE
      }
      if (link.rawQuality >= 80) {
        features += HOT_MEAL_SERVICE
      }
    } else {
      if (link.rawQuality >= 40) {
        features += BEVERAGE_SERVICE
      }
      if (link.rawQuality >= 60) {
        features += HOT_MEAL_SERVICE
      }
    }

    if (link.rawQuality == 100 && airlineServiceQuality >= 70) {
      features += PREMIUM_DRINK_SERVICE
    }

    if (link.rawQuality == 100 && airlineServiceQuality >= 80) {
      features += POSH
    }

    link.getAssignedModel().foreach { model =>
      if (model.capacity >= 100) {
        if (airlineServiceQuality >= 50) {
          features += IFE
        }

        if (airlineServiceQuality >= 60) {
          features += POWER_OUTLET
        }

        if (airlineServiceQuality >= 70) {
          features += WIFI
        }

        if (airlineServiceQuality >= 80) {
          features += GAME
        }
      }
    }

    features.toList
  }

  def generateRemarks(routeEntries: List[(SimpleRoute, Int)]): Map[SimpleRoute, List[LinkRemark.Value]] =  {
    val result = mutable.HashMap[SimpleRoute, ListBuffer[LinkRemark.Value]]()
    if (routeEntries.length >= 3) {
      val (maxRoute, maxPassengerCount) = routeEntries.maxBy(_._2)
      result.getOrElseUpdate(maxRoute, ListBuffer()).append(LinkRemark.BEST_SELLER)


      val (cheapestRoute, _) = routeEntries.minBy(_._1.totalPrice)
      result.getOrElseUpdate(cheapestRoute, ListBuffer()).append(LinkRemark.BEST_DEAL)
    }
    result.view.mapValues(_.toList).toMap
  }

  def researchLink(fromAirportId : Int, toAirportId : Int) = Action { request =>
    request.headers.get(IF_NONE_MATCH) match {
      case Some(etag) if etag == s""""$currentCycle"""" =>
        NotModified
      case _ =>
        val cacheKey = s"$fromAirportId-$toAirportId"
        val json = Option(ResponseCache.researchLinkCache.getIfPresent(cacheKey)).filter(_._1 == currentCycle).map(_._2).getOrElse {
          val fresh = computeResearchLink(fromAirportId, toAirportId)
          ResponseCache.researchLinkCache.put(cacheKey, (currentCycle, fresh))
          fresh
        }
        Ok(json).withHeaders(CACHE_CONTROL -> "no-cache", ETAG -> s""""$currentCycle"""")
    }
  }

  private def computeResearchLink(fromAirportId : Int, toAirportId : Int) : JsValue = {
    val fromAirport = AirportCache.getAirport(fromAirportId, true).get
    val toAirport = AirportCache.getAirport(toAirportId, true).get
    val distance = Computation.calculateDistance(fromAirport, toAirport)
    val relationship = CountrySource.getCountryMutualRelationship(fromAirport.countryCode, toAirport.countryCode)
    val flightCategory = Computation.getFlightCategory(fromAirport, toAirport)
    val affinity = Computation.calculateAffinityValue(fromAirport.zone, toAirport.zone, relationship)
    val affinityText = Computation.constructAffinityText(fromAirport.zone, toAirport.zone, fromAirport.countryCode, toAirport.countryCode, relationship, affinity)

    val (fromDemandDetailsJson, toDemandDetailsJson, fromDemandTotal, toDemandTotal) = LinkApplication.generateDemands(fromAirport, toAirport, affinity, distance, flightCategory)

    val directDemand = fromDemandTotal + toDemandTotal

    val fromQualitySearch = LinkUtil.findExpectedQuality(fromAirportId: Int, toAirportId: Int, fromAirportId: Int)
    val fromExpectedQualities = fromQualitySearch match {
      case Some(classes) =>
        var result = Json.obj()
        LinkClass.values.foreach { linkClass: LinkClass =>
          result += (linkClass.code -> JsNumber(classes(linkClass)))
        }
        result
    }
    val toQualitySearch = LinkUtil.findExpectedQuality(fromAirportId: Int, toAirportId: Int, fromAirportId: Int)
    val toExpectedQualities = toQualitySearch match {
      case Some(classes) =>
        var result = Json.obj()
        LinkClass.values.foreach { linkClass: LinkClass =>
          result += (linkClass.code -> JsNumber(classes(linkClass)))
        }
        result
    }
    val basePrice = Pricing.computeStandardPriceForAllClass(distance, flightCategory, PassengerType.TOURIST, fromAirport.income)

    var result = Json.obj(
      "fromAirportId" -> fromAirport.id,
      "fromAirportText" -> fromAirport.displayText,
      "fromAirportIata" -> fromAirport.iata,
      "fromAirportCountryCode" -> fromAirport.countryCode,
      "fromAirportPopulation" -> fromAirport.population,
      "fromAirportIncome" -> fromAirport.income,
      "toAirportId" -> toAirport.id,
      "toAirportText" -> toAirport.displayText,
      "toAirportIata" -> toAirport.iata,
      "toAirportCountryCode" -> toAirport.countryCode,
      "toAirportPopulation" -> toAirport.population,
      "toAirportIncome" -> toAirport.income,
      "distance" -> distance,
      "flightType" -> FlightCategory.label(flightCategory),
      "directDemand" -> directDemand,
      "mutualRelationship" -> relationship,
      "affinity" -> affinityText,
      "basePrice" -> basePrice,
      "fromDemands" -> fromDemandDetailsJson,
      "toDemands" -> toDemandDetailsJson,
      "fromAirportDemand" -> fromDemandTotal,
      "toAirportDemand" -> toDemandTotal,
      "fromExpectedQualities" -> fromExpectedQualities,
      "toExpectedQualities" -> toExpectedQualities,
    )

    //load existing links
    val links = LinkSource.loadFlightLinksByAirports(fromAirportId, toAirportId) ++ LinkSource.loadFlightLinksByAirports(toAirportId, fromAirportId)
    result = result + ("links" -> Json.toJson(links.sortBy(_.airline.id)))
    val consumptions = LinkSource.loadLinkConsumptionsByLinksId(links.map(_.id)).sortBy(_.link.airline.id)

    result = result + ("consumptions" -> Json.toJson(consumptions)(Writes.list(SimpleLinkConsumptionWrite)))
    result
  }

  object LinkFeature extends Enumeration {
    type LinkFeature = Value
    val WIFI, BEVERAGE_SERVICE, HOT_MEAL_SERVICE, PREMIUM_DRINK_SERVICE, IFE, POWER_OUTLET, GAME, POSH, SHUTTLE, LOUNGE  = Value //No LOUNGE for now, code is too ugly...
  }

  object LinkRemark extends Enumeration {
    type LinkComment = Value
    val BEST_SELLER, BEST_DEAL = Value
  }



  case class SearchResultRoute(linkDetails : List[LinkDetail], passengerCount: Int)
  case class LinkDetail(link : Link, timeslot : TimeSlot)

}
