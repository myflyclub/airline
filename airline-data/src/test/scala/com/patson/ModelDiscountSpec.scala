package com.patson

import com.patson.model._
import com.patson.model.airplane._
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.matchers.should.Matchers

class ModelDiscountSpec extends AnyWordSpecLike with Matchers {

  // Lazy — model construction triggers AirplaneModelCache (DB), same as other specs in this project.
  // Tests that don't use models can run without a DB connection.
  lazy val smallModel: Model  = Model.models.find(_.category == Model.Category.SMALL).get
  lazy val largeModel: Model  = Model.models.find(_.category == Model.Category.LARGE).get
  lazy val mediumModel: Model = Model.models.find(_.category == Model.Category.MEDIUM).get

  private def callComputeLowDemandDiscounts(model: Model, totalOwnedCount: Int, multiplier: Double): List[ModelDiscount] = {
    val m = ModelDiscount.getClass.getDeclaredMethod(
      "computeLowDemandDiscounts", classOf[Model], classOf[Int], classOf[Double]
    )
    m.setAccessible(true)
    m.invoke(ModelDiscount, model, Int.box(totalOwnedCount), Double.box(multiplier))
      .asInstanceOf[List[ModelDiscount]]
  }

  private def callComputeManagerMultiplier(managers: List[Manager], currentCycle: Int): Double = {
    val m = ModelDiscount.getClass.getDeclaredMethod(
      "computeManagerMultiplier", classOf[List[_]], classOf[Int]
    )
    m.setAccessible(true)
    m.invoke(ModelDiscount, managers, Int.box(currentCycle)).asInstanceOf[Double]
  }

  private def makeManager(startCycle: Int, modelId: Int = 1): Manager =
    Manager(Airline("TestAirline"), AircraftModelManagerTask(startCycle, modelId, "TestModel"), availableCycle = None)

  private def priceDiscount(discounts: List[ModelDiscount])        = discounts.find(_.discountType == DiscountType.PRICE).get.discount
  private def constructionDiscount(discounts: List[ModelDiscount]) = discounts.find(_.discountType == DiscountType.CONSTRUCTION_TIME).get.discount

  // ── computeManagerMultiplier ─────────────────────────────────────────────
  // No DB required

  "computeManagerMultiplier" should {

    // LEVEL_CYCLE_THRESHOLDS = [4, 52, 148, 484] → level = number of thresholds crossed

    "return 0.0 with no managers" in {
      callComputeManagerMultiplier(List.empty, 100) shouldBe 0.0
    }

    "return 0.0 when the single manager hasn't yet reached level 1 (duration < 4)" in {
      // startCycle=100, currentCycle=102 → duration=2 → level 0
      callComputeManagerMultiplier(List(makeManager(100)), 102) shouldBe 0.0
    }

    "return 0.125 for one manager at level 1 (duration ≥4, <52)" in {
      callComputeManagerMultiplier(List(makeManager(0)), 10) shouldBe 0.125 +- 1e-6
    }

    "return 0.25 for one manager at level 2 (duration ≥52, <148)" in {
      callComputeManagerMultiplier(List(makeManager(0)), 60) shouldBe 0.25 +- 1e-6
    }

    "return 0.375 for one manager at level 3 (duration ≥148, <484)" in {
      callComputeManagerMultiplier(List(makeManager(0)), 200) shouldBe 0.375 +- 1e-6
    }

    "return 0.5 for one manager at level 4 (duration ≥484)" in {
      callComputeManagerMultiplier(List(makeManager(0)), 500) shouldBe 0.5 +- 1e-6
    }

    "sum levels across multiple managers before capping" in {
      // two level-2 managers → totalLevel=4 → min(1.0, 0.5) = 0.5
      val result = callComputeManagerMultiplier(List(makeManager(0), makeManager(0)), 60)
      result shouldBe 0.5 +- 1e-6
    }

    "cap at 1.0 when combined levels exceed 8" in {
      // two level-4 managers → totalLevel=8 → min(1.0, 1.0) = 1.0
      val result = callComputeManagerMultiplier(List(makeManager(0), makeManager(0)), 500)
      result shouldBe 1.0 +- 1e-6
    }
  }

  // ── computeLowDemandDiscounts ────────────────────────────────────────────
  // Requires DB. Tests cover:
  //   - boundary conditions (delta=0, multiplier=0)
  //   - full-demand scenario (count=0, multiplier=1.0) → max discount
  //   - partial-demand + sub-1 multiplier → no floor, linear scaling
  //   - near-threshold count + multiplier=1.0 → minimum floor kicks in
  //   - LARGE model (smaller threshold) for category variation

  "computeLowDemandDiscounts" should {

    // ── Boundary: no discount produced ───────────────────────────────────

    "produce nothing when multiplier is 0 regardless of count" in {
      callComputeLowDemandDiscounts(smallModel, 0, 0.0) shouldBe empty
      callComputeLowDemandDiscounts(smallModel, 400, 0.0) shouldBe empty
    }

    "produce Min when count equals threshold (delta=0)" in {
      // SMALL threshold = 450
      val discounts = callComputeLowDemandDiscounts(smallModel, 450, 1.0)
      priceDiscount(discounts)        shouldBe ModelDiscount.MIN_PRICE_DISCOUNT_PERCENTAGE.toDouble / 100
    }

    "produce Min when count exceeds threshold" in {
      val discounts = callComputeLowDemandDiscounts(smallModel, 500, 1.0)
      priceDiscount(discounts)        shouldBe ModelDiscount.MIN_PRICE_DISCOUNT_PERCENTAGE.toDouble / 100
    }

    // ── Full demand (count=0) + full multiplier (1.0) → maximum discounts ─

    "produce 50% price and 99% construction discount when count=0 and multiplier=1.0 (SMALL)" in {
      // delta/threshold = 1.0 → priceFactor=0.5, constructionFactor=0.99
      // multiplier=1.0 → max(MIN=0.10, 0.50) = 0.50 and max(MIN=0.30, 0.99) = 0.99
      val discounts = callComputeLowDemandDiscounts(smallModel, 0, 1.0)
      priceDiscount(discounts)        shouldBe ModelDiscount.MAX_PRICE_DISCOUNT_PERCENTAGE.toDouble / 100
      constructionDiscount(discounts) shouldBe ModelDiscount.MAX_CONSTRUCTION_TIME_DISCOUNT_PERCENTAGE.toDouble / 100
    }

    "produce 50% price discount for LARGE at half threshold (count=90, multiplier=1.0)" in {
      // LARGE threshold=180; delta=90 → priceFactor = 90/180 * 0.50 = 0.25
      // constructionFactor = 90/180 * 0.99 = 0.495 → above floor
      val discounts = callComputeLowDemandDiscounts(largeModel, 90, 1.0)
      priceDiscount(discounts)        shouldBe 0.3  +- 1e-4
      constructionDiscount(discounts) shouldBe 0.65 +- 1e-4
    }

    // ── Near-threshold + multiplier=1.0: minimum floor kicks in ──────────

    "apply 10% price floor and 30% construction floor when count is just below threshold (count=449, multiplier=1.0)" in {
      // delta=1 → priceFactor ≈ 0.00111, constructionFactor ≈ 0.0022 — both below floors
      val discounts = callComputeLowDemandDiscounts(largeModel, 179, 1.0)
      priceDiscount(discounts)        shouldBe 0.10 +- 1e-2
      constructionDiscount(discounts) shouldBe 0.30 +- 1e-2
    }

    "halve the discount when multiplier drops from 1.0 to 0.5 below the floor threshold (count=300)" in {
      // count=300 is above-floor territory; priceFactor=1/6, ctFactor=0.33
      val full = callComputeLowDemandDiscounts(smallModel, 300, 1.0)
      val half = callComputeLowDemandDiscounts(smallModel, 300, 0.5)
      priceDiscount(half)        shouldBe priceDiscount(full) * 0.5   +- 1e-6
      constructionDiscount(half) shouldBe constructionDiscount(full) * 0.5 +- 1e-6
    }

    "not apply the minimum floor when multiplier is 0.5, even near threshold" in {
      // count=449, multiplier=0.5: factors are tiny but floor only applies when multiplier >= 1.0
      val discounts = callComputeLowDemandDiscounts(smallModel, 449, 0.5)
      priceDiscount(discounts)        should be < 0.10
      constructionDiscount(discounts) should be < 0.30
    }

    // ── Metadata on returned discounts ───────────────────────────────────

    "tag every returned discount with LOW_DEMAND reason and correct modelId" in {
      val discounts = callComputeLowDemandDiscounts(mediumModel, 100, 0.5)
      discounts should not be empty
      discounts.foreach { d =>
        d.discountReason shouldBe DiscountReason.LOW_DEMAND
        d.modelId        shouldBe mediumModel.id
      }
    }
  }

  // ── computeMaxLowDemandPriceDiscountPct ─────────────────────────────────
  // Requires DB

  "computeMaxLowDemandPriceDiscountPct" should {

    "return 0 when count meets or exceeds threshold" in {
      ModelDiscount.computeMaxLowDemandPriceDiscountPct(smallModel, 450) shouldBe 0.0
      ModelDiscount.computeMaxLowDemandPriceDiscountPct(smallModel, 600) shouldBe 0.0
    }

    "return 50 (max) when count is 0" in {
      ModelDiscount.computeMaxLowDemandPriceDiscountPct(smallModel, 0) shouldBe 50.0 +- 1e-6
    }

    "return the 10% minimum floor for counts just below threshold (count=449, SMALL)" in {
      // delta=1 → 1/450*50 ≈ 0.11% → clamped up to 10
      ModelDiscount.computeMaxLowDemandPriceDiscountPct(smallModel, 449) shouldBe 10.0 +- 1e-6
    }

    "return the floor (10) for any count that yields a sub-floor raw value (SMALL, count=400)" in {
      // delta=50 → 50/450*50 ≈ 5.6% → clamped up to 10
      ModelDiscount.computeMaxLowDemandPriceDiscountPct(smallModel, 400) shouldBe 10.0 +- 1e-6
    }

    "return proportional value above the floor (SMALL, count=300 → delta=150 → 16.7%)" in {
      val result = ModelDiscount.computeMaxLowDemandPriceDiscountPct(smallModel, 300)
      result shouldBe (150.0 / 450 * 50) +- 1e-4
      result should be > 10.0
    }

    "return 25 for LARGE model at exactly half its threshold (count=90 out of 180)" in {
      ModelDiscount.computeMaxLowDemandPriceDiscountPct(largeModel, 90) shouldBe 25.0 +- 1e-4
    }

    "decrease as count increases from 0 toward threshold (SMALL)" in {
      val at0   = ModelDiscount.computeMaxLowDemandPriceDiscountPct(smallModel, 0)
      val at200 = ModelDiscount.computeMaxLowDemandPriceDiscountPct(smallModel, 200)
      val at350 = ModelDiscount.computeMaxLowDemandPriceDiscountPct(smallModel, 350)
      at0 should be > at200
      at200 should be > at350
    }
  }

  // ── ModelDiscount.description ────────────────────────────────────────────
  // No DB required

  "ModelDiscount.description" should {
    "include the integer percentage, 'off', and 'low demand' for LOW_DEMAND PRICE" in {
      val d = ModelDiscount(1, 0.15, DiscountType.PRICE, DiscountReason.LOW_DEMAND)
      d.description should include("15%")
      d.description should include("off")
      d.description.toLowerCase should include("low demand")
    }

    "include the integer percentage and 'low demand' for LOW_DEMAND CONSTRUCTION_TIME" in {
      val d = ModelDiscount(1, 0.30, DiscountType.CONSTRUCTION_TIME, DiscountReason.LOW_DEMAND)
      d.description should include("30%")
      d.description.toLowerCase should include("low demand")
    }

    "include the integer percentage and 'preferred supplier' for PREFERRED_SUPPLIER" in {
      val d = ModelDiscount(1, 0.05, DiscountType.PRICE, DiscountReason.PREFERRED_SUPPLIER)
      d.description should include("5%")
      d.description.toLowerCase should include("preferred supplier")
    }

    "truncate (not round) fractional percentage — 0.199 shows as 19%, not 20%" in {
      val d = ModelDiscount(1, 0.199, DiscountType.PRICE, DiscountReason.LOW_DEMAND)
      d.description should include("19%")
      d.description should not include "20%"
    }
  }
}
