package controllers

import scala.math.BigDecimal.int2bigDecimal
import com.patson.data.{AirlineSource, AirplaneSource, AirportSource, CountrySource, CycleSource, RankingLeaderboardSource}
import com.patson.data.airplane.ModelSource
import com.patson.model.airplane._
import com.patson.model._
import play.api.libs.json._
import play.api.mvc._
import controllers.AuthenticationObject.AuthenticatedAirline

import javax.inject.{Inject, Singleton}
import com.github.benmanes.caffeine.cache.{AsyncLoadingCache, Caffeine}
import java.util.concurrent.{CompletableFuture, TimeUnit}
import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.FutureConverters._ // Required for Scala 2.13 CompletableFuture interop

@Singleton
class RankingApplication @Inject()(cc: ControllerComponents)(implicit ec: ExecutionContext) extends AbstractController(cc) {
  val MAX_ENTRY = 20

  // 1st cache: per cycle data
  val rankingsCache: AsyncLoadingCache[Integer, Map[RankingType.Value, List[Ranking]]] =
    Caffeine.newBuilder()
      .maximumSize(10)
      .expireAfterWrite(1, TimeUnit.HOURS)
      .buildAsync { (cycle: Integer, executor: java.util.concurrent.Executor) =>
        CompletableFuture.supplyAsync(
          () => RankingLeaderboardSource.loadRankingsByCycle(cycle), 
          executor
        )
      }

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

  // 2nd cache: per cycle JSON serialization of top N entries for each ranking type
  private val cachedTopJson = new AtomicReference[(Int, Map[RankingType.Value, JsArray])]((-1, Map.empty))

  private def getTopRankingsJson(rankings: Map[RankingType.Value, List[Ranking]], cycle: Int): Map[RankingType.Value, JsArray] = {
    cachedTopJson.updateAndGet { current =>
      if (current._1 == cycle) {
        current // Cache hit, return existing
      } else {
        // Cache miss, execute JSON serialization exactly once
        val built = rankings.view.mapValues(rs => Json.toJson(rs.take(MAX_ENTRY)).as[JsArray]).toMap
        (cycle, built)
      }
    }._2
  }

  def getRankingsWithAirline(airlineId: Int) = AuthenticatedAirline(airlineId).async { request =>

    // Execute the O(1) query on the isolated database thread pool
    val cycleFuture: Future[Int] = Future {
      CycleSource.loadCycle() - 1 // Want last completed cycle, so -1
    }

    cycleFuture.flatMap { cycle =>
      request.headers.get(IF_NONE_MATCH) match {
        case Some(etag) if etag == s""""$cycle"""" =>
          Future.successful(NotModified)
        case _ =>
          // Cache miss/stale: fetch new data
          val rankingsFuture: Future[Map[RankingType.Value, List[Ranking]]] = rankingsCache.get(cycle).asScala
          val hqAirportIdFuture: Future[Option[Int]] = Future {
            AirlineSource.loadAirlineById(airlineId, fullLoad = true)
              .flatMap(_.getHeadQuarter())
              .map(_.airport.id)
          }

          (rankingsFuture zip hqAirportIdFuture).map { case (rankings, hqAirportId) =>
            val airlineKey = RankingKey.AirlineKey(airlineId)
            val topJson = getTopRankingsJson(rankings, cycle)

            val rankingJson = topJson.foldLeft(Json.obj()) {
              case (acc, (rankingType, topArr)) =>
                val allRankings = rankings.getOrElse(rankingType, Nil)
                val airlineRankingOpt: Option[Ranking] = rankingType match {
                  case RankingType.AIRPORT | RankingType.MOST_CONGESTED_AIRPORT =>
                    hqAirportId.flatMap(id => allRankings.find(_.key == RankingKey.AirportKey(id)))
                  case RankingType.LOUNGE =>
                    allRankings.find(_.key match {
                      case RankingKey.AirlineAirportKey(aid, _) => aid == airlineId
                      case _ => false
                    })
                  case _ =>
                    allRankings.find(_.key == airlineKey)
                }
                val airlineExtra = airlineRankingOpt.filter(_.ranking > MAX_ENTRY)
                val arr: JsValue = airlineExtra.fold(topArr: JsValue)(r => topArr :+ Json.toJson(r))
                acc + (rankingType.toString -> arr)
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