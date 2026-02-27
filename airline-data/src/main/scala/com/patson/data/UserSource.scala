package com.patson.data

import com.patson.model.{UserModifier, _}
import com.patson.data.Constants._

import scala.collection.mutable.ListBuffer
import java.util.Calendar
import java.text.SimpleDateFormat
import java.sql.Statement
import java.util.Date
import com.patson.util.{AirlineCache, UserCache}

import scala.collection.mutable
import scala.util.Using

object UserSource {
  val dateFormat = new ThreadLocal[SimpleDateFormat]() {
    override def initialValue() = {
      new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
    }
  }

  def loadUserSecret(userName : String) : Option[UserSecret] = {
    Using.resource(Meta.getConnection()) { connection =>
      Using.resource(connection.prepareStatement("SELECT * FROM " + USER_SECRET_TABLE + " WHERE user_name = ?")) { preparedStatement =>
        preparedStatement.setString(1, userName)
        Using.resource(preparedStatement.executeQuery()) { resultSet =>
          if (resultSet.next()) {
            Some(UserSecret(resultSet.getString("user_name"), resultSet.getString("digest"), resultSet.getString("salt")))
          } else {
            None
          }
        }
      }
    }
  }

  def saveUserSecret(userSecret : UserSecret) : Boolean = {
    Using.resource(Meta.getConnection()) { connection =>
      Using.resource(connection.prepareStatement("REPLACE INTO " + USER_SECRET_TABLE + "(user_name, digest, salt) VALUES(?,?,?)")) { statement =>
        statement.setString(1, userSecret.userName)
        statement.setString(2, userSecret.digest)
        statement.setString(3, userSecret.salt)
        statement.executeUpdate()
        true
      }
    }
  }


  def loadUsersByCriteria(criteria : List[(String, Any)]) : List[User] = {
    // Load modifiers first with their own connection, before opening the main connection
    val modifiersByUserId : Map[Int, List[UserModifier.Value]] = UserSource.loadUserModifiers()

    Using.resource(Meta.getConnection()) { connection =>
      var queryString = "SELECT u.*, ua.* FROM " +  USER_TABLE + " u LEFT JOIN " + USER_AIRLINE_TABLE + " ua ON u.user_name = ua.user_name"

      if (!criteria.isEmpty) {
        queryString += " WHERE "
        for (i <- 0 until criteria.size - 1) {
          queryString += criteria(i)._1 + " = ? AND "
        }
        queryString += criteria.last._1 + " = ?"
      }

      val userList = Using.resource(connection.prepareStatement(queryString)) { preparedStatement =>
        for (i <- 0 until criteria.size) {
          preparedStatement.setObject(i + 1, criteria(i)._2)
        }
        Using.resource(preparedStatement.executeQuery()) { resultSet =>
          val userList = scala.collection.mutable.Map[Int, (User, ListBuffer[Int])]() //Map[UserId, (User, List[AirlineId])]

          while (resultSet.next()) {
            val userId = resultSet.getInt("u.id")
            val (user, userAirlines) = userList.getOrElseUpdate(userId, {
              val userName = resultSet.getString("u.user_name")
              val creationTime = Calendar.getInstance()
              creationTime.setTime(dateFormat.get().parse(resultSet.getString("u.creation_time")))
              val lastActiveTime = Calendar.getInstance()
              lastActiveTime.setTime(dateFormat.get().parse(resultSet.getString("u.last_active")))
              val status = UserStatus.withName(resultSet.getString("u.status"))
              val adminStatusObject = resultSet.getObject("u.admin_status")
              val adminStatus = if (adminStatusObject == null) None else Some(AdminStatus.withName(adminStatusObject.asInstanceOf[String]))

              val modifiers = modifiersByUserId.getOrElse(userId, List.empty)
              (User(userName, resultSet.getString("u.email"), creationTime, lastActiveTime, status, level = resultSet.getInt("level"),  adminStatus = adminStatus, modifiers = modifiers, id = userId), ListBuffer[Int]())
            })

            val airlineId = resultSet.getInt("ua.airline")
            if (airlineId != 0) {
              userAirlines += airlineId
            } else {
              println(s"User $user has no airline!")
            }
          }
          userList
        }
      }

      val allAirlineIds : List[Int] = userList.values.map(_._2).flatten.toSet.toList

      //val airlinesMap = AirlineSource.loadAirlinesByIds(allAirlineIds, true).map(airline => (airline.id, airline)).toMap
      val airlinesMap = AirlineCache.getAirlines(allAirlineIds, true)

      userList.values.foreach {
        case(user,userAirlineIds) =>
          user.setAccesibleAirlines(userAirlineIds.map(airlineId => airlinesMap.get(airlineId)).flatten.toList)
      }

      userList.values.map(_._1).toList
    }
  }


  def loadUserById(id : Int) = {
      val result = loadUsersByCriteria(List(("u.id", id)))
      if (result.isEmpty) None else Some(result(0))
  }

  def loadUserByUserName(userName : String) = {
      val result = loadUsersByCriteria(List(("u.user_name", userName)))
      if (result.isEmpty) None else Some(result(0))
  }

  def loadUserByAirlineId(airlineId : Int) = {
    val result = loadUsersByCriteria(List(("ua.airline", airlineId)))
    if (result.isEmpty) None else Some(result(0))
  }

  def saveUser(user: User) = {
    Using.resource(Meta.getConnection()) { connection =>
      Using.resource(connection.prepareStatement("INSERT INTO " + USER_TABLE + "(user_name, email, status) VALUES(?,?,?)", Statement.RETURN_GENERATED_KEYS)) { preparedStatement =>
        preparedStatement.setString(1, user.userName)
        preparedStatement.setString(2, user.email)
        preparedStatement.setString(3, user.status.toString)
        preparedStatement.executeUpdate()

        Using.resource(preparedStatement.getGeneratedKeys) { generatedKeys =>
          if (generatedKeys.next()) {
            val generatedId = generatedKeys.getInt(1)
            user.id = generatedId
          }
        }
      }
    }
  }

  def updateUser(user: User) : Boolean = {
    Using.resource(Meta.getConnection()) { connection =>
      Using.resource(connection.prepareStatement("UPDATE " + USER_TABLE + " SET email = ?, status = ?, level = ? WHERE id = ?")) { preparedStatement =>
        preparedStatement.setString(1, user.email)
        preparedStatement.setString(2, user.status.toString)
        preparedStatement.setInt(3, user.level)
        preparedStatement.setInt(4, user.id)
        val updateCount = preparedStatement.executeUpdate()
        UserCache.invalidateUser(user.id)
        updateCount == 1
      }
    }
  }

  def updateUserLastActive(user: User) = {
    Using.resource(Meta.getConnection()) { connection =>
      Using.resource(connection.prepareStatement("UPDATE " + USER_TABLE + " SET last_active = ? WHERE id = ?")) { preparedStatement =>
        preparedStatement.setTimestamp(1, new java.sql.Timestamp(new Date().getTime()))
        preparedStatement.setInt(2, user.id)
        preparedStatement.executeUpdate()
        UserCache.invalidateUser(user.id)
      }
    }
  }

  def setUserAirline(user: User, airline : Airline) = {
    Using.resource(Meta.getConnection()) { connection =>
      Using.resource(connection.prepareStatement("INSERT INTO " + USER_AIRLINE_TABLE + "(user_name, airline) VALUES(?,?)")) { preparedStatement =>
        preparedStatement.setString(1, user.userName)
        preparedStatement.setInt(2, airline.id)
        val updateCount = preparedStatement.executeUpdate()
        updateCount == 1
      }
    }
  }

  def deleteGeneratedUsers() = {
    Using.resource(Meta.getConnection()) { connection =>
      Using.resource(connection.prepareStatement("DELETE FROM " + USER_TABLE + " WHERE email = ?")) { preparedStatement =>
        preparedStatement.setString(1, "bot")
        preparedStatement.executeUpdate()
      }
    }
  }

  def saveResetUser(username : String, resetToken : String) = {
    Using.resource(Meta.getConnection()) { connection =>
      Using.resource(connection.prepareStatement("REPLACE INTO " + RESET_USER_TABLE + "(user_name, token) VALUES(?,?)")) { preparedStatement =>
        preparedStatement.setString(1, username)
        preparedStatement.setString(2, resetToken)
        val updateCount = preparedStatement.executeUpdate()
        updateCount == 1
      }
    }
  }

  def loadResetUser(resetToken : String) : Option[String] = {
    Using.resource(Meta.getConnection()) { connection =>
      Using.resource(connection.prepareStatement("SELECT * FROM " + RESET_USER_TABLE + " WHERE token = ?")) { preparedStatement =>
        preparedStatement.setString(1, resetToken)
        Using.resource(preparedStatement.executeQuery()) { resultSet =>
          if (resultSet.next()) {
            Some(resultSet.getString("user_name"))
          } else {
            None
          }
        }
      }
    }
  }

  def deleteResetUser(resetToken : String) = {
    Using.resource(Meta.getConnection()) { connection =>
      Using.resource(connection.prepareStatement("DELETE FROM " + RESET_USER_TABLE + " WHERE token = ?")) { preparedStatement =>
        preparedStatement.setString(1, resetToken)
        val updateCount = preparedStatement.executeUpdate()
        updateCount == 1
      }
    }
  }

  def deleteUserModifiers(userId : Int) = {
    Using.resource(Meta.getConnection()) { connection =>
      Using.resource(connection.prepareStatement(s"DELETE FROM $USER_MODIFIER_TABLE WHERE user = ?")) { preparedStatement =>
        preparedStatement.setInt(1, userId)
        preparedStatement.executeUpdate()
      }
    }
  }



  def saveUserModifier(userId : Int, modifier : UserModifier.Value) = {
    val cycle = CycleSource.loadCycle()
    Using.resource(Meta.getConnection()) { connection =>
      Using.resource(connection.prepareStatement(s"REPLACE INTO $USER_MODIFIER_TABLE (user, modifier_name, creation) VALUES(?, ?, ?)")) { preparedStatement =>
        preparedStatement.setInt(1, userId)
        preparedStatement.setString(2, modifier.toString)
        preparedStatement.setInt(3, cycle)
        preparedStatement.executeUpdate()
        UserCache.invalidateUser(userId)
      }
    }
  }


  def loadUserModifiers() : Map[Int, List[UserModifier.Value]] = { //_1 is user Id
    Using.resource(Meta.getConnection()) { connection =>
      Using.resource(connection.prepareStatement("SELECT * FROM " + USER_MODIFIER_TABLE)) { preparedStatement =>
        Using.resource(preparedStatement.executeQuery()) { resultSet =>
          val result = mutable.HashMap[Int, ListBuffer[UserModifier.Value]]()
          while (resultSet.next()) {
            val userModifier = UserModifier.withName(resultSet.getString("modifier_name"))
            val modifiers = result.getOrElseUpdate(resultSet.getInt("user"), ListBuffer())
            modifiers.append(userModifier)
          }
          result.view.mapValues(_.toList).toMap
        }
      }
    }
  }

  def loadUserModifierByUserId(userId : Int) : List[UserModifier.Value] = {
    Using.resource(Meta.getConnection()) { connection =>
      Using.resource(connection.prepareStatement("SELECT * FROM " + USER_MODIFIER_TABLE + " WHERE user = ?")) { preparedStatement =>
        preparedStatement.setInt(1, userId)
        Using.resource(preparedStatement.executeQuery()) { resultSet =>
          val result = ListBuffer[UserModifier.Value]()
          while (resultSet.next()) {
            val userModifier = UserModifier.withName(resultSet.getString("modifier_name"))
            result.append(userModifier)
          }
          result.toList
        }
      }
    }
  }
}
