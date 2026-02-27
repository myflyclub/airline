package controllers

import java.util.UUID
import com.patson.Authentication
import com.patson.data.UserSource
import com.patson.model._
import javax.inject.{Inject, Singleton}
import models._
import play.api.data.Forms._
import play.api.data._
import play.api.mvc._
import play.api.Configuration
import services.EmailService // Import the new service

@Singleton
class AccountApplication @Inject()(
    cc: ControllerComponents, 
    emailService: EmailService, // Inject the EmailService
    config: Configuration       // Inject Play's Configuration safely
) extends AbstractController(cc) with play.api.i18n.I18nSupport {

  val form: Form[PasswordReset] = Form(
    
    // Define a mapping that will handle User values
    mapping(
      "resetToken" -> text,
      "password" -> tuple(
        "main" -> text(minLength = 4),
        "confirm" -> text
      ).verifying(
        // Add an additional constraint: both passwords must match
        "Passwords don't match", passwords => passwords._1 == passwords._2
      )
    )
    // The mapping signature doesn't match the User case class signature,
    // so we have to define custom binding/unbinding functions
    {
      // Binding: Create a User from the mapping result (ignore the second password and the accept field)
      (token, passwords) => PasswordReset(token, passwords._1) 
    } 
    {
      // Unbinding: Create the mapping values from an existing User value
      passwordReset => Some(passwordReset.token, (passwordReset.password, ""))
    }
  )
  
  val forgotIdForm : Form[ForgotId] = Form(
    
    // Define a mapping that will handle User values
    mapping(
      "email" -> text/*.verifying(
        "Email address is not found",  
        email => !(UserSource.loadUsersByCriteria(List(("email", email))).isEmpty)
      )*/
    )
    // The mapping signature doesn't match the User case class signature,
    // so we have to define custom binding/unbinding functions
    {
      // Binding: Create a User from the mapping result (ignore the second password and the accept field)
      (email) => ForgotId(email) 
    } 
    {
      // Unbinding: Create the mapping values from an existing User value
      forgotId => Some(forgotId.email)
    }
  )
  
  val forgotPasswordForm : Form[ForgotPassword] = Form(
    
    // Define a mapping that will handle User values
    mapping(
      "userName" -> text.verifying(
        "User Name is not found",  
        userName => UserSource.loadUserByUserName(userName).isDefined
      ),
      "email" -> text
    )
    // The mapping signature doesn't match the User case class signature,
    // so we have to define custom binding/unbinding functions
    {
      // Binding: Create a User from the mapping result (ignore the second password and the accept field)
      (userName, email) => ForgotPassword(userName, email) 
    } 
    {
      // Unbinding: Create the mapping values from an existing User value
      forgotPassword => Some(forgotPassword.userName, forgotPassword.email)
    }
  )
  
  /**
   * Display an empty form.
   */
  def passwordResetForm(resetToken : String) = Action { implicit request =>
    println("token is " + resetToken)
    UserSource.loadResetUser(resetToken) match {
    case Some(username) => {
      Ok(views.html.passwordReset(form.fill(PasswordReset(resetToken, ""))))
    }
      case None => Forbidden
    }
    
    
  }
  
  def forgotId() = Action { implicit request =>
    Ok(views.html.forgotId(forgotIdForm.fill(ForgotId(""))))
  }
  
  def forgotPassword() = Action { implicit request =>
    Ok(views.html.forgotPassword(forgotPasswordForm.fill(ForgotPassword("", ""))))
  }  
  
  /**
   * Handle form submission.
   */
  def passwordResetSubmit = Action { implicit request =>
    form.bindFromRequest().fold(
      // Form has errors, redisplay it
      errors => {
        println(errors)
        BadRequest(views.html.passwordReset(errors))
      }, 
      userInput =>
          UserSource.loadResetUser(userInput.token) match {
            case Some(username) =>
              println("Resetting user for " + username)
              Authentication.createUserSecret(username, userInput.password)
              UserSource.deleteResetUser(userInput.token)
              Redirect("/")
            case None =>
              println("TOKEN " + userInput.token)
              Forbidden
          }
    )
  }
  
  /**
   * Handle form submission.
   */
  def forgotIdSubmit = Action { implicit request =>
    forgotIdForm.bindFromRequest().fold(
      errors => {
        println(errors)
        BadRequest(views.html.forgotId(errors))
      }, 
      userInput => {
          val users = UserSource.loadUsersByCriteria(List(("email", userInput.email)))
          if (users.size > 0) {
            println("Sending email for forgot ID " + users)
            emailService.sendEmail(userInput.email, "Forgot User Name from airline-club.com", getForgotIdMessage(users))
          } else {
            println("Sending email for forgot ID but email " + userInput.email + " has no account!")
          }
          Ok(views.html.checkEmail())
      }
    )
  }
  
   def forgotPasswordSubmit = Action { implicit request =>
    forgotPasswordForm.bindFromRequest().fold(
      errors => {
        println(errors)
        BadRequest(views.html.forgotPassword(errors))
      }, 
      userInput => {
          val user = UserSource.loadUserByUserName(userInput.userName).get
          if (user.email == userInput.email) {
            println("Sending email for reset password " + user)
            val scheme = if (request.secure) "https://" else "http://"
            val host = request.headers.get("X-Forwarded-Host").getOrElse(request.host)

            val baseUrl = s"$scheme$host/password-reset"
            emailService.sendEmail(user.email, "Reset password for airline-club.com", getResetPasswordMessage(user, baseUrl))
          } else {
            println("Want to reset password for " + user.userName + " but email does not match!")
          }
          
          Ok(views.html.checkEmail())
      }
    )
  }

  def getForgotIdMessage(users : List[User]) = {
     val message =  new StringBuilder("Your User Name(s) registered with this email address : \r\n")
     users.foreach { user =>
       message ++= (user.userName + "\r\n")
     }
    
    message.toString()
  }
  
  def getResetPasswordMessage(user : User, baseUrl: String) = {
    val resetLink = generateResetLink(user, baseUrl)
    "Please follow this link \r\n" + resetLink + "\r\nto reset your password."
  }
  
  def generateResetLink(user : User, baseUrl : String) = {
    val resetToken = UUID.randomUUID().toString()
    
    UserSource.saveResetUser(user.userName, resetToken)

    s"$baseUrl?resetToken=" + resetToken
  }
}