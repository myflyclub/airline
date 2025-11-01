package com.patson.data

import com.patson.data.Constants._

import java.sql.Statement
import scala.collection.mutable.ListBuffer

object NotesSource {

  case class UserNotes(airlineNotes: List[String], airportNotes: List[UserNote], linkNotes: List[UserNote])
  case class UserNote(id: Int, note: String)

  /**
    * Retrieves all notes for a given airlineId across all three notes tables.
    * @param airlineId
    * @return List of notes
    */
  def loadNotesByAirline(airlineId: Int): UserNotes = {
    val connection = Meta.getConnection()
    try {
      val airlineNotes = ListBuffer[String]()
      val airportNotes = ListBuffer[UserNote]()
      val linkNotes = ListBuffer[UserNote]()

      // Query NOTES_AIRLINE_TABLE
      val airlineStatement = connection.prepareStatement(s"SELECT * FROM $NOTES_AIRLINE_TABLE WHERE airline = ?")
      airlineStatement.setInt(1, airlineId)
      val airlineResultSet = airlineStatement.executeQuery()
      while (airlineResultSet.next()) {
        airlineNotes += airlineResultSet.getString("notes")
      }
      airlineResultSet.close()
      airlineStatement.close()

      // Query NOTES_AIRPORT_TABLE
      val airportStatement = connection.prepareStatement(s"SELECT * FROM $NOTES_AIRPORT_TABLE WHERE airline = ?")
      airportStatement.setInt(1, airlineId)
      val airportResultSet = airportStatement.executeQuery()
      while (airportResultSet.next()) {
        airportNotes.append(UserNote(airportResultSet.getInt("airport"), airportResultSet.getString("notes")))
      }
      airportResultSet.close()
      airportStatement.close()

      // Query NOTES_LINK_TABLE
      val linkStatement = connection.prepareStatement(s"SELECT * FROM $NOTES_LINK_TABLE WHERE airline = ?")
      linkStatement.setInt(1, airlineId)
      val linkResultSet = linkStatement.executeQuery()
      while (linkResultSet.next()) {
        linkNotes.append(UserNote(linkResultSet.getInt("link"), linkResultSet.getString("notes")))
      }
      linkResultSet.close()
      linkStatement.close()

      UserNotes(airlineNotes.toList, airportNotes.toList, linkNotes.toList)
    } finally {
      connection.close()
    }
  }

  /**
    * Saves a note to the NOTES_AIRLINE_TABLE.
    * @param airlineId The airline ID
    * @param note The note text
    */
  def saveNoteToAirlineTable(airlineId: Int, note: String): Unit = {
    val connection = Meta.getConnection()
    try {
      val statement = connection.prepareStatement(s"REPLACE INTO $NOTES_AIRLINE_TABLE (airline, notes) VALUES(?, ?)")
      statement.setInt(1, airlineId)
      statement.setString(2, note)
      statement.executeUpdate()
      statement.close()
    } finally {
      connection.close()
    }
  }

  /**
    * Saves a note to NOTES_LINK.
    * @param linkId The link ID
    * @param airlineId The airline ID
    * @param note The note text
    */
  def saveNoteToLinkTable(linkId: Int, airlineId: Int, note: String): Unit = {
    val connection = Meta.getConnection()
    try {
      val statement = connection.prepareStatement(s"REPLACE INTO $NOTES_LINK_TABLE (link, airline, notes) VALUES(?, ?, ?)")
      statement.setInt(1, linkId)
      statement.setInt(2, airlineId)
      statement.setString(3, note)
      statement.executeUpdate()
      statement.close()
    } finally {
      connection.close()
    }
  }

  /**
    * Saves a note to the NOTES_AIRPORT.
    * @param airportId The airport ID
    * @param airlineId The airline ID
    * @param note The note text
    */
  def saveNoteToAirportTable(airportId: Int, airlineId: Int, note: String): Unit = {
    val connection = Meta.getConnection()
    try {
      val statement = connection.prepareStatement(s"REPLACE INTO $NOTES_AIRPORT_TABLE (airport, airline, notes) VALUES(?, ?, ?)")
      statement.setInt(1, airportId)
      statement.setInt(2, airlineId)
      statement.setString(3, note)
      statement.executeUpdate()
      statement.close()
    } finally {
      connection.close()
    }
  }
}