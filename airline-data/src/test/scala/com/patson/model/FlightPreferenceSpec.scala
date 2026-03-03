package com.patson.model

import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.testkit.ImplicitSender
import org.apache.pekko.testkit.TestKit
import com.patson.model.airplane.{Airplane, Model}

/**
 * Comprehensive tests for FlightPreference cost adjustment factors.
 *
 * SUMMARY OF BIGGEST LEVERS FOR LOWERING PERCEIVED COST:
 *
 * 1. PRICE (priceAdjustRatio):
 *    - Most impactful across all groups
 *    - priceSensitivity by class: FIRST=0.75, BUSINESS=0.85, ECONOMY=0.95, DISCOUNT=1.05
 *    - DealPreference adds +0.15 to class sensitivity (most price-sensitive)
 *    - FREQUENT/BRAND/LAST_MINUTE get 10% reduction on low-price benefit
 *
 * 2. QUALITY (qualityAdjustRatio):
 *    - DealPreference: qualitySensitivity=0.4 (least affected)
 *    - AppealPreference: qualitySensitivity=1.1-1.5 (most affected)
 *    - LastMinutePreference: qualitySensitivity=0.3 (deal) or 1.0 (urgent)
 *    - Quality delta from expected quality matters; +20 delta gives best discount
 *
 * 3. FREQUENCY (frequencyAdjustRatio):
 *    - BUSINESS pax: frequencySensitivity=0.6 (highest)
 *    - ELITE pax: frequencySensitivity=0.3
 *    - TRAVELER pax: frequencySensitivity=0.2
 *    - Others: frequencySensitivity=0.15
 *    - Short flights (<180 min) care much more about frequency
 *
 * 4. TRIP DURATION (tripDurationAdjustRatio):
 *    - ELITE pax: 0.7 sensitivity (highest)
 *    - BUSINESS pax: 0.2 + class modifier (0.1-0.5)
 *    - FIRST class adds +0.5, BUSINESS +0.35, others +0.1
 *    - Slow planes penalized up to 1.6x cost
 *
 * 5. LOYALTY (loyaltyAdjustRatio):
 *    - BOTH BRAND (loyaltyRatio=1.0) and FREQUENT (loyaltyRatio > 1) are sensitive
 *    - FREQUENT is MORE sensitive
 *    - Max loyalty gives ~25% cost reduction for BRAND, more for FREQUENT
 *    - connectionCostRatio: FREQUENT=1.6, BRAND=1.2, DEAL=0.6 (deal prefers connections)
 *
 * 6. LOUNGE (loungeAdjust):
 *    - Only affects BUSINESS/FIRST class
 *    - Level 3 lounge at both ends gives significant discount
 *    - Fixed deduction (45) means it is MUCH more impactful on short flights
 */
class FlightPreferenceSpec(_system: ActorSystem) extends TestKit(_system) with ImplicitSender
  with AnyWordSpecLike with Matchers with BeforeAndAfterAll {

  def this() = this(ActorSystem("FlightPreferenceSpec"))

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  // Test setup
  val testAirline = Airline("test airline", id = 1)
  val fromAirport = Airport("", "", "From Airport", 0, 0, "", "", "", 1, baseIncome = 40000, basePopulation = 1000000, 0, 0)
  val toAirport = Airport("", "", "To Airport", 0, 30, "", "", "", 1, baseIncome = 40000, basePopulation = 1000000, 0, 0)

  fromAirport.initAirlineAppeals(scala.collection.immutable.Map(testAirline.id -> AirlineAppeal(50)))
  toAirport.initAirlineAppeals(scala.collection.immutable.Map.empty)
  fromAirport.initLounges(scala.collection.immutable.List.empty)
  toAirport.initLounges(scala.collection.immutable.List.empty)

  val distance = 1000
  val defaultPrice = Pricing.computeStandardPriceForAllClass(distance, FlightCategory.DOMESTIC, PassengerType.TRAVELER, fromAirport.baseIncome)
  val defaultCapacity = LinkClassValues.getInstance(10000, 10000, 10000)
  val model = Model.modelByName("Boeing 737 MAX 9")
  val duration = Computation.calculateDuration(model, distance)

  def createLink(
    price: LinkClassValues = defaultPrice,
    quality: Int = 50,
    frequency: Int = 14,
    linkDuration: Int = duration
  ): Link = {
    val link = Link(fromAirport, toAirport, testAirline, price, distance = distance, defaultCapacity, rawQuality = quality, linkDuration, frequency)
    link.setQuality(quality)
    link.setTestingAssignedAirplanes(scala.collection.immutable.Map(Airplane(model, testAirline, 0, purchasedCycle = 0, 100, 0) -> 1))
    link
  }

  "priceSensitivity by LinkClass".must {
    "show FIRST class is least price-sensitive (0.75)".in {
      FIRST.priceSensitivity shouldBe 0.75
    }
    "show BUSINESS class has moderate price-sensitivity (0.85)".in {
      BUSINESS.priceSensitivity shouldBe 0.85
    }
    "show ECONOMY class has high price-sensitivity (0.95)".in {
      ECONOMY.priceSensitivity shouldBe 0.95
    }
    "show DISCOUNT_ECONOMY class is most price-sensitive (1.05)".in {
      DISCOUNT_ECONOMY.priceSensitivity shouldBe 1.05
    }
  }

  "priceAdjustRatio".must {
    "return 1.0 when price equals standard price".in {
      val link = createLink()
      val preference = DealPreference(fromAirport, ECONOMY, 1.0)
      val ratio = preference.priceAdjustRatio(link, ECONOMY, PassengerType.TRAVELER)
      ratio shouldBe 1.0 +- 0.01
    }

    "increase cost when price is above standard (amplified by sensitivity)".in {
      val expensivePrice = defaultPrice * 1.2
      val link = createLink(price = expensivePrice)
      val preference = DealPreference(fromAirport, ECONOMY, 1.0)
      // DealPreference adds 0.15 to class sensitivity: 0.95 + 0.15 = 1.1
      val ratio = preference.priceAdjustRatio(link, ECONOMY, PassengerType.TRAVELER)
      ratio should be > 1.0
      println(s"priceAdjustRatio at 120% price (DealPreference ECONOMY): $ratio")
    }

    "reduce cost when price is below standard".in {
      val cheapPrice = defaultPrice * 0.8
      val link = createLink(price = cheapPrice)
      val preference = DealPreference(fromAirport, ECONOMY, 1.0)
      val ratio = preference.priceAdjustRatio(link, ECONOMY, PassengerType.TRAVELER)
      ratio should be < 1.0
      println(s"priceAdjustRatio at 80% price (DealPreference ECONOMY): $ratio")
    }

    "show FIRST class passengers less affected by price changes".in {
      val expensivePrice = defaultPrice * 1.3
      val link = createLink(price = expensivePrice)

      val econPref = AppealPreference(fromAirport, ECONOMY, 1.0, 0, 1.0, 1)
      val firstPref = AppealPreference(fromAirport, FIRST, 1.0, 0, 1.0, 2)

      val econRatio = econPref.priceAdjustRatio(link, ECONOMY, PassengerType.TRAVELER)
      val firstRatio = firstPref.priceAdjustRatio(link, FIRST, PassengerType.TRAVELER)

      println(s"At 130% price - ECONOMY ratio: $econRatio, FIRST ratio: $firstRatio")
      // FIRST should have smaller increase because lower sensitivity
      (econRatio - 1.0) should be > (firstRatio - 1.0)
    }

    "show DealPreference is most price-sensitive (+0.15 added)".in {
      val cheapPrice = defaultPrice * 0.7
      val link = createLink(price = cheapPrice)

      val dealPref = DealPreference(fromAirport, ECONOMY, 1.0)
      val appealPref = AppealPreference(fromAirport, ECONOMY, 1.0, 0, 1.0, 1)

      val dealRatio = dealPref.priceAdjustRatio(link, ECONOMY, PassengerType.TRAVELER)
      val appealRatio = appealPref.priceAdjustRatio(link, ECONOMY, PassengerType.TRAVELER)

      println(s"At 70% price - DealPreference ratio: $dealRatio, AppealPreference ratio: $appealRatio")
      // Deal should benefit more from low prices
      dealRatio should be < appealRatio
    }
  }

  "qualityAdjustRatio".must {
    "return ~1.0 when quality matches expected".in {
      val expectedQuality = fromAirport.expectedQuality(distance, ECONOMY)
      val link = createLink(quality = expectedQuality)
      val preference = AppealPreference(fromAirport, ECONOMY, 1.0, 0, 1.0, 1)
      val ratio = preference.qualityAdjustRatio(fromAirport, link, ECONOMY, PassengerType.TRAVELER)
      ratio shouldBe 1.0 +- 0.05
    }

    "reduce cost for quality above expected (up to +20 delta efficiently)".in {
      val expectedQuality = fromAirport.expectedQuality(distance, ECONOMY)
      val highQualityLink = createLink(quality = Math.min(100, expectedQuality + 25))
      val preference = AppealPreference(fromAirport, ECONOMY, 1.0, 0, 1.0, 1)
      val ratio = preference.qualityAdjustRatio(fromAirport, highQualityLink, ECONOMY, PassengerType.TRAVELER)
      ratio should be < 1.0
      println(s"qualityAdjustRatio at +25 quality delta (AppealPreference): $ratio")
    }

    "increase cost for quality below expected".in {
      val expectedQuality = fromAirport.expectedQuality(distance, ECONOMY)
      val lowQualityLink = createLink(quality = Math.max(0, expectedQuality - 30))
      val preference = AppealPreference(fromAirport, ECONOMY, 1.0, 0, 1.0, 1)
      val ratio = preference.qualityAdjustRatio(fromAirport, lowQualityLink, ECONOMY, PassengerType.TRAVELER)
      ratio should be > 1.0
      println(s"qualityAdjustRatio at -30 quality delta (AppealPreference): $ratio")
    }

    "show DealPreference is least quality-sensitive (0.4)".in {
      val expectedQuality = fromAirport.expectedQuality(distance, ECONOMY)
      val lowQualityLink = createLink(quality = Math.max(0, expectedQuality - 30))

      val dealPref = DealPreference(fromAirport, ECONOMY, 1.0)
      val appealPref = AppealPreference(fromAirport, ECONOMY, 1.0, 0, 0.5, 1) // BRAND has 1.5 qualitySensitivity

      val dealRatio = dealPref.qualityAdjustRatio(fromAirport, lowQualityLink, ECONOMY, PassengerType.TRAVELER)
      val appealRatio = appealPref.qualityAdjustRatio(fromAirport, lowQualityLink, ECONOMY, PassengerType.TRAVELER)

      println(s"Low quality - DealPreference ratio: $dealRatio, AppealPreference(BRAND) ratio: $appealRatio")
      // Appeal/Brand should be more penalized by low quality
      appealRatio should be > dealRatio
    }

    "show ELITE pax benefit MORE from high quality (lower ratio = more discount)".in {
      val highQualityLink = createLink(quality = 80)
      val preference = AppealPreference(fromAirport, ECONOMY, 1.0, 0, 1.0, 1)

      val travelerRatio = preference.qualityAdjustRatio(fromAirport, highQualityLink, ECONOMY, PassengerType.TRAVELER)
      val eliteRatio = preference.qualityAdjustRatio(fromAirport, highQualityLink, ECONOMY, PassengerType.ELITE)

      println(s"High quality link - TRAVELER ratio: $travelerRatio, ELITE ratio: $eliteRatio")
      // ELITE has higher good quality delta threshold (30 vs 20), so at quality 80 they get MORE discount
      eliteRatio should be < travelerRatio
    }
  }

  "frequencyAdjustRatio".must {
    "favor high frequency links".in {
      val lowFreqLink = createLink(frequency = 3)
      val highFreqLink = createLink(frequency = 21)
      val preference = AppealPreference(fromAirport, ECONOMY, 1.0, 0, 1.0, 1)

      // Average over samples due to randomness
      var lowTotal, highTotal = 0.0
      val samples = 1000
      for (_ <- 0 until samples) {
        lowTotal += preference.frequencyAdjustRatio(lowFreqLink, ECONOMY, PassengerType.BUSINESS)
        highTotal += preference.frequencyAdjustRatio(highFreqLink, ECONOMY, PassengerType.BUSINESS)
      }
      val lowRatio = lowTotal / samples
      val highRatio = highTotal / samples

      println(s"BUSINESS pax avg - low freq (3): $lowRatio, high freq (21): $highRatio")
      highRatio should be < lowRatio
    }

    "show BUSINESS pax are most frequency-sensitive (0.6)".in {
      val lowFreqLink = createLink(frequency = 2)  // Very low frequency to ensure penalty
      val preference = AppealPreference(fromAirport, ECONOMY, 1.0, 0, 1.0, 1)

      // Average over many samples due to randomness in frequencyThresholdperPax
      var businessTotal, travelerTotal, touristTotal = 0.0
      val samples = 1000
      for (_ <- 0 until samples) {
        businessTotal += preference.frequencyAdjustRatio(lowFreqLink, ECONOMY, PassengerType.BUSINESS)
        travelerTotal += preference.frequencyAdjustRatio(lowFreqLink, ECONOMY, PassengerType.TRAVELER)
        touristTotal += preference.frequencyAdjustRatio(lowFreqLink, ECONOMY, PassengerType.TOURIST)
      }
      val businessRatio = businessTotal / samples
      val travelerRatio = travelerTotal / samples
      val touristRatio = touristTotal / samples

      println(s"Low freq (2) avg - BUSINESS: $businessRatio, TRAVELER: $travelerRatio, TOURIST: $touristRatio")
      // Business has frequencySensitivity=0.6, Traveler=0.2, Tourist=0.15
      // On average, business should be most penalized
      businessRatio should be > travelerRatio
    }

    "show short flights care more about frequency (distanceModifier)".in {
      // Create short flight airport pair
      val nearbyToAirport = Airport("", "", "Nearby", 0, 2, "", "", "", 1, baseIncome = 40000, basePopulation = 1000000, 0, 0)
      nearbyToAirport.initAirlineAppeals(scala.collection.immutable.Map.empty)
      nearbyToAirport.initLounges(scala.collection.immutable.List.empty)

      val shortDistance = 200
      val shortDuration = Computation.calculateDuration(model, shortDistance)
      val shortPrice = Pricing.computeStandardPriceForAllClass(shortDistance, FlightCategory.DOMESTIC, PassengerType.TRAVELER, fromAirport.baseIncome)

      val shortLink = Link(fromAirport, nearbyToAirport, testAirline, shortPrice, distance = shortDistance, defaultCapacity, rawQuality = 50, shortDuration, 3)
      shortLink.setQuality(50)
      shortLink.setTestingAssignedAirplanes(scala.collection.immutable.Map(Airplane(model, testAirline, 0, purchasedCycle = 0, 100, 0) -> 1))

      val longDistance = 8000
      val longDuration = Computation.calculateDuration(model, longDistance)
      val longPrice = Pricing.computeStandardPriceForAllClass(longDistance, FlightCategory.DOMESTIC, PassengerType.TRAVELER, fromAirport.baseIncome)

      val longLink = Link(fromAirport, nearbyToAirport, testAirline, longPrice, distance = longDistance, defaultCapacity, rawQuality = 50, longDuration, 3)
      longLink.setQuality(50)
      longLink.setTestingAssignedAirplanes(scala.collection.immutable.Map(Airplane(model, testAirline, 0, purchasedCycle = 0, 100, 0) -> 1))

      val preference = AppealPreference(fromAirport, ECONOMY, 1.0, 0, 1.0, 1)

      // Average over many samples due to randomness
      var shortTotal, longTotal = 0.0
      val samples = 2000
      for (_ <- 0 until samples) {
        shortTotal += preference.frequencyAdjustRatio(shortLink, ECONOMY, PassengerType.BUSINESS)
        longTotal += preference.frequencyAdjustRatio(longLink, ECONOMY, PassengerType.BUSINESS)
      }
      val shortRatio = shortTotal / samples
      val longRatio = longTotal / samples

      println(s"Low freq (3) avg - Short flight ratio: $shortRatio, Long flight ratio: $longRatio")
      // Short flights should be more affected by low frequency (larger delta from 1.0)
      Math.abs(shortRatio - 1.0) should be > Math.abs(longRatio - 1.0)
    }
  }

  "tripDurationAdjustRatio".must {
    "favor faster planes".in {
      val slowModel = Model.modelByName("Cessna 208 Caravan")
      val fastModel = Model.modelByName("Boeing 747SP")

      val slowDuration = Computation.calculateDuration(slowModel, distance)
      val fastDuration = Computation.calculateDuration(fastModel, distance)

      val slowLink = createLink(linkDuration = slowDuration)
      val fastLink = createLink(linkDuration = fastDuration)

      val preference = DealPreference(fromAirport, ECONOMY, 1.0)

      val slowRatio = preference.tripDurationAdjustRatio(slowLink, ECONOMY, PassengerType.BUSINESS)
      val fastRatio = preference.tripDurationAdjustRatio(fastLink, ECONOMY, PassengerType.BUSINESS)

      println(s"BUSINESS pax - Slow plane ratio: $slowRatio, Fast plane ratio: $fastRatio")
      fastRatio should be < slowRatio
    }

    "show ELITE pax are most duration-sensitive (0.7)".in {
      val slowModel = Model.modelByName("Cessna 208 Caravan")
      val slowDuration = Computation.calculateDuration(slowModel, distance)
      val slowLink = createLink(linkDuration = slowDuration)

      val preference = DealPreference(fromAirport, ECONOMY, 1.0)

      val eliteRatio = preference.tripDurationAdjustRatio(slowLink, ECONOMY, PassengerType.ELITE)
      val businessRatio = preference.tripDurationAdjustRatio(slowLink, ECONOMY, PassengerType.BUSINESS)
      val travelerRatio = preference.tripDurationAdjustRatio(slowLink, ECONOMY, PassengerType.TRAVELER)

      println(s"Slow plane - ELITE: $eliteRatio, BUSINESS: $businessRatio, TRAVELER: $travelerRatio")
      eliteRatio should be > businessRatio
    }

    "show FIRST class passengers more duration-sensitive than ECONOMY".in {
      val slowModel = Model.modelByName("Cessna 208 Caravan")
      val slowDuration = Computation.calculateDuration(slowModel, distance)
      val slowLink = createLink(linkDuration = slowDuration)

      val econPref = DealPreference(fromAirport, ECONOMY, 1.0)
      val firstPref = DealPreference(fromAirport, FIRST, 1.0)

      val econRatio = econPref.tripDurationAdjustRatio(slowLink, ECONOMY, PassengerType.BUSINESS)
      val firstRatio = firstPref.tripDurationAdjustRatio(slowLink, FIRST, PassengerType.BUSINESS)

      println(s"Slow plane BUSINESS pax - ECONOMY class: $econRatio, FIRST class: $firstRatio")
      firstRatio should be > econRatio
    }

    "cap penalty at 2x cost".in {
      // Create extremely slow flight
      val verySlowLink = createLink(linkDuration = duration * 5)
      val preference = DealPreference(fromAirport, FIRST, 1.0)

      val ratio = preference.tripDurationAdjustRatio(verySlowLink, FIRST, PassengerType.ELITE)
      ratio should be <= 1.6
      println(s"Extremely slow flight - ELITE FIRST ratio: $ratio (capped at 1.6)")
    }
  }

  "loyaltyAdjustRatio".must {
    "affect FREQUENT preference more (loyaltyRatio > 1)".in {
      fromAirport.initAirlineAppeals(scala.collection.immutable.Map(testAirline.id -> AirlineAppeal(80)))
      val link = createLink()

      val brandPref = AppealPreference(fromAirport, ECONOMY, 1.0, 0, 1.0, 1)  // BRAND: loyaltyRatio <= 1
      val frequentPref = AppealPreference(fromAirport, ECONOMY, 1.0, 0, 2.0, 2)  // FREQUENT: loyaltyRatio > 1

      val brandRatio = brandPref.loyaltyAdjustRatio(link)
      val frequentRatio = frequentPref.loyaltyAdjustRatio(link)

      println(s"Loyalty 80 - BRAND ratio: $brandRatio, FREQUENT ratio: $frequentRatio")
      frequentRatio should be < brandRatio  // FREQUENT benefits more from high loyalty
    }

    "reduce cost for high loyalty airlines".in {
      fromAirport.initAirlineAppeals(scala.collection.immutable.Map(testAirline.id -> AirlineAppeal(100)))
      val link = createLink()

      val frequentPref = AppealPreference(fromAirport, ECONOMY, 1.0, 0, 2.0, 1)
      val ratio = frequentPref.loyaltyAdjustRatio(link)

      println(s"FREQUENT preference at max loyalty (100): $ratio")
      ratio should be < 1.0
    }

    "increase cost for zero loyalty airlines".in {
      fromAirport.initAirlineAppeals(scala.collection.immutable.Map(testAirline.id -> AirlineAppeal(0)))
      val link = createLink()

      val frequentPref = AppealPreference(fromAirport, ECONOMY, 1.0, 0, 2.0, 1)
      val ratio = frequentPref.loyaltyAdjustRatio(link)

      println(s"FREQUENT preference at zero loyalty: $ratio")
      ratio should be > 1.0
    }
  }

  "connectionCostRatio".must {
    "show DEAL preference favors connections (0.6)".in {
      val dealPref = DealPreference(fromAirport, ECONOMY, 1.0)
      dealPref.connectionCostRatio shouldBe 0.6
    }

    "show FREQUENT preference dislikes connections (1.6)".in {
      val frequentPref = AppealPreference(fromAirport, ECONOMY, 1.0, 0, 2.0, 1)
      frequentPref.connectionCostRatio shouldBe 1.6
    }

    "show BRAND preference moderately dislikes connections (1.2)".in {
      val brandPref = AppealPreference(fromAirport, ECONOMY, 1.0, 0, 0.5, 1)
      brandPref.connectionCostRatio shouldBe 1.2
    }

    "show LAST_MINUTE_DEAL prefers connections (0.2)".in {
      val lastMinuteDealPref = LastMinutePreference(fromAirport, ECONOMY, 0.8, 0)  // priceModifier < 1
      lastMinuteDealPref.connectionCostRatio shouldBe 0.2
    }

    "show LAST_MINUTE urgent dislikes connections (1.0)".in {
      val lastMinutePref = LastMinutePreference(fromAirport, ECONOMY, 1.2, 0)  // priceModifier >= 1
      lastMinutePref.connectionCostRatio shouldBe 1.0
    }
  }

  "Combined cost computation".must {
    "show price is the dominant factor for DEAL preference".in {
      fromAirport.initAirlineAppeals(scala.collection.immutable.Map(testAirline.id -> AirlineAppeal(50)))

      val baseLink = createLink()
      val cheapLink = createLink(price = defaultPrice * 0.7)
      val qualityLink = createLink(quality = 100)

      val preference = DealPreference(fromAirport, ECONOMY, 1.0)

      // Get average costs (deal preference has noise)
      var baseCost, cheapCost, qualityCost = 0.0
      val iterations = 1000
      for (_ <- 0 until iterations) {
        baseCost += preference.computeCost(baseLink, ECONOMY, PassengerType.TRAVELER)
        cheapCost += preference.computeCost(cheapLink, ECONOMY, PassengerType.TRAVELER)
        qualityCost += preference.computeCost(qualityLink, ECONOMY, PassengerType.TRAVELER)
      }
      baseCost /= iterations
      cheapCost /= iterations
      qualityCost /= iterations

      val priceImpact = (baseCost - cheapCost) / baseCost
      val qualityImpact = (baseCost - qualityCost) / baseCost

      println(s"DEAL ECONOMY - Base: $baseCost, Cheap: $cheapCost, High Quality: $qualityCost")
      println(s"Price impact: ${priceImpact * 100}%, Quality impact: ${qualityImpact * 100}%")

      // For deal seekers, price should have bigger impact than quality
      priceImpact should be > qualityImpact
    }

    "show quality matters more for BRAND preference".in {
      fromAirport.initAirlineAppeals(scala.collection.immutable.Map(testAirline.id -> AirlineAppeal(50)))

      val baseLink = createLink(quality = 50)
      val highQualityLink = createLink(quality = 90)

      val dealPref = DealPreference(fromAirport, ECONOMY, 1.0)
      val brandPref = AppealPreference(fromAirport, ECONOMY, 1.0, 0, 0.5, 1)  // BRAND

      var dealBase, dealQuality, brandBase, brandQuality = 0.0
      val iterations = 1000
      for (_ <- 0 until iterations) {
        dealBase += dealPref.computeCost(baseLink, ECONOMY, PassengerType.TRAVELER)
        dealQuality += dealPref.computeCost(highQualityLink, ECONOMY, PassengerType.TRAVELER)
        brandBase += brandPref.computeCost(baseLink, ECONOMY, PassengerType.TRAVELER)
        brandQuality += brandPref.computeCost(highQualityLink, ECONOMY, PassengerType.TRAVELER)
      }
      dealBase /= iterations
      dealQuality /= iterations
      brandBase /= iterations
      brandQuality /= iterations

      val dealQualityImpact = (dealBase - dealQuality) / dealBase
      val brandQualityImpact = (brandBase - brandQuality) / brandBase

      println(s"Quality impact - DEAL: ${dealQualityImpact * 100}%, BRAND: ${brandQualityImpact * 100}%")

      brandQualityImpact should be > dealQualityImpact
    }

    "show loyalty is key lever for FREQUENT preference".in {
      val lowLoyaltyAirport = fromAirport.copy()
      lowLoyaltyAirport.initAirlineAppeals(scala.collection.immutable.Map(testAirline.id -> AirlineAppeal(10)))
      lowLoyaltyAirport.initLounges(scala.collection.immutable.List.empty)

      val highLoyaltyAirport = fromAirport.copy()
      highLoyaltyAirport.initAirlineAppeals(scala.collection.immutable.Map(testAirline.id -> AirlineAppeal(90)))
      highLoyaltyAirport.initLounges(scala.collection.immutable.List.empty)

      val link = createLink()

      val lowLoyaltyPref = AppealPreference(lowLoyaltyAirport, ECONOMY, 1.0, 0, 2.0, 1)  // FREQUENT
      val highLoyaltyPref = AppealPreference(highLoyaltyAirport, ECONOMY, 1.0, 0, 2.0, 2)  // FREQUENT

      var lowCost, highCost = 0.0
      val iterations = 1000
      for (_ <- 0 until iterations) {
        lowCost += lowLoyaltyPref.computeCost(link, ECONOMY, PassengerType.TRAVELER)
        highCost += highLoyaltyPref.computeCost(link, ECONOMY, PassengerType.TRAVELER)
      }
      lowCost /= iterations
      highCost /= iterations

      val loyaltyImpact = (lowCost - highCost) / lowCost

      println(s"FREQUENT - Low loyalty cost: $lowCost, High loyalty cost: $highCost")
      println(s"Loyalty impact: ${loyaltyImpact * 100}%")

      loyaltyImpact should be > 0.1  // At least 10% difference
    }
  }

  "Lever impact summary by passenger type".must {
    "calculate relative impacts for all pax types".in {
      // Setup airports with low and high loyalty
      val lowLoyaltyAirport = fromAirport.copy()
      lowLoyaltyAirport.initAirlineAppeals(scala.collection.immutable.Map(testAirline.id -> AirlineAppeal(10)))
      lowLoyaltyAirport.initLounges(scala.collection.immutable.List.empty)

      val highLoyaltyAirport = fromAirport.copy()
      highLoyaltyAirport.initAirlineAppeals(scala.collection.immutable.Map(testAirline.id -> AirlineAppeal(90)))
      highLoyaltyAirport.initLounges(scala.collection.immutable.List.empty)

      fromAirport.initAirlineAppeals(scala.collection.immutable.Map(testAirline.id -> AirlineAppeal(50)))

      val baseLink = createLink()
      val cheapLink = createLink(price = defaultPrice * 0.7)
      val highQualityLink = createLink(quality = 20)
      val lowFreqLink = createLink(frequency = 3)
      val highFreqLink = createLink(frequency = 28)

      val paxTypes = List(
        PassengerType.TRAVELER,
        PassengerType.BUSINESS,
        PassengerType.ELITE,
        PassengerType.TOURIST
      )

      println("\n=== LEVER IMPACT BY PASSENGER TYPE (ECONOMY class) ===")
      println("%-15s | %-10s | %-10s | %-10s | %-10s".format("Passenger Type", "Price -30%", "Quality +20", "Freq +3", "Freq +28", "Loyalty +40"))
      println("-" * 70)

      for (paxType <- paxTypes) {
        val preference = AppealPreference(fromAirport, ECONOMY, 1.0, 0, 1.0, 1)
        val lowLoyaltyPref = AppealPreference(lowLoyaltyAirport, ECONOMY, 1.0, 0, 2.0, 1)  // FREQUENT to see loyalty effect
        val highLoyaltyPref = AppealPreference(highLoyaltyAirport, ECONOMY, 1.0, 0, 2.0, 2)

        var baseCost, priceCost, qualityCost, lowFreqCost, highFreqCost, lowLoyaltyCost, highLoyaltyCost = 0.0
        val iterations = 1000
        for (_ <- 0 until iterations) {
          baseCost += preference.computeCost(baseLink, ECONOMY, paxType)
          priceCost += preference.computeCost(cheapLink, ECONOMY, paxType)
          qualityCost += preference.computeCost(highQualityLink, ECONOMY, paxType)
          lowFreqCost += preference.computeCost(lowFreqLink, ECONOMY, paxType)
          highFreqCost += preference.computeCost(highFreqLink, ECONOMY, paxType)
          lowLoyaltyCost += lowLoyaltyPref.computeCost(baseLink, ECONOMY, paxType)
          highLoyaltyCost += highLoyaltyPref.computeCost(baseLink, ECONOMY, paxType)
        }
        baseCost /= iterations
        priceCost /= iterations
        qualityCost /= iterations
        lowFreqCost /= iterations
        highFreqCost /= iterations
        lowLoyaltyCost /= iterations
        highLoyaltyCost /= iterations

        val priceImpact = (baseCost - priceCost) / baseCost * 100
        val qualityImpact = (baseCost - qualityCost) / baseCost * 100
        val lowFreqImpact = (baseCost - lowFreqCost) / baseCost * 100
        val highFreqImpact = (baseCost - highFreqCost) / baseCost * 100
        val loyaltyImpact = (lowLoyaltyCost - highLoyaltyCost) / lowLoyaltyCost * 100

        println("%-15s | %8.1f%% | %8.1f%% | %8.1f%% | %8.1f%%".format(
          PassengerType.label(paxType),
          priceImpact,
          qualityImpact,
          lowFreqImpact,
          highFreqImpact,
          loyaltyImpact
        ))
      }

      // Lounge note
      println("\n(Lounge only affects BUSINESS/FIRST class - see class table below)")

      true shouldBe true
    }

    "calculate relative impacts for all link classes".in {
      // Setup airports with and without lounges
      val noLoungeAirport = fromAirport.copy()
      noLoungeAirport.initAirlineAppeals(scala.collection.immutable.Map(testAirline.id -> AirlineAppeal(50)))
      noLoungeAirport.initLounges(scala.collection.immutable.List.empty)

      val loungeFromAirport = fromAirport.copy()
      loungeFromAirport.initAirlineAppeals(scala.collection.immutable.Map(testAirline.id -> AirlineAppeal(50)))
      loungeFromAirport.initLounges(scala.collection.immutable.List(
        Lounge(testAirline, allianceId = None, loungeFromAirport, level = 3, status = LoungeStatus.ACTIVE, foundedCycle = 0)
      ))

      val loungeToAirport = toAirport.copy()
      loungeToAirport.initAirlineAppeals(scala.collection.immutable.Map.empty)
      loungeToAirport.initLounges(scala.collection.immutable.List(
        Lounge(testAirline, allianceId = None, loungeToAirport, level = 3, status = LoungeStatus.ACTIVE, foundedCycle = 0)
      ))

      // Setup airports with low and high loyalty
      val lowLoyaltyAirport = fromAirport.copy()
      lowLoyaltyAirport.initAirlineAppeals(scala.collection.immutable.Map(testAirline.id -> AirlineAppeal(10)))
      lowLoyaltyAirport.initLounges(scala.collection.immutable.List.empty)

      val highLoyaltyAirport = fromAirport.copy()
      highLoyaltyAirport.initAirlineAppeals(scala.collection.immutable.Map(testAirline.id -> AirlineAppeal(90)))
      highLoyaltyAirport.initLounges(scala.collection.immutable.List.empty)

      fromAirport.initAirlineAppeals(scala.collection.immutable.Map(testAirline.id -> AirlineAppeal(50)))

      val classes = List(ECONOMY, BUSINESS, FIRST)

      println("\n=== LEVER IMPACT BY LINK CLASS (TRAVELER pax) ===")
      println("%-8s | %-9s | %-9s | %-9s | %-9s | %-9s".format("Class", "Price-30%", "Qual+40", "Freq+14", "Loyal+80", "Lounge L3"))
      println("-" * 75)

      for (linkClass <- classes) {
        val classPrice = Pricing.computeStandardPriceForAllClass(distance, FlightCategory.DOMESTIC, PassengerType.TRAVELER, fromAirport.baseIncome)
        val baseLink = createLink(price = classPrice)
        val cheapLink = createLink(price = classPrice * 0.7)
        val highQualityLink = createLink(quality = 90, price = classPrice)
        val highFreqLink = createLink(frequency = 28, price = classPrice)

        // Create lounge link
        val loungeLink = Link(loungeFromAirport, loungeToAirport, testAirline, classPrice, distance = distance, defaultCapacity, rawQuality = 50, duration, 14)
        loungeLink.setQuality(50)
        loungeLink.setTestingAssignedAirplanes(scala.collection.immutable.Map(Airplane(model, testAirline, 0, purchasedCycle = 0, 100, 0) -> 1))

        val preference = AppealPreference(noLoungeAirport, linkClass, 1.0, 0, 1.0, 1)
        val loungePreference = AppealPreference(loungeFromAirport, linkClass, 1.0, 0, 1.0, 3)
        val lowLoyaltyPref = AppealPreference(lowLoyaltyAirport, linkClass, 1.0, 0, 2.0, 4)
        val highLoyaltyPref = AppealPreference(highLoyaltyAirport, linkClass, 1.0, 0, 2.0, 5)

        var baseCost, priceCost, qualityCost, freqCost, noLoungeCost, loungeCost, lowLoyaltyCost, highLoyaltyCost = 0.0
        val iterations = 1000
        for (_ <- 0 until iterations) {
          baseCost += preference.computeCost(baseLink, linkClass, PassengerType.TRAVELER)
          priceCost += preference.computeCost(cheapLink, linkClass, PassengerType.TRAVELER)
          qualityCost += preference.computeCost(highQualityLink, linkClass, PassengerType.TRAVELER)
          freqCost += preference.computeCost(highFreqLink, linkClass, PassengerType.TRAVELER)
          noLoungeCost += preference.computeCost(baseLink, linkClass, PassengerType.TRAVELER)
          loungeCost += loungePreference.computeCost(loungeLink, linkClass, PassengerType.TRAVELER)
          lowLoyaltyCost += lowLoyaltyPref.computeCost(baseLink, linkClass, PassengerType.TRAVELER)
          highLoyaltyCost += highLoyaltyPref.computeCost(baseLink, linkClass, PassengerType.TRAVELER)
        }
        baseCost /= iterations
        priceCost /= iterations
        qualityCost /= iterations
        freqCost /= iterations
        noLoungeCost /= iterations
        loungeCost /= iterations
        lowLoyaltyCost /= iterations
        highLoyaltyCost /= iterations

        val priceImpact = (baseCost - priceCost) / baseCost * 100
        val qualityImpact = (baseCost - qualityCost) / baseCost * 100
        val freqImpact = (baseCost - freqCost) / baseCost * 100
        val loyaltyImpact = (lowLoyaltyCost - highLoyaltyCost) / lowLoyaltyCost * 100
        val loungeImpact = (noLoungeCost - loungeCost) / noLoungeCost * 100

        println("%-8s | %7.1f%% | %7.1f%% | %7.1f%% | %7.1f%% | %7.1f%%".format(
          linkClass.code,
          priceImpact,
          qualityImpact,
          freqImpact,
          loyaltyImpact,
          loungeImpact
        ))
      }

      true shouldBe true
    }

    "calculate relative impacts for all preference types".in {
      // Setup airports with low and high loyalty
      val lowLoyaltyAirport = fromAirport.copy()
      lowLoyaltyAirport.initAirlineAppeals(scala.collection.immutable.Map(testAirline.id -> AirlineAppeal(10)))
      lowLoyaltyAirport.initLounges(scala.collection.immutable.List.empty)

      val highLoyaltyAirport = fromAirport.copy()
      highLoyaltyAirport.initAirlineAppeals(scala.collection.immutable.Map(testAirline.id -> AirlineAppeal(90)))
      highLoyaltyAirport.initLounges(scala.collection.immutable.List.empty)

      fromAirport.initAirlineAppeals(scala.collection.immutable.Map(testAirline.id -> AirlineAppeal(50)))

      val baseLink = createLink()
      val cheapLink = createLink(price = defaultPrice * 0.7)
      val highQualityLink = createLink(quality = 90)
      val highFreqLink = createLink(frequency = 28)

      // For each preference type, we need low/high loyalty versions to measure loyalty impact
      // loyaltyRatio determines if loyalty matters: >1 means FREQUENT (high sensitivity), <=1 means others
      val preferenceSpecs = List(
        ("DEAL", (ap: Airport) => DealPreference(ap, ECONOMY, 1.0), 0.0),  // loyaltySensitivity = 0
        ("BRAND", (ap: Airport) => AppealPreference(ap, ECONOMY, 1.0, 0, 0.5, 1), 0.5),  // has some sensitivity
        ("FREQUENT", (ap: Airport) => AppealPreference(ap, ECONOMY, 1.0, 0, 2.0, 2), 2.0),  // high sensitivity
        ("LAST_MINUTE", (ap: Airport) => LastMinutePreference(ap, ECONOMY, 1.2, 0), 0.0),
        ("LAST_MIN_DEAL", (ap: Airport) => LastMinutePreference(ap, ECONOMY, 0.8, 0), 0.0)
      )

      println("\n=== LEVER IMPACT BY PREFERENCE TYPE (ECONOMY class, TRAVELER pax) ===")
      println("%-13s | %-9s | %-9s | %-9s | %-9s | %-6s".format("Preference", "Price-30%", "Qual+40", "Freq+14", "Loyal+80", "ConnRatio"))
      println("-" * 75)

      for ((name, prefFactory, loyaltyRatio) <- preferenceSpecs) {
        val preference = prefFactory(fromAirport)
        val lowLoyaltyPref = prefFactory(lowLoyaltyAirport)
        val highLoyaltyPref = prefFactory(highLoyaltyAirport)

        var baseCost, priceCost, qualityCost, freqCost, lowLoyaltyCost, highLoyaltyCost = 0.0
        val iterations = 1000
        for (_ <- 0 until iterations) {
          baseCost += preference.computeCost(baseLink, ECONOMY, PassengerType.TRAVELER)
          priceCost += preference.computeCost(cheapLink, ECONOMY, PassengerType.TRAVELER)
          qualityCost += preference.computeCost(highQualityLink, ECONOMY, PassengerType.TRAVELER)
          freqCost += preference.computeCost(highFreqLink, ECONOMY, PassengerType.TRAVELER)
          lowLoyaltyCost += lowLoyaltyPref.computeCost(baseLink, ECONOMY, PassengerType.TRAVELER)
          highLoyaltyCost += highLoyaltyPref.computeCost(baseLink, ECONOMY, PassengerType.TRAVELER)
        }
        baseCost /= iterations
        priceCost /= iterations
        qualityCost /= iterations
        freqCost /= iterations
        lowLoyaltyCost /= iterations
        highLoyaltyCost /= iterations

        val priceImpact = (baseCost - priceCost) / baseCost * 100
        val qualityImpact = (baseCost - qualityCost) / baseCost * 100
        val freqImpact = (baseCost - freqCost) / baseCost * 100
        val loyaltyImpact = if (lowLoyaltyCost > 0) (lowLoyaltyCost - highLoyaltyCost) / lowLoyaltyCost * 100 else 0.0

        println("%-13s | %7.1f%% | %7.1f%% | %7.1f%% | %7.1f%% | %5.1fx".format(
          name,
          priceImpact,
          qualityImpact,
          freqImpact,
          loyaltyImpact,
          preference.connectionCostRatio
        ))
      }

      println("\n(Lounge only affects BUSINESS/FIRST - see link class table)")

      true shouldBe true
    }
  }

  "investigation findings".must {
    "show BRAND preference is sensitive to loyalty" in {
      val lowLoyaltyAirport = fromAirport.copy()
      lowLoyaltyAirport.initAirlineAppeals(scala.collection.immutable.Map(testAirline.id -> AirlineAppeal(0)))
      lowLoyaltyAirport.initLounges(scala.collection.immutable.List.empty)

      val highLoyaltyAirport = fromAirport.copy()
      highLoyaltyAirport.initAirlineAppeals(scala.collection.immutable.Map(testAirline.id -> AirlineAppeal(100)))
      highLoyaltyAirport.initLounges(scala.collection.immutable.List.empty)

      val brandPrefLow = AppealPreference(lowLoyaltyAirport, ECONOMY, 1.0, 0, 1.0, 1)
      val brandPrefHigh = AppealPreference(highLoyaltyAirport, ECONOMY, 1.0, 0, 1.0, 2)

      val link = createLink()

      val ratioLow = brandPrefLow.loyaltyAdjustRatio(link)
      val ratioHigh = brandPrefHigh.loyaltyAdjustRatio(link)

      println(s"BRAND ratio at 0 loyalty: $ratioLow")
      println(s"BRAND ratio at 100 loyalty: $ratioHigh")

      ratioHigh should be < ratioLow
      ratioHigh should be < 1.0
      ratioLow should be > 1.0
    }

    "show loungeAdjust works correctly for both ends and is more impactful on short flights" in {
      val airline = testAirline
      val l3 = Lounge(airline, None, fromAirport, "L3", level = 3, LoungeStatus.ACTIVE, 0)

      val airportWithLounge = Airport("A1", "A1", "A1", 0, 0, "", "", "", 1, baseIncome = 40000, basePopulation = 1000000, 0, 0)
      airportWithLounge.initLounges(List(l3))

      val airportWithoutLounge = Airport("A2", "A2", "A2", 0, 0, "", "", "", 1, baseIncome = 40000, basePopulation = 1000000, 0, 0)
      airportWithoutLounge.initLounges(List.empty)

      val preference = DealPreference(airportWithLounge, BUSINESS, 1.0)

      // Case 1: Lounge at FROM only
      val link1 = Link(airportWithLounge, airportWithoutLounge, airline, defaultPrice, distance = 1000, defaultCapacity, rawQuality = 50, duration, 14)
      val cost1 = preference.loungeAdjust(1000.0, link1, 0, BUSINESS)

      // Case 2: Lounge at TO only
      val link2 = Link(airportWithoutLounge, airportWithLounge, airline, defaultPrice, distance = 1000, defaultCapacity, rawQuality = 50, duration, 14)
      val cost2 = preference.loungeAdjust(1000.0, link2, 0, BUSINESS)

      // Case 3: Lounge at BOTH
      val link3 = Link(airportWithLounge, airportWithLounge, airline, defaultPrice, distance = 1000, defaultCapacity, rawQuality = 50, duration, 14)
      val cost3 = preference.loungeAdjust(1000.0, link3, 0, BUSINESS)

      println(s"Lounge at FROM only: $cost1")
      println(s"Lounge at TO only: $cost2")
      println(s"Lounge at BOTH: $cost3")

      cost1 shouldBe 880.0
      cost2 shouldBe 880.0
      cost3 shouldBe 769.0

      // Short vs Long flight impact
      val shortCost = 200.0
      val longCost = 2000.0

      val adjustedShort = preference.loungeAdjust(shortCost, link2, 0, BUSINESS)
      val adjustedLong = preference.loungeAdjust(longCost, link2, 0, BUSINESS)

      val shortReduction = (shortCost - adjustedShort) / shortCost
      val longReduction = (longCost - adjustedLong) / longCost

      println(s"Short flight (cost 200) lounge reduction: ${shortReduction * 100}%")
      println(s"Long flight (cost 2000) lounge reduction: ${longReduction * 100}%")

      shortReduction should be > longReduction
    }
  }
}
