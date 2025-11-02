package controllers

import scala.math.BigDecimal.int2bigDecimal
import com.patson.data.{AirlineSource, AirplaneSource, AirportSource, CountrySource, CycleSource, RankingLeaderboardSource}
import com.patson.data.airplane.ModelSource
import com.patson.model.airplane._
import com.patson.model._
import play.api.libs.json.JsNumber
import play.api.libs.json.JsObject
import play.api.libs.json.JsString
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.libs.json.Writes
import play.api.mvc._

import scala.collection.mutable.ListBuffer
import controllers.AuthenticationObject.AuthenticatedAirline

import javax.inject.Inject
// ADDED IMPORTS
import com.google.common.cache.{CacheBuilder, CacheLoader, LoadingCache}
import java.util.concurrent.TimeUnit
import scala.concurrent.{ExecutionContext, Future}


// MODIFIED CLASS CONSTRUCTOR - removed AsyncCacheApi
class RankingApplication @Inject()(cc: ControllerComponents)(implicit ec: ExecutionContext) extends AbstractController(cc) {
  val MAX_ENTRY = 20

  // START: GUAVA CACHE DEFINITION
  // Define the loader that fetches data on a cache miss
  val rankingsLoader: CacheLoader[Int, Map[RankingType.Value, List[Ranking]]] =
    new CacheLoader[Int, Map[RankingType.Value, List[Ranking]]] {
      def load(cycle: Int): Map[RankingType.Value, List[Ranking]] = {
        // This is the synchronous database call
        RankingLeaderboardSource.loadRankingsByCycle(cycle)
      }
    }

  // Build the cache instance
  val rankingsCache: LoadingCache[Int, Map[RankingType.Value, List[Ranking]]] =
    CacheBuilder.newBuilder()
      .maximumSize(10) // Cache up to 10 cycles' data
      .expireAfterWrite(1, TimeUnit.HOURS) // Evict entries after 1 hour
      .build(rankingsLoader)
  // END: GUAVA CACHE DEFINITION

  implicit object RankingWrites extends Writes[Ranking] {
    def writes(ranking: Ranking): JsValue = {
      var result = Json.obj(
        "rank" -> ranking.ranking,
        "rankedValue" -> ranking.rankedValue.toString,
        "movement" -> ranking.movement
      )

      ranking.entry match {
        case airline: Airline =>
          result = result + ("airlineName" -> JsString(airline.name)) + ("airlineId" -> JsNumber(airline.id)) + ("airlineSlogan" -> JsString(airline.slogan.getOrElse("")))
        case link: Link =>
          val fromJson = Json.toJson(link.from)(AirportSimpleWrites)
          val toJson = Json.toJson(link.to)(AirportSimpleWrites)
          result = result + ("airlineName" -> JsString(link.airline.name)) + ("airlineId" -> JsNumber(link.airline.id)) ++ Json.obj("rankInfo" -> Json.obj("from" -> fromJson, "to" -> toJson))
        case lounge: Lounge =>
          result = result + ("airlineName" -> JsString(lounge.airline.name)) + ("airlineId" -> JsNumber(lounge.airline.id)) + ("rankInfo" -> JsString(getLoungeDescription(lounge)))
        case alliance: Alliance =>
          result = result + ("allianceName" -> JsString(alliance.name)) + ("allianceId" -> JsNumber(alliance.id))
        case (airline: Airline, airport: Airport) =>
          result = result + ("rankInfo" -> JsString(airport.name)) + ("airportId" -> JsNumber(airport.id)) + ("airlineName" -> JsString(airline.name)) + ("airlineId" -> JsNumber(airline.id)) + ("airlineSlogan" -> JsString(airline.slogan.getOrElse("")))
        case airport: Airport =>
          result = result + ("airportName" -> JsString(airport.name)) + ("airportId" -> JsNumber(airport.id)) + ("iata" -> JsString(airport.iata)) + ("countryCode" -> JsString(airport.countryCode))
        case (airport1: Airport, airport2: Airport) =>
          result = result ++ Json.obj("airport1" -> Json.toJson(airport1)(AirportSimpleWrites), "airport2" -> Json.toJson(airport2)(AirportSimpleWrites))
        case _ =>
      }

      ranking.reputationPrize match {
        case Some(value) => result = result + ("reputationPrize" -> JsNumber(value))
        case None =>
      }

      result
    }
  }

  implicit object RankingTypeWrites extends Writes[RankingType.Value] {
    def writes(rankingType: RankingType.Value): JsValue = {
      Json.obj(
        "rankingType" -> rankingType.toString
      )
    }
  }

  def getLoungeDescription(lounge: Lounge) = {
    lounge.name + " at " + lounge.airport.city + "(" + lounge.airport.iata + ")"
  }

  def getRankingsWithAirline(airlineId: Int) = AuthenticatedAirline(airlineId).async { request =>
    request.headers.get(IF_NONE_MATCH) match {
      case Some(etag) if etag == s""""$currentCycle"""" =>
        Future.successful(NotModified)
      case _ =>
        val latestCycleOpt = RankingLeaderboardSource.loadLatestCycle()

        latestCycleOpt match {
          case None =>
            // No cycle found, return empty object
            Future.successful(Ok(Json.obj()).withHeaders(ETAG -> s""""0""""))
          case Some(cycle) =>
            // 1. Wrap the blocking cache.get() call in a Future to keep the action non-blocking
            val rankingsFuture: Future[Map[RankingType.Value, List[Ranking]]] = Future {
              rankingsCache.get(cycle) // This will either return the cached value or trigger rankingsLoader.load(cycle)
            }

            // 2. Process the result asynchronously
            rankingsFuture.map { rankings =>
              val airlineKey = RankingKey.AirlineKey(airlineId)

              val rankingJson = rankings.foldLeft(Json.obj()) {
                case (jsonAccumulator, (rankingType, allRankings)) =>

                  val topRankings = allRankings.take(MAX_ENTRY)
                  val airlineRankingOpt = allRankings.find(_.key == airlineKey)
                  val airlineRankingToAppend = airlineRankingOpt.filter(_.ranking > MAX_ENTRY)

                  val rankingsList = topRankings ++ airlineRankingToAppend.toList
                  val rankingEntriesJson = Json.toJson(rankingsList)

                  jsonAccumulator + (rankingType.toString -> rankingEntriesJson)
              }

              Ok(rankingJson).withHeaders(
                CACHE_CONTROL -> "no-cache",
                ETAG -> s""""$cycle""""
              )
            }
        }
    }
  }
}