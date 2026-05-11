package controllers

import com.patson.data.airplane.ModelSource

import java.util.Random
import com.patson.data.{AirlineSource, AirplaneSource, AirportSource, BankSource, CycleSource, NotificationSource}
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

  val BASE_CAPITAL = 150_000_000
  val BONUS_PER_DIFFICULTY_POINT = 1250000
  val BASE_INTEREST_RATE = 0.05
  val LOAN_YEARS = 12

  def generateAirplanes(value : Long, capacityRange : scala.collection.immutable.Range, quality : Int, homeAirport : Airport, condition : Double, airline : Airline, random : Random) : List[Airplane] =  {
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
        val amount = Math.min(value / model.price, 8L).toInt
        val age = (Airplane.MAX_CONDITION - condition) / (Airplane.MAX_CONDITION.toDouble / model.lifespan)  //not really that useful, just to fake a more reasonable number
        val constructedCycle = Math.max(0, currentCycle - age.toInt)
        (0 until amount).map(_ => Airplane(model, airline, constructedCycle, constructedCycle, condition, purchasePrice = (model.price * condition / Airplane.MAX_CONDITION).toInt, home = homeAirport)).toList
      case None =>
        List.empty
    }
  }


  def generateProfiles(airline: Airline, airport: Airport): List[Profile] = {
    val airportDifficultyBonus = (100 - airport.rating.overallDifficulty) * BONUS_PER_DIFFICULTY_POINT
    val MAX_REBUILD_CAPITAL = 5_000_000_000L
    val HAIRCUT_FLOOR = 2_000_000_000L
    val capital: Long =
        if (airline.isInitialized) {
            val balance = airline.getBalance()
            if (balance <= HAIRCUT_FLOOR) balance
            else if (balance <= MAX_REBUILD_CAPITAL * 2 - HAIRCUT_FLOOR) HAIRCUT_FLOOR + (balance - HAIRCUT_FLOOR) / 2
            else MAX_REBUILD_CAPITAL
        } else {
            BASE_CAPITAL
        } + airportDifficultyBonus 

    val profiles = ListBuffer[Profile]()
    val random = new Random(airport.id)

    val loanProfile = Profile(
      name = "Entrepreneurial spirit",
      airlineType = LegacyAirline,
      description = "You and the bank are betting big that there's money in commercial aviation! Plan carefully but make bold moves to thrive in this brave new world!",
      cash = capital,
      airport = airport,
      rule = LegacyAirline.description,
      loan = Some(Bank.getLoan(airline.id, (capital / 2).toLong, BASE_INTEREST_RATE * 2 / 3, CycleSource.loadCycle(), LOAN_YEARS))
    )
    profiles.append(loanProfile)

    val largeAirplanes = generateAirplanes(capital, (80 to airport.size * 21), 4, airport, 72, airline, random)
    if (largeAirplanes.nonEmpty) {
      val largeAirplaneProfile = Profile(
        name = "Revival of past glory",
        airlineType = LegacyAirline,
        description = "A once great airline now saddled with debt and aging airplanes. Can you turn this airline around?",
        cash = (capital * 1.4).toLong - largeAirplanes.map(_.purchasePrice.toLong).sum,
        airport = airport,
        reputation = 60,
        quality = 0,
        rule = LegacyAirline.description,
        airplanes = largeAirplanes,
        loan = Some(Bank.getLoan(airline.id, capital, BASE_INTEREST_RATE, CycleSource.loadCycle(), LOAN_YEARS * 2))
      )
      profiles.append(largeAirplaneProfile)
    }

    val megaHQ = Profile(
      name = "Mega HQ",
      airlineType = MegaHqAirline,
      description = "Your home town has charged you with connecting it to the world!",
      rule = MegaHqAirline.description,
      cash = capital + airportDifficultyBonus, //receive double bonus for starting in small airport
      quality = 40,
      airport = airport,
      loan = Some(Bank.getLoan(airline.id, (capital * 2 / 3).toLong, BASE_INTEREST_RATE / 2, CycleSource.loadCycle(), LOAN_YEARS * 2))
    )
    profiles.append(megaHQ)

    val DiscountAirplanes = generateAirplanes(capital, (airport.size * 14 to airport.size * 22), 3, airport, 85, airline, random)
    if (!DiscountAirplanes.isEmpty) {
      val cheapAirplaneProfile = Profile(
        name = "Discount Airline",
        airlineType = DiscountAirline,
        description = "Time to pack in the masses!",
        rule = DiscountAirline.description,
        cash = capital - DiscountAirplanes.map(_.purchasePrice.toLong).sum,
        airport = airport,
        reputation = 20,
        quality = 0,
        airplanes = DiscountAirplanes,
        loan = Some(Bank.getLoan(airline.id, (capital / 2).toLong, BASE_INTEREST_RATE, CycleSource.loadCycle(), LOAN_YEARS))
      )
      profiles.append(cheapAirplaneProfile)
    }

    val regionalAdjustedCapital = Math.min(capital, 1_000_000_000L)
    val regionalAirplanes = generateAirplanes(regionalAdjustedCapital, 60 to 112, 4, airport, 82, airline, random)
    if (!regionalAirplanes.isEmpty) {
      val regionalProfile = Profile(
        name = "Regional Partner Airline",
        airlineType = RegionalAirline,
        description = "Work with your alliance partners!",
        rule = RegionalAirline.description,
        cash = regionalAdjustedCapital - regionalAirplanes.map(_.purchasePrice.toLong).sum,
        airport = airport,
        reputation = 20,
        quality = 30,
        airplanes = regionalAirplanes,
        loan = Some(Bank.getLoan(airline.id, (regionalAdjustedCapital * 2 / 3).toLong, BASE_INTEREST_RATE, CycleSource.loadCycle(), LOAN_YEARS))
      )
      profiles.append(regionalProfile)
    }

    val smallAirplanes = generateAirplanes((regionalAdjustedCapital).toLong, 1 to 70, 4, airport, 90, airline, random)
    if (!smallAirplanes.isEmpty && airport.size <= 4) {
      val regionalProfile = Profile(
        name = "Small Town Flyer",
        airlineType = RegionalAirline,
        description = "Small planes for a small flyer!",
        rule = RegionalAirline.description,
        cash = regionalAdjustedCapital - smallAirplanes.map(_.purchasePrice.toLong).sum,
        airport = airport,
        reputation = 20,
        airplanes = smallAirplanes,
        loan = Some(Bank.getLoan(airline.id, (regionalAdjustedCapital / 2).toLong, BASE_INTEREST_RATE / 2, CycleSource.loadCycle(), LOAN_YEARS))
      )
      profiles.append(regionalProfile)
    }

    val fancyAirplanes = generateAirplanes(capital, (5 to 60), 10, airport, 85, airline, random)
    if (fancyAirplanes.nonEmpty) {
      val luxuryAirlineProfile = Profile(
        name = "Luxury Startup",
        airlineType = LuxuryAirline,
        description = "Profit from business passengers while gaining reputation by carrying the world's elite!",
        rule = LuxuryAirline.description,
        cash = (capital * 1.2).toLong - fancyAirplanes.map(_.purchasePrice.toLong).sum,
        airport = airport,
        reputation = 25,
        quality = 70,
        airplanes = fancyAirplanes,
        loan = Some(Bank.getLoan(airline.id, (capital * 2 / 3).toLong, BASE_INTEREST_RATE, CycleSource.loadCycle(), LOAN_YEARS))
      )
      profiles.append(luxuryAirlineProfile)
    }

    profiles.toList
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
          val targetQuality = Math.max(25, profile.quality) //set sane default

          val base = AirlineBase(airline, airport, airport.countryCode, 1, cycle, true)
          airline.airlineType = profile.airlineType
          AirlineSource.updateAirlineType(airlineId, airline.airlineType.id)
          AirlineSource.saveAirlineBase(base)
          Prestige.updatePrestigeCharmForAirport(airportId)
          airline.setCountryCode(airport.countryCode)
          airline.setReputation(profile.reputation)
          airline.setCurrentServiceQuality(profile.quality)
          airline.setTargetServiceQuality(targetQuality)
          airline.setSharesOutstanding(200_000_000)
          if (profile.airlineType.stockRepPerLevel > 0) {
            airline.setStockPrice(1.0)
          }
          val startingActionPoints = Math.min(100, 30 + (cycle.toDouble / 48).toInt)
          airline.setActionPoints(startingActionPoints)
          airline.setBalance(0)

          profile.airplanes.foreach(_.assignDefaultConfiguration())
          AirplaneSource.saveAirplanes(profile.airplanes)

          profile.loan.foreach(BankSource.saveLoan)

          AirlineSource.saveFoundedCycle(airlineId, cycle)

          airline.setInitialized(true)
          AirlineSource.saveAirlineInfo(airline, updateBalance = true)
          AirlineSource.saveLedgerEntry(AirlineLedgerEntry(airlineId, cycle, LedgerType.LOAN_DISBURSEMENT, profile.cash, Some(s"Starting capital")))

          NotificationSource.markCategoryRead(airlineId, NotificationCategory.TUTORIAL)
          if (!airline.isSkipTutorial) {
            NotificationSource.insertNotification(Notification(airlineId, NotificationCategory.TUTORIAL,
              s"Headquarters established at ${airport.name}! Select an airport and click the Plan Route button to set up your first route.", cycle))
          }

          val updatedAirline = AirlineSource.loadAirlineById(airlineId, true)

          Ok(Json.toJson(updatedAirline))
      }
    }
  }
}
