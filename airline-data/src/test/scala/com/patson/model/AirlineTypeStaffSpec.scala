package com.patson.model

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class AirlineTypeStaffSpec extends AnyWordSpec with Matchers {

  // Compute total staff (modifier = 1.0, i.e. no base modifier)
  def totalStaff(distance: Int, flightCategory: FlightCategory.Value, airlineType: AirlineType, frequency: Int, planeCapacity: Int): Int = {
    val StaffSchemeBreakdown(basic, perFreq, per500Pax) = Link.getStaffRequired(distance, flightCategory, airlineType)
    val totalCapacity = planeCapacity * frequency
    Math.max(5, (basic + perFreq * frequency + per500Pax * totalCapacity / 500)).toInt
  }

  val SHORT_DOMESTIC   = 800   // under RegionalAirline.staffReductionRange (1500 km)
  val MEDIUM_DOMESTIC  = 1750  // inside fade zone (1500-2000 km)
  val LONGER        = 2500

  val LOW_FREQ  = 2
  val HIGH_FREQ = 28

  val SMALL_CAP  = 50
  val LARGE_CAP  = 120

  val airlineTypes = List(
    ("Legacy",   LegacyAirline,   FlightCategory.DOMESTIC),
    ("Luxury",   LuxuryAirline,   FlightCategory.DOMESTIC),
    ("Regional", RegionalAirline, FlightCategory.DOMESTIC)
  )

  "AirlineType staff comparison" when {

    "short domestic route (800 km)" should {

      "print staff table across frequency and capacity" in {
        println(s"\n=== SHORT DOMESTIC ($SHORT_DOMESTIC km) ===")
        println(f"${"Type"}%-10s ${"Freq"}%5s ${"Cap"}%5s ${"Staff"}%7s")
        for {
          (label, airlineType, cat) <- airlineTypes
          freq                      <- List(LOW_FREQ, HIGH_FREQ)
          cap                       <- List(SMALL_CAP, LARGE_CAP)
        } {
          val s = totalStaff(SHORT_DOMESTIC, cat, airlineType, freq, cap)
          println(f"$label%-10s $freq%5d $cap%5d $s%7d")
        }
        succeed
      }

      "Regional should require fewer or equal freq staff than Legacy at low frequency" in {
        val regional = totalStaff(SHORT_DOMESTIC, FlightCategory.DOMESTIC, RegionalAirline, LOW_FREQ, SMALL_CAP)
        val legacy   = totalStaff(SHORT_DOMESTIC, FlightCategory.DOMESTIC, LegacyAirline,   LOW_FREQ, SMALL_CAP)
        regional should be <= legacy
      }

      "Regional should require fewer or equal freq staff than Legacy at high frequency" in {
        val regional = totalStaff(SHORT_DOMESTIC, FlightCategory.DOMESTIC, RegionalAirline, HIGH_FREQ, SMALL_CAP)
        val legacy   = totalStaff(SHORT_DOMESTIC, FlightCategory.DOMESTIC, LegacyAirline,   HIGH_FREQ, SMALL_CAP)
        regional should be <= legacy
      }

      "Luxury should require fewer or equal freq staff than Legacy at both frequencies (small plane)" in {
        for (freq <- List(LOW_FREQ, HIGH_FREQ)) {
          val luxury = totalStaff(SHORT_DOMESTIC, FlightCategory.DOMESTIC, LuxuryAirline, freq, SMALL_CAP)
          val legacy = totalStaff(SHORT_DOMESTIC, FlightCategory.DOMESTIC, LegacyAirline, freq, SMALL_CAP)
          luxury should be <= legacy
        }
      }

      "Regional should cost proportionally more for large-capacity vs small-capacity than Legacy" in {
        val regionalSmall = totalStaff(SHORT_DOMESTIC, FlightCategory.DOMESTIC, RegionalAirline, LOW_FREQ, SMALL_CAP)
        val regionalLarge = totalStaff(SHORT_DOMESTIC, FlightCategory.DOMESTIC, RegionalAirline, LOW_FREQ, LARGE_CAP)
        val legacySmall   = totalStaff(SHORT_DOMESTIC, FlightCategory.DOMESTIC, LegacyAirline,   LOW_FREQ, SMALL_CAP)
        val legacyLarge   = totalStaff(SHORT_DOMESTIC, FlightCategory.DOMESTIC, LegacyAirline,   LOW_FREQ, LARGE_CAP)
        // Regional has 1.4x capacity staff ratio, so its cap diff should exceed Legacy's cap diff
        (regionalLarge - regionalSmall) should be >= (legacyLarge - legacySmall)
      }

      "all types should have higher staff at 28x vs 2x frequency (large plane)" in {
        for ((_, airlineType, cat) <- airlineTypes) {
          val lowFreqStaff  = totalStaff(SHORT_DOMESTIC, cat, airlineType, LOW_FREQ,  LARGE_CAP)
          val highFreqStaff = totalStaff(SHORT_DOMESTIC, cat, airlineType, HIGH_FREQ, LARGE_CAP)
          highFreqStaff should be >= lowFreqStaff
        }
      }

      "all types should have higher staff for 120-seat vs 50-seat plane at same frequency" in {
        for ((_, airlineType, cat) <- airlineTypes; freq <- List(LOW_FREQ, HIGH_FREQ)) {
          val smallStaff = totalStaff(SHORT_DOMESTIC, cat, airlineType, freq, SMALL_CAP)
          val largeStaff = totalStaff(SHORT_DOMESTIC, cat, airlineType, freq, LARGE_CAP)
          largeStaff should be >= smallStaff
        }
      }
    }

    "medium domestic route in fade zone (1750 km)" should {

      "print staff table across frequency and capacity" in {
        println(s"\n=== MEDIUM DOMESTIC ($MEDIUM_DOMESTIC km, fade zone) ===")
        println(f"${"Type"}%-10s ${"Freq"}%5s ${"Cap"}%5s ${"Staff"}%7s")
        for {
          (label, airlineType, cat) <- airlineTypes
          freq                      <- List(LOW_FREQ, HIGH_FREQ)
          cap                       <- List(SMALL_CAP, LARGE_CAP)
        } {
          val s = totalStaff(MEDIUM_DOMESTIC, cat, airlineType, freq, cap)
          println(f"$label%-10s $freq%5d $cap%5d $s%7d")
        }
        succeed
      }

      "Regional freq staff should be between its short-route and long-route values at high frequency" in {
        val shortStaff  = totalStaff(SHORT_DOMESTIC,  FlightCategory.DOMESTIC, RegionalAirline, HIGH_FREQ, SMALL_CAP)
        val mediumStaff = totalStaff(MEDIUM_DOMESTIC, FlightCategory.DOMESTIC, RegionalAirline, HIGH_FREQ, SMALL_CAP)
        val longStaff   = totalStaff(LONGER,       FlightCategory.DOMESTIC, RegionalAirline, HIGH_FREQ, SMALL_CAP)
        mediumStaff should be >= shortStaff
        mediumStaff should be <= longStaff
      }
    }

    "long international route (4000 km)" should {

      "print staff table across frequency and capacity" in {
        println(s"\n=== LONG INTERNATIONAL ($LONGER km) ===")
        println(f"${"Type"}%-10s ${"Freq"}%5s ${"Cap"}%5s ${"Staff"}%7s")
        for {
          (label, airlineType, _) <- airlineTypes
          freq                    <- List(LOW_FREQ, HIGH_FREQ)
          cap                     <- List(SMALL_CAP, LARGE_CAP)
        } {
          val s = totalStaff(LONGER, FlightCategory.INTERNATIONAL, airlineType, freq, cap)
          println(f"$label%-10s $freq%5d $cap%5d $s%7d")
        }
        succeed
      }

      "Regional should no longer have zero freq staff on long international routes" in {
        val StaffSchemeBreakdown(_, perFreq, _) = Link.getStaffRequired(LONGER, FlightCategory.INTERNATIONAL, RegionalAirline)
        perFreq should be > 0.0
      }

      "Luxury should still require fewer freq staff than Legacy on long international routes" in {
        for (freq <- List(LOW_FREQ, HIGH_FREQ); cap <- List(SMALL_CAP, LARGE_CAP)) {
          val luxury = totalStaff(LONGER, FlightCategory.INTERNATIONAL, LuxuryAirline, freq, cap)
          val legacy = totalStaff(LONGER, FlightCategory.INTERNATIONAL, LegacyAirline, freq, cap)
          luxury should be <= legacy
        }
      }
    }

    "comparing StaffSchemeBreakdown components directly" should {

      "RegionalAirline should have zero perFrequency staff on short domestic routes" in {
        val StaffSchemeBreakdown(_, perFreq, _) = Link.getStaffRequired(SHORT_DOMESTIC, FlightCategory.DOMESTIC, RegionalAirline)
        perFreq shouldEqual 0.0
      }

      "LuxuryAirline perFrequency should be 0.8x of LegacyAirline on any route" in {
        for ((distance, cat) <- List((SHORT_DOMESTIC, FlightCategory.DOMESTIC), (LONGER, FlightCategory.INTERNATIONAL))) {
          val StaffSchemeBreakdown(_, luxFreq, _) = Link.getStaffRequired(distance, cat, LuxuryAirline)
          val StaffSchemeBreakdown(_, legFreq, _) = Link.getStaffRequired(distance, cat, LegacyAirline)
          luxFreq shouldEqual legFreq * LuxuryAirline.staffFreqRatio +- 0.001
        }
      }

      "RegionalAirline per500Pax should be 1.4x of LegacyAirline on any route" in {
        for ((distance, cat) <- List((SHORT_DOMESTIC, FlightCategory.DOMESTIC), (LONGER, FlightCategory.INTERNATIONAL))) {
          val StaffSchemeBreakdown(_, _, regCap) = Link.getStaffRequired(distance, cat, RegionalAirline)
          val StaffSchemeBreakdown(_, _, legCap) = Link.getStaffRequired(distance, cat, LegacyAirline)
          regCap shouldEqual legCap * RegionalAirline.staffCapRatio +- 0.001
        }
      }
    }
  }
}
