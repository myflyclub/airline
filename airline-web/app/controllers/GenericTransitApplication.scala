package controllers

import com.patson.data.LinkSource
import com.patson.model._
import play.api.libs.json._
import play.api.mvc._

import javax.inject.Inject
import scala.math.BigDecimal.int2bigDecimal


class GenericTransitApplication @Inject()(cc: ControllerComponents) extends AbstractController(cc) {

  def getGenericTransits(airportId : Int) = Action { request =>
    request.headers.get(IF_NONE_MATCH) match {
      case Some(etag) if etag == s""""$currentCycle"""" =>
        NotModified
      case _ =>
        val json = Option(ResponseCache.transitCache.getIfPresent(airportId)).filter(_._1 == currentCycle).map(_._2).getOrElse {
          val genericTransits = (LinkSource.loadLinksByCriteria(List(("from_airport", airportId), ("transport_type", TransportType.GENERIC_TRANSIT.id))) ++
            LinkSource.loadLinksByCriteria(List(("to_airport", airportId), ("transport_type", TransportType.GENERIC_TRANSIT.id)))).map(_.asInstanceOf[GenericTransit])
          var resultJson = Json.arr()
          val consumptionByLinkId = LinkSource.loadLinkConsumptionsByLinksId(genericTransits.map(_.id)).map(entry => (entry.link.id, entry)).toMap
          genericTransits.foreach { transit =>
            val toAirport =
              if (transit.from.id == airportId) {
                transit.to
              } else {
                transit.from
              }

            var transitJson = Json.obj("toAirportId" -> toAirport.id, "toAirportText" -> toAirport.displayText, "toAirportPopulation" -> toAirport.population, "capacity" -> transit.capacity.total, "linkId" -> transit.id, "distance" -> transit.distance)
            consumptionByLinkId.get(transit.id) match {
              case Some(consumption) =>
                transitJson = transitJson + ("passenger" -> JsNumber(consumption.link.getTotalSoldSeats))
              case None =>
                transitJson = transitJson + ("passenger" -> JsNumber(0))
            }
            resultJson = resultJson.append(transitJson)
          }
          ResponseCache.transitCache.put(airportId, (currentCycle, resultJson))
          resultJson
        }
        Ok(json)
          .withHeaders(
            CACHE_CONTROL -> "no-cache",
            ETAG -> s""""$currentCycle""""
          )
    }
  }
}
