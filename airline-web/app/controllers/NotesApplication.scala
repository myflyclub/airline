package controllers

import com.patson.data.NotesSource
import controllers.AuthenticationObject.{Authenticated, AuthenticatedAirline}
import play.api.mvc._
import play.api.libs.json.{JsValue, Json, Writes}
import org.apache.commons.text.StringEscapeUtils

import javax.inject.Inject
import scala.util.{Failure, Success, Try}

class NotesApplication @Inject()(cc: ControllerComponents) extends AbstractController(cc) {

    implicit object NoteWrites extends Writes[NotesSource.UserNote] {
      def writes(userNote: NotesSource.UserNote): JsValue = {
        Json.obj(
            "id" -> userNote.id,
            "note" -> StringEscapeUtils.escapeHtml4(userNote.note)
        )
      }
    }
    
    implicit object AirlineNotesWrites extends Writes[NotesSource.UserNotes] {
      def writes(notes: NotesSource.UserNotes): JsValue = {
        Json.obj(
            "airlineNotes" -> Json.toJson(notes.airlineNotes.map(StringEscapeUtils.escapeHtml4)),
            "airportNotes" -> Json.toJson(notes.airportNotes),
            "linkNotes" -> Json.toJson(notes.linkNotes)
        )
      }
    }

  /**
    * Retrieve all notes for a given airlineId.
    * @param airlineId The airline ID
    * @return JSON response with the list of notes
    */
  def getNotesByAirline(airlineId: Int) = AuthenticatedAirline(airlineId) { request =>
    Try {
      val notes = NotesSource.loadNotesByAirline(airlineId)
      Ok(Json.toJson(notes))
    } match {
      case Success(result) => result
      case Failure(exception) =>
        BadRequest(Json.obj("error" -> s"Failed to retrieve notes: ${exception.getMessage}"))
    }
  }

  /**
    * Endpoint to save a note to the NOTES_AIRLINE_TABLE.
    * @param airlineId The airline ID
    * @param note The note text
    * @return JSON response indicating success or failure
    */
  def saveNoteToAirline(airlineId: Int) = AuthenticatedAirline(airlineId) { request =>
    val note = request.body.asInstanceOf[AnyContentAsJson].json.\("note").as[String]
    val sanitizedNote = StringEscapeUtils.escapeEcmaScript(note)
    Try {
      NotesSource.saveNoteToAirlineTable(airlineId, sanitizedNote)
      Ok(Json.obj("success" -> true))
    } match {
      case Success(result) => result
      case Failure(exception) =>
        BadRequest(Json.obj("error" -> s"Failed to save note: ${exception.getMessage}"))
    }
  }

  /**
    * Endpoint to save a note to the NOTES_AIRPORT_TABLE.
    * @param airlineId The airline ID
    * @param airportId The link ID
    * @param note The note text
    * @return JSON response indicating success or failure
    */
  def saveNoteToAirport(airlineId: Int, airportId: Int) = AuthenticatedAirline(airlineId) { request =>
    val note = request.body.asInstanceOf[AnyContentAsJson].json.\("note").as[String]
    val sanitizedNote = StringEscapeUtils.escapeEcmaScript(note)
    Try {
      NotesSource.saveNoteToAirportTable(airportId, airlineId, sanitizedNote)
      Ok(Json.obj("success" -> true))
    } match {
      case Success(result) => result
      case Failure(exception) =>
        BadRequest(Json.obj("error" -> s"Failed to save note: ${exception.getMessage}"))
    }
  }

  /**
    * Endpoint to save a note to the NOTES_LINK_TABLE.
    * @param airlineId The airline ID
    * @param linkId The airport ID
    * @param note The note text
    * @return JSON response indicating success or failure
    */
  def saveNoteToLink(airlineId: Int, linkId: Int) = AuthenticatedAirline(airlineId) { request =>
    val note = request.body.asInstanceOf[AnyContentAsJson].json.\("note").as[String]
    val sanitizedNote = StringEscapeUtils.escapeEcmaScript(note)
    Try {
      NotesSource.saveNoteToLinkTable(linkId, airlineId, sanitizedNote)
      Ok(Json.obj("success" -> true))
    } match {
      case Success(result) => result
      case Failure(exception) =>
        BadRequest(Json.obj("error" -> s"Failed to save note: ${exception.getMessage}"))
    }
  }
}