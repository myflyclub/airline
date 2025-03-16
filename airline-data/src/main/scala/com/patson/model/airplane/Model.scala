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
    } else if (capacity <= 112 || family == "Embraer" || family == "Sud Aviation Caravelle" || family == "Sukhoi Superjet" || family == "Dornier") {
      REGIONAL
    } else if (capacity <= 215) {
      MEDIUM
    } else if (family == "Boeing 757" || family == "Boeing 737"  || family == "Airbus A320" || family == "DC-8" || family == "Yakovlev MC-21") {
      MEDIUM_XL
    } else if (capacity <= 375) {
      LARGE
    } else if (capacity <= 475) {
      EXTRA_LARGE
    } else if (capacity <= 800) {
      JUMBO
    } else {
      JUMBO_XL
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

  def fromId(id : Int) = {
    val modelWithJustId = Model("Unknown", "Unknown", 0, 0, 0, 0, 0, 0, 0, 0, 0, Manufacturer("Unknown", countryCode = ""), runwayRequirement = 0)
    modelWithJustId.id = id
    modelWithJustId
  }

  object Type extends Enumeration {
    type Type = Value
    val AIRSHIP, HELICOPTER, PROPELLER_SMALL, PROPELLER_MEDIUM, SMALL, REGIONAL, MEDIUM, MEDIUM_XL, LARGE, EXTRA_LARGE, JUMBO, JUMBO_XL, SUPERSONIC = Value

    val label: Type => String = {
      case AIRSHIP => "Airship"
      case HELICOPTER => "Helicopter"
      case PROPELLER_SMALL => "Small Prop"
      case PROPELLER_MEDIUM => "Large Prop"
      case SMALL => "Small Jet"
      case REGIONAL => "Regional Jet"
      case MEDIUM => "Narrow-body"
      case MEDIUM_XL => "Narrow-body XL"
      case LARGE => "Wide-body"
      case EXTRA_LARGE => "Wide-body XL"
      case JUMBO => "Jumbo"
      case JUMBO_XL => "Jumbo XL"
      case SUPERSONIC => "Supersonic"
    }

    val size: Type => Double = {
      case HELICOPTER => 0.03
      case PROPELLER_SMALL => 0.055
      case PROPELLER_MEDIUM => 0.095
      case SMALL => 0.055
      case REGIONAL => 0.095
      case MEDIUM => 0.14
      case MEDIUM_XL => 0.18
      case AIRSHIP => 0.2
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
      REGIONAL -> List(Type.REGIONAL, Type.PROPELLER_MEDIUM),
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
Model("Airbus A220-100",	"Airbus A220",	135,	7,	0.93,	1.33,	828,	3710,	99294457,	1560,	14,	Manufacturer("Airbus",	"NL"),	1483,	"https://www.norebbo.com/2016/02/bombardier-cs100-blank-illustration-templates/"),
Model("Airbus A220-300",	"Airbus A220",	160,	7,	0.84,	1.28,	828,	4745,	121966754,	1560,	14,	Manufacturer("Airbus",	"NL"),	1890,	"https://www.norebbo.com/2016/02/bombardier-cs300-blank-illustration-templates/"),
Model("Airbus A221",	"Airbus A220",	188,	7,	0.83,	1.16,	828,	4800,	158812855,	1560,	20,	Manufacturer("Airbus",	"NL"),	2000,	""),
Model("Airbus A300-600",	"Airbus A300/A310",	304,	5,	1.4,	1.65,	833,	6125,	84936577,	1820,	36,	Manufacturer("Airbus",	"NL"),	2480,	"https://www.norebbo.com/2018/11/airbus-a300b4-600r-blank-illustration-templates-with-general-electric-engines/"),
Model("Airbus A300B4",	"Airbus A300/A310",	320,	5,	1.29,	1.79,	847,	5375,	84174937,	1820,	32,	Manufacturer("Airbus",	"NL"),	2250,	"https://www.norebbo.com/2018/11/airbus-a300b4-600r-blank-illustration-templates-with-general-electric-engines/"),
Model("Airbus A310-200",	"Airbus A300/A310",	240,	6,	1.51,	1.61,	850,	5800,	94708112,	1820,	36,	Manufacturer("Airbus",	"NL"),	2070,	"https://www.norebbo.com/2015/07/airbus-a310-300-blank-illustration-templates/"),
Model("Airbus A310-300",	"Airbus A300/A310",	240,	6,	1.51,	1.54,	850,	8500,	103292131,	1820,	36,	Manufacturer("Airbus",	"NL"),	2380,	"https://www.norebbo.com/2015/07/airbus-a310-300-blank-illustration-templates/"),
Model("Airbus A318",	"Airbus A320",	136,	6,	1.21,	1.52,	829,	5569,	59248976,	1820,	8,	Manufacturer("Airbus",	"NL"),	1880,	"https://www.norebbo.com/airbus-a318-blank-illustration-templates-with-pratt-whitney-and-cfm56-engines/"),
Model("Airbus A319",	"Airbus A320",	160,	6,	1.2,	1.44,	830,	4862,	75462522,	1820,	16,	Manufacturer("Airbus",	"NL"),	1950,	"https://www.norebbo.com/2014/05/airbus-a319-blank-illustration-templates/"),
Model("Airbus A319neo",	"Airbus A320",	160,	7,	0.95,	1.18,	828,	4900,	148110595,	1820,	20,	Manufacturer("Airbus",	"NL"),	1880,	"https://www.norebbo.com/2017/09/airbus-a319-neo-blank-illustration-templates/"),
Model("Airbus A320",	"Airbus A320",	195,	6,	1.17,	1.37,	828,	5408,	94056194,	1820,	20,	Manufacturer("Airbus",	"NL"),	2150,	"https://www.norebbo.com/2013/08/airbus-a320-blank-illustration-templates/"),
Model("Airbus A320neo",	"Airbus A320",	195,	7,	0.92,	1.11,	833,	5646,	183686635,	1820,	24,	Manufacturer("Airbus",	"NL"),	1970,	"https://www.norebbo.com/2017/08/airbus-a320-neo-blank-illustration-templates/"),
Model("Airbus A321",	"Airbus A320",	236,	6,	1.21,	1.34,	830,	5184,	121690256,	1820,	24,	Manufacturer("Airbus",	"NL"),	2210,	"https://www.norebbo.com/2014/03/airbus-a321-blank-illustration-templates/"),
Model("Airbus A321neo",	"Airbus A320",	244,	7,	0.9,	1.09,	828,	5450,	214494262,	1820,	36,	Manufacturer("Airbus",	"NL"),	2177,	"https://www.norebbo.com/2017/09/airbus-a321-neo-blank-illustration-templates/"),
Model("Airbus A321neoLR",	"Airbus A320",	230,	7,	1.47,	1.08,	828,	7200,	205783040,	1820,	36,	Manufacturer("Airbus",	"NL"),	2495,	"https://www.norebbo.com/2018/10/airbus-a321neo-lr-long-range-blank-illustration-templates/"),
Model("Airbus A321neoXLR",	"Airbus A320",	230,	7,	1.57,	1.03,	828,	8400,	204883644,	1820,	36,	Manufacturer("Airbus",	"NL"),	2900,	"https://www.norebbo.com/2018/10/airbus-a321neo-lr-long-range-blank-illustration-templates/"),
Model("Airbus A330-200",	"Airbus A330",	406,	6,	1.76,	1.21,	871,	11300,	255141243,	1820,	24,	Manufacturer("Airbus",	"NL"),	2770,	"https://www.norebbo.com/2016/02/airbus-a330-200-blank-illustration-templates-with-pratt-whitney-engines/"),
Model("Airbus A330-300",	"Airbus A330",	440,	6,	1.84,	1.19,	871,	10250,	274155261,	1820,	24,	Manufacturer("Airbus",	"NL"),	2770,	"https://www.norebbo.com/2016/02/airbus-a330-300-blank-illustration-templates-with-all-three-engine-options/"),
Model("Airbus A330-800neo",	"Airbus A330",	406,	7,	1.78,	1.01,	918,	10800,	377120155,	1820,	36,	Manufacturer("Airbus",	"NL"),	2770,	"https://www.norebbo.com/2018/06/airbus-a330-800-neo-blank-illustration-templates/"),
Model("Airbus A330-900neo",	"Airbus A330",	440,	7,	1.81,	0.99,	918,	10939,	412634868,	1820,	36,	Manufacturer("Airbus",	"NL"),	2770,	"https://www.norebbo.com/2018/06/airbus-a330-900-neo-blank-illustration-templates/"),
Model("Airbus A340-300",	"Airbus A340",	350,	6,	1.94,	1.36,	880,	12824,	176720223,	1820,	36,	Manufacturer("Airbus",	"NL"),	3000,	"https://www.norebbo.com/2016/04/airbus-340-300-and-a340-300x-blank-illustration-templates/"),
Model("Airbus A340-500",	"Airbus A340",	375,	6,	2.15,	1.34,	871,	15345,	185776477,	1820,	36,	Manufacturer("Airbus",	"NL"),	3250,	"https://www.norebbo.com/2016/08/airbus-a340-500-blank-illustration-templates/"),
Model("Airbus A340-600",	"Airbus A340",	440,	6,	2.19,	1.29,	905,	13965,	237131713,	1820,	36,	Manufacturer("Airbus",	"NL"),	3200,	"https://www.norebbo.com/2016/11/airbus-a340-600-blank-illustration-templates/"),
Model("Airbus A350-1000",	"Airbus A350",	480,	8,	1.42,	0.91,	910,	14535,	521678631,	1820,	40,	Manufacturer("Airbus",	"NL"),	2880,	"https://www.norebbo.com/2015/11/airbus-a350-1000-blank-illustration-templates/"),
Model("Airbus A350-1000 Sunrise",	"Airbus A350",	420,	9,	2.31,	0.92,	910,	16800,	527476660,	1820,	40,	Manufacturer("Airbus",	"NL"),	2900,	"https://www.norebbo.com/2015/11/airbus-a350-1000-blank-illustration-templates/"),
Model("Airbus A350-900",	"Airbus A350",	440,	8,	1.58,	0.97,	903,	14147,	467327392,	1820,	36,	Manufacturer("Airbus",	"NL"),	2600,	"https://www.norebbo.com/2013/07/airbus-a350-900-blank-illustration-templates/"),
Model("Airbus A350-900ULR",	"Airbus A350",	374,	9,	2.34,	0.98,	910,	16349,	472654943,	1820,	36,	Manufacturer("Airbus",	"NL"),	2700,	"https://www.norebbo.com/2013/07/airbus-a350-900-blank-illustration-templates/"),
Model("Airbus A380-800",	"Airbus A380",	853,	7,	3.22,	1.05,	925,	14200,	659620414,	1820,	54,	Manufacturer("Airbus",	"NL"),	3200,	"https://www.norebbo.com/2013/06/airbus-a380-800-blank-illustration-templates/"),
Model("Airbus H225 Eurocopter",	"Eurocopter",	15,	8,	1.1,	2.7,	262,	857,	2034801,	1560,	8,	Manufacturer("Airbus",	"NL"),	1,	""),
Model("Airbus ZeroE Turbofan",	"Airbus ZE",	175,	9,	0.36,	0.59,	795,	1400,	117980968,	1040,	20,	Manufacturer("Airbus",	"NL"),	2100,	""),
Model("Airbus ZeroE Turboprop",	"Airbus ZE",	85,	9,	0.39,	0.56,	674,	1100,	88351304,	1040,	24,	Manufacturer("Airbus",	"NL"),	2000,	""),
Model("Airlander 10",	"HAV Airlander",	250,	10,	0.1,	0.07,	178,	3400,	39081456,	780,	20,	Manufacturer("HAV Airlander",	"GB"),	100,	""),
Model("Antonov An-10A",	"Antonov An",	130,	1,	1.62,	3.33,	680,	1300,	3640509,	1456,	8,	Manufacturer("Antonov",	"UA"),	975,	""),
Model("Antonov An-148",	"Antonov An",	85,	5,	1.4,	1.94,	835,	3500,	16481962,	1040,	6,	Manufacturer("Antonov",	"UA"),	1100,	""),
Model("Antonov An-24",	"Antonov An",	50,	1,	1.58,	3.43,	450,	680,	921079,	1456,	4,	Manufacturer("Antonov",	"UA"),	970,	""),
Model("Antonov An-72",	"Antonov An",	52,	3,	1.86,	2.9,	700,	2600,	3877603,	1456,	4,	Manufacturer("Antonov",	"UA"),	700,	""),
Model("ATR 42-400",	"ATR-Regional",	48,	4,	1.03,	3.03,	484,	1420,	4567444,	1040,	0,	Manufacturer("ATR",	"FR"),	1050,	"https://www.norebbo.com/atr-42-blank-illustration-templates/"),
Model("ATR 42-600S",	"ATR-Regional",	48,	5,	1.06,	2.96,	535,	1260,	7422087,	1040,	0,	Manufacturer("ATR",	"FR"),	750,	"https://www.norebbo.com/atr-42-blank-illustration-templates/"),
Model("ATR 72-200",	"ATR-Regional",	68,	4,	1.06,	2.53,	517,	1464,	9596000,	1040,	0,	Manufacturer("ATR",	"FR"),	1211,	"https://www.norebbo.com/2017/04/atr-72-blank-illustration-templates/"),
Model("ATR 72-600",	"ATR-Regional",	72,	5,	1.02,	2.52,	510,	1655,	11313842,	1040,	4,	Manufacturer("ATR",	"FR"),	1279,	"https://www.norebbo.com/2017/04/atr-72-blank-illustration-templates/"),
Model("Aurora D8",	"Aurora D",	188,	9,	1.1,	0.84,	937,	5200,	303046174,	1820,	36,	Manufacturer("Aurora Flight Sciences",	"US"),	2300,	""),
Model("BAe 146-100",	"BAe 146",	82,	4,	1.26,	2.49,	747,	2090,	12524542,	1560,	2,	Manufacturer("BAe",	"GB"),	1195,	"https://www.norebbo.com/2018/11/british-aerospace-bae-146-200-avro-rj85-blank-illustration-templates/"),
Model("BAe 146-200",	"BAe 146",	100,	4,	1.16,	2.33,	747,	1800,	14075531,	1560,	2,	Manufacturer("BAe",	"GB"),	1390,	"https://www.norebbo.com/2018/11/british-aerospace-bae-146-200-avro-rj85-blank-illustration-templates/"),
Model("BAe 146-300",	"BAe 146",	112,	4,	1.11,	2.26,	747,	1650,	15670201,	1560,	2,	Manufacturer("BAe",	"GB"),	1535,	"https://www.norebbo.com/2018/11/british-aerospace-bae-146-200-avro-rj85-blank-illustration-templates/"),
Model("BAe Jetstream 31",	"BAe Jetstream",	19,	5,	1.55,	3.14,	430,	1260,	2883736,	1820,	0,	Manufacturer("BAe",	"GB"),	1100,	"https://www.norebbo.com/british-aerospace-jetstream-41-blank-illustration-templates/"),
Model("BAe Jetstream 41",	"BAe Jetstream",	29,	7,	1.4,	3.07,	482,	1210,	9684694,	1820,	0,	Manufacturer("BAe",	"GB"),	1090,	"https://www.norebbo.com/british-aerospace-jetstream-41-blank-illustration-templates/"),
Model("Beechcraft 1900D",	"Beechcraft",	17,	6,	1.38,	2.54,	518,	707,	3215838,	1820,	0,	Manufacturer("Beechcraft",	"US"),	1140,	"https://www.norebbo.com/beechcraft-1900d-blank-illustration-templates/"),
Model("Beechcraft B200 Super King Air",	"Beechcraft",	11,	5,	1.35,	3.13,	561,	2800,	2061312,	1820,	0,	Manufacturer("Beechcraft",	"US"),	1020,	"https://www.norebbo.com/beechcraft-b200-king-air-side-view/"),
Model("Boeing 2707",	"Boeing 2707",	247,	8,	3.88,	4.51,	3300,	5600,	665785345,	1664,	42,	Manufacturer("Boeing",	"US"),	3590,	""),
Model("Boeing 307 Stratoliner",	"Post-War Props",	60,	1,	1.92,	2.5,	357,	3850,	796110,	1508,	8,	Manufacturer("Boeing",	"US"),	805,	""),
Model("Boeing 377 Stratocruiser",	"Post-War Props",	117,	1,	2.52,	2.19,	480,	6760,	1023129,	1820,	12,	Manufacturer("Boeing",	"US"),	1920,	""),
Model("Boeing 707-120",	"Boeing 707",	194,	3,	1.71,	2,	952,	5100,	9947374,	2496,	16,	Manufacturer("Boeing",	"US"),	2700,	""),
Model("Boeing 707-320",	"Boeing 707",	189,	3,	2.8,	1.91,	952,	8200,	12966040,	2496,	20,	Manufacturer("Boeing",	"US"),	3150,	"https://www.norebbo.com/boeing-707-320c-blank-illustration-templates/"),
Model("Boeing 717-200",	"DC-9",	123,	6,	1.11,	1.74,	822,	2397,	49956783,	1820,	12,	Manufacturer("Boeing",	"US"),	1675,	"https://www.norebbo.com/2017/06/boeing-717-200-blank-illustration-templates/"),
Model("Boeing 720B",	"Boeing 707",	165,	3,	1.51,	2.15,	896,	5700,	6518167,	1976,	16,	Manufacturer("Boeing",	"US"),	2000,	""),
Model("Boeing 727-100",	"Boeing 727",	131,	3,	1.54,	2.12,	960,	3983,	8105814,	2184,	16,	Manufacturer("Boeing",	"US"),	1750,	"https://www.norebbo.com/boeing-727-100-blank-illustration-templates/"),
Model("Boeing 727-200",	"Boeing 727",	189,	4,	1.62,	1.99,	811,	3695,	27684804,	2184,	24,	Manufacturer("Boeing",	"US"),	1800,	"https://www.norebbo.com/2018/03/boeing-727-200-blank-illustration-templates/"),
Model("Boeing 737 MAX 10",	"Boeing 737",	230,	7,	0.88,	1.2,	830,	5400,	181777135,	1664,	36,	Manufacturer("Boeing",	"US"),	2700,	"https://www.norebbo.com/2019/01/737-10-max-side-view/"),
Model("Boeing 737 MAX 7",	"Boeing 737",	172,	7,	0.97,	1.27,	830,	6800,	131802423,	1664,	24,	Manufacturer("Boeing",	"US"),	2100,	"https://www.norebbo.com/2016/07/boeing-737-max-7-blank-illustration-templates/"),
Model("Boeing 737 MAX 8",	"Boeing 737",	189,	7,	0.94,	1.25,	830,	6306,	145803119,	1664,	24,	Manufacturer("Boeing",	"US"),	2035,	"https://www.norebbo.com/2016/07/boeing-737-max-8-blank-illustration-templates/"),
Model("Boeing 737 MAX 8-200",	"Boeing 737",	210,	6,	0.9,	1.23,	839,	4090,	140407470,	1664,	24,	Manufacturer("Boeing",	"US"),	1820,	"https://www.norebbo.com/2016/07/boeing-737-max-8-blank-illustration-templates/"),
Model("Boeing 737 MAX 9",	"Boeing 737",	220,	7,	0.92,	1.22,	839,	5830,	168877430,	1664,	36,	Manufacturer("Boeing",	"US"),	2600,	"https://www.norebbo.com/2018/05/boeing-737-9-max-blank-illustration-templates/"),
Model("Boeing 737-100",	"Boeing 737",	124,	3,	1.62,	1.95,	780,	2850,	8009114,	1976,	8,	Manufacturer("Boeing",	"US"),	1800,	"https://www.norebbo.com/2018/10/boeing-737-100-blank-illustration-templates/"),
Model("Boeing 737-200",	"Boeing 737",	136,	4,	1.53,	1.91,	780,	3693,	17974505,	1976,	12,	Manufacturer("Boeing",	"US"),	1859,	"https://www.norebbo.com/2018/09/boeing-737-200-blank-illustration-templates/"),
Model("Boeing 737-300",	"Boeing 737",	144,	4,	1.32,	1.81,	800,	4128,	22424504,	1820,	16,	Manufacturer("Boeing",	"US"),	2010,	"https://www.norebbo.com/2018/09/boeing-737-300-blank-illustration-templates/"),
Model("Boeing 737-400",	"Boeing 737",	168,	4,	1.24,	1.67,	800,	4105,	31096606,	1820,	16,	Manufacturer("Boeing",	"US"),	2540,	"https://www.norebbo.com/2018/09/boeing-737-400-blank-illustration-templates/"),
Model("Boeing 737-500",	"Boeing 737",	132,	5,	1.36,	1.84,	800,	4249,	29588476,	1820,	12,	Manufacturer("Boeing",	"US"),	2280,	"https://www.norebbo.com/2018/09/boeing-737-500-blank-illustration-templates-with-and-without-blended-winglets/"),
Model("Boeing 737-600",	"Boeing 737",	130,	5,	1.32,	1.76,	834,	4681,	45489984,	1820,	16,	Manufacturer("Boeing",	"US"),	1804,	"https://www.norebbo.com/2018/09/boeing-737-600-blank-illustration-templates/"),
Model("Boeing 737-700",	"Boeing 737",	148,	5,	1.19,	1.62,	834,	5838,	52323057,	1820,	16,	Manufacturer("Boeing",	"US"),	1655,	"https://www.norebbo.com/2014/04/boeing-737-700-blank-illustration-templates/"),
Model("Boeing 737-700ER",	"Boeing 737",	140,	6,	1.42,	1.57,	834,	7200,	59752010,	1820,	16,	Manufacturer("Boeing",	"US"),	2060,	"https://www.norebbo.com/2014/04/boeing-737-700-blank-illustration-templates/"),
Model("Boeing 737-800",	"Boeing 737",	188,	6,	1.09,	1.56,	842,	5279,	82461134,	1820,	20,	Manufacturer("Boeing",	"US"),	2230,	"https://www.norebbo.com/2012/11/boeing-737-800-blank-illustration-templates/"),
Model("Boeing 737-900ER",	"Boeing 737",	215,	6,	1.04,	1.51,	844,	5638,	97051337,	1820,	24,	Manufacturer("Boeing",	"US"),	2880,	"https://www.norebbo.com/2016/07/boeing-737-900er-with-split-scimitar-winglets-blank-illustration-templates/"),
Model("Boeing 747-100",	"Boeing 747",	520,	4,	3.3,	1.74,	907,	8530,	65308281,	1820,	36,	Manufacturer("Boeing",	"US"),	3250,	"https://www.norebbo.com/2019/07/boeing-747-100-side-view/"),
Model("Boeing 747-200",	"Boeing 747",	520,	4,	2.95,	1.58,	907,	10854,	115958116,	1820,	40,	Manufacturer("Boeing",	"US"),	3300,	"https://www.norebbo.com/2019/08/boeing-747-200-side-view/"),
Model("Boeing 747-300",	"Boeing 747",	520,	5,	2.82,	1.42,	939,	11127,	209416260,	1820,	48,	Manufacturer("Boeing",	"US"),	2955,	"https://www.norebbo.com/boeing-747-300-side-view/"),
Model("Boeing 747-400",	"Boeing 747",	585,	6,	2.63,	1.17,	933,	11821,	389605112,	1820,	48,	Manufacturer("Boeing",	"US"),	2980,	"https://www.norebbo.com/2013/09/boeing-747-400-blank-illustration-templates/"),
Model("Boeing 747-400D",	"Boeing 747",	605,	6,	1.1,	1.07,	933,	2800,	392163729,	1820,	48,	Manufacturer("Boeing",	"US"),	2480,	"https://www.norebbo.com/2013/09/boeing-747-400-blank-illustration-templates/"),
Model("Boeing 747-400ER",	"Boeing 747",	585,	6,	2.74,	1.18,	933,	13804,	395581828,	1820,	48,	Manufacturer("Boeing",	"US"),	3300,	"https://www.norebbo.com/2013/09/boeing-747-400-blank-illustration-templates/"),
Model("Boeing 747-8i",	"Boeing 747",	645,	7,	2.61,	1.04,	933,	13804,	559165611,	1820,	48,	Manufacturer("Boeing",	"US"),	3180,	"https://www.norebbo.com/2015/12/boeing-747-8i-blank-illustration-templates/"),
Model("Boeing 747SP",	"Boeing 747",	388,	6,	3.19,	1.66,	994,	12051,	155254144,	1820,	36,	Manufacturer("Boeing",	"US"),	2820,	"https://www.norebbo.com/2019/08/boeing-747sp-side-view/"),
Model("Boeing 757-200",	"Boeing 757",	239,	5,	1.55,	1.42,	854,	6016,	109648741,	1976,	36,	Manufacturer("Boeing",	"US"),	2240,	"https://www.norebbo.com/2015/01/boeing-757-200-blank-illustration-templates/"),
Model("Boeing 757-200ER",	"Boeing 757",	239,	5,	2.2,	1.36,	850,	7378,	113577191,	1976,	36,	Manufacturer("Boeing",	"US"),	2550,	"https://www.norebbo.com/2015/01/boeing-757-200-blank-illustration-templates/"),
Model("Boeing 757-300",	"Boeing 757",	295,	5,	1.4,	1.37,	850,	5742,	131799150,	1976,	36,	Manufacturer("Boeing",	"US"),	2400,	"https://www.norebbo.com/2017/03/boeing-757-300-blank-illustration-templates/"),
Model("Boeing 767-200",	"Boeing 767",	245,	5,	1.42,	1.4,	860,	6240,	112146281,	1820,	36,	Manufacturer("Boeing",	"US"),	1900,	"https://www.norebbo.com/2014/07/boeing-767-200-blank-illustration-templates/"),
Model("Boeing 767-200ER",	"Boeing 767",	245,	5,	2.28,	1.41,	896,	11272,	112431710,	1820,	36,	Manufacturer("Boeing",	"US"),	2480,	"https://www.norebbo.com/2014/07/boeing-767-200-blank-illustration-templates/"),
Model("Boeing 767-300",	"Boeing 767",	290,	5,	1.07,	1.31,	860,	6800,	148898240,	1820,	36,	Manufacturer("Boeing",	"US"),	2800,	"https://www.norebbo.com/2014/07/boeing-767-200-blank-illustration-templates/"),
Model("Boeing 767-300ER",	"Boeing 767",	290,	6,	2.48,	1.32,	896,	11093,	169246302,	1820,	36,	Manufacturer("Boeing",	"US"),	2650,	"https://www.norebbo.com/2014/07/boeing-767-200-blank-illustration-templates/"),
Model("Boeing 767-400ER",	"Boeing 767",	375,	5,	2,	1.21,	896,	8600,	170349809,	1820,	36,	Manufacturer("Boeing",	"US"),	3220,	""),
Model("Boeing 777-200",	"Boeing 777",	440,	6,	1.24,	1.14,	896,	7004,	314330052,	1664,	36,	Manufacturer("Boeing",	"US"),	2350,	"https://www.norebbo.com/2012/12/boeing-777-200-blank-illustration-templates/"),
Model("Boeing 777-200ER",	"Boeing 777",	440,	7,	2.51,	1.05,	896,	12680,	349929025,	1664,	36,	Manufacturer("Boeing",	"US"),	3140,	"https://www.norebbo.com/2012/12/boeing-777-200-blank-illustration-templates/"),
Model("Boeing 777-200LR",	"Boeing 777",	440,	7,	2.59,	1.03,	896,	14204,	380242935,	1664,	36,	Manufacturer("Boeing",	"US"),	2940,	"https://www.norebbo.com/2012/12/boeing-777-200-blank-illustration-templates/"),
Model("Boeing 777-300",	"Boeing 777",	520,	6,	1.16,	1.05,	945,	6220,	403159567,	1664,	44,	Manufacturer("Boeing",	"US"),	2950,	"https://www.norebbo.com/2014/03/boeing-777-300-blank-illustration-templates/"),
Model("Boeing 777-300ER",	"Boeing 777",	520,	7,	2.55,	0.99,	945,	13712,	457661459,	1664,	44,	Manufacturer("Boeing",	"US"),	3120,	"https://www.norebbo.com/2014/03/boeing-777-300-blank-illustration-templates/"),
Model("Boeing 777-8",	"Boeing 777",	480,	8,	2.11,	0.9,	896,	15590,	558973311,	1664,	36,	Manufacturer("Boeing",	"US"),	3050,	"https://www.norebbo.com/2019/12/boeing-777-8-side-view/"),
Model("Boeing 777-9",	"Boeing 777",	550,	8,	2.2,	0.88,	896,	13940,	635031743,	1664,	44,	Manufacturer("Boeing",	"US"),	3050,	"https://www.norebbo.com/2019/12/boeing-777-9-side-view/"),
Model("Boeing 787-10 Dreamliner",	"Boeing 787",	440,	7,	1.75,	0.92,	903,	11720,	431437667,	1664,	36,	Manufacturer("Boeing",	"US"),	2800,	"https://www.norebbo.com/2017/06/boeing-787-10-blank-illustration-templates/"),
Model("Boeing 787-8 Dreamliner",	"Boeing 787",	359,	7,	1.82,	0.95,	903,	13593,	331979210,	1664,	36,	Manufacturer("Boeing",	"US"),	2600,	"https://www.norebbo.com/2013/02/boeing-787-8-blank-illustration-templates/"),
Model("Boeing 787-9 Dreamliner",	"Boeing 787",	406,	7,	1.77,	0.93,	903,	13010,	390480304,	1664,	36,	Manufacturer("Boeing",	"US"),	2800,	"https://www.norebbo.com/2014/04/boeing-787-9-blank-illustration-templates/"),
Model("Boeing 787-9 ER",	"Boeing 787",	376,	7,	1.91,	0.89,	903,	15720,	394242856,	1664,	36,	Manufacturer("Boeing",	"US"),	2900,	"https://www.norebbo.com/2014/04/boeing-787-9-blank-illustration-templates/"),
Model("Boeing 797-6",	"Boeing 797",	230,	8,	0.89,	0.93,	890,	7840,	305436518,	1820,	40,	Manufacturer("Boeing",	"US"),	2600,	"https://www.norebbo.com/boeing-797-side-view/"),
Model("Boeing 797-7",	"Boeing 797",	275,	8,	1.11,	0.91,	890,	7200,	360245502,	1820,	40,	Manufacturer("Boeing",	"US"),	2600,	"https://www.norebbo.com/boeing-797-side-view/"),
Model("Boeing Vertol 107-II ",	"Boeing Vertol",	28,	4,	1.52,	3.15,	265,	1020,	1677694,	1300,	4,	Manufacturer("Boeing",	"US"),	1,	""),
Model("Boeing Vertol 234",	"Boeing Vertol",	44,	4,	1.4,	3.11,	269,	1010,	2166065,	1300,	4,	Manufacturer("Boeing",	"US"),	1,	""),
Model("Bombardier CRJ100",	"Bombardier CRJ",	50,	5,	1.3,	2.6,	830,	1820,	26797975,	1820,	0,	Manufacturer("Bombardier",	"CA"),	1920,	"https://www.norebbo.com/2015/04/bombardier-canadair-regional-jet-200-blank-illustration-templates/"),
Model("Bombardier CRJ1000",	"Bombardier CRJ",	104,	5,	1.1,	1.91,	870,	1600,	53396836,	1820,	8,	Manufacturer("Bombardier",	"CA"),	2120,	"https://www.norebbo.com/2019/06/bombardier-crj-1000-side-view/"),
Model("Bombardier CRJ200",	"Bombardier CRJ",	50,	5,	1.27,	2.51,	830,	2813,	28225213,	1820,	0,	Manufacturer("Bombardier",	"CA"),	1920,	"https://www.norebbo.com/2015/04/bombardier-canadair-regional-jet-200-blank-illustration-templates/"),
Model("Bombardier CRJ700",	"Bombardier CRJ",	78,	5,	1.23,	2.29,	828,	2480,	43342237,	1820,	4,	Manufacturer("Bombardier",	"CA"),	1605,	"https://www.norebbo.com/2015/05/bombardier-canadair-regional-jet-700-blank-illustration-templates/"),
Model("Bombardier CRJ900",	"Bombardier CRJ",	90,	5,	1.24,	1.98,	870,	2931,	42491446,	1820,	8,	Manufacturer("Bombardier",	"CA"),	1939,	"https://www.norebbo.com/2016/07/bombardier-canadair-regional-jet-900-blank-illustration-templates/"),
Model("Bombardier Global 5000",	"Modern Business Jet",	30,	10,	2.1,	2.11,	934,	9630,	16631717,	1560,	8,	Manufacturer("Bombardier",	"CA"),	1689,	"https://www.norebbo.com/bombardier-global-5000-blank-illustration-templates/"),
Model("Bombardier Global 7500",	"Modern Business Jet",	36,	10,	2.4,	1.92,	1080,	14260,	31123123,	1560,	8,	Manufacturer("Bombardier",	"CA"),	1768,	"https://www.norebbo.com/bombardier-global-7500-side-view/"),
Model("Boom Overture",	"Boom Overture",	128,	10,	2.7,	2.92,	1800,	7870,	425899589,	1664,	100,	Manufacturer("Boom Technology",	"US"),	3048,	""),
Model("Bristol Britannia",	"Post-War Props",	139,	1,	2.84,	2.1,	575,	6400,	2976439,	1820,	4,	Manufacturer("BAe",	"GB"),	2225,	""),
Model("CASA C-212 Aviocar",	"CASA",	26,	3,	1.23,	2.97,	354,	2680,	1266620,	1560,	0,	Manufacturer("CASA",	"ES"),	600,	""),
Model("CASA CN-235",	"CASA",	40,	4,	1.39,	2.75,	460,	3658,	3663290,	1560,	0,	Manufacturer("CASA",	"ES"),	1204,	""),
Model("Cessna 208 Caravan",	"Cessna",	14,	5,	0.88,	2.73,	355,	1680,	1818737,	1768,	0,	Manufacturer("Cessna",	"US"),	762,	"https://www.norebbo.com/2017/06/cessna-208-grand-caravan-blank-illustration-templates/"),
Model("Cessna 408 Skycourier",	"Cessna",	19,	6,	0.85,	2.52,	390,	715,	3993280,	1768,	0,	Manufacturer("Cessna",	"US"),	890,	""),
Model("Cessna Citation X",	"Modern Business Jet",	20,	10,	1.87,	2.3,	900,	6050,	11471480,	1820,	8,	Manufacturer("Cessna",	"US"),	1600,	"https://www.norebbo.com/cessna-citation-x-template/"),
Model("Comac C909 ER",	"Comac C909",	90,	5,	1.55,	2.3,	828,	3200,	15385447,	1560,	8,	Manufacturer("COMAC",	"CN"),	1780,	""),
Model("Comac C909 STD",	"Comac C909",	90,	5,	1.08,	2.36,	828,	2040,	14497001,	1560,	8,	Manufacturer("COMAC",	"CN"),	1700,	""),
Model("Comac C919-100 ER",	"Comac C919",	168,	6,	1.21,	1.4,	838,	4300,	79471790,	1560,	16,	Manufacturer("COMAC",	"CN"),	2000,	"https://www.norebbo.com/comac-c919-side-view/"),
Model("Comac C919-100 STD",	"Comac C919",	168,	6,	0.93,	1.46,	838,	2800,	74658434,	1560,	16,	Manufacturer("COMAC",	"CN"),	2000,	"https://www.norebbo.com/comac-c919-side-view/"),
Model("Comac C919-300",	"Comac C919",	198,	6,	0.92,	1.3,	838,	2670,	105092295,	1560,	20,	Manufacturer("COMAC",	"CN"),	2120,	""),
Model("Comac C929-600",	"Comac C929",	405,	7,	1.5,	1.05,	908,	11890,	325927155,	1560,	24,	Manufacturer("COMAC",	"CN"),	2780,	""),
Model("Comac C939",	"Comac C939",	400,	7,	1.87,	1,	908,	10720,	331826595,	1560,	30,	Manufacturer("COMAC",	"CN"),	2780,	""),
Model("Concorde",	"Concorde",	130,	7,	4.67,	3.7,	2158,	6332,	298293201,	1560,	60,	Manufacturer("BAe",	"GB"),	3390,	"https://www.norebbo.com/aerospatiale-bac-concorde-blank-illustration-templates/"),
Model("Convair 880",	"Convair",	110,	4,	2.17,	1.92,	880,	5636,	17457791,	1560,	4,	Manufacturer("Convair",	"US"),	2670,	""),
Model("Convair 990 Coronado",	"Convair",	121,	4,	2.43,	1.9,	990,	6116,	22312142,	1560,	8,	Manufacturer("Convair",	"US"),	2875,	""),
Model("Convair 990A Coronado",	"Convair",	149,	5,	2.61,	2.24,	1030,	6116,	24700604,	1560,	12,	Manufacturer("Convair",	"US"),	3002,	""),
Model("Dassault Mercure",	"Dassault",	162,	2,	1.84,	2.14,	926,	1684,	4733076,	1560,	12,	Manufacturer("Dassault Aviation",	"FR"),	2100,	""),
Model("De Havilland DHC-7-100",	"DHC Dash",	50,	3,	1.31,	2.82,	428,	1300,	3308059,	1560,	4,	Manufacturer("De Havilland Canada",	"CA"),	620,	""),
Model("De Havilland DHC-8-100",	"DHC Dash",	39,	4,	1.2,	2.72,	448,	1889,	4702450,	1560,	2,	Manufacturer("De Havilland Canada",	"CA"),	950,	"https://www.norebbo.com/2018/01/de-havilland-dhc-8-200-dash-8-blank-illustration-templates/"),
Model("De Havilland DHC-8-200",	"DHC Dash",	39,	4,	1.2,	2.6,	448,	2084,	4750361,	1560,	2,	Manufacturer("De Havilland Canada",	"CA"),	1000,	"https://www.norebbo.com/2018/01/de-havilland-dhc-8-200-dash-8-blank-illustration-templates/"),
Model("De Havilland DHC-8-300",	"DHC Dash",	50,	4,	1.26,	2.4,	450,	1711,	6002829,	1560,	4,	Manufacturer("De Havilland Canada",	"CA"),	1085,	"https://www.norebbo.com/2018/05/de-havilland-dhc-8-300-blank-illustration-templates/"),
Model("De Havilland DHC-8-400",	"DHC Dash",	68,	4,	1.24,	2.22,	667,	1980,	12336151,	1560,	4,	Manufacturer("De Havilland Canada",	"CA"),	1085,	"https://www.norebbo.com/bombardier-dhc-8-402-q400-blank-illustration-templates/"),
Model("De Havilland Q400",	"DHC Dash",	86,	5,	0.97,	2.1,	562,	2040,	15186616,	1560,	8,	Manufacturer("De Havilland Canada",	"CA"),	1885,	"https://www.norebbo.com/bombardier-dhc-8-402-q400-blank-illustration-templates/"),
Model("Dornier 1128",	"Dornier",	128,	7,	1.3,	1.79,	923,	2640,	95985067,	1976,	12,	Manufacturer("Dornier",	"DE"),	1550,	""),
Model("Dornier 328-110",	"Dornier",	33,	7,	1.23,	2.38,	620,	820,	13142667,	1560,	6,	Manufacturer("Dornier",	"DE"),	1088,	"https://www.norebbo.com/2019/01/dornier-328-110-blank-illustration-templates/"),
Model("Dornier 328eco",	"Dornier",	44,	9,	0,	0.43,	600,	690,	73040291,	1820,	8,	Manufacturer("Dornier",	"DE"),	1367,	""),
Model("Dornier 328JET",	"Dornier",	44,	7,	1.65,	2.12,	740,	1150,	21141100,	1820,	6,	Manufacturer("Dornier",	"DE"),	1367,	"https://www.norebbo.com/2019/01/fairchild-dornier-328jet-illustrations/"),
Model("Dornier 728",	"Dornier",	80,	7,	1.59,	1.95,	980,	2340,	72245120,	1820,	6,	Manufacturer("Dornier",	"DE"),	1463,	""),
Model("Dornier 928",	"Dornier",	110,	7,	1.44,	1.82,	951,	2180,	85873202,	1820,	8,	Manufacturer("Dornier",	"DE"),	1513,	""),
Model("Douglas DC-3",	"Post-War Props",	32,	0,	1.56,	3.01,	333,	1400,	201603,	3224,	4,	Manufacturer("Douglas Aircraft Company",	"US"),	701,	"https://www.norebbo.com/douglas-dc-3-blank-illustration-templates/"),
Model("Douglas DC-8-10",	"DC-8",	177,	2,	1.82,	2.3,	895,	3760,	2692551,	2496,	16,	Manufacturer("Douglas Aircraft Company",	"US"),	2840,	""),
Model("Douglas DC-8-53",	"DC-8",	189,	2,	1.73,	2.21,	895,	8700,	5665115,	2496,	16,	Manufacturer("Douglas Aircraft Company",	"US"),	3200,	"https://www.norebbo.com/douglas-dc-8-53-blank-illustration-templates/"),
Model("Embraer E170",	"Embraer",	72,	6,	1.4,	1.83,	797,	2400,	28754772,	1560,	2,	Manufacturer("Embraer",	"BR"),	1438,	"https://www.norebbo.com/embraer-erj-175-templates-with-the-new-style-winglets/"),
Model("Embraer E175 LR",	"Embraer",	78,	6,	1.52,	1.79,	797,	2900,	33840897,	1560,	4,	Manufacturer("Embraer",	"BR"),	1420,	"https://www.norebbo.com/2015/10/embraer-erj-175-templates-with-the-new-style-winglets/"),
Model("Embraer E175-E2",	"Embraer",	88,	6,	1.1,	1.66,	833,	3650,	43569273,	1560,	6,	Manufacturer("Embraer",	"BR"),	1800,	"https://www.norebbo.com/2019/03/e175-e2-side-view/"),
Model("Embraer E190",	"Embraer",	100,	6,	1.36,	1.92,	829,	3685,	35823398,	1560,	8,	Manufacturer("Embraer",	"BR"),	1650,	"https://www.norebbo.com/2015/06/embraer-190-blank-illustration-templates/"),
Model("Embraer E190-E2",	"Embraer",	114,	6,	1.1,	1.44,	833,	4620,	70409132,	1560,	8,	Manufacturer("Embraer",	"BR"),	1465,	"https://www.norebbo.com/2019/03/e190-e2-blank-side-view/"),
Model("Embraer E195-E2",	"Embraer",	146,	6,	1.08,	1.4,	833,	4511,	87170478,	1560,	12,	Manufacturer("Embraer",	"BR"),	1605,	"https://www.norebbo.com/2019/03/embraer-e195-e2-side-view/"),
Model("Embraer EMB 120",	"Embraer",	30,	4,	1.49,	2.65,	552,	1550,	4339982,	1300,	2,	Manufacturer("Embraer",	"BR"),	980,	""),
Model("Embraer ERJ 135LR",	"Embraer",	37,	5,	1.43,	2.35,	833,	2854,	14287418,	1560,	4,	Manufacturer("Embraer",	"BR"),	1580,	"https://www.norebbo.com/2018/05/embraer-erj-135-blank-illustration-templates/"),
Model("Embraer ERJ 145XR",	"Embraer",	50,	6,	1.68,	2.3,	854,	3450,	20052005,	1560,	4,	Manufacturer("Embraer",	"BR"),	1720,	"https://www.norebbo.com/2018/04/embraer-erj-145xr-blank-illustration-templates/"),
Model("Fokker 100",	"Fokker",	109,	5,	1.2,	1.94,	845,	2220,	20437152,	1300,	4,	Manufacturer("Fokker",	"NL"),	1550,	"https://www.norebbo.com/2018/07/fokker-100-f-28-0100-blank-illustration-templates/"),
Model("Fokker 50",	"Fokker",	56,	4,	1.55,	2.63,	500,	2310,	4495245,	1300,	2,	Manufacturer("Fokker",	"NL"),	1550,	"https://www.norebbo.com/fokker-70-blank-illustration-templates/"),
Model("Fokker 60",	"Fokker",	64,	4,	1.59,	2.58,	515,	2950,	5724542,	1300,	2,	Manufacturer("Fokker",	"NL"),	1550,	"https://www.norebbo.com/fokker-70-blank-illustration-templates/"),
Model("Fokker 70",	"Fokker",	85,	4,	1.14,	2.1,	845,	2840,	14411828,	1300,	6,	Manufacturer("Fokker",	"NL"),	1480,	"https://www.norebbo.com/fokker-70-blank-illustration-templates/"),
Model("Fokker F27 Friendship",	"Fokker",	44,	3,	1.27,	2.97,	460,	1380,	2350641,	1300,	4,	Manufacturer("Fokker",	"NL"),	1550,	""),
Model("Gulfstream G650ER",	"Modern Business Jet",	40,	10,	1.82,	1.51,	966,	13890,	61140153,	2080,	8,	Manufacturer("Gulfstream",	"US"),	1920,	"https://www.norebbo.com/gulfstream-g650er-template/"),
Model("Harbin Y-12",	"Harbin Y",	12,	5,	0.88,	2.43,	310,	1140,	2539571,	1664,	4,	Manufacturer("Harbin",	"CN"),	720,	""),
Model("Heart ES-30",	"Electric Props",	30,	9,	0,	0.23,	370,	400,	19267690,	1040,	4,	Manufacturer("Heart Aerospace",	"SE"),	920,	""),
Model("Ilyushin Il-18",	"Ilyushin Il",	120,	0,	2.8,	2.28,	625,	6500,	2432110,	1300,	4,	Manufacturer("Ilyushin",	"RU"),	1350,	""),
Model("Ilyushin Il-62M",	"Ilyushin Il",	186,	4,	2.7,	1.94,	900,	7000,	26352942,	1820,	12,	Manufacturer("Ilyushin",	"RU"),	2300,	""),
Model("Ilyushin Il-86",	"Ilyushin Il-96",	350,	4,	2.36,	2.19,	870,	4400,	24578457,	1456,	16,	Manufacturer("Ilyushin",	"RU"),	2800,	""),
Model("Ilyushin Il-96-300",	"Ilyushin Il-96",	300,	5,	2.86,	2.02,	870,	10100,	31248026,	1456,	18,	Manufacturer("Ilyushin",	"RU"),	3200,	""),
Model("Ilyushin Il-96-400M",	"Ilyushin Il-96",	436,	6,	2.67,	1.72,	870,	9600,	112486051,	1456,	24,	Manufacturer("UAC",	"RU"),	2600,	""),
Model("Lockheed L-1011-100",	"Lockheed TriStar",	400,	4,	1.58,	1.77,	963,	4200,	96605802,	2080,	16,	Manufacturer("Lockheed",	"US"),	2560,	"https://www.norebbo.com/lockheed-l-1011-1-blank-illustration-templates/"),
Model("Lockheed L-1011-200",	"Lockheed TriStar",	400,	5,	1.95,	1.62,	954,	6100,	131442573,	2080,	16,	Manufacturer("Lockheed",	"US"),	2560,	"https://www.norebbo.com/lockheed-l-1011-1-blank-illustration-templates/"),
Model("Lockheed L-1011-500",	"Lockheed TriStar",	360,	5,	2.31,	1.69,	972,	9344,	125858931,	1560,	24,	Manufacturer("Lockheed",	"US"),	2865,	"https://www.norebbo.com/2015/03/lockheed-l-1011-500-blank-illustration-templates/"),
Model("Lockheed L-1649 Starliner",	"Lockheed Props",	99,	2,	3.15,	2.04,	490,	7950,	1679287,	1560,	8,	Manufacturer("Lockheed",	"US"),	1610,	""),
Model("Lockheed L-188 Electra",	"Lockheed Props",	98,	1,	1.7,	2.82,	620,	3100,	2660332,	1820,	4,	Manufacturer("Lockheed",	"US"),	980,	""),
Model("Lockheed L-749 Constellation",	"Lockheed Props",	81,	1,	3.1,	2.21,	490,	8039,	408518,	1560,	8,	Manufacturer("Lockheed",	"US"),	1524,	""),
Model("McDonnell Douglas DC-10-10",	"DC-10",	410,	4,	1.81,	1.82,	876,	6500,	49803316,	1820,	16,	Manufacturer("McDonnell Douglas",	"US"),	2700,	"https://www.norebbo.com/mcdonnell-douglas-dc-10-30-blank-templates/"),
Model("McDonnell Douglas DC-10-30",	"DC-10",	410,	4,	2.31,	1.8,	886,	9400,	57036893,	1820,	20,	Manufacturer("McDonnell Douglas",	"US"),	3220,	"https://www.norebbo.com/mcdonnell-douglas-dc-10-30-blank-templates/"),
Model("McDonnell Douglas DC-10-40",	"DC-10",	410,	5,	2.55,	1.82,	886,	12392,	81037859,	1820,	24,	Manufacturer("McDonnell Douglas",	"US"),	2980,	"https://www.norebbo.com/mcdonnell-douglas-dc-10-30-blank-templates/"),
Model("McDonnell Douglas DC-8-61",	"DC-8",	259,	3,	2.02,	2.18,	895,	6200,	11432383,	2496,	24,	Manufacturer("McDonnell Douglas",	"US"),	2680,	"https://www.norebbo.com/douglas-dc-8-61-blank-illustration-templates/"),
Model("McDonnell Douglas DC-8-62",	"DC-8",	189,	3,	2.96,	1.91,	895,	9800,	18282890,	2496,	16,	Manufacturer("McDonnell Douglas",	"US"),	2680,	""),
Model("McDonnell Douglas DC-8-63",	"DC-8",	259,	3,	2.4,	2.1,	895,	8100,	15529382,	2496,	24,	Manufacturer("McDonnell Douglas",	"US"),	2680,	"https://www.norebbo.com/douglas-dc-8-73-and-dc-8-73cf-blank-illustration-templates/"),
Model("McDonnell Douglas DC-9-10",	"DC-9",	92,	2,	1.71,	2.31,	965,	2367,	3695550,	1040,	4,	Manufacturer("McDonnell Douglas",	"US"),	1816,	""),
Model("McDonnell Douglas DC-9-30",	"DC-9",	115,	2,	1.54,	2.24,	804,	2778,	3953450,	1040,	4,	Manufacturer("McDonnell Douglas",	"US"),	1900,	"https://www.norebbo.com/mcdonnell-douglas-dc-9-30-templates/"),
Model("McDonnell Douglas DC-9-50",	"DC-9",	139,	3,	1.36,	2.07,	804,	3030,	6808190,	1040,	4,	Manufacturer("McDonnell Douglas",	"US"),	2100,	"https://www.norebbo.com/dc-9-50-side-view/"),
Model("McDonnell Douglas MD-11",	"DC-10",	410,	6,	2.52,	1.66,	886,	11963,	113096637,	1820,	24,	Manufacturer("McDonnell Douglas",	"US"),	3050,	"https://www.norebbo.com/mcdonnell-douglas-md-11-blank-illustration-templates-with-ge-engines/"),
Model("McDonnell Douglas MD-81",	"DC-9",	161,	4,	1.11,	1.95,	830,	2600,	24356018,	1560,	12,	Manufacturer("McDonnell Douglas",	"US"),	2070,	"https://www.norebbo.com/2015/02/mcdonnell-douglas-md-80-blank-illustration-templates/"),
Model("McDonnell Douglas MD-85",	"DC-9",	135,	4,	1.15,	1.98,	830,	2500,	19325005,	1560,	12,	Manufacturer("McDonnell Douglas",	"US"),	1840,	"https://www.norebbo.com/2015/02/mcdonnell-douglas-md-80-blank-illustration-templates/"),
Model("McDonnell Douglas MD-90",	"DC-9",	171,	5,	1.05,	1.79,	830,	1400,	37305430,	1560,	20,	Manufacturer("McDonnell Douglas",	"US"),	2134,	"https://www.norebbo.com/2018/02/mcdonnell-douglas-md-90-blank-illustration-templates/"),
Model("McDonnell Douglas MD-90ER",	"DC-9",	163,	5,	1.12,	1.75,	830,	3300,	41130744,	1560,	20,	Manufacturer("McDonnell Douglas",	"US"),	2134,	"https://www.norebbo.com/2018/02/mcdonnell-douglas-md-90-blank-illustration-templates/"),
Model("Mil Mi-26",	"Mil",	78,	0,	1.12,	2.7,	255,	870,	820394,	780,	4,	Manufacturer("Mil",	"RU"),	1,	""),
Model("NAMC YS-11",	"Post-War Props",	64,	2,	0.94,	2.42,	469,	970,	10013685,	2600,	6,	Manufacturer("NAMC",	"JP"),	1210,	""),
Model("Saab 2000",	"Saab Regional",	58,	5,	1.55,	2.23,	608,	2868,	15313624,	1820,	2,	Manufacturer("Saab",	"SE"),	1252,	"https://www.norebbo.com/saab-340b-blank-illustration-templates/"),
Model("Saab 340B",	"Saab Regional",	34,	4,	1.65,	2.49,	524,	1350,	3516314,	1820,	0,	Manufacturer("Saab",	"SE"),	850,	""),
Model("Sikorsky S-76",	"Sikorsky",	25,	9,	1.27,	3.22,	287,	761,	3000745,	1560,	2,	Manufacturer("Sikorsky",	"US"),	1,	""),
Model("Sikorsky S-92",	"Sikorsky",	40,	10,	1.23,	3.26,	290,	998,	6518816,	1560,	2,	Manufacturer("Sikorsky",	"US"),	1,	""),
Model("Sud Aviation Caravelle 12",	"Sud Aviation Caravelle",	140,	2,	1.75,	2.22,	810,	2200,	2634238,	1300,	6,	Manufacturer("Sud Aviation",	"FR"),	1310,	""),
Model("Sud Aviation Caravelle III",	"Sud Aviation Caravelle",	80,	2,	2.34,	2.52,	845,	2100,	1924977,	1300,	6,	Manufacturer("Sud Aviation",	"FR"),	1270,	""),
Model("Sukhoi KR-860",	"Sukhoi",	920,	5,	3.49,	1.65,	980,	8800,	222595111,	1300,	58,	Manufacturer("Sukhoi",	"RU"),	3500,	""),
Model("Sukhoi Superjet 100",	"Sukhoi Superjet",	108,	6,	1.11,	1.76,	844,	4578,	37251112,	1300,	16,	Manufacturer("UAC",	"RU"),	1400,	"https://www.norebbo.com/2016/02/sukhoi-ssj-100-blank-illustration-templates/"),
Model("Sukhoi Superjet 130NG",	"Sukhoi Superjet",	130,	6,	1.03,	1.91,	844,	4008,	36269331,	1300,	16,	Manufacturer("UAC",	"RU"),	1400,	"https://www.norebbo.com/2016/02/sukhoi-ssj-100-blank-illustration-templates/"),
Model("Tupolev Tu-134",	"Tupolev 124",	72,	2,	2.19,	3.02,	850,	2200,	741238,	1456,	12,	Manufacturer("Tupolev",	"RU"),	1450,	""),
Model("Tupolev Tu-144",	"Tupolev 144",	183,	8,	3.65,	5.5,	2125,	6200,	235256842,	1300,	24,	Manufacturer("Tupolev",	"RU"),	3550,	""),
Model("Tupolev Tu-154",	"Tupolev 154",	180,	3,	1.51,	2.95,	850,	2500,	2593432,	1664,	12,	Manufacturer("Tupolev",	"RU"),	1140,	"https://www.norebbo.com/tupolev-tu-154-side-view/"),
Model("Tupolev Tu-154M",	"Tupolev 154",	180,	4,	1.42,	2.48,	850,	5280,	9390505,	1664,	12,	Manufacturer("Tupolev",	"RU"),	1250,	"https://www.norebbo.com/tupolev-tu-154-side-view/"),
Model("Tupolev Tu-204-120",	"Tupolev 204",	210,	4,	1.33,	2.08,	820,	3800,	17260545,	1300,	18,	Manufacturer("Tupolev",	"RU"),	1870,	"https://www.norebbo.com/tupolev-tu-204-100-blank-illustration-templates/"),
Model("Tupolev Tu-204–300",	"Tupolev 204",	156,	6,	1.46,	1.85,	820,	6452,	27879725,	1300,	18,	Manufacturer("Tupolev",	"RU"),	1650,	"https://www.norebbo.com/tupolev-tu-204-100-blank-illustration-templates/"),
Model("Vickers VC10",	"Vickers",	150,	3,	2.31,	2.2,	930,	9410,	3287908,	1976,	12,	Manufacturer("Vickers-Armstrongs",	"GB"),	2520,	""),
Model("Xi'an MA60",	"Xi'an Turboprop",	60,	4,	1.62,	2.53,	519,	1180,	6091859,	1300,	4,	Manufacturer("AVIC",	"CN"),	730,	""),
Model("Xi'an MA600",	"Xi'an Turboprop",	62,	3,	1.55,	2.41,	514,	1430,	5004576,	1300,	4,	Manufacturer("AVIC",	"CN"),	750,	""),
Model("Xi'an MA700",	"Xi'an Turboprop",	86,	6,	1.51,	2.28,	637,	1620,	19316210,	1300,	8,	Manufacturer("AVIC",	"CN"),	840,	""),
Model("Yakovlev MC-21-210",	"Yakovlev MC-21",	165,	5,	1.01,	1.39,	870,	2700,	70328646,	1300,	24,	Manufacturer("UAC",	"RU"),	1250,	""),
Model("Yakovlev MC-21-310",	"Yakovlev MC-21",	211,	5,	1.05,	1.44,	870,	2400,	82032720,	1300,	30,	Manufacturer("UAC",	"RU"),	1644,	"https://www.norebbo.com/irkut-mc-21-300/"),
Model("Yakovlev MC-21-310 LR",	"Yakovlev MC-21",	190,	5,	1.14,	1.41,	880,	4200,	91535288,	1300,	30,	Manufacturer("UAC",	"RU"),	1880,	"https://www.norebbo.com/irkut-mc-21-300/"),
Model("Yakovlev MC-21-410",	"Yakovlev MC-21",	230,	5,	1.19,	1.4,	870,	2200,	95385010,	1300,	36,	Manufacturer("UAC",	"RU"),	1700,	""),
Model("Zeppelin",	"Zeppelin",	148,	10,	1.02,	0.05,	165,	8000,	6614805,	1144,	12,	Manufacturer("Zeppelin Luftschifftechnik GmbH",	"DE"),	100,	""),
  )
  val modelByName = models.map { model => (model.name, model) }.toMap
}