package controllers

import com.patson.data.{AirlineSource, AllianceSource, CycleSource, LinkSource, UserSource}
import com.patson.model.AllianceEvent._
import com.patson.model.AllianceRole._
import com.patson.model.AllianceStatus._
import com.patson.model.{AllianceHistory, AllianceMember, _}
import com.patson.model.alliance.AllianceStats
import com.patson.util.{AirlineCache, AirportChampionInfo, AllianceCache, AllianceRankingUtil, ChampionUtil, CountryChampionInfo, LogoGenerator, UserCache}
import java.awt.Color
import com.google.common.cache.{CacheBuilder, CacheLoader, LoadingCache}
import java.util.concurrent.TimeUnit
import controllers.AuthenticationObject.AuthenticatedAirline

import javax.inject.Inject
import play.api.data.Forms._
import play.api.data._
import play.api.libs.json._
import play.api.mvc._
import websocket.chat.ChatControllerActor

import java.util.Calendar
import java.nio.file.Files
import scala.collection.{MapView, mutable}
import scala.math.BigDecimal.{RoundingMode, int2bigDecimal}


class AllianceApplication @Inject()(cc: ControllerComponents) extends AbstractController(cc) {
  implicit object AllianceWrites extends Writes[Alliance] {
    //case class AllianceMember(alliance: Alliance, airline : Airline, role : AllianceRole.Value, joinedCycle : Int)
    def writes(alliance: Alliance): JsValue = JsObject(List(
      "id" -> JsNumber(alliance.id),
      "name" -> JsString(alliance.name),
      "status" -> JsString(alliance.status match {
        case ESTABLISHED => "Established"
        case FORMING => "Forming"
      })
    ))
  }

  implicit object AllianceMemberWrites extends Writes[AllianceMember] {
    //case class AllianceMember(alliance: Alliance, airline : Airline, role : AllianceRole.Value, joinedCycle : Int)
    def writes(allianceMember: AllianceMember): JsValue = JsObject(List(
      "airlineId" -> JsNumber(allianceMember.airline.id),
      "airlineName" -> JsString(allianceMember.airline.name),
      "airlineType" -> JsString(allianceMember.airline.airlineType.label),
      "allianceRole" -> JsString(allianceMember.role match {
        case LEADER => "Leader"
        case CO_LEADER => "Co-leader"
        case MEMBER => "Member"
        case APPLICANT => "Applicant"
      }),
      "isAdmin" -> JsBoolean(AllianceRole.isAdmin(allianceMember.role)),
      "allianceId" -> JsNumber(allianceMember.allianceId),
      "allianceName" -> JsString(AllianceCache.getAlliance(allianceMember.allianceId).get.name)))
  }


  implicit object HistoryWrites extends Writes[AllianceHistory] {
    //case class AllianceHistory(allianceName : String, airline : Airline, event : AllianceEvent.Value, cycle : Int, var id : Int = 0)
    def writes(history: AllianceHistory): JsValue = JsObject(List(
      "cycle" -> JsNumber(history.cycle),
      "description" -> JsString(getHistoryDescription(history))
    ))

    def getHistoryDescription(history: AllianceHistory): String = {
      val eventAction = AllianceUtil.getAllianceEventText(history.event)
      history.airline.name + " " + eventAction + " " + history.allianceName
    }
  }

  implicit object AllianceStatsWrites extends Writes[AllianceStats] {
    def writes(stats: AllianceStats): JsValue = {
      Json.obj(
        "travelerPax" -> stats.travelerPax,
        "businessPax" -> stats.businessPax,
        "elitePax" -> stats.elitePax,
        "touristPax" -> stats.touristPax,
        "totalAirportRep" -> stats.airportRep,
        "combinedMarketCap" -> stats.airlineMarketCap,
        "loungeVisitors" -> stats.loungeVisit,
        "totalFlightProfit" -> stats.profit
      )
    }
  }


  private def currentCycle: Int = CycleSource.loadCycle() - 1

  private val alliancesDataCache: LoadingCache[Int, JsValue] =
    CacheBuilder.newBuilder()
      .maximumSize(2)
      .expireAfterWrite(10, TimeUnit.MINUTES)
      .build(new CacheLoader[Int, JsValue] {
        override def load(cycle: Int): JsValue = buildAlliancesData(cycle)
      })

  def getAlliancesData() = Action { request =>
    request.headers.get(IF_NONE_MATCH) match {
      case Some(etag) if etag == s""""$currentCycle"""" =>
        NotModified
      case _ =>
        val cycle = currentCycle
        Ok(alliancesDataCache.get(cycle)).withHeaders(
          CACHE_CONTROL -> "no-cache",
          ETAG -> s""""$cycle""""
        )
    }
  }

  private def buildAlliancesData(cycle: Int): JsValue = {
    val alliances = AllianceSource.loadAllAlliances(true)
    val alliancesWithRanking: Map[Int, (Int, BigDecimal)] = AllianceRankingUtil.getRankings()
    val currentStats: Map[Int, AllianceStats] = AllianceSource.loadAllianceStatsByCycle(cycle)
      .groupBy(_.alliance.id).view.mapValues(_.head).toMap

    val weeklyHistory  = AllianceSource.loadAllianceStatsByCycleRange(cycle - 52, cycle, Period.WEEKLY)
    val quarterHistory = AllianceSource.loadAllianceStatsByCycleRange(cycle - 52 * 4, cycle, Period.QUARTER)
    val yearHistory    = AllianceSource.loadAllianceStatsByCycleRange(cycle - 52 * 10, cycle, Period.YEAR)
    val allHistory     = weeklyHistory ++ quarterHistory ++ yearHistory

    val alliancesJson = JsArray(alliances.map { alliance =>
      var allianceJson: JsObject = Json.obj(
        "id"          -> alliance.id,
        "name"        -> alliance.name,
        "status"      -> (if (alliance.status == ESTABLISHED) "Established" else "Forming"),
        "memberCount" -> alliance.members.count(_.role != AllianceRole.APPLICANT)
      )
      alliancesWithRanking.get(alliance.id).foreach { case (ranking, championPoints) =>
        allianceJson = allianceJson +
          ("ranking"        -> JsNumber(ranking)) +
          ("championPoints" -> JsNumber(championPoints.toInt))
      }
      alliance.members.find(_.role == LEADER).foreach { leader =>
        allianceJson = allianceJson + ("leaderName" -> JsString(leader.airline.name))
      }
      currentStats.get(alliance.id).foreach { stats =>
        allianceJson = allianceJson + ("currentStats" -> Json.obj(
          "totalAirportRep"  -> BigDecimal(stats.airportRep).setScale(1, RoundingMode.HALF_UP),
          "combinedMarketCap" -> stats.airlineMarketCap,
          "elitePax"         -> stats.elitePax,
          "touristPax"       -> stats.touristPax,
          "totalPax"         -> stats.totalPax
        ))
      }
      allianceJson
    })

    val historyByAlliance: Map[Int, List[AllianceStats]] = allHistory.groupBy(_.alliance.id)
    val historyJson = JsObject(historyByAlliance.map { case (allianceId, statsList) =>
      allianceId.toString -> JsArray(statsList.sortBy(_.cycle).map { stats =>
        Json.obj(
          "cycle"             -> stats.cycle,
          "period"            -> stats.period.toString,
          "totalAirportRep"   -> stats.airportRep.toInt,
          "combinedMarketCap" -> stats.airlineMarketCap,
          "elitePax"          -> stats.elitePax,
          "touristPax"        -> stats.touristPax,
          "totalPax"          -> stats.totalPax
        )
      })
    }.toSeq)

    Json.obj("alliances" -> alliancesJson, "history" -> historyJson)
  }

  case class FormAlliance(allianceName: String)

  val formAllianceForm: Form[FormAlliance] = Form(

    // Define a mapping that will handle User values
    mapping(
      "allianceName" -> text(minLength = 1, maxLength = 50).verifying(
        "Alliance name can only contain space and characters",
        allianceName => allianceName.forall(char => char.isLetter || char == ' ') && !"".equals(allianceName.trim())).verifying(
        "This Alliance name  is not available",
        allianceName => !AllianceSource.loadAllAlliances(false).map {
          _.name.toLowerCase()
        }.contains(allianceName.toLowerCase())
      )
    ) { //binding
      (allianceName) => FormAlliance(allianceName.trim)
    } { //unbinding
      formAlliance => Some(formAlliance.allianceName)
    }
  )


  def getAirlineAllianceDetails(airlineId: Int) = AuthenticatedAirline(airlineId) { request =>
    var result = Json.obj()
    AllianceSource.loadAllianceMemberByAirline(request.user) match {
      case Some(allianceMember) =>
        result = result ++ Json.toJson(allianceMember).asInstanceOf[JsObject]
        result = result + ("isAdmin" -> JsBoolean(AllianceRole.isAdmin(allianceMember.role)))

      case None => //do nothing
    }

    val history = AllianceSource.loadAllianceHistoryByAirline(airlineId)
    if (!history.isEmpty) {
      result = result + ("history" -> Json.toJson(history.sortBy(_.cycle)(Ordering.Int.reverse)))
    }
    Ok(result)
  }

  def formAlliance(airlineId: Int) = AuthenticatedAirline(airlineId) { implicit request =>
    formAllianceForm.bindFromRequest().fold(
      // Form has errors, redisplay it
      erroredForm => Ok(Json.obj("rejection" -> JsString(erroredForm.error("allianceName").get.message))), { formAllianceInput =>
        //make sure the current airline is not in any alliance
        AllianceSource.loadAllianceMemberByAirline(request.user) match {
          case None =>
            val allianceName = formAllianceInput.allianceName
            val currentCycle = CycleSource.loadCycle()
            val newAlliance = Alliance(name = allianceName, creationCycle = currentCycle, members = List())
            AllianceSource.saveAlliance(newAlliance)

            val allianceMember = AllianceMember(allianceId = newAlliance.id, airline = request.user, role = LEADER, joinedCycle = currentCycle)
            AllianceSource.saveAllianceMember(allianceMember)

            val history = AllianceHistory(allianceName = newAlliance.name, airline = request.user, event = FOUND_ALLIANCE, cycle = currentCycle)
            AllianceSource.saveAllianceHistory(history)


            Ok(Json.toJson(newAlliance.copy(members = List(allianceMember))))
          case Some(currentAirlineAllianceMember) =>
            BadRequest(s"Current airline is an alliance member of $currentAirlineAllianceMember, cannot form new alliance")
        }
      }
    )
  }

  //add cycle cache
  def getAlliances(): Action[AnyContent] = Action {
    val alliances: List[Alliance] = AllianceSource.loadAllAlliances(true)
    val cycle = CycleSource.loadCycle() - 1
    val alliancesWithRanking: Map[Int, (Int, BigDecimal)] = AllianceRankingUtil.getRankings()
    val allianceStats: Map[Int, AllianceStats] = AllianceSource.loadAllianceStatsByCycle(cycle).groupBy(_.alliance.id).view.mapValues(_.head).toMap

    var result = Json.arr()

    alliances.foreach {
      alliance =>
        var allianceJson = Json.toJson(alliance).asInstanceOf[JsObject]
        var allianceMemberJson = Json.arr()
        alliance.members.foreach { allianceMember =>
          val thisMemberJson = Json.toJson(allianceMember).asInstanceOf[JsObject]
          allianceMemberJson = allianceMemberJson.append(thisMemberJson)
          if (allianceMember.role == LEADER) {
            allianceJson = allianceJson.asInstanceOf[JsObject] + ("leader" -> Json.toJson(allianceMember.airline))
          }
        }
        allianceJson = allianceJson + ("members" -> allianceMemberJson)
        alliancesWithRanking.get(alliance.id).foreach {
          case ((ranking, championPoints)) =>
            allianceJson = allianceJson + ("ranking" -> JsNumber(ranking))
            allianceJson = allianceJson + ("championPoints" -> JsNumber(championPoints.toInt))
        }

        allianceStats.get(alliance.id).foreach {
          case (stats) =>
            allianceJson = allianceJson + ("stats" -> Json.toJson(stats))
        }

        result = result.append(allianceJson)
    }

    Ok(result)
  }

  object SimpleLinkWrites extends Writes[List[Link]] {
    def writes(links: List[Link]): JsValue = {
      var result = Json.arr()

      links.foreach { link =>
        var linkJson = JsObject(List(
          "id" -> JsNumber(link.id),
          "fromAirportId" -> JsNumber(link.from.id),
          "toAirportId" -> JsNumber(link.to.id),
          "fromAirportCode" -> JsString(link.from.iata),
          "toAirportCode" -> JsString(link.to.iata),
          "fromAirportName" -> JsString(link.from.name),
          "toAirportName" -> JsString(link.to.name),
          "fromAirportCity" -> JsString(link.from.city),
          "toAirportCity" -> JsString(link.to.city),
          "fromCountryCode" -> JsString(link.from.countryCode),
          "toCountryCode" -> JsString(link.to.countryCode),
          "airlineId" -> JsNumber(link.airline.id),
          "airlineName" -> JsString(link.airline.name),
          "frequency" -> JsNumber(link.frequency),
          "fromLatitude" -> JsNumber(link.from.latitude),
          "fromLongitude" -> JsNumber(link.from.longitude),
          "toLatitude" -> JsNumber(link.to.latitude),
          "toLongitude" -> JsNumber(link.to.longitude),
          "capacity" -> Json.toJson(link.capacity),
          "flightCode" -> JsString(LinkUtil.getFlightCode(link.airline, link.flightNumber))))
        result = result.append(linkJson)
      }
      result
    }
  }

  object AllianceAirlinesWrites extends Writes[List[(Airline, AllianceRole.Value)]] { //a bit more info -nothing confidential tho! since this is accessible to public
    def writes(entries: List[(Airline, AllianceRole.Value)]): JsValue = {
      var result = Json.arr()
      entries.foreach { case (airline, role) =>
        var airlineJson = Json.toJson(airline).asInstanceOf[JsObject]
        //then add base info
        airlineJson = airlineJson + ("bases", Json.toJson(airline.getBases())) + ("role" -> JsString(role.toString))
        result = result.append(airlineJson)
      }
      result
    }
  }

  def getAllianceLogo(allianceId: Int) = Action { request =>
    Ok(LogoUtil.getAllianceLogo(allianceId)).as("image/png")
  }


  /**
   * Used in Alliance map
   *
   * @param allianceId
   * @return
   */
  def getAllAllianceDetails(allianceId: Int) = Action { request =>
    request.headers.get(IF_NONE_MATCH) match {
      case Some(etag) if etag == s""""$currentCycle"""" =>
        NotModified
      case _ =>
        AllianceCache.getAlliance(allianceId, true) match {
          case None => NotFound("Alliance with " + allianceId + " is not found")
          case Some(alliance) =>
            val links = alliance.members.flatMap { allianceMember =>
              if (allianceMember.role != AllianceRole.APPLICANT) {
                LinkSource.loadFlightLinksByAirlineId(allianceMember.airline.id)
              } else {
                List.empty
              }
            }

            Ok(Json.obj("links" -> Json.toJson(links)(SimpleLinkWrites),
              "members" -> Json.toJson(alliance.members.map(member => (member.airline, member.role)))(AllianceAirlinesWrites)
            )).withHeaders(
              CACHE_CONTROL -> "no-cache",
              ETAG -> s""""$currentCycle""""
            )
        }
    }
  }

  def getMemberLoginStatus(allianceId: Int) = Action { request =>
    AllianceCache.getAlliance(allianceId, true) match {
      case None => NotFound("Alliance with " + allianceId + " is not found")
      case Some(alliance) => {
        val userByAirlineId = mutable.HashMap[Int, User]()
        alliance.members.foreach { allianceMember =>
          UserSource.loadUserByAirlineId(allianceMember.airline.id).foreach { user =>
            userByAirlineId.put(allianceMember.airline.id, user)
          }
        }

        val onlineUserIds = ChatControllerActor.getActiveUsers().map(_.id)
        val sevenDaysAgo = Calendar.getInstance();
        sevenDaysAgo.add(Calendar.DATE, -7)
        val thirtyDaysAgo = Calendar.getInstance()
        thirtyDaysAgo.add(Calendar.DATE, -30)

        val loginStatusIdByAirlineId = userByAirlineId.map {
          case (airlineId, user) =>
            val loginStatus =
              if (onlineUserIds.contains(user.id)) {
                LoginStatus.ONLINE
              } else if (user.lastActiveTime.after(sevenDaysAgo)) {
                LoginStatus.ACTIVE_7_DAYS
              } else if (user.lastActiveTime.after(thirtyDaysAgo)) {
                LoginStatus.ACTIVE_30_DAYS
              } else {
                LoginStatus.INACTIVE
              }
            (airlineId.toString, loginStatus.id)
        }.toMap

        Ok(Json.toJson(loginStatusIdByAirlineId))
      }
    }
  }

  val MAX_CHAMPION_ENTRIES = 100

  def getAllianceAirportChampions(allianceId: Int) = Action { request =>
    AllianceCache.getAlliance(allianceId, true) match {
      case None => NotFound("Alliance with " + allianceId + " is not found")
      case Some(alliance) => {
        val allianceChampions: List[AirportChampionInfo] = alliance.members.map { allianceMember =>
          ChampionUtil.loadAirportChampionInfoByAirline(allianceMember.airline.id)
        }.flatten

        val approvedMemberAirlineIds = alliance.members.filter(_.role != APPLICANT).map(_.airline.id)
        val topEntries = allianceChampions.sortBy(_.reputationBoost).reverse.take(MAX_CHAMPION_ENTRIES)
        val (topMemberChampions, topApplicantChampions) = topEntries.partition(entry => approvedMemberAirlineIds.contains(entry.loyalist.airline.id))

        val totalReputation = AirlineCache.getAirlines(approvedMemberAirlineIds).map(_._2.getReputation()).sum
        Ok(Json.obj("members" -> Json.toJson(topMemberChampions), "applicants" -> Json.toJson(topApplicantChampions), "totalReputation" -> BigDecimal(totalReputation).setScale(2, RoundingMode.HALF_UP), "truncatedEntries" -> Math.max(0, allianceChampions.length - MAX_CHAMPION_ENTRIES)))
      }
    }
  }

  def getAllianceCountryChampions(allianceId: Int) = Action { request =>
    AllianceCache.getAlliance(allianceId, true) match {
      case None => NotFound("Alliance with " + allianceId + " is not found")
      case Some(alliance) => {
        val allianceChampions: List[CountryChampionInfo] = alliance.members.map { allianceMember =>
          ChampionUtil.getCountryChampionInfoByAirlineId(allianceMember.airline.id)
        }.flatten.sortBy(_.ranking).sortBy(entry => entry.country.airportPopulation.toLong * entry.country.income)(Ordering[Long].reverse)
        Ok(Json.toJson(allianceChampions))
      }
    }
  }

  def getRemoveConsideration(targetMember: AllianceMember, currentMember: AllianceMember): Either[String, String] = {
    if (targetMember.allianceId != currentMember.allianceId) {
      Left(s"Airline ${targetMember.airline} does not belong to alliance ${currentMember.allianceId}")
    } else if (targetMember.airline.id == currentMember.airline.id) { //remove self
      Right(s"Leave this alliance?")
    } else if (!AllianceRole.isAdmin(currentMember.role)) {
      Left(s"Current airline ${currentMember.airline} cannot remove airline ${targetMember.airline} from alliance as current airline is not admin")
    } else if (currentMember.role.id >= targetMember.role.id) { //higher the id, lower the rank. Can only remove people at the lower rank
      Left(s"Current airline ${currentMember.airline} of role ${currentMember.role} cannot remove airline ${targetMember.airline} of role ${targetMember.role} from alliance")
    } else {
      Right(s"Remove ${targetMember.airline.name} from the alliance?")
    }
  }

  def getPromoteConsideration(targetMember: AllianceMember, currentMember: AllianceMember): Either[String, String] = {
    if (targetMember.allianceId != currentMember.allianceId) {
      Left(s"Airline ${targetMember.airline} does not belong to alliance ${currentMember.allianceId}")
    } else if (!AllianceRole.isAdmin(currentMember.role)) {
      Left(s"Current airline ${currentMember.airline} cannot promote airline ${targetMember.airline} from alliance as current airline is not admin")
    } else if (targetMember.role.id > AllianceRole.MEMBER.id) {
      Left(s"Cannot promote non-member airline ${targetMember.airline} of role ${targetMember.role}")
    } else if (currentMember.role.id >= targetMember.role.id) { //higher the id, lower the rank. Can only promote people at the lower rank
      Left(s"Current airline ${currentMember.airline} of role ${currentMember.role} cannot promote airline ${targetMember.airline} of role ${targetMember.role} from alliance")
    } else {
      if (AllianceRole(targetMember.role.id - 1) == AllianceRole.LEADER) {
        Right(s"Promote ${targetMember.airline.name} as the new Alliance Leader? Your airline will be demoted to Co-leader!")
      } else {
        Right(s"Promote ${targetMember.airline.name} as Alliance Co-Leader?")
      }
    }
  }

  def getDemoteConsideration(targetMember: AllianceMember, currentMember: AllianceMember): Either[String, String] = {
    if (targetMember.allianceId != currentMember.allianceId) {
      Left(s"Airline ${targetMember.airline} does not belong to alliance ${currentMember.allianceId}")
    } else if (currentMember.airline.id == targetMember.airline.id) {
      Left(s"Airline ${targetMember.airline} cannot demote self")
    } else if (targetMember.role.id >= AllianceRole.MEMBER.id) {
      Left(s"Current airline ${currentMember.airline} of role ${currentMember.role} cannot demote airline ${targetMember.airline} of role ${targetMember.role} any further")
    } else if (!AllianceRole.isAdmin(currentMember.role)) {
      Left(s"Current airline ${currentMember.airline} cannot demote airline ${targetMember.airline} from alliance as current airline is not admin")
    } else if (currentMember.role.id >= targetMember.role.id) { //higher the id, lower the rank. Can only remove people at the lower rank
      Left(s"Current airline ${currentMember.airline} of role ${currentMember.role} cannot demote airline ${targetMember.airline} of role ${targetMember.role} in alliance")
    } else {
      Right(s"Demote ${targetMember.airline.name}?")
    }
  }

  def evaluateAlliance(airlineId: Int, allianceId: Int) = AuthenticatedAirline(airlineId) { implicit request =>
    AllianceCache.getAlliance(allianceId, true) match {
      case None => NotFound("Alliance with id " + allianceId + " is not found")
      case Some(alliance) =>
        var result = Json.obj()
        alliance.members.find(_.airline.id == airlineId) match {
          case Some(currentMember) => //already a member
            //get member actions
            var memberActionsJson = Json.arr()
            alliance.members.foreach { targetMember =>
              var memberJson = Json.obj("airlineId" -> targetMember.airline.id)
              getRemoveConsideration(targetMember, currentMember) match {
                case Left(rejection) => memberJson = memberJson + ("removeRejection" -> JsString(rejection))
                case Right(prompt) => memberJson = memberJson + ("removePrompt" -> JsString(prompt))
              }
              getPromoteConsideration(targetMember, currentMember) match {
                case Left(rejection) => memberJson = memberJson + ("promoteRejection" -> JsString(rejection))
                case Right(prompt) => memberJson = memberJson + ("promotePrompt" -> JsString(prompt))
              }
              getDemoteConsideration(targetMember, currentMember) match {
                case Left(rejection) => memberJson = memberJson + ("demoteRejection" -> JsString(rejection))
                case Right(prompt) => memberJson = memberJson + ("demotePrompt" -> JsString(prompt))
              }

              if (AllianceRole.isAdmin(currentMember.role) && targetMember.role == AllianceRole.APPLICANT) {
                getApplyRejection(targetMember.airline, alliance) match {
                  case Some(rejection) => memberJson = memberJson + ("acceptRejection" -> JsString(rejection))
                  case None => memberJson = memberJson + ("acceptPrompt" -> JsString(s"Accept ${targetMember.airline.name} into the alliance?"))
                }

              }

              memberActionsJson = memberActionsJson.append(memberJson)
            }
            result = result + ("isMember" -> JsBoolean(true)) + ("memberActions" -> memberActionsJson)
          case None => getApplyRejection(request.user, alliance) match {
            case Some(rejection) => result = result + ("rejection" -> JsString(rejection))
            case None =>
            //nothing
          }

        }

        Ok(result)
    }
  }

  def applyForAlliance(airlineId: Int, allianceId: Int) = AuthenticatedAirline(airlineId) { implicit request =>
    AllianceCache.getAlliance(allianceId, true) match {
      case None => NotFound("Alliance with id " + allianceId + " is not found")
      case Some(alliance) =>
        getApplyRejection(request.user, alliance) match {
          case Some(rejection) => BadRequest(rejection)
          case None => //ok
            val currentCycle = CycleSource.loadCycle()
            val newMember = AllianceMember(allianceId = alliance.id, airline = request.user, role = APPLICANT, joinedCycle = currentCycle)
            AllianceSource.saveAllianceMember(newMember)
            val history = AllianceHistory(allianceName = alliance.name, airline = request.user, event = APPLY_ALLIANCE, cycle = currentCycle)
            AllianceSource.saveAllianceHistory(history)
            Ok(Json.toJson(newMember))
        }
    }
  }

  def removeFromAlliance(airlineId: Int, targetAirlineId: Int) = AuthenticatedAirline(airlineId) { implicit request =>
    AllianceSource.loadAllianceMemberByAirline(request.user) match {
      case None => BadRequest("Current airline " + request.user + " cannot remove airline id " + targetAirlineId + " from alliance as current airline does not belong to any alliance")
      case Some(currentAirlineAllianceMember) =>
        val alliance = AllianceCache.getAlliance(currentAirlineAllianceMember.allianceId, false).get
        if (airlineId == targetAirlineId) { //removing itself, ok!
          alliance.removeMember(currentAirlineAllianceMember, true)

          Ok(Json.obj("removed" -> "alliance"))
        } else { //check if current airline has the right permission and the target airline is within this alliance
          AirlineCache.getAirline(targetAirlineId) match {
            case None => NotFound("Airline with id " + targetAirlineId + " not found")
            case Some(targetAirline) =>
              AllianceSource.loadAllianceMemberByAirline(targetAirline) match {
                case None => NotFound("Airline " + targetAirline + " does not belong to any alliance!")
                case Some(allianceMember) =>
                  getRemoveConsideration(allianceMember, currentAirlineAllianceMember) match {
                    case Left(rejection) => BadRequest(rejection)
                    case Right(_) => //OK ..removing
                      alliance.removeMember(allianceMember, false)

                      Ok(Json.obj("removed" -> "member"))
                  }
              }
          }
        }
    }
  }

  def addToAlliance(airlineId: Int, targetAirlineId: Int) = AuthenticatedAirline(airlineId) { implicit request =>
    val currentCycle = CycleSource.loadCycle()

    AllianceSource.loadAllianceMemberByAirline(request.user) match {
      case None => BadRequest("Current airline " + request.user + " cannot add airline id " + targetAirlineId + " to alliance as current airline does not belong to any alliance")
      case Some(currentAirlineAllianceMember) =>
        //check if current airline is leader and the target airline has applied to this alliance
        if (!AllianceRole.isAdmin(currentAirlineAllianceMember.role)) {
          BadRequest("Current airline " + request.user + " cannot accept airline id " + targetAirlineId + " from alliance as current airline is not leader")
        } else {
          val alliance = AllianceCache.getAlliance(currentAirlineAllianceMember.allianceId, false).get
          AirlineCache.getAirline(targetAirlineId, true) match {
            case None => NotFound("Airline with id " + targetAirlineId + " not found")
            case Some(targetAirline) =>
              AllianceSource.loadAllianceMemberByAirline(targetAirline) match {
                case None => NotFound("Airline " + targetAirline + " does not belong to any alliance!")
                case Some(allianceMember) =>
                  if (allianceMember.allianceId != currentAirlineAllianceMember.allianceId) {
                    BadRequest("Airline " + targetAirline + " does not belong to alliance " + alliance)
                  } else if (allianceMember.role != APPLICANT) {
                    BadRequest("Airline " + targetAirline + " is not applicant of " + alliance)
                  } else {
                    getApplyRejection(targetAirline, alliance) match {
                      case Some(rejection) => BadRequest(rejection) //confirm once more as there could be other approved applicant now with conflicting base
                      case None =>
                        //OK ..adding
                        AllianceSource.saveAllianceMember(allianceMember.copy(role = MEMBER, joinedCycle = currentCycle))
                        AllianceSource.saveAllianceHistory(AllianceHistory(allianceName = alliance.name, airline = allianceMember.airline, event = JOIN_ALLIANCE, cycle = currentCycle))

                        Ok(Json.toJson(allianceMember))
                    }
                  }
              }
          }
        }
    }
  }

  def promoteMember(airlineId: Int, targetAirlineId: Int) = AuthenticatedAirline(airlineId) { implicit request =>
    val currentCycle = CycleSource.loadCycle()
    AllianceSource.loadAllianceMemberByAirline(request.user) match {
      case None => BadRequest("Current airline " + request.user + " cannot promote airline id " + targetAirlineId + " to alliance as current airline does not belong to any alliance")
      case Some(currentMember) =>
        val alliance = AllianceCache.getAlliance(currentMember.allianceId, false).get
        AirlineCache.getAirline(targetAirlineId) match {
          case None => NotFound("Airline with id " + targetAirlineId + " not found")
          case Some(targetAirline) =>
            AllianceSource.loadAllianceMemberByAirline(targetAirline) match {
              case None => NotFound("Airline " + targetAirline + " does not belong to any alliance!")
              case Some(targetMember) =>
                getPromoteConsideration(targetMember, currentMember) match {
                  case Left(rejection) => BadRequest(rejection)
                  case Right(_) =>
                    //OK ..promoting
                    val newRole = AllianceRole(targetMember.role.id - 1)
                    AllianceSource.saveAllianceMember(targetMember.copy(role = newRole))

                    if (newRole == AllianceRole.LEADER && currentMember.role == AllianceRole.LEADER) { //promotion to leader, have to demote current leader then
                      AllianceSource.saveAllianceMember(currentMember.copy(role = AllianceRole(AllianceRole.LEADER.id + 1)))
                      AllianceSource.saveAllianceHistory(AllianceHistory(allianceName = alliance.name, airline = targetMember.airline, event = PROMOTE_LEADER, cycle = currentCycle))
                    } else { //otherwise assume it is always to co-leader
                      AllianceSource.saveAllianceHistory(AllianceHistory(allianceName = alliance.name, airline = targetMember.airline, event = PROMOTE_CO_LEADER, cycle = currentCycle))
                    }

                    Ok(Json.toJson(targetMember))
                }
            }

        }
    }
  }

  def demoteMember(airlineId: Int, targetAirlineId: Int) = AuthenticatedAirline(airlineId) { implicit request =>
    val currentCycle = CycleSource.loadCycle()
    AllianceSource.loadAllianceMemberByAirline(request.user) match {
      case None => BadRequest("Current airline " + request.user + " cannot demote airline id " + targetAirlineId + " to alliance as current airline does not belong to any alliance")
      case Some(currentMember) =>
        val alliance = AllianceCache.getAlliance(currentMember.allianceId, false).get
        AirlineCache.getAirline(targetAirlineId) match {
          case None => NotFound("Airline with id " + targetAirlineId + " not found")
          case Some(targetAirline) =>
            AllianceSource.loadAllianceMemberByAirline(targetAirline) match {
              case None => NotFound("Airline " + targetAirline + " does not belong to any alliance!")
              case Some(targetMember) =>
                getDemoteConsideration(targetMember, currentMember) match {
                  case Left(rejection) => BadRequest(rejection)
                  case Right(_) =>
                    //OK ..demoting
                    val newRole = AllianceRole(targetMember.role.id + 1)
                    AllianceSource.saveAllianceMember(targetMember.copy(role = newRole))
                    AllianceSource.saveAllianceHistory(AllianceHistory(allianceName = alliance.name, airline = targetMember.airline, event = DEMOTE, cycle = currentCycle))

                    Ok(Json.toJson(targetMember))
                }
            }

        }
    }
  }


  def getApplyRejection(airline: Airline, alliance: Alliance): Option[String] = {
    val approvedMembers = alliance.members.filter(_.role != AllianceRole.APPLICANT)
    val approvedMembersRegionalCount = Math.min(Alliance.MAX_MEMBER_REGIONALS_COUNT, alliance.members.count(member => member.role != AllianceRole.APPLICANT && member.airline.airlineType == RegionalAirline))
    val maxSize = Alliance.MAX_MEMBER_NON_REGIONAL_COUNT + Alliance.MAX_MEMBER_REGIONALS_COUNT

    if (airline.getHeadQuarter().isEmpty) {
      return Some("Airline does not have headquarters")
    }

    if (approvedMembers.size >= maxSize) {
      return Some("Alliance has reached " + maxSize + " max member size for all airline types already")
    } else if (airline.airlineType != RegionalAirline && approvedMembers.size - approvedMembersRegionalCount >= Alliance.MAX_MEMBER_NON_REGIONAL_COUNT) {
      return Some("Alliance only has slots to accept Regional Partner Airline members")
    }

    val allAllianceHeadquarters = approvedMembers.flatMap(_.airline.getHeadQuarter()).map(_.airport)

    val airlineHeadquarters = airline.getHeadQuarter().get.airport

    if (allAllianceHeadquarters.contains(airlineHeadquarters)) {
      return Some("One of the alliance members has Headquarters at " + getAirportText(airlineHeadquarters))
    }

    val invalidAirports: List[Airport] = airline.getBases().filter(!_.headquarter).flatMap { base =>
      AirlineBase.validAllianceBasesAtAirport(airline, base) match {
        case Some(_) => Some(base.airport)
        case None => None
      }
    }

    if (invalidAirports.nonEmpty) {
      var message = "Alliance members have overlapping airport bases: "
      invalidAirports.foreach { overlappingBase =>
        message += getAirportText(overlappingBase) + "; "
      }

      return Some(message)
    }

    AllianceSource.loadAllianceMemberByAirline(airline) match {
      case Some(allianceMember) =>
        if (allianceMember.allianceId != alliance.id) {
          return Some("Airline is already a member of another alliance " + AllianceCache.getAlliance(allianceMember.allianceId).get.name)
        }
      case None =>

    }
    return None
  }

  def saveAllianceLogo(airlineId: Int, allianceId: Int) = AuthenticatedAirline(airlineId) { request =>
    AllianceCache.getAlliance(allianceId, true) match {
      case None =>
        NotFound(s"Alliance with id $allianceId is not found")
      case Some(alliance) =>
        if (alliance.status != ESTABLISHED) {
          BadRequest("Cannot save logo for a non-established alliance")
        } else {
          val isAdmin = alliance.members.exists(member =>
            member.airline.id == request.user.id && AllianceRole.isAdmin(member.role)
          )

          if (!isAdmin) {
            Forbidden("Only alliance leaders can save alliance logos")
          } else {
            request.body.asMultipartFormData.map { data =>
              data.file("logoFile").map { logoFile =>
                val logoPath = logoFile.ref.path
                LogoUtil.validateUpload(logoPath) match {
                  case Some(rejection) =>
                    BadRequest(Json.obj("error" -> JsString(rejection)))
                  case None =>
                    try {
                      val logoBytes = java.nio.file.Files.readAllBytes(logoPath)
                      LogoUtil.saveAllianceLogo(allianceId, logoBytes)
                      Ok(Json.obj("success" -> JsString("File uploaded")))
                    } catch {
                      case e: IllegalArgumentException =>
                        BadRequest(Json.obj("error" -> JsString(e.getMessage)))
                    }
                }
              }.getOrElse {
                BadRequest(Json.obj("error" -> JsString("Cannot find uploaded file")))
              }
            }.getOrElse {
              BadRequest(Json.obj("error" -> JsString("Invalid request format")))
            }
          }
        }
    }
  }

  def setAllianceLogo(airlineId: Int, allianceId: Int, templateIndex: Int, color1: String, color2: String) = AuthenticatedAirline(airlineId) { request =>
    AllianceCache.getAlliance(allianceId, true) match {
      case None =>
        NotFound(s"Alliance with id $allianceId is not found")
      case Some(alliance) =>
        if (alliance.status != ESTABLISHED) {
          BadRequest("Cannot save logo for a non-established alliance")
        } else {
          val isAdmin = alliance.members.exists(member =>
            member.airline.id == request.user.id && AllianceRole.isAdmin(member.role)
          )

          if (!isAdmin) {
            Forbidden("Only alliance leaders can save alliance logos")
          } else {
            val logo = LogoGenerator.generateLogo(templateIndex, Color.decode(color1).getRGB, Color.decode(color2).getRGB)
            LogoUtil.saveAllianceLogo(allianceId, logo)
            Ok(Json.obj())
          }
        }
    }
  }

  def getAirportText(airport: Airport) = {
    airport.city + " - " + airport.name
  }
}
