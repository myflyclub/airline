package controllers

import play.api.mvc._
import javax.inject._
import com.patson.data.UserSource
import com.patson.model._
import com.patson.Authentication

import java.util.Calendar
import com.patson.data.{AirlineSource, CycleSource, NotificationSource}
import com.patson.model.{Notification, NotificationCategory}
import com.patson.util.AirlineCache
import play.api.libs.ws.WSClient

import scala.concurrent.duration.Duration
import java.util.concurrent.TimeUnit
import scala.concurrent.Await
import play.api.libs.json.Writes
import play.api.libs.json.Json
import play.api.libs.json.JsValue
import play.api.libs.json.JsObject
import play.api.libs.json.JsNumber
import play.api.libs.json.JsString

class SignUp @Inject()(cc: ControllerComponents)(ws: WSClient) extends AbstractController(cc) with play.api.i18n.I18nSupport {
  private[this] val recaptchaUrl = "https://www.google.com/recaptcha/api/siteverify"
  private[this] val recaptchaAction = "signup"
  private[this] val recaptchaSecret = "6LefN0MnAAAAAF58zLqHe7sDsfJ2q7AFUl1FP9ls"
  private[this] val recaptchaScoreThreshold = 0.5
  val MIN_AIRLINE_NAME_LENGTH = 1
  val MAX_AIRLINE_NAME_LENGTH = 50

  private[this] def isValidAirlineNameChar(c: Char): Boolean =
    (c.isLetter && c <= 'z') || c == ' ' || SignUp.AIRLINE_NAME_SAFE_SYMBOLS.contains(c)

  private[this] val airlineNameCharError =
    s"Airline name can only contain letters, spaces, and these symbols: ${SignUp.AIRLINE_NAME_SAFE_SYMBOLS.toSeq.sorted.mkString(" ")}"
  def airlineNameCheck(airlineName : String)= Action { implicit request =>
    val length = airlineName.length
    if (length < MIN_AIRLINE_NAME_LENGTH || length > MAX_AIRLINE_NAME_LENGTH) {
      Ok(Json.obj("rejection" -> s"Length should be $MIN_AIRLINE_NAME_LENGTH - $MAX_AIRLINE_NAME_LENGTH"))
    } else if (!airlineName.forall(isValidAirlineNameChar)) {
      Ok(Json.obj("rejection" -> airlineNameCharError))
    } else if (AirlineSource.loadAirlinesByCriteria(List(("name", airlineName.trim))).length > 0) {
      Ok(Json.obj("rejection" -> s"Airline name is already taken"))
    } else {
      Ok(Json.obj("ok" -> true))
    }
  }

  /**
   * Handle JSON signup submission.
   */
  def submitJson = Action { implicit request =>
    request.body.asJson match {
      case None => BadRequest(Json.obj("error" -> "Expected JSON body"))
      case Some(json) =>
        val username = (json \ "username").asOpt[String].getOrElse("").trim
        val email = (json \ "email").asOpt[String].getOrElse("").trim
        val password = (json \ "password").asOpt[String].getOrElse("")
        val passwordConfirm = (json \ "passwordConfirm").asOpt[String].getOrElse("")
        val airlineName = (json \ "airlineName").asOpt[String].getOrElse("").trim

        // Validate
        val errors = scala.collection.mutable.ListBuffer[String]()

        if (username.length < 4 || username.length > 20) {
          errors += "Username must be 4-20 characters"
        } else if (!username.forall(char => char.isLetterOrDigit && char <= 'z')) {
          errors += "Username can only contain alphanumeric characters"
        } else if (UserSource.loadUserByUserName(username).isDefined) {
          errors += "This username is not available"
        }

        if (email.isEmpty || !email.contains("@")) {
          errors += "Please enter a valid email address"
        }

        if (password.length < 6) {
          errors += "Password must be at least 6 characters"
        } else if (password != passwordConfirm) {
          errors += "Passwords don't match"
        }

        if (airlineName.length < MIN_AIRLINE_NAME_LENGTH || airlineName.length > MAX_AIRLINE_NAME_LENGTH) {
          errors += s"Airline name must be $MIN_AIRLINE_NAME_LENGTH-$MAX_AIRLINE_NAME_LENGTH characters"
        } else if (!airlineName.forall(isValidAirlineNameChar) || airlineName.trim.isEmpty) {
          errors += airlineNameCharError
        } else if (AirlineSource.loadAirlinesByCriteria(List(("name", airlineName))).nonEmpty) {
          errors += "This airline name is not available"
        }

        if (errors.nonEmpty) {
          BadRequest(Json.obj("errors" -> errors.toList))
        } else {
          val user = User(username, email, Calendar.getInstance, Calendar.getInstance, UserStatus.ACTIVE, level = 0, None, List.empty)
          UserSource.saveUser(user)
          Authentication.createUserSecret(username, password)

          val newAirline = initializeNewAirline(airlineName, user)

          Ok(Json.obj(
            "id" -> user.id,
            "userName" -> username,
            "airlineIds" -> Json.arr(newAirline.id)
          )).withCookies(Cookie("sessionActive", "true", httpOnly = false, maxAge = Some(2592000)))
            .withSession("userToken" -> SessionUtil.addUserId(user.id))
        }
    }
  }

  def createAdditionalAirline = AuthenticationObject.Authenticated { implicit request =>
    val user = request.user
    request.body.asJson match {
      case None => BadRequest(Json.obj("error" -> "Expected JSON body"))
      case Some(json) =>
        val airlineName = (json \ "airlineName").asOpt[String].getOrElse("").trim

        if (user.getAccessibleAirlines().size >= user.maxAirlinesAllowed) {
          BadRequest(Json.obj("error" -> s"You already have the maximum of ${user.maxAirlinesAllowed} airlines"))
        } else if (airlineName.length < MIN_AIRLINE_NAME_LENGTH || airlineName.length > MAX_AIRLINE_NAME_LENGTH) {
          BadRequest(Json.obj("error" -> s"Airline name must be $MIN_AIRLINE_NAME_LENGTH-$MAX_AIRLINE_NAME_LENGTH characters"))
        } else if (!airlineName.forall(isValidAirlineNameChar) || airlineName.isEmpty) {
          BadRequest(Json.obj("error" -> airlineNameCharError))
        } else if (AirlineSource.loadAirlinesByCriteria(List(("name", airlineName))).nonEmpty) {
          BadRequest(Json.obj("error" -> "This airline name is not available"))
        } else {
          val newAirline = initializeNewAirline(airlineName, user)
          Ok(Json.obj("airlineId" -> newAirline.id, "airlineName" -> newAirline.name))
        }
    }
  }

  private def initializeNewAirline(airlineName: String, user: User): Airline = {
    val newAirline = Airline(airlineName)
    newAirline.setMinimumRenewalBalance(300000)
    AirlineSource.saveAirlines(List(newAirline))
    AirlineSource.saveAirlineCode(newAirline.id, newAirline.getDefaultAirlineCode())
    UserSource.setUserAirline(user, newAirline)
    AirlineSource.saveAirplaneRenewal(newAirline.id, 40)
    if (!newAirline.isSkipTutorial) {
      NotificationSource.insertNotification(Notification(newAirline.id, NotificationCategory.TUTORIAL, "Welcome! Choose an airport to build your HQ and get started.", CycleSource.loadCycle()))
    }
    newAirline
  }

  def isValidRecaptcha(recaptchaToken: String) : Boolean = {
    println("checking token " + recaptchaToken)
    val request = ws.url(recaptchaUrl).withQueryStringParameters("secret" -> recaptchaSecret, "response" -> recaptchaToken)
    
    val (successJs, scoreJs, actionJs, responseBody) = Await.result(request.get().map { response =>
      ((response.json \ "success"), (response.json \ "score"), (response.json \ "action"), response.body)
    }, Duration(10, TimeUnit.SECONDS))
    
    if (!successJs.as[Boolean]) {
      println("recaptcha response with success as false")
      return false;  
    }
    
    val score = scoreJs.as[Double]
    val action = actionJs.as[String]
    
    println("recaptcha score " + score + " action " + action)
    
    return action == recaptchaAction && score >= recaptchaScoreThreshold
  }
}

object SignUp {
  val AIRLINE_NAME_SAFE_SYMBOLS: Set[Char] = Set('-', '&', '.', '\'')
}
