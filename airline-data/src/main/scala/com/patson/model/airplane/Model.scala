package com.patson.model.airplane

import com.patson.model.IdObject
import com.patson.model.Airline
import com.patson.model.airplane.Model.Category
import com.patson.util.AirplaneModelCache

case class Model(name : String, family : String = "", capacity : Int, maxSeats : Int, quality : Double, fuelBurn : Int, speed : Int, range : Int, price : Int, lifespan : Int, constructionTime : Int, manufacturer: Manufacturer, runwayRequirement : Int, imageUrl : String = "", var id : Int = 0) extends IdObject {
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
    } else {
      capacity match {
        case x if (x <= SIZE_SMALL_THRESHOLD) => SMALL
        case x if (x <= 146) => REGIONAL
        case x if (x <= 260) => MEDIUM
        case x if (x <= 460) => LARGE
        case _ => JUMBO
      }
    }
  }
  val category = Category.fromType(airplaneType)

  private[this]val BASE_TURNAROUND_TIME = 30
  val turnaroundTime : Int = {
    BASE_TURNAROUND_TIME +
      (airplaneType match {
        case HELICOPTER => 0 //buffing for short distances
        case SMALL =>  maxSeats / 3 //70
        case REGIONAL => maxSeats / 3
        case MEDIUM =>  maxSeats / 2.5
        case LARGE => maxSeats / 2.5 //180
        case JUMBO => maxSeats / 2.5 //220
        case _ => maxSeats / 3
      }).toInt
  }

  val airplaneTypeLabel : String = label(airplaneType)

  //weekly fixed cost
  val baseMaintenanceCost : Int = {
    (capacity * 120).toInt //for now
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
    val AIRSHIP, HELICOPTER, PROPELLER_SMALL, PROPELLER_MEDIUM, SMALL, REGIONAL, MEDIUM, LARGE, JUMBO, SUPERSONIC = Value

    val label = (airplaneType : Type) => { airplaneType match {
        case AIRSHIP => "Airship"
        case HELICOPTER => "Helicopter"
        case PROPELLER_SMALL => "Prop, Small"
        case PROPELLER_MEDIUM => "Prop, Medium"
        case SMALL => "Small Jet"
        case REGIONAL => "Regional Jet"
        case MEDIUM => "Medium Jet"
        case LARGE => "Large / Wide body Jet"
        case JUMBO => "Jumbo Jet"
        case SUPERSONIC => "Supersonic"
      }
    }
  }

  object Category extends Enumeration {
    type Category = Value
    val SPECIAL, SMALL, REGIONAL, MEDIUM, LARGE, EXTRAORDINARY = Value
    val grouping = Map(
      SMALL -> List(Type.SMALL, Type.PROPELLER_SMALL),
      REGIONAL -> List(Type.REGIONAL, Type.PROPELLER_MEDIUM),
      MEDIUM -> List(Type.MEDIUM),
      LARGE -> List(Type.LARGE),
      EXTRAORDINARY -> List(Type.JUMBO, Type.SUPERSONIC),
      SPECIAL -> List(Type.AIRSHIP, Type.HELICOPTER),
    )

    val fromType = (airplaneType : Type.Value) => {
      grouping.find(_._2.contains(airplaneType)).get._1
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
Model("Airbus A220-100",	"Airbus A220",	135,	135,	7,	395,	828,	6390,	96654000,	1820,	21,	Manufacturer("Airbus",	countryCode="CA"),	1463,	imageUrl ="https://www.norebbo.com/2016/02/bombardier-cs100-blank-illustration-templates/"),
Model("Airbus A220-300",	"Airbus A220",	160,	160,	7,	524,	828,	6700,	114988000,	1820,	21,	Manufacturer("Airbus",	countryCode="CA"),	1890,	imageUrl ="https://www.norebbo.com/2016/02/bombardier-cs300-blank-illustration-templates/"),
Model("Airbus A220-500",	"Airbus A220",	190,	190,	7,	640,	828,	6100,	154593000,	1820,	30,	Manufacturer("Airbus",	countryCode="CA"),	2000,	imageUrl =""),
Model("Airbus A300-600",	"Airbus A300/A310",	266,	266,	5,	1185,	833,	7500,	111428000,	1820,	54,	Manufacturer("Airbus",	countryCode="NL"),	2400,	imageUrl ="https://www.norebbo.com/2018/11/airbus-a300b4-600r-blank-illustration-templates-with-general-electric-engines/"),
Model("Airbus A300B4",	"Airbus A300/A310",	345,	345,	5,	1470,	847,	5375,	152368000,	1820,	48,	Manufacturer("Airbus",	countryCode="NL"),	1950,	imageUrl ="https://www.norebbo.com/2018/11/airbus-a300b4-600r-blank-illustration-templates-with-general-electric-engines/"),
Model("Airbus A310-200",	"Airbus A300/A310",	240,	240,	6,	989,	850,	6400,	135280000,	1820,	54,	Manufacturer("Airbus",	countryCode="NL"),	1860,	imageUrl ="https://www.norebbo.com/2015/07/airbus-a310-300-blank-illustration-templates/"),
Model("Airbus A310-300",	"Airbus A300/A310",	240,	240,	6,	989,	850,	8050,	143290000,	1820,	54,	Manufacturer("Airbus",	countryCode="NL"),	2000,	imageUrl ="https://www.norebbo.com/2015/07/airbus-a310-300-blank-illustration-templates/"),
Model("Airbus A318",	"Airbus A320",	136,	136,	6,	480,	829,	7800,	62122000,	1820,	12,	Manufacturer("Airbus",	countryCode="NL"),	1780,	imageUrl ="https://www.norebbo.com/airbus-a318-blank-illustration-templates-with-pratt-whitney-and-cfm56-engines/"),
Model("Airbus A319",	"Airbus A320",	160,	160,	6,	599,	830,	6940,	82770000,	1820,	24,	Manufacturer("Airbus",	countryCode="NL"),	1850,	imageUrl ="https://www.norebbo.com/2014/05/airbus-a319-blank-illustration-templates/"),
Model("Airbus A319neo",	"Airbus A320",	160,	160,	7,	499,	828,	6850,	127448000,	1820,	30,	Manufacturer("Airbus",	countryCode="NL"),	2164,	imageUrl ="https://www.norebbo.com/2017/09/airbus-a319-neo-blank-illustration-templates/"),
Model("Airbus A320",	"Airbus A320",	195,	195,	6,	754,	828,	6150,	93361000,	1820,	36,	Manufacturer("Airbus",	countryCode="NL"),	2050,	imageUrl ="https://www.norebbo.com/2013/08/airbus-a320-blank-illustration-templates/"),
Model("Airbus A320neo",	"Airbus A320",	195,	195,	7,	690,	833,	6500,	136704000,	1820,	36,	Manufacturer("Airbus",	countryCode="NL"),	2100,	imageUrl ="https://www.norebbo.com/2017/08/airbus-a320-neo-blank-illustration-templates/"),
Model("Airbus A321",	"Airbus A320",	236,	236,	6,	990,	830,	5930,	114454000,	1820,	54,	Manufacturer("Airbus",	countryCode="NL"),	2210,	imageUrl ="https://www.norebbo.com/2014/03/airbus-a321-blank-illustration-templates/"),
Model("Airbus A321neo",	"Airbus A320",	244,	244,	7,	854,	828,	6850,	177555000,	1820,	54,	Manufacturer("Airbus",	countryCode="NL"),	1988,	imageUrl ="https://www.norebbo.com/2017/09/airbus-a321-neo-blank-illustration-templates/"),
Model("Airbus A321neoLR",	"Airbus A320",	244,	182,	7,	832,	828,	7400,	182005000,	1820,	54,	Manufacturer("Airbus",	countryCode="NL"),	2300,	imageUrl ="https://www.norebbo.com/2018/10/airbus-a321neo-lr-long-range-blank-illustration-templates/"),
Model("Airbus A321neoXLR",	"Airbus A320",	244,	154,	8,	800,	828,	8700,	201674000,	1820,	54,	Manufacturer("Airbus",	countryCode="NL"),	2400,	imageUrl ="https://www.norebbo.com/2018/10/airbus-a321neo-lr-long-range-blank-illustration-templates/"),
Model("Airbus A330-200",	"Airbus A330",	406,	406,	6,	1726,	871,	11300,	240033000,	1820,	54,	Manufacturer("Airbus",	countryCode="NL"),	2770,	imageUrl ="https://www.norebbo.com/2016/02/airbus-a330-200-blank-illustration-templates-with-pratt-whitney-engines/"),
Model("Airbus A330-300",	"Airbus A330",	440,	440,	6,	1998,	871,	13400,	253205000,	1820,	54,	Manufacturer("Airbus",	countryCode="NL"),	2770,	imageUrl ="https://www.norebbo.com/2016/02/airbus-a330-300-blank-illustration-templates-with-all-three-engine-options/"),
Model("Airbus A330-800neo",	"Airbus A330",	406,	406,	7,	1707,	918,	13900,	332949000,	1820,	54,	Manufacturer("Airbus",	countryCode="NL"),	2770,	imageUrl ="https://www.norebbo.com/2018/06/airbus-a330-800-neo-blank-illustration-templates/"),
Model("Airbus A330-900neo",	"Airbus A330",	460,	460,	7,	1940,	918,	12130,	377271000,	1820,	54,	Manufacturer("Airbus",	countryCode="NL"),	2770,	imageUrl ="https://www.norebbo.com/2018/06/airbus-a330-900-neo-blank-illustration-templates/"),
Model("Airbus A340-200",	"Airbus A340",	315,	315,	5,	1480,	880,	14800,	154771000,	1820,	54,	Manufacturer("Airbus",	countryCode="NL"),	2900,	imageUrl ="https://www.norebbo.com/2019/01/airbus-a340-200-side-view/"),
Model("Airbus A340-300",	"Airbus A340",	350,	350,	5,	1665,	880,	13350,	161624000,	1820,	54,	Manufacturer("Airbus",	countryCode="NL"),	3000,	imageUrl ="https://www.norebbo.com/2016/04/airbus-340-300-and-a340-300x-blank-illustration-templates/"),
Model("Airbus A340-500",	"Airbus A340",	375,	375,	5,	1792,	871,	17000,	203365000,	1820,	54,	Manufacturer("Airbus",	countryCode="NL"),	3350,	imageUrl ="https://www.norebbo.com/2016/08/airbus-a340-500-blank-illustration-templates/"),
Model("Airbus A340-600",	"Airbus A340",	440,	440,	5,	2288,	905,	13900,	249556000,	1820,	54,	Manufacturer("Airbus",	countryCode="NL"),	3400,	imageUrl ="https://www.norebbo.com/2016/11/airbus-a340-600-blank-illustration-templates/"),
Model("Airbus A350-1000",	"Airbus A350",	475,	475,	7,	2205,	910,	15050,	397563000,	1820,	60,	Manufacturer("Airbus",	countryCode="NL"),	2600,	imageUrl ="https://www.norebbo.com/2015/11/airbus-a350-1000-blank-illustration-templates/"),
Model("Airbus A350-1000 Sunrise",	"Airbus A350",	475,	212,	9,	1952,	910,	18000,	494039000,	1820,	60,	Manufacturer("Airbus",	countryCode="NL"),	2700,	imageUrl ="https://www.norebbo.com/2015/11/airbus-a350-1000-blank-illustration-templates/"),
Model("Airbus A350-900",	"Airbus A350",	440,	440,	7,	1956,	903,	15000,	356267000,	1820,	54,	Manufacturer("Airbus",	countryCode="NL"),	2600,	imageUrl ="https://www.norebbo.com/2013/07/airbus-a350-900-blank-illustration-templates/"),
Model("Airbus A350-900ULR",	"Airbus A350",	440,	182,	7,	1760,	910,	17960,	394537000,	1820,	54,	Manufacturer("Airbus",	countryCode="NL"),	2600,	imageUrl ="https://www.norebbo.com/2013/07/airbus-a350-900-blank-illustration-templates/"),
Model("Airbus A380-800",	"Airbus A380",	880,	853,	7,	4679,	925,	15700,	552245000,	1820,	81,	Manufacturer("Airbus",	countryCode="NL"),	3000,	imageUrl ="https://www.norebbo.com/2013/06/airbus-a380-800-blank-illustration-templates/"),
Model("Airbus H225 Eurocopter",	"Eurocopter",	28,	19,	6,	30,	262,	857,	5607000,	1560,	12,	Manufacturer("Airbus",	countryCode="NL"),	1,	imageUrl =""),
Model("Airbus ZeroE Turbofan",	"Airbus ZE",	175,	175,	8,	118,	795,	2000,	242436000,	1040,	30,	Manufacturer("Airbus",	countryCode="NL"),	2100,	imageUrl =""),
Model("Airbus ZeroE Turboprop",	"Airbus ZE",	85,	85,	8,	43,	674,	1000,	97811000,	1040,	36,	Manufacturer("Airbus",	countryCode="NL"),	2000,	imageUrl =""),
Model("Airlander 10 Cruise",	"HAV Airlander",	324,	84,	10,	7,	124,	3700,	82325000,	780,	30,	Manufacturer("HAV Airlander",	countryCode="GB"),	100,	imageUrl =""),
Model("Airlander 10 Ferry",	"HAV Airlander",	324,	110,	9,	14,	116,	1600,	58918000,	780,	30,	Manufacturer("HAV Airlander",	countryCode="GB"),	100,	imageUrl =""),
Model("Airlander 50",	"HAV Airlander",	540,	200,	9,	28,	114,	2200,	103329000,	780,	30,	Manufacturer("HAV Airlander",	countryCode="GB"),	100,	imageUrl =""),
Model("Antonov An-10A",	"Antonov An",	132,	132,	3,	389,	570,	4075,	21805000,	1040,	12,	Manufacturer("Antonov",	countryCode="UA"),	975,	imageUrl =""),
Model("Antonov An-148",	"Antonov An",	75,	75,	4,	233,	835,	5300,	16465000,	1040,	9,	Manufacturer("Antonov",	countryCode="UA"),	1100,	imageUrl =""),
Model("Antonov An-158",	"Antonov An",	102,	102,	4,	322,	835,	3500,	23318000,	1040,	9,	Manufacturer("Antonov",	countryCode="UA"),	1900,	imageUrl =""),
Model("Antonov An-72",	"Antonov An",	52,	52,	3,	140,	700,	4325,	8455000,	1040,	0,	Manufacturer("Antonov",	countryCode="UA"),	700,	imageUrl =""),
Model("ATR 42-400",	"ATR-Regional",	48,	48,	4,	58,	484,	1326,	12460000,	1040,	0,	Manufacturer("ATR",	countryCode="FR"),	1050,	imageUrl =""),
Model("ATR 42-600S",	"ATR-Regional",	48,	48,	5,	58,	535,	1260,	14774000,	1040,	0,	Manufacturer("ATR",	countryCode="FR"),	750,	imageUrl =""),
Model("ATR 72-200",	"ATR-Regional",	66,	66,	5,	82,	517,	1464,	18601000,	1040,	0,	Manufacturer("ATR",	countryCode="FR"),	1211,	imageUrl =""),
Model("ATR 72-600",	"ATR-Regional",	72,	72,	5,	88,	510,	1403,	22784000,	1040,	6,	Manufacturer("ATR",	countryCode="FR"),	1279,	imageUrl ="https://www.norebbo.com/2017/04/atr-72-blank-illustration-templates/"),
Model("Aurora D8",	"Aurora D",	229,	180,	9,	530,	937,	5600,	333928000,	1820,	42,	Manufacturer("Aurora Flight Sciences",	countryCode="US"),	2300,	imageUrl =""),
Model("BAe 146-100",	"BAe 146",	82,	82,	5,	249,	789,	3650,	24742000,	1560,	12,	Manufacturer("BAe",	countryCode="GB"),	1195,	imageUrl ="https://www.norebbo.com/2018/11/british-aerospace-bae-146-200-avro-rj85-blank-illustration-templates/"),
Model("BAe 146-200",	"BAe 146",	93,	93,	5,	300,	789,	3650,	28035000,	1560,	12,	Manufacturer("BAe",	countryCode="GB"),	1390,	imageUrl ="https://www.norebbo.com/2018/11/british-aerospace-bae-146-200-avro-rj85-blank-illustration-templates/"),
Model("BAe 146-300",	"BAe 146",	113,	113,	5,	349,	789,	3650,	38626000,	1560,	12,	Manufacturer("BAe",	countryCode="GB"),	1535,	imageUrl ="https://www.norebbo.com/2018/11/british-aerospace-bae-146-200-avro-rj85-blank-illustration-templates/"),
Model("BAe Jetstream 41",	"BAe Jetstream",	29,	29,	6,	40,	482,	1433,	8811000,	1820,	0,	Manufacturer("BAe",	countryCode="GB"),	1524,	imageUrl =""),
Model("BAe Jetstream 61",	"BAe Jetstream",	56,	56,	6,	110,	496,	1825,	20292000,	1820,	6,	Manufacturer("BAe",	countryCode="GB"),	1200,	imageUrl =""),
Model("Boeing 2707",	"Boeing 2707",	554,	277,	10,	11100,	3300,	5900,	798597000,	1664,	63,	Manufacturer("Boeing",	countryCode="US"),	3800,	imageUrl =""),
Model("Boeing 307 Stratoliner",	"Post-War Props",	60,	60,	1,	114,	357,	3850,	2403000,	1508,	0,	Manufacturer("Boeing",	countryCode="US"),	620,	imageUrl =""),
Model("Boeing 377 Stratocruiser",	"Post-War Props",	117,	117,	2,	310,	480,	6760,	18690000,	1456,	6,	Manufacturer("Boeing",	countryCode="US"),	1000,	imageUrl =""),
Model("Boeing 707",	"Boeing 707",	194,	194,	3,	1025,	1003,	6700,	39338000,	2496,	0,	Manufacturer("Boeing",	countryCode="US"),	2700,	imageUrl ="https://www.norebbo.com/boeing-707-320c-blank-illustration-templates/"),
Model("Boeing 717-200",	"DC-9",	134,	134,	5,	415,	811,	2645,	43877000,	1820,	6,	Manufacturer("Boeing",	countryCode="US"),	2050,	imageUrl ="https://www.norebbo.com/2017/06/boeing-717-200-blank-illustration-templates/"),
Model("Boeing 727-100",	"Boeing 727",	131,	131,	4,	610,	960,	4170,	38715000,	2184,	18,	Manufacturer("Boeing",	countryCode="US"),	1750,	imageUrl =""),
Model("Boeing 727-200",	"Boeing 727",	189,	189,	5,	831,	811,	4020,	62923000,	2184,	36,	Manufacturer("Boeing",	countryCode="US"),	1800,	imageUrl ="https://www.norebbo.com/2018/03/boeing-727-200-blank-illustration-templates/"),
Model("Boeing 737 MAX 10",	"Boeing 737",	230,	204,	7,	750,	830,	6500,	174440000,	1820,	54,	Manufacturer("Boeing",	countryCode="US"),	2700,	imageUrl ="https://www.norebbo.com/2019/01/737-10-max-side-view/"),
Model("Boeing 737 MAX 7",	"Boeing 737",	172,	153,	7,	550,	830,	6500,	120506000,	1820,	36,	Manufacturer("Boeing",	countryCode="US"),	2050,	imageUrl ="https://www.norebbo.com/2016/07/boeing-737-max-7-blank-illustration-templates/"),
Model("Boeing 737 MAX 8",	"Boeing 737",	210,	178,	7,	658,	830,	6500,	146672000,	1820,	36,	Manufacturer("Boeing",	countryCode="US"),	2500,	imageUrl ="https://www.norebbo.com/2016/07/boeing-737-max-8-blank-illustration-templates/"),
Model("Boeing 737 MAX 8-200",	"Boeing 737",	200,	200,	6,	685,	839,	5200,	133055000,	1820,	36,	Manufacturer("Boeing",	countryCode="US"),	2500,	imageUrl ="https://www.norebbo.com/2016/07/boeing-737-max-8-blank-illustration-templates/"),
Model("Boeing 737 MAX 9",	"Boeing 737",	220,	193,	7,	712,	839,	6570,	152190000,	1820,	54,	Manufacturer("Boeing",	countryCode="US"),	2600,	imageUrl ="https://www.norebbo.com/2018/05/boeing-737-9-max-blank-illustration-templates/"),
Model("Boeing 737-100",	"Boeing 737",	124,	124,	3,	525,	780,	3440,	23941000,	1976,	12,	Manufacturer("Boeing",	countryCode="US"),	1800,	imageUrl ="https://www.norebbo.com/2018/10/boeing-737-100-blank-illustration-templates/"),
Model("Boeing 737-200",	"Boeing 737",	136,	136,	4,	560,	780,	4200,	36935000,	1976,	18,	Manufacturer("Boeing",	countryCode="US"),	1859,	imageUrl ="https://www.norebbo.com/2018/09/boeing-737-200-blank-illustration-templates/"),
Model("Boeing 737-300",	"Boeing 737",	144,	144,	4,	590,	800,	4400,	46280000,	1976,	24,	Manufacturer("Boeing",	countryCode="US"),	1940,	imageUrl ="https://www.norebbo.com/2018/09/boeing-737-300-blank-illustration-templates/"),
Model("Boeing 737-400",	"Boeing 737",	168,	168,	4,	680,	800,	5000,	63991000,	1976,	24,	Manufacturer("Boeing",	countryCode="US"),	2540,	imageUrl ="https://www.norebbo.com/2018/09/boeing-737-400-blank-illustration-templates/"),
Model("Boeing 737-500",	"Boeing 737",	132,	132,	5,	510,	800,	5200,	55981000,	1976,	18,	Manufacturer("Boeing",	countryCode="US"),	1830,	imageUrl ="https://www.norebbo.com/2018/09/boeing-737-500-blank-illustration-templates-with-and-without-blended-winglets/"),
Model("Boeing 737-600",	"Boeing 737",	130,	130,	5,	489,	834,	7200,	54023000,	1976,	24,	Manufacturer("Boeing",	countryCode="US"),	1878,	imageUrl ="https://www.norebbo.com/2018/09/boeing-737-600-blank-illustration-templates/"),
Model("Boeing 737-700",	"Boeing 737",	149,	149,	5,	592,	834,	7630,	64347000,	1820,	24,	Manufacturer("Boeing",	countryCode="US"),	2042,	imageUrl ="https://www.norebbo.com/2014/04/boeing-737-700-blank-illustration-templates/"),
Model("Boeing 737-700ER",	"Boeing 737",	149,	76,	6,	525,	834,	10695,	79477000,	1820,	24,	Manufacturer("Boeing",	countryCode="US"),	2042,	imageUrl ="https://www.norebbo.com/2014/04/boeing-737-700-blank-illustration-templates/"),
Model("Boeing 737-800",	"Boeing 737",	180,	180,	6,	738,	842,	5436,	91937000,	1820,	36,	Manufacturer("Boeing",	countryCode="US"),	2316,	imageUrl ="https://www.norebbo.com/2012/11/boeing-737-800-blank-illustration-templates/"),
Model("Boeing 737-900",	"Boeing 737",	215,	215,	6,	875,	842,	5436,	112318000,	1820,	36,	Manufacturer("Boeing",	countryCode="US"),	3000,	imageUrl ="https://www.norebbo.com/2014/08/boeing-737-900-blank-illustration-templates/"),
Model("Boeing 737-900ER",	"Boeing 737",	220,	220,	6,	870,	844,	5460,	119972000,	1820,	36,	Manufacturer("Boeing",	countryCode="US"),	3000,	imageUrl ="https://www.norebbo.com/2016/07/boeing-737-900er-with-split-scimitar-winglets-blank-illustration-templates/"),
Model("Boeing 747-100",	"Boeing 747",	550,	550,	5,	3105,	907,	8530,	246352000,	1820,	54,	Manufacturer("Boeing",	countryCode="US"),	3250,	imageUrl ="https://www.norebbo.com/2019/07/boeing-747-100-side-view/"),
Model("Boeing 747-200",	"Boeing 747",	550,	550,	5,	3025,	907,	12150,	259702000,	1820,	60,	Manufacturer("Boeing",	countryCode="US"),	3300,	imageUrl ="https://www.norebbo.com/2019/08/boeing-747-200-side-view/"),
Model("Boeing 747-300",	"Boeing 747",	660,	400,	6,	3602,	939,	11720,	350927000,	1820,	72,	Manufacturer("Boeing",	countryCode="US"),	2955,	imageUrl ="https://www.norebbo.com/boeing-747-300-side-view/"),
Model("Boeing 747-400",	"Boeing 747",	660,	416,	6,	3615,	943,	13490,	368727000,	1820,	72,	Manufacturer("Boeing",	countryCode="US"),	3260,	imageUrl =""),
Model("Boeing 747-400D",	"Boeing 747",	660,	660,	5,	3845,	943,	10490,	302778000,	1820,	72,	Manufacturer("Boeing",	countryCode="US"),	3260,	imageUrl =""),
Model("Boeing 747-400ER",	"Boeing 747",	660,	416,	6,	3625,	943,	14045,	384747000,	1820,	72,	Manufacturer("Boeing",	countryCode="US"),	3300,	imageUrl ="https://www.norebbo.com/2013/09/boeing-747-400-blank-illustration-templates/"),
Model("Boeing 747-8i",	"Boeing 747",	718,	605,	7,	3690,	933,	14320,	525990000,	1820,	72,	Manufacturer("Boeing",	countryCode="US"),	3100,	imageUrl ="https://www.norebbo.com/2015/12/boeing-747-8i-blank-illustration-templates/"),
Model("Boeing 747SP",	"Boeing 747",	400,	242,	6,	2090,	994,	10800,	240300000,	1820,	54,	Manufacturer("Boeing",	countryCode="US"),	2820,	imageUrl ="https://www.norebbo.com/2019/08/boeing-747sp-side-view/"),
Model("Boeing 757-100",	"Boeing 757",	160,	160,	5,	660,	854,	8250,	69598000,	1976,	54,	Manufacturer("Boeing",	countryCode="US"),	1910,	imageUrl =""),
Model("Boeing 757-200",	"Boeing 757",	239,	239,	5,	1023,	854,	7250,	88733000,	1976,	54,	Manufacturer("Boeing",	countryCode="US"),	2070,	imageUrl ="https://www.norebbo.com/2015/01/boeing-757-200-blank-illustration-templates/"),
Model("Boeing 757-200ER",	"Boeing 757",	239,	239,	5,	970,	850,	9170,	99413000,	1976,	54,	Manufacturer("Boeing",	countryCode="US"),	2070,	imageUrl ="https://www.norebbo.com/2015/01/boeing-757-200-blank-illustration-templates/"),
Model("Boeing 757-300",	"Boeing 757",	295,	295,	5,	1426,	850,	6421,	123977000,	1976,	54,	Manufacturer("Boeing",	countryCode="US"),	2377,	imageUrl ="https://www.norebbo.com/2017/03/boeing-757-300-blank-illustration-templates/"),
Model("Boeing 767-200",	"Boeing 767",	265,	265,	5,	1285,	851,	7200,	85529000,	1820,	54,	Manufacturer("Boeing",	countryCode="US"),	1900,	imageUrl ="https://www.norebbo.com/2014/07/boeing-767-200-blank-illustration-templates/"),
Model("Boeing 767-200ER",	"Boeing 767",	265,	216,	5,	1179,	896,	12200,	111339000,	1820,	54,	Manufacturer("Boeing",	countryCode="US"),	2480,	imageUrl ="https://www.norebbo.com/2014/07/boeing-767-200-blank-illustration-templates/"),
Model("Boeing 767-300",	"Boeing 767",	299,	299,	5,	1385,	851,	7200,	145693000,	1820,	54,	Manufacturer("Boeing",	countryCode="US"),	2800,	imageUrl ="https://www.norebbo.com/2014/07/boeing-767-200-blank-illustration-templates/"),
Model("Boeing 767-300ER",	"Boeing 767",	299,	261,	6,	1315,	896,	11093,	172393000,	1820,	54,	Manufacturer("Boeing",	countryCode="US"),	2650,	imageUrl ="https://www.norebbo.com/2014/07/boeing-767-200-blank-illustration-templates/"),
Model("Boeing 767-400ER",	"Boeing 767",	409,	272,	6,	1815,	896,	10415,	219029000,	1820,	54,	Manufacturer("Boeing",	countryCode="US"),	3290,	imageUrl ="https://www.norebbo.com/2014/07/boeing-767-400-blank-illustration-templates/"),
Model("Boeing 777-200",	"Boeing 777",	440,	440,	6,	2050,	896,	9700,	293255000,	1820,	54,	Manufacturer("Boeing",	countryCode="US"),	2440,	imageUrl ="https://www.norebbo.com/2012/12/boeing-777-200-blank-illustration-templates/"),
Model("Boeing 777-200ER",	"Boeing 777",	440,	440,	7,	2023,	896,	13080,	325117000,	1820,	54,	Manufacturer("Boeing",	countryCode="US"),	3380,	imageUrl ="https://www.norebbo.com/2012/12/boeing-777-200-blank-illustration-templates/"),
Model("Boeing 777-200LR",	"Boeing 777",	440,	301,	7,	2023,	896,	15843,	329567000,	1820,	54,	Manufacturer("Boeing",	countryCode="US"),	2800,	imageUrl ="https://www.norebbo.com/2012/12/boeing-777-200-blank-illustration-templates/"),
Model("Boeing 777-300",	"Boeing 777",	550,	550,	6,	2955,	945,	11121,	335441000,	1820,	66,	Manufacturer("Boeing",	countryCode="US"),	3230,	imageUrl ="https://www.norebbo.com/2014/03/boeing-777-300-blank-illustration-templates/"),
Model("Boeing 777-300ER",	"Boeing 777",	550,	365,	7,	2935,	945,	13649,	396317000,	1820,	66,	Manufacturer("Boeing",	countryCode="US"),	3050,	imageUrl ="https://www.norebbo.com/2014/03/boeing-777-300-blank-illustration-templates/"),
Model("Boeing 777-8",	"Boeing 777",	440,	395,	8,	1778,	896,	16090,	424975000,	1820,	54,	Manufacturer("Boeing",	countryCode="US"),	3050,	imageUrl ="https://www.norebbo.com/2019/12/boeing-777-8-side-view/"),
Model("Boeing 777-9",	"Boeing 777",	520,	426,	8,	2079,	896,	13940,	530796000,	1820,	66,	Manufacturer("Boeing",	countryCode="US"),	3050,	imageUrl ="https://www.norebbo.com/2019/12/boeing-777-9-side-view/"),
Model("Boeing 787-10 Dreamliner",	"Boeing 787",	440,	440,	7,	1903,	903,	11750,	342917000,	1820,	54,	Manufacturer("Boeing",	countryCode="US"),	2800,	imageUrl ="https://www.norebbo.com/2017/06/boeing-787-10-blank-illustration-templates/"),
Model("Boeing 787-10ER",	"Boeing 787",	440,	280,	7,	1860,	903,	13750,	365167000,	1820,	54,	Manufacturer("Boeing",	countryCode="US"),	2900,	imageUrl ="https://www.norebbo.com/2017/06/boeing-787-10-blank-illustration-templates/"),
Model("Boeing 787-8 Dreamliner",	"Boeing 787",	359,	359,	7,	1489,	907,	13621,	258545000,	1820,	54,	Manufacturer("Boeing",	countryCode="US"),	2600,	imageUrl ="https://www.norebbo.com/2013/02/boeing-787-8-blank-illustration-templates/"),
Model("Boeing 787-9 Dreamliner",	"Boeing 787",	420,	420,	7,	1853,	903,	14010,	295124000,	1820,	54,	Manufacturer("Boeing",	countryCode="US"),	2800,	imageUrl ="https://www.norebbo.com/2014/04/boeing-787-9-blank-illustration-templates/"),
Model("Boeing 787-9ER",	"Boeing 787",	420,	265,	7,	1762,	903,	15400,	330724000,	1820,	54,	Manufacturer("Boeing",	countryCode="US"),	2900,	imageUrl ="https://www.norebbo.com/2014/04/boeing-787-9-blank-illustration-templates/"),
Model("Boeing 797-6",	"Boeing 797",	225,	225,	8,	769,	890,	9260,	215736000,	1820,	60,	Manufacturer("Boeing",	countryCode="US"),	2600,	imageUrl ="https://www.norebbo.com/boeing-797-side-view/"),
Model("Boeing 797-7",	"Boeing 797",	275,	275,	8,	979,	890,	8330,	272874000,	1820,	60,	Manufacturer("Boeing",	countryCode="US"),	2600,	imageUrl ="https://www.norebbo.com/boeing-797-side-view/"),
Model("Boeing Vertol 107-II ",	"Boeing Vertol",	28,	28,	4,	39,	265,	1020,	3738000,	1300,	6,	Manufacturer("Boeing",	countryCode="US"),	1,	imageUrl =""),
Model("Boeing Vertol 234",	"Boeing Vertol",	44,	44,	4,	54,	269,	1010,	7743000,	1300,	6,	Manufacturer("Boeing",	countryCode="US"),	1,	imageUrl =""),
Model("Bombardier CRJ100",	"Bombardier CRJ",	50,	50,	7,	95,	830,	2250,	30349000,	1820,	0,	Manufacturer("Bombardier",	countryCode="CA"),	1920,	imageUrl ="https://www.norebbo.com/2015/04/bombardier-canadair-regional-jet-200-blank-illustration-templates/"),
Model("Bombardier CRJ1000",	"Bombardier CRJ",	104,	104,	7,	312,	870,	3004,	59630000,	1820,	12,	Manufacturer("Bombardier",	countryCode="CA"),	2120,	imageUrl ="https://www.norebbo.com/2019/06/bombardier-crj-1000-side-view/"),
Model("Bombardier CRJ200",	"Bombardier CRJ",	50,	50,	7,	95,	830,	3150,	31239000,	1820,	0,	Manufacturer("Bombardier",	countryCode="CA"),	1920,	imageUrl ="https://www.norebbo.com/2015/04/bombardier-canadair-regional-jet-200-blank-illustration-templates/"),
Model("Bombardier CRJ700",	"Bombardier CRJ",	78,	78,	7,	179,	828,	3045,	49128000,	1820,	6,	Manufacturer("Bombardier",	countryCode="CA"),	1605,	imageUrl ="https://www.norebbo.com/2015/05/bombardier-canadair-regional-jet-700-blank-illustration-templates/"),
Model("Bombardier CRJ900",	"Bombardier CRJ",	90,	90,	7,	221,	870,	2876,	53311000,	1820,	12,	Manufacturer("Bombardier",	countryCode="CA"),	1939,	imageUrl ="https://www.norebbo.com/2016/07/bombardier-canadair-regional-jet-900-blank-illustration-templates/"),
Model("Bombardier Global 5000",	"Modern Business Jet",	40,	16,	10,	50,	934,	9630,	29815000,	1820,	12,	Manufacturer("Bombardier",	countryCode="CA"),	1689,	imageUrl ="https://www.norebbo.com/bombardier-global-5000-blank-illustration-templates/"),
Model("Bombardier Global 7500",	"Modern Business Jet",	48,	19,	10,	67,	1080,	14260,	44233000,	1820,	12,	Manufacturer("Bombardier",	countryCode="CA"),	1768,	imageUrl ="https://www.norebbo.com/bombardier-global-7500-side-view/"),
Model("Boom Overture",	"Boom Overture",	160,	80,	10,	1996,	1800,	7870,	325651000,	1664,	150,	Manufacturer("Boom Technology",	countryCode="US"),	3048,	imageUrl =""),
Model("CASA C-212 Aviocar",	"CASA",	26,	26,	2,	36,	354,	2680,	2759000,	1560,	0,	Manufacturer("CASA",	countryCode="ES"),	600,	imageUrl =""),
Model("CASA CN-235",	"CASA",	40,	40,	2,	63,	460,	3658,	7120000,	1560,	0,	Manufacturer("CASA",	countryCode="ES"),	1204,	imageUrl =""),
Model("Cessna Caravan",	"Cessna",	16,	16,	4,	15,	355,	2400,	3115000,	1820,	0,	Manufacturer("Cessna",	countryCode="US"),	762,	imageUrl ="https://www.norebbo.com/2017/06/cessna-208-grand-caravan-blank-illustration-templates/"),
Model("Cessna Citation X",	"Cessna",	30,	12,	10,	34,	900,	6050,	23318000,	1820,	12,	Manufacturer("Cessna",	countryCode="US"),	1600,	imageUrl ="https://www.norebbo.com/cessna-citation-x-template/"),
Model("Comac ARJ21",	"Comac ARJ21",	90,	90,	4,	245,	828,	2200,	34265000,	1300,	12,	Manufacturer("COMAC",	countryCode="CN"),	1700,	imageUrl =""),
Model("Comac C919",	"Comac C919",	168,	168,	5,	634,	834,	4075,	69509000,	1300,	30,	Manufacturer("COMAC",	countryCode="CN"),	2000,	imageUrl ="https://www.norebbo.com/comac-c919-side-view/"),
Model("Comac C929-500",	"Comac C929",	348,	348,	6,	1608,	908,	14000,	175864000,	1560,	36,	Manufacturer("COMAC",	countryCode="CN"),	2700,	imageUrl =""),
Model("Comac C929-600",	"Comac C929",	405,	405,	6,	1950,	908,	12000,	203899000,	1560,	36,	Manufacturer("COMAC",	countryCode="CN"),	2800,	imageUrl =""),
Model("Comac C929-700",	"Comac C929",	440,	440,	6,	2110,	908,	10000,	244305000,	1560,	36,	Manufacturer("COMAC",	countryCode="CN"),	2900,	imageUrl =""),
Model("Comac C939",	"Comac C939",	460,	460,	6,	2188,	908,	14000,	248666000,	1560,	45,	Manufacturer("COMAC",	countryCode="CN"),	2800,	imageUrl =""),
Model("Concorde",	"Concorde",	192,	128,	10,	3390,	2158,	7223,	367303000,	1820,	90,	Manufacturer("BAe",	countryCode="GB"),	3600,	imageUrl ="https://www.norebbo.com/aerospatiale-bac-concorde-blank-illustration-templates/"),
Model("Dassault Falcon 50",	"60s Business Jet",	28,	14,	9,	57,	883,	5660,	10324000,	1456,	12,	Manufacturer("Dassault",	countryCode="FR"),	1524,	imageUrl ="https://www.norebbo.com/dassault-falcon-50/"),
Model("Dassault Mercure",	"Dassault",	162,	162,	3,	540,	926,	2084,	63101000,	1820,	18,	Manufacturer("Dassault Aviation",	countryCode="FR"),	2100,	imageUrl =""),
Model("De Havilland Canada DHC-7-100",	"De Havilland Canada DHC",	50,	50,	4,	66,	428,	1300,	17355000,	1820,	6,	Manufacturer("De Havilland Canada",	countryCode="CA"),	620,	imageUrl =""),
Model("De Havilland Canada DHC-8-100",	"De Havilland Canada DHC",	39,	39,	5,	55,	448,	1889,	14240000,	1820,	6,	Manufacturer("De Havilland Canada",	countryCode="CA"),	950,	imageUrl ="https://www.norebbo.com/2018/01/de-havilland-dhc-8-200-dash-8-blank-illustration-templates/"),
Model("De Havilland Canada DHC-8-200",	"De Havilland Canada DHC",	39,	39,	5,	55,	448,	2084,	15575000,	1820,	6,	Manufacturer("De Havilland Canada",	countryCode="CA"),	1000,	imageUrl ="https://www.norebbo.com/2018/01/de-havilland-dhc-8-200-dash-8-blank-illustration-templates/"),
Model("De Havilland Canada DHC-8-300",	"De Havilland Canada DHC",	50,	50,	5,	74,	450,	1711,	19847000,	1820,	6,	Manufacturer("De Havilland Canada",	countryCode="CA"),	1085,	imageUrl ="https://www.norebbo.com/2018/05/de-havilland-dhc-8-300-blank-illustration-templates/"),
Model("De Havilland Canada DHC-8-400",	"De Havilland Canada DHC",	68,	68,	5,	159,	667,	1980,	30616000,	1560,	6,	Manufacturer("De Havilland Canada",	countryCode="CA"),	1085,	imageUrl ="https://www.norebbo.com/2018/05/de-havilland-dhc-8-300-blank-illustration-templates/"),
Model("De Havilland Canada Q400",	"De Havilland Canada DHC",	82,	82,	5,	160,	562,	2040,	33998000,	1560,	12,	Manufacturer("De Havilland Canada",	countryCode="CA"),	1885,	imageUrl ="https://www.norebbo.com/2015/08/bombardier-dhc-8-402-q400-blank-illustration-templates/"),
Model("Dornier 1128",	"Dornier 728",	130,	130,	8,	464,	923,	3800,	70132000,	1300,	18,	Manufacturer("Dornier",	countryCode="DE"),	1550,	imageUrl =""),
Model("Dornier 328-110",	"Dornier 328",	33,	33,	8,	60,	620,	1310,	15931000,	1820,	9,	Manufacturer("Dornier",	countryCode="DE"),	1088,	imageUrl ="https://www.norebbo.com/2019/01/dornier-328-110-blank-illustration-templates/"),
Model("Dornier 328JET",	"Dornier 328",	44,	44,	8,	74,	740,	1665,	24386000,	1820,	9,	Manufacturer("Dornier",	countryCode="DE"),	1367,	imageUrl ="https://www.norebbo.com/2019/01/fairchild-dornier-328jet-illustrations/"),
Model("Dornier 528",	"Dornier 728",	65,	65,	8,	178,	1000,	3000,	34799000,	1820,	9,	Manufacturer("Dornier",	countryCode="DE"),	1363,	imageUrl =""),
Model("Dornier 728",	"Dornier 728",	80,	80,	8,	253,	1000,	3300,	41741000,	1820,	9,	Manufacturer("Dornier",	countryCode="DE"),	1463,	imageUrl =""),
Model("Dornier 928",	"Dornier 728",	112,	112,	8,	399,	951,	3600,	53311000,	1300,	15,	Manufacturer("Dornier",	countryCode="DE"),	1513,	imageUrl =""),
Model("Douglas DC-10-10",	"DC-10",	410,	410,	3,	1835,	876,	6500,	116679000,	1820,	18,	Manufacturer("McDonnell Douglas",	countryCode="US"),	2700,	imageUrl ="https://www.norebbo.com/2018/05/mcdonnell-douglas-md-11-blank-illustration-templates-with-ge-engines/"),
Model("Douglas DC-10-30",	"DC-10",	410,	410,	3,	1850,	886,	9400,	146939000,	1820,	24,	Manufacturer("McDonnell Douglas",	countryCode="US"),	3200,	imageUrl ="https://www.norebbo.com/2018/05/mcdonnell-douglas-md-11-blank-illustration-templates-with-ge-engines/"),
Model("Douglas DC-10-40",	"DC-10",	410,	410,	4,	1900,	886,	12392,	157708000,	1820,	30,	Manufacturer("McDonnell Douglas",	countryCode="US"),	2900,	imageUrl =""),
Model("Douglas DC-3",	"Post-War Props",	32,	32,	0,	59,	333,	2400,	1157000,	2912,	0,	Manufacturer("Douglas Aircraft Company",	countryCode="US"),	1312,	imageUrl =""),
Model("Douglas DC-8-10",	"DC-8",	177,	177,	2,	949,	895,	6960,	20470000,	1820,	6,	Manufacturer("Douglas Aircraft Company",	countryCode="US"),	2680,	imageUrl ="https://www.norebbo.com/douglas-dc-8-61-blank-illustration-templates/"),
Model("Douglas DC-8-72",	"DC-8",	189,	189,	3,	989,	895,	9800,	28124000,	1820,	12,	Manufacturer("Douglas Aircraft Company",	countryCode="US"),	2680,	imageUrl ="https://www.norebbo.com/douglas-dc-8-73-and-dc-8-73cf-blank-illustration-templates/"),
Model("Douglas DC-8-73",	"DC-8",	259,	259,	3,	1340,	895,	8300,	44500000,	1820,	12,	Manufacturer("Douglas Aircraft Company",	countryCode="US"),	2680,	imageUrl ="https://www.norebbo.com/douglas-dc-8-73-and-dc-8-73cf-blank-illustration-templates/"),
Model("Embraer E175-E2",	"Embraer E-Jet E2",	88,	88,	6,	200,	870,	3735,	50285000,	1560,	9,	Manufacturer("Embraer",	countryCode="BR"),	1800,	imageUrl ="https://www.norebbo.com/2019/03/e175-e2-side-view/"),
Model("Embraer E190-E2",	"Embraer E-Jet E2",	106,	106,	6,	260,	870,	5278,	62033000,	1560,	12,	Manufacturer("Embraer",	countryCode="BR"),	1450,	imageUrl ="https://www.norebbo.com/2019/03/e190-e2-blank-side-view/"),
Model("Embraer E195-E2",	"Embraer E-Jet E2",	146,	146,	6,	421,	833,	4800,	75205000,	1560,	18,	Manufacturer("Embraer",	countryCode="BR"),	1970,	imageUrl ="https://www.norebbo.com/2019/03/embraer-e195-e2-side-view/"),
Model("Embraer ERJ135",	"Embraer ERJ",	37,	37,	5,	79,	850,	3241,	10146000,	1560,	6,	Manufacturer("Embraer",	countryCode="BR"),	1580,	imageUrl ="https://www.norebbo.com/2018/05/embraer-erj-135-blank-illustration-templates/"),
Model("Embraer ERJ145",	"Embraer ERJ",	50,	50,	5,	130,	850,	2800,	18957000,	1560,	6,	Manufacturer("Embraer",	countryCode="BR"),	1410,	imageUrl ="https://www.norebbo.com/2018/04/embraer-erj-145-blank-illustration-templates/"),
Model("Embraer ERJ145XR",	"Embraer ERJ",	50,	50,	6,	124,	850,	3700,	21182000,	1560,	6,	Manufacturer("Embraer",	countryCode="BR"),	1720,	imageUrl ="https://www.norebbo.com/2018/04/embraer-erj-145xr-blank-illustration-templates/"),
Model("Embraer ERJ170",	"Embraer ERJ",	72,	72,	5,	216,	870,	3982,	24208000,	1560,	6,	Manufacturer("Embraer",	countryCode="BR"),	1644,	imageUrl ="https://www.norebbo.com/embraer-erj-175-templates-with-the-new-style-winglets/"),
Model("Embraer ERJ175",	"Embraer ERJ",	78,	78,	5,	239,	870,	4074,	28124000,	1560,	6,	Manufacturer("Embraer",	countryCode="BR"),	2144,	imageUrl ="https://www.norebbo.com/2015/10/embraer-erj-175-templates-with-the-new-style-winglets/"),
Model("Embraer ERJ190",	"Embraer ERJ",	100,	100,	5,	320,	823,	4537,	34710000,	1560,	12,	Manufacturer("Embraer",	countryCode="BR"),	2054,	imageUrl ="https://www.norebbo.com/2015/06/embraer-190-blank-illustration-templates/"),
Model("F27-100",	"Fokker",	44,	44,	4,	61,	460,	1468,	9790000,	1560,	0,	Manufacturer("Fokker",	countryCode="NL"),	1550,	imageUrl =""),
Model("Fokker 100",	"Fokker",	109,	109,	5,	340,	845,	1686,	43788000,	1820,	3,	Manufacturer("Fokker",	countryCode="NL"),	1550,	imageUrl ="https://www.norebbo.com/2018/07/fokker-100-f-28-0100-blank-illustration-templates/"),
Model("Fokker 50",	"Fokker",	56,	56,	4,	86,	500,	1700,	13884000,	1560,	3,	Manufacturer("Fokker",	countryCode="NL"),	1550,	imageUrl =""),
Model("Fokker 60",	"Fokker",	64,	64,	4,	120,	515,	1550,	17355000,	1560,	3,	Manufacturer("Fokker",	countryCode="NL"),	1550,	imageUrl =""),
Model("Fokker 60 Jet",	"Fokker",	64,	64,	4,	165,	845,	1400,	22695000,	1560,	6,	Manufacturer("Fokker",	countryCode="NL"),	1550,	imageUrl =""),
Model("Gulfstream G650ER",	"Modern Business Jet",	48,	19,	10,	51,	966,	13890,	49751000,	2080,	12,	Manufacturer("Gulfstream",	countryCode="US"),	1920,	imageUrl ="https://www.norebbo.com/gulfstream-g650er-template/"),
Model("Ilyushin Il-18",	"Ilyushin Il",	120,	120,	1,	448,	625,	6500,	15753000,	1300,	6,	Manufacturer("Ilyushin",	countryCode="RU"),	1350,	imageUrl =""),
Model("Ilyushin Il-62",	"Ilyushin Il",	186,	186,	2,	983,	900,	10000,	27145000,	1456,	18,	Manufacturer("Ilyushin",	countryCode="RU"),	2300,	imageUrl =""),
Model("Ilyushin Il-86",	"Ilyushin Il-96",	320,	320,	3,	1560,	950,	5000,	81791000,	1456,	36,	Manufacturer("Ilyushin",	countryCode="RU"),	2800,	imageUrl =""),
Model("Ilyushin Il-96-300",	"Ilyushin Il-96",	300,	300,	4,	1500,	900,	13500,	73870000,	1456,	27,	Manufacturer("Ilyushin",	countryCode="RU"),	3200,	imageUrl =""),
Model("Ilyushin Il-96-400",	"Ilyushin Il-96",	436,	436,	4,	2198,	900,	14500,	123621000,	1456,	36,	Manufacturer("Ilyushin",	countryCode="RU"),	2600,	imageUrl =""),
Model("Lockheed Constellation L-749",	"Post-War Props",	81,	81,	1,	240,	555,	8039,	11036000,	1820,	6,	Manufacturer("Lockheed",	countryCode="US"),	1050,	imageUrl =""),
Model("Lockheed L-1011-100",	"Lockheed TriStar",	400,	400,	5,	1882,	962,	4963,	140709000,	1560,	24,	Manufacturer("Lockheed",	countryCode="US"),	2560,	imageUrl =""),
Model("Lockheed L-1011-200",	"Lockheed TriStar",	400,	400,	5,	1892,	969,	6500,	157619000,	1560,	27,	Manufacturer("Lockheed",	countryCode="US"),	2560,	imageUrl =""),
Model("Lockheed L-1011-500",	"Lockheed TriStar",	400,	250,	6,	1868,	986,	9899,	207014000,	1560,	36,	Manufacturer("Lockheed",	countryCode="US"),	2865,	imageUrl ="https://www.norebbo.com/2015/03/lockheed-l-1011-500-blank-illustration-templates/"),
Model("McDonnell Douglas DC-9-10",	"DC-9",	92,	92,	3,	270,	965,	2367,	23941000,	1040,	6,	Manufacturer("McDonnell Douglas",	countryCode="US"),	1816,	imageUrl =""),
Model("McDonnell Douglas DC-9-30",	"DC-9",	115,	115,	4,	345,	804,	2778,	29459000,	1040,	6,	Manufacturer("McDonnell Douglas",	countryCode="US"),	1900,	imageUrl ="https://www.norebbo.com/mcdonnell-douglas-dc-9-30-templates/"),
Model("McDonnell Douglas DC-9-50",	"DC-9",	139,	139,	4,	464,	804,	3030,	38537000,	1040,	6,	Manufacturer("McDonnell Douglas",	countryCode="US"),	2100,	imageUrl ="https://www.norebbo.com/dc-9-50-side-view/"),
Model("McDonnell Douglas MD-11",	"DC-10",	410,	410,	5,	1910,	886,	12455,	206658000,	1820,	36,	Manufacturer("McDonnell Douglas",	countryCode="US"),	3050,	imageUrl =""),
Model("McDonnell Douglas MD-220",	"60s Business Jet",	29,	29,	7,	70,	1020,	4100,	13172000,	1820,	12,	Manufacturer("McDonnell Douglas",	countryCode="US"),	1200,	imageUrl =""),
Model("McDonnell Douglas MD-81",	"DC-9",	146,	146,	4,	425,	811,	4635,	68352000,	1560,	24,	Manufacturer("McDonnell Douglas",	countryCode="US"),	2070,	imageUrl ="https://www.norebbo.com/2015/02/mcdonnell-douglas-md-80-blank-illustration-templates/"),
Model("McDonnell Douglas MD-90",	"DC-9",	160,	160,	5,	530,	811,	3787,	79566000,	1560,	30,	Manufacturer("McDonnell Douglas",	countryCode="US"),	2134,	imageUrl ="https://www.norebbo.com/2018/02/mcdonnell-douglas-md-90-blank-illustration-templates/"),
Model("McDonnell Douglas MD-90ER",	"DC-9",	160,	160,	5,	510,	811,	4143,	87576000,	1560,	30,	Manufacturer("McDonnell Douglas",	countryCode="US"),	2134,	imageUrl ="https://www.norebbo.com/2018/02/mcdonnell-douglas-md-90-blank-illustration-templates/"),
Model("McDonnell Douglas MD-XX",	"DC-10",	440,	380,	5,	1950,	906,	10455,	230866000,	1820,	36,	Manufacturer("McDonnell Douglas",	countryCode="US"),	2964,	imageUrl =""),
Model("Mil Mi-26",	"Mil",	90,	90,	1,	123,	255,	970,	14062000,	1300,	0,	Manufacturer("Mil",	countryCode="RU"),	1,	imageUrl =""),
Model("Mitsubishi MRJ-100",	"Mitsubishi SpaceJet",	88,	88,	7,	195,	900,	3540,	74137000,	2080,	9,	Manufacturer("Mitsubishi",	countryCode="JP"),	1760,	imageUrl =""),
Model("Mitsubishi MRJ-90",	"Mitsubishi SpaceJet",	92,	92,	7,	204,	900,	3770,	79388000,	2080,	12,	Manufacturer("Mitsubishi",	countryCode="JP"),	1740,	imageUrl =""),
Model("Pilatus PC-12",	"Pilatus",	9,	9,	5,	8,	528,	3417,	1869000,	1456,	0,	Manufacturer("Pilatus",	countryCode="CH"),	758,	imageUrl =""),
Model("Saab 2000",	"Saab Regional",	58,	58,	5,	88,	608,	2868,	25721000,	1820,	6,	Manufacturer("Saab",	countryCode="SE"),	1252,	imageUrl =""),
Model("Saab 90 Scandia",	"Saab Regional",	32,	32,	4,	36,	340,	2650,	5963000,	1820,	0,	Manufacturer("Saab",	countryCode="SE"),	850,	imageUrl =""),
Model("Shaanxi Y-10",	"Shaanxi Y",	178,	178,	4,	775,	917,	5560,	43165000,	1040,	18,	Manufacturer("Shaanxi Aircraft Corporation",	countryCode="CN"),	2700,	imageUrl =""),
Model("Sikorsky S-76",	"Sikorsky",	12,	12,	6,	16,	287,	761,	2581000,	1560,	3,	Manufacturer("Sikorsky",	countryCode="US"),	1,	imageUrl =""),
Model("Sikorsky S-92",	"Sikorsky",	25,	19,	7,	28,	290,	998,	6497000,	1560,	3,	Manufacturer("Sikorsky",	countryCode="US"),	1,	imageUrl =""),
Model("Sukhoi KR860",	"Sukhoi",	860,	860,	5,	4770,	907,	9400,	304113000,	1300,	87,	Manufacturer("JSC Sukhoi",	countryCode="RU"),	3400,	imageUrl =""),
Model("Sukhoi Su-80",	"Sukhoi",	32,	32,	5,	44,	520,	1600,	6319000,	1300,	12,	Manufacturer("JSC Sukhoi",	countryCode="RU"),	650,	imageUrl =""),
Model("Sukhoi Superjet 100",	"Sukhoi",	108,	108,	6,	300,	828,	4578,	51976000,	1560,	24,	Manufacturer("JSC Sukhoi",	countryCode="RU"),	1731,	imageUrl ="https://www.norebbo.com/2016/02/sukhoi-ssj-100-blank-illustration-templates/"),
Model("Sukhoi Superjet 130NG",	"Sukhoi",	130,	130,	6,	499,	871,	4008,	56159000,	1560,	24,	Manufacturer("JSC Sukhoi",	countryCode="RU"),	1731,	imageUrl =""),
Model("Tupolev Tu-124",	"Tupolev Tu",	56,	56,	1,	259,	970,	2300,	7921000,	1300,	12,	Manufacturer("Tupolev",	countryCode="RU"),	1550,	imageUrl =""),
Model("Tupolev Tu-204",	"Tupolev Tu",	210,	210,	4,	965,	810,	4300,	42809000,	1300,	24,	Manufacturer("Tupolev",	countryCode="RU"),	1870,	imageUrl ="https://www.norebbo.com/tupolev-tu-204-100-blank-illustration-templates/"),
Model("Tupolev Tu-334-100",	"Tupolev Tu",	102,	102,	4,	288,	820,	4100,	30171000,	1300,	12,	Manufacturer("Tupolev",	countryCode="RU"),	1940,	imageUrl =""),
Model("Tupolev Tu-334-200",	"Tupolev Tu",	126,	126,	4,	465,	820,	3150,	33286000,	1300,	6,	Manufacturer("Tupolev",	countryCode="RU"),	1820,	imageUrl =""),
Model("Vickers VC10",	"Vickers",	150,	150,	3,	555,	930,	9410,	54735000,	1560,	18,	Manufacturer("Vickers-Armstrongs",	countryCode="GB"),	2520,	imageUrl =""),
Model("Xi'an MA600",	"Xi'an Turboprops",	60,	60,	4,	99,	514,	1600,	21627000,	1300,	6,	Manufacturer("Xi'an Aircraft Industrial Corporation",	countryCode="CN"),	750,	imageUrl =""),
Model("Xi'an MA700",	"Xi'an Turboprops",	86,	86,	4,	180,	637,	1500,	33019000,	1300,	12,	Manufacturer("Xi'an Aircraft Industrial Corporation",	countryCode="CN"),	630,	imageUrl =""),
Model("Yakovlev MC-21-100",	"Yakovlev MC-21",	132,	132,	6,	465,	870,	6140,	54824000,	1300,	30,	Manufacturer("Irkut",	countryCode="RU"),	1322,	imageUrl ="https://www.norebbo.com/irkut-mc-21-300/"),
Model("Yakovlev MC-21-200",	"Yakovlev MC-21",	165,	165,	6,	625,	870,	6400,	70577000,	1300,	36,	Manufacturer("Irkut",	countryCode="RU"),	1350,	imageUrl ="https://www.norebbo.com/irkut-mc-21-300/"),
Model("Yakovlev MC-21-300",	"Yakovlev MC-21",	211,	211,	6,	865,	870,	6000,	82770000,	1300,	45,	Manufacturer("Irkut",	countryCode="RU"),	1644,	imageUrl ="https://www.norebbo.com/irkut-mc-21-300/"),
Model("Yakovlev MC-21-400",	"Yakovlev MC-21",	230,	230,	6,	937,	870,	5500,	97455000,	1300,	54,	Manufacturer("Irkut",	countryCode="RU"),	1500,	imageUrl ="https://www.norebbo.com/irkut-mc-21-300/"),
Model("Zeppelin",	"Zeppelin",	432,	72,	10,	22,	141,	8000,	97811000,	624,	24,	Manufacturer("Zeppelin Luftschifftechnik GmbH",	countryCode="DE"),	100,	imageUrl =""),
Model("Zeppelin NT",	"Zeppelin",	50,	20,	7,	3,	128,	900,	8633000,	936,	18,	Manufacturer("Zeppelin Luftschifftechnik GmbH",	countryCode="DE"),	100,	imageUrl =""),
  )
  val modelByName = models.map { model => (model.name, model) }.toMap
}