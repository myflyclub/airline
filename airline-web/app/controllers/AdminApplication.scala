package controllers

import com.patson.data.{AdminSource, AirlineSource, CycleSource, IpSource, UserSource, UserUuidSource}
import com.patson.stream.CycleCompleted
import websocket.ActorCenter
import com.patson.model.UserStatus.UserStatus
import com.patson.model.{Airline, AirlineLedgerEntry, AirlineModifier, AirlineModifierType, BannerLoyaltyAirlineModifier, LedgerType, User, UserModifier, UserStatus}
import com.patson.util.{AirlineCache, AirplaneOwnershipCache, AirportCache, AirportStatisticsCache, AllianceCache, CountryCache, UserCache}
import controllers.AuthenticationObject.Authenticated
import controllers.GoogleImageUtil.{AirportKey, CityKey}
import play.api.libs.json.{JsArray, JsObject, Json}
import play.api.mvc.Security.AuthenticatedRequest
import play.api.mvc._
import websocket.Broadcaster

import java.text.DateFormat
import java.util.Calendar
import javax.inject.Inject


class AdminApplication @Inject()(cc: ControllerComponents) extends AbstractController(cc) {

  def adminMultiAction(action : String) = Authenticated { implicit request =>
    val userIds = request.body.asInstanceOf[AnyContentAsJson].json.\("userIds").get.asInstanceOf[JsArray].value.map(_.as[Int])
    doAdminActions(request, action, userIds.toList)
  }

  def doAdminActions(request : AuthenticatedRequest[AnyContent, User], action : String, userIds : List[Int]) : Result = {
    userIds.foreach { userId =>
      doAdminAction(request, action, userId) match {
        case Left(errorResult) => return errorResult
        case Right(okResult) =>
      }
    }
    return Ok(Json.obj("userIds" -> userIds))
  }


  def adminAction(action : String, targetUserId : Int) = Authenticated { implicit request =>
    doAdminAction(request, action, targetUserId) match {
      case Left(errorResult) => errorResult
      case Right(okResult) => okResult
    }
  }

  def addAirlineModifier(modifier : AirlineModifierType.Value, airlines : List[Airline]) = {
    val currentCycle = CycleSource.loadCycle()
    airlines.foreach { airline =>
      AirlineSource.saveAirlineModifier(airline.id, AirlineModifier.fromValues(modifier, currentCycle, None, Map.empty))
    }
  }

  def removeAirlineModifier(modifierType : AirlineModifierType.Value, airlines : List[Airline]) = {
    airlines.foreach { airline =>
      AirlineSource.deleteAirlineModifier(airline.id, modifierType)
    }
  }

  def doAdminAction(request : AuthenticatedRequest[AnyContent, User], action : String, targetUserId : Int) : Either[Result, Result] = {
    val adminUser = request.user
    if (adminUser.isAdmin) {
      AdminSource.saveLog(action, adminUser.userName, targetUserId)

      UserSource.loadUserById(targetUserId) match {
        case Some(targetUser) =>
          if (targetUser.isAdmin && adminUser.adminStatus.get.id <= targetUser.adminStatus.get.id) {
            println(s"ADMIN - Forbidden action $action user ${targetUser.userName} as the target user is ${targetUser.adminStatus} while current user is ${adminUser.adminStatus}")
            Left(BadRequest(s"ADMIN - Forbidden action $action user ${targetUser.userName} as the target user is ${targetUser.adminStatus} while current user is ${adminUser.adminStatus}"))
          } else {
            action match {
              case "warn" =>
                setUserModifier(UserModifier.WARNED, targetUser)
                Right(Ok(Json.obj("action" -> action)))
              case "ban" =>
                setUserModifier(UserModifier.BANNED, targetUser)
                Right(Ok(Json.obj("action" -> action)))
              case "ban-chat" =>
                setUserModifier(UserModifier.CHAT_BANNED, targetUser)
                Right(Ok(Json.obj("action" -> action)))
              case "nerf" =>
                addAirlineModifier(AirlineModifierType.NERFED, targetUser.getAccessibleAirlines())
                Right(Ok(Json.obj("action" -> action)))
              case "ban-reset" =>
                setUserModifier(UserModifier.BANNED, targetUser)

                targetUser.getAccessibleAirlines().foreach { airline =>
                  Airline.resetAirline(airline.id, newBalance = 0, true) match {
                    case Some(airline) =>
                      Right(Ok(Json.obj("action" -> action)))
                    case None => Left(NotFound)
                  }
                }

                Right(Ok(Json.obj("action" -> action)))
              case "restore" =>
                clearUserModifiers(targetUser)
                removeAirlineModifier(AirlineModifierType.NERFED, targetUser.getAccessibleAirlines())
                Right(Ok(Json.obj("action" -> action)))
              case "set-user-level" =>
                if (!adminUser.isSuperAdmin) {
                  Left(BadRequest(s"ADMIN - Forbidden action $action user ${targetUser.userName} as the current user is ${adminUser.adminStatus}"))
                }

                //add the new one
                val inputJson = request.body.asJson.get.asInstanceOf[JsObject]
                val level = inputJson("level").as[Int]

                UserSource.updateUser(targetUser.copy(level = level))
                Right(Ok(Json.obj("action" -> action)))
              case "set-banner-winner" =>
                if (!adminUser.isSuperAdmin) {
                  Left(BadRequest(s"ADMIN - Forbidden action $action user ${targetUser.userName} as the current user is ${adminUser.adminStatus}"))
                }

                //add the new one
                val inputJson = request.body.asJson.get.asInstanceOf[JsObject]
                val airlineId = inputJson("airlineId").as[Int]
                val strength = inputJson("strength").as[Int]

                AirlineSource.saveAirlineModifier(airlineId, BannerLoyaltyAirlineModifier(strength, CycleSource.loadCycle()))
                Right(Ok(Json.obj("action" -> action)))
              case "switch" =>
                if (adminUser.isSuperAdmin) {

                  request.session.get("userToken") match {
                    case Some(userToken) =>
                      SessionUtil.getUserId(userToken) match {
                        case Some(userId) => Right(Ok(Json.obj("action" -> action)).withSession("userToken" -> SessionUtil.addUserId(targetUserId), "adminToken" -> userToken))
                        case None => Left(BadRequest(s"Invalid token (admin) $userToken"))
                      }
                    case None => Left(BadRequest("no current admin token"))
                  }
                } else {
                  Left(Forbidden("Not a super admin user"))
                }
              case "inject-money" =>
                val inputJson = request.body.asJson.get.asInstanceOf[JsObject]
                val airlineId = inputJson("airlineId").as[Int]
                val amount = inputJson("amount").as[Long]
                val cycle = CycleSource.loadCycle()
                AirlineSource.saveLedgerEntry(AirlineLedgerEntry(airlineId, cycle, LedgerType.ADMIN_INJECT, amount, Some(s"Admin inject by ${adminUser.userName}")))
                Right(Ok(Json.obj("action" -> action)))
              case "inject-ap" =>
                val inputJson = request.body.asJson.get.asInstanceOf[JsObject]
                val airlineId = inputJson("airlineId").as[Int]
                val amount = inputJson("amount").as[Double]
                AirlineSource.adjustAirlineActionPoints(airlineId, amount)
                Right(Ok(Json.obj("action" -> action)))
              case _ =>
                println(s"unknown admin action $action")
                Left(BadRequest(Json.obj("action" -> action)))
            }
          }
        case None => Left(BadRequest(s"ADMIN - Failed action $action as User $targetUserId is not found"))
      }

    } else {
      println(s"Non admin ${adminUser} tried to access admin operations!!")
      Left(Forbidden("Not an admin user"))
    }
  }

  def getUserIps(userId : Int) = Authenticated { implicit request =>
    if (request.user.isAdmin) {
      val cutoff = Calendar.getInstance()
      cutoff.add(Calendar.DATE, -30)

      Ok(Json.toJson(IpSource.loadUserIps(userId).toList.sortBy(_._2.occurrence)(Ordering[Int].reverse).filter(_._2.lastUpdated.after(cutoff.getTime)).map {
          case (ip, ipDetails) => (ip, ipDetails.occurrence, ipDetails.lastUpdated)
      }))
    } else {
      println(s"Non admin ${request.user} tried to access admin operations!!")
      Forbidden("Not an admin user")
    }
  }

  def getAirlinesByIp(ip : String) = Authenticated { implicit request =>
    if (request.user.isAdmin) {
      var result = Json.arr()
      IpSource.loadUsersByIp(ip).foreach {
        case (user, ipDetails) =>
          user.getAccessibleAirlines().foreach { airline =>
            val airlineModifiers = AirlineSource.loadAirlineModifierByAirlineId(airline.id)
            var userJson = Json.obj(
              "airlineName" -> airline.name,
              "airlineId" -> airline.id,
              "userId" -> user.id,
              "username" -> user.userName,
              "userStatus" -> user.status,
              "userLevel" -> user.level,
              "userModifiers" -> user.modifiers,
              "airlineModifiers" -> airlineModifiers.map(_.modifierType),
              "lastUpdated" -> DateFormat.getInstance().format(ipDetails.lastUpdated),
              "occurrence" -> ipDetails.occurrence,
            )
            airline.getHeadQuarter().foreach(hq => userJson = userJson + ("hqAirport" -> Json.toJson(hq.airport)))
            result = result.append(userJson)
          }

      }

      Ok(result)
    } else {
      println(s"Non admin ${request.user} tried to access admin operations!!")
      Forbidden("Not an admin user")
    }
  }

  def getUserUuids(userId : Int) = Authenticated { implicit request =>
    if (request.user.isAdmin) {
      val cutoff = Calendar.getInstance()
      cutoff.add(Calendar.DATE, -30)

      Ok(Json.toJson(UserUuidSource.loadUserUuids(userId).toList.sortBy(_._2.occurrence)(Ordering[Int].reverse).filter(_._2.lastUpdated.after(cutoff.getTime)).map {
        case (ip, ipDetails) => (ip, ipDetails.occurrence, ipDetails.lastUpdated)
      }))
    } else {
      println(s"Non admin ${request.user} tried to access admin operations!!")
      Forbidden("Not an admin user")
    }
  }

  def getAirlinesByUuid(uuid : String) = Authenticated { implicit request =>
    if (request.user.isAdmin) {
      var result = Json.arr()
      UserUuidSource.loadUsersByUuid(uuid).foreach {
        case (user, ipDetails) =>
          user.getAccessibleAirlines().foreach { airline =>
            val airlineModifiers = AirlineSource.loadAirlineModifierByAirlineId(airline.id)
            var userJson = Json.obj(
              "airlineName" -> airline.name,
              "airlineId" -> airline.id,
              "userId" -> user.id,
              "username" -> user.userName,
              "userLevel" -> user.level,
              "userStatus" -> user.status,
              "userModifiers" -> user.modifiers,
              "airlineModifiers" -> airlineModifiers.map(_.modifierType),
              "lastUpdated" -> DateFormat.getInstance().format(ipDetails.lastUpdated),
              "occurrence" -> ipDetails.occurrence,
            )
            airline.getHeadQuarter().foreach(hq => userJson = userJson + ("hqAirport" -> Json.toJson(hq.airport)))
            result = result.append(userJson)
          }

      }

      Ok(result)
    } else {
      println(s"Non admin ${request.user} tried to access admin operations!!")
      Forbidden("Not an admin user")
    }
  }


  def invalidateCustomization(airlineId : Int) = Authenticated { implicit request =>
    if (request.user.isAdmin) {
      LiveryUtil.deleteLivery(airlineId)
      AirlineSource.saveSlogan(airlineId, "")
      Ok(Json.obj("result" -> airlineId))
    } else {
      println(s"Non admin ${request.user} tried to access admin operations!!")
      Forbidden("Not an admin user")
    }
  }

  def invalidateImage(imageType : String, airportId : Int) = Authenticated { implicit request =>
    if (request.user.isAdmin) {
      AirportCache.getAirport(airportId) match {
        case Some(airport) =>
          val key : controllers.GoogleImageUtil.Key =
            if (imageType == "airport") {
              new AirportKey(airport.id, airport.name, airport.latitude, airport.longitude)
            } else {
              new CityKey(airport.id, airport.city, airport.latitude, airport.longitude)
            }
          GoogleImageUtil.invalidate(key)
          Ok(Json.obj("result" -> airport))
        case None => NotFound(s"Airport $airportId not found")
      }


    } else {
      println(s"Non admin ${request.user} tried to access admin operations!!")
      Forbidden("Not an admin user")
    }
  }

  def setUserModifier(userModifier: UserModifier.Value, targetUser: User) = {
    UserSource.saveUserModifier(targetUser.id, userModifier)
    println(s"ADMIN - updated user modifier $userModifier on user ${targetUser.userName}")
  }

  def clearUserModifiers(targetUser: User) = {
    UserSource.deleteUserModifiers(targetUser.id)
    println(s"ADMIN - clear user modifier on user ${targetUser.userName}")
  }

  def sendBroadcastMessage() = Authenticated { implicit request =>
    if (request.user.isSuperAdmin) {
      val message = request.body.asInstanceOf[AnyContentAsJson].json.\("message").as[String]
      Broadcaster.broadcastMessage(message)
      Ok(Json.obj())
    } else {
      println(s"Non admin ${request.user} tried to access admin operations!!")
      Forbidden("Not a super admin user")
    }
  }

  def triggerCycleCompleted() = Authenticated { implicit request =>
    if (request.user.isSuperAdmin) {
      val cycle = CycleSource.loadCycle()
      ActorCenter.localMainActor ! (CycleCompleted(cycle, System.currentTimeMillis()), ())
      println(s"ADMIN - triggerCycleCompleted for cycle $cycle by ${request.user.userName}")
      Ok(Json.obj("cycle" -> cycle))
    } else {
      Forbidden("Not a super admin user")
    }
  }

  def sendAirlineMessage(targetAirlineId : Int) = Authenticated { implicit request =>
    if (request.user.isAdmin) {
      AirlineCache.getAirline(targetAirlineId) match {
        case Some(airline) =>
          val message = request.body.asInstanceOf[AnyContentAsJson].json.\("message").as[String]
          Broadcaster.sendMessage(airline, message)
          Ok(Json.obj())
        case None =>
          NotFound(s"Airline with id $targetAirlineId not found")
      }
    } else {
      println(s"Non admin ${request.user} tried to access admin operations!!")
      Forbidden("Not an admin user")
    }

  }

  private def caffeineStats(cache: com.github.benmanes.caffeine.cache.Cache[_, _], maxSize: Long): JsObject = {
    Json.obj(
      "size" -> cache.estimatedSize(),
      "maxSize" -> maxSize
    )
  }

  def getCacheStats() = Authenticated { implicit request =>
    if (request.user.isAdmin) {
      Ok(Json.obj(
        "airlineCache" -> Json.obj(
          "detailed" -> caffeineStats(AirlineCache.detailedCache, 5000),
          "simple" -> caffeineStats(AirlineCache.simpleCache, 5000)
        ),
        "airportCache" -> Json.obj(
          "detailed" -> caffeineStats(AirportCache.detailedCache, 2500),
          "simple" -> caffeineStats(AirportCache.simpleCache, 5000)
        ),
        "allianceCache" -> Json.obj(
          "detailed" -> caffeineStats(AllianceCache.detailedCache, 1000),
          "simple" -> caffeineStats(AllianceCache.simpleCache, 1000)
        ),
        "userCache" -> caffeineStats(UserCache.simpleCache, 10000),
        "countryCache" -> caffeineStats(CountryCache.simpleCache, 1000),
        "airportStatisticsCache" -> caffeineStats(AirportStatisticsCache.simpleCache, 4000),
        "airplaneOwnershipCache" -> caffeineStats(AirplaneOwnershipCache.simpleCache, 10000),
        "responseCache" -> Json.obj(
          "transit" -> caffeineStats(ResponseCache.transitCache, 4000),
          "airportDetail" -> caffeineStats(ResponseCache.airportDetailCache, 4000),
          "demand" -> caffeineStats(ResponseCache.demandCache, 4000),
          "olympicsDetails" -> caffeineStats(ResponseCache.olympicsDetailsCache, 100),
          "searchRoute" -> caffeineStats(ResponseCache.searchRouteCache, 1000),
          "researchLink" -> caffeineStats(ResponseCache.researchLinkCache, 1000)
        ),
        "sessions" -> controllers.Session.size
      ))
    } else {
      Forbidden("Not an admin user")
    }
  }

  def getPoolStats() = Authenticated { implicit request =>
    if (request.user.isAdmin) {
      val ds = com.patson.data.Meta.dataSource
      Ok(Json.obj(
        "numConnections" -> ds.getNumConnections(),
        "numBusyConnections" -> ds.getNumBusyConnections(),
        "numIdleConnections" -> ds.getNumIdleConnections(),
        "maxPoolSize" -> ds.getMaxPoolSize(),
        "numFailedCheckouts" -> ds.getNumFailedCheckoutsDefaultUser(),
        "threadPoolNumActiveThreads" -> ds.getThreadPoolNumActiveThreads()
      ))
    } else {
      Forbidden("Not an admin user")
    }
  }

  def getRequestStats() = Authenticated { implicit request =>
    if (request.user.isAdmin) {
      val topUsers = RequestStats.getTopUsers(50)
      val total = RequestStats.totalRequests.get()

      import scala.jdk.CollectionConverters._
      Ok(Json.obj(
        "totalRequests" -> total,
        "topUsers" -> topUsers.map { case (userId, count) =>
          Json.obj("userId" -> userId, "requests" -> count)
        },
        "anonEndpoints" -> Json.toJson(
          RequestStats.anonStats.asScala.map { case (ep, count) => ep -> count.sum() }.toSeq.sortBy(-_._2).map { case (ep, count) => Json.obj("endpoint" -> ep, "requests" -> count) }
        )
      ))
    } else {
      Forbidden("Not an admin user")
    }
  }

  def resetRequestStats() = Authenticated { implicit request =>
    if (request.user.isAdmin) {
      RequestStats.reset()
      Ok(Json.toJson("Request stats reset"))
    } else {
      Forbidden("Not an admin user")
    }
  }

  def getAirlineFinancials(airlineId: Int) = Authenticated { implicit request =>
    if (request.user.isAdmin) {
      AirlineSource.loadAirlineById(airlineId, fullLoad = true) match {
        case Some(airline) =>
          Ok(Json.obj(
            "balance" -> airline.airlineInfo.balance,
            "actionPoints" -> airline.getActionPoints()
          ))
        case None => NotFound("Airline not found")
      }
    } else {
      Forbidden("Not an admin user")
    }
  }

  def clearCache() = Authenticated { implicit request =>
    if (request.user.isAdmin) {
      AirlineCache.invalidateAll()
      AirportCache.invalidateAll()
      AirportCache.getAllAirports()
      AirportStatisticsCache.invalidateAll()
      AirplaneOwnershipCache.invalidateAll()
      ResponseCache.invalidateAll()
      Ok(Json.toJson("Cache cleared"))
    } else {
      Forbidden("Not an admin user")
    }
  }

}
