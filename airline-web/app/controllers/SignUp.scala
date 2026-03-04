package controllers

import play.api.mvc._
import play.api.data._
import play.api.data.Forms._

import javax.inject._
import views._
import models._
import com.patson.data.UserSource
import com.patson.model._
import com.patson.Authentication

import java.util.Calendar
import com.patson.data.AirlineSource
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
  /**
   * Sign Up Form definition.
   *
   * Once defined it handle automatically, ,
   * validation, submission, errors, redisplaying, ...
   */
  val signupForm: Form[NewUser] = Form(
    
    // Define a mapping that will handle User values
    mapping(
      "username" -> text(minLength = 4, maxLength = 20).verifying(
        "username can only contain alphanumeric characters",
        userName => userName.forall(char => char.isLetterOrDigit && char <= 'z')).verifying(
        "This username is not available",
        userName => !UserSource.loadUsersByCriteria(List.empty).map { _.userName.toLowerCase() }.contains(userName.toLowerCase())    
      ),
      "email" -> email,
      // Create a tuple mapping for the password/confirm
      "password" -> tuple(
        "main" -> text(minLength = 6),
        "confirm" -> text
      ).verifying(
        // Add an additional constraint: both passwords must match
        "Passwords don't match", passwords => passwords._1 == passwords._2
      ),
      "recaptchaToken" -> text,
      "airlineName" -> text(minLength = MIN_AIRLINE_NAME_LENGTH, maxLength = MAX_AIRLINE_NAME_LENGTH).verifying(
        airlineNameCharError,
        airlineName => airlineName.forall(isValidAirlineNameChar) && !"".equals(airlineName.trim())).verifying(
        "This airline name is not available",
        airlineName => !AirlineSource.loadAllAirlines(false).map { _.name.toLowerCase().replaceAll("\\s", "") }.contains(airlineName.replaceAll("\\s", "").toLowerCase())
      )
    )
    // The mapping signature doesn't match the User case class signature,
    // so we have to define custom binding/unbinding functions
    {
      // Binding: Create a User from the mapping result (ignore the second password and the accept field)
      (username, email, passwords, recaptureToken, airlineName) => NewUser(username.trim, passwords._1, email.trim, recaptureToken, airlineName.trim)
    } 
    {
      // Unbinding: Create the mapping values from an existing User value
      user => Some(user.username, user.email, (user.password, ""), "", user.airlineName)
    }
  )
  
  /**
   * Display an empty form.
   */
  def form = Action { implicit request =>
    Ok(html.signup(signupForm))
  }

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
 * Handle form submission (legacy form POST).
 */
  def submit = Action { implicit request =>
    signupForm.bindFromRequest().fold(
      errors => BadRequest(html.signup(errors)), { userInput =>
          val user = User(userInput.username, userInput.email, Calendar.getInstance, Calendar.getInstance, UserStatus.ACTIVE, level = 0, None, List.empty)
          UserSource.saveUser(user)
          Authentication.createUserSecret(userInput.username, userInput.password)

          val newAirline = Airline(userInput.airlineName)
          newAirline.setMinimumRenewalBalance(300000)
          AirlineSource.saveAirlines(List(newAirline))
          AirlineSource.saveAirlineCode(newAirline.id, newAirline.getDefaultAirlineCode())
          UserSource.setUserAirline(user, newAirline)

          AirlineSource.saveAirplaneRenewal(newAirline.id, 40)

          Redirect("/").withCookies(Cookie("sessionActive", "true", httpOnly = false, maxAge = Some(2592000))).withSession("userToken" -> SessionUtil.addUserId(user.id))
      }
    )
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
        } else if (UserSource.loadUsersByCriteria(List.empty).exists(_.userName.equalsIgnoreCase(username))) {
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

          val newAirline = Airline(airlineName)
          newAirline.setMinimumRenewalBalance(300000)
          AirlineSource.saveAirlines(List(newAirline))
          AirlineSource.saveAirlineCode(newAirline.id, newAirline.getDefaultAirlineCode())
          UserSource.setUserAirline(user, newAirline)

          AirlineSource.saveAirplaneRenewal(newAirline.id, 40)

          Ok(Json.obj(
            "id" -> user.id,
            "userName" -> username,
            "airlineIds" -> Json.arr(newAirline.id)
          )).withCookies(Cookie("sessionActive", "true", httpOnly = false, maxAge = Some(2592000)))
            .withSession("userToken" -> SessionUtil.addUserId(user.id))
        }
    }
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
