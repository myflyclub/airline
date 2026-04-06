package controllers

import com.patson.data.{AirportSource, ChristmasSource}
import com.patson.model._
import com.patson.model.christmas.{SantaClausAward, SantaClausAwardType, SantaClausGuess, SantaClausInfo}
import com.patson.model.easter.EasterBunnyAward
import com.patson.util.AirportCache
import controllers.AuthenticationObject.AuthenticatedAirline
import javax.inject.Inject
import play.api.libs.json._
import play.api.mvc._


class EasterApplication @Inject()(cc: ControllerComponents) extends AbstractController(cc) {

  lazy val possibleAirports = AirportCache.getAllAirports().filter(_.size >= SantaClausInfo.AIRPORT_SIZE_THRESHOLD).map(_.id)

  def getAttemptStatus(airportId: Int, airlineId : Int) = AuthenticatedAirline(airlineId) { request =>
    if (possibleAirports.contains(airportId)) {
      val airline = request.user
      airline.getHeadQuarter() match {
        case Some(_) => ChristmasSource.loadSantaClausInfoByAirline(airline.id) match {
          case Some(entry) =>
            var result = Json.obj("found" -> entry.found, "attemptsLeft" -> entry.attemptsLeft)

            SantaClausAward.getDifficultyLevel(entry).foreach { difficulty =>
              result = result + ("difficulty" -> JsString(difficulty.toString.toLowerCase))
            }

            entry.pickedAward.foreach { pickedOption =>
              val pickedAward = EasterBunnyAward.getRewardByType(entry, pickedOption)
              result = result + ("pickedAwardDescription" -> JsString(pickedAward.description))
            }
            var guessesJson = Json.arr()
            entry.guesses.foreach { guess =>
              guessesJson = guessesJson.append(
                Json.obj(
                  "airportId" -> JsNumber(guess.airport.id),
                  "airportName" -> JsString(guess.airport.name),
                  "airportCode" -> JsString(guess.airport.iata),
                  "city" -> JsString(guess.airport.city),
                  "distanceText" -> JsString(getDistanceText(Computation.calculateDistance(entry.airport, guess.airport)))))
            }
            result = result + ("guesses" -> guessesJson)
            Ok(result)
          case None => Ok(Json.obj())
        }
        case None => Ok(Json.obj())
      }
    } else {
      Ok(Json.obj())
    }
  }

  def makeGuess(airportId: Int, airlineId : Int, difficulty : String) = AuthenticatedAirline(airlineId) { request =>
    val airline = request.user
    ChristmasSource.loadSantaClausInfoByAirline(airline.id) match {
      case Some(entry) =>
        var attemptsAdjustment = 0
        if (SantaClausAward.getDifficultyLevel(entry).isEmpty) {
          if (difficulty == "hard") attemptsAdjustment = -5
        }

        if (entry.attemptsLeft <= 0) {
          BadRequest(Json.obj())
        } else {
          val bunnyAirport = entry.airport
          val selectedAirport = AirportCache.getAirport(airportId).get
          val found = bunnyAirport.id == airportId
          val newAttemptsLeft = entry.attemptsLeft - 1 + attemptsAdjustment

          ChristmasSource.updateSantaClausInfo(entry.copy(attemptsLeft = newAttemptsLeft, found = found))
          ChristmasSource.saveSantaClausGuess(SantaClausGuess(selectedAirport, airline))

          if (found) {
            Ok(Json.obj("found" -> true, "attemptsLeft" -> newAttemptsLeft))
          } else {
            Ok(Json.obj("found" -> false, "attemptsLeft" -> newAttemptsLeft, "distanceText" -> getDistanceText(Computation.calculateDistance(bunnyAirport, selectedAirport))))
          }
        }
      case None => NotFound
    }
  }

  def getAwardOptions(airlineId : Int) = AuthenticatedAirline(airlineId) { request =>
    ChristmasSource.loadSantaClausInfoByAirline(airlineId) match {
      case Some(entry) =>
        if (!entry.found || entry.pickedAward.isDefined) {
          BadRequest("Either not found yet or reward has already been redeemed!")
        } else {
          var result = Json.arr()
          EasterBunnyAward.getAllRewards(entry).foreach { option =>
            result = result.append(Json.obj("id" -> option.getType.id, "description" -> option.description))
          }
          Ok(result)
        }
      case None => NotFound
    }
  }

  def pickAwardOption(airlineId : Int, optionId : Int) = AuthenticatedAirline(airlineId) { request =>
    ChristmasSource.loadSantaClausInfoByAirline(airlineId) match {
      case Some(entry) =>
        if (!entry.found || entry.pickedAward.isDefined) {
          BadRequest("Either not found yet or reward has already been redeemed!")
        } else {
          val pickedAward = EasterBunnyAward.getRewardByType(entry, SantaClausAwardType(optionId))
          pickedAward.apply
          Ok(Json.obj())
        }
      case None => NotFound
    }
  }


  def getDistanceText(distance : Int) : String = {
    if (distance >= 10000) {
      "More than 10000 km away... Do you even bunny?"
    } else if (distance >= 5000) {
      "More than 5000 km away... The Easter Bunny was hopping very far from here! ❄️"
    } else if (distance >= 2000) {
      "More than 2000 km away... Cold! You're not even in the right neighbourhood yet. 🌨️"
    } else if (distance >= 1000) {
      "Between 1000 - 2000 km away... you're on the right track but still a long hop away!"
    } else if (distance >= 500) {
      "Between 500 - 1000 km away...  Warm! Can you smell the chocolate eggs yet? 🌤️"
    } else if (distance >= 250) {
      "Between 250 - 500 km away... Getting hot! The bunny's been through here recently! ☀️"
    } else if (distance >= 100) {
      "Between 100 - 250 km away... Very close to the basket! 🔥"
    } else if (distance >= 1) {
      "SCORCHING! You could practically trip over it! 🌋"
    } else {
      "You found the Easter Bunny's basket!"
    }
  }
}
