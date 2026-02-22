package controllers

import com.patson.data.LinkSource
import com.patson.model._
import play.api.libs.json._
import play.api.mvc._

import javax.inject.Inject
import scala.math.BigDecimal.int2bigDecimal


class GenericTransitApplication @Inject()(cc: ControllerComponents) extends AbstractController(cc) {
  private val transitCache = new scala.collection.concurrent.TrieMap[Int, (Int, JsValue)]()

  def getGenericTransits(airportId : Int) = Action { request =>
    val cycle = currentCycle
    val json = transitCache.get(airportId).filter(_._1 == cycle).map(_._2).getOrElse {
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
        println(consumptionByLinkId)
        consumptionByLinkId.get(transit.id) match {
          case Some(consumption) =>
            println("consumption found")
            println(consumption)
            transitJson = transitJson + ("passenger" -> JsNumber(consumption.link.getTotalSoldSeats))
          case None =>
            transitJson = transitJson + ("passenger" -> JsNumber(0))
        }
        resultJson = resultJson.append(transitJson)
      }
      transitCache(airportId) = (cycle, resultJson)
      resultJson
    }
    Ok(json)
      .withHeaders(
        ETAG -> s""""$cycle""""
      )
  }
}
