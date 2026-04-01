package controllers

import com.patson.data.{AirlineSource, AirportSource, CountrySource}
import com.patson.model._
import com.patson.util.{AirlineCache, ChampionUtil, CountryCache}
import controllers.AuthenticationObject.AuthenticatedAirline
import javax.inject.Inject
import play.api.libs.json._
import play.api.mvc._

import scala.math.BigDecimal.int2bigDecimal


class CountryApplication @Inject()(cc: ControllerComponents) extends AbstractController(cc) {

  /**
   * Static country data
   */
  def getAllCountries() = Action {
    val countries = CountrySource.loadAllCountries().filter(_.airportPopulation > 0)
    val airportsByCountryCode = AirportSource.loadAllAirports().filter(_.popMiddleIncome > 0).groupBy(_.countryCode)

    val airportCountsByCountryCode = airportsByCountryCode.view.mapValues { airports =>
      val smallAirportCount = airports.count(airport => airport.size <= 2)
      val mediumAirportCount = airports.count(airport => airport.size >= 3 && airport.size <= 5)
      val largeAirportCount = airports.count(airport => airport.size >= 6)
      Json.obj(
        "smallAirportCount" -> smallAirportCount,
        "mediumAirportCount" -> mediumAirportCount,
        "largeAirportCount" -> largeAirportCount
      )
    }

    val result = countries.map { country =>
      val countryJson = Json.toJson(country).as[JsObject]
      val airportCountJson = airportCountsByCountryCode.getOrElse(country.countryCode, Json.obj())
      country.countryCode -> (countryJson ++ airportCountJson)
    }.toMap
    Ok(Json.toJson(result))
      .withHeaders(
        CACHE_CONTROL -> "public, max-age=2419200",
        ETAG -> s""""$currentApiVersion"""", // Use version as ETag
        EXPIRES -> java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME
          .format(java.time.ZonedDateTime.now().plusWeeks(4))
      )
  }

  /**
   * Per-cycle country data
   *
   * @param airlineId
   * @return
   */
  def getAllCountriesWithAirlineDetails(airlineId: Int) = AuthenticatedAirline(airlineId) { request =>
    request.headers.get(IF_NONE_MATCH) match {
      case Some(etag) if etag == s""""$currentCycle"""" =>
        NotModified
      case _ =>
        val countries = CountrySource.loadAllCountries().filter(_.airportPopulation > 0)

        AirlineCache.getAirline(airlineId, fullLoad = true) match {
          case None => NotFound
          case Some(airline) =>
            var result = Json.arr()
            val baseCountByCountryCode = airline.getBases().groupBy(_.countryCode).view.mapValues(_.length)
            val managersByCountryCode = airline.getManagerInfo().busyManagers.filter(_.assignedTask.getTaskType == ManagerTaskType.COUNTRY).map(_.assignedTask.asInstanceOf[CountryManagerTask]).groupBy(_.country.countryCode)
            val mutualRelationships: Map[String, Int] =
              airline.getCountryCode() match {
                case Some(homeCountryCode) => CountrySource.getCountryMutualRelationships(homeCountryCode)
                case None => Map.empty[String, Int]
              }

            countries.foreach { country =>
              var countryJson: JsObject = Json.obj("countryCode" -> country.countryCode)
              baseCountByCountryCode.get(country.countryCode).foreach { baseCount =>
                countryJson = countryJson + ("baseCount" -> JsNumber(baseCount))
              }
              managersByCountryCode.get(country.countryCode).foreach { managersAssignedToThisCountry =>
                countryJson = countryJson + ("managersCount" -> JsNumber(managersAssignedToThisCountry.length))
              }

              if (airline.getHeadQuarter().isDefined) {
                val relationship: Int = mutualRelationships.getOrElse(country.countryCode, 0)
                countryJson = countryJson + ("mutualRelationship" -> JsNumber(relationship))

                val airlineCountryRelationship = AirlineCountryRelationship.getAirlineCountryRelationship(country.countryCode, airline)
                countryJson = countryJson + ("countryRelationship" -> Json.toJson(airlineCountryRelationship))
                val title = CountryAirlineTitle.getTitle(country.countryCode, airline)
                countryJson = countryJson + (("CountryTitle") -> Json.toJson(title))
              }
              result = result.append(countryJson)
            }

            Ok(result)
              .withHeaders(
                CACHE_CONTROL -> "no-cache",
                ETAG -> s""""$currentCycle""""
              )
        }
    }
  }

  private val countryJsonCache = new scala.collection.concurrent.TrieMap[String, (Int, JsValue)]()
  private val titleProgressionCache = new scala.collection.concurrent.TrieMap[String, (Int, JsValue)]()

  def getCountry(countryCode: String) = Action { request =>
    request.headers.get(IF_NONE_MATCH) match {
      case Some(etag) if etag == s""""$currentCycle"""" =>
        NotModified
      case _ =>
        val json: Option[JsValue] = countryJsonCache.get(countryCode).filter(_._1 == currentCycle).map(_._2).orElse {
          val fresh = getCountryJson(countryCode)
          fresh.foreach { j => countryJsonCache(countryCode) = (currentCycle, j) }
          fresh
        }
        json match {
          case Some(j) => Ok(j).withHeaders(CACHE_CONTROL -> "no-cache", ETAG -> s""""$currentCycle"""")
          case None => NotFound
        }
    }
  }

  def getCountryAirlineTitleProgression(countryCode: String) = Action { request =>
    request.headers.get(IF_NONE_MATCH) match {
      case Some(etag) if etag == s""""$currentCycle"""" =>
        NotModified
      case _ =>
        val json = titleProgressionCache.get(countryCode).filter(_._1 == currentCycle).map(_._2).orElse {
          CountryCache.getCountry(countryCode).map { country =>
            var result = Json.arr()
            Title.values.toList.sortBy(_.id).reverse.foreach { title =>
              val titleJson = Json.obj("title" -> title.toString,
                "description" -> Title.description(title),
                "requirements" -> CountryAirlineTitle.getTitleRequirements(title, country),
                "bonus" -> CountryAirlineTitle.getTitleBonus(title, country))
              result = result.append(titleJson)
            }
            titleProgressionCache(countryCode) = (currentCycle, result)
            result: JsValue
          }
        }
        json match {
          case Some(j) => Ok(j).withHeaders(CACHE_CONTROL -> "no-cache", ETAG -> s""""$currentCycle"""")
          case None => NotFound
        }
    }
  }

  def getCountryJson(countryCode: String): Option[JsObject] = {
    CountryCache.getCountry(countryCode).map { country =>
      var jsonObject: JsObject = Json.toJson(country).asInstanceOf[JsObject]

      val allBases = AirlineSource.loadAirlineBasesByCountryCode(countryCode)

      val (headquarters, bases) = allBases.partition {
        _.headquarter
      }

      jsonObject = jsonObject.asInstanceOf[JsObject] ++
        Json.obj(
          "headquartersCount" -> headquarters.length,
          "basesCount" -> bases.length
        )
      CountrySource.loadMarketSharesByCountryCode(countryCode).foreach { marketShares => //if it has market share data
        val champions = ChampionUtil.getCountryChampionInfoByCountryCode(countryCode).sortBy(_.ranking)
        val championsJson = Json.toJson(champions)

        jsonObject = jsonObject.asInstanceOf[JsObject] + ("champions" -> championsJson)

        var nationalAirlinesJson = Json.arr()
        var partneredAirlinesJson = Json.arr()
        CountryAirlineTitle.getTopTitlesByCountry(countryCode).foreach {
          case countryAirlineTitle =>
            val CountryAirlineTitle(country, airline, title) = countryAirlineTitle
            val share: Long = marketShares.airlineShares.getOrElse(airline.id, 0L)
            val relationship = AirlineCountryRelationship.getAirlineCountryRelationship(countryCode, airline).relationship
            title match {
              case Title.NATIONAL_AIRLINE =>
                nationalAirlinesJson = nationalAirlinesJson.append(Json.obj("airlineId" -> airline.id, "airlineName" -> airline.name, "passengerCount" -> share, "loyaltyBonus" -> countryAirlineTitle.loyaltyBonus, "relationship" -> relationship))
              case Title.PARTNERED_AIRLINE =>
                partneredAirlinesJson = partneredAirlinesJson.append(Json.obj("airlineId" -> airline.id, "airlineName" -> airline.name, "passengerCount" -> share, "loyaltyBonus" -> countryAirlineTitle.loyaltyBonus, "relationship" -> relationship))
            }
        }

        var favoredAirlinesJson = Json.arr()
        CountryAirlineTitle.getNextInLineByCountry(countryCode).foreach { countryAirlineTitle =>
          val CountryAirlineTitle(_, airline, _) = countryAirlineTitle
          val relationship = AirlineCountryRelationship.getAirlineCountryRelationship(countryCode, airline).relationship
          favoredAirlinesJson = favoredAirlinesJson.append(Json.obj("airlineId" -> airline.id, "airlineName" -> airline.name, "relationship" -> relationship))
        }

        jsonObject = jsonObject + ("nationalAirlines" -> nationalAirlinesJson) + ("partneredAirlines" -> partneredAirlinesJson) + ("favoredAirlines" -> favoredAirlinesJson)

        jsonObject = jsonObject.asInstanceOf[JsObject] + ("marketShares" -> Json.toJson(marketShares.airlineShares.map {
          case ((airlineId, passengerCount)) => (AirlineCache.getAirline(airlineId).getOrElse(Airline.fromId(airlineId)), passengerCount)
        }.toList)(AirlineSharesWrites))
      }
      jsonObject
    }

  }

  object AirlineSharesWrites extends Writes[List[(Airline, Long)]] {
    def writes(shares: List[(Airline, Long)]): JsValue = {
      var jsonArray = Json.arr()
      shares.foreach { share =>
        jsonArray = jsonArray :+ JsObject(List(
          "airlineId" -> JsNumber(share._1.id),
          "airlineName" -> JsString(share._1.name),
          "passengerCount" -> JsNumber(share._2)
        ))
      }
      jsonArray
    }
  }
}
