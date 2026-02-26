package controllers

import com.patson.data._
import com.patson.model._
import play.api.libs.json._
import play.api.mvc._
import controllers.AuthenticationObject.Authenticated

import javax.inject.Inject
import com.google.common.cache.{CacheBuilder, CacheLoader, LoadingCache}
import java.util.concurrent.TimeUnit
import scala.concurrent.ExecutionContext

class RivalsApplication @Inject()(cc: ControllerComponents)(implicit ec: ExecutionContext) extends AbstractController(cc) {
  val MIN_REPUTATION = 60

  case class RivalsData(airlines: JsValue, history: JsValue)

  val rivalsLoader: CacheLoader[Int, RivalsData] =
    new CacheLoader[Int, RivalsData] {
      def load(cycle: Int): RivalsData = {
        val allAirlines = AirlineSource.loadAllAirlines(fullLoad = true).filter { airline =>
          airline.getHeadQuarter().isDefined && airline.getReputation() >= MIN_REPUTATION
        }

        val airlineIds = allAirlines.map(_.id)

        // Lightweight single-table query on income for stock_price + total_value
        val priceHistory = IncomeSource.loadStockPriceHistory(airlineIds)

        // Load reputation history from airline_statistics
        val repHistory = AirlineStatisticsSource.loadAirlineStatsForAirlineIds(airlineIds)

        // Build airline -> alliance name map
        val alliances = AllianceSource.loadAllAlliances(fullLoad = true)
        val airlineAllianceMap = alliances.flatMap { alliance =>
          alliance.members.map(member => member.airline.id -> alliance.name)
        }.toMap

        val airlinesJson = JsArray(allAirlines.map { airline =>
          Json.obj(
            "id" -> airline.id,
            "name" -> airline.name,
            "airlineCode" -> airline.getAirlineCode(),
            "countryCode" -> JsString(airline.getHeadQuarter().map(_.airport.countryCode).getOrElse("")),
            "airlineType" -> JsString(airline.airlineType.label),
            "reputation" -> BigDecimal(airline.getReputation()).setScale(1, BigDecimal.RoundingMode.HALF_UP),
            "currentPrice" -> BigDecimal(airline.getStockPrice()).setScale(2, BigDecimal.RoundingMode.HALF_UP),
            "alliance" -> JsString(airlineAllianceMap.getOrElse(airline.id, ""))
          )
        })

        // Build history keyed by airlineId, combining price + reputation data
        val priceByAirline = priceHistory.groupBy(_._1) // grouped by airlineId
        val repByAirline = repHistory.groupBy(_.airlineId)

        val historyJson = JsObject(airlineIds.flatMap { airlineId =>
          val priceEntries = priceByAirline.getOrElse(airlineId, List.empty)
          val repEntries = repByAirline.getOrElse(airlineId, List.empty)

          // Build a map of (cycle, period) -> rep_total from airline_statistics
          val repMap: Map[(Int, Int), Int] = repEntries.map(s => (s.cycle, s.period.id) -> s.repTotal).toMap

          val entries = priceEntries.map { case (_, cycle: Int, period: Int, stockPrice: Double, totalValue: Long) =>
            val rep: Int = repMap.getOrElse((cycle, period), 0)
            Json.obj(
              "cycle" -> cycle,
              "period" -> Period(period).toString,
              "price" -> BigDecimal(stockPrice).setScale(2, BigDecimal.RoundingMode.HALF_UP),
              "totalValue" -> totalValue,
              "reputation" -> rep
            )
          }

          if (entries.nonEmpty) Some(airlineId.toString -> JsArray(entries)) else None
        }.toMap)

        RivalsData(airlinesJson, historyJson)
      }
    }

  val rivalsCache: LoadingCache[Int, RivalsData] =
    CacheBuilder.newBuilder()
      .maximumSize(2)
      .expireAfterWrite(10, TimeUnit.MINUTES)
      .build(rivalsLoader)

  def currentCycle: Int = CycleSource.loadCycle() - 1

  def getRivalsData() = Authenticated { implicit request =>
    request.headers.get(IF_NONE_MATCH) match {
      case Some(etag) if etag == s""""$currentCycle"""" =>
        NotModified
      case _ =>
        val cycle = currentCycle
        val data = rivalsCache.get(cycle)
        Ok(Json.obj(
          "airlines" -> data.airlines,
          "history" -> data.history
        )).withHeaders(
          CACHE_CONTROL -> "no-cache",
          ETAG -> s""""$cycle""""
        )
    }
  }
}
