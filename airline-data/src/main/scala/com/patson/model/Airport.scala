package com.patson.model

import com.github.benmanes.caffeine.cache.{Caffeine, CacheLoader, LoadingCache}
import com.patson.data.{AirportSource, CountrySource, DestinationSource, GameConstants}
import com.patson.model.AirportAssetType.{PassengerCostModifier, TransitModifier}
import com.patson.model.airplane.Model

import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters._
import AirportFeatureType._
import com.patson.model.airplane.Model.Type.HELICOPTER

case class Airport(iata: String, icao: String, name: String, latitude: Double, longitude: Double, countryCode: String, city: String, zone: String, var size: Int, baseIncome: Int, basePopulation: Int, basePopMiddleIncome: Int = 0, basePopElite: Int = 0, var runwayLength : Int = Airport.MIN_RUNWAY_LENGTH, var id : Int = 0) extends IdObject {
  private[this] val airlineBaseAppeals = new java.util.HashMap[Int, AirlineAppeal]() //base appeals
  private[this] val airlineAdjustedAppeals = new java.util.HashMap[Int, AirlineAppeal]() //base appeals + bonus
  private[this] val allAirlineBonuses = new java.util.HashMap[Int, List[AirlineBonus]]() //bonus appeals
  private[this] var airlineAppealsLoaded = false
  private[model] val airlineBases = scala.collection.mutable.Map[Int, AirlineBase]()
  private[this] var airlineBasesLoaded = false
  private[this] val baseFeatures = ListBuffer[AirportFeature]()
  private[this] var featuresLoaded = false
  private[this] var assetsLoaded = false
  private[this] val loungesByAirline = scala.collection.mutable.Map[Int, Lounge]()
  private[this] val loungesByAlliance = scala.collection.mutable.Map[Int, Lounge]()
  private[this] var assets = List[AirportAsset]()
  lazy val transitModifiers = getTransitModifiers()
  lazy val assetPassengerCostModifiers = getPassengerCostModifiers()


  private[model] var country : Option[Country] = None

  private[model] var assetBoostFactors : Map[AirportBoostType.Value, List[(AirportAsset, AirportBoost)]] = Map.empty
  private[model] var specializationBoostFactors : Map[AirportBoostType.Value, List[(String, Double)]] = Map.empty

  @deprecated lazy val baseIncomeLevel = baseIncome
  @deprecated lazy val incomeLevel = baseIncomeLevel + incomeLevelBoost
  @deprecated lazy val incomeLevelBoost = boostFactorsByType.get(AirportBoostType.INCOME).map(_._2).sum

  /**
   * These are the four critical variables for demand generation, modded by dynamic boost factors
   */
  lazy val income: Int = baseIncome + incomeBoost
  lazy val population: Int = basePopulation + populationBoost
  lazy val popMiddleIncome: Int = basePopMiddleIncome + populationBoost
  lazy val popElite: Int = basePopElite + eliteBoost

  lazy val boostFactorsByType: LoadingCache[AirportBoostType.Value, List[(String, Double)]] = Caffeine.newBuilder().build(Airport.createBoostFactorsLoader(this))
  lazy val incomeBoost: Int = boostFactorsByType.get(AirportBoostType.INCOME).map(_._2).sum.toInt
  lazy val populationBoost: Int = boostFactorsByType.get(AirportBoostType.POPULATION).map(_._2).sum.toInt
  lazy val eliteBoost: Int = boostFactorsByType.get(AirportBoostType.ELITE).map(_._2).sum.toInt

  lazy val features: List[AirportFeature] = computeFeatures()
  lazy val rating: AirportRating =  AirportRating.rateAirport(this)

  def getAirlineAdjustedAppeals() : Map[Int, AirlineAppeal] = {
    if (!airlineAppealsLoaded) {
      throw new IllegalStateException("airline appeal is not properly initialized! If loaded from DB, please use fullload")
    }
    airlineAdjustedAppeals.asScala.toMap
  }

  def getAirlineBaseAppeal(airlineId : Int) : AirlineAppeal = {
    if (!airlineAppealsLoaded) {
      throw new IllegalStateException("airline appeal is not properly initialized! If loaded from DB, please use fullload")
    }
    val result = airlineBaseAppeals.get(airlineId)
    if (result != null) {
      result
    } else {
      AirlineAppeal(0)
    }
  }



  def getAirlineBonuses(airlineId : Int) : List[AirlineBonus] = {
    if (!airlineAppealsLoaded) {
      throw new IllegalStateException("airline appeal is not properly initialized! If loaded from DB, please use fullload")
    }
    allAirlineBonuses.asScala.getOrElse(airlineId, List.empty)
  }

  def getAllAirlineBonuses() : Map[Int, List[AirlineBonus]] = {
    if (!airlineAppealsLoaded) {
      throw new IllegalStateException("airline appeal is not properly initialized! If loaded from DB, please use fullload")
    }
    allAirlineBonuses.asScala.toMap
  }

  def getAirlineLoyalty(airlineId : Int) : Double = {
    if (!airlineAppealsLoaded) {
      throw new IllegalStateException("airline appeal is not properly initialized! If loaded from DB, please use fullload")
    }
    val appeal = airlineAdjustedAppeals.get(airlineId)
    if (appeal != null) {
      appeal.loyalty
    } else {
      0
    }
  }

  def isAirlineAppealsInitialized = airlineAppealsLoaded


  def getAirlineBase(airlineId : Int) : Option[AirlineBase] = {
    if (!airlineBasesLoaded) {
      throw new IllegalStateException("airport base is not properly initialized! If loaded from DB, please use fullload")
    }
    airlineBases.get(airlineId)
  }

  def getAirlineBases() : Map[Int, AirlineBase] = {
    airlineBases.toMap
  }

  def getLoungeByAirline(airlineId : Int, activeOnly : Boolean = false) : Option[Lounge] = {
    loungesByAirline.get(airlineId).filter(!activeOnly || _.status == LoungeStatus.ACTIVE)
  }

  def getLounges() : List[Lounge] = {
    loungesByAirline.values.toList
  }

  def getLoungeByAlliance(alliance : Int, activeOnly : Boolean = false) : Option[Lounge] = {
    loungesByAlliance.get(alliance).filter(!activeOnly || _.status == LoungeStatus.ACTIVE)
  }

  def getLounge(airlineId : Int, allianceIdOption : Option[Int], activeOnly : Boolean = false) : Option[Lounge] = {
     getLoungeByAirline(airlineId, activeOnly) match {
       case Some(lounge) => Some(lounge)
       case None => allianceIdOption match {
         case Some(allianceId) => getLoungeByAlliance(allianceId, activeOnly)
         case None => None
       }
     }
  }

  def getZoneAffinities() : String = {
    val affinities = zone.split("-").filter(_ != "None|")
    val domestics = affinities.filterNot(_.startsWith("|")).filterNot(_.endsWith("|"))
    val internationals = affinities.filter(_.endsWith("|")).map(_.dropRight(1))
    val diasporas = affinities.filter(_.startsWith("|")).map(_.drop(4) + " diaspora").toSet

    val result = new StringBuilder()

    if (domestics.nonEmpty) {
      result.append("Trade affinities: ")
      result.append(domestics.mkString(", "))
    }

    if (internationals.nonEmpty) {
      if (result.nonEmpty) result.append("; ")
      result.append("Cultural-political affinities: ")
      result.append(internationals.mkString(", "))
    }

    if (diasporas.nonEmpty) {
      if (result.nonEmpty) result.append(", ")
      result.append(diasporas.mkString(", "))
    }

    result.toString()
  }

  def isFeaturesLoaded = featuresLoaded

  def getFeatures() : List[AirportFeature] = {
    features.toList
  }

  def hasFeature(targetFeature: AirportFeatureType): Boolean = {
    baseFeatures.exists(_.featureType == targetFeature)
  }

  def isGateway(): Boolean = {
    baseFeatures.exists(_.featureType == AirportFeatureType.GATEWAY_AIRPORT)
  }

  def isDomesticAirport(): Boolean = {
    baseFeatures.exists(_.featureType == AirportFeatureType.DOMESTIC_AIRPORT)
  }

  def isOrangeAirport: Boolean = {
    (baseFeatures.exists(_.featureType == AirportFeatureType.DOMESTIC_AIRPORT)
    || this.size <= 3 && baseFeatures.exists(_.featureType == AirportFeatureType.BUSH_HUB))
  }

  def initAirlineAppealsComputeLoyalty(airlineBonuses : Map[Int, List[AirlineBonus]] = Map.empty, loyalistEntries : List[Loyalist]) = {
    this.loyalistEntries = loyalistEntries
    val airlineBaseLoyalty : Map[Int, Double] = computeLoyaltyByLoyalist(loyalistEntries)

    val appealsByAirlineId = airlineBaseLoyalty.view.mapValues(AirlineAppeal(_))
    initAirlineAppeals(appealsByAirlineId.toMap, airlineBonuses)
  }

  private[model] lazy val computeLoyaltyByLoyalist = (loyalistEntries : List[Loyalist]) => loyalistEntries.map {
    case Loyalist(_, airline, amount) => {
      if (population == 0) { //should not happen, but just to be safe
        (airline.id, 0.0)
      } else {
        val loyalistRatio = amount.toDouble / population //to attain 100, it requires full conversion
        val baseLoyalty = Math.log10(1 + loyalistRatio * 9) * 100 // 0 -> 0, 1 -> 100
        (airline.id, Math.min(AirlineAppeal.MAX_LOYALTY, baseLoyalty))
      }
    }
  }.toMap


  def initAirlineAppeals(airlineBaseAppeals : Map[Int, AirlineAppeal], airlineBonuses : Map[Int, List[AirlineBonus]] = Map.empty) = {
    this.airlineBaseAppeals.clear()
    this.airlineBaseAppeals.asScala ++= airlineBaseAppeals

    this.airlineAdjustedAppeals.clear()
    this.airlineAdjustedAppeals.asScala ++= airlineBaseAppeals

    this.allAirlineBonuses.clear()
    airlineBonuses.foreach {
      case(airlineId, bonuses) =>
        allAirlineBonuses.put(airlineId, bonuses)
        //add the adjustments
        bonuses.foreach { bonus =>
          val existingAppeal = this.airlineAdjustedAppeals.get(airlineId)
          if (existingAppeal != null) {
            val newLoyalty = Math.min(existingAppeal.loyalty + bonus.bonus.loyalty, AirlineAppeal.MAX_LOYALTY)
            this.airlineAdjustedAppeals.put(airlineId, AirlineAppeal(newLoyalty))
          } else { //not yet has appeal data, add one
            this.airlineAdjustedAppeals.put(airlineId, bonus.bonus)
          }
        }
    }

    airlineAppealsLoaded = true
  }

  def initAirlineBases(airlineBases : List[AirlineBase]) = {
    this.airlineBases.clear()
    airlineBases.foreach { airlineBase =>
      this.airlineBases.put(airlineBase.airline.id, airlineBase)
    }

    val result = mutable.HashMap[AirportBoostType.Value, ListBuffer[(String, Double)]]()
    airlineBases.foreach { airlineBase =>
      airlineBase.specializations.foreach {
        case contributor: AirportBoostContributor =>
          val contributions = contributor.getAirportBoostContributions(this, airlineBase.airline)
          contributions.foreach { case (boostType, (description, value)) =>
            val list = result.getOrElseUpdate(boostType, ListBuffer[(String, Double)]())
            list.append((description, value))
          }
        case _ => // Non-contributing specializations
      }
    }
    specializationBoostFactors = result.view.mapValues(_.toList).toMap

    airlineBasesLoaded = true
  }
  def initFeatures(features : List[AirportFeature]) = {
    this.baseFeatures.clear()
    this.baseFeatures ++= features
    featuresLoaded = true
  }

  def initAssets(assets : List[AirportAsset]) = {
    this.assets = assets
    assetsLoaded = true
    val result = mutable.HashMap[AirportBoostType.Value, ListBuffer[(AirportAsset, AirportBoost)]]()
    assets.foreach { asset =>
      asset.boosts.foreach { boost =>
        val list = result.getOrElseUpdate(boost.boostType, ListBuffer[(AirportAsset, AirportBoost)]())
        list.append((asset, boost))
      }
    }
    assetBoostFactors = result.view.mapValues(_.toList).toMap
  }

  private[this] def getTransitModifiers() : List[TransitModifier] = {
    if (!assetsLoaded) {
      println("Cannot get airline transit modifiers w/o assets loaded")
      List.empty
    } else {
      assets.filter(asset => asset.isInstanceOf[TransitModifier] && asset.level > 0).groupBy(_.assetType).map {
        case (_, assetsByType) => assetsByType.maxBy(_.level).asInstanceOf[TransitModifier] //only count the top one for now...
      }.toList
    }
  }

  private[this] def getPassengerCostModifiers() : List[PassengerCostModifier] = {
    if (!assetsLoaded) {
      println("Cannot get airline pax cost modifiers w/o assets loaded")
      List.empty
    } else {
      assets.filter(asset => asset.isInstanceOf[PassengerCostModifier] && asset.level > 0).map(_.asInstanceOf[PassengerCostModifier])
    }
  }

  def computePassengerCostAssetDiscount(linkConsideration : LinkConsideration, paxGroup : PassengerGroup) : Option[(Double, List[AirportAsset])] = {
    if (assetPassengerCostModifiers.isEmpty) {
      None
    } else {
      val visitedAssets = ListBuffer[AirportAsset]()
      var totalDiscount = 0.0
      assetPassengerCostModifiers.foreach { costModifier =>
        costModifier.computeDiscount(linkConsideration, paxGroup).foreach { discount =>
          totalDiscount += discount
          visitedAssets.append(costModifier.asInstanceOf[AirportAsset])
        }
      }
      if (visitedAssets.isEmpty) {
        None
      } else {
        Some(totalDiscount, visitedAssets.toList)
      }
    }
  }

  def computeTransitDiscount(fromLinkConsideration : LinkConsideration, toLinkConsideration : LinkConsideration, paxGroup : PassengerGroup): Double = {
    if (transitModifiers.isEmpty) {
      0
    } else {
      transitModifiers.map { transitModifier =>
        transitModifier.computeTransitDiscount(fromLinkConsideration, toLinkConsideration, paxGroup)
      }.sum
    }
  }

  def initLounges(lounges : List[Lounge]) = {
    this.loungesByAirline.clear()
    lounges.foreach { lounge =>
      this.loungesByAirline.put(lounge.airline.id, lounge)
      lounge.allianceId.foreach {
         allianceId => this.loungesByAlliance.put(allianceId, lounge)
      }
    }
  }

  def slotFee(airplaneModel : Model, airline : Airline) : Int = {
    val baseSlotFee = if (airplaneModel.airplaneType == HELICOPTER) {
      2
    } else {
      Airport.SLOT_FEES_AIRPORT_SIZE.getOrElse(size, 2)
    }

    val multiplier: Double = Airport.SLOT_FEES_AIRPLANE_SIZE.getOrElse(airplaneModel.airplaneType, 2.0)

    //apply discount if it's a base
    val discount = getAirlineBase(airline.id) match {
      case Some(airlineBase) =>
        if (airlineBase.headquarter) 0.8 else 1.0 //headquarter 20% off
      case None =>
        1 //no discount
    }

    (baseSlotFee * multiplier * discount).toInt
  }

  def landingFee(soldSeats : Int) : Int = {
    val perSeat =
      if (size <= 2 && this.hasFeature(AirportFeatureType.ISOLATED_TOWN)) {
        -12 //remote subsidy
      } else {
        size - 1
      }
    soldSeats * perSeat
  }

  def allowsModel(airplaneModel : Model) : Boolean = {
    runwayLength >= airplaneModel.runwayRequirement
  }

  //class baseline = -10 to 35
  //distance = up to 20, 30
  //airport income = up to 30
  //GOOD_QUALITY_DELTA seeks up to another 20
  val expectedQuality = (distance: Int, linkClass: LinkClass) => {
    val classBaseline = LinkClassValues.getInstance(10, 20, 35, -10)
    val distanceMod = Math.min(10.0, distance / 1000.0) * LinkClassValues.getInstance(2, 3, 3, 2)(linkClass)
    val incomeBaseline = Math.min((baseIncome.toDouble / Airport.HIGH_INCOME * 30).toInt, 30)

    Math.max(0, Math.min(100,classBaseline(linkClass) + incomeBaseline + distanceMod).toInt)
  }

  //airport range, only used when generating airport stats
  lazy val airportRadius : Int = {
    if (GameConstants.COUNTRIES_SUB_SAHARAN.contains(countryCode)) {
      300
    } else {
      size match {
        case 1 => 200
        case 2 => 200
        case 6 => 280
        case 7 => 320
        case n if (n >= 8) => 360
        case _ => 240
      }
    }
  }

  val displayText = city + "(" + iata + ")"

  var loyalistEntries : List[Loyalist] = List.empty

  def computeFeatures() = {
    val newFeatures = ListBuffer[AirportFeature]()
    assetBoostFactors.foreach {
      case(boostType, boosts) =>
        boostType match {
          case com.patson.model.AirportBoostType.INTERNATIONAL_HUB =>
            newFeatures.append(InternationalHubFeature(0, boosts.map(_._2)))
          case com.patson.model.AirportBoostType.VACATION_HUB =>
            newFeatures.append(VacationHubFeature(0, boosts.map(_._2)))
          case com.patson.model.AirportBoostType.FINANCIAL_HUB =>
            newFeatures.append(FinancialHubFeature(0, boosts.map(_._2)))
          case _ =>
        }
    }
    (baseFeatures ++ newFeatures).groupBy(_.getClass).map {
      case(_, features) =>
        if (features.size <= 1) {
          features(0)
        } else { //should be 2
          features(0) match {
            case basicFeature : InternationalHubFeature => InternationalHubFeature(basicFeature.baseStrength, features(1).asInstanceOf[InternationalHubFeature].boosts)
            case basicFeature : FinancialHubFeature => FinancialHubFeature(basicFeature.baseStrength, features(1).asInstanceOf[FinancialHubFeature].boosts)
            case basicFeature : VacationHubFeature => VacationHubFeature(basicFeature.baseStrength, features(1).asInstanceOf[VacationHubFeature].boosts)
            case _ => features(0) //don't know how to merge
          }
        }
    }.toList
  }
}

case class AirlineAppeal(loyalty : Double)
object AirlineAppeal {
  val MAX_LOYALTY = 100
}

case class AirlineBonus(bonusType : BonusType.Value, bonus : AirlineAppeal, expirationCycle : Option[Int])

object BonusType extends Enumeration {
  type BonusType = Value
  val NATIONAL_AIRLINE, PARTNERED_AIRLINE, OLYMPICS_VOTE, OLYMPICS_PASSENGER, SANTA_CLAUS, CAMPAIGN, NEGOTIATION_BONUS, BASE_SPECIALIZATION_BONUS, BANNER, NO_BONUS, LUXURY = Value
  val description : BonusType.Value => String = {
    case NATIONAL_AIRLINE => "National Airline"
    case PARTNERED_AIRLINE => "Partnered Airline"
    case OLYMPICS_VOTE => "Olympics Vote Reward"
    case OLYMPICS_PASSENGER => "Olympics Goal Reward"
    case SANTA_CLAUS => "Santa Claus Reward"
    case CAMPAIGN => "Campaign"
    case NEGOTIATION_BONUS => "Negotiation Great Success"
    case BASE_SPECIALIZATION_BONUS => "Base Specialization Bonus"
    case BANNER => "Winning Banner"
    case LUXURY => "No Rift-raft Luxury Loyalty Bonus"
    case NO_BONUS => "N/A"

  }
}

object Airport {
  def fromId(id : Int) = {
    val airportWithJustId = Airport("", "", "", 0, 0, "", "", "", 0, 0, 0, 0, 0)
    airportWithJustId.id = id
    airportWithJustId
  }

  val MAJOR_AIRPORT_LOWER_THRESHOLD = 5
  val HIGH_INCOME = 80000
  val MIN_RUNWAY_LENGTH = 750
  val CONGESTION_MODERATE = 60
  val CONGESTION_HIGH = 100
  val TOOLTIP_CONGESTION = List(
    s"Congestion increases likelihood of delays, especially when it's over $CONGESTION_HIGH%",
    s"Negotiations become more difficult when congestion is over $CONGESTION_MODERATE% and much harder when over $CONGESTION_HIGH% as slots have to be coordinated."
  )
  val GLOBAL_AIRPORT_REPUTATION_POOL = 11000
  val SLOT_FEES_AIRPORT_SIZE: Map[Int, Int] = Map(
    1 -> 2,
    2 -> 4,
    3 -> 8,
    4 -> 16,
    5 -> 32,
    6 -> 64,
    7 -> 88,
    8 -> 112,
    9 -> 112,
    10 -> 112,
  )
  val SLOT_FEES_AIRPLANE_SIZE: Map[Model.Type.Value, Double] = Map(
    Model.Type.AIRSHIP -> 1.0,
    Model.Type.HELICOPTER -> 1.5,
    Model.Type.PROPELLER_SMALL -> 2.0,
    Model.Type.SMALL -> 2.0,
    Model.Type.PROPELLER_MEDIUM -> 2.0,
    Model.Type.REGIONAL -> 3.0,
    Model.Type.REGIONAL_XL -> 3.5,
    Model.Type.MEDIUM -> 3.5,
    Model.Type.MEDIUM_XL -> 4.0,
    Model.Type.LARGE -> 6.0,
    Model.Type.EXTRA_LARGE -> 8.0,
    Model.Type.JUMBO -> 12.0,
    Model.Type.JUMBO_XL -> 16.0,
    Model.Type.SUPERSONIC -> 16.0,
  )

  def travelRateAdjusted(fromPax: Int, baselineDemand: Int, airportSize: Int) : Double = {
    val percentDemandMet = (0.9 * fromPax + 0.1 * baselineDemand) / baselineDemand //need to blend so randomizer doesn't create peaks that perpetuate
    val baseTravelRate = airportSize match {
      case 7 => 0.2
      case 6 => 0.3
      case 5 => 0.4
      case 4 => 0.5
      case 3 => 0.6
      case 2 => 0.7
      case 1 => 0.8
      case _ => 0.15
    }
    val modified = if (percentDemandMet < 0.7) {
      baseTravelRate + percentDemandMet
    } else if (percentDemandMet < 2.0) {
      val fadeFactor = (2.0 - percentDemandMet) / 1.3
      (baseTravelRate * fadeFactor) + percentDemandMet
    } else {
      percentDemandMet
    }
    Math.min(modified, 8.0)
  }

  /**
   * Creates a boost factors loader for the given airport.
   */
  def createBoostFactorsLoader(airport: Airport): CacheLoader[AirportBoostType.Value, List[(String, Double)]] = {
    new CacheLoader[AirportBoostType.Value, List[(String, Double)]] {
      override def load(boostType: AirportBoostType.Value): List[(String, Double)] = {
        val assetFactors = airport.assetBoostFactors.getOrElse(boostType, List.empty).map {
          case (asset, boost) => (asset.name, boost.value)
        }

        val specializationFactors = airport.specializationBoostFactors.getOrElse(boostType, List.empty)

        assetFactors ++ specializationFactors
      }
    }
  }
}

case class Runway(length : Int, code : String, runwayType : RunwayType.Value, lighted : Boolean)

object RunwayType extends Enumeration {
    type RunwayType = Value
    val Asphalt, Concrete, Grass, Bitumen, Clay, Chipseal, Composite, Coral, Rock, Dirt, Hardcore, Laterite, Paved, Pavement, Sand, Sealed, Soil, Tarmac, Turf, Unpaved, Water, Cement, MarstonMat, Grout, Steel, Gravel, Unknown, abandoned, military, old, closed = Value
}
