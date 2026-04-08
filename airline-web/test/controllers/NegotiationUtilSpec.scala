package controllers

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class NegotiationUtilSpec extends AnyWordSpec with Matchers {

  // ─── computeOdds ──────────────────────────────────────────────────────────

  "NegotiationUtil.computeOdds" should {

    "return exactly Map(0 → 1.0) when requirement is 0 (no delegates needed)" in {
      NegotiationUtil.computeOdds(0.0, 11) shouldBe Map(0 -> 1.0)
    }

    "return 0.0 for every delegate count strictly below the requirement" in {
      val odds = NegotiationUtil.computeOdds(4.0, 11)
      odds.filter(_._1 < 4).values.foreach(_ shouldBe 0.0)
    }

    "return 0.0 when delegate count equals floor of a fractional requirement" in {
      // ceil(3.5) = 4, so 3 delegates still gives 0
      val odds = NegotiationUtil.computeOdds(3.5, 11)
      odds(3) shouldBe 0.0
    }

    "return non-zero odds at the first delegate count that meets the requirement" in {
      val odds = NegotiationUtil.computeOdds(3.5, 11)
      odds(4) should be > 0.0
    }

    "have monotonically non-decreasing odds as delegates increase" in {
      val odds = NegotiationUtil.computeOdds(3.0, 11)
      val sorted = odds.toSeq.sortBy(_._1)
      sorted.zip(sorted.tail).foreach { case ((_, a), (_, b)) => b should be >= a }
    }

    "cap all odds at 1.0" in {
      NegotiationUtil.computeOdds(2.0, 11).values.foreach(_ should be <= 1.0)
    }

    "stop the map at the first entry reaching 1.0 (no trailing entries)" in {
      val odds = NegotiationUtil.computeOdds(1.0, 11)
      odds.toSeq.sortBy(_._1).last._2 shouldBe 1.0
    }

    "respect maxDelegateCount as an upper bound on keys" in {
      NegotiationUtil.computeOdds(0.5, 3).keys.max should be <= 3
      NegotiationUtil.computeOdds(0.5, 5).keys.max should be <= 5
    }

    "include an entry for 0 delegates even for positive requirements (with 0 odds)" in {
      val odds = NegotiationUtil.computeOdds(5.0, 11)
      odds should contain key 0
      odds(0) shouldBe 0.0
    }
  }

  // ─── NegotiationResult ────────────────────────────────────────────────────

  "NegotiationResult" should {

    "succeed when result >= threshold" in {
      NegotiationResult(0.5, 0.5).isSuccessful shouldBe true
      NegotiationResult(0.3, 0.9).isSuccessful shouldBe true
    }

    "fail when result < threshold" in {
      NegotiationResult(0.5, 0.49).isSuccessful shouldBe false
      NegotiationResult(0.8, 0.0).isSuccessful  shouldBe false
    }

    "be a great success when result >= GREAT_SUCCESS_THRESHOLD and negotiation succeeded" in {
      val t = NegotiationUtil.GREAT_SUCCESS_THRESHOLD
      NegotiationResult(0.0, t).isGreatSuccess       shouldBe true
      NegotiationResult(0.0, t + 0.05).isGreatSuccess shouldBe true
      NegotiationResult(0.5, t + 0.01).isGreatSuccess shouldBe true
    }

    "not be a great success when result is just below GREAT_SUCCESS_THRESHOLD" in {
      val t = NegotiationUtil.GREAT_SUCCESS_THRESHOLD
      NegotiationResult(0.0, t - 0.01).isGreatSuccess shouldBe false
    }

    "not be a great success when the negotiation itself failed (result < threshold)" in {
      // result = 0.95 > GREAT_SUCCESS_THRESHOLD but threshold = 0.98 so it fails
      NegotiationResult(0.98, 0.95).isSuccessful  shouldBe false
      NegotiationResult(0.98, 0.95).isGreatSuccess shouldBe false
    }
  }

  // ─── isBadFailure ─────────────────────────────────────────────────────────

  "NegotiationUtil.isBadFailure" should {

    "return true when result is clearly at or below 35% of threshold" in {
      // threshold = 0.8; 35% of 0.8 = 0.8 * 0.35 ≈ 0.27999… (floating point)
      // Use 0.27 to stay safely below the boundary
      NegotiationUtil.isBadFailure(NegotiationResult(0.8, 0.27)) shouldBe true
      NegotiationUtil.isBadFailure(NegotiationResult(0.8, 0.10)) shouldBe true
      NegotiationUtil.isBadFailure(NegotiationResult(0.8, 0.0))  shouldBe true
    }

    "return false when the negotiation succeeded" in {
      NegotiationUtil.isBadFailure(NegotiationResult(0.5, 0.6)) shouldBe false
    }

    "return false when the failure result is above 35% of threshold" in {
      // threshold = 0.8; result = 0.4 > 0.28
      NegotiationUtil.isBadFailure(NegotiationResult(0.8, 0.4)) shouldBe false
    }
  }

  // ─── Failure AP refund (20 % floor) ───────────────────────────────────────

  "Negotiation failure AP refund" should {

    // mirrors the formula in addLinkBlock:  (actionPoints * 0.2).toInt
    def failureRefund(ap: Int): Int = (ap * 0.2).toInt

    "refund 0 APs for 1–4 APs spent (20 % < 1)" in {
      failureRefund(1) shouldBe 0
      failureRefund(2) shouldBe 0
      failureRefund(3) shouldBe 0
      failureRefund(4) shouldBe 0
    }

    "refund 1 AP for 5–9 APs spent" in {
      failureRefund(5) shouldBe 1
      failureRefund(6) shouldBe 1
      failureRefund(9) shouldBe 1
    }

    "refund 2 APs for 10–11 APs spent (up to MAX_ASSIGNED_DELEGATE)" in {
      failureRefund(10) shouldBe 2
      failureRefund(NegotiationUtil.MAX_ASSIGNED_DELEGATE) shouldBe 2
    }

    "never exceed the number of APs spent" in {
      (1 to NegotiationUtil.MAX_ASSIGNED_DELEGATE).foreach { ap =>
        failureRefund(ap) should be <= ap
      }
    }
  }

  // ─── Capacity-reduction AP refund formula ─────────────────────────────────

  "AP refund for capacity reduction" should {

    // mirrors actionPointRefund(difficulty) inside getLinkNegotiationInfo:
    //   Math.floor(Math.abs(difficulty) / 0.5).toInt
    def apRefund(difficulty: Double): Int =
      Math.floor(Math.abs(difficulty) / 0.5).toInt

    "return 0 for difficulty magnitude < 0.5" in {
      apRefund(-0.0) shouldBe 0
      apRefund(-0.4) shouldBe 0
      apRefund( 0.4) shouldBe 0
    }

    "return 1 for difficulty magnitude in [0.5, 1.0)" in {
      apRefund(-0.5) shouldBe 1
      apRefund(-0.9) shouldBe 1
    }

    "return 2 for difficulty magnitude in [1.0, 1.5)" in {
      apRefund(-1.0) shouldBe 2
      apRefund(-1.4) shouldBe 2
    }

    "scale by 2 per unit of difficulty magnitude" in {
      apRefund(-5.0) shouldBe 10
      apRefund(-3.5) shouldBe 7
      apRefund(-0.5) shouldBe 1
    }

    "treat positive and negative difficulty symmetrically" in {
      Seq(0.5, 1.0, 2.5, 5.0).foreach { d =>
        apRefund(d) shouldBe apRefund(-d)
      }
    }
  }

  // ─── Minimum 1 AP for new links ───────────────────────────────────────────

  "New-link minimum 1 AP rule" should {

    // computeOdds(0.0, n) → Map(0 → 1.0) confirms that a 0-requirement link
    // needs no delegates to succeed.  The business rule layered on top of this
    // in addLinkBlock is that creating ANY new link still costs at least 1 AP.
    "confirm that 0-requirement routes succeed with 0 delegates (negotiation-free)" in {
      val odds = NegotiationUtil.computeOdds(0.0, NegotiationUtil.MAX_ASSIGNED_DELEGATE)
      odds(0) shouldBe 1.0
    }

  }

  // ─── Higher requirement sum → more action points needed ───────────────────

  "Higher requirement sum" should {

    // The requirement sum is passed directly to computeOdds as finalRequirementValue.
    // Delegates below ceil(requirementSum) always yield zero odds, so a larger sum
    // means the player must spend more APs before any positive chance of success.

    def minDelegatesForAnyOdds(reqSum: Double): Int =
      NegotiationUtil.computeOdds(reqSum, NegotiationUtil.MAX_ASSIGNED_DELEGATE)
        .filter(_._2 > 0.0)
        .keys.min

    def delegatesToReach(reqSum: Double, targetOdds: Double): Option[Int] =
      NegotiationUtil.computeOdds(reqSum, NegotiationUtil.MAX_ASSIGNED_DELEGATE)
        .toSeq.sortBy(_._1)
        .find(_._2 >= targetOdds)
        .map(_._1)

    "require more delegates before any positive odds appear as requirement grows" in {
      val sums = Seq(1.0, 2.0, 3.0, 4.0, 5.0, 6.0)
      val mins = sums.map(minDelegatesForAnyOdds)
      // monotonically non-decreasing
      mins.zip(mins.tail).foreach { case (a, b) => b should be >= a }
      // and strictly greater overall
      mins.last should be > mins.head
    }

    "have the first-positive-odds threshold equal to ceil(requirementSum)" in {
      Seq(1.0, 2.0, 3.5, 4.0, 5.5, 6.0).foreach { req =>
        withClue(s"requirement $req:") {
          minDelegatesForAnyOdds(req) shouldBe Math.ceil(req).toInt
        }
      }
    }

    "require more delegates to reach 50% success odds as requirement grows" in {
      val (low, mid, high) = (
        delegatesToReach(1.0, 0.5),
        delegatesToReach(3.0, 0.5),
        delegatesToReach(6.0, 0.5)
      )
      (low, mid, high) match {
        case (Some(l), Some(m), Some(h)) =>
          m should be > l
          h should be > m
        case _ => fail("Expected 50% odds to be reachable for all requirement levels within MAX_ASSIGNED_DELEGATE")
      }
    }

    "produce lower odds at a fixed delegate count as requirement increases" in {
      // Use requirements 4–8 at delegate 8: none of these cap at 1.0 before delegate 8,
      // so the map always contains an entry for delegate 8.
      val fixedDelegates = 8
      val odds = Seq(4.0, 5.0, 6.0, 7.0, 8.0).map { req =>
        NegotiationUtil.computeOdds(req, NegotiationUtil.MAX_ASSIGNED_DELEGATE)
          .getOrElse(fixedDelegates, fail(s"No entry at delegate $fixedDelegates for requirement $req").asInstanceOf[Double])
      }
      odds.zip(odds.tail).foreach { case (a, b) => b should be < a }
    }
  }

  // ─── computeStartupVigorAdjustment ────────────────────────────────────────

  "NegotiationUtil.computeStartupVigorAdjustment" should {

    val MAX_REP  = NegotiationUtil.STARTUP_MAX_REPUTATION.toDouble   // 80
    val FADE_MAX = MAX_REP * 2                                        // 160

    // ── below STARTUP_MAX_REPUTATION (full vigor) ──────────────────────────

    "return None when requirementTotal is below 0.25 (adjustment would be positive)" in {
      NegotiationUtil.computeStartupVigorAdjustment(0.0,  0.0) shouldBe None
      NegotiationUtil.computeStartupVigorAdjustment(50.0, 0.1) shouldBe None
      NegotiationUtil.computeStartupVigorAdjustment(79.0, 0.24) shouldBe None
    }

    "return Some(0.0) at the exact boundary requirementTotal = 0.25" in {
      NegotiationUtil.computeStartupVigorAdjustment(50.0, 0.25) shouldBe Some(0.0)
    }

    "return a negative adjustment for full-vigor airlines with meaningful requirements" in {
      Seq(0.5, 1.0, 4.0, 9.0).foreach { req =>
        withClue(s"requirementTotal=$req: ") {
          val adj = NegotiationUtil.computeStartupVigorAdjustment(0.0, req)
          adj shouldBe defined
          adj.get should be < 0.0
        }
      }
    }

    "produce larger (more negative) adjustments for higher requirementTotal" in {
      val reqs = Seq(1.0, 2.0, 4.0, 9.0)
      val adjs = reqs.map(r => NegotiationUtil.computeStartupVigorAdjustment(50.0, r).get)
      adjs.zip(adjs.tail).foreach { case (a, b) => b should be < a }
    }

    // ── at and above STARTUP_MAX_REPUTATION (fading zone) ─────────────────

    "return None when reputation >= startupFadeCeiling" in {
      NegotiationUtil.computeStartupVigorAdjustment(FADE_MAX,       9.0) shouldBe None
      NegotiationUtil.computeStartupVigorAdjustment(FADE_MAX + 10,  9.0) shouldBe None
    }

    "be continuous at STARTUP_MAX_REPUTATION (fading at rep=80 equals full vigor)" in {
      val reqTotal = 4.0
      val fullVigor        = NegotiationUtil.computeStartupVigorAdjustment(MAX_REP - 0.001, reqTotal)
      val fadingAtBoundary = NegotiationUtil.computeStartupVigorAdjustment(MAX_REP, reqTotal)
      // both should be defined and essentially equal (strength ≈ 1.0 at boundary)
      fullVigor        shouldBe defined
      fadingAtBoundary shouldBe defined
      fadingAtBoundary.get should be (fullVigor.get +- 0.01)
    }

    "decrease in magnitude (less discount) as reputation increases through the fading zone" in {
      val reqTotal = 4.0
      val reps = Seq(0.0, 40.0, MAX_REP, 100.0, 130.0, FADE_MAX - 1)
      val adjs = reps.map(r => NegotiationUtil.computeStartupVigorAdjustment(r, reqTotal).getOrElse(0.0))
      // each successive adjustment should be >= (less negative or zero) than the previous
      adjs.zip(adjs.tail).foreach { case (a, b) => b should be >= a }
      // and there must be a real spread
      adjs.head should be < adjs.last
    }

    // ── deletion-refund guard ──────────────────────────────────────────────

    "never push the net requirement negative (adjustment magnitude never exceeds requirementTotal)" in {
      // When a link is deleted, the refund is proportional to Math.abs(requirementsSum).
      // If startup vigor drove the sum below zero it would inflate the refund above the
      // original cost.  This property ensures that can never happen.
      val reqTotals = Seq(0.25, 0.5, 1.0, 2.0, 4.0, 9.0, 16.0)
      val reps      = Seq(0.0, 40.0, MAX_REP - 0.001, MAX_REP, MAX_REP + 40, FADE_MAX - 1, FADE_MAX)
      for (req <- reqTotals; rep <- reps) {
        withClue(s"rep=$rep, requirementTotal=$req: ") {
          val net = req + NegotiationUtil.computeStartupVigorAdjustment(rep, req).getOrElse(0.0)
          net should be >= 0.0
        }
      }
    }
  }

  // ─── Swing chance functions ────────────────────────────────────────────────

  "NegotiationUtil.swingTriggerChance" should {

    "return a higher trigger chance for riskier (lower) odds" in {
      val lowRisk  = NegotiationUtil.swingTriggerChance(0.9, success = false)
      val highRisk = NegotiationUtil.swingTriggerChance(0.1, success = false)
      highRisk should be > lowRisk
    }

    "return values in [0, 1] for all inputs" in {
      Seq(0.0, 0.1, 0.5, 0.9, 1.0).foreach { odds =>
        NegotiationUtil.swingTriggerChance(odds, success = true)  should (be >= 0.0 and be <= 1.0)
        NegotiationUtil.swingTriggerChance(odds, success = false) should (be >= 0.0 and be <= 1.0)
      }
    }
  }

  "NegotiationUtil.bigSwingChance" should {

    "return a higher big-swing chance for riskier (lower) odds" in {
      NegotiationUtil.bigSwingChance(0.1) should be > NegotiationUtil.bigSwingChance(0.9)
    }

    "return values in [0, 1] for all inputs" in {
      Seq(0.0, 0.5, 1.0).foreach { odds =>
        NegotiationUtil.bigSwingChance(odds) should (be >= 0.0 and be <= 1.0)
      }
    }
  }
}
