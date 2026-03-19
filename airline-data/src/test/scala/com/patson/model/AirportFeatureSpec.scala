package com.patson.model

import com.patson.data.{AirportFeatureRegistry, AirportSource}
import com.patson.util.AirportCache
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class AirportFeatureSpec extends AnyFunSuite with Matchers {

  private def makeAirport(iata: String, id: Int, countryCode: String, affinity: Int = 5, popMiddleIncome: Int = 5_000_000): Airport = {
    val a = Airport(iata, "", s"Airport $iata", 0, 0, countryCode, "", s"zone_$countryCode", 6, 40000, 5_000_000, basePopMiddleIncome = popMiddleIncome, id = id)
    a.initFeatures(List.empty)
    a.initAirlineBases(List.empty)
    a.initAirlineAppeals(Map.empty)
    a
  }

  // ── DomesticAirportFeature ────────────────────────────────────────────────

  test("DomesticAirportFeature: domestic affinity (>= 5) adds demand") {
    val feature = DomesticAirportFeature()
    val from = makeAirport("AAA", 1, "US")
    val to   = makeAirport("BBB", 2, "US")
    val adj = feature.demandAdjustment(100.0, PassengerType.TRAVELER, airportId = 2, from, to, affinity = 5, distance = 500)
    adj should be > 0
  }

  test("DomesticAirportFeature: international affinity (< 5) subtracts demand") {
    val feature = DomesticAirportFeature()
    val from = makeAirport("AAA", 1, "US")
    val to   = makeAirport("BBB", 2, "GB")
    val adj = feature.demandAdjustment(100.0, PassengerType.TRAVELER, airportId = 2, from, to, affinity = 2, distance = 5000)
    adj should be < 0
  }

  // ── VacationHubFeature ────────────────────────────────────────────────────

  test("VacationHubFeature: returns 0 for non-TOURIST passenger type") {
    val feature = VacationHubFeature(80)
    val from = makeAirport("NYC", 1, "US")
    val to   = makeAirport("MCO", 2, "US")
    feature.demandAdjustment(100.0, PassengerType.BUSINESS, airportId = 2, from, to, affinity = 5, distance = 1000) shouldBe 0
    feature.demandAdjustment(100.0, PassengerType.TRAVELER, airportId = 2, from, to, affinity = 5, distance = 1000) shouldBe 0
  }

  test("VacationHubFeature: returns 0 when destination airport is the origin, not the hub") {
    val feature = VacationHubFeature(80)
    val from = makeAirport("MCO", 1, "US")
    val to   = makeAirport("NYC", 2, "US")
    // airportId = 1 (from), so toAirport.id (2) != airportId → 0
    feature.demandAdjustment(100.0, PassengerType.TOURIST, airportId = 1, from, to, affinity = 5, distance = 1000) shouldBe 0
  }

  test("VacationHubFeature: returns positive demand for TOURIST traveling to hub") {
    val feature = VacationHubFeature(80)
    val from = makeAirport("NYC", 1, "US", popMiddleIncome = 10_000_000)
    val to   = makeAirport("MCO", 2, "US")
    val adj = feature.demandAdjustment(100.0, PassengerType.TOURIST, airportId = 2, from, to, affinity = 5, distance = 1000)
    adj should be > 0
  }

  test("VacationHubFeature: higher strength produces higher demand") {
    val low  = VacationHubFeature(30)
    val high = VacationHubFeature(90)
    val from = makeAirport("NYC", 1, "US", popMiddleIncome = 10_000_000)
    val to   = makeAirport("MCO", 2, "US")
    val adjLow  = low.demandAdjustment(100.0, PassengerType.TOURIST, airportId = 2, from, to, affinity = 5, distance = 1000)
    val adjHigh = high.demandAdjustment(100.0, PassengerType.TOURIST, airportId = 2, from, to, affinity = 5, distance = 1000)
    adjHigh should be > adjLow
  }

  // ── GatewayAirportFeature ─────────────────────────────────────────────────

  test("GatewayAirportFeature: returns 0 when neither airport is a gateway") {
    val feature = GatewayAirportFeature()
    val from = makeAirport("AAA", 1, "US", popMiddleIncome = 5_000_000)
    val to   = makeAirport("BBB", 2, "US")
    feature.demandAdjustment(100.0, PassengerType.TRAVELER, airportId = 2, from, to, affinity = 5, distance = 1000) shouldBe 0
  }

  test("GatewayAirportFeature: returns positive demand when both airports are gateways with sufficient popMiddleIncome") {
    val feature = GatewayAirportFeature()
    val from = makeAirport("AAA", 1, "US", popMiddleIncome = 10_000_000)
    val to   = makeAirport("BBB", 2, "GB", popMiddleIncome = 10_000_000)
    from.initFeatures(List(GatewayAirportFeature()))
    to.initFeatures(List(GatewayAirportFeature()))
    val adj = feature.demandAdjustment(100.0, PassengerType.TRAVELER, airportId = 2, from, to, affinity = 3, distance = 2500)
    adj should be > 0
  }

  // ── AirportFeatureRegistry (pure CSV, no DB) ──────────────────────────────

  test("AirportFeatureRegistry: MCO has a VACATION_HUB feature") {
    val features = AirportFeatureRegistry("MCO")
    features.exists(_.featureType == AirportFeatureType.VACATION_HUB) shouldBe true
  }

  test("AirportFeatureRegistry: MCO VACATION_HUB strength is > 100") {
    val features = AirportFeatureRegistry("MCO")
    val hub = features.find(_.featureType == AirportFeatureType.VACATION_HUB)
    hub shouldBe defined
    hub.get.strength should be > 100
  }

  test("AirportFeatureRegistry: unknown IATA returns empty list") {
    AirportFeatureRegistry("ZZZ") shouldBe empty
  }

  // ── AirportCache integration (requires DB) ────────────────────────────────

  test("AirportCache.getAllAirports: MCO has VACATION_HUB with strength > 100") {
    AirportCache.invalidateAll()
    val allAirports = AirportCache.getAllAirports(fullLoad = true)
    val mco = allAirports.find(_.iata == "MCO")
    val jfk = allAirports.find(_.iata == "JFK")
    val bet = allAirports.find(_.iata == "BET")
    mco shouldBe defined
    val vacation = mco.get.features.find(_.featureType == AirportFeatureType.VACATION_HUB)
    vacation shouldBe defined
    vacation.get.strength should be > 100
    var gateway = jfk.get.features.find(_.featureType == AirportFeatureType.FINANCIAL_HUB)
    gateway shouldBe defined
    gateway.get.strength should be > 40
    gateway = jfk.get.features.find(_.featureType == AirportFeatureType.GATEWAY_AIRPORT)
    gateway shouldBe defined
    var isolatedHub = bet.get.features.find(_.featureType == AirportFeatureType.ISOLATED_TOWN)
    isolatedHub shouldBe defined
    isolatedHub.get.strength should be > 3
    isolatedHub = bet.get.features.find(_.featureType == AirportFeatureType.BUSH_HUB)
    isolatedHub shouldBe defined
  }
}
