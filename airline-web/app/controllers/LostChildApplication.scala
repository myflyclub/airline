package controllers

import com.patson.data.{AirlineSource, ChristmasSource, CycleSource}
import com.patson.model._
import com.patson.model.christmas.{SantaClausAwardType, SantaClausGuess}
import com.patson.model.lostchild.{LostChildAward, LostChildInfo}
import com.patson.util.AirportCache
import controllers.AuthenticationObject.AuthenticatedAirline
import javax.inject.Inject
import play.api.libs.json._
import play.api.mvc._


class LostChildApplication @Inject()(cc: ControllerComponents) extends AbstractController(cc) {

  lazy val possibleAirports = AirportCache.getAllAirports().filter(_.size >= LostChildInfo.AIRPORT_SIZE_THRESHOLD).map(_.id)

  private def getWeatherObservation(airport: Airport): String = {
    val weather = WeatherUtil.getWeather(new WeatherUtil.Coordinates(airport.latitude, airport.longitude))
    if (weather == null) return ""
    val id = weather.getWeatherId
    val temp = weather.getTemperature
    if (id >= 600 && id <= 622)
      "Notably, the child keeps asking 'why so green' and complains about being hot."
    else if (temp >= 32)
      "The child was seen revelling in the heat. Could they be from a particularly hot place?"
    else if (temp <= 0)
      "The child arrived in a miniature ski suit and seems to seek cold."
    else if (temp <= 10)
      "Curiously, the child declared this airport 'too hot' and immediately started taking off all their clothes."
    else if (id == 800)
      "By the way, a flight attendant noticed the child had a sun hat and sunscreen."
    else if (id >= 200 && id <= 531)
      "Curiously, the child expresses shock at seeing the sun."
    else
      "A flight attendant notices the child's clothing is aggressively ordinary, as if they come from a locale with an aggressively mild climate."
  }

  def getAttemptStatus(airportId: Int, airlineId: Int) = AuthenticatedAirline(airlineId) { request =>
    if (possibleAirports.contains(airportId)) {
      val airline = request.user
      airline.getHeadQuarter() match {
        case Some(hq) => ChristmasSource.loadSantaClausInfoByAirline(airline.id) match {
          case Some(entry) =>
            val interrogationCount = entry.guesses.count(_.airport.id == 0)
            val interrogationsLeft = math.max(0, LostChildInfo.MAX_INTERROGATIONS - interrogationCount)
            var result = Json.obj(
              "found"             -> entry.found,
              "attemptsLeft"      -> entry.attemptsLeft,
              "interrogationsLeft" -> interrogationsLeft,
              "isHq"              -> (airportId == hq.airport.id)
            )

            entry.pickedAward.foreach { pickedOption =>
              val pickedAward = LostChildAward.getRewardByType(entry, pickedOption)
              result = result + ("pickedAwardDescription" -> JsString(pickedAward.description))
            }

            var interrogationIdx = 0
            var flightIdx = 0
            var guessesJson = Json.arr()
            entry.guesses.foreach { guess =>
              val isInterrogation = guess.airport.id == 0
              val clueText = if (isInterrogation) {
                interrogationIdx += 1
                LostChildAward.getInterrogationClueText(interrogationIdx, entry.airport)
              } else {
                flightIdx += 1
                LostChildAward.getFlightClueText(flightIdx, entry.airport, guess.airport, getWeatherObservation(entry.airport))
              }
              guessesJson = guessesJson.append(Json.obj(
                "isInterrogation" -> JsBoolean(isInterrogation),
                "airportId"       -> JsNumber(guess.airport.id),
                "airportName"     -> JsString(guess.airport.name),
                "airportCode"     -> JsString(guess.airport.iata),
                "city"            -> JsString(guess.airport.city),
                "clueText"        -> JsString(clueText)
              ))
            }
            result = result + ("guesses" -> guessesJson)
            if (!entry.found && entry.attemptsLeft <= 0) {
              result = result + ("targetCity" -> JsString(entry.airport.city))
            }
            Ok(result)
          case None => Ok(Json.obj())
        }
        case None => Ok(Json.obj())
      }
    } else {
      Ok(Json.obj())
    }
  }

  def interrogate(airlineId: Int) = AuthenticatedAirline(airlineId) { request =>
    val airline = request.user
    ChristmasSource.loadSantaClausInfoByAirline(airline.id) match {
      case Some(entry) =>
        val interrogationCount = entry.guesses.count(_.airport.id == 0)
        if (entry.attemptsLeft <= 0) {
          BadRequest(Json.obj("error" -> "No attempts left"))
        } else if (interrogationCount >= LostChildInfo.MAX_INTERROGATIONS) {
          BadRequest(Json.obj("error" -> "No interrogations left"))
        } else {
          val interrogationIndex = interrogationCount + 1
          val newAttemptsLeft = entry.attemptsLeft - 1
          val interrogationsLeft = math.max(0, LostChildInfo.MAX_INTERROGATIONS - interrogationIndex)

          ChristmasSource.updateSantaClausInfo(entry.copy(attemptsLeft = newAttemptsLeft))
          ChristmasSource.saveSantaClausGuess(SantaClausGuess(Airport.fromId(0), airline))

          val clueText = LostChildAward.getInterrogationClueText(interrogationIndex, entry.airport)

          Ok(Json.obj("attemptsLeft" -> newAttemptsLeft, "interrogationsLeft" -> interrogationsLeft, "clueText" -> clueText))
        }
      case None => NotFound
    }
  }

  def makeGuess(airportId: Int, airlineId: Int) = AuthenticatedAirline(airlineId) { request =>
    val airline = request.user
    ChristmasSource.loadSantaClausInfoByAirline(airline.id) match {
      case Some(entry) =>
        if (entry.attemptsLeft <= 0) {
          BadRequest(Json.obj())
        } else {
          val targetAirport = entry.airport
          val selectedAirport = AirportCache.getAirport(airportId).get
          val found = targetAirport.id == airportId
          val newAttemptsLeft = entry.attemptsLeft - 1
          val flightIndex = entry.guesses.count(_.airport.id != 0) + 1

          ChristmasSource.updateSantaClausInfo(entry.copy(attemptsLeft = newAttemptsLeft, found = found))
          ChristmasSource.saveSantaClausGuess(SantaClausGuess(selectedAirport, airline))

          if (!found && newAttemptsLeft == 0) {
            val PENALTY = -36000
            AirlineSource.saveLedgerEntry(AirlineLedgerEntry(airline.id, CycleSource.loadCycle(), LedgerType.PRIZE, PENALTY, Some("Meal vouchers for child you failed to reunite with family.")))
          }

          val clueText = LostChildAward.getFlightClueText(flightIndex, targetAirport, selectedAirport, getWeatherObservation(targetAirport))

          Ok(Json.obj("found" -> found, "attemptsLeft" -> newAttemptsLeft, "clueText" -> clueText))
        }
      case None => NotFound
    }
  }

  def getAwardOptions(airlineId: Int) = AuthenticatedAirline(airlineId) { request =>
    val airline = request.user
    ChristmasSource.loadSantaClausInfoByAirline(airline.id) match {
      case Some(entry) =>
        if (!entry.found || entry.pickedAward.isDefined) {
          BadRequest("Either not found yet or reward has already been redeemed!")
        } else {
          var result = Json.arr()
          LostChildAward.getAllRewards(entry).foreach { option =>
            result = result.append(Json.obj("id" -> option.getType.id, "description" -> option.description))
          }
          Ok(result)
        }
      case None => NotFound
    }
  }

  def pickAwardOption(airlineId: Int, optionId: Int) = AuthenticatedAirline(airlineId) { request =>
    val airline = request.user
    ChristmasSource.loadSantaClausInfoByAirline(airline.id) match {
      case Some(entry) =>
        if (!entry.found || entry.pickedAward.isDefined) {
          BadRequest("Either not found yet or reward has already been redeemed!")
        } else {
          val pickedAward = LostChildAward.getRewardByType(entry, SantaClausAwardType(optionId))
          pickedAward.apply
          Ok(Json.obj())
        }
      case None => NotFound
    }
  }
}
