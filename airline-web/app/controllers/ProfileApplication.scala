package controllers

import com.patson.data.airplane.ModelSource

import java.util.Random
import com.patson.data.{AirlineSource, AirplaneSource, AirportSource, BankSource, CycleSource, TutorialSource}
import com.patson.model._
import com.patson.model.AirlineType._
import com.patson.model.airplane._
import com.patson.util.AirportCache
import controllers.AuthenticationObject.AuthenticatedAirline

import javax.inject.Inject
import models.Profile
import play.api.libs.json.{JsValue, Json, _}
import play.api.mvc._

import scala.collection.mutable.ListBuffer

class ProfileApplication @Inject()(cc: ControllerComponents) extends AbstractController(cc) {
  implicit object ProfileWrites extends Writes[Profile] {
    def writes(profile: Profile): JsValue = {
      var result = Json.obj(
        "name" -> profile.name,
        "type" -> profile.airlineType.id,
        "typeLabel" -> profile.airlineType.label,
        "description" -> profile.description,
        "rule" -> profile.rule,
        "cash" -> profile.cash,
        "quality" -> profile.quality,
        "airplanes" -> profile.airplanes,
        "reputation" -> profile.reputation,
        "airportText" -> profile.airport.displayText,
        "loan" -> profile.loan.fold(JsNull: JsValue)(l => Json.toJson(l)(new LoanWrites(CycleSource.loadCycle())))
      )
      result
    }
  }

  val BASE_CAPITAL = 55_000_000
  val BONUS_PER_DIFFICULTY_POINT = 1350000
  val BASE_INTEREST_RATE = 0.05
  val LOAN_YEARS = 12

  def generateAirplanes(value : Int, capacityRange : scala.collection.immutable.Range, quality : Int, homeAirport : Airport, condition : Double, airline : Airline, random : Random) : List[Airplane] =  {
    val qualityRange = (quality - 1) to (quality + 1)
    val eligibleModels = allAirplaneModels.filter(model => capacityRange.contains(model.capacity))
      .filter(model => model.purchasableWithRelationship(allCountryRelationships.getOrElse((homeAirport.countryCode, model.countryCode), 0)))
      .filter(model => model.price * condition / Airplane.MAX_CONDITION <= value / 3)
      .filter(model => model.runwayRequirement <= homeAirport.runwayLength)
      .filter(model => model.range >= 1400)
      .filter(model => qualityRange.contains(model.quality))
    val countryModels = eligibleModels.filter(_.countryCode == homeAirport.countryCode)
    val currentCycle = CycleSource.loadCycle()

    val selectedModel =
      if (eligibleModels.isEmpty) {
        None
      } else {
        if (countryModels.nonEmpty) { //always go for airplanes from this country first
          Some(countryModels(random.nextInt(countryModels.length)))
        } else {
          Some(eligibleModels(random.nextInt(eligibleModels.length)))
        }
      }
    selectedModel match {
      case Some(model) =>
        val amount = Math.min(value / model.price, 7)
        val age = (Airplane.MAX_CONDITION - condition) / (Airplane.MAX_CONDITION.toDouble / model.lifespan)  //not really that useful, just to fake a more reasonable number
        val constructedCycle = Math.max(0, currentCycle - age.toInt)
        (0 until amount).map(_ => Airplane(model, airline, constructedCycle, constructedCycle, condition, depreciationRate = 0, value = (model.price * condition / Airplane.MAX_CONDITION).toInt, home = homeAirport)).toList
      case None =>
        List.empty
    }
  }


  def generateInitialProfiles(airline: Airline, airport: Airport): List[Profile] = {
    val difficulty = airport.rating.overallDifficulty
    val capital = BASE_CAPITAL + difficulty * BONUS_PER_DIFFICULTY_POINT
    val profiles = ListBuffer[Profile]()
    val random = new Random(airport.id)

    val regionalAirplanes = generateAirplanes((capital * 2.9).toInt, 60 to 112, 4, airport, 82, airline, random)
    if (regionalAirplanes.nonEmpty) {
      val beginnerProfile = Profile(
        name = "Beginner's start",
        airlineType = BeginnerAirline,
        difficulty = "Easy",
        description = "Recommended for new players. This is for learning the game!",
        rule = BeginnerAirline.description,
        airplanes = regionalAirplanes,
        reputation = 0,
        cash = capital * 3,
        airport = airport,
        loan = Some(Bank.getLoan(airline.id, (capital / 2).toInt, BASE_INTEREST_RATE, CycleSource.loadCycle(), LOAN_YEARS))
      )
      profiles.append(beginnerProfile)
    }

    val loanProfile = Profile(
      name = "Entrepreneurial spirit",
      airlineType = LegacyAirline,
      description = "You and the bank are betting big that there's money in commercial aviation! Plan carefully but make bold moves to thrive in this brave new world!",
      cash = (capital * 2.5).toInt,
      airport = airport,
      rule = MegaHqAirline.description,
      loan = Some(Bank.getLoan(airline.id, (capital * 2).toInt, BASE_INTEREST_RATE * 0.8, CycleSource.loadCycle(), LOAN_YEARS))
    )
    profiles.append(loanProfile)

    val largeAirplanes = generateAirplanes((capital * 4).toInt, (80 to airport.size * 21), 4, airport, 72, airline, random)
    if (largeAirplanes.nonEmpty) {
      val largeAirplaneProfile = Profile(
        name = "Revival of past glory",
        airlineType = LegacyAirline,
        description = "A once great airline now saddled with debt and aging airplanes. Can you turn this airline around?",
        cash = (capital * 4.8).toInt - largeAirplanes.map(_.value).sum,
        airport = airport,
        reputation = 30,
        quality = 35,
        rule = MegaHqAirline.description,
        airplanes = largeAirplanes,
        loan = Some(Bank.getLoan(airline.id, (capital * 4).toInt, BASE_INTEREST_RATE, CycleSource.loadCycle(), LOAN_YEARS * 2))
      )
      profiles.append(largeAirplaneProfile)
    }

    val megaHQ = Profile(
      name = "Mega HQ",
      airlineType = MegaHqAirline,
      description = "Your home town has charged you with connecting it to the world!",
      rule = MegaHqAirline.description,
      cash = (capital * 3).toInt + difficulty * BONUS_PER_DIFFICULTY_POINT, //receive double bonus for starting in small airport
      airport = airport,
      loan = Some(Bank.getLoan(airline.id, (capital * 1.75).toInt, BASE_INTEREST_RATE / 2, CycleSource.loadCycle(), LOAN_YEARS * 2))
    )
    profiles.append(megaHQ)

    val DiscountAirplanes = generateAirplanes((capital * 2.75).toInt, (airport.size * 14 to airport.size * 22), 3, airport, 85, airline, random)
    if (!DiscountAirplanes.isEmpty) {
      val cheapAirplaneProfile = Profile(
        name = "Discount Airline",
        airlineType = DiscountAirline,
        difficulty = "Hard",
        description = "Time to pack in the masses!",
        rule = DiscountAirline.description,
        cash = (capital * 3.5).toInt - DiscountAirplanes.map(_.value).sum,
        airport = airport,
        reputation = 20,
        quality = 0,
        airplanes = DiscountAirplanes,
        loan = Some(Bank.getLoan(airline.id, (capital * 2.25).toInt, BASE_INTEREST_RATE, CycleSource.loadCycle(), LOAN_YEARS))
      )
      profiles.append(cheapAirplaneProfile)
    }

    if (!regionalAirplanes.isEmpty) {
      val regionalProfile = Profile(
        name = "Regional Partner Airline",
        airlineType = RegionalAirline,
        difficulty = "Hard",
        description = "Work with your alliance partners!",
        rule = RegionalAirline.description,
        cash = (capital * 3.5).toInt - regionalAirplanes.map(_.value).sum,
        airport = airport,
        reputation = 20,
        quality = 30,
        airplanes = regionalAirplanes,
        loan = Some(Bank.getLoan(airline.id, (capital * 2.25).toInt, BASE_INTEREST_RATE, CycleSource.loadCycle(), LOAN_YEARS))
      )
      profiles.append(regionalProfile)
    }

    val fancyAirplanes = generateAirplanes((capital * 2.5).toInt, (36 to 90), 10, airport, 85, airline, random)
    if (fancyAirplanes.nonEmpty) {
      val luxuryAirlineProfile = Profile(
        name = "Luxury Startup",
        airlineType = LuxuryAirline,
        difficulty = "Very Hard",
        description = "Profit from business passengers while gaining reputation by carrying the world's elite!",
        rule = LuxuryAirline.description,
        cash = (capital * 3.75).toInt - fancyAirplanes.map(_.value).sum,
        airport = airport,
        reputation = 25,
        quality = 70,
        airplanes = fancyAirplanes,
        loan = Some(Bank.getLoan(airline.id, (capital * 2).toInt, BASE_INTEREST_RATE, CycleSource.loadCycle(), LOAN_YEARS))
      )
      profiles.append(luxuryAirlineProfile)
    }

    profiles.toList
  }

  def generateRebuildProfiles(airline: Airline, airport: Airport): List[Profile] = {
    val types = List(LegacyAirline, MegaHqAirline, DiscountAirline, RegionalAirline, LuxuryAirline)
    val cash = Math.min(airline.getBalance() / 2, 2000000000)
    types.map { airlineType =>
      val (description, quality) = airlineType match {
        case LegacyAirline =>
          ("The world is your oyster!", 35)
        case MegaHqAirline =>
          ("Your home town has charged you with connecting it to the world!", 30)
        case DiscountAirline =>
          ("Time to pack in the masses!", 0)
        case RegionalAirline =>
          ("Work with your alliance partners!", 30)
        case LuxuryAirline =>
          ("Profit from business passengers while gaining reputation by carrying the world's elite!", 70)
        case _ =>
          (s"Profile for ${airlineType.label}.", 0)
      }
      Profile(
        name = s"${airlineType.label} Profile",
        airlineType = airlineType,
        description = description,
        rule = airlineType.description,
        cash = cash,
        airport = airport,
        airplanes = List.empty,
        loan = None,
        reputation = 0,
        quality = quality,
      )
    }
  }

  def generateProfiles(airline: Airline, airport: Airport): List[Profile] = {
    if (!airline.isInitialized) {
      generateInitialProfiles(airline, airport)
    } else {
      generateRebuildProfiles(airline, airport)
    }
  }

  def getProfiles(airlineId : Int, airportId : Int) = AuthenticatedAirline(airlineId) { request =>
    request.user.getHeadQuarter() match {
      case Some(headquarters) =>
        BadRequest("Cannot select profile with active HQ")
      case None =>
        Ok(Json.toJson(generateProfiles(request.user, AirportCache.getAirport(airportId, true).get)))
    }

  }

  private[this] val buildHqWithProfileLock = new Object()
  def buildHqWithProfile(airlineId : Int, airportId : Int, profileId : Int) = AuthenticatedAirline(airlineId) { request =>
    val airline = request.user
    buildHqWithProfileLock.synchronized {
      request.user.getHeadQuarter() match {
        case Some(headquarters) =>
          BadRequest("Cannot select profile with active HQ")
        case None =>
          val cycle = CycleSource.loadCycle()
          val airport = AirportCache.getAirport(airportId, true).get
          val profile = generateProfiles(airline, airport)(profileId)
          val targetQuality = profile.quality

          val base = AirlineBase(airline, airport, airport.countryCode, 1, cycle, true)
          airline.airlineType = profile.airlineType
          AirlineSource.updateAirlineType(airlineId, airline.airlineType.id)
          AirlineSource.saveAirlineBase(base)
          Prestige.updatePrestigeCharmForAirport(airportId)
          // So any prestige charm change can be reflected in the airport detail panel immediately
          ResponseCache.airportDetailCache.invalidate(airportId)
          airline.setCountryCode(airport.countryCode)
          airline.setReputation(profile.reputation)
          airline.setCurrentServiceQuality(profile.quality)
          airline.setTargetServiceQuality(targetQuality)
          airline.setBalance(profile.cash)
          airline.setSharesOutstanding(500_000_000)

          profile.airplanes.foreach(_.assignDefaultConfiguration())
          AirplaneSource.saveAirplanes(profile.airplanes)

          profile.loan.foreach(BankSource.saveLoan)

          airline.setInitialized(true)
          AirlineSource.saveAirlineInfo(airline, true)
          val updatedAirline = AirlineSource.loadAirlineById(airlineId, true)

          val bonus = 16
          val gameWeeks = 12 * 4 + 6
          AirlineSource.saveAirlineModifier(airline.id, DelegateBoostAirlineModifier(bonus, gameWeeks, cycle))

          Ok(Json.toJson(updatedAirline))
      }
    }
  }
}
