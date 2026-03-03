package com.patson.model.airplane

import com.patson.model.IdObject
import com.patson.model.Airline
import com.patson.model.airplane.Model.Category
import com.patson.util.AirplaneModelCache

/**
 *
 * @param name
 * @param family
 * @param capacity
 * @param quality 0-10 quality "passenger experience"
 * @param ascentBurn
 * @param cruiseBurn
 * @param speed
 * @param range
 * @param price
 * @param lifespan
 * @param constructionTime
 * @param manufacturer
 * @param runwayRequirement
 * @param imageUrl
 * @param id
 */
case class Model(name : String, family : String = "", capacity : Int, quality : Int, ascentBurn : Double, cruiseBurn : Double, speed : Int, range : Int, price : Int, lifespan : Int, constructionTime : Int, manufacturer: Manufacturer, runwayRequirement : Int, imageUrl : String = "", var id : Int = 0) extends IdObject {
  import Model.Type._

  val countryCode = manufacturer.countryCode
  val SUPERSONIC_SPEED_THRESHOLD = 1236
  val SIZE_SMALL_THRESHOLD = 80

  val airplaneType : Type = {
    if (speed > SUPERSONIC_SPEED_THRESHOLD) {
      SUPERSONIC
    } else if (speed < 180) {
      AIRSHIP
    } else if (speed <= 300) {
      HELICOPTER
    } else if (speed <= 680) {
      if (capacity <= SIZE_SMALL_THRESHOLD) {
        PROPELLER_SMALL
      } else {
        PROPELLER_MEDIUM
      }
    } else if (capacity <= SIZE_SMALL_THRESHOLD) {
      SMALL
    } else if (capacity >= 800) {
      JUMBO_XL
    } else if (capacity >= 475 || family == "Airbus A350") {
      JUMBO
    } else if (capacity <= 115 || family == "Embraer" || family == "Sud Aviation Caravelle" || family == "Sukhoi Superjet" || family == "Dornier" || family == "Airbus ZE" || family == "BAe 146") {
      if (capacity < 110) REGIONAL else REGIONAL_XL
    } else if (capacity <= 215) {
      MEDIUM
    } else if (family == "Boeing 757" || family == "Boeing 737"  || family == "Airbus A320" || family == "DC-8" || family == "Yakovlev MC-21") {
      MEDIUM_XL
    } else if (capacity <= 375) {
      LARGE
    } else {
      EXTRA_LARGE
    }
  }

  val category: Model.Category.Value = Category.fromType(airplaneType)

  private[this]val BASE_TURNAROUND_TIME = 35
  val turnaroundTime : Int = {
    BASE_TURNAROUND_TIME +
      (airplaneType match {
        case HELICOPTER => 0 //buffing for short distances
        case AIRSHIP => 0
        case _ => capacity / 3.75
      }).toInt
  }

  val airplaneTypeLabel : String = label(airplaneType)

  val airplaneTypeSize : Double = size(airplaneType)

  //weekly fixed cost
  val baseMaintenanceCost : Int = {
    (capacity / 50.0 * 725).toInt //46,500
    (capacity * 155).toInt //1240, 37820
  }

  def applyDiscount(discounts : List[ModelDiscount]) = {
    var discountedModel = this
    discounts.groupBy(_.discountType).foreach {
      case (discountType, discounts) => discountType match {
        case DiscountType.PRICE =>
          val totalDiscount = discounts.map(_.discount).sum
          discountedModel = discountedModel.copy(price = (price * (1 - totalDiscount)).toInt)
        case DiscountType.CONSTRUCTION_TIME =>
          var totalDiscount = discounts.map(_.discount).sum
          totalDiscount = Math.min(1, totalDiscount)
          discountedModel = discountedModel.copy(constructionTime = (constructionTime * (1 - totalDiscount)).toInt)
      }
    }
    discountedModel
  }

  val purchasableWithRelationship = (relationship : Int) => {
    relationship >= Model.BUY_RELATIONSHIP_THRESHOLD
  }
}

object Model {
  val BUY_RELATIONSHIP_THRESHOLD = 0
  val TIME_TO_CRUISE_HELICOPTER = 0
  val TIME_TO_CRUISE_PROPELLER_SMALL = 5
  val TIME_TO_CRUISE_PROPELLER_MEDIUM = 8
  val TIME_TO_CRUISE_SMALL = 14
  val TIME_TO_CRUISE_REGIONAL = 20
  val TIME_TO_CRUISE_REGIONAL_XL = 22
  val TIME_TO_CRUISE_MEDIUM = 27
  val TIME_TO_CRUISE_OTHER = 35

  def fromId(id : Int) = {
    val modelWithJustId = Model("Unknown", "Unknown", 0, 0, 0, 0, 0, 0, 0, 0, 0, Manufacturer("Unknown", countryCode = ""), runwayRequirement = 0)
    modelWithJustId.id = id
    modelWithJustId
  }

  object Type extends Enumeration {
    type Type = Value
    val AIRSHIP, HELICOPTER, PROPELLER_SMALL, PROPELLER_MEDIUM, SMALL, REGIONAL, REGIONAL_XL, MEDIUM, MEDIUM_XL, LARGE, EXTRA_LARGE, JUMBO, JUMBO_XL, SUPERSONIC = Value

    val label: Type => String = {
      case AIRSHIP => "Airship"
      case HELICOPTER => "Helicopter"
      case PROPELLER_SMALL => "Small Prop"
      case PROPELLER_MEDIUM => "Large Prop"
      case SMALL => "Small Jet"
      case REGIONAL => "Regional Jet"
      case REGIONAL_XL => "Regional Jet XL"
      case MEDIUM => "Narrow-body"
      case MEDIUM_XL => "Narrow-body XL"
      case LARGE => "Wide-body"
      case EXTRA_LARGE => "Wide-body XL"
      case JUMBO => "Jumbo"
      case JUMBO_XL => "Jumbo XL"
      case SUPERSONIC => "Supersonic"
    }

    implicit class TypeOps(airplaneType: Type) {
      def label: String = Type.label(airplaneType)
    }

    val size: Type => Double = {
      case HELICOPTER => 0.03
      case PROPELLER_SMALL => 0.05
      case PROPELLER_MEDIUM => 0.09
      case SMALL => 0.06
      case REGIONAL => 0.1
      case REGIONAL_XL => 0.11
      case MEDIUM => 0.14
      case MEDIUM_XL => 0.18
      case AIRSHIP => 0.19
      case SUPERSONIC => 0.24
      case LARGE => 0.20
      case EXTRA_LARGE => 0.25
      case JUMBO => 0.3
      case JUMBO_XL => 0.33
    }
  }

  object Category extends Enumeration {
    type Category = Value
    val SPECIAL, SMALL, REGIONAL, MEDIUM, LARGE, EXTRAORDINARY = Value
    val grouping: Map[Model.Category.Value, List[Type.Value]] = Map(
      SMALL -> List(Type.SMALL, Type.PROPELLER_SMALL),
      REGIONAL -> List(Type.REGIONAL, Type.PROPELLER_MEDIUM, Type.REGIONAL_XL),
      MEDIUM -> List(Type.MEDIUM, Type.MEDIUM_XL),
      LARGE -> List(Type.LARGE, Type.EXTRA_LARGE),
      EXTRAORDINARY -> List(Type.JUMBO, Type.JUMBO_XL, Type.SUPERSONIC),
      SPECIAL -> List(Type.AIRSHIP, Type.HELICOPTER),
    )

    val fromType: Type.Value => Model.Category.Value = (airplaneType : Type.Value) => {
      grouping.find(_._2.contains(airplaneType)).map(_._1).getOrElse(Model.Category.EXTRAORDINARY)
    }

    val capacityRange : Map[Category.Value, (Int, Int)]= {
      AirplaneModelCache.allModels.values.groupBy(_.category).view.mapValues { models =>
        val sortedByCapacity = models.toList.sortBy(_.capacity)
        (sortedByCapacity.head.capacity, sortedByCapacity.last.capacity)
      }.toMap
    }

    val speedRange: Map[Category.Value, (Int, Int)] = {
      AirplaneModelCache.allModels.values.groupBy(_.category).view.mapValues { models =>
        val sortedBySpeed = models.toList.sortBy(_.speed)
        (sortedBySpeed.head.speed, sortedBySpeed.last.speed)
      }.toMap
    }

    def getCapacityRange(category: Category.Value): (Int, Int) = {
      capacityRange.getOrElse(category, (0, 0))
    }

    def getSpeedRange(category: Category.Value): (Int, Int) = {
      speedRange.getOrElse(category, (0, 0))
    }

  }

  val models = List(
Model("Airbus A220-100",	"Airbus A220",	135,	7,	0.85,	1.09,	828,	5375,	96075826,	1456,	20,	Manufacturer("Airbus",	"NL"),	1483,	"https://www.norebbo.com/2016/02/bombardier-cs100-blank-illustration-templates/"),
Model("Airbus A220-300",	"Airbus A220",	160,	7,	0.82,	1.02,	828,	5050,	127057588,	1560,	20,	Manufacturer("Airbus",	"NL"),	1890,	"https://www.norebbo.com/2016/02/bombardier-cs300-blank-illustration-templates/"),
Model("Airbus A220-500",	"Airbus A220",	188,	7,	0.8,	0.9,	828,	5250,	164112082,	1560,	30,	Manufacturer("Airbus",	"NL"),	2000,	""),
Model("Airbus A300-600",	"Airbus A300/A310",	304,	5,	1.79,	1.29,	833,	6125,	95906431,	1820,	36,	Manufacturer("Airbus",	"NL"),	2480,	"https://www.norebbo.com/2018/11/airbus-a300b4-600r-blank-illustration-templates-with-general-electric-engines/"),
Model("Airbus A300B4",	"Airbus A300/A310",	320,	5,	1.18,	1.64,	847,	5375,	75541546,	1820,	24,	Manufacturer("Airbus",	"NL"),	2250,	"https://www.norebbo.com/2018/11/airbus-a300b4-600r-blank-illustration-templates-with-general-electric-engines/"),
Model("Airbus A310-200",	"Airbus A300/A310",	240,	6,	1.5,	1.17,	850,	5800,	125882599,	1820,	36,	Manufacturer("Airbus",	"NL"),	2070,	"https://www.norebbo.com/2015/07/airbus-a310-300-blank-illustration-templates/"),
Model("Airbus A310-300",	"Airbus A300/A310",	240,	6,	1.73,	1.14,	850,	8500,	129123472,	1820,	36,	Manufacturer("Airbus",	"NL"),	2380,	"https://www.norebbo.com/2015/07/airbus-a310-300-blank-illustration-templates/"),
Model("Airbus A318",	"Airbus A320",	136,	6,	1.68,	1.22,	829,	5569,	55856730,	1560,	8,	Manufacturer("Airbus",	"NL"),	1880,	"https://www.norebbo.com/airbus-a318-blank-illustration-templates-with-pratt-whitney-and-cfm56-engines/"),
Model("Airbus A319",	"Airbus A320",	160,	6,	1.69,	1.21,	830,	4862,	68122083,	1560,	16,	Manufacturer("Airbus",	"NL"),	1950,	"https://www.norebbo.com/2014/05/airbus-a319-blank-illustration-templates/"),
Model("Airbus A319CJ",	"Airbus A320",	125,	10,	2.95,	2.5,	855,	7190,	90482141,	1664,	20,	Manufacturer("Airbus",	"NL"),	1880,	"https://www.norebbo.com/2014/05/airbus-a319-blank-illustration-templates/"),
Model("Airbus A319neo",	"Airbus A320",	160,	7,	1.44,	0.97,	828,	5540,	111256795,	1560,	20,	Manufacturer("Airbus",	"NL"),	1880,	"https://www.norebbo.com/2017/09/airbus-a319-neo-blank-illustration-templates/"),
Model("Airbus A320",	"Airbus A320",	195,	6,	1.71,	1.19,	828,	5408,	79740433,	1560,	20,	Manufacturer("Airbus",	"NL"),	2150,	"https://www.norebbo.com/2013/08/airbus-a320-blank-illustration-templates/"),
Model("Airbus A320neo",	"Airbus A320",	195,	7,	1.44,	0.96,	833,	5780,	137944060,	1560,	24,	Manufacturer("Airbus",	"NL"),	1970,	"https://www.norebbo.com/2017/08/airbus-a320-neo-blank-illustration-templates/"),
Model("Airbus A321",	"Airbus A320",	236,	6,	1.72,	1.16,	830,	5440,	103795525,	1560,	24,	Manufacturer("Airbus",	"NL"),	2210,	"https://www.norebbo.com/2014/03/airbus-a321-blank-illustration-templates/"),
Model("Airbus A321neo",	"Airbus A320",	244,	7,	1.43,	0.94,	828,	5450,	173483541,	1560,	30,	Manufacturer("Airbus",	"NL"),	2177,	"https://www.norebbo.com/2017/09/airbus-a321-neo-blank-illustration-templates/"),
Model("Airbus A321neoLR",	"Airbus A320",	230,	7,	1.84,	0.92,	828,	7200,	189838671,	1664,	36,	Manufacturer("Airbus",	"NL"),	2495,	"https://www.norebbo.com/2018/10/airbus-a321neo-lr-long-range-blank-illustration-templates/"),
Model("Airbus A321neoXLR",	"Airbus A320",	230,	7,	1.86,	0.87,	828,	8300,	207394219,	1820,	36,	Manufacturer("Airbus",	"NL"),	2900,	"https://www.norebbo.com/2018/10/airbus-a321neo-lr-long-range-blank-illustration-templates/"),
Model("Airbus A330-200",	"Airbus A330",	406,	6,	2.16,	0.95,	871,	11300,	291042857,	1820,	24,	Manufacturer("Airbus",	"NL"),	2770,	"https://www.norebbo.com/2016/02/airbus-a330-200-blank-illustration-templates-with-pratt-whitney-engines/"),
Model("Airbus A330-300",	"Airbus A330",	440,	6,	2.2,	1.01,	871,	10250,	293222232,	1820,	24,	Manufacturer("Airbus",	"NL"),	2770,	"https://www.norebbo.com/2016/02/airbus-a330-300-blank-illustration-templates-with-all-three-engine-options/"),
Model("Airbus A330-800neo",	"Airbus A330",	406,	7,	2.12,	0.9,	918,	10800,	422412872,	1820,	36,	Manufacturer("Airbus",	"NL"),	2770,	"https://www.norebbo.com/2018/06/airbus-a330-800-neo-blank-illustration-templates/"),
Model("Airbus A330-900neo",	"Airbus A330",	440,	7,	2.04,	0.89,	918,	10939,	466005591,	1820,	36,	Manufacturer("Airbus",	"NL"),	2770,	"https://www.norebbo.com/2018/06/airbus-a330-900-neo-blank-illustration-templates/"),
Model("Airbus A330CJ",	"Airbus A330",	300,	10,	3.05,	1.42,	871,	13860,	318009106,	1820,	30,	Manufacturer("Airbus",	"NL"),	2770,	"https://www.norebbo.com/2016/02/airbus-a330-300-blank-illustration-templates-with-all-three-engine-options/"),
Model("Airbus A340-300",	"Airbus A340",	350,	6,	5.08,	0.95,	880,	12824,	210001410,	1768,	36,	Manufacturer("Airbus",	"NL"),	3000,	"https://www.norebbo.com/2016/04/airbus-340-300-and-a340-300x-blank-illustration-templates/"),
Model("Airbus A340-500",	"Airbus A340",	375,	6,	5.04,	0.94,	871,	15345,	231758018,	1768,	36,	Manufacturer("Airbus",	"NL"),	3250,	"https://www.norebbo.com/2016/08/airbus-a340-500-blank-illustration-templates/"),
Model("Airbus A340-600",	"Airbus A340",	440,	6,	4.97,	0.87,	905,	13965,	321861977,	1820,	36,	Manufacturer("Airbus",	"NL"),	3200,	"https://www.norebbo.com/2016/11/airbus-a340-600-blank-illustration-templates/"),
Model("Airbus A350-1000",	"Airbus A350",	480,	7,	2.6,	0.85,	910,	14535,	528367790,	1768,	36,	Manufacturer("Airbus",	"NL"),	2880,	"https://www.norebbo.com/2015/11/airbus-a350-1000-blank-illustration-templates/"),
Model("Airbus A350-1000 Sunrise",	"Airbus A350",	390,	9,	4.14,	0.77,	910,	17800,	537285132,	1768,	36,	Manufacturer("Airbus",	"NL"),	2900,	"https://www.norebbo.com/2015/11/airbus-a350-1000-blank-illustration-templates/"),
Model("Airbus A350-900",	"Airbus A350",	440,	7,	2.69,	0.87,	903,	14147,	457910770,	1768,	36,	Manufacturer("Airbus",	"NL"),	2600,	"https://www.norebbo.com/2013/07/airbus-a350-900-blank-illustration-templates/"),
Model("Airbus A350-900ULR",	"Airbus A350",	374,	8,	4.23,	0.84,	910,	16449,	524257447,	1768,	36,	Manufacturer("Airbus",	"NL"),	2700,	"https://www.norebbo.com/2013/07/airbus-a350-900-blank-illustration-templates/"),
Model("Airbus A380-800",	"Airbus A380",	853,	7,	4.46,	1.07,	925,	14200,	472173429,	1820,	40,	Manufacturer("Airbus",	"NL"),	3200,	"https://www.norebbo.com/2013/06/airbus-a380-800-blank-illustration-templates/"),
Model("Airbus ZeroE Turbofan",	"Airbus ZE",	150,	8,	0.21,	0.68,	795,	2620,	148783216,	1300,	20,	Manufacturer("Airbus",	"NL"),	2100,	""),
Model("Airbus ZeroE Turboprop",	"Airbus ZE",	90,	8,	0.11,	0.48,	674,	1650,	104525466,	1300,	24,	Manufacturer("Airbus",	"NL"),	2000,	""),
Model("Antonov An-10A",	"Antonov An",	130,	1,	2.02,	1.62,	680,	1300,	1062865,	1456,	8,	Manufacturer("Antonov",	"UA"),	975,	""),
Model("Antonov An-148",	"Antonov An",	85,	5,	1.2,	1.85,	835,	3500,	19085895,	1040,	6,	Manufacturer("Antonov",	"UA"),	1100,	"https://en.wikipedia.org/wiki/Antonov_An-24"),
Model("Antonov An-24",	"Antonov An",	50,	1,	1.24,	2.14,	450,	680,	751661,	1456,	4,	Manufacturer("Antonov",	"UA"),	970,	"https://en.wikipedia.org/wiki/Antonov_An-24"),
Model("Antonov An-72",	"Antonov An",	52,	3,	1.42,	1.97,	700,	2600,	4940652,	1456,	4,	Manufacturer("Antonov",	"UA"),	700,	""),
Model("ATR 42-400",	"ATR-Regional",	48,	5,	0.9,	2,	484,	1420,	9114000,	1040,	0,	Manufacturer("ATR",	"FR"),	1050,	"https://www.norebbo.com/atr-42-blank-illustration-templates/"),
Model("ATR 42-600S",	"ATR-Regional",	48,	6,	0.88,	1.98,	535,	1260,	14458826,	1040,	0,	Manufacturer("ATR",	"FR"),	750,	"https://www.norebbo.com/atr-42-blank-illustration-templates/"),
Model("ATR 72-200",	"ATR-Regional",	68,	5,	0.75,	1.85,	517,	1464,	14781115,	1040,	0,	Manufacturer("ATR",	"FR"),	1199,	"https://www.norebbo.com/2017/04/atr-72-blank-illustration-templates/"),
Model("ATR 72-600",	"ATR-Regional",	72,	6,	0.69,	1.79,	510,	1655,	24369937,	1040,	4,	Manufacturer("ATR",	"FR"),	1249,	"https://www.norebbo.com/2017/04/atr-72-blank-illustration-templates/"),
Model("Aurora D8",	"Aurora D",	188,	9,	1.18,	0.65,	937,	5200,	318443929,	1820,	36,	Manufacturer("Aurora Flight Sciences",	"US"),	2300,	""),
Model("BAe 146-100",	"BAe 146",	82,	4,	0.81,	2.6,	747,	2613,	13929925,	1560,	2,	Manufacturer("BAe",	"GB"),	1195,	"https://www.norebbo.com/2018/11/british-aerospace-bae-146-200-avro-rj85-blank-illustration-templates/"),
Model("BAe 146-200",	"BAe 146",	100,	4,	0.78,	2.49,	747,	2250,	17624407,	1560,	2,	Manufacturer("BAe",	"GB"),	1390,	"https://www.norebbo.com/2018/11/british-aerospace-bae-146-200-avro-rj85-blank-illustration-templates/"),
Model("BAe 146-300",	"BAe 146",	112,	4,	0.73,	2.37,	747,	2145,	19954820,	1560,	2,	Manufacturer("BAe",	"GB"),	1535,	"https://www.norebbo.com/2018/11/british-aerospace-bae-146-200-avro-rj85-blank-illustration-templates/"),
Model("BAe Jetstream 31",	"BAe Jetstream",	19,	6,	2.64,	1,	430,	1260,	6807212,	1820,	0,	Manufacturer("BAe",	"GB"),	1100,	"https://www.norebbo.com/british-aerospace-jetstream-41-blank-illustration-templates/"),
Model("BAe Jetstream 41",	"BAe Jetstream",	29,	8,	2.57,	0.97,	482,	1210,	17627638,	1820,	0,	Manufacturer("BAe",	"GB"),	1090,	"https://www.norebbo.com/british-aerospace-jetstream-41-blank-illustration-templates/"),
Model("Beechcraft 1900D",	"Beechcraft",	17,	6,	1.4,	1.4,	518,	707,	6125290,	1820,	0,	Manufacturer("Beechcraft",	"US"),	1140,	"https://www.norebbo.com/beechcraft-1900d-blank-illustration-templates/"),
Model("Beechcraft B200 Super King Air",	"Beechcraft",	11,	5,	1.61,	1.4,	561,	2800,	3386101,	1820,	0,	Manufacturer("Beechcraft",	"US"),	1020,	"https://www.norebbo.com/beechcraft-b200-king-air-side-view/"),
Model("Boeing 2707",	"Boeing 2707",	247,	8,	4.44,	4.8,	3300,	5600,	698463456,	1664,	42,	Manufacturer("Boeing",	"US"),	3590,	""),
Model("Boeing 707-120",	"Boeing 707",	194,	3,	2.09,	1.73,	952,	5100,	17875679,	2496,	16,	Manufacturer("Boeing",	"US"),	2700,	"https://www.norebbo.com/boeing-707-320c-blank-illustration-templates/"),
Model("Boeing 707-320",	"Boeing 707",	189,	4,	3.23,	1.75,	952,	8200,	29236039,	2496,	20,	Manufacturer("Boeing",	"US"),	3150,	"https://www.norebbo.com/boeing-707-320c-blank-illustration-templates/"),
Model("Boeing 720B",	"Boeing 707",	165,	3,	1.92,	1.7,	896,	5700,	15601244,	1976,	16,	Manufacturer("Boeing",	"US"),	2000,	""),
Model("Boeing 727-100",	"Boeing 727",	131,	3,	1.65,	1.72,	960,	3983,	13627560,	2184,	16,	Manufacturer("Boeing",	"US"),	1750,	"https://www.norebbo.com/boeing-727-100-blank-illustration-templates/"),
Model("Boeing 727-200",	"Boeing 727",	189,	4,	1.53,	1.65,	811,	3695,	31008496,	2184,	24,	Manufacturer("Boeing",	"US"),	1800,	"https://www.norebbo.com/2018/03/boeing-727-200-blank-illustration-templates/"),
Model("Boeing 737 MAX 10",	"Boeing 737",	230,	7,	0.75,	1.29,	830,	5400,	156662034,	1664,	36,	Manufacturer("Boeing",	"US"),	2700,	"https://www.norebbo.com/2019/01/737-10-max-side-view/"),
Model("Boeing 737 MAX 7",	"Boeing 737",	172,	7,	0.76,	1.34,	830,	6800,	121801024,	1664,	24,	Manufacturer("Boeing",	"US"),	2100,	"https://www.norebbo.com/2016/07/boeing-737-max-7-blank-illustration-templates/"),
Model("Boeing 737 MAX 8",	"Boeing 737",	189,	7,	0.78,	1.32,	830,	6306,	130905753,	1664,	24,	Manufacturer("Boeing",	"US"),	2035,	"https://www.norebbo.com/2016/07/boeing-737-max-8-blank-illustration-templates/"),
Model("Boeing 737 MAX 8-200",	"Boeing 737",	210,	6,	0.46,	1.29,	839,	4090,	130945761,	1664,	24,	Manufacturer("Boeing",	"US"),	1820,	"https://www.norebbo.com/2016/07/boeing-737-max-8-blank-illustration-templates/"),
Model("Boeing 737 MAX 9",	"Boeing 737",	220,	7,	0.79,	1.31,	839,	5830,	149961833,	1664,	24,	Manufacturer("Boeing",	"US"),	2600,	"https://www.norebbo.com/2018/05/boeing-737-9-max-blank-illustration-templates/"),
Model("Boeing 737-100",	"Boeing 737",	124,	4,	1.34,	1.92,	780,	2850,	11136624,	2028,	8,	Manufacturer("Boeing",	"US"),	1800,	"https://www.norebbo.com/2018/10/boeing-737-100-blank-illustration-templates/"),
Model("Boeing 737-200",	"Boeing 737",	136,	4,	1.3,	1.85,	780,	3693,	16595026,	2028,	12,	Manufacturer("Boeing",	"US"),	1859,	"https://www.norebbo.com/2018/09/boeing-737-200-blank-illustration-templates/"),
Model("Boeing 737-300",	"Boeing 737",	144,	5,	1.16,	1.7,	800,	4128,	41287481,	1976,	16,	Manufacturer("Boeing",	"US"),	2010,	"https://www.norebbo.com/2018/09/boeing-737-300-blank-illustration-templates/"),
Model("Boeing 737-400",	"Boeing 737",	168,	5,	1.13,	1.67,	800,	4105,	49300190,	1976,	16,	Manufacturer("Boeing",	"US"),	2540,	"https://www.norebbo.com/2018/09/boeing-737-400-blank-illustration-templates/"),
Model("Boeing 737-500",	"Boeing 737",	132,	5,	1.2,	1.73,	800,	4249,	36796672,	1820,	12,	Manufacturer("Boeing",	"US"),	2280,	"https://www.norebbo.com/2018/09/boeing-737-500-blank-illustration-templates-with-and-without-blended-winglets/"),
Model("Boeing 737-600",	"Boeing 737",	130,	6,	1.03,	1.55,	834,	4681,	60181523,	1820,	16,	Manufacturer("Boeing",	"US"),	1804,	"https://www.norebbo.com/2018/09/boeing-737-600-blank-illustration-templates/"),
Model("Boeing 737-700",	"Boeing 737",	148,	6,	1,	1.53,	834,	5838,	67878016,	1820,	16,	Manufacturer("Boeing",	"US"),	1655,	"https://www.norebbo.com/2014/04/boeing-737-700-blank-illustration-templates/"),
Model("Boeing 737-700ER",	"Boeing 737",	140,	6,	1.29,	1.51,	834,	7200,	62561185,	1820,	16,	Manufacturer("Boeing",	"US"),	2060,	"https://www.norebbo.com/2014/04/boeing-737-700-blank-illustration-templates/"),
Model("Boeing 737-800",	"Boeing 737",	188,	6,	0.98,	1.5,	842,	5279,	76131320,	1664,	20,	Manufacturer("Boeing",	"US"),	2230,	"https://www.norebbo.com/2012/11/boeing-737-800-blank-illustration-templates/"),
Model("Boeing 737-900ER",	"Boeing 737",	215,	6,	1.05,	1.46,	844,	5638,	91453860,	1664,	24,	Manufacturer("Boeing",	"US"),	2880,	"https://www.norebbo.com/2016/07/boeing-737-900er-with-split-scimitar-winglets-blank-illustration-templates/"),
Model("Boeing BBJ MAX 8",	"Boeing 737",	180,	10,	2.32,	2.39,	844,	11227,	134667756,	2080,	20,	Manufacturer("Boeing",	"US"),	2500,	"https://www.norebbo.com/2016/07/boeing-737-max-8-blank-illustration-templates/"),
Model("Boeing BBJ1",	"Boeing 737",	116,	10,	2.96,	2.22,	872,	9933,	56452547,	1820,	20,	Manufacturer("Boeing",	"US"),	2060,	"https://www.norebbo.com/2014/04/boeing-737-700-blank-illustration-templates/"),
Model("Boeing 747-100",	"Boeing 747",	520,	4,	3.01,	1.57,	907,	8530,	40868218,	1820,	36,	Manufacturer("Boeing",	"US"),	3250,	"https://www.norebbo.com/2019/07/boeing-747-100-side-view/"),
Model("Boeing 747-200",	"Boeing 747",	520,	4,	3.26,	1.4,	907,	10854,	53525431,	1820,	36,	Manufacturer("Boeing",	"US"),	3300,	"https://www.norebbo.com/2019/08/boeing-747-200-side-view/"),
Model("Boeing 747-300",	"Boeing 747",	520,	5,	2.89,	1.26,	939,	11127,	160657770,	1820,	36,	Manufacturer("Boeing",	"US"),	2955,	"https://www.norebbo.com/boeing-747-300-side-view/"),
Model("Boeing 747-400",	"Boeing 747",	585,	6,	2.95,	1.08,	933,	12534,	348668047,	1820,	40,	Manufacturer("Boeing",	"US"),	2980,	"https://www.norebbo.com/2013/09/boeing-747-400-blank-illustration-templates/"),
Model("Boeing 747-400D",	"Boeing 747",	605,	6,	0.97,	1.02,	933,	3200,	351547572,	1664,	40,	Manufacturer("Boeing",	"US"),	2480,	"https://www.norebbo.com/2013/09/boeing-747-400-blank-illustration-templates/"),
Model("Boeing 747-8i",	"Boeing 747",	645,	7,	2.84,	0.92,	933,	13804,	586103950,	1820,	40,	Manufacturer("Boeing",	"US"),	3180,	"https://www.norebbo.com/2015/12/boeing-747-8i-blank-illustration-templates/"),
Model("Boeing 747SP",	"Boeing 747",	388,	5,	4.8,	1.42,	994,	12051,	47835756,	1820,	36,	Manufacturer("Boeing",	"US"),	2820,	"https://www.norebbo.com/2019/08/boeing-747sp-side-view/"),
Model("Boeing 757-200",	"Boeing 757",	239,	5,	1.79,	1.3,	854,	6016,	80311748,	1976,	24,	Manufacturer("Boeing",	"US"),	2240,	"https://www.norebbo.com/2015/01/boeing-757-200-blank-illustration-templates/"),
Model("Boeing 757-200ER",	"Boeing 757",	239,	6,	2.29,	1.27,	850,	7378,	102416957,	1976,	24,	Manufacturer("Boeing",	"US"),	2550,	"https://www.norebbo.com/2015/01/boeing-757-200-blank-illustration-templates/"),
Model("Boeing 757-300",	"Boeing 757",	295,	6,	1.75,	1.27,	850,	5742,	150631710,	1976,	30,	Manufacturer("Boeing",	"US"),	2400,	"https://www.norebbo.com/2017/03/boeing-757-300-blank-illustration-templates/"),
Model("Boeing 767-200",	"Boeing 767",	245,	5,	1.75,	1.24,	860,	6240,	74481167,	1664,	30,	Manufacturer("Boeing",	"US"),	1900,	"https://www.norebbo.com/2014/07/boeing-767-200-blank-illustration-templates/"),
Model("Boeing 767-200ER",	"Boeing 767",	245,	5,	2.19,	1.23,	896,	11272,	79831347,	1768,	36,	Manufacturer("Boeing",	"US"),	2480,	"https://www.norebbo.com/2014/07/boeing-767-200-blank-illustration-templates/"),
Model("Boeing 767-300",	"Boeing 767",	290,	5,	1.68,	1.2,	860,	6800,	95324838,	1664,	30,	Manufacturer("Boeing",	"US"),	2800,	"https://www.norebbo.com/boeing-767-300-blank-illustration-templates/"),
Model("Boeing 767-300ER",	"Boeing 767",	290,	6,	2.12,	1.17,	896,	11093,	151768708,	1768,	36,	Manufacturer("Boeing",	"US"),	2650,	"https://www.norebbo.com/boeing-767-300-blank-illustration-templates/"),
Model("Boeing 767-400ER",	"Boeing 767",	375,	6,	2.02,	1.15,	896,	8600,	200622715,	1768,	30,	Manufacturer("Boeing",	"US"),	3220,	"https://www.norebbo.com/boeing-767-400-blank-illustration-templates/"),
Model("Boeing 777-200",	"Boeing 777",	440,	6,	1.89,	0.96,	896,	8405,	289889719,	1664,	30,	Manufacturer("Boeing",	"US"),	2350,	"https://www.norebbo.com/2012/12/boeing-777-200-blank-illustration-templates/"),
Model("Boeing 777-200ER",	"Boeing 777",	440,	7,	3.3,	0.99,	896,	12680,	336793106,	1664,	36,	Manufacturer("Boeing",	"US"),	3140,	"https://www.norebbo.com/2012/12/boeing-777-200-blank-illustration-templates/"),
Model("Boeing 777-200LR",	"Boeing 777",	440,	7,	3.4,	0.95,	896,	14204,	350939943,	1664,	36,	Manufacturer("Boeing",	"US"),	2940,	"https://www.norebbo.com/2012/12/boeing-777-200-blank-illustration-templates/"),
Model("Boeing 777-300",	"Boeing 777",	520,	7,	1.48,	0.95,	945,	7464,	473204683,	1664,	36,	Manufacturer("Boeing",	"US"),	2950,	"https://www.norebbo.com/2014/03/boeing-777-300-blank-illustration-templates/"),
Model("Boeing 777-300ER",	"Boeing 777",	520,	7,	3.12,	0.9,	945,	13712,	491099644,	1664,	36,	Manufacturer("Boeing",	"US"),	3120,	"https://www.norebbo.com/2014/03/boeing-777-300-blank-illustration-templates/"),
Model("Boeing 777-8",	"Boeing 777",	480,	8,	3.52,	0.56,	896,	15590,	705837669,	1664,	36,	Manufacturer("Boeing",	"US"),	3050,	"https://www.norebbo.com/2019/12/boeing-777-8-side-view/"),
Model("Boeing 777-9",	"Boeing 777",	550,	8,	3.6,	0.54,	896,	13940,	771554976,	1664,	36,	Manufacturer("Boeing",	"US"),	3050,	"https://www.norebbo.com/2019/12/boeing-777-9-side-view/"),
Model("Boeing 787-8 Dreamliner",	"Boeing 787",	366,	7,	3.3,	0.75,	903,	14293,	391997315,	1664,	36,	Manufacturer("Boeing",	"US"),	2700,	"https://www.norebbo.com/2013/02/boeing-787-8-blank-illustration-templates/"),
Model("Boeing 787-9 Dreamliner",	"Boeing 787",	406,	7,	3.21,	0.72,	903,	13950,	449667317,	1664,	36,	Manufacturer("Boeing",	"US"),	3000,	"https://www.norebbo.com/2014/04/boeing-787-9-blank-illustration-templates/"),
Model("Boeing 787-10 Dreamliner",	"Boeing 787",	440,	7,	3.29,	0.72,	903,	11920,	494098395,	1664,	36,	Manufacturer("Boeing",	"US"),	3200,	"https://www.norebbo.com/2017/06/boeing-787-10-blank-illustration-templates/"),
Model("Boeing BBJ 787-9",	"Boeing 787",	340,	10,	3.58,	1.4,	968,	17880,	552473642,	2080,	40,	Manufacturer("Boeing",	"US"),	2900,	"https://www.norebbo.com/2014/04/boeing-787-9-blank-illustration-templates/"),
Model("Boeing 797-6",	"Boeing 797",	230,	8,	1.28,	0.7,	890,	7840,	319454338,	1820,	40,	Manufacturer("Boeing",	"US"),	2600,	"https://www.norebbo.com/boeing-797-side-view/"),
Model("Boeing 797-7",	"Boeing 797",	275,	8,	1.23,	0.63,	890,	7200,	394699217,	1820,	40,	Manufacturer("Boeing",	"US"),	2600,	"https://www.norebbo.com/boeing-797-side-view/"),
Model("Boeing Vertol 107-II ",	"Boeing Vertol",	28,	4,	1.05,	3.15,	265,	1020,	1745382,	1300,	4,	Manufacturer("Boeing",	"US"),	1,	""),
Model("Boeing Vertol 234",	"Boeing Vertol",	44,	4,	1.06,	3.17,	269,	1010,	2674539,	1300,	4,	Manufacturer("Boeing",	"US"),	1,	""),
Model("Bombardier CRJ100",	"Bombardier CRJ",	50,	4,	1.73,	2.3,	830,	1820,	7939674,	1456,	0,	Manufacturer("Bombardier",	"CA"),	1920,	"https://www.norebbo.com/2015/04/bombardier-canadair-regional-jet-200-blank-illustration-templates/"),
Model("Bombardier CRJ1000",	"Bombardier CRJ",	104,	5,	1.41,	1.91,	870,	1600,	28165384,	1456,	8,	Manufacturer("Bombardier",	"CA"),	2120,	"https://www.norebbo.com/2019/06/bombardier-crj-1000-side-view/"),
Model("Bombardier CRJ200",	"Bombardier CRJ",	50,	5,	1.67,	2.21,	830,	2813,	14046811,	1456,	0,	Manufacturer("Bombardier",	"CA"),	1920,	"https://www.norebbo.com/2015/04/bombardier-canadair-regional-jet-200-blank-illustration-templates/"),
Model("Bombardier CRJ700",	"Bombardier CRJ",	78,	5,	1.58,	2.08,	828,	2480,	23447861,	1456,	4,	Manufacturer("Bombardier",	"CA"),	1605,	"https://www.norebbo.com/2015/05/bombardier-canadair-regional-jet-700-blank-illustration-templates/"),
Model("Bombardier CRJ900",	"Bombardier CRJ",	90,	5,	1.44,	1.88,	870,	2931,	26860432,	1456,	8,	Manufacturer("Bombardier",	"CA"),	1939,	"https://www.norebbo.com/2016/07/bombardier-canadair-regional-jet-900-blank-illustration-templates/"),
Model("Boom Overture",	"Boom Overture",	138,	10,	2.7,	2.92,	1800,	7870,	487919918,	1664,	100,	Manufacturer("Boom Technology",	"US"),	3048,	""),
Model("CASA C-212 Aviocar",	"CASA",	26,	3,	0.92,	3.02,	354,	2680,	1554789,	1560,	0,	Manufacturer("CASA",	"ES"),	600,	"https://en.wikipedia.org/wiki/CASA_C-212_Aviocar"),
Model("CASA CN-235",	"CASA",	40,	4,	0.87,	2.82,	460,	3658,	4739257,	1560,	0,	Manufacturer("CASA",	"ES"),	1204,	"https://en.wikipedia.org/wiki/CASA_C-212_Aviocar"),
Model("Cessna 208 Caravan",	"Cessna",	14,	5,	0.91,	2.43,	355,	1680,	3082293,	1768,	0,	Manufacturer("Cessna",	"US"),	762,	"https://www.norebbo.com/2017/06/cessna-208-grand-caravan-blank-illustration-templates/"),
Model("Cessna 408 Skycourier",	"Cessna",	19,	6,	0.84,	2.45,	390,	715,	6491899,	1768,	0,	Manufacturer("Cessna",	"US"),	810,	"https://www.norebbo.com/2017/06/cessna-208-grand-caravan-blank-illustration-templates/"),
Model("Comac C949",	"Comac",	120,	9,	4.14,	3.84,	1838,	10400,	325284249,	1560,	36,	Manufacturer("COMAC",	"CN"),	3240,	""),
Model("Comac C909 ER",	"Comac C909",	90,	5,	2.3,	2.3,	828,	3200,	12564840,	1248,	8,	Manufacturer("COMAC",	"CN"),	1780,	"https://www.norebbo.com/comac-c919-side-view/"),
Model("Comac C909 STD",	"Comac C909",	90,	5,	1.82,	2.31,	828,	2040,	12402475,	1248,	8,	Manufacturer("COMAC",	"CN"),	1700,	"https://www.norebbo.com/comac-c919-side-view/"),
Model("Comac C919-100 ER",	"Comac C919",	168,	6,	1.93,	1.4,	838,	4300,	58469649,	1456,	16,	Manufacturer("COMAC",	"CN"),	2000,	"https://www.norebbo.com/comac-c919-side-view/"),
Model("Comac C919-100 STD",	"Comac C919",	168,	6,	1.46,	1.46,	838,	2800,	54492196,	1352,	16,	Manufacturer("COMAC",	"CN"),	2000,	"https://www.norebbo.com/comac-c919-side-view/"),
Model("Comac C919-300",	"Comac C919",	198,	6,	1.3,	1.3,	838,	2670,	77874948,	1456,	20,	Manufacturer("COMAC",	"CN"),	2120,	"https://www.norebbo.com/comac-c919-side-view/"),
Model("Comac C929-600",	"Comac C929",	405,	6,	2.45,	1.02,	908,	11890,	197077511,	1560,	24,	Manufacturer("COMAC",	"CN"),	2780,	"https://www.norebbo.com/comac-c919-side-view/"),
Model("Comac C939",	"Comac C939",	400,	7,	2.65,	1,	908,	10720,	330517291,	1560,	30,	Manufacturer("COMAC",	"CN"),	2780,	""),
Model("Concorde",	"Concorde",	130,	7,	4.67,	4.07,	2158,	6332,	336690855,	1560,	60,	Manufacturer("BAe",	"GB"),	3390,	"https://www.norebbo.com/aerospatiale-bac-concorde-blank-illustration-templates/"),
Model("Convair 880",	"Convair",	110,	4,	1.94,	1.81,	880,	5636,	11702156,	1560,	4,	Manufacturer("Convair",	"US"),	2670,	"https://en.wikipedia.org/wiki/Convair_880"),
Model("Convair 990 Coronado",	"Convair",	121,	4,	1.81,	1.72,	990,	6116,	16425364,	1560,	8,	Manufacturer("Convair",	"US"),	2875,	"https://en.wikipedia.org/wiki/Convair_880"),
Model("Convair 990A Coronado",	"Convair",	149,	5,	2.21,	1.52,	1030,	6116,	35619224,	1560,	12,	Manufacturer("Convair",	"US"),	3002,	"https://en.wikipedia.org/wiki/Convair_880"),
Model("Dassault Mercure",	"Dassault",	162,	2,	2.6,	2.04,	926,	1684,	1933089,	1560,	12,	Manufacturer("Dassault Aviation",	"FR"),	2100,	""),
Model("McDonnell Douglas DC-10-10",	"DC-10",	410,	4,	2.33,	1.62,	876,	6500,	42416039,	1820,	16,	Manufacturer("McDonnell Douglas",	"US"),	2700,	"https://www.norebbo.com/mcdonnell-douglas-dc-10-30-blank-templates/"),
Model("McDonnell Douglas DC-10-30",	"DC-10",	410,	4,	2.48,	1.6,	886,	9400,	52175592,	2080,	20,	Manufacturer("McDonnell Douglas",	"US"),	3220,	"https://www.norebbo.com/mcdonnell-douglas-dc-10-30-blank-templates/"),
Model("McDonnell Douglas DC-10-40",	"DC-10",	410,	5,	4.27,	1.55,	886,	12392,	56976522,	2080,	24,	Manufacturer("McDonnell Douglas",	"US"),	2980,	"https://www.norebbo.com/mcdonnell-douglas-dc-10-30-blank-templates/"),
Model("McDonnell Douglas MD-11",	"DC-10",	410,	6,	4.48,	1.5,	886,	11963,	112790220,	1820,	24,	Manufacturer("McDonnell Douglas",	"US"),	3050,	"https://www.norebbo.com/mcdonnell-douglas-md-11-blank-illustration-templates-with-ge-engines/"),
Model("Douglas DC-8-10",	"DC-8",	177,	2,	3.01,	1.76,	895,	3760,	5607289,	2496,	16,	Manufacturer("Douglas Aircraft Company",	"US"),	2840,	""),
Model("Douglas DC-8-55",	"DC-8",	189,	3,	2.73,	1.78,	895,	8700,	18486225,	2496,	16,	Manufacturer("Douglas Aircraft Company",	"US"),	3200,	"https://www.norebbo.com/douglas-dc-8-53-blank-illustration-templates/"),
Model("McDonnell Douglas DC-8-61",	"DC-8",	259,	3,	2.8,	1.73,	895,	6200,	21193906,	2496,	24,	Manufacturer("McDonnell Douglas",	"US"),	2680,	"https://www.norebbo.com/douglas-dc-8-61-blank-illustration-templates/"),
Model("McDonnell Douglas DC-8-62",	"DC-8",	189,	3,	3.81,	1.58,	895,	9800,	22929354,	2496,	16,	Manufacturer("McDonnell Douglas",	"US"),	2680,	"https://www.norebbo.com/douglas-dc-8-61-blank-illustration-templates/"),
Model("McDonnell Douglas DC-8-63",	"DC-8",	259,	3,	2.71,	1.6,	895,	8100,	26932722,	2496,	24,	Manufacturer("McDonnell Douglas",	"US"),	2680,	"https://www.norebbo.com/douglas-dc-8-73-and-dc-8-73cf-blank-illustration-templates/"),
Model("Boeing 717-200",	"DC-9",	123,	6,	0.6,	2.02,	822,	2397,	42552042,	1820,	12,	Manufacturer("Boeing",	"US"),	1872,	"https://www.norebbo.com/2017/06/boeing-717-200-blank-illustration-templates/"),
Model("McDonnell Douglas DC-9-10",	"DC-9",	92,	3,	0.77,	2.31,	965,	2367,	3869405,	1040,	4,	Manufacturer("McDonnell Douglas",	"US"),	1816,	"https://www.norebbo.com/mcdonnell-douglas-dc-9-30-templates/"),
Model("McDonnell Douglas DC-9-30",	"DC-9",	115,	3,	0.75,	2.24,	804,	2778,	4721087,	1040,	4,	Manufacturer("McDonnell Douglas",	"US"),	1900,	"https://www.norebbo.com/mcdonnell-douglas-dc-9-30-templates/"),
Model("McDonnell Douglas DC-9-50",	"DC-9",	139,	3,	0.68,	2.13,	804,	3030,	5434289,	1040,	4,	Manufacturer("McDonnell Douglas",	"US"),	2100,	"https://www.norebbo.com/dc-9-50-side-view/"),
Model("McDonnell Douglas MD-82",	"DC-9",	172,	4,	0.68,	2.1,	820,	2200,	17449851,	1456,	12,	Manufacturer("McDonnell Douglas",	"US"),	2200,	"https://www.norebbo.com/2015/02/mcdonnell-douglas-md-80-blank-illustration-templates/"),
Model("McDonnell Douglas MD-87",	"DC-9",	139,	4,	0.65,	2.2,	820,	3720,	15422873,	1456,	12,	Manufacturer("McDonnell Douglas",	"US"),	2550,	"https://www.norebbo.com/2015/02/mcdonnell-douglas-md-80-blank-illustration-templates/"),
Model("McDonnell Douglas MD-88",	"DC-9",	172,	4,	0.63,	2.01,	828,	3850,	24987390,	1560,	20,	Manufacturer("McDonnell Douglas",	"US"),	2500,	"https://www.norebbo.com/2015/02/mcdonnell-douglas-md-80-blank-illustration-templates/"),
Model("McDonnell Douglas MD-90",	"DC-9",	172,	5,	0.54,	1.98,	820,	4140,	43250892,	1768,	20,	Manufacturer("McDonnell Douglas",	"US"),	2200,	"https://www.norebbo.com/2018/02/mcdonnell-douglas-md-90-blank-illustration-templates/"),
Model("De Havilland DHC-7-100",	"DHC Dash",	50,	3,	0.94,	2.82,	428,	1300,	3701156,	1560,	4,	Manufacturer("De Havilland Canada",	"CA"),	620,	"https://www.norebbo.com/2018/01/de-havilland-dhc-8-200-dash-8-blank-illustration-templates/"),
Model("De Havilland DHC-8-100",	"DHC Dash",	39,	4,	0.91,	2.72,	448,	1889,	5192046,	1560,	2,	Manufacturer("De Havilland Canada",	"CA"),	950,	"https://www.norebbo.com/2018/01/de-havilland-dhc-8-200-dash-8-blank-illustration-templates/"),
Model("De Havilland DHC-8-200",	"DHC Dash",	39,	4,	0.83,	2.6,	448,	2084,	5378718,	1560,	2,	Manufacturer("De Havilland Canada",	"CA"),	1000,	"https://www.norebbo.com/2018/01/de-havilland-dhc-8-200-dash-8-blank-illustration-templates/"),
Model("De Havilland DHC-8-300",	"DHC Dash",	50,	4,	0.8,	2.4,	450,	1711,	7075093,	1560,	4,	Manufacturer("De Havilland Canada",	"CA"),	1085,	"https://www.norebbo.com/2018/05/de-havilland-dhc-8-300-blank-illustration-templates/"),
Model("De Havilland DHC-8-400",	"DHC Dash",	68,	4,	0.67,	2.22,	667,	1980,	14873987,	1560,	4,	Manufacturer("De Havilland Canada",	"CA"),	1085,	"https://www.norebbo.com/bombardier-dhc-8-402-q400-blank-illustration-templates/"),
Model("De Havilland Q400",	"DHC Dash",	78,	5,	0.58,	2.38,	562,	1570,	20778212,	1560,	8,	Manufacturer("De Havilland Canada",	"CA"),	1210,	"https://www.norebbo.com/bombardier-dhc-8-402-q400-blank-illustration-templates/"),
Model("De Havilland Q400 NextGen",	"DHC Dash",	90,	5,	0.5,	2.34,	562,	2040,	23426362,	1560,	8,	Manufacturer("De Havilland Canada",	"CA"),	1425,	"https://www.norebbo.com/bombardier-dhc-8-402-q400-blank-illustration-templates/"),
Model("Dornier 1128",	"Dornier",	128,	8,	1.07,	2.17,	923,	2940,	118495412,	2288,	20,	Manufacturer("Dornier",	"DE"),	1550,	""),
Model("Dornier 328-110",	"Dornier",	33,	8,	0.89,	1.99,	620,	920,	23070550,	1560,	12,	Manufacturer("Dornier",	"DE"),	1088,	"https://www.norebbo.com/2019/01/dornier-328-110-blank-illustration-templates/"),
Model("Dornier 328eco",	"Dornier",	44,	9,	0,	0.11,	600,	1213,	74263784,	1820,	18,	Manufacturer("Dornier",	"DE"),	1082,	""),
Model("Dornier 328JET",	"Dornier",	44,	8,	0.97,	2.07,	740,	1250,	39669449,	1976,	12,	Manufacturer("Dornier",	"DE"),	1367,	"https://www.norebbo.com/2019/01/fairchild-dornier-328jet-illustrations/"),
Model("Dornier 728",	"Dornier",	80,	8,	1.21,	2.31,	980,	2640,	80692540,	2288,	16,	Manufacturer("Dornier",	"DE"),	1463,	""),
Model("Dornier 928",	"Dornier",	109,	8,	1.16,	2.26,	951,	2820,	100429488,	2288,	18,	Manufacturer("Dornier",	"DE"),	1513,	""),
Model("Heart ES-30",	"Electric Props",	30,	9,	0,	0.23,	370,	400,	18712337,	1040,	4,	Manufacturer("Heart Aerospace",	"SE"),	920,	""),
Model("Embraer E170",	"Embraer",	72,	6,	1.4,	1.53,	797,	2400,	36056157,	1352,	2,	Manufacturer("Embraer",	"BR"),	1438,	"https://www.norebbo.com/embraer-erj-175-templates-with-the-new-style-winglets/"),
Model("Embraer E175 LR",	"Embraer",	78,	6,	1.87,	1.51,	797,	2900,	39170038,	1456,	4,	Manufacturer("Embraer",	"BR"),	1420,	"https://www.norebbo.com/2015/10/embraer-erj-175-templates-with-the-new-style-winglets/"),
Model("Embraer E175-E2",	"Embraer",	88,	7,	1.61,	1.64,	833,	3650,	52321839,	1456,	6,	Manufacturer("Embraer",	"BR"),	1800,	"https://www.norebbo.com/2019/03/e175-e2-side-view/"),
Model("Embraer E190",	"Embraer",	100,	6,	1.33,	1.46,	829,	3685,	53325625,	1456,	8,	Manufacturer("Embraer",	"BR"),	1650,	"https://www.norebbo.com/2015/06/embraer-190-blank-illustration-templates/"),
Model("Embraer E190-E2",	"Embraer",	114,	7,	1.22,	1.28,	833,	4620,	81642367,	1456,	8,	Manufacturer("Embraer",	"BR"),	1465,	"https://www.norebbo.com/2019/03/e190-e2-blank-side-view/"),
Model("Embraer E195",	"Embraer",	116,	6,	1.3,	1.43,	829,	3480,	62148144,	1456,	8,	Manufacturer("Embraer",	"BR"),	1650,	"https://www.norebbo.com/2015/06/embraer-190-blank-illustration-templates/"),
Model("Embraer E195-E2",	"Embraer",	146,	7,	1.19,	1.2,	833,	4511,	111902957,	1456,	12,	Manufacturer("Embraer",	"BR"),	1605,	"https://www.norebbo.com/2019/03/embraer-e195-e2-side-view/"),
Model("Embraer EMB 120",	"Embraer",	30,	4,	1.55,	1.68,	552,	1550,	5055002,	1300,	2,	Manufacturer("Embraer",	"BR"),	980,	""),
Model("Embraer ERJ 135LR",	"Embraer",	37,	5,	2,	1.7,	833,	2854,	11110680,	1456,	4,	Manufacturer("Embraer",	"BR"),	1580,	"https://www.norebbo.com/2018/05/embraer-erj-135-blank-illustration-templates/"),
Model("Embraer ERJ 145XR",	"Embraer",	50,	6,	2.02,	1.6,	854,	3450,	23455754,	1456,	4,	Manufacturer("Embraer",	"BR"),	1720,	"https://www.norebbo.com/2018/04/embraer-erj-145xr-blank-illustration-templates/"),
Model("Airbus H225 Eurocopter",	"Eurocopter",	19,	8,	0.9,	2.7,	262,	857,	5551615,	1560,	8,	Manufacturer("Airbus",	"NL"),	1,	""),
Model("Fokker 100",	"Fokker",	109,	5,	0.68,	2.44,	845,	2220,	23676506,	1300,	4,	Manufacturer("Fokker",	"NL"),	1550,	"https://www.norebbo.com/2018/07/fokker-100-f-28-0100-blank-illustration-templates/"),
Model("Fokker 50",	"Fokker",	56,	4,	0.82,	2.72,	500,	2310,	6163497,	1300,	2,	Manufacturer("Fokker",	"NL"),	1550,	"https://www.norebbo.com/fokker-70-blank-illustration-templates/"),
Model("Fokker 60",	"Fokker",	64,	4,	0.8,	2.72,	515,	2950,	7026838,	1300,	2,	Manufacturer("Fokker",	"NL"),	1550,	"https://www.norebbo.com/fokker-70-blank-illustration-templates/"),
Model("Fokker 70",	"Fokker",	85,	5,	0.74,	2.52,	845,	2840,	17740589,	1300,	6,	Manufacturer("Fokker",	"NL"),	1480,	"https://www.norebbo.com/fokker-70-blank-illustration-templates/"),
Model("Fokker F27 Friendship",	"Fokker",	44,	3,	1.04,	3.21,	460,	1380,	1635854,	1300,	4,	Manufacturer("Fokker",	"NL"),	1550,	"https://www.norebbo.com/fokker-70-blank-illustration-templates/"),
Model("Harbin Y-12",	"Harbin Y",	12,	5,	0.81,	2.43,	310,	1140,	2188417,	1664,	4,	Manufacturer("Harbin",	"CN"),	720,	""),
Model("Airlander 10",	"HAV Airlander",	250,	10,	0.02,	0.01,	178,	3400,	37078401,	780,	20,	Manufacturer("HAV Airlander",	"GB"),	100,	""),
Model("Ilyushin Il-18",	"Ilyushin Il",	120,	0,	1.9,	1.43,	625,	6500,	15597840,	1664,	4,	Manufacturer("Ilyushin",	"RU"),	1350,	"https://en.wikipedia.org/wiki/Ilyushin_Il-96"),
Model("Ilyushin Il-62M",	"Ilyushin Il",	186,	4,	3.9,	1.31,	900,	7000,	19279947,	1820,	12,	Manufacturer("Ilyushin",	"RU"),	2300,	"https://en.wikipedia.org/wiki/Ilyushin_Il-96"),
Model("Ilyushin Il-86",	"Ilyushin Il-96",	350,	4,	3.17,	1.2,	870,	4400,	36234557,	1560,	16,	Manufacturer("Ilyushin",	"RU"),	2800,	"https://en.wikipedia.org/wiki/Ilyushin_Il-96"),
Model("Ilyushin Il-96-300",	"Ilyushin Il-96",	300,	5,	3.3,	1.35,	870,	10100,	44199293,	1560,	18,	Manufacturer("Ilyushin",	"RU"),	3200,	"https://en.wikipedia.org/wiki/Ilyushin_Il-96"),
Model("Ilyushin Il-96-400M",	"Ilyushin Il-96",	436,	6,	4.2,	1.6,	870,	9600,	56465286,	1560,	24,	Manufacturer("UAC",	"RU"),	2600,	"https://en.wikipedia.org/wiki/Ilyushin_Il-96"),
Model("Lockheed L-188 Electra",	"Lockheed Props",	98,	2,	1.28,	2.51,	620,	3100,	19903090,	2600,	4,	Manufacturer("Lockheed",	"US"),	980,	""),
Model("Lockheed L-749 Constellation",	"Lockheed Props",	81,	1,	1.54,	1.7,	490,	8039,	1797869,	1820,	8,	Manufacturer("Lockheed",	"US"),	1524,	""),
Model("Lockheed L-1011-100",	"Lockheed TriStar",	400,	5,	2.06,	1.43,	963,	4200,	105309730,	2080,	16,	Manufacturer("Lockheed",	"US"),	2560,	"https://www.norebbo.com/lockheed-l-1011-1-blank-illustration-templates/"),
Model("Lockheed L-1011-200",	"Lockheed TriStar",	400,	5,	2.85,	1.4,	954,	6100,	104901996,	2080,	16,	Manufacturer("Lockheed",	"US"),	2560,	"https://www.norebbo.com/lockheed-l-1011-1-blank-illustration-templates/"),
Model("Lockheed L-1011-500",	"Lockheed TriStar",	360,	5,	3.7,	1.28,	972,	9344,	121695381,	2080,	24,	Manufacturer("Lockheed",	"US"),	2865,	"https://www.norebbo.com/2015/03/lockheed-l-1011-500-blank-illustration-templates/"),
Model("Mil Mi-26",	"Mil",	78,	0,	0.9,	2.7,	255,	870,	1275000,	780,	4,	Manufacturer("Mil",	"RU"),	1,	""),
Model("Bombardier Global 5000",	"Modern Business Jet",	40,	10,	3.37,	2.53,	902,	9630,	18349857,	1560,	8,	Manufacturer("Bombardier",	"CA"),	1689,	"https://www.norebbo.com/bombardier-global-5000-blank-illustration-templates/"),
Model("Bombardier Global 7500",	"Modern Business Jet",	48,	10,	4.8,	2.4,	1080,	14260,	11589183,	1560,	8,	Manufacturer("Bombardier",	"CA"),	1768,	"https://www.norebbo.com/bombardier-global-7500-side-view/"),
Model("Cessna Citation X",	"Modern Business Jet",	30,	10,	1.57,	2.35,	900,	6050,	26685624,	1820,	8,	Manufacturer("Cessna",	"US"),	1600,	"https://www.norebbo.com/cessna-citation-x-template/"),
Model("Gulfstream G650ER",	"Modern Business Jet",	48,	10,	1.99,	1.99,	966,	13890,	39661117,	1560,	8,	Manufacturer("Gulfstream",	"US"),	1920,	"https://www.norebbo.com/gulfstream-g650er-template/"),
Model("Boeing 307 Stratoliner",	"Post-War Props",	60,	1,	3.33,	1.6,	357,	3850,	775000,	1508,	8,	Manufacturer("Boeing",	"US"),	805,	""),
Model("Bristol Britannia",	"Post-War Props",	139,	1,	2.1,	2.1,	575,	6400,	575000,	1820,	4,	Manufacturer("BAe",	"GB"),	2225,	""),
Model("Douglas DC-3",	"Post-War Props",	32,	0,	0.94,	2.75,	333,	1400,	225000,	3224,	4,	Manufacturer("Douglas Aircraft Company",	"US"),	701,	"https://www.norebbo.com/douglas-dc-3-blank-illustration-templates/"),
Model("NAMC YS-11",	"Post-War Props",	64,	2,	0.81,	2.42,	469,	970,	4810379,	2600,	6,	Manufacturer("NAMC",	"JP"),	1210,	""),
Model("Saab 2000",	"Saab Regional",	58,	5,	0.81,	2.42,	608,	2868,	16065696,	1820,	2,	Manufacturer("Saab",	"SE"),	1252,	"https://www.norebbo.com/saab-340b-blank-illustration-templates/"),
Model("Saab 340B",	"Saab Regional",	34,	5,	1.66,	2.49,	524,	1350,	6441753,	1820,	0,	Manufacturer("Saab",	"SE"),	850,	"https://www.norebbo.com/saab-340b-blank-illustration-templates/"),
Model("Sikorsky S-76",	"Sikorsky",	13,	9,	1.17,	3.22,	287,	761,	3969134,	1560,	2,	Manufacturer("Sikorsky",	"US"),	1,	""),
Model("Sikorsky S-92",	"Sikorsky",	40,	10,	1.2,	3.42,	290,	998,	12441093,	1560,	2,	Manufacturer("Sikorsky",	"US"),	1,	""),
Model("Sud Aviation Caravelle 11",	"Sud Aviation Caravelle",	105,	2,	1.92,	2.36,	810,	2600,	6710311,	1300,	6,	Manufacturer("Sud Aviation",	"FR"),	1310,	"https://en.wikipedia.org/wiki/Sud_Aviation_Caravelle"),
Model("Sud Aviation Caravelle III",	"Sud Aviation Caravelle",	80,	2,	1.98,	2.32,	790,	1900,	3155122,	1300,	6,	Manufacturer("Sud Aviation",	"FR"),	1270,	"https://en.wikipedia.org/wiki/Sud_Aviation_Caravelle"),
Model("Sukhoi KR-860",	"Sukhoi",	920,	5,	4.4,	1.58,	980,	8800,	94340380,	1300,	40,	Manufacturer("Sukhoi",	"RU"),	3500,	""),
Model("Sukhoi Superjet 100",	"Sukhoi Superjet",	108,	6,	1.31,	1.88,	844,	4578,	40507731,	1300,	16,	Manufacturer("UAC",	"RU"),	1400,	"https://www.norebbo.com/2016/02/sukhoi-ssj-100-blank-illustration-templates/"),
Model("Sukhoi Superjet 130NG",	"Sukhoi Superjet",	130,	6,	1.27,	1.7,	844,	4008,	51105752,	1300,	16,	Manufacturer("UAC",	"RU"),	1400,	"https://www.norebbo.com/2016/02/sukhoi-ssj-100-blank-illustration-templates/"),
Model("Tupolev Tu-134",	"Tupolev 124",	72,	2,	1.1,	2.92,	850,	2200,	2302985,	1456,	12,	Manufacturer("Tupolev",	"RU"),	1450,	"https://en.wikipedia.org/wiki/Tupolev_Tu-134"),
Model("Tupolev Tu-144",	"Tupolev 144",	183,	6,	6.38,	5.5,	2125,	6200,	169020820,	1300,	24,	Manufacturer("Tupolev",	"RU"),	3550,	"https://en.wikipedia.org/wiki/Tupolev_Tu-134"),
Model("Tupolev Tu-154",	"Tupolev 154",	180,	3,	1,	2.3,	850,	2300,	8050119,	1664,	12,	Manufacturer("Tupolev",	"RU"),	1140,	"https://www.norebbo.com/tupolev-tu-154-side-view/"),
Model("Tupolev Tu-154M",	"Tupolev 154",	180,	4,	0.83,	2.08,	850,	5280,	17955647,	1664,	12,	Manufacturer("Tupolev",	"RU"),	1250,	"https://www.norebbo.com/tupolev-tu-154-side-view/"),
Model("Tupolev Tu-204-120",	"Tupolev 204",	210,	5,	1.67,	1.45,	820,	3800,	39415438,	1300,	18,	Manufacturer("Tupolev",	"RU"),	1870,	"https://www.norebbo.com/tupolev-tu-204-100-blank-illustration-templates/"),
Model("Tupolev Tu-204–300",	"Tupolev 204",	156,	5,	1.69,	1.5,	820,	6452,	30196172,	1300,	18,	Manufacturer("Tupolev",	"RU"),	1650,	"https://www.norebbo.com/tupolev-tu-204-100-blank-illustration-templates/"),
Model("Vickers VC10",	"Vickers",	150,	4,	3.2,	1.6,	930,	9410,	13086204,	2288,	12,	Manufacturer("Vickers-Armstrongs",	"GB"),	2520,	""),
Model("Xi'an MA60",	"Xi'an Turboprop",	60,	5,	0.86,	2.58,	519,	1180,	11583675,	1300,	4,	Manufacturer("AVIC",	"CN"),	730,	"https://en.wikipedia.org/wiki/Xi%27an_MA60"),
Model("Xi'an MA600",	"Xi'an Turboprop",	62,	4,	0.96,	2.89,	514,	1430,	6591534,	1300,	4,	Manufacturer("AVIC",	"CN"),	750,	"https://en.wikipedia.org/wiki/Xi%27an_MA60"),
Model("Xi'an MA700",	"Xi'an Turboprop",	86,	6,	0.83,	2.48,	637,	1620,	28181555,	1300,	8,	Manufacturer("AVIC",	"CN"),	830,	"https://en.wikipedia.org/wiki/Xi%27an_MA60"),
Model("Yakovlev MC-21-210",	"Yakovlev MC-21",	165,	6,	1.48,	1.46,	870,	2700,	50334380,	1300,	24,	Manufacturer("UAC",	"RU"),	1250,	""),
Model("Yakovlev MC-21-310",	"Yakovlev MC-21",	211,	6,	1.46,	1.44,	870,	2400,	65707408,	1300,	24,	Manufacturer("UAC",	"RU"),	1644,	"https://www.norebbo.com/irkut-mc-21-300/"),
Model("Yakovlev MC-21-310 LR",	"Yakovlev MC-21",	190,	7,	1.79,	1.46,	880,	4200,	79083388,	1300,	24,	Manufacturer("UAC",	"RU"),	1880,	"https://www.norebbo.com/irkut-mc-21-300/"),
Model("Yakovlev MC-21-410",	"Yakovlev MC-21",	230,	6,	1.43,	1.4,	870,	2200,	79068649,	1300,	32,	Manufacturer("UAC",	"RU"),	1700,	""),
Model("Zeppelin",	"Zeppelin",	175,	10,	0.04,	0.03,	165,	8000,	15152029,	1144,	12,	Manufacturer("Zeppelin Luftschifftechnik GmbH",	"DE"),	100,	""),
  )
  val modelByName = models.map { model => (model.name, model) }.toMap
}